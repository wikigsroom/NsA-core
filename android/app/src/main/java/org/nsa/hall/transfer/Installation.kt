package org.nsa.hall.transfer

import android.system.Os
import android.system.OsConstants
import androidx.room.withTransaction
import org.json.JSONObject
import org.nsa.hall.data.*
import org.nsa.hall.security.*
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.ZipFile

fun atomicJson(file: File, json: JSONObject) {
    file.parentFile!!.mkdirs()
    val temporary = File(file.parentFile, file.name + ".new")
    temporary.outputStream().use { it.write(json.toString().toByteArray()); it.fd.sync() }
    Os.rename(temporary.absolutePath, file.absolutePath)
    syncDirectory(file.parentFile!!)
}
fun syncDirectory(directory: File) { require(directory.isDirectory); val fd = Os.open(directory.absolutePath, OsConstants.O_RDONLY, 0); try { Os.fsync(fd) } finally { Os.close(fd) } }

class Installation(private val repository: HallRepository) {
    private val journals = File(repository.context.filesDir, "install-journals").apply { mkdirs() }
    suspend fun install(record: TransferRecord, archive: File, index: JSONObject, checkpoint: suspend () -> Unit) {
        val manifest = JSONObject(record.manifest)
        val target = repository.releaseRoot(record.gameId, record.releaseId)
        val staging = File(target.parentFile, ".${record.releaseId}.staging")
        val journalFile = File(journals, safeId(record.id) + ".json")
        val journal = JSONObject().put("game_id", record.gameId).put("release_id", record.releaseId).put("transfer_id", record.id).put("manifest", manifest).put("phase", "CREATED")
        atomicJson(journalFile, journal)
        if (staging.exists()) staging.deleteRecursively()
        staging.mkdirs()
        repository.dao.putTransfer((repository.dao.transfer(record.id) ?: record).copy(state = "installing"))
        rejectLinksAndSpecialEntries(archive)
        val entries = index.getJSONArray("files").objects()
        require(entries.size == manifest.getInt("file_count") && index.getInt("file_count") == entries.size && entries.size in 1..250000)
        val files = entries.associateBy { it.getString("path") }
        require(files.size == entries.size && files.keys.map { it.lowercase(java.util.Locale.ROOT) }.distinct().size == files.size)
        files.keys.forEach { name -> safeChild(staging, name); require(!name.endsWith(".so", true) && !name.endsWith(".apk", true)) { "资源包不可携带外部运行组件" } }
        var total = 0L
        ZipFile(archive).use { zip ->
            val seen = mutableSetOf<String>()
            val iterator = zip.entries()
            while (iterator.hasMoreElements()) {
                checkpoint()
                val entry = iterator.nextElement(); val expected = files[entry.name] ?: error("发现未登记的文件")
                require(seen.add(entry.name) && !entry.isDirectory && entry.method in listOf(0, 8))
                val bytes = checkedBytes(expected.getString("bytes")); require(bytes == entry.size)
                require(repository.availableBytes() >= repository.additionalBytes(manifest, record.total, total)) { "安装空间不足" }
                val targetFile = safeChild(staging, entry.name); targetFile.parentFile!!.mkdirs()
                var count = 0L
                zip.getInputStream(entry).use { input -> targetFile.outputStream().use { output ->
                    val buffer = ByteArray(1024 * 1024)
                    while (true) { val n = input.read(buffer); if (n < 0) break; count = Math.addExact(count, n.toLong()); require(count <= bytes); output.write(buffer, 0, n) }
                    output.fd.sync()
                } }
                require(count == bytes && sha256(targetFile) == expected.getString("sha256")) { "展开文件校验失败" }
                total = Math.addExact(total, count)
            }
            require(seen == files.keys)
        }
        require(total == checkedBytes(manifest.getString("unpacked_bytes")) && total == checkedBytes(index.getString("unpacked_bytes")))
        atomicJson(journalFile, journal.put("phase", "EXTRACTED"))
        atomicJson(File(staging, ".file-index.json"), index)
        atomicJson(File(staging, ".ready.json"), JSONObject().put("release_id", record.releaseId).put("manifest_sha256", manifest.getString("_verified_sha256")).put("file_count", entries.size))
        syncDirectory(staging)
        atomicJson(journalFile, journal.put("phase", "VERIFIED"))
        checkpoint()
        val current = repository.dao.game(record.gameId)
        if (current != null && current.activeRelease.isNotEmpty() && current.activeRelease != record.releaseId && repository.saveRoot(record.gameId).walkTopDown().any { it.isFile }) {
            SaveManager(repository).backupUnderOperationLock(record.gameId, "before-upgrade")
            val old = repository.dao.install(record.gameId, current.activeRelease) ?: error("旧版本记录不完整")
            val oldSave = JSONObject(old.manifest).getJSONObject("save")
            val newSave = manifest.getJSONObject("save")
            require(oldSave.getString("compatibility_group") == newSave.getString("compatibility_group") || current.activeRelease in newSave.getJSONArray("compatible_previous_releases").strings()) { "更新未声明存档兼容，请保留原版本" }
        }
        target.parentFile!!.mkdirs()
        if (target.exists()) {
            require(File(target, ".ready.json").readText().let(::JSONObject).getString("manifest_sha256") == manifest.getString("_verified_sha256")) { "禁止覆盖同版本的不同资源" }
            val identical = entries.all { expected -> val oldFile = safeChild(target, expected.getString("path")); oldFile.isFile && oldFile.length() == checkedBytes(expected.getString("bytes")) && sha256(oldFile) == expected.getString("sha256") }
            if (identical) staging.deleteRecursively() else {
                val replaced = File(target.parentFile, ".${record.releaseId}.replaced")
                replaced.deleteRecursively()
                Os.rename(target.absolutePath, replaced.absolutePath); syncDirectory(target.parentFile!!)
                Os.rename(staging.absolutePath, target.absolutePath); syncDirectory(target.parentFile!!)
                replaced.deleteRecursively()
            }
        } else Os.rename(staging.absolutePath, target.absolutePath)
        syncDirectory(target.parentFile!!)
        atomicJson(journalFile, journal.put("phase", "DIRECTORY_PUBLISHED"))
        activate(record.gameId, record.releaseId, manifest)
        atomicJson(journalFile, journal.put("phase", "DB_ACTIVATED"))
        atomicJson(journalFile, journal.put("phase", "CLEANED"))
        journalFile.delete(); syncDirectory(journals)
    }
    private suspend fun activate(gameId: String, releaseId: String, manifest: JSONObject) {
        repository.database.withTransaction {
            repository.dao.putInstall(InstallRecord(gameId, releaseId, manifest.toString()))
            val game = repository.dao.game(gameId) ?: error("游戏资料不存在")
            repository.dao.putGame(game.copy(activeRelease = releaseId))
        }
    }
    suspend fun recover() {
        journals.listFiles { file -> file.extension == "json" }?.forEach { file ->
            val journal = strictObject(file.readBytes())
            val game = safeId(journal.getString("game_id")); val release = safeId(journal.getString("release_id")); val phase = journal.getString("phase")
            val target = repository.releaseRoot(game, release)
            val staging = File(target.parentFile, ".$release.staging")
            val replaced = File(target.parentFile, ".$release.replaced")
            if (!target.exists() && replaced.exists()) {
                if (staging.exists() && File(staging, ".ready.json").isFile) Os.rename(staging.absolutePath, target.absolutePath)
                else Os.rename(replaced.absolutePath, target.absolutePath)
                syncDirectory(target.parentFile!!)
            }
            if (phase in listOf("DIRECTORY_PUBLISHED", "DB_ACTIVATED", "CLEANED") || (phase == "VERIFIED" && target.exists())) {
                val ready = File(target, ".ready.json")
                require(ready.isFile && JSONObject(ready.readText()).getString("manifest_sha256") == journal.getJSONObject("manifest").getString("_verified_sha256"))
                activate(game, release, journal.getJSONObject("manifest"))
                val transfer = repository.dao.transfer(journal.getString("transfer_id"))
                if (transfer != null) repository.dao.putTransfer(transfer.copy(state = "completed", bytes = transfer.total))
            } else staging.deleteRecursively()
            if (target.exists()) replaced.deleteRecursively()
            file.delete()
        }
    }
    suspend fun repair(gameId: String): List<String> {
        require(repository.dao.unfinished().none { it.gameId == gameId }) { "请先退出游戏" }
        val game = repository.dao.game(gameId) ?: error("游戏不存在")
        val root = repository.releaseRoot(gameId, game.activeRelease)
        val index = strictObject(File(root, ".file-index.json").readBytes())
        return index.getJSONArray("files").objects().mapNotNull { expected -> val path = safeChild(root, expected.getString("path")); if (!path.isFile || path.length() != checkedBytes(expected.getString("bytes")) || sha256(path) != expected.getString("sha256")) expected.getString("path") else null }
    }
}

