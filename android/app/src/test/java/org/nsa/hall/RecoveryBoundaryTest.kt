package org.nsa.hall

import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.nsa.hall.runtime.RuntimeLease
import org.nsa.hall.security.*
import org.nsa.hall.transfer.SaveFormat
import org.nsa.hall.transfer.SaveSwap
import org.nsa.hall.transfer.SaveExpansionBudget
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

class RecoveryBoundaryTest {
    @get:Rule val temporary = TemporaryFolder()
    private fun metadata(bytes: ByteArray = "new".toByteArray()) = JSONObject()
        .put("save_schema", 1).put("game_id", "test-game").put("local_player_id", "local").put("source_release_id", "old-release")
        .put("compatibility_group", "save-v1").put("commit_seq", 3).put("created_at", "2026-11-07T00:00:00Z").put("total_bytes", bytes.size.toString())
        .put("files", JSONArray().put(JSONObject().put("path", "nested/state.dat").put("bytes", bytes.size.toString()).put("sha256", sha256(bytes))))

    @Test fun metadataIsValidatedBeforeExtraction() {
        val original = metadata()
        assertEquals(3L, SaveFormat.validate(original).single().bytes)
        val mutations: List<(JSONObject) -> Unit> = listOf(
            { it.remove("source_release_id") }, { it.put("unknown", true) }, { it.put("commit_seq", "3") },
            { it.put("commit_seq", -1) }, { it.put("created_at", "2026-11-07") }, { it.put("local_player_id", 7) },
            { it.put("source_release_id", "../other") }, { it.put("total_bytes", "4") },
            { it.getJSONArray("files").getJSONObject(0).put("bytes", (SaveFormat.MAXIMUM + 1).toString()) },
            { it.getJSONArray("files").getJSONObject(0).put("path", "bad\u0000name") },
            { it.getJSONArray("files").getJSONObject(0).put("path", "bad\u007fname") }
        )
        mutations.forEachIndexed { index, mutate -> val changed = JSONObject(original.toString()); mutate(changed); assertTrue("mutation $index", runCatching { SaveFormat.validate(changed) }.isFailure) }
        assertTrue(runCatching { SaveFormat.validate(original, 2) }.isFailure)
        val collision = metadata().put("total_bytes", "6")
        collision.getJSONArray("files").put(JSONObject(collision.getJSONArray("files").getJSONObject(0).toString()).put("path", "nested"))
        assertTrue(runCatching { SaveFormat.validate(collision) }.isFailure)
    }

    @Test fun policyReplayCannotClearKnownBlockingAcrossScopes() {
        fun policy(epoch: Long, state: String) = JSONObject().put("policy_schema", 1).put("epoch", epoch).put("issued_at", "2026-11-07T00:00:00Z")
            .put("revoked_key_ids", JSONArray()).put("releases", JSONArray().put(JSONObject().put("release_id", "release-1").put("state", state).put("reason_code", "test")))
        val blocked = acceptPolicy(null, policy(10, "blocked"), "a".repeat(64), "internal")
        assertTrue(runCatching { acceptPolicy(blocked, policy(10, "published"), "b".repeat(64), "internal") }.isFailure)
        assertTrue(runCatching { acceptPolicy(blocked, policy(9, "published"), "b".repeat(64), "internal") }.isFailure)
        assertEquals(10L, acceptPolicy(blocked, policy(10, "blocked"), "a".repeat(64), "internal").getLong("epoch"))
        var merged = mergeReleasePolicies(JSONArray(), blocked)
        merged = mergeReleasePolicies(merged, policy(9, "published"))
        assertEquals("blocked", merged.getJSONObject(0).getString("state"))
        merged = mergeReleasePolicies(merged, policy(11, "published").put("releases", JSONArray()))
        assertEquals("blocked", merged.getJSONObject(0).getString("state"))
        assertEquals("published", mergeReleasePolicies(merged, policy(12, "published")).getJSONObject(0).getString("state"))
    }
    @Test fun expandedBytesCannotExceedTheDeclaredFileOrSharedBudget() {
        val budget = SaveExpansionBudget(5, 5); val first = ByteArrayOutputStream()
        assertEquals(3L, budget.copy("abc".byteInputStream(), first, 3)); assertEquals(2L, budget.remaining)
        val overflow = ByteArrayOutputStream()
        assertTrue(runCatching { budget.copy("too large".byteInputStream(), overflow, 2) }.isFailure)
        assertEquals(0, overflow.size()); assertEquals(2L, budget.remaining)
        assertTrue(runCatching { budget.copy("abc".byteInputStream(), overflow, 3) }.isFailure)
        assertEquals(0, overflow.size())
    }

    @Test fun policyEqualEpochMustKeepTheEntirePayloadIdentity() {
        val previous = JSONObject().put("policy_schema", 1).put("epoch", 4).put("issued_at", "2026-11-07T00:00:00Z").put("releases", JSONArray()).put("revoked_key_ids", JSONArray())
        val accepted = acceptPolicy(null, previous, "a".repeat(64), "scope")
        assertTrue(runCatching { acceptPolicy(accepted, JSONObject(previous.toString()).put("issued_at", "2026-11-07T00:00:01Z"), "b".repeat(64), "scope") }.isFailure)
    }

