package org.nsa.hall

import android.content.Context
import android.content.ContextWrapper
import android.database.sqlite.SQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.nsa.hall.data.*
import org.nsa.hall.security.canonicalIndexBytes
import org.nsa.hall.security.sha256
import org.nsa.hall.transfer.Installation
import org.nsa.hall.transfer.StorageReconciliation
import java.io.File
import java.util.UUID

/** Exercises real Android SQLite/fsync under a unique, disposable private root. */
@RunWith(AndroidJUnit4::class)
class TransferPersistenceTest {
    private class IsolatedContext(base: Context, private val root: File) : ContextWrapper(base) {
        override fun getFilesDir() = File(root, "files").apply { mkdirs() }
        override fun getCacheDir() = File(root, "cache").apply { mkdirs() }
        override fun getDatabasePath(name: String) = File(root, "db/$name").apply { parentFile!!.mkdirs() }
    }
    private suspend fun isolated(test: suspend (HallRepository) -> Unit) {
        val base = InstrumentationRegistry.getInstrumentation().targetContext
        val root = File(base.cacheDir, "isolated-transfer-${UUID.randomUUID()}").apply { mkdirs() }
        val repository = HallRepository(IsolatedContext(base, root))
        try { test(repository) } finally { repository.database.close(); root.deleteRecursively() }
    }

    @Test fun cancellationRejectsStaleProgressAndRetainsOwnershipUntilCleanupAcknowledged() = runBlocking { isolated { r ->
        r.dao.putTransfer(TransferRecord("task-a", "game-a", "r1", "{}", bytes = 3, total = 9))
        val stale = r.dao.transfer("task-a")!!
        assertEquals(1, r.dao.pauseTransfer(stale.id))
        assertEquals(0, r.dao.transferProgress(stale.id, "downloading", 6))
        assertEquals("pause", r.dao.transfer(stale.id)!!.intent)
        r.dao.cancelTransfer(stale.id)
        assertEquals(0, r.dao.resumeTransfer(stale.id)); assertEquals(0, r.dao.transferProgress(stale.id, "downloading", 9))
        assertEquals(0, r.dao.transferFailure(stale.id, "late network error"))
        assertEquals("task-a", r.dao.activeTransfer("game-a")!!.id)
        runCatching { r.dao.putTransfer(TransferRecord("task-b", "game-a", "r2", "{}")) }
        assertNull(r.dao.transfer("task-b"))
        assertEquals(1, r.dao.acknowledgeCancellation(stale.id))
        r.dao.putTransfer(TransferRecord("task-b", "game-a", "r2", "{}"))
        assertEquals("task-b", r.dao.activeTransfer("game-a")!!.id)
        assertEquals("cancelled", r.dao.transfer(stale.id)!!.state)
    } }

    @Test fun sharedFailureBudgetStopsAtFiveAndExplicitRetryResetsIt() = runBlocking { isolated { r ->
        r.dao.putTransfer(TransferRecord("retry", "game-a", "r1", "{}"))
        repeat(5) { r.dao.transferFailure("retry", "service failure", retryAt = 2000L) }
        assertEquals("failed", r.dao.transfer("retry")!!.state)
        assertEquals(0, r.dao.transferProgress("retry", "downloading", 10))
        assertEquals(5, r.dao.transfer("retry")!!.failures)
        r.dao.resumeTransfer("retry")
        assertEquals(0, r.dao.transfer("retry")!!.failures); assertEquals(0L, r.dao.transfer("retry")!!.retryAt)
    } }

