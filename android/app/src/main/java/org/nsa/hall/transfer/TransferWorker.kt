package org.nsa.hall.transfer

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
import org.json.JSONArray
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
        val lease = TransferLease.acquire(applicationContext.filesDir) ?: return@withContext Result.retry()
        lease.use {
            try { repository.initialize() } catch (error: CancellationException) { throw error } catch (_: Exception) { return@withContext Result.retry() }
            setForeground(notification("准备游戏下载"))
            repository.operationLock.withLock {
                if (repository.dao.unfinished().isEmpty() && !RuntimeLease.held(repository.context.filesDir)) Installation(repository).recover()
            }
            var retry = false
            drainTransferQueue(repository.dao::allTransfers) { candidate ->
                if (isStopped) throw CancellationException()
                try {
                    if (candidate.intent == "cancel") cancel(candidate.id) else perform(candidate.id)
                } catch (_: Interrupted) {
                    val row = repository.dao.transfer(candidate.id)
                    if (row?.intent == "run" && row.state != "failed") retry = true
                } catch (error: CancellationException) { throw error }
                catch (error: Exception) {
                    val permanent = (error as? TransferProtocolException)?.permanent == true || error is ApiException && error.status in listOf(400, 401, 403, 404, 409, 410, 422)
                    val wait = (error as? TransferProtocolException)?.retryAfter ?: (error as? ApiException)?.retryAfter ?: 0
                    repository.dao.transferFailure(candidate.id, error.message ?: "下载失败，可重试", permanent,
                        if (wait > 0) System.currentTimeMillis() + wait.coerceAtMost(3600) * 1000 else 0)
                    val row = repository.dao.transfer(candidate.id)
                    if (row?.intent == "run" && row.state !in listOf("failed", "completed", "cancelled")) {
                        delay(minOf(60000L, 2000L shl (row.failures - 1).coerceIn(0, 4))); retry = true
                    }
                }
                // perform has unwound and closed every archive/response handle.
                if (repository.dao.transfer(candidate.id)?.let { it.intent == "cancel" && it.state !in listOf("completed", "cancelled") } == true) {
                    try { cancel(candidate.id) } catch (error: CancellationException) { throw error }
                    catch (_: Exception) { repository.dao.transferNotice(candidate.id, "取消后的清理待完成，将重试"); retry = true }
                    if (repository.dao.transfer(candidate.id)?.state !in listOf("completed", "cancelled")) retry = true
                }
            }
            if (retry) Result.retry() else Result.success()
        }
    }
    private suspend fun cancel(id: String) = repository.operationLock.withLock {
        repository.requireIdle()
        Installation(repository).cancel(id)
    }
    private fun notification(text: String): ForegroundInfo {
        val open = PendingIntent.getActivity(applicationContext, 0, Intent(applicationContext, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val notification = NotificationCompat.Builder(applicationContext, "downloads").setSmallIcon(R.drawable.ic_launcher).setContentTitle("NsA · 下载中心").setContentText(text).setContentIntent(open).setOngoing(true).setOnlyAlertOnce(true).build()
        return ForegroundInfo(1001, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
    }
    private suspend fun checkpoint(id: String): TransferRecord {
        val row = repository.dao.transfer(id) ?: throw Interrupted()
        if (isStopped || row.intent != "run" || row.ownerGameId != row.gameId || row.state in listOf("completed", "cancelled", "failed")) throw Interrupted()
        val manager = applicationContext.getSystemService(ConnectivityManager::class.java)
        val caps = manager.getNetworkCapabilities(manager.activeNetwork)
        val blockers = mutableListOf<String>()
        if (caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) != true) blockers += "network"
        val freeNetwork = caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED) == true && (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) || caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET))
        if (repository.preferences.get().wifiOnly && !freeNetwork && !row.cellularApproved) blockers += "cellular_consent"
        if (repository.dao.unfinished().isNotEmpty() || RuntimeLease.held(repository.context.filesDir)) blockers += "game_running"
        if (row.retryAt > System.currentTimeMillis()) blockers += "retry_after"
        if (blockers.isNotEmpty()) {
            repository.dao.transferProgress(id, "waiting", row.bytes, JSONArray(blockers).toString(), row.error)
            throw Interrupted()
        }
        return row
    }
    private suspend fun space(id: String, manifest: JSONObject, downloaded: Long, installed: Long = 0) {
        if (repository.availableBytes() < repository.additionalBytes(manifest, downloaded, installed)) {
            val row = repository.dao.transfer(id) ?: throw Interrupted()
            repository.dao.transferProgress(id, "waiting", row.bytes, "[\"storage\"]", "可用空间不足，释放空间后可继续")
            throw Interrupted()
        }
    }
    private suspend fun bind(id: String, manifest: JSONObject, key: String, grant: JSONObject): JSONObject {
        val fact = "transfer-object:$id:$key"
        val previous = repository.dao.fact(fact)?.value?.let(::JSONObject)
        val identity = TransferProtocol.identity(manifest, key, grant, previous)
        if (previous == null) repository.dao.putFact(Fact(fact, identity.toString()))
        return grant
    }
    private suspend fun grant(id: String, manifest: JSONObject, key: String) = bind(id, manifest, key, repository.grant(manifest, key, checkpoint(id).experimentalApproved))
    private suspend fun response(id: String, manifest: JSONObject, key: String, initial: JSONObject, range: String? = null): Pair<okhttp3.Response, JSONObject> {
        var grant = initial
        var result = repository.api.open(grant.getString("url"), range, if (range != null) grant.getString("etag") else null)
        if (TransferProtocol.expiredUrl(result, grant)) {
            result.close(); checkpoint(id)
            grant = bind(id, manifest, key, repository.api.request("/v1/download-sessions/${grant.getString("download_session_id")}/renew", JSONObject().put("object_id", grant.getString("object_id")).put("manifest_sha256", grant.getString("manifest_sha256"))))
            result = repository.api.open(grant.getString("url"), range, if (range != null) grant.getString("etag") else null)
        }
        if (result.code in listOf(404, 410)) {
            try { repository.updatePolicies() } catch (error: CancellationException) { result.close(); throw error } catch (_: Exception) { }
        }
        return result to grant
    }
    private suspend fun auxiliary(id: String, manifest: JSONObject, key: String, limit: Long, root: File): JSONObject {
        val objectInfo = manifest.getJSONObject(key)
        val bytes = checkedBytes(objectInfo.getString("bytes")); require(bytes <= limit)
        val file = File(root, "$key.json")
        if (!file.isFile || file.length() != bytes || sha256(file) != objectInfo.getString("sha256")) {
            val grant = grant(id, manifest, key)
            val (response, current) = response(id, manifest, key, grant)
            response.use {
                if (it.code != 200) throw TransferProtocol.failure(it)
                TransferProtocol.identityEncoding(it)
                require(it.header("ETag") == current.getString("etag")) { "索引版本标识不匹配" }
                val temporary = File(root, "$key.partial")
                var count = 0L; var checked = 0L
                it.body!!.byteStream().use { input -> temporary.outputStream().use { output ->
                    val buffer = ByteArray(65536)
                    while (true) {
                        if (count - checked >= 1024 * 1024) { checkpoint(id); checked = count }
                        val n = input.read(buffer); if (n < 0) break
                        count = Math.addExact(count, n.toLong()); require(count <= bytes); output.write(buffer, 0, n)
                    }
                    output.fd.sync()
                } }
                require(count == bytes && sha256(temporary) == objectInfo.getString("sha256")) { "索引校验失败" }
                checkpoint(id); check(temporary.renameTo(file)); syncDirectory(root)
            }
        }
        return strictObject(file.readBytes())
    }
    private suspend fun perform(id: String) {
        var row = checkpoint(id)
        val manifest = JSONObject(row.manifest); repository.trust.slot(manifest)
        val root = File(repository.transferRoot, safeId(id)); durableMkdirs(root, ::syncDirectory)
        val files = auxiliary(id, manifest, "file_index", 64 * 1024 * 1024L, root)
        val chunks = auxiliary(id, manifest, "chunk_index", 16 * 1024 * 1024L, root)
        require(files.getString("release_id") == row.releaseId && chunks.getString("object_id") == manifest.getJSONObject("archive").getString("object_id"))
        val items = chunks.getJSONArray("chunks").objects(); require(items.isNotEmpty() && items.size <= 1048576)
        val archive = File(root, "archive.zip.partial")
        var grant = grant(id, manifest, "archive")
        var offset = 0L
        RandomAccessFile(archive, "rw").use { output ->
            for ((index, chunk) in items.withIndex()) {
                row = checkpoint(id)
                val bytes = checkedBytes(chunk.getString("length_bytes")); require(bytes in 1..8388608 && chunk.getInt("index") == index && checkedBytes(chunk.getString("offset_bytes")) == offset)
                require(Math.addExact(offset, bytes) <= row.total)
                var correct = false
                if (output.length() >= Math.addExact(offset, bytes)) {
                    val existing = ByteArray(bytes.toInt()); output.seek(offset); output.readFully(existing)
                    correct = sha256(existing) == chunk.getString("sha256")
                }
                if (!correct) {
                    space(id, manifest, offset)
                    val opened = response(id, manifest, "archive", grant, "bytes=$offset-${offset + bytes - 1}")
                    grant = opened.second
                    var complete = false
                    opened.first.use {
                        if (it.code == 416) {
                            if (!TransferProtocol.completeAfter416(it, archive, manifest.getJSONObject("archive"), grant.getString("etag"))) throw TransferProtocolException("范围不可满足，本地文件尚未通过完整校验；已保留可信分块")
                            complete = true
                        } else {
                            if (it.code != 206) throw TransferProtocol.failure(it)
                            TransferProtocol.range(it, offset, bytes, row.total, grant.getString("etag"))
                            val data = it.body!!.byteStream().use { stream -> stream.readBounded(bytes.toInt() + 1) }
                            require(data.size.toLong() == bytes && sha256(data) == chunk.getString("sha256")) { "下载分块校验失败" }
                            checkpoint(id); output.seek(offset); output.write(data); output.fd.sync()
                        }
                    }
                    if (complete) { offset = row.total; break }
                }
                offset = Math.addExact(offset, bytes)
                if (repository.dao.transferProgress(id, "downloading", offset) != 1) throw Interrupted()
                setForeground(notification("已下载 ${formatBytes(offset)} / ${formatBytes(row.total)}"))
            }
            require(offset == row.total); output.setLength(offset); output.fd.sync()
        }
        row = checkpoint(id)
        if (repository.dao.transferProgress(id, "verifying", row.total) != 1) throw Interrupted()
        require(sha256(archive) == manifest.getJSONObject("archive").getString("sha256")) { "完整文件校验失败" }
        repository.operationLock.withLock {
            repository.requireIdle(); checkpoint(id)
            Installation(repository).install(row, archive, files) { checkpoint(id) }
        }
    }
}

fun formatBytes(bytes: Long): String = when {
    bytes >= 1024L * 1024 * 1024 -> "%.1f GB".format(bytes.toDouble() / (1024 * 1024 * 1024))
    bytes >= 1024L * 1024 -> "%.1f MB".format(bytes.toDouble() / (1024 * 1024))
    bytes >= 1024 -> "%.1f KB".format(bytes.toDouble() / 1024)
    else -> "$bytes B"
}
