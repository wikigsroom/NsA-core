# Implementation and verification record

Integrated verification, 2026-11-07. Internal development build; full game/device and production acceptance remain open. Main-branch integration and its rebuilt artifacts are recorded separately when executed. The physical device is a Motorola edge plus, Android 12 / API 31, arm64-v8a, Adreno 650.

The implementation includes the portrait Compose hall, persisted downloads, signed ZIP64 installation, save exchange and backups, isolated native runtime slots, catalogue/worker/operations services, native Windows tooling and bilingual documentation. See the [90-item source map](requirement-coverage.md), [operating guide](README.md), [中文手册](README.zh-CN.md), [reviewed media](../media/README.md) and [measured storage](storage.md). Source mapping is not acceptance of every rule.

| Gate | Actual evidence | Status |
|---|---|---|
| Android build | Both compiled native slots packaged; assemble, 6 JVM tests and lint | Passed |
| Physical Android instrumentation | Capability JSON wire types; Activity recreation preserves Settings navigation and Application-owned coordinator | 2 tests passed |
| Service integration | Real HTTP publication, signatures, object read-back, worker ownership, concurrency and durable credential receipt replay | 32 passed |
| Existing native/core checks | Native Windows Python environment | 111 run: 91 passed, 20 skipped |
| Portrait hall | Discovery, detail, download confirmation, preparation, library, settings, device, storage, feedback, game management and saves | USB walkthrough passed |
| Download → install → play | Real local HTTPS catalogue, signed 2048 archive and compiled slot in one APK | Passed |
| 2048 actual input/save/reload | Eight directional inputs, changed state, fresh game PID, byte-identical reloaded save and all 16 matching rendered tiles; portrait return | Passed short sequence |
| Audible output | Native audio implementation and earlier Android audio-output diagnostics; no audible confirmation | Not accepted |
| BotW resource installation | 15,354,465,247-byte archive; 41,086 files; actual App download, file verification and install; approximately 14.39 GiB installed allocation | Passed internal preparation |
| BotW initial save import | Actual App and system file picker; 8 original save files match their source hashes | Passed import only; not a new in-game commit |
| BotW play in unified APK | Standalone observations do not transfer; unified launch/input/new save/reload outstanding | Not accepted |
| G0 two-game A → B → A | Combined process/data/save isolation sequence outstanding | Not accepted |
| 30-minute gameplay and performance | No accepted continuous new record | Not accepted |
| README screenshots, MP4 and GIF | Real USB application sources with neighboring provenance; desktop operations captures explicitly separate | Exported; refreshed after final build |
| Production deployment | No production domain or release-signed APK published; S3/OIDC providers have source-level and local test coverage | Not deployed |

The successfully tested APK SHA-256 was `5c451e68509bcb1d6e7018475cbf1325465980b7b759c19a4d40d71ffe78a8e8`. In its combined hall/2048 scenario, the hall PID was 5981, the game PID changed from 7546 to 7983, and the committed/reloaded save SHA-256 was `b85235a04195b42d60b7148c0670380aceb7de37a49d9107aefa1770300be8a3`. Those PIDs are short-lived observations, not device identifiers. The native 2048 game is original open-source software and does not demonstrate Switch compatibility.

Resolved findings include stale DEX capability wire types (packaged verification now precedes install), durable large-publication journals and worker lease renewal, a PowerShell native-exit-code scope issue, real motion/audio-focus handling, separate cache/settings/save actions, bounded logs, application-scoped session coordination, operation locking and pre-update save backups. Credential recovery now retains pending encrypted receipts on transport failures, synchronously persists rotations, distinguishes 401 expiry from 403 scope denial, and replays a committed refresh without losing internal scope. Test navigation follows the real nested return stack and preserved scroll position.

No original requirement/acceptance ID is accepted merely by this document. Machine statuses remain unchanged until complete specified evidence exists. Private raw reports and game progress stay in `.gamedump/Hall/device-evidence/`; execution logs stay in `output/hall/`. Public clips have no audio track and cannot establish sound or sustained playability. Earlier failed attempts remain private and are not relabeled as successful runs.

中文说明：当前版本已通过完整大厅流程和 2048 实际输入、写档、正常退出、新进程重读及竖屏恢复。服务端 32 项通过，端侧 6 项单元测试与 2 项真机仪器测试通过；原有核心共 111 项，其中 91 项通过、20 项跳过。《旷野之息》已在统一 App 内完成资源下载、校验、安装及原始存档导入，实际游玩、声音、新写档重读、双游戏连续切换与长期性能仍需分别验收。后续更新同步到 main，并保留远端已有修改。
