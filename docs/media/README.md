# Actual application media

Current reviewed USB set / 当前实拍素材：source `f1c68f7`, APK `d8c51a63f1c6134ec53237209cfc9253ffa543bc0206bfc6dfb029a1784c79af`. [Runtime execution record](../hall/runtime-round-2026-11-07.md).

| Current actual capture / 本轮实际采集 | Artifact | Provenance |
|---|---|---|
| Portrait library / 竖屏游戏库 | [PNG](runtime-library.png) | [JSON](runtime-library.json) |
| Completed v3 download history / v3 下载完成历史 | [PNG](runtime-installed.png) | [JSON](runtime-installed.json) |
| Fresh-process 2048 reload / 新进程重读棋盘 | [PNG](runtime-reloaded.png) | [JSON](runtime-reloaded.json) |
| v3 download/preparation, 15-second requested excerpt / 下载准备 | [GIF](runtime-hall.gif) · [MP4](runtime-hall.mp4) | [JSON](runtime-hall.json) |
| Actual 2048 input, pause/confirmation and return, 20-second excerpt / 操作与退出 | [GIF](runtime-2048.gif) · [MP4](runtime-2048.mp4) | [JSON](runtime-2048.json) |

Three screenshots and two GIF/MP4 pairs are added. All public clips have no audio track; private tone recordings are analyzed separately. The multiple completed 2048 rows are retained task history, not simultaneous downloads. Raw captures, exact encoded durations, SHA-256 digests and transformations are recorded in each JSON. No BotW frames, personal saves, keys or APKs are exported. / 新增 7 份素材，完成列表保留历史任务。音频结论不由无声视频证明；时间、摘要和转换逐份记录。

## Historical transfer candidate / 历史下载安装候选

Reviewed preceding USB set / 前一轮真机素材：source `720c785`, APK `e3b259216dbb9685b28040ff1108e586145cdd25f4693c018fb5806f714742da`. [Execution record](../hall/transfer-round-2026-11-07.md). No BotW frames or audio-quality pass are published.

| New actual capture | Artifact | Provenance |
|---|---|---|
| Discovery / 发现 | [PNG](transfer-discovery.png) | [JSON](transfer-discovery.json) |
| Completed v2 download / 下载历史 | [PNG](transfer-installed.png) | [JSON](transfer-installed.json) |
| Fresh-process reloaded 2048 / 重读棋盘 | [PNG](transfer-reloaded.png) | [JSON](transfer-reloaded.json) |
| Hall download excerpt, about 20 s / 大厅下载片段 | [GIF](transfer-hall.gif) · [MP4](transfer-hall.mp4) | [JSON](transfer-hall.json) |
| Native 2048 input and return excerpt, 20 s / 原生操作与返回 | [GIF](transfer-2048.gif) · [MP4](transfer-2048.mp4) | [JSON](transfer-2048.json) |

The new set adds 3 screenshots and 2 GIF/MP4 pairs. All clips are silent, with raw source hash, conversion and exact APK identity. The historical 23 artifacts below remain unchanged and belong to their original candidates. / 新增三张截图与两组 GIF/MP4；视频无音轨。后方 23 份旧素材保持原有身份。

以下历史素材说明保留其制作时点。本轮已重新获得 USB 测试授权；[下载与安装修正](../hall/transfer-round-2026-11-07.md)的新素材必须单独记录精确 APK 与采集来源。下方旧素材身份保持不变，不因源码更新自动成为新版截图。

The [download/install documentation review](../hall/transfer-install-review-2026-11-07.md) produced no new APK or captures. Existing media does not demonstrate its unimplemented queue/recovery corrections. / 下载与安装文档核对没有新 APK 或素材，现有实录不能证明后续待实施修正。

Only reviewed captures from the USB physical Android device belong here. Each exported PNG/GIF/MP4 has a neighboring provenance JSON recording the application ID, source hash and conversion. Screens captured from the design prototype stay in `docs/design` and must not be represented as runtime evidence.

The [recovery candidate](../hall/recovery-round-2026-11-07.md) has no new device captures yet. All images and clips below keep their original APK identities; they are not screenshots of the new recovery behavior. / 恢复修正候选尚未采集新真机素材；下方截图与视频继续对应各自旧 APK，不能代表新恢复界面或行为已验证。

Capture with `devtools/hall_device.py`, review private information and game content, then export using `devtools/hall_media.py`. An app UI recording demonstrates the visible behavior captured; it does not prove 30-minute game stability, save compatibility or the complete G0 matrix.

