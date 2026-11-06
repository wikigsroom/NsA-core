package org.nsa.hall.transfer

import android.net.Uri
import android.system.Os
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject
import org.nsa.hall.data.Fact
import org.nsa.hall.data.HallRepository
import org.nsa.hall.security.*
import java.io.File
import java.nio.file.Files
import java.time.Instant
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

class SaveManager(private val repository: HallRepository) {
    val exchangeRoot = File(repository.context.filesDir, "save-exchange").apply { mkdirs() }
    private fun swap(game: String) = SaveSwap(repository.saveRoot(game).parentFile!!, ::syncDirectory,
        { from, to -> Os.rename(from.absolutePath, to.absolutePath) }, ::atomicJson,
        { repository.dao.fact("save-index:$game")?.value?.let(::JSONObject) },
        { value -> if (value == null) repository.dao.deleteFact("save-index:$game") else repository.dao.putFact(Fact("save-index:$game", value.toString())) })

    internal suspend fun recoverUnderOperationLock(game: String): String? = swap(safeId(game)).recover()
    internal suspend fun recoverAllUnderOperationLock() {
        File(repository.context.filesDir, "players/local/saves").listFiles().orEmpty().filter { it.isDirectory }.forEach { recoverUnderOperationLock(safeId(it.name)) }
    }
    private suspend fun installed(game: String): Pair<String, JSONObject> {
        val current = repository.dao.game(game) ?: error("游戏不存在")
        val install = repository.dao.install(game, current.activeRelease) ?: error("需要已安装的游戏版本")
        return current.activeRelease to JSONObject(install.manifest)
    }
    private fun byPath(entries: JSONArray?): JSONObject = JSONObject().apply {
        if (entries != null) for (index in 0 until entries.length()) { val file = entries.getJSONObject(index); put(file.getString("path"), file) }
    }
    private fun files(game: String, manifest: JSONObject): List<File> {
        val root = repository.saveRoot(game)
        require(!Files.isSymbolicLink(root.toPath()))
        val all = root.walkTopDown().onEnter { require(!Files.isSymbolicLink(it.toPath())); true }.filter { it.isFile }.toList()
        all.forEach { require(!Files.isSymbolicLink(it.toPath())); safeChild(root, it.relativeTo(root).invariantSeparatorsPath) }
        // The compiled demo owns state.dat. Its historical state.new was a
        // private staging file, never an authoritative committed save.
        return if (manifest.getJSONObject("runtime").getString("runtime_slot_id") == "nsa-2048-v1") all.filter { it.relativeTo(root).invariantSeparatorsPath == "state.dat" } else all
    }
    suspend fun createExport(game: String): File = repository.operationLock.withLock { createExportUnderOperationLock(game) }
    private suspend fun createExportUnderOperationLock(game: String): File {
        repository.requireIdle(game); recoverUnderOperationLock(game)
        val (release, manifest) = installed(game)
        val root = repository.saveRoot(game); val sources = files(game, manifest)
        require(sources.isNotEmpty() && sources.size <= 10000) { "暂无可导出的游戏存档" }
        val entries = JSONArray(); var total = 0L
        sources.forEach { file ->
            total = Math.addExact(total, file.length()); require(total <= SaveFormat.MAXIMUM)
            entries.put(JSONObject().put("path", file.relativeTo(root).invariantSeparatorsPath).put("bytes", file.length().toString()).put("sha256", sha256(file)))
        }
        require(repository.availableBytes() >= Math.addExact(total, 64L * 1024 * 1024)) { "导出空间不足，请先清理可重建缓存" }
        var index = repository.dao.fact("save-index:$game")?.value?.let(::JSONObject)
        if (index?.optString("kind") in listOf("import", "observed") && !jsonEquivalent(byPath(index?.optJSONArray("files")), byPath(entries))) {
            // A native process may have committed while the hall was absent and
            // died before its event could be replayed. Record observation only.
            val previous = index!!
            index = JSONObject().put("kind", "observed").put("revision", Math.addExact(previous.getLong("revision"), 1))
                .put("previous_import_id", previous.optString("import_id", previous.optString("previous_import_id"))).put("source_release_id", release)
                .put("compatibility_group", manifest.getJSONObject("save").getString("compatibility_group"))
                .put("observed_at", Instant.now().toString()).put("files", entries)
            repository.dao.putFact(Fact("save-index:$game", index.toString()))
        }
        val metadata = JSONObject().put("save_schema", 1).put("game_id", game).put("local_player_id", "local")
            .put("source_release_id", index?.optString("source_release_id", release) ?: release)
            .put("compatibility_group", manifest.getJSONObject("save").getString("compatibility_group"))
            .put("commit_seq", index?.optLong("revision") ?: repository.dao.fact("save-seq:$game")?.value?.toLongOrNull() ?: 0)
            .put("created_at", Instant.now().toString()).put("total_bytes", total.toString()).put("files", entries)
        SaveFormat.validate(metadata)
        val out = File(exchangeRoot, "export-${UUID.randomUUID()}.nsa-save.zip")
        try {
            out.outputStream().use { stream ->
                ZipOutputStream(stream.buffered()).use { zip ->
                zip.putNextEntry(ZipEntry("backup.json")); zip.write(metadata.toString().toByteArray()); zip.closeEntry()
                sources.forEach { file -> zip.putNextEntry(ZipEntry("files/" + file.relativeTo(root).invariantSeparatorsPath)); file.inputStream().use { it.copyTo(zip, 65536) }; zip.closeEntry() }
                zip.finish(); zip.flush(); stream.fd.sync()
                }
            }
            syncDirectory(exchangeRoot); require(out.length() <= SaveFormat.MAXIMUM) { "备份文件超过导入大小上限" }
            return out
        } catch (error: Exception) { out.delete(); throw error }
    }
    suspend fun backupToLocal(game: String, reason: String): File = repository.operationLock.withLock { backupUnderOperationLock(game, reason) }
    internal suspend fun backupUnderOperationLock(game: String, reason: String): File {
        val archive = createExportUnderOperationLock(game)
        val directory = File(repository.context.filesDir, "players/local/backups/${safeId(game)}").apply { mkdirs() }
        val target = File(directory, "${System.currentTimeMillis()}-${safeId(reason)}-${UUID.randomUUID()}.nsa-save.zip")
        val temporary = File(directory, target.name + ".partial")
        try {
            archive.inputStream().use { input -> temporary.outputStream().use { output -> input.copyTo(output, 65536); output.fd.sync() } }
            Os.rename(temporary.absolutePath, target.absolutePath); syncDirectory(directory)
            return target
        } finally { temporary.delete(); archive.delete() }
    }
    suspend fun copyImport(uri: Uri, target: File) {
        require(target.parentFile == exchangeRoot)
        val temporary = File(exchangeRoot, target.name + ".partial")
        var count = 0L
        try {
            repository.context.contentResolver.openInputStream(uri)!!.use { input -> temporary.outputStream().use { output ->
                val buffer = ByteArray(65536)
                while (true) { val n = input.read(buffer); if (n < 0) break; count = Math.addExact(count, n.toLong()); require(count <= SaveFormat.MAXIMUM) { "备份文件超过 2 GiB 上限" }; require(repository.availableBytes() >= n + 64L * 1024 * 1024) { "复制备份空间不足" }; output.write(buffer, 0, n) }
                output.fd.sync()
            } }
            Os.rename(temporary.absolutePath, target.absolutePath); syncDirectory(exchangeRoot)
        } finally { temporary.delete() }
    }
    suspend fun inspect(game: String, archive: File): JSONObject {
        repository.requireIdle(game)
        require(archive.isFile && archive.length() in 1..SaveFormat.MAXIMUM)
        rejectLinksAndSpecialEntries(archive)
        val metadata = ZipFile(archive).use { zip ->
            val entry = zip.getEntry("backup.json") ?: error("缺少存档备份清单")
            require(entry.size in 1..(8 * 1024 * 1024))
            strictObject(zip.getInputStream(entry).use { it.readBounded(8 * 1024 * 1024 + 1) })
        }
        val entries = SaveFormat.validate(metadata)
        require(metadata.getString("game_id") == game && metadata.getString("local_player_id") == "local") { "备份属于其他游戏或玩家" }
        val manifest = installed(game).second
        require(metadata.getString("compatibility_group") == manifest.getJSONObject("save").getString("compatibility_group")) { "存档格式与当前游戏不兼容" }
        if (manifest.getJSONObject("runtime").getString("runtime_slot_id") == "nsa-2048-v1") require(entries.map { it.path } == listOf("state.dat")) { "此游戏仅接受已提交的 state.dat" }
        ZipFile(archive).use { zip ->
            val expected = entries.associateBy { "files/" + it.path }
            require(zip.size() == expected.size + 1)
            val names = mutableSetOf<String>()
            zip.entries().asSequence().forEach { entry ->
                require(names.add(entry.name) && !entry.isDirectory && entry.method in listOf(0, 8))
                if (entry.name != "backup.json") require(entry.size == (expected[entry.name] ?: error("备份含未登记文件")).bytes)
            }
            require(names == expected.keys + "backup.json")
        }
        return metadata
    }
    suspend fun importVerified(game: String, archive: File, operationId: String): Unit = repository.operationLock.withLock {
        repository.requireIdle(game); recoverUnderOperationLock(game)
        if (repository.dao.fact("save-index:$game")?.value?.let(::JSONObject)?.optString("import_id") == operationId) return@withLock
        val metadata = inspect(game, archive); val entries = SaveFormat.validate(metadata)
        val parent = repository.saveRoot(game).parentFile!!.apply { mkdirs() }
        val staging = File(parent, "import-${safeId(operationId)}")
        val oldBytes = repository.saveRoot(game).walkTopDown().filter { it.isFile }.fold(0L) { sum, file -> Math.addExact(sum, file.length()) }
        val required = Math.addExact(checkedBytes(metadata.getString("total_bytes")), Math.addExact(oldBytes, 128L * 1024 * 1024))
        require(repository.availableBytes() >= required) { "导入空间不足：需要展开存档、原存档备份和安全预留空间" }
        require(!staging.exists() || staging.deleteRecursively()); require(staging.mkdirs())
        try {
            val budget = SaveExpansionBudget(checkedBytes(metadata.getString("total_bytes")))
            ZipFile(archive).use { zip -> entries.forEach { expected ->
                val file = safeChild(staging, expected.path); require(file.parentFile!!.isDirectory || file.parentFile!!.mkdirs())
                var expanded = 0L
                zip.getInputStream(zip.getEntry("files/" + expected.path)).use { input -> file.outputStream().use { output ->
                    expanded = budget.copy(input, output, expected.bytes)
                    output.fd.sync()
                } }
                require(expanded == expected.bytes && sha256(file) == expected.sha256) { "备份文件校验失败" }
            } }
            require(budget.remaining == 0L)
            staging.walkBottomUp().filter { it.isDirectory }.forEach(::syncDirectory)
            if (repository.saveRoot(game).walkTopDown().any { it.isFile }) backupUnderOperationLock(game, "before-import")
            val previous = repository.dao.fact("save-index:$game")?.value?.let(::JSONObject)
            val index = JSONObject().put("kind", "import").put("import_id", safeId(operationId)).put("revision", Math.addExact(previous?.optLong("revision") ?: 0, 1))
                .put("source_release_id", metadata.getString("source_release_id")).put("compatibility_group", metadata.getString("compatibility_group"))
                .put("archive_sha256", sha256(archive)).put("origin_commit_seq", metadata.getLong("commit_seq")).put("origin_created_at", metadata.getString("created_at"))
                .put("committed_at", Instant.now().toString()).put("total_bytes", metadata.getString("total_bytes")).put("files", metadata.getJSONArray("files"))
            swap(game).publish(staging, metadata, index)
        } catch (error: Exception) {
            val completed = swap(game).recover() ?: repository.dao.fact("save-index:$game")?.value?.let(::JSONObject)?.optString("import_id")
            if (completed != operationId) throw error
        } finally {
            if (!File(parent, "import-journal.json").exists()) staging.deleteRecursively()
        }
    }
}
