# Physical-device validation / 真机验收操作

[Executed results / 已执行结果](implementation-status.md) · [English guide](README.md) · [中文手册](README.zh-CN.md) · [Normative gates / 技术门槛](../engineering/08-quality-governance.md)

This procedure is for the **next** device run, not a pass report. The merged-main run reached BotW's title and imported Shrine scene, then another USB test stopped NsA. The user requested preserving that test and finishing documentation first. Resume when the phone is available for this run.

本文件规定下一轮操作，不代表已经验收通过。上次 main 运行至《旷野之息》标题和导入存档的神庙场景，随后被另一项 USB 测试结束。按用户要求保留该测试，先完成文档，待手机可用后继续。

The [historical targeted source review](source-review.md) led to the [completed source correction batch](recovery-round-2026-11-07.md). Its computer-side checks and candidate identity are recorded separately. Install only after content/service rollout is ready, then repeat the affected device cases. Historical screenshots and passes do not transfer to a changed APK.

[历史定向源码核对](source-review.md)对应的[整批源码修正](recovery-round-2026-11-07.md)已有独立电脑端检查与候选身份。真机安装须先完成资源和服务更新准备，再重跑受影响案例；旧截图及通过结果不能转算到新 APK。

The later [download/install review](transfer-install-review-2026-11-07.md) identifies additional pending source corrections. Before a new acceptance run, finish its complete queue/intent, install publication/recovery and cleanup batch and the consolidated computer checks. Then add late enqueue, cancel/replacement, equal-time ordering, same-release repair interrupted after VERIFIED, journal failures and recovered-archive cleanup to the device matrix. No device run or new APK is implied by that review.

后续[下载与安装核对](transfer-install-review-2026-11-07.md)的修正仍未实施。下一验收批次先整批完成代码及电脑检查，再补测新增任务接续、取消/替代、同时间排序、同版本修复在 VERIFIED 后退出、候选失败隔离及恢复后归档清理。目录同步的实际掉电验证与普通进程退出必须分开记录；不破坏真实玩家文件模拟故障。

## Preparation / 准备

1. Finish intended source changes, then run consolidated checks. Use native Windows tools and one authorized USB physical Android device. No emulator or virtualization environment.
2. Ensure no second automation, instrumentation or manual user controls the same phone. If focus moves to another app, stop input/recording and retain the interrupted evidence. Obtain permission before stopping another project's work; do not repeatedly take over its app.
3. Export important progress through the App save page/system picker. Do not clear data, uninstall, reset the phone or modify the standalone game installation. Overlay installation can stop a game: exit normally and confirm no live `:game` process before installation.
4. Start the HTTPS API and worker; check USB authorization, `adb reverse tcp:8443 tcp:8443`, matching CA and invitation. Keep keys/tokens and raw diagnostics private.
5. Record source commit, tree state, APK SHA/version/signer, registry/library digests, releases, profile/module/driver identities, OS/GPU/RAM/page size, graphics settings and timestamps. A production candidate needs identities generated from clean committed source. For the recovery candidate, use its recorded native revision and new 2048 resource release; never relabel the published v1 fixture or reuse its incompatible profile.

先完成整批代码，再统一构建验证。确保手机没有其他控制任务；重要进度先通过 App 导出。安装前正常结束游戏，不清数据、不卸载、不重置。记录当前构建、内容、运行组件、驱动、设备与设置，凭据不写入公开记录。

```powershell
./devtools/hall.ps1 verify
./devtools/hall.ps1 install
services/.venv/Scripts/python.exe devtools/hall_smoke.py all
```

`all` removes/re-downloads only the original 2048 resources and checks that saved bytes survive. It does not execute the BotW/G0 steps below. Run installation only after the preparation above.

## Recovery-candidate cases / 恢复候选专测

The candidate includes **seven compiled instrumentation cases, zero executed in the correction round**. Run the complete `DeviceContractTest`, `NavigationRecoveryTest` and five-case `SaveRecoveryTest` suite on the one authorized phone after preserving its progress. `SaveRecoveryTest` uses separate Room/files roots beneath its own test cache; it never targets the player's installed games or saves. Compilation alone does not establish its filesystem assertions.

