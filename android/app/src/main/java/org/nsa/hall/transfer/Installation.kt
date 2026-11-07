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
import java.nio.file.Files
import java.util.zip.ZipFile
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.withLock

fun atomicJson(file: File, json: JSONObject) {
    durableMkdirs(file.parentFile!!, ::syncDirectory)
    val temporary = File(file.parentFile, file.name + ".new")
    temporary.outputStream().use { it.write(json.toString().toByteArray()); it.fd.sync() }
    Os.rename(temporary.absolutePath, file.absolutePath)
    syncDirectory(file.parentFile!!)
}
fun syncDirectory(directory: File) {
    require(directory.isDirectory && !Files.isSymbolicLink(directory.toPath()))
    val fd = Os.open(directory.absolutePath, OsConstants.O_RDONLY or OsConstants.O_DIRECTORY or OsConstants.O_NOFOLLOW, 0)
    try { Os.fsync(fd) } finally { Os.close(fd) }
}

/** Never traverses an external tree; the transfer lifetime owner has closed its writers. */
fun removeOwnedTree(file: File) {
    deletePrivateTree(file)
    syncDirectory(file.parentFile!!)
}

class Installation(private val repository: HallRepository) {
    private val journals = File(repository.context.filesDir, "install-journals").also { durableMkdirs(it, ::syncDirectory) }
    private fun journalFile(id: String) = File(journals, safeId(id) + ".json")
    private suspend fun validateJournal(journal: JSONObject, id: String) {
        require(journal.getString("transfer_id") == id)
        require(journal.getString("phase") in listOf("CREATED", "EXTRACTED", "VERIFIED", "DIRECTORY_PUBLISHED", "DB_ACTIVATED", "CLEANED"))
        val row = repository.dao.transfer(id) ?: error("缺少资源任务，已保留恢复记录")
        require(journal.getString("game_id") == row.gameId && journal.getString("release_id") == row.releaseId)
        require(journal.getJSONObject("manifest").getString("_verified_sha256") == JSONObject(row.manifest).getString("_verified_sha256"))
        paths(journal)
    }
    private fun paths(journal: JSONObject): Triple<File, File, File> {
        val target = repository.releaseRoot(journal.getString("game_id"), journal.getString("release_id"))
        val release = safeId(journal.getString("release_id")); val id = safeId(journal.getString("transfer_id"))
        val suffix = sha256(id.toByteArray()).take(16)
        val stageName = if (journal.optInt("journal_schema") == 2) ".$release.staging-$suffix" else ".$release.staging"
        val oldName = if (journal.optInt("journal_schema") == 2) ".$release.replaced-$suffix" else ".$release.replaced"
        return Triple(target, File(target.parentFile, stageName), File(target.parentFile, oldName))
    }
    private fun valid(root: File, journal: JSONObject) = StorageReconciliation(repository).installationProblem(
        InstallRecord(journal.getString("game_id"), journal.getString("release_id"), journal.getJSONObject("manifest").toString()), root, true) == null
    private fun cleanupDownload(id: String) = removeOwnedTree(File(repository.transferRoot, safeId(id)))
    private suspend fun warning(id: String, error: String) {
        repository.dao.putFact(Fact("install-recovery:$id", error))
        repository.dao.transferNotice(id, error)
        refreshWarning()
    }
    private suspend fun clearWarning(id: String) { repository.dao.deleteFact("install-recovery:$id"); refreshWarning() }
    private suspend fun refreshWarning() {
        val count = repository.dao.installRecoveryFacts().size
        repository.recoveryWarning.value = if (count == 0) "" else "$count 项资源操作待恢复或清理；其他完整游戏仍可使用，请到下载中心重试。"
    }
    private fun committer(file: File, journal: JSONObject): InstallCommit {
        val (target, staging, replaced) = paths(journal)
        return InstallCommit(file, journal, target, staging, replaced, ::syncDirectory,
            { from, to -> Os.rename(from.absolutePath, to.absolutePath) }, ::atomicJson,
            { valid(it, journal) },
            { activate(journal.getString("game_id"), journal.getString("release_id"), journal.getJSONObject("manifest")) },
            { repository.dao.completeTransfer(journal.getString("transfer_id")) },
            {
                cleanupDownload(journal.getString("transfer_id"))
                val previous = File(journals, safeId(journal.getString("transfer_id")) + ".previous")
                if (previous.exists()) { require(previous.delete()); syncDirectory(journals) }
            })
    }
    private suspend fun prepareActivation(file: File, journal: JSONObject) {
        val gameId = journal.getString("game_id"); val releaseId = journal.getString("release_id")
        val current = repository.dao.game(gameId) ?: error("游戏资料不存在")
        if (current.activeRelease.isEmpty() || current.activeRelease == releaseId || !repository.saveRoot(gameId).walkTopDown().any { it.isFile }) return
        val old = repository.dao.install(gameId, current.activeRelease) ?: error("旧版本记录不完整")
        val oldSave = JSONObject(old.manifest).getJSONObject("save"); val newSave = journal.getJSONObject("manifest").getJSONObject("save")
        require(oldSave.getString("compatibility_group") == newSave.getString("compatibility_group") || current.activeRelease in newSave.getJSONArray("compatible_previous_releases").strings()) { "更新未声明存档兼容，请保留原版本" }
        if (journal.optString("save_backup_for") != current.activeRelease) {
            SaveManager(repository).backupUnderOperationLock(gameId, "before-upgrade")
            atomicJson(file, journal.put("save_backup_for", current.activeRelease))
        }
    }
    private suspend fun finish(file: File, journal: JSONObject, allowActivation: Boolean, recordFailure: Boolean = true): Boolean = try {
        if (journal.getString("phase") in listOf("VERIFIED", "DIRECTORY_PUBLISHED")) {
            repository.dao.install(journal.getString("game_id"), journal.getString("release_id"))?.let { old ->
                require(JSONObject(old.manifest).getString("_verified_sha256") == journal.getJSONObject("manifest").getString("_verified_sha256")) { "同版本资源身份不一致，已保留旧版" }
            }
        }
        if (allowActivation && journal.getString("phase") in listOf("VERIFIED", "DIRECTORY_PUBLISHED")) prepareActivation(file, journal)
        val completed = committer(file, journal).recover(allowActivation)
        if (completed) clearWarning(journal.getString("transfer_id"))
        completed
    } catch (error: CancellationException) { throw error }
    catch (error: Exception) {
        val id = journal.getString("transfer_id")
        val message = if (journal.getString("phase") in listOf("DB_ACTIVATED", "CLEANED")) "已安装；临时文件待清理，请重试" else error.message ?: "此游戏资源待恢复，请重试"
        warning(id, message)
        if (recordFailure && journal.getString("phase") !in listOf("DB_ACTIVATED", "CLEANED")) repository.dao.transferFailure(id, message, true)
        false
    }

