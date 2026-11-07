# 契约示例边界

[compiled-slot-identity.json](compiled-slot-identity.json) 是 [编译身份 schema](../compiled-slot-identity.schema.json) 的虚构示例，必须与本目录运行注册表的首个 slot 精确一致，但不包含 `library_sha256`；实际验证还要读取 ELF 和真实 SONAME。本示例不是已编译运行库或可玩性证据。

本目录为虚构的协议结构夹具。demo 标识、摘要与模块不代表真实可下载或可运行游戏；archive 的占位摘要不对应真实 ZIP。file/chunk 索引和清单间的元数据可做一致性检查，不将其当端到端游戏包。

release-envelope.json 使用临时生成、只用于本目录的测试公钥验签，测试公钥在 fixture-public-key.json；私钥不保存、不用于发行。生产根公钥与签名流程另行配置。
