---
title: Players Cannot Pay
description: Step-by-step diagnosis of why /pay does not work: permissions, limits, offline player, insufficient balance, chat mode cancel.
---

# FAQ: Players Cannot Pay (`/pay`)

## 1. `pay.no_permission` when typing `/pay`

The player does not have the `vault.pay` permission.

**Check with LuckPerms (example)**:
```
/lp user Notch info
/lp group default permission set vault.pay true
/lp group default permission set vault.balance true
/lp group default permission set vault.loan true
```

By default in `plugin.yml` these are:
```yaml
vault.balance: default=true
vault.pay:     default=true
vault.loan:    default=true
```
If your permission plugin overrides `default: true` → you must reassign them manually.

## 2. `pay.invalid_amount` when entering the amount

The amount was not parsed as a positive double. `ChatInputSanitizer.parsePositiveDouble` rejects:
- Negatives (`/pay Notch -100`)
- Zero
- Non-numeric characters (`/pay Notch onehundred`)
- Double decimal separators (`/pay Notch 1.5.7`)

## 3. `pay.amount_too_small` / `pay.amount_too_large`

Global limits were violated:

```yaml
pay_limits:
  min: 1
  max: 100000
```

`PayCommand.java:110-116` compares first without bypass. To make a user rank ignore limits:
- `vault.pay.bypass_min` → ignores the minimum
- `vault.pay.bypass_max` → ignores the maximum
- The Charge flow also accepts the wildcard `vault.pay.bypass_limits`

## 4. `pay.not_enough_money`

The sender does not have enough balance in that world/currency. The check happens AFTER the limits:

```java
double senderBalance = se.getBalance(cid, player, worldName);
if (senderBalance < amount) { player.sendMessage("pay.not_enough_money"); return; }
```

Use `/balance` to verify. If you use multi-world, check that they are in the correct world and not in a separate one.

## 5. `pay.player_offline` when paying someone who is disconnected

There are 2 scenarios:

### Scenario A — unknown recipient + `offline-uuid-fallback: false`
Direct response: the player has never been seen and the config forbids offline UUIDs. Solution: `config.yml → offline-uuid-fallback: true`.

### Scenario B — recipient seen but disconnected
`PayCommand.java:158` checks:
```java
if (!(economy instanceof SimpleEconomy) || offlinePay == null) {
    player.sendMessage("pay.player_offline"); return;
}
```
If you are using MySQL backend and the `OfflinePayQueueService` injection failed in `VaultPlugin.onEnable()`, this message appears. Check the startup log for previous errors.

## 6. The `/pay` menu does not open or does not show heads

Typical causes:
- `pay_menu.size` is not a multiple of 9 → the service forces it to 27.
- Size too small and players don't fit → in the main loop it cuts off when `size` is reached.
- Players with `hide from player list` (Essentials vanish) still appear in `Bukkit.getOnlinePlayers()`; use `/pay <name>` directly if you don't see them.
- SkinsRestorer not installed → heads fall back to Bukkit's `setOwningPlayer` (Steve/Alex skin).

## 7. Chat mode hangs and does not accept amounts

A `ChargeRequestService` or loans flow is still open. Type **`cancel`** or **`cancelar`** (configurable words in `pay.prompt.cancel_words`) and try again.

Another way: the player left the server without closing the window and upon returning is still in `awaitingAmount`. Type `/pay cancel` to clear it.
