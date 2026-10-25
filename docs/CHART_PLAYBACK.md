# 轨道谱面呈现模块

现有随机TAP测试保留为`/chunithm test`。新增`ChartDefinition`及`ChartPlayback`，加载指定JSON谱面后，在现有轨道内按时间、方块/秒和镜像设置呈现。此模块是可运行的可视播放后端，不是判定/评分系统。

Hold/Slide 的条带主体以原定义宽度的 95% 居中显示，两侧各内收 2.5%；Slide 的可见中间节点同样横向缩放至 95%。Hold/Slide 的头部和尾部保持原来的满格宽度。源谱面的 lane、width、路径中心线、转折位置、到达时间与镜像计算保持原定义，缩窄只作用于显示。

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
- speed范围0.25～30格/秒，默认20；mirror默认false。已有机台保存的自定义设置继续保留。
- `seek`参数为谱面秒数，从歌曲0时刻计算；暂停/停止时定位会进入暂停预览状态，继续时从该位置开始。
- `stop`清空显示并回到预滚起点，保留选中谱面；`/chunithm stop`则卸载整个可视播放会话，同时停止旧随机测试。
- 当前每个维度共享一个呈现会话，管理员命令控制该维度中的会话。

可直接指定数据包资源ID，命令参数需要双引号，例如：

```mcfunction
/chunithm chart play "minecraftchunithm:songs/0180/master.json" 10 false
```

## 已接入的资源

谱面与各自的 `song.json` 存放在 `data/minecraftchunithm/songs/<四位ID>/`。数字歌曲 ID 默认加载 `master.json`。目前曲库共十一首，Theatore Creatore（2712）、YOUNITHM（2967）均拥有 MASTER 与 ULTIMA；ULTIMA 使用各自文件夹内的 `ultima.json`，可由歌曲面板直接选择。图案和演出限制见 [ULTIMA](ULTIMA.md)，新曲转换见 [Theatore Creatore](THEATORE_CREATORE.md)。

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
- `judgmentZ = 轨道长度 - 0.5`，与金色判定线中心完全一致。在 `now == noteTime` 时音符中心到达此线；越线后继续滑出轨道再移除。到达时间与消失时间分别处理，轨道末端不是音符的目标时间点。
- scrollTime为视觉倍率的积分；0倍率区间停止视觉运动，但谱面时钟仍推进。
- 变速提示线按基础速度走独立路径，且不覆盖为满亮度。它与普通音符在同一歌曲时钟中定位。
- 镜像为`lane'=16-lane-width`；路径的两个端点均镜像，中途插值使用镜像后的坐标。
- 服务端每tick更新显示实体，客户端用1tick的位置/变换插值平滑移动。原始6.25ms路径段仍保留为几何结构，不将源时间量化成50ms。

## 长音符与边界

HOLD用固定宽尾带和首尾条；SLIDE按每条原始路径段绘制蓝色中心渐变尾带及节点；AIR只绘制薄片箭头，复用其已有地面音符；AHD/ASC/ASD 绘制位于轨道表面上方 1 格的半透明绿色荧光连接线和悬空紫色动作条，并对真正起始动作绘制加长箭头。材质采用提供的 ntt_ahline 贴图，细节见 `AIR_ACTION.md`。

长条先在SFL边界分段，再裁切可见部分。恒宽直线用一个可剪切模型表示，变宽时按每0.25分区宽度差进一步拆分；每片采用保守宽度，确保曲线/变宽时不越出金边。倒退段分别裁切，不能因长条的两个端点都在屏幕外而丢掉中间的可见部分。持续尾部必须离开末端后才清除，经过判定线不会整条消失。

长条使用原生ItemDisplay分段近似，不是自定义逐顶点网格；变宽路径与复杂交汇的视觉细节仍可进一步精修。SLD节点当前按其起始位置和最终端点显示，后续可依据原游戏逐点校准。已有Flick模型仍使用现有对称方向提示。

离开末端时先发布最终边缘位置并保留2tick，让客户端插值完成后再移除；播放停止、轨道损坏/替换、服务器退出时清除会话。旧存档中的呈现实体重新加载时会被清理，防止冻结残留。

## UI与程序接口

模块注册为`HeadControlServices.PlaybackBackend`。使用load命令为头部连接的轨道选中谱面后，原头部窗口可开始、暂停、停止；既有UI仍要求轨道/歌曲面板连接及权限。独立轨道可直接使用命令，不依赖歌曲面板。

Java调用：`ChartDefinition.load(level, resource)`读取数据包资源；`ChartPlayback.load(level, trackPart, chart, speed, mirror)`加载到已登记完整轨道。TrackPart来自PartConnections注册结构。UI按钮负责后续状态控制。

## 验证与当前边界

自动检查九首MASTER、所有源记录数量、全节点镜像、3/10/20格每秒采样，以及每段SFL的边界/中点，共1780个画面采样；包含倒退段中途入屏的回归检查、轨道横向/纵向裁切、0倍率停止与恢复线。另有现有边界/插值测试、资源模型检查和JAR引用检查。

已导入歌曲的声音现已通过轨道两侧边框播放，支持开始、暂停、恢复、停止与定位；详见 `RAIL_AUDIO.md`。当前不实现输入判定或得分。服务器游戏时钟与客户端音频时钟通过快照校准，服务器卡顿、网络或设备延迟仍可能影响同步。

同时显示的几何片上限4096，超限停止会话并记录错误，避免无界生成实体。声音功能另在独立测试存档中验证，正式世界未改动。
