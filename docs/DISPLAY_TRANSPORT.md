# Random ribbon overshoot at the judgment stripe

The screenshots reported on 2026-10-27 are a display transport bug, not new
off-track chart records. The effect can vary with client frame/packet timing.

## Reproduction and cause

The previous renderer changed an ItemDisplay's entity position and its local
transformation independently, with one tick of vanilla interpolation on each.
At a clipping boundary, the same ribbon key could change from a 23-block segment
centered near the middle of the track into a very short segment near the end.
If the position arrived/interpolated ahead of the shrink, the client drew the
old long segment around the new end position. Replaying Melodiniq ULTIMA found
an approximately 10.4-block extension beyond the old 24-block display viewport.

Minecraft 26.3's `Display.setTransformation` also decomposes affine matrices
into translation, two quaternion rotations and scale. The client interpolates
these decomposed values rather than the original shear matrix. Thin/short,
strongly sheared segments can acquire intermediate geometry different from
either safe endpoint; the diagnostic also observed reconstruction error.
Server-side checks of `Spec.z ± Spec.sz/2` could not detect those client frames.

There were two additional issues: clipping used the physical track end, half a
block after the judgment stripe, and disappearing body fragments were retained
for two ticks while their complete geometry was moved to an edge. That retention
was intended for small note caps, not source/tessellation fragments.

## Fix

- Chart displays keep a fixed world anchor at the track origin. The complete
  pose is synchronized atomically as `minecraftchunithm:chart_pose` on the display
  item: translation, scale, shear/lift, model depth, viewport end and frame number.
- Only these marked chart displays use the client renderer hook. Ordinary
  Minecraft displays, cabinet labels and other mods' displays retain vanilla rendering.
- The client interpolates the complete affine pose with one progress value,
  then passes the raw matrix to rendering. No quaternion/SVD interpolation or
  independent entity teleport animation is used for chart geometry. Movement
  remains interpolated between server ticks.
- A corner's position is a linear combination of matrix entries. Interpolating
  complete affine matrices therefore interpolates each corner between its safe
  endpoints; it cannot produce the old extension outside the viewport.
- Body fragments are removed as soon as their clipped geometry disappears.
  Small note caps keep their two-tick endpoint delivery, wholly inside the stripe.
- Caps, Air/ALD lines and sustain bodies clip at `RhythmTiming.judgment(length)`.
  Source timing, scoring, sound cues, mirrors and the raised outside deck stay intact.
- Culling bounds include geometry relative to the fixed anchor. Serialized
  poses remain readable after save; the existing chart-entity cleanup handles them.

## Checks

```powershell
.\dev.ps1 checkDisplayTransport --offline
.\dev.ps1 build --offline
.\dev.ps1 runClient -PoffTrackQuickPlay --offline
```

The transport regression deliberately reproduces the unsafe independent/SVD
interpolation and compares it with the new complete affine matrices. The current
sample checks 112,791 intermediate ribbon matrices with no judgment-line escape,
including network/save codec round-trips. The native test covers normal and
mirrored passages, including the late-song shrink transition, and asserts the
bounds inside the actual client renderer on every rendered chart subframe.
The result includes `clientRenderSubframes` in
`build/air-input-test/melodiniq-off-track-checks.json`.

Verified 2026-10-27: 20 native passages (10 per mirror), including the passage
at approximately 170.55 seconds / Critical 4286 shown in the report. The actual
client renderer checked 262,349 display subframes without a viewport escape.
The paired passage screenshots are `melodiniq-off-track-8.png` and
`melodiniq-off-track-18.png`; the colored end-section lines are captured in
`melodiniq-off-track-9.png` and `melodiniq-off-track-19.png`.
