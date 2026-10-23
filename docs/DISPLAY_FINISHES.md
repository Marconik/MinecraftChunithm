# Cabinet material finishes

- The two track rails use a mirrored inner-bright gold gradient. Two narrow specular bands, shaded lower edges and subtle seamless grain add a polished-metal finish while preserving the original rail dimensions and night-time brightness.
- `DisplayFinish.polish` places a thin transparent quad over a portrait, song cover, progress bar or progress cell. It uses a soft diagonal highlight, a small bright bevel and a darker lower/right edge. Imported images and blue/gold progress colors remain visible underneath.
- The progress-cell coating uses the same shear as the cell. Coatings carry their parent's display tag, so updating a panel removes the old coating together with its artwork.
- MAX COMBO, SCORE and both values retain their dark font and layout, surrounded by two full-bright white outline rings with decreasing opacity. Score formatting remains comma-separated.

These are native Minecraft textures and transparent highlights. They do not require a shader pack, and they do not implement real-time reflections or bloom.

Generate and verify resources:

```powershell
node tools/generate-models.cjs
node tools/generate-display-finishes.cjs
node tools/check-models.cjs
node tools/check-display-finishes.cjs
```

After a full client restart, existing track blocks pick up the gold texture. Regenerate the machine with `/chunithm assemble 24` to create the portrait/cover/meter coatings and text halos, or refresh each panel through its existing data update API.
