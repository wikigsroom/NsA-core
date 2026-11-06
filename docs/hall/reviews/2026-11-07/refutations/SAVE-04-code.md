> Archived review record; source snapshot: `cdedc7179ec1842592243a052525e021d8cf70f1`. Initial candidate/pending labels are historical. Final dispositions, including the superseding APP-05-D03 clarification, are in the [consolidated review](../../../source-review.md).
> 归档核对记录；初核中的候选/待复核状态保留历史，最终结论以汇总及独立复核的追加判定为准。

## SAVE-04 — Independent code refutation

> 导出只读取已提交快照，经系统选择器写到玩家选定位置；取消无副作用。
> 导入校验格式、游戏、兼容组、大小、清单与每文件摘要，拒绝越界和非法链接。
> 导入前自动保留当前有效档，离线事务替换后更新索引，失败恢复原档。
> 游戏运行中不开放覆盖，明确先结束游戏；导入完成不直接启动或旋转。
> — docs/product/产品需求细则.md:L960-L963

**Verdict:** the core claims of F1–F8 survive independent code refutation; this is not the independent specification decision or runtime acceptance.

Read-only at cdedc7179ec1842592243a052525e021d8cf70f1. Reviewer did not author SAVE-04.md/json. Inputs: requirements/SAVE-04.md and .json. No product edits, tests, builds, phone reads/input, browser, virtualization or external messages. Only this report and its JSON are written.

**What this demands of the code check:** attempt to locate compensation, recovery, serialization, validation, durable owner/index or caller behavior that defeats each concrete trigger. Distinguish hall save management from legacy native snapshot helpers and source reachability from device evidence.

---

**Where enforcement lives and how each candidate was challenged:**

### SAVE-04-F1 — survives

Reported exchange failures do not restore the old save before returning; a later export/backup can create an empty committed directory that causes launch-time recovery to discard both old and staged directory recovery points.

- **Trigger:** An exception or main-process termination after old committed is renamed and before new committed is published; export before the next game launch.
- **Evidence:** `android/app/src/main/java/org/nsa/hall/transfer/SaveManager.kt:117-125`; `android/app/src/main/java/org/nsa/hall/transfer/SaveManager.kt:127-135`; `android/app/src/main/java/org/nsa/hall/transfer/SaveManager.kt:27`; `android/app/src/main/java/org/nsa/hall/transfer/SaveManager.kt:92`; `android/app/src/main/java/org/nsa/hall/data/HallRepository.kt:42`; `android/app/src/main/java/org/nsa/hall/data/HallRepository.kt:48-60`; `android/app/src/main/java/org/nsa/hall/runtime/SessionController.kt:83,97`; `android/app/src/main/java/org/nsa/hall/transfer/Installation.kt:15-22,105-125`; `android/app/src/main/java/org/nsa/hall/transfer/AppUpdater.kt:44-49`.
- **Counterarguments tested:** The existing private before-import ZIP protects an additional copy but is not consulted by recover and does not restore the active tree. Installation.recover is invoked at repository initialization, but it only scans installation journals/versions. The only SaveManager.recover call occurs during game launch; export and another import call saveRoot first. Atomic journal publication fsyncs its own file/directory; it does not compensate a failed subsequent save-directory rename.
- **Call chain:** HallApp export result → HallViewModel.perform → SaveManager.exportTo → createExport → saveRoot(mkdirs); later SessionController.beginLaunch → SaveManager.recover sees committed.exists and deletes before-import/staging.
- **Boundary retained:** Does not establish permanent loss of every backup. A successful pre-import automatic ZIP normally remains. Failures before the first directory move leave the old tree intact; failure after new rename can retain a fully verified new tree while reporting failure.
- **Minimal correction:** Recover pending save transactions before any committed-path creation/read/write, compensate reported failures to old before releasing ownership, and keep validated old/new identities plus a durable index commit before cleanup.
- **Later validation:** Fault journal/rename/fsync boundaries, interrupt between both renames, interpose export/backup/import before launch, and run recovery twice.

### SAVE-04-F2 — survives

If :game survives loss of the main process, initialization turns its unfinished session into crashed without checking the live owner; the final save idle check then accepts import/export.

