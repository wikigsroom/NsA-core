# 04 · 传输、安装、空间、更新与存档事务

## 1. 执行器与持久化

TransferEngine只负责HTTP、流式写入、段校验与检查点；Android适配器负责何时可执行。API34+用户可见发起的大传输使用UIDT，API31–33采用合法前台传输，均需可见状态和持久恢复。系统停止不保证回调，不能只在onStop中保存。参见 [UIDT](https://developer.android.com/develop/background-work/background-tasks/uidt)、[前台服务时限](https://developer.android.com/develop/background-work/services/fgs/timeout)。

全局单任务、单连接、8MiB校验段；至少每完成一段同步文件后提交可信记录。每游戏一个活动内容任务；完成传输后的安装为独立记录。user_intent与blockers分开：user/network/metered_policy/storage/system/game_running/authorization可共存，只在全部解除且用户仍要运行时继续。

## 2. 传输协议

签名清单绑定object_id、总字节、SHA-256、文件/分段索引。下载URL不写进清单；API续签只能指向同一对象。请求Accept-Encoding: identity，禁透明解压影响Range；强ETag为不透明版本标识，不当SHA用。

| 响应/事件 | 必须处理 |
| --- | --- |
| 206 | 核对起止、总长、identity、对象与ETag；分段完成验证摘要 |
| 非零offset的200 | 不追加；重新确认同对象，受控全量恢复需清理未引用临时文件 |
| 416 | 核对本地长度与整体摘要，不能直接当完成 |
| 401/403 | 区分安装凭证、资格与URL到期；仅URL到期续签同对象 |
| 404/410 | 刷新版本策略；不切到另一个release拼接 |
| 429/短暂5xx/超时 | 有界抖动退避，连续自动最多5次；上限后等用户 |
| 段/整包摘要失败 | 标记不可信并分类；只允许有限受影响段重取，不无限耗流量 |
| 取消/系统结束 | 先记录意图；关闭写句柄并校验可信边界；重建时对账 |

段提交顺序：写完整段→同步数据→验证摘要→短DB事务记录offset/length/hash/verified→更新UI/通知。收到字节不是已验证字节。恢复重验已提交段与文件实际长度，截除不可信尾部；文件已同步但DB未提交可重验收养，不能盲目继续。

连接默认20秒、读取30秒、120秒无进度进入可恢复失败；退避2秒起、60秒封顶，Retry-After上限1小时且可取消。暂停不消耗自动重试预算。重试由引擎和调度器共用一个预算，防止嵌套重试翻倍。

## 3. 空间预算

应用内大内容默认同一私有卷；/data与/sdcard可能是同卷视图，不相加。字节在本地使用checked Long，API用十进制字符串；界面GiB/MiB，速度MiB/s。

```text
reserve = max(2GiB, ceil(unpacked_bytes * 0.05))
additional_peak = unallocated_download + unwritten_install + remaining_work + reserve
available_for_this = platform_free - other_unallocated_app_reservations
missing = max(0, additional_peak - available_for_this)
```

已物理分配的字节已反映在free，不再次扣；旧有效版也不再次相加。稀疏逻辑长度不能算物理预留，预分配必须验证。应用预留只能减少本应用冲突，不能阻止其他App占空间。下载确认、每段、解包前、提交前与写失败重算。

定稿样例：首次10+15+1+2=28GiB；64%已有6.4GiB，额外3.6+15+1+2=21.6GiB。样例可用42.4/36GiB是两种情境，不是手机实测；生产使用签名包与真实快照。

## 4. 解包与信任

先验签原始payload，再schema/依赖→索引摘要→段/整包→逐文件→READY。清单≤2MiB；文件索引≤64MiB、分段索引≤16MiB、文件≤250000、路径≤1024UTF8字节/32层。超过限制需新协议或受审配置，不自动扩大。

ZIP64只允许受支持STORE/DEFLATE普通文件与目录；拒绝绝对/盘符/UNC/上级/空或点段/反斜线/控制字符路径、符号/硬链接、设备文件、重复/归一化冲突、未声明文件。制作端还拒绝Windows无法稳定表示的路径/大小写冲突；客户端按索引相对路径原样验证归属。

用openat/无跟随或等效机制避免检查后路径替换；父目录由本操作新建且归属检查。流式计实际展开量，每文件及总量不超过签名声明；文件索引长度和总和必须一致。归档头、CRC、mtime不代替SHA校验。失败只处理本安装staging，不改旧版和保存目录。

## 5. 安装提交与对账

| 日志阶段 | 已完成事实 | 中断恢复 |
| --- | --- | --- |
| CREATED | 建专属staging、锁及operation | 继续或受控取消 |
| EXTRACTED | 全部声明文件已展开 | 继续逐文件验证 |
| VERIFIED | 文件和依赖已验、READY及目录落盘 | 允许发布目录，不先显示可玩 |
| DIRECTORY_PUBLISHED | 同卷rename到不可变package | 校验目录/日志，完成DB登记或保留待恢复 |
| DB_ACTIVATED | 短事务切active，保留旧引用 | 允许启动，清理延后 |
| CLEANED | 无引用下载/临时已清 | 幂等结束 |

rename与Room不是跨系统原子事务。每一边界故障注入；冷启动根据日志、READY、清单摘要、真实目录和active pointer对账。孤立完整候选不能自动盖过有效旧版；缺失/不一致记录为repair_required或恢复中。文件/父目录同步失败必须上报，不能用“已调用rename”宣称耐断电。

提交最短临界区不任意取消。用户取消持久记录，待临界区结束按记录决定回退/完成；不在DB激活前删资源，也不留下可启动半包。清理引用：active、pending、session、rollback、唯一备份分别检查。

## 6. 更新与健康

下载完整新版到新release目录，运行钉住旧版。激活前取得同游戏修改锁，活动会话未结束则等待。新版本健康至少有进入真实可操作场景、无阻断运行错误和存档兼容记录；首帧只表示呈现，不自动释放回退资源。

回退同时要求旧内容、内置模块/驱动和兼容存档/备份；条件不足保留数据并提示选择，不能静默降级新版存档。首版完整包更新/修复，不宣称二进制差分。普通下架不删安装；阻断按可信策略限制下一次启动。

## 7. 存档事务

提交真相是native保存事务/磁盘标记。保存身份player+game+compatibility_group，commit_seq、来源release、摘要、大小与提交时间有记录。不要从Activity.finish或退出按钮推导保存成功。

导出：取得一致已提交快照→复制/校验到导出临时→SAF选择位置→流式写并确认成功。若提交在快照期间变化，重试或使用运行器的明确快照握手；不能读save.work。目标取消/不可写/空间不足不删除原档。参见 [SAF官方文档](https://developer.android.com/guide/topics/providers/document-provider)。

导入：选择→解析save_schema→校验游戏/组/完整性/路径/大小→取得同游戏与save独占锁→备份原有效档→staging→校验同步→记录交换日志→原子目录交换/登记→保留旧备份。上限默认2GiB归档，逐文件累计及展开总量同时受限；不得因压缩率突破上限。游戏或保存事务运行时禁覆盖。

交换阶段保存old/new路径归属与commit；每边界中断重启都能取回原档或已验证新档。恢复前不清唯一旧副本。导入成功不自动启动游戏。重置设置/删除存档需要各自确认，不由修复或资源删除隐式触发。

## 8. 缓存与清理

cache key=release+runtime/profile+shader+driver+GPU+OS；变更只失效对应缓存。clear-cache不动save/settings/package。GC仅操作归属目录且无有效引用；删除失败记录待清理及真实残留字节。私有backup不抵抗卸载，用户持久备份走SAF；App数据不把十几GiB资源混入系统自动备份。
