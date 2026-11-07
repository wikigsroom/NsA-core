# USB runtime corrections / 真机运行修正

Follow-up to the actual [transfer-round USB observations](transfer-round-2026-11-07.md), source `720c785`. Its successful save/reload, zero-signal capture and Back failure remain separate historical evidence. This batch finishes source, verification tooling and the complete plan before any new consolidated build/test.

## Source behavior

`USB-AUDIO-01`: native 2048 registers an AAudio error callback that only records an atomic error. Its game control thread owns opening, starting, stopping and closing the stream, recreates a disconnected output and backs off two seconds after failed opening/starting. The data callback owns its tone countdown and atomically consumes new tone requests, preventing an old callback from erasing a new move. Pause produces silence; the persisted audio setting still disables output. See the [AAudio disconnected-stream guidance](https://developer.android.com/ndk/guides/audio/aaudio/aaudio#disconnected_audio_stream).

`USB-BACK-01`: a unified-hall native frontend returns Android Back as unhandled before its native text/menu handlers. Android can then deliver it to the existing GameActivity pause/confirmation path. The native touch/controller menu retains gamepad controls. Standalone native builds retain their own Back behavior. API 33+ also retains the registered platform callback; actual newer-device coverage is separate.

中文：2048 断线回调只记录错误，游戏控制线程负责关闭和重开声音流，失败后退避；音效倒计时不再覆盖新操作。统一大厅构建将系统返回键交给现有暂停和退出确认，避免被原生菜单截获。上述修正不改变存档格式、ABI、Room 版本或产品验收编号。

## Immutable inputs and compatibility

Commit the complete batch before generating native identities. Rebuild both changed native inputs and collect their real digests into the APK registry. A changed 2048 source changes its profile/module identity and needs a new immutable `nsa-2048-local-v3` content release with the same `nsa-2048-save-v1` compatibility group. Preserve published v1/v2 records and existing progress.

BotW frontend/core changes require a new compiled core identity and APK, but its unmodified game profile, module IDs, shader chain and data remain compatible with the existing immutable `botw-local-v1` manifest. The manifest does not pin the host core revision; the local registry and runtime ABI bind the new compiled core. Verify those fields rather than redownloading or mutating the 14.30 GiB archive.

## Consolidated execution plan

1. Preserve the preceding APK, bindings and post-game player backup; confirm no running game before overlay installation.
2. Generate identities from the committed source, rebuild affected native hosts, collect both slots and run the consolidated Android build, JVM tests, lint, APK identity checks and baseline checks. Do not test one source edit at a time.
3. Publish 2048 v3 through the existing publisher and real worker; verify old immutable release records and BotW content remain unchanged. Overlay the APK and retain Room v2/player data.
4. Execute existing 13 isolated Android tests. Use the real hall to install 2048 v3 with its pre-upgrade save backup; prove input, normal direct-Back exit, new process and unchanged reloaded board.
5. While native 2048 is foreground, run `services/.venv/Scripts/python.exe devtools/hall_audio_check.py --label runtime-audio-20261107`. It captures two real output-route transitions, sends actual moves, compares saves and measures decoded PCM. Inspect route/reopen logs and each recording. A nonzero signal is not subjective listening acceptance.
6. Launch BotW, load the newly saved progress, exercise touch movement and direct Android Back, cancel/resume and normal confirmed exit. Check that the old game PID disappears and portrait returns. Repeat actual cross-game/save isolation on the exact candidate; keep any failure.
7. Record exact source/APK/native digests, actual results, limitations and reviewed new media. Five-round switching, 30-minute representative play/performance, subjective audio and remaining requirement/device coverage stay open until executed.

No game data, private recordings, keys or APKs are published. No other application's processes or global audio settings are changed by this plan. All tooling is native Windows and USB; no virtualized environment or browser tabs are needed.

## Executed candidate / 实际候选

Source `f1c68f72e0b0e4d3820fd3b0a1466756cadc8605`, clean before identity generation. APK SHA-256 `d8c51a63f1c6134ec53237209cfc9253ffa543bc0206bfc6dfb029a1784c79af`, **82,194,663 bytes**. Capture-only follow-up `b872778` changes the measurement tool, not that APK or native inputs. [Machine-readable record](verification/2026-11-07-runtime-round.json).

| Boundary / 边界 | Actual result / 实际结果 | Limit / 限制 |
|---|---|---|
| Native and APK build | 145 native sources, zero failures; bootstrap and two slots rebuilt/collected; Android build succeeded; two packaged identities/digests verified | Native Windows; no virtualized environment |
| JVM and lint | Existing 26-case JVM result UP-TO-DATE; lint 0 errors / 16 warnings | JVM cases were not freshly rerun for this native-only batch |
| Baseline | 8/8 documentation/contract groups passed | Contract conformance is not game acceptance |
| Immutable publication | New signed `nsa-2048-local-v3`; 26 preceding immutable records unchanged; BotW profile/modules/shader binding unchanged | Existing 14.30 GiB BotW archive is reused, not relabeled or downloaded again |
| Overlay and device tests | 13/13 actually rerun in 21.978 seconds; Room v2 retained; all 20 preceding player files identical afterward | Snapshot taken before subsequent actual game input |
| Recovery/temporary data | No install-recovery warning; transfer directory 4 KiB after instrumentation | Final post-game snapshot pending normal exit when phone becomes available |
| 2048 upgrade and play | Real 191-byte download / 41-byte expanded fixture, prior save preserved; eight directional inputs; direct Back pause, confirmed exit and portrait return; fresh game process reload | Short original open-source fixture; not Switch compatibility or sustained performance |
| Audio recovery | Four stream-reopen logs, five starts; two actual recordings contain the expected game tone | Original measurement threshold failed; frequency confirmation is separate; no subjective listening pass |
| BotW on this APK | Not yet launched for the new candidate | Wait for the other authorized phone test to finish; preceding APK results do not count here |

本轮已构建并覆盖安装，13 项真机自动检查实际重跑通过。2048 升级保留原进度，完成真实下载、输入、直接返回退出及新进程重读。旧版 BotW 新写档和加载结果仍留在下载轮次；新版 BotW、五轮切换和完整性能尚未验收。

## Save and process evidence / 存档与进程证据

The completed 2048 sequence used hall PID `23955`, game PID `25109` and fresh reload PID `25486`. Its committed/reloaded state SHA-256 is `0eb99efa6f6fbf385da8009b5e2d166af55b25ada352bf6656a177798881c57d`; all 16 rendered tiles match the persisted board. Later audio operations legitimately advanced that progress, so this is a sequence checkpoint, not a claim about the latest save. The upgrade itself preserved checkpoint `ef01e264086705c0018b6f383b0cbfa8f7cc9e9121e6d0cbdc8a0e1df5a2ff1e` before play.

2048 v3 profile/module identity: `721d3bd3427b9cd40ddc9fd0af7d654c046bb257d3b92d558b298d38897420ff`, library SHA-256 `81796fa6c926881431c5e354f619320526c714edad230356f6013e28bc145952`. BotW library SHA-256 is `266fb2343098529988918bcbb315b18c75f695852e1b8625c939ee0bf730204b`; the unchanged profile is `c329645b44fa82a9d72dfb21a7c422d74c9cbf40cd6fca4a33e3d043045f17fa`. Full registry identities are in the machine record.

进程编号仅表示当次运行；每次启动应有独立游戏进程。公开记录保存摘要与观察结果，原始存档及私人素材继续留在忽略目录。

## Audio observation and measurement correction / 声音观察与测量修正

The first capture-tool run retained nonzero output but failed its fixed `peak > 100` criterion: actual peak was 75 at the unchanged device volume. Keep its original `signal_check_passed: false` record. Both captures had five actual changed/saved moves under game PID `25771`:

| Capture / 录音 | Decoded seconds | Nonzero samples | Peak / dBFS | 440 Hz fit |
|---|---:|---:|---:|---:|
| 1 | 19.3716875 | 7,024 | 75 / −52.8078 | 0.9998213 |
| 2 | 15.9535 | 17,426 | 75 / −52.8078 | 0.9998282 |

The expected 440 Hz tone is confirmed by independent offline fitting of the retained PCM, including off-frequency, silence and seeded-noise controls. This is a separate analytical result, not a rewritten pass for the failed criterion. Reopen/start logs also show the new runtime recovering route changes. Recordings include startup/route time and variable cadence; actual decoded durations above replace the nominal 16-second request.

The corrected tool requires an above-quantization signal (peak at least 8), over 1,000 nonzero samples, at least two actual saved moves and at least 80% energy explained by a phase-independent 440 Hz fit in its loudest 1024-sample window. It records dBFS. Its attempted new capture stopped at the foreground guard **before sending input** because another project's app was active. The new criterion has offline controls; a new actual capture run is still pending.

中文：原固定振幅阈值判失败的记录保留，不能改写为通过。已有录音的频率分析可确认 2048 的实际音效信号，日志确认声音流重开；这不等于主观音量、音质、BotW 音频或完整可玩性已验收。未改变手机的全局音量设置，也没有向其他应用发送按键。

## Waiting boundary and remaining work / 等待边界与后续工作

The user explicitly selected preserving the other project's phone test and resuming NsA after it finishes. Do not take foreground or stop that project. Documentation, media review and computer-only validation can continue while waiting. Once it finishes, confirm actual device ownership, finish the corrected audio capture, then execute BotW movement/new save/direct Back/cancel/resume/confirmed exit/fresh-process reload on this exact APK, followed by five A → B → A rounds with independent progress and resource samples.

Representative continuous 30-minute gameplay/performance, subjective audio, physical power-loss/ENOSPC boundaries, remaining device/requirement coverage and production gates remain open. An older short BotW observation around 7–13 FPS is not a current accepted performance measurement.

## Reviewed media and retention / 实拍素材与保留材料

The [media index](../media/README.md) adds three current screenshots and two GIF/MP4 pairs: portrait library, completed v3 history, fresh-process reloaded board, real download/preparation and 2048 input/pause/return. They contain no BotW frames or audio track. Each neighboring JSON records exact APK, original source digest, actual duration and conversion. Historical media retain their original identities.

The delivery check passes for 34 Markdown documents, 720 local links, all 37 current/historical media artifacts and 29 provenance records, with full clip decoding and current APK identity matching. / 文档、链接、媒体摘要及完整解码均通过；这些交付检查不替代游戏验收。

Private raw evidence is under `.gamedump/Hall/device-evidence/20261107-runtime/` and `runtime-audio-20261107/`; build logs, preceding APK/bindings, the catalogue backup and analytical results are under `output/hall/runtime-round-20261107/`. Original game inputs, packaged content, service objects and toolchains remain for continued testing; see [storage and purposes](storage.md). No game binaries, keys, player saves or private APKs are committed.
