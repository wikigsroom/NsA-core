# Implementation and verification record

[构建修正轮次](native-build-round-2026-11-07.md)已安装：源码 `d427f08`、APK `5808f5…6d06`，两个运行库的实际编译身份与安装包一致。2048 v4 已下载、操作、保存并重读；《旷野之息》已进入真实场景，完成触控移动、新手动存档、正常返回与新进程重读。20 项原生回归通过；Android 首次 12/13（导航等待超时），同包重跑 13/13。**系统因内存不足回收大厅后，旧会话曾阻止切换，需显式恢复；自动恢复、持续可玩性和正式发行仍未验收。** 重启大厅后完成 5/5 轮 A → B → A；保留此前中断。完整 G0 仍待验收。

[Current machine record / 当前机器记录](verification/2026-11-07-native-build-round.json) · [Current build correction / 构建轮次](native-build-round-2026-11-07.md) · [Preceding runtime record / 前一运行轮次](runtime-round-2026-11-07.md) · [Reviewed media / 实拍素材](../media/README.md)

| Current candidate / 当前候选 | Actual result / 实际结果 |
|---|---|
| Native + Android | 20/20 native regression cases, no skips, including real NDK compilation under Unicode/spaced paths; 145 BotW inputs checked, 1 actually rebuilt, 144 cached, 0 failed; both ELF identities/SONAMEs and final packaged guard passed |
| Android + JVM + lint | Build passed in 3m 26s, 9 tasks executed / 81 UP-TO-DATE; existing 26 JVM cases retained rather than freshly run; lint 0 errors / 16 warnings |
| Documentation/contracts | 8/8 groups, 13 schemas and 20 positive examples; this is contract validation, not gameplay |
| Physical instrumentation | First run 12/13, navigation initial wait timed out; same-APK rerun 13/13 in 10.31s. Cold-start performance remains open |
| Overlay preservation | Room v2, all 21 preexisting player files identical after overlay and instrumentation; no recovery warning; transfer directory 4 KiB |
| Content publication | Immutable 2048 v4 published through real API/reviewer/worker, 191 archive bytes / 41 expanded; 36 earlier immutable records preserved; BotW resources reused |
| 2048 v4 | Eight actual inputs, normal direct Back exit, fresh game PID and matching committed bytes plus all 16 rendered tiles |
| Audio | Two actual 440 Hz captures passed with fits 0.999903 / 0.999836; 4 reopens / 5 output starts, 5 and 4 changed saved moves. No subjective or BotW audio acceptance |
| BotW | Actual touch movement, new manual slot 1, direct Back pause, cancel/resume same process, normal exit/portrait and fresh-process reload; all three committed slot files match |
| Main-process loss | Android records LOW_MEMORY for hall PID 23279 while game PID 29126 survives. Normal exit recreates hall PID 3158; stale persisted session blocks A. Recovery confirmation was opened but not submitted; a fresh restart reconciled the row. USB-RECOVER-01 remains open |
| Repeated switching | 5/5 repeated A → B → A rounds completed after a fresh hall restart; preceding interrupted attempts retained. Full G0 remains open. |
| Performance/release | Short shrine p50 79.45ms / p95 94.26ms is not a representative benchmark or an accepted playable label; full G0, 30-minute performance, sound, physical faults and production remain open |

Public captures add three PNGs and two silent GIF/MP4 pairs from APK `5808f5…6d06`. Historical media retains its original source hashes. Failed instrumentation and switching attempts remain recorded; a later rerun does not erase them.

## Historical execution and review snapshots / 历史快照

The following text, counts, PIDs, APKs and pending labels describe their original snapshots. Consult the current record above for the active candidate. / 下文旧计数、进程、候选和“待执行”均属于原轮次，当前状态以上方新记录为准。

Historical installed build, recorded 2026-11-07, Asia/Taipei: merged source `3c096c8`, retaining remote main `4d1348c` and feature `8709245`. This is the retained phone build, not the latest repository revision. The physical device is a Motorola edge plus, Android 12 / API 31, arm64-v8a, Adreno 650, with 4096-byte pages.

