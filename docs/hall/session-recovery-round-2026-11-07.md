# Main-process recovery batch / 大厅进程恢复修正

This complete source batch addresses `USB-RECOVER-01`, observed on the preceding `5808f5…6d06` APK. Source and nine isolated Android cases are prepared; **the new candidate has not yet passed device verification**. [ADR 0010](../engineering/decisions/0010-stopped-session-reconciliation.md) is the implementation constraint. The [preceding build/game record](native-build-round-2026-11-07.md) retains its actual five ordinary switching rounds, Android low-memory exit history and failed recovery attempts.

中文：上一候选已完成五轮常规切换，但大厅被系统回收、游戏随后退出的时间窗口会遗留旧会话。本批补齐无主会话清理与未知状态保护；源码完成不等于新安装包通过实测。

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

## Consolidated execution / 集中执行

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
