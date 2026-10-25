# Music 三曲队列窗口

从头部控制界面的已连接Music卡片打开，沿用浅灰机框、深灰内容区、紫色高亮和原生像素按钮。窗口仍属于同一个头部菜单，不会切换为物品栏。右上角仅保留X，关闭整个窗口；Esc回到设备控制。

歌曲面板背面已提供独立的三曲选曲窗口，使用SELECT进入歌曲与难度目录；详见`SONG_SELECTOR_UI.md`。TRACK数字的横向位置按原生位图字形的实际留白补偿，避免大字号数字偏左。

## 使用

- 三行分别对应TRACK 1、2、3，采用白框洋红底的小方形序号标签，TRACK数字使用大字号。显示封面、曲名、作者，以及同一彩色标签内的难度和粗略等级（例如MASTER 14+）；不显示内部小数定数。标签向右加宽，难度与等级分别采用固定左对齐起点。曲名字号增大，过长的曲名、作者用省略号截断，鼠标悬停仅查看完整曲名与作者，不显示难度或定数。
- 右侧仅用文字显示PLAYED或READY，不放置状态或换曲按钮。队列来自已连接歌曲面板的输入；未输入的位置显示浅灰底、红框及左上至右下红斜线的封面占位图，曲名和作者均为No Data，难度标签为无文字的灰色框，不显示游玩状态。占位仅用于UI，不写入歌曲输入。目前九首均只有MASTER；不自动填充示例曲目。未导入谱面的自定义曲目仍显示输入的元数据，但不会因此变成可播放谱面。
- SPEED默认20.00，每次增减0.25方块/秒，范围0.25～30.00；到达上下限禁用对应按钮。MIRROR切换ON/OFF。新设置保存到服务端，作用于该头部连接轨道的已选谱面；播放/暂停中保留当前曲目时间，尚未开始时重新计算预滚时间。旧机台保留已保存的流速。
- SPEED按钮均为浅灰背景，上箭头使用绿色，下箭头使用红色。MIRROR关闭时为深灰底、浅灰OFF，开启时为浅灰底、白色ON。设置修改立即刷新世界中歌曲面板的SPEED/MIRROR显示，并应用于该机台已加载的谱面。
- 配置曲目后准备第一首未播谱面，回到主控制界面即可开始。播放完成后对应行变为PLAYED；再次开始时准备下一首未播曲目（存在时），不自动连播。重复歌曲的已播标记独立保存。
- 播放中锁定换曲，暂停后可以修改；修改其他位置不会重置仍然有效的当前暂停谱面。速度和镜像仍可修改。设置若导致当前可见实体超过既有上限，会拒绝修改并保留之前的设置。
- 无管理员权限可查看，不能修改。面板断连时显示OFFLINE并禁用设置；伪造按钮包仍由服务端复核权限、菜单所有者、距离、结构和连接状态。

曲目输入随歌曲面板保存到世界`data/minecraftchunithm/parts_<维度>.json`的`songInputs`字段，谱面标识、已播标记、速度和镜像保存在该面板的`musicQueue`字段。头部只读取连接面板的数据，不回填旧头部队列。旧面板有真实单曲输入时迁移到其TRACK位置；默认NO TRACK保持空槽。重新打开头部时恢复待播谱面，但不会自动开始播放。同维度仍沿用一个谱面呈现会话的限制，准备队列不会中断另一台正在播放/暂停的机台。

机台右侧歌曲面板同步当前曲目、TRACK序号、速度和镜像。补充了物品图集对`textures/song/`的引用，以便已有的封面模型能够显示。

## 程序接口

- `SongLibrary.charts(level)`：读取数据包索引和元数据，返回真实可加载的难度、显示等级、定数、封面和谱面资源；按服务端ResourceManager缓存，数据包重新加载时使用新目录。
- `SongPanel.update(level, placement, data)`：向歌曲面板输入一首曲目，根据TRACK 1～3写入对应位置；空曲名清空该位置。输入中速度/镜像同步为机台设置。已有提供器和`/chunithm song_data`单曲JSON沿用此接口。
- `SongPanel.setQueue(level, placement, SongQueueData inputs, speed, mirror)`：一次替换三曲输入；`SongQueueData.empty().withSlot(0, data)`构造稀疏队列，null槽为空，`SongQueueData.empty()`清空全队列。曲目关联到已导入谱面时可用于播放。
- `MusicQueue.setChoice(level, headPart, slot, choice)`：按歌曲ID/难度写入已连接歌曲面板，再由头部读取；`MusicQueueData.Choice.empty()`可清空位置。
- `MusicQueue.settings(level, headPart, data)`：保存速度/镜像并应用于现有谱面。
- `MusicQueue.snapshot(...)`：生成三行服务端状态。`HeadControlMenu`沿用原生按钮包接收操作，只额外用一个有长度限制、带containerId的客户端快照包传送文字和封面路径。
- `ChartPlayback.loadSong/configure/queueStatus`：连接现有谱面后端，不在界面里复制一套播放时钟。

使用既有谱面播放模块，并通过轨道边框双声道后端同步乐曲（见 `RAIL_AUDIO.md`）；不包含输入判定或评分逻辑。

## 验证

`dev.ps1 build --offline`运行九首谱面回归检查与MusicQueueCheck：三槽约束、速度0.25步长与上下限、镜像/重复歌曲已播标记保存、客户端快照JSON往返、面板输入优先于目录元数据、稀疏/空队列、粗略等级及面板设置显示同步。

`dev.ps1 runClient -PmusicUiPreview --offline`是显式开发预览：在标题界面调用生产视图，以内置歌曲元数据截取头部有曲目/空队列，以及歌曲面板的初始、目录和锁定画面后自动退出，不打开或修改世界。头部截图在`run/screenshots/music-queue-ui.png`和`music-queue-empty.png`；歌曲面板截图见`SONG_SELECTOR_UI.md`。