新候选的 7 项仪器测试均已编译、本轮均未执行；其中 5 项新增用例覆盖真实存档导入/再导出与幂等、原游戏确认恢复、2048 暂存排除、安装/分块对账、旧选择器回调归属。待手机可用时整套执行，保留测试报告和失败；测试私有目录与玩家资料隔离。

| Case / 场景 | Required observation / 必须观察 |
|---|---|
| Scoped policy / 范围策略 | Repeated unchanged responses retain signed identity; narrowed/older scope cannot erase a known release block; installed fixed-digest content tolerates key revocation alone while exact release blocking still wins. Use separate local fault fixtures; restore policy after the scenario. |
| Offline readiness / 离线准备 | First trusted preparation has a visible retry path; a valid empty cache differs from no cache. Prepared offline-permitted content starts without a second online dependency. |
| Catalogue recovery / 目录恢复 | Commit a search/type filter, recreate the main process and restore matching results, query, endpoint and update time; stale earlier requests cannot overwrite the latest query. |
| Live game with main-process loss / 大厅进程死亡 | Preserve the running `:game` process while ending only this app's main process. The rebuilt hall keeps the kernel guard, authenticates reconnect to the original session/generation, receives replay and starts no second native instance. Unknown ownership remains guarded. Package-wide force-stop is a different case. |
| Pending chooser / 文件选择器 | Open import/export, recreate Activity/main process, return the result and retain original game/operation ownership. Cancel, start a different same-kind operation, and ensure a delayed old result cannot affect it or report success. |
| Confirmation/cancellation / 确认与取消 | Before confirmation, cancel removes only the draft. Throughout confirmed import, repeated confirm/back/outside dismissal cannot cancel publication; progress remains visible. Retry retains the verified private archive. |
| Publication faults / 交换故障 | With expendable isolated test saves, interrupt around journal/old rename/new rename/index/cleanup and directory-sync/space faults; recover the complete indexed new snapshot or the original old snapshot, never an empty replacement. Repeat recovery without advancing the import revision again. |
| Export/native boundary / 导出与原生入口 | Export the same authoritative snapshot represented by its local revision; no demo staging file. Hall native menus cannot bypass the exchange coordinator; standalone behavior remains separately tested. |

Use isolated generated saves and a local test service for destructive/fault cases. Preserve the player's originals and the candidate's exact SHA. These scenarios are additional to the portrait walkthrough, actual game progress, audio, repeated switching and sustained-play gates below.

故障注入使用可丢弃的隔离测试档和本地服务，保留原始进度及候选摘要。以上专测不能替代下方的真实游戏操作、新进度保存重读、声音、五轮切换与连续游玩验收。

## Portrait hall / 竖屏大厅

| Action / 操作 | Required observation / 必须观察 |
|---|---|
| Launch / 打开 App | Portrait, local player, cache/error recovery, no automatic game start |
| Discover / 查找游戏 | Search/type/pagination, exact-device/experimental filters, truthful compatibility |
| Detail / 查看详情 | Version/language/controls, download/expanded bytes, reserve and limitations |
| Confirm / 确认下载 | Per-task experimental/network consent where needed; safe cancellation |
| Download/install / 下载与准备 | Real signed responses, checked bytes, durable task, complete ready installation |
| Library / 游戏库 | Prepared games reachable by scrolling/search; Continue and management are separate |
| Management / 管理 | Separate saves/settings/cache/resources; destructive scope explained |
| Return / 返回 | Source page, query/filter and scroll position restored in portrait |

Capture actual states. A small fixture can finish between frames; do not invent progress or extrapolate a commercial-game transfer rate. Completed queue rows are history, rather than simultaneous active downloads.

## Same-APK G0 / 同一 APK 双游戏

Use **A = original native 2048**, **B = prepared BotW**, one exact APK. 2048 establishes the slot/session mechanism. If the accepted release scope requires two Switch titles, this pair is insufficient: prepare a second distinct Switch title.

