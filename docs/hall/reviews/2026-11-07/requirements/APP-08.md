> Archived review record; source snapshot: `cdedc7179ec1842592243a052525e021d8cf70f1`. Initial candidate/pending labels are historical. Final dispositions, including the superseding APP-05-D03 clarification, are in the [consolidated review](../../../source-review.md).
> 归档核对记录；初核中的候选/待复核状态保留历史，最终结论以汇总及独立复核的追加判定为准。

## APP-08 — Restore the actual page, task and runtime state

> "前后台切换、来电和系统重建后恢复最近页面与实际任务状态；不因重开页面重复创建下载或错误宣称游戏仍在运行。"
> — docs/product/产品需求细则.md:L139, APP-08 (P0)

**Verdict:** partial · confidence: high (source audit only)

Revision: cdedc7179ec1842592243a052525e021d8cf70f1, main. Normative subrules: docs/product/产品需求细则.md:L145-L148. Supporting page/engineering rules are cited below. Read AGENTS.md, CLAUDE.md, product/engineering READMEs, and applicable nxnative/AGENTS.md. ADR 0001 requires the complete planned implementation batch before consolidated verification.

Only these two ignored audit files are written. No product edits, test/build execution, phone reads/input, browser, virtualization, or further agents. Runtime acceptance remains open. Findings below are source-supported candidates pending the root coordinator's independent code/specification refutation.

| Subrule | Verdict | Reason |
| --- | --- | --- |
| APP-08.01 | partial | Saved navigation exists; committed search context/results and reconciliation-before-render do not hold. |
| APP-08.02 | partial | Durable rows/checkpoints/journals exist; paused/offline/file-missing reconciliation and serialized startup recovery do not hold. |
| APP-08.03 | partial | Normal handshake/death handling exists; main-process reconstruction blindly terminalizes possibly live sessions. |
| APP-08.04 | implemented | Lifecycle entrypoints do not create work; shared coordination and service/activity/native guards prevent repeated start. This is a source verdict, not device acceptance. |

---

## APP-08.01 — Restore navigation and committed query before content

> "保存根标签、路由、游戏选择、滚动锚点和已提交查询；重建后先读取本地事实再渲染。"
> — docs/product/产品需求细则.md:L145

**Verdict:** partial · confidence: high

**What this demands of an implementation:** Preserve the root tab, route, chosen game, scroll context and the actual submitted query, then restore the corresponding facts before rendering restored content. Supporting constraints: docs/engineering/02-android-client.md:L44 (query/filter and item key/offset), L66 (bounded reconciliation before real library/tasks); docs/engineering/05-data-state.md:L101 (publish UI snapshot last); docs/product/页面与交互验收规格.md:L9 (facts and source restoration).

**Where enforcement lives:**

- HallApp.kt:L85-L101 saves tab, route, stack, selected game, query/filter and modal IDs with rememberSaveable. SaveableStateProvider wraps each page at L148; ScrollPage uses rememberScrollState at L347. LazyColumn appears at L150/L165/L373, with stable object keys in discover/download rows at L159/L376. This is real saved-state machinery, not an absent implementation.
- MainActivity.kt:L32-L38 exposes DAO/DataStore streams independently and performs initialize followed by refresh() with default empty arguments for every new ViewModel.
- HallRepository.kt:L63-L81 adds q/genre only when supplied; a non-pagination refresh replaces the shared catalogue. Only blank query/genre is cached persistently at L80.
- HallApp.kt:L121-L124 reloads restored detail routes only. The search page at L191-L198 uses the shared catalogue as-is, with no replay of the saved search and no result filtering by query.
- MainActivity.kt:L69 installs HallApp without awaiting initialize. HallApp.kt:L147-L148 renders based on welcomed rather than a reconciliation-ready snapshot; working merely shows a header spinner at L145.

**Paths walked:**

- Same-task Activity recreation -> saved tab/route/game/query -> page holder: navigation fields are supported.
- Submitted search -> system saves task/reclaims hall -> new ViewModel -> initialize -> unfiltered refresh -> restored search page: the old query text is combined with the unfiltered catalogue. **APP-08-F01.**
- Restored DAO rows -> HallApp library/tasks while initialize/recovery runs separately: no content publication gate. This is also part of the file-fact mismatch in **APP-08-F02**.

