> Archived original_candidate_review; reviewed source `189169fded9cd6557b3cb146b56d8b2e5243d705`. Original candidate/pending labels are historical; read the [final dispositions](../../../transfer-install-review-2026-11-07.md).
> 归档核对证据；原始候选标签保留，最终结论与限定见汇总。

## PKG-06 — 可恢复的版本安装提交

> 安装使用不可变版本目录和提交记录。文件完整落盘并完成标记后，才能切换有效版本；任意阶段断电或进程退出，都能恢复为旧版可用或新版本待恢复。
> — `docs/product/data/requirements.json:L1505-L1542`；`docs/product/产品需求细则.md:L642-L655`

**Verdict:** partial · confidence: high. Three source-confirmed divergences are proposed below, pending independent refutation. This is a read-only audit at `189169fded9cd6557b3cb146b56d8b2e5243d705`; it is not execution or device acceptance evidence. All paths in this report are repository-relative.

**What this demands:** bind a fully verified, durable candidate to its manifest; publish its directory on the same volume; activate its database pointer in a short transaction; persist enough ownership and phase information to finish or roll back every interrupted boundary; defer deletion of older usable releases until the health/rollback conditions permit it.

| Rule, verbatim | Verdict | Reason |
|---|---|---|
| PKG-06.01 候选安装经过创建、展开、验证、目录发布、DB 激活和清理的持久提交日志。 | partial | All six phase names are written, but CLEANED precedes download cleanup and the successful recovery path omits cleanup entirely: F03. |
| PKG-06.02 READY 记录绑定清单摘要和校验结果，文件落盘后同卷 rename，再事务更新 active_release。 | partial | File hashes, manifest-bound marker, sibling rename and Room transaction exist; nested/ancestor directory durability is missing: F01. |
| PKG-06.03 文件系统与数据库不构成一个原子事务，重启按日志和目录对账完成或回滚。 | partial | Valid published candidates can be activated after restart, but an invalid candidate poisons initialization with no per-candidate rollback/quarantine path: F02; recovered operations also lose their cleanup obligation: F03. |
| PKG-06.04 旧版本到新版本健康与回退条件成立后才回收，不能凭 UI 显示完成就删唯一可用版。 | implemented (conservative retention) | Installing a different release does not delete prior version directories/rows. There is no automatic installed-version GC. This verdict covers retention, not the separate UPD health/rollback feature. |

**Specifications and acceptance read:** `docs/engineering/04-transfer-install-save.md:L53-L72`, `docs/engineering/05-data-state.md:L25-L61,L97-L111`, READY/journal definitions in `docs/engineering/contracts/local-records.schema.json:L5-L57,L131-L201`, `docs/product/页面与交互验收规格.md:L100-L111,L380-L391`, AC-P05 (`docs/product/data/acceptance.json:L278-L286`), CON-06 (`L685-L695`). AC-U02/AC-U03 (`L457-L475`) were read only to bound the adjacent health/rollback observation.

---

### Where enforcement lives

The actual download-to-install caller is `TransferWorker.perform` (`android/app/src/main/java/org/nsa/hall/transfer/TransferWorker.kt:L99-L154`). It validates the runtime binding at L102, validates auxiliary indexes at L77-L97 and L104-L106, reconstructs/checks every chunk at L112-L143, then checks the whole archive at L145-L147. It acquires `repository.operationLock`, checks runtime idleness and calls `Installation.install` at L148-L150. This is the only application call to the resource installer.

`android/app/src/main/java/org/nsa/hall/transfer/Installation.kt`:

