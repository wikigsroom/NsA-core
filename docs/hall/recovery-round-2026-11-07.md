# Recovery correction round / 恢复与存档修正轮次

2026-11-07 · Follow-up to the [historical source review](source-review.md). [ADR 0007](../engineering/decisions/0007-policy-runtime-save-recovery.md) defines behavior, persistence, compatibility and rollout constraints.

This record describes a new source batch. It does not replace the review's pinned evidence, the installed APK's previous results or the original media provenance. The user asked to preserve the other project's phone test. This round uses computer-side builds/checks; new device checks and captures remain pending.

本页记录后续整批代码修正，不改写此前源码核对和真机素材的历史身份。按用户要求保留其他项目的手机测试；本轮不安装、不输入、不录制手机。先完成整批实现与必要用例，再集中构建验证。以下“已接入”指源码路径存在，不等同于真机验收通过。

| Finding | Implemented boundary / 已接入边界 | Verification boundary / 验证范围 |
|---|---|---|
| APP-05-D01 | Stable scoped service snapshots, durable sequence, atomic epoch/digest compare, retained endpoint blocking / 稳定分范围快照、单调序列、原子比较与阻断保留 | Concurrent/frozen-clock and JVM replay tests passed; local migration and repeated real HTTPS policy responses passed; production rollout pending |
| APP-05-D02 | Only fixed-digest installed release verification bypasses key revocation; exact release blocking remains / 仅已安装固定摘要豁免单纯撤钥，精确版本阻断仍生效 | Conditional policy state; current service emits an empty revocation list; live revocation scenario pending |
| APP-05-D04 | One final refresh and serialized final policy/session claim / 最后一次同步与会话认领共用策略保护 | Source path and host policy tests; late-response launch device case pending |
| APP-05-D05 | Cache presence distinguished from a valid empty result; timestamp on recovery page / 无缓存与空缓存区分，恢复页展示时间 | New UI compiled; updated physical-device captures pending |
| APP-08-F01 | Persist committed query/filter, matching results and endpoint; refresh restored context / 恢复已提交查询、筛选与匹配缓存 | UI compile; process-recreation search scenario pending |
| APP-08-F02 | Marker/index/file-size reconciliation and checked chunk prefix, retained task intent / 安装文件对账与真实分块进度，保留任务意图 | Isolated Android test added for missing content, partial chunks and completed history; execution pending |
| APP-08-F03 / SAVE-04-F2 | Kernel lifetime lease, persisted context, authenticated reconnect; confirmed termination before release / 内核租约、原会话重连、确认结束后释放保护 | JVM lease ownership check; actual main-process-loss handshake pending |
| APP-08-F04 | Worker initialization barrier and shared installation/recovery operation lock / 后台任务初始化屏障与恢复/安装同锁 | Source path and complete Android build; Android race/fault exercise pending |
| SAVE-04-F1 | Recoverable old/new directory swap with durable index and immediate compensation / 原档与新档可恢复交换、持久索引、即时补偿 | JVM publication-boundary and fsync-failure injection; Android power-loss/filesystem exercise pending |
| SAVE-04-F3 | Durable original game/operation/archive ownership; operation-bound pickers, restored confirmation, retained retry input and orphan cleanup / 原游戏与草稿归属持久化，选择器绑定原编号，恢复确认与清理无主草稿 | Android restart/confirmation and stale-callback tests compiled; execution pending |
| SAVE-04-F4 | Single owner, repeated confirm blocked, no cancel/dismiss throughout confirmed import / 单操作归属，确认后全程禁重复与取消 | Source/UI compile; rapid tap, back and recreation device cases pending |
| SAVE-04-F5 | Complete sum and space preflight, independent budget before each write / 提前校验合计与空间，每次写入前执行独立预算 | JVM bounded stream overflow test; Android ENOSPC injection pending |
| SAVE-04-F6 | Complete metadata schema, exact fields/types, shared safe path and symmetric 8 MiB metadata cap / 完整元数据、统一安全路径，导入导出同限 8 MiB 清单 | JVM malformed metadata/path/collision tests passed; Android ZIP/link cases pending |
| SAVE-04-F7 | Idempotent local import revision and origin, distinct native events, current export revision / 导入本地索引与来源可幂等、区分原生提交，导出使用当前修订 | Isolated Android import/reexport/idempotency test added; execution pending |
| SAVE-04-F8 | Demo stages outside committed and exports its owned authoritative file only / 原创游戏在提交树外暂存，只导出约定的有效文件 | Native rebuild; isolated Android legacy staging exclusion test added; execution pending |
| Native exchange integration | Hall builds hide raw menu exchange and skip deferred import; standalone retained / 统一构建关闭旁路交换，独立版保留 | Affected native host rebuild; real menu/input check pending |

