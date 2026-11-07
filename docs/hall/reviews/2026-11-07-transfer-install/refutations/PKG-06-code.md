> Archived independent_code_refutation; reviewed source `189169fded9cd6557b3cb146b56d8b2e5243d705`. Original candidate/pending labels are historical; read the [final dispositions](../../../transfer-install-review-2026-11-07.md).
> 归档核对证据；原始候选标签保留，最终结论与限定见汇总。

## PKG-06 — 独立代码反证

**Review type:** independent code refutation. **Pinned source:** `189169fded9cd6557b3cb146b56d8b2e5243d705`. The reviewer authored DL-07, not PKG-06. Inputs: `../requirements/PKG-06.md` and `../requirements/PKG-06.json` in this output directory. This review does not edit those author reports.

**Result:** F01 survives as a conditional power-loss durability gap; F02 survives with a stronger process-interruption reproducer during same-release repair; F03 survives with ordinary process termination sufficient. No source path was found that automatically cleans recovered completed transfer roots. None of the scenarios was executed.

All code paths below are repository-relative. No tests, builds, network, browser, phone/ADB, Docker, WSL, VM or emulator were used. Only this independent report and its JSON are written. Existing phone work was not accessed.

---

### F01 — Missing nested/ancestor directory barriers

**Disposition:** survives, narrowed. **Severity:** Medium / P2 for the specified durability gap; observed device data loss unverified. **Confidence:** high for absent calls, conditional for persistence outcome.

The strongest attempted refutations were that file synchronization, READY's atomic helper, publication-parent synchronization, or the save-backup callee might supply the missing directory barriers.

- `android/app/src/main/java/org/nsa/hall/transfer/Installation.kt:L16-L23` writes/syncs a JSON temporary, renames it, and syncs only its containing directory. It does not recursively sync any descendants or the containing directory's own newly created parent link.
- Every extracted file is synchronized at `L55-L60`, but the extractor creates arbitrary nested parents at `L53`. The only candidate-directory syncs before VERIFIED are READY's helper and explicit staging-root sync at `L71-L73`. No bottom-up walk exists in this installer.
- `security/Trust.kt:L61-L65` is canonical path validation only. Neither `safeChild` nor `sha256` (`L14`) performs directory synchronization.
- Publication syncs the final directory's parent at `Installation.kt:L90-L95`. Repository root creation is `data/HallRepository.kt:L42-L43`; the versions/game ancestors are created through staging/parent `mkdirs` at `Installation.kt:L35,L83`. No parent-link sync chain is present.
- The save backup `transfer/SaveManager.kt:L93-L102` synchronizes its own backup file/directory. It does not synchronize the content candidate tree. The helper is called with the operation lock already held; `backupUnderOperationLock` does not reacquire that non-reentrant mutex. This corrects the author report's potentially misleading phrase “callee takes the same lock.”
- The save-import implementation independently has an explicit bottom-up directory walk at `SaveManager.kt:L167`; it is not called for installation and does not refute this absence.

**Mandatory source contract:** `docs/engineering/04-transfer-install-save.md:L59-L64` requires a durable verified candidate and reports failures for files/parent directories. The absence is not merely lack of a test.

**Limit:** an ordinary application-process kill does not by itself discard the filesystem's already completed kernel writes. The proposed missing-name outcome requires power loss/storage failure plus filesystem behavior that does not supply stronger implicit guarantees. No Android filesystem power-loss trace was inspected or produced. Do not say “a normal crash certainly loses nested files,” “all fsync is absent,” or “a phone reproduced corruption.”

**Retained finding:** the implementation does not issue the required barriers before durable VERIFIED/activation; the exact loss outcome remains filesystem-dependent. F01 is independent of F02's recovery response to an invalid candidate.

---

### F02 — Invalid journal candidate repeatedly blocks initialization

**Disposition:** survives; original trigger must be qualified, and an additional crash path is established from source. **Severity:** High / P1, gated by a damaged existing target/invalid published candidate. **Confidence:** high for persistent global gating.

Attempted refutations:

