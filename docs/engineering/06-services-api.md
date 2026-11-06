# 06 · 服务端、API 与运营管理

基线 v2.0 · 2026-11-06。关联 CAT、DL、UPD、OPS、NFR。HTTP 方法、路径、请求/响应、身份方式、角色与需求关联以 [OpenAPI 3.1.1](contracts/openapi.json) 为机器契约；本章补充 Schema 无法表达的授权、事务和时序规则。当前包含 40 个路径、43 个操作，均为待实现契约。

## 1. 部署与服务边界

公开 API、管理 API 可共用代码与数据库，但使用不同鉴权依赖、网络入口策略和速率配额；不把管理权限加在玩家安装凭据上。大对象上传/下载走私有对象存储及受控 CDN，API 不代理十几 GiB 文件，也不在请求线程运行 porter/编译器。

FastAPI 负责 DTO 验证、授权、短事务与作业创建；独立 worker 执行归档检查、哈希、签名协作、策略发布和清理。PostgreSQL 是发布与范围事实源；搜索投影和缓存失败不能绕过范围核验。服务实现可以分进程，首版不要求微服务、Redis 或 Kubernetes。

API、对象域名、OIDC 发行方、区域、预算属于 DEC-02/06/09 的部署输入。契约中的 `example.invalid` 为不可部署占位域名；不能在正式安装包内保留、回退到明文测试域名，或把开发电脑共享目录当作线上下载源。

## 2. 身份和访问范围

| 主体 | 获得身份 | 能做什么 | 必须拒绝 |
| --- | --- | --- | --- |
| 未联网访客 | 本地档案即可进入 App | 看本地库、许可、设置；离线启动允许内容 | 首屏强制注册、无网卡死启动 |
| 匿名安装 | bootstrap 生成安装凭据，非注册账号 | 浏览开放目录、申请允许内容、提交自己的反馈 | 仅改 channel、安装 ID 或设备字符串获得内测/管理权限 |
| 内测安装 | 服务端批准的一次性 access_grant 或受控准入记录 | 访问被授予的 internal 范围 | 公开目录泄露候选游戏、私有材料或其他测试人资格 |
| 管理人员 | OIDC 身份，后端验证 issuer/audience/签名/到期 | 执行其角色和对象范围内的管理操作 | 客户端隐藏按钮替代服务端授权、自己审核自己作品 |
| worker / signer | 独立最小服务身份 | 读取已审核输入、写指定产物或签名用途 | 使用玩家令牌、获得不必要的角色管理权限 |

bootstrap 每次新安装使用随机 nonce 和至少 128 位随机幂等键。它只能新建凭据或在受保护的短期幂等窗口重放同一新建结果，不能仅凭公开 installation_id/nonce 找回历史凭据。首次响应的短期重放数据加密保存，工程默认窗口 10 分钟，随后清除敏感响应并要求新建安装；此类重放不沿用普通 24 小时响应保留。安装凭据不恢复本地存档。

access token 工程默认 1 小时，refresh token 空闲有效期 30 天；刷新轮换并检测旧 token 重用。服务端只保存 refresh 摘要，客户端用受保护存储；单飞刷新防止并发轮换冲突。刷新请求用原幂等键处理丢失响应，短时仅允许同次结果重放；成功后立即丢弃旧 refresh。失效后开放内容可重新建立匿名身份，未确认的受限资格不自动继承。

管理 Web 使用 OIDC Authorization Code + PKCE；不得在前端嵌入 client secret。浏览器 token 仅内存持有，若采用会话 cookie 则服务端 BFF、HttpOnly/Secure/SameSite、CSRF 与 Origin 检查成套实施；二者不得拼成 token 长期存 localStorage 的快捷方案。发行方、管理员初始化和 MFA 是部署门槛，开发者不能造一个默认 admin/admin 账户发布。

## 3. 接口通用协议

