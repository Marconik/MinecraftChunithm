# Melodiniq ULTIMA 超界参数与播放

分析对象是本地 `A000/music/music2999/2999_04.c2s`，C2S 1.14.00。
6,166 条音符/路径/装饰记录均保留。MASTER 没有横向超界，ULTIMA 有
2,559 条记录的起点或终点超界。重新分析后的所有源统计、Air 绑定和
176 个 Slide 起点检查均通过，无未知指令。

## 定义方式

超界不是新的音符类型，也没有单独的开关、飞行速度、旋转角度或缩放曲线指令。
源谱直接使用常规音符的有符号横坐标，以及持续路径的终点、宽度和高度。
以下表格中的字段名与转换后的 JSON 对应：

| 源参数 | JSON | 含义及本谱数据 |
| --- | --- | --- |
| Measure、Offset | timeMs | 音乐时间；一小节 384 tick，使用实际 BPM 事件换算 |
| Cell | lane | 音符左边沿分区；起点出现 `-16…31`，负数位于轨道左侧 |
| Width | width | 向右占用的分区宽度；`1…16`，可从轨道外延伸回轨道内 |
| Duration | endTimeMs | 持续段长度，以 tick 定义；它决定路径变化所占的音乐时间 |
| End Cell | endLane | Slide、Air Slide、ALD 段末端的左边沿；可以超界 |
| End Width | endWidth | 段末宽度；和起始宽度不同即形成收窄/展开 |
| Starting Height、Target Height | height、endHeight | ASC/ASD 和 ALD 的起止高度；本谱合计 `1…25` |
| Target Note | targetType | Air 依附类型；通过原始时间、分区和宽度解析关联 |
| Color | color | 装饰/Air 颜色；不改变坐标或宽度 |
| SLP 的倍率与 ID、SLA 的区域 | scrollEvents、scrollProfiles、scrollProfile | 控制纵向流速与差速，不是横向超界开关 |

完整横向覆盖为 `[-16, 32]`，相当于正常 `[0, 16]` 轨道宽度的三倍。
620 条记录的起止宽度不同，其中 174 条同时超界。没有独立的
`scale` 字段；例如宽度 `8 → 1` 就是末端变为起点宽度的 12.5%。
单个 Tap/EX/Mine 等瞬时记录只有自己的位置和宽度，没有独立的横向动画轨迹。

路径在起止数据之间作线性插值；源谱用很多短段拼接成曲线和快速抖动。
例（源第 2160、2161 行）：

```text
SXC 78 99  4   8 1 -16 1 SLD BS
SXC 78 100 -16 1 1 4   8 SLD BS
```

第一段在 1 tick 内由左边沿 4、宽 8 变到左边沿 -16、宽 1；下一段再返回。
这个位置约 97.044 秒，1 tick 约 3.189 毫秒。它描述的是极短空间尖峰，
不应改成“把整个音符在固定时长内甩出去”的额外动画。

瞬时超界示例（源第 2240 行）：

```text
CHR 79 288 -12 16 CE
```

这枚 EX Tap 占用 `[-12, 4]`，原来的钳制会错误地把它移动到 `[0, 16]`。

## MOD 映射与保留行为

转换器不再钳制 `lane/endLane`。新谱使用
`outOfBoundsPolicy.mode = PRESERVE_SOURCE_COORDINATES`，并核验超界记录数。
原先 `sourceLane/sourceEndLane/sourceFields` 的追溯信息继续保留。
旧 `CLAMP_DISPLAY_PRESERVE_SOURCE` 谱仍按其已有坐标加载，不偷偷修改资源。
只有显式启用扩展坐标的谱允许轨外位置；异常、非整数、非法宽度和极端坐标仍拒绝加载。

- 横向中心：`x = 0.5 + (lane + width / 2) × 0.5`，每个分区是 MC 半格。
- 镜像：`lane' = 16 - lane - width`，轨外左侧翻到轨外右侧，不贴边。
- 纵向：沿用各自滚动曲线积分，仍在原音乐时间通过判定线。
- 超界抬升斜面：头部端抬升为 0，向判定线逐渐升高；判定线端的高度
  取横向超出距离（MC 格），最多 1 格。对应公式如下。音符的原有高度
  叠加在这个斜面上，Air/装饰的源高度不会被压平。
