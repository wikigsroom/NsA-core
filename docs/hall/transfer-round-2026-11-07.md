# Download and installation correction round / 下载与安装修正轮次

2026-11-07 · Follow-up to the [pinned DL-06/DL-07/PKG-06 review](transfer-install-review-2026-11-07.md). [ADR 0008](../engineering/decisions/0008-transfer-ownership-and-install-recovery.md) fixes ownership, protocol, persistence/migration, publication and cleanup rules. Historical reviews, APK results and media provenance remain unchanged.

用户已重新授权 USB 实测。本轮先完成整批代码及必要用例，再统一构建、安装与真机验证。本轮实际构建与真机结果如下，均绑定新 APK，不沿用旧版本通过结果。

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

Source `720c785` · APK SHA-256 `e3b259216dbb9685b28040ff1108e586145cdd25f4693c018fb5806f714742da` · 82,194,533 bytes. [Machine-readable evidence](verification/2026-11-07-transfer-round.json).

| Check / 检查 | Actual result / 实际结果 | Limit / 边界 |
|---|---|---|
| Consolidated Android build | Debug/test APK, 26 JVM tests, APK contents/two slots passed; lint 0 errors, 16 warnings | First compile failed on public-SDK directory constant; fixed before successful rebuild |
| Contract baseline | 8/8 groups; journal-v2 valid adapter and 5 invalid vectors checked | Document/schema checks do not establish gameplay |
| Real Android instrumentation | 13/13 passed: capability/navigation, 5 save-recovery and 6 transfer-persistence cases | Isolated fixture faults; no destructive real-player fault injection |
| Overlay upgrade / 覆盖升级 | Room 1→2; both existing active releases retained; all 19 preexisting player-file hashes unchanged | Compared immediately after migration/instrumentation, before actual new gameplay |
| Real download/install | 2048 v2 signed 191-byte archive, 41 expanded bytes; actual hall flow passed | Native demo, not Switch compatibility evidence |
| 2048 input/save/reload | 8 inputs; game PID 15209→15623; all 16 tiles and save digest match; normal portrait return | Short sequence only; later audio test made additional moves |
| BotW new progress | Actual left-stick movement, manual save dated 11/07/2026 09:39, normal exit, fresh PID 17407→21520, saved position restored | Slot 1 game/caption files unchanged through reload; option.sav changed at startup |
| Recovery/download cleanup | Final transfer directory 4 KiB, no outstanding recovery warning; old completed BotW archive absent | This observation does not prove physical power-loss durability |
| Audio | 15.9935-second output capture contains zero nonzero PCM samples; route disconnection confirmed in log | **Failed**; no sound acceptance |
| Direct Android Back | BotW native overlay consumes Back; no hall return in that overlay | **Failed**; used Home/resume to reach Java pause dialog and normal exit |

2048 committed/reloaded SHA-256: `a7ccf3f67db53b880f5891afb89463b0864e1c6b2e42f99ab4a41adcc06893f9`. BotW was not force-killed for the accepted save/reload observation; its direct-Back defect remains recorded separately. No five-cycle sequence, 30-minute performance, audible output or full G0 acceptance is claimed.

本轮已完成覆盖升级、数据库迁移、十三项真机隔离用例、真实下载与 2048 存读档。《旷野之息》也完成实际移动、新写档及新进程重读，退出使用恢复后的 Java 暂停菜单。直接返回键和音频路由切换暴露两个真实缺陷，须整批修正后重新验证。声音、五轮双游戏切换、长期性能与正式发行继续开放。

Reviewed new screenshots/recordings: [media catalogue](../media/README.md). Public MP4/GIF are silent and contain the original open-source 2048 and hall only; private BotW frames, saves, keys and APK stay excluded. Historical review documents and earlier APK records keep their original identities.
