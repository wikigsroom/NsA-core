# Actual application media

Only reviewed captures from the USB physical Android device belong here. Each exported PNG/GIF/MP4 has a neighboring provenance JSON recording the application ID, source hash and conversion. Screens captured from the design prototype stay in `docs/design` and must not be represented as runtime evidence.

Capture with `devtools/hall_device.py`, review private information and game content, then export using `devtools/hall_media.py`. An app UI recording demonstrates the visible behavior captured; it does not prove 30-minute game stability, save compatibility or the complete G0 matrix.

Actual captures date: 2026-11-07, Asia/Taipei. Device: Motorola edge plus, Android 12 / API 31, arm64-v8a, Adreno 650. The installed APK digest is recorded beside every USB image/clip. Source recordings, raw diagnostics and personal game saves remain private. The app interface currently uses Simplified Chinese; the project guides are bilingual.

| Actual Android page | Screenshot | Provenance |
|---|---|---|
| Library / 我的游戏 | [PNG](library.png) | [JSON](library.json) |
| Settings / 设置 | [PNG](settings.png) | [JSON](settings.json) |
| Device support / 设备支持 | [PNG](device-support.png) | [JSON](device-support.json) |
| Storage / 存储空间 | [PNG](storage.png) | [JSON](storage.json) |
| Manage game / 管理游戏 | [PNG](manage-game.png) | [JSON](manage-game.json) |
| Saves / 存档与备份 | [PNG](saves.png) | [JSON](saves.json) |
| Native pause menu / 横屏暂停 | [PNG](game-pause.png) | [JSON](game-pause.json) |
| Return to portrait detail / 保留滚动位置返回详情 | [PNG](returned-portrait.png) | [JSON](returned-portrait.json) |
| Real native input / 2048 实际操作 | [PNG](2048-play.png) | [JSON](2048-play.json) |
| Reloaded native board / 2048 重读棋盘 | [PNG](2048-reloaded.png) | [JSON](2048-reloaded.json) |

| Sequence | GIF | MP4 | Provenance and limits |
|---|---|---|---|
| Confirm download → prepare resources → library | [GIF](hall-walkthrough.gif) | [MP4](hall-walkthrough.mp4) | [JSON](hall-walkthrough.json); a continuous excerpt, approximately 38 seconds |
| Directional input in original native 2048 | [GIF](native-2048.gif) | [MP4](native-2048.mp4) | [JSON](native-2048.json); short input excerpt, approximately 7 seconds according to encoded timestamps |

The videos have no audio track. GIF conversion resizes and encodes the real frames; no design-prototype frames were inserted. Exact durations, dimensions and SHA-256 digests are in each JSON. A public clip is selected to exclude other applications and private game progress.

The staff site is a separate **desktop web application**, captured from the real local HTTPS service. It is not an Android screenshot: [game metadata](operations-games.png), [publication management](operations-releases.png), [service health](operations-health.png), [desktop provenance](operations.json). Its browser/context were closed after capture; login credential fields were cleared before screenshots.

中文说明：这里展示的均为实际程序素材，手机截图来自 USB 真机，后台截图来自独立桌面网页。页面演示、输入片段、存档重读检查和完整游戏验收各有不同范围。声音、长时间运行、性能与双游戏切换以[验证记录](../hall/implementation-status.md)为准，不能由截图或 GIF 推断。设计图继续保存在 `docs/design`。
