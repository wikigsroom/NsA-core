package org.nsa.hall.transfer

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject
import org.nsa.hall.HallApplication
import org.nsa.hall.MainActivity
import org.nsa.hall.R
import org.nsa.hall.data.*
import org.nsa.hall.security.*
import org.nsa.hall.runtime.RuntimeLease
import java.io.File
import java.io.RandomAccessFile
import java.util.concurrent.CancellationException

class TransferWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    private val repository = (context.applicationContext as HallApplication).repository
    private class Interrupted : Exception()
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try { repository.initialize() } catch (error: CancellationException) { throw error } catch (_: Exception) { return@withContext Result.retry() }
        setForeground(notification("准备游戏下载"))
        val candidates = repository.dao.allTransfers().filter { it.intent == "run" && it.state !in listOf("completed", "cancelled", "failed") }
        var retry = false
        for (candidate in candidates) {
            if (isStopped) return@withContext Result.retry()
            try { perform(candidate.id) }
            catch (_: Interrupted) { retry = true }
            catch (error: CancellationException) { throw error }
            catch (error: Exception) {
                val latest = repository.dao.transfer(candidate.id) ?: continue
                if (latest.intent != "run") continue
                val failures = latest.failures + 1
                repository.dao.putTransfer(latest.copy(state = if (failures >= 5) "failed" else "waiting", failures = failures, error = error.message ?: "下载失败，可重试"))
                if (failures < 5) { delay(minOf(60000L, 2000L shl (failures - 1))); retry = true }
            }
        }
        if (retry) Result.retry() else Result.success()
    }
    private fun notification(text: String): ForegroundInfo {
        val open = PendingIntent.getActivity(applicationContext, 0, Intent(applicationContext, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val notification = NotificationCompat.Builder(applicationContext, "downloads").setSmallIcon(R.drawable.ic_launcher).setContentTitle("NsA · 下载中心").setContentText(text).setContentIntent(open).setOngoing(true).setOnlyAlertOnce(true).build()
        return ForegroundInfo(1001, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
    }
    private suspend fun checkpoint(id: String): TransferRecord {
        val row = repository.dao.transfer(id) ?: throw Interrupted()
        if (isStopped || row.intent != "run") {
            if (row.intent == "cancel") File(repository.transferRoot, safeId(id)).deleteRecursively()
            throw Interrupted()
        }
        val manager = applicationContext.getSystemService(ConnectivityManager::class.java)
        val caps = manager.getNetworkCapabilities(manager.activeNetwork)
        val blockers = mutableListOf<String>()
        if (caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) != true) blockers += "network"
        val freeNetwork = caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED) == true && (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) || caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET))
        if (repository.preferences.get().wifiOnly && !freeNetwork && !row.cellularApproved) blockers += "cellular_consent"
        if (repository.dao.unfinished().isNotEmpty() || RuntimeLease.held(repository.context.filesDir)) blockers += "game_running"
        if (blockers.isNotEmpty()) {
            repository.dao.putTransfer(row.copy(state = "waiting", blockers = org.json.JSONArray(blockers).toString()))
            throw Interrupted()
        }
        if (row.blockers != "[]") repository.dao.putTransfer(row.copy(blockers = "[]"))
        return row
    }
    private suspend fun auxiliary(id: String, manifest: JSONObject, key: String, limit: Long, root: File): JSONObject {
        val objectInfo = manifest.getJSONObject(key)
        val bytes = checkedBytes(objectInfo.getString("bytes")); require(bytes <= limit)
        val file = File(root, "$key.json")
        if (!file.isFile || file.length() != bytes || sha256(file) != objectInfo.getString("sha256")) {
            val current = checkpoint(id)
            val grant = repository.grant(manifest, key, current.experimentalApproved)
            repository.api.open(grant.getString("url")).use { response ->
                require(response.code == 200) { "无法获取校验索引" }
                val temporary = File(root, "$key.partial")
                var count = 0L
                response.body!!.byteStream().use { input -> temporary.outputStream().use { output ->
                    val buffer = ByteArray(65536)
                    while (true) { val n = input.read(buffer); if (n < 0) break; count = Math.addExact(count, n.toLong()); require(count <= bytes); output.write(buffer, 0, n) }
                    output.fd.sync()
                } }
                require(count == bytes && sha256(temporary) == objectInfo.getString("sha256")) { "索引校验失败" }
                check(temporary.renameTo(file))
            }
        }
        return strictObject(file.readBytes())
    }
    private suspend fun perform(id: String) {
        var row = checkpoint(id)
        val manifest = JSONObject(row.manifest)
        repository.trust.slot(manifest)
        val root = File(repository.transferRoot, safeId(id)).apply { mkdirs() }
        val files = auxiliary(id, manifest, "file_index", 64 * 1024 * 1024L, root)
        val chunks = auxiliary(id, manifest, "chunk_index", 16 * 1024 * 1024L, root)
        require(files.getString("release_id") == row.releaseId && chunks.getString("object_id") == manifest.getJSONObject("archive").getString("object_id"))
        val items = chunks.getJSONArray("chunks").objects()
        require(items.isNotEmpty() && items.size <= 1048576)
        val archive = File(root, "archive.zip.partial")
        var grant = repository.grant(manifest, "archive", row.experimentalApproved)
        var offset = 0L
        RandomAccessFile(archive, "rw").use { output ->
            for ((index, chunk) in items.withIndex()) {
                val bytes = checkedBytes(chunk.getString("length_bytes")); require(bytes in 1..8388608 && chunk.getInt("index") == index && checkedBytes(chunk.getString("offset_bytes")) == offset)
                var correct = false
                if (output.length() >= Math.addExact(offset, bytes)) {
                    val existing = ByteArray(bytes.toInt()); output.seek(offset); output.readFully(existing)
                    correct = sha256(existing) == chunk.getString("sha256")
                }
                if (!correct) {
                    row = checkpoint(id)
                    require(repository.availableBytes() >= repository.additionalBytes(manifest, offset, 0)) { "可用空间不足，任务已暂停" }
                    var response = repository.api.open(grant.getString("url"), "bytes=$offset-${offset + bytes - 1}", grant.getString("etag"))
                    if (response.code in listOf(401, 403)) {
                        response.close()
                        grant = repository.api.request("/v1/download-sessions/${grant.getString("download_session_id")}/renew", JSONObject().put("object_id", grant.getString("object_id")).put("manifest_sha256", grant.getString("manifest_sha256")))
                        response = repository.api.open(grant.getString("url"), "bytes=$offset-${offset + bytes - 1}", grant.getString("etag"))
                    }
                    response.use {
                        require(it.code == 206 && it.header("Content-Range") == "bytes $offset-${offset + bytes - 1}/${row.total}") { "下载范围不匹配，已保留完整校验的分块" }
                        require(it.header("ETag") == grant.getString("etag")) { "远程文件版本发生变化" }
                        val data = it.body!!.byteStream().use { stream -> stream.readBounded(bytes.toInt() + 1) }
                        require(data.size.toLong() == bytes && sha256(data) == chunk.getString("sha256")) { "下载分块校验失败" }
                        checkpoint(id)
                        output.seek(offset); output.write(data); output.fd.sync()
                    }
                }
                offset = Math.addExact(offset, bytes)
                row = checkpoint(id)
                repository.dao.putTransfer(row.copy(state = "downloading", bytes = offset, error = ""))
                setForeground(notification("已下载 ${formatBytes(offset)} / ${formatBytes(row.total)}"))
            }
            require(offset == row.total); output.setLength(offset); output.fd.sync()
        }
        row = checkpoint(id)
        repository.dao.putTransfer(row.copy(state = "verifying"))
        require(sha256(archive) == manifest.getJSONObject("archive").getString("sha256")) { "完整文件校验失败" }
        repository.operationLock.withLock {
            repository.requireIdle()
            Installation(repository).install(row, archive, files) { checkpoint(id) }
        }
        repository.dao.transfer(id)?.let { repository.dao.putTransfer(it.copy(state = "completed", bytes = it.total, error = "")) }
        root.deleteRecursively()
    }
}

fun formatBytes(bytes: Long): String = when {
    bytes >= 1024L * 1024 * 1024 -> "%.1f GB".format(bytes.toDouble() / (1024 * 1024 * 1024))
    bytes >= 1024L * 1024 -> "%.1f MB".format(bytes.toDouble() / (1024 * 1024))
    bytes >= 1024 -> "%.1f KB".format(bytes.toDouble() / 1024)
    else -> "$bytes B"
}
