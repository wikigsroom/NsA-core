package org.nsa.hall

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewModelScope
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.platform.compositionContext
import androidx.compose.ui.platform.createLifecycleAwareWindowRecomposer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.nsa.hall.data.*
import org.nsa.hall.runtime.SessionController
import org.nsa.hall.ui.HallApp

class HallViewModel(val repository: HallRepository, val controller: SessionController) : ViewModel() {
    private val actionLock = Mutex()
    val games = repository.dao.games().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val transfers = repository.dao.transfers().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val settings = repository.preferences.flow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), Settings(false, true, true, false, BuildConfig.DEFAULT_ENDPOINT, "玩家", "zh-CN", false))
    val session = repository.dao.latestSession().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    val notice = MutableStateFlow("")
    val working = MutableStateFlow(false)
    init { perform { repository.initialize(); if (repository.preferences.get().welcomed) repository.refresh() } }
    fun perform(action: suspend () -> Unit) { viewModelScope.launch { actionLock.withLock { working.value = true; try { withContext(Dispatchers.IO) { action() } } catch (error: Exception) { notice.value = error.message ?: "操作失败，请重试" } finally { working.value = false } } } }
    fun refresh(query: String = "", genre: String = "", more: Boolean = false) = perform { repository.refresh(query, genre, more) }
    fun launch(activity: ComponentActivity, id: String) {
        if (actionLock.isLocked) { notice.value = "请等待当前数据操作完成，再开始游戏"; return }
        viewModelScope.launch { actionLock.withLock { controller.launch(activity, id) } }
    }
    fun dismissNotice() { notice.value = "" }
}
class MainActivity : ComponentActivity() {
    private class HallMotionScale : MotionDurationScale {
        override var scaleFactor: Float by mutableFloatStateOf(1f)
    }
    @OptIn(ExperimentalComposeUiApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val app = application as HallApplication
        val repository = app.repository
        val motionScale = HallMotionScale()
        window.decorView.compositionContext = window.decorView.createLifecycleAwareWindowRecomposer(motionScale, lifecycle)
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                repository.preferences.flow.collect { preference ->
                    motionScale.scaleFactor = if (preference.reduceMotion) 0f else android.provider.Settings.Global.getFloat(contentResolver, android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
                }
            }
        }
        val model = ViewModelProvider(this, object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST") override fun <T : ViewModel> create(modelClass: Class<T>): T = HallViewModel(repository, app.sessionController) as T
        })[HallViewModel::class.java]
        setContent { HallApp(model, this) }
    }
    override fun onResume() { super.onResume(); (application as HallApplication).sessionController.attach(this) }
}
