package org.nsa.hall.security

import android.content.Context
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import java.io.File
import java.io.InputStream
import java.io.ByteArrayOutputStream
import java.security.MessageDigest

fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
fun sha256(file: File): String { val hash = MessageDigest.getInstance("SHA-256"); file.inputStream().buffered().use { input -> val buffer = ByteArray(1024 * 1024); while (true) { val count = input.read(buffer); if (count < 0) break; hash.update(buffer, 0, count) } }; return hash.digest().joinToString("") { "%02x".format(it) } }
fun checkedBytes(value: String): Long { require(value.matches(Regex("0|[1-9][0-9]*"))) { "无效的文件大小" }; return value.toLong().also { require(it >= 0) } }
fun InputStream.readBounded(limit: Int): ByteArray {
    require(limit in 1..64 * 1024 * 1024 + 1)
    val output = ByteArrayOutputStream(minOf(limit, 8192))
    val buffer = ByteArray(minOf(limit, 8192))
    while (output.size() < limit) {
        val count = read(buffer, 0, minOf(buffer.size, limit - output.size()))
        if (count < 0) break
        if (count > 0) output.write(buffer, 0, count)
    }
    return output.toByteArray()
}
fun jsonEquivalent(left: Any?, right: Any?): Boolean = when {
    left is JSONObject && right is JSONObject -> {
        val keys = left.keys().asSequence().toSet()
        keys == right.keys().asSequence().toSet() && keys.all { jsonEquivalent(left.get(it), right.get(it)) }
    }
    left is JSONArray && right is JSONArray -> left.length() == right.length() && (0 until left.length()).all { jsonEquivalent(left.get(it), right.get(it)) }
    left is Number && right is Number -> left.toString().toBigDecimal().compareTo(right.toString().toBigDecimal()) == 0
    else -> left == right
}
/** Canonical UTF-8 used by service indexes, including legacy reserialized copies. */
fun canonicalIndexBytes(value: Any): ByteArray {
    fun encode(item: Any): String = when (item) {
        is JSONObject -> item.keys().asSequence().sorted().joinToString(",", "{", "}") { encode(it) + ":" + encode(item.get(it)) }
        is JSONArray -> (0 until item.length()).joinToString(",", "[", "]") { encode(item.get(it)) }
        is String -> buildString {
            append('"'); item.forEach { c -> when (c) {
                '"' -> append("\\\""); '\\' -> append("\\\\"); '\b' -> append("\\b"); '\t' -> append("\\t"); '\n' -> append("\\n"); '\r' -> append("\\r"); '\u000c' -> append("\\f")
                else -> if (c.code < 32) append("\\u%04x".format(c.code)) else append(c)
            } }; append('"')
        }
        is Boolean -> item.toString()
        is Number -> item.toString().also { require(it.matches(Regex("0|[1-9][0-9]*"))) }
        else -> { require(item === JSONObject.NULL); "null" }
    }
    return encode(value).toByteArray(Charsets.UTF_8)
}
fun safeId(value: String): String = value.also { require(it.matches(Regex("[A-Za-z0-9][A-Za-z0-9._-]{0,159}"))) { "无效的内容标识" } }
fun safeRelativePath(relative: String): String {
    require(relative.isNotEmpty() && relative.toByteArray().size <= 1024 && !relative.contains('\\') && !relative.contains(':') && !relative.startsWith('/') && relative.none { Character.isISOControl(it) }) { "文件路径不安全" }
    val parts = relative.split('/')
    require(parts.size <= 32 && parts.all { it.isNotEmpty() && it != "." && it != ".." && !it.endsWith('.') && !it.endsWith(' ') }) { "文件路径不安全" }
    require(java.text.Normalizer.normalize(relative, java.text.Normalizer.Form.NFC) == relative)
    return relative
}
fun safeChild(root: File, relative: String): File {
    safeRelativePath(relative)
    val file = File(root, relative)
    require(file.canonicalPath.startsWith(root.canonicalPath + File.separator))
    return file
}

