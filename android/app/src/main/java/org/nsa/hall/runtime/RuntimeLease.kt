package org.nsa.hall.runtime

import java.io.Closeable
import java.io.File
import java.io.RandomAccessFile
import java.nio.channels.FileLock
import java.nio.channels.OverlappingFileLockException

/** Kernel-owned lease survives loss of the hall process and dies with :game. */
class RuntimeLease private constructor(private val file: RandomAccessFile, private val lock: FileLock) : Closeable {
    override fun close() { try { lock.release() } finally { file.close() } }
    companion object {
        fun acquire(root: File): RuntimeLease? {
            root.mkdirs()
            val file = RandomAccessFile(File(root, "runtime-session.lock"), "rw")
            try {
                val lock = try { file.channel.tryLock() } catch (_: OverlappingFileLockException) { null }
                if (lock == null) { file.close(); return null }
                return RuntimeLease(file, lock)
            } catch (error: Exception) { file.close(); throw error }
        }
        fun held(root: File): Boolean = try { acquire(root)?.use { false } ?: true } catch (_: Exception) { true }
    }
}
