> Archived review record; source snapshot: `cdedc7179ec1842592243a052525e021d8cf70f1`. Initial candidate/pending labels are historical. Final dispositions, including the superseding APP-05-D03 clarification, are in the [consolidated review](../../../source-review.md).
> 归档核对记录；初核中的候选/待复核状态保留历史，最终结论以汇总及独立复核的追加判定为准。

## SAVE-04 — Committed save export and offline verified import

> 提供导出已提交存档至系统选择的持久位置，以及导入相应游戏存档。导入前验证格式、大小与游戏标识，并备份旧档；不在游戏运行中直接替换存档。
> — docs/product/产品需求细则.md:L954, SAVE-04
>
> SAVE-04.01：导出只读取已提交快照，经系统选择器写到玩家选定位置；取消无副作用。
>
> SAVE-04.02：导入校验格式、游戏、兼容组、大小、清单与每文件摘要，拒绝越界和非法链接。
>
> SAVE-04.03：导入前自动保留当前有效档，离线事务替换后更新索引，失败恢复原档。
>
> SAVE-04.04：游戏运行中不开放覆盖，明确先结束游戏；导入完成不直接启动或旋转。
> — docs/product/产品需求细则.md:L960-L963

**Verdict:** partial · confidence: high · P0 requirement.

Read-only source audit of main cdedc7179ec1842592243a052525e021d8cf70f1, 2026-11-07 Asia/Taipei. Findings below are source-supported candidates for the parent's independent code/spec refutation, not device acceptance. No product files changed; no tests, builds, device reads/input, browser, virtualization, cloud-save expansion, or further agents were used. Only this report and SAVE-04.json are written.

**What this demands of an implementation:** export a coherent committed save through the selected SAF destination, preserve original saves on cancellation/failure, fully validate bounded imported data, retain the old valid save before a recoverable directory/index transaction, and prevent concurrent live-native save replacement even after coordinator recreation. Successful import must remain in the portrait hall.

Supplementary normative evidence:

- docs/product/页面与交互验收规格.md:L230-L247: S15 uses commit/backup/lock facts, keeps original plus draft on failure, and S16 requires backup before exchange, original on any failure, and no directory modification on cancel.
- The same file:L469, INT-03: distinguish selection/parse/preflight/backup/exchange/success/failure; SAF cancellation is not success; insufficient space must not begin overwrite.
- docs/design/obsidian-portrait/03-screen-specs.md:L550-L562 and L579-L594: display backup identity/coverage, recover the whole flow, retain originals on write interruption, and prohibit import while running.
- docs/engineering/04-transfer-install-save.md:L74-L80: native/disk commit authority; export snapshot before picker; archive and expanded-size limits of 2 GiB; exclusive offline import; journal old/new ownership and commit; recover a complete old/new save at each interruption boundary.
- docs/engineering/05-data-state.md:L40-L44 and L99-L105: save commit/backup indexes, recoverable pending system results, and disk/database reconciliation.
- docs/engineering/03-runtime-integration.md:L64-L74: process-local observations alone cannot prove native resources released; reconcile or re-handshake an alive :game after main-process recreation.
- docs/product/开发计划与验收矩阵.md:L132,L249,L256: AC-S03, CON-03 and CON-10.
- docs/engineering/decisions/0001-integrated-development-round.md:L5-L7: implement the whole round before consolidated verification. This audit only lists future test needs.

| Subrule | Verdict | Enforcement present | Remaining gaps |
| --- | --- | --- | --- |
| SAVE-04.01 | partial | CreateDocument result, null-result no-op, operation-locked snapshot, compressed temporary archive, stream to SAF | F2: stale idle authority after main recreation; F8: 2048 leaves uncommitted state.new inside the exported tree |
| SAVE-04.02 | partial | ZIP structure/mode checks, game/player/group, archive byte cap, safe paths, membership/count checks, per-file length/hash | F5: expanded cap checked after the large file is written; F6: required metadata fields and control-character paths accepted |
| SAVE-04.03 | partial | Pre-exchange automatic ZIP backup, verified staging, journaled renames, launch-time recovery | F1: exchange failures and recovery ordering; F3: lost import draft/retry; F4: cancel does not cancel ongoing import; F7: no import index/commit update |
| SAVE-04.04 | partial | Final idle recheck inside operationLock; import success only closes dialog and shows notice | F2: idle recheck trusts records unconditionally ended on coordinator restart; UI never disables running-state import |

