# Native build correction and USB results / 构建修正与真机结果

The corrected candidate is installed: committed source `d427f08356f528d0b4a28910309489ef6cf4d2ab`, APK SHA-256 `5808f5a427a2a0ee8654d7fc9f994a486950048a52bf184ea6f5ca50869e6d06`, **82,195,381 bytes**. BotW now reaches actual gameplay, responds to touch movement, writes a new manual save and reloads it in a fresh process. A subsequent Android low-memory reclamation exposed a separate stale-session recovery gap. **Full G0, sustained playability and production acceptance remain open.**

中文：新版已在真机进入《旷野之息》，完成触控移动、新手动存档、正常退出和新进程重读。随后发现大厅被系统低内存回收后，退出游戏留下未结束的会话记录，会阻止下一款游戏启动。原构建标识问题已解决，这条恢复路径仍待修正；不能把“进入游戏”写成完整可玩或正式发布通过。

[Machine record / 机器记录](verification/2026-11-07-native-build-round.json) · [Current status / 当前状态](implementation-status.md) · [ADR 0009](../engineering/decisions/0009-compiled-slot-identity-and-unicode-builds.md) · [Reviewed captures / 实拍素材](../media/README.md)

## Current consolidated results / 当前集中验证结果

All planned source, regression cases, contracts and implementation documentation were completed and committed before consolidated verification. The expected core was not relabeled, runtime checks were not weakened, and unchanged BotW content was reused.

| Check | Actual result |
|---|---|
| Native regression | **20/20 passed**, 18.918 seconds, zero skips; actual NDK compilation under Unicode/spaced paths |
| BotW host | 145 inputs checked: **1 actually compiled**, 144 cached, 0 failed |
| Collection/final APK | Both actual ELF identities, strict metadata and SONAMEs match; old host correctly rejected |
| Android build | Success in **3m 26s**, 9 tasks executed / 81 UP-TO-DATE |
| JVM | Existing 26-case report retained, task UP-TO-DATE; **not freshly run** |
| Lint | 0 errors, 16 warnings |
| Contracts | 8/8 groups, 13 schemas, 20 positive examples; no gameplay inference |
| Content publication | Real API, independent reviewer and worker published 2048 v4; **36 previous immutable records unchanged** |
| Android instrumentation | First attempt **12/13**, navigation initial wait timed out; same-APK rerun **13/13 in 10.31s** |
| Overlay/data | No uninstall or clear-data; Room 2; all **21 preexisting player files** identical after overlay/tests; no recovery warning; transfers 4 KiB |

Both registry slots name source `d427f08356f528d0b4a28910309489ef6cf4d2ab`. 2048's new immutable profile/module is `f4ab9562219beb4c9855c401463fe5eca788ecc6297358f35c8b6d7f4b635d97`; BotW keeps its original content/profile and rebuilt host. Full library/module/driver/shader bindings are in the machine record. Test APK: 2,428,862 bytes, SHA-256 `6ea5b06e8d6d1317b469111bdcf5a1d5791789b9c3326ae168584faf0aa3db66`.

The initial Android navigation timeout remains a failure. The subsequent pass does not establish its precise cause or satisfy the cold-start p95 requirement. File-intensive startup work is a suspected contributor requiring measurement.

## Actual new-candidate game observations / 新候选实际游戏结果

2048 v4 was installed through the real update flow, preserving its prior save and pre-upgrade backup. Eight actual inputs produced checkpoint `a23d63409c45c36a97e5023d4a7515b0f2ce3faa87113140a68ce315af006e2f`. Direct Back opened pause/exit confirmation; normal exit restored portrait. A fresh process reloaded identical bytes and all 16 rendered tiles. Hall PID `21188` survived game PIDs `21488` → `21915` during this sequence.

Two new Android-output captures decoded to 16.029270833 and 15.9535 seconds. Their 440 Hz fits were **0.999902978 / 0.999835612**, peaks 78 / 75 and approximately −52.47 / −52.81 dBFS. Four audio reopens and five output starts were observed. Respectively **five and four moves changed saved state**; one attempted direction did not change the board. This proves the measured tone, not subjective listening or BotW sound. Normal exit afterward preserved checkpoint `9abb3374…47d770`.

BotW launched as PID `23613` from hall `23279` and loaded Shrine of Resurrection. An early fixed wait still showed loading and was not counted as gameplay. The first downward gesture was inconclusive; a later upward left-stick gesture visibly moved Link and changed the scene position. The actual System → Save flow created manual slot 1, changing all three slot files while preserving 2048 progress. The confirmation showed 11:37; the fresh load list showed **11/07/2026 11:38**.

