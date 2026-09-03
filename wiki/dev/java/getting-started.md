---
title: Java Getting Started
description: First steps with Vault 2.1.0 SimpleEconomy API: getBalance, depositPlayer, withdrawPlayer, format, and getDefaultCurrencyId.
---

# Java: Getting Started (SimpleEconomy)

Quick guide to integrate Vault 2.1.0 into your plugin using the modern **SimpleEconomy** interface.

## Add Vault to plugin.yml

```yaml
name: MyPlugin
main: com.yourplugin.MyPlugin
version: 1.0.0
depend:
  - Vault
```

## Register Economy Provider (onEnable)

```java
package com.yourplugin;

import net.milkbowl.vault.VaultPlugin;
import net.milkbowl.vault.economy.SimpleEconomy;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

public class MyPlugin extends JavaPlugin {

    private static SimpleEconomy economy;

    @Override
    public void onEnable() {
        if (!setupEconomy()) {
            getLogger().severe("Vault not found!");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        getLogger().info("✓ Vault 2.1.0 loaded");
    }

    private boolean setupEconomy() {
        RegisteredServiceProvider<SimpleEconomy> rsp =
            getServer().getServicesManager().getRegistration(SimpleEconomy.class);
        if (rsp == null) return false;
        economy = rsp.getProvider();
        return economy != null;
    }

    public static SimpleEconomy getEconomy() {
        return economy;
    }
}
```

## Basic SimpleEconomy Methods

| Method | Return | Description |
|---|---|---|
| `getBalance(OfflinePlayer)` | `double` | Player balance in default currency |
| `getBalance(OfflinePlayer, String)` | `double` | Balance in a specific currency |
| `depositPlayer(OfflinePlayer, double)` | `EconomyResponse` | Deposit money |
| `withdrawPlayer(OfflinePlayer, double)` | `EconomyResponse` | Withdraw money |
| `has(OfflinePlayer, double)` | `boolean` | Does the player have X money? |
| `format(double)` | `String` | Formats amount with symbol |
| `getDefaultCurrencyId()` | `String` | Default currency ID |
| `currencyNamePlural()` | `String` | Plural currency name |
| `currencyNameSingular()` | `String` | Singular currency name |

## getBalance

```java
import org.bukkit.entity.Player;

public class BalanceExample {

    public void showBalance(Player p) {
        SimpleEconomy eco = MyPlugin.getEconomy();

        double balance = eco.getBalance(p);

        // Also by currency
        double gemBalance = eco.getBalance(p, "gems");

        p.sendMessage("Your balance: " + eco.format(balance));
    }
}
```

## depositPlayer

```java
import net.milkbowl.vault.economy.EconomyResponse;

public void reward(Player p, double amount) {
    SimpleEconomy eco = MyPlugin.getEconomy();

    EconomyResponse response = eco.depositPlayer(p, amount);

    if (response.transactionSuccess()) {
        p.sendMessage("&a+ " + eco.format(response.amount));
    } else {
        p.sendMessage("&cError: " + response.errorMessage);
    }
}
```

## withdrawPlayer

```java
public void charge(Player p, double price) {
    SimpleEconomy eco = MyPlugin.getEconomy();

    if (!eco.has(p, price)) {
        p.sendMessage("&cYou don't have enough money");
        return;
    }

    EconomyResponse response = eco.withdrawPlayer(p, price);

    if (response.transactionSuccess()) {
        p.sendMessage("&aPaid: " + eco.format(response.amount));
        p.sendMessage("&fRemaining balance: " + eco.format(response.balance));
    }
}
```

## format

```java
public String formatMoney(double value) {
    SimpleEconomy eco = MyPlugin.getEconomy();

    // Example output: $1,500.75
    return eco.format(value);
}
```

## getDefaultCurrencyId

```yaml
# balances.yml
currencies:
  coins:
    symbol: "$"
    name-plural: "dollars"
    name-singular: "dollar"
    default: true      # ← this is the one getDefaultCurrencyId() returns
  gems:
    symbol: "💎"
    name-plural: "gems"
```

```java
public void defaultCurrencyInfo() {
    SimpleEconomy eco = MyPlugin.getEconomy();

    String id = eco.getDefaultCurrencyId();           // "coins"
    String plural = eco.currencyNamePlural();          // "dollars"
    String singular = eco.currencyNameSingular();      // "dollar"

    getLogger().info("Active currency: " + id);
}
```

## Transfer Between Players

```java
public boolean transfer(Player from, Player to, double amount) {
    SimpleEconomy eco = MyPlugin.getEconomy();

    if (!eco.has(from, amount)) return false;

    EconomyResponse withdrawal = eco.withdrawPlayer(from, amount);
    if (!withdrawal.transactionSuccess()) return false;

    EconomyResponse deposit = eco.depositPlayer(to, amount);
    if (!deposit.transactionSuccess()) {
        // Manual rollback
        eco.depositPlayer(from, amount);
        return false;
    }

    return true;
}
```

## OfflinePlayer

All methods accept `OfflinePlayer` for disconnected players:

```java
import org.bukkit.OfflinePlayer;

public void chargeOfflineUser(UUID uuid, double amount) {
    SimpleEconomy eco = MyPlugin.getEconomy();
    OfflinePlayer op = Bukkit.getOfflinePlayer(uuid);

    eco.withdrawPlayer(op, amount); // Supports offline players
}
```

## EconomyResponse

| Field | Type | Description |
|---|---|---|
| `amount` | `double` | Amount that was moved |
| `balance` | `double` | Player's final balance |
| `type` | `ResponseType` | `SUCCESS`, `FAILURE`, `NOT_IMPLEMENTED` |
| `errorMessage` | `String` | Error message if it failed |
| `transactionSuccess()` | `boolean` | Was the transaction OK? |

```java
if (resp.type == EconomyResponse.ResponseType.SUCCESS) {
    // OK
}
```
