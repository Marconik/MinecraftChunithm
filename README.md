# MinecraftChunithm
Play CHUNITHM(NEW and Later) in Minecraft

Minecraft Java 26.3 / Fabric 项目。已实现轨道模型、16 种宽度的 TAP、轨道生成、边界检查和按 BPM 运行的随机运动测试；尚未实现击打判定或音乐播放。

## 音符运动测试

右侧曲目信息面板：`/chunithm song` 独立生成，`/chunithm song_data <JSON>` 更新封面、曲名、创作者、TRACK、难度、定级、速度及镜像。详见 [曲目面板](docs/SONG_PANEL.md)。

只生成空轨道：`/chunithm track_empty 24`，长度支持 2～1024 格，省略长度默认为 24。生成轨道、边框和尾部金色判定线，不生成头部、音符或节拍线。起点中央为脚下方块，沿玩家朝向延伸，会替换对应范围方块；当前维度的运动测试会停止，并清除本次范围内的旧展示音符和判定线后重新添加一条判定线。

独立的轨道控制头部可用 `/chunithm head` 生成，尺寸和放置说明见 [轨道头部](docs/TRACK_HEAD.md)。

已加入 EX-Tap、Hold、Slide、Flick、Air、Air-Action 的属性定义与全部 16 种宽度的静态模型，详见 [音符说明](docs/NOTES.md)。在空地运行 `/function minecraftchunithm:note_showcase` 可查看六类模型。

在空地执行（会生成/替换脚下一层 9×24 格轨道）：

```mcfunction
/chunithm test 24 120 10
```

参数依次为长度、BPM、速度（格/秒）。`/chunithm test` 等价于上面的默认配置；`/chunithm test 32` 则使用 32 格长度、120 BPM、10 格/秒。BPM 范围 30～300，速度范围 0.25～20；预计同时显示的测试实体超过 512 个时会拒绝启动。

- 从 +Z 方向的起点端向终点端运动，TAP 沿运动方向长 0.5 格（原来的两倍），中央带横向白线。中心在起点内 0.25 格处出现，保证实体模型不伸到轨道后方。
- 120 BPM 每 0.5 游戏秒（10 tick）产生一个 TAP 和一条灰色四分音符节拍线，随机宽度覆盖 1～16 分区，位置始终限制在有效的 8 格宽区域。
- 10 格/秒每 tick 前进 0.5 格，相邻节拍线间隔 5 格。使用原版 Display 插值平滑位置，不受客户端 FPS 的逐帧累加误差影响。
- 金色判定线横跨有效 8 格，中心在末端前 0.5 格，线宽 0.09375 格，其外沿到轨道末端仍有约 0.453 格空隙。判定线仅用于显示，不含评分或输入判定。
- TAP 与灰线通过金色判定线后继续运动，中心到达轨道实际末端时先同步末端位置，再保留 2 tick 让客户端完成插值后删除，避免高速下看起来提前消失。位置固定在末端，不继续向轨道外移动；可能看见极短的末端停留。24 格轨道的行进距离为 23.75 格，10 格/秒时约 2.375 游戏秒；越过判定线后还会前进 0.5 格（约 0.05 秒）。
- 轨道只显示 7 条纵向分隔线，划分为 8 个一格宽区域；音符定位仍使用 16 个半格分区，16 种横向宽度保持不变。
- 两侧金色边框的亮边靠近轨道，向外侧渐变为深金色；沿轨道长度方向保持连续，不在每格重新开始渐变。
- 每个维度同时运行一个测试，再次启动会清除旧的运动实体。`/chunithm stop` 停止生成并清空运动音符与灰线，保留轨道及金色判定线。
- 轨道卸载、被破坏或服务器退出时停止测试；再次进入世界不会自动恢复，也不会保留停滞的运动音符。

这版使用游戏时间：正常 20 TPS 时与实际秒数一致，服务器卡顿或游戏暂停时会一起放慢/暂停。后续接入音乐后再改用音频时间轴。

已通过构建、120 BPM 节拍计算、10 格/秒位移、到线消失时机、10,000 次随机宽度/位置检查，以及客户端资源加载。开发自动测试可实际进入世界、生成轨道并运行测试，日志每 200 tick 输出生成与清除计数；电脑操作工具不可用时仍可通过此方式测试，但不替代画面观察。

