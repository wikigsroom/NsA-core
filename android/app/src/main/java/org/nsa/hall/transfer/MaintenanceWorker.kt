package org.nsa.hall.transfer

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.nsa.hall.HallApplication

/** Local retention runs when Android schedules it; it never uploads log files. */
class MaintenanceWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val repository = (applicationContext as HallApplication).repository
        repository.operationLock.withLock {
            if (repository.dao.unfinished().isEmpty()) repository.trimSessionLogs()
        }
        Result.success()
    }
}
