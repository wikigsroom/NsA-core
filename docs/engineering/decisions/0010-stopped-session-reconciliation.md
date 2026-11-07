# ADR 0010: reconcile stopped sessions after main-process loss

Status: implemented; host checks and 22 physical instrumentation cases passed; bounded natural low-memory recovery observed. Controlled live reattachment/full G0 remain open. Date: 2026-11-07.

Actual new-candidate execution and retained failures are in the [session recovery record](../../hall/session-recovery-round-2026-11-07.md). Source regression, natural process reclamation and controlled surviving-game reconnect remain distinct evidence classes.

## Observed timing gap

Candidate `5808f5…6d06` completed five ordinary A → B → A rounds, but an earlier Android `LOW_MEMORY` reclamation exposed `USB-RECOVER-01`. A native game outlived main, then exited normally. Initialization could observe its still-held lease and skip session cleanup; the later controller recovery returned immediately once the lease disappeared. The reconstructed hall retained a `first_frame` row and rejected the next launch despite no remaining game process. A fresh hall restart reconciled it. The original attempts and database/exit-history evidence remain in the [preceding record](../../hall/native-build-round-2026-11-07.md).

## Decisions

1. Share stopped-session reconciliation between repository initialization and controller recovery. Execute under the repository operation mutex, before any new controller launch. Acquire the actual kernel lease, keeping it exclusively owned throughout the Room transaction. A database row or an absent PID is not sufficient proof.
2. If exclusive ownership is obtained, close unfinished rows conservatively as `crashed` and delete their matching recovery contexts atomically. Preserve session/generation/event/save counters, existing terminal rows, native save bytes and save indexes. Do not fabricate `exit_complete` or `save_committed`.
3. A lease held elsewhere preserves every row/context and follows authenticated reconnect. An I/O/ownership failure is a separate unknown state: keep protection enabled, report the uncertainty and start no game. Malformed JSON, stale session/generation or a missing/invalid nonce must not crash recovery or attach to a live owner.
4. Guard concurrent recovery and launch in the Application-owned controller. Recheck local controller state after the operation-lock wait. Controller recovery reports whether it closed abandoned sessions; the owning ViewModel then wakes the transfer queue. Test-only repositories do not enqueue production work as a side effect of calling recovery.
5. Retain explicit confirmed force-exit for unknown/live ownership. It remains a distinct user action and does not claim saved progress. A successful ordinary restart or a confirmation merely being opened cannot establish automatic recovery acceptance.

## Compatibility and build inputs

Runtime ABI stays 1, Room stays 2, and IPC, save formats and immutable content identities do not change. This batch changes Android coordination only. Reuse the exact native libraries compiled from `d427f08`, with their existing compiled metadata, registry and v4/BotW-v1 content. Record Android source and native source separately; recompute the complete APK digest and repeat its packaged guard. No v5 resource release or BotW redownload is required. Do not relabel native libraries as built from the newer Android-only commit.

## Consolidated verification obligations

Finish all source, nine Android regression cases and documentation before building. The isolated cases use actual Room and kernel locks beneath disposable test-cache roots: orphan closure with unchanged progress, held ownership, unknown ownership, idempotency, initialization/live-exit timing, controller unknown-state protection, malformed/stale/missing-nonce contexts, concurrent recovery/launch serialization and database-failure lease release. They do not establish actual Binder reattachment.

Build app/test APKs, JVM cases, lint and packaged identity checks together. Overlay with no live game; preserve current progress. Execute the complete Android suite, retaining failures. On USB, separately prove a live game survives main-only termination and reconnects to the same process; prove game exit during main loss restores the hall and permits a subsequent launch without restarting main; verify actual A/B progress, normal exit/reload, repeated switching and resource/data isolation on the exact new APK. Keep performance, subjective audio, physical faults and production gates separate.

中文：在操作锁内拿到内核排他锁，才能把无主旧会话清成历史；拿不到或无法判断时继续保护。不会修改玩家存档或伪造保存成功。新 Android 源码与复用的原生源码分别记录，全部代码与用例完成后再集中验证。
