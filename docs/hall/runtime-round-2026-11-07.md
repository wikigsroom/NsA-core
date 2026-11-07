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

## Execution status

Source batch and verification tool prepared; **not yet built or tested**. The preceding candidate's results do not transfer to the next APK.
