---
title: LuckPerms
description: Integration with LuckPerms for permission-based economy features, group multipliers, meta-based currency limits, and context-aware transactions in Vault 2.1.0.
---

# LuckPerms

Vault 2.1.0 integrates natively with **LuckPerms** (>= 5.4) to enable permission-based economy features, group balance multipliers, permission-gated commands, and context-aware transactions.

## Features

- ✅ Permission-based economy command access (`vault.pay`, `vault.admin`)
- ✅ Group-based balance multipliers via LuckPerms meta (`balance-multiplier`)
- ✅ Per-group transaction limits via meta keys (`max-daily-transfer`)
- ✅ Context-aware transactions (world, server, region contexts)
- ✅ Automatic permission registration via `VaultPermissionRegistry`
- ✅ Fallback to Bukkit `hasPermission()` when LuckPerms is absent

## LuckPermsMetaService

The internal `LuckPermsMetaService` reads meta keys from LuckPerms to adjust economy behavior.

```java
// Internal usage example:
@Service
public class LuckPermsMetaService {

    private final LuckPerms luckPerms;

    public double getMultiplier(Player player) {
        User user = luckPerms.getUserManager().getUser(player.getUniqueId());
        if (user == null) return 1.0;

        String meta = user.getCachedData().getMetaData().getMetaValue("balance-multiplier");
        return meta != null ? Double.parseDouble(meta) : 1.0;
    }

    public BigDecimal getDailyLimit(Player player) {
        MetaDataStack meta = luckPerms.getUserManager().getUser(player.getUniqueId())
            .getCachedData().getMetaData();
        String limit = meta.getMetaValue("max-daily-transfer");
        return limit != null ? new BigDecimal(limit) : new BigDecimal("1000000");
    }
}
```

## Supported Meta Keys

| Meta Key | Type | Description | Default |
|---|---|---|---|
| `balance-multiplier` | `double` | Multiplier applied to `/eco give` and interest rewards | `1.0` |
| `max-daily-transfer` | `BigDecimal` | Max amount a player can send via `/pay` per day | `1000000` |
| `max-note-value` | `BigDecimal` | Maximum physical bank note value the player can issue | `10000` |
| `loan-max-principal` | `BigDecimal` | Maximum loan principal allowed for this player | `50000` |
| `loan-interest-bonus` | `double` | Percent discount on loan interest rate (0.1 = 10% discount) | `0` |
| `tax-exempt` | `boolean` | If `true`, player is exempt from transfer taxes | `false` |

## Setting Meta via LuckPerms Commands

```
/lp user Notch meta set balance-multiplier 1.5
/lp group vip meta set max-daily-transfer 500000
/lp group premium meta set tax-exempt true
/lp group admin permission set vault.admin true
```

## Permission Nodes

Vault registers the following nodes, compatible with both LuckPerms and the Bukkit permission system:

| Permission | Description | Default |
|---|---|---|
| `vault.user` | Basic user access (/balance, /pay, /baltop) | true |
| `vault.pay` | Allow /pay transfers | true |
| `vault.baltop` | Allow viewing the balance leaderboard | true |
| `vault.note.issue` | Allow issuing physical bank notes | op |
| `vault.note.redeem` | Allow redeeming physical bank notes | true |
| `vault.loan.apply` | Allow applying for loans | true |
| `vault.eco.give` | `/eco give` admin command | op |
| `vault.eco.take` | `/eco take` admin command | op |
| `vault.eco.set` | `/eco set` admin command | op |
| `vault.admin` | All `/vault admin` subcommands | op |

## Context-Aware Transactions

Vault 2.1.0 reads LuckPerms contexts (world, server, region) to apply world-specific currency rules.

```java
public EconomyResponse depositWithContext(Player player, BigDecimal amount, String currency) {
    Contexts contexts = luckPerms.getContextManager().getContexts(player);

    // World-specific currency restriction
    if (currency.equals("nether_coins") && !contexts.getContexts().containsKey("world:world_nether")) {
        return new EconomyResponse(0, 0, EconomyResponse.ResponseType.FAILURE,
            "nether_coins can only be used in the Nether");
    }

    return economy.depositPlayer(player, amount.doubleValue());
}
```

## Configuration

```yaml
# config.yml
luckperms:
  enabled: true
  use_meta_multipliers: true
  use_meta_limits: true
  context_aware_currencies: true
  cache_meta_seconds: 300
  auto_register_permissions: true
```

## Dependency in plugin.yml

```yaml
softdepend:
  - LuckPerms
```

## Plugin Detection

```java
if (Bukkit.getPluginManager().isPluginEnabled("LuckPerms")) {
    LuckPerms api = LuckPermsProvider.get();
    metaService = new LuckPermsMetaService(api);
    permissionRegistry = new LuckPermsPermissionRegistry(api);
} else {
    metaService = new DefaultMetaService();
    permissionRegistry = new BukkitPermissionRegistry();
}
```

## Multiplier Application Flow

```
1. Admin runs /eco give Notch 1000
2. VaultPermissionRegistry checks vault.eco.give
3. LuckPermsMetaService reads balance-multiplier meta
   ├─ balance-multiplier = 1.5 → final amount = 1500
   └─ tax-exempt = false → apply 2% transfer tax
4. depositPlayer() executes with adjusted amount
5. TransactionEvent is fired with multiplier metadata
```

## Troubleshooting LuckPerms Integration

| Issue | Solution |
|---|---|
| Multipliers not applying | Verify `luckperms.use_meta_multipliers: true` in config |
| Permissions not registering | Run `/vault admin perms reload` |
| Meta keys return null | Check LuckPerms verbose: `/lp verbose on <player>` |
| Contexts not detected | Ensure LuckPerms context providers are loaded |
| Tax exemption not working | Set meta `tax-exempt` to string `"true"` (boolean literal) |