The implementation includes the portrait Compose hall, persisted downloads, signed ZIP64 installation, save exchange and backups, isolated native runtime slots, catalogue/worker/operations services, native Windows tooling and bilingual documentation. See the [90-item source map](requirement-coverage.md), [operating guide](README.md), [中文手册](README.zh-CN.md), [reviewed media](../media/README.md), [measured storage](storage.md), [next device procedure](device-validation.md), [machine-readable execution summary](verification/2026-11-07-main.json) and [targeted source review](source-review.md). Source mapping is not acceptance of every rule.

The historical documentation-only review inspects source `cdedc71`: APP-05, APP-08 and SAVE-04, all twelve detailed rules. Separate code/specification refutation retains 16 original defect cores (15 after deduplicating the surviving-process issue); APP-05-D03 remains unverified, and a supplementary review resolves unified native player import/export as in scope. No new APK, application tests or phone operations were performed in that review round. The later [correction batch](recovery-round-2026-11-07.md) implements the changes with separate build/test evidence and pending device cases. The other 87 requirements were not individually audited. Read the [historical findings](source-review.md), [evidence records](reviews/2026-11-07/README.md) and [structured review](verification/2026-11-07-source-review.json).

历史文档轮次只做源码核对与交付说明：三个需求、十二条细则，独立复核后保留十六条有证据支持的缺陷核心，重复根因合并后十五项；初始离线策略问题未获确认，另补充统一原生菜单的适用范围。该历史轮次没有新构建、程序测试或手机操作；随后修正批次已有独立实现和电脑端验证记录，真机仍待执行。其余 87 项没有逐条审查。以下历史执行结果继续对应各自原 APK。

| Gate | Actual evidence | Status |
|---|---|---|
| Native main build | BotW host: 145 sources, zero failed; bootstrap and two explicitly selected slots collected | Passed |
| Android main build | Assemble debug/test APKs, 6 JVM tests and lint; packaged classes, registry and library digest checks | Passed |
| Physical Android instrumentation on main | Capability JSON wire types; Activity recreation preserves Settings navigation and Application-owned coordinator | 2 tests passed |
| Service integration | Real HTTP publication, signatures, object read-back, worker ownership, concurrency and durable credential receipt replay | 32 passed |
| Existing native/core checks on main | Native Windows Python environment | 131 run: 92 passed, 39 skipped; skips are not passes |
| Contract/document baseline | Requirement identities, schemas, offline references, signing vectors, negative cases and links | 8/8 groups passed after documentation refresh |
| Hall README/media validation | Nine Markdown documents, 519 local links, all 23 media artifacts and 19 provenance records; exact APK/hash match and full clip decode | Passed |
| Portrait hall on preceding APK | Discovery, detail, download confirmation, preparation, library, settings, device, storage, feedback, game management and saves | USB walkthrough passed |
| Download → install → play on preceding APK | Real local HTTPS catalogue, signed 2048 archive and compiled slot in one APK | Passed |
| 2048 actual input/save/reload on preceding APK | Eight directional inputs, changed state, fresh game PID, byte-identical reloaded save and all 16 matching rendered tiles; portrait return | Passed short sequence |
| Audible output | Native audio implementation and earlier Android audio-output diagnostics; no audible confirmation | Not accepted |
| BotW resource installation | 15,354,465,247-byte archive; 41,086 files; actual App download, file verification and install; approximately 14.39 GiB installed allocation | Passed internal preparation |
| BotW initial save import | Actual App and system file picker; 8 original save files match their source hashes | Passed import only; not a new in-game commit |
| BotW title/load on main | Continue recognizes original save; Start loads Shrine of Resurrection scene | Observed; private frames/log retained |
| BotW movement/new progress save/reload on main | Another USB test externally force-stopped NsA during the scenario | Not completed; not accepted |
| G0 two-game A → B → A | Combined process/data/save isolation sequence outstanding | Not accepted |
| 30-minute gameplay and performance | No accepted continuous new record | Not accepted |
| README screenshots, MP4 and GIF | 16 USB screenshots, 3 desktop screenshots, 2 GIF/MP4 pairs; neighboring provenance identifies each source build | Reviewed and exported |
| Production deployment | No production domain or release-signed APK published; S3/OIDC providers have source-level and local test coverage | Not deployed |

