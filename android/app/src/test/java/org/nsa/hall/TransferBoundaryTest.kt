package org.nsa.hall

import kotlinx.coroutines.runBlocking
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.nsa.hall.data.TransferRecord
import org.nsa.hall.security.sha256
import org.nsa.hall.transfer.*
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

class TransferBoundaryTest {
    @get:Rule val temporary = TemporaryFolder()
    private fun response(code: Int, vararg headers: Pair<String, String>): Response = Response.Builder()
        .request(Request.Builder().url("https://localhost/object").build()).protocol(Protocol.HTTP_1_1).code(code).message("test")
        .apply { headers.forEach { addHeader(it.first, it.second) } }.build()

    @Test fun rangeRejectsWholeObjectsWrongOffsetsChangedVersionsAndEncoding() {
        TransferProtocol.range(response(206, "Content-Range" to "bytes 3-5/9", "ETag" to "\"v1\""), 3, 3, 9, "\"v1\"")
        listOf(
            response(200, "Content-Range" to "bytes 3-5/9", "ETag" to "\"v1\""),
            response(206, "Content-Range" to "bytes 0-2/9", "ETag" to "\"v1\""),
            response(206, "Content-Range" to "bytes 3-5/9", "ETag" to "\"v2\""),
            response(206, "Content-Range" to "bytes 3-5/9", "ETag" to "\"v1\"", "Content-Encoding" to "gzip", "Content-Encoding" to "identity")
        ).forEach { assertTrue(runCatching { TransferProtocol.range(it, 3, 3, 9, "\"v1\"") }.exceptionOrNull() is TransferProtocolException) }
    }

    @Test fun unsatisfiedRangeRequiresAnEntireMatchingSignedLocalObject() {
        val file = temporary.newFile().apply { writeText("abc") }
        val signed = JSONObject().put("bytes", "3").put("sha256", sha256(file))
        val reply = response(416, "Content-Range" to "bytes */3", "ETag" to "\"v1\"")
        assertTrue(TransferProtocol.completeAfter416(reply, file, signed, "\"v1\""))
        file.writeText("abX"); assertFalse(TransferProtocol.completeAfter416(reply, file, signed, "\"v1\""))
        file.writeText("abcX"); assertFalse(TransferProtocol.completeAfter416(reply, file, signed, "\"v1\""))
        file.writeText("abc"); assertFalse(TransferProtocol.completeAfter416(response(416, "Content-Range" to "bytes */4"), file, signed, "\"v1\""))
    }

    @Test fun renewalCannotChangeAnySignedIdentityOrStrongVersion() {
        val signed = JSONObject().put("object_id", "object-1").put("bytes", "3").put("sha256", "b".repeat(64))
        val manifest = JSONObject().put("release_id", "release-1").put("_verified_sha256", "a".repeat(64)).put("archive", signed)
        val grant = JSONObject(signed.toString()).put("release_id", "release-1").put("manifest_sha256", "a".repeat(64)).put("etag", "\"v1\"").put("url", "https://localhost/secret")
        val bound = TransferProtocol.identity(manifest, "archive", grant)
        assertFalse(bound.has("url"))
        listOf("object_id", "bytes", "sha256", "release_id", "manifest_sha256", "etag").forEach { field ->
            val changed = JSONObject(grant.toString()).put(field, if (field == "etag") "\"v2\"" else "changed")
            assertTrue(field, runCatching { TransferProtocol.identity(manifest, "archive", changed, bound) }.isFailure)
        }
        assertTrue(runCatching { TransferProtocol.identity(manifest, "archive", JSONObject(grant.toString()).put("etag", "W/\"v1\"")) }.isFailure)
    }

    @Test fun forbiddenIsNotAutomaticallyAnExpiredUrlAndRetryBudgetIsShared() {
        val grant = JSONObject().put("expires_at", "2026-11-07T12:00:00Z")
        assertFalse(TransferProtocol.expiredUrl(response(403, "Date" to "Sat, 7 Nov 2026 11:59:00 GMT"), grant))
        assertTrue(TransferProtocol.expiredUrl(response(403, "Date" to "Sat, 7 Nov 2026 12:01:00 GMT"), grant))
        assertFalse(TransferProtocol.expiredUrl(response(500, "Date" to "Sat, 7 Nov 2026 12:01:00 GMT"), grant))
        assertTrue(TransferProtocol.failure(response(403)).permanent)
        assertFalse(TransferProtocol.failure(response(429, "Retry-After" to "120")).permanent)
        assertEquals(120L, TransferProtocol.failure(response(429, "Retry-After" to "120")).retryAfter)
    }

