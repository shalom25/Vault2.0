---
title: Per-World Balances
description: Independent per-world balances using separate_worlds and MySQL vault_world_balances table. Compatibility with Vault legacy API.
---

# Per-World Balances

By default Vault 2.x uses a **global balance** per player across all worlds. If you need isolated economies (e.g.: a SkyBlock server with separate resource world and islands world), enable per-world separation.

```yaml
world_balances:
  separate_worlds:
    - world_nether
    - mines
```

Worlds that appear in this list get their **own balance row**; the rest share the global balance.

## Composite key `MultiBalanceKey`

Internally `SimpleEconomy` identifies each amount with the tuple:
```
(uuid, worldName, currencyId)
```
- If `worldName == null` → global balance (default behavior)
- If `worldName` is a world listed in `separate_worlds` → isolated balance
- If `worldName` is not listed → redirected to global **even if the handler received a world name** (to maintain compatibility with plugins that always pass the world)

## MySQL table `vault_world_balances`

If `storage.use_mysql: true`, `Database.ensureSchema()` automatically creates:

```sql
CREATE TABLE IF NOT EXISTS vault_world_balances (
  world_name VARCHAR(64)  NOT NULL,
  uuid       CHAR(36)     NOT NULL,
  balance    DECIMAL(19,4) NOT NULL DEFAULT 0,
  PRIMARY KEY (world_name, uuid)
);
```

Batch operations:
- `loadAllWorldBalances()` → returns `Map<worldName, Map<UUID, Double>>`
- `saveWorldBalances(...)` → `INSERT ... ON DUPLICATE KEY UPDATE balance=VALUES(balance)`
- `clearAllBalances()` → deletes both `vault_balances` and `vault_world_balances` at once

## Flow in `/pay` and `/balance` commands

Each handler reads `player.getWorld().getName()` and passes it to SimpleEconomy:

```java
String worldName = player.getWorld() != null ? player.getWorld().getName() : null;
if (economy instanceof SimpleEconomy) {
    economy.createPlayerAccount(player, worldName);
    double bal = economy.getBalance(player, worldName);
}
```

The offline payment `PlayerResolver` also accepts a world; when the payment is delivered with `deliverFor()` it is deposited in the global balance (since the player does not yet have a world assigned until they move).

## Vault legacy API compatibility

The classic API `net.milkbowl.vault.economy.Economy` defines two method families:

| Legacy signature                     | Vault 2.x returns                              |
|----------------------------------|-------------------------------------------------|
| `getBalance(OfflinePlayer)`      | Always **global** balance (null world)           |
| `getBalance(OfflinePlayer, String worldName)` | Resolves separate_worlds → global or isolated |
| `withdrawPlayer(OfflinePlayer, amt)` | Global                                       |
| `withdrawPlayer(p, world, amt)`  | Resolves separate_worlds                        |

This is intentional: old plugins that don't know about multi-world (such as ShopGUIPlus, Jobs) continue working with the default currency balance and the global world.
