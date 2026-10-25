# Character avatar resource library

Player-character portraits are stored as Minecraft textures under:

```
assets/minecraftchunithm/textures/character/<four-digit-id>.png
```

The data-pack index is:

```
data/minecraftchunithm/characters/index.json
```

Each index entry keeps the four-digit character ID as a string so leading zeroes
are never lost. `texture` is the Minecraft texture resource ID without the
`textures/` prefix or `.png` suffix. `itemModel` points to a generated item-model
definition suitable for an item display. For example, character `0011` resolves
to `minecraftchunithm:character/0011` for both lookups.

Run `tools/import-character-avatars.py` with one or more
`CHU_UI_Character_NNNN_XX_XX.dds` files and the main resources directory. The
importer preserves the original 128×128 RGBA pixels, removes DDS/container
metadata, writes metadata-free PNG files, rejects duplicate IDs, and rebuilds a
stable ID-sorted index.

`CharacterLibrary.all(level)` enumerates the index. `find(level, "0011")` keeps
the explicit four-digit ID form, while `find(level, 11)` safely pads it to the
same key. Invalid or unknown IDs return `null`.
