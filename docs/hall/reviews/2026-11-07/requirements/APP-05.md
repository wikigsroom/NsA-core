> Archived review record; source snapshot: `cdedc7179ec1842592243a052525e021d8cf70f1`. Initial candidate/pending labels are historical. Final dispositions, including the superseding APP-05-D03 clarification, are in the [consolidated review](../../../source-review.md).
> 归档核对记录；初核中的候选/待复核状态保留历史，最终结论以汇总及独立复核的追加判定为准。

## APP-05 — 离线大厅、有效安装与持久阻断

> “无网络时可打开大厅和我的游戏；已安装且允许离线的内容仍可启动。首次没有目录缓存时明确解释，不伪造在线列表。”
> — docs/product/产品需求细则.md:L89-L102，APP-05 · P0

**Verdict:** partial · confidence: high

审计版本：cdedc7179ec1842592243a052525e021d8cf70f1。只读静态审计；未构建、未执行测试、未查询或操作手机、未调用浏览器/虚拟化。本文五个差异为作者分析，尚待根任务安排的独立反证；不能作为 AC-A04 或 G0 真机通过记录。

---

**What this demands of an implementation:**

| 子规则（逐字引用） | 静态结论 | 说明 |
| --- | --- | --- |
| APP-05.01：“无缓存离线时显示联网说明和空本地库；有缓存显示更新时间，不填造在线条目。” | partial | 没有伪造条目，空库可进入；无缓存错误条却声称正在展示缓存，S27 也没有缓存时间/无缓存分支。D05。 |
| APP-05.02：“已安装开放内容在无本地阻断且安装有效时可离线启动；目录刷新失败不能成为统一登录墙。” | partial | 有缓存策略的通常路径可离线；已撤销签名 key 的有效安装被一律拒绝，且成功安装但首次策略未缓存时仍被要求联网。D02、D03。 |
| APP-05.03：“联网恢复只刷新目录和任务，不自动启动游戏或改变页面方向。” | implemented | 重试与任务调度不调用启动/改方向；启动入口来自用户操作。不存在网络可用回调自动发起游戏。此结论是恢复副作用约束，不推断规范额外要求自动触发目录刷新。 |
| APP-05.04：“缓存失效和内容撤回是不同事实；已知阻断必须持久保存，不能被旧目录覆盖。” | partial | 缓存/策略分开，普通清缓存保留策略；但相同 epoch 的旧签名策略可以覆盖已知阻断，在线专用启动的最后一次策略更新还可绕过已经同步的阻断。D01、D04。 |

子规则原文：docs/product/产品需求细则.md:L97-L100；机器副本一致：docs/product/data/requirements.json:L165-L183。

补充约束：
- docs/engineering/07-build-release-operations.md:L47：“策略有单调 epoch，阻断状态和 revoked_keys 在普通缓存清理后仍保留。恢复旧目录缓存不降低策略；同 epoch 不同摘要拒绝。已知 revoked key 不能验证新内容；已安装内容如何处理由签名策略的精确 release 状态决定，不扩大删除全部游戏/存档。”
- docs/engineering/05-data-state.md:L28-L30、L43：目录缓存和 release_policy 独立；策略需存 epoch、signed_digest、issued_at、revoked_keys、blocked_versions，普通清缓存不删安全事实。L93 另要求旧 UI 不绕过后来同步的阻断。
- docs/product/产品需求细则.md:L1123-L1126、L1133-L1142：普通下架保留已安装开放内容使用；解除阻断需新可信记录；目录/配置失败不影响无已知阻断的离线内容。
- docs/engineering/03-runtime-integration.md:L29、L70、L76：建 session 前重查策略；取消后迟到事件不能再进入；启动检查默认 15 秒。
- docs/design/obsidian-portrait/03-screen-specs.md:L930-L945、L970：S27 有“缓存时间或无缓存说明”；S28 首开离线说明联网后发现游戏。
- docs/engineering/decisions/0005-client-guardrails-and-cache.md:L11：取消先使待启动尝试失效，清理等待数据库检查点；0006-durable-credential-renewal.md:L7-L11：网络/限流/服务器失败保留凭据尝试，只有权威 AUTH_EXPIRED 续签响应重置身份。

---

**Where enforcement lives:**