- **Trigger:** Main process is recreated while an authenticated native :game process still owns the save.
- **Evidence:** `android/app/src/main/java/org/nsa/hall/data/HallRepository.kt:36,58`; `android/app/src/main/java/org/nsa/hall/data/Database.kt:35`; `android/app/src/main/java/org/nsa/hall/transfer/SaveManager.kt:20-23,86-87`; `android/app/src/main/AndroidManifest.xml:15-20`; `android/app/src/main/java/org/nsa/hall/runtime/GameProcessService.kt:15-16,24-41`; `android/app/src/main/java/org/nsa/hall/runtime/SessionController.kt:64-72,100-107,169-176`; `android/app/src/main/java/org/nsa/hall/runtime/GameActivity.kt:116-124`; `android/app/src/main/cpp/bootstrap.c:63,86-93`; `android/app/src/main/java/org/nsa/hall/ui/HallApp.kt:243-245,351`.
- **Counterarguments tested:** The final idle check and normal beginLaunch both use operationLock, so ordinary same-main-process launch/import races are prevented. GameActivity explicitly destroys/kills :game on its own onDestroy, but no code causes that callback merely because the main host Binder died. SessionController links to native Binder death, which watches the opposite direction; GameProcessService has no host Binder-death teardown or reattach path. Native bootstrap cancellation is changed by explicit cancel/destroy, not by failed sends to the old main process.
- **Call chain:** New HallViewModel.init → repository.initialize → putSession(crashed) → DAO unfinished excludes crashed → SaveManager.idle accepts → importVerified takes only the new process-local mutex.
- **Boundary retained:** Conditional on actual :game survival; no device/lifecycle reproduction was performed. Foreground/background pausing is not proof that native ownership or dirty working state has been released. No second native runtime start is claimed.
- **Minimal correction:** Reconcile/reattach or terminate the actual native owner before ending the session; keep saves unavailable while ownership is unresolved and align UI availability with the same fact.
- **Later validation:** Preserve :game during main-process recreation, then attempt import/export; separately preserve ordinary launch/import mutex exclusion.

### SAVE-04-F3 — survives

A restored import confirmation has no inspected archive pair; errors within importVerified's try delete the archive while leaving the confirmation/retry state pointing to it.

- **Trigger:** Recreate the activity after inspection finishes, or fail extraction/backup/exchange and retry from the existing dialog.
- **Evidence:** `android/app/src/main/java/org/nsa/hall/ui/HallApp.kt:86-100,113-114,340`; `android/app/src/main/java/org/nsa/hall/MainActivity.kt:30-39`; `android/app/src/main/java/org/nsa/hall/transfer/SaveManager.kt:61-84,86-97,125`; `android/app/src/main/java/org/nsa/hall/data/Database.kt:41`.
- **Counterarguments tested:** Activity-result launchers are always registered and selected/route/dialog are saveable; this can preserve an undelivered picker result but does not persist an already-inspected archive. ViewModel survives configuration recreation, but the archive pair is owned by the old composition rather than the ViewModel. Only the success path clears dialog/importArchive; perform reports an error notice without repairing that state. Pre-try errors such as idle/current compatibility recheck do not execute finally and need not delete the file.
- **Call chain:** OpenDocument result → ViewModel.perform → inspectImport(cache UUID archive) → composition importArchive; recreation creates null remember value while dialog restores; Confirm returns at null. Entered importVerified error → finally archive.delete → error notice → stale Confirm.
- **Boundary retained:** Does not claim picker-open recreation always loses the result. User can reselect the original external archive. Recovery/retry loss is proven after result consumption.
- **Minimal correction:** Own a small durable pending import context and restore/reparse it before enabling confirmation; retain a usable archive on retryable failure and clean it on explicit cancellation or durable success.
- **Later validation:** Recreate after result delivery, during copy/parse, on confirmation, and after a recoverable backup failure; retry must have a valid source or explicit reselect state.

### SAVE-04-F4 — survives

The enabled Cancel/outside dismissal changes UI state only; a captured import action can continue through verified staging, backup and directory exchange.