The preceding APK SHA-256 was `5c451e68509bcb1d6e7018475cbf1325465980b7b759c19a4d40d71ffe78a8e8`. Its completed hall/2048 scenario used hall PID 5981 and game PIDs 7546 → 7983. Committed/reloaded save SHA-256 was `b85235a04195b42d60b7148c0670380aceb7de37a49d9107aefa1770300be8a3`, and all 16 rendered tiles matched. PIDs are short-lived observations, not device identifiers. The original native 2048 does not demonstrate Switch compatibility.

The merged-main APK SHA-256 is `cd2b565141e81b1f7c73a1757eaa32b775495d96f0fcbd19b912b6475ed9a027`. Android, service, content-tool and UI-harness source have no changes between `8709245` and `3c096c8`. The merge preserves upstream native changes; its shader conflict retains both the cull-output fix and NaN-aware GLSL handling. Different native libraries make these APKs distinct: the preceding gameplay result is not a completed gameplay result on main.

Main started BotW with hall PID 11498 and game PID 11728. Actual input selected Continue, the imported save and Start, then the game rendered Shrine of Resurrection. A startup `save_committed` event does not establish a new gameplay-progress save. Movement/manual-save/normal-exit/fresh-process-reload remain required. Menu frame rates and adaptive logs do not establish in-game performance thresholds.

At 05:08:24 local, Android records `USER REQUESTED` externally stopping both NsA processes during the concurrent USB test. This run is interrupted, rather than accepted as a normal exit or relabeled as an internal crash. The user requested keeping the other test and finishing documentation, so NsA device input stopped. No other project's files or processes were changed.

The existing private internal fixtures retain historical nominal `core_revision` `80340c5521b589a4dd82b89306e680c8e5174c02`. This retained compatibility label does not claim that current libraries were compiled from that old source. The registry binds actual library digests: 2048 `9ac6aad3cf6d414bd7ed421c22379452a59d0e3c0c64e92fd16f7b7ee2bce40c`, BotW `3eda71ce7ef9f6ed5234a909f7e2593772651afd36443372d83eef9c1cdda20f`. Packaged verification checks these bytes. A production candidate must regenerate identities from clean committed source, issue matching immutable releases, configure signing/service and repeat acceptance. Do not overwrite published fixtures. The debug APK contains private provisioning and remains excluded from Git/public distribution.

Resolved findings include stale DEX capability wire types (packaged verification now precedes install), durable large-publication journals and worker lease renewal, a PowerShell native-exit-code scope issue, real motion/audio-focus handling, separate cache/settings/save actions, bounded logs, application-scoped session coordination, operation locking and pre-update save backups. Credential recovery now retains pending encrypted receipts on transport failures, synchronously persists rotations, distinguishes 401 expiry from 403 scope denial, and replays a committed refresh without losing internal scope. Test navigation follows the real nested return stack and preserved scroll position.

No original requirement/acceptance ID is accepted merely by this document. Machine statuses remain unchanged until complete specified evidence exists. Private raw reports and game progress stay in `.gamedump/Hall/device-evidence/`; execution logs stay in `output/hall/`. Public clips have no audio track and cannot establish sound or sustained playability. Earlier failed attempts remain private and are not relabeled as successful runs.

中文说明：已将完整源码合并并推送到 main，保留远端更新。主分支构建、六项单元测试、静态检查和两项真机仪器测试通过；原有核心共 131 项，其中 92 项通过、39 项跳过；服务端 32 项通过。前一 APK 的完整大厅和 2048 操作、保存、正常退出、新进程重读及竖屏恢复通过。main APK 已将《旷野之息》原始存档读入复苏神庙；另一项真机测试主动结束 NsA，后续移动、新写档重读、声音、双游戏切换与长期性能尚未完成。按用户要求保留该测试并先完成文档，未把中断写成通过。
