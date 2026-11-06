package org.nsa.hall.data

import android.content.Context
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.json.JSONObject
import org.nsa.hall.BuildConfig
import org.nsa.hall.security.readBounded
import org.nsa.hall.security.strictObject
import org.nsa.hall.security.sha256
import java.security.KeyStore
import java.security.SecureRandom
import java.security.cert.CertificateFactory
import java.util.UUID
import java.util.concurrent.TimeUnit
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager

class ApiException(val code: String, message: String, val status: Int = 0, val retryAfter: Long = 0) : Exception(message)
class Vault(context: Context) {
    private val preferences = context.getSharedPreferences("credentials", Context.MODE_PRIVATE)
    private val key: SecretKey by lazy {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey("nsa-credentials", null) as? SecretKey) ?: KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder("nsa-credentials", KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT).setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        }.generateKey()
    }
    fun write(value: JSONObject) { val cipher = Cipher.getInstance("AES/GCM/NoPadding"); cipher.init(Cipher.ENCRYPT_MODE, key); val encrypted = cipher.doFinal(value.toString().toByteArray()); require(preferences.edit().putString("value", Base64.encodeToString(cipher.iv + encrypted, Base64.NO_WRAP)).commit()) { "无法保存安装凭据，请检查可用空间" } }
    fun read(): JSONObject? = runCatching { val bytes = Base64.decode(preferences.getString("value", null) ?: return null, Base64.NO_WRAP); val cipher = Cipher.getInstance("AES/GCM/NoPadding"); cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, bytes.copyOfRange(0, 12))); JSONObject(String(cipher.doFinal(bytes.copyOfRange(12, bytes.size)))) }.getOrNull()
    fun clear() { require(preferences.edit().clear().commit()) { "无法更新安装凭据" } }
}

