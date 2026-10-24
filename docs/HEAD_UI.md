# 头部原生控制界面

头部背面窗口现在使用专用客户端 Screen，不再显示箱子格子。布局按 `head-ui-concept-v2.png` 实现：原版浅灰色底、左右 Air、中央 16 段 Ground Slide、紧凑 Track 播放区、左 Music / 中 CHUNITHM / 右 Player，无八格轨道预览、进度条或底部说明栏。

按钮、边框、灯条和图标是独立绘制的像素 UI，不是整张效果图覆盖层。开始为绿色圆点，暂停为双竖线，结束为红色方块；三者是独立交互控件，具有禁用外观、短悬停说明、键盘焦点和读屏名称。禁用开始时圆点变灰；窗口会根据 GUI 可用空间统一缩放显示与鼠标命中区域。浅色面板上的深色文字关闭偏移阴影，避免双重笔画；提示延迟 350ms 出现。

已激活部件使用深灰面板（#303030）、浅色加粗标题和多层半透明像素光晕，未连接部件仍保持浅灰禁用样式。Air 为绿光、Music 为紫光、Player 为黄光；头部与 Track 面板为金色边缘光。CHUNITHM 标题为金色，其他激活标题为近白色，均不绘制偏移文字阴影。Ground Slide 的响应进一步增强对应色段及边框亮度。主窗口背景保留原版浅灰，Track 内三个按键的颜色、图标、绘制和可用规则不变。

顶栏标题使用白色加粗字体，横向放宽至 1.2 倍，保留原版白字阴影增强对比，并限制宽度以避开关闭键。Ground Slide 的 16 格按总宽度居中：每格 13 像素、间距 2 像素，总宽 238 像素，在 256 像素模块内左右各留 9 像素。

## 打开和使用

安装最新构建并重启客户端，已有注册完整的头部无需重新生成，右键背面入口窗口即可打开新界面。旧版本尚未注册的散装结构需重新生成或使用：

```mcfunction
/chunithm assemble 24
```

头部背面入口使用灰框、黑底与用户提供的 CHUNITHM Logo，保持原图 380:164 比例。外框等比放大为高 1 格、宽 5/3 格，中心仍在头部局部 (5.5, 2.3)，横跨第 4～6 列。边框正面与头部背面齐平，黑底和 Logo 位于深 1/8 格的真实凹槽内；六个相交方块的模型、碰撞与点击形状同步开孔，周围背面与正面保持完整。槽口整个范围均可右键，包含左右相邻方块。左右信息面板的入口暂不变。旧注册完整头部在区块加载后自动清理并重建入口显示实体，无需重建机台或修改面板数据；打开头部窗口也会刷新。Logo 原图位于 `textures/block/head/control_logo.png`。

Air 卡片内仍显示 Air；悬停标题按玩家站在 Ground Slide 面向轨道时的左右方向命名，因此窗口左侧为 Right Air，右侧为 Left Air。已连接的输入模块说明仅显示“已连接”，不再附加管线路径。

头部中间 CHUNITHM 悬停列出轨道三个接口以及 Music、Player 的状态，点击刷新。Music、Player 接口连接完整时分别显示紫色、黄色边框；按最新要求，头部界面中这两个卡片的点击暂时不触发任何动作，也不向服务端发送打开请求。服务端同时忽略相应的旧动作编号。侧面板自身的背面窗口仍可独立使用。未连接、损坏或未加载时置灰并显示原因。

Air、Ground Slide 默认使用真实部件链路和物理输入。Air 经 Ground Slide、完整轨道到达头部，左右独立检查；Ground Slide 的 16 路压感与其物理红色感应带同步。未连接时灰色禁用，完整连接无输入时保持低亮。实体触发 Air 高度通道时本侧绿光增强，Ground Slide 的 16 段按各自强度显示彩虹色，松开后短暂消退。中途断路立即清零，修复后自动恢复。Air 实体检测范围及五路 Provider 见 `AIR_SENSORS.md`。

Track 开始按钮需要完整轨道、连接的歌曲板、管理员权限，以及后端报告已加载的可播放谱面。暂停和结束不依赖歌曲板或谱面是否就绪，只需完整轨道与管理员权限；没有谱面或已经停止时也可点击，作为幂等控制请求处理。默认后端为空时暂停和结束安全地无操作，不会伪装成正在播放歌曲；仅开始键灰色。

