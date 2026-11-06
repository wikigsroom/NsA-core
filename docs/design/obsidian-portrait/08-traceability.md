# 08 · 需求与设计追踪

逐项覆盖既有产品需求中的 90 个唯一编号。映射表示设计已有落点或明确的工程/运营责任，不代表该功能代码、服务或真机验收已完成。P1 项明确保留原范围，不混入当前 P0 主导航。

[原始需求全文](../../product/游戏大厅二次开发需求说明书.md) · [可读页面规格](03-screen-specs.md) · [机器可读追踪](data/traceability.json)

| 需求 | 优先级 | 页面/边界 | 设计落实及后续实现责任 | 负责人领域 |
| --- | --- | --- | --- | --- |
| APP-01 | P0 | [S00](03-screen-specs.md#s00) / [S23](03-screen-specs.md#s23) | 一个固定包名、一个桌面入口；页面按统一状态规则表达，实现仍按原需求验收。 | Android |
| APP-02 | P0 | [S00](03-screen-specs.md#s00) / [S01](03-screen-specs.md#s01) | 全新安装后可直接进入游客大厅；页面按统一状态规则表达，实现仍按原需求验收。 | Android |
| APP-03 | P0 | [S00](03-screen-specs.md#s00) / [S20](03-screen-specs.md#s20) | 首次启动执行轻量能力采集并保留结果；页面按统一状态规则表达，实现仍按原需求验收。 | Android |
| APP-04 | P0 | [S25](03-screen-specs.md#s25) / [S04](03-screen-specs.md#s04) / [S15](03-screen-specs.md#s15) | 权限按用户动作申请；页面按统一状态规则表达，实现仍按原需求验收。 | Android |
| APP-05 | P0 | [S07](03-screen-specs.md#s07) / [S27](03-screen-specs.md#s27) / [S28](03-screen-specs.md#s28) | 无网络时可打开大厅和我的游戏；页面按统一状态规则表达，实现仍按原需求验收。 | Android |
| APP-06 | P0 | [S01](03-screen-specs.md#s01) / [S07](03-screen-specs.md#s07) / [S18](03-screen-specs.md#s18) | 竖屏非游戏流程、底部四入口、语义与字体/触控规范；系统读屏需原生验证。 | Android |
| APP-07 | P0 | [S00](03-screen-specs.md#s00) / [S03](03-screen-specs.md#s03) / [S05](03-screen-specs.md#s05) | 面向玩家的按钮使用“下载、准备中、开始游戏、返回大厅”等可理解语言；页面按统一状态规则表达，实现仍按原需求验收。 | Android |
| APP-08 | P0 | [S05](03-screen-specs.md#s05) / [S08](03-screen-specs.md#s08) / [S12](03-screen-specs.md#s12) | 前后台切换、来电和系统重建后恢复最近页面与实际任务状态；页面按统一状态规则表达，实现仍按原需求验收。 | Android |
| CAT-01 | P0 | [S01](03-screen-specs.md#s01) / [S03](03-screen-specs.md#s03) | 服务端维护有发布状态的游戏目录；页面按统一状态规则表达，实现仍按原需求验收。 | Android / 目录服务 |
| CAT-02 | P0 | [S02](03-screen-specs.md#s02) | 支持名称、英文名、中文别名的搜索，大小写和首尾空格不影响结果；页面按统一状态规则表达，实现仍按原需求验收。 | Android / 目录服务 |
| CAT-03 | P0 | [S01](03-screen-specs.md#s01) / [S02](03-screen-specs.md#s02) | 首版提供“本机可玩、已安装、类型”等必要筛选及清除筛选；页面按统一状态规则表达，实现仍按原需求验收。 | Android / 目录服务 |
| CAT-04 | P0 | [S03](03-screen-specs.md#s03) | 每款游戏有独立详情，显示游戏版本、语言、操作支持、下载大小、安装后大小、安装期间所需空间和已知限制；页面按统一状态规则表达，实现仍按原需求验收。 | Android / 目录服务 |
| CAT-05 | P0 | [S03](03-screen-specs.md#s03) / [S20](03-screen-specs.md#s20) | 本机状态至少区分“已验证可玩、实验运行、未验证、不支持、需要更新 App”；页面按统一状态规则表达，实现仍按原需求验收。 | Android / 目录服务 |
| CAT-06 | P0 | [S03](03-screen-specs.md#s03) / [S22](03-screen-specs.md#s22) | 只有可公开分发、内容包完整且存在匹配运行模块的版本可供正式下载；页面按统一状态规则表达，实现仍按原需求验收。 | Android / 目录服务 |
| CAT-07 | P0 | [S03](03-screen-specs.md#s03) / [S05](03-screen-specs.md#s05) / [S07](03-screen-specs.md#s07) | 同一游戏条目关联同一个下载与安装记录；页面按统一状态规则表达，实现仍按原需求验收。 | Android / 目录服务 |
| CAT-08 | P0 | [S01](03-screen-specs.md#s01) / [S27](03-screen-specs.md#s27) | 目录、封面与最近一次兼容结果可缓存；页面按统一状态规则表达，实现仍按原需求验收。 | Android / 目录服务 |
| CAT-09 | P0 | [S01](03-screen-specs.md#s01) / [S20](03-screen-specs.md#s20) | 正式区默认优先展示本机已验证内容；页面按统一状态规则表达，实现仍按原需求验收。 | Android / 目录服务 |
| CAT-10 | P1 | [S01](03-screen-specs.md#s01) / [S02](03-screen-specs.md#s02) | P1 收藏/个性化不新增 P0 页面；目录模型保留扩展，具体功能后续另行设计。 | Android / 目录服务 |
| DL-01 | P0 | [S03](03-screen-specs.md#s03) / [S04](03-screen-specs.md#s04) | 点击下载先检查内容访问资格、本机支持、App/模块版本和可用空间；页面按统一状态规则表达，实现仍按原需求验收。 | Android / 传输调度 |
| DL-02 | P0 | [S04](03-screen-specs.md#s04) / [S19](03-screen-specs.md#s19) | 下载前显示下载量、安装后大小、当前还需可用空间；页面按统一状态规则表达，实现仍按原需求验收。 | Android / 传输调度 |
| DL-03 | P0 | [S05](03-screen-specs.md#s05) | 展示已下载字节、总字节、百分比、速度和剩余时间估计；页面按统一状态规则表达，实现仍按原需求验收。 | Android / 传输调度 |
| DL-04 | P0 | [S05](03-screen-specs.md#s05) | 支持暂停和继续；页面按统一状态规则表达，实现仍按原需求验收。 | Android / 传输调度 |
| DL-05 | P0 | [S05](03-screen-specs.md#s05) / [S27](03-screen-specs.md#s27) | 进程被回收或设备重启后，任务记录和已验证分段可恢复；页面按统一状态规则表达，实现仍按原需求验收。 | Android / 传输调度 |
| DL-06 | P0 | [S05](03-screen-specs.md#s05) | 界面只表达恢复阶段和原因；Range/ETag/签名身份由传输层验证。 | Android / 传输调度 |
| DL-07 | P0 | [S04](03-screen-specs.md#s04) / [S05](03-screen-specs.md#s05) | 一款游戏只存在一个活动下载任务；页面按统一状态规则表达，实现仍按原需求验收。 | Android / 传输调度 |
| DL-08 | P0 | [S05](03-screen-specs.md#s05) | 取消任务需说明将删除未完成文件；页面按统一状态规则表达，实现仍按原需求验收。 | Android / 传输调度 |
| DL-09 | P0 | [S05](03-screen-specs.md#s05) / [S27](03-screen-specs.md#s27) | 断网、超时、429 和服务端短暂失败可以有限退避重试；页面按统一状态规则表达，实现仍按原需求验收。 | Android / 传输调度 |
| DL-10 | P0 | [S05](03-screen-specs.md#s05) / [S25](03-screen-specs.md#s25) | 下载大包按适用 Android 版本接入系统允许的用户主动传输机制与可见状态；页面按统一状态规则表达，实现仍按原需求验收。 | Android / 传输调度 |
| DL-11 | P0 | [S05](03-screen-specs.md#s05) / [S19](03-screen-specs.md#s19) | Wi-Fi 转到移动网络、网络失去互联网或计费属性改变时重新检查已授权网络策略；页面按统一状态规则表达，实现仍按原需求验收。 | Android / 传输调度 |
| DL-12 | P0 | [S05](03-screen-specs.md#s05) / [S14](03-screen-specs.md#s14) | 下载过程中持续检查剩余空间和写入错误；页面按统一状态规则表达，实现仍按原需求验收。 | Android / 传输调度 |
| DL-13 | P0 | [S05](03-screen-specs.md#s05) / [S06](03-screen-specs.md#s06) | 校验、解包、安装准备与联网下载显示为不同阶段；页面按统一状态规则表达，实现仍按原需求验收。 | Android / 传输调度 |
| DL-14 | P0 | [S08](03-screen-specs.md#s08) / [S09](03-screen-specs.md#s09) / [S05](03-screen-specs.md#s05) | 运行期间暂停其他资源重任务，区分系统策略与用户暂停；返回后按偏好恢复。 | Android / 传输调度 |
| DL-15 | P1 | [S05](03-screen-specs.md#s05) / [S19](03-screen-specs.md#s19) | P1 限速/排序/完成后开始保留范围；本轮默认单下载、手动开始，无后台自动横屏。 | Android / 传输调度 |
| PKG-01 | P0 | [S03](03-screen-specs.md#s03) / [S06](03-screen-specs.md#s06) | 玩家只下载已制作包并查看准备；解包原始游戏/编译/密钥仍在受控制作链。 | 制作链 / 安装层 |
| PKG-02 | P0 | [S06](03-screen-specs.md#s06) / [S31](03-screen-specs.md#s31) | 验签与完整性由安装层执行；UI 表达校验/失败，不展示密钥配置。 | 制作链 / 安装层 |
| PKG-03 | P0 | [S03](03-screen-specs.md#s03) / [S06](03-screen-specs.md#s06) | 内容包必须声明游戏 ID、游戏版本、模块 Build ID、运行模块 ID、内容包格式、着色器格式/转换链和最低 App 要求；页面按统一状态规则表达，实现仍按原需求验收。 | 制作链 / 安装层 |
| PKG-04 | P0 | [S06](03-screen-specs.md#s06) | 安全解包由安装层处理，失败保持旧有效版与存档，页面提供恢复。 | 制作链 / 安装层 |
| PKG-05 | P0 | [S04](03-screen-specs.md#s04) / [S14](03-screen-specs.md#s14) | 下载、解包和旧版并存的峰值按当前真实字节数计算；页面按统一状态规则表达，实现仍按原需求验收。 | 制作链 / 安装层 |
| PKG-06 | P0 | [S06](03-screen-specs.md#s06) / [S26](03-screen-specs.md#s26) | 不可变目录/提交记录由安装层实现；准备未完成不能出现普通开始。 | 制作链 / 安装层 |
| PKG-07 | P0 | [S13](03-screen-specs.md#s13) / [S15](03-screen-specs.md#s15) / [S30](03-screen-specs.md#s30) | 每个游戏的资源、改写模块、存档、设置、缓存、日志和临时文件分别命名空间隔离；页面按统一状态规则表达，实现仍按原需求验收。 | 制作链 / 安装层 |
| PKG-08 | P0 | [S06](03-screen-specs.md#s06) / [S13](03-screen-specs.md#s13) | 首次创建本机图形缓存可显示独立准备状态，后续复用缓存；页面按统一状态规则表达，实现仍按原需求验收。 | 制作链 / 安装层 |
| PKG-09 | P0 | [S08](03-screen-specs.md#s08) / [S31](03-screen-specs.md#s31) | 安装完成写入可核对的本地记录；页面按统一状态规则表达，实现仍按原需求验收。 | 制作链 / 安装层 |
| RUN-01 | P0 | [S03](03-screen-specs.md#s03) / [S08](03-screen-specs.md#s08) | 开始前再次检查游戏、包版本、模块、设备、有效安装和当前会话；页面按统一状态规则表达，实现仍按原需求验收。 | Android / 原生运行层 |
| RUN-02 | P0 | [S08](03-screen-specs.md#s08) / [S09](03-screen-specs.md#s09) | 同 App 独立游戏进程与一次一会话；原型只展示横屏边界。 | Android / 原生运行层 |
| RUN-03 | P0 | [S08](03-screen-specs.md#s08) / [S09](03-screen-specs.md#s09) / [S12](03-screen-specs.md#s12) | 启动阶段区分检查资源、加载游戏、准备画面和进入游戏；页面按统一状态规则表达，实现仍按原需求验收。 | Android / 原生运行层 |
| RUN-04 | P0 | [S17](03-screen-specs.md#s17) / [S09](03-screen-specs.md#s09) | 复用并适配触屏手柄，支持位置、尺寸、透明度与每游戏保存的布局；页面按统一状态规则表达，实现仍按原需求验收。 | Android / 原生运行层 |
| RUN-05 | P0 | [S17](03-screen-specs.md#s17) / [S20](03-screen-specs.md#s20) | 标注并验证游戏所需的输入方式；页面按统一状态规则表达，实现仍按原需求验收。 | Android / 原生运行层 |
| RUN-06 | P0 | [S10](03-screen-specs.md#s10) / [S17](03-screen-specs.md#s17) | 暂停菜单可返回游戏、调整已验证的画质/帧率目标、控制布局、音量和返回大厅；页面按统一状态规则表达，实现仍按原需求验收。 | Android / 原生运行层 |
| RUN-07 | P0 | [S11](03-screen-specs.md#s11) | 返回大厅前提示未由游戏保存的进度可能丢失；页面按统一状态规则表达，实现仍按原需求验收。 | Android / 原生运行层 |
| RUN-08 | P0 | [S07](03-screen-specs.md#s07) / [S09](03-screen-specs.md#s09) / [S11](03-screen-specs.md#s11) | 设计规定退出恢复和再次选择；真实 A→B→A 与原生资源释放仍须真机验收。 | Android / 原生运行层 |
| RUN-09 | P0 | [S09](03-screen-specs.md#s09) / [S10](03-screen-specs.md#s10) / [S12](03-screen-specs.md#s12) | 切后台时暂停或按已验证策略挂起画面、声音和输入；页面按统一状态规则表达，实现仍按原需求验收。 | Android / 原生运行层 |
| RUN-10 | P0 | [S12](03-screen-specs.md#s12) | 游戏进程崩溃或退出后，大厅能恢复或重新构建，显示该会话失败原因与反馈入口；页面按统一状态规则表达，实现仍按原需求验收。 | Android / 原生运行层 |
| RUN-11 | P0 | [S08](03-screen-specs.md#s08) / [S12](03-screen-specs.md#s12) / [S17](03-screen-specs.md#s17) | 来电、耳机变化、锁屏、亮暗模式按应用/游戏支持范围处理；页面按统一状态规则表达，实现仍按原需求验收。 | Android / 原生运行层 |
| RUN-12 | P1 | [S17](03-screen-specs.md#s17) / [S20](03-screen-specs.md#s20) | P1 陀螺仪/振动/外接屏按已验证声明扩展，本轮不添加未验证开关。 | Android / 原生运行层 |
| SAVE-01 | P0 | [S15](03-screen-specs.md#s15) | 存档按游戏 ID、玩家本地档案和存档格式隔离；页面按统一状态规则表达，实现仍按原需求验收。 | Android / 存档层 |
| SAVE-02 | P0 | [S11](03-screen-specs.md#s11) / [S15](03-screen-specs.md#s15) | 展示最近已提交事实，不把现有写档证据等同真实读档通过。 | Android / 存档层 |
| SAVE-03 | P0 | [S30](03-screen-specs.md#s30) / [S13](03-screen-specs.md#s13) | 删除已安装游戏资源默认保留存档、控制布局和必要设置；页面按统一状态规则表达，实现仍按原需求验收。 | Android / 存档层 |
| SAVE-04 | P0 | [S15](03-screen-specs.md#s15) / [S16](03-screen-specs.md#s16) | 提供导出已提交存档至系统选择的持久位置，以及导入相应游戏存档；页面按统一状态规则表达，实现仍按原需求验收。 | Android / 存档层 |
| SAVE-05 | P0 | [S16](03-screen-specs.md#s16) / [S26](03-screen-specs.md#s26) / [S31](03-screen-specs.md#s31) | 内容或 App 更新前保留可恢复的存档版本；页面按统一状态规则表达，实现仍按原需求验收。 | Android / 存档层 |
| SAVE-06 | P0 | [S13](03-screen-specs.md#s13) / [S14](03-screen-specs.md#s14) / [S30](03-screen-specs.md#s30) | 清图形缓存、清下载临时文件、卸载游戏资源、重置设置、删除存档分成不同动作，并分别显示可释放空间和影响范围；页面按统一状态规则表达，实现仍按原需求验收。 | Android / 存档层 |
| SAVE-07 | P1 | [S15](03-screen-specs.md#s15) | P1 账号/云存档与冲突处理保留原需求，本轮不增加云同步或登录入口。 | Android / 存档层 |
| SAVE-08 | P0 | [S15](03-screen-specs.md#s15) / [S23](03-screen-specs.md#s23) | 说明卸载整个 App 会删除应用私有数据；页面按统一状态规则表达，实现仍按原需求验收。 | Android / 存档层 |
| UPD-01 | P0 | [S03](03-screen-specs.md#s03) / [S22](03-screen-specs.md#s22) | 发现更新时分别说明“App 更新”和“游戏内容更新”；页面按统一状态规则表达，实现仍按原需求验收。 | Android / 发布服务 |
| UPD-02 | P0 | [S26](03-screen-specs.md#s26) / [S13](03-screen-specs.md#s13) | 正在运行的内容版本保持固定；页面按统一状态规则表达，实现仍按原需求验收。 | Android / 发布服务 |
| UPD-03 | P0 | [S26](03-screen-specs.md#s26) / [S31](03-screen-specs.md#s31) | 新版准备失败仍可使用未撤回的旧版；页面按统一状态规则表达，实现仍按原需求验收。 | Android / 发布服务 |
| UPD-04 | P0 | [S26](03-screen-specs.md#s26) / [S16](03-screen-specs.md#s16) | 自动回退只适用于已声明内容与存档兼容的情况；页面按统一状态规则表达，实现仍按原需求验收。 | Android / 发布服务 |
| UPD-05 | P0 | [S22](03-screen-specs.md#s22) | 官网渠道提供签名一致、versionCode 递增的 App 更新；页面按统一状态规则表达，实现仍按原需求验收。 | Android / 发布服务 |
| UPD-06 | P0 | [S03](03-screen-specs.md#s03) / [S26](03-screen-specs.md#s26) | 下架和紧急阻断分开：普通下架停止新下载；页面按统一状态规则表达，实现仍按原需求验收。 | Android / 发布服务 |
| UPD-07 | P0 | [S03](03-screen-specs.md#s03) / [S27](03-screen-specs.md#s27) | 更新目录或配置失败不会影响已经安装、允许离线且没有本地阻断记录的游戏启动；页面按统一状态规则表达，实现仍按原需求验收。 | Android / 发布服务 |
| UPD-08 | P1 | [S26](03-screen-specs.md#s26) | P1 灰度/增量保留发行扩展；当前说明完整包更新，不承诺差分节省量。 | Android / 发布服务 |
| OPS-01 | P0 | [S01](03-screen-specs.md#s01) / [S03](03-screen-specs.md#s03) | 运营提供真实元数据和素材；玩家页消费公开字段，管理台沿原需求实施。 | 运营 / 发布 / 后端 |
| OPS-02 | P0 | [S01](03-screen-specs.md#s01) / [S03](03-screen-specs.md#s03) | 发行资格由服务端决定可见/下载范围，内部材料不出现在玩家字段。 | 运营 / 发布 / 后端 |
| OPS-03 | P0 | [S03](03-screen-specs.md#s03) / [S06](03-screen-specs.md#s06) | 制作可追溯性属制作/发布交付；玩家仅展示正常版本和必要错误。 | 运营 / 发布 / 后端 |
| OPS-04 | P0 | [S04](03-screen-specs.md#s04) / [S06](03-screen-specs.md#s06) | 不可变对象、真实上传后下载与签名由发布链验证；玩家确认页消费可信发行信息。 | 运营 / 发布 / 后端 |
| OPS-05 | P0 | [S03](03-screen-specs.md#s03) / [S20](03-screen-specs.md#s20) | 本机支持页展示精确证据范围；真实兼容证据由原生测试与运营提供。 | 运营 / 发布 / 后端 |
| OPS-06 | P0 | [S01](03-screen-specs.md#s01) / [S03](03-screen-specs.md#s03) | 发布/撤回状态驱动目录和详情；不在玩家设置中暴露发布按钮。 | 运营 / 发布 / 后端 |
| OPS-07 | P0 | [S01](03-screen-specs.md#s01) / [S03](03-screen-specs.md#s03) | 服务端资格/地区/撤回控制驱动玩家状态；运营权限与玩家身份分离。 | 运营 / 发布 / 后端 |
| OPS-08 | P0 | [S05](03-screen-specs.md#s05) / [S12](03-screen-specs.md#s12) / [S21](03-screen-specs.md#s21) | 玩家故障分类与反馈入口统一；后台监控和工单仍属服务端交付。 | 运营 / 发布 / 后端 |
| OPS-09 | P0 | [S21](03-screen-specs.md#s21) / [S29](03-screen-specs.md#s29) | 表单、脱敏诊断预览/选择、真实受理后成功页；不上传原始输入。 | 运营 / 发布 / 后端 |
| OPS-10 | P0 | [S23](03-screen-specs.md#s23) | 许可/来源可阅读；后台审计与私钥保管不交给玩家页面。 | 运营 / 发布 / 后端 |
| NFR-01 | P0 | [S00](03-screen-specs.md#s00) / [S01](03-screen-specs.md#s01) | 页面采用缓存、占位和渐进加载；3 秒等性能目标须原生参考机测量。 | 对应工程与测试负责人 |
| NFR-02 | P0 | [S05](03-screen-specs.md#s05) / [S06](03-screen-specs.md#s06) / [S14](03-screen-specs.md#s14) | UI 按字节格式化与阶段呈现；大包流式内存和 >4/10GiB 测试由任务层执行。 | 对应工程与测试负责人 |
| NFR-03 | P0 | [S01](03-screen-specs.md#s01) / [S27](03-screen-specs.md#s27) | 离线/服务异常有降级设计；服务可用率须实际监控周期验证。 | 对应工程与测试负责人 |
| NFR-04 | P0 | [S08](03-screen-specs.md#s08) / [S12](03-screen-specs.md#s12) / [S21](03-screen-specs.md#s21) | 运行失败与诊断有页面；崩溃率和持续真游戏验收仍按原计划。 | 对应工程与测试负责人 |
| NFR-05 | P0 | [S20](03-screen-specs.md#s20) | 本机支持不伪造帧率；真实性能分级由设备/版本证据决定。 | 对应工程与测试负责人 |
| NFR-06 | P0 | [S04](03-screen-specs.md#s04) / [S06](03-screen-specs.md#s06) / [S21](03-screen-specs.md#s21) / [S24](03-screen-specs.md#s24) | 资格、校验和诊断选择有清楚状态；HTTPS/验签/密钥保护由工程实施。 | 对应工程与测试负责人 |
| NFR-07 | P0 | [S05](03-screen-specs.md#s05) / [S06](03-screen-specs.md#s06) / [S08](03-screen-specs.md#s08) | 同一对象各入口同源；进程/文件/数据库恢复由端侧事务和状态层实现。 | 对应工程与测试负责人 |
| NFR-08 | P0 | [S17](03-screen-specs.md#s17) / [S20](03-screen-specs.md#s20) | 规定窗口/字体/输入/方向矩阵；真实系统、GPU 和游戏组合单独验证。 | 对应工程与测试负责人 |
| NFR-09 | P0 | [S23](03-screen-specs.md#s23) | 文档与设计资产进入指定仓库；本轮使用本机原生工具且浏览器检查后关闭。 | 对应工程与测试负责人 |
| NFR-10 | P0 | [S21](03-screen-specs.md#s21) / [S24](03-screen-specs.md#s24) | 阶段/原因清楚，诊断由用户选择；容量/保留与服务指标按真实工程实现。 | 对应工程与测试负责人 |

运营制作、签名、审计、服务可用率和真实游戏性能等要求通过数据/事件影响玩家界面，并继续沿用原技术与验收计划；不会因为存在对应截图而自动完成。