1. android/app/src/main/java/org/nsa/hall/data/HallRepository.kt:L30-L40、L48-L61：目录、错误、更新时间分别为 StateFlow；启动从 Room 的 catalogue/catalogue-updated 恢复数据，从单独 policy 事实恢复 revokedKeys。L63-L84：仅真实 API items 更新目录；请求失败只写错误，不删安装/策略；成功后尽力刷新策略。
2. android/app/src/main/java/org/nsa/hall/data/Database.kt:L7-L16、L29-L38：games、installs、sessions、facts 独立，policy 是 Room facts 的一个键；fact 读取和 Upsert 为分开的 suspend 操作，没有策略 CAS/条件 Upsert。
3. android/app/src/main/java/org/nsa/hall/ui/HallApp.kt:L147-L180：welcome 偏好写入后即可进入四入口；发现页错误展示和本地库读取分别来自目录 StateFlow 与 games；本地库不依赖新目录或凭据成功。L306-L312 的 S27 有重试、网络设置、我的游戏，但无时间/无缓存投影。
4. android/app/src/main/java/org/nsa/hall/data/Api.kt:L76-L109：API 自动游客凭据与续签。L82-L88 只在权威 401/AUTH_EXPIRED 后清 vault；断网、429、5xx、403 不清本地安装或 policy。L110-L126 使用 IO 中同步 OkHttp；没有调用取消 HTTP 的协程取消处理。
5. android/app/src/main/java/org/nsa/hall/data/HallRepository.kt:L140-L145 的全部策略提交代码：
~~~kotlin
suspend fun updatePolicies() {
    val envelope = api.request("/v1/policies"); val policy = trust.verify(envelope.toString().toByteArray(), "policy")
    val old = dao.fact("policy")?.value?.let(::JSONObject)
    require(old == null || policy.getLong("epoch") >= old.getLong("epoch")) { "拒绝过期的目录策略" }
    dao.putFact(Fact("policy", policy.toString()))
    trust.revokedKeys = policy.getJSONArray("revoked_key_ids").strings().toSet()
}
~~~
没有保存 envelope 的 payload_sha256，没有相同 epoch 摘要等值检查，也没有包住读取/比较/写入的策略锁或事务。
6. android/app/src/main/java/org/nsa/hall/data/HallRepository.kt:L148-L167：唯一新会话 preflight 的核心顺序如下：
~~~kotlin
// L153-L161
val manifest = JSONObject(install.manifest)
val authoritative = trust.verify(manifest.getJSONObject("_envelope").toString().toByteArray(), "release", manifest.getString("_verified_sha256"))
require(jsonEquivalent(authoritative, JSONObject(manifest.toString()).apply { remove("_envelope"); remove("_verified_sha256") })) { "本地安装信息已变更，请重新校验" }
require(manifest.getString("game_id") == gameId && manifest.getString("release_id") == install.releaseId)
runCatching { kotlinx.coroutines.withTimeout(15000) { updatePolicies() } }
val policy = dao.fact("policy")?.value?.let(::JSONObject)
require(policy != null) { "首次启动需要连接目录获取策略" }
require(policy?.getJSONArray("releases")?.objects()?.none { it.getString("release_id") == install.releaseId && it.getString("state") == "blocked" } != false) { "此版本已暂停运行，请检查游戏更新" }
if (!manifest.getBoolean("offline_permitted")) updatePolicies()
~~~
后续 L162-L167 只查 slot、minimum capability、READY 文件存在与空间，没有再读/判策略。完整资源哈希/损坏验收属于另一个 PKG 审计，本报告不把 READY 存在等同完整安装证明。
7. android/app/src/main/java/org/nsa/hall/security/Trust.kt:L52-L64：验证用途/算法/摘要/Ed25519 后返回严格 JSON；L56 对所有用途、包括已安装清单，无条件拒绝 revokedKeys。L66-L79 只处理内置模块匹配与库摘要，不另查 release block。SignatureVerifier.kt:L7-L13 仅执行密码学签名核验。
8. android/app/src/main/java/org/nsa/hall/runtime/SessionController.kt:L50-L80：显式用户 launch；L65 调用 preflight，L66 检查取消和尝试身份才建 session，L73/L79 再检查；L102 的绑定回调检查取消；L123-L128 准备完成后仍检查取消、生命周期与 preparing 状态；L162-L181 取消先设标志，清理等待 operationLock。
9. services/catalog/app.py:L263-L266：/v1/policies 由所有 release 的最大 updated 生成 epoch，issued_at 每次重新取当前时间，revoked_key_ids 当前固定 []。services/catalog/db.py:L19-L20、L56-L59 的 updated 为整数秒，release revision 虽递增，策略 epoch 并未使用它。app.py:L453-L466 的 publish/unlist/block/unblock 经权限与 revision 校验后调用 change。crypto.py:L31-L39 对最终原始 payload 签名，没有提供额外策略序列。
10. services/catalog/app.py:L177-L212：目录只从真实可见 release 构造，旧 snapshot 页再次过 visible。L99-L109 仅 published 且材料有效可新曝光；L232-L262 清单/授权/续签对 blocked 重查。这些服务检查保护新下载，不是客户端现有安装启动的补偿。
11. HallRepository.kt:L180-L184 只清 files/runtime-cache/<game>；HallApp.kt:L239 只清 cacheDir 的导出/导入临时数据；未找到删除 policy fact 的清缓存路径。AndroidManifest.xml:L12、L15-L17 分别固定大厅 portrait、游戏 sensorLandscape；刷新逻辑没有写 requestedOrientation。

