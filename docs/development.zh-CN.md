# 开发与本地复现手册

[English](development.md) · [项目首页](../README.zh-CN.md) · [部署](deployment.zh-CN.md) · [技术基线](engineering/README.md)

## 工程结构与前置条件

| 路径 | 职责 |
|---|---|
| `android/app/` | Compose、Room、网络、下载、安装、存档与游戏会话协调 |
| `android/native-games/2048/` | 原创 MIT 原生演示游戏与声音输出 |
| `nxnative/dm/android/` | 原生宿主、Vulkan 适配、大厅 ABI 与运行身份检查 |
| `porter/`、`gui/` | 独立桌面移植流程与 GUI |
| `content_tools/` | 带完整索引的 ZIP64 打包、真实 API 可恢复发布 |
| `services/catalog/` | FastAPI、凭据、策略、存储和运营接口 |
| `services/worker/`、`services/admin/` | 对象校验进程与独立运营网页 |
| `devtools/` | Windows 原生初始化、构建、设备与素材工具 |
| `packaging/`、`.github/workflows/` | 桌面分发包和校验后发布流程 |
| `docs/product/`、`engineering/`、`design/` | 需求、契约、研发约束与设计定稿 |

大厅已验证开发环境为 Windows、Python 3.13、Java 21、Gradle 8.13、Android SDK 36 / build-tools 36.0.0、NDK r27c（`27.2.12479018`）。真机检查要求 arm64 Android API 31+ 手机；只有媒体处理需要 FFmpeg/ffprobe。桌面压缩包自带 Python 运行时，但不等于大厅 Android 开发环境。

仓库、新下载、环境、缓存、输出全部放在指定 `游戏移植/` 根下。禁止调用本地 Docker、WSL、虚拟机或安卓模拟器。若检查浏览器，完成后关闭不用的 tab；以下 USB 流程无需浏览器。

## 从干净源码构建开源演示

将 `JAVA_HOME` 指向 Java 21，先安装所需 SDK/NDK；可用 `NSA_SDK_SOURCE`、`NSA_NDK_SOURCE` 指明位置。`hall_setup.py` 将组件复制到项目内、校验 Gradle 下载摘要并生成被忽略的 `android/local.properties`。

```powershell
git clone git@github.com:wikigsroom/NsA-core.git
Set-Location NsA-core
./devtools/hall_bootstrap.ps1
./devtools/hall.ps1 native
./devtools/hall.ps1 build
```

初始化创建 `services/.venv`、安装开发依赖、生成本地 TLS/内容签名和独立编辑/审核凭据、创建一次性邀请并生成所选开源模块身份。`native` 实际编译模块/bootstrap，检查编译入 ELF 的元数据后收集库；`build` 组装 debug APK 并核对最终注册表和库摘要。

开发秘密与生成文件继续忽略：`services/local/`、debug CA/邀请、运行注册表、信任公钥文档、原生 `.so`、APK、SDK/Gradle 缓存及 `.gamedump/`。

分别在两个终端启动 API 与 worker：

```powershell
./devtools/hall.ps1 serve
```

```powershell
./devtools/hall.ps1 worker
```

随后执行：

```powershell
./devtools/hall.ps1 content
./devtools/hall.ps1 publish-2048
./devtools/hall.ps1 install
```

发布通过真实上传、存储回读、构建/权利/版本登记、worker 校验和独立审核后进入内测目录。保持 API/worker 运行。手机先解锁并授权 USB；安装覆盖 `org.nsa.hall.debug`、保留数据并建立 `adb reverse tcp:8443 tcp:8443`。内测演示使用实验游戏筛选；旧独立游戏 App 不会被删除。

原创演示不读取商业游戏，也不需要 `prod.keys`。

## 重复构建与不可变身份

仅 Android 协调代码变化时，可以复用完全相同的原生字节，分别记录 Android 与原生源码提交；不能给缓存旧库换个新提交标签。

原生代码、配置或驱动变化后，重新生成身份、实际编译和收集，并使用新的不可变内容发布。收集核对 ELF 内的编译身份与 SONAME，最终 APK 再检查选中库与注册表摘要；缺失或无法读取的依赖强制重建，中文路径依赖按 UTF-8 解析。

不得覆盖已发布的内容编号。包输入必须匹配准备的身份；新包通过发布客户端 `--release-id` 使用新编号，并保留私有续传日志。改变输入不能重用旧幂等记录。

开发邀请用尽时，只重新初始化本地凭据，再构建覆盖；不要为不变内容重新生成运行身份：

