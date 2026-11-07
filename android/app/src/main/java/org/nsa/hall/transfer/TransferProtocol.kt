package org.nsa.hall.transfer

import okhttp3.Response
import org.json.JSONObject
import org.nsa.hall.security.checkedBytes
import org.nsa.hall.security.sha256
import java.io.File
import java.time.Instant
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

class TransferProtocolException(message: String, val permanent: Boolean = true, val retryAfter: Long = 0) : Exception(message)

/** Persisted identity contains no bearer URL or download-session credential. */
object TransferProtocol {
    fun identity(manifest: JSONObject, key: String, grant: JSONObject, previous: JSONObject? = null): JSONObject {
        val signed = manifest.getJSONObject(key)
        val expected = JSONObject().put("release_id", manifest.getString("release_id"))
            .put("manifest_sha256", manifest.getString("_verified_sha256"))
            .put("object_id", signed.getString("object_id")).put("bytes", signed.getString("bytes"))
            .put("sha256", signed.getString("sha256"))
        for (field in listOf("release_id", "manifest_sha256", "object_id", "bytes", "sha256")) {
            if (grant.optString(field) != expected.getString(field)) throw TransferProtocolException("下载授权的资源身份发生变化，已保留可信进度")
        }
        val etag = grant.getString("etag")
        if (!etag.matches(Regex("\"[\\x21\\x23-\\x7e\\x80-\\xff]*\""))) throw TransferProtocolException("资源缺少有效的强版本标识")
        expected.put("etag", etag)
        if (previous != null && expected.keys().asSequence().any { previous.optString(it) != expected.getString(it) }) {
            throw TransferProtocolException("续签的资源版本发生变化，请保留此任务并重新检查版本")
        }
        return expected
    }

    fun identityEncoding(response: Response) {
        if (response.headers.values("Content-Encoding").any { !it.equals("identity", true) }) throw TransferProtocolException("资源编码不符合范围下载要求，已停止写入")
    }

    fun range(response: Response, start: Long, bytes: Long, total: Long, etag: String) {
        identityEncoding(response)
        val end = Math.addExact(start, bytes - 1)
        if (response.code != 206 || response.header("Content-Range") != "bytes $start-$end/$total") {
            throw TransferProtocolException("下载范围不匹配，已保留完整校验的分块")
        }
        if (response.header("ETag") != etag) throw TransferProtocolException("远程文件版本发生变化，已停止拼接")
    }

    /** 416 alone is never success; only the exact complete signed local object is. */
    fun completeAfter416(response: Response, archive: File, signed: JSONObject, etag: String): Boolean {
        identityEncoding(response)
        val bytes = checkedBytes(signed.getString("bytes"))
        if (response.code != 416 || response.header("Content-Range") != "bytes */$bytes") return false
        if (response.header("ETag")?.let { it != etag } == true) return false
        return archive.isFile && archive.length() == bytes && sha256(archive) == signed.getString("sha256")
    }

    fun expiredUrl(response: Response, grant: JSONObject): Boolean {
        if (response.code !in listOf(401, 403)) return false
        // Prefer the object's HTTP date; a local clock guess never grants access.
        val now = response.header("Date")?.let { runCatching { ZonedDateTime.parse(it, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant() }.getOrNull() }
            ?: Instant.now()
        return runCatching { !Instant.parse(grant.getString("expires_at")).isAfter(now) }.getOrDefault(false)
    }

    fun failure(response: Response): TransferProtocolException {
        val retry = response.header("Retry-After")?.toLongOrNull()?.coerceIn(0, 3600) ?: 0
        return when (response.code) {
            401, 403 -> TransferProtocolException("资源授权未通过，请连接目录确认资格后重试")
            404, 410 -> TransferProtocolException("此资源暂不可用，请刷新版本信息；已有进度保留")
            429 -> TransferProtocolException("请求较多，稍后按原任务继续", false, retry)
            in 500..599 -> TransferProtocolException("资源服务暂不可用，稍后继续", false, retry)
            else -> TransferProtocolException("资源响应不符合下载协议，已保留可信进度")
        }
    }
}
