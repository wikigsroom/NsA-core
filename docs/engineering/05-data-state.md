# 05 · 数据模型、状态与一致性

新增实施约束见 [ADR 0008](decisions/0008-transfer-ownership-and-install-recovery.md)：任务所有权持续至完成/取消清理确认；条件 SQL 隔离用户意图与进度；稳定排序和持久接续；完整候选验证、逐目录同步及保留日志的安装清理。Room schema 2 与 Android journal_schema 2 是各自独立的本地版本，保留旧版迁移/路径适配。原规范和验收编号不变；实际结果见[修正轮次](../hall/transfer-round-2026-11-07.md)。

基线 v2.0 · 2026-11-06。关联 NFR-07、CAT-07、DL-01～14、PKG-04～09、RUN-01～11、SAVE-01～08、UPD-01～07。字段类型与线上 DTO 见 [domain.schema.json](contracts/domain.schema.json)，持久化记录见 [local-records.schema.json](contracts/local-records.schema.json)。本章是建库约束，不表示数据库已经实现。

## 1. 身份、数值与时间

| 概念 | 唯一性与用途 | 禁止混用 |
| --- | --- | --- |
| game_id | 稳定逻辑游戏身份；受控 ASCII 标识 | 名称、商店展示名、文件夹名不能充当身份 |
| release_id | 某一不可变内容版本 | 修改字节、模块依赖、存档关系必须生成新版本 |
| runtime_slot_id / build_id | APK 注册表中的模块身份及精确构建 | 不按当前文件名“差不多相同”放行 |
| local_player_id | 本安装的本地档案；P0 默认一个 | 不使用 IMEI、ADB 序列号或服务端安装 ID 替代 |
| task_id / operation_id | 本地内容操作和服务端作业分别标识 | 服务端授权会话不等于本地下载任务 |
| session_id + generation | 一次运行尝试及本地主进程协调代次 | PID 会复用；不能作为会话唯一凭据 |
| command_id / Idempotency-Key | 一次用户意图的幂等标识 | 重试沿用；新意图、变更请求体重新生成 |

线上所有字节数使用十进制字符串 `ByteCount`，范围 0～9223372036854775807；拒绝负数、小数、指数、前导零与溢出。Android/SQLite/PostgreSQL 使用有符号 64 位整数，运算先检查加减乘溢出；浏览器不能先转 JavaScript Number 再计算。版本序号与 revision 是有界整数，哈希是小写 SHA-256，Git revision 允许 40 或 64 位十六进制，二者不能互换。

持久化时间统一 UTC RFC3339 `Z`；UI 按设备时区显示。超时与速度采样用单调时钟，不以玩家修改墙钟计算。跨重启的到期时间用可信服务时间与本地采样关联；时间不可确认时不延长临时授权。日志同时保留 request_id、事件时间与本地单调序号。

## 2. Android Room 表

实体、迁移及索引须在首次实现时导出 Room schema。以下是最小必需字段；新增字段可通过迁移扩充，不得删掉保护约束。