| 项 | 约束 |
| --- | --- |
| 编码 | UTF-8 JSON；请求拒绝重复键、非有限数值、超限深度/长度；普通 JSON 请求上限 256 KiB |
| 标识 | `Id` 按契约，不接受路径分隔符、任意 URL、SQL 片段；参数化数据库查询 |
| 字节 | ByteCount 字符串，64 位范围由服务端/客户端再校验；不可依赖 JSON 数字精度 |
| 分页 | page_size 默认 24、最大 50；cursor 绑定条件、稳定排序、范围、语言和兼容上下文，默认 15 分钟到期 |
| 条件读取 | 目录/详情/清单/策略支持 ETag；304 前仍核验访问范围和最新阻断；缓存键隔离身份范围 |
| 幂等 | 修改操作必须 Idempotency-Key；按 actor+method+route+key+请求体摘要保存，普通回执 24 小时；同键异体 409 |
| 并发 | 可变管理聚合必须 expected_revision；UPDATE WHERE revision 匹配，失败 409 并带 current_revision |
| 长操作 | 202 仅说明已入队，返回 operation_id；管理端查询作业状态，不显示已经校验/签名完成 |
| 错误 | 固定 Error DTO + request_id；敏感栈在脱敏受限日志，HTTP 状态与 errors.json 一致 |
| 限流 | 超限 429 + Retry-After；客户端上限 3600 秒，超出转等待且不忙循环 |
| CORS | 只允许已配置管理来源；玩家原生客户端不需要放开任意来源凭据 |

请求体摘要对服务端收到且解析验证后的规范请求结构计算；不与签名 payload 的原始字节协议混用。幂等判断先认证/限流，再查回执；返回回执仍验证当前主体，不能向已失权主体泄露历史敏感 URL。签名 URL 到期后通过续签操作申请新 URL，不通过重用旧幂等回执假装尚未过期。

普通请求校验失败使用 REQUEST_INVALID/422；对象不存在或对当前主体不可见时 NOT_FOUND/404，不能用不同提示枚举私有内容。已知玩家内容范围不允许可用 CAT_SCOPE_DENIED/403。服务暂时不可用返回 503，内部失败 INTERNAL_ERROR/500；客户端只重试可重试类别，绝不无限重试签名、路径或摘要错误。

## 4. 目录、搜索与兼容上下文

搜索对中文名、英文名和登记别名做归一化匹配；关键词按 Unicode 字符计最多 64，去首尾空白，纯空白等于无关键词。P0 只承诺已设计类型/本机支持筛选与默认排序，收藏、复杂推荐等 P1 不悄悄进范围。排序以默认 published_at 降序、game_id 升序的唯一尾键保证分页稳定。

`compatibility/evaluate` 输入最多 50 个 release_id 与完整已知能力，生成短期 capability_context_id。GET 目录/详情引用该上下文，服务端把证据与精确版本组合映射为 verified/experimental/unverified/unsupported/app_update_required。上下文仅对当前安装和相应 App/注册表有效，默认 1 小时；能力变化立即作废。上下文缺失或探测未完，结果是未验证，不凭机型名字猜为 verified。

下载请求必须引用本次 capability_context_id，过期或 App/注册表不匹配先重新评估。experimental/unverified 仅在已获内部范围且 experimental_acknowledged=true 时允许试跑；没有资格只提供真实限制说明，不用一个无效“加入内测”按钮冒充自助准入。P0 内测资格通过受控分发/一次性 grant 准备，不新增账号或虚假的申请成功页。

兼容筛选必须在服务端快照内应用后分页；不能取一页再本地剔除，造成空页/结果数错误。“已安装”筛选如果设计页启用，使用本地安装事实与元数据连接形成独立本地列表，不能声称为全站在线匹配总数。服务端总数未知时不显示伪造精确数。

App 版本、注册表摘要与设备能力均是声明信息，用于推荐，不是授权依据。download-session 创建时重新核验发行范围、模块映射、精确清单与策略；篡改一个 GPU 字段不能获得内容权限或修改签名包。verified 标签必须有可读测试范围、证据 ID、日期和已知限制。

目录只显示对应主体允许看见的条目及目标发行。普通下架停止新曝光和新发放；已安装、已获授权的允许离线内容与未结束下载可按有效授予继续。严重阻断、范围到期/撤销则撤回对应授权；每次续签与启动同步分别执行，不以 CDN 缓存代替决策。

## 5. 下载授权与私有对象

创建授权必须绑定 installation_id、release_id、manifest_sha256、object_id、完整大小/摘要、strong quoted ETag、范围与到期时间。URL 可以变，以上身份不可变；续签得到不同对象/长度/ETag 必须停止拼接。下载会话工程默认最长 7 天，单次 URL 默认 15 分钟；会话可撤销，新授权仍需重新检查。