1. **Storage reconciliation could downgrade the damaged candidate and retain usable games.** It does for registered installs when reached (`transfer/StorageReconciliation.kt:L40-L42`). But `data/HallRepository.kt:L80-L88` calls `Installation.recover` first; exceptions prevent the reconciler and `ready=true`.
2. **A Room transaction could roll back the failing candidate.** `Installation.activate:L103` validates before the transaction at `L104-L108`. It throws rather than recording a recoverable error. The existing active pointer is protected from partial SQL writes, but that does not release the global UI gate.
3. **Next restart/retry could clear the invalid journal.** `recover:L124-L130` deletes the journal only after marker checks and activation. Both checks can throw before deletion; `initialize` leaves its in-memory initialized/ready flags unset (`HallRepository.kt:L86-L88`). The UI's only offered operation in that state is retry (`ui/HallApp.kt:L169-L175`). Launch independently requires ready (`HallRepository.kt:L234`; `MainActivity.kt:L57-L59`).
4. **The runtime lease could intentionally skip mutation.** This is a real branch (`HallRepository.kt:L80`) but is not a repair path. The reproducer starts without a running game; the worker also checks idleness (`TransferWorker.kt:L148-L150`).
5. **Ordinary new-release process death makes an otherwise complete candidate invalid.** This stronger claim is refuted. If a valid v2 is durably published, no extra content fault occurs, and no power loss occurs, the normal recovery branch can activate it. The author's missing-file exercise has an explicit additional corruption/deletion fault; F01 is power-loss-conditional.

**Stronger source reproducer: same-release repair with only process interruption after staging verification.**

1. Have a damaged installed release R with its original READY/index still present, a missing declared file, and another valid game U. Missing critical files are an explicit acceptance precondition (`docs/product/data/acceptance.json:L284`).
2. Enter R's repair page. `HallApp.kt:L344` calls `Installation.repair:L133-L145`, which can mark the install repair_required. `HallApp.kt:L347` exposes “重新下载资源” and opens the download confirmation directly, including for the current same release.
3. Wait for the refreshed detail and confirm R. `HallRepository.enqueue:L144-L157` permits this when no unfinished transfer owns the game; it does not forbid reinstalling the active release. The worker obtains and verifies the signed archive and creates a completely verified sibling `.R.staging`.
4. Terminate the application process after `Installation.kt:L73` has persisted phase VERIFIED, and before the old damaged `target` is moved aside at `L90`. The complete staging tree and the old damaged target both exist. No post-crash file deletion or simulated power loss is needed.
5. On restart `recover:L122` treats VERIFIED + target.exists as a published candidate. It checks the old target's READY at `L124` and calls `activate` on the old target, not staging. Its missing file is rejected at `StorageReconciliation.kt:L33` / `Installation.kt:L103`.
6. The throw leaves the VERIFIED journal and the new verified staging tree present, `ready=false`, and U behind the global gate. Repeating retry chooses the same old target again. No same-candidate swap, per-journal quarantine, rollback or repair-status path is reached.

This path adds a legitimate damaged-install precondition, not an arbitrary new process writer. It strengthens source reachability without claiming a clean normal update always fails.

**Scope correction:** “permanently” means persistent until data/state changes or recovery logic is fixed; an external intervention can remove the fault. The source establishes an unresolvable product retry loop in that state, not irreversible physical data destruction. Missing previous-active fields (`Installation.kt:L32`) are supportive design incompleteness, not the sole proof: the thrown check and retained journal establish this candidate directly.

---

### F03 — Successful recovery drops the cleanup obligation

**Disposition:** survives unchanged, with a cleanup-boundary clarification. **Severity:** Medium / P2. **Confidence:** high. Ordinary process death suffices.

The normal path marks DB_ACTIVATED and CLEANED, deletes the journal, then returns (`Installation.kt:L97-L100`). Only the calling worker subsequently marks the transfer completed and deletes its task root (`TransferWorker.kt:L152-L153`). The deletion return value is ignored.

The recovery path directly activates, marks the transfer completed and deletes the journal (`Installation.kt:L125-L130`); it does not call `perform` or remove `transfers/<id>`. The full archive and both index files are still owned by that root.

Attempted compensating paths were checked:

