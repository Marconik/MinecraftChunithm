# 2712 · Theatore Creatore

作者：Toby Fox & かめりあ。元数据、300×300 无元数据 PNG 封面、48 kHz 立体声 Ogg Vorbis 音乐与 MASTER/ULTIMA 均已加入 MOD。音乐时长 154.389333 秒；基准 BPM 185；音频完整解码和流式引用检查通过，原始 E 盘文件保持不变。

| 难度 | 定数 | 显示等级 | 源记录（含控制/装饰） | 逻辑音符数 | 原始判定数 |
| --- | ---: | --- | ---: | ---: | ---: |
| MASTER | 14.4 | 14 | 2548 | 1514 | 2097 |
| ULTIMA | 15.3 | 15 | 3754 | 2179 | 2889 |

歌曲身份和难度以 Music.xml 映射为准。谱面首音符时间为 1297.297297 ms，末音符为 151459.459459 ms，首尾时间和源统计核验通过。元数据和谱面位于 `data/minecraftchunithm/songs/2712/`；索引列出 MASTER、ULTIMA，曲库现在共十一首、十三张已导入谱面。

两份 C2S 1.13 数据均无未识别记录。MASTER 的 8 条、ULTIMA 的 18 条 SLP 已按行保留在 `sourceVisualControls`，**SLP 演出效果当前仍未实现**，与 YOUNITHM 保持相同的预览限制；`visualWarnings` 明确记录此限制。ALD 灰/黑装饰使用与 Air 同粗细的纯色荧光线，NON 保留动作条。MOD 尚无输入判定和评分。

## 播放

新机台默认流速 20、上限 30，SPEED 每步 0.25。旧机台保存的设置不被新默认值覆盖。通过歌曲面板选择对应难度，或在已有完整轨道旁运行：

```mcfunction
/chunithm chart play 2712
/chunithm chart play "minecraftchunithm:songs/2712/ultima.json" 30 false
```

首条命令默认 MASTER、20 格/秒。音符中心在谱面时间点到达判定线（末端前半格），随后继续离场；离场移除时间不作为到达时间。

## 重复转换

先对 `A000/music/music2712/2712_03.c2s`、`2712_04.c2s` 运行 `tools/analyze-c2s.py`，输出为 `build/chart-analysis/2712_03.analysis.json`、`2712_04.analysis.json`。音频通过 `tools/convert-hdd-song-audio.py 2712` 转换一次，再运行：

```powershell
python tools/package-hdd-songs.py 2712 --difficulties MASTER ULTIMA
python tools/export-playable-charts.py 2712 --difficulties MASTER ULTIMA
.\dev.ps1 build --offline
python tools/check-song-resources.py
```

几何检查覆盖默认/最高速度、正反镜像及两张新谱面全曲每 250 ms 采样；对所有歌曲的音符端点另核对其到达坐标为判定线中心，而非轨道末端。

定向客户端测试为 `.\dev.ps1 runClient -PinputQuickPlay -PtheatoreQuickPlay --offline`，使用独立测试存档，自动检查默认 20 的面板、双难度选曲、音乐播放/暂停及 30 流速下的跳转。截图位于 `build/air-input-test/screenshots/theatore-*.png`。