    @Test fun drainObservesLateArrivalsAndUsesStableTieBreakWithoutRevivingPausedRows() = runBlocking {
        fun row(id: String, created: Long, intent: String = "run") = TransferRecord(id, "game-$id", "r", "{}", intent = intent, created = created)
        val rows = mutableListOf(row("b", 1), row("a", 1), row("paused", 0, "pause"))
        val seen = mutableListOf<String>()
        drainTransferQueue({ rows.toList() }) { task ->
            seen += task.id; rows.removeAll { it.id == task.id }
            if (task.id == "a") rows += row("late", 2)
        }
        assertEquals(listOf("a", "b", "late"), seen)
        assertEquals(listOf("paused"), rows.map { it.id })
    }

    @Test fun independentTransferOwnersCannotOpenTheSameWriterLifetime() {
        val root = temporary.newFolder()
        TransferLease.acquire(root)!!.use { assertNull(TransferLease.acquire(root)) }
        TransferLease.acquire(root)!!.close()
    }

    private class Crash : Error()
    private inner class Fixture {
        val root = temporary.newFolder()
        val journalFile = File(root, "journal.json")
        val target = File(root, "release")
        val staging = File(root, "staging")
        val replaced = File(root, "replaced")
        val archive = File(root, "archive.zip").apply { writeText("archive") }
        var activations = 0
        var completed = false
        var activeRelease = "old"
        init {
            File(target, "nested").mkdirs(); File(target, "nested/data").writeText("BAD")
            File(staging, "nested").mkdirs(); File(staging, "nested/data").writeText("new")
            journalFile.writeText(JSONObject().put("phase", "VERIFIED").toString())
        }
        fun valid(file: File) = File(file, "nested/data").let { it.isFile && it.readText() == "new" }
        suspend fun recover(crashAt: String? = null, sync: (File) -> Unit = {}, cleanup: () -> Unit = { archive.delete(); Unit }) {
            if (!journalFile.exists()) return
            InstallCommit(journalFile, JSONObject(journalFile.readText()), target, staging, replaced, sync,
                { from, to -> Files.move(from.toPath(), to.toPath(), StandardCopyOption.ATOMIC_MOVE) },
                { file, value ->
                    val draft = File(root, "journal.new"); draft.writeText(value.toString())
                    Files.move(draft.toPath(), file.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
                }, ::valid, { activations++; activeRelease = "new" }, { completed = true }, cleanup,
                { if (it == crashAt) throw Crash() }).recover()
        }
    }

    @Test fun damagedSameReleaseRecoversAcrossEveryPublicationAndCleanupBoundary() = runBlocking {
        listOf("old-replaced", "candidate-published", "DIRECTORY_PUBLISHED", "database-committed", "DB_ACTIVATED", "transfer-completed", "download-cleaned", "CLEANED", "journal-cleaned").forEach { point ->
            val f = Fixture()
            try { f.recover(point) } catch (_: Crash) { }
            f.recover(); f.recover()
            assertTrue(point, f.valid(f.target)); assertTrue(point, f.completed)
            assertEquals(point, "new", f.activeRelease)
            assertFalse(point, f.archive.exists()); assertFalse(point, f.replaced.exists()); assertFalse(point, f.journalFile.exists())
        }
    }

    @Test fun failedCleanupKeepsItsJournalAndNeverReactivatesAnOldRelease() = runBlocking {
        val f = Fixture()
        assertTrue(runCatching { f.recover(cleanup = { error("injected full archive deletion failure") }) }.isFailure)
        assertTrue(f.completed); assertTrue(f.archive.exists())
        assertEquals("DB_ACTIVATED", JSONObject(f.journalFile.readText()).getString("phase"))
        val before = f.activations; f.activeRelease = "a-newer-release"
        f.recover(); f.recover()
        assertEquals(before, f.activations); assertEquals("a-newer-release", f.activeRelease); assertFalse(f.archive.exists())
    }

    @Test fun missingCandidatesDoNotActivateOrDiscardTheUniqueOldCopy() = runBlocking {
        val f = Fixture(); f.staging.deleteRecursively()
        assertTrue(runCatching { f.recover() }.isFailure)
        assertEquals("BAD", File(f.target, "nested/data").readText())
        assertEquals(0, f.activations); assertTrue(f.journalFile.exists()); assertTrue(f.archive.exists())
    }

    @Test fun directoryBarrierFailureRetainsARecoverableCandidateAndChildBarriersPrecedeParents() = runBlocking {
        val f = Fixture(); var fail = true; val synchronized = mutableListOf<File>()
        assertTrue(runCatching { f.recover(sync = { synchronized += it; if (fail) { fail = false; error("injected fsync failure") } }) }.isFailure)
        assertEquals(0, f.activations); assertTrue(f.journalFile.exists())
        f.recover(sync = { synchronized += it }); f.recover()
        assertTrue(f.valid(f.target))
        val nested = synchronized.indexOf(File(f.staging, "nested")); val parent = synchronized.indexOf(f.staging)
        assertTrue(nested >= 0 && parent > nested)
    }
}
