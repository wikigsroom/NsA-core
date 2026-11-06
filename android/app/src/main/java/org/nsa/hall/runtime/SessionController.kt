package org.nsa.hall.runtime

import android.app.Activity
import android.content.*
import android.os.*
import android.os.Process
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.withLock
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import org.json.JSONObject
import org.nsa.hall.data.*
import org.nsa.hall.security.safeId
import org.nsa.hall.transfer.SaveManager
import java.io.File
import java.util.UUID
import java.lang.ref.WeakReference

class SessionController(private val repository: HallRepository, private val scope: CoroutineScope) {
    val message = MutableStateFlow("")
    val launching = MutableStateFlow(false)
    private var game: Messenger? = null
    private var session: SessionRecord? = null
    private var nonce = ""
    private var activity = WeakReference<Activity>(null)
    private var preparedForActivity = false
    @Volatile private var launchToken = ""
    @Volatile private var cancelled = false
    private var finishing = false
    private var connection: ServiceConnection? = null
    private var pid = 0
    private var lastHeartbeat = 0L
    private var timeout: Job? = null
    private var pendingContext: JSONObject? = null
    private val events = Channel<Pair<Int, Bundle>>(256)
    private val incoming = Messenger(Handler(Looper.getMainLooper()) { event -> events.trySend(event.what to Bundle(event.data)); true })
    init { scope.launch { for ((kind, data) in events) {
        when (kind) {
            Protocol.PREPARED -> prepared(data)
            Protocol.EVENT -> acceptEvent(data.getString("json") ?: "")
            Protocol.HEARTBEAT -> if (belongsToSession(data)) lastHeartbeat = SystemClock.elapsedRealtime()
            Protocol.CLOSED -> if (belongsToSession(data)) finish(if (data.getString("json") == "forced") "cancelled" else "ended")
        }
    } } }
    private fun belongsToSession(data: Bundle): Boolean = session?.let {
        !finishing && data.getString("session_id") == it.id && data.getInt("generation", -1) == it.generation && data.getInt("pid") == pid
    } ?: false
    fun launch(source: Activity, gameId: String) {
        if (launching.value || finishing) return
        session?.let { active ->
            if (active.gameId == gameId && pid > 0) source.startActivity(Intent(source, GameActivity::class.java).putExtra("session_id", active.id).putExtra("nonce", nonce))
            else message.value = "请先返回或结束当前游戏，再开始另一款游戏"
            return
        }
        val id = UUID.randomUUID().toString(); launchToken = id; cancelled = false
        activity = WeakReference(source)
        launching.value = true; message.value = "正在检查游戏与设备"
        scope.launch { beginLaunch(id, gameId) }
    }
    private suspend fun beginLaunch(id: String, gameId: String) {
        try {
            val (selection, pendingSession) = withContext(Dispatchers.IO) { repository.operationLock.withLock {
                val selection = repository.preflightUnderOperationLock(gameId)
                if (cancelled || launchToken != id) throw CancellationException("Launch cancelled")
                val generation = Math.addExact(repository.dao.fact("session-generation")?.value?.toIntOrNull() ?: 0, 1)
                repository.dao.putFact(Fact("session-generation", generation.toString()))
                val created = SessionRecord(id, gameId, selection.second.releaseId, generation, "validating")
                repository.dao.putSession(created)
                selection to created
            } }
            if (cancelled || launchToken != id || finishing) return
            session = pendingSession
            val (record, install) = selection
            // Retain at most 10 MiB of old logs before the native writer uses
            // its bounded 40 MiB active-session rotation budget.
            withContext(Dispatchers.IO) { repository.trimSessionLogs(10L * 1024 * 1024) }
            if (cancelled || launchToken != id || finishing) return
            val manifest = JSONObject(install.manifest); val slot = repository.trust.slot(manifest)
            nonce = UUID.randomUUID().toString() + UUID.randomUUID().toString()
            val generation = session?.generation ?: return
            SaveManager(repository).recover(gameId)
            val gameSettings = JSONObject(record.settings)
            val nativeSettings = File(repository.settingRoot(gameId), "settings.txt")
            val retained = if (nativeSettings.isFile) nativeSettings.readLines().filter { it.startsWith("ui=") || it.startsWith("opacity=") || it.startsWith("grid=") } else emptyList()
            nativeSettings.writeText((retained + listOf("overlay=" + if (gameSettings.optBoolean("touch_controls", true)) "1" else "0", "rumble=" + if (gameSettings.optBoolean("haptics", true)) "1" else "0", "fps=" + if (gameSettings.optBoolean("show_performance", false)) "1" else "0")).joinToString("\n", postfix = "\n"))
            val packageRoot = repository.releaseRoot(gameId, install.releaseId)
            val sessionRoot = File(repository.context.filesDir, "sessions/$id").apply { mkdirs() }
            fun directory(name: String) = File(sessionRoot, name).apply { mkdirs() }.absolutePath
            pendingContext = JSONObject().put("session_id", id).put("generation", generation).put("nonce", nonce).put("game_id", gameId).put("game_name", JSONObject(record.summary).optString("name", gameId))
                .put("release_id", install.releaseId).put("local_player_id", "local").put("runtime_slot_id", slot.getString("runtime_slot_id"))
                .put("library_name", slot.getString("library_name")).put("library_sha256", slot.getString("library_sha256"))
                .put("core_revision", slot.getString("core_revision")).put("profile_digest", slot.getString("profile_digest")).put("shader_chain_id", slot.getString("shader_chain_id"))
                .put("module_build_ids", manifest.getJSONObject("runtime").getJSONArray("module_build_ids")).put("manifest_sha256", manifest.getString("_verified_sha256"))
                .put("package_root", packageRoot.absolutePath).put("romfs_root", File(packageRoot, "RomFS").absolutePath).put("shader_root", packageRoot.absolutePath)
                .put("save_root", repository.saveRoot(gameId).absolutePath).put("settings_root", repository.settingRoot(gameId).absolutePath)
                .put("cache_root", repository.cacheRoot(gameId, manifest).absolutePath).put("temp_root", directory("temp")).put("log_root", directory("logs")).put("settings_json", record.settings)
            val contextJson = pendingContext.toString()
            connection = object : ServiceConnection {
                override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
                    if (cancelled || launchToken != id || finishing) return
                    game = Messenger(binder)
                    runCatching { binder?.linkToDeath({ scope.launch { delay(500); finish("crashed", "游戏进程意外结束；可重新启动或导出诊断", id) } }, 0) }
                    game?.send(Message.obtain(null, Protocol.PREPARE).apply { replyTo = incoming; data = Bundle().apply { putString("json", contextJson) } })
                }
                override fun onServiceDisconnected(name: ComponentName?) { scope.launch { finish("crashed", "游戏连接已断开", id) } }
            }
            require(repository.context.bindService(Intent(repository.context, GameProcessService::class.java), connection!!, Context.BIND_AUTO_CREATE))
            timeout = scope.launch {
                delay(120000)
                val active = session
                if (active != null && active.state in listOf("validating", "loading_modules", "runtime_entered")) { message.value = "启动耗时较长，可取消并返回大厅" }
                while (session != null) {
                    if (lastHeartbeat > 0 && SystemClock.elapsedRealtime() - lastHeartbeat > 60000) { message.value = "游戏进程暂时没有响应，可选择结束" }
                    delay(10000)
                }
            }
        } catch (error: CancellationException) { finish("cancelled", "已取消启动", id) }
          catch (error: Exception) { finish("failed", error.message ?: "无法启动游戏", id) }
    }
    fun attach(source: Activity) { activity = WeakReference(source); startPreparedActivity() }
    private fun startPreparedActivity() {
        val source = activity.get() ?: return
        val active = session ?: return
        if (cancelled || finishing || !preparedForActivity || !launching.value || source.isFinishing || source.isDestroyed || (source as? LifecycleOwner)?.lifecycle?.currentState?.isAtLeast(Lifecycle.State.RESUMED) != true) return
        source.startActivity(Intent(source, GameActivity::class.java).putExtra("session_id", active.id).putExtra("nonce", nonce))
        preparedForActivity = false; launching.value = false; message.value = ""
    }
    private suspend fun prepared(bundle: Bundle) {
        val active = session ?: return
        val data = JSONObject(bundle.getString("json") ?: return)
        if (data.optString("session_id") != active.id || data.optInt("generation") != active.generation || data.optString("nonce") != nonce || !launching.value) return
        pid = bundle.getInt("pid")
        require(pid != Process.myPid() && pid > 0)
        lastHeartbeat = SystemClock.elapsedRealtime()
        preparedForActivity = true; startPreparedActivity()
    }
    private suspend fun acceptEvent(json: String) {
        if (json.toByteArray().size > 16384) return
        val event = runCatching { JSONObject(json) }.getOrNull() ?: return
        repository.operationLock.withLock {
        val active = session ?: return
        if (finishing || cancelled || launchToken != active.id) return
        if (event.optInt("event_schema") != 1 || event.optString("session_id") != active.id || event.optInt("generation") != active.generation || event.optLong("seq") <= active.seq) return
        val type = event.optString("event_type")
        if (type !in listOf("validating", "loading_modules", "preparing_graphics", "runtime_entered", "first_frame", "interactive", "save_committed", "exit_requested", "exit_complete", "fatal_error")) return
        if (active.state == "exit_requested" && type !in listOf("exit_complete", "fatal_error", "save_committed")) return
        val phases = listOf("validating", "loading_modules", "preparing_graphics", "runtime_entered", "first_frame", "interactive", "exit_requested", "exit_complete")
        if (type in phases && active.state in phases && phases.indexOf(type) < phases.indexOf(active.state)) return
        if (type == "save_committed") {
            val seq = event.optLong("save_commit_seq", -1); if (seq <= active.saveSeq) return
            repository.dao.putFact(Fact("save-seq:${active.gameId}", seq.toString()))
            session = active.copy(seq = event.getLong("seq"), saveSeq = seq, updated = System.currentTimeMillis())
        } else session = active.copy(seq = event.getLong("seq"), state = type, error = event.optString("error_code"), updated = System.currentTimeMillis())
        repository.dao.putSession(session!!)
        if (type == "interactive") repository.dao.game(active.gameId)?.let { repository.dao.putGame(it.copy(lastPlayed = System.currentTimeMillis())) }
        }
        if (event.optString("event_type") == "fatal_error") finish("failed", "游戏运行中断 · ${event.optString("error_code")}", event.optString("session_id"))
        if (event.optString("event_type") == "exit_complete") finish("ended", expectedId = event.optString("session_id"))
    }
    fun cancel() { cancelled = true; runCatching { game?.send(Message.obtain(null, Protocol.COMMAND).apply { arg1 = 4 }) }; val id = launchToken; scope.launch { finish("cancelled", "已取消启动", id) } }
    fun forceExit() { val id = launchToken; if (pid > 0 && pid != Process.myPid()) Process.killProcess(pid); scope.launch { finish("cancelled", "已结束游戏进程；没有额外生成存档", id) } }
    private suspend fun finish(state: String, error: String = "", expectedId: String = launchToken) {
        if (finishing || expectedId.isEmpty() || expectedId != launchToken) return
        finishing = true; cancelled = true
        val current = session; session = null
        timeout?.cancel(); timeout = null
        connection?.let { runCatching { repository.context.unbindService(it) } }; connection = null
        repository.context.stopService(Intent(repository.context, GameProcessService::class.java))
        if (pid > 0 && pid != Process.myPid()) Process.killProcess(pid)
        game = null; pid = 0; pendingContext = null; activity.clear(); nonce = ""; preparedForActivity = false; lastHeartbeat = 0
        try {
            withContext(Dispatchers.IO) { repository.operationLock.withLock {
                val saved = repository.dao.session(expectedId) ?: current
                if (saved != null) repository.dao.putSession(saved.copy(state = state, error = error, updated = System.currentTimeMillis()))
                repository.trimSessionLogs()
            } }
            repository.scheduleDownloads()
        } finally {
            launchToken = ""; launching.value = false; message.value = error; finishing = false
        }
    }
}