---

**Paths walked:**

| 路径/条件 | 实际结果与证据 |
| --- | --- |
| 首次无缓存、无网络 → 进入大厅 | initialize 保持空 catalogue；welcome 先写偏好再尝试 refresh，失败保留四入口；库从 games 构造且没有伪造游戏。HallRepository.kt:L48-L59、L69-L84；HallApp.kt:L130-L180。错误文案仍错误声称有缓存，见 D05。 |
| 有 catalogue 缓存、刷新失败 | initialize 恢复原列表/时间；refresh catch 保留列表；发现页带上次 updated 值。HallRepository.kt:L51-L53、L84；HallApp.kt:L153。 |
| 有本地有效离线安装 + 有策略 + 无该 release 阻断 + 网络/凭据/服务失败 | installed envelope 与摘要检查后，尽力同步错误被捕获，读取保留策略；offline_permitted=true 不再强制在线；其余本地检查通过便返回 selection。HallRepository.kt:L151-L167。没有统一账号登录流程。 |
| 已知 blocked release，尽力在线请求失败或超时 | 原 policy 不因请求失败被删；L160 按 install.releaseId 拒绝，不能由旧 catalogue.summary 的状态绕过。目录和策略分别持久。 |
| 相同条件，收到真正更高 epoch 的 published/unlisted 策略 | 正常写入新可信策略后按最新精确 release 状态处理；允许可信解除阻断，不要求永久锁死旧 block。HallRepository.kt:L140-L160。 |
| 安装前新内容签名 key 已知撤销 | enqueue → Trust.verify 在写任务/发 grant 之前拒绝。HallRepository.kt:L107-L117；Trust.kt:L56。此新内容保护成立。 |
| 既有安装的签名 key 已撤销，但该 release 仍 published/unlisted | preflight 的同一 Trust.verify 在读取精确 release 策略之前拒绝；离线已安装内容被扩大阻断。D02。 |
| 安装签名 key 尚未在内存撤销，尽力刷新刚同步其撤销且 release 未 blocked | 清单验证发生在刷新前；本次可继续，后续启动在 L154 拒绝。因此 D02 还导致第一次/下一次行为不一致；不能据此主张本次放行违反“安装由精确 release 状态决定”。 |
| 已知 block，返回较低 epoch 策略 | require >= 失败，已知策略未覆写；尽力刷新捕获失败后 L160 继续拒绝，保护成立。 |
| 已知 block，返回相同 epoch 但不同 signed digest 的旧 published 策略 | >= 接受并整条 Upsert，block 消失；D01。 |
| 在线专用内容，第一次刷新 published，第二次刷新 blocked | L160 检查旧局部 policy；L161 同步新 block 后没有重查，创建新会话；D04。 |
| 在线专用内容，第二次必须在线刷新失败 | L161 异常向上传播，SessionController 报失败，不启动。不是离线内容的统一登录墙。 |
| 安装已 ready，但之前所有初始 /policies 请求都失败 | 安装过程无策略前置；断网后 L159 强制需要首次连接。D03。 |
| 联网恢复/用户重试目录 | HallViewModel.refresh → repository.refresh，只改目录/兼容投影/策略；任务受 WorkManager CONNECTED 重跑，并在 checkpoint 重读 run 意图、网络、计费条件和活会话。没有自动 launch。MainActivity.kt:L38-L44；HallRepository.kt:L126-L128；TransferWorker.kt:L31-L48、L55-L73。 |
| 用户暂停任务后网络恢复 | candidates 只取 intent=run，checkpoint 不修改 pause→run。TransferWorker.kt:L33、L55-L73。没有自动游戏启动或改方向。 |
| 用户在 preflight 的尽力在线刷新中取消启动，之后 HTTP 成功、失败或超时 | cancel 先置 cancelled，finish 置 finishing 并等待 operationLock。即使 runCatching 吞掉刷新异常，preflight 返回后 L66 仍阻止建新 session；若取消发生于检查后的 DB 窗口，L73 阻止继续，等待中的 finish 会读回并终结同 ID 的已写 session。不会抵达有效 PREPARE/打开游戏。SessionController.kt:L64-L79、L162-L181。 |
| 已写 session 后取消；Binder/PREPARED/运行事件迟到 | L79/L102/L126 的标志、L133 的 ID/generation/nonce 与 launching、L144 的取消/代次检查共同阻止迟到拉起。没有把“runCatching 捕获取消”孤立误报为能自动进入游戏。 |
| 整个 launch 协程取消（不同于 UI cancel） | runCatching 捕获取消后仍处于已取消上下文；Room 后续 suspend/外层 withContext 的完成会传播取消，SessionController:L119 作取消分支。没有证据表明会绕过外层 session/attempt 检查。 |

