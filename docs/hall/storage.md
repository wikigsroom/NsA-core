# Local storage / 本地存储

构建修正候选在五轮游戏切换后的快照显示：Room 2、无恢复警告、15 个会话正常结束、无未结束会话，下载临时目录 **4 KiB**；手机 /data 剩余 **43,758,170,112 字节（40.75 GiB）**，未删除任何原有玩家文件。电脑目录尺寸记录于 **2026-11-07 12:09（Asia/Taipei）**，早于为下一修正轮保存额外 APK 的操作；后续产物需另计。物理断电边界仍未验收。

Observed on 2026-11-07. All paths below are relative to this checkout under `游戏移植/`. Values are **logical file sizes**, rounded to GiB; hard links and filesystem allocation can make physical disk use differ. They are measurements of this local development profile, not a minimum download size for every user.

2026-11-07 实测。下列目录均位于指定工作根下；数值为逻辑文件大小，硬链接与实际分配会影响物理占用。它们描述本机开发材料，不是所有玩家的必需下载量。

| Directory | Logical size | Retained purpose / 保留原因 |
|---|---:|---|
| `.gamedump/Hall/` | 14.636 GiB | Private packaged resources, native build and raw USB evidence / 私有资源归档、原生构建和真机原始证据 |
| `.gamedump/BOTW/` | 39.423 GiB | Existing extracted inputs, standalone build, original saves and diagnostics / 已有解包输入、单游戏构建、原始存档与诊断 |
| `services/local/` | 14.311 GiB | Private service object copy, database and development credentials / 目录服务使用的私有对象副本、数据库和开发凭据 |
| `tools/hall/` | 2.109 GiB | SDK, Gradle, official scrcpy and reproducible local build caches / SDK、Gradle、官方录音工具及本地构建缓存 |
| `tools/player/toolchains/` | 2.951 GiB | Existing native Android toolchain / 原生 Android 工具链 |
| `android/` | 0.294 GiB | Source plus generated app/test packages and build output / 源码、应用及测试包与构建输出 |
| `output/` | 0.435 GiB | Reviewed design/validation outputs and private prior/current APKs, bindings, database backup and build logs / 设计验证输出、本轮新旧安装包、绑定、数据库备份与日志 |

The native-build measurement found **476,300,509,184 free bytes** on the development drive (about 443.59 GiB). This is an observation at measurement time, not reserved capacity or the minimum installation requirement. The five retained directories above 1 GiB have their purposes listed above; no original game inputs, progress or published service objects were removed. / 构建轮次测得开发盘剩余约 443.59 GiB；五个超过 1 GiB 的目录按表中用途保留，未删除原游戏输入、进度或服务已发布对象。

The private BotW archive is **15,354,465,247 bytes** (14.300 GiB), with **41,086 files** and **15,346,165,157 unpacked bytes** (14.292 GiB). The initial phone preparation estimate is **32,848,114,052 additional bytes** (30.592 GiB), including the 2 GiB safety reserve. It excludes the already installed APK, existing saves and a retained previous release during an update. The historical [PKG-06-F03](transfer-install-review-2026-11-07.md) gap could retain completed archives. The [correction round](transfer-round-2026-11-07.md) implements journal-backed/legacy cleanup and observed a 4 KiB transfer directory; physical power-loss coverage remains open. Directory allocation and generated metadata add overhead; the installed resource directory was observed at approximately 14.39 GiB.

本机《旷野之息》首次准备需约 **30.592 GiB 新增可用空间**，资源目录实测约 14.39 GiB。归档清理是预期行为；旧版本恢复路径可能遗留约 **14.30 GiB** 完整归档；本轮已修正并实测当前临时目录仅 4 KiB，不代表全部掉电边界通过。更新保留旧版本、存档备份和图形缓存时，还需另行计算。电脑同时保留打包归档和服务对象副本，各占约 14.30 GiB。

General additional-space rule: `max(archive - downloaded, 0) + max(expanded - installed, 0) + working + max(2 GiB, ceil(expanded × 5%))`. The client rechecks remaining space while downloading and extracting. The APK alone is not the total footprint. The default open-source 2048 profile is much smaller and does not require BotW resources.

Private inputs, keys, saves, native binaries, APKs and raw device captures remain ignored by Git. Retain original progress and only copies of source inputs until their ownership/links are checked; do not remove them merely to shrink a source commit. Reviewed README media belongs in `docs/media/`. See [the guide](README.md), [中文手册](README.zh-CN.md) and [actual verification record](implementation-status.md).
