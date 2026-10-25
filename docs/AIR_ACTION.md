# Air Action 高度与荧光材质

`NoteDefinition.AIR_ACTION_HEIGHT = 1.0` 表示相对轨道表面（`TrackBlock.SURFACE_HEIGHT = 0.625`）的高度，世界坐标为轨道基准 Y + 1.625。AHD、ASC、ASD 的连接线和动作条统一使用这一高度，ALD 的 NON 动作条同样处理；源数据中的空间高度字段仍保留，其他彩色 ALD 特效继续使用原有高度。

连接线采用用户提供的 `ntt_ahline.dds`，64×64 RGBA 原始转换保存在 `tools/assets/air-action-line.png`。运行纹理的 alpha 取原始值的 80%，透明边缘保留，中心最大 alpha 为 204。原有不透明方管替换为水平/竖直交叉的双面薄片，材质启用 `force_translucent`，模型满亮并关闭面部阴影。柔边光晕外径为 0.30 格，原语义线宽 0.075 格不变；不会添加遮盖整条轨道的实体绿块。

Air 上抬、下压及四种斜向箭头的标记高度从 13 模型单位增加为 17.5（约 +35%），地面底键与横向分区宽度不变。手持预览、线性/抛物线示例和谱面专用模型使用同一组参数。

`tools/air-action-models.cjs` 定向更新荧光连接线、示例及箭头，重复运行不会二次拉伸。完整 `generate-models.cjs` 经 `note-models.cjs` 调用同一生成器。模型仍使用原版半透明绘制，不依赖外部着色器。

谱面几何回归会检查每个 Air Action 连接线和动作条的高度恰好为 1、竖直倾斜为 0，并检查横向/纵向裁切。独立测试存档保存 `air-action-front.png`、`air-action-oblique.png`；测试包含原始高度 0、2、8 的节点，以验证统一高度规则。

## ALD 装饰线

所有歌曲的彩色 ALD 路径（包括 YOUNITHM、Chaotic Ørder）现在共享 Air 的 0.30 格光晕外径、交叉双面薄片和满亮度材质。按源节点中心线连接，保留原高度、走向、时间和镜像；源分区宽度不再让装饰线扩成色带。恒定粗细也不再因源宽度变化产生多余细分。

13 种颜色的 RGB 在整张贴图中保持不变，没有沿路径或横向的颜色渐变。仅 alpha 复用 Air 贴图原有的柔边光晕（0～204），实现对应颜色的半透明荧光；不用外部光影模组。NON 保留其动作条含义。

`tools/decorative-line-models.cjs` 可定向重建所有装饰模型/贴图。Air 和完整音符生成器同样调用它；ULTIMA 背景生成器不再覆盖装饰材质。执行 `python tools/check-decorative-lines.py` 可核对 13 色的纯色像素、透明度、薄片几何及图集引用。原生定向测试为 `.\dev.ps1 runClient -PinputQuickPlay -PdecorationQuickPlay --offline`，只在独立测试存档中生成调色板和两首歌曲的暂停预览。
