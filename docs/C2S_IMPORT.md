# ID 103 MASTER：C2S 定义与 MOD 转换方案

分析对象：`E:\SDHD_2.50.00_20260518110527_0\data\A000\music\music0103\0103_03.c2s`。

本文以本地文件内容、尾部统计和现有 Java `NoteDefinition` 为依据。社区实现只作交叉参考；这不是 SEGA 官方格式规范。已生成结构分析 JSON，尚未把歌曲接入游戏播放。

## 1. 文本结构

文件是可直接读取的 UTF-8/ASCII 文本，字段由 TAB 分隔，行以 CRLF 结束。不要按固定字符宽度截取字段，也不要把整个文件按任意空白切词，否则可能拆坏作者、标题等字符串。数字字段可单独 trim。

内容分成：头部元信息、BPM/拍号事件、音符/路径段、`T_` 开头的统计。解析时先保留命令、字段和源行号，第二遍建立连接。不能只靠相邻行建立长音符，因为多条音符会按时间交错写入。

| 头部 | 本文件值 | 导入处理 |
| --- | --- | --- |
| VERSION | 1.08.00 / 1.08.00 | 保留版本，按版本选择解析规则 |
| MUSIC | 103 | 歌曲 ID |
| DIFFICULT | 03 | 本文件为 MASTER；对应 Music.xml 的 Master 条目 |
| LEVEL | 0.0 | 不能作为定级；Music.xml 的 Master level=15、levelDecimal=0 |
| CREATOR | Revo@LC | 谱师；不同于 Music.xml 中的曲作者 |
| BPM_DEF | 四个 257.000 | 四个值的通用含义未核实，完整保留；实际时序使用 BPM 事件 |
| MET_DEF | 4 / 4 | 默认拍号 |
| RESOLUTION | 384 | 本文件每个 4/4 小节的时间刻度数 |
| CLK_DEF | 384 | 与本文件分辨率相同；其他值的关系待独立样本核实 |
| PROGJUDGE_BPM / AER | 240 / 0.999 | 原样保留，不据此替代歌曲 BPM 或推导判定窗口 |
| SEQUENCEID / TUTORIAL | 0 / 0 | 保留，不参与当前音符坐标转换 |

## 2. 时间定义：到达判定线的时间

本文件只有 `BPM 0 0 257.000` 与 `MET 0 0 4 4`，没有变速、变拍。

音符的公共格式：

```text
类型  小节号  小节内刻度  左边界分区  宽度分区  [类型专用字段...]
```

小节号从 0 开始。令 m 为小节号、r 为小节内刻度：

```text
absoluteTick = m × 384 + r
ticksPerQuarter = 384 / 4 = 96
timeMs = absoluteTick / 96 × 60000 / 257
```

`timeMs` 对应现有 `NoteDefinition.timeMs`，表示到达判定线的时刻，不是生成时刻。不要把绝对刻度直接当毫秒、Minecraft tick 或四分音符数量。

独立核验：第一条音符 tick=768，换算 1867.704280ms；文件 `T_FIRST_MSEC=1867`。最后位置 tick=68640，换算 166926.070039ms；`T_FINAL_MSEC=166926`。原文件统计只保留整数毫秒。

有 BPM 变化时，按每个 BPM 生效刻度分段积分；持续时间应算 `time(endTick)-time(startTick)`，不能只用起点 BPM。保留整数刻度或有理数计算，最后才转 double 毫秒，避免逐段取整累积漂移。

以上小节换算在本文件4/4情况下验证通过。后续ID180 MASTER样本补充验证了MET变化：C2S坐标格仍固定为384刻度；MET按“分母、分子”记录，只改变小节线显示，不重新缩放绝对刻度或音符时刻。更新后的分析脚本已支持这些独立显示事件，详见 `docs/C2S_0180_MASTER.md`。

## 3. 横向坐标

`lane` 是音符左边缘，`width` 是占用分区数量，均使用 16 分区。合法范围：`lane >= 0`、`width > 0`、`lane+width <= 16`。这与现有 Java 属性一致，无需把数值先除以二再传入 `NoteDefinition`。

模型放置时才转换成方块：

```text
leftBlocks = lane × 0.5
widthBlocks = width × 0.5
centerAcross = 0.5 + lane × 0.5 + width × 0.25
```

`centerAcross` 使用现有轨道 Placement 的局部横坐标；0.5 是左金边后的轨道表面边界。举例 `TAP 2 0 8 4`：第 2 小节起点、timeMs=1867.704280、lane=8、width=4，实际宽 2 格，局部中心 x=5.5。

镜像时用 `lane'=16-lane-width`；每个路径节点也分别镜像，并交换左右方向。不能用 `16-lane`。

## 4. 本文件的八种音符/段命令