**Searched:** JSON search records S01-S04. Production initialize has exactly one caller, MainActivity.kt:L38. committedQuery/submittedQuery/lastQuery/searchQuery/catalogue_snapshot have 0 hits. returnOrigin/return_origin/source_route/onSaveInstanceState/SavedStateHandle/pending_result have 0 hits in main Kotlin/XML. Explicit rememberLazyListState/firstVisibleItem/scrollAnchor have 0 hits, but default LazyColumn state is not treated as evidence that all scroll restoration is absent.

**How the verdict was reached:** partial because field restoration is implemented, but a concrete restored search binds to the wrong data source. No assumption about a function name or an unexecuted screen is needed.

**APP-08-F01 · P2 / medium — Restored search runs an unfiltered request.**

Trigger: submit a nonempty search, save the task/background it, reclaim only its process, and reopen with connectivity. HallApp.kt:L90 restores the field, while MainActivity.kt:L38 calls refresh() and HallRepository.kt:L68/L77 requests/replaces the unfiltered page. HallApp.kt:L197 renders those results without matching the displayed query. An unsent draft also cannot be distinguished from the committed query because only one query variable exists (HallApp.kt:L90/L193).

Minimal correction: preserve a committed search context separately from draft input, restore the corresponding cache/results and refresh using that context after local initialization. Test need: process reconstruction with a nonempty committed query plus a changed draft; check request context and rendered game IDs. Existing NavigationRecoveryTest.kt:L17-L29 only recreates Settings and checks controller identity.

**Open questions:** No device or framework restoration execution. Item-key behavior under list insertion/reordering remains to be checked; no extra scroll absence finding is asserted.

---

## APP-08.02 — Restore durable facts and reconcile files

> "任务和安装状态来自持久记录及文件对账，不能仅恢复上一次屏幕上的百分比。"
> — docs/product/产品需求细则.md:L146

**Verdict:** partial · confidence: high

**What this demands of an implementation:** Recover truthful task bytes/phases and installation readiness from both persistent records and disk, including paused/failed/offline tasks, and prevent recovery from interfering with a live operation. Supporting rule: docs/engineering/05-data-state.md:L101 explicitly orders journal/session/READY/task reconciliation before publishing UI. Shared lock ordering and live-resource ownership: docs/engineering/03-runtime-integration.md:L66 and docs/engineering/05-data-state.md:L111.

**Where enforcement lives:**

- Database.kt:L9-L14 stores TransferRecord byte/state and InstallRecord/SessionRecord in Room. DAO transfer streams are at L24-L27.
- TransferWorker.kt:L114-L138 hashes available existing chunks; new data is verified then written and fsynced at L131-L133 before progress is committed at L138. The final archive hash is checked at L145.
- Installation.kt:L15-L22 writes/fsyncs atomic JSON and syncs directories. L65-L96 persists install phases around READY, directory publication and activation. L98-L103 activates the install/game together in a DB transaction.
- Startup HallRepository.kt:L48-L60 only loads policy/catalogue, trims logs, scans install journals and resets sessions. It does not enumerate persisted task files or active installs for normal reconciliation.
- Installation.recover at L105-L126 iterates existing journal JSON only. If a completed install's journal was already removed at L95-L96, its READY/active metadata is never checked here.
- TransferWorker.doWork at L31-L44 excludes paused/failed/completed work; its network-constrained scheduler is HallRepository.kt:L126-L128. Paused/offline task files therefore cannot be reconciled by relying on this worker.
- TransferCard uses the database row directly at HallApp.kt:L402-L405. Installed cards/Continue use activeRelease at L164/L212/L364; no startup validity projection intervenes. Launch preflight checks only READY existence at HallRepository.kt:L165 and does not correct displayed state.
- initialize uses initializationLock at HallRepository.kt:L49 but not operationLock. The worker owns operationLock for installation at TransferWorker.kt:L146-L149, enters doWork without initialize, and recovery deletes an early-phase staging tree at Installation.kt:L123.

**Paths walked:**

