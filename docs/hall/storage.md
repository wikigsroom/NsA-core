# Local storage / 本地存储

Observed on 2026-11-07. All paths below are relative to this checkout under `游戏移植/`. Values are **logical file sizes**, rounded to GiB; hard links and filesystem allocation can make physical disk use differ. They are measurements of this local development profile, not a minimum download size for every user.

2026-11-07 实测。下列目录均位于指定工作根下；数值为逻辑文件大小，硬链接与实际分配会影响物理占用。它们描述本机开发材料，不是所有玩家的必需下载量。

| Directory | Logical size | Retained purpose / 保留原因 |
|---|---:|---|
| `.gamedump/Hall/` | 14.35 GiB | Private packaged resources, native build and raw USB evidence / 私有资源归档、原生构建和真机原始证据 |
| `.gamedump/BOTW/` | 39.42 GiB | Existing extracted inputs, standalone build, original saves and diagnostics / 已有解包输入、单游戏构建、原始存档与诊断 |
| `services/local/` | 14.31 GiB | Private service object copy, database and development credentials / 目录服务使用的私有对象副本、数据库和开发凭据 |
| `tools/hall/` | 2.07 GiB | SDK, Gradle and reproducible local build caches / SDK、Gradle 及本地构建缓存 |
| `tools/player/toolchains/` | 2.95 GiB | Existing native Android toolchain / 原生 Android 工具链 |
| `android/` | 0.29 GiB | Source plus generated app/test packages and build output / 源码、应用及测试包与构建输出 |

The private BotW archive is **15,354,465,247 bytes** (14.300 GiB), with **41,086 files** and **15,346,165,157 unpacked bytes** (14.292 GiB). The initial phone preparation estimate is **32,848,114,052 additional bytes** (30.592 GiB), including the 2 GiB safety reserve. It excludes the already installed APK, existing saves and a retained previous release during an update. Completed installation removes the reproducible download archive from the phone. Directory allocation and generated metadata add overhead; the installed resource directory was observed at approximately 14.39 GiB.

本机《旷野之息》首次准备需约 **30.592 GiB 新增可用空间**，安装完成后删除可重建的下载归档，资源目录实测约 14.39 GiB。更新保留旧版本、存档备份和图形缓存时，还需另行计算。电脑同时保留打包归档和服务对象副本，各占约 14.30 GiB。

General additional-space rule: `max(archive - downloaded, 0) + max(expanded - installed, 0) + working + max(2 GiB, ceil(expanded × 5%))`. The client rechecks remaining space while downloading and extracting. The APK alone is not the total footprint. The default open-source 2048 profile is much smaller and does not require BotW resources.

Private inputs, keys, saves, native binaries, APKs and raw device captures remain ignored by Git. Retain original progress and only copies of source inputs until their ownership/links are checked; do not remove them merely to shrink a source commit. Reviewed README media belongs in `docs/media/`. See [the guide](README.md), [中文手册](README.zh-CN.md) and [actual verification record](implementation-status.md).
