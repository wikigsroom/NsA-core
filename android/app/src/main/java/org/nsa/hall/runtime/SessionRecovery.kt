package org.nsa.hall.runtime

import androidx.room.withTransaction
import org.nsa.hall.data.HallRepository

internal sealed interface SessionReconciliation {
    data class Idle(val closedSessions: Int) : SessionReconciliation
    data object Owned : SessionReconciliation
    data object Unknown : SessionReconciliation
}

/** Caller holds operationLock and owns no pending launch. A row is not a lease. */
internal suspend fun HallRepository.reconcileStoppedSessionsUnderOperationLock(): SessionReconciliation {
    val lease = try { RuntimeLease.acquire(context.filesDir) }
        catch (_: Exception) { return SessionReconciliation.Unknown }
        ?: return SessionReconciliation.Owned
    return lease.use {
        // Retain exclusive kernel ownership throughout the transaction. An
        // uncertain/held lock must never turn a running session into history.
        database.withTransaction {
            val stopped = dao.unfinished()
            val now = System.currentTimeMillis()
            stopped.forEach { session ->
                dao.putSession(session.copy(state = "crashed", error = "上次游戏进程已结束；存档状态以上次真实写入为准", updated = now))
                dao.deleteFact("runtime-context:${session.id}")
            }
            SessionReconciliation.Idle(stopped.size)
        }
    }
}