- Process restart -> DAO transfer row -> HallApp.Downloads -> TransferCard: durable facts exist, but skipped file reconciliation can leave false progress.
- Process restart -> initialize -> recover -> journal scan -> activate late phase / delete early staging: journal recovery exists, but no-journal active installations escape it.
- WorkManager first starts a new app process -> doWork -> perform -> locked install; first MainActivity then calls initialize -> unlocked recover: recovery can operate on the current worker's journal and staging tree.

**Searched:** JSON S01/S05-S07. All allTransfers/putTransfer/putInstall/activeRelease/archive.zip.partial/.ready.json hits and their callers were read. install-journals/.staging/DIRECTORY_PUBLISHED show one install/recover pair sharing the same files. MaintenanceWorker.kt:L13-L18 only trims logs. initialize has one caller in HallViewModel; the worker never calls it.

**How the verdict was reached:** partial because real durable chunk and installation recovery exists, with identifiable paths where no reconciliation occurs and a separate active-operation race.

**APP-08-F02 · P2 / medium — Persisted progress/readiness bypasses file reconciliation.**

Trigger A: a paused/failed/offline transfer has stored bytes but its archive is missing/truncated. The initialized UI still shows the stored bytes; paused work never reaches the hashing loop. Trigger B: active install metadata exists but package/READY files are missing after its journal was cleaned. Journal-only recovery does nothing and installed/Continue remains visible (HallApp.kt:L164/L212/L364). These are concrete recovery-state inputs, not a claim that normal fsync loses bytes.

Minimal correction: reconcile every task's local durable checkpoint and every active install's bounded metadata/critical-file facts, preserve manual pause, downgrade missing state to recoverable/repair, and publish content only after this pass. Full content hashing can remain an explicit repair operation, as the spec allows.

Test need: offline/paused missing/truncated archive; no-journal installed package with absent READY/critical files; assert the first published content snapshot and retained pause intent. Tests are not run.

**APP-08-F04 · P1 / high — Startup recovery can delete active extraction.**

Concrete interleaving:

1. WorkManager starts the process before any Activity; TransferWorker.kt:L31-L37 runs without initialize. L146-L149 holds operationLock during Installation.install.
2. Installation.kt:L31-L35 creates a CREATED journal and staging. It extracts, hashes and counts file A at L52-L60. The journal stays CREATED until all extraction finishes at L65.
3. The user opens the hall. MainActivity.kt:L38 -> HallRepository.initialize, which owns only initializationLock and invokes recover at L57.
4. recover reads that CREATED journal at Installation.kt:L107-L108, takes the early-phase branch at L123 and deletes the active staging directory, then removes the journal at L125.
5. The worker can fail on a removed file. A stronger outcome is also allowed by these exact operations: if A had already passed its hash check, creating B's parent at L52 recreates the staging root, and the in-memory total/seen still counts A (L59-L64). No final per-file existence pass occurs before writing READY/index and activating at L65-L103. The package can be marked ready with A missing.

Minimal correction: one shared initialization barrier for worker/UI entry, and serialize recovery with the same operation ownership lock. Do not classify a currently owned journal as abandoned.

Test need: a deterministic two-file extraction barrier after A's validation; invoke startup recovery, resume B, and check that active staging cannot be deleted and no missing-file install becomes ready. No test or phone interruption is performed in this audit.

**Open questions:** Runtime scheduler timing/interruption is unexecuted. The missing lock and exact destructive interleaving are source-visible; independent reviewers must still refute the path and requirement interpretation.

---

## APP-08.03 — Reconcile the live process and handshake

> "运行会话只有活进程及有效会话握手才能标记运行中；失联会话转恢复状态。"
> — docs/product/产品需求细则.md:L147

**Verdict:** partial · confidence: high

**What this demands of an implementation:** A restored record cannot prove either a running or a dead runtime. Reconcile the real process/Binder and valid session identity; reattach a still-live process or retain controlled recovery/resource restrictions until death is confirmed. Supporting text: docs/engineering/03-runtime-integration.md:L66/L74 and docs/engineering/05-data-state.md:L101. S08/S12 sources and restore behavior: docs/product/页面与交互验收规格.md:L132-L135/L188-L191.

**Where enforcement lives:**