APP-05-D03 remains unconfirmed as a defect. The first trusted policy gate is retained with preparation/retry text. Initial installation completion is still distinct from completed offline preparation. The disproved late-fetch cancellation bypass is not reintroduced as a finding. These changes do not claim an audit of the other 87 requirements.

APP-05-D03 未被确认为缺陷；首次可信策略准备保留，并提供联网重试说明。已安装仍不等于离线准备完成。已被反证否定的取消绕过不作为本轮修复成果；其余 87 项需求也没有因此自动通过核对。

## Consolidated checks / 集中验证

**Computer-side verification passed. No phone installation, instrumentation execution or new capture occurred in this round.** See the [machine-readable round record](verification/2026-11-07-recovery-round.json). No host result substitutes for actual Android behavior.

**本轮电脑端集中验证通过，未安装或操作手机，未执行真机测试，也未采集新截图或录像。** 按用户要求保留另一项目的设备测试。主程序源码为 `a1e0f18aec24c7832d326f25c16b553d88b07ae5`；原生组件来自干净提交 `e6ee168bd9f733023f3338949df9f283555120fb`。其后对发布器的许可文件来源修正提交为 `2f25acaa4aac2167aac0cd42b3cdb6f9f4caede7`，没有改变候选 APK 输入。

| Check / 检查 | Executed result / 实际结果 | Limit / 边界 |
|---|---|---|
| Native / 原生 | BotW host 145 sources, 0 failed; bootstrap and 2 slots collected | Compile/package verification; no new device run |
| Android / 客户端 | Debug APK + instrumentation APK assembled; 16 JVM tests passed; lint 0 errors, 16 warnings; 2-slot APK digests verified | Instrumentation package compiled, not executed |
| Services / 服务 | 35 tests passed, 1 dependency deprecation warning | Local test providers; not production deployment |
| Existing core / 核心 | 131 run: 92 passed, 39 skipped | Skips are not passes |
| Contract baseline / 规范契约 | 8/8 verification groups passed | Contract conformance, not game acceptance |
| Documentation / 文档 | 27 Markdown documents, 716 local links and 18 unchanged historical audit artifacts checked | Structure/link/provenance checks; no new browser or phone capture |
| Local rollout / 本地更新 | Database backed up/migrated; own API/worker restarted; 2048 v2 uploaded, worker-verified, independently reviewed and published | Internal localhost development catalogue only |
| Policy HTTPS / 真实接口 | Official/internal snapshots separately signed and stable across repeat requests; internal releases absent from official response | No live key-revocation simulation or phone policy race executed |
| Preservation / 数据保留 | All 18 pre-existing game/release/build/rights/artifact records retain exact row digests | Original published v1 content preserved |
| Phone / 手机 | 7 instrumentation cases compiled, including 5 new recovery/save cases; 0 executed on candidate | Main-process loss, SAF UI recreation, filesystem faults, game acceptance pending |

The initial service run exposed a typo in the added test's URL (`/v1/policies/releases`); the test was corrected to the actual contract route `/v1/policies`, then all 35 passed. The first Android run passed before the final picker/draft ownership refinement; after that complete refinement batch, Android assemble, unit tests, lint and APK verification were repeated together and passed. Native/core/service inputs were unchanged by that Android refinement, so their passed suites were retained. The small publisher license-source correction was exercised by the subsequent real 2048 v2 publication.

