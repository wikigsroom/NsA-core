# ADR 0007 · Policy, runtime ownership and recoverable save exchange

2026-11-07 · Implementation decision for APP-05, APP-08 and SAVE-04. Device acceptance is separate; see the [round record](../../hall/recovery-round-2026-11-07.md).

## Policy identity

The service allocates policy epochs from a durable database sequence under a database write fence. Epochs are opaque, strictly increasing snapshot identities; callers must not interpret them as timestamps. An unchanged visible release set and revocation set returns the exact previously signed payload, including its original issuance time. Official and internal visibility have separate stored snapshots and share one sequence.

The optional signed `visibility_scope` field (`official` or `internal`) extends policy schema v1. The current older Android verifier ignores additional payload fields, so it can consume these responses. Validators using the former closed schema must upgrade their schema before deployment. New clients accept older responses without the field in a separate `legacy` bucket. Existing snapshots are regenerated once with a new sequence when the scope field first appears; existing games and credentials are preserved. Production deployments run `services/migrate.py` before starting the new API. The development bootstrap initializes the sequence idempotently. No sequence may be reset to reuse an existing snapshot identity.

Android scopes policy facts by endpoint, installation identity and signed visibility. A Room transaction under the policy mutex compares the previous epoch and signed payload digest. Equal-epoch different payloads and lower epochs in that scope are rejected. The endpoint's known release decisions are merged by sequence, so an older or narrower scope cannot erase a known block. Known signing-key revocations are retained per endpoint. This implementation does not provide key unrevocation; adding it requires a separate explicit policy contract.

Installed release manifests remain bound to their stored signed digest and cryptographic signature. A revoked signing key alone does not invalidate these previously installed bytes; precise release blocking still applies. New downloads and other signed objects continue to reject revoked keys. Launch performs one final policy refresh, then checks the effective block again under the same policy mutex that owns the durable session claim. First use still requires a trusted policy. An installation's READY marker alone is not an offline-preparation certificate.

## Ownership and initialization

Every transfer worker awaits repository initialization. Initialization, installation recovery, activation, save recovery and destructive operations share the application operation mutex. The hall exposes an explicit initialization gate/error instead of showing actionable stale records. Startup checks installation marker, bound index and real file sizes; complete resource hashes remain an explicit integrity/repair operation. Legacy reserialized indexes can be verified through the service's canonical JSON representation. Paused/incomplete transfers recover their verified contiguous chunk prefix while retaining pause/cancel intent; completed history does not require a retained download archive.

The game process holds an exclusive kernel file lease for its entire lifetime, independent of the main process's memory. Its private persisted runtime context contains session ID, generation, nonce and selected bindings. Main-process recreation reconnects to the service using that identity. The service authenticates the handshake, replays its most recent phase/save events and never starts a second native instance. Unknown ownership retains data guards. Session termination becomes durable only after the lease is free; explicit termination targets the current application's own `:game` process, not an unchecked stale PID. Reconnection across actual main-process loss still requires device validation.

New service message numbers RECONNECT=7 and UNAVAILABLE=8 are private application IPC. Bootstrap ABI v1 and existing native event schema remain unchanged. Room schema v1 remains compatible: new data uses namespaced `facts` rows (`runtime-context:<session>`, scoped policies, `save-index:<game>`, `pending-save`, `catalogue-context`). No destructive Room migration is used.

## Save exchange

`saveRoot()` resolves a path without creating it. Every export, import, local backup and runtime preparation recovers the game's import journal before using that directory. The journal owns staging, the previous directory and the planned local index. Verified files and directories, rename boundaries and journal writes are synchronized. The Room index records a stable import ID and an incremented local revision. Recovery retains an indexed, fully verified new snapshot or restores the old one; repeating recovery does not repeat the revision. An ambiguous or incomplete recovery keeps the journal and blocks further save use. The unique pre-import copy wins over an empty directory created by an older exporter.

Import validation checks the full backup schema, field types, IDs, UTC timestamp, sequence, unknown fields, exact ZIP entry set, links/special entries, safe normalized paths, collisions, total size and every file digest. The complete declared sum is checked before extraction. An independent expansion budget is enforced before each write, with a 2 GiB expanded/archive cap. Space preflight includes staging, a backup of the existing save and a 128 MiB reserve. This unsigned player-backup format does not gain a platform-signature requirement.

Import provenance stores source release, compatibility group, archive hash, original commit sequence/time and local publication revision. It is not a native save event. Exports describe the current local revision instead of reusing the imported origin's sequence. A changed previously imported snapshot without a received native event is recorded as an observation, not as a fabricated native commit. An actual native commit updates a distinct native index with its session and native sequence.

Private exchange archives live in `files/save-exchange/`; a small durable `pending-save` fact owns the original game, operation ID, source route, phase, filename, bytes and digest. Large metadata/file lists never enter navigation or SavedState. Import confirmation can be restored and revalidated; retryable failure keeps the archive. Export creates its fixed snapshot before opening the destination picker. Callbacks use durable ownership instead of the page's current game selection. Copying can be resumed when a retained URI grant is available; otherwise the player gets a retry/reselection path. External partial exports can remain after provider failure and are not reported as successful.

After confirmation the entire import operation is non-cancellable in the UI, with visible processing state and disabled repeated confirmation, outside dismissal and cancel actions. Before confirmation, cancellation removes only the owned draft. This defines the cancellation boundary; hiding a dialog never cancels a captured background publication. Import completion does not launch or rotate the game.

The original 2048 slot owns `state.dat`; its historical `state.new` is excluded by that slot's authoritative file contract. New saves stage outside `committed`, remove failed staging and emit a save event only after synchronization. No global filename-suffix exclusion is imposed on other games. Under `DM_HALL_BINDING`, native raw import/export actions and deferred import requests are disabled; the portrait hall is the unified exchange entry. Standalone builds retain their original menu.

## Rollout and limits

Complete the source batch before consolidated tests, as required by ADR 0001. Rebuild affected native libraries, bind real library hashes, use a clean source revision, and issue a new immutable content release when a profile/module binding changes. Do not rewrite an already published development release. Existing APK screenshots and recordings retain their original identities. Passing host tests does not establish Android filesystem behavior, SAF recreation, actual IPC recovery, G0 game switching or new game progress save/reload.

## 中文执行约束

本决策落实三个 P0 需求的定向修正，不改变验收编号与正式发布门槛。策略采用稳定签名快照和数据库单调序列；新增可选签名字段区分公开/内测可见范围，旧服务响应进入独立兼容桶。已知阻断跨范围保留，首次可信准备仍然必需。正式服务先迁移数据库；闭合旧 schema 的验证器须同步升级。

初始化、安装恢复与写入共用操作锁；先核对真实文件再显示可操作大厅。游戏进程持有内核租约，大厅重建后按原会话和随机身份重连，确认结束前继续保护资源与存档。不能只把持久会话行改成“崩溃”就释放保护。

所有存档入口先恢复交换记录，再访问或创建提交目录。导入先完整校验清单和空间，逐次写入执行展开预算，备份原档，交换目录并登记可幂等的本地索引；失败恢复原档，或保留已登记且完整校验的新档。草稿归属、阶段、文件摘要和来源游戏持久保存；失败保留重试输入。导出选择位置前固定快照。确认导入后全程显示处理中并禁取消/重复提交；确认前取消只清草稿。统一大厅关闭原生菜单的旁路交换入口，独立游戏构建保持原行为。

上述代码变化必须完成集中构建与故障验证，真机和 G0 另行验收；不得把历史素材或本轮电脑端结果当成手机已经验证。
