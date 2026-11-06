package org.nsa.hall.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings as AndroidSettings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.nsa.hall.*
import org.nsa.hall.data.*
import org.nsa.hall.security.sha256
import org.nsa.hall.security.strings
import org.nsa.hall.transfer.*
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

private val Bg = Color(0xFF101317)
private val Surface = Color(0xFF1B2128)
private val Raised = Color(0xFF242C35)
private val Accent = Color(0xFF81D8DF)
private val Secondary = Color(0xFF9EAAB5)
private val Border = Color(0xFF303943)
private val Warning = Color(0xFFE8C47E)
private val Error = Color(0xFFF0A6A6)
private val Palette = darkColorScheme(primary = Accent, onPrimary = Color(0xFF102226), primaryContainer = Color(0xFF28434A), onPrimaryContainer = Accent, secondary = Accent, onSecondary = Bg, secondaryContainer = Color(0xFF28434A), onSecondaryContainer = Accent, background = Bg, onBackground = Color(0xFFF0F4F7), surface = Surface, onSurface = Color(0xFFF0F4F7), surfaceVariant = Raised, onSurfaceVariant = Secondary, surfaceTint = Color.Transparent, surfaceDim = Bg, surfaceBright = Raised, surfaceContainerLowest = Bg, surfaceContainerLow = Surface, surfaceContainer = Surface, surfaceContainerHigh = Raised, surfaceContainerHighest = Raised, outline = Color(0xFF647888), outlineVariant = Border, error = Error)

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun HallApp(model: HallViewModel, activity: MainActivity) {
    val repository = model.repository
    val games by model.games.collectAsStateWithLifecycle()
    val installs by model.installs.collectAsStateWithLifecycle()
    val pendingSave by model.pendingSave.collectAsStateWithLifecycle()
    val transfers by model.transfers.collectAsStateWithLifecycle()
    val settings by model.settings.collectAsStateWithLifecycle()
    val catalogue by repository.catalog.collectAsStateWithLifecycle()
    val networkError by repository.catalogueError.collectAsStateWithLifecycle()
    val catalogueUpdated by repository.catalogueUpdated.collectAsStateWithLifecycle()
    val hasCache by repository.hasCatalogueCache.collectAsStateWithLifecycle()
    val ready by repository.ready.collectAsStateWithLifecycle()
    val initializationError by repository.initializationError.collectAsStateWithLifecycle()
    val loading by repository.busy.collectAsStateWithLifecycle()
    val working by model.working.collectAsStateWithLifecycle()
    val notice by model.notice.collectAsStateWithLifecycle()
    val launchMessage by model.controller.message.collectAsStateWithLifecycle()
    val launching by model.controller.launching.collectAsStateWithLifecycle()
    val protectedRuntime by model.controller.protectedRuntime.collectAsStateWithLifecycle()
    val latest by model.session.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableStateOf("discover") }
    var route by rememberSaveable { mutableStateOf("root") }
    var backStack by rememberSaveable { mutableStateOf(arrayListOf<String>()) }
    val pageStates = rememberSaveableStateHolder()
    var selected by rememberSaveable { mutableStateOf("") }
    var query by rememberSaveable { mutableStateOf("") }
    var genre by rememberSaveable { mutableStateOf("") }
    var catalogueFilter by rememberSaveable { mutableStateOf("verified") }
    var cancelTransfer by rememberSaveable { mutableStateOf("") }
    var detail by remember { mutableStateOf<JSONObject?>(null) }
    var detailRequest by remember { mutableIntStateOf(0) }
    var dialog by rememberSaveable { mutableStateOf("") }
    var cellular by rememberSaveable { mutableStateOf(false) }
    var experimental by rememberSaveable { mutableStateOf(false) }
    var keepSaves by rememberSaveable { mutableStateOf(true) }
    var restoredCatalogueContext by rememberSaveable { mutableStateOf(false) }
    var resultText by rememberSaveable { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    val selectedRecord = games.firstOrNull { it.id == selected }
    val selectedInstall = installs.firstOrNull { it.gameId == selected && it.releaseId == selectedRecord?.activeRelease }
    val selectedTransfer = transfers.firstOrNull { it.gameId == selected && it.state !in listOf("completed", "cancelled") }
    val visibleCatalogue = catalogue.filter { summary ->
        val state = summary.optJSONObject("compatibility")?.optString("status")
        when (catalogueFilter) {
            "installed" -> games.any { it.id == summary.optString("game_id") && it.activeRelease.isNotEmpty() }
            "experimental" -> state != "verified"
            else -> state == "verified"
        }
    }
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri -> model.saveResult("export", uri) }
    val import = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> model.saveResult("import", uri) }
    val notifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { enabled -> model.perform { repository.preferences.boolean("notifications", enabled) } }
    fun go(next: String) { if (next != route) { backStack = ArrayList(if (route == "root") listOf("root") else (backStack + route).takeLast(16)); route = next } }
    fun back() { route = backStack.lastOrNull() ?: "root"; backStack = ArrayList(backStack.dropLast(1)) }
    fun openGame(id: String, next: String = "detail") { selected = id; detail = null; go(next); detailRequest++ }
    fun startGame() {
        val game = games.firstOrNull { it.id == selected }
        if (installs.none { it.gameId == selected && it.releaseId == game?.activeRelease && it.state == "ready" }) go("repair") else model.launch(activity, selected)
    }
    fun transferAction(id: String, action: String) { if (action == "cancel") { cancelTransfer = id; dialog = "cancel-transfer" } else model.perform { repository.transferAction(id, action) } }
    LaunchedEffect(ready) {
        if (ready && !restoredCatalogueContext) { query = repository.catalogueContext.value.optString("query"); genre = repository.catalogueContext.value.optString("genre"); restoredCatalogueContext = true }
    }
    LaunchedEffect(ready, pendingSave?.optString("id"), pendingSave?.optString("stage")) {
        val pending = pendingSave
        if (ready && pending?.optString("stage") == "picker") model.openSavePicker(pending.getString("id")) { kind ->
            if (kind == "export") export.launch("${pending.getString("game_id")}-${System.currentTimeMillis()}.nsa-save.zip") else import.launch(arrayOf("application/zip", "application/octet-stream"))
        }
    }
    LaunchedEffect(ready, route, selected, detailRequest) {
        if (ready && route in listOf("detail", "game-update") && selected.isNotEmpty() && detail == null) {
            model.perform { val restored = repository.detail(selected); withContext(Dispatchers.Main) { if (selected == restored.getJSONObject("game").getString("game_id")) detail = restored } }
        }
    }
    val roots = listOf("discover", "library", "downloads", "settings")
    MaterialTheme(colorScheme = Palette) {
        BackHandler(enabled = route != "root" || tab != "discover") { if (route != "root") back() else tab = "discover" }
        Scaffold(containerColor = Bg, bottomBar = {
            if (ready && settings.welcomed && route == "root") NavigationBar(containerColor = Bg, tonalElevation = 0.dp, modifier = Modifier.border(BorderStroke(1.dp, Border))) {
                listOf(Triple("discover", "发现游戏", Icons.Outlined.Explore), Triple("library", "我的游戏", Icons.Outlined.SportsEsports), Triple("downloads", "下载", Icons.Outlined.Download), Triple("settings", "设置", Icons.Outlined.Settings)).forEach { (key, label, icon) ->
                    NavigationBarItem(selected = tab == key, onClick = { tab = key; query = "" }, icon = { BadgedBox(badge = { if (key == "downloads" && transfers.any { it.state !in listOf("completed", "cancelled") }) Badge(containerColor = Accent, contentColor = Bg) { Text(transfers.count { it.state !in listOf("completed", "cancelled") }.toString()) } }) { Icon(icon, label) } }, label = { Text(label, maxLines = 1) }, colors = NavigationBarItemDefaults.colors(selectedIconColor = Accent, selectedTextColor = Accent, indicatorColor = Accent.copy(alpha = .12f), unselectedIconColor = Secondary, unselectedTextColor = Secondary))
                }
            }
        }) { padding ->
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
                Column(Modifier.widthIn(max = 640.dp).fillMaxWidth().padding(horizontal = 16.dp)) {
                    if (settings.welcomed) Row(Modifier.fillMaxWidth().height(64.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (route != "root") IconButton(onClick = { back() }) { Icon(Icons.Outlined.ArrowBack, "返回") }
                        Text("NsA", fontSize = 29.sp, fontWeight = FontWeight.Black, letterSpacing = (-1).sp)
                        Box(Modifier.padding(horizontal = 16.dp).height(22.dp).width(1.dp).background(Border))
                        Surface(shape = RoundedCornerShape(24.dp), color = Accent.copy(alpha = .16f)) { Icon(Icons.Outlined.Person, "本地玩家", Modifier.padding(8.dp), tint = Accent) }
                        Text(settings.player, Modifier.padding(start = 10.dp), fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.weight(1f))
                        if (loading || working) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    }
                    if (!ready) {
                        PageHeading("正在恢复本地数据", "核对安装、下载任务与存档，完成后显示游戏大厅。")
                        if (initializationError.isBlank()) LinearProgressIndicator(Modifier.fillMaxWidth())
                        else MessageCard("恢复尚未完成", initializationError, Icons.Outlined.Restore, "重试恢复") { model.initialize() }
                    }
                    else if (!settings.welcomed) Welcome { model.perform { repository.preferences.boolean("welcome", true); repository.refresh() } }
                    else pageStates.SaveableStateProvider(if (route == "root") tab else "$tab/$route/$selected") { when (route) {
                        "root" -> when (tab) {
                            "discover" -> LazyColumn(verticalArrangement = Arrangement.spacedBy(16.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
                                item { PageHeading("发现游戏", "找到下一段属于你的旅程") }
                                item { SearchField(query, { query = it }, { go("search"); model.refresh(query, genre) }) }
                                if (networkError.isNotEmpty()) item { MessageCard("暂时无法更新目录", if (hasCache) "正在展示缓存资料（" + catalogueUpdated.ifBlank { "时间未知" } + "）。已安装游戏仍可按离线策略运行。" else "此设备尚无目录缓存，请联网获取游戏列表。可在我的游戏中查看本地安装。", Icons.Outlined.CloudOff, "重试") { model.refresh(query, genre) } }
                                item { CatalogueFilters(catalogueFilter) { catalogueFilter = it } }
                                if (catalogue.isEmpty() && !loading) item { Empty("等待新的旅程", if (networkError.isNotBlank()) "目录连接失败，可前往网络恢复页面检查。" else "当前目录暂无可用游戏。发布后的游戏会显示在这里。", Icons.Outlined.Explore, "刷新目录") { model.refresh() } }
                                if (catalogue.isNotEmpty() && visibleCatalogue.isEmpty()) item { Empty("当前筛选暂无游戏", "本机已验证与实验适配分别展示。也可以加载更多目录内容。", Icons.Outlined.FilterList, "查看实验游戏") { catalogueFilter = "experimental" } }
                                visibleCatalogue.firstOrNull()?.let { featured -> item { Text(if (catalogueFilter == "experimental") "实验游戏 · 仅限受邀测试" else "本期精选", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold); Spacer(Modifier.height(12.dp)); Featured(featured, games.firstOrNull { it.id == featured.optString("game_id") }, { openGame(featured.getString("game_id")) }, null) } }
                                item { GenreChips(genre) { genre = it; model.refresh(query, it) } }
                                items(visibleCatalogue.drop(1), key = { it.getString("game_id") }) { summary -> GameRow(summary, games.firstOrNull { it.id == summary.getString("game_id") }, { openGame(summary.getString("game_id")) }) }
                                if (catalogue.isNotEmpty()) item { SecondaryButton("加载更多") { model.refresh(query, genre, true) } }
                                if (networkError.isNotEmpty()) item { TextButton(onClick = { go("network-error") }) { Text("检查连接与恢复") } }
                            }
                            "library" -> {
                                val installed = games.filter { it.activeRelease.isNotEmpty() && (query.isBlank() || JSONObject(it.summary).optString("name").contains(query, true)) }
                                LazyColumn(verticalArrangement = Arrangement.spacedBy(16.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
                                    item { PageHeading("我的游戏", "随时回到你的游戏世界") }
                                    item { SearchField(query, { query = it }, {}) }
                                    if (installed.isEmpty()) item { Empty(if (query.isBlank()) "游戏库等待你的第一款游戏" else "没有找到相关游戏", "前往发现游戏，选择内容并完成下载，便可在这里继续游玩。", Icons.Outlined.SportsEsports, "发现游戏") { tab = "discover"; query = "" } }
                                    installed.firstOrNull()?.let { game -> item { val usable = installs.any { it.gameId == game.id && it.releaseId == game.activeRelease && it.state == "ready" }; Featured(JSONObject(game.summary), game, { selected = game.id; startGame() }, { openGame(game.id, "manage") }, { openGame(game.id) }, if (usable) "继续游戏" else "资源需要修复") } }
                                    items(installed.drop(1).chunked(2)) { row ->
                                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                            row.forEach { game ->
                                                Box(Modifier.weight(1f)) { CompactGame(JSONObject(game.summary), game) { openGame(game.id) } }
                                            }
                                            if (row.size == 1) Spacer(Modifier.weight(1f))
                                        }
                                    }
                                    val favorites = games.filter { it.favorite && it.activeRelease.isEmpty() }
                                    if (favorites.isNotEmpty()) item { Text("已收藏", style = MaterialTheme.typography.titleLarge) }
                                    items(favorites) { game -> GameRow(JSONObject(game.summary), game) { openGame(game.id) } }
                                }
                            }
                            "downloads" -> Downloads(transfers, games, ::transferAction, { selected = it; go("install") }, { tab = "discover" })
                            "settings" -> ScrollPage {
                                PageHeading("设置", "让每一次打开都更加顺手")
                                Group("常用") { SettingRow("下载偏好", if (settings.wifiOnly) "仅在 Wi-Fi 下自动下载" else "允许移动网络下载", Icons.Outlined.Download) { go("download-settings") }; SettingRow("存储空间", formatBytes(repository.availableBytes()) + " 可用", Icons.Outlined.Storage) { go("storage") }; SettingRow("设备支持", "查看设备信息与适配说明", Icons.Outlined.PhoneAndroid) { go("device") } }
                                Group("体验") { SettingRow("玩家资料", settings.player, Icons.Outlined.Person) { dialog = "player" }; SwitchRow("减少动态效果", "减少页面与弹层动画", settings.reduceMotion) { model.perform { repository.preferences.boolean("reduceMotion", it) } }; SettingRow("权限与通知", "按需授权，不影响浏览游戏", Icons.Outlined.NotificationsNone) { go("permissions") } }
                                Group("支持与说明") { SettingRow("帮助与反馈", "遇到问题时获取帮助", Icons.Outlined.HelpOutline) { go("feedback") }; SettingRow("App 更新", "当前版本 " + BuildConfig.VERSION_NAME, Icons.Outlined.SystemUpdate) { go("app-update") }; SettingRow("隐私与诊断", "由你选择是否上传诊断", Icons.Outlined.PrivacyTip) { go("privacy") }; SettingRow("关于与协议", "NsA 与开源许可", Icons.Outlined.Info) { go("about") } }
                            }
                        }
                        "search" -> ScrollPage {
                            PageHeading("搜索游戏", "按名称、类型查找")
                            SearchField(query, { query = it }, { model.refresh(query, genre) })
                            GenreChips(genre) { genre = it; model.refresh(query, it) }
                            CatalogueFilters(catalogueFilter) { catalogueFilter = it }
                            if (catalogue.isEmpty() && !loading) Empty("暂无搜索结果", "试试其他名称或清除类型筛选。", Icons.Outlined.Search, "清除筛选") { genre = ""; query = ""; model.refresh() }
                            visibleCatalogue.forEach { summary -> GameRow(summary, games.firstOrNull { it.id == summary.getString("game_id") }) { openGame(summary.getString("game_id")) } }
                            if (catalogue.isNotEmpty()) SecondaryButton("加载更多") { model.refresh(query, genre, true) }
                        }
                        "detail", "game-update" -> {
                            val currentDetail = detail
                            if (currentDetail == null) Empty("正在读取游戏资料", "如果连接失败，可返回已安装的游戏库。", Icons.Outlined.SportsEsports, "重试") { openGame(selected, route) }
                            else ScrollPage {
                                val summary = currentDetail.getJSONObject("game")
                                Cover(summary, 212)
                                PageHeading(summary.getString("name"), currentDetail.optString("game_version") + " · " + genreLabel(summary.optString("genre")))
                                Compatibility(summary.optJSONObject("compatibility"))
                                Text(currentDetail.getString("description"), color = Secondary, lineHeight = 26.sp)
                                if (currentDetail.optBoolean("cached")) Notice("当前显示缓存资料；下载与更新需要重新连接目录。")
                                Group("游戏信息") { InfoRow("下载大小", formatBytes(currentDetail.getString("download_bytes").toLong())); InfoRow("展开大小", formatBytes(currentDetail.getString("unpacked_bytes").toLong())); InfoRow("语言", currentDetail.getJSONArray("languages").strings().joinToString("、") { languageLabel(it) }); InfoRow("操作方式", currentDetail.getJSONArray("input_modes").strings().joinToString("、") { inputLabel(it) }); InfoRow("离线游玩", if (currentDetail.getBoolean("offline_permitted")) "首次准备完成后支持" else "需要联网验证") }
                                Notice("本次完整准备至少需要 " + formatBytes(detailSpace(repository, currentDetail)) + " 可用空间（包含下载、展开、工作空间与安全预留）。已有下载任务以任务中的实际进度为准。")
                                if (selectedRecord?.activeRelease?.isNotEmpty() == true) { PrimaryButton(if (selectedInstall?.state == "ready") "继续游戏" else "资源需要修复", Icons.Outlined.PlayArrow) { startGame() }; SecondaryButton("管理游戏") { go("manage") } }
                                if (selectedRecord?.activeRelease != summary.getString("release_id")) when (summary.optJSONObject("compatibility")?.optString("status")) {
                                    "app_update_required" -> PrimaryButton("需要更新 App", Icons.Outlined.SystemUpdate) { go("app-update") }
                                    "unsupported", "blocked" -> Notice("当前设备或版本暂不支持下载，请查看适配说明。")
                                    else -> PrimaryButton(if (route == "game-update") "下载游戏更新" else if (selectedTransfer != null) "查看下载任务" else "下载游戏", Icons.Outlined.Download) { if (selectedTransfer != null) { tab = "downloads"; route = "root" } else { cellular = false; experimental = false; dialog = "download" } }
                                }
                                TextButton(onClick = { model.perform { repository.favorite(selected) } }) { Icon(if (selectedRecord?.favorite == true) Icons.Outlined.Favorite else Icons.Outlined.FavoriteBorder, null); Spacer(Modifier.width(8.dp)); Text(if (selectedRecord?.favorite == true) "已收藏" else "收藏游戏") }
                            }
                        }
                        "install" -> ScrollPage {
                            PageHeading("准备游戏", "下载、校验与安装进度")
                            val task = transfers.lastOrNull { it.gameId == selected }
                            if (task != null) TransferCard(task, selectedRecord?.let { JSONObject(it.summary).optString("name") } ?: "游戏资源", { action -> transferAction(task.id, action) }, {}) else Notice("当前没有进行中的安装任务。")
                            if (task?.state == "completed") PrimaryButton("开始游戏", Icons.Outlined.PlayArrow) { startGame() }
                            SecondaryButton("前往下载中心") { tab = "downloads"; route = "root" }
                        }
                        "manage" -> ScrollPage {
                            PageHeading("管理游戏", selectedRecord?.let { JSONObject(it.summary).optString("name") } ?: selected)
                            Group("游戏与数据") { SettingRow("存档与备份", "导出、导入与查看本地备份", Icons.Outlined.Save) { go("saves") }; SettingRow("操作与画面", "此游戏独立的操作偏好", Icons.Outlined.Tune) { go("game-settings") }; SettingRow("检查游戏更新", "保留存档并检查目录版本", Icons.Outlined.Update) { openGame(selected, "game-update") }; SettingRow("校验与修复", "检查文件完整性", Icons.Outlined.Build) { go("repair") } }
                            SecondaryButton("回退到经过验证的旧版本") { model.perform { repository.rollback(selected); model.notice.value = "已切换旧版本；启动前仍会检查存档兼容性" } }
                            TextButton(onClick = { keepSaves = true; dialog = "delete" }, colors = ButtonDefaults.textButtonColors(contentColor = Error)) { Icon(Icons.Outlined.DeleteOutline, null); Text("删除游戏资源", Modifier.padding(start = 8.dp)) }
                        }
                        "storage" -> ScrollPage {
                            PageHeading("存储空间", "保留进度，合理安排下载空间")
                            Group("设备空间") { InfoRow("当前可用", formatBytes(repository.availableBytes())); InfoRow("下载安全预留", "至少 2 GB，或展开大小的 5%") }
                            Notice("空间估算包含尚未下载的文件、尚未展开的文件与工作空间；已实际占用的数据不会重复计算。")
                            games.filter { it.activeRelease.isNotEmpty() }.forEach { game -> GameRow(JSONObject(game.summary), game) { openGame(game.id, "manage") } }
                            SecondaryButton("清理可重建的临时文件") { model.perform { require(repository.dao.unfinished().isEmpty()) { "请先退出游戏" }; File(activity.cacheDir, "exports").deleteRecursively(); File(activity.cacheDir, "save-import").deleteRecursively(); model.notice.value = "临时导出文件已清理，游戏与存档已保留" } }
                        }
                        "saves" -> ScrollPage {
                            PageHeading("存档与备份", "游戏真实保存后，进度才会更新")
                            Notice("退出游戏后再操作备份。自动备份会在更新与导入前保留原有存档；卸载 App 会删除未导出的本地数据。")
                            PrimaryButton("导出游戏存档", Icons.Outlined.Upload) { model.beginSave(selected, "export", route) }
                            SecondaryButton("导入存档备份") { model.beginSave(selected, "import", route) }
                            Group("本地备份") {
                                val backups = File(activity.filesDir, "players/local/backups/$selected").listFiles()?.sortedByDescending { it.lastModified() }.orEmpty()
                                if (backups.isEmpty()) Text("暂无自动备份", color = Secondary)
                                backups.forEach { file -> SettingRow(file.name.substringBefore(".nsa-save"), formatBytes(file.length()), Icons.Outlined.FolderZip) {
                                    val uri = FileProvider.getUriForFile(activity, activity.packageName + ".files", file)
                                    runCatching { activity.startActivity(Intent(Intent.ACTION_SEND).setType("application/zip").putExtra(Intent.EXTRA_STREAM, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)) }.onFailure { model.notice.value = "无法分享备份，请使用导出功能" }
                                } }
                            }
                            TextButton(onClick = { dialog = "delete-saves" }, colors = ButtonDefaults.textButtonColors(contentColor = Error)) { Text("删除此游戏的存档与本地备份") }
                        }
                        "game-settings" -> ScrollPage {
                            PageHeading("操作与画面", "仅应用到此游戏，下次启动生效")
                            val gameSettings = selectedRecord?.settings?.let(::JSONObject) ?: JSONObject()
                            fun save(key: String, value: Any) = model.perform { val game = repository.dao.game(selected) ?: return@perform; repository.dao.putGame(game.copy(settings = JSONObject(game.settings).put(key, value).toString())) }
                            Group("操作") { SwitchRow("触屏手柄", "显示屏幕上的操作按钮", gameSettings.optBoolean("touch_controls", true)) { save("touch_controls", it) }; SwitchRow("操作震动", "按系统许可提供轻量反馈", gameSettings.optBoolean("haptics", true)) { save("haptics", it) } }
                            SecondaryButton("重置此游戏的设置与布局") { dialog = "reset-settings" }
                            Group("画面与声音") { SwitchRow("显示性能信息", "实际运行时显示可用的性能数据", gameSettings.optBoolean("show_performance", false)) { save("show_performance", it) }; SwitchRow("游戏声音", "关闭后下次启动保持静音", gameSettings.optBoolean("audio", true)) { save("audio", it) } }
                            Notice("当前使用 App 内置的已登记系统驱动。高级画面参数只会在运行组件明确支持时开放。")
                        }
                        "download-settings" -> ScrollPage {
                            PageHeading("下载偏好", "大文件下载，由你决定")
                            Group("网络与提醒") { SwitchRow("仅在 Wi-Fi 下自动下载", "移动网络需要对任务单独确认", settings.wifiOnly) { model.perform { repository.preferences.boolean("wifi", it); repository.scheduleDownloads() } }; SwitchRow("完成与异常通知", "下载仍会受系统通知权限控制", settings.notifications) { enabled -> if (enabled && Build.VERSION.SDK_INT >= 33) notifications.launch(Manifest.permission.POST_NOTIFICATIONS) else model.perform { repository.preferences.boolean("notifications", enabled) } } }
                            Notice("一次下载一个游戏。主动暂停的任务不会因网络恢复而自动继续；可在下载中心手动恢复。")
                        }
                        "device" -> ScrollPage {
                            PageHeading("设备支持", "适配结论以真实设备证据为准")
                            val cap = repository.capability()
                            Group("当前设备") { InfoRow("设备型号", Build.MANUFACTURER + " " + Build.MODEL); InfoRow("Android", Build.VERSION.RELEASE + " · API " + Build.VERSION.SDK_INT); InfoRow("处理器架构", cap.optString("abi")); InfoRow("内存", formatBytes(cap.optString("ram_bytes", "0").toLong())); InfoRow("系统页大小", cap.optString("page_size_bytes") + " bytes") }
                            Notice("实验适配不代表已验证可玩。完整验证需要实际输入、声音、存档与重新读取，以及对应场景的性能记录。")
                            Group("适配状态说明") { InfoRow("已验证", "当前设备组合有完整试玩证据"); InfoRow("实验适配", "仅限受邀测试，并需主动确认"); InfoRow("暂不支持", "设备能力或内置组件不满足条件"); InfoRow("需要更新 App", "当前 App 缺少对应运行组件") }
                        }
                        "feedback" -> FeedbackForm(settings.diagnostics, { go("privacy") }) { category, description, contact, diagnostics -> model.perform {
                            val body = JSONObject().put("category", category).put("description", description)
                            if (contact.isNotBlank()) body.put("contact", contact)
                            if (selected.isNotEmpty()) body.put("game_id", selected)
                            if (diagnostics) body.put("diagnostic_upload_id", uploadDiagnostic(repository))
                            val receipt = repository.api.request("/v1/feedback", body)
                            withContext(Dispatchers.Main) { resultText = receipt.getString("ticket_id"); go("feedback-done") }
                        } }
                        "feedback-done" -> ScrollPage { Empty("反馈已提交", "我们已收到你的描述。保留编号以便跟进。", Icons.Outlined.CheckCircle, "完成") { route = "root" }; Group("反馈编号") { Text(resultText, color = Accent) } }
                        "app-update" -> UpdatePage(repository, model, activity)
                        "privacy" -> ScrollPage {
                            PageHeading("隐私与诊断", "让数据的使用清楚可见")
                            Group("本地保存") { Text("游戏库、下载进度、偏好与游戏存档保存在此设备。浏览目录使用匿名安装凭据；无需创建玩家账号。", color = Secondary) }
                            Group("可选诊断") { SwitchRow("允许附加诊断", "默认关闭；反馈提交时仍需本次勾选", settings.diagnostics) { model.perform { repository.preferences.boolean("diagnostics", it) } }; Text("诊断仅包含设备能力、版本与经过筛选的状态，不包含密钥、存档内容、原始游戏文件或访问凭据。上传上限 10 MB，服务保留 30 天。", color = Secondary) }
                            Group("内容与备份") { Text("目录仅展示当前渠道可分发的内容。导出的存档由你自行保管。删除游戏资源默认保留存档；卸载 App 由系统清除本地数据。", color = Secondary) }
                        }
                        "permissions" -> ScrollPage {
                            PageHeading("权限与通知", "仅在需要时向你申请")
                            SettingRow("系统通知", if (Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(activity, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) "已允许" else "未允许，仍可在 App 内查看进度", Icons.Outlined.NotificationsNone) { if (Build.VERSION.SDK_INT >= 33) notifications.launch(Manifest.permission.POST_NOTIFICATIONS) }
                            Notice("游戏资源保存在 App 私有目录，不申请所有文件访问。导入与导出使用系统文件选择器，取消后不会修改数据。")
                            SecondaryButton("打开系统应用设置") { activity.startActivity(Intent(AndroidSettings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + activity.packageName))) }
                        }
                        "about" -> ScrollPage {
                            PageHeading("关于 NsA", "发现 · 下载 · 游玩")
                            Group("应用信息") { InfoRow("版本", BuildConfig.VERSION_NAME); InfoRow("核心来源", "nx2android / nxnative"); InfoRow("运行方式", "本机原生运行，独立游戏进程") }
                            Text("此项目基于 nx2android 与 nxnative 开发，新增统一游戏大厅、内容分发和管理能力。开源组件保留各自许可。游戏内容不随源码分发。", color = Secondary)
                            SecondaryButton("查看项目源码与许可") { activity.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/wikigsroom/NsA-core"))) }
                            if (BuildConfig.DEBUG) Group("开发连接") { var endpoint by remember(settings.endpoint) { mutableStateOf(settings.endpoint) }; OutlinedTextField(endpoint, { endpoint = it }, label = { Text("HTTPS 服务地址") }, modifier = Modifier.fillMaxWidth(), singleLine = true); SecondaryButton("保存连接地址") { model.perform { val url = java.net.URI(endpoint); require(url.scheme == "https" && url.host != null && url.userInfo == null); repository.preferences.string("endpoint", endpoint.trimEnd('/')); repository.refresh() } } }
                        }
                        "network-error" -> ScrollPage {
                            PageHeading("恢复目录连接", "已安装内容与存档仍保留在本机")
                            Notice(networkError.ifBlank { "请确认网络连接正常，或稍后重试。" })
                            Notice(if (hasCache) "目录缓存更新时间：${catalogueUpdated.ifBlank { "时间未知" }}。当前缓存包含 ${catalogue.size} 款游戏。" else "尚无可用目录缓存。联网获取目录后，游戏列表才会显示内容。")
                            PrimaryButton("重新连接", Icons.Outlined.Refresh) { model.refresh(query, genre); back() }
                            SecondaryButton("打开系统网络设置") { activity.startActivity(Intent(AndroidSettings.ACTION_WIRELESS_SETTINGS)) }
                            SecondaryButton("前往我的游戏") { tab = "library"; route = "root" }
                        }
                        "repair" -> ScrollPage {
                            PageHeading("校验与修复", "先检查完整性，再按需重新下载")
                            Notice("校验只读取资源文件，不会修改存档。检测到损坏后，可重新下载对应完整版本。")
                            PrimaryButton("开始完整性校验", Icons.Outlined.Verified) { model.perform { val bad = Installation(repository).repair(selected); withContext(Dispatchers.Main) { resultText = if (bad.isEmpty()) "资源文件完整，无需修复" else "发现 ${bad.size} 个缺失或损坏的文件。重新下载当前版本可修复。" } } }
                            if (resultText.isNotEmpty()) Group("校验结果") { Text(resultText) }
                            SecondaryButton("清理此游戏的图形缓存") { model.perform { repository.clearCache(selected); model.notice.value = "图形缓存已清理，下次启动时重建；资源与存档已保留" } }
                            SecondaryButton("重新下载资源") { openGame(selected); dialog = "download" }
                        }
                    } }
                }
            }
        }
        if (notice.isNotEmpty()) AlertDialog(onDismissRequest = model::dismissNotice, title = { Text("操作提示") }, text = { Text(notice) }, confirmButton = { TextButton(onClick = model::dismissNotice) { Text("知道了") } })
        if (launching) AlertDialog(onDismissRequest = {}, title = { Text("正在启动游戏") }, text = { Column(verticalArrangement = Arrangement.spacedBy(16.dp)) { LinearProgressIndicator(Modifier.fillMaxWidth()); Text(launchMessage.ifEmpty { "检查组件、资源与存档，准备进入横屏。" }) } }, confirmButton = { TextButton(onClick = { model.controller.cancel() }) { Text("取消启动") } })
        if (!launching && launchMessage.isNotEmpty()) AlertDialog(onDismissRequest = { model.controller.message.value = "" }, title = { Text("游戏运行提示") }, text = { Text(launchMessage) }, confirmButton = { TextButton(onClick = { model.controller.message.value = "" }) { Text("返回大厅") } }, dismissButton = { Row { if (protectedRuntime) TextButton(onClick = { model.controller.reconnect() }) { Text("重试连接") }; if (protectedRuntime || latest?.state !in listOf(null, "ended", "failed", "cancelled", "crashed")) TextButton(onClick = { dialog = "force-exit" }) { Text("结束游戏进程") } } })
        when (dialog) {
            "cancel-transfer" -> AlertDialog(onDismissRequest = { dialog = "" }, title = { Text("取消此下载任务？") }, text = { Text("此任务尚未完成的下载文件会被删除。已安装的资源与存档会保留；需要保留下载进度时请选择暂停。") }, confirmButton = { TextButton(onClick = { model.perform { repository.transferAction(cancelTransfer, "cancel"); withContext(Dispatchers.Main) { dialog = ""; cancelTransfer = "" } } }) { Text("取消任务", color = Error) } }, dismissButton = { TextButton(onClick = { dialog = "" }) { Text("保留任务") } })
            "delete-saves" -> AlertDialog(onDismissRequest = { dialog = "" }, title = { Text("删除此游戏的存档？") }, text = { Text((selectedRecord?.let { JSONObject(it.summary).optString("name") } ?: selected) + "\n将删除当前存档与 App 内本地备份，无法撤销。已导出至外部位置的文件会保留。建议先导出重要进度。") }, confirmButton = { TextButton(onClick = { model.perform { repository.deleteSaves(selected); withContext(Dispatchers.Main) { dialog = ""; model.notice.value = "此游戏的本地存档与备份已删除" } } }) { Text("确认删除存档", color = Error) } }, dismissButton = { TextButton(onClick = { dialog = "" }) { Text("取消") } })
            "reset-settings" -> AlertDialog(onDismissRequest = { dialog = "" }, title = { Text("重置此游戏的设置？") }, text = { Text("操作布局、画面和声音偏好会恢复默认。游戏资源与存档会保留。") }, confirmButton = { TextButton(onClick = { model.perform { repository.resetSettings(selected); withContext(Dispatchers.Main) { dialog = ""; model.notice.value = "设置已恢复默认，下次启动生效" } } }) { Text("确认重置") } }, dismissButton = { TextButton(onClick = { dialog = "" }) { Text("取消") } })
            "download" -> detail?.let { value -> AlertDialog(onDismissRequest = { dialog = "" }, title = { Text("下载游戏") }, text = { Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(value.getJSONObject("game").getString("name"), fontWeight = FontWeight.Bold)
                Text("下载 " + formatBytes(value.getString("download_bytes").toLong()) + " · 展开 " + formatBytes(value.getString("unpacked_bytes").toLong()))
                Text("本次完整准备至少需 " + formatBytes(detailSpace(repository, value)) + " 可用空间，含工作空间与安全预留。当前可用 " + formatBytes(repository.availableBytes()), color = Secondary)
                CheckRow("允许此任务使用移动网络", cellular) { cellular = it }
                if (value.getJSONObject("game").getJSONObject("compatibility").optString("status") != "verified") { Text("此设备组合属于实验适配，可能无法启动或完整游玩。", color = Warning); CheckRow("我了解实验适配的限制，继续内部测试", experimental) { experimental = it } }
            } }, confirmButton = { TextButton(enabled = !working && (value.getJSONObject("game").getJSONObject("compatibility").optString("status") == "verified" || experimental), onClick = { model.perform { repository.enqueue(value, cellular, experimental); withContext(Dispatchers.Main) { dialog = ""; tab = "downloads"; route = "root" } } }) { Text("确认下载") } }, dismissButton = { TextButton(onClick = { dialog = "" }) { Text("取消") } }) }
            "delete" -> AlertDialog(onDismissRequest = { dialog = "" }, title = { Text("删除游戏资源？") }, text = { Column { Text("下载后的资源文件会被移除，之后可重新下载。") ; CheckRow("保留游戏存档与本地备份", keepSaves) { keepSaves = it }; if (!keepSaves) Text("取消勾选会同时删除本地存档，无法撤销。建议先导出备份。", color = Error) } }, confirmButton = { TextButton(onClick = { model.perform { repository.deleteResources(selected, keepSaves); withContext(Dispatchers.Main) { dialog = ""; route = "root"; tab = "library" } } }) { Text("确认删除", color = Error) } }, dismissButton = { TextButton(onClick = { dialog = "" }) { Text("取消") } })
            "force-exit" -> AlertDialog(onDismissRequest = { dialog = "" }, title = { Text("强制结束游戏？") }, text = { Text("尚未保存的进度可能丢失，此操作不会生成存档成功记录。") }, confirmButton = { TextButton(onClick = { model.controller.forceExit(); dialog = "" }) { Text("结束进程", color = Error) } }, dismissButton = { TextButton(onClick = { dialog = "" }) { Text("继续等待") } })
            "player" -> { var name by remember { mutableStateOf(settings.player) }; AlertDialog(onDismissRequest = { dialog = "" }, title = { Text("本地玩家") }, text = { OutlinedTextField(name, { name = it.take(24) }, label = { Text("玩家称呼") }, singleLine = true) }, confirmButton = { TextButton(enabled = name.isNotBlank(), onClick = { model.perform { repository.preferences.string("player", name.trim()); withContext(Dispatchers.Main) { dialog = "" } } }) { Text("保存") } }, dismissButton = { TextButton(onClick = { dialog = "" }) { Text("取消") } }) }
        }
        pendingSave?.let { pending ->
            val stage = pending.getString("stage"); val id = pending.getString("id")
            val processing = working || stage in listOf("preparing", "copying", "applying", "writing")
            if (stage !in listOf("picker", "awaiting-result")) AlertDialog(
                onDismissRequest = { if (!processing) model.perform { repository.saveExchange.cancel(id) } },
                title = { Text(if (stage == "failed") "存档操作尚未完成" else if (processing) "正在处理存档" else "导入此存档备份？") },
                text = { Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    val game = games.firstOrNull { it.id == pending.getString("game_id") }
                    Text(game?.let { JSONObject(it.summary).optString("name", it.id) } ?: pending.getString("game_id"))
                    if (processing) { LinearProgressIndicator(Modifier.fillMaxWidth()); Text("正在校验、备份或写入存档。请等待完成，处理中无法取消。") }
                    else if (stage == "failed") Text(pending.optString("error", "可重试或取消备份草稿。"))
                    else { Text("备份版本：${pending.optString("source_release_id")}\n展开大小：${formatBytes(pending.optString("expanded_bytes", "0").toLong())}"); Text("确认后将重新校验全部文件、备份当前有效存档，再完成导入。导入后由你手动启动游戏。") }
                } },
                confirmButton = { TextButton(enabled = !processing, onClick = { if (!processing) { if (stage == "confirm") model.confirmSave(id) else model.perform { repository.saveExchange.retry(id) } } }) { Text(if (stage == "confirm") "确认导入" else "重试") } },
                dismissButton = { TextButton(enabled = !processing, onClick = { if (!processing) model.perform { repository.saveExchange.cancel(id) } }) { Text("取消草稿") } }
            )
        }
    }
}

