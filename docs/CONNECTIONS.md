# 部件接口与背面窗口

## 生成与摆放

重启最新版客户端后，可在空地执行：

```mcfunction
/chunithm assemble 24
```

生成与参考图相同的整机：中央头部、向正面延伸的空轨道、轨道末端 Ground Slide、左侧玩家面板、右侧歌曲面板。长度最少 2 格，默认 24；生成前检查整个范围的边界和区块加载状态，会替换范围内方块。使用与 `/chunithm head` 相同的中央定位规则。原有独立生成命令仍可使用。

头部局部坐标：宽 11、高 4、厚 1，X 从正面看到的左方向右增加，Z 朝正面增加。

| 部件 | 相对头部左下角的起点方块 | 正面朝向 |
| --- | --- | --- |
| 轨道 | (1, 0, 1) | 与头部相同，向正面延伸 |
| 玩家面板 | (-7, 1, 0) | 与头部相同 |
| 歌曲面板 | (11, 1, 0) | 与头部相同 |

Ground Slide 以轨道起点为基准，从轨道长度对应的下一格开始，方向与轨道一致。它占 13×3 格，左右边框比九格宽轨道各向外伸出两格，中央 8×2 格发光面与轨道有效区对齐，且不会覆盖轨道末端判定线。右侧空位中央为纵向装饰投币口。完整结构存在时，头部控制界面的 Ground Slide 模块自动显示为已连接，并通过默认输入提供器传输实际 16 路压感。

左右 Air 感应器的 5×3×1 底座紧贴 Ground Slide 两侧，3×3×1 立板位于底座最外排，含底座总高 4 格。黑色内侧与底座顶面带 1/6 格白边和磨制石材感，底座有三枚向外箭头。五个左绿右红的常亮窗口位于站立面上方 0.5～2.5 格，各自提供一个实体高度感应通道；详见 `AIR_SENSORS.md`。

`PartConnections.inputRoute` 统一检查 `Air → Ground Slide → Track → CHUNITHM` 的数据管线。所有位置使用局部坐标转换，支持四个水平朝向，不支持跨间隙接线。头部/轨道/Ground Slide 任一损坏或未加载会阻断两侧 Air；单侧 Air 损坏只阻断该侧，不影响另一侧和 Ground Slide。连接与信号通过原生头部菜单同步，设备无输入时保留已连接样式，断路立即清零，恢复后自动重新连接。原有正确登记的结构无需重新生成。

头部底座三个接口位于 X=1.5、5.5、9.5，轨道起点三个接口位于 X=0.5、4.5、8.5。接口为黑色座加青色插口；头部侧面中部与两侧面板的对应侧面均有插口。接口直接接触连接，目前不支持跨间隙连线、电缆或转接。

旧版本生成的部件没有注册记录和接口，需要使用新命令重新生成一次。自行复制方块不会复制部件注册信息。重叠生成会取消旧部件注册，不再把残缺旧结构识别为完整设备。

## 背面窗口

到部件**背面**，右键中间的入口窗口。头部为高一格、宽 5/3 格的灰框、黑底与 CHUNITHM Logo，边框齐平于头部背面，按钮嵌入 1/8 格；横跨三列的可见窗口都可点击。旧注册头部加载后自动刷新入口，无需重建。玩家板与歌曲板也改为一格高的内嵌入口，分别显示 ALL.Net 与 CHUNITHM 图案；详见 `PANEL_TERMINALS.md`。头部和歌曲面板使用原生控制/选曲界面；玩家面板保留原版容器窗口，图标不能取走或放入物品。界面关闭后保留设置。

- 头部：使用新版原生控制界面，悬停 CHUNITHM 查看轨道左、中、右接口及两侧面板状态，点击刷新。检测包含所有结构方块及其朝向；轨道完整性检查覆盖全长。状态为已连接、未连接/未对齐、结构损坏、区块未加载。详见 `HEAD_UI.md`。
- 玩家面板：列出可导入的信息，点击一个条目更新面板。默认仅提供当前 Minecraft 玩家的名字，其他数值为占位；完整玩家资料由提供器接入。支持翻页。
- 歌曲面板：原生三曲配置窗口，SELECT打开歌曲与难度目录。点击可用难度写入对应TRACK槽位；未导入难度置灰。头部正在播放时禁止换曲，暂停/停止后可重选。选歌保留速度、镜像，这两个设置在头部Music窗口控制。详见`SONG_SELECTOR_UI.md`。

背面窗口需结构完整才可打开；远离、跨维度或结构损坏会关闭。玩家、歌曲设置修改需要与生成命令相同的管理员权限，连接状态可查看。不要在旁观模式下测试方块右键交互；切换创造模式后操作。

歌曲面板输入是头部Music三曲队列的数据来源；未输入位置留空。已导入的歌曲/难度可连接现有可视谱面播放模块，速度/镜像同时更新面板与当前谱面；自定义元数据或未导入难度仍可显示，但不会因此变成可播放谱面。暂不接入音频同步或评分判定，也不修改随机TAP测试。详见`MUSIC_QUEUE_UI.md`。

## 后续接入接口

```java
PanelServices.setPlayerInfoProvider(player -> List.of(profileA, profileB));
PanelServices.setSongProvider(player -> List.of(masterChart, expertChart));
```

类型分别为 `NameDisplayData`、`SongDisplayData`。提供器在服务器线程调用，返回已准备好的列表，不能阻塞进行联网或文件选择；异常会记录日志并显示空列表。无需 GUI 提供器时，原有 `NamePanel.update`、`SongPanel.update` 和 JSON 命令仍可使用，且会同步保存设置。

结构与状态查询：

```java
var head = PartConnections.find(level, PartConnections.Kind.HEAD, headPlacement);
var links = PartConnections.links(level, head);
var song = PartConnections.find(level, PartConnections.Kind.SONG,
        PartConnections.target(headPlacement, PartConnections.Kind.SONG));
SongDisplayData playbackSettings = song.song;
```

调用前处理 `find` 返回的 null；连接状态按当前位置、朝向和方块完整性实时计算，不依赖过期的“已连接”缓存。不强制加载区块。部件与玩家/歌曲设置保存在该世界的 `data/minecraftchunithm/parts_<dimension>.json`，与存档一起保留；保存失败会报告异常，避免静默丢失。显示实体也由正常世界存档保存。

## 开发测试

`powershell -NoProfile -ExecutionPolicy Bypass -File .\dev.ps1 runClient -PconnectionQuickPlay` 自动进入 New World，在 X=116～140、Y=-61～-58 附近生成测试整机，检查五个头部接口及轨道末端 Ground Slide 全部连接，并将玩家移到头部背面、打开状态窗口。测试路径会加载其小范围区块，并设置创造模式与白天；普通启动不会执行。

该显式开发测试还会通过游戏自身的截图接口保存 `run/screenshots/head-control-ui.png`，随后关闭菜单并保存背面 Logo 截图 `head-control-logo.png`，便于确认实际加载的是最新版界面和资源。普通启动不会自动截图或关闭菜单。

已通过构建、四个水平朝向的接口对齐检查，以及客户端中的整机生成、五接口状态、头部菜单打开及注册文件写入检查。玩家导入和歌曲设置按钮尚未完成手动逐项视觉验收。
