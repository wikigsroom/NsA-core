package org.nsa.hall.transfer

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject
import org.nsa.hall.BuildConfig
import org.nsa.hall.MainActivity
import org.nsa.hall.data.HallRepository
import org.nsa.hall.security.checkedBytes
import org.nsa.hall.security.sha256
import java.io.File

class AppUpdater(private val repository: HallRepository) {
    suspend fun prepare(metadata: JSONObject): File {
        require(metadata.getString("application_id") == repository.context.packageName && metadata.getLong("version_code") > BuildConfig.VERSION_CODE) { "此更新不适用于当前应用" }
        require(metadata.getInt("min_api") <= Build.VERSION.SDK_INT)
        val artifact = metadata.getJSONObject("apk")
        val access = repository.api.request("/v1/app-update/download")
        require(access.getString("object_id") == artifact.getString("object_id") && access.getString("sha256") == artifact.getString("sha256"))
        val directory = File(repository.context.filesDir, "updates").apply { mkdirs() }
        val file = File(directory, artifact.getString("sha256") + ".apk")
        val total = checkedBytes(artifact.getString("bytes"))
        require(total in 1..(2L * 1024 * 1024 * 1024))
        if (!file.isFile || file.length() != total || sha256(file) != artifact.getString("sha256")) {
            val temporary = File(directory, file.name + ".partial")
            repository.api.open(access.getString("download_url")).use { response ->
                require(response.isSuccessful)
                var bytes = 0L
                response.body!!.byteStream().use { input -> temporary.outputStream().use { output -> val buffer = ByteArray(65536); while (true) { val n = input.read(buffer); if (n < 0) break; bytes = Math.addExact(bytes, n.toLong()); require(bytes <= total); output.write(buffer, 0, n) }; output.fd.sync() } }
                require(bytes == total && sha256(temporary) == artifact.getString("sha256")) { "应用更新包校验失败" }
                require(temporary.renameTo(file))
            }
        }
        val manager = repository.context.packageManager
        @Suppress("DEPRECATION") val current = manager.getPackageInfo(repository.context.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
        @Suppress("DEPRECATION") val next = manager.getPackageArchiveInfo(file.absolutePath, PackageManager.GET_SIGNING_CERTIFICATES) ?: error("无效的应用更新包")
        val approved = metadata.getString("signing_certificate_sha256")
        require(current.signingInfo!!.apkContentsSigners.any { sha256(it.toByteArray()) == approved } && next.signingInfo!!.apkContentsSigners.any { sha256(it.toByteArray()) == approved }) { "应用更新签名与已安装版本不一致" }
        require(next.packageName == repository.context.packageName && next.longVersionCode == metadata.getLong("version_code"))
        repository.operationLock.withLock {
            repository.requireIdle()
            val saves = SaveManager(repository)
            saves.recoverAllUnderOperationLock()
            repository.dao.allGames().filter { it.activeRelease.isNotEmpty() }.forEach { game ->
                if (repository.saveRoot(game.id).walkTopDown().any { it.isFile }) saves.backupUnderOperationLock(game.id, "before-app-update")
            }
        }
        return file
    }
    fun install(activity: MainActivity, file: File) {
        if (!activity.packageManager.canRequestPackageInstalls()) { activity.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, android.net.Uri.parse("package:" + activity.packageName))); return }
        val uri = FileProvider.getUriForFile(activity, activity.packageName + ".files", file)
        activity.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/vnd.android.package-archive").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
    }
}