- `atomicJson` L16-L23 writes and synchronizes a sibling `.new` file, renames it and synchronizes the containing directory. Errors propagate; `syncDirectory` does not swallow `Os.fsync` failure.
- `install` L27-L42 derives `content/<game>/versions/<release>` and its sibling staging directory from controlled IDs, creates a journal before extraction and rejects invalid archive entries. `safeChild` only checks the relative/canonical path; it performs no hidden synchronization (`android/app/src/main/java/org/nsa/hall/security/Trust.kt:L53-L66`).
- L43-L65 walks every ZIP entry, checks declared size and SHA-256 after each file's `fd.sync`, verifies the entry set and declared total. L66 records EXTRACTED. L67-L70 copies the **original signed index bytes**, checks their digest and synchronizes the copied file.
- L71-L73 writes a READY marker bound to `_verified_sha256` and file count, synchronizes staging, then records VERIFIED. The manifest transitively binds the signed file index. L74 is the last explicit user/system checkpoint before the publication section.
- L75-L82 backs up an existing save before a cross-release upgrade and checks its declared compatibility. The internal backup callee runs under the already-held operation lock and recovers the save exchange before creating a snapshot (`android/app/src/main/java/org/nsa/hall/transfer/SaveManager.kt:L47-L51,L92-L102`).
- L83-L96 publishes a sibling directory. A different manifest under the same release is rejected at L85. Identical existing payload files cause staging deletion; damaged same-release payloads use `.replaced` and two renames. These paths do not touch a different older release directory.
- `activate` L102-L108 first calls the metadata reconciler, then upserts the install row and the game's active pointer in one Room transaction. The active pointer is `GameRecord.activeRelease`, not a separate SQL table (`android/app/src/main/java/org/nsa/hall/data/Database.kt:L7-L12,L23,L33`). This storage representation still provides a single active value per game and atomic installation/pointer publication on this call path.
- L98-L100 records DB_ACTIVATED, records CLEANED, then deletes/synchronizes the journal. The worker marks the transfer completed and deletes the download root **after** this returns (`TransferWorker.kt:L152-L153`).

The cold-start caller is `HallRepository.initialize` (`android/app/src/main/java/org/nsa/hall/data/HallRepository.kt:L59-L90`). It serializes initialization and installation under the same operation mutex. When the kernel runtime lease is free, it invokes installation recovery, save recovery and unfinished-session reconciliation at L80-L84. It then performs storage reconciliation at L85 and publishes `ready=true` only at L86-L87. A held lease deliberately prevents installation mutation. The worker awaits initialization (`TransferWorker.kt:L33`), and launch preflight requires `ready`, idle runtime, no install/verify task, valid install metadata, manifest signature and exact runtime binding (`HallRepository.kt:L233-L250`). These previous recovery corrections are present.

`StorageReconciliation.installationProblem` (`android/app/src/main/java/org/nsa/hall/transfer/StorageReconciliation.kt:L14-L38`) checks directory/link status, marker release/digest, exact or legacy-canonical signed index bytes, file count, each declared file's existence/length and total. Startup metadata checks intentionally do not rehash entire resource files; ADR 0007 explicitly reserves full hashes for installation/repair. `reconcile` L40-L72 downgrades invalid **registered ready installs** and reconstructs noncompleted download prefixes while retaining pause/cancel intent. It neither repairs a journal rejected earlier by `Installation.recover` nor cleans completed transfer directories.

---

### Paths and interruption boundaries walked

| Durable boundary / alternate path | Restart behavior in the pinned implementation | Assessment |
|---|---|---|
| Before CREATED / while writing journal `.new` | Only `*.json` is scanned; an uncommitted `.new` does not claim activation (`Installation.kt:L111`). No target activation has occurred. | Prior active version preserved; orphan `.new` is not separately collected. |
| CREATED / partially extracted files | Recovery removes staging and the journal (`L128-L130`); transfer reconciliation checks retained archive chunks and requeues/pauses/cancels according to intent (`StorageReconciliation.kt:L44-L71`). | Rolls back candidate work; does not claim ready. |
| EXTRACTED | Same rollback/restart path; it does not continue validation in place. | Re-extraction is conservative and does not change the old active version. |
| VERIFIED, new final directory absent | Removes staging, then resumes/restarts via retained transfer. | Old version or pending transfer preserved. |
| Rename completed, journal still VERIFIED | `target.exists()` enters publication recovery, validates marker and calls activation (`Installation.kt:L122-L125`). | Correct for complete target; F02 for inconsistent target. |
| Same-release repair: target moved to `.replaced`, before new rename | If target absent and `.replaced` exists, recovery chooses a staging directory with a marker or restores `.replaced`, then syncs the parent (`L117-L120`). Subsequent activation checks sizes/index/marker. | Recovery exists; no compensation if the selected candidate later fails validation: F02. |
| DIRECTORY_PUBLISHED / before Room commit | Recovery rechecks marker and metadata, then atomically registers/activates (`L122-L125,L102-L108`). | Valid candidate converges; invalid candidate aborts global initialization: F02. |
| Room commit / before DB_ACTIVATED | Published journal is replayed through the same transaction. | Idempotent active value for the same release; rows get fresh default health/install time. |
| DB_ACTIVATED or CLEANED journal still present | Recovery activates, marks transfer completed, deletes journal (`L122-L130`). | Download archive is not cleaned: F03. |
| Journal durably removed / before worker completion | Registered install is reconciled; the transfer remains incomplete and is eligible to run again. | Safe resource activation, but redundant full extraction can recur. |
| Transfer marked completed / before root deletion or root deletion fails | Completed history is ignored by both worker and reconciler; maintenance only trims session logs. | Download bytes remain indefinitely: F03. |
| File/marker/parent synchronization throws | Installation/worker propagate and classify the failure; no unchecked successful publication claim is made by the failed call. | Existing sync calls correctly fail closed, but missing directory sync calls are F01 and retained journals can enter F02. |
| Pause/cancel before last checkpoint | Intent is persisted; checkpoint can terminate and only transfer-owned data is removed. | Prepublication recovery discards staging. |
| Pause/cancel after last checkpoint | Publication proceeds through journal/Room boundaries; process termination remains recoverable on the valid path. | No arbitrary explicit checkpoint in publication. System coroutine cancellation can still stop at a suspension; the journal is the recovery mechanism. |

