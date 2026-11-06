> Archived review record; source snapshot: `cdedc7179ec1842592243a052525e021d8cf70f1`. Initial candidate/pending labels are historical. Final dispositions, including the superseding APP-05-D03 clarification, are in the [consolidated review](../../../source-review.md).
> 归档核对记录；初核中的候选/待复核状态保留历史，最终结论以汇总及独立复核的追加判定为准。

## APP-08 — independent code refutation

Base: cdedc7179ec1842592243a052525e021d8cf70f1. Reviewer did not author APP-08. Read the requirement Markdown/JSON, then independently re-read production entrypoints, recovery, workers, DAO streams, IPC, runtime/native callbacks and relevant callers. No product edits, test/build execution, device access, browser or virtualization. This is code refutation; specification refutation remains separate.

| Finding | Outcome | Confidence | Retained scope |
| --- | --- | --- | --- |
| APP-08-F01 | survives | high | Process restoration can show restored query text with a new unfiltered catalogue; same-process Activity recreation is protected. |
| APP-08-F02 | survives | high | Persisted file-mismatched tasks/install metadata can be rendered without startup reconciliation; missing READY is blocked at launch. |
| APP-08-F03 | survives | high, conditional trigger | If :game survives main-process death, startup releases its DB pin without proving death; duplicate native start remains blocked. |
| APP-08-F04 | survives | high | First UI initialization can delete a live worker's staging; the stronger incomplete-READY outcome is source-supported for the exact interleaving below. |

---

**APP-08-F01 — survives.**

Attempts to refute:
- Application-owned repository/controller and ViewModel retention protect ordinary Activity recreation. HallApplication.kt:L12-L15 and MainActivity.kt:L66-L71 are real counterevidence to a broad “every rotation resets search” claim. F01 correctly requires a newly created process/ViewModel, not routine recreation.
- HallApp.kt:L85-L92 saves route/query/genre/filter. This restores the text, not the search response. L121-L124's restore effect handles only detail/game-update.
- visibleCatalogue at HallApp.kt:L105-L111 filters by compatibility/installed status only; it does not apply name/genre query. SearchField at L360 invokes onChange without submitting; there is no hidden search on text restoration. Search route L191-L198 renders that shared catalogue.
- Repository catalogueRevision at HallRepository.kt:L64/L76 rejects older requests, but the empty default refresh on a fresh process is the current request; it does not recover the submitted context.

Verified chain: new HallViewModel init (MainActivity.kt:L38) -> initialize -> refresh() with blank defaults; HallRepository.kt:L68 omits q/genre, L77 replaces shared results. L80 persists only blank-query/genre catalogue. A restored route=search/query=Q has no replay until the user explicitly submits. With two visible games where only one matches Q, the rendered IDs can belong to the unfiltered response. A changed unsubmitted draft is stored in the same query variable, so committed and draft contexts are also not recoverable separately.

Safe minimum fix: preserve submitted query/filter context separately from draft text and bind the result snapshot to that context. Restore it before choosing the initial refresh; prevent a generic startup refresh from overwriting the restored search projection. Do not remove ordinary saved-state machinery.

Verification need after the implementation batch: process/task restoration with a committed query plus different draft; inspect actual request parameters and displayed IDs. No execution occurred here.

---

**APP-08-F02 — survives, with existing protections explicitly retained.**

Attempts to refute:
- TransferWorker.kt:L114-L138 checks existing chunks on resume, and L131-L133 hashes/fsyncs before committing progress. This prevents a broad claim that normal checkpointing knowingly commits unverified bytes.
- The same worker calls checkpoint before reaching that loop (L98), and checkpoint refuses missing validated network/paused intent (L55-L70). Offline workers are also constrained by HallRepository.kt:L126-L128; failed rows are excluded by TransferWorker.kt:L33, and pause intent stops checkpoint. Therefore the worker's later hash loop cannot repair the first offline/paused/failed UI projection.
- Installation.kt:L98-L103 atomically pairs install and activeRelease, and recover:L117-L122 can reconcile late journal phases. But recover:L106 enumerates existing journal files only. Successfully completed installation removes the journal at L95-L96; there is no sweep of all active installs.
- HallRepository.kt:L165 prevents a missing READY from reaching native launch. Thus “an absent package necessarily launches” is disproved. That stronger claim is not necessary for F02 and is not retained.
- Explicit repair exists at Installation.kt:L128-L133 and HallApp.kt:L316, but is user-triggered, does not publish an initial validated projection, and does not update install health/state.