@Composable private fun ScrollPage(content: @Composable ColumnScope.() -> Unit) { Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(20.dp), content = content) }
@Composable private fun PageHeading(title: String, subtitle: String) { Column(Modifier.padding(top = 8.dp, bottom = 4.dp)) { Text(title, fontSize = 28.sp, lineHeight = 36.sp, fontWeight = FontWeight.Bold); if (subtitle.isNotBlank()) Text(subtitle, Modifier.padding(top = 8.dp), fontSize = 14.sp, color = Secondary) } }
@Composable private fun Welcome(continueAction: () -> Unit) { Column(Modifier.fillMaxSize().padding(vertical = 40.dp), verticalArrangement = Arrangement.SpaceBetween) { Column { Text("NsA", fontSize = 52.sp, fontWeight = FontWeight.Black); Text("你的游戏\n随时出发", Modifier.padding(top = 40.dp), fontSize = 38.sp, lineHeight = 52.sp, fontWeight = FontWeight.Bold); Text("一个熟悉的入口，连接你的下一段旅程。", Modifier.padding(top = 20.dp), color = Secondary, lineHeight = 26.sp) }; Column(verticalArrangement = Arrangement.spacedBy(20.dp)) { listOf(Icons.Outlined.Explore to "发现与收藏喜欢的游戏", Icons.Outlined.Download to "下载、校验并准备好资源", Icons.Outlined.SportsEsports to "进入横屏游戏，退出回到这里").forEach { (icon, text) -> Row(verticalAlignment = Alignment.CenterVertically) { Icon(icon, null, tint = Accent); Text(text, Modifier.padding(start = 16.dp)) } }; Text("使用前请阅读项目说明与内容许可。首次连接需要网络；存档默认保存在设备本地。", color = Secondary, fontSize = 12.sp); PrimaryButton("进入游戏大厅", Icons.Outlined.ArrowForward, continueAction) } } }
@Composable private fun PrimaryButton(label: String, icon: ImageVector = Icons.Outlined.ArrowForward, onClick: () -> Unit) { Button(onClick, Modifier.fillMaxWidth().heightIn(min = 56.dp), shape = RoundedCornerShape(14.dp)) { Icon(icon, null); Spacer(Modifier.width(10.dp)); Text(label, fontSize = 16.sp, fontWeight = FontWeight.SemiBold) } }
@Composable private fun SecondaryButton(label: String, onClick: () -> Unit) { OutlinedButton(onClick, Modifier.fillMaxWidth().heightIn(min = 48.dp), shape = RoundedCornerShape(14.dp)) { Text(label) } }
@Composable private fun Group(title: String, content: @Composable ColumnScope.() -> Unit) { Column(verticalArrangement = Arrangement.spacedBy(12.dp)) { Text(title, fontSize = 18.sp, fontWeight = FontWeight.SemiBold); Surface(shape = RoundedCornerShape(16.dp), color = Surface, border = BorderStroke(1.dp, Border)) { Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp), content = content) } } }
@Composable private fun SettingRow(title: String, description: String, icon: ImageVector, onClick: () -> Unit) { Row(Modifier.fillMaxWidth().heightIn(min = 64.dp).clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick).padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) { Icon(icon, null, tint = Secondary); Column(Modifier.weight(1f).padding(horizontal = 14.dp)) { Text(title, fontSize = 16.sp); Text(description, fontSize = 12.sp, color = Secondary, modifier = Modifier.padding(top = 4.dp)) }; Icon(Icons.Outlined.ChevronRight, null, tint = Secondary) } }
@Composable private fun SwitchRow(title: String, description: String, checked: Boolean, onChange: (Boolean) -> Unit) { Row(Modifier.fillMaxWidth().heightIn(min = 64.dp), verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f).padding(end = 12.dp)) { Text(title); Text(description, fontSize = 12.sp, color = Secondary) }; Switch(checked, onChange, modifier = Modifier.semantics { contentDescription = title }) } }
@Composable private fun CheckRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) { Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable { onChange(!checked) }, verticalAlignment = Alignment.CenterVertically) { Checkbox(checked, onChange); Text(label, fontSize = 14.sp) } }
@Composable private fun InfoRow(label: String, value: String) { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) { Text(label, color = Secondary, fontSize = 14.sp, modifier = Modifier.weight(1f)); Text(value, fontSize = 14.sp, modifier = Modifier.weight(1.3f)) } }
@Composable private fun Notice(text: String) { Surface(shape = RoundedCornerShape(14.dp), color = Accent.copy(alpha = .07f)) { Text(text, Modifier.padding(16.dp), fontSize = 14.sp, color = Secondary, lineHeight = 23.sp) } }
@Composable private fun Empty(title: String, message: String, icon: ImageVector, action: String, onClick: () -> Unit) { Column(Modifier.fillMaxWidth().padding(vertical = 32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) { Surface(shape = RoundedCornerShape(24.dp), color = Raised) { Icon(icon, null, Modifier.padding(22.dp).size(40.dp), tint = Accent) }; Text(title, fontSize = 20.sp, fontWeight = FontWeight.SemiBold); Text(message, color = Secondary, fontSize = 14.sp, lineHeight = 24.sp); OutlinedButton(onClick, shape = RoundedCornerShape(14.dp)) { Text(action) } } }
@Composable private fun MessageCard(title: String, description: String, icon: ImageVector, action: String, onClick: () -> Unit) { Surface(shape = RoundedCornerShape(16.dp), color = Raised) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { Row { Icon(icon, null, tint = Warning); Text(title, Modifier.padding(start = 10.dp), fontWeight = FontWeight.SemiBold) }; Text(description, color = Secondary, fontSize = 14.sp); TextButton(onClick) { Text(action) } } } }
@Composable private fun SearchField(query: String, onChange: (String) -> Unit, onSearch: () -> Unit) { OutlinedTextField(query, { onChange(it.take(64)) }, Modifier.fillMaxWidth().heightIn(min = 56.dp), placeholder = { Text("搜索游戏") }, leadingIcon = { Icon(Icons.Outlined.Search, null) }, trailingIcon = { IconButton(onSearch) { Icon(Icons.Outlined.ArrowForward, "搜索") } }, shape = RoundedCornerShape(12.dp), singleLine = true) }
@Composable private fun GenreChips(selected: String, onSelect: (String) -> Unit) { Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) { listOf("" to "全部类型", "adventure" to "冒险", "action" to "动作", "puzzle" to "益智", "role_playing" to "角色扮演").forEach { (key, label) -> FilterChip(selected == key, { onSelect(key) }, label = { Text(label) }, modifier = Modifier.heightIn(min = 48.dp)) } } }
@Composable private fun CatalogueFilters(selected: String, onSelect: (String) -> Unit) { Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) { listOf("verified" to "本机可玩", "installed" to "已安装", "experimental" to "实验游戏").forEach { (key, label) -> FilterChip(selected == key, { onSelect(key) }, label = { Text(label) }, modifier = Modifier.heightIn(min = 48.dp)) } } }
@Composable private fun Cover(summary: JSONObject, height: Int) { val url = summary.optString("cover_url"); Box(Modifier.fillMaxWidth().height(height.dp).clip(RoundedCornerShape(14.dp)).background(Brush.linearGradient(listOf(Color(0xFF29454D), Color(0xFF17202E), Color(0xFF3B3852)))), contentAlignment = Alignment.Center) { if (url.startsWith("https://")) AsyncImage(url, summary.optString("name"), Modifier.fillMaxSize(), contentScale = ContentScale.Crop) else { Icon(Icons.Outlined.SportsEsports, summary.optString("name"), Modifier.size(64.dp), tint = Accent.copy(alpha = .65f)); Text("PLAY NEXT", Modifier.align(Alignment.BottomStart).padding(16.dp), color = Accent.copy(alpha = .6f), fontSize = 11.sp, letterSpacing = 3.sp) } } }
@Composable private fun Featured(summary: JSONObject, game: GameRecord?, onPlay: () -> Unit, onManage: (() -> Unit)?, onOpen: () -> Unit = onPlay, playLabel: String? = null) { Surface(shape = RoundedCornerShape(18.dp), border = BorderStroke(1.dp, Border), color = Surface) { Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) { Column(Modifier.clickable(onClick = onOpen), verticalArrangement = Arrangement.spacedBy(14.dp)) { Cover(summary, 172); Text(summary.optString("name"), fontSize = 24.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis) }; Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) { Icon(if (game?.activeRelease?.isNotEmpty() == true) Icons.Outlined.CheckCircle else Icons.Outlined.Explore, null, tint = Accent, modifier = Modifier.size(18.dp)); Text(if (playLabel == "资源需要修复") "待修复" else if (game?.activeRelease?.isNotEmpty() == true) "已安装" else "等待探索", color = Accent, fontSize = 14.sp); if (game != null && game.lastPlayed > 0) Text("· " + DateTimeFormatter.ofPattern("MM-dd HH:mm").withZone(ZoneId.systemDefault()).format(Instant.ofEpochMilli(game.lastPlayed)), color = Secondary, fontSize = 12.sp) }; PrimaryButton(playLabel ?: if (game?.activeRelease?.isNotEmpty() == true && onManage != null) "继续游戏" else "查看游戏", Icons.Outlined.PlayArrow, onPlay); if (onManage != null) SecondaryButton("管理游戏", onManage) } } }
@Composable private fun CompactGame(summary: JSONObject, game: GameRecord?, onClick: () -> Unit) { Surface(onClick, shape = RoundedCornerShape(16.dp), color = Surface, border = BorderStroke(1.dp, Border)) { Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) { Cover(summary, 116); Text(summary.optString("name"), fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis); Text(if (game?.activeRelease?.isNotEmpty() == true) "● 已安装" else "查看详情", color = Accent, fontSize = 12.sp) } } }
@Composable private fun GameRow(summary: JSONObject, game: GameRecord?, onClick: () -> Unit) { Surface(onClick, shape = RoundedCornerShape(16.dp), color = Surface, border = BorderStroke(1.dp, Border)) { Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) { Box(Modifier.width(82.dp)) { Cover(summary, 82) }; Column(Modifier.weight(1f).padding(start = 14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { Text(summary.optString("name"), fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis); Text(if (game?.activeRelease?.isNotEmpty() == true) "已安装" else summary.optString("download_bytes").toLongOrNull()?.let(::formatBytes) ?: "查看游戏", color = Accent, fontSize = 12.sp) }; Icon(Icons.Outlined.ChevronRight, null, tint = Secondary) } } }
private fun genreLabel(value: String) = mapOf("adventure" to "冒险", "action" to "动作", "puzzle" to "益智", "role_playing" to "角色扮演", "simulation" to "模拟", "strategy" to "策略", "sports" to "体育", "racing" to "竞速", "other" to "其他")[value] ?: value
private fun languageLabel(value: String) = when (value) { "zh-CN", "zh-Hans" -> "简体中文"; "zh-TW", "zh-Hant" -> "繁体中文"; else -> java.util.Locale.forLanguageTag(value).getDisplayName(java.util.Locale.SIMPLIFIED_CHINESE).ifBlank { value } }
private fun inputLabel(value: String) = mapOf("touch" to "触屏", "gamepad" to "手柄", "controller" to "手柄", "keyboard" to "键盘", "keyboard_mouse" to "键鼠")[value] ?: value
private fun detailSpace(repository: HallRepository, value: JSONObject) = repository.additionalBytes(JSONObject().put("archive", JSONObject().put("bytes", value.getString("download_bytes"))).put("unpacked_bytes", value.getString("unpacked_bytes")).put("working_bytes", value.getString("working_bytes")), 0, 0)
@Composable private fun Compatibility(value: JSONObject?) { val status = value?.optString("status") ?: "experimental"; val label = mapOf("verified" to "已验证适配", "experimental" to "实验适配", "unsupported" to "设备暂不支持", "app_update_required" to "需要更新 App", "blocked" to "版本已暂停")[status] ?: "适配待确认"; AssistChip({}, label = { Text(label) }, leadingIcon = { Icon(Icons.Outlined.Info, null, Modifier.size(16.dp)) }); value?.optJSONArray("known_limitations")?.let { array -> repeat(array.length()) { val text = array.getString(it); Text(if (text == "Actual gameplay and save/reload are not verified for this device.") "此设备尚未完成完整试玩及存档重读验收。" else text, color = Warning, fontSize = 13.sp) } } }
@Composable private fun Downloads(tasks: List<TransferRecord>, games: List<GameRecord>, action: (String, String) -> Unit, open: (String) -> Unit, discover: () -> Unit) {
    LazyColumn(verticalArrangement = Arrangement.spacedBy(16.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
        item { PageHeading("下载中心", "每一步进度，都有迹可循") }
        if (tasks.isEmpty()) item { Empty("下载列表空空的", "去发现喜欢的游戏，确认下载后可在这里管理任务。", Icons.Outlined.Download, "发现游戏", discover) }
        items(tasks.reversed(), key = { it.id }) { task ->
            val name = games.firstOrNull { it.id == task.gameId }?.let { JSONObject(it.summary).optString("name") } ?: "游戏资源"
            TransferCard(task, name, { action(task.id, it) }, { open(task.gameId) })
        }
    }
}
@Composable private fun TransferCard(task: TransferRecord, name: String, action: (String) -> Unit, open: () -> Unit) {
    val currentBytes by rememberUpdatedState(task.bytes)
    var speed by remember(task.id, task.state) { mutableDoubleStateOf(0.0) }
    LaunchedEffect(task.id, task.state) {
        if (task.state == "downloading") {
            var previous = currentBytes
            var sampled = android.os.SystemClock.elapsedRealtime()
            while (true) {
                delay(3000)
                val now = android.os.SystemClock.elapsedRealtime()
                val delta = currentBytes - previous
                val next = if (delta > 0) delta.toDouble() * 1000 / (now - sampled).coerceAtLeast(1) else 0.0
                speed = if (next == 0.0 || speed == 0.0) next else speed * .4 + next * .6
                previous = currentBytes; sampled = now
            }
        }
    }
    Surface(shape = RoundedCornerShape(16.dp), color = Surface, border = BorderStroke(1.dp, Border)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(name, fontWeight = FontWeight.SemiBold)
            Text(mapOf("queued" to "排队等待", "downloading" to "正在下载", "waiting" to "等待恢复条件", "paused" to "已主动暂停", "verifying" to "正在校验文件", "installing" to "正在准备游戏", "completed" to "游戏已准备好", "failed" to "任务失败，可重试", "cancelled" to "已取消")[task.state] ?: task.state, color = if (task.state == "failed") Error else Accent)
            val progress = if (task.total <= 0L) 0f else (task.bytes.toDouble() / task.total).toFloat().coerceIn(0f, 1f)
            LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)))
            Text(formatBytes(task.bytes) + if (task.total > 0L) " / " + formatBytes(task.total) + " · " + (progress * 100).toInt() + "%" else " · 总量待确认", color = Secondary, fontSize = 12.sp)
            if (task.state == "downloading") Text(if (speed > 0) {
                val seconds = ((task.total - task.bytes).coerceAtLeast(0) / speed).toLong()
                formatBytes(speed.toLong()) + "/s · " + if (task.total <= 0L) "剩余时间待确认" else if (seconds < 60) "预计不足 1 分钟" else "预计约 ${(seconds + 59) / 60} 分钟"
            } else "正在估算速度与剩余时间…", color = Secondary, fontSize = 12.sp)
            if (task.error.isNotBlank()) Text(task.error, color = Error, fontSize = 12.sp)
            if (task.blockers.contains("game_running")) Text("游玩期间暂停资源准备，退出游戏后按网络条件恢复。", color = Secondary, fontSize = 12.sp)
            if (task.blockers.contains("cellular_consent")) TextButton(onClick = { action("cellular") }) { Text("允许此任务使用移动网络") }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (task.state in listOf("paused", "failed", "waiting")) TextButton(onClick = { action(if (task.state == "failed") "retry" else "resume") }) { Text("继续") }
                else if (task.state in listOf("queued", "downloading")) TextButton(onClick = { action("pause") }) { Text("暂停") }
                if (task.state !in listOf("completed", "cancelled")) TextButton(onClick = { action("cancel") }) { Text("取消任务") }
                if (task.state in listOf("installing", "verifying", "completed")) TextButton(onClick = open) { Text("查看准备进度") }
            }
        }
    }
}

