# Download and installation correction round / 下载与安装修正轮次

2026-11-07 · Follow-up to the [pinned DL-06/DL-07/PKG-06 review](transfer-install-review-2026-11-07.md). [ADR 0008](../engineering/decisions/0008-transfer-ownership-and-install-recovery.md) fixes ownership, protocol, persistence/migration, publication and cleanup rules. Historical reviews, APK results and media provenance remain unchanged.

用户已重新授权 USB 实测。本轮先完成整批代码及必要用例，再统一构建、安装与真机验证。以下为已编写的实现和待执行计划；结果只在真实运行后填写，不能将旧 APK 的通过结果沿用到新版本。

| Finding | Source correction / 源码修正 | Evidence planned / 必要验证 |
|---|---|---|
| DL-06-D01 | Identity request/response encoding, immutable grant/ETag binding, explicit 206/200/416 and error handling | Response negatives, changed renewal identity, exact whole-object 416 hash |
| DL-07-F01 | Re-read queue after every task; durable serial WorkManager successor | Enqueue during processing and final-query race boundary |
| DL-07-F02 | Unique persisted per-game owner, conditional command/progress SQL, cleanup acknowledgement, whole-run file lock | Real Room cancel/stale-progress/replacement and lock exclusivity |
| DL-07-F03 | Stable created/id tie-break; active FIFO and effective position; separate recent history | Equal-time selection and actual download page |
| PKG-06-F01 | File, nested-directory, new-ancestor and rename-parent sync barriers | Injected sync failures; real Android fsync; physical power loss remains separate |
| PKG-06-F02 | Full candidate verification, damaged same-release publication, per-journal error isolation | Every publication process-exit boundary, invalid candidate and independent journal |
| PKG-06-F03 | Journal-backed archive/staging cleanup, idle maintenance and legacy terminal cleanup | Cleanup failure, repeat recovery, no old-release reactivation, actual archive absence |

## Player behavior / 使用逻辑

下载中心按加入顺序展示活动任务；暂停或条件未满足时可以处理下一项。取消会先显示“等待安全清理”，旧进度不会将它恢复成下载；同一游戏在清理确认前不能新建替代任务。已经进入不可取消发布临界区的安装会完成为可用版本，再清理临时文件。

安装恢复会检查文件完整性及真实候选，保留无法确认用途的旧副本。单条异常只显示待处理提示，其余完整游戏仍可使用。已安装但归档清理失败时，在下载中心点击“重试清理”；这不会重新下载或将旧版本切回活动版本。未知损坏记录保留诊断提示，不能宣称已释放其全部空间。

In Downloads, active transfers use FIFO with a stable tie-break. Pause/failure/cancellation remain independent from background progress. Cleanup must acknowledge cancellation before another same-game task is admitted. Publication already in its protected critical section may finish installation. A failed recovery record leaves other complete games available. Retry cleanup does not recreate a transfer or reactivate an old release.

## Compatibility and execution / 兼容与执行

- Room 1→2 preserves player records and files; duplicate legacy owners are retired through cancellation cleanup. Schema downgrade is unsupported.
- Android journal schema 2 derives task-specific paths; legacy journals retain their old path mapping. Normative IDs and acceptance gates are unchanged.
- Tests use isolated private directories. Overlay installation must retain the existing package data and saves.
- Raw captures, full diagnostic reports, game frames and saves stay in ignored private paths. Public media must identify the exact APK and be reviewed before publication.

## Execution record / 执行记录

Status at source-batch preparation: **not run**. The consolidated build, JVM tests, Android instrumentation, USB download/recovery and actual gameplay will be recorded here and in a separate machine-readable execution record after execution. No pass or new phone build is claimed by this preparation entry.
