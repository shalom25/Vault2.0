---
title: Top Players (/vaultop and /eco top)
description: Asynchronous ranking of the richest players with configurable cache, threshold-based invalidation, and 10-per-page pagination.
---

# Top Players

Three commands access the balance ranking, all backed by `TopCacheService`:

| Command           | Permission     | Description                                                 |
|-------------------|--------------|-------------------------------------------------------------|
| `/vaultop [page]` | `vault.top` | Quick alias command (default `true`)                       |
| `/vault top [p]`  | `vault.eco.top` | Admin sub-command                                     |
| `/eco top [p]`    | `vault.eco.top` | Sub-command within /eco (OP default)                     |

## `TopCacheService` — Asynchronous Architecture

Never calculates the top on the player event thread. Instead:

```
 Bukkit Async Scheduler (every refresh_seconds)
            ↓
  build() → snapshotBalances() for each currencyId
            sorted DESC by balance
            Bukkit.getOfflinePlayer(uuid) to resolve names
            ↓
  CachedTop { List<TopEntry>, rankByUuid: Map<UUID,Integer> }
            ↓
  ConcurrentHashMap caches.put(currencyId, cached)
```

### Configuration

```yaml
top:
  refresh_seconds: 300        # recalculate every 5 min (300 s)
  change_threshold: 0       # invalidate cache only if |Δ| > threshold
```

- `refresh_seconds` — period of the `runTaskTimerAsynchronously`. Minimum value `1s` (internally `Math.max(1L, x)`).
- `change_threshold: 0` — aggressive invalidation on every admin op (`/eco set/give/take/reset`).

### Manual Invalidation

```
TopCacheService.invalidateAll()         → clears all currencies
TopCacheService.invalidate(currencyId)  → clears one specific currency
```

Both trigger an async `refreshAll()` in the background.

## `TopEntry` Structure

Each ranking entry:
```java
class TopEntry {
  int     rank;      // 1-based
  UUID    uuid;
  String  name;      // OfflinePlayer.getName() or UUID[:8]
  double  balance;   // raw unformatted value
}
```

## Linked Placeholders

PlaceholderAPI (if installed) reads directly from TopCacheService:

| Placeholder               | Returns                                                        |
|---------------------------|--------------------------------------------------------------|
| `%vault_top%`             | Multi-line formatted top 10                                   |
| `%vault_top_<n>%`         | Full line for position n                                     |
| `%vault_top_name_<n>%`    | Player name at position n                                     |
| `%vault_top_amount_<n>%` | Formatted balance of player at position n                     |

## CLI Pagination

Commands return **10 results per page**. Limit calculation is done in each command handler; if `page ≤ 0` page 1 is assumed.

The `rankByUuid` cache (Map `UUID → rank`) enables O(1) lookups for "what rank does player X hold?" via `getRankNumber(currencyId, uuid)`.