Verified chain: MainActivity.kt:L32-L35 subscribes directly to DAO rows; setContent at L69 does not await initialization. DataStore welcomed state may arrive independently. HallApp.kt:L147-L148 gates only on welcomed, while L145 shows a spinner without withholding content. TransferCard L402-L405 displays the stored bytes/state and library/continue uses activeRelease (L164/L212/L364). HallRepository.initialize:L48-L60 performs no per-transfer archive length/checkpoint sweep and no no-journal active-install sweep.

Retained consequence: given a missing/truncated partial file or missing active package/READY after a journal has been cleaned, the UI can present historical progress/installed state as current. These are explicit recovery/fault inputs; this audit does not invent a normal power-loss mechanism that defeats fsync. Full archive/resource hashing at every startup is not required by this code finding.

Safe minimum fix: bounded recovery of each persisted task's durable file length/checkpoint and each active installation's READY/index/identity/critical-file metadata, preserving pause intent. Publish reconciled content snapshots only after completion; show a recovery state beforehand. Do not delete saves or silently reset failed tasks, and do not replace full repair hashing with fabricated “healthy”.

Verification need after the complete batch: first published snapshot for offline paused/failed tasks with missing/truncated partials and no-journal active installs with missing markers/critical files. No execution occurred here.

---

**APP-08-F03 — survives with the exact surviving-process precondition.**

Attempts to refute:
- The processes are explicitly distinct: AndroidManifest.xml:L12 and L15-L20 place only GameActivity/GameProcessService in :game. Application-owned controller retention helps Activity recreation within one main process, not a new Application instance.
- SessionController.kt:L104-L107 watches the runtime Binder from the living main process. It does not let a newly reconstructed host rediscover/re-authenticate the old runtime, and attach:L122-L128 only updates the Activity reference. Controller state is reset at L24-L36.
- If the last host binding disappears, GameProcessService.onDestroy:L41 only removes callbacks. It does not finish GameActivity/native or kill the process. GameProcess.send:L15-L16 ignores a dead host Messenger failure. There is no game-side host linkToDeath/reattach protocol.
- GameActivity.onDestroy:L116-L124 and frontend on_destroy at nxnative/dm/android/front_android.c:L144-L148 really kill their own process, but require Activity destruction. They do not establish that every main-process-only death necessarily destroys a surviving GameActivity.
- Native hall_adapter.c:L87-L94 observes only its local host cancellation callback and save counter. bootstrap.c:L63 returns its local atomic cancelled flag, set by local native lifecycle/commands at L86/L90/L93. It is not main-process death detection.
- GameProcessService.kt:L25 refuses another prepare for existing context; GameActivity.kt:L43-L48 and hall_adapter.c:L105 prevent duplicate native execution. A second simultaneous native game is therefore not established and must not be claimed.

Verified chain: HallRepository.initialize:L58 sets every unfinished row to crashed, with a “previous game process ended” message, without probing live processes/Binder. Database.kt:L35 excludes crashed from unfinished. L59 schedules work. TransferWorker.kt:L67/L147 and HallRepository.kt:L170/L181/L196/L201 gate resource/save operations only on unfinished rows; SaveManager.kt:L20 does likewise. A new host's controller has no old PID/nonce/session to reattach or terminate. Thus if the ordinary :game Activity survives, the live resource/save pin is released before death is confirmed.

Retained consequence: resource or save mutations may be admitted while the original game process remains alive; retry cannot reattach the existing context. This is a conditional source path, not a claim that every Android crash/force-stop or every hardware scheduler leaves :game alive. Package force-stop does not demonstrate this scenario.

Safe minimum fix: reconstruct persistent sessions into a recovering/pinned state; use a non-starting, identity-bound IPC probe for original session/generation/process and reattach or explicitly terminate. Release resource/save restrictions only after confirmed death/resource closure. A timeout alone must not prove death. Restore recovery UI from persisted facts.

Verification need after the implementation batch: main-process-only interruption with :game verified alive, plus dead-runtime and stale-IPC cases. This audit did not query or interrupt any device.

---

**APP-08-F04 — survives, including the incomplete-READY interleaving.**

