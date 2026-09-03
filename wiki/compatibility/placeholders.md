---
title: PlaceholderAPI
description: Complete list of PlaceholderAPI placeholders supported by Vault 2.1.0, including balance, baltop, currency, and dynamic placeholders.
---

# PlaceholderAPI

Vault 2.1.0 integrates two PlaceholderAPI expansions: **Vault2PlaceholderExpansion** and **VaultPlaceholderExpansion**, with over 25 placeholders for balance, player leaderboards, and currency symbols.

## Registered Placeholders

| Placeholder | Description | Return |
|---|---|---|
| `%vault_balance%` | Player balance in the default currency | `double` |
| `%vault_balance_formatted%` | Formatted balance with symbol | `String` |
| `%vault_eco_balance%` | Alias of `%vault_balance%` | `double` |
| `%vault_eco_balance_formatted%` | Alias of `%vault_balance_formatted%` | `String` |
| `%vault_eco_balance_fixed%` | Balance with 2 fixed decimals | `String` |
| `%vault_eco_balance_commas%` | Balance with thousands separators | `String` |
| `%vault_eco_balance_short%` | Abbreviated balance format (1.2K, 3.4M) | `String` |
| `%vault_currency_symbol%` | Default currency symbol | `String` |
| `%vault_balance_<player>%` | Balance of a specific player | `double` |
| `%vault_balance_formatted_<player>%` | Formatted balance of a player | `String` |
| `%vault_ecobalance<0-8>dp%` | Balance with N decimals (0 to 8) | `String` |
| `%vault_top%` | Complete /baltop list (Top 10) | `String` |
| `%vault_top_<n>%` | Nth entry of the top: `Name - Amount` | `String` |
| `%vault_top_name_<n>%` | Player name at position N | `String` |
| `%vault_top_amount_<n>%` | Amount at position N of the leaderboard | `String` |

## Usage Examples

### Scoreboard

```yaml
# Example in config.yml of a scoreboard plugin
lines:
  - "&6&lECONOMY"
  - "&fBalance: &e%vault_balance_formatted%"
  - "&fTop 1: &a%vault_top_name_1%"
  - "&f  %vault_top_amount_1%"
  - " "
  - "&fTop 2: &a%vault_top_name_2%"
  - "&f  %vault_top_amount_2%"
```

### Tab (TAB Plugin)

```yaml
# header:
  left:
    - "&fMoney: &6%vault_balance_formatted%"
```

### Dynamic Player-Specific Placeholders

```yaml
# Placeholders for tables and lists:
- "%vault_balance_Notch%"       # Balance of player Notch
- "%vault_balance_formatted_Notch%"  # Formatted balance of Notch
```

### Configurable Decimals (0-8 dp)

```yaml
# %vault_ecobalance0dp%  → 1500
# %vault_ecobalance2dp%  → 1500.50
# %vault_ecobalance4dp%  → 1500.5000
```

## Internal Expansions

### Vault2PlaceholderExpansion
- **Identifier:** `vault2`
- Manages modern placeholders (`%vault_eco_balance_fixed%`, `%vault_eco_balance_short%`, `%vault_ecobalance<N>dp%`)

### VaultPlaceholderExpansion
- **Identifier:** `vault`
- Legacy placeholders and compatibility (`%vault_balance%`, `%vault_top%`, `%vault_balance_<player>%`)

## Dependency

Make sure you have PlaceholderAPI installed:

```yaml
# In your server plugin.yml:
softdepend:
  - PlaceholderAPI
```

Register the placeholders with:
```
/papi ecloud download Vault
```
