> Archived review record; source snapshot: `cdedc7179ec1842592243a052525e021d8cf70f1`. Initial candidate/pending labels are historical. Final dispositions, including the superseding APP-05-D03 clarification, are in the [consolidated review](../../../source-review.md).
> 归档核对记录；初核中的候选/待复核状态保留历史，最终结论以汇总及独立复核的追加判定为准。

# SAVE-04 independent specification refutation

- Baseline: `cdedc7179ec1842592243a052525e021d8cf70f1` (unchanged main).
- Input: `requirements/SAVE-04.md` and `SAVE-04.json`; this reviewer did not author those findings.
- Mode: authoritative specification/contract/ADR refutation with bounded source corroboration, not a second runtime execution audit.
- Scope: F1–F8, legacy native import/export menu, current two slots versus conditional dual-mount saves.
- Restrictions observed: no product edits, tests, builds, device queries/input, browser, virtualization, or other agents. Only this ignored Markdown report and its JSON companion were added.

## Result

| Claim | Outcome | Retained scope |
| --- | --- | --- |
| F1 exchange failure/recovery | survives | Reported import failure must retain/restore the original; restart must resolve to a complete old or verified new tree, never a fabricated empty tree. |
| F2 main recreation loses live-save exclusion | survives, conditional | A surviving `:game` must remain an owner until reconciled. Ordinary Activity recreation or a package-wide force-stop is not the trigger. |
| F3 inspected import confirmation/retry is lost | survives | Preserve a usable draft/recovery route; exact persistence technology is a choice. |
| F4 visible Cancel does not stop import | survives | An accepted Cancel before publication cannot silently publish later; atomic exchange need not be interruptible midway. |
| F5 expanded cap enforced after writing | survives | Both archive and actual expanded totals are bounded to 2 GiB, independent of claimed metadata. |
| F6 required metadata/control paths omitted | survives | Schema-v1 required fields/types and hard path rules apply to unsigned user backups too. |
| F7 import has no durable index/commit update | narrowed | Index/provenance update is required; copying the imported sequence or creation timestamp verbatim is not required. |
| F8 2048 temporary state exported | survives | Export is a committed snapshot; putting a file under a folder named committed does not make it committed. |
| Legacy menu | narrowed: unified player flow is in scope | Standalone legacy helpers may remain; the player-facing alternate commands compiled into the unified App must obey the current save contract. |
| Dual-mount gap | unverified for current slots | Do not report current 2048/BotW save loss from a conditional future-runtime branch alone. |

These are specification outcomes. Conditional lifecycle interleavings and storage faults remain unexecuted; this report does not claim observed user data loss.

## Authority and exceptions checked

`docs/engineering/README.md:L25` explicitly says:

> 产品/页面定义行为，机器契约固定线上/本地格式和状态，技术章固定实现约束，设计源固定视觉。冲突需同步修订，不选一个旧稿绕过另一规范。

The normative clauses are `docs/product/产品需求细则.md:L960-L963`:

- SAVE-04.01: “导出只读取已提交快照，经系统选择器写到玩家选定位置；取消无副作用。”
- SAVE-04.02: “导入校验格式、游戏、兼容组、大小、清单与每文件摘要，拒绝越界和非法链接。”
- SAVE-04.03: “导入前自动保留当前有效档，离线事务替换后更新索引，失败恢复原档。”
- SAVE-04.04: “游戏运行中不开放覆盖，明确先结束游戏；导入完成不直接启动或旋转。”

The prefix at L954 assigns these behaviors to the save import/export feature, and L956 names S15/S16. They are not limited to one particular Kotlin class or happy-path button.

ADR 0001 (`docs/engineering/decisions/0001-integrated-development-round.md:L5-L7`) changes implementation/testing ordering, not save safety or acceptance: “It does not relax G0 acceptance”. ADR 0004 (`0004-large-content-and-public-demo.md:L3-L5`) makes original 2048 the default and BotW explicit opt-in. ADR 0005 (`0005-client-guardrails-and-cache.md:L11`) affirms the shared mutex and active-group recheck; its L21 says “No HTTP operation, schema identity or ABI version changes.” None waives transaction recovery, required backup fields, or main/game reconciliation. ADRs 0002/0003/0006 concern additive operations, tooling/device capabilities, and credentials; no save-contract override was found.

## F1 — survives: failure compensation and recovery before root creation

