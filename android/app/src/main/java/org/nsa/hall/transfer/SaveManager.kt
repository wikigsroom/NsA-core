package org.nsa.hall.transfer

import android.net.Uri
import android.system.Os
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject
import org.nsa.hall.data.HallRepository
import org.nsa.hall.data.objects
import org.nsa.hall.security.*
import java.io.File
import java.time.Instant
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

class SaveManager(private val repository: HallRepository) {
    private val maximum = 2L * 1024 * 1024 * 1024
    private suspend fun idle(game: String) { require(repository.dao.unfinished().none { it.gameId == game }) { "请先退出游戏，再管理存档" } }
    suspend fun createExport(game: String): File = repository.operationLock.withLock { createExportUnderOperationLock(game) }
    private suspend fun createExportUnderOperationLock(game: String): File {
        idle(game)
        val record = repository.dao.game(game) ?: error("游戏不存在")
        val install = repository.dao.install(game, record.activeRelease) ?: error("需要已安装的游戏版本")
        val manifest = JSONObject(install.manifest)
        val root = repository.saveRoot(game)
        val files = root.walkTopDown().filter { it.isFile }.toList()
        require(files.isNotEmpty() && files.size <= 10000) { "暂无可导出的游戏存档" }
        val entries = JSONArray(); var total = 0L
        files.forEach { file ->
            require(!java.nio.file.Files.isSymbolicLink(file.toPath()))
            val path = file.relativeTo(root).invariantSeparatorsPath; safeChild(root, path)
            total = Math.addExact(total, file.length()); require(total <= maximum)
            entries.put(JSONObject().put("path", path).put("bytes", file.length().toString()).put("sha256", sha256(file)))
        }
        val metadata = JSONObject().put("save_schema", 1).put("game_id", game).put("local_player_id", "local").put("source_release_id", record.activeRelease).put("compatibility_group", manifest.getJSONObject("save").getString("compatibility_group")).put("commit_seq", repository.dao.fact("save-seq:$game")?.value?.toLongOrNull() ?: 0).put("created_at", Instant.now().toString()).put("total_bytes", total.toString()).put("files", entries)
        val out = File(repository.context.cacheDir, "exports").apply { mkdirs() }.resolve("$game-${UUID.randomUUID()}.nsa-save.zip")
        ZipOutputStream(out.outputStream().buffered()).use { zip ->
            zip.putNextEntry(ZipEntry("backup.json")); zip.write(metadata.toString().toByteArray()); zip.closeEntry()
            files.forEach { file -> zip.putNextEntry(ZipEntry("files/" + file.relativeTo(root).invariantSeparatorsPath)); file.inputStream().use { it.copyTo(zip, 1024 * 1024) }; zip.closeEntry() }
        }
        require(out.length() <= maximum)
        return out
    }
    suspend fun exportTo(game: String, uri: Uri) {
        val archive = createExport(game)
        try { repository.context.contentResolver.openOutputStream(uri, "wt")!!.use { output -> archive.inputStream().use { it.copyTo(output, 1024 * 1024) } } }
        finally { archive.delete() }
    }
    suspend fun backupToLocal(game: String, reason: String): File = repository.operationLock.withLock { backupUnderOperationLock(game, reason) }
    /** The caller owns operationLock, which is intentionally non-reentrant. */
    internal suspend fun backupUnderOperationLock(game: String, reason: String): File {
        val file = createExportUnderOperationLock(game)
        val directory = File(repository.context.filesDir, "players/local/backups/${safeId(game)}").apply { mkdirs() }
        val target = File(directory, "${System.currentTimeMillis()}-${safeId(reason)}.nsa-save.zip")
        file.copyTo(target); file.delete()
        java.io.RandomAccessFile(target, "rw").use { it.fd.sync() }
        return target
    }
    suspend fun inspectImport(game: String, uri: Uri): Pair<File, JSONObject> {
        idle(game)
        val directory = File(repository.context.cacheDir, "save-import").apply { mkdirs() }
        val archive = File(directory, UUID.randomUUID().toString() + ".zip")
        var count = 0L
        try {
            repository.context.contentResolver.openInputStream(uri)!!.use { input -> archive.outputStream().use { output ->
                val buffer = ByteArray(1024 * 1024)
                while (true) { val n = input.read(buffer); if (n < 0) break; count = Math.addExact(count, n.toLong()); require(count <= maximum); output.write(buffer, 0, n) }
                output.fd.sync()
            } }
            rejectLinksAndSpecialEntries(archive)
            val metadata = ZipFile(archive).use { zip ->
                val entry = zip.getEntry("backup.json") ?: error("缺少存档备份清单")
                require(entry.size in 1..(8 * 1024 * 1024))
                strictObject(zip.getInputStream(entry).use { it.readBounded(8 * 1024 * 1024 + 1) })
            }
            require(metadata.getInt("save_schema") == 1 && metadata.getString("game_id") == game && metadata.getString("local_player_id") == "local") { "备份属于其他游戏或玩家" }
            val record = repository.dao.game(game) ?: error("游戏不存在")
            val install = repository.dao.install(game, record.activeRelease) ?: error("需要已安装的游戏版本")
            require(metadata.getString("compatibility_group") == JSONObject(install.manifest).getJSONObject("save").getString("compatibility_group")) { "存档格式与当前游戏不兼容" }
            require(checkedBytes(metadata.getString("total_bytes")) <= maximum && metadata.getJSONArray("files").length() in 1..10000)
            return archive to metadata
        } catch (error: Exception) { archive.delete(); throw error }
    }
    suspend fun importVerified(game: String, archive: File, metadata: JSONObject) = repository.operationLock.withLock {
        idle(game)
        val current = repository.dao.game(game) ?: error("游戏不存在")
        val install = repository.dao.install(game, current.activeRelease) ?: error("需要已安装的游戏版本")
        require(metadata.getInt("save_schema") == 1 && metadata.getString("game_id") == game && metadata.getString("local_player_id") == "local") { "备份属于其他游戏或玩家" }
        require(metadata.getString("compatibility_group") == JSONObject(install.manifest).getJSONObject("save").getString("compatibility_group")) { "当前安装版本已变化，请重新选择兼容的存档" }
        val committed = repository.saveRoot(game)
        val parent = committed.parentFile!!
        val staging = File(parent, "import-${UUID.randomUUID()}").apply { mkdirs() }
        val before = File(parent, "before-import")
        val journal = File(parent, "import-journal.json")
        try {
            val index = metadata.getJSONArray("files").objects()
            require(index.map { it.getString("path").lowercase() }.distinct().size == index.size)
            var total = 0L
            ZipFile(archive).use { zip ->
                require(zip.size() == index.size + 1)
                index.forEach { expected ->
                    val path = expected.getString("path"); val file = safeChild(staging, path); val bytes = checkedBytes(expected.getString("bytes")); val entry = zip.getEntry("files/$path") ?: error("备份文件缺失")
                    require(entry.size == bytes)
                    file.parentFile!!.mkdirs(); var expanded = 0L
                    zip.getInputStream(entry).use { input -> file.outputStream().use { output ->
                        val buffer = ByteArray(65536)
                        while (true) { val n = input.read(buffer); if (n < 0) break; expanded = Math.addExact(expanded, n.toLong()); require(expanded <= bytes); output.write(buffer, 0, n) }
                        output.fd.sync()
                    } }
                    require(expanded == bytes && sha256(file) == expected.getString("sha256")) { "备份文件校验失败" }
                    total = Math.addExact(total, bytes); require(total <= maximum)
                }
            }
            require(total == checkedBytes(metadata.getString("total_bytes")))
            if (committed.walkTopDown().any { it.isFile }) backupUnderOperationLock(game, "before-import")
            before.deleteRecursively()
            atomicJson(journal, JSONObject().put("staging", staging.name).put("phase", "verified"))
            Os.rename(committed.absolutePath, before.absolutePath); syncDirectory(parent)
            atomicJson(journal, JSONObject().put("staging", staging.name).put("phase", "old-moved"))
            Os.rename(staging.absolutePath, committed.absolutePath); syncDirectory(parent)
            atomicJson(journal, JSONObject().put("staging", staging.name).put("phase", "new-active"))
            before.deleteRecursively(); journal.delete(); syncDirectory(parent)
        } finally { staging.deleteRecursively(); archive.delete() }
    }
    fun recover(game: String) {
        val parent = File(repository.context.filesDir, "players/local/saves/${safeId(game)}")
        val journal = File(parent, "import-journal.json")
        if (!journal.isFile) return
        val state = strictObject(journal.readBytes()); val committed = File(parent, "committed"); val before = File(parent, "before-import")
        val staging = safeChild(parent, state.getString("staging"))
        if (!committed.exists() && before.exists()) Os.rename(before.absolutePath, committed.absolutePath)
        if (!committed.exists() && state.getString("phase") == "new-active" && staging.exists()) Os.rename(staging.absolutePath, committed.absolutePath)
        staging.deleteRecursively(); if (committed.exists()) before.deleteRecursively(); journal.delete()
    }
}
