# Targeted source review / 定向源码核对

Follow-up / 后续：[整批修正轮次](recovery-round-2026-11-07.md)记录新的实现与验证状态。本页以下内容及独立报告保留为已固定源码上的历史核对，不改写原始判断。

2026-11-07 · Reviewed source: `cdedc7179ec1842592243a052525e021d8cf70f1`.

This is a documentation handoff for the next complete implementation round. The findings below remain **open**. No application code was changed, no new APK was built, and no phone input was sent in this documentation round. The other project's physical-device test was preserved at the user's request.

本页将源码核对结果纳入后续研发约束，**不表示问题已修复**。本轮按用户要求先完成文档，保留另一项目的真机测试；未修改程序代码、构建新 APK 或向手机发送操作。

See the [operating guide](README.md), [中文手册](README.zh-CN.md), [execution record](implementation-status.md), [90-item source map](requirement-coverage.md) and [next device procedure](device-validation.md). Existing screenshots, GIFs and recordings retain their original source-build identities in the [media index](../media/README.md).

## Scope and evidence / 范围与证据

The review follows three P0 product requirements, with four detailed rules each: APP-05 (offline use and trusted policy), APP-08 (reconstruction and actual task/session state), and SAVE-04 (save import/export). Requirement identifiers and acceptance statuses are unchanged. This is **12 of 360 detailed rules**, not a complete audit of the 90 requirements. Source inspection does not establish device behavior or acceptance.

本次检查三个 P0 需求、共十二条细则：离线使用与可信策略、页面/进程恢复、存档导入导出。其余需求仍只具有原有代码入口映射，不能推定已经逐条核实。原需求编号和机器验收状态保持不变。

Each requirement has its own call-chain review. Separate reviewers then attempt to refute the code path and the specification interpretation. The [evidence index](reviews/2026-11-07/README.md) preserves the individual records; the [structured summary](verification/2026-11-07-source-review.json) identifies the reviewed and unchecked requirements. Findings are retained only within the boundaries supported by both reviews.

每项需求独立追踪入口、被调函数和恢复路径，再分别复核代码反例与规范适用范围。完整记录保留检查位置、搜索范围、被排除的猜测及尚需执行的验证；本文合并重复问题，作为后续整批开发清单。

The P1/P2/P3 labels below describe **defect severity**, not the product roadmap's P0/P1 requirement classification. These three requirements are all P0.

下表 P1/P2/P3 表示缺陷严重度；它们不是将 P0 产品需求降级为后续可选功能。

## Open findings / 待修正清单