**Attempted refutation:** a private old backup still exists, the recovery function runs before native launch, and atomic rename avoids a mixed directory. Does that satisfy the requirement without immediate rollback?

**Normative evidence:**

- `产品需求细则.md:L962`: “失败恢复原档。”
- `页面与交互验收规格.md:L247`: “导入先备份后交换；任意失败回原档；取消不修改目录。”
- `docs/engineering/04-transfer-install-save.md:L80`: “交换阶段保存old/new路径归属与commit；每边界中断重启都能取回原档或已验证新档。恢复前不清唯一旧副本。”
- `docs/engineering/05-data-state.md:L105`: “重启在旧/新完整提交间恢复，不留下半个目录。”

**Why the refutation fails:** backup retention is necessary but does not itself restore the active save after a reported failure. `SaveManager.kt:L120-L125` moves old then new, but finally only removes staging/archive; it has no failure compensation. `L127-L135` restores before-import only if committed is absent, and treats any existing committed directory as sufficient to discard before-import. `HallRepository.kt:L42` creates committed as a side effect, while initialize L48-L60 does not call save recovery. Native launch calls recover only later (`SessionController.kt:L83`). Therefore the reported export-before-launch interleaving can turn “missing committed, valid before-import” into “empty committed exists” without a validating commit identity. An old ZIP remaining elsewhere disproves total loss of all copies, not the active-state/recovery violation.

**Scope limit:** a process interruption is not necessarily an operation that returned failure. The restart rule expressly allows either the complete old tree or a verified new tree. Do not require every crash to restore old unconditionally, and do not claim every exception deletes every old copy.

**Minimum correction:** reconcile an owned save journal under the operation/save lock before any caller creates/reads the active root; record old/new identity and commit, compensate reported failures, and retain rollback until the selected tree and matching index are durable. A completed recovered new commit must be reported coherently instead of an error that silently changed the active save.

**Later verification:** fault each exchange/journal/index boundary, then interpose export, backup, second import and launch before recovery; assert a verified old/new tree and matching index, with idempotent recovery.

## F2 — survives, conditional: main-process reconstruction is not proof of idle

**Attempted refutation:** the mutex, idle check and ADR 0005 already forbid an import while a game runs. Could the contract permit clearing old rows when a new main process starts?

**Normative evidence:**

- `产品需求细则.md:L963`: “游戏运行中不开放覆盖，明确先结束游戏”.
- `docs/engineering/03-runtime-integration.md:L64`: “保存导入使用独占save锁。”
- L66: “持久租约的超时只触发对账，不足以证明native已释放资源；结合进程generation、活连接与文件锁验证。重建主进程发现仍活:game时重新握手或受控恢复，不启动第二份。”
- L74: “主进程也可能被系统回收，恢复时对账session与真实:game”.
- `docs/design/obsidian-portrait/03-screen-specs.md:L550,L562`: running imports prompt exit / are prohibited.

**Why the refutation fails:** the explicit recovery contract rules out equating fresh main-process state with a dead native owner. `HallRepository.kt:L58` marks every unfinished session crashed without checking the real game process; `SaveManager.kt:L20,L86-L87` then uses those rows as idle authority. The shared main-process mutex is a real protection for ordinary launch/import concurrency, but cannot prove that an independent surviving game process released the directory after main reconstruction.

**Scope limit:** conditional on main ending while `:game` survives. A same-process Activity recreation preserves the Application coordinator per ADR 0005 and is not evidence for this defect; package force-stop kills a broader scope. Keeping an Import button visible alone is not proof that a normal running import can overwrite: backend idle currently rejects it.

**Minimum correction:** keep the session/save ownership unresolved until a generation-bound handshake or controlled termination establishes the actual owner state; block save replacement while unresolved and explain ending/recovering the game. No specific interprocess-lock API is mandated, but actual ownership validation is.

**Later verification:** independently recreate main while game survives, then attempt import and export; preserve the passing ordinary mutex exclusion case.

## F3 — survives: restoration/retry requires a usable import context

**Attempted refutation:** ActivityResult launchers are registered unconditionally, selected/route/dialog are saveable, and the player can choose the original file again. Does that eliminate the requirement to retain the parsed Pair?

**Normative evidence:**