    @Test fun actualVersionOneMigrationPreservesRecordsAndRetiresOnlyDuplicateOwners() = runBlocking {
        val base = InstrumentationRegistry.getInstrumentation().targetContext
        val root = File(base.cacheDir, "isolated-migration-${UUID.randomUUID()}").apply { mkdirs() }
        val context = IsolatedContext(base, root)
        SQLiteDatabase.openOrCreateDatabase(context.getDatabasePath("hall.db"), null).use { db ->
            db.execSQL("CREATE TABLE games (id TEXT NOT NULL PRIMARY KEY, summary TEXT NOT NULL, detail TEXT NOT NULL, activeRelease TEXT NOT NULL, favorite INTEGER NOT NULL, lastPlayed INTEGER NOT NULL, settings TEXT NOT NULL)")
            db.execSQL("CREATE TABLE transfers (id TEXT NOT NULL PRIMARY KEY, gameId TEXT NOT NULL, releaseId TEXT NOT NULL, manifest TEXT NOT NULL, state TEXT NOT NULL, intent TEXT NOT NULL, blockers TEXT NOT NULL, bytes INTEGER NOT NULL, total INTEGER NOT NULL, failures INTEGER NOT NULL, error TEXT NOT NULL, created INTEGER NOT NULL, cellularApproved INTEGER NOT NULL, experimentalApproved INTEGER NOT NULL)")
            db.execSQL("CREATE INDEX index_transfers_gameId ON transfers(gameId)")
            db.execSQL("CREATE TABLE installs (gameId TEXT NOT NULL, releaseId TEXT NOT NULL, manifest TEXT NOT NULL, state TEXT NOT NULL, healthy INTEGER NOT NULL, installed INTEGER NOT NULL, PRIMARY KEY(gameId,releaseId))")
            db.execSQL("CREATE TABLE sessions (id TEXT NOT NULL PRIMARY KEY, gameId TEXT NOT NULL, releaseId TEXT NOT NULL, generation INTEGER NOT NULL, state TEXT NOT NULL, seq INTEGER NOT NULL, saveSeq INTEGER NOT NULL, error TEXT NOT NULL, started INTEGER NOT NULL, updated INTEGER NOT NULL)")
            db.execSQL("CREATE TABLE facts (`key` TEXT NOT NULL PRIMARY KEY, value TEXT NOT NULL)")
            db.execSQL("INSERT INTO games VALUES ('game-a','summary','detail','r0',1,123,'settings')")
            db.execSQL("INSERT INTO installs VALUES ('game-a','r0','manifest','ready',1,123)")
            db.execSQL("INSERT INTO facts VALUES ('save-index:game-a','committed-index')")
            listOf("b", "a").forEach { id -> db.execSQL("INSERT INTO transfers VALUES (?, 'game-a','r1','manifest','downloading','run','[]',3,9,2,'',10,1,1)", arrayOf(id)) }
            db.execSQL("INSERT INTO transfers VALUES ('history','game-a','r0','manifest','completed','run','[]',9,9,0,'',1,0,0)")
            db.version = 1
        }
        val database = HallDatabase.open(context)
        try {
            val dao = database.dao()
            assertEquals("r0", dao.game("game-a")!!.activeRelease); assertTrue(dao.game("game-a")!!.favorite)
            assertEquals("settings", dao.game("game-a")!!.settings)
            assertEquals("committed-index", dao.fact("save-index:game-a")!!.value)
            assertTrue(dao.install("game-a", "r0")!!.healthy)
            assertEquals("a", dao.activeTransfer("game-a")!!.id)
            assertEquals("cancel", dao.transfer("b")!!.intent); assertNull(dao.transfer("b")!!.ownerGameId)
            assertEquals(3L, dao.transfer("b")!!.bytes); assertEquals("completed", dao.transfer("history")!!.state)
            assertEquals(listOf("history", "a", "b"), dao.allTransfers().map { it.id })
        } finally { database.close(); root.deleteRecursively() }
    }

