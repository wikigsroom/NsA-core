package org.nsa.hall.runtime

import android.app.Activity
import android.app.ActivityManager
import android.content.*
import android.os.*
import android.os.Process
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.withLock
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.room.withTransaction
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
    val protectedRuntime = MutableStateFlow(false)
    private var game: Messenger? = null
    private var session: SessionRecord? = null
    private var nonce = ""
    private var activity = WeakReference<Activity>(null)
    private var preparedForActivity = false
    @Volatile private var launchToken = ""
    @Volatile private var cancelled = false
    private var finishing = false
    private var recovering = false
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
            Protocol.UNAVAILABLE -> unavailable(data)
        }
    } } }
    /** Reattach using the persisted identity; a database row alone is no proof. */
    suspend fun recover(): Boolean {
        if (session != null || launching.value || finishing || recovering) return false
        recovering = true
        try {
            // Initialization may have observed a live lease just before the
            // old game exited. Recheck now; an empty PID/old row is not proof.
            val ownership = withContext(Dispatchers.IO) { repository.operationLock.withLock {
                repository.reconcileStoppedSessionsUnderOperationLock()
            } }
            if (session != null || launching.value || finishing) return false
            when (ownership) {
                is SessionReconciliation.Idle -> {
                    if (protectedRuntime.value) message.value = ""
                    protectedRuntime.value = false
                    return ownership.closedSessions > 0
                }
                SessionReconciliation.Unknown -> {
                    protectedRuntime.value = true
                    message.value = "无法确认游戏进程状态，继续保护存档；请稍后重试或结束游戏进程"
                    return false
                }
                SessionReconciliation.Owned -> Unit
            }
            val active = withContext(Dispatchers.IO) { repository.dao.unfinished().firstOrNull() }
            protectedRuntime.value = true
            if (active == null) { message.value = "发现尚未结束的游戏进程，请先结束后再管理数据"; return false }
            session = active; launchToken = active.id; cancelled = false
            val stored = withContext(Dispatchers.IO) { repository.dao.fact("runtime-context:${active.id}")?.value?.let { runCatching { JSONObject(it) }.getOrNull() } }
            val storedNonce = stored?.opt("nonce") as? String
            val storedGeneration = stored?.opt("generation") as? Int
            if (stored == null || stored.opt("session_id") != active.id || storedGeneration != active.generation || storedNonce.isNullOrBlank()) {
                message.value = "游戏进程仍占用存档，但恢复信息不完整；可先结束该进程"
                return false
            }
            pendingContext = stored; nonce = storedNonce; launching.value = true
            message.value = "正在重新连接上次游戏；完成前继续保护存档"
            connect(stored, true)
            return false
        } finally {
            recovering = false
        }
    }
    private fun connect(config: JSONObject, reconnect: Boolean) {
        val id = config.getString("session_id")
        connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
                if (cancelled || launchToken != id || finishing) return
                game = Messenger(binder)
                runCatching { binder?.linkToDeath({ scope.launch { connectionLost(id) } }, 0) }
                runCatching { game?.send(Message.obtain(null, if (reconnect) Protocol.RECONNECT else Protocol.PREPARE).apply { replyTo = incoming; data = Bundle().apply { putString("json", config.toString()) } }) }
                    .onFailure { scope.launch { connectionLost(id) } }
            }
            override fun onServiceDisconnected(name: ComponentName?) { scope.launch { connectionLost(id) } }
            override fun onBindingDied(name: ComponentName?) { scope.launch { connectionLost(id) } }
        }
        require(repository.context.bindService(Intent(repository.context, GameProcessService::class.java), connection!!, Context.BIND_AUTO_CREATE))
        timeout?.cancel()
        timeout = scope.launch {
            delay(120000)
            if (session != null && launching.value) message.value = "连接耗时较长；可以结束游戏进程后返回大厅"
            while (session != null) {
                if (lastHeartbeat > 0 && SystemClock.elapsedRealtime() - lastHeartbeat > 60000) message.value = "游戏进程暂时没有响应，可选择结束"
                delay(10000)
            }
        }
    }
    private suspend fun connectionLost(id: String) {
        delay(500)
        if (id != launchToken || finishing) return
        if (!RuntimeLease.held(repository.context.filesDir)) finish("crashed", "游戏进程已结束；可重新启动或导出诊断", id)
        else { launching.value = false; message.value = "与游戏失去连接，存档仍受保护；请重试连接或结束游戏" }
    }
    private suspend fun unavailable(bundle: Bundle) {
        val active = session ?: return
        val json = runCatching { JSONObject(bundle.getString("json") ?: "{}") }.getOrNull() ?: return
        val suppliedNonce = bundle.getString("nonce") ?: json.optString("nonce")
        if (bundle.getString("session_id") != active.id || bundle.getInt("generation", -1) != active.generation || suppliedNonce != nonce) return
        pid = bundle.getInt("pid")
        finish("crashed", "原游戏会话已结束；存档以上次真实写入为准", active.id)
    }
    private fun belongsToSession(data: Bundle): Boolean = session?.let {
        !finishing && data.getString("session_id") == it.id && data.getInt("generation", -1) == it.generation && data.getInt("pid") == pid
    } ?: false
    fun launch(source: Activity, gameId: String) {
        if (launching.value || finishing || recovering) return
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
                SaveManager(repository).recoverUnderOperationLock(gameId)
                repository.saveRoot(gameId).mkdirs()
                val created = repository.createSessionUnderOperationLock(id, gameId, selection.second.releaseId)
                selection to created
            } }
            if (cancelled || launchToken != id || finishing) return
            session = pendingSession
            val (record, install) = selection
            // Retain at most 10 MiB of old logs before the native writer uses
            // its bounded 40 MiB active-session rotation budget.
            withContext(Dispatchers.IO) { repository.trimSessionLogs(10L * 1024 * 1024) }
            if (cancelled || launchToken != id || finishing) return
            withContext(Dispatchers.IO) {
            val manifest = JSONObject(install.manifest); val slot = repository.trust.slot(manifest)
            nonce = UUID.randomUUID().toString() + UUID.randomUUID().toString()
            val generation = pendingSession.generation
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
            repository.dao.putFact(Fact("runtime-context:$id", pendingContext.toString()))
            }
            if (cancelled || launchToken != id || finishing) return
            connect(pendingContext!!, false)
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
        protectedRuntime.value = true
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
            repository.database.withTransaction {
                val old = repository.dao.fact("save-index:${active.gameId}")?.value?.let(::JSONObject)
                val install = repository.dao.install(active.gameId, active.releaseId) ?: error("运行版本记录缺失")
                val index = JSONObject().put("kind", "native").put("revision", Math.addExact(old?.optLong("revision") ?: 0, 1))
                    .put("native_commit_seq", seq).put("session_id", active.id).put("source_release_id", active.releaseId)
                    .put("compatibility_group", JSONObject(install.manifest).getJSONObject("save").getString("compatibility_group"))
                    .put("committed_at", java.time.Instant.now().toString())
                repository.dao.putFact(Fact("save-seq:${active.gameId}", seq.toString()))
                repository.dao.putFact(Fact("save-index:${active.gameId}", index.toString()))
                session = active.copy(seq = event.getLong("seq"), saveSeq = seq, updated = System.currentTimeMillis())
                repository.dao.putSession(session!!)
            }
        } else session = active.copy(seq = event.getLong("seq"), state = type, error = event.optString("error_code"), updated = System.currentTimeMillis())
        repository.dao.putSession(session!!)
        if (type == "interactive") repository.dao.gamePlayed(active.gameId, System.currentTimeMillis())
        }
        if (event.optString("event_type") == "fatal_error") finish("failed", "游戏运行中断 · ${event.optString("error_code")}", event.optString("session_id"))
        if (event.optString("event_type") == "exit_complete") finish("ended", expectedId = event.optString("session_id"))
    }
    fun cancel() { cancelled = true; runCatching { game?.send(Message.obtain(null, Protocol.COMMAND).apply { arg1 = 4 }) }; val id = launchToken; scope.launch { finish("cancelled", "已取消启动", id) } }
    fun reconnect() { if (finishing) return; val config = pendingContext ?: run { message.value = "恢复信息不完整，请先结束游戏进程；当前存档仍受保护"; return }; connection?.let { runCatching { repository.context.unbindService(it) } }; cancelled = false; launching.value = true; connect(config, true) }
    fun forceExit() {
        scope.launch {
            if (launchToken.isEmpty()) {
                val old = repository.dao.unfinished().firstOrNull()
                launchToken = old?.id ?: UUID.randomUUID().toString(); session = old
            }
            finish("cancelled", "已结束游戏进程；没有额外生成存档", launchToken, terminateUnknown = true)
        }
    }
    private fun killOwnedGameProcess() {
        repository.context.getSystemService(ActivityManager::class.java).runningAppProcesses.orEmpty()
            .filter { it.uid == Process.myUid() && it.processName == repository.context.packageName + ":game" && it.pid != Process.myPid() }
            .forEach { Process.killProcess(it.pid) }
    }
    private suspend fun finish(state: String, error: String = "", expectedId: String = launchToken, terminateUnknown: Boolean = false) {
        if (finishing || expectedId.isEmpty() || expectedId != launchToken) return
        finishing = true; cancelled = true
        val current = session
        timeout?.cancel(); timeout = null
        connection?.let { runCatching { repository.context.unbindService(it) } }; connection = null
        repository.context.stopService(Intent(repository.context, GameProcessService::class.java))
        if (current != null || pendingContext != null || terminateUnknown) killOwnedGameProcess()
        repeat(50) { if (RuntimeLease.held(repository.context.filesDir)) delay(100) }
        if (RuntimeLease.held(repository.context.filesDir)) {
            protectedRuntime.value = true
            cancelled = false; finishing = false; launching.value = false
            message.value = "游戏进程尚未确认结束，继续保护资源与存档；请稍后重试结束"
            return
        }
        session = null
        protectedRuntime.value = false
        game = null; pid = 0; pendingContext = null; activity.clear(); nonce = ""; preparedForActivity = false; lastHeartbeat = 0
        try {
            withContext(Dispatchers.IO) { repository.operationLock.withLock {
                val saved = repository.dao.session(expectedId) ?: current
                if (saved != null) repository.dao.putSession(saved.copy(state = state, error = error, updated = System.currentTimeMillis()))
                repository.dao.deleteFact("runtime-context:$expectedId")
                repository.trimSessionLogs()
            } }
            repository.scheduleDownloads()
        } finally {
            launchToken = ""; launching.value = false; message.value = error; finishing = false
        }
    }
}
