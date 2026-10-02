---
title: Scripting Placeholders
description: Quick reference of placeholders with practical examples for scoreboards, tablists, NPCs and scripts. Includes bank, interest, tax, net-worth, loan and wallet placeholders added in v2.1.2.
---

# Scripting Placeholders

Quick reference of placeholders to integrate **Vault 2.1.2** into scoreboard, tab, NPC and script plugins.

## Quick Index

| Category | Key placeholder |
|---|---|
| Own balance | `%vault_balance_formatted%` |
| Short balance | `%vault_eco_balance_short%` |
| Bank balance (NEW in v2.1.2) | `%vault_bank_formatted%` |
| Interest (NEW) | `%vault_interest_formatted%` |
| Tax (NEW) | `%vault_tax_formatted%` |
| Net worth (NEW) | `%vault_net_formatted%` |
| Wallet (NEW, alias) | `%vault_wallet_formatted%` |
| Loan total (NEW) | `%vault_loan_total_formatted%` |
| Loan remaining (NEW) | `%vault_loan_remainder_formatted%` |
| Installments left (NEW) | `%vault_loan_installments_left%` |
| Top 1-10 | `%vault_top_name_1%` ... `%vault_top_name_10%` |
| External player | `%vault_balance_formatted_Notch%` |
| Currency | `%vault_currency_symbol%` |

---

## 🆕 Complete placeholders (v2.1.2 — 38+)

### Wallet / Economy balance (all variants)

| Placeholder | Value for `$1,234,567.89` |
|---|---|
| `%vault_balance%` / `%vault_balance_raw%` | `1234567.89` |
| `%vault_balance_fixed%` | `1234567.89` |
| `%vault_balance_int%` | `1234568` |
| `%vault_balance_commas%` | `1,234,567.89` |
| `%vault_balance_short%` | `1.2m` |
| `%vault_balance_formatted%` | `$ 1,234,567.89` |
| `%vault_eco_balance%` (all 6 variants above) | same values (eco_ prefix alias) |
| `%vault_ecobalance<0-8>dp%` | custom 0..8 decimals |

### 🏦 Bank placeholders (NEW in v2.1.2)

Same 6 variants — replace `balance` with `bank`:

| Placeholder |
|---|
| `%vault_bank%` / `%vault_bank_raw%` |
| `%vault_bank_fixed%` / `%vault_bank_int%` |
| `%vault_bank_commas%` / `%vault_bank_short%` |
| `%vault_bank_formatted%` |

### 💸 Interest & Tax (NEW)

| Placeholder | Description |
|---|---|
| `%vault_interest%` (all 6 variants) | Interest the player will receive in the next bank cycle. |
| `%vault_tax%` (all 6 variants) | Tax the player will pay (if their balance is over `bank.tax.threshold`). |

### 💰 Net worth & Wallet alias (NEW)

| Placeholder | Description |
|---|---|
| `%vault_net%` (all 6 variants) | **Net worth** = wallet + bank - remaining active loans. |
| `%vault_wallet%` (all 6 variants) | Explicit alias for vault_balance (wallet only, NOT including bank). |

### 💳 Loan placeholders (NEW)

| Placeholder | Description |
|---|---|
| `%vault_loan_total%` (all 6 variants) | Total principal of the active loan. |
| `%vault_loan_remainder%` (all 6 variants) | Remaining debt (remaining principal). |
| `%vault_loan_installment_amount%` (all 6 variants) | Cost per installment. |
| `%vault_loan_installments_left%` | Integer installments remaining. |
| `%vault_loan_installments_left_formatted%` | Formatted remaining installments. |
| `%vault_loan_interest%` (all 6 variants) | Total interest still to be paid. |

### Top rankings & Misc

| Placeholder | Description |
|---|---|
| `%vault_top%` | Top 10 players (multiline list). |
| `%vault_top_<n>%` | Full entry for rank `<n>`. |
| `%vault_top_name_<n>%` | Player name at rank `<n>`. |
| `%vault_top_amount_<n>%` | Formatted balance at rank `<n>`. |
| `%vault_top_uuid_<n>%` | **(NEW in v2.1.2)** UUID at rank `<n>`. |
| `%vault_currency_symbol%` | Currency symbol from config. |
| `%vault_balance_<player>%` | Raw balance for a specific named player. |
| `%vault_balance_formatted_<player>%` | Formatted balance for a specific named player. |

> **TIP:** There is also a `%vault2_<rest>%` ALIAS expansion (vault2_bank, vault2_loan_remainder …) that works **identically** to the `vault_` ones — use whichever prefix you prefer.

---

## Scoreboard Example (FeatherBoard / AnimatedScoreboard)

```yaml
# scoreboard.yml
title: "&6&l⛃ SERVER ECONOMY"
lines:
  - "&7&m----------------------"
  - " "
  - "&fWelcome, &a%player_name%"
  - " "
  - "&6➤ YOUR MONEY"
  - "  &fWallet: &e%vault_wallet_formatted%"
  - "  &fBank:   &6%vault_bank_formatted%"
  - "  &fNet:    &a%vault_net_formatted%"
  - "  &fAbbreviated: &6%vault_eco_balance_short%"
  - " "
  - "&6➤ BANK CYCLE"
  - "  &fInterest: &a+%vault_interest_formatted%"
  - "  &fTax:      &c-%vault_tax_formatted%"
  - " "
  - "&6➤ LOAN STATUS"
  - "  &fLoan:     &7%vault_loan_total_formatted%"
  - "  &fRemaining:&c%vault_loan_remainder_formatted%"
  - "  &fLeft:     &e%vault_loan_installments_left% installments"
  - " "
  - "&6➤ TOP ECONOMY"
  - "  &8#1 &a%vault_top_name_1%: &f%vault_top_amount_1%"
  - "  &8#2 &a%vault_top_name_2%: &f%vault_top_amount_2%"
  - "  &8#3 &a%vault_top_name_3%: &f%vault_top_amount_3%"
  - " "
  - "&7&m----------------------"
  - "&fmc.yourserver.com"
```

