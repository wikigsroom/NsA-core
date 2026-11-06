# 09 · 页面图册与图像使用

本轮以方案 1（D · 曜黑极简）展开。先看 [完整图册](gallery.html)，再用 [原型](prototype/index.html) 检查按钮、全部状态和纵向滚动内容。

## 1. 三种交付的用途

| 交付 | 内容 | 使用方式 |
| --- | --- | --- |
| 选定参考 | 用户选择的 D 首页 | 视觉方向与主卡/其他游戏布局依据 |
| 规范页面图 | 32 张，由同一原型导出 | 组件、层级与页面默认状态；长页仅是参考视口，完整内容见原型和逐页规格 |
| 关键视觉扩展 | 8 张实际生成的竖屏单页图 | 观察美术气质、封面与页面密度；精确文字、布局、图标与操作条件以规范为准 |

## 2. 关键视觉扩展

| 页面 | 单张图片 | 提示词 | 实际尺寸 |
| --- | --- | --- | --- |
| S01 发现游戏 | [查看单图](visuals/S01-discover.png) | [查看](prompts/S01-discover-v2.txt) | 916 × 1717 |
| S03 游戏详情 | [查看单图](visuals/S03-detail.png) | [查看](prompts/S03-detail.txt) | 916 × 1717 |
| S05 下载中心 | [查看单图](visuals/S05-downloads.png) | [查看](prompts/S05-downloads.txt) | 916 × 1717 |
| S13 管理游戏 | [查看单图](visuals/S13-manage.png) | [查看](prompts/S13-manage.txt) | 916 × 1717 |
| S14 存储管理 | [查看单图](visuals/S14-storage.png) | [查看](prompts/S14-storage.txt) | 916 × 1717 |
| S15 存档与备份 | [查看单图](visuals/S15-saves.png) | [查看](prompts/S15-saves.txt) | 916 × 1717 |
| S18 设置 | [查看单图](visuals/S18-settings.png) | [查看](prompts/S18-settings.txt) | 916 × 1717 |
| S12 运行中断与恢复 | [查看单图](visuals/S12-run-error.png) | [查看](prompts/S12-run-error.txt) | 916 × 1717 |

请求均为 1080×1920，实际服务输出以此表的图片尺寸为准；未放大、拼接或伪装为请求分辨率。使用此前已获用户授权的 gpt-image-2.5-flare 和原 sub2-image-gen 脚本。模型、字节、SHA-256、重试与取舍见 [生成记录](data/generation-record.json)。

发现页初稿出现与工作标识不符的图形，已剔除并按纯文字 NsA 重做；其余合格单图逐张复核。原始出图与临时文件保留在忽略目录，仓库只保存已采用版本。

发现与详情视觉采用首次下载情境，下载中心采用 64% 情境；它们展示不同状态，不是同一时刻的连续截图。规范原型可以按情境连续操作。

## 3. 规范页面图

