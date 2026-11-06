package org.nsa.hall.runtime

import android.app.AlertDialog
import android.app.NativeActivity
import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.*
import android.os.Process
import android.view.KeyEvent
import android.view.WindowManager
import android.widget.Toast
import androidx.core.content.ContextCompat
import org.json.JSONObject
import org.nsa.hall.security.safeId
import org.nsa.hall.security.sha256
import java.io.File

class GameActivity : NativeActivity() {
    private lateinit var config: JSONObject
    private val handler = Handler(Looper.getMainLooper())
    private var closed = false
    private var menu: AlertDialog? = null
    private var interrupted = false
    private var receiverRegistered = false
    private val audioManager by lazy { getSystemService(AudioManager::class.java) }
    private val audioFocus by lazy {
        AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
            .setOnAudioFocusChangeListener({ change -> if (change != AudioManager.AUDIOFOCUS_GAIN) interruptPlayback() }, handler)
            .build()
    }
    private val noisyOutput = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) { if (intent?.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY) interruptPlayback() }
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        val pending = GameProcess.context
        if (pending == null || GameProcess.consumed || intent.getStringExtra("nonce") != pending.optString("nonce") || intent.getStringExtra("session_id") != pending.optString("session_id")) {
            // NativeActivity must never load code for an unauthenticated or replayed launch.
            finish(); Process.killProcess(Process.myPid()); return
        }
        config = pending
        GameProcess.consumed = true
        val own = filesDir.canonicalFile
        for (key in listOf("package_root", "romfs_root", "shader_root", "save_root", "settings_root", "cache_root", "temp_root", "log_root")) {
            val path = File(config.getString(key)).canonicalFile
            require(path.path.startsWith(own.path + File.separator))
        }
        val library = config.getString("library_name")
        require(library.matches(Regex("libnsa_[a-z0-9_]+\\.so")))
        require(sha256(File(applicationInfo.nativeLibraryDir, library)) == config.getString("library_sha256"))
        // Register JNI commands with this class loader before NativeActivity's dlopen.
        System.loadLibrary("nsa_bootstrap")
        GameProcess.activity = this
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        super.onCreate(savedInstanceState)
        ContextCompat.registerReceiver(this, noisyOutput, IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY), ContextCompat.RECEIVER_NOT_EXPORTED)
        receiverRegistered = true
        if (audioManager.requestAudioFocus(audioFocus) != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) interruptPlayback()
        if (Build.VERSION.SDK_INT >= 33) onBackInvokedDispatcher.registerOnBackInvokedCallback(android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT) { pauseMenu() }
    }
    @Suppress("unused") fun getRuntimeField(key: String): String = when (key) { "library_path" -> File(applicationInfo.nativeLibraryDir, config.getString("library_name")).absolutePath; else -> config.opt(key)?.toString() ?: "" }
    @Suppress("unused") fun onRuntimeEvent(json: String) { if (!closed && json.toByteArray().size <= 16384) GameProcess.send(Protocol.EVENT, json) }
    external fun nativeRequest(command: Int): Int
    external fun nativeDestroy()
    fun requestCommand(command: Int) { if (!closed) runCatching { nativeRequest(command) } }
    private fun interruptPlayback() {
        if (closed) return
        interrupted = true; requestCommand(1)
        if (hasWindowFocus() && !isFinishing) handler.post { pauseMenu() }
    }
    private fun resumeGame() {
        if (audioManager.requestAudioFocus(audioFocus) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
            interrupted = false; requestCommand(2)
        } else {
            interrupted = true; requestCommand(1)
            Toast.makeText(this, "请结束通话或声音占用后，再继续游戏", Toast.LENGTH_LONG).show()
            handler.postDelayed({ pauseMenu() }, 250)
        }
    }
    override fun onPause() { interrupted = true; requestCommand(1); super.onPause() }
    override fun onStop() { audioManager.abandonAudioFocusRequest(audioFocus); super.onStop() }
    override fun onResume() {
        super.onResume()
        if (interrupted) { requestCommand(1); handler.post { if (!isFinishing && !closed) pauseMenu() } }
    }
    private fun pauseMenu() {
        if (closed || menu?.isShowing == true) return
        requestCommand(1)
        menu = AlertDialog.Builder(this).setTitle(config.optString("game_name", "游戏已暂停")).setMessage("请确认当前进度已保存。未保存的进度可能丢失。")
            .setPositiveButton("继续游戏") { _, _ -> resumeGame() }
            .setNeutralButton("返回游戏大厅") { _, _ -> exitConfirmation() }
            .setOnCancelListener { resumeGame() }.show()
    }
    private fun exitConfirmation() {
        AlertDialog.Builder(this).setTitle("退出本次游戏？").setMessage("将返回竖屏大厅。未保存的游戏进度可能丢失。")
            .setNegativeButton("继续游戏") { _, _ -> resumeGame() }
            .setPositiveButton("退出游戏") { _, _ ->
                requestCommand(3)
                handler.postDelayed({ if (!isFinishing && !closed) AlertDialog.Builder(this).setTitle("游戏仍在结束").setMessage("可以继续等待，或结束游戏进程。强制结束不会生成保存成功记录。")
                    .setNegativeButton("继续等待") { _, _ -> requestCommand(3) }
                    .setPositiveButton("强制结束") { _, _ -> GameProcess.send(Protocol.CLOSED, "forced"); finish() }.setCancelable(false).show() }, 10000)
            }.setOnCancelListener { resumeGame() }.show()
    }
    // API 33+ uses the registered platform callback above; NativeActivity needs
    // the legacy route for the two supported versions without that dispatcher.
    @SuppressLint("GestureBackNavigation")
    @Suppress("DEPRECATION") override fun onBackPressed() { if (Build.VERSION.SDK_INT < 33) pauseMenu() }
    @SuppressLint("GestureBackNavigation")
    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean = if (Build.VERSION.SDK_INT < 33 && keyCode == KeyEvent.KEYCODE_BACK) { pauseMenu(); true } else super.onKeyDown(keyCode, event)
    override fun onDestroy() {
        closed = true; handler.removeCallbacksAndMessages(null); menu?.dismiss()
        if (receiverRegistered) unregisterReceiver(noisyOutput)
        audioManager.abandonAudioFocusRequest(audioFocus)
        runCatching { nativeDestroy() }
        GameProcess.activity = null
        GameProcess.send(Protocol.CLOSED)
        super.onDestroy()
        Process.killProcess(Process.myPid())
    }
}
