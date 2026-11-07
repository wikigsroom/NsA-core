# NsA 完整文档索引

[English](README.md) · [项目首页](../README.zh-CN.md) · [版本说明](releases/v0.4.0.md)

本体系围绕统一 Android 游戏大厅和原生内容制作流程，分别提供玩家操作、开发、部署、设计及实际验证入口。

| 读者 | 首先阅读 | 继续参考 |
|---|---|---|
| 玩家或体验评审 | [玩家手册](hall/README.zh-CN.md) | [页面与实机游戏](media/README.md)、[已知限制](hall/implementation-status.md) |
| Android/原生开发 | [开发手册](development.zh-CN.md) | [运行接入约束](engineering/03-runtime-integration.md)、[总体架构](engineering/01-architecture.md) |
| 服务开发 | [开发手册](development.zh-CN.md) | [机器契约](engineering/contracts/README.md)、[服务方案](engineering/06-services-api.md) |
| 运营与发行维护 | [部署手册](deployment.zh-CN.md) | [v0.4.0 说明](releases/v0.4.0.md)、[发布流程](releases/README.md) |
| 产品与设计 | [产品基线](product/README.md) | [360 条需求细则](product/产品需求细则.md)、[完整设计定稿](design/obsidian-portrait/README.md) |
| 测试 | [真机验证流程](hall/device-validation.md) | [需求对照](hall/requirement-coverage.md)、[当前候选执行记录](hall/session-recovery-round-2026-11-07.md) |
| 单游戏移植制作者 | [桌面工具安装](../packaging/INSTALL.md) | [原引擎架构](ARCHITECTURE.md)、[制作工具开发](DEVELOPMENT_GUIDE.md) |

中英操作文档对应同一实现。产品、设计与技术规范目前主要为中文。面向读者的日期与执行记录使用 Asia/Taipei；明确标记 UTC 的 JSON 时间继续保留 UTC。

源码反映实际实现；规范定义需要达到的行为；执行记录说明真正测过的范围。设计图、源码对照、游戏首帧或软件版本发布，均不能直接作为完整游戏验收。

## 文档分组

- `hall/`：玩家行为、空间实测、验收步骤与保留原身份的历史记录。
- `development*.md`：Windows 原生开发准备、准确命令、源码结构与扩展规则。
- `deployment*.md`：生产配置、内容与签名流程、服务部署和维护责任。
- `media/`：经过检查的真机与后台网页实拍，每份附来源。
- `releases/`：中英版本说明与自动发布规则。
- `product/`：稳定需求编号、细则、验收案例和离线手册。
- `engineering/`：开发约束、Schema、OpenAPI、状态机与 ADR。
- `design/obsidian-portrait/`：定稿暗色竖屏设计与原型，明确归为设计材料。

[English 项目 README](../README.md) · [中文项目说明](../README.zh-CN.md)
