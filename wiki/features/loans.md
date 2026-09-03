---
title: Loans (/loan and /vault loan)
description: Player loan system with installments, automatic interest, missed payments, and defaulting penalty effects.
---

# Loans (`/loan` and `/vault loan`)

Allows players to borrow money from the server and repay it in installments. Installment collection runs automatically via the scheduler; continued default marks the loan as `DEFAULTED` and applies penalty effects.

## Commands

- `/loan` (alias `/prestamo`) — Opens the loans GUI menu (`LoanMenuService`)
- `/vault loan` — Equivalent access from the main command
- `/vault cancel` — Cancels an in-progress chat flow

Permission: `vault.loan` (default `true`). Global config `loans.enabled: false` disables the entire module without needing permissions.

## `loans.yml` / config.yml Configuration

```yaml
loans:
  enabled: true
  max_active_per_player: 1      # simultaneous loans per player
  min_amount: 1
  max_amount: 100000
  min_installment: 1
  min_installment_by_amount:    # increasing minimum installment by amount
    1000: 50
    5000: 250
  max_installments: 60
  default_interval_hours: 24    # hours between installments (default value)
  charge_check_seconds: 60      # charge scheduler frequency
  max_missed_payments: 3        # missed payments → DEFAULTED
  defaulted_effects:
    enabled: true
    refresh_seconds: 5          # re-applies effects every X seconds
    duration_seconds: 8
    effects:
      - "SLOW:1"                # slowness (EFFECT_NAME:LEVEL 1-based)
      - "SLOW_DIGGING:1"        # mining fatigue
```

Legacy formats are also accepted: `loans.installment_minimum_tiers` (map list) and `loans.minimum_installment_by_amount` (nested section). `minimumInstallmentFor()` evaluates **all sources** and picks the maximum.

## Creation Wizard (Chat Flow)

`LoanService.openRequestFlow()` starts a chat conversation with `ConversationState` states (LoanService.java:786–796):

```
ASK_AMOUNT → ASK_TYPE ── [total] → ASK_TOTAL_DELAY_HOURS → createLoan
                 │
                 └─ [installments] → ASK_INSTALLMENTS_MODE ── [count] → ASK_INSTALLMENTS
                                                                        └─ [amount] → ASK_INSTALLMENT_AMOUNT
                                                                             ↓
                                                                      ASK_INTERVAL_HOURS → ASK_FIRST_DELAY_HOURS → createLoan
```

- **Total mode** — A single future payment (1 installment). Player only enters the delay in hours.
- **Installments by count mode** — Specifies how many installments; installment = amount / N.
- **Installments by $ mode** — Specifies amount per installment; N = ceil(amount / installment).

If `interval_hours ≤ 0` or `first_delay_hours ≤ 0`, the config default value is used.

## Automatic Charge Scheduler

Every `charge_check_seconds` a `runTaskTimerAsynchronously` scans active loans and synchronizes back to the main thread to load balances (LoanService.java:305–371):

1. If `nextChargeAtMs ≤ now` attempts to charge `installmentAmount` (or whatever remains)
2. If balance exists: charges → `installmentsLeft--` → advances `nextChargeAtMs += intervalMs`
3. If NO balance: `missedPayments++`
4. When `max_missed_payments` is reached: `status = DEFAULTED`

## Statuses and Transaction Types

`LoanStatus`: `ACTIVE` → `PAID` or `DEFAULTED`

| Event                    | TxType                    |
|--------------------------|---------------------------|
| Initial disbursement     | `LOAN_DISBURSE`           |
| Manual / installment payment | `LOAN_REPAY` (note that charging uses normal economy.withdraw) |
| Loan defaulted           | `LOAN_DEFAULT`            |
| Collateral seized        | `LOAN_COLLATERAL_SEIZED`  |

When paying manually (`/vault loan` → **Pay**) the player can enter the amount or `all` / `todo` to settle the full debt.

## DEFAULTED Effects

If `defaulted_effects.enabled: true` and the loan is in `DEFAULTED` status, effects are RE-applied to the online player every `refresh_seconds`. Legacy name mapping:

| Legacy alias     | Modern Minecraft name        |
|------------------|------------------------------|
| `SLOWNESS`       | `slowness`                   |
| `FATIGUE`        | `mining_fatigue`             |
| `FAST_DIGGING`   | `haste`                      |
| `INCREASE_DAMAGE`| `strength`                   |
| `HEAL`/`HARM`    | `instant_health` / `instant_damage` |
| etc.             | Resolved by `legacyEffectKey()` |

Config effect levels are **1-based**; internally 1 is subtracted to convert to Bukkit's 0-based `amplifier`.