## 输入接入

```java
HeadControlServices.setInputProvider((level, headPlacement) ->
    new HeadControlServices.Input(
        leftConnected, rightConnected, groundConnected,
        leftResponse, rightResponse, sixteenGroundResponses));
```

强度范围 0～1；必须提供长度为 16 的数组。非有限值归零，越界值钳制，数组防御性复制。未连接通道的输入即使有值也不会同步或发光。提供器在服务器线程调用，不应执行阻塞 I/O。状态通过原版菜单数据同步到观看窗口的客户端，不写入存档。

## 播放接入

```java
HeadControlServices.setPlaybackBackend(new HeadControlServices.PlaybackBackend() {
    public HeadControlServices.Transport snapshot(ServerLevel level, Placement head) {
        // ready 必须表示当前机器已有可播放的谱面，不只是菜单被打开。
        return new HeadControlServices.Transport(ready, playbackState);
    }
    public void execute(ServerLevel level, Placement head, HeadControlServices.Action action) {
        // START_OR_RESUME、PAUSE、STOP
        // 接入音乐/谱面播放时钟；按维度及 head 原点、朝向区分每台机器。
    }
});
```

播放状态：STOPPED / PLAYING / PAUSED / FINISHED。开始支持从停止/结束重新播放及从暂停继续，播放中禁止重复开始；暂停和结束在无谱面时依然可用。结束动作改为 STOP，不再是跳到谱面末尾；后端应将其实现为幂等停止/清理，暂停对已停止的会话也应安全处理。默认不修改现有随机 TAP 测试，也没有接入真实音乐或谱面计时。

按钮只发送动作编号，服务端重新验证当前菜单、玩家、距离、管理员权限、部件完整性和后端状态。开始按钮不能在 PLAYING 时重复启动；非法编号和不允许的动作被拒绝。正常状态以服务器同步结果为准，客户端不直接把自己设成播放状态。完整性与输入采用不同频率刷新，避免每个渲染帧扫描整条轨道。

## 版本更新与开发启动

Java 代码更新必须保存世界并**完全关闭 Minecraft 进程**后重新编译、启动；只回到标题页、重新进入世界或 F3+T 不会重新加载 Java 类。开发客户端会延迟从同一构建目录加载类，运行时覆盖 `.class` 文件会混用旧初始化器、旧 Screen 和新生成代码，可能在 Logo 尚未注册时中断整机生成。

`dev.ps1` 现在检测本项目的运行中客户端：编译或重复启动时会拒绝继续，要求先保存退出；不强行杀进程。头部/整机生成在修改世界之前检查控制入口初始化状态，旧混合运行状态不会再先留下半个头部。操作系统/IDE 直接编译也应遵守完全退出客户端后再编译的顺序。

当前启动日志会记录 `build=head-control-logo-2` 和 `control logo registered=true`，便于确认实际加载版本。历史失败留下的半成品可在原定位重新执行生成命令覆盖，或使用新的空地测试；修复不会自动删除旧建筑。

## 验证记录

- 构建和现有检查通过，新增输入数组长度/不可变性/非法数值处理以及播放按钮可用状态检查。
- 完全关闭旧客户端后，启动新 JVM，确认整机生成完成、五个接口连接、原生菜单打开，且无生成命令异常。
- 使用 Minecraft 自身的截图接口，实际检查白色加宽标题、居中十六格、无谱面按钮状态，以及头部背面黑底 Logo 已正常显示。截图位于开发目录 `run/screenshots/head-control-ui.png` 和 `head-control-logo.png`。
- Air 数据链路回归已覆盖四朝向、独立输入、共用上游与单侧故障隔离、旋转错误、恢复重连、16 路压感和实体检测/释放；原生菜单截图确认两侧 Air 与 Ground Slide 均为已连接样式。未连接的物理设备仍灰色。

源文件：公共 `HeadControlMenu.java`、`HeadControlServices.java`；客户端 `client/HeadControlScreen.java`。公共入口没有引用客户端类，可用于独立服务器。