| Review identity | Severity | Confirmed boundary / 已确认边界 | Required correction / 修正约束 |
|---|---|---|---|
| APP-05-D01 | P1 | Equal-epoch policies with different signed payloads can replace a known block; second-resolution server epochs do not uniquely identify a snapshot. 同一 epoch 的不同签名内容可覆盖已知阻断。 | Persist a stable, monotonic policy identity; compare epoch and signed digest atomically. 服务端稳定快照与单调序列，客户端原子拒绝回退和同序列异摘要。 |
| APP-05-D02 | P2, conditional | A known revoked signing key rejects an already verified, unblocked installation before exact release policy is considered. 当前服务尚未签发非空撤销列表，此项是契约允许状态下的消费分支缺口。 | Separate verification of the fixed installed payload from acceptance of newly signed content; retain exact-release blocking. 已安装固定摘要与新内容验签分开处理，不取消新内容撤销检查。 |
| APP-05-D04 | P1 | An online-only launch can fetch a final blocked policy after its last blocking check and still proceed. 仅在线运行分支最后一次同步阻断后没有再次判定。 | Check the final trusted snapshot before session creation; define its serialization boundary. 启动提交必须依据最后可信策略，不能检查后再次无条件刷新并继续。 |
| APP-05-D05 | P3 | The no-cache error banner says cached content is being displayed; the recovery page lacks the cache/no-cache context. 首次无缓存也显示正在展示缓存资料。 | Distinguish absent cache from a valid empty cached catalogue and show the actual cache time. 区分无缓存、有效空目录和旧资料，保留本地游戏入口。 |
| APP-08-F01 | P2 | Restored search text can accompany an unfiltered refresh after main-process reconstruction. 恢复的查询与结果范围可能不一致。 | Persist committed query/filter context and reload matching results, or display a real loading/error state. 允许同查询刷新，不要求永久保留旧结果快照。 |
| APP-08-F02 | P2 | Paused/offline transfer byte counts and active installations lack a complete startup file reconciliation. 恢复后可直接展示旧字节数和安装投影。 | Reconcile durable blocks, READY/index/manifest and active pointers before exposing readiness. 使用真实文件事实，缺失或不一致进入待恢复/需修复状态。 |
| APP-08-F03; SAVE-04-F2 | P1 | Main-process reconstruction marks old sessions crashed without proving that the separate game process stopped, releasing resource/save guards. 大厅进程重建可能提前释放仍存活游戏的保护。 | Keep a recovering session pinned until authenticated reattachment or confirmed termination. 不能以数据库改为结束态代替真实进程对账；包级强停也不能代替该测试。 |
| APP-08-F04 | P1 | Startup install recovery and worker extraction do not share the same ownership barrier; recovery can delete actively verified staging. 对账可能删除正在安装的暂存目录。 | Share initialization and operation ownership across all entrypoints; never recover an actively owned journal as abandoned. 后台与前台等待同一初始化屏障，对账与安装互斥。 |
| SAVE-04-F1 | P1 | A failed/interrupted exchange can leave the committed directory absent; an intervening export may create an empty directory that defeats later rollback. 交换失败后的其他操作可能破坏恢复判定。 | Recover under the save lock before any save-root creation/use; preserve the unique old copy and reconcile every exchange boundary. 恢复须幂等，异常补偿、目录同步与索引登记共同完成。 |
| SAVE-04-F3 | P2 | The confirmation survives recreation while its in-memory archive reference does not; a failed import also deletes the file needed by Retry. 导入确认可变成无效按钮，失败后不能直接重试。 | Persist a small owned pending-result record and restore/revalidate it; retain retryable input until explicit cancellation or durable completion. 不把大文件或完整清单塞进导航参数。 |
| SAVE-04-F4 | P1 | An enabled Cancel/dismiss can hide an import that continues to replace the save. 确认后的取消可能只关闭界面而未停止替换。 | Give the operation one owner; honor cancellation before publication, and visibly disable cancellation during the noncancellable boundary. 禁止重复确认排队，明确真实阶段。 |
| SAVE-04-F5 | P2 | The expanded total cap is checked after a complete indexed file has been written and hashed. 单个压缩文件可先展开超过预算再被拒绝。 | Validate declared sums before extraction and enforce remaining per-file/total budget before every write. 2 GiB 上限不能只约束压缩包或文件结束后的累计值。 |
| SAVE-04-F6 | P2 | Required backup metadata/types and control-character paths are not fully validated. 缺失来源/时间/序列字段及控制字符路径可进入导入流程。 | Enforce the supported save schema and shared path policy before file I/O. 存档允许无平台签名，不新增规范未要求的签名门槛。 |
| SAVE-04-F7 | P2 | Successful import does not register a durable import/index/provenance update; immediate re-export can use the previous save sequence. 导入数据与登记信息可能不一致。 | Reconcile the imported state and origin exactly once with the exchange journal; distinguish imported origin from local commit identity. 不把旧序列复制成新原生保存成功事件。 |
| SAVE-04-F8 | P2 | The original 2048 slot writes `state.new` inside the tree exported as committed saves. 失败的临时写入可被导出。 | Keep this slot's staging outside the committed tree, or use an authoritative committed-file index. 不能按通用 `.new` 后缀过滤所有游戏的合法文件。 |

Of 17 original candidate records, 16 retain a supported defect core. The table has 15 rows after merging the shared surviving-process defect. APP-05-D03 was not confirmed and is separated below. Conditional findings are not claims that the condition occurred on this phone. Full triggers, source locations and independent qualifications are in the evidence records.

原始十七项候选中，十六项保留有证据支持的缺陷核心；同一存活游戏进程问题去重后为十五行。APP-05-D03 未获确认，单独列在下方。条件分支缺陷不等于手机已经发生该问题；本轮未通过实际故障注入观察这些结果。

**APP-05-D03 — unverified; preparation/UX clarification.** The initial review inferred an offline-readiness violation from an installed release with no cached policy. Independent re-reading found that install-ready means activation/READY verification, while the UI's offline wording is conditional. A first trusted policy may legitimately be necessary preparation. The code explains the missing initial policy when launch is attempted. The stronger defect claim is therefore excluded. Clarify first-use preparation and retry behavior in the next round; **do not remove the policy safety gate** or pretend offline readiness has been explicitly promised by the current install state.

**APP-05-D03 未确认：**“安装已完成”不能直接等同于“离线授权准备已完成”。现有离线文案附有首次准备条件，启动时会说明缺少初始策略。后续应明确展示准备条件与重试入口，但不能据此直接删除首次可信策略检查。