Actual captures date: 2026-11-07, Asia/Taipei. Device: Motorola edge plus, Android 12 / API 31, arm64-v8a, Adreno 650. The installed APK digest is recorded beside every USB image/clip. Source recordings, raw diagnostics and personal game saves remain private. The app interface currently uses Simplified Chinese; the project guides are bilingual.

This set contains **16 Android screenshots, 3 desktop screenshots, 2 GIFs and 2 MP4s**. The library image comes from merged-main APK `cd2b565141e81b1f7c73a1757eaa32b775495d96f0fcbd19b912b6475ed9a027`; the other USB sources come from the completed hall/2048 run on APK `5c451e68509bcb1d6e7018475cbf1325465980b7b759c19a4d40d71ffe78a8e8`. Android and service source are identical between feature commit `8709245` and merge commit `3c096c8`; native upstream changes make the APKs distinct. These are separate execution records. A neighboring capture timestamp denotes when its provenance was recorded, which can be at recording completion; encoded video timestamps determine excerpt duration.

| Actual Android page | Screenshot | Provenance |
|---|---|---|
| Discovery / 发现游戏（实验筛选） | [PNG](discovery.png) | [JSON](discovery.json) |
| Detail, information section / 详情信息区 | [PNG](game-detail.png) | [JSON](game-detail.json) |
| Download confirmation / 下载确认 | [PNG](download-confirmation.png) | [JSON](download-confirmation.json) |
| Downloads and completed history / 下载与已完成记录 | [PNG](downloads.png) | [JSON](downloads.json) |
| Prepared resources / 资源准备完成 | [PNG](installed.png) | [JSON](installed.json) |
| Library / 我的游戏 | [PNG](library.png) | [JSON](library.json) |
| Settings / 设置 | [PNG](settings.png) | [JSON](settings.json) |
| Device support / 设备支持 | [PNG](device-support.png) | [JSON](device-support.json) |
| Storage / 存储空间 | [PNG](storage.png) | [JSON](storage.json) |
| Help and feedback / 帮助与反馈 | [PNG](feedback.png) | [JSON](feedback.json) |
| Manage game / 管理游戏 | [PNG](manage-game.png) | [JSON](manage-game.json) |
| Saves / 存档与备份 | [PNG](saves.png) | [JSON](saves.json) |
| Native pause menu / 横屏暂停 | [PNG](game-pause.png) | [JSON](game-pause.json) |
| Return to portrait detail / 保留滚动位置返回详情 | [PNG](returned-portrait.png) | [JSON](returned-portrait.json) |
| Real native input / 2048 实际操作 | [PNG](2048-play.png) | [JSON](2048-play.json) |
| Reloaded native board / 2048 重读棋盘 | [PNG](2048-reloaded.png) | [JSON](2048-reloaded.json) |

| Sequence | GIF | MP4 | Provenance and limits |
|---|---|---|---|
| Detail → confirm download → prepared resources → library → settings | [GIF](hall-walkthrough.gif) | [MP4](hall-walkthrough.mp4) | [JSON](hall-walkthrough.json); continuous 38-second excerpt starting at source second 49 |
| Directional input in original native 2048 | [GIF](native-2048.gif) | [MP4](native-2048.mp4) | [JSON](native-2048.json); short input excerpt, approximately 7 seconds according to encoded timestamps |

The videos have no audio track. GIF conversion resizes and encodes the real frames; no design-prototype frames were inserted. Exact durations, dimensions and SHA-256 digests are in each JSON. A public clip is selected to exclude other applications and private game progress.

The walkthrough uses the original 2048 fixture: 171 downloaded bytes and 41 unpacked bytes. It establishes the actual service/download/install UI flow for that fixture, rather than the transfer time of a commercial game. Completed rows are retained task history. The detail/return captures retain the actual scroll position and show the information section. Commercial game title metadata may be visible; its program, resources and personal progress are not included in these media files.

The staff site is a separate **desktop web application**, captured from the real local HTTPS service. It is not an Android screenshot: [game metadata](operations-games.png), [publication management](operations-releases.png), [service health](operations-health.png), [desktop provenance](operations.json). Its browser/context were closed after capture; login credential fields were cleared before screenshots.

中文说明：这里展示的均为实际程序素材，手机截图来自 USB 真机，后台截图来自独立桌面网页。页面演示、输入片段、存档重读检查和完整游戏验收各有不同范围。声音、长时间运行、性能与双游戏切换以[验证记录](../hall/implementation-status.md)为准，不能由截图或 GIF 推断。设计图继续保存在 `docs/design`。