归档、索引、封面与证据只能引用平台创建的 object_id。服务端不得接受用户填写任意远程 URL 后代抓取，避免越权读取和内网请求。上传授权只允许固定对象键、声明大小、类型和限时方法；上传完成后 worker 从真实存储读回验证长度和摘要，不能只信调用方的 complete 请求或对象元数据。

发布大包必须支持 multipart：默认≥64 MiB 即分段上传，分段默认 64 MiB，按供应商实际最小/最大段和最多 10000 段调整，超限在授予前拒绝。创建操作返回 mode、object_id、revision 和 session 到期；multipart 再按最多 100 个 part_number 分批申请限时 PUT URL，客户端流式上传并持久记录各段 ETag，过期只重签同一 upload/object/part。ETag 是存储完成凭据，不当成完整 SHA-256。

complete 的 multipart parts 必须按 part_number 升序、唯一、连续、齐全，与声明总长一致；服务端完成对象合并后才排真实全对象哈希作业，返回 202 不等于已核验。complete JSON 特例上限 4 MiB，其他普通请求仍 256 KiB。上传会话默认 24 小时、分段 URL 15 分钟；abort/过期清理同时释放对象服务的未完成 parts，不能只删数据库行。S3 单 PUT 有大小上限，不能用一个 URL 接口声称可上传十几 GiB；兼容服务以实际验证结果为准。[S3 上传协议](https://docs.aws.amazon.com/AmazonS3/latest/userguide/upload-objects.html)、[multipart 限制](https://docs.aws.amazon.com/AmazonS3/latest/userguide/qfacts.html)。

管理 `artifact/access` 角色检查后仍按 kind 和对象范围收紧：rights_document 仅 editor/reviewer/publisher/admin 的相应材料；evidence 仅 QA/审核/发布/值班所需；runtime/content 源产物仅制作/验证/发布所需。oncall 不因能看反馈就默认读取分发材料或原始内容输入。临时审阅 URL 禁止缓存到公共页或审计正文，审计只留 object_id 和授予结果。

大文件对象必须支持 HTTPS、identity、Range、稳定长度与强 ETag，并在 G1 经实际服务验证。代理 gzip 改写/弱 ETag/静默重压缩导致续传条件不满足时不能发布该对象。完整协议见 [04](04-transfer-install-save.md) 和 [HTTP Range](https://www.rfc-editor.org/rfc/rfc9110.html#name-range)。

## 6. 最小管理后台

| 角色 | 可执行工作 | 分离要求 |
| --- | --- | --- |
| editor | 游戏资料、素材、分发范围材料草稿 | 不审核自己提交的材料 |
| builder | 构建溯源、版本候选、产物上传/校验请求 | 不自行宣布兼容通过或公开发布 |
| qa | 兼容证据、候选验证 | verified 必须满足质量门槛，不能编辑哈希绕过验证 |
| reviewer | 审范围和候选、读审计 | 与编辑/制作该候选的人员不同 |
| publisher | 发布、普通下架、经审核解除阻断 | 无权跳过审核；不能自行解除自己创建的阻断而无独立复核 |
| oncall | 反馈处理、聚合健康、精确版本紧急阻断/下架 | 不自动赋予发布、签名或资料修改权 |
| admin | 身份和角色配置、受审计恢复 | 角色不取消双人审核条件；禁止自提权 |

后台必须提供列表/筛选/详情、当前 revision、证据引用、审核结论和原因输入；返回等待/进行/通过/失败真实作业状态。编辑器保存失败保留草稿；版本冲突显示重载和差异，不最后写入覆盖他人。危险操作精确显示 game/release/范围和影响，提交后回查权威结果。截图上的成功 toast 不替代已提交审计。

发布事务必须检查：范围材料已批准且当前有效、构建来源完整、对象真实校验通过、App 注册表有匹配 slot、兼容证据满足发布层级、审核人与制作人分离、manifest 已签、目标 scope 不越界。通过后 revision 更新并产生目录/策略 outbox。unblock 由 publisher/admin 提交，仍要求独立 reviewer 审核和新证据；不能因为原阻断人在线就取消保护。

后台不是玩家导航第五入口，管理路由和凭据不打包进 Android 产品。上线时不接受开放注册自动成为管理人员。内部原始输入、prod.keys、账号凭据或完整游戏包不得进入 Git、反馈附件或公开后台资料。

## 7. 反馈和诊断

反馈 description 去首尾空白后 10～2000 字符；类别必填、联系信息可选不超过 254。先本地验证，再服务验证；只写“已提交”于收到真实 ticket_id 后。网络失败保留草稿并使用同一个幂等键重试，不能产生多张工单。

诊断默认不上传。玩家选择后先看预览/清单和大小，包含 App/模块/内容版本、错误码、脱敏日志与必要能力摘要；不含密钥、签名 URL、序列号、存档/ROM、自由目录内容。分配最多 10 MiB 的上传位置，上传后 complete 验证归属/大小/哈希，只有 ready 状态可关联该安装的工单。附件失败可让用户选择纯文字提交，不静默上传重试。

工单管理默认 new → triaged → resolved / closed，可附理由重新 triaged；每次变更需 revision、操作者、原因。玩家 P0 只承诺真实提交回执和本地保留凭据，不承诺实时聊天、邮件推送或账号工单中心。删除/保留按隐私说明与实际存储清理执行，不能承诺超出部署能力的响应 SLA。

## 8. 作业、健康与故障恢复

后台作业记录 queued/running/succeeded/failed/cancelled、attempt、lease_owner、lease_until、输入摘要、输出引用和错误码。claim 使用短事务，工作在事务外进行；租约过期只对幂等作业重试，签名/发布结果通过操作 ID 去重。失败不能留下公开可见的部分版本。取消协作点在安全边界处理，已完成发布的撤回通过独立受审计操作。

健康摘要包含 API/数据库/对象读取/校验队列/签名可用性与延迟，不返回数据库连接串或凭据。readiness 不通过时流量转移或 503；liveness 不应依赖远程 CDN 导致不停重启。管理入口与指标端点需鉴权/网络限制。对象读回失败、队列堆积、签名失败、错误率和诊断清理逾期都要可告警并指派责任人。

错误目录的 50 个代码覆盖网络、完整性、安装、运行、存档、更新与管理并发。完整玩家文案、是否可自动重试和恢复动作见 [errors.json](contracts/errors.json)；增加代码必须同步 DTO 枚举、客户端映射、指标和验收。未知代码使用通用说明和 request_id，保留真实错误分类，不能降格为成功。

## 9. 操作索引

以下索引由本版 OpenAPI 生成，字段与鉴权以同目录机器契约为准；公开接口使用匿名安装 Bearer，bootstrap/refresh 除外；所有管理接口使用 admin Bearer。部署验收须逐个验证越权、无效请求、幂等重放和 revision 冲突。

| 方法与路径 | 操作 ID | 身份/角色 | 需求 |
| --- | --- | --- | --- |
| `POST /v1/bootstrap` | `bootstrapInstallation` | 无需 Bearer | APP-02、NFR-06 |
| `POST /v1/credentials/refresh` | `refreshInstallationCredential` | 无需 Bearer | APP-02、NFR-06 |
| `GET /v1/catalogue` | `listCatalogue` | 匿名安装 | CAT-01、CAT-02、CAT-03、CAT-08 |
| `GET /v1/games/{game_id}` | `getGameDetail` | 匿名安装 | CAT-04、CAT-05、CAT-06 |
| `POST /v1/compatibility/evaluate` | `evaluateCompatibility` | 匿名安装 | APP-03、CAT-05、OPS-05 |
| `GET /v1/releases/{release_id}/manifest` | `getReleaseManifest` | 匿名安装 | PKG-02、PKG-03 |
| `POST /v1/download-sessions` | `createDownloadSession` | 匿名安装 | DL-01、DL-06、CAT-06 |
| `POST /v1/download-sessions/{download_session_id}/renew` | `renewDownloadSession` | 匿名安装 | DL-06、DL-09 |
| `GET /v1/policies` | `getReleasePolicies` | 匿名安装 | UPD-06、UPD-07 |
| `GET /v1/app-update` | `getAppUpdate` | 匿名安装 | UPD-01、UPD-05 |
| `POST /v1/diagnostic-uploads` | `createDiagnosticUpload` | 匿名安装 | OPS-09、NFR-06、NFR-10 |
| `POST /v1/diagnostic-uploads/{upload_id}/complete` | `completeDiagnosticUpload` | 匿名安装 | OPS-09、NFR-10 |
| `POST /v1/feedback` | `submitFeedback` | 匿名安装 | OPS-09 |
| `POST /admin/v1/games` | `createGame` | editor / admin | OPS-01 |
| `GET /admin/v1/games` | `listAdminGames` | builder / editor / qa / reviewer / publisher / oncall / admin | OPS-01 |
| `PATCH /admin/v1/games/{game_id}` | `updateGame` | editor / admin | OPS-01、OPS-10 |
| `GET /admin/v1/games/{game_id}` | `getAdminGame` | builder / editor / qa / reviewer / publisher / oncall / admin | OPS-01 |
| `POST /admin/v1/rights-records` | `createRightsRecord` | editor / admin | OPS-02 |
| `POST /admin/v1/rights-records/{rights_record_id}/reviews` | `reviewRightsRecord` | reviewer / admin | OPS-02、OPS-07 |
| `POST /admin/v1/releases` | `createRelease` | builder / editor / admin | OPS-03、OPS-06 |
| `GET /admin/v1/releases` | `listAdminReleases` | builder / qa / reviewer / publisher / oncall / admin / editor | OPS-06 |
| `POST /admin/v1/builds` | `registerBuild` | builder / admin | OPS-03 |
| `POST /admin/v1/artifact-uploads` | `createArtifactUpload` | builder / editor / qa / admin | OPS-01、OPS-04、OPS-05 |
| `POST /admin/v1/artifact-uploads/{object_id}/complete` | `completeArtifactUpload` | builder / editor / qa / admin | OPS-04 |
| `POST /admin/v1/releases/{release_id}/verify` | `verifyReleaseArtifacts` | builder / qa / admin | OPS-04、PKG-04 |
| `POST /admin/v1/compatibility-evidence` | `createCompatibilityEvidence` | qa / admin | OPS-05、NFR-05 |
| `POST /admin/v1/releases/{release_id}/reviews` | `reviewRelease` | reviewer / admin | OPS-06、OPS-07 |
| `POST /admin/v1/releases/{release_id}/publish` | `publishRelease` | publisher / admin | OPS-06、OPS-07、OPS-10 |
| `POST /admin/v1/releases/{release_id}/unlist` | `unlistRelease` | publisher / oncall / admin | UPD-06、OPS-10 |
| `POST /admin/v1/releases/{release_id}/block` | `blockRelease` | oncall / admin | UPD-06、OPS-07、OPS-10 |
| `POST /admin/v1/releases/{release_id}/unblock` | `unblockRelease` | publisher / admin | UPD-06、OPS-10 |
| `PUT /admin/v1/principals/{principal_id}/roles` | `setPrincipalRoles` | admin | OPS-07、OPS-10 |
| `GET /admin/v1/audit` | `listAuditLog` | reviewer / oncall / admin | OPS-10 |
| `GET /admin/v1/health-summary` | `readHealthSummary` | oncall / admin | OPS-08、NFR-03 |
| `GET /admin/v1/operations/{operation_id}` | `getAdminOperation` | builder / qa / reviewer / publisher / oncall / admin | OPS-04、OPS-08 |
| `GET /admin/v1/releases/{release_id}` | `getAdminRelease` | builder / qa / reviewer / publisher / oncall / admin / editor | OPS-06 |
| `GET /admin/v1/feedback` | `listFeedbackTickets` | oncall / admin | OPS-09 |
| `PATCH /admin/v1/feedback/{ticket_id}` | `triageFeedbackTicket` | oncall / admin | OPS-09、OPS-10 |
| `GET /admin/v1/rights-records/{rights_record_id}` | `getRightsRecord` | editor / reviewer / publisher / admin | OPS-02、OPS-06 |
| `GET /admin/v1/builds/{build_id}` | `getBuildRecord` | builder / qa / reviewer / publisher / admin | OPS-03 |
| `GET /admin/v1/artifacts/{object_id}/access` | `getArtifactAccess` | builder / editor / qa / reviewer / publisher / oncall / admin | OPS-04、OPS-06 |
| `POST /admin/v1/artifact-uploads/{object_id}/parts` | `authorizeUploadParts` | builder / editor / qa / admin | OPS-04 |
| `POST /admin/v1/artifact-uploads/{object_id}/abort` | `abortArtifactUpload` | builder / editor / qa / admin | OPS-04 |