**Supplemental scope conclusion — native player import/export.** Separate source and specification reviews confirm that the user-facing raw import/export menu compiled into the unified native player is subject to SAVE-04 and APP-04.03. Its SAF/format/index bypass is an additional integration gap. Disable or delegate those unified-player actions and deferred raw import consumption to the hall's validated flow. Private local snapshot helpers and the standalone legacy App are separate scopes; no standalone rewrite is required. The old import applies on the next launch before mounts, so this is not a demonstrated overwrite during the still-running old game. See the [code review](reviews/2026-11-07/refutations/SAVE-04-code.md) and [scope decision](reviews/2026-11-07/refutations/SAVE-04-spec.md).

**补充范围结论：**统一运行器中面向玩家的原生导入/导出菜单同样受现有安全交换和系统文件选择器规则约束。后续须接入大厅流程或关闭该替代入口及其延后执行的原始导入；内部私有快照与旧独立 App 分开处理。旧导入在下次启动、挂载前执行，不能描述为已经证实运行中直接覆盖。

## Next complete round / 下一轮完整开发约束

Finish the entire source batch before consolidated testing, as required by [ADR 0001](../engineering/decisions/0001-integrated-development-round.md). Preserve the existing product, page, contract and acceptance identities. The following is a dependency order for development, not permission to mark an intermediate module accepted.

按用户要求，下一轮先完成整批代码和关联文档，再集中测试。下列顺序表示实现依赖，不逐模块调测，不把中途编译或单个页面通过当作整轮完成。

1. **Policy and readiness:** stable signed snapshots, atomic monotonic persistence, exact-release blocking, installed-payload verification semantics, first-use/offline preparation, truthful cache states.
2. **Recovery ownership:** one initialization barrier; file-backed transfer/install reconciliation; recover or retain a live runtime pin; committed query restoration. All destructive/save operations must observe that ownership.
3. **Save operations:** durable picker/import records, one operation owner, real cancellation boundaries, early bounded validation, recoverable directory/index exchange, provenance and committed-only native saves. Do not introduce a second unsafe import route.
4. **Integration and documentation:** update affected state/data contracts or record a compatible implementation mapping; preserve existing data; update both guides and source mapping; finish all related changes before starting the combined checks.

集中验证必须覆盖：同 epoch 异摘要与同秒阻断、最终在线阻断、离线准备未完成、查询恢复、暂停任务文件缺失、后台安装/启动对账竞争、大厅进程单独终止而游戏仍存活、存档每个交换边界的异常与重复恢复、确认后取消/重复确认、压缩展开预算、格式/路径负例、导入后立即导出，以及 2048 临时文件失败。空间故障使用受控注入，不为测试填满用户磁盘。

Run host-side policy, file and transaction regressions plus Android compilation/static checks after that batch is complete. Device-only results remain open while the other project's phone test is preserved. Later physical tests must distinguish Activity recreation, main-process-only loss and a package-wide force-stop; these are different failure scopes. Never interrupt another project's test to obtain evidence.

后续真机按[设备验证步骤](device-validation.md)执行同 APK 双游戏、实际操作/声音/保存重读、正常退出、五轮切换与持续性能。源码检查、旧 APK 录像和首次出图均不能替代这些验收。

## Limits and excluded claims / 限制与已排除结论

- The existing cancellation/session-generation guards refute the claim that a delayed network refresh alone automatically launches a cancelled game. No such defect is reported here.
- Lifecycle creation guards exist for downloads and native sessions. The surviving-process finding does not prove a second native runtime was started.
- Existing save backups often preserve another copy after an exchange failure. The failure finding does not claim every copy is lost.
- The service currently emits an empty key-revocation list. Revoked-key handling remains a conditional source/contract finding.
- A future runtime's separate device-save mount is outside current two-slot evidence; define its snapshot contract before enabling it. Do not infer current BotW data loss from a future slot.
- The legacy native raw import/export menu is in scope when exposed to players in a unified build. Its deferred import applies on a later launch; it must not be described as a demonstrated overwrite during the current game session. Standalone and explicitly internal snapshot helpers have separate boundaries.

以上限定与未检查范围均为报告的一部分。原有真实执行结果仍以[实现与验证记录](implementation-status.md)为准，正式验收继续保持未通过。

Documentation checks for this handoff passed: 22 Markdown documents, 619 local links, all 18 published evidence hashes and 8/8 baseline groups. These are documentation/contract checks, with no new application or device execution.

本文档交付检查通过：22 份 Markdown、619 个本地链接、18 个证据文件摘要、8/8 组基线检查。本轮没有新增程序或真机执行结果。
