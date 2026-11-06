# NsA · 技术开发约束基线 v2.0

2026-11-06 · 对应已选 **D · 曜黑极简**、默认竖屏大厅。本文档包固定下一步开发的行为、契约、架构和验收；它不表示统一 Android App、在线服务或多游戏运行已经完成。

基线描述保留制定时点；后续实现与测试以[实施手册](../hall/README.zh-CN.md)、[验证记录](../hall/implementation-status.md)、[代码对照](../hall/requirement-coverage.md)和[实施决策](decisions/0005-client-guardrails-and-cache.md)为准。基线未验收状态不会由新增代码自动提升。

[定向源码核对](../hall/source-review.md)记录 APP-05、APP-08、SAVE-04 的开放问题、独立反证及下一整批修正/验证约束。该报告没有修改规范编号、降低 P0 要求或提升验收状态；已安装与通常路径存在不等于异常边界已可靠实现。

先读 [产品主需求](../product/游戏大厅二次开发需求说明书.md)、[90 项需求的 360 条实施规则](../product/产品需求细则.md) 和 [32 页交互规格](../product/页面与交互验收规格.md)，再按本目录约束实现。视觉依据 [设计定稿](../design/obsidian-portrait/README.md)，工作拆分与真实验收依据 [开发计划](../product/开发计划与验收矩阵.md)。离线通读可打开 [完整手册](../product/产品与技术开发基线.html)。

| 章节 | 解决的问题 |
| --- | --- |
| [01 总体架构与选型](01-architecture.md) | 现有仓库边界、统一 APK、多模块、独立游戏进程、工程结构和版本锁 |
| [02 Android 客户端](02-android-client.md) | 32 页、事实来源、动作解析、导航/方向、权限、无障碍 |
| [03 原生运行接入](03-runtime-integration.md) | bootstrap ABI、模块注册、会话 IPC、Surface/输入、正常/异常退出 |
| [04 传输安装与存档](04-transfer-install-save.md) | Range/续签、检查点、空间、安全解包、激活/回退、存档交换 |
| [05 数据与状态](05-data-state.md) | Room/PostgreSQL、身份/字节、独立状态机、约束、幂等、迁移与对账 |
| [06 服务与 API](06-services-api.md) | 公开/管理接口、游客与内测、OIDC/RBAC、发布审核、反馈诊断 |
| [07 制作发行与运行保障](07-build-release-operations.md) | 构建溯源、签名、对象交付、更新、渠道、系统适配、容量与恢复 |
| [08 质量与治理](08-quality-governance.md) | 故障矩阵、可玩性测量、门槛、PR/变更、证据与开发约束 |
| [机器契约说明](contracts/README.md) | OpenAPI、JSON Schema、状态、错误、C ABI 和可验证示例 |

## 执行规则

1. 用户指示与适用 AGENTS 优先；新增内容与缓存在 `E:\dev\sandbox\游戏移植`，不调用 Docker/WSL/VM/模拟器；浏览器操作结束关闭不用的 tab。
2. 保留原有 90 需求、24 工作项、58 验收编号，细则用 `.01` 等扩展；补充交互 INT 与契约案例 CON 不冒充新一级页面或已测结果。
3. 产品/页面定义行为，机器契约固定线上/本地格式和状态，技术章固定实现约束，设计源固定视觉。冲突需同步修订，不选一个旧稿绕过另一规范。
4. [工程默认参数](../product/data/defaults.json) 可直接作为当前研发默认，变更走 ADR/CR；DEC-01～09 的商业/发行事项在对应阶段前确定，不能伪造已批准。
5. G0 先验证同 APK 两款真实游戏、独立进程和存读档。工程方向为 Kotlin/Compose + Room、预置运行模块、签名版本内容、FastAPI/PostgreSQL/私有对象存储；当前没有完成这些新增模块。
6. 测试示例不是游戏包；文档校验不是实机验收。可玩状态与当前低帧率 BotW 实验分开。先处理真实依赖与失败，再记录证据。

## 版本与校验

文档基线 v2.0；HTTP `/v1`/OpenAPI 3.1.1；数据/manifest/save/registry schema v1；原生 bootstrap ABI v1。这些版本互不替代，破坏性修改须评估消费者、数据迁移和兼容窗口。精确工具链版本在 G0 首次可重现构建时锁定，缺锁不放行。

执行方式见 [契约说明](contracts/README.md)。[校验器](verify_baseline.py) 只读源文件，报告可输出到任务根；[本次校验记录](data/baseline-review.json) 仅说明文档契约的静态检查结果。所有实际 App/服务/游戏案例仍待实现及执行。
