# ULTIMA 与 YOUNITHM

ULTIMA 使用既有难度枚举，并在选曲列表、待播队列中统一显示黑紫色标签。导入的 ULTIMA 与 MASTER 分别选择、保存和播放；未拥有 ULTIMA 的歌曲仍显示空标签。

歌曲信息面板选择 ULTIMA 时，在原有封面、难度、歌曲名、作者、SPEED/MIRROR 层下显示用户提供的黑底粉红斜纹图案。MASTER 保持紫色背景。切换时移除旧显示层，重新打开存档后也按当前难度刷新。背景使用原生方块图集单面模型，贴合原有表面深度。

## 2967 · YOUNITHM

作者：大国奏音。身份和难度取自本地 Music.xml，忽略 C2S 中占位的 MUSIC/DIFFICULT/LEVEL 字段。

| 难度 | 定数 | 显示等级 | 源记录（含控制/装饰） | 逻辑音符数 | 源判定数 |
| --- | ---: | --- | ---: | ---: | ---: |
| MASTER | 15.5 | 15+ | 2975 | 1752 | 3045 |
| ULTIMA | 15.8 | 15+ | 6460 | 2446 | 4321 |

资源在 `data/minecraftchunithm/songs/2967/`，包含 `song.json`、`master.json`、`ultima.json`；索引同时列出两种难度。音乐为 178.176 秒、48 kHz、立体声 Ogg Vorbis，共用既有左右边框声源后端；封面为 300×300 无元数据 PNG。未修改 E 盘原始数据。

ULTIMA 源文件为 C2S 1.13，新增 GRN、PNK、CYN 装饰色已加入模型、注册和校验。共 21 条 SLP、14 条 SLA 已逐行保留在 `sourceVisualControls`，含原字段、行号、起止时间；**其演出语义尚未核实，当前渲染器不执行这些控制**。`visualWarnings` 写入转换结果、元数据及加载日志。当前可测试基础谱面/音乐，不能视为原作演出的完整复现。原有 ALD 动画、透明度和动作间隔也仍属既有近似实现。

所有判定统计仅用于核验转换，MOD 仍未实现评分。MASTER/ULTIMA 均通过源统计、首尾时间、锚点检查；音符记录按行保留，不按相同坐标去重。

密集路径的宽度渐变细分限制在沿轨道每 1/16 格的粒度，不删除源段，避免微小控制段生成数千个重复子段。仍保留每帧 4096 个显示实体上限。

## 使用

在歌曲面板背面的 SELECT 列表选择 YOUNITHM 的紫色 15.5 或黑紫色 15.8 标签，然后从头部开始。也可在已有完整轨道旁使用：

```mcfunction
/chunithm chart play 2967 10 false
/chunithm chart play "minecraftchunithm:songs/2967/ultima.json" 10 false
```

数字 ID 保持默认 MASTER，指定 ULTIMA 使用完整资源 ID。面板外观由歌曲面板的队列输入驱动，直接执行 chart 命令不改写该队列。

## 重新转换

```powershell
python tools/analyze-c2s.py E:/SDHD_2.50.00_20260518110527_0/data/A000/music/music2967/2967_03.c2s --output build/chart-analysis/2967_03.analysis.json
python tools/analyze-c2s.py E:/SDHD_2.50.00_20260518110527_0/data/A000/music/music2967/2967_04.c2s --output build/chart-analysis/2967_04.analysis.json
python tools/convert-hdd-song-audio.py 2967
python tools/package-hdd-songs.py 2967 --difficulties MASTER ULTIMA
python tools/export-playable-charts.py 2967 --difficulties MASTER ULTIMA
python tools/generate-ultima-assets.py
.\dev.ps1 build --offline
python tools/check-song-resources.py
```

音频已经转换时不必重复运行音频步骤。转换器默认仍为 MASTER，双难度需传入 `--difficulties MASTER ULTIMA`。

## 验证

- 全曲每 250 ms 对两张新谱面采样，覆盖 3/10/20 格每秒及两种镜像；连同原有回归共 10,372 帧，峰值 2,356 个显示实体。
- 两张谱面的所有记录逐条比对原字段、行号、时间、宽度、颜色和高度，35 条 SLP/SLA 原记录完整保留。
- JAR 内十首歌曲的元数据、索引、谱面、封面模型与流式声音引用检查通过，音频完整解码通过。
- 独立测试世界内核对 MASTER 紫色、ULTIMA 图案、可选 15.5/15.8 标签及选择回填；两种谱面均检查原生左右声源活动，覆盖 60/140 秒跳转及暂停。测试自动退出，不改动 `run/saves` 的用户世界。

可复查原生界面：`build/air-input-test/screenshots/younithm-master-panel.png`、`younithm-ultima-panel.png`、`younithm-difficulty-selector.png`、`younithm-selected-queue.png`。定向复测命令为 `.\dev.ps1 runClient -PinputQuickPlay -PultimaQuickPlay --offline`，仅用于已准备的独立测试世界。
