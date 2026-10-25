# Temporary all-critical scoring

The current playback session awards JUSTICE CRITICAL automatically at each
scoring node's original judgment-line timestamp. It does not read player input
or implement timing windows. `AutoScoreTimeline` is separate from sound cues:
simultaneous notes each score, even though their sound is merged into one cue.

The user-selected temporary counting policy is visible arrival nodes:

- Tap, EX-Tap, Mine, Flick and each Air direction: one event.
- Hold: head and tail.
- Slide: root, visible SLD/SXD intermediate nodes and final tail. Invisible
  SLC/SXC path controls do not add events.
- Air Action: visible action nodes and final tail. Adjacent AHD segments share
  one boundary event; invisible ASC controls do not add events.
- ALD decoration (including NON bars), beat lines and speed markers:
  excluded.

This is not the original game's full continuous-sustain judgment schedule.
Source `T_NOTE_ALL` and `T_JUDGE_ALL` remain preserved metadata and are not used
as the denominator for this temporary policy. The denominator and numerator
both come from the same actual scoring-node list.

```text
score = floor(passedNodes * 1_010_000 / totalNodes)
```

Integer arithmetic applies flooring to the cumulative ratio, so the final
score is exactly 1,010,000. A chart with no scoring nodes stays at zero. The
critical counter and MAX COMBO both equal passedNodes; the other counters stay
zero. Original musical time controls counting: speed, mirror, reverse scroll,
stops and multiple visual crossings cannot duplicate score.

Playback behavior:

- Loading a chart, Stop and replay reset the counters, combo and progress.
- Pause freezes the current result; resume continues it.
- Seek recomputes the result at the target time, including backward seeks.
- Natural completion retains the final result.

`ChartPlayback.score(level)` exposes the current authoritative snapshot.
The connected head uses that snapshot to update existing TextDisplay and
BlockDisplay entities in place, preserving text style and avoiding per-note
facade respawns. Saved older head displays migrate once on their first update.

The existing progress gauge still fills one cell per 101,000 points, with
the bar showing progress to the next cell and turning gold from the third
cell onward. At 1,010,000, all ten cells are filled and the bar remains full.
Both the cells and the bar derive directly from `HeadDisplayData.score`.

Checks:

```powershell
.\dev.ps1 build --offline
.\dev.ps1 checkAutoScore --offline
.\dev.ps1 runClient -PinputQuickPlay -PscoreQuickPlay --offline
```

The native test uses only the isolated `build/air-input-test` save. It checks
actual text values, filled cell block states, serialized bar width and color,
display UUID stability, pause, backward seek, speed/mirror changes, stop/replay
and natural completion. It captures partial/full-score screenshots, writes
`automatic-score-checks.json`, saves and closes the client automatically.

Verified 2026-10-25: 500,500 exact score ratios, all 21 imported charts reaching
the exact maximum, the existing 70,700-frame playback regression, and 187 native
display checks passed. The updated fixture includes one Mine among 13 scoring nodes. The partial
screenshot shows 8 / 13 critical nodes, 621,538 points and matching progress;
the completed screenshot shows 13 / 13,
1,010,000 points, all ten cells and a full bar.

Mines count as physical notes: each MNE adds one event to both the total and
the critical/combo count when it reaches its timestamp. A Mine simultaneous
with a Tap counts twice. Melodiniq ULTIMA therefore includes all 814 mines
in its scoring denominator. No mine-specific input judgment or penalty is added.
