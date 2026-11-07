> Archived independent_spec_refutation; reviewed source `189169fded9cd6557b3cb146b56d8b2e5243d705`. Original candidate/pending labels are historical; read the [final dispositions](../../../transfer-install-review-2026-11-07.md).
> 归档核对证据；原始候选标签保留，最终结论与限定见汇总。

## DL-07 — Independent specification refutation

**Reviewer role:** independent specification reviewer; original assigned requirement was PKG-06, not DL-07. **Pinned source/specification:** `189169fded9cd6557b3cb146b56d8b2e5243d705`. **Result:** the specification supports F01/F02/F03 with the limits below. Narrow F02's rule mapping; do not add an unsupported simultaneous-writer or routine failed-task duplicate claim. No source/test/device execution or specification modification occurred.

Inputs: `../requirements/DL-07.md` / `../requirements/DL-07.json` in this directory, all four machine/detailed rules, S05 and INT-09, engineering queue/intent/data constraints, AC-D05 and CON-07. Code citations were checked against the pinned enforcement already read during the installation audit and targeted queue/UI reads; this review's independent role is to challenge whether those behaviors actually contradict the documents.

### Exact requirements and their force

`docs/product/data/requirements.json:L953-L971` and `docs/product/产品需求细则.md:L415-L426` agree:

> 一款游戏只存在一个活动下载任务；首版同时下载 1 款，其余排队。多次点击下载、任务恢复和列表刷新不增加重复连接或副本。

| Rule | Verbatim |
|---|---|
| DL-07.01 | 全局同时一个内容传输，其余按确认时间与 task_id 排队；每游戏最多一个活动任务。 |
| DL-07.02 | 下载前占用调度租约，进程重建对租约对账，防止两个执行器同时写同一文件。 |
| DL-07.03 | 重复通知和页面动作不会增加连接数量；单任务首版不分多路并行连接。 |
| DL-07.04 | 安装与更新也受同卷空间和重 I/O 调度，不因只有一个网络任务就允许无限解包。 |

These are current P0 required rules, not a roadmap or an optional optimization (`requirements.json:L954,L986-L987`). `not_accepted` and acceptance `not_run` mean they have not been accepted; they do not waive implementation. `docs/engineering/04-transfer-install-save.md:L9` reinforces one global task/connection and one active game-content task while keeping user intent separate from blockers. `docs/engineering/05-data-state.md:L32,L50-L61` additionally requires one unfinished content operation per game, DB/coordinator uniqueness, and failed ownership retention until retry/abandonment.

S05 specifies **“全局单传输 FIFO”** (`docs/product/页面与交互验收规格.md:L94`) and ordered tasks (`L92`). INT-09 requires **“队列等待卡可见游戏、顺序、原因和取消”** (`L477`). AC-D05 includes enqueuing two games and requires an explainable queue position (`docs/product/data/acceptance.json:L157-L164`; `docs/product/开发计划与验收矩阵.md:L92`). CON-07 requires resumption only while allowed and user intent remains run (`acceptance.json:L698-L709`). No read ADR relaxes these requirements; ADR 0007's new initialization barrier does not replace queue ordering/draining or durable user intent.

### F01 — Newly confirmed work can remain stranded after the existing snapshot succeeds

**Specification disposition:** upheld. **Mapping:** DL-07.01 / AC-D05. **Severity:** Medium / P2; keep the source/dynamic-evidence limits.

The strongest attempted spec refutation is that “排队” only promises storage and ordering, not eventual automatic advancement. That reading conflicts with S05's FIFO executable queue, user-confirmed `run` intent and AC-D05's two-game queue case. A game with all execution conditions satisfied cannot remain indefinitely without any executor merely because it was confirmed after an implementation snapshot. The documents do not promise a numeric latency deadline, so the report must claim **no scheduled successor on the described all-success path**, not a fabricated elapsed-time SLA breach.

The described path reads one candidate list (`TransferWorker.kt:L35`), another game's enqueue commits then schedules using KEEP while that work is unfinished (`HallRepository.kt:L157-L168`), and the original successful list returns success without a new queue read (`TransferWorker.kt:L37-L50`). The finding explicitly excludes errors or later user/runtime/recreation scheduling actions that can eventually pick the row up. That restriction is necessary and does not eliminate the ordinary successful A-then-B scenario from the spec.

### F02 — Cancellation can be overwritten, allowing two active tasks for one game

