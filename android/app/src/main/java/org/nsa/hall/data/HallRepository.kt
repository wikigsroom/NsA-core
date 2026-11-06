package org.nsa.hall.data

import android.content.Context
import android.os.Build
import android.os.StatFs
import androidx.room.withTransaction
import androidx.work.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import org.nsa.hall.BuildConfig
import org.nsa.hall.security.*
import org.nsa.hall.transfer.TransferWorker
import org.nsa.hall.transfer.Installation
import java.io.File
import java.net.URLEncoder
import java.util.UUID
import java.util.concurrent.atomic.AtomicLong

class HallRepository(val context: Context) {
    val database = HallDatabase.open(context)
    val dao = database.dao()
    val preferences = Preferences(context)
    val api = Api(context, preferences)
    val trust = Trust(context)
    val catalog = MutableStateFlow<List<JSONObject>>(emptyList())
    val catalogueError = MutableStateFlow("")
    val catalogueUpdated = MutableStateFlow("")
    val busy = MutableStateFlow(false)
    val facts = File(context.filesDir, "content").apply { mkdirs() }
    val transferRoot = File(context.filesDir, "transfers").apply { mkdirs() }
    val operationLock = Mutex()
    private var cursor: String? = null
    private val catalogueRevision = AtomicLong()
    private val initializationLock = Mutex()
    private var initialized = false
    fun releaseRoot(game: String, release: String) = File(facts, "${safeId(game)}/versions/${safeId(release)}")
    fun saveRoot(game: String) = File(context.filesDir, "players/local/saves/${safeId(game)}/committed").apply { mkdirs() }
    fun settingRoot(game: String) = File(context.filesDir, "players/local/settings/${safeId(game)}").apply { mkdirs() }
    fun cacheRoot(game: String, manifest: JSONObject): File {
        val binding = sha256((manifest.getJSONObject("runtime").toString() + trust.slot(manifest).getString("library_sha256") + capability().optString("gpu_driver_digest") + Build.FINGERPRINT).toByteArray())
        return File(context.filesDir, "runtime-cache/${safeId(game)}/$binding").apply { mkdirs() }
    }
    suspend fun initialize() = withContext(Dispatchers.IO) {
      initializationLock.withLock {
        if (initialized) return@withLock
        val cache = dao.fact("catalogue")?.value
        if (cache != null) catalog.value = JSONArray(cache).objects()
        catalogueUpdated.value = dao.fact("catalogue-updated")?.value.orEmpty()
        trimSessionLogs()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork("local-maintenance", ExistingPeriodicWorkPolicy.KEEP, PeriodicWorkRequestBuilder<org.nsa.hall.transfer.MaintenanceWorker>(12, java.util.concurrent.TimeUnit.HOURS).build())
        dao.fact("policy")?.value?.let { trust.revokedKeys = JSONObject(it).getJSONArray("revoked_key_ids").strings().toSet() }
        Installation(this@HallRepository).recover()
        dao.unfinished().forEach { dao.putSession(it.copy(state = "crashed", error = "上次游戏进程已结束；存档状态以上次真实写入为准", updated = System.currentTimeMillis())) }
        scheduleDownloads()
        initialized = true
      }
    }
    suspend fun refresh(query: String = "", genre: String = "", more: Boolean = false) = withContext(Dispatchers.IO) {
        val revision = catalogueRevision.incrementAndGet()
        val requestCursor = if (more) cursor else null
        busy.value = true
        try {
            val params = buildList { add("limit=24"); if (query.isNotBlank()) add("q=" + URLEncoder.encode(query.take(64), "UTF-8")); if (genre.isNotBlank()) add("genre=" + URLEncoder.encode(genre, "UTF-8")); if (requestCursor != null) add("cursor=" + URLEncoder.encode(requestCursor, "UTF-8")) }
            val page = api.request("/v1/catalogue?" + params.joinToString("&"))
            val items = page.getJSONArray("items").objects()
            if (items.isNotEmpty()) {
                val evaluated = api.request("/v1/compatibility/evaluate", JSONObject().put("app_version_code", BuildConfig.VERSION_CODE).put("runtime_registry_sha256", trust.registryHash).put("capability", capability()).put("release_ids", JSONArray(items.map { it.getString("release_id") })))
                val byRelease = evaluated.getJSONArray("evaluations").objects().associateBy { it.getString("release_id") }
                items.forEach { item -> byRelease[item.getString("release_id")]?.getJSONObject("compatibility")?.let { item.put("compatibility", it) } }
            }
            if (revision != catalogueRevision.get()) return@withContext
            catalog.value = if (more) (catalog.value + items).distinctBy { it.getString("game_id") } else items
            cursor = page.optString("next_cursor").takeUnless { it == "null" || it.isBlank() }
            catalogueUpdated.value = page.optString("updated_at")
            if (query.isBlank() && genre.isBlank()) { dao.putFact(Fact("catalogue", JSONArray(catalog.value).toString())); dao.putFact(Fact("catalogue-updated", catalogueUpdated.value)) }
            items.forEach { summary -> val id = summary.getString("game_id"); val old = dao.game(id); dao.putGame((old ?: GameRecord(id, summary.toString())).copy(summary = summary.toString())) }
            catalogueError.value = ""
            runCatching { updatePolicies() }
        } catch (error: Exception) { if (revision == catalogueRevision.get()) catalogueError.value = error.message ?: "无法连接游戏目录" } finally { if (revision == catalogueRevision.get()) busy.value = false }
    }
    suspend fun detail(id: String): JSONObject = withContext(Dispatchers.IO) {
        val game = dao.game(id)
        try {
            val detail = api.request("/v1/games/${safeId(id)}")
            val evaluation = api.request("/v1/compatibility/evaluate", JSONObject().put("app_version_code", BuildConfig.VERSION_CODE).put("runtime_registry_sha256", trust.registryHash).put("capability", capability()).put("release_ids", JSONArray().put(detail.getJSONObject("game").getString("release_id"))))
            evaluation.getJSONArray("evaluations").objects().firstOrNull()?.optJSONObject("compatibility")?.let { detail.getJSONObject("game").put("compatibility", it) }
            if (trust.registry.getJSONArray("slots").objects().none { it.getString("runtime_slot_id") == detail.getString("runtime_slot_id") }) {
                detail.getJSONObject("game").put("compatibility", JSONObject().put("status", "app_update_required").put("reason_code", "compiled_slot_missing"))
            }
            dao.putGame((game ?: GameRecord(id, detail.getJSONObject("game").toString())).copy(detail = detail.toString())); detail
        }
        catch (error: Exception) { if (game?.detail?.isNotEmpty() == true) JSONObject(game.detail).put("cached", true) else throw error }
    }
    fun capability() = DeviceCapabilities.read(context)
    suspend fun grant(manifest: JSONObject, objectKey: String, experimental: Boolean): JSONObject {
        val evaluation = api.request("/v1/compatibility/evaluate", JSONObject().put("app_version_code", BuildConfig.VERSION_CODE).put("runtime_registry_sha256", trust.registryHash).put("capability", capability()).put("release_ids", JSONArray().put(manifest.getString("release_id"))))
        return api.request("/v1/download-sessions", JSONObject().put("release_id", manifest.getString("release_id")).put("manifest_sha256", manifest.getString("_verified_sha256")).put("object_id", manifest.getJSONObject(objectKey).getString("object_id")).put("app_version_code", BuildConfig.VERSION_CODE).put("runtime_registry_sha256", trust.registryHash).put("capability_context_id", evaluation.getString("capability_context_id")).put("experimental_acknowledged", experimental))
    }
    suspend fun enqueue(detail: JSONObject, cellular: Boolean, experimental: Boolean) = operationLock.withLock {
        val summary = detail.getJSONObject("game"); val gameId = summary.getString("game_id"); val releaseId = summary.getString("release_id")
        require(dao.allTransfers().none { it.gameId == gameId && it.state !in listOf("completed", "cancelled", "failed") }) { "此游戏已有进行中的任务" }
        val envelope = api.request("/v1/releases/$releaseId/manifest")
        val manifest = trust.verify(envelope.toString().toByteArray(), "release", detail.getString("manifest_sha256"))
        require(manifest.getString("game_id") == gameId && manifest.getString("release_id") == releaseId)
        trust.slot(manifest)
        manifest.put("_verified_sha256", envelope.getString("payload_sha256"))
        manifest.put("_envelope", envelope)
        require(availableBytes() >= additionalBytes(manifest, 0, 0)) { "可用空间不足；需要下载文件、展开文件与安全预留空间" }
        // Recheck server authorization before persisting a task. The worker
        // obtains its own short-lived grant when it starts or resumes.
        grant(manifest, "archive", experimental)
        dao.putTransfer(TransferRecord(UUID.randomUUID().toString(), gameId, releaseId, manifest.toString(), total = checkedBytes(manifest.getJSONObject("archive").getString("bytes")), cellularApproved = cellular, experimentalApproved = experimental))
        scheduleDownloads()
    }
    fun availableBytes() = StatFs(context.filesDir.absolutePath).availableBytes
    fun additionalBytes(manifest: JSONObject, downloaded: Long, installed: Long): Long {
        val archive = checkedBytes(manifest.getJSONObject("archive").getString("bytes")); val expanded = checkedBytes(manifest.getString("unpacked_bytes")); val working = checkedBytes(manifest.getString("working_bytes"))
        val reserve = maxOf(2L * 1024 * 1024 * 1024, Math.addExact(expanded / 20, if (expanded % 20 > 0) 1L else 0L))
        return Math.addExact(Math.addExact((archive - downloaded).coerceAtLeast(0), (expanded - installed).coerceAtLeast(0)), Math.addExact(working, reserve))
    }
    fun scheduleDownloads() {
        val work = OneTimeWorkRequestBuilder<TransferWorker>().setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()).setBackoffCriteria(BackoffPolicy.EXPONENTIAL, java.time.Duration.ofSeconds(10)).build()
        WorkManager.getInstance(context).enqueueUniqueWork("content-transfer", ExistingWorkPolicy.KEEP, work)
    }
    suspend fun transferAction(id: String, action: String) {
        val row = dao.transfer(id) ?: return
        when (action) {
            "pause" -> dao.putTransfer(row.copy(intent = "pause", state = "paused"))
            "resume", "retry" -> { dao.putTransfer(row.copy(intent = "run", state = "queued", error = "", failures = 0)); scheduleDownloads() }
            "cancel" -> { dao.putTransfer(row.copy(intent = "cancel", state = "cancelled")); if (row.state in listOf("paused", "failed", "waiting", "queued")) File(transferRoot, safeId(id)).deleteRecursively() }
            "cellular" -> { dao.putTransfer(row.copy(cellularApproved = true)); scheduleDownloads() }
        }
    }
    suspend fun favorite(id: String) { dao.game(id)?.let { dao.putGame(it.copy(favorite = !it.favorite)) } }
    suspend fun updatePolicies() {
        val envelope = api.request("/v1/policies"); val policy = trust.verify(envelope.toString().toByteArray(), "policy")
        val old = dao.fact("policy")?.value?.let(::JSONObject)
        require(old == null || policy.getLong("epoch") >= old.getLong("epoch")) { "拒绝过期的目录策略" }
        dao.putFact(Fact("policy", policy.toString()))
        trust.revokedKeys = policy.getJSONArray("revoked_key_ids").strings().toSet()
    }
    suspend fun preflight(gameId: String): Pair<GameRecord, InstallRecord> = operationLock.withLock { preflightUnderOperationLock(gameId) }
    internal suspend fun preflightUnderOperationLock(gameId: String): Pair<GameRecord, InstallRecord> {
        require(dao.unfinished().isEmpty()) { "已有游戏正在运行" }
        require(dao.allTransfers().none { it.gameId == gameId && it.state in listOf("installing", "verifying") }) { "正在校验或安装此游戏" }
        val game = dao.game(gameId) ?: error("游戏不存在")
        val install = dao.install(gameId, game.activeRelease) ?: error("请先下载并安装此游戏")
        val manifest = JSONObject(install.manifest)
        val authoritative = trust.verify(manifest.getJSONObject("_envelope").toString().toByteArray(), "release", manifest.getString("_verified_sha256"))
        require(jsonEquivalent(authoritative, JSONObject(manifest.toString()).apply { remove("_envelope"); remove("_verified_sha256") })) { "本地安装信息已变更，请重新校验" }
        require(manifest.getString("game_id") == gameId && manifest.getString("release_id") == install.releaseId)
        runCatching { kotlinx.coroutines.withTimeout(15000) { updatePolicies() } }
        val policy = dao.fact("policy")?.value?.let(::JSONObject)
        require(policy != null) { "首次启动需要连接目录获取策略" }
        require(policy?.getJSONArray("releases")?.objects()?.none { it.getString("release_id") == install.releaseId && it.getString("state") == "blocked" } != false) { "此版本已暂停运行，请检查游戏更新" }
        if (!manifest.getBoolean("offline_permitted")) updatePolicies()
        trust.slot(manifest)
        val root = releaseRoot(gameId, install.releaseId)
        require(meetsMinimum(capability(), manifest.getJSONObject("minimum_capability"))) { "此设备不满足游戏所需的运行能力" }
        require(File(root, ".ready.json").isFile) { "游戏安装记录不完整，请修复" }
        require(availableBytes() >= maxOf(256 * 1024 * 1024L, checkedBytes(manifest.getString("working_bytes")))) { "运行所需空间不足" }
        return game to install
    }
    suspend fun deleteResources(gameId: String, keepSaves: Boolean) = operationLock.withLock {
        require(dao.unfinished().none { it.gameId == gameId }) { "请先退出游戏" }
        require(dao.allTransfers().none { it.gameId == gameId && it.state !in listOf("completed", "cancelled", "failed") }) { "请先取消下载任务" }
        dao.installs(gameId).forEach { record -> releaseRoot(gameId, record.releaseId).deleteRecursively(); dao.deleteInstall(gameId, record.releaseId) }
        dao.game(gameId)?.let { dao.putGame(it.copy(activeRelease = "")) }
        if (!keepSaves) {
            require(saveRoot(gameId).parentFile!!.deleteRecursively()) { "存档未能完全删除，请重试" }
            val backups = File(context.filesDir, "players/local/backups/${safeId(gameId)}")
            require(!backups.exists() || backups.deleteRecursively()) { "备份未能完全删除，请重试" }
        }
    }
    suspend fun clearCache(gameId: String) = operationLock.withLock {
        require(dao.unfinished().none { it.gameId == gameId }) { "请先退出游戏" }
        val root = File(context.filesDir, "runtime-cache/${safeId(gameId)}")
        require(!root.exists() || root.deleteRecursively()) { "缓存未能完全清理，请重试" }
    }
    fun trimSessionLogs(maximum: Long = 50L * 1024 * 1024) {
        val root = File(context.filesDir, "sessions")
        val logs = root.listFiles().orEmpty().flatMap { File(it, "logs").listFiles().orEmpty().toList() }.filter { it.isFile && !java.nio.file.Files.isSymbolicLink(it.toPath()) }.sortedBy { it.lastModified() }
        var total = logs.sumOf { it.length() }
        val oldest = System.currentTimeMillis() - 7L * 86400000
        for (file in logs) if (file.lastModified() < oldest || total > maximum) {
            val size = file.length()
            if (file.delete()) total -= size
        }
    }
    suspend fun resetSettings(gameId: String) = operationLock.withLock {
        require(dao.unfinished().none { it.gameId == gameId }) { "请先退出游戏" }
        settingRoot(gameId).deleteRecursively()
        dao.game(gameId)?.let { dao.putGame(it.copy(settings = "{}")) }
    }
    suspend fun deleteSaves(gameId: String) = operationLock.withLock {
        require(dao.unfinished().none { it.gameId == gameId }) { "请先退出游戏" }
        // Exported files outside the application remain owned by the player.
        require(saveRoot(gameId).parentFile!!.deleteRecursively()) { "存档未能完全删除，请重试" }
        val backups = File(context.filesDir, "players/local/backups/${safeId(gameId)}")
        require(!backups.exists() || backups.deleteRecursively()) { "备份未能完全删除，请重试" }
    }
    suspend fun rollback(gameId: String) = operationLock.withLock {
        require(dao.unfinished().isEmpty())
        val game = dao.game(gameId) ?: error("游戏不存在")
        val previous = dao.installs(gameId).firstOrNull { it.releaseId != game.activeRelease && it.healthy } ?: error("没有经过验证的可回退版本")
        val current = dao.install(gameId, game.activeRelease) ?: error("当前版本不存在")
        val currentSave = JSONObject(current.manifest).getJSONObject("save")
        val previousSave = JSONObject(previous.manifest).getJSONObject("save")
        require(currentSave.getString("compatibility_group") == previousSave.getString("compatibility_group")) { "存档格式不兼容，不能直接回退" }
        dao.putGame(game.copy(activeRelease = previous.releaseId))
    }
}
fun JSONArray.objects() = (0 until length()).map { getJSONObject(it) }