@Composable private fun FeedbackForm(diagnosticPreference: Boolean, privacy: () -> Unit, submit: (String, String, String, Boolean) -> Unit) {
    var description by rememberSaveable { mutableStateOf("") }; var contact by rememberSaveable { mutableStateOf("") }; var diagnostics by rememberSaveable { mutableStateOf(false) }; var category by rememberSaveable { mutableStateOf("other") }
    ScrollPage { PageHeading("帮助与反馈", "把问题描述清楚，帮助我们定位")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { listOf("download" to "下载", "game_runtime" to "运行", "save" to "存档", "other" to "其他").forEach { (key, label) -> FilterChip(category == key, { category = key }, label = { Text(label) }) } }
        OutlinedTextField(description, { description = it.take(2000) }, label = { Text("问题描述（至少 10 个字）") }, minLines = 6, modifier = Modifier.fillMaxWidth(), supportingText = { Text("${description.length} / 2000") })
        OutlinedTextField(contact, { contact = it.take(254) }, label = { Text("联系方式（可选）") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        CheckRow("本次附加经过筛选的诊断信息", diagnostics) { diagnostics = it }
        TextButton(privacy) { Text("查看诊断内容与保留期限") }
        Button({ submit(category, description.trim(), contact.trim(), diagnostics) }, Modifier.fillMaxWidth().height(56.dp), enabled = description.trim().length in 10..2000) { Text("提交反馈") }
    }
}

private suspend fun uploadDiagnostic(repository: HallRepository): String {
    val directory = File(repository.context.cacheDir, "diagnostics").apply { mkdirs() }; val archive = File(directory, "diagnostic-${System.currentTimeMillis()}.zip")
    val report = JSONObject().put("app_version", BuildConfig.VERSION_NAME).put("capability", repository.capability()).put("transfers", org.json.JSONArray(repository.dao.allTransfers().map { JSONObject().put("state", it.state).put("bytes", it.bytes.toString()).put("failures", it.failures) }))
    ZipOutputStream(archive.outputStream()).use { zip -> zip.putNextEntry(ZipEntry("diagnostic.json")); zip.write(report.toString().toByteArray()); zip.closeEntry() }
    try { val sha = sha256(archive); val grant = repository.api.request("/v1/diagnostic-uploads", JSONObject().put("bytes", archive.length().toString()).put("sha256", sha).put("content_type", "application/zip").put("consent_version", "1").put("consented_at", Instant.now().toString())); repository.api.upload(grant.getString("upload_url"), archive); repository.api.request("/v1/diagnostic-uploads/${grant.getString("upload_id")}/complete", JSONObject().put("sha256", sha)); return grant.getString("upload_id") } finally { archive.delete() }
}

@Composable private fun UpdatePage(repository: HallRepository, model: HallViewModel, activity: MainActivity) {
    var update by remember { mutableStateOf<JSONObject?>(null) }; var checked by remember { mutableStateOf(false) }
    ScrollPage { PageHeading("App 更新", "更新会保留游戏与本地进度")
        Group("当前版本") { Text(BuildConfig.VERSION_NAME, fontSize = 24.sp, fontWeight = FontWeight.Bold); Text(if (BuildConfig.DEBUG) "内部开发构建" else "正式应用构建", color = Secondary) }
        PrimaryButton("检查更新", Icons.Outlined.Refresh) { model.perform { val envelope = repository.api.request("/v1/app-update"); val result = if (envelope.length() == 0) null else repository.trust.verify(envelope.toString().toByteArray(), "app_update"); withContext(Dispatchers.Main) { update = result; checked = true } } }
        if (checked && update == null) Notice("当前服务没有可用的新版本。")
        update?.let { value -> Group("可用版本") { Text(value.getString("version_name"), fontWeight = FontWeight.Bold); Text(value.getString("release_notes"), color = Secondary); Notice("下载完成后会核对文件摘要、包名、版本与当前安装签名，再交由系统确认更新。游戏资源与存档保留。"); PrimaryButton("下载并安装更新", Icons.Outlined.SystemUpdate) { model.perform { val updater = AppUpdater(repository); val file = updater.prepare(value); withContext(Dispatchers.Main) { updater.install(activity, file) } } } } }
    }
}
