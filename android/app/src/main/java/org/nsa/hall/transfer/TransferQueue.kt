package org.nsa.hall.transfer

import org.nsa.hall.data.TransferRecord
import java.io.Closeable
import java.io.File
import java.io.RandomAccessFile
import java.nio.channels.FileLock
import java.nio.channels.OverlappingFileLockException

/** Select again after each operation; a committed late arrival is not a snapshot. */
suspend fun drainTransferQueue(read: suspend () -> List<TransferRecord>, perform: suspend (TransferRecord) -> Unit) {
    val attempted = mutableSetOf<String>()
    while (true) {
        val next = orderedTransfers(read()).firstOrNull { it.id !in attempted &&
            (it.intent == "cancel" && it.state !in listOf("completed", "cancelled") ||
             it.intent == "run" && it.ownerGameId == it.gameId && it.state !in listOf("completed", "cancelled", "failed")) } ?: return
        attempted += next.id
        perform(next)
    }
}

fun orderedTransfers(rows: List<TransferRecord>) = rows.sortedWith(compareBy<TransferRecord> { it.created }.thenBy { it.id })

/** Also covers a stopped old runner until its last blocking I/O/handle is closed. */
class TransferLease private constructor(private val file: RandomAccessFile, private val lock: FileLock) : Closeable {
    override fun close() { try { lock.release() } finally { file.close() } }
    companion object {
        fun acquire(root: File): TransferLease? {
            require(root.isDirectory || root.mkdirs())
            val file = RandomAccessFile(File(root, "content-transfer.lock"), "rw")
            try {
                val lock = try { file.channel.tryLock() } catch (_: OverlappingFileLockException) { null }
                if (lock == null) { file.close(); return null }
                return TransferLease(file, lock)
            } catch (error: Exception) { file.close(); throw error }
        }
    }
}
