# Main-process recovery batch / 大厅进程恢复修正

This complete source batch addresses `USB-RECOVER-01`, observed on the preceding `5808f5…6d06` APK. The new Android source is `5c1bff0`, APK `3bd6c8…3451d`. Consolidated host checks and all 22 USB instrumentation cases passed. Actual gameplay and a natural low-memory recovery/reload were observed; **controlled live reattachment and full G0 remain unaccepted**. [ADR 0010](../engineering/decisions/0010-stopped-session-reconciliation.md) constrains implementation. The [preceding record](native-build-round-2026-11-07.md) retains its five ordinary switching rounds and original failures.

中文：本批补齐无主会话清理与未知状态保护，已完成构建、26 项 JVM 和 22 项实机测试。实拍补录中大厅自然被系统回收，游戏正常退出后直接从重建大厅再次启动并读取新存档；无需人工重启大厅。受控终止脚本未成功执行，不能把自然恢复算成受控存活重连通过。

[Machine evidence / 机器记录](verification/2026-11-07-session-recovery-round.json) · [Current status / 当前状态](implementation-status.md) · [Reviewed captures / 实拍素材](../media/README.md)

## Actual execution / 实际执行

| Check | Recorded result |
|---|---|
| Identity | Android `5c1bff02b717ecf3af3cd46ad407c38a7e825f75`; native `d427f08356f528d0b4a28910309489ef6cf4d2ab`; all 11 native libraries and registry unchanged |
| App / test package | SHA-256 `3bd6c8a7e5a06bc1679df78792f97827455f8c915e5be4c31728ca15f6a3451d` / `27fa3458699b4b9cb410a845a37250447b1730162e6200527cf2f664892a43b9` |
| Build / package guard | 5m 1s, 90 tasks: 29 executed / 61 UP-TO-DATE; two packaged slots verified |
| JVM / lint | 26 cases freshly executed and passed; lint 0 errors / 16 warnings |
| Contracts | 8 groups passed; 13 schemas / 20 positive examples; separate from gameplay acceptance |
| Physical instrumentation | First attempt 22/22 in 22.798s; nine new actual Room/kernel-lock recovery cases |
| Player preservation | All 22 existing player files identical after overlay and tests; no data clearing or reinstall reset |
| Original 2048 | Eight actual inputs; normal exit; game PID 19334 → 19719; committed/reloaded SHA-256 `5616dc…61f`; all 16 rendered tiles matched |
| First BotW scenario | Actual scene, new manual save, Back pause, cancelled exit in the same process, normal portrait return, fresh-process reload and matching three committed files. Initial touch only revealed overlay; displacement was not established |
| Movement retake | Visually reviewed real touch movement before the door; actual new manual slot 1. The recording is the source of the public 9-second GIF/MP4 |
| Natural main loss | Android records hall 18943 `LOW_MEMORY` while game 25344 survives; game later `EXIT_SELF`; hall reconstructs as 26934. Original script interrupted because it required unchanged main PID; failure retained |
| Direct follow-up reload | Without restarting hall 26934 or forced exit, game 27431 starts and loads the new manual scene; all three committed slot hashes match, A's `e1247e…e38` progress remains unchanged; normal portrait return |
| Controlled termination | Three guarded harness attempts did not successfully terminate main; live Binder reattachment is not passed |
| Post-game state | Room 2, no recovery warning, 22 player files with no removals; 14 ended / 1 conservatively crashed among 15 recorded recent rows; transfer directory 4 KiB |
| Historical checks | Prior five switching rounds and audio-signal captures stay pinned to `5808f5…6d06`; neither was rerun on this candidate |

The final BotW manual-slot hashes are `8ebf5e8c62a121d954f68a329e198c1d164a99205ee9436cef7aa98c9e93ccf4` (game data), `7a5c4b990b6a39307b33af61d82393f0899ac91c395a0655da5f20e7753fca5b` (caption data), and `b1e783c43474e2914ee7d5e9b0af32a5fd0d2e295883bb355cef1c6dfa7d0721` (caption image). Raw progress remains private; the machine record binds retained diagnostics by size/hash.