---

**Where enforcement lives:**

~~~kotlin
// android/app/src/main/java/org/nsa/hall/transfer/SaveManager.kt:L20-L27
private suspend fun idle(game: String) {
    require(repository.dao.unfinished().none { it.gameId == game }) {
        "请先退出游戏，再管理存档"
    }
}
suspend fun createExport(game: String): File =
    repository.operationLock.withLock { createExportUnderOperationLock(game) }
// createExportUnderOperationLock calls idle, resolves installed manifest, and saveRoot.
~~~

- SaveManager.kt:L28-L44 enumerates the root, rejects symlink files, bounds aggregate size/count, computes per-file hashes, creates backup.json and files/*, and bounds the compressed result.
- SaveManager.kt:L46-L49 writes the archive to the URI returned by SAF and deletes the export temporary afterward. HallApp.kt:L113-L114 ignores null picker results; L244-L245 actually invokes system pickers.
- SaveManager.kt:L61-L84 copies and caps the input, fsyncs it, invokes ZIP mode validation, reads strict JSON, checks schema value/game/player/current compatibility group, total bytes and count.
- SaveManager.kt:L86-L116 rechecks idle/game/group inside operationLock, rejects case-colliding index paths, requires archive membership count, validates safe paths, exact lengths, per-file SHA-256, expanded total and manifest total.
- Trust.kt:L37-L44 checks relative path length, absolute/colon/backslash/dot segments, NFC and canonical containment. Installation.kt:L138-L164 walks the ZIP central directory and rejects encrypted entries and Unix nonregular types. strictObject at Trust.kt:L85-L116 rejects duplicate JSON keys/invalid UTF-8/depth/trailing data.
- SaveManager.kt:L117-L124 backs up existing files, writes verified/old-moved/new-active journals, moves directories and deletes the old directory. L127-L135 implements recovery.
- HallRepository.kt:L36 defines one in-process mutex. SessionController.kt:L64-L72 writes the unfinished session under that mutex before native startup; L174-L176 ends it after native termination at L170-L172. TransferWorker.kt:L146-L149, Installation.kt:L72-L78, and AppUpdater.kt:L44-L49 use the same lock and explicit already-locked backup helper.
- HallApp.kt:L340 success changes only importArchive/dialog/notice. MainActivity is portrait and GameActivity is separately landscape in AndroidManifest.xml:L12-L20; no import-success launch call exists.

---

**Candidate divergences and safe minimal corrections:**

### F1 — High / P1: an exchange failure is not rolled back, and another operation can erase the automatic rollback directory

**Evidence:** SaveManager.kt:L119-L125, L127-L135; HallRepository.kt:L42,L48-L60; SessionController.kt:L83,L97; HallApp.kt:L113,L244.

**Concrete trigger A:** after old committed is renamed to before-import at L120, make syncDirectory(parent), the next journal update, or the new-directory rename fail. The finally block deletes staging/archive but never restores before-import; the caller reports an error through MainActivity.kt:L39. The current committed path is absent until an explicit later game launch invokes recover. Failure after the new rename at L122 instead leaves the new save active despite an import error, and recover accepts any existing committed path and removes before-import regardless of the recorded phase.

**Concrete trigger B:** terminate the main process between the old rename (L120) and new rename (L122). Reopen the hall and export before launching the game. initialize only calls Installation.recover, not SaveManager.recover. createExport calls saveRoot, whose mkdirs creates an empty committed directory, then reports no files. On later game launch recover sees committed.exists(), skips restoration, removes staging/before-import and journal, and starts with the empty tree.

A separate automatic ZIP from L117 normally still exists. This finding does **not** claim every copy is permanently lost; it proves failed-original restoration and destruction of the automatic directory recovery point. Before-import ZIP copy failure or fsync failure occurs before the first rename and leaves the original intact.

**Correction:** resolve any pending import under operationLock before every path that can create/read/mutate committed (including export, new import, upgrade/App backup, launch). Distinguish a missing save from a path merely created by mkdirs. Journal old/new validated ownership, commit identity and durable phase; on a reported failure compensate to old before releasing the lock. Retain old and verified staging until publication/index registration succeeds; do not remove recovery evidence on uncertain state. Sync backup/staging directory entries as well as files, and make recovery durable before cleanup. Do not treat directory existence as proof of a verified new commit.

### F2 — High / P1: recreating the main process can permit overwrite of a still-live native session

**Evidence:** HallRepository.kt:L58; Database.kt:L35; SaveManager.kt:L20,L86-L87; AndroidManifest.xml:L15-L20; GameProcessService.kt:L15-L16,L24-L41; SessionController.kt:L104; bootstrap.c:L63,L86-L93; docs/engineering/03-runtime-integration.md:L66,L74.

**Concrete trigger:** keep :game alive while only the hall/main process is reclaimed, then reconstruct the hall and import the same game's save. initialize unconditionally changes every unfinished record to crashed. idle now accepts the game. The game has a separate process; GameProcess.send swallows a dead host send, the game side installs no host Binder-death stop/reconcile handler, and bootstrap cancellation changes only on its own destroy/cancel calls. Neither SaveManager nor native save code takes a cross-process save lease/file lock. Thus the final under-lock recheck can approve replacement while the old native process still owns its working save and can commit.

This is a source-level reachable-state finding; whether a particular device reclaims main while preserving :game was not exercised. The specification explicitly requires handling that state. It is not a normal same-main-process launch race: the session creation and final idle check share operationLock, so that simpler suspected race is refuted.

The page also always enables Import at HallApp.kt:L245 (SecondaryButton at L351 has no enabled state), including when latest reports an active session; the normal backend does show the required exit message at SaveManager.kt:L20.

**Correction:** before marking a session ended, reconcile/reattach the actual native owner or perform controlled teardown and prove release. Conservatively block import/export while ownership is unresolved, with an explicit end-game action. Add an interprocess save-ownership guard where needed; a new Mutex after process death is insufficient. Bind UI availability to the same active-session fact and retain the backend check.

### F3 — Medium / P2: import confirmation/retry loses its file state on recreation or failure

**Evidence:** HallApp.kt:L86,L89,L96,L100,L114,L340; SaveManager.kt:L63-L84,L125; MainActivity.kt:L39; Database.kt:L41.

**Concrete trigger A:** choose a valid backup, reach confirmation, then recreate the activity or main process. dialog is rememberSaveable, but importArchive is only remember. The restored import dialog still appears; Confirm executes importArchive ?: return and silently does nothing. There is no restored path/metadata/pending operation record to repopulate the pair.

**Concrete trigger B:** fail hash validation, extraction, backup creation, or exchange after entering importVerified's try. finally deletes the source cache archive. The UI only clears importArchive on success, so the still-open dialog's next Confirm uses a deleted file and cannot retry. A process interruption while the already-returned picker result is being copied/parsed likewise has no durable operation receipt to resume.

This does **not** claim process death while the picker itself is open necessarily loses the result: selected and route are saveable and both launchers are unconditionally registered. Null SAF results are genuine no-ops. The proven failures are after result consumption/confirmation; reselecting the user's original backup remains possible.

**Correction:** persist a small pending-import record (stable game/source route, owned cache or files path, metadata digest, stage/status), restore/reparse it before enabling confirmation, and display a recover/reselect state if the file was reclaimed. Keep a usable inspected archive on recoverable failure; clean it on explicit cancellation or durable success. Do not store the large archive/JSONObject itself in saved navigation state.

### F4 — High / P1: Cancel remains available while the import continues to replace the save

**Evidence:** HallApp.kt:L340; MainActivity.kt:L31,L39; SaveManager.kt:L101-L125; S16 cancel requirement at 页面与交互验收规格.md:L247.

**Concrete trigger:** tap Confirm, allow extraction to finish and automatic backup at L117 to start, then tap Cancel or dismiss the still-visible dialog. The UI deletes importArchive, clears dialog and returns. The model action already captured the pair and is running independently; it receives no cancellation intent and goes on to L119-L124 and replaces the save. Deleting the source archive at that stage does not affect already-verified staging. An earlier cancel after ZipFile opened can also leave its open descriptor usable. This is an enabled explicit cancellation followed by the cancelled overwrite, not cancellation of the system picker.

Repeated Confirm is also enabled: actionLock queues another import using the same pair, and the second one fails after the first deletes the archive. The model's working spinner does not disable these buttons.

**Correction:** model selection/validation/backup/exchange as an operation with one owner and a re-entry guard. Honor Cancel before the noncancellable publication boundary and persist that intent. Once that boundary starts, disable/hide cancellation and outside dismissal and show the real phase until completion/recovery; do not offer a button labelled Cancel that only hides the UI.

### F5 — Medium / P2: a compressed file can expand beyond 2 GiB before the size limit is enforced

**Evidence:** SaveManager.kt:L19,L69,L82,L104-L116; engineering/04-transfer-install-save.md:L78.

**Concrete trigger:** a small DEFLATE archive declares total_bytes below 2 GiB but one indexed/ZIP file has bytes/entry.size greater than 2 GiB and a matching digest. inspectImport only checks claimed total. importVerified checks the entry against its own declared bytes, writes the complete file, fsyncs and hashes it, then checks total <= maximum at L113. The archive is eventually rejected, but arbitrarily more than the expanded cap has already been written, potentially filling internal storage.

Existing archive-copy bounds correctly run before writing the over-limit compressed chunk. Per-file expanded <= bytes protects against a dishonest entry's own size, but is not the required independent total cap. Original committed data is not intentionally moved on this failure.

**Correction:** preflight every file size with checked arithmetic and a maximum, require the sum to equal total_bytes and stay <= 2 GiB before extraction, and enforce an independent remaining expanded-byte budget before each write. Keep the current exact length/hash checks; estimate staging plus old-backup space before starting publication.

### F6 — Medium / P2: the import format and path policy are only partially validated

**Evidence:** SaveManager.kt:L74-L82,L90-L116; save-backup.schema.json:L15-L26,L59-L70; Trust.kt:L37-L44; engineering/04-transfer-install-save.md:L47; CON-03 at 开发计划与验收矩阵.md:L249.

**Concrete trigger A:** provide a structurally valid archive with correct game/player/group/files/lengths/digests but omit source_release_id, commit_seq and created_at from backup.json. Those required schema fields are never read by inspectImport/importVerified, so the backup can be imported. strictObject is a syntax/duplicate-key parser, not a save-schema validator. Required types/unknown-field policy also lack schema enforcement.

**Concrete trigger B:** include a file path with an escaped newline/control character (for example slot plus U+000A plus data.bin), with matching ZIP name and digest. safeChild rejects traversal/backslash/colon/non-NFC/dot segments, but contains no control-character rule; on Android this path is accepted and written despite the explicit safety rule.

**Correction:** validate required save-schema fields/types/identifier/time/sequence constraints and the supported field policy before confirmation, preserving origin metadata for index registration. Explicitly reject control characters (including NUL/DEL) in archive/index paths before file I/O; use the same path validator for save and resource archives. Do not add a platform signature requirement: S16 explicitly permits unsigned backup packages.

### F7 — Medium / P2: a successful import does not register the imported save or update its index

**Evidence:** SaveManager.kt:L37,L86-L125,L127-L135; SessionController.kt:L151-L156; Database.kt:L7-L16,L37-L45; SAVE-04.03 at 产品需求细则.md:L962; engineering/05-data-state.md:L40-L41,L105; local-records.schema.json:L256-L302.

**Concrete trigger:** with persisted save-seq:G = 12, import a valid compatible backup from commit 3 and a different source release, then immediately export without launching. importVerified/recover only alter directories and delete the journal; neither writes a commit/index fact or retains imported origin. createExport reads the old value 12 and labels the data with the current active release/current creation time, losing imported origin/index linkage.

No caller compensates: HallApp success only changes in-memory dialog/notice; the only writer of save-seq is acceptEvent after a later native save event. The database has no save-commit/save-backup entity, and SaveManager has zero DAO mutation/transaction calls.

**Correction:** record a durable verified import commit plus source provenance/digest/bytes/compatibility group, and reconcile its index with the journal before old-state cleanup. Use a local monotonic revision distinct from the imported origin sequence; do not simply overwrite the local revision with a possibly lower remote/archive value. Export from that committed record, and recover its index exactly once.

### F8 — Medium / P2: the 2048 runtime leaves uncommitted temporary bytes inside the exportable committed tree

**Evidence:** android/native-games/2048/game.c:L48-L57,L75,L155-L157; SaveManager.kt:L27-L41; HallRepository.kt:L42; runtime-registry.json:L1.

**Concrete trigger:** interrupt a 2048 save after state.new is opened/written and before it is renamed, or cause its short-write/fsync/rename failure at L53-L54, then end the game without another successful save. state.new is placed under context.save_root and is not unlinked on failure or startup. Export's root.walkTopDown().filter(isFile) includes that uncommitted/partial file, and can even export state.new alone when no first state.dat was committed.

The Switch HLE path is different: hle_fs.c:L97-L108 and L549-L558 writes in committed.work / committed.new sibling trees and swaps the committed tree. This finding specifically concerns the original 2048 slot actually listed in the registry, not an inference that all runtimes put work files under committed.

**Correction:** keep the demo's staging file outside the committed root, clean only that owned staging location after failure/recreation, and publish/fsync a verified committed state. Alternatively give export an authoritative committed-file index. Do not globally exclude every filename ending .new, since those may be legitimate save files for another game.

---

**Paths walked:**

- Export selection cancellation: HallApp.kt:L244 -> L113 with null -> no model action, source/cache not touched. Successful selection -> MainActivity.kt:L39 -> SaveManager.exportTo -> operationLock -> idle -> game/install/manifest -> root/file-count/path/symlink/size/hash -> ZIP -> archive-size -> SAF stream -> export-temp cleanup. Stream/open/write errors do not delete the original; createExport failures may leave an unreferenced cache ZIP because exportTo's try starts after createExport (SaveManager.kt:L38-L49). The snapshot is created after picker return, differing from engineering L76 ordering; no additional corruption finding is inferred from that ordering alone.
- Import picker cancellation: HallApp.kt:L245 -> L114 null -> no inspect/import/success message. Non-null -> archive-copy limit/I/O/fsync -> rejectLinksAndSpecialEntries -> bounded metadata read -> strictObject -> schema/game/player -> current install/group -> total/count. Any inspect exception removes its cache copy and leaves source saves alone (SaveManager.kt:L61-L84).
- Confirmation: normal cancel/dismiss removes only the staged cache archive. Recreation/failure and in-progress Cancel differ as F3/F4; busy controls are not disabled.
- Final import checks: operationLock -> idle -> current install + schema/game/player/group recheck. Active group changing between inspect/confirm is rejected before directory staging. Unlocked inspect idle is not relied upon for overwrite.
- Extraction: duplicate case-folded index -> ZipFile count -> each safe path/size/entry -> exact entry size -> each read bound -> output fd.sync -> actual length/hash -> total -> manifest total. Missing files, hashes, overflow, invalid paths and ZIP parse errors exit before backup/exchange; finally removes archive/staging. Existing containment and mode checks do reject ordinary traversal/symlink/device-file payloads.
- Existing save: L117 -> backupUnderOperationLock -> createExportUnderOperationLock -> cache ZIP -> private ZIP copy -> private ZIP file fsync. Any backup error prevents the first rename. Empty current save skips backup. No recursive mutex acquisition occurs.
- Journal boundaries: before verified -> original intact; verified before first rename -> original intact; first rename before/after parent sync or old-moved -> absent committed with old in before-import; second rename before/after parent sync or new-active -> new committed and old before-import; cleanup -> new committed, private ZIP retained. Reported exceptions are not compensated (F1).
- Restart: HallRepository.initialize -> Installation.recover only -> mark old sessions crashed -> UI commands. Game launch later creates unfinished record under lock -> SaveManager.recover -> settings/context/saveRoot -> service/native. recover restores before only if committed is absent; it never verifies an existing committed tree, never updates the index, and does not fsync recovered renames/deletions.
- Upgrade/App-update backups: TransferWorker.kt:L146-L149 holds operationLock and blocks live game before Installation.install -> automatic backup; AppUpdater.kt:L44-L49 similarly holds it. They do not recover unresolved import journals before saveRoot. MaintenanceWorker.kt:L13-L18 only trims logs and provides no hidden save recovery.
- Native save ownership: SessionController.kt:L97 -> bootstrap.c:L159 -> hall_adapter.c:L109 -> DM_SAVE -> hle_fs.c:L493-L512 -> transactional mount/commit. Save commits are emitted through hall_adapter.c:L87-L92 -> SessionController.acceptEvent:L151-L156. Ordinary launch/termination and save commands share the main-process lock, but the native process owns no corresponding interprocess lease (F2).
- Other native save path: build.py:L71-L83 includes Android sources in hall bindings; hall_adapter.c:L108-L122 calls the legacy frontend. front_android.c:L433-L434,L849,L856-L868 exposes native import/export in the Game tab (overlay_layout.h:L110-L117). It uses saves_android.c:L101-L126 raw committed-copy export, and L150-L196 deferred raw-directory import before mounts. It is independent of SaveManager and does not compensate any hall import failure; see Open questions for its contract scope.

---

**Searched:**

- In android/app/src/main/java/org/nsa/hall, SavedStateHandle|pending_result|takePersistableUriPermission -> 0 hits. This alone is not a finding; saved selected/route and registered launcher behavior were inspected before narrowing F3.
- SaveManager(repository).recover -> exactly 1 call, SessionController.kt:L83. Repository initialize, Installation.recover and MaintenanceWorker were read to rule out a hidden cold-start save recovery.
- importVerified( -> 2 hits: declaration SaveManager.kt:L86 and UI caller HallApp.kt:L340. Both walked.
- backupUnderOperationLock -> 5 hits: wrapper, declaration, import caller, Installation caller, AppUpdater caller. All walked; the latter two already hold operationLock.
- save-seq: -> exactly 2 hits: export read at SaveManager.kt:L37, native-event write at SessionController.kt:L153.
- In SaveManager.kt, putFact|putGame|putInstall|putSession|withTransaction -> 0 hits; source_release_id|created_at|commit_seq -> 1 hit, only export creation at L37. Database entity/DAO definitions and both UI caller/success path were also read.
- In SaveManager.kt, maximum -> 6 hits: declaration L19; export expanded L34; export archive L43; input-copy L69; metadata total L82; post-file actual total L113. No independent expanded budget exists inside L109's copy loop.
- In SaveManager.kt, syncDirectory -> 3 hits, all parent syncs after exchange/cleanup at L120,L122,L124. atomicJson syncs its own journal directory (Installation.kt:L15-L22); private backup has only file fd.sync at L58; extracted nested directories have no explicit sync.
- GameProcessService.kt host-death patterns linkToDeath|onUnbind|onBindingDied|isBinderAlive -> 0 hits. SessionController.kt has the opposite direction's native Binder death listener at L104; GameActivity and bootstrap cancellation paths were read.
- flock|F_SETLK|F_SETLKW|lockFile|FileLock in hle_fs.c, saves_android.c and SaveManager.kt -> 0 hits; pthread/path locks in hle_fs.c are local to native process.
- In SaveManager.kt, state.new|committed_device|_device -> 0 hits. Native/demo publishers were read rather than treating every file under the folder name as committed.
- HallApp selected/route/dialog/importArchive -> 4 state declarations; importArchive is the only nonsaveable one. CreateDocument|OpenDocument|export.launch|import.launch -> 4 entry/callback hits. import dialog -> 1 branch at L340.
- Existing tests read only: StorageBoundaryTest.kt checks a small traversal set, byte counts, and duplicate JSON. It does not test import transactions, process recreation, controls, cancellation, ZIP expansion limits or commit indexes. No tests executed.
- Corrected early nonexistent guesses nxnative/dm/hle_fs.c and nxnative/dm/hle to the actual nxnative/dm/host/hle_fs.c. Stopped an overly broad tools/ search; all conclusions use the scoped, subsequently completed source searches above.

---

**How the verdict was reached:** all four rules have substantial implementation and real entrypoints, so absent/contradicted would discard working enforcement. They are partial because the failure/lifecycle/decompression paths above defeat explicit clauses while happy-path validation/backup/portrait-return remain present. This is source compliance evidence only; the existing coverage map at docs/hall/requirement-coverage.md:L66 explicitly leaves full acceptance open.

**Open questions:**

- The hall binding still exposes the legacy native raw import/export menu and automatically reads settings-root saves/import.txt before runtime mounts (front_android.c:L1113,L1136-L1138; saves_android.c:L188-L196). That path has no save_schema/game/group/digest/size validator, uses private raw backup folders instead of SAF, removes its request before applying, and swaps user/device saves separately (saves_android.c:L158-L186). Source reachability is established through the menu and build list. The parent spec refuter should decide whether these are allowed internal snapshot helpers or user-facing alternate SAVE-04 operations. If user-facing, the smallest correction is to disable/delegate these operations in DM_HALL_BINDING and use the hall flow; standalone behavior can stay. No untrusted remote injection is claimed.
- hle_fs.c:L503-L512 can use a second committed_device sibling, and saves_android.c:L104-L120 includes it; the hall SaveManager only walks committed. The current registry has 2048/BotW, not the stated dual-mount example. This is a conditional future-runtime contract gap, not a confirmed current dual-save loss. It should be specified/tested before enabling such a slot.
- Actual power-loss outcomes of unsynced backup/staging directory entries were not exercised. The omission is source-visible, but no particular filesystem's observed loss is asserted.
- SAF provider durability/late close errors and actual same-device lifecycle behavior require concentrated verification after the parent finishes the source batch. No external source backup, real save, phone, or generated game data was read.

**Test needs after the complete correction batch (not executed here):**

1. AC-S03/CON-10: fault or terminate at every backup/journal/old-rename/new-rename/index/cleanup boundary, including each write/fsync exception; recover old or verified new according to the specified result, never an empty or mixed tree. Repeat recovery twice, and interpose export, second import, upgrade, App backup and launch before/after recovery. Assert private old ZIP remains usable and index matches the chosen tree.
2. Recreate activity/main while SAF is open, after URI delivery/during copying, on confirmation, during backup and during exchange. Confirm the target game/source/draft is restored or explicitly reselected and no enabled inert Confirm remains.
3. Confirm then Cancel/dismiss at extraction/backup and queued-action boundaries; verify cancel actually prevents publication, or is visibly unavailable during the noncancellable interval. Double Confirm must not enqueue a stale second import.
4. Keep the independent :game alive while main is recreated; verify no import/export can claim idle until re-handshake or controlled native release, and the UI explains ending the game. Separately preserve the normal under-lock launch/import exclusion.
5. CON-03: wrong game/player/group/schema; missing required origin/time/sequence; malformed types/duplicates; control/NUL/DEL/traversal/absolute/UNC/colon/backslash/non-NFC/duplicate paths; symlink/device modes; missing/extra files; bad sizes/hashes. Reject before moving original data.
6. A compressible file larger than the expanded cap with a smaller claimed total, multi-file totals crossing the cap, and integer boundaries: verify byte budget before writes and no staging allocation beyond the cap. Use bounded I/O fixtures/fault injection rather than filling the user's disk.
7. Inject the 2048 state.new short write/fsync/rename interruption, then export while idle: include only committed state.dat, with no synthetic success if no committed state exists. Import and re-export preserves validated source provenance and updates a local commit index exactly once.
8. After permitted real-device acceptance, successful import stays portrait without launching a game, null SAF cancellation never shows success, external export remains usable across a dedicated test uninstall, and actual reload matches exported committed progress. These remain unexecuted acceptance needs.