- SessionController.kt:L64-L72 creates a persistent generation-bound launch record under operationLock. PREPARE goes through the bound service at L100-L109.
- PREPARED checks session ID, generation, nonce and a positive different PID at L130-L137 before GameActivity starts. Runtime events check schema/ID/generation/sequence/phase at L139-L160.
- GameActivity.kt:L41-L61 rejects unauthenticated/consumed launches before native loading. Bootstrap.c:L141-L171 invokes the registered module; hall_adapter.c:L102-L128 supplies identity-bound real phase events.
- Host-alive death paths use linkToDeath/onServiceDisconnected at SessionController.kt:L104-L107 and finish at L164-L181. finish writes terminal state and resumes scheduling.
- In a new main process, SessionController.kt:L24-L36 starts empty. Its attach at L122-L128 only replaces an Activity reference, with no persisted-session/Binder restoration.
- HallRepository.kt:L58 rewrites every unfinished record to crashed with the assertion that the previous process has ended, without a live-process/controlled-Binder check. L59 immediately schedules downloads.
- GameProcessService.kt:L15-L16 swallows failed sends to the old host; L24-L29 refuses PREPARE if a context is already present. No host-death recipient or reattach command exists. onDestroy at L41 only removes handler callbacks; it does not end the GameActivity/native process.
- Resource/save guards use DAO unfinished rows (TransferWorker.kt:L67/L147; HallRepository.kt:L170/L181/L196/L201; SaveManager.kt:L20). They cannot compensate for premature terminalization.
- The UI recovery notice uses controller.message at HallApp.kt:L325-L327. Reading latestSession does not recreate that message from persisted crashed.error.

**Paths walked:**

- Normal launch -> nonce/generation PREPARED -> authenticated GameActivity -> bootstrap/module events -> monotone persisted phases: implemented.
- Runtime dies while host lives -> Binder callback -> finish(crashed) -> message and terminal record: implemented.
- Main process dies while :game survives -> new controller -> blanket DB crashed -> policy/resource/save operations observe idle: divergent.
- User retry after that -> new validating record -> PREPARE on surviving singleton -> ignored without new handshake: recovery cannot reattach. This does **not** establish a second native game.

**Searched:** JSON S08-S10. All putSession callers were traced: creation/event/finish/startup reset. linkToDeath has exactly one hit, SessionController.kt:L104 (host observing game). reconnect/rehandshake/process_identity/runningAppProcesses/isBinderAlive/pingBinder and other named death handlers have 0 hits. Entire controller/service/activity were read, plus native bootstrap/adapter and frontend lifecycle branches. The native monitor only watches save commits and local cancellation (hall_adapter.c:L87-L94), not main-process liveness.

**How the verdict was reached:** partial because normal handshake and host-alive crash handling work in source, but no live reconciliation occurs when the main process itself is rebuilt.

**APP-08-F03 · P1 / high — Reconstruction releases a possibly live game's pin.**

Trigger: reclaim only the hall/main process while the ordinary :game process with GameActivity survives; reopen the hall. It marks the old record crashed, allows download/checkpoint work and same-game destructive/save actions, and cannot rebind to the old context. The live game's resources can therefore be changed while it is still using them. This is the branch explicitly covered by engineering §5/§6; it is not an assumption that all Android process kills leave the game alive.

Minimal correction: retain a recovering/pinned session, probe the existing process without creating a new runtime, authenticate the original session/generation and reattach or explicitly end it. Only confirmed process death releases policy/resource locks. Restore a recovery UI from persistent facts, not a new empty message flow.

Test need: a controlled main-process-only interruption with :game verified alive; assert the pin/blocker remains and destructive operations stay blocked until valid reattach or controlled termination. Package force-stop kills the wrong scope and is not this test. Also test dead runtime/old IPC. All physical actions are deferred.

**Open questions:** Heartbeat expiry at SessionController.kt:L110-L116 changes the message but not the persisted phase. Whether that UI alone fulfills recoverable-timeout semantics is reserved for specification refutation; it is not a fifth finding.

---

## APP-08.04 — Lifecycle replay must not create duplicate work

> "旋转、来电、锁屏及再次点击图标都不能重复建立下载或第二个游戏会话。"
> — docs/product/产品需求细则.md:L148

**Verdict:** implemented · confidence: medium (source only)