    /** Extraction and publication are called under operationLock and TransferLease. */
    suspend fun install(record: TransferRecord, archive: File, index: JSONObject, checkpoint: suspend () -> Unit) {
        checkpoint()
        val manifest = JSONObject(record.manifest)
        repository.dao.install(record.gameId, record.releaseId)?.let { old ->
            require(JSONObject(old.manifest).getString("_verified_sha256") == manifest.getString("_verified_sha256")) { "禁止覆盖同版本的不同资源" }
        }
        val file = journalFile(record.id)
        var previous: JSONObject? = null
        if (file.exists()) {
            previous = runCatching { strictObject(file.readBytes()).also { validateJournal(it, record.id) } }.getOrNull()
            if (previous != null && !previous.optBoolean("abandoned") && finish(file, previous, true, false)) return
            if (previous?.getString("phase") in listOf("DB_ACTIVATED", "CLEANED")) return
            // An explicit retry may replace its own unfinished draft, retaining
            // the previous recovery record and all target/replaced directories.
            val history = File(journals, safeId(record.id) + ".previous")
            Os.rename(file.absolutePath, history.absolutePath); syncDirectory(journals)
        }
        val current = repository.dao.game(record.gameId)
        val journal = JSONObject().put("journal_schema", if (previous != null) previous.optInt("journal_schema", 1) else 2).put("game_id", record.gameId).put("release_id", record.releaseId)
            .put("transfer_id", record.id).put("manifest", manifest).put("phase", "CREATED")
            .put("previous_active_release_id", previous?.optString("previous_active_release_id")?.takeIf { it.isNotEmpty() } ?: current?.activeRelease.orEmpty())
        val (target, staging, _) = paths(journal)
        durableMkdirs(target.parentFile!!, ::syncDirectory)
        syncAncestors(target.parentFile!!, repository.context.filesDir, ::syncDirectory)
        atomicJson(file, journal)
        if (staging.exists()) removeOwnedTree(staging)
        durableMkdirs(staging, ::syncDirectory)
        require(repository.dao.transferProgress(record.id, "installing", record.total) == 1) { "下载意图已改变" }
        rejectLinksAndSpecialEntries(archive)
        val entries = index.getJSONArray("files").objects()
        require(entries.size == manifest.getInt("file_count") && index.getInt("file_count") == entries.size && entries.size in 1..250000)
        val files = entries.associateBy { it.getString("path") }
        require(files.size == entries.size && files.keys.map { it.lowercase(java.util.Locale.ROOT) }.distinct().size == files.size)
        files.keys.forEach { name ->
            safeChild(staging, name)
            require(name !in listOf(".ready.json", ".file-index.json") && !name.endsWith(".so", true) && !name.endsWith(".apk", true)) { "资源包不可携带运行组件或覆盖安装记录" }
        }
        var total = 0L
        ZipFile(archive).use { zip ->
            val seen = mutableSetOf<String>(); val iterator = zip.entries()
            while (iterator.hasMoreElements()) {
                checkpoint()
                val entry = iterator.nextElement(); val expected = files[entry.name] ?: error("发现未登记的文件")
                require(seen.add(entry.name) && !entry.isDirectory && entry.method in listOf(0, 8))
                val bytes = checkedBytes(expected.getString("bytes")); require(bytes == entry.size)
                require(repository.availableBytes() >= repository.additionalBytes(manifest, record.total, total)) { "安装空间不足" }
                val outputFile = safeChild(staging, entry.name); outputFile.parentFile!!.mkdirs()
                var count = 0L
                zip.getInputStream(entry).use { input -> outputFile.outputStream().use { output ->
                    val buffer = ByteArray(1024 * 1024)
                    while (true) {
                        checkpoint()
                        val n = input.read(buffer); if (n < 0) break
                        count = Math.addExact(count, n.toLong()); require(count <= bytes)
                        require(Math.addExact(total, count) <= checkedBytes(manifest.getString("unpacked_bytes")))
                        output.write(buffer, 0, n)
                    }
                    output.fd.sync()
                } }
                require(count == bytes && sha256(outputFile) == expected.getString("sha256")) { "展开文件校验失败" }
                total = Math.addExact(total, count)
            }
            require(seen == files.keys)
        }
        require(total == checkedBytes(manifest.getString("unpacked_bytes")) && total == checkedBytes(index.getString("unpacked_bytes")))
        atomicJson(file, journal.put("phase", "EXTRACTED"))
        val rawIndex = File(File(repository.transferRoot, safeId(record.id)), "file_index.json")
        val expectedIndex = manifest.getJSONObject("file_index")
        require(rawIndex.length() == checkedBytes(expectedIndex.getString("bytes")) && sha256(rawIndex) == expectedIndex.getString("sha256"))
        rawIndex.inputStream().use { input -> File(staging, ".file-index.json").outputStream().use { output -> input.copyTo(output); output.fd.sync() } }
        atomicJson(File(staging, ".ready.json"), JSONObject().put("release_id", record.releaseId).put("manifest_sha256", manifest.getString("_verified_sha256")).put("file_count", entries.size))
        syncDirectoryTree(staging, ::syncDirectory)
        syncAncestors(staging.parentFile!!, repository.context.filesDir, ::syncDirectory)
        prepareActivation(file, journal)
        atomicJson(file, journal.put("phase", "VERIFIED"))
        checkpoint()
        repository.dao.install(record.gameId, record.releaseId)?.let { old ->
            require(JSONObject(old.manifest).getString("_verified_sha256") == manifest.getString("_verified_sha256")) { "禁止覆盖同版本的不同资源" }
        }
        finish(file, journal, true)
    }
    private suspend fun activate(gameId: String, releaseId: String, manifest: JSONObject) {
        require(StorageReconciliation(repository).installationProblem(InstallRecord(gameId, releaseId, manifest.toString())) == null)
        repository.database.withTransaction {
            val existing = repository.dao.install(gameId, releaseId)
            if (existing != null) require(JSONObject(existing.manifest).getString("_verified_sha256") == manifest.getString("_verified_sha256"))
            repository.dao.putInstall(existing?.copy(state = "ready", manifest = manifest.toString()) ?: InstallRecord(gameId, releaseId, manifest.toString()))
            val game = repository.dao.game(gameId) ?: error("游戏资料不存在")
            repository.dao.putGame(game.copy(activeRelease = releaseId))
        }
    }
    suspend fun recover() {
        journals.listFiles { file -> file.extension == "json" }?.sortedBy { it.name }?.forEach { file ->
            try {
                val journal = strictObject(file.readBytes())
                val id = safeId(journal.getString("transfer_id")); require(file.nameWithoutExtension == id)
                validateJournal(journal, id)
                val row = repository.dao.transfer(id)
                if (journal.optBoolean("abandoned")) { abandon(file, journal); return@forEach }
                if (row?.intent == "cancel") { cancel(id); return@forEach }
                if (row?.intent == "pause" && journal.getString("phase") in listOf("CREATED", "EXTRACTED", "VERIFIED")) return@forEach
                if (row?.state == "failed" && journal.getString("phase") !in listOf("DB_ACTIVATED", "CLEANED")) return@forEach
                val allowed = row?.ownerGameId == row?.gameId && row != null || repository.dao.game(journal.getString("game_id"))?.activeRelease == journal.getString("release_id")
                finish(file, journal, allowed)
            } catch (error: CancellationException) { throw error }
            catch (_: Exception) { warning(runCatching { safeId(file.nameWithoutExtension) }.getOrElse { "invalid-" + sha256(file.name.toByteArray()).take(16) }, "此资源恢复记录待处理；其他完整游戏可继续使用") }
        }
        // Backfill cleanup lost by the legacy completed/cancelled paths.
        val journalIds = journals.listFiles { f -> f.extension in listOf("json", "invalid") }.orEmpty().map { it.nameWithoutExtension }.toSet()
        repository.dao.allTransfers().filter { it.state in listOf("completed", "cancelled") && it.id !in journalIds }.forEach { row ->
            try { cleanupDownload(row.id); clearWarning(row.id); if (row.error.contains("待清理")) repository.dao.transferNotice(row.id, "") }
            catch (_: Exception) { warning(row.id, "临时文件待清理，请重试") }
        }
        refreshWarning()
    }
    private suspend fun abandon(file: File, journal: JSONObject) {
        val (target, staging, replaced) = paths(journal)
        atomicJson(file, journal.put("abandoned", true))
        cleanupDownload(journal.getString("transfer_id"))
        if (staging.exists()) removeOwnedTree(staging)
        // A sole old copy is kept until a verified target exists, even after cancel.
        if (replaced.exists()) {
            require(valid(target, journal)) { "旧资源副本仍保留，等待修复后清理" }
            removeOwnedTree(replaced)
        }
        require(file.delete()); syncDirectory(journals)
        clearWarning(journal.getString("transfer_id"))
    }
    suspend fun cancel(id: String) {
        val file = journalFile(id)
        if (file.exists()) {
            val journal = runCatching { strictObject(file.readBytes()).also { validateJournal(it, id) } }.getOrNull()
            if (journal == null) {
                // Unknown old directories may be the only surviving copy. Keep
                // the damaged record, clean only this known transfer workspace.
                cleanupDownload(id)
                Os.rename(file.absolutePath, File(journals, safeId(id) + ".invalid").absolutePath); syncDirectory(journals)
                warning(id, "已取消；损坏的恢复记录与可能的旧资源副本仍保留，可重新下载修复")
                repository.dao.acknowledgeCancellation(id)
                return
            }
            val (target, staging, _) = paths(journal)
            val phase = journal.getString("phase")
            val row = repository.dao.transfer(id)
            val ownsPublication = row?.ownerGameId == row?.gameId && row != null || repository.dao.game(journal.getString("game_id"))?.activeRelease == journal.getString("release_id")
            if (ownsPublication && !journal.optBoolean("abandoned") && (phase in listOf("DIRECTORY_PUBLISHED", "DB_ACTIVATED", "CLEANED") || phase == "VERIFIED" && !staging.exists() && valid(target, journal))) {
                if (finish(file, journal, true) || repository.dao.transfer(id)?.state == "completed") return
            }
            try { abandon(file, journal) }
            catch (error: Exception) {
                // Mark abandonment durably before releasing this game's ownership.
                atomicJson(file, journal.put("abandoned", true))
                warning(id, error.message ?: "取消后的临时文件待清理")
                if (File(repository.transferRoot, safeId(id)).exists() || staging.exists()) return
            }
        } else cleanupDownload(id)
        repository.dao.acknowledgeCancellation(id)
    }
    fun hasPending(game: String): Boolean = journals.listFiles { f -> f.extension == "json" }.orEmpty().any { file ->
        runCatching { strictObject(file.readBytes()).getString("game_id") == game }.getOrDefault(true)
    }
    suspend fun repair(gameId: String): List<String> = repository.operationLock.withLock {
        repository.requireIdle(gameId)
        val game = repository.dao.game(gameId) ?: error("游戏不存在")
        val install = repository.dao.install(gameId, game.activeRelease) ?: error("安装记录缺失")
        val root = repository.releaseRoot(gameId, game.activeRelease)
        val problem = StorageReconciliation(repository).installationProblem(install)
        if (problem != null) { repository.dao.putInstall(install.copy(state = "repair_required", healthy = false)); return@withLock listOf(problem) }
        val index = strictObject(File(root, ".file-index.json").readBytes())
        val bad = index.getJSONArray("files").objects().mapNotNull { expected -> val path = safeChild(root, expected.getString("path")); if (!path.isFile || path.length() != checkedBytes(expected.getString("bytes")) || sha256(path) != expected.getString("sha256")) expected.getString("path") else null }
        repository.dao.putInstall(install.copy(state = if (bad.isEmpty()) "ready" else "repair_required", healthy = install.healthy && bad.isEmpty()))
        bad
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
