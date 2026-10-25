# Cabinet protection and five-song import

All 154 registered `minecraftchunithm` blocks use one shared property factory:
hardness 50, explosion resistance 1200 and correct-tool harvesting, matching
vanilla obsidian's mining strength and blast resistance. Existing geometry,
lighting and appearance remain intact. No cabinet block is registered as
flammable or ignitable by lava. The four obtainable track BlockItems are also
fire-resistant.

`minecraftchunithm:cabinet_blocks` is included in Minecraft's
`mineable/pickaxe` and `needs_diamond_tool` tags. The attack callback and
server-side break callback require a correctly tagged mining tool. Thus hand,
wood/stone/iron/gold picks and axes cannot clear a cabinet; diamond and
netherite picks can. The accidental-removal guard also applies in creative
mode. Right-click control windows and assembly commands remain available.
This is obsidian-level blast resistance, not immunity to arbitrary commands
or scripted world changes.

## Imported songs

| ID | Song | Imported charts |
| --- | --- | --- |
| 428 | Aleph-0 | MASTER 14.9, ULTIMA 15.4 |
| 618 | 業 -善なる神とこの世の悪について- | MASTER 15.6 |
| 2035 | XL TECHNO -More Dance Remix- | MASTER 14.5 |
| 2060 | 蜘蛛の糸 | MASTER 15.2 |
| 242 | Garakuta Doll Play (sasakure.UK clutter remix) | MASTER 15.6 |

CSV search and the local Music.xml identify ID 242 in A001 as the requested
Garakuta MASTER. ID 8024 in A000 is its WORLD'S END entry and is not imported
under a false MASTER label. Music, metadata and 300px covers are packaged for
all five songs; all music is decoded and validated stereo Ogg Vorbis.

The catalog now contains 23 songs and 27 imported charts.

## Compatibility additions

AHX is preserved as its own source command with target, duration, color and
original fields. It renders the existing Air Action beam and purple endpoints
without adding a green direction arrow, and supplies corresponding sound and
automatic-score events. It uses the same original musical clock and mirror
handling as other notes. The source shape was checked against the local files
and the [AirHold class and parser](https://github.com/Aeph1edur/C2SViewer/blob/main/src/notes/air.py).

The parser accepts legacy ALD records with an omitted color (default DEF),
and records with an extra trailing default field. All original fields remain
in `sourceFields`. An ALD's final sampled action is the last interval multiple
within its duration; its visual tail may extend further. This handles Spider's
Thread's one-tick visual tails with a six-tick action interval while retaining
the source final-note statistics. These changes pass statistics for all 27
source charts.

## Checks

```powershell
.\dev.ps1 build --offline
python tools/check-song-resources.py
.\dev.ps1 runClient -PinputQuickPlay -PscoreQuickPlay -PdurabilityQuickPlay --offline
```

The opt-in native test is confined to `build/air-input-test`. It examines every
registered cabinet block's properties and effective tool tags, checks rejection
of creative-mode hand/iron removal, triggers a real TNT-strength explosion on
an isolated test block, then removes it with a diamond pick. The original held
item and test block state are restored. It writes `cabinet-protection-checks.json`
and also runs the existing automatic score/display checks before closing.

Verified 2026-10-25: all 27 source charts passed their statistics, all 23 songs'
packaged audio/cover references passed validation, and 101,784 geometry frames
passed across both mirrors and speeds 3/10/20/30. Native checks passed for all
154 cabinet blocks, including real TNT survival and diamond removal, followed
by 187 automatic-score/display checks. The client saved and exited normally.
