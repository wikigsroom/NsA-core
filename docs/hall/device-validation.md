# Physical-device validation / 真机验收操作

[Executed results / 已执行结果](implementation-status.md) · [English guide](README.md) · [中文手册](README.zh-CN.md) · [Normative gates / 技术门槛](../engineering/08-quality-governance.md)

This procedure is for the **next** device run, not a pass report. The merged-main run reached BotW's title and imported Shrine scene, then another USB test stopped NsA. The user requested preserving that test and finishing documentation first. Resume when the phone is available for this run.

本文件规定下一轮操作，不代表已经验收通过。上次 main 运行至《旷野之息》标题和导入存档的神庙场景，随后被另一项 USB 测试结束。按用户要求保留该测试，先完成文档，待手机可用后继续。

The [targeted source review](source-review.md) adds open policy, reconstruction and save-exchange corrections to the next complete source batch. Finish that batch before its consolidated checks; record the new candidate's identity and repeat affected cases. Historical screenshots and passes do not transfer to a changed APK.

[定向源码核对](source-review.md)已补充下一整批的修正与故障验证要求。完成整批后集中验证，记录新候选身份并重跑受影响案例；旧截图及通过结果不能转算到新 APK。

## Preparation / 准备

1. Finish intended source changes, then run consolidated checks. Use native Windows tools and one authorized USB physical Android device. No emulator or virtualization environment.
2. Ensure no second automation, instrumentation or manual user controls the same phone. If focus moves to another app, stop input/recording and retain the interrupted evidence. Obtain permission before stopping another project's work; do not repeatedly take over its app.
3. Export important progress through the App save page/system picker. Do not clear data, uninstall, reset the phone or modify the standalone game installation. Overlay installation can stop a game: exit normally and confirm no live `:game` process before installation.
4. Start the HTTPS API and worker; check USB authorization, `adb reverse tcp:8443 tcp:8443`, matching CA and invitation. Keep keys/tokens and raw diagnostics private.
5. Record source commit, tree state, APK SHA/version/signer, registry/library digests, releases, profile/module/driver identities, OS/GPU/RAM/page size, graphics settings and timestamps. A production candidate needs identities generated from clean committed source.

先完成整批代码，再统一构建验证。确保手机没有其他控制任务；重要进度先通过 App 导出。安装前正常结束游戏，不清数据、不卸载、不重置。记录当前构建、内容、运行组件、驱动、设备与设置，凭据不写入公开记录。

```powershell
./devtools/hall.ps1 verify
./devtools/hall.ps1 install
services/.venv/Scripts/python.exe devtools/hall_smoke.py all
```

`all` removes/re-downloads only the original 2048 resources and checks that saved bytes survive. It does not execute the BotW/G0 steps below. Run installation only after the preparation above.

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
