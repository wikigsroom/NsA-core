package org.nsa.hall.runtime

import android.app.Service
import android.content.Intent
import android.os.*
import android.os.Process
import org.json.JSONObject
import java.util.concurrent.atomic.AtomicLong

object GameProcess {
    var context: JSONObject? = null
    var host: Messenger? = null
    var activity: GameActivity? = null
    var consumed = false
    var lease: RuntimeLease? = null
    private var lastPhase: String? = null
    private var lastSave: String? = null
    @Synchronized fun send(what: Int, json: String = "") {
        if (what == Protocol.EVENT) runCatching {
            if (JSONObject(json).optString("event_type") == "save_committed") lastSave = json else lastPhase = json
        }
        runCatching { host?.send(Message.obtain(null, what).apply { data = Bundle().apply { putString("json", json); putInt("pid", Process.myPid()); putString("session_id", context?.optString("session_id")); putInt("generation", context?.optInt("generation") ?: -1) } }) }
    }
    @Synchronized fun replay() { listOfNotNull(lastPhase, lastSave).distinct().sortedBy { JSONObject(it).getLong("seq") }.forEach { send(Protocol.EVENT, it) } }
}
object Protocol { const val PREPARE = 1; const val PREPARED = 2; const val EVENT = 3; const val COMMAND = 4; const val HEARTBEAT = 5; const val CLOSED = 6; const val RECONNECT = 7; const val UNAVAILABLE = 8 }

class GameProcessService : Service() {
    private var hostBinder: IBinder? = null
    private val hostDeath = IBinder.DeathRecipient { Handler(Looper.getMainLooper()).post { GameProcess.activity?.requestCommand(1) } }
    private fun attach(host: Messenger) {
        runCatching { hostBinder?.unlinkToDeath(hostDeath, 0) }
        hostBinder = host.binder; host.binder.linkToDeath(hostDeath, 0); GameProcess.host = host
    }
    private val handler = Handler(Looper.getMainLooper()) { message ->
        when (message.what) {
            Protocol.PREPARE -> {
                if (GameProcess.context != null || GameProcess.consumed) return@Handler true
                if (message.replyTo?.binder?.isBinderAlive != true) return@Handler true
                val json = JSONObject(message.data.getString("json") ?: return@Handler true)
                GameProcess.lease = RuntimeLease.acquire(filesDir) ?: return@Handler true
                GameProcess.context = json
                attach(message.replyTo)
                GameProcess.send(Protocol.PREPARED, JSONObject().put("session_id", json.getString("session_id")).put("generation", json.getInt("generation")).put("nonce", json.getString("nonce")).toString())
            }
            Protocol.RECONNECT -> {
                val expected = JSONObject(message.data.getString("json") ?: return@Handler true)
                val current = GameProcess.context
                if (current == null) {
                    message.replyTo?.send(Message.obtain(null, Protocol.UNAVAILABLE).apply { data = Bundle().apply { putString("session_id", expected.getString("session_id")); putInt("generation", expected.getInt("generation")); putString("nonce", expected.getString("nonce")); putInt("pid", Process.myPid()) } })
                    return@Handler true
                }
                if (listOf("session_id", "generation", "nonce").any { current.opt(it) != expected.opt(it) } || message.replyTo?.binder?.isBinderAlive != true) return@Handler true
                attach(message.replyTo)
                if (GameProcess.consumed && GameProcess.activity == null) {
                    GameProcess.send(Protocol.UNAVAILABLE, current.toString())
                } else {
                    GameProcess.send(Protocol.PREPARED, JSONObject().put("session_id", current.getString("session_id")).put("generation", current.getInt("generation")).put("nonce", current.getString("nonce")).toString())
                    GameProcess.replay()
                }
            }
            Protocol.COMMAND -> GameProcess.activity?.requestCommand(message.arg1)
        }
        true
    }
    private val messenger = Messenger(handler)
    private val heartbeat = object : Runnable {
        override fun run() { if (GameProcess.context != null) GameProcess.send(Protocol.HEARTBEAT); handler.postDelayed(this, 10000) }
    }
    override fun onCreate() { super.onCreate(); handler.post(heartbeat) }
    override fun onBind(intent: Intent?): IBinder = messenger.binder
    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null); runCatching { hostBinder?.unlinkToDeath(hostDeath, 0) }; super.onDestroy()
        // A still-running NativeActivity retains the process lease across an
        // unbind/main-process death. An unused prepared process owns no save.
        if (!GameProcess.consumed && GameProcess.activity == null) { GameProcess.lease?.close(); GameProcess.lease = null; Process.killProcess(Process.myPid()) }
    }
}
