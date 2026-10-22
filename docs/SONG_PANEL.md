# 曲目信息面板

尺寸：宽 7 格、高 3 格、厚 1 格。以玩家所在方格（floor 玩家坐标，Y 不减 1）沿水平朝向前进一格，作为面板左下角；面板从玩家看到的左侧向右展开，向上生成，正面朝向玩家。独立生成，不再相对头部定位；会替换对应范围的 21 个方块。侧面紫色、背面黑色；显示层使用满亮度。`song_data` 使用相同位置及朝向定位，需在生成时的同一锚点更新数据。

## 输入与测试

生成后，在相同位置与朝向执行：

```mcfunction
/chunithm song
/chunithm song_data {"cover":"minecraftchunithm:cover/default","title":"The First Seeker of Souls","artist":"Creator A vs Creator B","track":3,"difficulty":"MASTER","level":"15+","speed":8.0,"mirror":false}
```

`difficulty` 支持 MASTER（紫）、EXPERT（红）、ULTIMA（黑），不区分输入大小写。`track` 最小 1；`level` 是文本，可输入 15+ 等格式；`speed` 是有限正数，范围大于 0 且不超过 100；`mirror` 为布尔值。速度和镜像在此处只显示，不修改当前运动测试。

公共 Java 数据接口：

```java
SongPanel.update(level, placement, new SongDisplayData(
    coverModel, title, artist, track,
    SongDisplayData.Difficulty.MASTER, "15+", 8.0, false));
```

`placement` 为面板最左下方块与正面朝向的坐标系，可使用 `SongPanel.placement(commandSource)` 得到。更新会清理该面板上的旧显示实体并重建，显示实体可随存档保存，业务层自行保存原始数据。不计算评分或加载歌曲。

## 自定义封面

cover 是客户端物品模型资源 ID，不是网址或本地文件路径。默认已提供 `minecraftchunithm:cover/default`。自定义 ID 例如 `minecraftchunithm:cover/my_song`，资源包中需要：

- `assets/minecraftchunithm/items/cover/my_song.json` 指向一个 `minecraft:model`。
- 该模型可继承 `minecraft:item/generated`，并引用正方形 PNG 贴图。
- fixed 显示变换保持正面、单位缩放；所有观看客户端必须安装相同资源。未知 ID 会出现缺失模型，服务端无法核对客户端资源包。

可参考项目中的 `items/cover/default.json`、`models/item/cover_default.json` 和 `textures/item/cover_default.png`。

本轮编译通过，尚未进行游戏内视觉复测。原版字体可能无法显示本机客户端未覆盖的特殊字符。

难度标题字号为此前的 85%（缩放 2.55）；左边界设为 2.50 格，与右边界为 2.40 格的封面框留出 0.10 格间隙。左对齐与宽度限制统一计入粗体字宽增量，并补偿 TextDisplay 的一像素偏移，避免难度文字伸进封面。字宽按原版字体保守估算，自定义字体资源包可能需要调整。

排版修正：封面使用独立的 180 度朝向补偿，避免读到物品模型的镜像背面；白色封面框为 2.28×2.28 格，封面为 2.20×2.20 格。TRACK、难度、曲名和创作者采用同一左对齐方式，难度标题放大，LEVEL 区域独立对齐，速度与镜像之间增加分隔线，曲名与创作者之间增加细分隔线。封面 fixed 资源应保持原始图像方向，不需自行镜像。
