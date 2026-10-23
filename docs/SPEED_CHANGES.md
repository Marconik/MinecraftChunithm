# 变速标记线

音游中的视觉滚动倍率变化称为“变速”。歌曲BPM事件与视觉变速事件独立：BPM参与音乐时间换算，SFL进入scrollEvents，不能通过暂停歌曲时钟实现视觉停止。

## 外观与音符定义

- `NoteDefinition.Kind.SPEED_CHANGE`：瞬时的视觉标记。
- 固定lane=0、width=16，覆盖8格有效轨道表面；两侧金色边框不计入覆盖宽度。
- 紫色细线，无自发光，采用环境光照与普通明暗变化；沿行进方向深度0.03125格，与节拍线采用相同的薄片高度。静态预览不覆盖为满亮度，未来动态渲染也须保留环境光照。
- durationMs=0、direction=NONE、parentId为空、nodes为空。
- `judged()`为false，禁止进入判定、连击、分数、MISS和总音符数量计算。

资源/物品ID为`minecraftchunithm:speed_change_line`。模型由`tools/generate-models.cjs`生成；创造模式分类可找到“变速标记线（无判定）”，也可取得：

```mcfunction
/give @s minecraftchunithm:speed_change_line
```

它使用现有模型物品的静态检查放置方式。完整宽度预览需要从有效轨道横向低坐标端放置，以便通过既有边界检查。

## 转换规则

`tools/analyze-c2s.py`在分析结果中输出`speedChangeMarkers`。对每条SFL：

1. 在startTick生成START边界。
2. 在endTick=startTick+durationTicks生成END边界。
3. 使用BPM时间轴换算timeMs；线在该歌曲时间通过判定线，同时应用对应变速边界。
4. 同一tick的边界合并为一条线，但transitions保留所有关联事件；相邻区间同一时刻先结束前段，再开始后段。
5. 生成标记单独存放，不增加原始C2S的T_NOTE/T_REC/T_JUDGE统计。

示例字段：

```json
{
  "id": "speed_change_27264",
  "kind": "SPEED_CHANGE",
  "tick": 27264,
  "timeMs": 85200.0,
  "lane": 0,
  "width": 16,
  "durationMs": 0,
  "direction": "NONE",
  "parentId": "",
  "nodes": [],
  "judged": false,
  "countsForCombo": false,
  "countsForScore": false,
  "model": "minecraftchunithm:speed_change_line",
  "motion": "BASE_SPEED",
  "transitions": [{"eventId": "sfl_line_21", "phase": "START", "sourceLine": 21}]
}
```

END表示结束某个区间；发生重叠时不能简单地把倍率无条件设为1，应由未来的变速控制器根据剩余事件求当前状态。事件ID用于关联原始scrollEvents，不把倍率伪装成音符宽度或判定参数。当前解析器的SFL区间仍以原始duration描述；重叠效果的完整原游戏语义待实际播放器验证。

## 0倍速停止时的恢复提示

标记采用独立的基础流速移动，`motion=BASE_SPEED`，避免END线与普通音符一起停在远处，无法到达判定线。未来播放器应使用：

```text
markerZ = judgmentZ + baseSpeed * (songTimeMs - markerTimeMs) / 1000
```

这使开始和结束紫线都准时通过判定线；普通音符使用实际视觉变速映射。变速按歌曲时钟应用，不通过实体碰撞触发。暂停/恢复、跳转时应由歌曲时间重建当前状态，避免重复触发。

## 已生成的怒槌MASTER标记

| 开始紫线 | 结束紫线 | 原SFL倍率 |
| ---: | ---: | ---: |
| 85200ms | 85500ms | 0 |
| 86400ms | 86700ms | 0 |
| 87600ms | 87900ms | 0 |

共6条，无判定。ID103没有SFL，所以生成0条。两首原有逻辑音符和判定统计保持一致。

验证包括：紫线实际宽度与细线几何、无自发光材质、Java语义与宽度约束、无判定标记、相邻/重叠事件合并、停止区间恢复边界、两首样本的统计回归。

MOD类型、模型与转换标记现已接入`ChartPlayback`可视播放后端，支持怒槌MASTER的三段停止效果和六条紫线，详见[呈现模块](CHART_PLAYBACK.md)。现有随机TAP运动测试仍独立保留。
