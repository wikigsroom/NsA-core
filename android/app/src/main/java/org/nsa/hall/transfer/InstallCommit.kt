package org.nsa.hall.transfer

import org.json.JSONObject
import java.io.File

/** Portable publication protocol; caller owns transfer lifetime + installation lock. */
class InstallCommit(
    private val journalFile: File,
    private val journal: JSONObject,
    private val target: File,
    private val staging: File,
    private val replaced: File,
    private val sync: (File) -> Unit,
    private val rename: (File, File) -> Unit,
    private val write: (File, JSONObject) -> Unit,
    private val valid: (File) -> Boolean,
    private val activate: suspend () -> Unit,
    private val complete: suspend () -> Unit,
    private val cleanupDownload: () -> Unit,
    private val checkpoint: (String) -> Unit = {}
) {
    private fun phase(value: String) { write(journalFile, journal.put("phase", value)); checkpoint(value) }
    private fun remove(file: File) = deletePrivateTree(file)
    private fun move(from: File, to: File, point: String) { rename(from, to); sync(target.parentFile!!); checkpoint(point) }

    /** Returns false for an unverified draft. An exception retains the journal. */
    suspend fun recover(allowActivation: Boolean = true): Boolean {
        val current = journal.getString("phase")
        if (current in listOf("CREATED", "EXTRACTED")) return false
        require(current in listOf("VERIFIED", "DIRECTORY_PUBLISHED", "DB_ACTIVATED", "CLEANED"))
        if (current in listOf("VERIFIED", "DIRECTORY_PUBLISHED")) {
            require(allowActivation && !journal.optBoolean("abandoned")) { "资源操作已取消，等待安全清理" }
            // An old same-release target may still exist at VERIFIED. Its
            // existence does not mean the new candidate was published.
            if (!valid(target)) {
                val candidate = when { valid(staging) -> staging; valid(replaced) -> replaced; else -> error("安装候选不完整，原版本与恢复记录已保留") }
                syncDirectoryTree(candidate, sync)
                if (target.exists()) {
                    if (candidate == staging && !replaced.exists()) move(target, replaced, "old-replaced")
                    else { remove(target); sync(target.parentFile!!) }
                }
                move(candidate, target, "candidate-published")
            }
            require(valid(target)) { "发布目录校验未通过" }
            syncDirectoryTree(target, sync); sync(target.parentFile!!)
            phase("DIRECTORY_PUBLISHED")
            activate(); checkpoint("database-committed")
            phase("DB_ACTIVATED")
        }
        // DB_ACTIVATED never reactivates an older release during delayed GC.
        complete(); checkpoint("transfer-completed")
        cleanupDownload(); checkpoint("download-cleaned")
        remove(staging)
        if (replaced.exists()) { require(valid(target)) { "旧副本仍需保留，等待资源恢复" }; remove(replaced) }
        sync(target.parentFile!!)
        phase("CLEANED")
        require(journalFile.delete()); sync(journalFile.parentFile!!); checkpoint("journal-cleaned")
        return true
    }
}
