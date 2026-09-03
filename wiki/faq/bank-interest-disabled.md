---
title: Bank Interest Not Applied
description: Checklist when players do not receive INTEREST. enabled, percent, every_minutes, balance=0, async scheduler stopped.
---

# FAQ: Bank Interest Disabled / Not Arriving

You expected interest to be applied to the bank every hour and it doesn't happen. Here's the checklist in order of probability.

## 1. `bank.interest.enabled: false` — master switch

```yaml
bank:
  interest:
    enabled: true     # ← this is the general switch
    every_minutes: 60
    percent_per_period: 0.5
```

Even if `percent_per_period` is > 0, **`BankService.start()` does not schedule the task if `enabled` is `false`** (BankService.java:119-125):
```java
boolean interestEnabled = isInterestEnabled();
if (interestEnabled && interestPct > 0 && interestMinutes > 0) {
    // schedule runTaskTimerAsynchronously
}
```
With `enabled: false` none of this happens.

## 2. `percent_per_period` is 0 or negative

Same guard: `interestPct > 0`. If you have it set to 0 (for testing) the task is not scheduled.

## 3. `every_minutes` is 0

Guard: `interestMinutes = Math.max(1L, config.getLong(...))`. Internally it is forced to 1; but if you set it very high (1440 = 1 day) you'll think it doesn't work until 24 hours have passed.

## 4. The player's bank balance is 0

Inside `applyInterest()`:
```java
for (Map.Entry<UUID, Double> e : bankBalances.entrySet()) {
    double bal = sanitize(e.getValue());
    if (bal <= 0) continue; // ← skips to the next one
    ...
}
```
A player with `bank_balance = 0` will **never** have interest applied even if everything else is perfect. They must have deposited something first. `/vault bank` → **Deposit**.

## 5. Bank balance was not saved

`BankService.depositBank(uuid, amount)` writes to `bank_balances.yml`:
```java
bankBalances.merge(uuid, amount, ...); save();
```

If the file is not written due to permissions (see Installation FAQ), on restart it goes back to 0 and point 4 kicks in. Check the modification time of `bank_balances.yml`.

## 6. Async scheduler cancelled or plugin disabled

`interestTask` is stored in a `volatile BukkitTask`. If there is an unhandled exception in some plugin that kills Bukkit's async thread, the task will not run again.

**Quick diagnosis**:
- Wait 1 minute and run `/vault reload` (recreates all services).
- If after the reload it starts working, the task was stopped. Look for warnings in the log from the last hour.

## 7. TAX confused with INTEREST

Both schedulers are independent:
```yaml
bank:
  interest: { ... }  # ← gives money to the player
  tax:      { ... }  # ← TAKES money above bank.tax.threshold
```
If you confuse the two, you thought you were going to gain and ended up losing. Check `/vault history` looking for `TxType.TAX` or `TxType.INTEREST`. The bank GUI (SLOT_INFO slot 22) shows the next formatted interest/tax.

## 8. Interest does arrive but is minuscule

Formula: `interest = bankBal × percent_per_period / 100`.

With `percent_per_period: 0.5`:
- Bank = 100 → interest = **0.5**
- Bank = 1,000 → interest = **5**
- Bank = 1,000,000 → interest = **5,000**

If you expected 5% and put 0.5 (0.5%) it's normal. Raise it to `percent_per_period: 5`.
