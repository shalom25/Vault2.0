---
title: SimpleEconomy Hooks
description: How to get the EconomyProvider from VaultPlugin and perform instanceof checks against SimpleEconomy to detect the modern v2 API.
---

# SimpleEconomy Hooks

How to integrate your plugin with Vault 2.1.0's native API by directly accessing the provider via `VaultPlugin.getEconomyProvider()`.

## Get the Provider from VaultPlugin

The most direct and fastest way:

```java
import net.milkbowl.vault.VaultPlugin;
import net.milkbowl.vault.economy.SimpleEconomy;

public class VaultHook {

    private SimpleEconomy economy;

    public boolean hook() {
        VaultPlugin vault = (VaultPlugin) Bukkit.getPluginManager().getPlugin("Vault");
        if (vault == null || !vault.isEnabled()) {
            return false;
        }

        // ✅ Detect if it's Vault 2.x with modern SimpleEconomy
        Object provider = vault.getEconomyProvider();

        if (provider instanceof SimpleEconomy) {
            this.economy = (SimpleEconomy) provider;
            getLogger().info("[Vault 2.x] SimpleEconomy hook OK");
            return true;
        }

        // Fallback to legacy Vault 1.x API (Economy.class)
        // (See end of this page)
        return false;
    }

    public SimpleEconomy economy() { return economy; }
}
```

## Detection Flowchart

```
1. VaultPlugin.getEconomyProvider()
   ├─ instanceof SimpleEconomy?  → ✅ Modern API v2.x
   └─ instanceof Economy?        → ⚠ Legacy v1.x (com.milkbowl.vault.economy.Economy)
```

## Complete Plugin Example

```java
package com.yourplugin;

import net.milkbowl.vault.VaultPlugin;
import net.milkbowl.vault.economy.SimpleEconomy;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

public final class MyEconomyAddon extends JavaPlugin {

    private SimpleEconomy vaultEco;
    private boolean hooked = false;

    @Override
    public void onEnable() {
        hooked = tryHookVault();
        if (!hooked) {
            getLogger().severe("Failed to hook into Vault SimpleEconomy");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        getCommand("myshop").setExecutor(new ShopCommand(vaultEco));
    }

    private boolean tryHookVault() {
        VaultPlugin vault = (VaultPlugin) Bukkit.getPluginManager()
            .getPlugin("Vault");
        if (vault == null) return false;

        Object provider = vault.getEconomyProvider();
        if (provider instanceof SimpleEconomy) {
            vaultEco = (SimpleEconomy) provider;
            return true;
        }
        return false;
    }

    public SimpleEconomy getVaultEconomy() {
        return vaultEco;
    }
}
```

## Advantages of Direct Hook (SimpleEconomy)

| Feature | ServicesManager (classic) | `getEconomyProvider()` |
|---|---|---|
| Internal cache | Each `getRegistration()` lookup | Direct reference |
| Multi-currency | No (default only) | ✅ `getBalance(p, "gems")` |
| Offline mode ✅ | Yes | Yes |
| World-specific balances | No | ✅ |
| Async loading | No | ✅ |
| Internal Tx events | No | ✅ `TransactionLogService` |

## Extend: Access Internal Services

If your plugin hard-depends on Vault 2.1.0 you can access other services:

```java
import net.milkbowl.vault.services.transaction.TransactionLogService;
import net.milkbowl.vault.services.NoteService;
import net.milkbowl.vault.services.LoanService;

// Inside the plugin:

NoteService notes = vault.getService(NoteService.class);
TransactionLogService logs = vault.getService(TransactionLogService.class);
LoanService loans = vault.getService(LoanService.class);

// Create a note programmatically:
ItemStack note = notes.issue(player, new BigDecimal("1000"), "coins");
player.getInventory().addItem(note);
```

## Example Shop Command

```java
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class ShopCommand implements CommandExecutor {

    private final SimpleEconomy eco;

    public ShopCommand(SimpleEconomy eco) {
        this.eco = eco;
    }

    @Override
    public boolean onCommand(CommandSender s, Command c, String l, String[] a) {
        if (!(s instanceof Player)) return true;
        Player p = (Player) s;

        if (a.length == 0) {
            p.sendMessage("Use: /shop <buy>");
            return true;
        }

        if ("buy".equalsIgnoreCase(a[0])) {
            double price = 250.00;
            if (!eco.has(p, price)) {
                p.sendMessage("&cYou need " + eco.format(price));
                return true;
            }

            EconomyResponse r = eco.withdrawPlayer(p, price);
            if (r.transactionSuccess()) {
                p.getInventory().addItem(Material.DIAMOND_SWORD.asItemStack());
                p.sendMessage("&aYou bought a diamond sword");
                p.sendMessage("&fBalance: " + eco.format(r.balance));
            }
        }
        return true;
    }
}
```

## Fallback to Legacy Vault 1.x

If you must support both (1.x and 2.x):

```java
private Object hookAnyEconomy() {
    VaultPlugin vault = (VaultPlugin) Bukkit.getPluginManager().getPlugin("Vault");
    if (vault == null) return null;

    Object p = vault.getEconomyProvider();
    if (p instanceof SimpleEconomy) {
        // 2.x
        return new ModernWrapper((SimpleEconomy) p);
    }
    if (p instanceof net.milkbowl.vault.economy.Economy) {
        // 1.x legacy
        return new LegacyWrapper((net.milkbowl.vault.economy.Economy) p);
    }
    return null;
}
```

## Detect Vault Version at Runtime

```java
public static String vaultVersion() {
    Plugin vault = Bukkit.getPluginManager().getPlugin("Vault");
    return vault != null ? vault.getDescription().getVersion() : "0";
}

// Usage:
if (vaultVersion().startsWith("2.")) {
    // SimpleEconomy path
}
```

## Precautions

- ✅ Call `getEconomyProvider()` only once in `onEnable` and store the reference in a field.
- ⚠ Do not call `Bukkit.getServicesManager().getRegistration(...)` every tick.
- ⚠ In soft-depend plugins always validate that the provider is `instanceof SimpleEconomy` before casting.
- ✅ Use a `PluginLoader` that waits for Vault to be fully enabled if using WorldGuard/ProtocolLib.
