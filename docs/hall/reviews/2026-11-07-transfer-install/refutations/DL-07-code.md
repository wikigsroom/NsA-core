> Archived independent_code_refutation; reviewed source `189169fded9cd6557b3cb146b56d8b2e5243d705`. Original candidate/pending labels are historical; read the [final dispositions](../../../transfer-install-review-2026-11-07.md).
> 归档核对证据；原始候选标签保留，最终结论与限定见汇总。

# DL-07 — 独立代码反证

Source: `189169fded9cd6557b3cb146b56d8b2e5243d705`。反证者不是 DL-07 原作者。输入为同目录 `../requirements/DL-07.md`、`../requirements/DL-07.json`；未修改原始候选标签或原报告。适用 AGENTS/CLAUDE 及审查技能要求已在本上下文读取并继续遵守。

**最终裁定：F01 保留，F02 仅按 DL-07.01/CON-07 保留，F03 的 task_id 次序核心保留、展示部分单独收窄。** F01/F02 为 Medium/P2；F03 次序核心为 Low/P3。三项均为源码推导，未执行 Android 时序、并发或 UI 验证。没有确认两个生产执行器同时写同一归档，也没有把缺少应用租约本身升级为并发损坏结论。

**DL-07.02 的单独边界：常规路径为 qualified implemented；停止/重建交接的完整性仍 undecidable。** WorkManager 的持久同名工作与 Processor 代次去重提供框架级执行所有权，规范不强制自建应用 lease。初始化先于传输写入、运行实例普通回调去重均有源码/依赖支持。未检查并证明所有同步 HTTP 阻塞期间的停止、旧 runner 退出和新 runner 接手交错，故不把整个 .02 宣称完全验收，也不因没有自定义锁把 .02 判为多写者缺陷。F02 的两个活动任务行不是同文件双执行器证据。

---

## DL-07-F01 — 保留：成功路径会遗漏快照之后入队的任务

**源码缺口。** `android/app/src/main/java/org/nsa/hall/transfer/TransferWorker.kt:L35-L50` 只读一次候选列表，顺序执行该列表后，在没有 Interrupted/异常时返回 success。`android/app/src/main/java/org/nsa/hall/data/HallRepository.kt:L157-L168` 先提交新任务，再用同名 `content-transfer` / KEEP 调度；没有动态任务表读取或成功后的接续调度。

**框架反证尝试。** 独立检查声明的 WorkManager 2.10.5（`android/app/build.gradle.kts:L55`）及现有缓存 JAR：`EnqueueRunnable.enqueueWorkWithPrerequisites` 字节码 offsets 513–575 对 KEEP + ENQUEUED/RUNNING 直接返回 false；该请求不会成为依赖后继。`Processor.startWork` offsets 94–204 对已入执行队列的同一 WorkSpec 拒绝重复启动。框架确实防重复，但不观察应用自建的 transfers 表，也不能补回被 KEEP 丢弃的后继意图。

**可达时序，需保留的条件。** W 已在 L35 取得 [A]，A 正在下载且尚未进入安装 operationLock；用户确认另一游戏 B。B 的 DAO 插入完成，其 WorkManager enqueue 操作也须在 W 仍 RUNNING 时处理完成。W 之后所有原候选均成功，返回 success；B 留在 run/queued。若 enqueue 操作一直延迟到 W 完成之后才处理，它可能成功创建后继，这不会推翻前一种可达交错。复现应明确等待 KEEP enqueue 处理完成，而不能只以 API 调用返回作为所有时序证据。

**其他反证尝试。**

- Activity/ViewModel 初始化不会稳定补救：`HallRepository.kt:L61` 在 initialized 后提前返回，绕过 L89 的 schedule；`android/app/src/main/java/org/nsa/hall/MainActivity.kt:L41-L47` 只调用该入口。
- `MainActivity.kt:L34` 对 DAO Flow 的订阅只投影 UI，未调度；`HallRepository.kt:L92-L124` 刷新目录也不调度。
- `android/app/src/main/java/org/nsa/hall/transfer/MaintenanceWorker.kt:L14-L20` 调 initialize 后仅修剪日志；同进程已初始化时仍无下载 drain。
- 网络/临时错误触发 retry、显式 resume/cellular、网络偏好操作、运行会话结束（`runtime/SessionController.kt:L255`）或进程重建都可能补救。因此保留的结论是“成功快照结束且没有后来调度事件时，后入队任务可一直等待”，不是“B 永远不能恢复”。

