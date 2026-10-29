# 构建发布包

当前目标环境：Minecraft Java 26.3、Fabric Loader 0.19.5 或更新兼容版本、Java 25，以及对应 Minecraft 版本的 Fabric API。项目验证使用 Fabric API 0.163.0+26.3。

保存并关闭当前开发测试客户端后，在项目根目录运行：

```powershell
.\dev.ps1 build '-Pversion=0.1.0' --offline
python tools/check-song-resources.py --jar build/libs/minecraft-chunithm-0.1.0.jar
python tools/check-decorative-lines.py
```

`-Pversion` 覆盖本次构建的版本，同时写入 JAR 文件名和 fabric.mod.json；gradle.properties 中的默认开发版本保持不变。离线构建要求依赖已经缓存；首次设置环境可去掉 `--offline`。

给玩家使用的文件是 `build/libs/minecraft-chunithm-0.1.0.jar`。`-sources.jar` 是源码包。将 MOD JAR 和匹配版本的 Fabric API 放入目标实例的 `mods` 文件夹，使用 Fabric Loader 启动。

本项目使用 Minecraft 26.3 与 `net.fabricmc.fabric-loom`。该版本没有混淆映射，发布使用 `jar` 任务的产物，无需 remapJar。参见 [Fabric 官方迁移说明](https://www.fabricmc.net/2026/03/14/261.html)。

发布包包含 46 首歌曲、52 张已导入谱面、音乐、封面及头像资源；游戏运行不需要原 HDD 目录。开发自动测试仅在开发环境并显式启用相应启动参数时执行。普通安装不会自动生成测试机台或修改测试存档。

当前演出限制：QZKago Requiem 的两条 DCM 控制只保留原参数，尚未执行其差速演出。当前得分为音符过线后的自动 Critical 计分，尚未实现玩家输入判定。
