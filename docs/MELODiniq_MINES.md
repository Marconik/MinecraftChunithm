# Melodiniq / MNE

Song 2999 includes its metadata, 300px cover, stereo 48 kHz Vorbis audio
(181.013333 seconds), MASTER 15.6 and ULTIMA 16.0. MASTER contains 3,333
source records and no MNE; ULTIMA contains 6,166 records, including 814 MNE.
Both are selectable from the song panel.

## Mine rendering and sound

`MNE measure offset lane width` is an instantaneous note. It uses `mine_1`
through `mine_16`, with blue/violet emissive geometry and a translucent halo.
Its core's side cross-section is 3.5 x 3.5 model pixels (0.21875 blocks),
slightly taller than Tap's approximately 0.133-block height. Its width follows
the source lane count; its core remains square from the side at every width.
The halo stays inside the note's horizontal lane footprint.

MNE is included in `NoteCueTimeline` at its original musical timestamp. It
shares Tap's 0.2-second TAP CRITICAL sample, 20% volume and paired Air Sensor
emitters. Simultaneous notes share one cue; a later cue interrupts the earlier
one. Visual speed, reversal and mirror do not change its sound timestamp.
`NoteDefinition.Kind.MINE.judged()` is true: mines count as physical notes in
the temporary all-critical score, critical counter and combo. Mine-specific
input judgment and penalty behavior are not implemented.

ULTIMA also uses 86 `VLT` decoration records. A violet emissive line model is
registered for that color, using the same constant color and halo as other ALD.

## Off-track motion

ULTIMA has 2,559 records whose start or end coordinates are outside the track;
source horizontal extents reach -16 through 32. The analysis retains them
unchanged and resolves original path links, Air attachments and scroll groups
before export. Off-track playback is now implemented; see
[parameters, examples and checks](MELODiniq_OFF_TRACK.md).

Each affected JSON note retains `sourceFields`, `sourceLane`, and (for paths)
`sourceEndLane`, plus `sourceOutOfBounds: true`. Widths and musical timestamps
remain unchanged. `lane` and `endLane` now retain the source coordinates;
the root `outOfBoundsPolicy` is `PRESERVE_SOURCE_COORDINATES`. No source
records are dropped. Horizontal excursions, tapered paths and source heights
are rendered without moving outside notes onto a track edge.

A coincident Slide junction has one incoming path and two outgoing paths.
The extra start is retained as a new head in source order; the resulting 176
Slide roots match `T_NOTE_SLD`, and the logical and family counts match the
source statistics. All 21 imported source charts also pass those checks.

The format reference is [Chunithm-Research Charting.md](https://github.com/beer-psi/Chunithm-Research/blob/main/Charting.md).
The import's counts and timings are checked against the supplied local files.

## Rebuild and checks

```powershell
python tools/generate-mine-models.py
node tools/decorative-line-models.cjs
python tools/check-mine-conversion.py
.\dev.ps1 build --offline
python tools/check-song-resources.py
.\dev.ps1 runClient -PinputQuickPlay -PmineQuickPlay --offline
```

The opt-in native test uses `build/air-input-test`, checks an isolated MNE-only
cue chart, plays both imported difficulties, checks real emissive mine displays
in normal and mirrored playback, verifies stereo music and paired native cue
streams, takes screenshots and exits automatically. It does not use the normal
development save. Results and screenshots are placed in that test directory.

Verified on 2026-10-24: the MNE-only fixture emitted exactly five native paired
cues, including four replacements. The real imported charts played with stereo
rail music; normal ULTIMA produced 1,200 mine display observations at speed 3,
and mirrored ULTIMA produced 129 at speed 20. Pause stopped both music and cues.
The test closed successfully and wrote `melodiniq-mine-checks.json` and three
screenshots. Offline geometry checks covered 70,700 frames over 21 charts at
speeds 3/10/20/30 and both mirrors, peaking at 2,354 visible displays.

To play manually near a complete registered track:

```mcfunction
/chunithm chart play 2999 20 false
/chunithm chart play "minecraftchunithm:songs/2999/ultima.json" 20 false
```