| 页面 | 默认状态图 | 可点击页面 |
| --- | --- | --- |
| S00 首次进入 | [查看](screens/S00-welcome.png) | [打开原型](prototype/index.html?screen=S00&state=default) |
| S01 发现游戏 | [查看](screens/S01-discover.png) | [打开原型](prototype/index.html?screen=S01&state=default) |
| S02 搜索与筛选 | [查看](screens/S02-search.png) | [打开原型](prototype/index.html?screen=S02&state=default) |
| S03 游戏详情 | [查看](screens/S03-detail.png) | [打开原型](prototype/index.html?screen=S03&state=default) |
| S04 下载确认 | [查看](screens/S04-download-confirm.png) | [打开原型](prototype/index.html?screen=S04&state=default) |
| S05 下载中心 | [查看](screens/S05-downloads.png) | [打开原型](prototype/index.html?screen=S05&state=default) |
| S06 校验与安装准备 | [查看](screens/S06-install.png) | [打开原型](prototype/index.html?screen=S06&state=verifying) |
| S07 我的游戏 | [查看](screens/S07-library.png) | [打开原型](prototype/index.html?screen=S07&state=default) |
| S08 启动检查 | [查看](screens/S08-launch.png) | [打开原型](prototype/index.html?screen=S08&state=default) |
| S09 游戏运行边界 | [查看](screens/S09-running.png) | [打开原型](prototype/index.html?screen=S09&state=default) |
| S10 游戏暂停菜单 | [查看](screens/S10-pause.png) | [打开原型](prototype/index.html?screen=S10&state=default) |
| S11 退出游戏确认 | [查看](screens/S11-exit.png) | [打开原型](prototype/index.html?screen=S11&state=default) |
| S12 运行中断与恢复 | [查看](screens/S12-runtime-error.png) | [打开原型](prototype/index.html?screen=S12&state=default) |
| S13 管理游戏 | [查看](screens/S13-manage.png) | [打开原型](prototype/index.html?screen=S13&state=default) |
| S14 存储管理 | [查看](screens/S14-storage.png) | [打开原型](prototype/index.html?screen=S14&state=default) |
| S15 存档与备份 | [查看](screens/S15-saves.png) | [打开原型](prototype/index.html?screen=S15&state=default) |
| S16 导入备份确认 | [查看](screens/S16-save-import.png) | [打开原型](prototype/index.html?screen=S16&state=default) |
| S17 控制与画面 | [查看](screens/S17-game-settings.png) | [打开原型](prototype/index.html?screen=S17&state=default) |
| S18 设置 | [查看](screens/S18-settings.png) | [打开原型](prototype/index.html?screen=S18&state=default) |
| S19 下载偏好 | [查看](screens/S19-download-settings.png) | [打开原型](prototype/index.html?screen=S19&state=default) |
| S20 本机支持 | [查看](screens/S20-device.png) | [打开原型](prototype/index.html?screen=S20&state=default) |
| S21 帮助与反馈 | [查看](screens/S21-feedback.png) | [打开原型](prototype/index.html?screen=S21&state=default) |
| S22 App 更新 | [查看](screens/S22-app-update.png) | [打开原型](prototype/index.html?screen=S22&state=available) |
| S23 关于与许可 | [查看](screens/S23-about.png) | [打开原型](prototype/index.html?screen=S23&state=default) |
| S24 隐私与数据 | [查看](screens/S24-privacy.png) | [打开原型](prototype/index.html?screen=S24&state=default) |
| S25 权限与通知 | [查看](screens/S25-permissions.png) | [打开原型](prototype/index.html?screen=S25&state=default) |
| S26 游戏更新 | [查看](screens/S26-game-update.png) | [打开原型](prototype/index.html?screen=S26&state=available) |
| S27 网络与服务恢复 | [查看](screens/S27-network-error.png) | [打开原型](prototype/index.html?screen=S27&state=offline) |
| S28 空游戏库 | [查看](screens/S28-library-empty.png) | [打开原型](prototype/index.html?screen=S28&state=default) |
| S29 问题已提交 | [查看](screens/S29-feedback-done.png) | [打开原型](prototype/index.html?screen=S29&state=default) |
| S30 删除资源确认 | [查看](screens/S30-delete-resources.png) | [打开原型](prototype/index.html?screen=S30&state=default) |
| S31 检查与修复 | [查看](screens/S31-repair.png) | [打开原型](prototype/index.html?screen=S31&state=default) |

规范图默认手机视口为 393×852，游戏边界为 852×393；单位是浏览器 CSS 像素审阅尺寸，不是 Android 固定窗口。现场字号与安全区仍以系统为准。

## 4. 美术与品牌交接

虚构封面只为设计演示，不能据此声称游戏可下载或本机已支持。正式目录替换为实际发行素材并保留来源。NsA 是工作名称，正式商标、图标和包名在发布前确定。

保留黑灰层级、冰青语义色、真实中文文字、线性图标和稳定封面比例；不把生成单图整张当成可点击界面。图像里未出现在规范中的图标、头像或装饰不自动成为新需求。
