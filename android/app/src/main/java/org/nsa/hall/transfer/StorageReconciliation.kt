package org.nsa.hall.transfer

import org.json.JSONObject
import org.nsa.hall.data.HallRepository
import org.nsa.hall.data.InstallRecord
import org.nsa.hall.data.objects
import org.nsa.hall.security.*
import java.io.File
import java.io.RandomAccessFile
import java.nio.file.Files

/** Runs once behind the same operation lock as extraction and activation. */
class StorageReconciliation(private val repository: HallRepository) {
    fun installationProblem(record: InstallRecord): String? = try {
        val manifest = JSONObject(record.manifest)
        val root = repository.releaseRoot(record.gameId, record.releaseId)
        require(root.isDirectory && !Files.isSymbolicLink(root.toPath()))
        val marker = strictObject(File(root, ".ready.json").readBytes())
        require(marker.getString("release_id") == record.releaseId && marker.getString("manifest_sha256") == manifest.getString("_verified_sha256"))
        val indexFile = File(root, ".file-index.json")
        val expected = manifest.getJSONObject("file_index")
        require(indexFile.isFile && !Files.isSymbolicLink(indexFile.toPath()) && indexFile.length() in 1..(64L * 1024 * 1024))
        val raw = indexFile.readBytes(); val index = strictObject(raw)
        val canonical = canonicalIndexBytes(index)
        require(listOf(raw, canonical).any { it.size.toLong() == checkedBytes(expected.getString("bytes")) && sha256(it) == expected.getString("sha256") })
        val entries = index.getJSONArray("files").objects()
        require(index.getString("release_id") == record.releaseId && entries.size == manifest.getInt("file_count") && marker.getInt("file_count") == entries.size)
        require(entries.size in 1..250000)
        val names = mutableSetOf<String>(); var total = 0L
        entries.forEach { item ->
            val name = safeRelativePath(item.getString("path")); require(names.add(name.lowercase(java.util.Locale.ROOT)))
            val file = safeChild(root, name); val size = checkedBytes(item.getString("bytes"))
            require(file.isFile && !Files.isSymbolicLink(file.toPath()) && file.length() == size)
            total = Math.addExact(total, size)
        }
        require(total == checkedBytes(manifest.getString("unpacked_bytes")))
        null
    } catch (_: Exception) { "资源目录、清单或文件记录不完整，请校验并修复" }

    suspend fun reconcile() {
        repository.dao.allInstalls().forEach { record ->
            if (record.state == "ready" && installationProblem(record) != null) repository.dao.putInstall(record.copy(state = "repair_required", healthy = false))
        }
        repository.dao.allTransfers().filter { it.state !in listOf("completed", "cancelled") }.forEach { record ->
            val root = File(repository.transferRoot, safeId(record.id))
            val archive = File(root, "archive.zip.partial")
            val verified = runCatching {
                val manifest = JSONObject(record.manifest)
                val expected = manifest.getJSONObject("chunk_index")
                val index = File(root, "chunk_index.json")
                require(index.isFile && index.length() == checkedBytes(expected.getString("bytes")) && sha256(index) == expected.getString("sha256"))
                val chunks = strictObject(index.readBytes())
                require(chunks.getString("object_id") == manifest.getJSONObject("archive").getString("object_id"))
                val entries = chunks.getJSONArray("chunks").objects(); require(entries.size in 1..1048576)
                if (!archive.isFile) return@runCatching 0L
                var offset = 0L
                RandomAccessFile(archive, "rw").use { input ->
                    for ((number, chunk) in entries.withIndex()) {
                        val count = checkedBytes(chunk.getString("length_bytes"))
                        require(count in 1..8388608 && chunk.getInt("index") == number && checkedBytes(chunk.getString("offset_bytes")) == offset)
                        if (input.length() < Math.addExact(offset, count)) break
                        val bytes = ByteArray(count.toInt()); input.seek(offset); input.readFully(bytes)
                        if (sha256(bytes) != chunk.getString("sha256")) break
                        offset = Math.addExact(offset, count)
                    }
                    if (input.length() != offset) { input.setLength(offset); input.fd.sync() }
                }
                offset.coerceAtMost(record.total)
            }.getOrDefault(0L)
            val state = when { record.intent == "cancel" -> "cancelled"; record.intent == "pause" -> "paused"; record.state == "failed" -> "failed"; else -> "queued" }
            repository.dao.putTransfer(record.copy(bytes = verified, state = state))
        }
    }
}
