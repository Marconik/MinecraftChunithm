# 音符属性与模型

新增 `SPEED_CHANGE` 变速标记：紫色细线固定覆盖16分区（8格），`judged()`为false；不进入判定、连击或分数统计。资源ID为 `minecraftchunithm:speed_change_line`。转换时每段SFL的开始/结束各生成一条，同刻边界合并。详见 [变速定义](SPEED_CHANGES.md)。

所有音符使用 16 个逻辑分区，1 分区 = 0.5 格。六类新音符各有 16 种宽度的原生 JSON 模型，可在创造模式建筑方块分类搜索，或使用 `/give @s minecraftchunithm:hold_4` 等 ID 获取。

| 类型 | ID 前缀 | 属性与含义 | 模型样例 |
| --- | --- | --- | --- |
| EX-Tap | ex_tap | 瞬时触摸；区别于普通 TAP 的类型标记，判定宽容度待判定系统定义 | 金黄色短条、白边与中线 |
| Hold | hold | durationMs > 0；起终点位置及宽度一致，持续按住 | 金色端点、黄色连续尾带 |
| Slide | slide | durationMs > 0；多个严格按时间排序的路径节点，可改变位置及宽度，每段选择连接方式 | 蓝色端点、从亮青色中心向深蓝两侧渐变的连续带 |
| Flick | flick | ANY / LEFT / RIGHT；触摸后滑动 | 蓝色短条、白色横向方向提示 |
| Air | air | UP / DOWN 及四种斜向；可用 parentId 关联地面音符 | EX-Tap 底座，上抬绿箭头或下压粉色箭头，均有白边 |
| Air-Action | air_action | parentId 必填，关联起始 AIR；durationMs 和路径节点描述悬空路径 | 固定粗细的悬空绿线，两端各有一条悬空紫色动作条，无落地立柱 |

公共属性：id、kind、timeMs、lane、width、durationMs、direction、parentId、nodes。timeMs 表示到达判定线的谱面时间。节点包括 offsetMs（相对起始时间）、lane、width、height（格）；持续结构必须具有时间为 0 和 durationMs 的首尾节点。属性采用不可变 Java record，并校验时间、节点顺序与横向边界。关联 ID 的存在性需在后续完整谱面加载阶段检查。

`NoteModelItem.definition()` 提供模型物品对应的样例属性。模型是静态资源，尚未根据任意谱面实时生成。曲线展示资源中的细带在选定宽度包围范围内移动；包围范围不等于曲线上每个节点的实际带宽。

## 节点连接

节点新增 `connection`，控制从当前节点到下一个节点的连接，末节点不使用该字段。四参数节点构造器仍兼容，默认 LINEAR。

| connection | 归一化进度 p(t)，t∈[0,1] | 中点进度 |
| --- | --- | --- |
| LINEAR | t | 0.5 |
| PARABOLA_UP | 2t−t²（归一化图像中向上拱） | 0.75 |
| PARABOLA_DOWN | t²（归一化图像中向下弯） | 0.25 |

`NoteDefinition.sample(offsetMs)` 根据节点采样位置、宽度和高度，使用同一段的进度插值。曲线穿过精确端点且不会超出端点数值范围。这里的“上/下”是进度图中的弯曲方向，不会自动让地面 Slide 抬离轨道；Air-Action 的空间高度由节点 height 决定。

例如两节点：`Node(0,0,2,0,PARABOLA_UP)` 与 `Node(1000,14,2,0,LINEAR)`；中间 500ms 的 lane 为 10.5，仍在 16 分区范围内。可在不同相邻节点上混用三种连接。

模型使用自制的 Minecraft 方块几何与贴图，依据参考图制作近似外观。Hold/Slide 固定样例尾带深度为约 2 格，实际长度未来由持续时间 × 流速决定。方向与路径节点尚未接入动态长音符渲染。未添加实际输入判定、得分或音乐。

直线模型仍使用 `slide_4` / `air_action_4`；曲线版本为 `slide_parabola_up_4`、`slide_parabola_down_4` 和对应 air_action ID。Air 的默认 `air_4` 表示上抬，其余为 `air_up_left_4`、`air_up_right_4`、`air_down_4`、`air_down_left_4`、`air_down_right_4`。Air 和 Slide 支持后缀 1～16；Air-Action 仅保留后缀 2，绿线截面固定为 0.075×0.075 格，动作条模型宽 1 格。其他 Air-Action 宽度已移除。

在空地执行 `/function minecraftchunithm:path_showcase`，会替换脚下一层 9×48 格区域，展示三类路径/方向样例。

## 查看全部类型

在至少 9×96 格的空地、且区域已加载时执行：

```mcfunction
/function minecraftchunithm:note_showcase
```

会替换脚下一层的 9×96 格区域，并依次展示 EX-Tap、Hold、Slide、Flick、Air、Air-Action，每类宽度为 1、2、4、8、16 分区。该命令会停止当前维度的运动测试。普通 `/chunithm debug test` 目前仍然只生成 TAP。

也可在完整轨道表面右键放置单个模型；横向边界会检查。长模型向后延伸，放置时需自行给起点留出约 1.5 格纵向空间。

重新生成资源：`node tools/generate-models.cjs`。

Air-Action 的逻辑 width 表示动作条宽度（最少 2 分区，即 1 格），不控制绿线粗细。节点 width 也须满足此下限。当前三个展示模型为 air_action_2、air_action_parabola_up_2、air_action_parabola_down_2。旧存档中其他后缀的展示实体需清除后重新运行展示函数。

Air 上抬/下压标记为竖直薄片，最大厚度 0.009375 格（不含 EX-Tap 底座），采用宽箭头、白边、V 形缺口轮廓；下压为上下镜像，左右斜向由轮廓剪切生成。当前模型为原生方块模型条带近似轮廓，未使用半透明拖影。Air-Action 截面边长增至原来的三倍，截面积九倍，两端动作条仍为一格宽。

专用 Air 展示启动（保留测试存档，重建坐标 0,-61,0 开始的 9×32 区域）：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\dev.ps1 runClient -PmodelQuickPlay
```

该模式自动进入 New World，展示六种 Air 薄片箭头和三种 Air-Action 路径，将观察点置于区域末端上方。游戏内也可在空地执行 `/function minecraftchunithm:air_showcase`。不自动启动音符运动。