**取消与时限的边界：**Api.kt:L115 是同步 execute，withTimeout 本身没有对 HTTP call 的取消钩子；本报告不把 15 秒包装视为真实网络硬截止，也不声称 UI 取消即时完成。取消期间 finish 等待 operationLock 的时长需集中测试。已证实的是迟到网络结果不能越过 SessionController 的取消门，故没有单独“取消后自动启动”发现。

---

**Searched:**

- 范围：android/app/src/main/java、AndroidManifest.xml、services/catalog、services/tests、android/app/src/test 与 androidTest；规范主源及 0005/0006 ADR。未搜索无关游戏二进制、忽略构建/下载/产物内容。
- updatePolicies：主源码 4 处——定义 HallRepository.kt:L140；刷新目录 L83、preflight 尽力刷新 L157、在线专用刷新 L161。没有另一个策略消费者替 L161 做最终启动复检。
- fact("policy") / Fact("policy")：HallRepository.kt:L56（初始化恢复撤销 key）、L142（旧 epoch）、L144（写入）、L158（启动读取）；Database.kt:L37-L38 为通用 DAO。全部读写已追踪。
- revokedKeys：Trust.kt:L48/L56 声明与无条件 key 过滤，HallRepository.kt:L56/L145 两处赋值；没有“已安装且 digest 固定”的特殊验证路径。
- policy_scope / signed_digest / policy.*digest / Policy.*Digest：Android 与 services/catalog 均 0 命中；与完整阅读 L140-L145、服务策略分支共同确认缺少相同 epoch 的摘要冲突防护。并非只靠未命中推断。
- DELETE FROM facts / delete.*Fact：Android 主源码 0 命中；clearCache、storage temp 清理与 MaintenanceWorker 全部读取，均不删 policy。
- NetworkCallback / onAvailable / registerDefaultNetworkCallback / CONNECTIVITY_ACTION：Android 主源码 0 命中；网络恢复任务机制为 WorkManager CONNECTED；目录靠初始化或显式 retry/refresh。未凭空要求产品文案之外的自动目录回调。
- controller.launch / GameActivity::class / requestedOrientation / screenOrientation：MainActivity.kt:L43 的显式命令；SessionController.kt:L53（返回现游戏）、L127（已发起且准备好的会话）；方向只见 manifest。没有网络路径创建 GameActivity。
- offline_permitted / preflight：HallRepository.kt:L148-L167 的所有成功/拒绝分支完整读取；调用方 SessionController.kt:L50-L137 及其取消/finish 路径完整读取。trust.slot、meetsMinimum、Api 凭据/请求分支和 SaveManager.recover 均追踪至实现。
- epoch / revoked / policy / offline / cancel / blocked：现有自动测试只覆盖 Ed25519 域隔离、重复 JSON key、目录快照身份绑定、凭据续签；未找到已知 block/revoked key、离线初始策略、相同 epoch 冲突、第二次策略刷新或取消在线请求的启动回归用例。NavigationRecoveryTest 只在设置页重建 Activity；DeviceContractTest 只核对能力字段。测试源码检查不等同执行。
- 服务 route 通过 OpenAPI 动态登记（app.py:L494-L563）；getReleasePolicies 的 bearer 定义在 contracts/openapi.json:L569-L608。读取的是实际 dispatch 与认证链，不只路由名称。
- 初次目录搜索曾尝试不存在的 service/server/app 顶层目录；识别实际目录为 services 后重做范围搜索。未将不存在的目录算作“实现缺失”的证据。

