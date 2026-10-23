# 六首MASTER批量导入

六首音频、封面、歌曲元数据与MASTER已加入MOD。歌曲名取Music.xml；源谱面标题/难度头字段可能为占位值，身份以Music.xml的MASTER文件映射为准。未改动E盘源数据，未导入其他难度的谱面本体。

| ID | 曲名 | 音频秒数 | 源记录数（含控制/装饰段） | 原始判定数 | 紫色变速边界线 |
| --- | --- | ---: | ---: | ---: | ---: |
| 697 | folern | 142.656 | 1603 | 2000 | 0 |
| 465 | macrocosmos | 166.378667 | 2299 | 3085 | 39 |
| 258 | TiamaT:F minor | 152.128 | 3921 | 3175 | 0 |
| 2475 | Chaotic Ørder | 149.824 | 3789 | 3000 | 2 |
| 2354 | 月の光 | 178.645333 | 2298 | 2555 | 0 |
| 2241 | DA'AT -The First Seeker of Souls- | 164.266667 | 4682 | 3898 | 10 |

所有音频均为48kHz、双声道Ogg Vorbis，保留时间零点，通过FFmpeg完整解码检查。封面复用已转换的300×300、无元数据PNG。音频路径为`assets/minecraftchunithm/sounds/songs/<四位ID>/music.ogg`，歌曲元数据和可播放谱面为`data/minecraftchunithm/songs/<四位ID>/song.json`、`master.json`。

## 解析补充

- 增加AUL/AUR/ADW/ADL/ADR的方向，HXD、SXD、SXC的EX类别及动画字段，以及ASC、ASD、ALD的起终高度、颜色和动作间隔原字段。全部源记录保留，不按坐标去重。
- `sourceFields`逐条保存源记录的完整字段，`sourceOrder`保存原行号；同时保留sourceHeader、sourceStatistics、BPM/MET/CLK事件。已与六份原C2S逐行核对导出的字段、坐标、高度和时间。
- 旧版本的T_REC_ALL不含SLC控制段，新版1.11/1.12包含地面控制段；ALD装饰和ASC空中控制不算入该统计。分别校验各族记录数、逻辑起始数、锚点与首尾时间。六首均无未识别记录，所有校验通过。
- macrocosmos的CLK_DEF=0和CLK提示保留；不会把点击音提示当作时间坐标倍率。MET分子0表示结束后的分隔线关闭，不令音乐时钟停止。首尾时间与源统计相差不足1ms。
- macrocosmos共32段SFL，含负倍率、零倍率和最高750倍的短段。按原值保留；相邻区间交界合并成39条紫线。紫线沿基础速度移动且不发光。
- 月の光包含175条BPM记录；不能用单一BPM换算整曲时间。DA'AT保留其430条空中路径记录、1444条ALD装饰。

字段解释参考了[C2SViewer原始解析实现](https://github.com/Aeph1edur/C2SViewer/blob/main/src/core/read.py)，并以本地源文件的统计和时间独立核验。没有采用其按相同内容去重的方法，也没有改变已核验的固定384刻度坐标规则。

## 呈现与边界

六种Air使用现有薄片箭头，镜像时左右方向同时交换。EX-Hold及EX-Slide保留EX起始颜色；ASC/ASD按节点绘制悬空绿线、紫色动作条。源宽度为1分区的动作条在显示时扩至至少2分区（1方块），位置限制在轨道内，数据原宽度保持不变。

ALD彩色路径采用现有ItemDisplay色带近似，NON记录以悬空紫条表示。空中高度按源单位×0.2映射到方块，源高度5对应现有1格Air-Action高度。源动画代码、crushTick动作间隔保留为后续接口数据，暂未逐项复刻原作的动画、装饰透明度、重复动作及判定生成规则。转换保留完整数据，不表示这些视觉细节都已完成。

反向流速按SFL积分实现。长条在每个倍率边界分段并独立裁切；恒宽线不再按长度重复拆分，减少DA'AT等密集段的显示实体。八首共1630个离线画面采样检查通过，采样峰值2418，未提高4096上限；不保证任意轨道长度和任意自定义流速都低于上限。尚未在客户端逐曲目视校准。

## 测试

重启最新版客户端，靠近完整轨道后运行其一：

```mcfunction
/chunithm chart play 697 10 false
/chunithm chart play 465 10 false
/chunithm chart play 258 10 false
/chunithm chart play 2475 10 false
/chunithm chart play 2354 10 false
/chunithm chart play 2241 10 false
```

音频已打包，但既有chart播放模块仍不自动播放音频，也没有判定/评分或音画同步。命令呈现谱面；音频后端需后续对接。

## 重复转换

对每首先运行`analyze-c2s.py <MASTER源文件> --output build/chart-analysis/<四位ID>_03.analysis.json`和`convert-hdd-song-audio.py <ID>`。音频输出已存在时需要明确使用`--overwrite`才会重做。随后：

```powershell
python -B tools/package-hdd-songs.py 697 465 258 2475 2354 2241
python -B tools/export-playable-charts.py 697 465 258 2475 2354 2241
.\dev.ps1 build
python -B tools/check-song-resources.py
```

不清空build目录，其中仍保留音频中间文件、完整解码报告与源谱面结构分析。

## 追加1100：TECHNOPOLIS 2085

按相同流程加入音频、封面、元数据和MASTER：音频138.816秒、48kHz双声道Vorbis；源记录1372条（含418条Slide段），逻辑起始1015、原始判定1796。BPM134，无SFL变速。全部源统计校验及逐行原字段核对通过，无未识别记录。

谱面路径为`data/minecraftchunithm/songs/1100/master.json`，音频路径为`assets/minecraftchunithm/sounds/songs/1100/music.ogg`。测试命令：`/chunithm chart play 1100 10 false`。
