package org.nsa.hall.data

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.system.Os
import android.system.OsConstants
import org.json.JSONObject
import org.nsa.hall.security.sha256

object DeviceCapabilities {
    private external fun nativeProbe(): String
    private val graphics by lazy {
        runCatching {
            System.loadLibrary("nsa_bootstrap")
            JSONObject(nativeProbe())
        }.getOrElse { JSONObject() }
    }
    fun read(context: Context): JSONObject {
        val result = JSONObject().put("api_level", Build.VERSION.SDK_INT)
            .put("abi", Build.SUPPORTED_ABIS.firstOrNull() ?: "unknown")
            .put("page_size_bytes", Os.sysconf(OsConstants._SC_PAGESIZE).toInt())
            .put("ram_bytes", ActivityManager.MemoryInfo().also { context.getSystemService(ActivityManager::class.java).getMemoryInfo(it) }.totalMem.toString())
        val gpu = graphics
        if (gpu.has("gpu_family")) {
            result.put("gpu_family", gpu.getString("gpu_family").take(80))
                .put("gpu_driver_digest", sha256((gpu.getString("driver_marker") + ":" + Build.FINGERPRINT).toByteArray()))
                .put("vulkan_api", gpu.getString("vulkan_api"))
                .put("features", gpu.getJSONArray("features"))
        }
        return result
    }
}