- `TransferWorker.doWork:L33-L35` awaits initialization and then skips completed rows. Re-execution of an interrupted WorkManager item does not refute the leak: initialization first changes the row to completed, and the subsequent candidate snapshot excludes it.
- `StorageReconciliation.reconcile:L44` excludes completed/cancelled rows, so it neither discovers cleanup obligations nor requeues the recovered row.
- `MaintenanceWorker.kt:L14-L20` initializes and trims session logs only.
- `HallApp.kt:L266` clears two cache directories, neither of which is `files/transfers`.
- `HallRepository.deleteResources:L252-L269` removes installed version roots (and optionally saves/backups), not transfer roots.
- Completed cards expose no cancel (`HallApp.kt:L460`). Even a direct `transferAction(id,"cancel")` on a completed row does not delete its root, because `HallRepository.kt:L175` only immediately deletes for the prior states paused/failed/waiting/queued. Thus the author report's mention of explicit cancel as an ordinary deletion route must not be read as a working remedy for a recovered completed row.
- DAO `deleteTransfer` has no production caller; deleting a row would not itself delete its directory (`data/Database.kt:L28`).

**Source reproducer, unexecuted:** let v2 finish download and persist DIRECTORY_PUBLISHED at `Installation.kt:L96`; kill the process before activation/worker cleanup; restart with all candidate files intact. Recovery activates it, completes the row, and deletes its journal. The retained archive/index directory is never revisited by worker/reconciler/maintenance. A second process restart adds no cleanup mechanism. This needs no missing-file injection, power failure or deletion failure.

Actual bytes depend on the game's signed archive size; no invented GiB measurement is asserted. CLEANED is semantically premature under `docs/engineering/04-transfer-install-save.md:L62` even when eventual normal cleanup happens. Replay loses that eventual step.

---

### Complete code coverage and searches

Read the implementation/recovery helpers and their enforcing callers, rather than trusting phase names:

- `HallRepository.kt:L59-L90` → installation recovery → save recovery → storage reconciliation → ready publication; all initialization exits.
- `TransferWorker.kt:L29-L75,L99-L154` → checkpoint/index/chunk/full archive checks → operation lock → `Installation.install` → activation → worker completion/root deletion; error, user-intent and cancellation exits.
- `Installation.kt:L16-L148`: all created/extracted/verified/publication/activation/cleanup branches, target-present identical or replaced branches, recovery target absent/replaced/staging selection, marker failure and Room activation.
- `StorageReconciliation.kt:L14-L74`: every validation failure is converted to a problem string; the activation wrapper then throws. Registered-install downgrade and completed-transfer exclusion were checked.
- `Database.kt:L7-L49`: active pointer and install row representation, Upsert and database construction; no callback supplies filesystem cleanup/sync.
- `Trust.kt:L13-L65`: hash/path helpers have no hidden directory mutation/barrier.
- `SaveManager.kt:L19-L51,L92-L102,L145-L179`: backups and save import operate on separate directories; save tree sync is not an install call.
- `HallApp.kt:L169-L175,L261-L266,L341-L348,L460`, `MainActivity.kt:L31-L59`, `MaintenanceWorker.kt:L12-L20`: user gating, real repair redownload, cleanup controls and scheduling.

Searches and results:

| Search | Result |
| --- | --- |
| `syncDirectory / Os.fsync / fd.sync / mkdirs` across hall Kotlin | All actual barriers enumerated; no nested install-tree traversal. SaveManager has its own walk at L167. |
| `Installation( / .install( / installationProblem / recover(` | One game resource install caller and one initialization recovery caller; other install consumer is AppUpdater. |
| `transferRoot / archive.zip.partial / deleteTransfer(` | Only task worker, cancel actions, reconciliation and path construction. No completed-transfer sweeper. |
| `deleteRecursively(` across hall Kotlin | Install staging/replaced, transfer normal/cancel, explicit resources/cache/saves, save exchange; no hidden recovered-completed transfer cleanup. |
| `putInstall / activeRelease / deleteInstall` | Activation Room transaction and explicit resource deletion checked; a different old release is not automatically deleted. |

**Boundaries:** no automatic early old-version GC was found, so the author's PKG-06.04 retention conclusion stands. UPD health/rollback completeness is separate. No claim that process kill reproduces F01's power loss is accepted. All refutations here are static; AC-P05/CON-06 remain not_run and no public document/program file was edited.