---

**How the verdict was reached:**

partial 而非 absent/contradicted：通常离线路径、独立持久存储、真实空库、旧 epoch 拒绝、手动启动与取消令牌确实存在。失败集中于明确边界，不能把通常路径的成功推成四规则全部完成。相同 epoch 防护是规范明确要求，不能用当前服务通常无重放来替代客户端拒绝。

| ID | 严重度 | 子规则 | 具体差异 |
| --- | --- | --- | --- |
| APP-05-D01 | High / P1 | .04 | 策略相同 epoch 不同摘要可覆盖已知 block；服务整数秒 epoch 可合法生成这种冲突。 |
| APP-05-D02 | Medium / P2 | .02 | 全局 revoked-key 过滤被用于既有安装，忽略“精确 release 状态决定已安装内容”的规则。 |
| APP-05-D03 | Medium / P2 | .02 | 可已完成有效安装但没有初始 policy；离线启动仍要求连接，即使签名 manifest 允许离线且没有已知阻断。 |
| APP-05-D04 | High / P1 | .04 | 在线专用启动的第二次刷新能同步 blocked 后继续启动，最后检查用的是之前的 policy。 |
| APP-05-D05 | Low / P3 | .01 | 无缓存错误也显示“正在展示缓存资料（时间未知）”，S27 缺少缓存时间/无缓存事实。 |

**APP-05-D01 — 策略身份/单调提交不足。**
触发：在同一个整数秒 T 内取得 release R=published 的签名策略 A，管理员随后 block R，取得 B；两者 epoch 均可为 T（app.py:L264、db.py:L57-L59），但 payload_sha256 不同。客户端先存 B，再收到旧 A 时，L143 的 >= 接受 A，L144 将 block 整条覆盖；随后离线 L160 看到 published 可新启动。不需伪造签名。更一般地，服务在状态不变时也每次重新 issued_at，已违背“一 epoch 一摘要”的快照身份（app.py:L266、crypto.py:L34-L39）。读/比较/Upsert 没有事务/锁还无法对并发 policy 更新保证原子单调；不以 UI 按钮防抖作为补偿。
影响：已知紧急阻断可倒退为允许启动；不是声称任意互联网攻击者能穿过 HTTPS 注入响应，必要条件是较旧有效签名响应被迟到交付/重放。
最小修正：服务用持久策略序列和稳定快照字节，任意语义变更取新 epoch，同 epoch 保持原始字节/摘要；客户端保存签名 payload digest，相同 epoch 必须同 digest，低 epoch 拒绝，读取/比较/落库原子完成。范围隔离需要与工程表的 policy_scope 一致，不能为了稳定摘要公开内部 release。
集中测试建议：冻结时钟，同秒 published→blocked 必须产生新序列；相同 epoch 相同 payload 幂等，不同 digest 拒绝并保留 block；低序列/迟到响应与并发提交不能降低策略；普通缓存清理、重启后 block 和 revoked keys 仍在。

**APP-05-D02 — 将“撤销新内容签名能力”扩大为“停用全部既有安装”。**
触发：R 已验证安装，manifest.offline_permitted=true；已持久化可信策略撤销该清单签名 key K，但 R 的精确状态仍 published 或 unlisted。initialize 把 K 加入 revokedKeys（L56），下次 preflight:L154 调用 Trust.verify，Trust.kt:L56 立刻拒绝，尚未读取 R 的策略。其他同 key 的已安装 release 同样被拒绝。
影响：无精确 release block 的有效离线安装被停用；更高策略即使重新确认 R 可使用，也到不了精确检查。规范将撤销 key 对新内容的作用与既有安装处置明确分开。
最小修正：为已验证安装保留并绑定既有 manifest digest/来源；安装启动验证允许核对这个固定已安装 payload 的密码学完整性，并按当前精确 release 策略授权。新 manifest/新下载仍必须拒绝 revoked key；不可全面取消 key 检查。
集中测试建议：撤销 K 后，K 的新内容拒绝，K 签名但已固定 digest 的未 blocked 安装可离线，精确 blocked 的安装始终拒绝；同步撤销前后与重启后行为一致。
限制：当前服务 app.py:L266 固定发送空 revoked_key_ids，因此这是规范允许且 Trust 显式支持的“已知撤销”状态下的确定性分支缺陷，不声称当前手机已有这种生产策略或已观察到实际停用。有效轮换策略须由仍可信的策略 key 签发；不要求客户端信任已经撤销的 key 来签新策略。