- `页面与交互验收规格.md:L233`: “失败保留原档及草稿；长任务展示进度/恢复”.
- S16 L244-L247: facts come from parsed metadata/current commit/compatibility/overwrite scope; confirm/cancel are real commands.
- `docs/design/obsidian-portrait/03-screen-specs.md:L583`: “整个流程可恢复且不删除唯一副本。”
- L589: “校验失败/空间不足/写入中断保留现有存档与恢复入口。”
- `docs/engineering/05-data-state.md:L44`: pending_result keeps request/source/action/game/status, and system file-picker results are recoverable.

**Why the refutation fails:** the contract requires recovery behavior, not merely a restored dialog string. `HallApp.kt:L96,L100,L340` restores dialog but loses the ordinary remember-only archive/metadata Pair; Confirm then returns silently. After an import enters its try block, `SaveManager.kt:L125` removes the archive even on failure while the dialog's captured Pair can remain; another Confirm cannot perform the advertised retry. The original source file normally remains, so the correct impact is a broken draft/retry route, not destruction of the external backup.

**Scope limit:** retaining the Kotlin Pair or permanently storing a raw URI is not mandatory. Persist a small owned context, reconstruct/revalidate it, or explicitly show a recoverable reselect state. No finding is made merely from the absence of SavedStateHandle or on the assumption that every system-picker result is lost before delivery.

**Minimum correction:** restore an operation/game-bound request and usable staged input or an explicit reselect action; keep a retry input until deliberate cancellation/success; avoid a restored enabled inert Confirm.

**Later verification:** recreate after URI delivery and on confirmation; fail extraction/backup and retry; verify source/game remain bound and the recovery action works.

## F4 — survives: displayed Cancel must govern the pending operation

**Attempted refutation:** SAVE-04.01's cancellation clause names export/system selection. Could S16's Cancel mean only closing a confirmation, or must an atomic exchange be interruptible?

**Normative evidence:**

- S16 `页面与交互验收规格.md:L245,L247` pairs “confirmBackupThenImport；cancel” with “取消不修改目录”.
- INT-03 at L469 requires “文件选择、解析、预检、备份、交换、成功、失败分别有状态”.
- `docs/design/obsidian-portrait/03-screen-specs.md:L581,L593` includes explicit Cancel and truthful phase/recovery.
- The installation critical-section rule `docs/engineering/04-transfer-install-save.md:L64` says “提交最短临界区不任意取消”. It is in installation §5, so it is corroborating transaction guidance, not a separate import API requirement.

**Why the refutation fails:** this is an accepted visible Cancel before publication, not a demand for interrupting the old/new rename halfway. `HallApp.kt:L340` leaves Cancel and dismiss enabled during model.perform; they delete only the archive and UI context. At `SaveManager.kt:L117` the archive has already been extracted/closed, so cancellation during the old-save backup can no longer stop L119-L124 from swapping the save. The UI can indicate cancellation while the imported state becomes active afterward.

**Scope limit:** the spec permits a brief protected publication interval implemented with truthful state. It does not require deleting a current save midway or make cancellation at every machine instruction safe. Null SAF cancellation already avoids the import/export action and is not this finding.

**Minimum correction:** own the operation and prevent duplicate confirmation; honor cancellation up to an explicit safe boundary, then disable/replace Cancel and dismiss with truthful finishing/recovery state during the protected exchange. Merely hiding the dialog cannot be called cancellation while work still publishes.

**Later verification:** Cancel during extraction and old-backup work must prevent later publication; during protected exchange show a clear unavailable Cancel/finishing state and coherent durable result.

## F5 — survives: reject expanded oversize before consuming that output

**Attempted refutation:** the input archive is capped, each file is bounded by its stated size, and eventual total validation rejects the archive. Is post-write rejection enough?

**Normative evidence:**

- SAVE-04.02 (`产品需求细则.md:L961`) explicitly includes size/manifest/file validation.
- `docs/engineering/04-transfer-install-save.md:L78`: “上限默认2GiB归档，逐文件累计及展开总量同时受限；不得因压缩率突破上限。”
- `docs/engineering/contracts/README.md:L35`: “保存包归档及展开各≤2 GiB、文件≤10000，全部文件摘要/总量匹配”.
- CON-03 (`开发计划与验收矩阵.md:L249`) requires rejection of “超展开” while preserving source data.

**Why the refutation fails:** the aggregate cap constrains actual expanded output, not only the metadata total or accepted final save. `SaveManager.kt:L82` checks the claimed total; L104-L109 writes a file up to its declared size, and only L113 applies the global cap after that file was written/fsynced/hashed. A very compressible entry whose own declared size exceeds the cap can therefore consume more than the allowed expanded space before rejection. A malformed input is precisely what this boundary is intended to constrain.

