package org.nsa.hall

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import org.nsa.hall.data.HallRepository
import org.nsa.hall.runtime.SessionController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class HallApplication : Application() {
    val repository: HallRepository by lazy { HallRepository(this) }
    private val runtimeScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    val sessionController: SessionController by lazy { SessionController(repository, runtimeScope) }
    override fun onCreate() {
        super.onCreate()
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel("downloads", "游戏下载", NotificationManager.IMPORTANCE_LOW)
        )
    }
}
