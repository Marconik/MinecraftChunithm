# MinecraftChunithm
Play CHUNITHM(NEW and Later) in Minecraft

Minecraft Java 26.3 / Fabric 项目。当前是可编译的开发骨架，尚未实现轨道、音符或音乐播放。

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
