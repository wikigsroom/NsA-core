package org.nsa.hall.transfer

import org.json.JSONObject
import org.nsa.hall.security.checkedBytes
import org.nsa.hall.security.safeId
import org.nsa.hall.security.safeRelativePath
import java.math.BigDecimal
import java.time.Instant
import java.util.Locale
import java.io.InputStream
import java.io.OutputStream

data class SaveFile(val path: String, val bytes: Long, val sha256: String)

/** Enforces the independent archive budget before any expanded bytes are written. */
class SaveExpansionBudget(total: Long, maximum: Long = SaveFormat.MAXIMUM) {
    var remaining = total; private set
    init { require(total in 0..maximum) }
    fun copy(input: InputStream, output: OutputStream, expected: Long): Long {
        require(expected in 0..remaining)
        var expanded = 0L; val buffer = ByteArray(65536)
        while (true) {
            val count = input.read(buffer); if (count < 0) break
            require(count.toLong() <= expected - expanded && count.toLong() <= remaining) { "存档展开超出清单大小" }
            expanded += count; remaining -= count; output.write(buffer, 0, count)
        }
        require(expanded == expected) { "存档文件大小与清单不符" }
        return expanded
    }
}

object SaveFormat {
    const val MAXIMUM = 2L * 1024 * 1024 * 1024
    const val MAX_METADATA = 8 * 1024 * 1024
    private val fields = setOf("save_schema", "game_id", "local_player_id", "source_release_id", "compatibility_group", "commit_seq", "created_at", "total_bytes", "files")
    private fun string(value: JSONObject, field: String): String = (value.get(field) as? String) ?: error("存档备份字段类型无效：$field")
    fun validate(metadata: JSONObject, maximum: Long = MAXIMUM): List<SaveFile> {
        require(metadata.keys().asSequence().toSet() == fields) { "存档备份清单字段不完整或不受支持" }
        require(metadata.get("save_schema") is Number && BigDecimal(metadata.get("save_schema").toString()).compareTo(BigDecimal.ONE) == 0)
        listOf("game_id", "local_player_id", "source_release_id", "compatibility_group").forEach { safeId(string(metadata, it)) }
        val sequence = metadata.get("commit_seq")
        require(sequence is Number && BigDecimal(sequence.toString()).longValueExact() >= 0) { "存档序列无效" }
        val createdAt = string(metadata, "created_at")
        require(createdAt.matches(Regex("[0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}:[0-9]{2}(\\.[0-9]{1,9})?Z")))
        Instant.parse(createdAt)
        val total = checkedBytes(string(metadata, "total_bytes")); require(total <= maximum) { "存档展开大小超过上限" }
        val entries = metadata.getJSONArray("files"); require(entries.length() in 1..10000)
        val names = mutableSetOf<String>(); var sum = 0L
        return (0 until entries.length()).map { index ->
            val file = entries.getJSONObject(index)
            require(file.keys().asSequence().toSet() == setOf("path", "bytes", "sha256"))
            val path = safeRelativePath(string(file, "path"))
            require(names.add(path.lowercase(Locale.ROOT))) { "存档路径重复" }
            val bytes = checkedBytes(string(file, "bytes")); require(bytes <= maximum)
            sum = Math.addExact(sum, bytes); require(sum <= maximum) { "存档展开大小超过上限" }
            val hash = string(file, "sha256"); require(hash.matches(Regex("[0-9a-f]{64}")))
            SaveFile(path, bytes, hash)
        }.also {
            require(sum == total) { "存档文件大小合计与清单不一致" }
            it.forEach { file -> var path = file.path.lowercase(Locale.ROOT); while ('/' in path) { path = path.substringBeforeLast('/'); require(path !in names) { "存档文件与目录路径冲突" } } }
        }
    }
}