- **Trigger:** Confirm import, wait until extraction has finished and backup is underway, then press Cancel or dismiss.
- **Evidence:** `android/app/src/main/java/org/nsa/hall/ui/HallApp.kt:80,100,145,338,340`; `android/app/src/main/java/org/nsa/hall/MainActivity.kt:31,39`; `android/app/src/main/java/org/nsa/hall/transfer/SaveManager.kt:101-125`.
- **Counterarguments tested:** working is read by the screen but only shows a small header spinner; it does not block the import dialog. The download dialog disables Confirm when working, but the import dialog has no enabled predicate or dismissal guard. Deleting the archive can fail early import, but after extraction and ZipFile closure the exchange no longer reads it. The import runs in ViewModel scope, not rememberCoroutineScope or a dialog-owned Job; no cancellation token is delivered.
- **Call chain:** Confirm captures backup → model.perform queues/runs independent ViewModel action on IO → Cancel deletes archive and hides dialog → already-verified staging plus backup → journal/rename publication still proceeds.
- **Boundary retained:** Requires cancellation after the source archive is no longer needed; not every early cancellation overwrites. Null SAF picker cancellation remains a no-op. Repeated Confirm can queue a stale second action through actionLock.
- **Minimal correction:** Give the import one operation identity/reentry guard and cancellable pre-publication stages; disable cancel/outside dismissal during a truthful noncancellable publication boundary or honor a persisted cancellation decision.
- **Later validation:** Cancel/dismiss at extraction, backup and publication boundaries; double Confirm must not queue a stale action.

### SAVE-04-F5 — survives

A declared/ZIP-sized single file can exceed the 2 GiB expansion cap and be completely written and hashed before the aggregate maximum check rejects it.

- **Trigger:** Compressed archive below the input cap, total_bytes below the claimed cap, but one indexed file has a greater-than-2-GiB size, matching ZIP entry.size and matching digest.
- **Evidence:** `android/app/src/main/java/org/nsa/hall/transfer/SaveManager.kt:19,69,72,82,104-116`; `android/app/src/main/java/org/nsa/hall/security/Trust.kt:15`; `android/app/src/main/java/org/nsa/hall/transfer/Installation.kt:138-164`.
- **Counterarguments tested:** The compressed copy bounds count before output.write, but this is not an expansion bound. checkedBytes accepts any nonnegative Long in canonical decimal form; it imposes no per-file maximum. rejectLinksAndSpecialEntries validates central-directory structure/encryption/mode, not size budgets. expanded <= bytes is a bound against the file's own attacker-selected declaration; total <= maximum runs after complete extraction/fsync/hash.
- **Call chain:** inspectImport trusts capped total_bytes → importVerified per-entry bytes/entry.size agree → write loop checks expanded only against bytes → fd.sync and sha256 → aggregate cap at 113 rejects.
- **Boundary retained:** Original committed tree is not intentionally moved on this rejection. Actual allocated bytes may instead stop at an I/O failure, which is the resource-exhaustion impact rather than an independent cap.
- **Minimal correction:** Validate checked per-file and summed sizes/equality before extraction and enforce an independent cumulative remaining-byte budget before every write.
- **Later validation:** Use bounded/fake I/O to verify no write crosses cap for one large file, cumulative files and overflow; do not fill the user's disk.

### SAVE-04-F6 — survives

Imports omit complete metadata-schema enforcement and explicitly prohibited control characters in paths; a correct backup missing source_release_id/commit_seq/created_at or using U+000A in an indexed filename can reach publication.

- **Trigger:** Valid game/player/group/index/hash/length archive with required provenance fields omitted, or an escaped newline in both metadata path and matching ZIP filename.
- **Evidence:** `android/app/src/main/java/org/nsa/hall/transfer/SaveManager.kt:74-82,90-116`; `android/app/src/main/java/org/nsa/hall/security/Trust.kt:37-44,85-116`; `docs/engineering/contracts/save-backup.schema.json:15-26,48-70`; `docs/engineering/04-transfer-install-save.md:47`.
- **Counterarguments tested:** strictObject rejects malformed/duplicate-key JSON, not missing schema fields or unknown properties. save_schema/game/player/group are rechecked, but no reader accesses the required provenance/time/sequence fields during import. safeChild blocks traversal, absolute paths, colon/backslash, non-NFC and canonical escape; those are real protections. canonicalPath may itself fail for NUL; the retained concrete control-character example is newline, which has no rejection in this validator.
- **Call chain:** inspectImport strictObject + selected-field checks → final import rechecks selected fields → safeChild lacks control-character test → length/hash match → publication.
- **Boundary retained:** No root escape or platform-signature requirement is claimed. Existing mode checks reject ordinary symlink/special-file entries.
- **Minimal correction:** Validate the entire supported save metadata schema and reject controls in shared archive/index path validation before file I/O.
- **Later validation:** Missing/wrong-type provenance/time/sequence, unknown fields, newline/DEL controls, and existing traversal/mode/hash rejection fixtures.

### SAVE-04-F7 — survives

Successful import and recovery perform no durable imported-commit/index registration; the immediate next export still reads the prior save-seq fact and current install metadata.