自动进入已有的开发超平坦测试存档 `New World` 并运行默认运动测试：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\dev.ps1 runClient -PmotionQuickPlay
```

该选项会重建测试存档中 `(0,-61,0)` 开始的 9×24 区域，切换旁观模式，并将视角放到轨道终点上方。仅开发环境显式启用该参数时生效；普通启动和发布 JAR 不会自动生成。测试存档必须已存在。

## 轨道与 TAP

在开启命令的世界中执行（需要管理员命令权限）：

```mcfunction
/chunithm track 24
```

参数为长度，支持 2～1024 格，整个区域必须已加载。命令从执行位置向下一个方块层开始，向 +X 铺设 9 格宽、向 +Z 铺设指定长度，会替换该范围的方块。总宽为 9 格：左右边框各占 0.5 格，中间有效区域为 8 格、16 个半格分区。底部到轨道表面为 0.625 格，到金色边框顶部为 0.75 格；碰撞形状与这些高度一致。

TAP 在创造模式的建筑方块分类中，宽度为 1～16 分区（0.5～8 格）。右键轨道表面，点击的半格区域作为音符的低坐标起点。系统会检查同一行完整的 9 个轨道模块和朝向，拒绝放在边框上、缺损轨道上或横向越界；8 格宽 TAP 只能从有效区域最左端（低坐标端）放置。沿 Z 方向横向铺设时规则相同。

轨道模型尺寸已变更，旧版本生成的 8 格总宽轨道需要重新生成，旧展示音符也不会自动重新定位。静态展示实体可用下面命令清除，仅针对附近带模组展示标签的音符：

```mcfunction
/kill @e[type=minecraft:item_display,tag=minecraftchunithm.tap_preview,distance=..32]
```

在空地执行 `/function minecraftchunithm:model_showcase` 可生成 9×24 格轨道及居中的 16 种宽度样品。不要在同一位置反复执行，以免叠加展示实体。管理员直接使用原版 summon/data 命令可以绕过物品放置检查。

模型资源可通过 `node tools/generate-models.cjs` 重新生成，通过 `node tools/check-models.cjs` 检查尺寸。`build` 包含所有 16 种宽度在各半格槽位的边界检查。

## 开发环境

| 依赖 | 固定版本 |
| --- | --- |
| JDK | 25 |
| Minecraft | 26.3 |
| Fabric Loader | 0.19.5 |
| Fabric API | 0.163.0+26.3 |
| Fabric Loom | 1.17.21 |
| Gradle Wrapper | 9.6.0 |

构建结构参考 [Fabric 官方 26.3 示例](https://github.com/FabricMC/fabric-example-mod/tree/26.3)。
依赖由 Gradle 下载，无需手动把 Fabric API 放入源码目录。

### Windows 命令

在项目根目录执行：

```powershell
# 编译和打包
powershell -NoProfile -ExecutionPolicy Bypass -File .\dev.ps1 build

# 启动开发客户端
powershell -NoProfile -ExecutionPolicy Bypass -File .\dev.ps1 runClient

# 生成可供 IDE 浏览的 Minecraft 源码
powershell -NoProfile -ExecutionPolicy Bypass -File .\dev.ps1 genSources
```

`dev.ps1` 依次检查 `CHUNITHM_JAVA_HOME`、相邻的 `../runtime/jdk-25.0.4.1`、`JAVA_HOME`，选择 JDK 25。
它只为当前构建进程设置 Java，不修改系统 Java；Gradle 缓存位于已忽略的 `.gradle-user-home/`。
在其他电脑上可以设置：

```powershell
$env:CHUNITHM_JAVA_HOME = 'C:\path\to\jdk-25'
```

其他系统安装 JDK 25 后使用 `./gradlew build` 或 `./gradlew runClient`。
产物位于 `build/libs/`，安装测试选择不带 `-sources` 后缀的 JAR。
游戏测试实例需要 Minecraft 26.3、Fabric Loader 和适配 26.3 的 Fabric API。

### IntelliJ IDEA

打开本目录并导入 Gradle 项目。Project SDK 和 Gradle JVM 均选择 JDK 25。
如需复用脚本下载的依赖，将 IDE 的 Gradle user home 设置为本项目的 `.gradle-user-home` 绝对路径。
开发存档位于 `run/`，不会使用或提交现有游戏存档。

## 代码布局

- `src/main/java`：公共入口，后续加入轨道配置、谱面、播放状态及服务端逻辑。
- `src/client/java`：客户端入口，后续加入轨道/音符渲染、音频与界面。
- `src/main/resources/fabric.mod.json`：模组身份与依赖约束。
- `docs/PLAN.md`：首个可用原型的范围与验收标准。

公共代码不能直接引用客户端类，以便后续支持独立服务器。
当前未引入额外音频、动画或 GUI 库，也没有无用途的 Mixin。

## Git 同步

本地 `main` 跟踪 `origin/main`。同步是显式操作，不会自动上传正在编辑的文件。

```powershell
git pull --ff-only
git status
git add <本次修改的文件>
git commit -m "Describe the change"
git push
```

构建缓存、IDE 配置、运行目录和日志不会提交。
项目许可证待仓库所有者确定；没有复制原作音乐或图像资源。

生成锚点更新：`/chunithm track <长度>` 以脚下方块为起点行中央，向玩家朝向延伸；`/chunithm head` 以脚下方块沿朝向前进一格作为头部中间最下方块，正面朝玩家。站立方块按 floor(位置).below() 计算。普通启动不生成测试内容，保持空白测试世界请勿使用 QuickPlay 自动生成参数。