**影响与等级。** 普通确认第二款游戏即可触发下载停滞，需额外动作才能继续；Medium/P2。未声称丢包或多连接。待执行证据是明确的 WorkManager enqueue 结果、A 成功结尾、B 持久状态与无后继执行记录。

---

## DL-07-F02 — 保留：旧全行进度写入可覆盖取消并与替代任务并存

**源码缺口。** `TransferWorker.kt:L139` 取得 run 行的快照，L140 用 `row.copy(state = "downloading", ...)` 全行 Upsert，保留旧 intent。取消在 `HallRepository.kt:L170-L175` 读取并全行写 cancel/cancelled，既不持有 operationLock，也无 revision/CAS。enqueue 在 L146 把 cancelled 视为已释放。`android/app/src/main/java/org/nsa/hall/data/Database.kt:L9-L10,L27,L45-L49` 只有 id 主键和普通 gameId 索引，无活动任务唯一约束、迁移回调或条件写接口。

**独立框架/DAO核对。** 缓存生成的 `android/app/build/generated/source/kapt/debug/org/nsa/hall/data/HallDao_Impl.java:L190` 的 UPDATE 写入全部字段，包括 state/intent，WHERE 只有 id；没有“仅更新 bytes”或“intent 仍为 run”条件。该生成文件不是 pinned 源码，只是辅助；结论本身来自 pinned 实体/DAO 与调用。单条 Room 事务并不把 L139 的读和 L140 的写包成与取消/新建互斥的原子命令。

**可达时序。** 已下载多个段、DB state 已为 downloading 的 A，在 L139 读成功后、调用/提交 L140 前暂时不运行。用户取消 A：取消记录提交，且因取消读取的旧 state=downloading，L175 不立即删目录。Room Flow 更新后，详情 `android/app/src/main/java/org/nsa/hall/ui/HallApp.kt:L111,L240-L243` 排除 cancelled 并允许重新下载；确认路径 L360-L366 → enqueue，提交同游戏 B。随后 A 的旧 L140 写恢复 run/downloading，两个任务同时成为活动行。这个交错需要保留取消提交、替代任务提交均先于旧进度写的条件；不需要两个 Worker。

**反证未成立的原因。**

- Application 单例仓库（`HallApplication.kt:L12-L15`）与 ViewModel actionLock（`MainActivity.kt:L31-L47`）只能串行 UI 动作，后台 Worker 没有取得该 actionLock。取消和新建可按顺序完成，仍会被旧后台快照覆盖。
- enqueue 的 operationLock 只锁其本身；Worker 在 L140 没持锁，只有 L148 的安装区持有它；取消也没有共享锁。
- Worker 的下次 checkpoint 会重读，但旧写已把 intent 恢复成 run，因而不能再识别曾经的取消。若取消在 L139 前被读到，checkpoint 会停止；那是未触发该交错的受保护路径。
- WorkManager 去重仍限制为单执行器；两个 task_id 也导向两个目录（`TransferWorker.kt:L103`），安装仍串行。这些保护推翻“已证明并行连接/同文件同时写入”的扩大说法，但不保护同游戏活动行唯一性或取消意图。

**影响与等级。** 取消丢失、旧任务继续，允许同游戏替代任务同时持有活动记录及独立下载副本；仅证明 DL-07.01/CON-07，Medium/P2，不能据此确认 .02 的同文件双执行器违例。B 后续是否自动执行还受 F01 及下一次调度影响，不将 B 立即并发下载作为结论。Android 上实际触发窗口和多余流量/副本大小仍待执行验证。

---

## DL-07-F03 — 次序核心保留；展示事实单独收窄