| 命令 | 公共四字段后的内容 | MOD 中的含义 |
| --- | --- | --- |
| TAP | 无 | TAP 瞬时地面音符 |
| CHR | `UP` | EX_TAP；本样本全部是 UP，不能误当成 AIR 上抬 |
| FLK | `L` | FLICK；保留原方向标签，本样本只有 L，实际滑动判定方向仍需对照实机核实 |
| HLD | `durationTicks` | HOLD，位置/宽度固定，具有首尾节点 |
| SLD | `durationTicks endLane endWidth` | 带显式 Slide 节点语义的路径段 |
| SLC | 同 SLD | Slide 形状控制段；不能额外算作独立 Tap |
| AIR | `targetType` | 上抬 AIR，关联同时间/位置的某种地面对象或长音符终点 |
| AHD | `targetType durationTicks` | 空中持续/动作段，投射到 MOD 的 AIR_ACTION；targetType=AHD 表示续接 |

本地记录数量：TAP 1244、CHR 260、FLK 55、HLD 44、SLD 67、SLC 212、AIR 270、AHD 44；共 2196 条（含 212 条形状控制记录）。

SLD/SLC 的可见/控制区别也存在于 [C2SViewer 的音符结构](https://github.com/Aeph1edur/C2SViewer/blob/main/src/notes/slide.py)。转换时应单独保留节点类型，不把“控制段”直接解释成“抛物线”。可见条究竟画在每段哪个端点，需要在动态渲染实现时与原始画面逐点对照；本轮结构分析不冒充已经完成该视觉验证。

### HOLD 的实例

```text
HLD 8 240 10 4 96
AIR 8 336 10 4 HLD
```

HOLD 从 tick3312 到 tick3408，即 8054.474708ms 至 8287.937743ms；durationMs=233.463035，lane=10、width=4，两个节点高度均为0。紧随的 AIR 在 HOLD 结束时刻，应该绑定 HOLD 的 END，不是 START。

### Slide 的实例与连接

```text
SLC 30   0 0 4 48 4 4
SLC 30  48 4 4 24 5 4
SLC 30  72 5 4 48 6 4
SLD 30 120 6 4 72 6 4
AIR 30 192 6 4 SLD
```

四个段的节点序列为 `(30:0,0,4) -> (30:48,4,4) -> (30:72,5,4) -> (30:120,6,4) -> (30:192,6,4)`。整段起点时间 28015.564202ms，终点 28482.490272ms；最后 AIR 绑定 Slide 的末端。

连接候选条件为前段 `endTick/endLane/endWidth` 等于后段 `tick/lane/width`。本文件279个段中检测到41个无前驱的起始段，与 `T_NOTE_SLD=41` 一致；不能把67条 SLD 理解为67条互相独立的完整 Slide。

第95～99小节附近有多条段在同一位置汇合、分离或交叉，48条段的前驱或后继存在多解。因此仅按坐标取第一个候选会错连。中间格式应保留每条段的独立 ID、原始顺序和所有连接候选，支持图结构或在验证后明确分链；不能因为相同时间/坐标而去重。41个起始段不意味着已经无歧义恢复了41条完整链。

本样本的段没有上/下抛物线字段，应先按每个原始段 LINEAR 重放密集节点。不要根据 SLC 名称自动用 PARABOLA_UP/DOWN，也不要平滑掉原始节点。MOD 自制谱仍可单独支持这些曲线。

### AIR / AHD 的关联

`targetType` 是被绑定对象的类型标签，不是字符串 ID。需要用 `(类型,绝对刻度,lane,width)` 建立索引，并包含 HLD/SLD/AHD 的结束锚点。多候选时保留并报告，不能随意取一条。类似做法可参照 [C2SViewer 的关联实现](https://github.com/Aeph1edur/C2SViewer/blob/main/src/core/read.py)，但本地结果需独立校验。

本文件270条 AIR 与44条 AHD 均找到位置匹配；44条 AHD 的关联标签分别为 TAP19、CHR8、HLD8、SLD1、AHD8。后8条连接先前 AHD 终点，因而36个空中持续结构的起始动作，与 `T_NOTE_AHD=36` 对上。

AHD 自带开始/续接语义，不要求另有一条 AIR 行。导入时可为真正的36个起始结构生成逻辑 AIR 锚点，以满足当前 `AIR_ACTION.parentId`；8条续接不能重复生成新的上抬。紫色动作条的事件需单独保留，并在续接位置合并重复绘制。

AIR 是覆盖在所属对象上的动作标记。当前静态 Air 模型自带 EX-Tap 底座；导入 TAP、HOLD、SLIDE 上的 AIR 时应分开绘制箭头，保留原地面对象，避免多画黄色底座或错误改色。

AHD 没有高度字段，空中高度应作为 MOD 的渲染参数；不能从 duration 或 width 猜测高度。逻辑动作区域宽度和固定绿线截面是两个概念。本文件所有节点满足现有最小动作条宽度；以后遇到 width=1 要保留逻辑宽度，单独处理显示宽度限制。

## 5. 当前 Java 定义能直接使用的部分与缺口

现有 `NoteDefinition` 已提供 kind/timeMs/lane/width/durationMs/direction/parentId/nodes。简单 TAP、CHR、FLK、HOLD 可以直接映射。Node.offsetMs 是相对该条完整路径起点的毫秒，而不是绝对谱面毫秒；所有地面节点 height=0。

建议在正式导入前增加以下信息，名称是提案，不是已经存在的 API：

| 补充信息 | 原因 |
| --- | --- |
| ChartDefinition：metadata、tempoMap、meterMap、audioOffsetMs、audio、notes/paths | 当前只有单个音符 record，没有完整歌曲级谱面定义或加载器 |
| sourceLine/sourceType/sourceFields/sourceOrder | 保留原始含义、错误定位、支持后续格式扩展 |
| 节点/边独立 ID 与 predecessor/successor 候选 | 处理交汇重叠，避免强制丢失为单一节点数组 |
| NodeRole 或 visible/judged 标记 | 区分控制节点、显式 Slide 节点、首尾端点和动作条 |
| parentAnchor：parentId + part + offsetMs | AIR 可绑定 START、END 或中途节点；目前仅 parentId 不充分 |
| HeadStyle 与 arrow-only 渲染 | 防止长音符起点或 Air 底座重复生成 |
| sourceStatistics / judgementEvents | T_NOTE/T_NUM/T_JUDGE 不是同一数量，评分事件应独立处理 |

可以把无歧义路径投射为现有 `List<Node>`；但这应是渲染层适配，原始边集合仍要保留。当前 TrackMotion 仅是随机 TAP 运动样例，不是文件谱面播放后端。

## 6. 播放位置与音频同步

建议保留文件时间零点，音频与谱面共用同一播放时钟，允许独立可调 audioOffsetMs；不要擅自移除开头两小节。第一音符约在音乐开始后1.868秒，音频共170.901333秒，谱面最后位置166.926070秒，剩余约3.975秒是尾音空间。

使用现有从起点向末端运动的局部 Z 方向时：

```text
z(nowMs) = judgmentZ + speedBlocksPerSecond × (nowMs - hitTimeMs) / 1000
judgmentZ = trackLength - 0.5
```

瞬时音符只有在进入可视轨道后才显示，过判定线继续移动到 trackLength 才移除。长音符应对每段做轨道边界裁切，并等待尾节点越过末端后销毁，不能在头节点命中时整条删除。

预滚时长应覆盖 `(judgmentZ - spawnZ)/speed`，让音乐0时刻前应该出现的音符也有完整入场距离。服务端tick仍可处理输入/同步，客户端位置应按连续播放时钟插值，不能把所有音符时间四舍五入到50ms。

## 7. 统计与独立校验

| 检查 | 本文件结果 |
| --- | --- |
| 所有源音符/段起终点横向边界 | 通过，均在0～16分区 |
| 首个/最后绝对刻度 | 768 / 68640，与源文件一致 |
| 首个/最后毫秒数 | 与T_FIRST_MSEC、T_FINAL_MSEC相差不足1ms |
| Slide无前驱起始段 | 41，与T_NOTE_SLD一致；存在多解连接 |
| Air-Action起始结构 | 44段减8续接=36，与T_NOTE_AHD一致 |
| AIR/AHD关联位置 | 314条均找到候选位置；不代表已消除所有身份歧义 |
| 逻辑起始音符数 | 1244+260+55+44+41+270+36=1950，与T_NOTE_ALL一致 |
| 非SLC记录数 | 1984，与T_REC_ALL一致 |
| 判定事件总数 | T_JUDGE_ALL=2277；不能用1950替代评分计数 |

特别注意：T_JUDGE_ALL包含持续结构的判定事件。当前没有推导其所有tick生成规则，不应先猜分配方法再宣称完全还原。T_PROG_*也原样保留，不把它们当BPM或速度事件。

## 8. 本轮产物与边界

结构分析脚本：`tools/analyze-c2s.py`。输出：`build/chart-analysis/0103_03.analysis.json`，包含每行原字段、绝对刻度、毫秒、段起终点、前后连接候选和AIR关联候选。

```powershell
python tools/analyze-c2s.py E:\SDHD_2.50.00_20260518110527_0\data\A000\music\music0103\0103_03.c2s --output build/chart-analysis/0103_03.analysis.json
```

脚本支持两份样本中的八种音符命令，以及独立的BPM/MET/SFL事件；时间坐标仍限制在已验证的384刻度布局，不是可播放的MOD谱面加载器。未知命令以原字段和行号保留在unclassifiedRecords。尚待：正式谱面结构、交汇分链和可见节点规则的视觉验证、评分事件重建、连续时钟播放器。

其他谱面可能出现 AUL/AUR/ADW/ADL/ADR、HXD/SXD/SXC、AHX、ASD/ASC/ALD、MNE 等。当前文件没有这些实例；未知命令必须保留并报告，不能静默丢弃，也不能仅凭本样本假定已经支持全部C2S版本。