---

### F01 — VERIFIED is recorded without synchronizing nested and newly created ancestor directories

**Rules:** PKG-06.02, PKG-06.03. **Severity:** medium / P2. **Confidence:** high for the missing required durability operations; actual loss on a particular Android filesystem is untested.

The extractor creates arbitrary nested parents at `Installation.kt:L53`, synchronizes each **file** at L58, then synchronizes only staging itself at L71-L72. For `RomFS/a/b/data.bin`, neither `RomFS/a/b` nor its intermediate parents receive a directory fsync. `safeChild` is a path checker only (`Trust.kt:L61-L65`). Publication synchronizes `versions` at L95, but new `content/<game>`/`versions` ancestors created by `mkdirs` are not synchronized through their parents. The new `install-journals` directory similarly only synchronizes its own contents, not its new name in `filesDir` (L26, L16-L23). No traversal/callee fills these gaps.

File fsync and top-directory fsync do not establish durability for every nested directory entry. Thus VERIFIED / DIRECTORY_PUBLISHED / active may become durable while some nested names or newly created ancestor links have no corresponding durable barrier. The engineering requirement explicitly demands READY **and directory** durability at VERIFIED and reporting all file/parent-directory synchronization failures (`docs/engineering/04-transfer-install-save.md:L59-L64`). Failure to call fsync for a directory cannot be reported by the existing propagation mechanism.

**Concrete fault exercise, not run:** install a new release containing `RomFS/a/b/data.bin`; retain successful file fsync, staging fsync, publication-parent fsync and journal writes, then cut power after DIRECTORY_PUBLISHED while dropping the nested directory-entry writes that were never covered by a directory fsync. On a filesystem without stronger implicit guarantees, the published candidate can lack a declared file. An existing v1 remains physically present; recovery then enters F02. With a first install, unsynchronized ancestor creation can also strand the final directory or its journal. This identifies the unprotected operations; it does not assert that a particular device reproduced the loss.

**Repair direction:** synchronize the created directory tree bottom-up before VERIFIED, and synchronize newly created ancestor links through their parents. Preserve the journal until those barriers succeed.

---

### F02 — One inconsistent published candidate permanently blocks the whole usable library

**Rules:** PKG-06.03. **Severity:** high / P1. **Confidence:** high.

`Installation.recover` has no per-journal error handling. Its marker requirement at L124 and the metadata requirement in `activate` at L103 throw rather than restore a previous active release or isolate a failed candidate. The throw bypasses `StorageReconciliation.reconcile` (`HallRepository.kt:L81-L85`), leaves `initialized=false` / `ready=false`, and is rethrown at L88. The journal is still present because deletion is later at `Installation.kt:L130`. Every retry repeats the same failing requirement.

The UI renders **only** the restoration page with “重试恢复” while `ready=false` (`android/app/src/main/java/org/nsa/hall/ui/HallApp.kt:L169-L175`). Launch also independently requires ready (`HallRepository.kt:L234`; `android/app/src/main/java/org/nsa/hall/MainActivity.kt:L57-L59`). Consequently, even a valid old active v1 and unrelated valid games cannot be started or repaired through the product. There is no pending candidate with an actionable repair state, nor a rollback path. The journal contains no previous-active identity or revision (`Installation.kt:L32`), contrary to the recovery facts required in `docs/engineering/05-data-state.md:L35,L99-L103`.