**Scope limit:** archive-copy cap and per-entry exact-size/hash validation are working defenses. Do not claim oversize input necessarily becomes active or that the original is deliberately moved before extraction validation.

**Minimum correction:** preflight checked per-file sums against the same cap and metadata total, then enforce an independent remaining actual expanded budget before every write; include storage-plan failure before replacement.

**Later verification:** bounded/fault-injected compressed single-file and multi-file cap crossings; never fill the user's disk.

## F6 — survives: strict JSON syntax is not the save-schema contract

**Attempted refutation:** only game/group/files matter to safety, backup metadata is unsigned, and API responses can be extensible. Could ignored metadata or control-character paths be tolerated?

**Normative evidence:**

- `docs/engineering/contracts/save-backup.schema.json:L59-L70` requires source_release_id, commit_seq and created_at as well as the tested identity/size/files fields, with additionalProperties=false.
- L15-L26: source is an Id; commit_seq is an integer minimum 0; created_at is UtcTime.
- L48-L56: every file needs path/bytes/sha256, additionalProperties=false, 1–10000 entries.
- `domain.schema.json:L5-L21`: Id is lowercase bounded text, Sha256 is exactly 64 lowercase hex, ByteCount is checked unsigned decimal text, UtcTime is date-time ending Z.
- `docs/engineering/contracts/README.md:L30`: “拒绝绝对/盘符/UNC/空/点/上级/反斜线/控制字符、链接与归一化冲突”.
- CON-03 explicitly maps the control-path/expansion boundary to SAVE-04 (`开发计划与验收矩阵.md:L249`).
- S16 (`页面与交互验收规格.md:L246`): “备份包无可信平台签名也须做结构/大小/摘要与归属校验”.

**Why the refutation fails:** unsigned does not mean schema-free. `SaveManager.kt:L78-L82,L90-L91,L98-L116` never reads or validates those required origin/time/sequence fields, so their absence does not prevent publication. `Trust.kt:L37-L44` validates containment, NFC and several path forms but does not reject controls; a matching indexed/ZIP filename containing U+000A remains legal to Android's ordinary filesystem and passes these predicates. JSON duplicate-key checking alone does not implement required types/properties.

**Scope limit:** do not impose platform signatures on user backups; do not reject a compatible older source release just because it differs from active release. A source ID need be syntactically valid provenance, not authorization to load content. The “responses extensible” note in contracts README L10 does not override this specific save-backup additionalProperties=false schema. Any future schema extension requires explicit version/contract treatment.

**Minimum correction:** validate the full supported v1 object/file schema and hard path rules before confirmation or staging; retain validated origin metadata for registration. Reuse a single control-character/path rule across archives.

**Later verification:** missing required fields, malformed types/time/sequence/IDs, supported-property policy, controls/NUL/DEL, normal valid unsigned backups and known traversal/link refusals.

## F7 — narrowed: an import index update is required, exact archive values are not

**Attempted refutation:** perhaps importing files needs no native save event, and a local monotonic sequence should not be lowered by an older external backup. Also, a newly exported archive can legitimately have a new creation timestamp.

**Normative evidence:**

- SAVE-04.03 (`产品需求细则.md:L962`) explicitly requires “离线事务替换后更新索引”.
- SAVE-01.01 at L912: “发行版本为来源元数据”; SAVE-01.04 at L915: indexes/recent save use committed markers.
- `docs/engineering/04-transfer-install-save.md:L74`: “提交真相是native保存事务/磁盘标记。保存身份player+game+compatibility_group，commit_seq、来源release、摘要、大小与提交时间有记录。”
- `docs/engineering/05-data-state.md:L40-L41` defines save_commit and save_backup records; L105 says authority comes from “原生提交与经过验证的备份”.
- `local-records.schema.json:L256-L302` fixes SaveCommit's game/player/source/group/commit_seq/bytes/tree digest/committed_at fields.

**What survives:** `SaveManager.kt:L86-L135` changes directories/journal but records no durable new save/index/verified-import identity. Export L37 reads the previous save-seq fact and active release with a new timestamp; only a later native event writes that fact (`SessionController.kt:L151-L156`). Thus changed active bytes are not reconciled with an authoritative index and provenance. A verified backup is already a permitted source of committed save authority: importing it must not wait for an invented game-save event or a game launch.

