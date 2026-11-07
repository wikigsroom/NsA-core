# 可执行契约与验证说明

新增 [ADR 0008](../decisions/0008-transfer-ownership-and-install-recovery.md) 明确 Room schema 2 及 Android `journal_schema:2` 的私有持久化映射。local-records 新增 AndroidInstallJournalV2 分支，原 record_schema:1 DTO 和既有示例保持兼容；这不表示全部命令 revision/receipt 规则已实现。

契约 v1 · 文档基线 v2.0 · 2026-11-06。这里保存开发必须遵守的协议；契约和示例本身不是服务、安装器或运行模块的实现，也不是验收证据。后续程序与已执行结果见[实施手册](../../hall/README.zh-CN.md)和[验证记录](../../hall/implementation-status.md)，剩余下载/安装缺口见[定向核对](../../hall/transfer-install-review-2026-11-07.md)。示例均为虚构协议数据，不包含可玩的游戏、真实资源、密钥或生产信任根。

## 契约清单

| 文件 | 固定内容 |
| --- | --- |
| [openapi.json](openapi.json) | OAS 3.1.1；40 路径、43 操作；公开/管理身份、字段、幂等、角色和需求关联 |
| [domain.schema.json](domain.schema.json) | 通用标识/字节/状态、公开与管理 DTO；响应可扩展，命令字段严格 |
| [release-manifest.schema.json](release-manifest.schema.json) | 发行、模块/profile/shader/driver 依赖、对象索引、空间、存档与离线条件 |
| [signed-envelope.schema.json](signed-envelope.schema.json) | 原始 payload、用途、key_id、Ed25519、摘要和签名编码 |
| [file-index.schema.json](file-index.schema.json) | 所有声明资源的相对路径、长度、摘要和角色 |
| [chunk-index.schema.json](chunk-index.schema.json) | 固定 8 MiB 段、偏移/长度/摘要、总对象身份 |
| [runtime-registry.schema.json](runtime-registry.schema.json) | APK 内可信 slot、唯一库名、精确构建与依赖版本 |
| [save-backup.schema.json](save-backup.schema.json) | 用户备份中的游戏/档案/组/提交序号/文件清单 |
| [policy.schema.json](policy.schema.json) | 单调 epoch、精确版本下架/阻断、已撤销签名 key |
| [app-update.schema.json](app-update.schema.json) | App 包名/版本/证书、APK 对象和更新说明 |
| [local-records.schema.json](local-records.schema.json) | READY、transfer、install journal、session、save commit 耐久记录 |
| [runtime-event.schema.json](runtime-event.schema.json) | 会话/代次/序号、真实阶段、提交和错误事件 |
| [state-machines.json](state-machines.json) | 传输、安装、会话、发行的枚举、边和守卫说明 |
| [errors.json](errors.json) | 50 错误码、HTTP 状态、重试/恢复与玩家文案 |
| [runtime-api-v1.h](runtime-api-v1.h) | bootstrap/module C ABI、上下文、生命周期回调所有权 |

Schema 使用 Draft 2020-12、离线可解析 `$id`；`schemas.nsa.invalid` 是命名空间，验证时注册本地资源，不发网络请求。OpenAPI 固定 [3.1.1 语义](https://spec.openapis.org/oas/v3.1.1.html)，不把它称为最新版本；实现不能因工具默认 OAS3.0 而改变 nullable/JSON Schema 行为。

## Schema 之外的硬性检查

1. ByteCount 是十进制字符串，语义范围≤Long.MAX_VALUE；任何求和/预留运算检查溢出，超过 2^53 不转 JS Number。
2. 文件路径≤1024 UTF-8 字节、≤32 层，拒绝绝对/盘符/UNC/空/点/上级/反斜线/控制字符、链接与归一化冲突；Windows 制作端拒绝大小写和保留名冲突。仅 regex 不足以代替真实路径归属与 no-follow 检查。
3. file_count 与数组长度、各文件长度之和与 unpacked_bytes 一致；不得有额外文件、重复文件或缺文件。
4. chunk index 从 0 连续、偏移无洞无重叠、非末段 8 MiB、末段为剩余长度，总和等于对象大小；清单和索引身份/摘要一致。
5. slot 和 library_name 唯一，manifest 模块、profile、shader、driver 与 APK 注册表精确匹配；下载包不能指定任意绝对库路径。
6. raw payload 的摘要和 Ed25519 签名通过才解析；目的绑定消息为 `NSA/<purpose>/v1` + NUL + 原始 UTF-8 字节。key_id 必须来自该用途既有信任根。
7. 保存包归档及展开各≤2 GiB、文件≤10000，全部文件摘要/总量匹配；不能覆盖其他游戏或正在运行的存档。
8. 本地 durable≤total；READY 与已签发行/真实目录/DB 一致；session source_screen 实际存在；状态转换、锁、nonce、seq 不由 Schema 自动保证。
9. verified 证据必须包含时长/实际读档/输入/帧时间/游戏速度/测试计划，满足 [质量口径](../08-quality-governance.md)；合法 JSON 不等于已验证可玩。
10. 请求通过 Schema 之后仍要认证、角色/范围检查、幂等、revision、双人审核与真实对象核验；不得以字段正确代替业务守卫。

## 示例与签名向量

[examples](examples/README.md) 提供索引、发行/运行注册表、存档、READY/journal、运行事件和签名 envelope；只包含小型虚构数据。归档本体并不存在，示例 archive hash 只是协议占位，不能用它演示安装成功。文件索引对虚构文字字节计算真实摘要，索引对象摘要与清单互相校验。

签名 fixture 公钥为临时测试生成，`production_trust_root=false`；私钥从未保存。校验器验证原向量通过、被改字节和错用途失败。若重生成向量，必须在临时进程生成新 key、签完整原始 payload，并同步公钥/信封，不把私钥写盘或加入生产配置。

## 重跑文档校验

验证依赖单独列在 [requirements-validation.txt](requirements-validation.txt)，仅供文档工具，不是产品后端依赖锁。使用 Windows 原生 Python 3.13（或经验证兼容的版本）在任务根创建工具目录，依赖与缓存留在用户指定路径。示意命令中的路径按实际工具位置填入：

```powershell
$env:PYTHONDONTWRITEBYTECODE='1'
$env:TEMP='E:\dev\sandbox\游戏移植\tools\temp'
$env:TMP=$env:TEMP
$env:PIP_CACHE_DIR='E:\dev\sandbox\游戏移植\tools\pip-cache'
python -m venv 'E:\dev\sandbox\游戏移植\tools\doc-venv'
& 'E:\dev\sandbox\游戏移植\tools\doc-venv\Scripts\python.exe' -m pip install -r docs/engineering/contracts/requirements-validation.txt
& 'E:\dev\sandbox\游戏移植\tools\doc-venv\Scripts\python.exe' -X utf8 docs/engineering/verify_baseline.py --report 'E:\dev\sandbox\游戏移植\doc-review.json'
```

先创建对应 TEMP/cache 父目录，必要时为已有 Python 传入任务内 `--deps` 目录。校验器只读规范，报告仅写显式路径，默认 stdout。内容包含需求/页面/验收追踪、离线 Schema/OpenAPI、示例语义、签名和负例、状态和本地链接。报告不包含游戏/服务已运行的结论。

离线阅读手册由 [build_handbook.py](../build_handbook.py) 从 14 份规范 Markdown 生成；使用同一文档工具环境运行，修改源后同步重建。正文中的 Mermaid 源以代码块保留供研发查看，不依赖远程渲染服务。