**Concrete reproducer, not run:** retain a valid installed v1 as the current active version; prepare v2 and terminate after DIRECTORY_PUBLISHED, before DB activation. In the interrupted state remove `content/<game>/versions/v2/RomFS/.../data.bin` (the missing-key-file acceptance fault in AC-P05), or model the same missing entry via F01. Restart. The READY digest check succeeds if marker/index survived; `installationProblem` returns an error on the missing file (`StorageReconciliation.kt:L33`), `activate` throws, and the app remains at the recovery page. Repeating recovery leaves the same journal and state. v1 was never deleted and is not corrupt, but it is unavailable. Missing/invalid marker cases throw even earlier at L124 with the same global result.

**Why not the previously fixed APP-08 missing-content issue:** the new reconciler correctly marks missing files `repair_required` when the record is already an installed ready row and no rejected journal precedes it. This finding concerns the **earlier journal-recovery path**, which prevents that correction from running at all.

**Repair direction:** validate/recover each journal independently; retain ownership and error state, preserve or restore a verified previous-active pointer, quarantine the incomplete candidate, and allow unrelated/old verified installs to reach the UI. Record enough previous-active/revision information to make the decision reviewable. Merely catching and deleting the journal would lose the recovery obligation.

---

### F03 — Recovered installs permanently retain the complete download, despite CLEANED semantics

**Rules:** PKG-06.01, PKG-06.03. **Severity:** medium / P2. **Confidence:** high.

On the ordinary path, `Installation.kt:L98-L100` writes DB_ACTIVATED then CLEANED and removes the journal **before** the worker deletes the transfer-owned directory at `TransferWorker.kt:L153`. CLEANED therefore does not mean the fact defined at `docs/engineering/04-transfer-install-save.md:L62` (“无引用下载/临时已清”). `deleteRecursively`'s result at L153 is also ignored.

The recovery path is stronger evidence than just that short kill window: `Installation.kt:L125-L130` activates a valid published directory, sets its transfer state to completed, and deletes its journal, with **no** download-root cleanup. Worker selection excludes completed rows (`TransferWorker.kt:L35`), storage reconciliation excludes them too (`StorageReconciliation.kt:L44`), and periodic maintenance only trims session logs (`android/app/src/main/java/org/nsa/hall/transfer/MaintenanceWorker.kt:L14-L20`). The only ordinary deletion of this root is in the skipped worker, or an explicit user cancel command (`HallRepository.kt:L175`). The storage UI's temporary-file cleanup only removes `cacheDir/exports` and `cacheDir/save-import`, not these files (`HallApp.kt:L266`).

**Concrete reproducer, not run:** complete the archive and publish v2; terminate immediately after the DIRECTORY_PUBLISHED journal write. Restart without further faults. Recovery activates v2 and marks the transfer completed, but `transfers/<transfer-id>/archive.zip.partial`, `file_index.json` and `chunk_index.json` remain. A second restart and maintenance execution have no branch that removes them. The complete archive can be many GiB; successful recovered updates accumulate avoidable storage and can prevent subsequent installations. The normal-path kill after L152/before L153, or failed recursive deletion at L153, produces the same terminal state.

**Repair direction:** retain/reconstruct a cleanup obligation until owned unreferenced temporary files are actually removed and the parent-directory deletion is synchronized. Cleanup must be idempotent and retry failures; only then write CLEANED/remove its durable ownership record. Do not make completed transfer history require an archive to exist.

---

### Existing retention, health/rollback interaction, and exclusions

Installing v2 does not enumerate or delete v1; the only `deleteInstall` caller is explicit resource deletion (`HallRepository.kt:L252-L262`). The installer deletes only staging and same-release `.replaced` paths (`Installation.kt:L34,L87-L92,L128-L129`). Explicit user deletion is guarded by idle runtime/lease and active transfer checks and is not automatic update GC. Therefore an early-GC allegation under PKG-06.04 would be false.

Adjacent **UPD-04 scope**, excluded from the three PKG-06 findings: `InstallRecord.healthy` defaults false (`Database.kt:L12`); activation recreates that default (`Installation.kt:L105`); `repair` can only retain an existing true value (`L145`); the complete runtime event handler records interactive/real save/exit events but never sets health true (`android/app/src/main/java/org/nsa/hall/runtime/SessionController.kt:L181-L213`). Manual rollback requires a healthy previous row (`HallRepository.kt:L301-L309`). Hence bytes are retained conservatively, but no normal source path makes a version eligible for this rollback control. First-frame health is **not** incorrectly used to delete versions. This adjacent incompleteness is not reclassified as early GC.