/** Java ZipFile omits UNIX mode flags; inspect the central directory before extraction. */
fun rejectLinksAndSpecialEntries(archive: File) {
    RandomAccessFile(archive, "r").use { file ->
        val tailSize = minOf(file.length(), 65557L).toInt(); val tail = ByteArray(tailSize); file.seek(file.length() - tailSize); file.readFully(tail)
        val bytes = ByteBuffer.wrap(tail).order(ByteOrder.LITTLE_ENDIAN)
        val eocd = (tailSize - 22 downTo 0).firstOrNull { bytes.getInt(it) == 0x06054b50 && it + 22 + (bytes.getShort(it + 20).toInt() and 65535) == tailSize } ?: error("ZIP 目录缺失")
        require(bytes.getShort(eocd + 4).toInt() == 0 && bytes.getShort(eocd + 6).toInt() == 0)
        var offset = bytes.getInt(eocd + 16).toLong() and 0xffffffffL
        var entries = bytes.getShort(eocd + 10).toLong() and 65535L
        if (offset == 0xffffffffL || entries == 65535L) {
            val locator = ByteArray(20); file.seek(file.length() - tailSize + eocd - 20); file.readFully(locator)
            val l = ByteBuffer.wrap(locator).order(ByteOrder.LITTLE_ENDIAN); require(l.int == 0x07064b50 && l.int == 0)
            val position = l.long; require(l.int == 1)
            val record = ByteArray(56); file.seek(position); file.readFully(record); val z = ByteBuffer.wrap(record).order(ByteOrder.LITTLE_ENDIAN)
            require(z.getInt(0) == 0x06064b50 && z.getInt(16) == 0 && z.getInt(20) == 0)
            entries = z.getLong(32); offset = z.getLong(48)
        }
        require(entries in 1..250000 && offset in 0 until file.length())
        file.seek(offset)
        repeat(entries.toInt()) {
            val header = ByteArray(46); file.readFully(header); val h = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN)
            require(h.int == 0x02014b50 && h.getShort(8).toInt() and 1 == 0)
            val mode = (h.getInt(38) ushr 16) and 0xf000
            require(mode == 0 || mode == 0x8000) { "ZIP 包含链接或特殊文件" }
            val skip = (h.getShort(28).toInt() and 65535) + (h.getShort(30).toInt() and 65535) + (h.getShort(32).toInt() and 65535)
            file.seek(Math.addExact(file.filePointer, skip.toLong()))
        }
    }
}