Direct Android Back opened pause; cancelling exit resumed the same process, while confirmed normal exit restored portrait and retained the main process at that point. Fresh BotW PID `29126` mapped only its intended slot and reloaded the new progress. The save list and matching scene were visually reviewed; all three new committed slot hashes matched:

| Slot 1 file | SHA-256 after save and fresh reload |
|---|---|
| `game_data.sav` | `9634af7eebe9f6d047d4557b147d76364720b5530f740d9ba2af1040ec542206` |
| `caption.sav` | `d328d8fbd092fb798d8f64269e8ac8a1649d351e50971f6807a87d62b2da0d77` |
| `caption.jpg` | `f5ece87026d55ad898286455a8809b724a6e2d3455531e28c21efc71a2bb1d0e` |

A short shrine sample reported p50 **79.45 ms**, p95 **94.26 ms**, p99 interval 121.26 ms and 1,673 MiB footprint. The inverse median is about 12.6 FPS, not a measured average, representative outdoor benchmark or 30-minute acceptance. Audio callback counters do not prove audible output.

## Main loss and switching / 大厅回收与连续切换

Android's exit history records hall PID `23279` ending for **LOW_MEMORY at 11:45:22.087 local**, while game PID `29126` survived. Normal game exit restored portrait with reconstructed hall PID `3158`. The database still retained the old BotW session in `first_frame` with its runtime context, despite no remaining game PID. A was therefore rejected with “请先返回并结束正在运行的游戏”; this is **not a successful switching round**.

The first recovery automation opened “结束游戏进程” but omitted its final confirmation. Its unchanged-save check proves only preservation while the confirmation was open, **not completed recovery**. The next attempt stayed blocked. Both failed attempts and a private database snapshot are retained. A fresh hall restart then used existing startup reconciliation; later switching observations remain separate and cannot establish automatic recovery inside the reconstructed process.

After that restart, **all five A → B → A rounds completed** on the same APK with main PID `6724`. All 15 game PIDs were distinct, each process mapped only its selected slot, A input changed its save and all 16 reloaded tiles matched, while B reloaded the new manual progress without changing A. The five B save lists/scenes were visually reviewed. Each exit returned to portrait; the final snapshot contains 15 ended sessions and no unfinished session.

Post-round main PSS samples were 106,740 / 106,832 / 107,446 / 107,123 / 108,195 KiB; file-descriptor counts were 141 / 140 / 141 / 140 / 140. These bounded samples do not establish long-term leak absence. Battery remained 6% at each round's start. Room remained schema 2, transfer allocation 4 KiB, no recovery warning and no preexisting player file removal. Full G0 remains open because the low-memory recovery gap and other normative gates are separate from these successful ordinary switching rounds.

### USB-RECOVER-01: next complete batch / 下一整批恢复修正

Observed state and source agree: initialization may see a still-held game lease and skip unfinished-session cleanup, while the subsequent `SessionController.recover()` returns immediately once that lease is gone. No later reconciliation closes the orphaned row in the same reconstructed process. This is a confirmed lifecycle timing gap.

The next source batch must cover orphaned-session reconciliation under the operation lock with proven kernel ownership absence; corresponding context cleanup without inventing save success; authenticated reattachment while the original game owns the lease; exit/binder-loss/initialization races; duplicate callbacks and stale generations; and safe retry with unchanged player bytes. Preserve fail-closed behavior when ownership cannot be established. Finish all affected source, regression/device cases and documentation before consolidated build/test. A restart or dismissed dialog cannot by itself mark this fixed.

## Current media and boundaries / 当前素材与边界

Three PNGs and two silent GIF/MP4 pairs are exported under new `native-*` names from this exact APK. Historical media keeps its own provenance. Raw BotW frames, player saves, keys and private APKs stay excluded from public exports. Native Windows/USB only; no browser tabs were opened in this round.

Automatic main-loss recovery, the initial cold-start timeout, full G0, representative continuous 30-minute gameplay/performance/game-speed/temperature, subjective sound and BotW audio, physical power-loss/ENOSPC, remaining requirement/device coverage and production gates remain open. See [storage and retention](storage.md) for retained inputs, immutable objects, backups and toolchains.

## Historical preparation and preceding APK observations / 原计划与前一候选历史记录

The following sections retain their original preparation-time wording and preceding APK identities; current results are above. See the [preceding runtime record](runtime-round-2026-11-07.md) and [machine evidence](verification/2026-11-07-runtime-round.json).

## Actual resumed observations / 恢复后的实际结果