- **Trigger:** Persist save-seq:G=12, import an otherwise valid source commit 3, then export before a later native save.
- **Evidence:** `android/app/src/main/java/org/nsa/hall/transfer/SaveManager.kt:37,86-135`; `android/app/src/main/java/org/nsa/hall/ui/HallApp.kt:340`; `android/app/src/main/java/org/nsa/hall/runtime/SessionController.kt:151-156`; `android/app/src/main/java/org/nsa/hall/data/Database.kt:7-16,37-45`; `docs/product/产品需求细则.md:962`; `docs/engineering/05-data-state.md:40-41,105`.
- **Counterarguments tested:** The DAO facts table could store an index without a dedicated entity, but importVerified/recover write no facts or other DAO mutations. The UI success branch only clears state and displays a notice; no caller adds persistence. A later native save_committed event updates save-seq, but cannot register this completed offline import before another game launch/save. Keeping a local monotonic sequence is legitimate in principle; simply changing 12 to imported origin 3 is not the requested fix.
- **Call chain:** importVerified exchanges/cleans directories → UI notice only → createExport metadata pulls old save-seq/current active release → only later native acceptEvent can mutate the fact.
- **Boundary retained:** Retained defect is the missing durable import/index transaction. The example does not require replacing local monotonic revision with the archive's lower origin sequence, nor by itself establish which display timestamp provenance the product wants.
- **Minimal correction:** Register a verified imported commit and source provenance with an independent local revision; recover the matching tree/index exactly once before rollback cleanup.
- **Later validation:** Import/re-export without launching, inspect durable identity/provenance, and interrupt/recover at publication/index boundaries.

### SAVE-04-F8 — survives

2048 stages state.new inside the same committed root that hall export enumerates; failed/interrupted pre-rename saves leave it exportable despite no commit event.

- **Trigger:** Short write, fsync failure, rename failure or termination before rename; end the game without a later successful save, then export while genuinely idle.
- **Evidence:** `android/native-games/2048/game.c:48-57,75,129-149,155-157`; `android/app/src/main/java/org/nsa/hall/transfer/SaveManager.kt:27-41`; `android/app/src/main/java/org/nsa/hall/data/HallRepository.kt:42`; `android/app/src/main/java/org/nsa/hall/runtime/SessionController.kt:97`; `android/app/src/main/cpp/bootstrap.c:159`; `android/app/src/main/assets/runtime-registry.json:1`.
- **Counterarguments tested:** The filename 'committed' does not make every file there committed; the native publisher controls which bytes are published. 2048 correctly emits save_committed only after rename/directory fsync, but exporter does not use a committed-file index/marker. The 2048 exit loop performs no mandatory final save/cleanup, so ordinary exit after a failed save need not repair state.new. Normal successful save consumes state.new through rename; the finding is confined to failed/interrupted saves.
- **Call chain:** SessionController saveRoot(committed) → bootstrap context.save_root → 2048 open state.new/write/failure returns without unlink → process ends → hall createExport enumerates all regular files → packages state.new.
- **Boundary retained:** Current registry contains the 2048 slot; no device execution or source-to-binary equivalence was tested. Not every crash leaves a temporary file, and no normal successful state.dat corruption is claimed.
- **Minimal correction:** Stage owned temporary writes outside the exported committed tree or use authoritative committed-file records; clean only owned staging and publish verified state durably.
- **Later validation:** Fault 2048 short-write/fsync/rename boundaries, end without another save, and export only the prior committed state.dat; before first commit do not fabricate a nonempty backup.

---

**Legacy native menu reachability and scope facts:**

### SAVE-04-LEGACY-REACH — survives

Legacy Export save / Import save actions are source-reachable in a hall-bound nxnative runtime, through the native input queue and native overlay Game tab.

**Evidence:** `nxnative/dm/android/build.py:55-56,71-83`; `nxnative/dm/android/hall_adapter.c:108-125`; `nxnative/dm/android/front_android.c:1100-1113,1137-1150,1030,903-908,297-305,817-849,423-434,460`; `nxnative/dm/android/overlay_default.tsv:30,46`; `nxnative/dm/android/overlay_layout.h:9-18,110-117`; `nxnative/dm/android/gfx_vk_present.c:377-421,648-654`; `android/app/src/main/java/org/nsa/hall/runtime/GameActivity.kt:92-115`.

