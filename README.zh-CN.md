# NsA · Android 统一游戏大厅

后续代码、文档及已审核素材统一更新到 [wikigsroom/NsA-core 的 main 分支](https://github.com/wikigsroom/NsA-core/tree/main)，保留远端已有更新，不强制覆盖历史。

[English](README.md) · [完整方案与使用说明](docs/hall/README.zh-CN.md) · [产品需求](docs/product/README.md) · [技术约束](docs/engineering/README.md)

一个 App：发现游戏 → 查看适配与空间 → 下载 → 校验与安装 → 横屏游玩 → 保存并退出 → 回到竖屏大厅。

基于 [nx2android](https://github.com/shirai91/nx2android) 与 [nxnative](https://github.com/seedds/nxnative) 开发。新增 Kotlin/Compose 大厅、独立游戏进程、内容目录与发布服务，沿用已定稿的 D「曜黑极简」设计。源码更新目标是 [wikigsroom/NsA-core](https://github.com/wikigsroom/NsA-core)。

**当前是整批开发中的内部构建，未通过完整真机验收。** 页面源码、下载/安装/存档流程与服务端实现，不等同于《旷野之息》已在新大厅内完整可玩。真实构建、测试、截图与录屏结果见 [实现与验证记录](docs/hall/implementation-status.md)。原有单游戏测试结果与新大厅测试分开记录。

| 入口 | 使用逻辑 |
|---|---|
| 发现游戏 | 名称搜索、类型筛选、详情、版本与适配信息、下载确认 |
| 我的游戏 | 继续游戏、收藏、游戏管理、独立操作偏好、存档与更新 |
| 下载 | 单任务队列、暂停/继续/重试、网络条件、分块校验与安装进度 |
| 设置 | 下载偏好、存储、设备支持、权限、反馈、隐私、App 更新 |
| 独立后台 | 构建与授权登记、真实文件上传、独立审核、发布/阻断、证据与审计 |

大厅默认竖屏，只有进入游戏后横屏。游戏按 App 内已编译槽位运行，不从目录安装任意原生运行库。每次切换游戏使用新进程；存档按游戏与本地玩家隔离。资源删除默认保留存档，卸载 App 前请导出重要进度。

以下为 USB 真机上的实际页面，当前 App 界面使用简体中文：

<table>
<tr><td>我的游戏</td><td>设置</td><td>存档与备份</td></tr>
<tr><td><img src="docs/media/library.png" width="230" alt="实际竖屏游戏库"></td><td><img src="docs/media/settings.png" width="230" alt="实际设置页"></td><td><img src="docs/media/saves.png" width="230" alt="实际存档管理页"></td></tr>
</table>

<details>
<summary>查看真实下载流程、游戏操作 GIF、录屏与来源</summary>

![实际下载与准备流程](docs/media/hall-walkthrough.gif)

![原创原生 2048 的实际方向输入](docs/media/native-2048.gif)

[大厅 MP4 录屏](docs/media/hall-walkthrough.mp4) · [2048 MP4 录屏](docs/media/native-2048.mp4) · [全部截图及来源记录](docs/media/README.md)。录屏不含音轨，展示范围以实录片段为限；完整可玩结论见[实现与验证记录](docs/hall/implementation-status.md)。

</details>

本地开发使用 Windows 原生工具与 USB 真机，不调用 Docker、WSL、虚拟机或模拟器。所有新工具、缓存、下载、构建和诊断均位于 `游戏移植/`。源码仓库不包含游戏本体、密钥、私有构建或存档。

```powershell
./devtools/hall_bootstrap.ps1
./devtools/hall.ps1 native
./devtools/hall.ps1 build
```

默认只准备原创开源 2048，无需私有游戏文件。前置工具是 Git、Python 3.13、Java 21、SDK 36 和 NDK r27c。已有本地《旷野之息》材料时，使用显式的 `-WithBotw` 配置；完整步骤见下方手册。

服务启动、内容打包/发布、USB 安装、测试、存档与更新、正式部署的完整步骤见 [中文手册](docs/hall/README.zh-CN.md)。本机快捷配置读取已有的游戏工作目录；其他机器必须提供自己的对应输入，不能仅克隆源码即获得商业游戏内容。

截图、GIF 与录屏来自实际安装的程序，均有来源与摘要记录。设计定稿位于[设计目录](docs/design/obsidian-portrait/README.md)，与[程序实录](docs/media/README.md)分别保存。[完整手册](docs/hall/README.zh-CN.md)说明方案、运行边界、发布链、使用步骤和恢复方法；[90 项需求实现对照](docs/hall/requirement-coverage.md)关联代码入口与待验收边界；[技术开发约束](docs/engineering/README.md)及[实施决策](docs/engineering/decisions/0005-client-guardrails-and-cache.md)约束后续修改。

上游核心与第三方组件保留原许可，参见 `nxnative/LICENSE` 和各组件说明。原生 2048 为本项目新增的独立开源游戏，其验证不替代 Switch 游戏兼容性结论。
