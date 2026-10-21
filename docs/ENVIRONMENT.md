# 开发环境检查（2026-10-20）

- Git：2.44.0.windows.1；本地 main 跟踪 origin/main。
- 构建 JDK：25.0.4.1；本机位置为项目外相邻的 runtime/jdk-25.0.4.1。
- 系统默认 java 命令指向 Java 8，使用 dev.ps1 绕开，不修改全局环境。
- Gradle 9.6.0、Loom 1.17.21、Fabric Loader 0.19.5、Fabric API 0.163.0+26.3 已实际解析。
- build：成功，公共入口和客户端入口均编译，生成普通 JAR 和源码 JAR。
- runClient：日志确认 Minecraft 26.3 启动，两个模组入口初始化，窗口与音频引擎创建成功。
- 尚未验证进入世界、轨道功能或独立服务器；当前没有玩法实现和自动化玩法测试。
- 未确认 IntelliJ IDEA 已安装；可在 IDEA 中打开根目录，选择 JDK 25 与 Gradle Wrapper。

## 开发启动日志中的环境信息

开发客户端使用测试身份，用户属性和 Realms 请求出现 401/认证错误，不影响本次模组初始化。
本机 Windows 性能计数器信息读取失败，但游戏继续启动；未修改系统注册表。
当前日志显示实际使用 Intel Iris Xe 图形设备；之后评估轨道渲染性能时需记录这一点。

## 构建注意

请分别执行 build 和 genSources。当前 Loom / Gradle 组合将二者放在同一次调用中时会报告缓存产物的隐式任务依赖问题。
源码生成不是每次构建的必要步骤。
