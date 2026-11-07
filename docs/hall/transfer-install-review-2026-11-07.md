# Download and installation review / 下载与安装核对

2026-11-07 · Reviewed source: `189169fded9cd6557b3cb146b56d8b2e5243d705` · [Source snapshot](https://github.com/wikigsroom/NsA-core/tree/189169fded9cd6557b3cb146b56d8b2e5243d705).

本轮按用户要求先完善 NsA 文档，保留另一项目的手机测试。只核对 DL-06、DL-07、PKG-06 三项 P0 需求的十二条细则，未修改程序、构建 APK、运行程序测试、查询或操作手机，也未采集新素材。下面是源码与规范的核对结果及下一整批开发约束；不是已发生的手机故障或完成修复的记录。

This documentation round preserves the other project's phone test. It inspects twelve detailed rules across three P0 requirements. No application source change, build, application test, phone query/input or new capture occurred. Findings describe inspected source paths, not observed device failures or completed fixes.

[实施与验证记录](implementation-status.md) · [需求代码对照](requirement-coverage.md) · [独立核对与反证证据](reviews/2026-11-07-transfer-install/README.md) · [机器记录](verification/2026-11-07-transfer-install-review.json) · [下一轮设备步骤](device-validation.md)

## Scope and status / 范围与状态

| Requirement / 需求 | Source result / 源码结论 | Acceptance / 验收 |
|---|---|---|
| DL-06 · 范围续传 | Partial: exact status/range/length and signed hashes are checked; the explicit identity-encoding constraint is missing. / 已有范围、长度与摘要保护，显式编码约束缺失。 | AC-D04 remains not_run. |
| DL-07 · 单任务与队列 | Partial: ordinary transfers are serialized; late enqueue and stale progress writes break queue/intent semantics. / 通常传输串行，但新增任务接续、取消意图与确定排序仍有缺口。 | AC-D05 / CON-07 remain not_run. |
| PKG-06 · 安装提交与恢复 | Partial: signed indexes, READY, same-volume publication and DB transactions exist; journal recovery, durability barriers and cleanup remain incomplete. / 正常校验与提交已实现，异常恢复、目录同步和清理义务仍需修正。 | AC-P05 / CON-06 remain not_run. |

每项需求由独立上下文追踪调用链，再由不同作者分别复核代码和规范。证据保留源码位置、搜索范围、补偿路径与被排除的结论。原始报告中的“候选/待复核”标签保留，最终裁定以本页及机器记录为准。P1/P2/P3 是缺陷严重度，不改变三项需求本身的 P0 优先级。

Each requirement has a separate source review, an independent code refutation and an independent specification refutation. Original candidate labels are preserved in the evidence. Severity labels do not downgrade the normative P0 scope. Other requirements have not been individually reviewed in this round; the earlier APP-05/APP-08/SAVE-04 review and correction retain their own source identities and pending device acceptance.

## Findings and limits / 问题与限定

| Identity | Severity | Reachable consequence / 影响边界 | Required correction / 修正约束 |
|---|---|---|---|
| DL-06-D01 | P2, conditional | Object requests omit the required identity header and response constraint; an origin selecting encoded bytes can cause avoidable failed retries. / 编码选择可能造成不必要的失败；未证实错误内容成功落为有效归档。 | Request identity explicitly and reject non-identity representation before promotion; retain all signed hash/range checks. |
| DL-07-F01 | P2 | Confirm B while A's worker runs; its one-time candidate snapshot excludes B and KEEP drops a successor. If the original snapshot succeeds, B can remain queued until another scheduling action. / 正常第二次下载可能长期停在排队。 | Make enqueue and final queue-drain completion race-safe; a committed runnable task must retain a wake-up obligation. |
| DL-07-F02 | P2 | A stale full-row progress write can restore cancelled A after replacement B is admitted, leaving two active tasks for one game. / 取消可被旧进度覆盖，产生同游戏重复活动任务。 | Preserve current intent through conditional writes; retain ownership until cancellation is acknowledged; enforce active-operation uniqueness in the coordinator and database. |
| DL-07-F03 | P3 | SQL has no task-ID tie-break; the reversed history display does not explain execution order. / 同时间任务缺少确定顺序，页面不能解释实际排队顺序。 | Use confirmation order plus task ID; make active queue order distinguishable from completed history. A numeric position is one possible UI, not a new mandatory requirement. |
| PKG-06-F01 | P2, filesystem-dependent | File syncs and a staging-root sync do not establish durability for every nested/new ancestor directory entry. / 缺少嵌套目录与新建祖先目录的耐久屏障；目标机掉电丢失未实测。 | Sync created directories bottom-up and persist ancestor links before claiming VERIFIED/publication; propagate failures. |
| PKG-06-F02 | P1 | One invalid journal candidate throws during initialization and repeatedly prevents the entire hall from becoming ready, including retained valid games. / 一个候选恢复失败可卡住整个大厅和完整旧版。 | Persist original active identity and phase ownership; distinguish staged, replaced and published trees; isolate failed recovery without erasing the obligation or blocking unrelated verified games. |
| PKG-06-F03 | P2 | Recovery can activate a valid install, mark the transfer completed and delete its journal while retaining the whole download archive indefinitely. / 恢复完成后可永久遗留整包下载，继续占用数 GiB。 | Keep an idempotent cleanup obligation until deletion and parent sync succeed; CLEANED must describe actual cleanup, not just an activated DB row. |

**安装恢复的纯进程退出触发：**对当前同一 release 的损坏安装选择“重新下载”；新 staging 已通过校验并写入 VERIFIED，但尚未替换旧 target 时退出进程。恢复分支仅因旧 target 存在就尝试激活它，重新遇到损坏并抛错，遗留日志持续阻止初始化。该代码路径不需要额外删除文件或假定掉电；它仍是源码推导，尚未在设备执行。

**Process-exit-only install trigger:** repair a damaged installed release, stop after the replacement staging reaches VERIFIED but before the old target is replaced, then restart. Recovery mistakes the still-present old target for the published candidate and fails activation. The independent code review strengthens the original injected-missing-file example; neither scenario has been executed on the phone.

**必须保留的反证：**锁定的 OkHttp 4.12.0 在请求含 Range 时不自动启用 gzip/透明解压；分块和整包 SHA 也阻止编码后或其他错误字节通过最终校验。416 当前安全拒绝，不会直接变成成功；完整本地归档先重验分块，不再发 Range。缺少专门的 416 分支仍是协议细节缺口，不能写成已经证实的损坏成功。服务端新建与续签固定同一对象，不能仅凭客户端缺少额外字段比较就认定能混入另一游戏。

**Retained refutations:** signed chunk/full hashes and exact 206 checks remain effective. Missing identity handling is a protocol/conditional-availability issue, not demonstrated archive corruption. Safe 416 rejection, backend-bound renewal identity and valid-local-file reuse refute stronger integrity allegations. Blanket 401/403 renewal and 404 policy handling overlap DL-09 and are not separately confirmed here.

DL-07-F02 证明同游戏活动任务及取消语义被破坏，不证明同时出现两条网络连接或两个执行器写同一文件。现有 WorkManager 唯一工作与处理器去重是有效补偿机制；规范未要求必须自建另一套租约。其停止、重建与回调边界仍需真实验证。普通 failed 任务在常规详情 UI 中已被识别，不把一个绕过正常入口的调用当作玩家必然可触发的重复下载。

Old distinct release directories are retained conservatively. No early automatic deletion of the only usable previous release was found. Health/rollback completion, API 34+ UIDT adaptation, catalogue/active-pointer races and production object-store immutability are adjacent matters outside this bounded verdict; they remain separate review/development work, not inferred passes or additional confirmed findings.

## Next complete batch / 下一整批开发约束

按 [ADR 0001](../engineering/decisions/0001-integrated-development-round.md)，先完成下列整批代码、迁移、界面说明及必要故障用例，再统一构建与测试。此文档交付不启动任何手机任务，不把实现计划计入已完成能力。

| Work / 工作 | Implementation boundary / 实施边界 | Required evidence after the full batch / 整批完成后证据 |
|---|---|---|
| Protocol / 传输协议 | 显式 identity；在原对象/摘要/Range 保护下定义 416、授权与续签分类，保留可信分段。不得为通过错误响应测试降低摘要保护。 | Controlled 206/200/encoded/416/expiry/identity-change responses and exact final hashes; classify protocol compliance separately from corruption prevention. |
| Queue and intent / 队列与意图 | 任务加入与执行器退出竞态闭合；用户意图与进度分开写；取消终结有确认；同游戏唯一活动所有权；必要数据库迁移保留旧任务、安装、存档与凭据。 | Deterministic late-enqueue, repeated notification, cancel-before-progress, cancel/replacement and equal-time order cases; no duplicate active ownership or lost work. |
| Install publication / 安装发布 | 原 active 身份与候选归属持久化；逐阶段同步；同 release 修复区分旧 target 与新 staging；失败候选可恢复/修复而非卡全局。不能仅吞异常删日志。 | Faults before/after VERIFIED, replacement rename, publication and DB commit; repeated recovery; old valid/unrelated games remain available. |
| Cleanup / 清理 | 清理归属持续到真实删除并同步；失败保留错误及待清理记录，完成历史不要求归档存在。保留 active/session/rollback/save 引用。 | Kill after publication/activation and injected deletion failure; retry is idempotent, archive no longer leaks, live content and saves unchanged. |
| Documentation / 文档 | 同步中英手册、需求对照、状态契约/兼容映射、迁移与候选身份；旧报告/素材不改写为新结果。 | Local references, artifact hashes, original IDs and acceptance-status preservation; new screenshots only from a later exact device build. |

Host/JVM and isolated Android test source should be part of that batch before execution. Run affected computer checks together after implementation; run device-only filesystem, WorkManager lifecycle, process-loss and real gameplay cases only when the phone is available. Use small generated archives and isolated test roots for deterministic faults; do not fill the user's disk or damage their real saves to simulate failures.

## Space, media and acceptance / 空间、素材与验收

当前私有 BotW 下载归档为 15,354,465,247 字节，约 14.30 GiB。PKG-06-F03 的恢复路径会使这份本可清理的归档继续占用手机空间；它不是安装内容的第二份必需副本，也不能在空间说明中承诺所有完成路径都已删除。多次受影响安装可累积遗留。电脑端已有目录用途与测量时点见[空间记录](storage.md)，本轮未新增大包或重新测量设备空间。

No new APK or media was produced. The recovery candidate remains `e0e4cc749012ef4ff2ad6a3f71ccdb93b31d6f4c16ab95ef9a2b6f240c0ca8b5`, uninstalled; the phone's retained build and public captures retain their original identities. This review does not invalidate their historical observations or promote them to candidate acceptance.

G0、真实新进度存档并重读、声音、五轮双游戏切换、30 分钟持续运行、性能及正式部署继续未验收。三项需求、十二条细则的源码核对和文档校验不替代这些结果。原 90 项需求、360 条细则及所有 AC/CON 编号与机器验收状态保持不变。
