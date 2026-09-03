---
title: Skript
description: Skript compatibility: hooks for pay, add, take and balance management via Vault API v2.1.0.
---

# Skript Compatibility

Vault 2.1.0 includes native hooks with **Skript** (>= 2.7.0) to handle money in your scripts.

## Requirements

```
Skript >= 2.7.0
Vault  2.1.0
```

## Available Expressions

| Skript Syntax | Description |
|---|---|
| `balance of %player%` | Player's current balance (default currency) |
| `balance of %player% in %string%` | Balance in a specific currency |
| `(format|formatted) balance of %player%` | Formatted balance with symbol |
| `default currency id` | Default currency ID |

## Available Effects

| Skript Syntax | Alias |
|---|---|
| `pay %player% %number%` | `add %number% money to %player%` |
| `take %number% money from %player%` | `withdraw %number% from %player%` |
| `set balance of %player% to %number%` | `/eco set` |
| `reset balance of %player%` | `/eco reset` |
| `transfer %number% money from %player% to %player%` | Transfer |

## Conditions

| Syntax |
|---|
| `%player% has %number% money` |
| `%player% can afford %number%` |
| `%player%'s balance is (greater|less) than %number%` |

---

## Practical Examples

### Give Kill Reward

```skript
on death of player:
    attacker is a player
    set {_reward} to 250
    pay attacker {_reward}
    send "&6+%formatted balance of attacker%" to attacker
```

### Land Claim / Per-Block Price

```skript
on place:
    player doesn't have permission "vault.bypass"
    set {_price} to 15.5

    if player has {_price} money:
        take {_price} money from player
        send "&aYou paid &f%formatted {_price}% &ato place the block."
    else:
        cancel event
        send "&cYou don't have enough money (you need %formatted {_price}%)"
```

### Simple Shop (/shop Command)

```skript
command /shop:
    trigger:
        send "&8=========== &6SHOP &8==========="
        send "  &f1) &aDiamond x1  &7: &f$250"
        send "  &f2) &aApple x32  &7: &f$150"
        send "  &f3) &aXP 30 lvls  &7: &f$1000"
        send "&8==============================="
        send "&eUse: /buy <number>"

command /buy <integer>:
    trigger:
        set {_n} to arg 1
        if {_n} is 1:
            set {_price} to 250
            if player has {_price} money:
                take {_price} money from player
                give 1 diamond to player
                send "&aYou bought a diamond"
            else:
                send "&cYou need %formatted {_price}%"
        else if {_n} is 2:
            set {_price} to 150
            if player has {_price} money:
                take {_price} money from player
                give 32 apple to player
            else:
                send "&cYou don't have enough money"
        else if {_n} is 3:
            set {_price} to 1000
            if player has {_price} money:
                take {_price} money from player
                give 30 levels of xp to player
            else:
                send "&cInsufficient balance"
```

### Daily Salary System

```skript
every day at "09:00":
    loop all players:
        set {_salary} to 500
        if loop-player has permission "vip":
            set {_salary} to 1500
        pay loop-player {_salary}
        send "&aDaily salary: &f+%formatted {_salary}%" to loop-player
```

### Paid Kit

```skript
command /kit pvp:
    cooldown: 1 day
    trigger:
        set {_cost} to 750
        if player has {_cost} money:
            take {_cost} money from player
            give 1 diamond sword of sharpness 5 to player
            give 1 diamond helmet of protection 4 to player
            give 1 diamond chestplate of protection 4 to player
            give 1 diamond leggings of protection 4 to player
            give 1 diamond boots of protection 4 to player
            give 16 golden apple to player
            send "&aPVP Kit purchased!"
        else:
            cancel the cooldown
            send "&cYou are short: %formatted (750 - (balance of player))%"
```

---

## Multi-currency in Skript

```skript
# Currency "gems" (defined in balances.yml currencies.gems)
command /gems:
    trigger:
        set {_gems} to balance of player in "gems"
        send "&fYou have &b%{_gems}% 💎 gems"

command /buygems <number>:
    trigger:
        set {_amt} to arg 1
        set {_price} to {_amt} * 100   # 100 coins = 1 gem

        if player has {_price} money:
            take {_price} money from player
            set balance of player in "gems" to (balance of player in "gems") + {_amt}
            send "&a+%{_amt}% 💎"
```

---

## Scoreboard via Skript

```skript
every 20 ticks:
    loop all players:
        set line 1 of scoreboard of loop-player to "&6Your money"
        set line 2 of scoreboard of loop-player to "&f  %formatted balance of loop-player%"
        set line 4 of scoreboard of loop-player to "&7/shop to buy"
```

---

## Internal Hook

Vault 2.1.0 registers hooks via `SkriptHook`:

```java
// Vault 2.1.0 internal class:
public class SkriptHook {

    public void register() {
        // Register expressions
        ExprBalance.register();
        ExprFormattedBalance.register();

        // Register effects
        EffPay.register();
        EffTake.register();
        EffSetBalance.register();
        EffTransfer.register();

        // Register conditions
        CondHasMoney.register();
    }
}
```

## Softdepend

```yaml
# Vault plugin.yml:
softdepend:
  - Skript
```

If Skript is not present, the hooks do not load and Vault works normally with no performance penalty.

## Transfer Example

```skript
command /payp <player> <number>:
    trigger:
        if arg 2 <= 0:
            send "&cInvalid amount"
            stop
        if player has (arg 2) money:
            transfer (arg 2) money from player to arg 1
            send "&aYou paid %formatted arg 2% to %arg 1%"
            send "&a%player% sent you %formatted arg 2%" to arg 1
        else:
            send "&cYou don't have %formatted arg 2%"
```
