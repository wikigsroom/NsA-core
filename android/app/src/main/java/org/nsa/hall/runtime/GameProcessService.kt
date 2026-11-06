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
    fun send(what: Int, json: String = "") {
        runCatching { host?.send(Message.obtain(null, what).apply { data = Bundle().apply { putString("json", json); putInt("pid", Process.myPid()); putString("session_id", context?.optString("session_id")); putInt("generation", context?.optInt("generation") ?: -1) } }) }
    }
}
object Protocol { const val PREPARE = 1; const val PREPARED = 2; const val EVENT = 3; const val COMMAND = 4; const val HEARTBEAT = 5; const val CLOSED = 6 }

class GameProcessService : Service() {
    private val handler = Handler(Looper.getMainLooper()) { message ->
        when (message.what) {
            Protocol.PREPARE -> {
                if (GameProcess.context != null || GameProcess.consumed) return@Handler true
                val json = JSONObject(message.data.getString("json") ?: return@Handler true)
                GameProcess.context = json
                GameProcess.host = message.replyTo
                GameProcess.send(Protocol.PREPARED, JSONObject().put("session_id", json.getString("session_id")).put("generation", json.getInt("generation")).put("nonce", json.getString("nonce")).toString())
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
    override fun onDestroy() { handler.removeCallbacksAndMessages(null); super.onDestroy() }
}
