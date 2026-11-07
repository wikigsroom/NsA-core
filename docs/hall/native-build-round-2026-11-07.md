# Native build input correction / 原生构建输入修正

Source and verification preparation, 2026-11-07. [ADR 0009](../engineering/decisions/0009-compiled-slot-identity-and-unicode-builds.md) fixes the newly observed `USB-BUILD-01`. This page initially records the complete next batch before verification; it is not a new APK pass report. See the [preceding runtime record](runtime-round-2026-11-07.md) and its [machine evidence](verification/2026-11-07-runtime-round.json).

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
