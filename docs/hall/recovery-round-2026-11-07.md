# Recovery correction round / 恢复与存档修正轮次

2026-11-07 · Follow-up to the [historical source review](source-review.md). [ADR 0007](../engineering/decisions/0007-policy-runtime-save-recovery.md) defines behavior, persistence, compatibility and rollout constraints.

This record describes a new source batch. It does not replace the review's pinned evidence, the installed APK's previous results or the original media provenance. The user asked to preserve the other project's phone test. This round uses computer-side builds/checks; new device checks and captures remain pending.

本页记录后续整批代码修正，不改写此前源码核对和真机素材的历史身份。按用户要求保留其他项目的手机测试；本轮不安装、不输入、不录制手机。先完成整批实现与必要用例，再集中构建验证。以下“已接入”指源码路径存在，不等同于真机验收通过。

| Finding | Implemented boundary / 已接入边界 | Verification boundary / 验证范围 |
|---|---|---|
| APP-05-D01 | Stable scoped service snapshots, durable sequence, atomic epoch/digest compare, retained endpoint blocking / 稳定分范围快照、单调序列、原子比较与阻断保留 | Host concurrent/frozen-clock policy tests and JVM replay tests; real service rollout still requires migration |
| APP-05-D02 | Only fixed-digest installed release verification bypasses key revocation; exact release blocking remains / 仅已安装固定摘要豁免单纯撤钥，精确版本阻断仍生效 | Conditional policy state; current service emits an empty revocation list; live revocation scenario pending |
| APP-05-D04 | One final refresh and serialized final policy/session claim / 最后一次同步与会话认领共用策略保护 | Source path and host policy tests; late-response launch device case pending |
| APP-05-D05 | Cache presence distinguished from a valid empty result; timestamp on recovery page / 无缓存与空缓存区分，恢复页展示时间 | New UI compiled; updated physical-device captures pending |
| APP-08-F01 | Persist committed query/filter, matching results and endpoint; refresh restored context / 恢复已提交查询、筛选与匹配缓存 | UI compile; process-recreation search scenario pending |
| APP-08-F02 | Marker/index/file-size reconciliation and checked chunk prefix, retained task intent / 安装文件对账与真实分块进度，保留任务意图 | Isolated Android test added for missing content, partial chunks and completed history; execution pending |
| APP-08-F03 / SAVE-04-F2 | Kernel lifetime lease, persisted context, authenticated reconnect; confirmed termination before release / 内核租约、原会话重连、确认结束后释放保护 | JVM lease ownership check; actual main-process-loss handshake pending |
| APP-08-F04 | Worker initialization barrier and shared installation/recovery operation lock / 后台任务初始化屏障与恢复/安装同锁 | Source path and complete Android build; Android race/fault exercise pending |
| SAVE-04-F1 | Recoverable old/new directory swap with durable index and immediate compensation / 原档与新档可恢复交换、持久索引、即时补偿 | JVM publication-boundary and fsync-failure injection; Android power-loss/filesystem exercise pending |
| SAVE-04-F3 | Durable original game/operation/archive ownership; restored confirmation and retained retry input / 原游戏与草稿归属持久化，确认可恢复、失败输入保留 | Isolated Android restart/confirmation test added; execution pending |
| SAVE-04-F4 | Single owner, repeated confirm blocked, no cancel/dismiss throughout confirmed import / 单操作归属，确认后全程禁重复与取消 | Source/UI compile; rapid tap, back and recreation device cases pending |
| SAVE-04-F5 | Complete sum and space preflight, independent budget before each write / 提前校验合计与空间，每次写入前执行独立预算 | JVM bounded stream overflow test; Android ENOSPC injection pending |
| SAVE-04-F6 | Complete metadata schema, exact fields/types and shared safe path / 完整元数据与字段类型、统一安全路径 | JVM malformed metadata/path/collision tests; Android ZIP/link cases pending |
| SAVE-04-F7 | Idempotent local import revision and origin, distinct native events, current export revision / 导入本地索引与来源可幂等、区分原生提交，导出使用当前修订 | Isolated Android import/reexport/idempotency test added; execution pending |
| SAVE-04-F8 | Demo stages outside committed and exports its owned authoritative file only / 原创游戏在提交树外暂存，只导出约定的有效文件 | Native rebuild; isolated Android legacy staging exclusion test added; execution pending |
| Native exchange integration | Hall builds hide raw menu exchange and skip deferred import; standalone retained / 统一构建关闭旁路交换，独立版保留 | Affected native host rebuild; real menu/input check pending |

APP-05-D03 remains unconfirmed as a defect. The first trusted policy gate is retained with preparation/retry text. Initial installation completion is still distinct from completed offline preparation. The disproved late-fetch cancellation bypass is not reintroduced as a finding. These changes do not claim an audit of the other 87 requirements.

APP-05-D03 未被确认为缺陷；首次可信策略准备保留，并提供联网重试说明。已安装仍不等于离线准备完成。已被反证否定的取消绕过不作为本轮修复成果；其余 87 项需求也没有因此自动通过核对。

## Consolidated checks / 集中验证

Status at source-batch preparation: **pending execution**. The completed verification record will identify the exact source commit, native identities, APK digest, test counts and failures. No host result substitutes for actual Android behavior.

整批源码准备时状态：**尚未执行**。集中验证后补充精确源码提交、运行库身份、APK 摘要、用例数量和未通过项。真机测试将继续遵守用户的设备使用安排；未执行项明确保留。

## Upgrade and usage / 更新与使用

1. Run `services/migrate.py` before the updated production API. Keep existing database, objects, signing trust and credentials. Do not reset epochs or replace published artifacts.
2. Build native components from a clean source revision, then package the actual library hashes. A changed 2048 profile requires a new immutable resource release; existing content is not silently rewritten.
3. On updated Android startup, wait for local recovery. A retained runtime lease keeps resource/save actions guarded; reconnect or explicitly end the owned process.
4. Export creates its snapshot first, then opens the destination chooser. Import retains a private draft, displays its original game and source version, and reconfirms compatibility before publication. Cancel before confirmation removes only the draft. Confirmed imports finish or recover before cancellation becomes available.
5. Keep historical media labels. Capture new UI, SAF reconstruction, runtime recovery and real save/reload evidence only when the phone is available for NsA.

正式服务先运行迁移脚本，保留原数据库、对象、签名信任与凭据；不可重置序列或覆盖已发布内容。新 APK 需从干净源码重新生成运行身份，配置变化必须发布新资源版本。启动时先等待对账；活游戏未确认结束前不解除数据保护。导出先固定快照、导入保留草稿与原目标游戏，确认后等待完成或恢复。新素材与真机验证待设备可用于 NsA 时再补充。
