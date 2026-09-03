---
title: Scripting Placeholders
description: Quick reference of placeholders with practical examples for scoreboards, tablists, NPCs and scripts.
---

# Scripting Placeholders

Quick reference of placeholders to integrate Vault 2.1.0 into scoreboard, tab, NPC and script plugins.

## Quick Index

| Category | Key placeholder |
|---|---|
| Own balance | `%vault_balance_formatted%` |
| Short balance | `%vault_eco_balance_short%` |
| Top 1-10 | `%vault_top_name_1%` ... `%vault_top_name_10%` |
| External player | `%vault_balance_formatted_Notch%` |
| Currency | `%vault_currency_symbol%` |

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
  - "  &fBalance: &e%vault_balance_formatted%"
  - "  &fAbbreviated: &6%vault_eco_balance_short%"
  - "  &fCurrency: &7%vault_currency_symbol%"
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
  - "%luckperms_prefix%%player_name% &7| &f%vault_eco_balance_short%"

footer:
  - ""
  - "   &fYour money: &e%vault_balance_formatted%   "
  - "   &fTop 1: &a%vault_top_name_1% &8(%vault_top_amount_1%)   "
  - ""
```

---

## Citizens / NPC Example

```yaml
# citizens.yml (with CitizensCMD)
'npc-shop-1':
  messages:
    - "&f[NPC] &aMerchant:"
    - "  &fYour current balance: &e%vault_balance_formatted%"
    - "  &fBuy with /buy!"
```

```java
// Denizen example:
npc_command:
  type: assignment
  actions:
    on click:
      - narrate "<&a>Your balance: <&e>%vault_balance_formatted%"
```

---

## DeluxeMenus / GUI Example

```yaml
# deluxemenus/config.yml
main_menu:
  items:
    balance_item:
      material: GOLD_INGOT
      slot: 13
      name: "&6&lYOUR BALANCE"
      lore:
        - "&7You currently have:"
        - " "
        - "  &fBalance: &e%vault_balance_formatted%"
        - "  &fUnformatted: &6%vault_balance%"
        - "  &fAbbreviated: &6%vault_eco_balance_short%"
        - " "
        - "&7Click to go to the bank"
```

---

## Balance Formats Compared

| Placeholder | Value for 1234567.89 |
|---|---|
| `%vault_balance%` | `1234567.89` |
| `%vault_balance_formatted%` | `$1,234,567.89` |
| `%vault_eco_balance_fixed%` | `1234567.89` |
| `%vault_eco_balance_commas%` | `1,234,567.89` |
| `%vault_eco_balance_short%` | `$1.2M` |
| `%vault_ecobalance2dp%` | `1234567.89` |
| `%vault_ecobalance0dp%` | `1234568` |

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
      - "&fBalance: &e%vault_balance_formatted_Notch%"
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
  - "  &fGold: &e%vault_balance_formatted%"
  - " "
  - "&fPlayers alive: &c%bw_alive%"
  - "&7&m------------------------"
```

---

## PlaceholderAPI in Skript

```skript
# skript with Skript-placeholders addon:
on join:
    set line 1 of player's scoreboard to "&fMoney: &e%vault_balance_formatted%" parsed as placeholder
    set line 2 of player's scoreboard to "&fTop 1: &a%vault_top_name_1%" parsed as placeholder
```

## Validation

To test a placeholder without a scoreboard:

```
/papi parse me %vault_balance_formatted%
/papi parse Notch %vault_balance_formatted_Notch%
/papi parse me %vault_eco_balance_short%
```

Expected output:

```
> %vault_balance_formatted%  →  $15,420.50
> %vault_balance_formatted_Notch%  →  $999,999.00
> %vault_eco_balance_short%  →  $15.4K
```
