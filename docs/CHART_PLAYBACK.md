# 轨道谱面呈现模块

现有随机TAP测试保留为`/chunithm test`。新增`ChartDefinition`及`ChartPlayback`，加载指定JSON谱面后，在现有轨道内按时间、方块/秒和镜像设置呈现。此模块是可运行的可视播放后端，不是判定/评分系统。

## 测试命令

重新启动最新版测试客户端并进入世界。在空地生成轨道，或靠近已有完整轨道（32格以内）：

```mcfunction
/chunithm track_empty 24
/chunithm chart play 180 10 false
```

第二条命令选择最近的已登记轨道，加载怒槌MASTER，以10格/秒、非镜像开始呈现。它不会生成或替换方块。站位及朝向不需要与最初生成轨道时相同；建议从判定线附近朝头部观看。

```mcfunction
/chunithm chart play 103 10 true
/chunithm chart pause
/chunithm chart resume
/chunithm chart seek 85.3
/chunithm chart status
/chunithm chart stop
```

- `load <ID> [speed] [mirror]`只加载，不开始；可随后使用头部窗口的开始按钮。
- `play <ID> [speed] [mirror]`加载并开始；不带ID的`play`开始/继续已加载谱面。
- speed范围0.25～20格/秒，默认10；mirror默认false。
- `seek`参数为谱面秒数，从歌曲0时刻计算；暂停/停止时定位会进入暂停预览状态，继续时从该位置开始。
- `stop`清空显示并回到预滚起点，保留选中谱面；`/chunithm stop`则卸载整个可视播放会话，同时停止旧随机测试。
- 当前每个维度共享一个呈现会话，管理员命令控制该维度中的会话。

可直接指定数据包资源ID，命令参数需要双引号，例如：

```mcfunction
/chunithm chart play "minecraftchunithm:songs/0180/master.json" 10 false
```

## 已接入的资源

`data/minecraftchunithm/songs/0103/master.json`、`data/minecraftchunithm/songs/0180/master.json`为可加载格式，与各自的`song.json`存放在一起。数字歌曲ID从`songs/<四位ID>/master.json`加载；两首歌曲元数据的MASTER条目也指向这些资源，imported=true；其他难度仍未导入。

JSON根字段：schemaVersion=1、title、songId、difficulty、lastTimeMs、notes、scrollEvents、speedChangeMarkers、beatTimesMs。notes保留原始段，不会因交汇坐标相同而丢弃。每条记录包括id、command、timeMs、lane、width；持续记录另含endTimeMs、endLane、endWidth。SLD/SLC保留root/terminal标记；AIR/AHD保留targetType。

支持已核验的TAP、CHR、FLK、HLD/HXD、SLD/SLC/SXD/SXC、六种方向AIR、AHD、ASC/ASD、ALD及SFL正/零/负倍率区间。未知类型、越界坐标和重叠SFL会明确拒绝，不宣称支持全部C2S版本。自定义JSON需满足同一格式；本模块不直接执行原C2S文本。新增六首的路径与兼容细节见[批量歌曲导入](SONG_IMPORT_BATCH.md)。

转换：

```powershell
python -B tools/export-playable-charts.py 103 180
```

原始C2S需先通过`analyze-c2s.py`分析；导出器保留所有源音符及控制段，另生成四分拍时间。

## 时间、镜像与变速

- 曲目时间0保持原文件零点，自动预滚至少1秒，长轨道按有效行程增加预滚时间。
- 普通音符的局部位置为`judgmentZ+speed*(scrollTime(now)-scrollTime(noteTime))/1000`。
- scrollTime为视觉倍率的积分；0倍率区间停止视觉运动，但谱面时钟仍推进。
- 变速提示线按基础速度走独立路径，且不覆盖为满亮度。它与普通音符在同一歌曲时钟中定位。
- 镜像为`lane'=16-lane-width`；路径的两个端点均镜像，中途插值使用镜像后的坐标。
- 服务端每tick更新显示实体，客户端用1tick的位置/变换插值平滑移动。原始6.25ms路径段仍保留为几何结构，不将源时间量化成50ms。

## 长音符与边界

HOLD用固定宽尾带和首尾条；SLIDE按每条原始路径段绘制蓝色中心渐变尾带及节点；AIR只绘制薄片箭头，复用其已有地面音符；AHD绘制固定0.075格方截面绿线和悬空紫色动作条，并对真正起始动作绘制箭头。

长条先在SFL边界分段，再裁切可见部分。恒宽直线用一个可剪切模型表示，变宽时按每0.25分区宽度差进一步拆分；每片采用保守宽度，确保曲线/变宽时不越出金边。倒退段分别裁切，不能因长条的两个端点都在屏幕外而丢掉中间的可见部分。持续尾部必须离开末端后才清除，经过判定线不会整条消失。

长条使用原生ItemDisplay分段近似，不是自定义逐顶点网格；变宽路径与复杂交汇的视觉细节仍可进一步精修。SLD节点当前按其起始位置和最终端点显示，后续可依据原游戏逐点校准。已有Flick模型仍使用现有对称方向提示。

离开末端时先发布最终边缘位置并保留2tick，让客户端插值完成后再移除；播放停止、轨道损坏/替换、服务器退出时清除会话。旧存档中的呈现实体重新加载时会被清理，防止冻结残留。

## UI与程序接口

模块注册为`HeadControlServices.PlaybackBackend`。使用load命令为头部连接的轨道选中谱面后，原头部窗口可开始、暂停、停止；既有UI仍要求轨道/歌曲面板连接及权限。独立轨道可直接使用命令，不依赖歌曲面板。

Java调用：`ChartDefinition.load(level, resource)`读取数据包资源；`ChartPlayback.load(level, trackPart, chart, speed, mirror)`加载到已登记完整轨道。TrackPart来自PartConnections注册结构。UI按钮负责后续状态控制。

## 验证与当前边界

自动检查九首MASTER、所有源记录数量、全节点镜像、3/10/20格每秒采样，以及每段SFL的边界/中点，共1780个画面采样；包含倒退段中途入屏的回归检查、轨道横向/纵向裁切、0倍率停止与恢复线。另有现有边界/插值测试、资源模型检查和JAR引用检查。

本模块不自动播放音频，不实现输入判定、得分、歌曲选择界面或面板自动填充。音频资源仍已打包，后续需要具有暂停/定位能力的音频后端与此谱面时钟对接。当前用游戏时钟驱动，服务器卡顿时呈现也会减慢；原生客户端插值不等于已经完成音画同步。

同时显示的几何片上限4096，超限停止会话并记录错误，避免无界生成实体。验证为离线几何及构建检查，本轮未自动启动客户端或修改测试世界。
