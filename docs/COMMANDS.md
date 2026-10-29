# 指令与提示

所有指令需要管理员命令权限。`[]` 为可选参数。

| 指令 | 用途 |
| --- | --- |
| `/chunithm assemble [length]` | 生成整机，默认轨道长度 24，范围 2～1024 |
| `/chunithm stop` | 卸载谱面并停止随机测试，清除播放显示 |
| `/chunithm uninstall` | 清理所有维度中的 MOD 方块和相关实体并保存 |
| `/chunithm chart load <resource> [speed] [mirror]` | 加载谱面 |
| `/chunithm chart play [resource] [speed] [mirror]` | 开始、继续，或加载指定谱面后播放 |
| `/chunithm chart pause` | 暂停 |
| `/chunithm chart stop` | 停止并重置，保留谱面 |
| `/chunithm chart seek <seconds>` | 跳转播放位置 |
| `/chunithm chart status` | 在操作栏查看状态；控制台执行则写入日志 |

速度默认 20，范围 0.25～30，镜像默认 false。数字歌曲 ID 使用 MASTER；ULTIMA 可使用带引号的资源路径，如 `"minecraftchunithm:songs/0333/ultima.json"`。指定 mirror 时需要同时指定 speed。

调试入口统一放在 `/chunithm debug` 下，内部生成与测试方法继续保留：

- `track_empty [length]`
- `head`、`ground_slide`、`air_sensors`、`nameplate`、`song`
- `head_data <critical> <justice> <attack> <miss> <maxCombo> <score>`
- `nameplate_data <json>`、`song_data <json>`
- `test`、`test <length>`、`test <length> <bpm> <speed>`（BPM 30～300）

删除了重复的 `track` 和 `chart resume` 入口。旧的顶层调试入口不再注册。

聊天框只发送生成确认，例如 `已生成整机，轨道长度为 24 格。`、`已生成头部。`。整机内部各部件生成不会重复发送确认；停止播放、更新数据和启动测试不再追加聊天提示。

错误及卸载进度改在操作栏显示，错误同时写入日志；卸载生命周期与进度也记录到日志中。原版命令解析和权限错误仍由 Minecraft 处理。确认操作栏或日志显示卸载完成后，再关闭游戏移除 MOD。
