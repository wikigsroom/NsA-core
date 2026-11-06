package org.nsa.hall.transfer

import org.json.JSONObject
import org.nsa.hall.security.*
import java.io.File
import java.nio.file.Files

/** The caller owns the operation lock and has proved that no runtime owns saves. */
class SaveSwap(
    private val parent: File,
    private val sync: (File) -> Unit,
    private val rename: (File, File) -> Unit,
    private val write: (File, JSONObject) -> Unit,
    private val readIndex: suspend () -> JSONObject?,
    private val putIndex: suspend (JSONObject?) -> Unit,
    private val checkpoint: (String) -> Unit = {}
) {
    private val journal = File(parent, "import-journal.json")
    private val committed = File(parent, "committed")
    private val before = File(parent, "before-import")
    private fun remove(file: File) { require(!file.exists() || file.deleteRecursively()) { "存档恢复清理未完成，请重试" } }
    private fun move(from: File, to: File, point: String) { rename(from, to); sync(parent); checkpoint(point) }
    private fun treeMatches(root: File, metadata: JSONObject): Boolean = runCatching {
        val files = SaveFormat.validate(metadata)
        require(root.isDirectory && !Files.isSymbolicLink(root.toPath()))
        val actual = root.walkTopDown().onEnter { require(!Files.isSymbolicLink(it.toPath())); true }.filter { it.isFile }.toList()
        require(actual.map { it.relativeTo(root).invariantSeparatorsPath }.toSet() == files.map { it.path }.toSet())
        files.all { expected -> val file = safeChild(root, expected.path); !Files.isSymbolicLink(file.toPath()) && file.isFile && file.length() == expected.bytes && sha256(file) == expected.sha256 }
    }.getOrDefault(false)

    /** Returns the committed import ID, or null after restoring the old snapshot. */
    suspend fun recover(): String? {
        if (!journal.isFile) {
            require(!before.exists()) { "发现未登记的旧存档，已停止覆盖；请先恢复备份" }
            return null
        }
        val state = strictObject(journal.readBytes())
        val name = state.getString("staging")
        require(name.matches(Regex("import-[a-zA-Z0-9-]+")))
        val staging = safeChild(parent, name)
        if (!state.has("operation_id")) {
            // A previous exporter could create an empty committed directory
            // while the unique old snapshot still existed. Keep that old copy.
            if (before.exists()) {
                if (committed.exists()) move(committed, File(parent, "recovered-unindexed-${java.util.UUID.randomUUID()}"), "legacy-quarantine")
                move(before, committed, "legacy-restored")
            }
            remove(staging); require(journal.delete()); sync(parent)
            return null
        }
        val id = safeId(state.getString("operation_id"))
        val indexed = readIndex()?.optString("import_id") == id
        if (indexed && treeMatches(committed, state.getJSONObject("metadata"))) {
            remove(before); checkpoint("old-cleaned")
            remove(staging); require(journal.delete()); sync(parent); checkpoint("journal-cleaned")
            return id
        }
        if (before.exists()) {
            write(journal, state.put("phase", "restoring"))
            if (committed.exists()) remove(committed)
            move(before, committed, "old-restored")
        } else if (!state.getBoolean("had_old") && state.getString("phase") != "verified") {
            remove(committed)
        } else if (state.getBoolean("had_old")) {
            require(state.getString("phase") in listOf("verified", "restoring") && committed.exists()) { "原存档尚未恢复，已保留恢复记录" }
        }
        if (indexed) putIndex(state.optJSONObject("previous_index"))
        remove(staging); require(journal.delete()); sync(parent)
        return null
    }

    suspend fun publish(staging: File, metadata: JSONObject, index: JSONObject): String {
        require(!journal.exists() && !before.exists() && staging.parentFile == parent)
        require(treeMatches(staging, metadata)) { "暂存存档校验失败" }
        val state = JSONObject().put("operation_id", index.getString("import_id")).put("staging", staging.name)
            .put("had_old", committed.exists()).put("metadata", metadata).put("index", index)
            .put("previous_index", readIndex() ?: JSONObject.NULL).put("phase", "verified")
        write(journal, state); checkpoint("journal-verified")
        if (committed.exists()) move(committed, before, "old-moved")
        write(journal, state.put("phase", "old-moved")); checkpoint("journal-old-moved")
        move(staging, committed, "new-active")
        write(journal, state.put("phase", "new-active")); checkpoint("journal-new-active")
        putIndex(index); checkpoint("index-committed")
        write(journal, state.put("phase", "indexed")); checkpoint("journal-indexed")
        return recover() ?: error("存档导入未能完成，已恢复原存档")
    }
}