    private suspend fun seedCandidate(r: HallRepository): Pair<TransferRecord, JSONObject> {
        val index = JSONObject().put("release_id", "release-1").put("file_count", 1).put("unpacked_bytes", "3")
            .put("files", JSONArray().put(JSONObject().put("path", "RomFS/nested/data").put("bytes", "3").put("sha256", sha256("abc".toByteArray()))))
        val raw = canonicalIndexBytes(index)
        val manifest = JSONObject().put("_verified_sha256", "a".repeat(64)).put("file_count", 1).put("unpacked_bytes", "3")
            .put("file_index", JSONObject().put("bytes", raw.size.toString()).put("sha256", sha256(raw)))
        val row = TransferRecord("repair-task", "game-a", "release-1", manifest.toString(), state = "installing", total = 3)
        r.dao.putGame(GameRecord(row.gameId, "{}", activeRelease = row.releaseId))
        r.dao.putInstall(InstallRecord(row.gameId, row.releaseId, manifest.toString(), "repair_required"))
        r.dao.putTransfer(row)
        val target = r.releaseRoot(row.gameId, row.releaseId)
        File(target, "RomFS/nested").mkdirs(); File(target, "RomFS/nested/data").writeText("BAD")
        val staging = File(target.parentFile, ".release-1.staging-${sha256(row.id.toByteArray()).take(16)}")
        File(staging, "RomFS/nested").mkdirs(); File(staging, "RomFS/nested/data").writeText("abc")
        File(staging, ".file-index.json").writeBytes(raw)
        File(staging, ".ready.json").writeText(JSONObject().put("release_id", row.releaseId).put("manifest_sha256", "a".repeat(64)).put("file_count", 1).toString())
        val journal = JSONObject().put("journal_schema", 2).put("transfer_id", row.id).put("game_id", row.gameId).put("release_id", row.releaseId).put("manifest", manifest).put("phase", "VERIFIED")
        File(r.context.filesDir, "install-journals").mkdirs()
        File(r.context.filesDir, "install-journals/${row.id}.json").writeText(journal.toString())
        File(r.transferRoot, row.id).mkdirs(); File(r.transferRoot, "${row.id}/archive.zip.partial").writeText("whole archive")
        return row to journal
    }

    @Test fun damagedSameReleaseAndIndependentBadJournalDoNotBlockOtherRecovery() = runBlocking { isolated { r ->
        val (row, _) = seedCandidate(r)
        File(r.context.filesDir, "install-journals/00-corrupt.json").writeText("{broken")
        Installation(r).recover(); Installation(r).recover()
        assertEquals("abc", File(r.releaseRoot(row.gameId, row.releaseId), "RomFS/nested/data").readText())
        assertEquals("completed", r.dao.transfer(row.id)!!.state)
        assertEquals("ready", r.dao.install(row.gameId, row.releaseId)!!.state)
        assertFalse(File(r.transferRoot, row.id).exists()); assertFalse(File(r.context.filesDir, "install-journals/${row.id}.json").exists())
        assertTrue(r.recoveryWarning.value.isNotBlank()); assertNotNull(r.dao.fact("install-recovery:00-corrupt"))
    } }

    @Test fun fullCandidateVerificationRejectsSameLengthCorruptionAndUnexpectedFiles() = runBlocking { isolated { r ->
        val (row, _) = seedCandidate(r)
        val target = r.releaseRoot(row.gameId, row.releaseId)
        val stage = File(target.parentFile, ".release-1.staging-${sha256(row.id.toByteArray()).take(16)}")
        val record = r.dao.install(row.gameId, row.releaseId)!!
        assertNull(StorageReconciliation(r).installationProblem(record, stage, true))
        File(stage, "extra").writeText("unlisted")
        assertNotNull(StorageReconciliation(r).installationProblem(record, stage, true)); File(stage, "extra").delete()
        File(stage, "RomFS/nested/data").writeText("BAD")
        assertNotNull(StorageReconciliation(r).installationProblem(record, stage, true))
        Installation(r).recover()
        assertEquals("failed", r.dao.transfer(row.id)!!.state)
        assertEquals("BAD", File(target, "RomFS/nested/data").readText())
        assertTrue(File(r.context.filesDir, "install-journals/${row.id}.json").exists())
    } }

    @Test fun legacyTerminalArchivesAreCleanedWithoutTouchingInstalledResources() = runBlocking { isolated { r ->
        r.dao.putTransfer(TransferRecord("legacy-completed", "game-a", "release-1", "{}", state = "completed", total = 10))
        File(r.transferRoot, "legacy-completed").mkdirs(); File(r.transferRoot, "legacy-completed/archive.zip.partial").writeText("archive")
        val keep = File(r.releaseRoot("game-a", "release-1"), "original").apply { parentFile!!.mkdirs(); writeText("keep") }
        Installation(r).recover(); Installation(r).recover()
        assertFalse(File(r.transferRoot, "legacy-completed").exists()); assertEquals("keep", keep.readText())
        assertEquals("completed", r.dao.transfer("legacy-completed")!!.state)
    } }
}