---

## TabList Example (TAB Plugin / BungeeTabListPlus)

```yaml
# tablist.yml
header:
  - ""
  - "   &6⛃ &lVAULT ECONOMY v2.1   "
  - "   &7%server_online% players online   "
  - ""

player-list:
  - "%luckperms_prefix%%player_name% &7| &f%vault_eco_balance_short% &7/ &6%vault_bank_short%"

footer:
  - ""
  - "   &fYour money: &e%vault_balance_formatted%   "
  - "   &fNet worth: &a%vault_net_formatted%   "
  - "   &fTop 1: &a%vault_top_name_1% &8(%vault_top_amount_1%)   "
  - ""
```

---

## Citizens / NPC Example

```yaml
# citizens.yml (with CitizensCMD)
'npc-bank-teller-1':
  messages:
    - "&f[NPC] &6Bank Teller:"
    - "  &fYour wallet: &e%vault_wallet_formatted%"
    - "  &fIn bank:    &6%vault_bank_formatted%"
    - "  &fNet worth:  &a%vault_net_formatted%"
    - "  &fRun &6/vault bank &fto manage!"
```

```java
// Denizen example:
npc_command:
  type: assignment
  actions:
    on click:
      - narrate "<&6>Bank: <&e>%vault_bank_formatted%"
      - narrate "<&c>Loan left: <&e>%vault_loan_remainder_formatted%"
```

---

## DeluxeMenus / GUI Example

```yaml
# deluxemenus/config.yml
bank_menu:
  items:
    bank_balance_item:
      material: EMERALD_BLOCK
      slot: 13
      name: "&6&lBANK BALANCE"
      lore:
        - "&7Account summary:"
        - " "
        - "  &fWallet:   &e%vault_wallet_formatted%"
        - "  &fIn bank:  &6%vault_bank_formatted%"
        - "  &fNet:      &a%vault_net_formatted%"
        - " "
        - "  &fNext interest: &a+%vault_interest_formatted%"
        - "  &fNext tax:      &c-%vault_tax_formatted%"
        - " "
        - "&aClick to open /vault bank"
```

---

## Balance Formats Compared

| Placeholder | Value for `$1,234,567.89` |
|---|---|
| `%vault_balance%` | `1234567.89` |
| `%vault_balance_formatted%` | `$ 1,234,567.89` |
| `%vault_eco_balance_fixed%` | `1234567.89` |
| `%vault_eco_balance_commas%` | `1,234,567.89` |
| `%vault_eco_balance_short%` | `$ 1.2m` |
| `%vault_ecobalance2dp%` | `1234567.89` |
| `%vault_ecobalance0dp%` | `1234568` |
| **`%vault_bank_formatted%`** (NEW) | `$ 5,000,000.00` |
| **`%vault_net_formatted%`** (NEW) | `$ 6,234,567.89` |

---

## External Player Placeholders Example

```yaml
# HeadDatabase / HDB + TAB:
# Show Notch's balance on a player head:

heads:
  notch:
    id: "MHF_Notch"
    name: "&6Notch &7(Admin)"
    lore:
      - "&fWallet: &e%vault_balance_formatted_Notch%"
      - "&fNet:    &a%vault_net_formatted%"
      - "&fGlobal top: &a#%vault_top_Notch%"
```

---

## PVP BedWars Scoreboard Example

```yaml
# bedwars_scoreboard.yml
title: "&c&l⚔ BEDWARS"
lines:
  - "&7&m------------------------"
  - " "
  - "&fMap: &eSkyIsland"
  - "&fYour team: &cRed"
  - " "
  - "&6⛃ Economy"
  - "  &fGold:   &e%vault_balance_formatted%"
  - "  &fBank:   &6%vault_bank_formatted%"
  - "  &fNet:    &a%vault_net_formatted%"
  - " "
  - "&fPlayers alive: &c%bw_alive%"
  - "&7&m------------------------"
```

---

## PlaceholderAPI in Skript

```skript
# skript with Skript-placeholders addon:
on join:
    set line 1 of player's scoreboard to "&fWallet: &e%vault_wallet_formatted%" parsed as placeholder
    set line 2 of player's scoreboard to "&fBank:   &6%vault_bank_formatted%" parsed as placeholder
    set line 3 of player's scoreboard to "&fNet:    &a%vault_net_formatted%" parsed as placeholder
    set line 4 of player's scoreboard to "&fTop 1: &a%vault_top_name_1%" parsed as placeholder
```

## Validation

To test a placeholder without a scoreboard:

```
/papi parse me %vault_balance_formatted%
/papi parse me %vault_bank_formatted%
/papi parse me %vault_loan_remainder_formatted%
/papi parse me %vault_net_formatted%
/papi parse Notch %vault_balance_formatted_Notch%
/papi parse me %vault_eco_balance_short%
```

Expected output:

```
> %vault_balance_formatted%  →  $ 15,420.50
> %vault_bank_formatted%     →  $ 250,000.00
> %vault_loan_remainder_formatted% → $ 1,200.00
> %vault_net_formatted%      →  $ 264,220.50
> %vault_balance_formatted_Notch%  →  $ 999,999.00
> %vault_eco_balance_short%  →  $ 15.4K
```