```powershell
services/.venv/Scripts/python.exe devtools/hall_local.py init
./devtools/hall.ps1 build
./devtools/hall.ps1 install
```

该命令只用于开发配置，不是生产发证。邀请、服务秘密、签名私钥与运营 token 不得打印或上传。

## 显式启用私有 BotW 接入

已有本机配置对应 BotW 1.9.0，要求 `.gamedump/BOTW/` 内已有配套输入与输出。`-WithBotw` 不是游戏下载器，也不会自动寻找商业游戏材料。

```powershell
./devtools/hall_bootstrap.ps1 -WithBotw
./devtools/hall.ps1 native
./devtools/hall.ps1 content
./devtools/hall.ps1 build
```

API/worker 运行后发布新准备的内测内容并安装；配置与已有版本不同时使用新不可变编号。配置绑定模块 ID、着色器/驱动及存档兼容组。本机实验配置使用 Turnip 与降低渲染比例，准确参数以各候选注册表和实测记录为准。

原游戏、解包数据、原生游戏代码、私有 APK 和存档仅保留被忽略的 `.gamedump/`。公开展示经过检查的游戏画面，不包含公开游戏程序。

## 新增一款支持的游戏

1. 准备本地输入和可用适配，核对模块/构建 ID 与宿主限制。
2. 按 ABI 实现命名运行模块，独立 SONAME、环境、资源、设置、缓存和存档路径。
3. 把真实配置/源码身份编译入模块，明确选择进 APK，通过收集及打包核对。
4. 准备独立不可变内容包：归档、完整文件索引、分块索引、能力规则和存档兼容信息。
5. 登记权利/构建/发布，经授权上传、worker 回读校验与独立审核后进入对应范围目录。
6. 真机执行输入、声音、存读档、生命周期/退出、反复切换与代表性持续运行，绑定准确设备、驱动、模块/配置、内容版本和 APK。
7. 仅对达到门槛的组合标记已验证；缺模块需更新 APK，目录元数据不能补齐原生能力。

## 集中验证

依 ADR 0001，先完成一整批源码、用例、契约与文档，再统一验证；失败和跳过不能计为通过。

```powershell
./devtools/hall.ps1 verify
```

该入口执行服务集成、核心单测、JVM 与 lint。完整 Android 候选同时组装应用/测试 APK，检查最终包：

```powershell
$env:GRADLE_USER_HOME = Join-Path (Get-Location) 'tools/hall/gradle-home'
$env:ANDROID_USER_HOME = Join-Path (Get-Location) 'tools/hall/android-user'
tools/hall/gradle-8.13/bin/gradle.bat -p android --continue assembleDebug assembleDebugAndroidTest testDebugUnitTest lintDebug
services/.venv/Scripts/python.exe devtools/hall_verify_apk.py
```

正常退出游戏后再覆盖安装和执行测试，不为通过而清数据或卸载。先保留备份和准确 APK 摘要。Android 回归使用独立临时根，与真实玩家隔离；安装匹配的测试 APK 后执行 `adb shell am instrument -w -r org.nsa.hall.debug.test/androidx.test.runner.AndroidJUnitRunner`，核对实际数量与失败。当前源码包含 22 项。

`devtools/hall_smoke.py all` 会执行真实界面/下载/操作/保存/重读，仅删除重装 2048 演示资源并检查存档保留；前台被其他 App 占用就停止。不得与另一真机项目抢用设备。其余恢复、性能和故障范围按[验证矩阵](hall/device-validation.md)执行。

## 素材与发布

通过 `hall_device.py` 或带前台保护的 USB 工具先私下采集 PNG/MP4，检查真实游玩和隐私后，用 `hall_media.py` 导出。记录安装包、源摘要、尺寸、时长与转换；保留失败。公开片段无音轨，设计原型继续放在 `docs/design/`。

[发布流程](releases/README.md)固定源码标签，在 Windows/macOS 生成桌面包，检查公开内容并回读上传字节后发布。公开游戏截图不等于上传含商业游戏代码的内部 APK。

## 后续研发约束

保留 90 个需求编号、360 条规则与验收身份。行为变化同步规范、契约与验证。运行 ABI 为 1、Room 为 2，不兼容修改需迁移和 ADR。会话恢复以操作互斥与实际内核锁决定，不能只看旧记录或 PID 缺失；对账保留原事件/存档序号和游戏字节。

[技术基线](engineering/README.md) · [ADR 0009](engineering/decisions/0009-compiled-slot-identity-and-unicode-builds.md) · [ADR 0010](engineering/decisions/0010-stopped-session-reconciliation.md) · [当前证据](hall/implementation-status.md)