**What this demands of an implementation:** Rotation, call/lock interruption and reopening must reconnect/re-render without enqueueing another content task or starting native execution again. This is distinct from the safe recovery defect in APP-08-F03.

**Where enforcement lives:**

- HallApplication.kt:L12-L15 owns one repository and SessionController across Activity/ViewModel recreation. MainActivity.kt:L38 initializes/refreshes; L71 only attaches on resume. Neither lifecycle method enqueues content or launches a game.
- HallApp.kt:L338 is the sole enqueue caller. HallRepository.kt:L104-L117 owns the shared operationLock and checks existing nonterminal transfers before creating a task. Unique content-transfer work uses KEEP at L126-L128.
- HallApp.kt:L119 -> HallViewModel.launch (MainActivity.kt:L41-L43) is the launch caller. SessionController.kt:L50-L60 checks launching/finishing/current session synchronously. beginLaunch creates its record only after locked preflight verifies no unfinished session (HallRepository.kt:L148-L150; SessionController.kt:L64-L72).
- AndroidManifest.xml:L15-L20 places the activity/service in one :game process, uses singleTask for the runtime and handles common configuration changes.
- GameProcessService.kt:L25 refuses another prepare for a used context; GameActivity.kt:L43-L48 rejects another onCreate for consumed identity before native initialization. hall_adapter.c:L105 atomically rejects a repeated module start. front_android.c:L1112 guards its own frontend start.
- Call/audio/lock paths use GameActivity.kt:L72-L98 pause/resume commands. Those reach bootstrap.c:L88-L91 -> hall_adapter.c:L130-L140 -> front_android.c:L150-L154, which pause/resume/end rather than start or enqueue.
- SessionController.kt:L144-L150/L164-L165 binds delayed callbacks to the current original identity, so ending a previous attempt does not create a new one.

**Paths walked:**

- Rotation/recreated MainActivity -> same Application controller -> attach -> optional prepared Activity dispatch, not a new beginLaunch.
- Call/lock/background/audio interruption -> PAUSE; foreground/explicit continue -> RESUME, not native start.
- Repeated confirm while an active task exists -> shared mutex -> durable active-task check. Repeated scheduling -> unique KEEP work.
- Repeat launch during preparation -> launching guard. Repeat same-game launch after preparation -> existing singleTask Activity. A replayed consumed onCreate is rejected; a repeated native start is refused.
- Cold reconstruction with a surviving runtime is faulty under APP-08.03, but the existing singleton/module guards block a demonstrated second native execution. No duplicate-native claim is made.

**Searched:** JSON S01/S10-S11; all enqueue/launch callers, lifecycle callbacks, manifest entrypoints, protocol handling and native start/command branches. No lifecycle-driven enqueue or native creation path found.

**Pending operations inspected:** Activity-result callbacks at HallApp.kt:L113-L115 only export/import saves or set notification preferences; they do not create content tasks/game sessions. SaveManager.kt:L61-L135 was read through inspection, locked commit and journal recovery. The save-import confirmation stores its archive in remember-only state (HallApp.kt:L100) but saves its dialog ID (L96); L340 becomes a no-op if that archive is lost. That broader save-import restoration issue is not promoted to an additional APP-08 finding or used to claim a duplicate runtime.

**How the verdict was reached:** creation is restricted to actual user commands and guarded by durable local state plus service/activity/native checks. Implemented describes the inspected source behavior; it does not establish physical-device acceptance. Failed/completed task retry semantics outside lifecycle replay are not broadened into this verdict.

**Open questions / later tests:** NavigationRecoveryTest.kt:L17-L29 covers only Settings Activity recreation/controller identity. The consolidated batch still needs rotation during pending launch, call/lock interruption, repeated icon/notification entry, process reconstruction, late ready/events, and task-count/native-start-count assertions. No current phone task is touched.

---

**Coverage and result handoff:** Four source-supported candidate divergences: F01 query restoration (P2), F02 file facts/publication gate (P2), F03 live-process reconciliation (P1), F04 recovery/install race (P1). Each has a concrete call chain, correction and deferred verification need above. All four subrules were audited; runtime/device acceptance is the only unexecuted layer. APP-08.json contains structured evidence, exact locations, search scopes/results and independent-review status.