| 表 | 主键、主要字段 | 必需约束与访问规则 |
| --- | --- | --- |
| local_player | player_id；created_at | P0 一个默认档案；删除需独立明确动作 |
| game_cache | game_id；展示元数据、目录 revision、fetched_at、可见范围 | 只作目录缓存；删除不删安装与存档 |
| release_cache | release_id；game_id、manifest_digest、runtime_binding、save_group、policy_epoch | 一份发行只能对应一个 digest；已签内容与展示字段分开 |
| catalogue_snapshot | cache_key；query/filter、cursor、snapshot、etag、context_id、fetched_at | cache_key 含渠道、范围、语言、兼容上下文；不能跨用户范围复用 |
| device_capability | capability_id；App/OS/GPU/driver/ABI、probe_state、measured_at | 显式保留 unknown，慢探测不阻塞首页；系统/驱动变化失效 |
| content_operation | operation_id；game_id、target_release_id、kind、status、revision | 同一游戏最多一条未结束操作；覆盖下载到安装全过程 |
| transfer_task | task_id；operation_id、release_id、object_id、manifest_digest、state、user_intent、blockers、durable_bytes、total_bytes、attempts、checkpoint | 字节不超过总量；URL/令牌不进入可导出记录；仅协调器写入 |
| transfer_chunk | (task_id, chunk_no)；offset、length、digest、durable_verified | 已验证位只能在相应字节 fsync 后提交；缺块不能计为耐久完成 |
| install_journal | install_id；operation_id、release_id、phase、staging_name、final_name、previous_active、revision、error | 阶段单调推进；保存恢复所需事实，路径必须由固定根派生 |
| installed_release | (game_id, release_id)；manifest_digest、state、ready_digest、allocated_bytes、installed_at | READY 文件、清单、索引、模块及 DB 事实匹配才 ready |
| active_release | game_id；release_id、activation_revision | 外键指向已验证版本；同一游戏一个激活版本 |
| runtime_session | session_id；game_id、release_id、slot、generation、state、last_seq、process_identity、source_route、exit_reason | 全局仅一个非结束会话；绑定启动版本，不跟随 active_release 变化 |
| session_event | (session_id, generation, seq)；type、received_at、payload_digest | 同键重放幂等；旧代次和越界事件拒绝，不延迟触发启动 |
| save_commit | (game_id, player_id, commit_seq)；release_id、save_group、digest、committed_at | 只有原生提交完成后追加；目录存在不等于已提交 |
| save_backup | backup_id；game/player、commit_seq、location_kind、uri_handle、digest、status | 私有备份/外部导出分开；外部位置失效不伪称可恢复 |
| game_preferences | (game_id, player_id)；schema_version、control/graphics/audio、revision | 白名单设置；每游戏隔离，未知字段保留或明确迁移 |
| release_policy | policy_scope；epoch、signed_digest、issued_at、revoked_keys、blocked_versions | epoch 不回退；普通清缓存不得删除安全阻断事实 |
| pending_result | request_id；source_route、action、game/session、status | 系统文件选择器、权限和更新安装结果可恢复；不在导航参数塞大文件 |
| feedback_draft | draft_id；description、contact、attachment_selection、consent_version | 可离线编辑；同意前不上传诊断，提交失败保留草稿 |
| command_receipt | command_id；aggregate_id、body_digest、result、revision | 同键异体冲突；完成回执便于重入，不重复排队/删除/启动 |

Room 是本地业务事实来源；DataStore 仅保存一般偏好、引导记录与非任务配置。凭据用系统密钥支持的受保护存储，不作为明文偏好、日志或备份输出。路径和 URI 在调试输出中脱敏。

核心唯一约束须在数据库和协调层共同实现：

```sql
CREATE UNIQUE INDEX one_content_operation_per_game
ON content_operation(game_id) WHERE status = 'active';
CREATE UNIQUE INDEX one_live_runtime_session
ON runtime_session((1)) WHERE state IN
('checking','launching','running','suspended','exiting');
CREATE UNIQUE INDEX one_active_release ON active_release(game_id);
```

Room 对表达式/部分索引的声明支持若受所锁版本限制，在显式迁移 SQL 中建立并检测；不得因注解不便而省略。`content_operation` 只有安装完成/明确取消/最终放弃并清算所有权后结束，不能在 transfer=downloaded 时释放唯一锁。failed 任务保留操作所有权直到重试或用户放弃，避免重复下载另一版本与旧安装冲突。

## 3. 服务端 PostgreSQL 模型

| 聚合 | 最小记录 | 事务边界与索引 |
| --- | --- | --- |
| 身份 | installations、refresh_credentials、principals、role_bindings、access_grants | refresh token 仅存摘要；轮换与重用检测；角色变更有审计 |
| 内容 | games、game_assets、aliases、rights_records、rights_reviews | game_id 唯一；检索索引；范围材料私有；审核记录追加 |
| 构建 | builds、input_inventories、runtime_slots、artifacts | source/core/profile/input/toolchain 摘要；对象身份不可变 |
| 发行 | releases、release_objects、release_reviews、release_scopes、policy_versions | release_id 唯一；revision 比较更新；已签 payload 不原地改 |
| 兼容 | device_profiles、compatibility_evidence、capability_contexts | release/slot/profile/driver/OS/输入模式精确组合，证据可追溯 |
| 交付 | download_sessions、grant_events、idempotency_receipts | session 固定对象、发行和清单；续签重新查范围/阻断 |
| 作业 | operations、job_leases、outbox | 验证/签名/清理至少一次执行；租约、attempt、可重入结果 |
| 支持 | feedback_tickets、diagnostic_objects、ticket_events | 安装身份隔离；诊断就绪后方可关联；内容最小授权 |
| 审计 | audit_events、security_events | actor/对象/前后 revision/原因/request_id；普通角色不能更改历史 |

数据库约束包含外键、非负字节数、有限状态检查、唯一对象版本和操作幂等键。公开读模型不直接序列化 ORM 行，敏感材料、内部路径、输入摘要明细与令牌不能透出。搜索列表可维护只读投影，但发放下载、签名与发布决策必须回查权威聚合。

元数据、审核结论、状态变化和 outbox 在同一数据库事务提交；对象上传、长时间哈希、签名调用与 CDN 失效在事务外执行，通过幂等作业和结果回写衔接。不跨网络持有行锁。worker 崩溃后租约到期可重做，但必须检查同一 operation 的完成标记，不产生重复发行或重复通知。

