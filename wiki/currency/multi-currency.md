---
title: Multi-currency
description: Configure multiple currencies (default + gems + tokens) in Vault v2.1.0 — inheritance of symbol/position/space/locale/abbreviate, default currency order, and real examples with /vault withdraw, /eco give commands and multi-currency PlaceholderAPI.
---

# 💎 Multi-currency (v2.1+)

As of Vault 2.1.0 you can define several currencies simultaneously under `currencies:`. The `default` currency (the first one, or the one literally named `default`) is what Vault's legacy API consumes for third-party plugins (ShopGUIPlus, Jobs Reborn, etc.). The other currencies (gems, tokens, coins_pvp…) are **Vault-specific** and are only handled through their commands and placeholders.

---

## 1. Minimum structure

```yaml
# config.yml
currencies:
  default:
    symbol: "$"
    position: suffix
    space: true
  gems:
    symbol: "💎"
    position: suffix
    space: false
```

This generates:

| Currency Id | Symbol | Example output |
| :--- | :--- | :--- |
| `default` | `$` | `1.234,56 $` |
| `gems` | `💎` | `12💎` |

If the `currencies:` section is **fully commented out / removed`, the plugin falls back to single-currency mode and uses `currency.*` directly (the top-level block).

---

## 2. Inheritance rules (fallback)

Each secondary currency **does not need to repeat every key`. Anything you don't declare in `currencies.<id>.*` falls back up to the generic `currency.*` block.

```yaml
currency:                          # parent fallback
  symbol: "$"
  position: suffix
  space: true
  locale: "eu"
  abbreviate:
    decimals: 1
    suffix: {k: "k", m: "m", b: "b", t: "t"}

currencies:
  default:                          # inherits EVERYTHING from currency.*
    symbol: "$"
    position: suffix
    space: true

  gems:
    symbol: "💎"                    # only override symbol + locale
    position: suffix                # (the rest falls to currency.* → space:true, abbreviate decimals:1)
    space: false
    locale: "us"
    abbreviate:
      decimals: 0                   # override: 0 decimals in abbreviation
      suffix: {k: "K", m: "M", b: "B", t: "T"}

  vip_tokens:
    symbol: "🪙"                     # EVERYTHING else (position, space, locale, abbreviate)
                                     # is inherited from currency.*
```

Resulting inheritance table:

| Currency | position | space | locale | abbreviate.decimals |
| :--- | :--- | :--- | :--- | :--- |
| `default` | `suffix` (explicit) | `true` (explicit) | `eu` (inherited) | `1` (inherited) |
| `gems` | `suffix` (explicit) | `false` (explicit) | `us` (explicit) | `0` (override) |
| `vip_tokens` | `suffix` (inherited) | `true` (inherited) | `eu` (inherited) | `1` (inherited) |

---

## 3. Where is what stored? (storage)

| Currency | YAML `balances.yml` | MySQL `balances` table |
| :--- | :--- | :--- |
| `default` | ✅ `currencies.default.balances.<uuid>: 1234.56` | ✅ `balance_double` column by `(uuid, currency_id = 'default')` |
| `gems`, `vip_tokens`, etc. | ✅ `currencies.gems.balances.<uuid>: 77` | ❌ **Not stored in MySQL in v2.1**. Remain in YAML only (next release). |
| `bank_balance` | `bank.yml` by UUID | ❌ always YAML. |

Therefore in v2.1 **we recommend storing your primary currency in `default`** (the only one that lives in MySQL) and using secondaries for small-scale rewards/events that don't require massive scale.

---

## 4. Commands that accept `<currencyId>`

Some commands accept an optional final argument with the currency id:

```yaml
# Give 500 gems (not "default") to the player
/eco give Alex 500 gems
/eco take  Alex 50 gems

# Remove all gems
/eco set Alex 0 gems
/eco reset Alex gems

# Withdraw a PhysicalNote of 50 gems
/vault withdraw 50 gems

# Top of the gems currency
/vault top 1 gems

# Specific placeholders (v2 Placeholder Expansion)
%vault2_balance_gems%               → 50  (raw)
%vault2_balance_formatted_gems%     → 50💎
%vault2_top_name_3_gems%            → 3rd name of the gems ranking
%vault2_top_amount_3_gems%          → 12,3K 💎
```

If you omit `<currencyId>` the command assumes `default`.

---

## 5. Full example: server with $ + Gems + PvP Tokens

```yaml
currency:
  symbol: "$"
  position: suffix
  space: true
  locale: "eu"
  abbreviate:
    decimals: 1
    suffix: {k: "k", m: "M", b: "B", t: "T"}

currencies:
  default:
    symbol: "$"
    position: suffix
    space: true
  gems:
    symbol: "💎"
    position: suffix
    space: false
    locale: "us"
    abbreviate:
      decimals: 0
      suffix: {k: "K", m: "Mio", b: "B", t: "T"}
  pvptokens:
    symbol: "🗡️"
    position: prefix
    space: true
    locale: "eu"
    abbreviate:
      decimals: 0
      suffix: {k: "K", m: "M", b: "B", t: "T"}
```

Rendered output:

| Value in `default` | Formatted |
| :--- | :--- |
| `1234567.89` | `1.234.567,89 $` |
| `1500.0` | `1,5k $` |

| Value in `gems` | Formatted |
| :--- | :--- |
| `42` | `42💎` |
| `1500` | `2K💎` |

| Value in `pvptokens` | Formatted |
| :--- | :--- |
| `7` | `🗡️ 7` |
| `9800` | `🗡️ 10K` |

---

## 6. Multi-currency placeholders (Vault2PlaceholderExpansion)

When PlaceholderAPI is present, every `<cid>` defined in `currencies.<cid>` gains these placeholders automatically:

```
%vault2_balance_<cid>%                  → raw amount
%vault2_balance_formatted_<cid>%        → eco.format(cid, amount)
%vault2_currency_symbol_<cid>%          → currencies.<cid>.symbol
%vault2_top_name_<n>_<cid>%             → player at position n of the <cid> ranking
%vault2_top_amount_<n>_<cid>%           → their formatted balance
%vault2_top_full_<n>_<cid>%             → full line: "n. Name  —  1,2k 💎"
```

E.g. `%vault2_balance_formatted_gems%` → `42💎`.