**What is narrowed/refuted:** the example “local 12, imported 3, export 12” is not by itself proof of a wrong sequence. No cited clause demands replacing local 12 with archive 3, preserving an old archive's created_at verbatim, or pretending a new native save occurred. Archive created_at and local committed_at are distinct named fields. Local monotonic revision and imported origin sequence may be recorded separately; a fresh export may have a fresh archive creation time. The decisive defect is missing coherent durable index/provenance, not timestamp inequality. The data-state table's “只有原生提交完成后追加” cannot be read to forbid verified-backup registration because the same chapter L105 and explicit SAVE-04.03 require it; do not fabricate a native event to paper over this wording.

**Minimum correction:** atomically register/recover the verified import and corresponding bytes/digest/group/source history as part of journal completion. Choose a defined local monotonic revision with separate imported origin; do not blindly lower or reuse a source sequence as if it were a fresh native event. Export factual metadata from that committed record, with clearly defined archive creation time.

**Later verification:** import an older compatible backup, export before native launch, recover twice; selected bytes and index must agree, provenance remain explainable, local revision never accidentally regress, and no fake native-save event appear.

## F8 — survives: the original 2048 slot is inside current scope

**Attempted refutation:** 2048 is a public demonstration, and state.new is under context.save_root. Could a root directory name alone make every contained file a committed snapshot?

**Normative evidence:**

- SAVE-04.01 (`产品需求细则.md:L960`) says “只读取已提交快照”.
- `docs/engineering/04-transfer-install-save.md:L74,L76`: commit truth comes from native transaction/disk markers; export cannot read save.work.
- `docs/engineering/05-data-state.md:L40`: “目录存在不等于已提交”.
- ADR 0004 L3-L5 selects original 2048 as the default build; it does not exempt saves. ADR 0001 L5 preserves actual save/reload acceptance.
- `docs/hall/device-validation.md:L44,L53` explicitly identifies current A=2048, B=BotW, and distinguishes them from a two-Switch-title claim.

**Why the refutation fails:** `android/native-games/2048/game.c:L48-L57` uses state.new before successful rename to state.dat and before save_committed; startup L155-L157 only consumes state.dat. `SaveManager.kt:L27-L41` exports all files, without a commit-file index or filtering of this owned staging path. A failed/interrupted write can leave uncommitted state.new for the next idle export. The predicate “under save_root” is not the required “committed”.

**Scope limit:** this is specific to 2048's owned staging name and current exporter. Switch HLE's sibling work/new trees are not evidence that this file is excluded, nor should the fix exclude every game's arbitrary .new suffix. No claim that every save/export contains the temporary file.

**Minimum correction:** put the demo's owned staging outside the committed tree or export by authoritative committed-file index; publish only verified committed state and retain the previous commit on failure.

**Later verification:** interrupt/fail 2048 write/fsync/rename, then export while idle; state.new is absent and no archive is advertised when no committed state exists.

## Legacy native menu scope — unified user-facing operations are in scope

**Established reachability (source, not executed):**