1. **A:** launch from Library; record separate main/game PIDs and data roots. Make real directional moves. Capture the board, committed-state digest and real save event. Exit through the normal confirmation; verify portrait return, old PID/window/audio/input cleanup and hall survival/reconstruction.
2. **B:** launch in the same App with a fresh game PID distinct from A/the hall. Select Continue, the intended imported save, then Start. Wait for an interactive scene; first frame/loading text alone is insufficient.
3. Move and perform representative real actions, retaining before/after evidence. Save **new gameplay progress** through the game's Save action, then record completion and committed changes. Startup option writes/imported fixtures are not new progress. Retain original exports.
4. Exit B normally and inspect final session state, old PID release and portrait return. Force exit, crash and external force-stop are separate cases, not normal-exit passes.
5. **A again:** use a fresh process and verify its exact committed board/state survived B unchanged. Exercise input/save/exit. Reopen B in another fresh process and actually load the new progress from step 3; compare state and location/scene. A file hash without the game reading it is insufficient.
6. Repeat A → hall → B → hall → A at least five times for the normative switching gate. Record every PID/event/root and inspect resource accumulation. Retain interruptions and failed samples instead of silently replacing them.

A 使用原创 2048，B 使用已准备的《旷野之息》，必须为同一 APK。每次启动新游戏进程；A 实际移动保存，B 读入后实际操作并新保存；正常退出后分别重开读取。核对存档、画面、进程与数据隔离，完成至少五轮切换。2048 不等同于第二款 Switch 游戏。

For developer input checks, Android `BUTTON_B` maps to Switch A, `BUTTON_A` to Switch B and `BUTTON_START` to Plus. The touch overlay uses displayed Switch labels. Check current scene/focus before every input; never replay unreviewed coordinates into another menu/app.

## Audio, interruptions and sustained play / 声音、中断与长期运行

| Area / 项目 | Evidence / 证据要求 |
|---|---|
| Sound / 声音 | Actual game-action output matched to its PID/track/route, confirmed by listening or suitable internal-audio recording. Active tracks/queued blocks alone do not establish audible correctness. Public recordings are silent. |
| Interruption / 中断 | Background/lock/focus loss pauses graphics/audio; explicit continuation retains the same session and restores input/audio. Do not record other apps or private notifications. |
| Exit / 退出 | Separate normal exit, deliberate force exit, crash and external termination; observe committed-progress preservation and hall recovery. |
| Continuous play / 连续游玩 | At least 30 minutes of representative actual input/gameplay on the candidate, including save/reload and thermal/power/settings context. Paused menus/interrupted recordings are insufficient. |
| Performance / 性能 | Actual present timestamps, total frames/duration, average FPS, p95, 1% low and logic speed. The 30 FPS gate is average ≥27, p95 ≤50 ms, 1% low ≥20, speed 0.95–1.05×. |

Follow the [normative measurement definitions](../engineering/08-quality-governance.md). Menu counters, sparse long-frame logs, window refresh rate and selected fast segments are not complete frame distributions. Declare loading intervals separately beforehand, retain gameplay stalls and failed samples. API 31 acceptance does not establish other APIs, 16 KiB pages, GPUs or tablets.

## Evidence and release / 记录与发布

Raw reports/game frames/progress stay in ignored `.gamedump/Hall/device-evidence/`. Each result records case/requirement IDs, artifact/device/content identities, preconditions, timestamps, actions, expected/actual behavior and `pass`/`fail`/`interrupted`/`not_run`. Only reviewed App UI/demo media with provenance goes to `docs/media/`. Close unused browser tabs after staff-site capture.

Update [implementation-status.md](implementation-status.md) and the execution summary from actual results. Accept normative cases/publish device compatibility only after complete corresponding evidence passes. Production signing, provisioning, infrastructure and immutable matching identities remain separate gates. Commit completed code/docs/reviewed media to `origin/main`, retaining its history.

原始游戏画面、存档与诊断保持私有。每条结果明确构建、设备、内容、步骤、期望、实际与状态，中断不能改写为通过。正式发布前核验身份、签名、服务、内容与设备组合；后续更新继续同步 main。
