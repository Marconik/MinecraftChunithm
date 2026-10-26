# One-command world cleanup

While the MOD is still installed, an operator can run:

```mcfunction
/chunithm uninstall
```

This is a destructive cleanup command. It immediately starts a server task;
there is no second confirmation. Keep the world running until the explicit
completion message appears, then save/exit, close Minecraft, and remove the
MOD JAR. Large worlds take longer and receive progress messages every five
seconds. Removed blocks become air; previous terrain is not reconstructed.

The task stops playback, closes cabinet menus, flushes the world, enumerates
existing region headers in all enabled dimensions, and scans saved block
palettes. It cleans every `minecraftchunithm` block, including broken,
unregistered and manually moved/placed blocks. Entity-containing chunks are
also loaded to remove the MOD's display entities, preview/judgment lines,
and dropped MOD items. Unrelated vanilla blocks/entities and other namespaces
are preserved. Player inventories, containers and world backups are outside
this block/entity cleanup command's scope.

The task uses up to 64 pending disk reads and eight temporary chunk-loading
tickets. These tickets are nonpersistent and distinct from `/forceload`;
existing forced-chunk settings are preserved. Blocks are changed through the
live Minecraft chunk/lighting APIs, not by rewriting live region files.
Normal cabinet generation and controls are unavailable during cleanup.

Only a successful scan deletes `data/minecraftchunithm/parts_*.json` and its
temporary registry files, clears the cached registrations and flushes the
world again. An I/O/load/save error produces an incomplete message instead
of claiming success. An interrupted task can be rerun with the MOD installed.
If saved custom dimensions are disabled, enable their provider before rerunning
so their content can be examined. Only explicitly matching registry filenames
are deleted; other save data is retained.

Development integration test (destructive only inside `build/air-input-test`):

```powershell
.\dev.ps1 runClient -PuninstallQuickPlay --offline
```

The test creates a full cabinet, unregistered MOD blocks in three dimensions,
an unloaded orphan block/display at negative coordinates, and vanilla bedrock
blocks/text displays as controls. It verifies cleanup, registry removal,
preservation of the controls and a second successful no-op uninstall.
The test saves its result to `build/air-input-test/uninstall-checks.json`.

Verified 2026-10-26: 12,321 saved chunks scanned in each pass; the first pass
removed 1,332 MOD blocks and 166 related entities across all three vanilla
dimensions. Unloaded unregistered content was removed, vanilla control blocks
and text displays survived, registry files disappeared, and the second pass
successfully removed zero blocks/entities.
