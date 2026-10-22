# 左侧名牌面板

独立生成，尺寸与歌曲面板相同：宽 7、高 3、厚 1 格。以 floor(玩家位置) 沿水平朝向前进一格，作为**右下角方块**，向左、向上展开，正面朝向玩家。Y 不减 1；与歌曲信息板的左下角生成规则左右对称。生成会替换范围内的 21 个方块。不要在同一位置连续生成歌曲板和名牌板，两者会共用锚点方块；应分别站在预期的左右面板锚点后方生成。

金色外框、淡金背景，无企鹅背景。顶部为 TEAM，中部为银色或金色称号，下方为等级、名字和 RATING，右下角为角色头像。侧面紫色、背面黑色；文字和显示层满亮度。此阶段只提供显示接口，不计算评级或角色成长。

## 命令

开发环境自动打开 `New World` 并生成银色、金色两块测试面板：`powershell -NoProfile -ExecutionPolicy Bypass -File .\dev.ps1 runClient -PnameplateQuickPlay`。测试区域位于 X=58～72、Y=-60、Z=-1；会替换两块面板范围内的方块，并设置白天、晴天及创造模式。普通启动不会自动生成。

```mcfunction
/chunithm nameplate
/chunithm nameplate_data {"team":"My Team","title":"欢迎来到中二节奏","titleColor":"SILVER","level":"99","name":"Player","character":"minecraftchunithm:character/default","rating":"16.50"}
```

更新数据时保持生成时的站位和朝向。`titleColor` 支持 `SILVER`、`GOLD`，不区分大小写。等级、RATING 使用文本，可保留小数位或显示 `**` 等占位。TEAM、称号、名字最长 256 字符，等级 16 字符，RATING 32 字符，均为单行。长文字自动缩小至对应区域内；名牌使用 minecraft:uniform 字体，ASCII 按当前游戏内置 Unifont 字宽计算，中日韩全宽字按 9 像素推进量计算，其余字符保守估算。自定义字体资源包可能需要调整。

Java 接口：

```java
NamePanel.update(level, NamePanel.placement(source), new NameDisplayData(
    "My Team", "称号", NameDisplayData.TitleColor.GOLD,
    "99", "Player", characterModel, "16.50"));
```

`characterModel` 为物品模型资源 ID，和歌曲封面采用相同机制。默认 `minecraftchunithm:character/default` 是中性人物剪影占位；真实角色头像由资源包提供，不附带原作角色素材。

例如自定义 `minecraftchunithm:character/my_character`：

- `assets/minecraftchunithm/items/character/my_character.json` 指向对应 `minecraft:model`。
- 对应模型继承 `minecraft:item/generated`，引用正方形头像 PNG，fixed 变换保持单位缩放。
- 所有观看客户端需安装相同资源。图片不应预先镜像；面板已补偿物品模型正面朝向。

数据更新清理并重建本面板的显示实体，使用独立标签，不会清理歌曲板或头部显示。存档保存显示实体，业务层自行保存输入数据。

外观资源由 tools/generate-name-panel.ps1 生成：连续淡金背景、细边框、银色/金色渐变称号条与中性头像占位图。背景和称号条通过满亮度物品显示实体绘制，不再拉伸原版砂岩、铁块或金块材质。不同显示层保留深度间隔，避免共面闪烁。docs/name-panel-preview.png 仅为静态背景预览，不含动态文字、称号和头像。

背景导出时单独补偿物品模型背面显示的水平 UV 镜像：游戏内 TEAM 银色标签在左侧，头像框在右侧，分隔线止于头像框左侧。预览保持正常阅读方向；无需修改文字坐标或自定义角色资源。


字体修正：统一使用原版高分辨率 Unicode 字体，TEAM 和称号适度加粗，名字、等级、RATING 使用正常字重；按字体实际 ASCII 推进量及半像素粗体增量进行宽度限制和左对齐，并调整竖直居中。称号条恢复最初的分层银色/金色样式（边框、主体色、上部亮条），不再使用连续渐变。需要重启客户端并重新生成或更新名牌才能替换已有文字实体。
