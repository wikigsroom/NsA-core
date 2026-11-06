# 02 · Android 客户端、设计映射与界面状态

## 1. 定稿约束

UI 唯一方向为 D · 曜黑极简。引用 [令牌](../design/obsidian-portrait/data/tokens.json)、[32页规格](../design/obsidian-portrait/03-screen-specs.md)、[页面行为](../product/页面与交互验收规格.md)。颜色、字号、间距、圆角由统一 DesignSystem 导出；真实文字与矢量组件实现，不切整张生成图当界面。

四个根标签固定为发现游戏、我的游戏、下载、设置；二级页无根导航。字号跟随系统，覆盖 200%、48dp触控和可访问语义。卡片、列表、图片惰性加载，不引入全屏实时模糊或持续循环装饰。

## 2. ViewModel 与命令

每页暴露不可变 UiState：loading/data/error、来源上下文、选中身份、可执行动作及实际任务投影。UI 只能提交稳定 ID 的命令；ViewModel 不保存唯一任务事实，不直接 set installed=true。业务命令返回 accepted/current-state/domain-error；请求中禁用只防误触，真正幂等在协调器/服务。

| 投影 | 最低字段 | 来源 |
| --- | --- | --- |
| GameCard | gameId/name/cover/compatibility/activeInstall/pendingTask | 目录缓存与本地库组合 |
| GameDetail | targetRelease、大小、语言、限制、依赖、PrimaryAction | 服务与本地 action resolver |
| TaskCard | taskId/revision/phase/blockers/verified/total/speed/ETA | TransferRepository |
| Library | selectedGame、activeRelease、recentPlayed、更新/异常 | InstallationRepository |
| Save | group/lastCommit/size/backups/busy | SaveRepository及提交标记 |
| Session | sessionId/revision/phase/alive/returnOrigin | RuntimeCoordinator |
| Storage | volumeId/free/usage/categories/capturedAt | 文件归属与StatFs快照 |

未知数据用 unknown/null，而非0假数值。网络迟到响应按 query/request revision 丢弃；跨标签不重复创建任务。与原生会话的绑定使用 session_id，不能按“当前选中的游戏名”接收事件。

## 3. 主按钮统一判定顺序

action resolver 接收策略、资格、兼容、App/module、active installation、pending task、session、storage，并返回 action、label_key、enabled、reason、target_id。先判断高优先阻断，再判断可继续的现会话，再选择安装/下载动作。

| 优先 | 条件 | 输出 |
| --- | --- | --- |
| 1 | 已知blocked/权限不满足 | unavailable / 查看恢复或资格原因 |
| 2 | 本机unsupported/unverified，或实验无资格 | view_support；不提供普通开始 |
| 3 | 当前App缺模块/ABI/必要驱动 | update_app；不先下载大包 |
| 4 | 相同游戏有效会话 | resume_game；另一游戏会话则给先退出说明 |
| 5 | active ready且关键校验有效 | continue_game / launch；合法旧版不被新版任务覆盖 |
| 6 | active repair_required | repair |
| 7 | 目标发行有下载/安装任务 | view_task；暂停/失败给恢复原因 |
| 8 | 未安装且全部条件满足 | download；点击进入S04复检 |

未在表中开放的状态保守返回 unavailable + 可理解原因。下载100%、prepared 或存在目录均不能直接输出 launch。就绪按钮点击后仍重验，避免页面出现后状态已改变。

## 4. 导航与方向

导航持久化 route、rootTab、gameId、查询/筛选、滚动 item key/offset 和 returnOrigin，不存储可被滥用的任意 URI/路径。根标签维护独立返回栈；深链只允许白名单 game_id/task_id，打开详情或任务，不直接启动或下载。

大厅及准备页保持竖屏。RuntimeActivity 在启动检查阶段仍显示竖屏准备；由本次有效会话进入实际运行窗口时请求横屏。暂停/退出跟随横屏；从暂停进入S17使用竖屏设置，返回原暂停会话再横屏。系统强制宽窗口仍用自适应布局，不重建第二个 native start。

NativeActivity/Java/Compose 配置变更由一个会话协调器统一处理；实际 Surface 尺寸传给渲染与触屏映射。不能只依赖旧 onBackPressed/keycode 接口实现返回；目标API36按现代返回机制集成。参见 [Android16行为变更](https://developer.android.com/about/versions/16/behavior-changes-16#adaptive-layouts)。

## 5. 系统交互

权限、通知、安装器和文件选择器结果均来自系统回调或恢复时实际读取。使用明确组件且不可修改的 PendingIntent；对任务通知动作验证task所属和revision。拒绝通知不删除任务，不假授权；SAF取消不报导出/导入成功。

反馈表单10–2000字符、可选联系≤254，附加诊断默认未选。成功只有真实 ticket_id；超时保留草稿与同一幂等键。编辑设置和触控布局先改草稿，应用通过校验才持久化；取消不改有效值。

关于页提供随包许可，隐私页说明必要数据、可选诊断、保存位置与卸载影响。工作标识、示例名称和原型回执在发行构建替换为真实配置。

## 6. 无障碍与验收

页面标题进入时获得合适语义；弹层隔离背景并循环焦点，关闭恢复来源。进度播报阶段与可信百分比，不每字节抢读。图标有动作名，装饰图不重复朗读。错误与字段关联且保留输入。

原生验证覆盖360/393/412/600宽、100/130/200%字号、系统insets/IME、TalkBack、键盘/手柄和系统返回。已有浏览器34条断言/56组合只属于设计证据，不替代上述真实系统验收。

## 7. 生命周期

进程重启先打开数据库、执行有限对账，再渲染真实库与任务。重建失败不能调用清库恢复默认；提供可恢复错误和诊断。声音/输入/Surface随当前会话生命周期，页面退出和大厅重建不能触发全应用终止。
