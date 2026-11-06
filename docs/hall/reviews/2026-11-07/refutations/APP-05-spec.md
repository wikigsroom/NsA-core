> Archived review record; source snapshot: `cdedc7179ec1842592243a052525e021d8cf70f1`. Initial candidate/pending labels are historical. Final dispositions, including the superseding APP-05-D03 clarification, are in the [consolidated review](../../../source-review.md).
> 归档核对记录；初核中的候选/待复核状态保留历史，最终结论以汇总及独立复核的追加判定为准。

# APP-05 — independent specification refutation

Source: `cdedc7179ec1842592243a052525e021d8cf70f1` in `.`.

仅反证既有 finding 的规范依据、优先级与建议范围；不新增 finding，不独立确认代码或实机触发。

Outcome: 4 survives, 1 narrowed, 0 refuted, 0 unverified. “survives” 仅表示规范依据经反证保留，不表示代码/设备触发已验证。

## Authority and scope

- [docs/engineering/README.md:25](https://github.com/wikigsroom/NsA-core/blob/cdedc7179ec1842592243a052525e021d8cf70f1/docs/engineering/README.md#L25) (authority): “产品/页面定义行为，机器契约固定线上/本地格式和状态，技术章固定实现约束，设计源固定视觉。冲突需同步修订，不选一个旧稿绕过另一规范。”
- [docs/product/README.md:24](https://github.com/wikigsroom/NsA-core/blob/cdedc7179ec1842592243a052525e021d8cf70f1/docs/product/README.md#L24) (scope): “90 项中 85 项 P0、5 项 P1；P1 未默认进入首版。”
- [docs/product/游戏大厅二次开发需求说明书.md:437](https://github.com/wikigsroom/NsA-core/blob/cdedc7179ec1842592243a052525e021d8cf70f1/docs/product/%E6%B8%B8%E6%88%8F%E5%A4%A7%E5%8E%85%E4%BA%8C%E6%AC%A1%E5%BC%80%E5%8F%91%E9%9C%80%E6%B1%82%E8%AF%B4%E6%98%8E%E4%B9%A6.md#L437) (scope): “其中 P1 仅 CAT-10、DL-15、RUN-12、SAVE-07、UPD-08。其余 85 项为 P0；规划表中的“账号”等未单独编号扩展不自动成为 P0。”
- [docs/product/data/defaults.json:4](https://github.com/wikigsroom/NsA-core/blob/cdedc7179ec1842592243a052525e021d8cf70f1/docs/product/data/defaults.json#L4) (normative-default): “"classification": "engineering defaults; change through ADR/CR; not claims of achieved performance or commercial approval"”
- [docs/engineering/decisions/0001-integrated-development-round.md:5](https://github.com/wikigsroom/NsA-core/blob/cdedc7179ec1842592243a052525e021d8cf70f1/docs/engineering/decisions/0001-integrated-development-round.md#L5) (current-ADR): “The user requires every development round to implement its complete planned scope before testing and debugging, instead of cycling through individual modules. This supersedes the earlier ordering that required G0 before writing the broad hall. It does not relax G0 acceptance: two real games, actual save/reload, process/data isolation and safe return are still mandatory before claiming the platform is verified.”

- APP-05 是 P0 产品义务；finding 的 P1/P2/P3 是修复优先级，不是把需求降为产品 P1。
- 工程默认值是当前可执行默认，可由 ADR/CR 改变；本报告不把默认等同已验收事实。
- ADR0001 仅改变整批开发/整批测试顺序。ADR0002 增补 App 更新/角色 API；0003 构建与设备能力；0004 大包验证/公开 demo；0005 客户端互斥与缓存；0006 匿名凭据续签。未见任何一项授权相同 epoch 换摘要、撤销 key 即统一停用已装发行，或放行已同步 blocked。
- 实施手册/验证记录更新了旧稿中“尚未实现”的时点叙述，不使产品和契约硬约束自动失效。既有测试通过不能反证未覆盖分支。
- 未来账号/有限期资格不默认进入首版；本报告不要求新增账号授权或在线资格产品。

| Finding | Outcome | Retained severity / priority |
| --- | --- | --- |
| APP-05-D01 | survives | high / P1 |
| APP-05-D02 | survives | medium / P2 |
| APP-05-D03 | narrowed | medium / P2 |
| APP-05-D04 | survives | high / P1 |
| APP-05-D05 | survives | low / P3 |

## APP-05-D01 — survives

**Demand.** 相同 policy scope 的已验证策略 epoch 不得回退；同 epoch 的不同签名 payload 摘要必须拒绝；已知精确阻断不能被旧可信响应移除。

**Exact specification evidence.**

- [docs/product/产品需求细则.md:100](https://github.com/wikigsroom/NsA-core/blob/cdedc7179ec1842592243a052525e021d8cf70f1/docs/product/%E4%BA%A7%E5%93%81%E9%9C%80%E6%B1%82%E7%BB%86%E5%88%99.md#L100) (mandated): “缓存失效和内容撤回是不同事实；已知阻断必须持久保存，不能被旧目录覆盖。”
- [docs/engineering/07-build-release-operations.md:47](https://github.com/wikigsroom/NsA-core/blob/cdedc7179ec1842592243a052525e021d8cf70f1/docs/engineering/07-build-release-operations.md#L47) (mandated): “策略有单调 epoch，阻断状态和 revoked_keys 在普通缓存清理后仍保留。恢复旧目录缓存不降低策略；同 epoch 不同摘要拒绝。”
- [docs/engineering/05-data-state.md:43](https://github.com/wikigsroom/NsA-core/blob/cdedc7179ec1842592243a052525e021d8cf70f1/docs/engineering/05-data-state.md#L43) (mandated): “release_policy | policy_scope；epoch、signed_digest、issued_at、revoked_keys、blocked_versions | epoch 不回退；普通清缓存不得删除安全阻断事实”
- [docs/engineering/contracts/openapi.json:572](https://github.com/wikigsroom/NsA-core/blob/cdedc7179ec1842592243a052525e021d8cf70f1/docs/engineering/contracts/openapi.json#L572) (contract): “"summary": "Read latest signed monotonic policy snapshot"”
- [docs/product/产品需求细则.md:1126](https://github.com/wikigsroom/NsA-core/blob/cdedc7179ec1842592243a052525e021d8cf70f1/docs/product/%E4%BA%A7%E5%93%81%E9%9C%80%E6%B1%82%E7%BB%86%E5%88%99.md#L1126) (mandated): “解除阻断也需新可信策略记录，旧缓存不能覆盖已知更高序号。”

**Attempts to refute.**

- 两份响应都签名有效，所以都可以接收。 → **refuted**。签名真实性不替代单调版本/同序号摘要的语义检查；07:L47 明确单独要求拒绝同 epoch 不同摘要。
- 规范只禁止更低 epoch；同 epoch 的 published 可以解除 blocked。 → **refuted**。同 epoch 不同摘要拒绝是明确文字。解除阻断也需新可信策略记录；不能用同序号另一份 payload 偷换事实。
- HTTPS 或响应正常按序使风险不存在。 → **scope-limited**。实际可达的迟到/重放需代码反证确认；规范仍要求拒绝输入。原 finding 已限定有效签名旧响应，不主张伪造签名或绕过 HTTPS。

**Retained scope.** 仅对同一策略范围、同 epoch 不同 payload digest 的覆盖和可导致阻断回退的迟到/重放分支成立。高严重度依赖代码反证确认该分支可移除已知 block。

**Excluded claims.**

- 不推定网络攻击者可破坏 TLS 或伪造 Ed25519。
- 不要求在完全离线设备上即时获得新阻断。
- 不以未定义的跨 scope 全局序号作唯一设计。

**Safe correction boundary.** 服务给签名策略分配不会复用到不同 payload 的单调序号；客户端持久化 scope/epoch/signed digest 并原子比较，同 epoch 相同摘要可幂等，同 epoch 不同摘要拒绝。具体序号存储方案可自行选择。

**Consolidated validation needs; not executed.**

- 构造同 scope 同 epoch 两个不同有效签名策略，先 blocked 后 published，确认后者被拒且重启后阻断仍在。
- 同 epoch 同 digest 重试仍幂等；更高可信 epoch 可以显式解除指定 release block。

**Code/runtime confidence limit.** 未独立执行或确认；交由代码反证。

## APP-05-D02 — survives

**Demand.** 撤销 key 阻止它验证新内容；已可信安装发行是否可运行必须由签名策略的精确 release 状态判断，不能仅因其旧清单 key 被撤销而统一失效。

**Exact specification evidence.**

- [docs/product/产品需求细则.md:98](https://github.com/wikigsroom/NsA-core/blob/cdedc7179ec1842592243a052525e021d8cf70f1/docs/product/%E4%BA%A7%E5%93%81%E9%9C%80%E6%B1%82%E7%BB%86%E5%88%99.md#L98) (mandated): “已安装开放内容在无本地阻断且安装有效时可离线启动；目录刷新失败不能成为统一登录墙。”
- [docs/engineering/07-build-release-operations.md:47](https://github.com/wikigsroom/NsA-core/blob/cdedc7179ec1842592243a052525e021d8cf70f1/docs/engineering/07-build-release-operations.md#L47) (mandated): “已知 revoked key 不能验证新内容；已安装内容如何处理由签名策略的精确 release 状态决定，不扩大删除全部游戏/存档。”
- [docs/product/产品需求细则.md:1123](https://github.com/wikigsroom/NsA-core/blob/cdedc7179ec1842592243a052525e021d8cf70f1/docs/product/%E4%BA%A7%E5%93%81%E9%9C%80%E6%B1%82%E7%BB%86%E5%88%99.md#L1123) (mandated): “普通下架停止新下载但默认保留已安装开放内容使用；紧急阻断独立按版本施加。”
- [docs/engineering/contracts/policy.schema.json:51](https://github.com/wikigsroom/NsA-core/blob/cdedc7179ec1842592243a052525e021d8cf70f1/docs/engineering/contracts/policy.schema.json#L51) (contract): “"revoked_key_ids": {”
- [docs/engineering/contracts/policy.schema.json:24](https://github.com/wikigsroom/NsA-core/blob/cdedc7179ec1842592243a052525e021d8cf70f1/docs/engineering/contracts/policy.schema.json#L24) (contract): “"state": {”

**Attempts to refute.**

- 常见安全设计会拒绝 revoked key 的所有签名，所以这是额外稳健性要求。 → **refuted**。当前工程条文明确区分新内容和已装内容，按精确发行策略处理后者；不能用通用偏好替换项目已选语义。
- 当前服务只发 revoked_key_ids=[]，因此该条没有规范基础。 → **scope-limited**。非空数组是既有契约允许且工程明确规定的状态，客户端接收它后的语义仍属于当前边界；但不能声称当前发行服务已经发出过非空撤销、已造成用户停用。
- 保留旧安装意味着继续接受 revoked key 签发的新清单/策略。 → **refuted**。finding 只涉及安装时已建立的可信发行及其固定摘要；不支持接受撤销后新内容或由失信 key 签的新策略。

**Retained scope.** 条件分支：有效安装已可信建立，后来由仍可信 policy key 签发的策略撤销安装清单 key，而该精确 release 未 blocked。当前服务是否实际发出这种策略未验证。

**Excluded claims.**

- 不允许 revoked key 继续验证新内容/新安装。
- 不削弱精确 release blocked 的新启动限制。
- 不声称现网已经发生该撤销事件。

**Safe correction boundary.** 区分安装时已验证的不可变发行/摘要与新内容验证；既有安装按当前可信精确 release 状态处置，继续拒绝撤销 key 的新内容。无需增加云服务或新账号能力。

**Consolidated validation needs; not executed.**

- 以可信 policy key 构造非空撤销策略；既有 published/unlisted 开放安装保持离线可用，同 key 的新安装拒绝。
- 同一发行明确 blocked 时仍拒绝新启动；重启和首次/后续启动语义一致。

**Code/runtime confidence limit.** 未独立执行或确认；当前服务空数组仅作为原报告的适用条件，交由代码反证核对。

## APP-05-D03 — narrowed

**Demand.** 完成必要准备的有效开放安装应能离线启动；首次可信策略同步可以属于必要本机准备，准备未完成时须如实显示，不得把“没有策略”等同“已验证无阻断”。

**Exact specification evidence.**

- [docs/product/产品需求细则.md:98](https://github.com/wikigsroom/NsA-core/blob/cdedc7179ec1842592243a052525e021d8cf70f1/docs/product/%E4%BA%A7%E5%93%81%E9%9C%80%E6%B1%82%E7%BB%86%E5%88%99.md#L98) (mandated): “已安装开放内容在无本地阻断且安装有效时可离线启动；目录刷新失败不能成为统一登录墙。”
- [docs/product/游戏大厅二次开发需求说明书.md:345](https://github.com/wikigsroom/NsA-core/blob/cdedc7179ec1842592243a052525e021d8cf70f1/docs/product/%E6%B8%B8%E6%88%8F%E5%A4%A7%E5%8E%85%E4%BA%8C%E6%AC%A1%E5%BC%80%E5%8F%91%E9%9C%80%E6%B1%82%E8%AF%B4%E6%98%8E%E4%B9%A6.md#L345) (mandated): “开放内容首次下载需要网络；完成安装与必要本机准备后可离线运行。离线时采用最近一次验证通过的清单和已知阻断记录，不要求仅为了展示目录而强制联网。”
- [docs/product/游戏大厅二次开发需求说明书.md:413](https://github.com/wikigsroom/NsA-core/blob/cdedc7179ec1842592243a052525e021d8cf70f1/docs/product/%E6%B8%B8%E6%88%8F%E5%A4%A7%E5%8E%85%E4%BA%8C%E6%AC%A1%E5%BC%80%E5%8F%91%E9%9C%80%E6%B1%82%E8%AF%B4%E6%98%8E%E4%B9%A6.md#L413) (mandated): “最近可信策略和有效安装可用；目录失败与内容阻断分开”
- [docs/product/产品需求细则.md:1139](https://github.com/wikigsroom/NsA-core/blob/cdedc7179ec1842592243a052525e021d8cf70f1/docs/product/%E4%BA%A7%E5%93%81%E9%9C%80%E6%B1%82%E7%BB%86%E5%88%99.md#L1139) (mandated): “目录失败不修改有效安装、离线许可或本地无阻断状态，开放内容允许离线启动。”
- [docs/engineering/05-data-state.md:89](https://github.com/wikigsroom/NsA-core/blob/cdedc7179ec1842592243a052525e021d8cf70f1/docs/engineering/05-data-state.md#L89) (mandated): “install.ready | 完成激活事务与 READY 复检 | 游戏已运行或性能已通过”
- [docs/engineering/02-android-client.md:40](https://github.com/wikigsroom/NsA-core/blob/cdedc7179ec1842592243a052525e021d8cf70f1/docs/engineering/02-android-client.md#L40) (mandated): “未在表中开放的状态保守返回 unavailable + 可理解原因。下载100%、prepared 或存在目录均不能直接输出 launch。就绪按钮点击后仍重验，避免页面出现后状态已改变。”

**Attempts to refute.**

- 只要安装文件有效且 offline_permitted=true，规范绝对禁止首次策略同步前置。 → **refuted**。主需求 L345 明示必要本机准备，并要求采用最近验证通过的清单和已知阻断记录；L413 表述最近可信策略。规范没有要求把从未拿到策略解释成可信的无阻断。
- 文件安装 ready 就证明全部离线准备已经完成。 → **refuted**。install.ready 在 05:L89 只表示激活/READY 复检，不等于所有启动资格就绪。不应为修复 finding 擅自改写此状态的契约语义。
- 首次策略请求失败时允许 UI 声称准备完成，等离线点开始才追加说明。 → **refuted**。产品对开放内容完成必要准备后的离线承诺仍在。必须明确未完成的是初始可信策略准备，或在宣称可开始/离线准备完成前可靠地完成并持久化它。
- 修复只能删除 policy 非空检查。 → **refuted**。这是过强且可能破坏信任边界的实现处方。原报告给出的准备阶段持久化初始授权分支是更有依据的最小方向。

**Retained scope.** 收窄为：如果首次策略获取失败仍被呈现为可开始/离线准备已完成，离线首次启动再遭统一联网门，则存在准备状态与承诺不一致。仅观察安装 DB ready 和 policy 为空，尚不足以单独证明离线安全门违反规范。

**Excluded claims.**

- 无可信初始策略也必须一律离线放行。
- 为消除网络门而清除已知 block、忽略签名或采信空默认策略。
- 将初始可信策略同步误称为被禁止的未来账号/有限期资格。
- 强制把安装 ready 的底层语义扩成全部启动资格。

**Safe correction boundary.** 在可开始/离线准备完成的事实中显式包含初始可信策略准备；首次同步失败保留有效安装、显示可恢复的准备未完成原因及重试。成功后持久化可信策略并允许按其离线启动。是否额外定义可验证的初始无阻断事实须同步产品/契约，不能自行默认。

**Consolidated validation needs; not executed.**

- 仅初次 policies 失败而安装成功时，不能提前声称已完成离线准备；联网重试成功后断网启动。
- 已有可信策略时，目录/续签失败不影响无 block 的离线开放安装；已知 block 不被任何兜底删除。

**Code/runtime confidence limit.** 必须由代码反证确认 UI/动作到底是否提前承诺离线完成；安装 ready 本身不视为充分证据。

## APP-05-D04 — survives

**Demand.** 任何已同步的可信精确 release block 都限制之后的新启动；启动命令不能沿用同步前快照绕过它。

**Exact specification evidence.**

- [docs/product/产品需求细则.md:100](https://github.com/wikigsroom/NsA-core/blob/cdedc7179ec1842592243a052525e021d8cf70f1/docs/product/%E4%BA%A7%E5%93%81%E9%9C%80%E6%B1%82%E7%BB%86%E5%88%99.md#L100) (mandated): “缓存失效和内容撤回是不同事实；已知阻断必须持久保存，不能被旧目录覆盖。”
- [docs/product/产品需求细则.md:1124](https://github.com/wikigsroom/NsA-core/blob/cdedc7179ec1842592243a052525e021d8cf70f1/docs/product/%E4%BA%A7%E5%93%81%E9%9C%80%E6%B1%82%E7%BB%86%E5%88%99.md#L1124) (mandated): “阻断策略有单调序号、原因和恢复入口，下一次同步后限制新启动，不远程删档。”
- [docs/engineering/05-data-state.md:93](https://github.com/wikigsroom/NsA-core/blob/cdedc7179ec1842592243a052525e021d8cf70f1/docs/engineering/05-data-state.md#L93) (mandated): “用户点下时命令层重新验证，旧 UI 快照不能绕过后来同步的阻断。”
- [docs/product/产品需求细则.md:1140](https://github.com/wikigsroom/NsA-core/blob/cdedc7179ec1842592243a052525e021d8cf70f1/docs/product/%E4%BA%A7%E5%93%81%E9%9C%80%E6%B1%82%E7%BB%86%E5%88%99.md#L1140) (mandated): “需联网或有限期资格的未来内容必须事先在详情展示，不能隐式增加。”
- [docs/product/产品需求细则.md:1142](https://github.com/wikigsroom/NsA-core/blob/cdedc7179ec1842592243a052525e021d8cf70f1/docs/product/%E4%BA%A7%E5%93%81%E9%9C%80%E6%B1%82%E7%BB%86%E5%88%99.md#L1142) (mandated): “联网恢复后刷新事实并保留当前游戏会话，是否结束活动会话按明确应急策略处理。”
- [docs/engineering/contracts/release-manifest.schema.json:128](https://github.com/wikigsroom/NsA-core/blob/cdedc7179ec1842592243a052525e021d8cf70f1/docs/engineering/contracts/release-manifest.schema.json#L128) (contract): “"offline_permitted": {”

**Attempts to refute.**

- 在线专用/有限期内容属于未来，因此当前 online-only 分支的错误无需处理。 → **scope-limited**。不要求新增未来账号资格产品；但既有 manifest 将 offline_permitted 定为必填 boolean，false 是既有代码/契约分支。只要该分支实际接收可信 blocked，就必须遵守已知阻断规则。当前公开离线发行是否走此分支需另证。
- 同一次 preflight 较早查过 published，最后同步 blocked 可以下次再管。 → **refuted**。UPD-06.02 明说下一次同步后限制新启动；05:L93 禁止旧快照绕过后来同步阻断。新会话尚未创建时不能视作既有活动会话。
- 规范要求收到 block 立即终止当前正在游玩的所有游戏。 → **refuted**。UPD-07.04 区分活动会话与新启动；是否结束前者取决于明确应急策略，原 finding 不应扩大到任意立即终止。

**Retained scope.** 仅 offline_permitted=false 且最后强制策略同步在创建新会话前成功持久化 blocked 的现有分支。高严重度依赖代码反证确认最终未重查且会创建新会话。

**Excluded claims.**

- 不声称默认允许离线的当前公开发行必受影响。
- 不要求新增账号或到期授权系统。
- 不要求对既有活动会话无条件即时强退或远程删档。

**Safe correction boundary.** 最后可能改变策略的同步完成后，在返回启动选择/创建会话前按同一最新事实复查该精确 release 的阻断。可消除重复同步或合并最终校验，避免为此引入长期持锁网络等待。

**Consolidated validation needs; not executed.**

- 控制两次策略响应为 published 再 blocked，确认第二次同步后不创建新会话。
- 既有运行会话的处置独立遵循明确策略，不因目录恢复无故结束。

**Code/runtime confidence limit.** 未独立执行或确认；online-only 可达性和最终 session 创建交由代码反证。

## APP-05-D05 — survives

**Demand.** 首次无目录缓存要明确说明联网发现；有缓存才说明展示缓存与更新时间，不能把无缓存表示成有缓存但时间未知。

**Exact specification evidence.**

- [docs/product/产品需求细则.md:97](https://github.com/wikigsroom/NsA-core/blob/cdedc7179ec1842592243a052525e021d8cf70f1/docs/product/%E4%BA%A7%E5%93%81%E9%9C%80%E6%B1%82%E7%BB%86%E5%88%99.md#L97) (mandated): “无缓存离线时显示联网说明和空本地库；有缓存显示更新时间，不填造在线条目。”
- [docs/design/obsidian-portrait/03-screen-specs.md:930](https://github.com/wikigsroom/NsA-core/blob/cdedc7179ec1842592243a052525e021d8cf70f1/docs/design/obsidian-portrait/03-screen-specs.md#L930) (mandated): “缓存时间或无缓存说明。”
- [docs/design/obsidian-portrait/03-screen-specs.md:944](https://github.com/wikigsroom/NsA-core/blob/cdedc7179ec1842592243a052525e021d8cf70f1/docs/design/obsidian-portrait/03-screen-specs.md#L944) (mandated): “显示离线与缓存时间；无缓存使用明确空状态。”
- [docs/design/obsidian-portrait/03-screen-specs.md:970](https://github.com/wikigsroom/NsA-core/blob/cdedc7179ec1842592243a052525e021d8cf70f1/docs/design/obsidian-portrait/03-screen-specs.md#L970) (mandated): “首开离线说明联网后发现游戏。”
- [docs/product/页面与交互验收规格.md:398](https://github.com/wikigsroom/NsA-core/blob/cdedc7179ec1842592243a052525e021d8cf70f1/docs/product/%E9%A1%B5%E9%9D%A2%E4%B8%8E%E4%BA%A4%E4%BA%92%E9%AA%8C%E6%94%B6%E8%A7%84%E6%A0%BC.md#L398) (mandated): “网络/服务/缓存可用状态、来源路由”

**Attempts to refute.**

- 已显示连接失败/空列表，所以声称展示缓存也无关紧要。 → **refuted**。连接失败与是否存在缓存是不同事实；有连接错误并不能使无缓存时的“正在展示缓存”真实。
- 每个根标签都必须再加一份完全相同的无缓存文案。 → **refuted**。条文要求可理解的真实状态，不规定重复布局或每个标签重复同一段文字。
- 这是伪造在线游戏或数据丢失，应提高严重度。 → **refuted**。既有 finding 没有宣称伪造条目/数据损失。保留低严重度，仅修正缓存存在性及相应说明。

**Retained scope.** 首次无缓存却宣称正在展示缓存的事实错误保留。不能将已有连接失败提示说成完全没有离线说明，也不要求固定页面布局。

**Excluded claims.**

- 不增加伪造游戏条目、删除本地数据或统一登录墙等未在该 finding 证明的影响。
- 不要求每个标签重复同一文案。

**Safe correction boundary.** 根据真实缓存快照存在性和 fetchedAt 显示缓存时间或首次无缓存说明；空但有效缓存与从未缓存可区分，保留本地库/重试入口。

**Consolidated validation needs; not executed.**

- 首次无缓存错误、有有效空缓存、有旧非空缓存三个状态的文案分别真实。

**Code/runtime confidence limit.** 未独立执行或确认；文案条件交由代码反证。

## Deferred and extra scope

- CAT-10 的最近搜索历史是产品 P1；不影响 APP-05 的 P0 离线行为。
- 未来账号/有限期资格未默认进入首版。D04 仅处理已有 boolean 分支的阻断一致性。
- 完全离线即时远程阻断、无条件结束活动会话、删玩家存档均不是本轮修正目标。
- 测试/实机触发未执行；verificationNeeds 仅供整批实现完成后的统一验证。

## Work constraints

- 无产品文件改动
- 无测试或构建
- 无设备读写/输入
- 无浏览器/虚拟化/其他聊天访问
- 未启动代理
- 仅写本任务指定的忽略报告