**确认的源码事实。** 两个读取接口均只有 `ORDER BY created`（`Database.kt:L24-L25`）；created 是毫秒墙钟，id 为随机 UUID（L10；`HallRepository.kt:L157`）。没有二级 id 排序或协调器比较器。Worker L35 直接保留 DAO 次序。相同 created 的合法持久行没有被这段实现强制按 task_id 排列；这不等于每次正常 A→B 确认都会乱序。

**展示部分的代码事实。** UI 的 `Downloads` 在 `HallApp.kt:L416-L423` 把完整列表反转，`TransferCard` L443-L461 只给通用“排队等待”，没有另行说明执行排序或给出有效位置；它可以让后确认的排队卡显示在前。`MainActivity.kt:L34` 的 Flow 没插入额外排序或解释数据。

**收窄并分开裁定。** task_id 二级排序缺失是独立保留的次序核心。newest-first 的历史列表本身并不证明执行器优先跑 B；执行仍按 created 升序。数字队列位置不是强制实现形式，若排序/文字/分组已让实际顺序可解释，即可采用等效满足路径。故展示部分只保留“反转列表、没有另行执行次序解释”的代码事实；是否构成 S05/INT-09 的独立解释缺陷，按规格反证与实际多个排队项场景判断，不另计一项，也不以“没有数字 #N”直接定错。不宣称数据丢失、并发或已实际发生同毫秒错序。

**影响与等级。** 确定性次序缺口，Low/P3；展示可解释性单独受上述边界限定。等 created 的构造是持久输入条件，不是已执行的真实 UI 操作记录；需在集中验收中验证等时队列和多个排队项的实际展示。

---

## 搜索与阅读范围

独立重读了 HallApplication、MainActivity、完整实体/DAO/open、worker 控制/检查点/传输/完成路径、enqueue/actions/scheduler、初始化早退、UI 详情/确认/取消/下载列表与卡片、MaintenanceWorker 及 session finish 的调度点。共享的传输/安装 callees 已在本上下文完整读取；其单执行器/安装锁仅作为反证，不扩展本任务为新主审。

| 搜索 | 范围 | 结果 |
| --- | --- | --- |
| `scheduleDownloads` | `android/app/src/main` | 7 行：声明，initialize/enqueue/resume/cellular/preferences/session finish；无 Worker 成功/表观察者 drain。 |
| `putTransfer\(` | 同上 | 15 行；全部写入位置已核对，无条件进度接口。 |
| `enqueueUniqueWork|enqueue\(|ExistingWorkPolicy|allTransfers\(|\.transfers\(` | 同上及主 Activity/UI | 唯一 content-transfer/KEEP 生产调度和自建 DAO Flow；无隐藏后继观察器。 |
| `one_content_operation|content_operation|command_receipt|CREATE UNIQUE INDEX|unique\s*=\s*true|WorkerFactory|Configuration.Provider|defaultProcessName|RemoteCoroutineWorker|RemoteWorker|work-multiprocess` | 同上及 app build | 0 hits；数据库 builder/Manifest 另外直接读，未发现活动唯一 fence 或替代传输进程。 |
| `withTransaction|withLock|update.*Transfer` | data/transfer 主源码 | 其他安装/策略/存档锁存在；取消与 L139/L140 不在共同临界区。 |
| `sorted|sortBy|ORDER BY created|collect` | DAO/repository/MainActivity/UI | 与任务有关的排序只有 created；UI 反转与状态投影没有 task_id 或位置计算。 |
| WorkManager KEEP / Processor | 本地缓存 `work-runtime-2.10.5-runtime.jar` | 只读 javap offsets 513–575 / 94–204，确认去重而非接续。 |

缓存依赖路径：`tools/hall/gradle-home/caches/8.13/transforms/2d61aa40fb5c45ed6e369fcbe8c7b0af/transformed/work-runtime-2.10.5-runtime.jar`。没有下载依赖或执行应用/测试。

**边界。** 保留是独立代码反证裁定，不代替规格反证与实机验收；原报告状态标签保持不变。没有网络、浏览器、测试、构建、ADB/手机或虚拟环境调用。只新写本反证的 Markdown/JSON；源文件和验收状态未改。