- ASC/ASD、ALD 的路径高度：`y = height × 0.2`，叠加轨道表面高度。
  默认高度 5 仍为 1 格；Air Slide 主体和动作横条随源高度变化。
  AHD/AHX 没有起止高度参数，继续固定在 1 格。方向箭头仍使用原有图案。
- 长条主体维持当前宽度的 95%，头尾维持满宽；渐变宽度由起止宽度插值。
  Air/ALD 线条维持此前的固定荧光截面，源宽度影响中心线路径，不把荧光线加粗。
- 纵向显示裁切到头部起点与判定线，不裁掉横向轨外部分。
  扩大实际显示实体的包围盒以包含倾斜段，避免横跨轨道的长段被视锥误裁。
- 提示音、临时自动 Critical 计分、总音符数和进度不随超界改变。
  Ground Slide 只亮与 `[0,16]` 实际相交的单元；完全轨外音符不挤到边缘单元。

斜面使用完整逻辑宽度计算（不使用缩到 95% 的主体或扩大的动作条图案）：

```text
outsideBlocks = max(0, -lane, lane + width - 16) × 0.5
endHeight     = min(1, outsideBlocks)
rampHeight(z) = endHeight × clamp(z / (trackLength - 0.5), 0, 1)
```

超出半格时，判定线端抬高半格；超出一格或更多时，端点抬高一格。
高度函数在判定线后保持终点高度，但显示在判定线处裁掉。镜像左右对称，轨内音符保持原高度。
单个按键随斜面倾斜；长条逐点叠加抬升，在进出轨道、达到最大高度、
跨过判定线和变速边界处切段。横移/变宽与斜面距离的乘积会形成弯曲，
按不超过 1/256 格的弦误差细分，以避免头尾和主体断开。

ULTIMA 超界记录分类：ALD 888、MNE 509、ASC 255、TAP 212、SXC 201、
CHR 95、FLK 78、SLC 76、SXD 56、AUL 53、AIR 39、AUR 28、ADL 18、
SLD 16、ASD 12、HLD 10、ADR 7、HXD 6。它们包含纯装饰路径，
因此 2,559 不是新增判定音符数。

格式字段参考：[Chunithm-Research 的作者研究文档](https://github.com/beer-psi/Chunithm-Research/blob/main/Charting.md)。
上述数量、坐标和示例均来自此次本地源文件分析；线性段拼接是 MOD 的空间呈现方式，
不声称复现原客户端全部透视、演出插值或超过游戏帧率的瞬时动画。

## 验证与手动查看

```powershell
python tools/check-mine-conversion.py
.\dev.ps1 build --offline
.\dev.ps1 runClient -PoffTrackQuickPlay --offline
```

离线检查覆盖原始 2,559 条超界记录、镜像、变宽段、源高度、95% 主体与满宽端点，
并与旧钳制坐标逐时刻比较提示音、计分和进度。原生测试仅使用
`build/air-input-test` 隔离存档，抽查正常/镜像各十个真实谱面片段的实体位置，
记录轨外实体、倾斜路径及空间 Air 段，保存截图后退出。
结果写入 `build/air-input-test/melodiniq-off-track-checks.json`。
随机过线的原因、同步姿态修复及实际客户端插值检查见 [显示同步](DISPLAY_TRANSPORT.md)。

2026-10-27 验证通过：全量构建检查了 101,784 个渲染帧，包含 3/10/20/30
流速及双镜像。改为判定线裁切后，源坐标检查观察到 340 个轨外端点、
450 个轨外路径片段与 312 个非默认高度 Air 片段。斜面回归另验证了 510 个
真实按键的抬升高度，以及短/长轨道、头部/判定线端点、镜像、变宽、倒放和动作条逻辑宽度。
原生客户端的二十个片段检查也通过，正常及镜像各记录到 136 个轨外中心、
236 个横向倾斜路径、25 个非默认高度 Air 片段和 34 个实际抬升的地面/方向按键；
实际渲染中心与计算结果一致，262,349 个客户端渲染子帧未越过判定线，
二十张截图已保存，客户端自动保存退出。

在完整机台附近查看：

```mcfunction
/chunithm chart play "minecraftchunithm:songs/2999/ultima.json" 20 false
/chunithm chart seek 97
```

约 97 秒起出现剧烈横移/缩放；约 123.6 秒起出现大量轨外 Mine、Tap、Air 路径。
