package org.nsa.hall.transfer

import android.content.Intent
import android.net.Uri
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject
import org.nsa.hall.data.Fact
import org.nsa.hall.data.HallRepository
import org.nsa.hall.security.safeChild
import org.nsa.hall.security.safeId
import org.nsa.hall.security.sha256
import java.io.File
import java.util.UUID

/** Only small ownership facts go in Room; archive bytes stay in private files. */
class SaveExchange(private val repository: HallRepository) {
    private val lock = Mutex()
    private val saves = SaveManager(repository)
    private suspend fun pending() = repository.dao.fact("pending-save")?.value?.let(::JSONObject)
    private suspend fun store(value: JSONObject) { repository.dao.putFact(Fact("pending-save", value.toString())) }
    private fun file(value: JSONObject): File = safeChild(saves.exchangeRoot, value.getString("file"))
    private suspend fun failed(value: JSONObject, error: Exception) {
        store(value.put("stage", "failed").put("error", error.message ?: "存档交换尚未完成，可重试"))
    }
    private fun releaseUri(value: JSONObject) {
        value.optString("uri").takeIf { it.isNotBlank() }?.let { raw -> runCatching { repository.context.contentResolver.releasePersistableUriPermission(Uri.parse(raw), Intent.FLAG_GRANT_READ_URI_PERMISSION) } }
    }
    private suspend fun clear(value: JSONObject) {
        releaseUri(value)
        if (value.has("file")) { val owned = file(value); require(!owned.exists() || owned.delete()) { "备份草稿尚未清理，请重试" } }
        repository.dao.deleteFact("pending-save")
    }
    suspend fun begin(game: String, kind: String, source: String) = lock.withLock {
        require(kind in listOf("import", "export")); require(pending() == null) { "请先完成或取消当前存档操作" }
        repository.requireIdle(game)
        val value = JSONObject().put("id", UUID.randomUUID().toString()).put("game_id", safeId(game)).put("kind", kind).put("source", source).put("stage", "preparing")
        if (kind == "export") value.put("file", "export-${value.getString("id")}.nsa-save.zip")
        store(value)
        try {
            if (kind == "export") {
                val archive = saves.createExport(game, value.getString("id"))
                value.put("file", archive.name).put("sha256", sha256(archive)).put("bytes", archive.length().toString())
            }
            store(value.put("stage", "picker"))
        } catch (error: Exception) { failed(value, error); throw error }
    }
    suspend fun claimPicker(id: String): String? = lock.withLock {
        val value = pending() ?: return@withLock null
        if (value.getString("id") != id || value.getString("stage") != "picker") return@withLock null
        store(value.put("stage", "awaiting-result")); value.getString("kind")
    }
    suspend fun result(kind: String, uri: Uri?, operationId: String): Boolean = lock.withLock {
        val value = pending() ?: return@withLock false
        if (value.getString("kind") != kind || value.getString("id") != operationId) return@withLock false
        if (value.getString("stage") !in listOf("awaiting-result", "failed")) return@withLock false
        if (uri == null) { clear(value); return@withLock false }
        try {
            if (kind == "export") {
                val archive = file(value)
                require(archive.isFile && archive.length().toString() == value.getString("bytes") && sha256(archive) == value.getString("sha256")) { "导出快照已失效，请重新创建" }
                store(value.put("stage", "writing"))
                repository.context.contentResolver.openOutputStream(uri, "wt")!!.use { output -> archive.inputStream().use { it.copyTo(output, 65536) }; output.flush() }
                clear(value)
            } else {
                value.put("uri", uri.toString()).put("file", "import-${value.getString("id")}.zip").put("stage", "copying")
                runCatching { repository.context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
                store(value); copyAndInspect(value)
            }
        } catch (error: Exception) { failed(value, error); throw error }
        true
    }
    private suspend fun copyAndInspect(value: JSONObject) {
        val archive = file(value)
        if (!archive.isFile) saves.copyImport(Uri.parse(value.getString("uri")), archive)
        val metadata = saves.inspect(value.getString("game_id"), archive)
        value.put("sha256", sha256(archive)).put("bytes", archive.length().toString()).put("source_release_id", metadata.getString("source_release_id"))
            .put("expanded_bytes", metadata.getString("total_bytes")).put("stage", "confirm").remove("error")
        store(value); releaseUri(value)
    }
    suspend fun confirm(id: String) = lock.withLock {
        val value = pending() ?: return@withLock
        require(value.getString("id") == id && value.getString("kind") == "import" && value.getString("stage") in listOf("confirm", "failed"))
        val archive = file(value)
        try {
            require(archive.isFile && archive.length().toString() == value.getString("bytes") && sha256(archive) == value.getString("sha256")) { "备份草稿已变化，请取消后重新选择" }
            store(value.put("stage", "applying"))
            saves.importVerified(value.getString("game_id"), archive, id)
            clear(value)
        } catch (error: Exception) { failed(value, error); throw error }
    }
    suspend fun cancel(id: String) = lock.withLock {
        val value = pending() ?: return@withLock
        require(value.getString("id") == id && value.getString("stage") !in listOf("applying", "copying", "writing", "preparing")) { "正在处理存档，请等待完成" }
        clear(value)
    }
    suspend fun retry(id: String) = lock.withLock {
        val value = pending() ?: return@withLock
        require(value.getString("id") == id && value.getString("stage") == "failed")
        try {
            when {
                value.getString("kind") == "import" && value.has("file") && file(value).isFile -> copyAndInspect(value)
                value.getString("kind") == "import" -> store(value.put("stage", "picker"))
                value.has("file") && file(value).isFile -> store(value.put("stage", "picker"))
                else -> {
                    val archive = saves.createExport(value.getString("game_id"), value.getString("id"))
                    store(value.put("file", archive.name).put("bytes", archive.length().toString()).put("sha256", sha256(archive)).put("stage", "picker"))
                }
            }
        } catch (error: Exception) { failed(value, error); throw error }
    }
    suspend fun recover() = lock.withLock {
        val value = pending()
        // Temporary exports used by automatic backups have no pending picker.
        // Serialize cleanup with the worker's snapshot writer as well.
        repository.operationLock.withLock {
            val keep = value?.optString("file")
            saves.exchangeRoot.listFiles().orEmpty().filter { !it.isDirectory && it.name != keep }.forEach { require(it.delete()) { "中断草稿清理未完成，请重试" } }
        }
        if (value == null) return@withLock
        if (value.getString("stage") == "preparing" && value.has("file")) {
            val partial = file(value); require(!partial.exists() || partial.delete()) { "中断导出尚未清理，请重试" }
        }
        if (value.getString("stage") == "confirm") {
            try {
                require(file(value).isFile && file(value).length().toString() == value.getString("bytes")) { "备份草稿已丢失，请取消后重新选择" }
                saves.inspect(value.getString("game_id"), file(value))
            } catch (error: Exception) { failed(value, error) }
            return@withLock
        }
        if (value.getString("stage") == "applying") {
            val game = value.getString("game_id")
            val index = repository.dao.fact("save-index:$game")?.value?.let(::JSONObject)
            if (index?.optString("import_id") == value.getString("id")) { clear(value); return@withLock }
        }
        if (value.getString("stage") == "copying") {
            try { copyAndInspect(value); return@withLock } catch (error: CancellationException) { throw error } catch (error: Exception) { failed(value, error); return@withLock }
        }
        if (value.getString("stage") in listOf("preparing", "applying", "writing", "awaiting-result")) failed(value, IllegalStateException("上次存档操作已中断；原存档由恢复记录保护，可重试或取消草稿"))
    }
}