class Trust(private val context: Context) {
    @Volatile var revokedKeys: Set<String> = emptySet()
    val registry: JSONObject by lazy { JSONObject(context.assets.open("runtime-registry.json").bufferedReader().use { it.readText() }) }
    val registryHash: String by lazy { sha256(context.assets.open("runtime-registry.json").use { it.readBytes() }) }
    private val keys by lazy { JSONObject(context.assets.open("trusted-keys.json").bufferedReader().use { it.readText() }).getJSONArray("keys") }
    fun verify(raw: ByteArray, purpose: String, expected: String? = null, installedPayload: Boolean = false): JSONObject {
        require(raw.size <= 3 * 1024 * 1024) { "签名响应过大" }
        val envelope = JSONObject(String(raw, Charsets.UTF_8))
        require(envelope.getInt("envelope_schema") == 1 && envelope.getString("purpose") == purpose && envelope.getString("algorithm") == "Ed25519")
        require(!installedPayload || (purpose == "release" && expected != null))
        require(installedPayload || envelope.getString("key_id") !in revokedKeys) { "此内容签名密钥已撤销" }
        val key = (0 until keys.length()).map { keys.getJSONObject(it) }.single { it.getString("key_id") == envelope.getString("key_id") }
        require((0 until key.getJSONArray("purposes").length()).any { key.getJSONArray("purposes").getString(it) == purpose })
        val payload = Base64.decode(envelope.getString("payload_base64"), Base64.NO_WRAP)
        require(payload.size <= 2 * 1024 * 1024)
        val digest = sha256(payload)
        require(digest == envelope.getString("payload_sha256") && (expected == null || digest == expected)) { "内容签名摘要不匹配" }
        require(verifyEd25519(Base64.decode(key.getString("public_key_base64"), Base64.NO_WRAP), Base64.decode(envelope.getString("signature_base64"), Base64.NO_WRAP), "NSA/$purpose/v1\u0000".toByteArray(Charsets.UTF_8) + payload)) { "内容签名验证失败" }
        return strictObject(payload)
    }
    fun slot(manifest: JSONObject): JSONObject {
        val runtime = manifest.getJSONObject("runtime")
        val slots = registry.getJSONArray("slots")
        val slot = (0 until slots.length()).map { slots.getJSONObject(it) }.singleOrNull { it.getString("runtime_slot_id") == runtime.getString("runtime_slot_id") } ?: error("需要更新 App 才能运行此游戏")
        require(runtime.getInt("runtime_api_version") == 1 && manifest.getInt("required_app_version_code") <= org.nsa.hall.BuildConfig.VERSION_CODE)
        require(slot.getString("profile_digest") == runtime.getString("profile_digest") && slot.getString("shader_chain_id") == runtime.getString("shader_chain_id")) { "游戏与内置运行组件不匹配" }
        val supported = slot.getJSONArray("supported_module_build_ids").strings().toSet()
        require(runtime.getJSONArray("module_build_ids").strings().all { it in supported })
        require(runtime.getInt("shader_bundle_version") in slot.getJSONArray("shader_bundle_versions").let { a -> (0 until a.length()).map { a.getInt(it) } })
        require(slot.getJSONArray("approved_driver_ids").strings().intersect(runtime.getJSONArray("approved_driver_ids").strings().toSet()).isNotEmpty()) { "运行组件缺少已批准的图形驱动" }
        val lib = slot.getString("library_name")
        require(lib.matches(Regex("libnsa_[a-z0-9_]+\\.so")))
        require(sha256(File(context.applicationInfo.nativeLibraryDir, lib)) == slot.getString("library_sha256")) { "内置运行组件校验失败" }
        return slot
    }
}
fun JSONArray.strings() = (0 until length()).map { getString(it) }

/** Reject duplicate fields before trusting JSON, including nested objects. */
fun strictObject(bytes: ByteArray): JSONObject {
    val decoder = Charsets.UTF_8.newDecoder().onMalformedInput(java.nio.charset.CodingErrorAction.REPORT)
    val text = decoder.decode(java.nio.ByteBuffer.wrap(bytes)).toString()
    val tokener = JSONTokener(text)
    fun readValue(depth: Int = 0): Any {
        require(depth <= 32) { "JSON 嵌套层级过深" }
        return when (val c = tokener.nextClean()) {
            '{' -> {
                val result = JSONObject(); val seen = mutableSetOf<String>()
                if (tokener.nextClean() == '}') return result
                tokener.back()
                while (true) {
                    val quote = tokener.nextClean(); require(quote == '"')
                    val key = tokener.nextString(quote); require(seen.add(key)) { "JSON 字段重复" }
                    require(tokener.nextClean() == ':'); result.put(key, readValue(depth + 1))
                    when (tokener.nextClean()) { '}' -> return result; ',' -> Unit; else -> error("无效的 JSON") }
                }
                @Suppress("UNREACHABLE_CODE") result
            }
            '[' -> {
                val array = JSONArray(); if (tokener.nextClean() == ']') return array
                tokener.back()
                while (true) { array.put(readValue(depth + 1)); when (tokener.nextClean()) { ']' -> return array; ',' -> Unit; else -> error("无效的 JSON") } }
                @Suppress("UNREACHABLE_CODE") array
            }
            '"' -> tokener.nextString(c)
            else -> { tokener.back(); val value = tokener.nextValue(); require(value !is JSONObject && value !is JSONArray); if (value is String) require(value in listOf("true", "false", "null")) { "JSON 字符串必须有引号" }; value }
        }
    }
    val result = readValue() as JSONObject
    require(tokener.nextClean() == '\u0000')
    return result
}