**APP-05-D03 — 初始策略失败会给 offline_permitted 安装增加隐式联网条件。**
触发：首个目录获取和兼容评估成功，随后 /v1/policies 单次故障。refresh 已存目录再吞掉策略失败（L77-L83）；detail/enqueue（L86-L118）和 Installation.activate（Installation.kt:L98-L103）均不要求策略就绪，可正常把完成且校验过的安装写为 ready。用户断网后首次启动，尽力策略请求失败，preflight:L159 无条件要求 policy 非空，显示“首次启动需要连接目录获取策略”。这不需要人为删除数据库或改 manifest。
影响：签名允许离线且无本地 block 的已安装开放内容，在其他准备已完成后仍因目录配置失败不能离线使用。详情只说“首次准备完成后支持”（HallApp.kt:L210），没有把“首次实际启动还需单独取策略”作为特殊联网许可展示。
最小修正：对允许离线的已验证开放安装，明确处理“从未同步策略”与“已知 block”，不能用缺少任意 policy 作统一网络门；若产品安全设计要求首次离线授权，应在准备提交前持久化该授权，未完成时不得先声称 offline-ready，且同步规范/页面。已持久化的 block 不得因此放行；online-only 仍要求在线。
集中测试建议：仅让初次策略请求失败，其余目录/清单/下载/安装成功，断网后验证已完成离线安装的行为；用“无策略/允许/blocked”三种持久状态覆盖网络不可达、AUTH_EXPIRED 续签失败和 403，不得删既有阻断来通过。

**APP-05-D04 — 最后一次在线同步没有再次执行 block 判定。**
触发：manifest.offline_permitted=false。第一轮 L157 更新并读取 published 策略，L160 放行；管理员此时阻断 R；L161 的必须在线更新取得并成功持久化 blocked。L162-L167 不读新策略即返回；SessionController.kt:L66-L70 只查取消后新建 session，L80 的 trust.slot 也不查 block。最终会把已知被阻断的 release 交给运行服务。
影响：已经同步的明确阻断仍可被一次新启动绕过，违背 next sync 后阻止新启动及本地已知阻断优先。下一次尝试会被挡，不弥补本次会话。
最小修正：按 offline_permitted 选择一次必要的刷新策略，再从最终可信快照执行精确 release block 检查；启动提交与策略更新有明确的版本/锁边界，不能在最终判定之后再无检查地刷新。
集中测试建议：两次策略读取分别返回 published 和 blocked 时必须没有 SessionRecord/服务 PREPARE；最终刷新失败的 online-only 内容也应停止；offline-permitted 的失败刷新继续使用保留的未阻断策略。

**APP-05-D05 — 首次离线错误的信息不真实。**
触发：全新库无 catalogue 和 catalogue-updated，进入大厅后 API 失败。HallApp.kt:L153 仅以 networkError 非空为条件，无缓存判断，显示“正在展示缓存资料（时间未知）”；L168 的空库说明也没有首次离线联网提示。进入 S27（L306-L312）既无缓存时间也无“尚无目录缓存”说明。
影响：没有伪造游戏条目，也没有数据损失；但用户被告知正在展示不存在的缓存，无法正确区分首次离线与旧资料。按实际后果为 Low。
最小修正：用持久缓存是否存在的事实分开文案；无缓存明确“联网后获取目录”，保留我的游戏入口；有缓存展示实际更新时间。不要以 catalogue 当前是否空代替缓存存在性，因为合法空目录也可能已缓存。
验证建议：实现后在集中 UI/人工验收中对照首次无缓存断网、有缓存断网、已缓存空目录和服务器故障的文案与本地入口即可，无需为简单文案变化增加镜像式单元测试。

---

**Open questions:**

- 没有影响上述源码分支结论的未读关键调用方；所有差异仍需独立反证。D02 的 revoked-key 条件明确不是当前服务已发出的生产状态。
- 本次不运行测试，故网络真实时限、取消反馈延迟、进程/Activity 实际重建、方向及实际游戏存读档均未验收。现有代码映射和历史文档不能代替 AC-A04/G0。
- .03 按“联网恢复不自动拉起/旋转”检查；代码没有自动网络可用时刷新目录的观察器，规范本句未显式要求自动刷新机制。若产品需要无需用户点击即刷新目录，应另行明确该触发条件；不把这个解释差异伪造成已确认实现缺陷。
