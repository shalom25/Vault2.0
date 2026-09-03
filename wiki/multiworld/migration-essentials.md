---
title: Migration from Essentials
description: Import Essentials balances from /plugins/Essentials/userdata/*.yml. Merge/replace modes, hidden internal marker.
---

# Essentials → Vault Migration

If your server was previously using **Essentials Economy**, you can transfer all balances to Vault with a single operation. `EssentialsImportService` reads the `Essentials/userdata/<uuid>.yml` files directly.

## Configuration

```yaml
import:
  essentials:
    enabled: false   # Set true to run at startup
    replace: false   # true = overwrites existing balances; false = only creates missing ones
    # target: file   # Optional: "file" forces saving to balances.yml even if MySQL is active
```

With `enabled: true`, the import fires in the plugin's `onEnable()`. You can also launch it afterward with `/vault import essentials` **if Essentials exists** in the plugins folder.

## Import modes

Internally `mode 0 = merge` / `mode 1 = replace`:

| Mode     | Balance already exists in Vault? | Action                                                                     |
|----------|------------------------------|----------------------------------------------------------------------------|
| merge=0  | Yes, but it's 0               | `canBackfillEmpty`: yes overwrites (if essentials has value ≠ 0)        |
| merge=0  | Yes, and it's > 0                | **Does not touch** → stats.skipped++                                               |
| merge=0  | No                           | Creates balance with Essentials value                                    |
| replace=1| Yes/No                        | Always overwrites with Essentials value                              |

Essentials balance resolution (`parseMoney`):
1. If `money` is a `Number` → `.doubleValue()` directly
2. If it is text → strips spaces and commas and `Double.parseDouble`
3. Not finite → null and `stats.failed` increments

## Player resolution by UUID or name

`resolvePlayer()` tries, in order, for each file `Name.yml`:

1. File name as UUID (`UUID.fromString(fileBase)`)
2. YAML field `uuid`
3. Field `last-account-uuid` (old formats)
4. Fallback by name: `last-account-name` → `player-name` → `name` → file name
5. If everything fails → `PlayerResolver.resolveByNameWithOfflineFallback` generates offline-mode UUID

## Import statistics

On completion it writes a line to the log:
```
[Vault] Essentials import finished.
 Scanned: 1205, imported: 903, skipped: 200,
 duplicates: 50, errors: 52 (hidden internal marker)
```

## Import marker (anti-re-execution)

So it doesn't repeat on every restart, upon finishing with `failed == 0` it writes:
- `.internal/essentials_import.done` (hidden `.internal/` folder with `dos:hidden` on Windows)
- Visible fallback: legacy `essentials_import.done`

If `failed > 0` it is not marked, so the next startup **retries** the failed ones.

## Persistence destination

- `target: file` or MySQL inactive → `economy.saveToFile()` (balances.yml)
- `target: auto` (default) and MySQL active → `economy.save()` via HikariCP
