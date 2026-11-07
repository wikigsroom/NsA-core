# 部署与内容运营手册

[English](deployment.md) · [开发](development.zh-CN.md) · [发行流程](releases/README.md)

v0.4.0 公开源码与桌面制作工具，本次不会自动部署公共游戏目录。下文说明已有实现的生产配置与操作顺序，不代表生产基础设施已经上线。

## 服务与生产前置条件

FastAPI API 与校验 worker 分别运行，运营网页由 API 的 `/operations/` 提供。生产需要 PostgreSQL、私有 S3 兼容存储、真实 HTTPS 地址、OIDC 和 Ed25519 内容签名私钥；Windows 本地开发采用 SQLite/文件对象和隔离开发凭据。

在独立环境安装 `services/requirements.txt`，用部署环境的秘密管理或进程环境注入配置，不写入版本控制。仓库根执行时，`PYTHONPATH` 同时包含 `services` 与仓库根。

| 变量 | 含义 |
|---|---|
| `NSA_MODE=production` | 强制生产提供方和地址要求 |
| `NSA_DATABASE_URL` | PostgreSQL 连接地址 |
| `NSA_LOCAL_ROOT` | 私有工作/密钥目录，默认 `services/local` |
| `NSA_SERVICE_SECRET` | 配置至少 32 个随机字节所对应的秘密值 |
| `NSA_PUBLIC_URL` | 真实 HTTPS API 地址，用于授权对象交付地址 |
| `NSA_SIGNING_KEY`、`NSA_KEY_ID` | Ed25519 私钥路径与公开信任编号 |
| `NSA_S3_BUCKET`、可选 `NSA_S3_ENDPOINT` | 私有对象桶及提供方端点 |
| 标准 AWS 凭据链 | 所选 S3 服务的访问凭据 |
| `NSA_OIDC_ISSUER`、`NSA_OIDC_AUDIENCE`、`NSA_OIDC_JWKS_URL` | 运营 token 的签名、签发者、受众核对 |
| `NSA_OIDC_CLIENT_ID`、`NSA_OIDC_AUTHORIZATION_ENDPOINT`、`NSA_OIDC_TOKEN_ENDPOINT` | 运营网页 Authorization Code + PKCE 登录 |

使用真实配置；文档示例域名只是占位。按部署主机设置 HTTPS 终止/反代、对象权限、备份、诊断保留与进程守护。本地开发运营凭据不能作为生产登录。

## 初始化与启动

```powershell
$env:PYTHONPATH = (Join-Path (Get-Location) 'services') + ';' + (Get-Location)
# 通过进程或秘密配置设置上表生产变量。
services/.venv/Scripts/python.exe services/migrate.py
services/.venv/Scripts/python.exe -m uvicorn catalog.app:create_app --factory --host 127.0.0.1 --port 8443
```

使用相同数据库/存储配置，单独守护运行 worker：

```powershell
services/.venv/Scripts/python.exe -m worker.main
```

`migrate.py` 当前初始化 schema 1 和策略序列，不删除原记录；它不是任意版本自动迁移系统。对已有生产库的结构变化，应先备份并审查迁移。

示例 API 仅监听 localhost，由运营方维护的 HTTPS 反代提供公网地址。worker 需读回上传对象并续期任务归属；对象权限、未完成分片生命周期与诊断删除策略按实际提供方配置。

## Android 分发配置

冻结包名/显示身份、版本递增、正式签名保管方式和目录服务。App 签名与内容签名使用不同密钥。当前内部 App 版本仍为 `0.1.0-dev`；仓库 `0.4.0` 发布不会给内部二进制改名冒充新版。

运营方组装 release 时传入 `-Pnsa.catalogUrl=https://YOUR_REAL_CATALOGUE`，配置对应 `trusted-keys.json` 公钥信任，只选择获准的原生模块。通过受保护密钥配置 Android 签名，分发前核对完成的 APK 与证书。当前 Gradle release 若未提供签名仍是未签名产物，不能作为可安装正式附件。

本地 CA 与一次性邀请仅属于 debug。生产采用系统 HTTPS 信任。公开分发物不得混入私有游戏模块/资源、服务秘密或玩家数据。服务目录必须匹配分发 APK 的真实注册表与内容身份。

## 一款游戏的发布流程

1. 登记元数据、交付范围与权利记录。
2. 从记录的源码提交编译所选运行模块/配置，登记构建、SBOM 和真实产物摘要。
3. 打包完整文件/分块索引与不可变发布编号。
4. 获取授权上传实际字节；大文件使用 64 MiB 分片，完成前检查顺序、唯一、连续与存储回读。
5. 构建 SBOM 用 `nsa:release-manifest` 登记拟发布清单；worker 核对归档和索引一致后才能签发。
6. 由独立审核者确认，再发布到公共或内测范围。
7. 登记准确 App/内容/配置/驱动组合的实机证据，实验与已验证状态分别管理。

现有发布客户端通过独立编辑/审核身份实现本地内测流程，将幂等、上传分片与操作回执保存在私有日志。生产使用配置好的运营授权与真实获准记录，不生成虚构的已验证证据。阻断/下架是明确操作，不删除玩家存档。

## 策略与离线行为

策略带签名、作用域和单调版本；已知阻断/撤销不会因清目录缓存而丢失，拒绝回退旧 epoch。已准备内容只有在有效策略允许时才能离线运行；长期离线手机不能立即得知尚未同步的撤销。

下载授权绑定不可变对象、设备能力评估和发布版本，临时链接只为同一身份续签。反馈诊断每次选择、过滤、有大小限制并遵循保留期限。

## 运维、备份与更新

| 领域 | 维护要求 |
|---|---|
| 数据库 | 备份并验证恢复，保留策略/审计/任务归属历史 |
| 对象存储 | 已发布对象不可变，核对回读并清理未完成分片 |
| 签名 | 保护私钥、保留 App 签名连续性、分发匹配的内容公钥信任 |
| 人员访问 | OIDC、真实角色、独立审核与修订号/幂等检查 |
| 监控 | API/worker 健康、校验失败、租约过期、队列/空间压力和诊断清理 |
| 玩家更新 | APK/内容分别发布，备份和迁移兼容，准备可用回退 |
| 发布失败 | 保留草稿/日志，检查错误，在仍有效时恢复同一不可变操作 |

首帧不能成为健康/已验证标签。App 更新要求包名和签名连续；修改已发布字节或移动标签不能作为修复方式，产物变化应发布新版本。

[发行技术约束](engineering/07-build-release-operations.md) · [契约](engineering/contracts/README.md) · [当前验收状态](hall/implementation-status.md)
