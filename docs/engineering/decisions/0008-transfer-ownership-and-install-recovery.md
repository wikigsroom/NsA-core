# ADR 0008 · Durable transfer ownership and installation recovery

2026-11-07 · Implements the bounded DL-06, DL-07 and PKG-06 findings from the [pinned review](../../hall/transfer-install-review-2026-11-07.md). Execution evidence belongs to the [correction round](../../hall/transfer-round-2026-11-07.md); source changes do not imply device acceptance.

## Commands, progress and ownership

Room schema 2 adds nullable `ownerGameId` with a unique index and persisted `retryAt`. An active task owns its game through run, pause, failure and requested cancellation. Only acknowledged cleanup or committed installation completion clears ownership. Multiple terminal history rows use NULL. User commands and executor progress use separate conditional SQL updates: a stale executor cannot overwrite pause/cancel, revive a terminal task or consume a cancelled task's retry budget. Explicit retry resets that budget. One automatic budget stops after five failed attempts, with server Retry-After persisted across worker restarts.

Migration 1→2 preserves games, installs, sessions, facts and every transfer row/file. The earliest nonterminal `(created,id)` becomes owner per game. Other legacy duplicates become cancellation requests without ownership; the executor cleans their own workspaces before acknowledging them. Migration never deletes content or saves. Room validates the resulting schema; downgrade is unsupported. Back up the database before deploying a schema-changing APK; do not install an old schema-1 APK over schema 2 and do not clear player data as a migration workaround.

Queue selection re-reads persisted rows after each operation, with ascending `(created,id)`. Paused/failed tasks are skipped; pending cancellations are processed without a network constraint. APPEND_OR_REPLACE creates a durable serial successor when an enqueue races the current worker's last read. WorkManager retries can delay successors; this change does not promise immediate execution under Android scheduling. A kernel file lock `content-transfer.lock` covers the whole runner lifetime, including its final blocking response/file handle, and is shared by maintenance. An old stopped runner retains it until unwinding completes. This complements WorkManager ownership; no wall-clock lease stealing is used.

Downloads shows active rows in this order, the effective runnable position and a reason for skipped rows; terminal history is newest first. Pending cancel displays waiting-for-cleanup and does not expose resume. Completed cleanup failure offers a separate retry that cannot recreate a download.

## Object identity and HTTP

Every object request sets Accept-Encoding: identity; every response rejects non-identity or mixed Content-Encoding. A grant binds release, signed manifest digest, object ID, length, SHA-256 and a strong opaque ETag. A renewed grant must preserve all of them. Only this identity is persisted, never a signed URL or session credential.

206 requires the exact range, total and ETag before writing. 200 cannot be appended. 416 succeeds only when Content-Range names the signed total and the entire existing local object passes exact length and SHA-256; otherwise trusted chunks are retained and the operation fails safely. An expired object's 401/403 may renew once against the same object; other authorization failures remain explicit. 404/410 refresh policy without switching releases. 429/5xx use the shared budget. HTTP Date helps classify URL expiry; it does not grant authority or bypass the service.

## Directory publication and recovery

Private Android journals now use `journal_schema:2`, distinct from the normative `record_schema:1` DTO and Room schema version. Their game/release/transfer IDs and verified manifest identity must match the persisted task before recovery. Paths are derived, never accepted as arbitrary journal paths. Version-2 staging/replaced names include a hash of the transfer ID. Legacy journals without version 2 retain their old derived names during recovery/retry. No eager directory rename or destructive migration runs during upgrade.

The machine contract explicitly lists the Android v2 adapter format. `transfer_id` maps to the operation identity; phase maps unchanged; target/staging/replaced paths are derived from fixed roots. The persisted manifest's `_verified_sha256` maps to manifest identity. This adapter does not claim every normative command revision/receipt field has been implemented; those broader constraints remain open.

Before VERIFIED, extracted files and metadata are synced, nested directories are synced bottom-up, and new ancestor directory entries are synced through filesDir. Publication syncs the target parent after each rename. Sync failure preserves a recovery record and prevents claiming durability. Host fault tests prove control-flow and process-exit recovery; they cannot prove physical power-loss durability on a device filesystem.

Recovery validates complete candidate hashes, exact declared file set and metadata. A pre-existing same-release target is not treated as published merely because it exists. If it is damaged and staging is valid, retain the old target as replaced, publish staging, recheck, then activate in a short Room transaction. Missing/invalid candidates retain the old copy and journal with an actionable warning. One bad journal cannot abort all other recovery or prevent the hall opening. Save compatibility and required backup are rechecked before activation, including recovery.

After DB_ACTIVATED, recovery only completes the task and garbage collection; it never reactivates an older release over a newer active pointer. The journal remains until the archive, staging and safely replaceable copy are removed and directory barriers succeed. Cleanup failure records a warning and can be retried at startup, explicit cleanup or scheduled idle maintenance. Legacy terminal rows without journals also have their transfer archives cleaned. Completed status is installation readiness, not a claim that temporary bytes have already been freed.

Unknown or sole old copies are retained. Cancelling an unreadable journal cleans only the task's known download workspace and retains the damaged record as `.invalid`; its warning remains for diagnosis. It cannot safely identify every old staging directory and does not silently delete them. No resource cleanup traverses symbolic links. Resource deletion/rollback refuses pending owned operations or known install journals. Targeted game-field writes avoid unrelated settings/history updates overwriting activation.

## Validation and limits

Finish the entire source, migration and necessary test batch before consolidated execution. JVM tests cover protocol negatives, live queue reads, independent locks and faults at every publication/cleanup boundary. Android tests use disposable private roots and real SQLite/fsync for schema migration, command/progress fencing, damaged same-release recovery, independent invalid journals, exact candidate verification and legacy archive cleanup. Preserve actual player installations/saves during USB tests.

API34 UIDT, actual device power loss, production deployment, a complete command-revision model and the wider 90-requirement acceptance are not established by this bounded correction. G0 actual two-game process/save/reload, audible output and sustained-play/performance gates retain their own evidence requirements.
