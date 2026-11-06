package org.nsa.hall

import android.content.Context
import android.content.ContextWrapper
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.nsa.hall.data.*
import org.nsa.hall.transfer.SaveManager
import org.nsa.hall.transfer.StorageReconciliation
import org.nsa.hall.security.canonicalIndexBytes
import org.nsa.hall.security.sha256
import org.json.JSONArray
import java.io.File
import java.util.UUID
import java.util.zip.ZipFile

/** Isolated Room/files; never deletes the player's installed game or saves. */
@RunWith(AndroidJUnit4::class)
class SaveRecoveryTest {
    private class IsolatedContext(base: Context, private val root: File) : ContextWrapper(base) {
        override fun getFilesDir() = File(root, "files").apply { mkdirs() }
        override fun getCacheDir() = File(root, "cache").apply { mkdirs() }
        override fun getDatabasePath(name: String) = File(root, "db/$name").apply { parentFile!!.mkdirs() }
    }
    private suspend fun seed(repository: HallRepository, demo: Boolean = false) {
        val manifest = JSONObject().put("runtime", JSONObject().put("runtime_slot_id", if (demo) "nsa-2048-v1" else "test-slot"))
            .put("save", JSONObject().put("compatibility_group", "test-save-v1"))
        repository.dao.putGame(GameRecord("save-test", "{}", activeRelease = "release-1"))
        repository.dao.putInstall(InstallRecord("save-test", "release-1", manifest.toString()))
        repository.saveRoot("save-test").mkdirs()
        File(repository.saveRoot("save-test"), "state.dat").writeText("first-save")
    }
    @Test fun importedSnapshotRegistersOnceAndReexportUsesItsLocalRevision() = runBlocking {
        val base = InstrumentationRegistry.getInstrumentation().targetContext
        val root = File(base.cacheDir, "isolated-save-${UUID.randomUUID()}").apply { mkdirs() }
        val repository = HallRepository(IsolatedContext(base, root))
        try {
            seed(repository)
            val saves = SaveManager(repository); val archive = saves.createExport("save-test")
            File(repository.saveRoot("save-test"), "state.dat").writeText("second-save")
            saves.importVerified("save-test", archive, "import-test-1")
            assertEquals("first-save", File(repository.saveRoot("save-test"), "state.dat").readText())
            val index = JSONObject(repository.dao.fact("save-index:save-test")!!.value)
            assertEquals("import", index.getString("kind")); assertEquals(1L, index.getLong("revision")); assertEquals(0L, index.getLong("origin_commit_seq"))
            saves.importVerified("save-test", archive, "import-test-1")
            assertEquals(index.toString(), repository.dao.fact("save-index:save-test")!!.value)
            val exported = saves.createExport("save-test")
            ZipFile(exported).use { zip ->
                val metadata = JSONObject(zip.getInputStream(zip.getEntry("backup.json")).bufferedReader().readText())
                assertEquals(1L, metadata.getLong("commit_seq")); assertEquals("release-1", metadata.getString("source_release_id"))
            }
            val backups = File(repository.context.filesDir, "players/local/backups/save-test").listFiles().orEmpty()
            assertEquals(1, backups.size)
            ZipFile(backups.single()).use { zip -> assertEquals("second-save", zip.getInputStream(zip.getEntry("files/state.dat")).bufferedReader().readText()) }
        } finally { repository.database.close(); root.deleteRecursively() }
    }
    @Test fun importConfirmationSurvivesNewRepositoryAndKeepsItsOriginalGame() = runBlocking {
        val base = InstrumentationRegistry.getInstrumentation().targetContext
        val root = File(base.cacheDir, "isolated-save-${UUID.randomUUID()}").apply { mkdirs() }
        val context = IsolatedContext(base, root)
        var repository = HallRepository(context)
        try {
            seed(repository)
            val archive = SaveManager(repository).createExport("save-test")
            repository.saveExchange.begin("save-test", "import", "saves")
            val id = JSONObject(repository.dao.fact("pending-save")!!.value).getString("id")
            assertEquals("import", repository.saveExchange.claimPicker(id))
            assertTrue(repository.saveExchange.result("import", Uri.fromFile(archive), id))
            repository.database.close(); repository = HallRepository(context)
            repository.saveExchange.recover()
            val pending = JSONObject(repository.dao.fact("pending-save")!!.value)
            assertEquals("save-test", pending.getString("game_id")); assertEquals("confirm", pending.getString("stage")); assertEquals(id, pending.getString("id"))
            repository.saveExchange.confirm(id)
            assertNull(repository.dao.fact("pending-save")); assertEquals(id, JSONObject(repository.dao.fact("save-index:save-test")!!.value).getString("import_id"))
        } finally { repository.database.close(); root.deleteRecursively() }
    }
    @Test fun demoExportIncludesOnlyItsAuthoritativeCommittedFile() = runBlocking {
        val base = InstrumentationRegistry.getInstrumentation().targetContext
        val root = File(base.cacheDir, "isolated-save-${UUID.randomUUID()}").apply { mkdirs() }
        val repository = HallRepository(IsolatedContext(base, root))
        try {
            seed(repository, true)
            File(repository.saveRoot("save-test"), "state.new").writeText("uncommitted")
            ZipFile(SaveManager(repository).createExport("save-test")).use { zip -> assertEquals(setOf("backup.json", "files/state.dat"), zip.entries().asSequence().map { it.name }.toSet()) }
        } finally { repository.database.close(); root.deleteRecursively() }
    }
    @Test fun startupReconcilesReadyFilesAndPausedChunksButKeepsCompletedHistory() = runBlocking {
        val base = InstrumentationRegistry.getInstrumentation().targetContext
        val root = File(base.cacheDir, "isolated-save-${UUID.randomUUID()}").apply { mkdirs() }
        val repository = HallRepository(IsolatedContext(base, root))
        try {
            val index = JSONObject().put("release_id", "release-1").put("file_count", 1).put("unpacked_bytes", "3")
                .put("files", JSONArray().put(JSONObject().put("path", "RomFS/data.bin").put("bytes", "3").put("sha256", sha256("abc".toByteArray()))))
            val rawIndex = canonicalIndexBytes(index)
            val manifest = JSONObject().put("_verified_sha256", "a".repeat(64)).put("file_count", 1).put("unpacked_bytes", "3")
                .put("file_index", JSONObject().put("bytes", rawIndex.size.toString()).put("sha256", sha256(rawIndex)))
            val release = repository.releaseRoot("save-test", "release-1")
            File(release, "RomFS").mkdirs(); File(release, "RomFS/data.bin").writeText("abc")
            // Exercise the old installer's reserialized index representation.
            File(release, ".file-index.json").writeText(index.toString())
            File(release, ".ready.json").writeText(JSONObject().put("release_id", "release-1").put("manifest_sha256", "a".repeat(64)).put("file_count", 1).toString())
            val installed = InstallRecord("save-test", "release-1", manifest.toString())
            repository.dao.putInstall(installed)
            assertNull(StorageReconciliation(repository).installationProblem(installed))
            File(release, "RomFS/data.bin").delete()
            val chunks = JSONObject().put("object_id", "archive-1").put("chunks", JSONArray().apply {
                listOf("abc", "def").forEachIndexed { number, bytes -> put(JSONObject().put("index", number).put("offset_bytes", (number * 3).toString()).put("length_bytes", "3").put("sha256", sha256(bytes.toByteArray()))) }
            })
            val rawChunks = canonicalIndexBytes(chunks)
            val transferManifest = JSONObject().put("archive", JSONObject().put("object_id", "archive-1")).put("chunk_index", JSONObject().put("bytes", rawChunks.size.toString()).put("sha256", sha256(rawChunks)))
            val task = File(repository.transferRoot, "transfer-1").apply { mkdirs() }
            File(task, "chunk_index.json").writeBytes(rawChunks); File(task, "archive.zip.partial").writeText("abcXXXtail")
            repository.dao.putTransfer(TransferRecord("transfer-1", "save-test", "release-1", transferManifest.toString(), state = "paused", intent = "pause", bytes = 6, total = 6))
            repository.dao.putTransfer(TransferRecord("completed-1", "save-test", "release-old", "{}", state = "completed", bytes = 9, total = 9))
            StorageReconciliation(repository).reconcile()
            assertEquals("repair_required", repository.dao.install("save-test", "release-1")!!.state)
            val paused = repository.dao.transfer("transfer-1")!!
            assertEquals("paused", paused.state); assertEquals(3L, paused.bytes); assertEquals(3L, File(task, "archive.zip.partial").length())
            assertEquals(9L, repository.dao.transfer("completed-1")!!.bytes)
        } finally { repository.database.close(); root.deleteRecursively() }
    }
    @Test fun stalePickerResultCannotBecomeAnotherOperationsImport() = runBlocking {
        val base = InstrumentationRegistry.getInstrumentation().targetContext
        val root = File(base.cacheDir, "isolated-save-${UUID.randomUUID()}").apply { mkdirs() }
        val repository = HallRepository(IsolatedContext(base, root))
        try {
            seed(repository)
            val archive = SaveManager(repository).createExport("save-test")
            // A test input outside the private exchange workspace, like SAF.
            val chosen = File(root, "chosen.zip"); archive.copyTo(chosen)
            repository.saveExchange.begin("save-test", "import", "saves")
            val first = JSONObject(repository.dao.fact("pending-save")!!.value).getString("id")
            repository.saveExchange.claimPicker(first); repository.saveExchange.cancel(first)
            repository.saveExchange.begin("save-test", "import", "saves")
            val second = JSONObject(repository.dao.fact("pending-save")!!.value).getString("id")
            repository.saveExchange.claimPicker(second)
            assertFalse(repository.saveExchange.result("import", Uri.fromFile(chosen), first))
            assertEquals("awaiting-result", JSONObject(repository.dao.fact("pending-save")!!.value).getString("stage"))
            assertTrue(repository.saveExchange.result("import", Uri.fromFile(chosen), second))
            repository.saveExchange.cancel(second)
        } finally { repository.database.close(); root.deleteRecursively() }
    }
}