Eight new public artifacts and six provenance records are exported from this exact candidate. The media directory now has 32 PNGs, 10 GIFs, 10 MP4s and 40 provenance records. Historical media retains original identities; desktop captures are distinguished from USB captures. Silent clips do not verify sound.

## Remaining device gates / 未完成门槛

The natural low-memory exit/reload establishes one bounded automatic stopped-session path. It does not prove controlled authenticated reconnect to a still-running game, every timing/failure boundary, five repeated new-candidate switching rounds or full G0. The formal public source/tool release is documented separately in [v0.4.0 notes](../releases/v0.4.0.md); no production service or release-signed public hall APK is asserted.

## Complete implementation scope / 整批范围

| Area | Required behavior |
|---|---|
| Shared reconciliation | Actual exclusive kernel lease held throughout the Room transaction; operation mutex required |
| Orphaned rows | Conservatively close unfinished sessions and delete matching contexts atomically; leave existing terminal history untouched |
| Player data | Do not change saves, save indexes, event/save counters or generation facts |
| Live ownership | Retain authenticated same-session/generation/nonce reconnect, no second native instance |
| Unknown ownership | Fail closed, keep protection, explain retry/explicit exit; start nothing |
| Invalid recovery context | Malformed JSON, stale IDs/generations and invalid nonce remain guarded rather than throwing or attaching |
| Controller/UI | Serialize recovery/launch, recheck after mutex wait, wake queued work only after actual orphan closure |
| Compatibility | ABI 1 / Room 2 / unchanged IPC and save formats; reuse exact d427f08 native bytes and existing v4/BotW-v1 content |

## Retained verification plan / 保留的验证计划

1. Preserve the preceding app/test APKs, full native-binding record, player snapshot and all failed attempts. Commit this complete source/test/doc batch before the new build.
2. Build debug/test APKs, JVM cases and lint together, then inspect actual packaged slots. Record Android source separately from the reused native source/digests; compare every packaged native library against the preserved APK.
3. Overlay without clearing data and execute all 22 Android cases, including nine new actual Room/kernel-lock cases. Keep the first attempt even if a later same-APK rerun succeeds; do not infer startup latency acceptance.
4. Run A with real input and committed progress. Terminate only the verified NsA main PID. Recreate the hall while A lives, authenticate reconnect, retain the same game PID/board and start no second native instance. Exit normally and verify stored session/data.
5. Start A again, save changed progress, terminate only main and then exit the game. The reconstructed hall must clean the abandoned row and start B without a hall restart or manual force-exit. Verify B's loaded scene and A's unchanged progress.
6. Complete actual B input/new manual save, direct Back/cancel/normal exit and fresh-process reload. Review actual frames, save bytes and game/main identities.
7. Repeat five A → B → A rounds, normal exits, independent progress, slot mappings and post-round resources on the exact new APK. Preserve any interruption and its cause.
8. Export reviewed current captures with exact provenance; update bilingual README, machine evidence, storage and current/historical status; validate all links/media and push completed results to `origin/main`.

The nine isolated cases protect disposable test-cache roots; they never reset a player's game. Main-only termination is not a package-wide force-stop. Reusing native bytes does not transfer a whole-APK acceptance result: the new Android candidate still needs the physical coordination checks above.

## Evidence boundaries / 证据边界

Keep new private diagnostics under `.gamedump/Hall/device-evidence/20261107-session-recovery/` and computer reports under `output/hall/session-recovery-round-20261107/`. The preceding APKs are preserved under `.gamedump/Hall/build-evidence/20261107-session-recovery/before/`. No game binaries, keys, private APKs or saves are uploaded. Native Windows and USB only; no browser is required.

Full G0, representative continuous 30-minute performance/game speed/temperature, subjective/BotW sound, initial cold-start latency, physical power-loss/ENOSPC, remaining requirement/device coverage and production deployment/signing remain separate gates until their specified evidence exists.