- `nxnative/dm/android/build.py:L71-L83` includes android/*.c and defines DM_HALL_BINDING for a hall identity.
- `hall_adapter.c:L108-L122` supplies hall settings/save paths and calls dm_front_hall_on_create.
- `overlay_layout.h:L110-L117` places OVM_EXPORT/OVM_IMPORT in the Game tab; `front_android.c:L433-L434,L849,L856-L868` reaches them from touch/controller/native input.
- `front_android.c:L483-L501,L580-L586` describes and presents player commands, including “Import this save?” and “Import and close”; these are not hidden implementation-only functions.
- Native startup `front_android.c:L1113,L1136-L1137` calls dm_saves_start before mounts. `saves_android.c:L188-L196` consumes a request and applies it.
- `saves_android.c:L101-L126,L150-L186` provides private raw copies and deferred raw import, not the user save-schema/SAF pipeline.

**Specification decision:** narrow the scope by build and purpose, not by language. The unified player-facing native “Export/Import” operations manage the same explicitly provided hall save context; SAVE-04 and APP-04.03 apply. `产品需求细则.md:L83` requires “导入/导出调用系统文件选择器”; L960-L963 require the committed/validated/transactional behavior. `docs/engineering/01-architecture.md:L14` explicitly says the reusable native save code must “扩展按游戏身份隔离，接入提交事件与安全导入”. There is no ADR waiver for alternate raw native commands inside the unified App.

The source shows a definite format/SAF/index scope gap for those exposed commands. Nevertheless, **do not** repackage every raw-copy helper as forbidden: private automatic/manual backup snapshots are allowed (`产品需求细则.md:L1025`, `04-transfer-install-save.md:L84`, S15 history at `03-screen-specs.md:L548`). A clearly named internal local-backup helper can remain distinct from user export, provided it preserves the same ownership/commit safety. Standalone legacy App code is outside the unified App's UI contract; SAVE-08.04 (`产品需求细则.md:L1027`) expressly permits old-App export migration and does not demand rewriting the standalone frontend here.

**Counterclaim rejected:** native deferred import does not overwrite during the still-running old game: `front_android.c:L483-L486` defers until next start before mounts. Do not report it as direct live-write solely because the player selects the menu during a session. Its remaining failures are bypassing current validation/transaction/index/SAF expectations, not a demonstrated remote injection.

**Minimum correction:** under DM_HALL_BINDING, disable or delegate native user import/export and deferred raw import consumption to the hall save flow; leave standalone behavior and non-player internal snapshot helpers intact if needed. If keeping an in-game private snapshot command, label it as local backup, avoid pretending it protects against uninstall, and give imports the same validated transaction owner. Do not expand this into an unsolicited standalone rewrite.

## Dual-mount scope — conditional, not an eighth/ninth current-loss finding

`nxnative/dm/host/hle_fs.c:L501-L512` selects a second DM_SAVE_device transaction only if a title imports both account/device mount functions; comments L503-L505 name Mario Kart 8 Deluxe as the on-hand both-mount example. The hall exporter walks only committed (`SaveManager.kt:L27-L41`), whereas the old native snapshot also copies its device sibling (`saves_android.c:L104-L120`).

Current-build evidence is original 2048 default / explicit two-game BotW profile (ADR 0004 L3-L5; `docs/hall/device-validation.md:L44,L53`). The current **ignored generated local** `android/app/src/main/assets/runtime-registry.json:L1` lists those two slots; it is not a committed binary-verification record. These sources do not establish either current slot creates a committed_device tree. No device or real save data was read.

**Outcome:** unverified for the current two slots; no confirmed current save-loss finding. For a future enabled dual-mount title, SAVE-04 still requires the whole logical committed snapshot and atomic/index-consistent import, so a manifest/contract/runtime adapter must declare all save roots before that title is accepted. No special new exemption or universal multi-root support is currently documented. Treat multi-root design/verification as a prerequisite when enabling such a slot, not evidence that present 2048/BotW already lost a device save.

## Search/evidence record and unexecuted follow-up

- Read the input finding report and its JSON, the four SAVE-04 subrules, neighboring SAVE-01/02/03/05/08, S15/S16/INT-03, AC-S03 and CON-03.
- Read engineering authority/architecture/runtime/save/data chapters, contract README, save-backup/local-records/domain schemas, applicable ADRs 0001–0006 (save/schema overrides searched), current implementation/device scope notes, and original demo/native save publishers.
- Searches used SAVE-04, S15/S16, 导入/导出/取消/草稿/原档/pending_result, commit/index/schema, DM_HALL_BINDING, native menu/request paths, 双挂载/dual.mount/committed_device/_device and current slots.
- Corroborated SaveManager entry/cancel/exchange/recover/index behavior, HallApp result/dialog state, MainActivity exception path, HallRepository initialization/saveRoot, SessionController recovery/event registration, native binding/menu/save helpers and HLE mount selection.
- Corrected PowerShell-invalid wildcard path arguments by searching actual directories with rg globs. Corrected guessed missing StorageBoundary.kt to Trust.kt, missing porter/profiles to the actual source evidence, and missing ADR name to 0001-integrated-development-round.md. No conclusion rests on a failed search.
- Output Markdown/JSON paths are ignored by .gitignore:L44. Baseline HEAD verified; tracked status was clean at the initial check. At final artifact validation the parent was assembling README/docs/hall handoff edits and adding docs/hall/source-review.md; this reviewer made no such edits. Product source/HEAD remained cdedc71. Both refutation JSON files parsed successfully; this is artifact validation only.
- Follow-up recommendations above are for the parent to consolidate **after** the full planned correction batch (ADR 0001). Nothing was tested/built here; no implementation acceptance was upgraded.