Prior fixes at `e6ee168` through `a1e0f18` were read and accounted for: worker initialization barrier; shared install/recovery mutex; raw signed index preservation and legacy canonical compatibility; startup marker/index/size reconciliation; activation metadata recheck; lifetime runtime lease; and save-swap recovery before backup. None is reported as absent. The historical test `startupReconcilesReadyFilesAndPausedChunksButKeepsCompletedHistory` (`android/app/src/androidTest/java/org/nsa/hall/SaveRecoveryTest.kt:L93-L126`) directly invokes the reconciler without an installation journal, so it does not refute F02/F03. Existing JVM boundary tests exercise `SaveSwap`, not installation publication (`android/app/src/test/java/org/nsa/hall/RecoveryBoundaryTest.kt:L89-L136`). These tests were read, not executed.

### Searched

- `rg --files` for AGENTS/CLAUDE: read parent `AGENTS.md`, repository `AGENTS.md`, `CLAUDE.md`; no additional hall subtree instruction files found.
- `rg -n 'PKG-06' docs/product/data/requirements.json`: statement, all four rules and AC-P05/CON-06 references at L1505-L1542; detailed text matches `产品需求细则.md:L644-L653`.
- `rg -n 'fsync|sync\(|READY|activeRelease|active_release|InstallJournal|installJournal|renameTo|rename\(|previousRelease|previous_release|Health|health|rollback|Rollback' android/app/src/main/java/org/nsa/hall`: located actual installer, worker, reconciler, all pointer/health uses; followed the relevant callers and callees listed above.
- `rg -n 'syncDirectory|fsync|fd\.sync|mkdirs' android/app/src/main/java/org/nsa/hall/transfer/Installation.kt`: 16 matching lines: L17,19,21,23,26,35,53,58,70,72,83,90,91,95,100,120. No nested directory traversal or ancestor-link synchronization.
- `rg -n 'CREATED|EXTRACTED|VERIFIED|DIRECTORY_PUBLISHED|DB_ACTIVATED|CLEANED'` in that file: 7 lines, six writes at L32,66,73,96,98,99 plus recovery selection L122.
- `rg -n 'previous_active|previousActive|activation_revision|activationRevision|record_schema|revision'` in that file: **0 hits**. Journal construction/recovery were read directly at L32 and L110-L131.
- `rg -n 'healthy\s*=\s*true|healthy"\s*:' android/app/src/main/java/org/nsa/hall`: **0 hits**. A broader `healthy` search finds only the default, downgrade/preserve operations and rollback predicate; no successful health transition.
- `rg -n 'Installation\(|\.install\(|\.recover\(|putInstall\(|deleteInstall\(|releaseRoot\(|\.deleteRecursively\(' android/app/src/main/java/org/nsa/hall`: resource installer caller is `TransferWorker:L150`, recovery caller is `HallRepository:L81`; other `install` hits concern AppUpdater. The only `deleteInstall` implementation/caller is DAO L34 / explicit resource deletion L260. Followed normal/failure/cancel/recovery cleanup rather than inferring from phase names.
- `rg -n 'transferRoot|archive.zip.partial|content-transfer|completed|deleteRecursively'` over worker/reconciler/maintenance: 9 lines, all in worker/reconciler; maintenance has no transfer cleanup branch. Read its entire body and the storage cleanup command.

### How the verdict was reached

This is partial, not absent: normal extraction verification, controlled same-volume publication, atomic DB activation, initialization serialization, runtime exclusion and conservative old-version retention are real enforcement. It is not fully implemented because durable journal names do not cover all directory persistence operations, inconsistent journal candidates do not converge, and recovery discards the obligation to remove the completed download. The implementation/specification statuses and device acceptance records were not changed.

### Open questions / unverified

- Actual power-loss behavior and directory-fsync support on the intended Android filesystems remain unverified; F01 records missing barriers and its filesystem-dependent consequence explicitly.
- No process-kill/fault scenario, application test, build, browser, network request, phone/ADB operation, Docker, WSL, VM or emulator was executed. Source/docs were not modified; only this requirement's two ignored output artifacts were created.
- F01–F03 require independent source/specification refutation before being called independently confirmed findings. The author did not substitute self-refutation for that step.
- Cross-cutting stale catalogue upserts of `GameRecord.activeRelease` were noted to the coordinator; they are not included in this bounded installation durability/recovery/cleanup verdict.