**Specification disposition:** upheld with narrowed mapping. **Mapping:** DL-07.01, plus durable intent/CON-07 and operation ownership in `05-data-state.md:L61`. **Do not use this candidate alone as proof of DL-07.02's same-file multiple-writer violation.** **Severity:** Medium / P2.

“Every game at most one active task” applies to persisted operation state as well as the one currently using a network connection. Therefore serial WorkManager execution does not refute two nonterminal same-game records created by cancellation release followed by a stale full-row worker write. The original report correctly restricts the conclusion to cancelled work reviving and duplicate active operations/copies. Separate task IDs use separate roots and installation is serialized; neither the specification nor this interleaving justifies claiming simultaneous archive writers.

The stated cancel-and-replace sequence matches source: read run snapshot at worker L139; cancel it via `HallRepository.kt:L175`; insert replacement while cancelled is excluded by L146; then worker full-row Upsert L140 restores run/downloading. UI cancellation is reachable via `HallApp.kt:L357,L460`; once cancelled, the detail task projection excludes it at L111. This is a valid source-level witness to the documented uniqueness/intent property; timing has not been executed.

The separate **ordinary failed-task** allegation remains excluded. The repository enqueue guard indeed excludes failed rows, and `05-data-state.md:L61` says failed operations retain ownership. But normal detail UI still treats a failed row as the existing task (`HallApp.kt:L111,L243`), while the downloads card offers retry on that **same** task (`L458`, `HallRepository.kt:L174`). Those facts defeat a blanket assertion that an ordinary failed-state download click automatically admits a duplicate. A new reachable sequence would be needed to promote that variant. The cancellation witness does not depend on it.

### F03 — Missing task-ID tie-break and unexplained reversed queue display

**Specification disposition:** upheld at Low / P3, with UI wording kept precise. **Mapping:** DL-07.01 / S05 / INT-09 / AC-D05.

The required tie-break is explicit, not inferred: “按确认时间与 task_id 排队” (`产品需求细则.md:L423`). `Database.kt:L24-L25` orders only by creation time. No implicit SQLite row order is a documented enforcement of task-ID ordering for equal timestamps. The original synthetic equal-timestamp witness is appropriate as a static testable gap; it was not exercised and establishes no data loss.

The documents do **not** require a particular pixel position or forbid newest-first completed history. The defensible UI gap is that waiting cards must expose their effective order/position: S05 is ordered FIFO and INT-09 says order is visible. `HallApp.kt:L420` reverses the task list while `TransferCard` L443-L461 gives only generic queued text, no effective position/ordering explanation. A reversed history list accompanied by explicit effective queue positions could comply. Therefore keep “queue order is not explained/visible”; do not state that newest-first rendering alone violates every permissible layout.

### Ownership/lease observation — limit the conclusion

DL-07.02 requires a scheduling lease and reconciliation, but does not literally require a custom application file lock or a table named `transfer_lease`. WorkManager's durable unique work and duplicate-processor suppression are candidate equivalent enforcement; the original report correctly cites them as compensating controls. Absence of a separate application lease, without proving the lifecycle in which an old executor still writes after a successor owns the task, does not establish concurrent production writers. F02's two different task roots also do not establish it. Retain an unverified ownership handoff/lifecycle question rather than adding a confirmed same-file-write defect, or prove an actual framework path in a later authorized batch.

DL-07.03's ordinary serial-request protection and DL-07.04's bounded inline extraction/global installation mutex are consistent with their checked paths. These positive checks are bounded; they do not accept API34 UIDT behavior, all space reservation rules or phone process-stop behavior.

### Review record

Independent document searches: `DL-07|AC-D05|CON-07|FIFO|task_id|确认时间|队列|调度租约|failed` over the named requirement, acceptance, S05 and engineering chapters. The exact force/order/uniqueness terms and the narrowed failed-operation clause are quoted/cited above. Read ADR 0005 and ADR 0007 to search for a later authorized exception; neither relaxes these queue requirements. Cross-read queue source and UI cases to test the claimed rule mappings rather than infer behavior from labels.

No tests, builds, phone/ADB, browser, network, Docker, WSL, VM or emulator were used. Only this independent report and JSON were written. Actual AC-D05/CON-07 remains unrun; no acceptance flags were changed. The final confirmed summary should retain the stated trigger conditions and separate source proof from future execution evidence.
