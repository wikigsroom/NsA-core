package org.nsa.hall

import android.content.Context
import android.app.Activity
import android.content.ContextWrapper
import android.content.Intent
import android.content.ServiceConnection
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.nsa.hall.data.*
import org.nsa.hall.runtime.*
import java.io.File
import java.util.UUID

/** Actual Room and kernel locks beneath disposable cache roots, never player data. */
@RunWith(AndroidJUnit4::class)
class SessionRecoveryTest {
    private class IsolatedContext(base: Context, val root: File) : ContextWrapper(base) {
        var unavailableFiles = false
        var bindCalls = 0
        override fun getFilesDir(): File = if (unavailableFiles) File(root, "not-a-directory").apply { if (!exists()) writeText("blocked") }
            else File(root, "files").apply { mkdirs() }
        override fun getCacheDir() = File(root, "cache").apply { mkdirs() }
        override fun getDatabasePath(name: String) = File(root, "db/$name").apply { parentFile!!.mkdirs() }
        override fun bindService(intent: Intent, connection: ServiceConnection, flags: Int): Boolean {
            bindCalls++
            error("An idle or invalid recovery must not start a runtime service")
        }
    }
    private suspend fun isolated(action: suspend (HallRepository, IsolatedContext) -> Unit) {
        val base = InstrumentationRegistry.getInstrumentation().targetContext
        val root = File(base.cacheDir, "isolated-session-${UUID.randomUUID()}").apply { mkdirs() }
        val context = IsolatedContext(base, root)
        val repository = HallRepository(context)
        try { action(repository, context) }
        finally { repository.database.close(); root.deleteRecursively() }
    }
    private suspend fun seed(repository: HallRepository, id: String = "old-session", generation: Int = 7): SessionRecord {
        val row = SessionRecord(id, "save-test", "release-1", generation, "first_frame", seq = 12, saveSeq = 3)
        repository.dao.putSession(row)
        repository.dao.putFact(Fact("runtime-context:$id", JSONObject().put("session_id", id).put("generation", generation).put("nonce", "private-test-session-nonce").toString()))
        repository.dao.putFact(Fact("session-generation", "19"))
        repository.dao.putFact(Fact("save-index:save-test", "{\"revision\":8,\"native_commit_seq\":3}"))
        repository.dao.putFact(Fact("save-seq:save-test", "3"))
        repository.saveRoot("save-test").mkdirs()
        File(repository.saveRoot("save-test"), "state.dat").writeBytes(byteArrayOf(9, 0, 6, 2))
        return row
    }
    private suspend fun reconcile(repository: HallRepository) = repository.operationLock.withLock {
        repository.reconcileStoppedSessionsUnderOperationLock()
    }
    @Test fun absentOwnerClosesOrphansAtomicallyWithoutInventingSaveProgress() = runBlocking {
        isolated { r, _ ->
            val old = seed(r)
            val second = old.copy(id = "second-orphan", generation = 8, state = "exit_requested")
            r.dao.putSession(second); r.dao.putFact(Fact("runtime-context:${second.id}", "second-context"))
            val terminal = old.copy(id = "already-ended", state = "ended")
            r.dao.putSession(terminal)
            assertEquals(SessionReconciliation.Idle(2), reconcile(r))
            assertTrue(r.dao.unfinished().isEmpty())
            for (id in listOf(old.id, second.id)) {
                val saved = r.dao.session(id)!!
                assertEquals("crashed", saved.state); assertEquals(12L, saved.seq); assertEquals(3L, saved.saveSeq)
                assertNull(r.dao.fact("runtime-context:$id"))
            }
            assertEquals(terminal, r.dao.session(terminal.id))
            assertEquals("19", r.dao.fact("session-generation")!!.value)
            assertEquals("{\"revision\":8,\"native_commit_seq\":3}", r.dao.fact("save-index:save-test")!!.value)
            assertEquals("3", r.dao.fact("save-seq:save-test")!!.value)
            assertArrayEquals(byteArrayOf(9, 0, 6, 2), File(r.saveRoot("save-test"), "state.dat").readBytes())
        }
    }
    @Test fun heldKernelLeasePreservesSessionAndReconnectContext() = runBlocking {
        isolated { r, _ ->
            val old = seed(r); val context = r.dao.fact("runtime-context:${old.id}")
            RuntimeLease.acquire(r.context.filesDir)!!.use {
                assertEquals(SessionReconciliation.Owned, reconcile(r))
                assertEquals(old, r.dao.session(old.id)); assertEquals(context, r.dao.fact("runtime-context:${old.id}"))
                assertTrue(RuntimeLease.held(r.context.filesDir))
            }
        }
    }
    @Test fun unreadableOwnershipFailsClosedWithoutChangingRows() = runBlocking {
        isolated { r, context ->
            val old = seed(r); val savedContext = r.dao.fact("runtime-context:${old.id}")
            context.unavailableFiles = true
            assertEquals(SessionReconciliation.Unknown, reconcile(r))
            assertEquals(old, r.dao.session(old.id)); assertEquals(savedContext, r.dao.fact("runtime-context:${old.id}"))
            context.unavailableFiles = false
        }
    }
    @Test fun repeatedReconciliationDoesNotRewriteTerminalHistoryOrSaveIndex() = runBlocking {
        isolated { r, _ ->
            val old = seed(r)
            assertEquals(SessionReconciliation.Idle(1), reconcile(r))
            val ended = r.dao.session(old.id); val index = r.dao.fact("save-index:save-test")
            assertEquals(SessionReconciliation.Idle(0), reconcile(r))
            assertEquals(ended, r.dao.session(old.id)); assertEquals(index, r.dao.fact("save-index:save-test"))
        }
    }
    @Test fun controllerRechecksAfterInitializationSawLiveGameWhichThenExited() = runBlocking {
        isolated { r, context ->
            val old = seed(r)
            RuntimeLease.acquire(r.context.filesDir)!!.use { assertEquals(SessionReconciliation.Owned, reconcile(r)) }
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
            try {
                val controller = withContext(Dispatchers.Main) { SessionController(r, scope) }
                assertTrue(withContext(Dispatchers.Main) { controller.recover() })
                assertEquals("crashed", r.dao.session(old.id)!!.state)
                assertNull(r.dao.fact("runtime-context:${old.id}"))
                assertFalse(controller.protectedRuntime.value); assertFalse(controller.launching.value)
                assertFalse(withContext(Dispatchers.Main) { controller.recover() })
                assertEquals(0, context.bindCalls)
            } finally { scope.cancel() }
        }
    }
    @Test fun controllerKeepsUnknownOwnershipProtectedAndStartsNothing() = runBlocking {
        isolated { r, context ->
            val old = seed(r); context.unavailableFiles = true
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
            try {
                val controller = withContext(Dispatchers.Main) { SessionController(r, scope) }
                assertFalse(withContext(Dispatchers.Main) { controller.recover() })
                assertTrue(controller.protectedRuntime.value); assertFalse(controller.launching.value)
                assertTrue(controller.message.value.isNotEmpty()); assertEquals(old, r.dao.session(old.id))
                assertEquals(0, context.bindCalls)
            } finally { context.unavailableFiles = false; scope.cancel() }
        }
    }
    @Test fun malformedStaleOrMissingNonceContextDoesNotAttachToLiveOwner() = runBlocking {
        isolated { r, context ->
            val old = seed(r)
            val invalid = listOf("{", "{\"session_id\":\"other\",\"generation\":7,\"nonce\":\"test\"}",
                "{\"session_id\":\"old-session\",\"generation\":8,\"nonce\":\"test\"}",
                "{\"session_id\":\"old-session\",\"generation\":\"7\",\"nonce\":\"test\"}",
                "{\"session_id\":\"old-session\",\"generation\":7.5,\"nonce\":\"test\"}",
                "{\"session_id\":\"old-session\",\"generation\":7}",
                "{\"session_id\":\"old-session\",\"generation\":7,\"nonce\":\"\"}",
                "{\"session_id\":\"old-session\",\"generation\":7,\"nonce\":17}")
            RuntimeLease.acquire(r.context.filesDir)!!.use {
                for (raw in invalid) {
                    r.dao.putFact(Fact("runtime-context:${old.id}", raw))
                    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
                    try {
                        val controller = withContext(Dispatchers.Main) { SessionController(r, scope) }
                        assertFalse(withContext(Dispatchers.Main) { controller.recover() })
                        assertTrue(controller.protectedRuntime.value); assertFalse(controller.launching.value)
                        assertEquals(old, r.dao.session(old.id)); assertEquals(raw, r.dao.fact("runtime-context:${old.id}")!!.value)
                        assertTrue(RuntimeLease.held(r.context.filesDir)); assertEquals(0, context.bindCalls)
                    } finally { scope.cancel() }
                }
            }
        }
    }
    @Test fun concurrentRecoveryAndLaunchCannotBypassTheOperationMutex() = runBlocking {
        isolated { r, context ->
            val old = seed(r)
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
            r.operationLock.lock(); var locked = true
            try {
                val controller = withContext(Dispatchers.Main) { SessionController(r, scope) }
                val first = scope.async { controller.recover() }
                withContext(Dispatchers.Main) {
                    assertFalse(controller.recover())
                    controller.launch(Activity(), "save-test")
                }
                assertFalse(first.isCompleted); assertEquals(old, r.dao.session(old.id))
                assertFalse(controller.launching.value)
                r.operationLock.unlock(); locked = false
                assertTrue(withTimeout(10000) { first.await() })
                assertEquals("crashed", r.dao.session(old.id)!!.state)
                assertEquals(0, context.bindCalls)
            } finally { if (locked) r.operationLock.unlock(); scope.cancel() }
        }
    }
    @Test fun databaseFailureReleasesExclusiveLeaseAndPreservesCommittedState() = runBlocking {
        isolated { r, context ->
            val old = seed(r); val savedContext = r.dao.fact("runtime-context:${old.id}")
            r.database.close()
            assertNotNull(runCatching { reconcile(r) }.exceptionOrNull())
            assertFalse(RuntimeLease.held(context.filesDir))
            val reopened = HallRepository(context)
            try {
                assertEquals(old, reopened.dao.session(old.id))
                assertEquals(savedContext, reopened.dao.fact("runtime-context:${old.id}"))
                assertArrayEquals(byteArrayOf(9, 0, 6, 2), File(reopened.saveRoot("save-test"), "state.dat").readBytes())
            } finally { reopened.database.close() }
        }
    }
}