首次服务检查发现新增测试误用了路径，已按现有契约纠正后集中重跑通过。Android 首轮通过后完成选择器编号归属与草稿恢复的整批收尾，再统一复验客户端；未把旧结果算给改动后的客户端。静态检查的 16 条警告涉及方向及 ABI 支持范围、集合状态、依赖升级与 Kotlin/KAPT 建议；无静态检查错误，但这不证明新系统、大屏或其他 ABI 已适配。

## Candidate and content / 候选与内容身份

| Artifact / 产物 | Identity / 身份 |
|---|---|
| Debug app | `org.nsa.hall.debug`, `0.1.0-dev` / code 1, arm64-v8a, 82,194,533 bytes |
| App SHA-256 | `e0e4cc749012ef4ff2ad6a3f71ccdb93b31d6f4c16ab95ef9a2b6f240c0ca8b5` |
| Instrumentation APK | 2,393,074 bytes; `88f6842f78619f6db3d53c92f0d20655991b5adfee2e06f2a9bfbb0628468cdb` |
| 2048 library SHA-256 | `2d68fc1087265f02123d8fedf9c2247ee6cf0ba480bb9d29badd02ff6884ea3c` |
| BotW library SHA-256 | `6c64326ad27346d2d7f6a21e1153c79ceb813f42797a1f4adf48901dec9e435a` |
| New demo resource | `nsa-2048-local-v2`; 191-byte archive, 41 expanded bytes, 1 file; published revision 5 |
| Demo runtime profile | `b18fc38452dedd8781b5c4b1d6b6efbaa8aa2f433e68eca540a27fd64a0840a7` |
| Phone's retained APK | Source `3c096c8`; SHA `cd2b565141e81b1f7c73a1757eaa32b775495d96f0fcbd19b912b6475ed9a027` |

The app and test APKs remain private under `output/hall/recovery-round/`; original bindings, the prior APK and a pre-migration database backup are retained in its `previous-bindings/` directory. No game data, credentials, backup or binary is added to Git. The two native identities correctly name their actual clean source revision; later Kotlin/docs/publisher commits did not change those native inputs. The debug candidate is not a publicly distributed release.

候选与测试安装包留在本地私有输出目录；旧 APK、绑定及迁移前数据库可恢复。新增 2048 资源版本使用其完整原始 MIT 许可，运行配置与候选一致，沿用 `nsa-2048-save-v1` 兼容组并声明兼容旧 v1。旧资源未覆盖，手机仍保留旧应用。BotW 的资源/适配配置未改变，旧 v1 继续保留；新运行库中的菜单与生命周期仍需重新真机验证。

## Upgrade and usage / 更新与使用

1. Run `services/migrate.py` before the updated production API. Keep existing database, objects, signing trust and credentials. Do not reset epochs or replace published artifacts. This local development database has already been migrated and its own API/worker restarted; production is not deployed.
2. Build native components from a clean source revision, then package the actual library hashes. The changed 2048 profile now uses published immutable release `nsa-2048-local-v2`; existing v1 content is retained. Another environment must prepare/publish its own matching release before installing the candidate.
3. On updated Android startup, wait for local recovery. A retained runtime lease keeps resource/save actions guarded; reconnect or explicitly end the owned process.
4. Export records its owned filename, creates the snapshot, then opens the destination chooser. Every picker callback carries that operation's ID; late results from old operations are ignored. Import retains a private draft, displays its original game and source version, and reconfirms compatibility before publication. Cancel before confirmation removes only the draft. Confirmed imports finish or recover before cancellation becomes available. Archive/expanded content each have a 2 GiB cap; metadata has an 8 MiB cap.
5. Keep historical media labels. Capture new UI, SAF reconstruction, runtime recovery and real save/reload evidence only when the phone is available for NsA.

本机开发服务已经迁移、重启并发布 2048 v2，正式服务仍需单独部署。保留原数据库、对象、签名信任与凭据，不重置序列或覆盖旧发布。其他环境须准备匹配内容后再安装新 APK。启动时先等待对账；活游戏未确认结束前不解除数据保护。导出先登记编号与快照，导入保留草稿与原目标游戏，确认后等待完成或恢复。下一步按[真机操作清单](device-validation.md)补测，旧截图与视频继续保留旧构建标签。