After the other project's device testing ended, NsA resumed as authorized. The corrected two-capture 2048 audio measurement passed on installed APK `d8c51a63f1c6134ec53237209cfc9253ffa543bc0206bfc6dfb029a1784c79af`, core `f1c68f7`. The captures decoded to 16.1535 and 16.0290625 seconds; each contained five actual saved moves. Their 440 Hz fits were 0.9998657 and 0.9998053, with peaks 80 and 77, approximately −52.25 and −52.58 dBFS. Four reopen and four start logs were observed. Subjective listening and BotW audio remain unaccepted. The original fixed-threshold failure is retained separately.

Direct Android Back then opened normal pause/exit confirmation and returned to portrait. Hall PID was `23955`; game PID `25771` ended. State checkpoint `7b252020363d9fa986fa1171a9798e34f26a7952126b8509ff29e3f6bb4a9560` remained identical through exit. The subsequent private snapshot retained the v3 pre-upgrade backup, all BotW player files, Room schema 2, no recovery warnings and 4 KiB transfer allocation.

The first BotW attempt on this APK failed **before gameplay**, with `MODULE_MISMATCH`. The generated header/registry expected `f1c68f7`; the compiled library still contained `e6ee168`. Its UTF-8 dependency file was previously decoded with Windows cp936, corrupting the Chinese absolute path. Missing dependency paths were ignored, leaving a stale adapter object. The old “compiled 145 sources” message included reused objects. Byte-hash agreement did not establish agreement with the generated identity. BotW saves remained unchanged after rejection. Five cross-game rounds are blocked by this defect, not by the completed peer test.

## Complete source batch / 整批变更

| Area | Required behavior |
|---|---|
| Dependency cache | Strict UTF-8 Make parsing; missing/invalid dependencies force rebuild; separate actual/cached counts |
| Compiled slot metadata | One shared header writer; same-unit allocated `.nsa_identity` JSON; unchanged runtime ABI |
| Collection and APK checks | Actual ELF/AArch64/metadata/SONAME comparison before copying, repeated after packaging |
| Contracts | Versioned schema, fictional example and cross-object baseline check; ADR 0009 |
| Regression sources | Twenty cases, including real Clang compilation in Unicode/spaced directories and rejection controls |
| Immutable content | New 2048 v4 identity/release; retain all published older records; reuse unchanged BotW content |

## Consolidated execution plan / 集中执行顺序

1. Preserve the rejected APK, registry, generated header, adapter object/dependency file, service records and current player backups. Finish and commit this complete source batch before generating identities from its committed revision.
2. Run the complete native-input regression suite; build both selected native slots; require compiled metadata and actual SONAME agreement. Retain the old artifact's expected rejection.
3. Build the Android APK/test APK, JVM cases, lint and final packaged guard together; validate updated contracts. Distinguish newly executed cases from `UP-TO-DATE` tasks.
4. Publish immutable 2048 v4 through the real worker, verify older records unchanged, then overlay the APK with no live game and without clearing player data. Re-run all 13 Android cases.
5. Install v4 through the real UI; verify pre-upgrade backup, actual input, direct Back pause/confirmed exit, fresh-process reload and two audio captures under a new evidence label.
6. Load BotW, move, write a new manual save, test direct Back/cancel/resume/confirmed exit and reload that progress in a fresh process. Inspect actual frames and save bytes.
7. Complete five A → B → A rounds on the same APK, with independent saves, fresh game PIDs, expected slot mappings, hall process/resource samples and cleanup.
8. Update bilingual status, exact identities, reviewed public screenshots/clips/GIFs and full media validation; synchronize completed results to `origin/main` without force-pushing.

The phone is idle and USB charging during computer work. Record its actual battery/temperature before prolonged tests; the last idle observation was 8% and about 38°C. Waiting time is not a test result.

## Evidence and open gates / 证据与开放门槛

Private audio evidence: `.gamedump/Hall/device-evidence/runtime-audio-frequency-resumed-20261107/observation.json`. Exit and post-rejection snapshots: `.gamedump/Hall/device-evidence/20261107-runtime/`. Preserve the rejected build under `.gamedump/Hall/build-evidence/20261107-native-input/before/` and computer reports under `output/hall/native-build-round-20261107/`. These paths are local diagnostics, not public downloads.

Representative continuous 30-minute gameplay/performance, subjective sound, physical power-loss/ENOSPC, remaining requirement/device coverage and production gates stay open. Original game inputs, packaged resources, immutable service objects, backups and native Windows toolchains remain for continued verification; see [storage and retention](storage.md). No keys, game binaries, player saves or private APKs are published.
