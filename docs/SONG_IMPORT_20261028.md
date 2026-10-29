# 追加九首歌曲

新增 9 首歌曲、10 张谱面。元数据、300×300 无元数据 PNG 封面、立体声 Ogg Vorbis 音频与可播放 JSON 谱面均已放入 MOD 资源；曲库索引更新为 38 首、44 张谱面。

| ID | 歌曲 | MASTER 定数 | ULTIMA 定数 | 分类 |
| --- | --- | ---: | ---: | --- |
| 333 | Oshama Scramble! | 14.0 | 15.0 | ゲキマイ |
| 1058 | the EmpErroR | 14.9 | — | ゲキマイ |
| 2968 | PANDORA PARADOXXX | 15.6 | — | ゲキマイ |
| 2320 | エータ・ベータ・イータ | 14.8 | — | ゲキマイ |
| 2229 | QZKago Requiem | 15.0 | — | ゲキマイ |
| 2477 | sølips | 15.3 | — | ゲキマイ |
| 2218 | Re：End of a Dream | 15.1 | — | VARIETY |
| 2140 | ピアノ協奏曲第１番”蠍火” | 15.5 | — | VARIETY |
| 2826 | ニルヴの心臓 | 15.0 | — | VARIETY |

“—”表示当前 Music.xml 没有启用的 ULTIMA，不生成虚构谱面。谱面身份和难度以 Music.xml 映射为准；部分源 C2S 的 MUSIC、DIFFICULT 头字段不是实际曲目 ID 或难度。

## 转换兼容

- ニルヴの心臓 使用 `RESOLUTION=384`、`CLK_DEF=576`。取消将 CLK_DEF 限制为 0/384 的检查，保留原值；时间换算继续使用 RESOLUTION。首音符 2812.5 ms、末音符 148125 ms 与源统计相差不足 1 ms。
- QZKago Requiem 的两条 DCM 原始行、字段及起止时间保存在 `sourceDynamicScrollEvents`。该控制与全局 SFL 不同，当前渲染器尚不执行它；元数据及谱面 `visualWarnings` 均明确标记此限制。其原始定义参见 [Chunithm-Research 的 DCM 记录](https://github.com/beer-psi/Chunithm-Research/blob/main/Charting.md#dcm)。没有将 DCM 改写成全局变速，也没有删除这些控制。
- 音符、长条、空中路径、差速配置及源字段沿用现有转换规则；音频保留时间零点。所有 HDD 源文件只读使用。

## 重建

对每个 Music.xml 启用的 MASTER/ULTIMA 源文件运行 `tools/analyze-c2s.py`，输出 `build/chart-analysis/<ID>_03.analysis.json` 或 `_04.analysis.json`。随后依次执行已有音频转换、资源打包及谱面导出工具：

```powershell
python tools/convert-hdd-song-audio.py 333 --index build/hdd-music-index-with-audio.csv
python tools/package-hdd-songs.py 333 --difficulties MASTER ULTIMA
python tools/export-playable-charts.py 333 --difficulties MASTER ULTIMA
```

其余八首仅传入 `--difficulties MASTER`。已存在音频时先核对转换报告，无需重复转码。

## 后续追加八首 MASTER

本轮请求中的 1086「祈 -我ら神祖と共に歩む者なり-」已存在，核对后保留原 MASTER 15.7 与歌曲资源，不重复导入。下列八首新增元数据、封面、立体声音乐及 MASTER。当前本地 Music.xml 中，这九首均没有启用的 ULTIMA。

| ID | 歌曲 | MASTER 定数 | 分类 |
| --- | --- | ---: | --- |
| 2891 | 創 -汝ら新世界へ歩む者なり- | 15.7 | ORIGINAL |
| 2589 | ÅMARA (大未来電脳) | 14.8 | niconico |
| 2582 | Ultimate Force | 15.6 | ORIGINAL |
| 2660 | What's up? Pop! | 15.3 | niconico |
| 2629 | 壱雫空 | 14.0 | POPS & ANIME |
| 2606 | リ・フィクション・O | 15.3 | ORIGINAL |
| 2641 | マシンガンポエムドール | 15.1 | niconico |
| 2718 | I Wanna | 15.2 | POPS & ANIME |

曲库索引现为 46 首歌曲、52 张已导入谱面。分类按钮从相同的元数据读取信息，无需为新曲另行修改窗口代码。

ÅMARA 和壱雫空分别包含 29 条、4 条 `LIM` 装饰线。本轮补齐 LIM 黄绿色的材质、模型、物品注册及谱面颜色校验；保持纯色 RGB，透明度、交叉薄片几何和荧光效果均与已有 Air/ALD 共用规则。

創 和 I Wanna 的源空中演出高度分别可达 91、72.6。加载器的源高度上限由 32 调整到 128，仍拒绝负数、非有限数和超限值；转换保留原高度，渲染继续按源单位 ×0.2 映射，不压缩这些演出。