## 4. 状态机与动作

权威枚举和允许边在 [state-machines.json](contracts/state-machines.json)。四个聚合分别表达传输、安装、会话、发行；它们不能合并成一个 `downloadStatus`。

| 聚合 | 关键语义 | 不能由此推导的事情 |
| --- | --- | --- |
| transfer.downloaded | 归档与分段完整性已验证 | 安装已完成、可以开始游戏 |
| install.prepared | 版本目录已验证并发布，但未激活 | 当前 active_release 已切换 |
| install.ready | 完成激活事务与 READY 复检 | 游戏已运行或性能已通过 |
| session.running | 接收可信 runtime_entered，交互子阶段继续独立记录 | first_frame/interactive/存读档均成功 |
| release.published | 此版本范围内可被交付 | 每台设备都已验证可玩 |

主按钮由 [02](02-android-client.md) 的动作解析器依据同一快照计算。快照应包含 revision；用户点下时命令层重新验证，旧 UI 快照不能绕过后来同步的阻断。错误码及建议恢复动作见 [errors.json](contracts/errors.json)，禁止把原生错误文本直接展示给用户。

阻塞条件是集合，用户意图另存。例如网络恢复只能清除 network，不能清除 user 或 storage。状态表示当前主要原因，显示优先级为：取消/终止错误 → 用户暂停 → 空间 → 授权 → 网络/计费 → 系统/运行资源策略 → 可执行。其他原因仍可展开查看。任何原因变化都不能自动把 `user_intent=paused` 改成 run。

## 5. 文件记录与数据库的恢复

READY、安装日志和存档提交记录都具有 schema、game/release、摘要与 revision；见 [local-records.schema.json](contracts/local-records.schema.json)。文件记录不存玩家令牌、临时 URL 或原始设备标识，记录路径是固定根下的相对名称。

启动对账顺序：读取安全策略和迁移状态 → 扫描未完成安装日志 → 检查当前会话是否仍有真实活进程/受控 Binder → 对账 READY/active_release → 恢复任务与待处理系统结果 → 发布 UI 快照。不能一边显示 ready、一边异步发现 active 文件不存在。只做必要元数据检查，完整资源哈希在安装或明确修复时流式执行。

文件原子 rename 不等于 DB 原子提交；逐阶段恢复以 [04](04-transfer-install-save.md) 为准。发现未知目录仅登记为孤立候选，不执行、不自动导入存档、不立即删除。可清理数据须经持有者、活会话和引用计数复查；symlink、根越界或伪造 READY 一律隔离为 repair_required。

存档权威来自原生提交与经过验证的备份，不以 Room 声明“保存成功”代替磁盘事实。导入事务在安全暂存区验证、备份旧档、持久化交换日志后切换；重启在旧/新完整提交间恢复，不留下半个目录。

## 6. 并发、事件与迁移

命令处理先查 command_receipt，再在短事务中检查聚合 revision 和允许转换。会话事件按 session_id/generation/seq 判定，重复事件返回已处理结果；先到 exit_complete 后到 first_frame 不能复活会话。失败取消后到达下载授权只能释放，不再创建任务。

锁顺序统一为运行租约 → 游戏锁 → 存档锁 → 短 DB 事务。下载与安装可预留资源，但准备版本不能激活到正在使用的路径。不得通过墙钟 TTL 强抢仍存活进程的会话租约；以 Binder 死亡/操作系统进程退出和最终资源确认关闭。

数据库每次升级须提供从所有仍支持线上版本到新版的迁移路径及数据样本验证；禁止 `fallbackToDestructiveMigration` 处理生产玩家数据库。迁移失败进入可恢复维护页，保留原库/存档和诊断，不启动新游戏/清库重建。数据库降级不默认支持；内容回退不等于 App/数据库降级。[Room 迁移说明](https://developer.android.com/training/data-storage/room/migrating-db-versions)。

## 7. 数据保留与清理

缓存、临时传输、已安装资源、设置、存档、私有备份、外部导出分别计量。清理操作生成声明清单和预计可释放实际占用，执行时再验证所有权与活会话；取消/重复执行幂等。不能从 `filesDir` 整根递归删除来实现“清缓存”。

本地日志默认 50 MiB/7 天先到者滚动，单次诊断最多 10 MiB。未关联的诊断暂存与失败上传由清理作业按 TTL 清算；诊断默认服务端保留 30 天，最终地区与保留由 DEC-09 决策。清理成功需记录完成时间；数据库行删除不代表对象字节已清除，服务端通过对象删除结果对账。
