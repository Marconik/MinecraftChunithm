# 四首 MASTER 谱面导入

本批仅导入 MASTER；同时包含 Music.xml 元数据、300×300 无元数据 PNG 封面、48 kHz 立体声 Ogg Vorbis 音乐。原始 E 盘文件未修改。索引列出名称和已导入难度；曲库共 15 首歌曲、17 张谱面。

| ID | 曲名 | MASTER 定数 | 源记录（含控制/装饰） | 逻辑音符数 | 原始判定数 | 音频秒数 |
| --- | --- | ---: | ---: | ---: | ---: | ---: |
| 2838 | 神鳴 | 15.6 | 4047 | 2403 | 4500 | 173.290667 |
| 464 | World Vanquisher | 15.5 | 3328 | 2156 | 3000 | 165.376000 |
| 2279 | グラウンドスライダー協奏曲第一番「風唄」 | 15.6 | 1872 | 1872 | 2299 | 164.629333 |
| 1035 | 赤壁、大炎上！！ | 15.1 | 3649 | 2384 | 4208 | 171.946667 |

资源位于 `data/minecraftchunithm/songs/{四位ID}/song.json`、`master.json`，音乐和封面使用对应的 `assets/minecraftchunithm/sounds/songs/`、`textures/song/` 路径。

## 保留的演出与限制

- 神鳴的 24 条 SLP 均已保留到 `sourceVisualControls`，当前渲染器不执行其演出效果；`visualWarnings` 在元数据、谱面与加载日志中明确提示。与已有 YOUNITHM、Theatore Creatore 使用相同限制。
- 神鳴最后的两条 ALD/NON 动作条在普通音符结束后再延续五个 tick（约 15.24 ms）。转换器的源统计范围现包含 NON 动作条，彩色 ALD 装饰尾迹仍单独保留。全部 17 份原始谱面重新分析通过，记录数量与源统计未变。
- 赤壁、大炎上！！包含 7 条 BPM、9 条 MET 和 67 条 SFL；转换为 87 条无判定、无发光的紫色变速边界线。
- 源统计用于转换核验，MOD 当前仍不提供命中判定与计分。

## 重复转换和播放

先对四首歌曲各自的 `xxxx_03.c2s` 运行 `tools/analyze-c2s.py`，输出到 `build/chart-analysis/xxxx_03.analysis.json`；再分别运行 `tools/convert-hdd-song-audio.py ID`。随后：

```powershell
python tools/package-hdd-songs.py 2838 464 2279 1035
python tools/export-playable-charts.py 2838 464 2279 1035
./dev.ps1 build
python tools/check-song-resources.py
```

通过歌曲选择窗口选择 MASTER，或在完整轨道旁运行 `/chunithm chart play ID`。默认速度 20，镜像默认关闭。构建检查覆盖本批四首全曲每 250 ms、速度 3/10/20/30、两种镜像下的几何，以及全部音符端点在判定线的到达位置。
