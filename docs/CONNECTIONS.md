# 部件接口与背面窗口

## 生成与摆放

重启最新版客户端后，可在空地执行：

```mcfunction
/chunithm assemble 24
```

生成与参考图相同的整机：中央头部、向正面延伸的空轨道、左侧玩家面板、右侧歌曲面板。长度最少 2 格，默认 24；生成前检查整个范围的边界和区块加载状态，会替换范围内方块。使用与 `/chunithm head` 相同的中央定位规则。原有独立生成命令仍可使用。

头部局部坐标：宽 11、高 4、厚 1，X 从正面看到的左方向右增加，Z 朝正面增加。

| 部件 | 相对头部左下角的起点方块 | 正面朝向 |
| --- | --- | --- |
| 轨道 | (1, 0, 1) | 与头部相同，向正面延伸 |
| 玩家面板 | (-7, 1, 0) | 与头部相同 |
| 歌曲面板 | (11, 1, 0) | 与头部相同 |

头部底座三个接口位于 X=1.5、5.5、9.5，轨道起点三个接口位于 X=0.5、4.5、8.5。接口为黑色座加青色插口；头部侧面中部与两侧面板的对应侧面均有插口。接口直接接触连接，目前不支持跨间隙连线、电缆或转接。

旧版本生成的部件没有注册记录和接口，需要使用新命令重新生成一次。自行复制方块不会复制部件注册信息。重叠生成会取消旧部件注册，不再把残缺旧结构识别为完整设备。

## 背面窗口

到部件**背面**，右键中间的入口窗口。头部为灰框、黑底与 CHUNITHM Logo，左右面板暂保留青色窗口。头部使用原生控制界面；左右面板使用原版容器窗口，悬停图标查看按钮说明，点击执行，图标不能取走或放入物品。界面关闭后保留设置。

- 头部：使用新版原生控制界面，悬停 CHUNITHM 查看轨道左、中、右接口及两侧面板状态，点击刷新。检测包含所有结构方块及其朝向；轨道完整性检查覆盖全长。状态为已连接、未连接/未对齐、结构损坏、区块未加载。详见 `HEAD_UI.md`。
- 玩家面板：列出可导入的信息，点击一个条目更新面板。默认仅提供当前 Minecraft 玩家的名字，其他数值为占位；完整玩家资料由提供器接入。支持翻页。
- 歌曲面板：选择提供器中的曲目/谱面，切换 MASTER、EXPERT、ULTIMA，按 0.5 调整音符速度（0.5～100），切换镜像。默认歌曲列表为空，界面明确提示等待目录接口。选歌保留当前速度、镜像设置。

背面窗口需结构完整才可打开；远离、跨维度或结构损坏会关闭。玩家、歌曲设置修改需要与生成命令相同的管理员权限，连接状态可查看。不要在旁观模式下测试方块右键交互；切换创造模式后操作。

目前歌曲/难度/速度/镜像是**保存的播放配置与显示数据**，未接入音乐、谱面加载、判定或运动播放。手动切换难度暂不校验某曲目是否有对应谱面，也不计算定级数；提供器可返回带难度及定级数的完整谱面条目。后台不会自动修改现有随机 TAP 测试。

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

`powershell -NoProfile -ExecutionPolicy Bypass -File .\dev.ps1 runClient -PconnectionQuickPlay` 自动进入 New World，在 X=116～140、Y=-61～-58 附近生成测试整机，检查五个接口全部连接，并将玩家移到头部背面、打开状态窗口。测试路径会加载其小范围区块，并设置创造模式与白天；普通启动不会执行。

该显式开发测试还会通过游戏自身的截图接口保存 `run/screenshots/head-control-ui.png`，随后关闭菜单并保存背面 Logo 截图 `head-control-logo.png`，便于确认实际加载的是最新版界面和资源。普通启动不会自动截图或关闭菜单。

已通过构建、四个水平朝向的接口对齐检查，以及客户端中的整机生成、五接口状态、头部菜单打开及注册文件写入检查。玩家导入和歌曲设置按钮尚未完成手动逐项视觉验收。