class Api(private val context: Context, private val preferences: Preferences) {
    private val vault = Vault(context)
    private val credentialLock = Mutex()
    val client: OkHttpClient by lazy {
        val builder = OkHttpClient.Builder().connectTimeout(20, TimeUnit.SECONDS).readTimeout(30, TimeUnit.SECONDS).writeTimeout(30, TimeUnit.SECONDS).followRedirects(false).followSslRedirects(false)
        if (BuildConfig.DEBUG) {
            runCatching { context.assets.open("local-ca.pem").use { stream ->
                val ca = CertificateFactory.getInstance("X.509").generateCertificate(stream)
                val store = KeyStore.getInstance(KeyStore.getDefaultType()).apply { load(null); setCertificateEntry("local", ca) }
                val trust = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm()).apply { init(store) }.trustManagers.single() as X509TrustManager
                // Keep system trust for public endpoints, local CA is only used by the localhost debug client.
                localTrust = trust
            } }
        }
        builder.build()
    }
    private var localTrust: X509TrustManager? = null
    private fun httpFor(url: String): OkHttpClient {
        val standard = client
        val parsed = url.toHttpUrlOrNull() ?: error("无效的服务地址")
        require(parsed.isHttps) { "服务连接必须使用 HTTPS" }
        if (BuildConfig.DEBUG && parsed.host in setOf("localhost", "127.0.0.1") && localTrust != null) {
            val ssl = SSLContext.getInstance("TLS").apply { init(null, arrayOf(localTrust!!), SecureRandom()) }
            return standard.newBuilder().sslSocketFactory(ssl.socketFactory, localTrust!!).build()
        }
        return standard
    }
    suspend fun endpoint() = preferences.get().endpoint.trimEnd('/').also { val u = it.toHttpUrlOrNull(); require(u != null && u.isHttps && u.username.isEmpty() && u.password.isEmpty() && u.query == null) }
    private suspend fun credentials(force: Boolean = false, failedToken: String? = null): JSONObject = credentialLock.withLock {
        val endpoint = endpoint()
        val current = vault.read()?.takeIf { it.optString("endpoint") == endpoint }
        if (current?.has("access_token") == true && ((!force && current.optLong("local_expiry") > System.currentTimeMillis() + 30000) || (force && failedToken != null && current.getString("access_token") != failedToken))) return@withLock current
        val result = if (current?.has("refresh_token") == true) {
            val key = current.optString("pending_refresh_key").ifBlank { UUID.randomUUID().toString() }
            vault.write(current.put("pending_refresh_key", key))
            try { send("/v1/credentials/refresh", JSONObject().put("refresh_token", current.getString("refresh_token")), null, key) }
            catch (error: ApiException) {
                // Connectivity, rate limiting and server faults retain the exact
                // pending receipt. Only an authoritative rejection resets identity.
                if (error.status != 401 || error.code != "AUTH_EXPIRED") throw error
                vault.clear(); bootstrap(null)
            }
        } else bootstrap(current)
        result.put("endpoint", endpoint).put("local_expiry", java.time.Instant.parse(result.getString("expires_at")).toEpochMilli())
        vault.write(result); result
    }
    private suspend fun bootstrap(existing: JSONObject?): JSONObject {
        val grant = if (BuildConfig.DEBUG) runCatching { context.assets.open("internal-grant.txt").bufferedReader().use { it.readText().trim() } }.getOrNull() else null
        val grantDigest = sha256((grant ?: "").toByteArray())
        val pending = existing?.takeIf { it.has("bootstrap_body") && it.optString("bootstrap_grant_digest") == grantDigest } ?: JSONObject().put("endpoint", endpoint()).put("bootstrap_key", UUID.randomUUID().toString()).put("bootstrap_grant_digest", grantDigest).put("bootstrap_body",
            JSONObject().put("app_version_code", BuildConfig.VERSION_CODE).put("installation_nonce", UUID.randomUUID().toString() + UUID.randomUUID().toString()).put("locale", preferences.get().language).put("channel", if (grant != null) "internal" else "official").apply { if (grant != null) put("access_grant", grant) })
        vault.write(pending)
        val body = pending.getJSONObject("bootstrap_body")
        return send("/v1/bootstrap", body, null, pending.getString("bootstrap_key"))
    }
    suspend fun request(path: String, body: JSONObject? = null): JSONObject {
        val auth = credentials()
        try { return send(path, body, auth.getString("access_token")) } catch (error: ApiException) {
            if (error.status != 401 || error.code != "AUTH_EXPIRED") throw error
            return send(path, body, credentials(true, auth.getString("access_token")).getString("access_token"))
        }
    }
    private suspend fun send(path: String, body: JSONObject?, token: String?, idempotencyKey: String? = null): JSONObject = withContext(Dispatchers.IO) {
        val url = endpoint() + path
        val request = Request.Builder().url(url).header("Accept", "application/json")
        if (token != null) request.header("Authorization", "Bearer $token")
        if (body != null) request.header("Idempotency-Key", idempotencyKey ?: UUID.randomUUID().toString()).post(body.toString().toRequestBody("application/json".toMediaType()))
        httpFor(url).newCall(request.build()).execute().use { response ->
            val data = response.body?.byteStream()?.use { it.readBounded(3 * 1024 * 1024 + 1) } ?: ByteArray(0)
            require(data.size <= 3 * 1024 * 1024) { "服务响应过大" }
            if (response.code == 204) return@withContext JSONObject()
            val json = strictObject(data)
            if (!response.isSuccessful) {
                val code = json.optString("code", "NETWORK_ERROR")
                val copy = mapOf("AUTH_EXPIRED" to "安装凭据需要更新，请重新连接目录后重试", "CAT_SCOPE_DENIED" to "当前内测资格已失效，请重新取得有效邀请；已有资源与存档保留", "PERMISSION_DENIED" to "当前操作暂未获得授权，请检查可用内容", "RATE_LIMITED" to "请求较多，请稍后重试", "RELEASE_BLOCKED" to "此游戏版本已暂停，资源与存档保留", "DEVICE_UNVERIFIED" to "此设备尚未验证，实验下载需要有效资格和明确确认")
                throw ApiException(code, (copy[code] ?: json.optString("message", "连接失败")) + json.optString("request_id").let { if (it.isEmpty()) "" else " · $it" }, response.code, response.header("Retry-After")?.toLongOrNull()?.coerceIn(0, 3600) ?: 0)
            }
            json
        }
    }
    suspend fun open(url: String, range: String? = null, etag: String? = null): Response = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(url)
        if (range != null) request.header("Range", range)
        if (etag != null) request.header("If-Range", etag)
        httpFor(url).newCall(request.build()).execute()
    }
    suspend fun upload(url: String, file: java.io.File) = withContext(Dispatchers.IO) {
        val body = object : RequestBody() {
            override fun contentType() = "application/zip".toMediaType()
            override fun contentLength() = file.length()
            override fun writeTo(sink: okio.BufferedSink) { file.inputStream().use { input -> val buffer = ByteArray(65536); while (true) { val n = input.read(buffer); if (n < 0) break; sink.write(buffer, 0, n) } } }
        }
        httpFor(url).newCall(Request.Builder().url(url).put(body).build()).execute().use { require(it.isSuccessful) { "诊断上传失败" } }
    }
}