**Call chain:** Hall slot build includes android/*.c under DM_HALL_BINDING → adapter installs front callbacks → native queue → on_input/handle_event/touch → OV_MENU tap → menu Game tab → menu_do(OVM_EXPORT/OVM_IMPORT).

**Boundary:** Java Back opens a separate Java pause menu, so Back alone is not the reachability proof. The native on-screen menu/controller route suffices. This applies to nxnative hall bindings, not the separate 2048 runtime implementation; binary/device reachability remains untested.


### SAVE-04-LEGACY-SCOPE — survives

In a hall binding, legacy raw backups/requests live in private settings_root/saves, and legacy import/export bypass the hall SAF archive/metadata/index flow.

**Evidence:** `nxnative/dm/android/hall_adapter.c:108-109`; `nxnative/dm/android/front_android.c:1113,1137`; `nxnative/dm/android/saves_android.c:93-97,101-126,133-155,158-174`; `android/app/src/main/java/org/nsa/hall/runtime/SessionController.kt:97`; `android/app/src/main/java/org/nsa/hall/data/HallRepository.kt:42-43`.

**Call chain:** settings_root → DM_HALL_ROOT → g_root → dm_saves_paths(g_root) → g_dir=<settings_root>/saves, g_save=DM_SAVE=<committed>; raw folder copy, name-only import checks and raw directory exchange.

**Boundary:** Standalone comments describe externalDataPath; the hall override changes g_root to private settings. No remote injection or cross-package privilege bypass is established. Whether these extra user-facing controls violate SAVE-04 or are allowed helpers is for the independent spec refuter.

**If specification review rejects this alternate flow:** Disable/delegate raw save actions and pending raw save requests specifically in DM_HALL_BINDING; retain approved standalone behavior.

### SAVE-04-LEGACY-INRUN — refuted

Legacy menu import directly overwrites the active save while the existing runtime is running.

**Evidence:** `nxnative/dm/android/front_android.c:527-538,524-525,1136-1150`; `nxnative/dm/android/saves_android.c:150-155,176-196,158-174`.

**Refutation rationale:** Menu import writes import.txt and exits :game; the next runtime start consumes the request before the native host thread/mounts start. It therefore does not prove same-runtime in-run replacement.

**Boundary:** A separate confirmed scope fact remains: next launch applies the raw import automatically without the hall metadata validator. Request is unlinked before apply; user and device trees swap separately.


### SAVE-04-DEVICE-SIBLING — unverified

A currently enabled runtime loses a second committed_device tree through the hall export.

**Evidence:** `nxnative/dm/host/hle_fs.c:493-512`; `nxnative/dm/android/saves_android.c:95-97,104-120`; `android/app/src/main/java/org/nsa/hall/transfer/SaveManager.kt:27-41`; `android/app/src/main/assets/runtime-registry.json:1`.

**Refutation rationale:** Conditional mechanism exists: hle_fs can mount DM_SAVE_device separately; legacy exporter includes it while hall export walks only committed. Current registry contains 2048/BotW and no inspected evidence establishes a current enabled slot's dual-mount use.

**Boundary:** Keep as a future-slot contract/test requirement, not a confirmed current loss finding.


---

**Paths walked:**

- Hall export: CreateDocument result (`HallApp.kt:L113`) → `perform:L39` → `exportTo:L46-L49` → operation-locked `createExport:L21-L44` → selected SAF stream. Null result is a real no-op. Ordinary idle enforcement is real, but stale owner facts (F2), unresolved import recovery (F1) and uncommitted native staging (F8) remain.
- Hall inspect: OpenDocument result (`HallApp.kt:L114`) → `inspectImport:L61-L84`: capped archive copy before write, fsync, central-directory mode scan, strict JSON and selected-field/game/group checks. This defeats ordinary traversal/link/duplicate-key claims, not F5/F6.
- Hall confirmation: `HallApp.kt:L340` captures pair → ViewModel action lock → operation lock → idle/current-group recheck → index/ZIP count → safe path + length + hash → post-file total → backup → journal/renames → cleanup. Early errors preserve original; entered-try errors destroy the cached source. Neither caller nor callee cancels on dialog dismissal or registers the imported index.
- Existing backup: `SaveManager.kt:L117` → already-locked backup helper:L53 → export helper → private ZIP copy/fsync:L55-L58. The helper avoids recursive mutex acquisition. Any failure before old rename preserves old; this does not compensate later exchange failures.
- Cold start: `HallViewModel:L38` → `initialize:L48-L60` → Installation recovery only → stale sessions marked crashed → UI. Later game launch has the sole SaveManager recovery at `SessionController:L83`; it precedes that launch's saveRoot creation but not intervening exports/backups.
- Session ownership: beginLaunch:L64-L72 and final import idle share operationLock; finish:L169-L176 tears down known :game before marking terminal. New-process initialize lacks that live owner check; game-side host send errors are swallowed.
- Native 2048: save root passed through `SessionController:L97` and `bootstrap.c:L159` → state.new created inside committed → rename/fsync before success event → no failure unlink or exit cleanup → all-file hall export.
- Legacy: hall build includes frontend/saves implementation → adapter overrides roots/install callbacks → native input queue → native menu → raw export or deferred import request → next start applies import before host threads mount saves. It does not compensate a hall import transaction.

---

**Searched:**

- `SaveManager(repository).recover / fun recover and all .recover callers` in android/app/src/main/java/org/nsa/hall: Only launch calls save recovery at SessionController:83; repository init calls Installation.recover, whose roots/phases are install-only.
- `saveRoot / backupUnderOperationLock callers` in android/app/src/main/java/org/nsa/hall: Export27, import92, launch97, upgrade Installation72-73, AppUpdater48, explicit deletes. Upgrade/update share operationLock but do not resolve save journals first.
- `SavedStateHandle|pending.import|takePersistableUriPermission|save-import|importArchive` in android/app/src/main/java and cpp: No durable pending import owner. Cache path SaveManager63; UI remember pair100, picker114, cleanup239, dialog340.
- `working|enabled|DialogProperties|BackHandler|Modal` in android/app/src/main/java/org/nsa/hall/ui/HallApp.kt: Header spinner145, download Confirm338 has working guard, import340 has no disable/dismiss cancellation guard.
- `save-seq:|save_commit|save_backup|putFact` in android/app/src/main/java/org/nsa/hall: save-seq read only SaveManager37, write only SessionController153; no imported commit/index or backup index writer.
- `flock|F_SETLK|F_SETLKW|FileLock|lockFile` in SaveManager.kt, android/app/src/main/cpp, nxnative/dm/android/saves_android.c, nxnative/dm/host/hle_fs.c: 0 interprocess save-lock hits in the inspected ownership paths.
- `state.new|state.dat|save_root|commit_sequence|unlink|save()` in android/native-games/2048/game.c: save48-57 leaves temporary on pre-rename failure; startup155-157 loads only state.dat; exit129-149 has no save/cleanup.
- `DM_HALL_BINDING|dm_saves|OVM_EXPORT|OVM_IMPORT|menu_open|on_input` in nxnative/dm/android front/adapter/build/overlay/saves/gfx source: Hall binding does not exclude raw menu/request code; input→menu and automatic pre-mount request chains walked.
- `initial guessed build/native filenames and broad tools search`: Corrected to nxnative/dm/android/build.py and gfx_vk_present.c via rg --files. Broad tools search was interrupted with no conclusions; no tool/build/device command was executed.

---

**How the verdict was reached:**

F1 is direct control-flow and recovery ordering, not a speculative power-loss effect: the mkdirs/existence branches deterministically discard the directory recovery points for the stated interruption ordering. The private automatic ZIP prevents a stronger “every copy lost” claim. F2 retains an explicit live-native survival precondition and rejects the simpler ordinary same-main-process race. F3/F4 are demonstrated by state ownership and action lifetime, independent of picker-open behavior. F5 is late enforcement rather than total absence of size checks. F6 retains existing traversal/link defenses. F7 is absence of any import index persistence, not a demand to lower local sequence numbers. F8 distinguishes published state.dat from the runtime's own uncommitted state.new.

The legacy path is exposed by native user controls in a hall binding at source level; the Java Back menu is a separate path and does not remove the native touch/controller route. Applying a pending legacy import before host start refutes an in-run overwrite claim. Raw operations lack the hall SAF/archive/index contract, but the independent specification reviewer must decide the permitted product scope.

---

**Open questions and validation limits:**

- Source evidence only. All F1-F8 core claims survive code refutation with explicit trigger boundaries; independent spec refutation decides final acceptance.
- Main/:game survival and filesystem power-loss behavior not reproduced.
- Current packaged library contents not reverse-verified against source; registry references do not prove native UI behavior on a device.
- No claim that imported origin sequence must replace a local monotonic sequence.
- No confirmed current dual-device-save loss, untrusted raw-import injection, or same-runtime legacy overwrite.

All proposed tests remain queued for the parent's consolidated verification after the complete implementation batch. No existing product test was run or altered.