    @Test fun canonicalIndexRecoversReserializedSignedBytes() {
        val index = JSONObject("{\"z\":\"中文/路径\",\"a\":1,\"files\":[{\"bytes\":\"0\",\"path\":\"a\\tb\"}]}")
        assertEquals("{\"a\":1,\"files\":[{\"bytes\":\"0\",\"path\":\"a\\tb\"}],\"z\":\"中文/路径\"}", String(canonicalIndexBytes(index), Charsets.UTF_8))
    }

    @Test fun runtimeLeaseSurvivesIndependentOwnersAndReleasesOnClose() {
        val root = temporary.newFolder()
        assertFalse(RuntimeLease.held(root))
        RuntimeLease.acquire(root)!!.use {
            assertTrue(RuntimeLease.held(root)); assertNull(RuntimeLease.acquire(root))
        }
        assertFalse(RuntimeLease.held(root)); RuntimeLease.acquire(root)!!.close()
    }

    @Test fun everyPublicationBoundaryRecoversExactlyOneWholeSnapshot() = runBlocking {
        val boundaries = listOf("journal-verified", "old-moved", "journal-old-moved", "new-active", "journal-new-active", "index-committed", "journal-indexed", "old-cleaned", "journal-cleaned")
        boundaries.forEach { boundary ->
            val root = temporary.newFolder(); File(root, "committed/nested").mkdirs(); File(root, "committed/nested/state.dat").writeText("old")
            val staging = File(root, "import-operation-1"); File(staging, "nested").mkdirs(); File(staging, "nested/state.dat").writeText("new")
            var index: JSONObject? = JSONObject().put("kind", "native").put("revision", 7)
            var writes = 0
            fun swap(fail: Boolean) = SaveSwap(root, {}, { from, to -> Files.move(from.toPath(), to.toPath(), StandardCopyOption.ATOMIC_MOVE) },
                { target, value -> val draft = File(root, target.name + ".new"); draft.writeText(value.toString()); Files.move(draft.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING) },
                { index }, { value -> index = value; writes++ }, { point -> if (fail && point == boundary) throw PowerLoss() })
            try { swap(true).publish(staging, metadata(), JSONObject().put("import_id", "operation-1").put("revision", 8)) } catch (_: PowerLoss) { }
            swap(false).recover(); swap(false).recover()
            val committed = File(root, "committed/nested/state.dat").readText()
            assertEquals(boundary, if (index?.optString("import_id") == "operation-1") "new" else "old", committed)
            assertTrue(boundary, writes <= 1); assertFalse(File(root, "before-import").exists()); assertFalse(File(root, "import-journal.json").exists())
        }
    }

    @Test fun directorySyncFailureImmediatelyCompensatesAndRecoveryIsRepeatable() = runBlocking {
        val root = temporary.newFolder(); File(root, "committed").mkdirs(); File(root, "committed/old.dat").writeText("old")
        val staging = File(root, "import-operation-2"); File(staging, "nested").mkdirs(); File(staging, "nested/state.dat").writeText("new")
        var failSync = true; var index: JSONObject? = null
        fun swap() = SaveSwap(root, { if (failSync) { failSync = false; error("injected fsync failure") } },
            { from, to -> Files.move(from.toPath(), to.toPath(), StandardCopyOption.ATOMIC_MOVE) }, { target, value -> target.writeText(value.toString()) }, { index }, { index = it })
        assertTrue(runCatching { swap().publish(staging, metadata(), JSONObject().put("import_id", "operation-2")) }.isFailure)
        swap().recover(); swap().recover()
        assertEquals("old", File(root, "committed/old.dat").readText()); assertNull(index)
    }

    @Test fun legacyEmptyCommittedDirectoryNeverWinsOverTheUniqueOldCopy() = runBlocking {
        val root = temporary.newFolder(); File(root, "committed").mkdirs(); File(root, "before-import").mkdirs(); File(root, "before-import/progress").writeText("unique-old")
        File(root, "import-journal.json").writeText(JSONObject().put("staging", "import-legacy").put("phase", "old-moved").toString())
        val swap = SaveSwap(root, {}, { from, to -> Files.move(from.toPath(), to.toPath(), StandardCopyOption.ATOMIC_MOVE) }, { target, value -> target.writeText(value.toString()) }, { null }, {})
        swap.recover(); swap.recover()
        assertEquals("unique-old", File(root, "committed/progress").readText())
    }
    private class PowerLoss : Error()
    @Test fun firstImportCanRollBackToNoSaveOrRetainItsIndexedSnapshot() = runBlocking {
        listOf("new-active", "index-committed").forEach { boundary ->
            val root = temporary.newFolder(); val staging = File(root, "import-first")
            File(staging, "nested").mkdirs(); File(staging, "nested/state.dat").writeText("new")
            var index: JSONObject? = null
            fun swap(crash: Boolean) = SaveSwap(root, {}, { from, to -> Files.move(from.toPath(), to.toPath(), StandardCopyOption.ATOMIC_MOVE) },
                { file, value -> file.writeText(value.toString()) }, { index }, { index = it }, { if (crash && it == boundary) throw PowerLoss() })
            try { swap(true).publish(staging, metadata(), JSONObject().put("import_id", "first")) } catch (_: PowerLoss) { }
            swap(false).recover(); swap(false).recover()
            assertEquals(boundary == "index-committed", File(root, "committed/nested/state.dat").exists())
        }
    }
}
