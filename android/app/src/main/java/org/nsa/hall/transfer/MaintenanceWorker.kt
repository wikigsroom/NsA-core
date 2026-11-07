package org.nsa.hall.transfer

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.nsa.hall.HallApplication
import org.nsa.hall.runtime.RuntimeLease

/** Local retention runs when Android schedules it; it never uploads log files. */
class MaintenanceWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val repository = (applicationContext as HallApplication).repository
        val lease = TransferLease.acquire(repository.context.filesDir) ?: return@withContext Result.retry()
        lease.use {
            repository.initialize()
            repository.operationLock.withLock {
                if (repository.dao.unfinished().isEmpty() && !RuntimeLease.held(repository.context.filesDir)) {
                    Installation(repository).recover()
                    repository.trimSessionLogs()
                }
            }
        }
        Result.success()
    }
}
