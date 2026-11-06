# 07 · 内容制作、签名、发行与运行保障

基线 v2.0 · 2026-11-06。关联 PKG、UPD、OPS、NFR-01/03/06/09/10。所有步骤是后续工程约束，未代表当前已有线上环境、生产签名或首发内容。

## 1. Windows 原生工作约束

仓库固定为 `E:\dev\sandbox\游戏移植\nx2android`，远端 `git@github.com:wikigsroom/NsA-core.git`。开发分支、依赖缓存、临时文件、下载、构建、真机诊断全部在 `E:\dev\sandbox\游戏移植` 内；原始游戏仅放仓库已忽略的 `.gamedump/<App>` 等受控目录，不能提交。不要把私人游戏数据当成测试 fixture。

不启动 Docker、WSL、VM、Android 模拟器及类似虚拟化环境。JDK、Android SDK/NDK、Python、数据库与本地服务使用 Windows 原生进程，或使用已明确配置的远端托管服务；不能为了方便绕到 WSL 构建。后台进程要可识别、限范围停止；不终止用户其他服务。浏览器验证结束关闭该次不用的 tab/上下文，不能遗留自动化浏览器占内存。

首次构建建立 task-local 缓存目录，显式设置 Gradle、pip、TEMP/TMP、SDK 项目路径，不覆盖 HOME/CODEX_HOME 等系统路径语义。Node/Python 工具若使用现有只读运行时可直接读取，但新增依赖安装在任务根。密钥只以环境引用/凭据存储注入，不把值写命令历史、文档或构建日志。

## 2. 可重现构建输入与输出

| 记录 | 必需字段 | 放行条件 |
| --- | --- | --- |
| source-lock | NsA-core commit、上游 core commit、未提交补丁摘要 | 正式构建使用可追溯已提交代码；不能只写 main |
| toolchain-lock | JDK/Gradle/AGP/Kotlin/BOM/NDK/SDK/Python、各依赖版本/摘要 | G0 原生构建验证兼容后锁定；禁止浮动依赖 |
| input-inventory | game/profile/版本、文件数量/总量/摘要、来源范围引用 | 私有存储；不含密钥值；哈希相同才复用构建缓存 |
| runtime-build | slot、唯一库名/SONAME、ABI、bootstrap API、core/profile/driver 摘要 | 与 APK 内注册表一致；真实载入验证 |
| content-build | release、archive/file-index/chunk-index 的长度和 SHA-256 | 从最终对象读回校验；文件索引完整且无额外文件 |
| release-evidence | App/模块/包/系统/GPU/驱动/输入、测试计划/结果 | 证据与发布组合精确对应，实验和已验证区分 |
| distribution | APK/applicationId/versionCode/证书摘要、SBOM、许可、清单签名 key_id | 版本递增、同渠道签名连续、来源与许可可查 |

porter 的单游戏生产入口保留为制作工具；新增 export-release 从已完成、已登记的产物导出受控包，不能让玩家手机承担解密、编译或任意游戏适配。App 内运行模块通过 Gradle/NDK 构建内置；每个 slot 的名称和配置独立。更换 profile、core 或驱动后重新验证相关内容组合，不能继承旧证据。

最终目录建议：`out/<build_id>/app/`、`runtime/`、`content/<release_id>/`、`indexes/`、`provenance/`、`qa/`；这些是待实现产物布局，不是要求把大文件加 Git。Git 保存源码、工具、Schema、锁文件、脱敏测试记录和许可清单；对象服务保存发布大产物及私有范围材料。

## 3. 内容格式与签名

首版内容归档为 ZIP64，允许 STORE/DEFLATE；不嵌套任意安装脚本，不附带可随意加载的驱动或宿主库。`mods/` 本身含改写程序，仍属于执行内容，不能在发布说明中改称“纯素材”来忽略加载和渠道约束。文件/分段索引使用 UTF-8 JSON，其最终原始字节摘要被 manifest 绑定。

签名 envelope 的机器定义为 [signed-envelope.schema.json](contracts/signed-envelope.schema.json)，payload 分别符合 release、policy、app-update Schema。签名过程：

```text
raw = 发布器生成的一份确定的 UTF-8 payload 字节
digest = SHA256(raw)
message = ASCII("NSA/" + purpose + "/v1") || 0x00 || raw
signature = Ed25519.sign(private_key, message)
envelope = schema/purpose/key_id/algorithm/payload_sha256/payload_base64/signature_base64
```

