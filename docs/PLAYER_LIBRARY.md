# Player information library

Player profiles are data-pack resources stored in:

```
data/minecraftchunithm/players/index.json
```

The version-1 schema stores `id` as an exact 16-digit string, `name`, the
four-digit `characterId`, integer `level` (1–99), two-decimal `rating`, `title`,
`titleColor` (`SILVER` or `GOLD`), and `team`. Keeping IDs and character IDs as
strings prevents leading-zero or JSON-number precision loss.

`PlayerLibrary.all(level)` returns the typed profiles and `find(level, id)`
looks up an exact account ID. The default `PanelServices` player provider now
uses `PlayerLibrary.displayChoices`, resolves every character through
`CharacterLibrary`, and turns it into the existing `NameDisplayData` used by
the player-information panel.

The initial bundled profile is:

| ID | Name | Character | Level | Rating | Title | Title color | Team |
|---|---|---|---:|---:|---|---|---|
| `5276027620366606` | Chunithm | `1524` | 1 | 17.58 | NEW COMER | GOLD | GiGO |

## Rear player terminal

The rear terminal opens a native slot-free `PlayerPanelMenu`. Its ID input is
split into four four-digit blocks. Search requests are sent to the server and
resolved against this library; invalid IDs return an intentionally empty view
without an error message. The special ID `0000-0000-0000-0000` creates a guest
view with character `0000`, name `GUEST`, no team/title and masked level/rating.
Completion is currently fixed at `0%` for every account.

Successful login updates the physical player panel and persists its account ID
with the assembled part. The logout X clears the account and restores a blank,
masked presentation. Search and logout retain the existing administrator-only
panel-edit permission policy.
