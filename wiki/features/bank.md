---
title: Bank (/vault bank)
description: Independent bank balance with GUI, deposits/withdrawals, periodic interest, and progressive tax on large fortunes.
---

# Bank (`/vault bank`)

The bank introduces a **second independent balance** per player (stored in `bank_balances.yml`) separate from the wallet. Players can deposit funds to generate interest or withdraw them when needed.

## Accessing the Bank

Open from:
- **Slot 13 🏦 Bank** of the main `/vault` menu (VaultMenuService.java:79-97, material `GOLD_BLOCK`)
- **Command `/vault bank`** → opens the **GUI directly by default** (same as `/vault bank menu|gui|open` — no extra word needed since v2.1.2!)
- CLI subcommands:
  - `/vault bank balance [player]` — show wallet + bank + total
  - `/vault bank deposit <amount>` — wallet → bank (CLI alternative)
  - `/vault bank withdraw <amount>` — bank → wallet (CLI alternative)
  - `/vault bank top [page]` — bank_balance ranking
  - `/vault bank menu|gui|open` — explicit GUI (alias for default)

**Permission required:** `vault.bank` (default: `true`; also included in the `vault.use` meta-permission bundle). To view another player's bank balance use `vault.admin`.

The GUI is managed by `BankMenuService` with a **45-slot** inventory.

## GUI Layout

| Slot    | Material              | Function                                       |
|---------|-----------------------|------------------------------------------------|
| 10      | Chest                 | **Wallet** — Balance in wallet                 |
| 13      | Ender Chest           | **Bank** — Deposited bank balance              |
| 16      | Beacon                | **Total** — Wallet + Bank sum                  |
| 20      | Hopper                | **Deposit** — Chat flow for deposit            |
| 22      | Paper                 | **Info** — Interest/tax summary                |
| 24      | Dispenser/Dropper     | **Withdraw** — Chat flow for withdrawal        |
| 30–33   | Green Stained Glass   | **Deposit** quick picks (10/25/50/75%)         |
| 39–42   | Red Stained Glass     | **Withdraw** quick picks (10/25/50/100%)       |

### Quick Picks with Shift ×10

The 4 percentage buttons behave differently when pressing **Shift + Click** (BankMenuService.java:270–280):

| Quick pick        | Normal click  | Shift click                          |
|-------------------|--------------|--------------------------------------|
| QP1 (10%)         | 10% × amount | 100% (10×)                           |
| QP2 (25%)         | 25%          | 250%                                 |
| QP3 (50%)         | 50%          | 500%                                 |
| QP4 (75/100)      | 75% deposit / 100% withdrawal | Full wallet / full bank     |

Quick picks use `niceRound()` to round to friendly multiples (5 / 10 / 100 / 1,000 / 10,000 / 100,000 / 1,000,000…).

## Bank Transactions

Each operation records a `TxType` in the transaction log (BankService.java:85–106):

- `BANK_DEPOSIT` — On deposit: withdraws from wallet → adds to bank_balance
- `BANK_WITHDRAW` — On withdrawal: subtracts from bank_balance → deposits into wallet

The `bank_balances.yml` file stores a `balances.<UUID>: value` map and optionally `teams.<teamId>` for team banks. It is persisted with `save()` after each operation.

## Interest and Tax Configuration

```yaml
bank:
  interest:
    enabled: true
    every_minutes: 60
    percent_per_period: 0.5
  tax:
    enabled: false
    every_minutes: 180
    threshold: 1000000
    percent_per_period: 0.1
```

### Interest (`applyInterest`)

**Asynchronous** scheduler (`runTaskTimerAsynchronously`) that applies `percent_per_period%` on the **full bank_balance** every `every_minutes` minutes. Records `TxType.INTEREST` for every player with balance > 0.

If `enabled: false`, no task is scheduled regardless of the percentage value.

### Progressive Tax (`applyTax`)

Only applies to the **portion of the balance STRICTLY ABOVE THE THRESHOLD**. Example:
- threshold = 1,000,000 ; balance = 1,500,000
- **Only 500,000 is taxed × 0.1% = 500**

Records `TxType.TAX` and subtracts from bank_balance.

---

## PlaceholderAPI placeholders (NEW in v2.1.2)

Every bank-related value below is available in **6 formats**: `_raw` (double), `_fixed` (2 decimals), `_int` (integer), `_commas` (thousands separators), `_short` (abbreviated), `_formatted` (with currency symbol).

| Placeholder base | Description |
|---|---|
| `%vault_bank...%` | Current bank balance (e.g. `%vault_bank_formatted%`). |
| `%vault_interest...%` | Interest the player will receive in the **next** `bank.interest.every_minutes` cycle. |
| `%vault_tax...%` | Tax the player will pay in the **next** `bank.tax.every_minutes` cycle (0 if under the threshold, or over the threshold slice only). |
| `%vault_net...%` | **Net worth** = wallet + bank − remaining active loans (real asset value). |
| `%vault_wallet...%` | Explicit alias for vault_balance (wallet only, **not** including bank — use for unambiguous scoreboard columns). |

> Every placeholder also works with the **`%vault2_` prefix** (e.g. `%vault2_bank_formatted%`, `%vault2_net_short%`).

### Quick copy examples

```
%vault_bank_formatted%              →  $ 1,000,000.00
%vault_bank_short%                  →  $ 1.0m
%vault_interest_formatted%          →  + $ 5,000.00
%vault_tax_formatted%               →  - $ 500.00
%vault_net_formatted%               →  $ 1,250,000.00
%vault_wallet_formatted%            →  $ 250,000.00
```