验证先检查外层大小/固定算法/用途/key_id，再严格 Base64 解码、校验摘要与签名，随后解析 payload、验证 Schema 和语义依赖。不得把 JSON 解析重排后再验签。payload 最多 2 MiB，envelope 最多 3 MiB；签名使用的原字节不得被 CDN/网关变换。[Ed25519 标准](https://www.rfc-editor.org/rfc/rfc8032)。

发布根公钥及 purpose scope 由 APK 可信配置固定，私钥不下发客户端。内容、策略和 App 更新可以分用途/环境 key；测试 key 不能进入生产信任列表。信任根轮换需经现有信任链授权或新 App，不能从未验证 manifest 的 URL 下载新根并立即信任。

策略有单调 epoch，阻断状态和 revoked_keys 在普通缓存清理后仍保留。恢复旧目录缓存不降低策略；同 epoch 不同摘要拒绝。已知 revoked key 不能验证新内容；已安装内容如何处理由签名策略的精确 release 状态决定，不扩大删除全部游戏/存档。离线不能得知尚未同步的新阻断，产品说明不得宣称能瞬间远程停用所有离线设备。

## 4. 内容发布流水线

| 顺序 | 输入与处理 | 失败行为 |
| --- | --- | --- |
| 1 登记 | game/范围材料/输入清单/制作负责人 | 不齐全只保留 draft |
| 2 构建 | 锁定工具、改写/模块/着色器/资源包 | 产物隔离，不覆盖线上对象 |
| 3 本地核验 | Schema、路径、长度、逐文件/分段/总摘要、模块绑定 | 拒绝越界与未声明文件 |
| 4 私有上传 | 创建 object_id、大包 multipart 限时上传/段续签/合并、真实存储读回 | 未完成对象保持不可交付并清理残留 parts |
| 5 候选验证 | 同一最终字节下载到 USB 真机，实测性能/输入/存读档 | 证据不能只来自开发机已有资源 |
| 6 独立审核 | 范围、证据、许可、已知问题、App 模块窗口 | 不允许作者自审通过 |
| 7 签名与发布 | signer 校验已批准摘要，生成 manifest/策略，短事务发布与 outbox | 作业幂等，任何半成品不出现在公开目录 |
| 8 在线探测 | 目录/manifest/Range/续签/哈希/启动组合 | 失败精确下架或阻断，保留诊断和存档 |

状态 draft → building → candidate → internal → published 的门槛由 [状态机](contracts/state-machines.json) 固定。无需内测分发的候选也必须走 candidate/internal 的验证门槛，不能直接从 draft publish。ordinary unlisted 停新交付；严重 blocked 按精确范围阻断下一次启动。解除阻断需要新证据与独立复核，不以修改 CDN 路径绕过审计。

公开目录必须同时满足可分发范围、对象可交付、客户端模块、设备条件；内部已取得测试输入不代表已获公开分发资格。首发真实清单由 DEC-03 决定，未决定前可继续工程和协议测试，不能把虚构游戏占位写成正式可下载产品。

## 5. Android 发行与更新

applicationId、显示名、图标、正式证书由 DEC-01 在首个对外版本前冻结；开发包与正式包隔离。APK signing key 与内容签名 key 分开保管；失去 App 签名连续性不能靠服务端更新清单修复。versionCode 递增，测试升级覆盖从所有仍支持版安装，保留数据库、设置与存档。

官网渠道可检查已签 app-update metadata，展示大小/说明，下载验证 APK 与证书/包名/versionCode，再交系统安装确认。拒绝、来源权限、取消和安装失败保留当前 App；不得静默安装或把弹出安装器记作更新成功。商店渠道由商店机制更新，不开启站外 APK 自更新兜底。

Google Play 对站外执行代码和自更新有具体限制，本项目内容含执行程序，因此现方案只冻结内部/官网架构；Play 需独立交付方案与渠道审核，不承诺现包形态能直接上架。[Google Play 政策](https://support.google.com/googleplay/android-developer/answer/16559646?hl=en)、[动态加载说明](https://developer.android.com/privacy-and-security/risks/dynamic-code-loading)。

App 新版本不能立即移除仍被允许内容引用的 slot。默认至少支持当前和上一个内容窗口，实际窗口由发布依赖清单显式列明；移除前提供可安装更新/兼容旧版或明确停用策略，保留可导出的存档。回滚 App 涉及系统防降级和数据库 schema，不等同于内容回退，不承诺任意安装旧 APK。

## 6. 系统适配约束

目标 min API31、compile/target36；实际支持以测试记录为准。API34+ 大下载采用 UIDT，旧版适配合法前台机制；系统停止、通知拒绝与后台限额进入可恢复状态，不绕过系统无限保活。Android 15+ 前台服务时限必须单独覆盖，不能把所有大包交给不检查时限的 dataSync 服务。[UIDT](https://developer.android.com/develop/background-work/background-tasks/uidt)、[FGS 时限](https://developer.android.com/develop/background-work/services/fgs/timeout)。

原生 `.so`、APK 打包对齐、改写 loader 均验证 4 KiB/16 KiB 目标页大小。仅设置 manifest 或 ELF 对齐不能证明匿名可执行映射可用；不支持某组合时明确标记并拒绝下载。没有对应真机时该项记录未验证，不能用禁止的模拟器伪造覆盖。[页面大小文档](https://developer.android.com/guide/practices/page-sizes)。

Android 16 大屏对方向/可调整大小有系统行为变化；手机竖屏大厅是产品基线，平板必须有可用布局与回流，不能伪报游戏 category 绕过大厅适配。系统边缘到边缘、返回手势、IME、状态栏/手势区与折叠变化均纳入实机验证。[Android 16 适配行为](https://developer.android.com/about/versions/16/behavior-changes-16#adaptive-layouts)。

正式网络只用 HTTPS，禁 cleartext、信任全部证书和自定义空校验器。开发 CA 只在 debug 配置；不把它加到 release。导入导出只通过 SAF 或受控私有目录，公开组件 `exported` 最小化，URI 授权最小读/写且仅对选中文件。[网络安全配置](https://developer.android.com/privacy-and-security/security-config)。

## 7. 配置、凭据与环境

| 配置类别 | 示例键名 | 处理 |
| --- | --- | --- |
| 非秘密运行配置 | NSA_API_BASE_URL、NSA_OBJECT_ENDPOINT、NSA_REGION、NSA_CHANNEL | 构建/部署校验实际域名，不接受玩家覆盖到任意地址 |
| 数据库 | NSA_DATABASE_URL | 凭据提供器注入；日志仅记录服务别名 |
| 对象存储 | NSA_OBJECT_ACCESS_KEY_REF、NSA_OBJECT_SECRET_REF | 引用凭据，不在客户端出现 |
| 管理身份 | NSA_OIDC_ISSUER、NSA_OIDC_AUDIENCE、NSA_ADMIN_ORIGINS | 服务端白名单验证，不接受任意 discovery 地址 |
| 签名 | NSA_SIGNER_ENDPOINT、NSA_RELEASE_KEY_ID、NSA_POLICY_KEY_ID | 私钥在独立 signer/受控密钥保管；用途不可混用 |
| 保留和限额 | NSA_DIAGNOSTIC_RETENTION_DAYS、NSA_UPLOAD_LIMIT_BYTES | 变更须审查隐私说明和默认值，不能无限保留 |

环境至少分 dev/test/production，数据库、bucket、签名根和管理身份权限隔离。生产恢复演练使用隔离位置，不能把私有材料复制到公共测试 bucket。脚本只输出键是否存在及引用标识，不输出值；不得把 `.env`、keystore、prod.keys、服务账号 JSON 或临时 URL 提交。

## 8. 容量与可观察性

资源预算以具体输入重算，不把此前实验目录空间等同于线上容量。客户端预算见 [04](04-transfer-install-save.md)；服务端至少计当前/回退内容、候选与未完成对象、APK/模块、索引/素材、证据和备份。统一显示 GiB/MiB，并标注计费方 GB 与 GiB 差异。

```text
存储 = 所有保留不可变对象实际占用 + 索引/素材/证据 + 备份 + 暂存余量
月交付量 ≈ Σ(有效下载次数 × 包大小 × 实际重传系数) + APK/素材流量
源站出流 ≈ CDN 未命中与回源量；不能把 CDN 命中误当免付用户出流
```

例如 1000 次完整下载、每包 10 GiB、重传系数 1.1，仅内容交付约 10.74 TiB；价格要在选定供应商/区域后测算，不捏造免费额度足够。大包修复 P0 可重取损坏段/完整包，未实现的差分更新不能记入节流收益。

监控必须分辨目录成功率、授权/续签、下载开始到完整、校验/安装、启动到 interactive、正常退出、崩溃/ANR、存档提交/实际重载、回退与清理。指标只用低基数维度和去标识统计，session_id/request_id 留受限追踪而非所有指标标签。失败分母包含用户真实尝试，用户取消和系统暂停单列，不删除失败记录美化成功率。

## 9. 备份、恢复与值班

工程默认数据库 RPO≤24 小时、RTO≤4 小时；最终服务目标/地区/预算经 DEC-09 冻结，默认值不是对外 SLA。每日受控加密备份、保留默认 7 个日点和 4 个周点；生产密钥、角色、对象版本和签名公钥保留关系有独立恢复方案。备份必须实际恢复演练后才叫可恢复。

发布内容对象不可变且保留回退窗口，GC 先计算数据库/候选/活授权/回退/审计引用再删除。灾难恢复先恢复身份/范围/阻断与审计，再开放下载；禁止恢复旧目录后跳过较新安全策略。玩家存档只在设备，P0 平台备份不能恢复未导出的私有存档，卸载前在产品内明确引导导出。

值班手册至少包含：API/对象不可用、凭据泄露、错误发行、签名 key 风险、严重游戏版本问题、磁盘/队列增长、诊断清理失败。每项记录识别指标、负责人、缓解动作、精确阻断/回退、验证与恢复条件；不默认发送外部消息或自动公开发布。G3 前完成至少一次范围阻断和一次备份恢复演练并保存脱敏证据。
