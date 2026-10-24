# 曲目信息面板

尺寸：宽 7 格、高 3 格、厚 1 格。以玩家所在方格（floor 玩家坐标，Y 不减 1）沿水平朝向前进一格，作为面板左下角；面板从玩家看到的左侧向右展开，向上生成，正面朝向玩家。独立生成，不再相对头部定位；会替换对应范围的 21 个方块。侧面紫色、背面黑色；显示层使用满亮度。`song_data` 使用相同位置及朝向定位，需在生成时的同一锚点更新数据。

## 输入与测试

生成后，在相同位置与朝向执行：

```mcfunction
/chunithm song
/chunithm song_data {"cover":"minecraftchunithm:cover/default","title":"The First Seeker of Souls","artist":"Creator A vs Creator B","track":3,"difficulty":"MASTER","level":"15+","speed":8.0,"mirror":false}
```

`difficulty` 支持 MASTER（紫）、EXPERT（红）、ULTIMA（黑），不区分输入大小写。输入的`track`为1～3，对应待播队列位置；`level`是文本，可输入15+等格式；`speed`范围0.25～20，`mirror`为布尔值。速度与镜像是连接机台的统一设置，控制界面和面板输入均会更新世界中的面板显示及已加载谱面，保留播放/暂停时间，不影响无关机台。

公共 Java 数据接口：

```java
SongPanel.update(level, placement, new SongDisplayData(
    coverModel, title, artist, track,
    SongDisplayData.Difficulty.MASTER, "15+", 8.0, false));
```

`placement`为面板最左下方块与正面朝向的坐标系，可使用`SongPanel.placement(commandSource)`得到。单曲更新会写入对应TRACK槽位，保留其他槽位；空曲名清空对应位置。头部Music窗口直接读取面板输入，不自动添加默认曲目。系统保存原始三槽输入、设置与已播标记，更新会清理该面板上的旧显示实体并重建；关联到目录中实际已导入的谱面时，可准备给已连接轨道播放，不计算评分。

三曲输入也可一次替换，缺少的位置使用null；空数组清空全部位置：

```mcfunction
/chunithm song_data {"songs":[null,{"cover":"minecraftchunithm:cover/default","title":"Custom song","artist":"Creator","difficulty":"MASTER","level":"14+"},null],"speed":10.25,"mirror":true}
/chunithm song_data {"songs":[],"speed":10.00,"mirror":false}
```

数组顺序为TRACK 1～3，最多三项；没有填满时其余位置为空。Java调用`SongPanel.setQueue(level, placement, SongQueueData.empty().withSlot(1, song), speed, mirror)`；`song.track()`必须为2，与其位置一致。三曲元数据无需依靠测试示例生成，即使自定义曲目没有可播放谱面，也会按输入显示。

## 自定义封面

cover 是客户端物品模型资源 ID，不是网址或本地文件路径。默认已提供 `minecraftchunithm:cover/default`。自定义 ID 例如 `minecraftchunithm:cover/my_song`，资源包中需要：

- `assets/minecraftchunithm/items/cover/my_song.json` 指向一个 `minecraft:model`。
- 该模型可继承 `minecraft:item/generated`，并引用正方形 PNG 贴图。
- fixed 显示变换保持正面、单位缩放；所有观看客户端必须安装相同资源。未知 ID 会出现缺失模型，服务端无法核对客户端资源包。

可参考项目中的 `items/cover/default.json`、`models/item/cover_default.json` 和 `textures/item/cover_default.png`。

本轮编译通过，尚未进行游戏内视觉复测。原版字体可能无法显示本机客户端未覆盖的特殊字符。

难度标题字号为此前的 85%（缩放 2.55）；左边界设为 2.50 格，与右边界为 2.40 格的封面框留出 0.10 格间隙。左对齐与宽度限制统一计入粗体字宽增量，并补偿 TextDisplay 的一像素偏移，避免难度文字伸进封面。字宽按原版字体保守估算，自定义字体资源包可能需要调整。

排版修正：封面使用独立的 180 度朝向补偿，避免读到物品模型的镜像背面；白色封面框为 2.28×2.28 格，封面为 2.20×2.20 格。TRACK、难度、曲名和创作者采用同一左对齐方式，难度标题放大，LEVEL 区域独立对齐，速度与镜像之间增加分隔线，曲名与创作者之间增加细分隔线。封面 fixed 资源应保持原始图像方向，不需自行镜像。
