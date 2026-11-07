# 桌面制作工具安装

[English](INSTALL.md) · [NsA 总览](../README.zh-CN.md) · [开发手册](../docs/development.zh-CN.md)

本包提供 **nx2android 桌面制作 GUI/CLI**、原生引擎及完整 NsA 文档与实拍素材。它不是 Android 安装包。构建统一竖屏大厅需要检出对应标签源码，按开发手册操作。桌面工具的单游戏 APK 制作与大厅的预置模块/内容发行属于不同流程。

## 下载与校验

从 [NsA Core Releases](https://github.com/wikigsroom/NsA-core/releases) 下载 Windows x64 或架构匹配的 macOS 附件及同版 `SHA256SUMS.txt`，解压前比对 ZIP 摘要：

```powershell
Get-FileHash ./nx2android-v0.4.0-windows-x64.zip -Algorithm SHA256
```

macOS 使用 `shasum -a 256 <压缩包.zip>`。目录内 `VERSION` 标明精确标签。保留整个文件夹，包含 `_internal`、`nxnative` 与文档。新工具、缓存和制作材料放在选定 `游戏移植/` 根下，预留解包/编译空间，无需 Docker、WSL 或模拟器。

程序尚未代码签名/公证。系统提示信任时先核对来源和摘要，再按系统应用审核方式处理，或从源码构建。不能仅因提示就认定“误报”；不要关闭杀毒软件或全局安全保护。

## 初始化桌面工具

Windows 启动 `nx2android.exe`；匹配架构的 Mac 启动 `./nx2android`。这是原桌面制作界面，不是手机 Obsidian 大厅。

1. 在 **Setup** 检查组件。**Download missing tools** 从发布者下载 Android NDK、.NET SDK、platform-tools 到本地工具目录，也可选择已有安装。
2. 制作 Switch 移植时选择本机 `prod.keys`，再点 **Save and check**。本版不提供游戏输入或密钥，原创原生 2048 大厅演示不需要二者。
3. 在 **Games** 查看单游戏 profile。已有 profile 不代表每个游戏版本、GPU 和手机兼容；大厅模块另行维护。

## 制作与安装单游戏移植

1. 在 **Port** 选择游戏输入、更新/DLC 目录，支持 NSP/XCI 和 NSZ/XCZ；压缩输入需额外解压空间。
2. 核对 profile、版本、选项并点 **Port**。日志显示解包、原生改写、着色器及打包，保留原始输入。
3. APK 与 `<Game>-data` 位于所选工作目录。时间/空间随游戏和缓存变化；出现菜单不等于可玩性验收。
4. 对授权实机启用 USB 调试，解锁并批准电脑，通过 **Install on device** 安装单游戏输出。重要进度先导出备份，不通过清数据解决安装问题。

单游戏安装器可验证并复用设备上可访问且版本匹配的 RomFS，避免重复复制。先阅读[上游说明](https://github.com/shirai91/nx2android#readme)及 CLI 帮助，Android 应用私有目录存在访问限制。这不是大厅的签名资源下载器。

## CLI、大厅与排错

```powershell
./nx2android.exe -m porter -h
./nx2android.exe -m porter doctor
./nx2android.exe -m porter profiles
```

macOS 将 `./nx2android.exe` 换成 `./nx2android`。实现“竖屏发现 → 下载准备 → 横屏运行”需要同标签源码，按[开发](../docs/development.zh-CN.md)、[部署](../docs/deployment.zh-CN.md)及[玩家手册](../docs/hall/README.zh-CN.md)操作。本包不含公共服务、私有双游戏 APK 或商业游戏程序。

向 [Issues](https://github.com/wikigsroom/NsA-core/issues) 报错时保留错误行、精确 `VERSION`、profile、游戏版本、GPU 和 Android 版本，不附密钥、凭据、存档或游戏文件。图形支持取决于主机与驱动，本机没有验收 Mali/PowerVR 及其他未测组合。新版解压到独立目录，保留旧输入、输出、配置与进度。阅读[版本说明](../docs/releases/v0.4.0.md)；BotW 仍为实验状态，持续性能、设备覆盖和生产分发门槛尚未验收。
