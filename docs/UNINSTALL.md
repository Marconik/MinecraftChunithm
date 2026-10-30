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

## Leftover display repair

The facades, score text, progress fill and connection sockets are vanilla
`text_display`, `item_display` and `block_display` entities. They have no normal
block collision or mining behaviour; Display rejects normal attack damage even
when its saved `Invulnerable` field is false. Many use vanilla paper/concrete,
so their `minecraftchunithm.*` ownership tags and custom item models identify
them independently of the material or the presence of cabinet blocks.

Cleanup now snapshots the native entity section storage, including hidden and
pending-unload entities omitted by `ServerLevel.getAllEntities()`. It preserves
players, unrelated vanilla entities and other namespaces. After the removal
pass it flushes through Minecraft's save APIs and asynchronously rereads every
saved entity chunk. A late residual triggers another live cleanup and readback;
an unreadable or persistently dirty chunk reports an incomplete result and
retains the part registrations instead of claiming success.

To repair an already dismantled cabinet, install the fixed MOD and run
`/chunithm uninstall` again. The original cabinet blocks and registrations are
not required for detecting orphan displays. Wait for the completed-and-saved
message before exiting and removing the MOD. This clears world-wide MOD
content and does not reconstruct terrain.

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

Verified 2026-10-30: the extended integration check reproduced three hidden
displays absent from `getAllEntities()`, removed 614 MOD blocks and 338 related
entities across three dimensions, and completed a second no-op uninstall.
Unloaded orphans and vanilla controls passed. After client shutdown, a separate
read of 109 active entity records across 13 entity region files found no MOD
ownership tags or model references. Test output is in
`build/air-input-test/uninstall-checks.json` and
`build/uninstall-analysis/post-shutdown-verification.json`.