Attempts to refute:
- ExistingWorkPolicy.KEEP at HallRepository.kt:L128 prevents duplicate transfer workers, but initialize/recover is not another worker.
- HallApplication.kt:L16-L20 does not initialize recovery at process start. The only production repository.initialize caller is MainActivity.kt:L38. TransferWorker.doWork:L31-L37 accesses the Application-owned repository but does not await that initializer.
- Worker installation owns operationLock at TransferWorker.kt:L146-L149. HallRepository.initialize owns only initializationLock:L49 and calls recover:L57 without operationLock. They are distinct Mutex instances. Room transactions serialize DB changes, not deletion/extraction of staging files.
- Atomic journal writes and per-entry hash checks protect against their own interrupted writes. They do not stop another thread from deleting files after successful validation.
- A surviving unfinished game row would make checkpoint refuse installation at L67. The valid trigger instead has no unfinished runtime row; it does not require bypassing that guard.

Concrete permitted interleaving, without test execution:
1. A prior queued/running transfer is scheduled by WorkManager in a fresh main process before any Activity; no unfinished runtime row exists.
2. Worker enters locked install; Installation.kt:L31-L35 writes CREATED and staging. It extracts A, closes/fsyncs/hashes it at L54-L59 and adds its size to total at L60. seen retains A. The journal is still CREATED until L65.
3. User opens MainActivity. initialize/recover runs under a different lock, reads CREATED at L107-L108, target and replaced do not exist for a new release, and the L123 branch deletes staging including A. L125 removes the journal.
4. Worker continues to entry B. checkpoint succeeds because intent remains run and no runtime is active. L52 mkdirs recreates B's parent/staging, then B is written and verified. The archive/index are outside staging and survive.
5. L62 checks the in-memory seen set and L64 checks the in-memory byte total; both still count A. There is no final existence pass over staging. L65 recreates the journal; L66-L69 writes index/READY and syncs the re-created root.
6. With a new target, L90 renames staging directly; L98-L103 writes the ready installation and activeRelease. A is missing. All stated per-entry hashes happened before the competing deletion and therefore do not refute this outcome.

Scope: not every schedule corrupts an install. Deletion during an open write or before that entry's hash can instead make the worker fail. The above schedule, after A validation and before B's parent creation, establishes that simple failure is not the only allowed result. Correct atomic writes do not protect files already deleted after verification.

Safe minimum fix: make worker and UI entry await one shared initialization barrier, and serialize recovery/reconciliation with the same operation ownership mutex used by installation. Never invoke the initializer reentrantly while holding that non-reentrant mutex. Do not treat an actively owned journal as abandoned. A final file-existence check is useful defense but cannot replace exclusion, since deletion can race that check too.

Verification need after the complete batch: deterministic two-entry extraction barrier after A validation, concurrent first initialization, resume B; assert recovery cannot remove active staging and no incomplete ready install is activated. No source/test/device action was performed.

---

**Search/coverage record**

- Re-read MainActivity.kt:L30-L72; HallApplication.kt:L12-L22; HallApp saveable context/effects L85-L124, search L191-L198, install projections L164/L212/L364 and progress L382-L405.
- Re-read HallRepository initialization L24-L84, launch/destructive guards L147-L205; Database.kt:L7-L46; full TransferWorker.kt and Installation.kt recovery/activation.
- Re-read full SessionController and GameProcessService, GameActivity lifecycle/identity, SaveManager.idle, bootstrap host event/cancellation/lifecycle L15-L93, native adapter monitor/start/request L74-L141, frontend commands/destruction L143-L154.
- initialize/recover search confirmed a single generic startup call in MainActivity plus separate per-game save recover at SessionController:L83; no hidden worker initialization.
- archive.zip.partial/.ready.json/repair/allTransfers hits traced to producer, worker reconciliation, explicit repair and UI. No hidden all-active-install metadata sweep found.
- committedQuery/submittedQuery/SavedStateHandle/catalogue_snapshot had no Android main implementation; current query variable, SearchField and all refresh callers were inspected rather than inferring absence from names alone.
- Host liveness search (PDEATHSIG/getppid/linkToDeath/binderDied/onBindingDied/isBinderAlive/pingBinder/runningAppProcesses/onTaskRemoved/stopWithTask) found only host→runtime linkToDeath in SessionController:L104 within reviewed runtime sources. No game→host termination/reattach fallback was found.

Product source stays at the audited revision. The four findings survive code refutation; production acceptance and specification interpretation remain for their respective independent reviews.
