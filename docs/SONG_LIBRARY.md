# 内置歌曲资源

现已打包ID103、180、258、465、697、1100、2241、2354、2475的元数据、封面、Ogg Vorbis音频和MASTER谱面。

## 文件布局

相对于`src/main/resources`：

```text
data/minecraftchunithm/songs/
  index.json
  0103/song.json
  0103/master.json
  0180/song.json
  0180/master.json
  <四位歌曲ID>/song.json
  <四位歌曲ID>/master.json
assets/minecraftchunithm/
  sounds.json
  sounds/songs/0103/music.ogg
  sounds/songs/0180/music.ogg
  textures/song/0103/cover.png
  textures/song/0180/cover.png
  items/cover/0103.json
  items/cover/0180.json
  models/item/cover_0103.json
  models/item/cover_0180.json
```

元数据在data目录，客户端音频、封面在assets目录；JSON中的资源ID使用namespace:path格式，无本机E盘路径依赖。`index.json`提供歌曲元数据入口，供后续歌曲库加载器使用。

索引条目包含`id`、`title`（曲名）、`difficulties`（已导入谱面的难度名称列表，按difficultyId排序）和`metadata`。`difficulties`只列出imported=true且谱面资源文件实际存在的难度；只有Music.xml难度描述、尚未导入谱面的条目不列入。目前九首均为`["MASTER"]`。

`package-hdd-songs.py`和`export-playable-charts.py`完成后自动刷新索引，也可单独运行`python -B tools/song_catalog.py`从所有歌曲目录的song.json重建。

| ID | 曲名 | 曲作者 | 默认BPM | 音频时长 | MASTER内部定级 |
| --- | --- | --- | ---: | ---: | ---: |
| 103 | エンドマークに希望と涙を添えて | cosMo＠暴走P | 257 | 170.901333秒 | 15.0 |
| 180 | 怒槌 | 光吉猛修 | 200 | 150.336秒 | 15.4 |
| 1100 | TECHNOPOLIS 2085 | PRASTIK DANCEFLOOR | 134 | 138.816秒 | 14.5 |

新增六首的转换统计、特殊元素及命令见[批量歌曲导入](SONG_IMPORT_BATCH.md)。各首音频和封面均使用同样的四位ID目录。

九首音频均为48kHz双声道Vorbis。保留原始时间零点；audio.offsetMs初始为0，没有删除开头或结尾音频。sounds.json设置stream=true。

## song.json字段

- schemaVersion、id、title、artist、sortName、genres、releaseDate、defaultBpm：歌曲公共信息。
- cover.texture：完整PNG资源ID；cover.itemModel：歌曲面板可用的物品模型ID，例如`minecraftchunithm:cover/0180`。
- audio：声音事件ID、完整音频资源ID、编码、采样率、声道、时长、流式设置、偏移量。
- charts：Music.xml中启用的难度条目；保留difficultyId、level、levelDecimal、internalLevel、源文件名。MASTER额外记录已分析的谱师、首尾时间、音符/判定数、变速标记数。
- source：来源包和歌曲目录名，用于追踪导入来源，不包含个人绝对路径。

`notesDesigner`是谱师，`artist`是曲作者，不能混用。`levelDecimal=40`表示内部定级小数部分0.40，不应直接拼成显示等级“15+”。九首MASTER已通过`export-playable-charts.py`导出至`songs/<四位ID>/master.json`，与歌曲元数据存放在同一目录。chartResource已有资源ID、imported=true，可用于[轨道谱面呈现](CHART_PLAYBACK.md)。

MASTER以外的难度只导入Music.xml中已有的描述，未读取/转换其谱面本体；缺失谱师用null表示，不猜测填充。这些难度chartResource为null、imported=false。MASTER现可进行可视播放；判定、评分及音画同步后端尚未完成。

声音资源ID：`minecraftchunithm:music.0103`与`minecraftchunithm:music.0180`。sounds.json中的name省略sounds/前缀与.ogg后缀；metadata.audio.resource则是完整资源文件ID，两者用途不同。

## 重复生成

先用`tools/convert-hdd-song-audio.py`在build/audio-tests下完成音频转换，用`tools/analyze-c2s.py`生成MASTER分析数据。然后运行：

```powershell
python -B tools/package-hdd-songs.py 103 180
```

脚本校验XML歌曲身份、MASTER身份、Vorbis编码、音频时长和完整解码、封面尺寸与元数据；预检查本次指定的歌曲后写入资源目录，复制音频/封面后逐字节比较。HCA中间文件和本地转换报告保留在build，不打包进MOD。

歌曲选择界面、自动填充面板、音频同步仍待接入；现可通过命令加载MASTER进行轨道呈现。
