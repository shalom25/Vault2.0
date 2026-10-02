---
title: PlaceholderAPI
description: Complete list of PlaceholderAPI placeholders supported by Vault v2.1.2, including wallet, bank, interest, tax, net-worth, loan, baltop, currency, and dynamic per-player placeholders.
---

# PlaceholderAPI

Vault **v2.1.2** integrates two PlaceholderAPI expansions: **Vault2PlaceholderExpansion** (alias `vault2_*`) and **VaultPlaceholderExpansion** (identifier `vault`). Combined: **6 categories, 38+ placeholders** for wallet, bank, interest/tax, net worth, loans, and player leaderboards.

---

## Quick Index by Category

| Category | Count | Key example |
|---|---|---|
| Wallet / Economy | 15 | `%vault_balance_formatted%` |
| 🏦 Bank (NEW in v2.1.2) | 6 | `%vault_bank_formatted%` |
| 💸 Interest & Tax (NEW) | 12 | `%vault_interest_formatted%`, `%vault_tax_formatted%` |
| 💰 Net worth & Wallet alias (NEW) | 12 | `%vault_net_formatted%`, `%vault_wallet_formatted%` |
| 💳 Loan (NEW) | 38 | `%vault_loan_total%` / `_remainder%` / `_installment_amount%` / `_installments_left%` / `_interest%` (all variants) |
| Top rankings & misc | ~10 | `%vault_top%`, `%vault_top_uuid_<n>%` |

---

## 📋 Registered Placeholders (full list)

### Wallet / Economy balance

| Placeholder | Description | Return |
|---|---|---|
| `%vault_balance%` / `%vault_balance_raw%` | Raw wallet balance (double, unformatted) | `double` |
| `%vault_balance_fixed%` | Balance fixed to 2 decimals | `String` |
| `%vault_balance_int%` | Integer (rounded, no decimals) | `String` |
| `%vault_balance_commas%` | US-style thousand separators | `String` |
| `%vault_balance_short%` | Abbreviated (`1.2k`, `3.4m`, `5.6b`) | `String` |
| `%vault_balance_formatted%` | **Main one** — with currency symbol + locale format | `String` |
| `%vault_eco_balance%` / `%vault_eco_balance_fixed%` / etc. | Aliases (same as the ones above with `eco_` prefix — same 6 variants) | `String` / `double` |
| `%vault_ecobalance<0-8>dp%` | Custom decimal places (0 through 8) | `String` |

### 🏦 Bank (NEW in v2.1.2)

All 6 balance variants available:

| Placeholder | Description | Return |
|---|---|---|
| `%vault_bank%` / `%vault_bank_raw%` | Raw bank balance | `double` |
| `%vault_bank_fixed%` | 2 decimals | `String` |
| `%vault_bank_int%` | Integer | `String` |
| `%vault_bank_commas%` | Thousands separators | `String` |
| `%vault_bank_short%` | Abbreviated | `String` |
| `%vault_bank_formatted%` | **Main one** — formatted w/ symbol | `String` |

### 💸 Interest & Tax (NEW)

All 6 variants: `_raw`, `_fixed`, `_int`, `_commas`, `_short`, `_formatted`.

| Placeholder | Description |
|---|---|
| `%vault_interest%` (+ all 6 variants) | Interest the player will gain next bank.interest.every_minutes cycle. |
| `%vault_tax%` (+ all 6 variants) | Tax the player will pay next bank.tax.every_minutes cycle (applies **only** to the slice strictly over `bank.tax.threshold`, if threshold is met). |

### 💰 Net worth & Wallet alias (NEW)

All 6 variants available for both:

| Placeholder | Description |
|---|---|
| `%vault_net%` (6 variants) | **Net worth = wallet + bank − remaining active loans.** Shows true economic status. |
| `%vault_wallet%` (6 variants) | Explicit alias for vault_balance — *wallet only, not including bank*. Useful for unambiguous scoreboard columns. |

### 💳 Loan placeholders (NEW)

All 6 balance variants (raw/fixed/int/commas/short/formatted) apply to each numeric loan field:

| Placeholder | Description |
|---|---|
| `%vault_loan_total%` (all 6) | Total principal of the player's active loan. |
| `%vault_loan_remainder%` (all 6) | Remaining principal debt. |
| `%vault_loan_installment_amount%` (all 6) | How much each installment costs. |
| `%vault_loan_installments_left%` | Integer — how many installments remain. |
| `%vault_loan_installments_left_formatted%` | Formatted installments left. |
| `%vault_loan_interest%` (all 6) | Total remaining interest to be paid. |

### Top rankings & Misc

| Placeholder | Description | Return |
|---|---|---|
| `%vault_currency_symbol%` | Default currency symbol from `config.yml` currency.symbol. | `String` |
| `%vault_top%` | Full **Top 10** as a multiline string. | `String` |
| `%vault_top_<n>%` | Complete Nth entry: `Name - Amount` for rank N. | `String` |
| `%vault_top_name_<n>%` | Player name at rank N. | `String` |
| `%vault_top_amount_<n>%` | Formatted balance at rank N. | `String` |
| `%vault_top_uuid_<n>%` | **(NEW v2.1.2)** UUID at rank N (useful for HDB heads). | `String` |
| `%vault_balance_<player>%` | Raw balance of a specific named player (online or offline). | `double` |
| `%vault_balance_formatted_<player>%` | Formatted balance of a specific named player. | `String` |

> **ALIAS:** A second PlaceholderAPI expansion registers identifier `vault2` — so every placeholder also works as `%vault2_bank_formatted%`, `%vault2_loan_remainder%`, etc. They are **100% identical in value**, pick whichever prefix you prefer.

---

## Usage Examples

### Scoreboard

```yaml
# Example in config.yml of a scoreboard plugin
lines:
  - "&6&l⛃ ECONOMY"
  - " "
  - "&fWallet:   &e%vault_wallet_formatted%"
  - "&fBank:     &6%vault_bank_formatted%"
  - "&fNet:      &a%vault_net_formatted%"
  - " "
  - "&fInterest: &a+%vault_interest_formatted%"
  - "&fTax:      &c-%vault_tax_formatted%"
  - " "
  - "&fLoan:     &7%vault_loan_total_formatted%"
  - "&fRemaining:&c%vault_loan_remainder_formatted%"
  - " "
  - "&f#1 &a%vault_top_name_1%:&f%vault_top_amount_1%"
  - "&f#2 &a%vault_top_name_2%:&f%vault_top_amount_2%"
```

### Tab (TAB Plugin)

```yaml
# header:
  left:
    - "&6⛃ Vault 2.1.2"
  center:
    - "&7Online: &a%server_online%"
  right:
    - "&fNet: &a%vault_net_formatted%"

player-list:
  - "%luckperms_prefix%%player_name% &7| &f%vault_eco_balance_short%"
```

### Dynamic Player-Specific Placeholders

```yaml
# Tables / HDB heads / GUIs:
- "%vault_balance_Notch%"                   # Balance of player Notch
- "%vault_balance_formatted_Notch%"         # Formatted balance of Notch
- "%vault_top_uuid_3%"                       # UUID of #3 richest (for head databases)
```

### Configurable Decimals (0-8 dp)

```yaml
# %vault_ecobalance0dp%   → 1500
# %vault_ecobalance2dp%   → 1500.50
# %vault_ecobalance4dp%   → 1500.5000
# %vault_ecobalance8dp%   → 1500.50000000
```

---

## Internal Expansions

### Vault2PlaceholderExpansion
- **Identifier:** `vault2`
- Handles modern placeholders: bank / interest / tax / net / wallet / loan placeholders + formatted variants + `vault_eco_balance_*` + `vault_ecobalance<N>dp`.

### VaultPlaceholderExpansion
- **Identifier:** `vault`
- Legacy placeholders + compatibility with older setups: `%vault_balance%`, `%vault_top%`, `%vault_balance_<player>%`.

---

## Dependency

Make sure you have PlaceholderAPI installed on the server:

```yaml
# In your server plugin.yml (softdepend):
softdepend:
  - PlaceholderAPI
```

You do **not** need to install any expansion from the eCloud; Vault registers both expansions automatically when PlaceholderAPI is present. To confirm they are loaded:

```
/papi expansions  → search vault / vault2 in the list
/papi parse me %vault_bank_formatted%
/papi parse me %vault_net_formatted%
```
