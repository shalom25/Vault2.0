---
title: Languages / i18n
description: 11 official supported languages in Vault v2.1.0 (en, es, pt, de, fr, nl, pl, ru, hi, zh_CN, zh_TW). How to change `config.language`, structure of messages_xx.yml and custom translation.
---

# 🌍 Languages / i18n (v2.1.0)

Vault v2.1.0 ships with 11 official translation files inside `plugins/Vault/messages/`. The active language is controlled by a single key in `config.yml`.

---

## 1. The 11 supported languages

| Code (put in `language:`) | Language | Physical file | Maintainer / Notes |
| :--- | :--- | :--- | :--- |
| `en` | 🇬🇧 English (US/UK) | `messages_en.yml` | Base / reference language. If a key is missing in any other yaml, it falls back to `en`. |
| `es` | 🇪🇸 Spanish (ES/LA) | `messages_es.yml` | Includes command aliases `dinero`, `pagar`, `cofre`, `retirar`, `historial`, `prestamo`. |
| `pt` | 🇵🇹 Portuguese (PT/BR) | `messages_pt.yml` | BR compat. Recommended preset locale: `"pt-BR"`. |
| `de` | 🇩🇪 Deutsch | `messages_de.yml` | Recommended preset locale: `"eu"` (1.000,00). |
| `fr` | 🇫🇷 Français | `messages_fr.yml` | Recommended preset locale: `"fr"` (1 000,00). |
| `nl` | 🇳🇱 Nederlands | `messages_nl.yml` | Locale preset `"eu"` (dot thousands, comma decimal). |
| `pl` | 🇵🇱 Polski | `messages_pl.yml` | Locale preset `"eu"`. |
| `ru` | 🇷🇺 Русский | `messages_ru.yml` | Cyrillic OK; ₽ symbol by default if you change it in `currency.symbol`. |
| `hi` | 🇮🇳 हिन्दी | `messages_hi.yml` | Locale preset `"in"` (1,00,000.00 / lakh grouping). |
| `zh_CN` | 🇨🇳 简体中文 | `messages_zh_CN.yml` | Simplified Chinese. Locale `us`-style (1,000.00). |
| `zh_TW` | 🇹🇼 繁體中文 | `messages_zh_TW.yml` | Traditional Chinese (Taiwan / HK). |

---

## 2. Change the active language

Edit `plugins/Vault/config.yml`:

```yaml
# --------------
# config.yml
# --------------
plugin_version: v2.1.0
language: en           # ← PUT THE CODE HERE
```

Save and run **any** of the 3 reload methods:

| Method | Command | Reloads messages_xx.yml? | Re-reads config.yml? |
| :--- | :--- | :---: | :---: |
| ✅ Vault-specific reload | `/vault reload` | Yes | Yes |
| ⚠️ Global Spigot reload | `/reload confirm` | Yes | Yes (not recommended, breaks loan timers) |
| 🔄 Full restart | Stop and start again | Yes | Yes (safest) |

You will see something like this in console:
```
[Vault] Locale: en  → messages_en.yml loaded   (74 keys)
[Vault] Fallback:    messages_en.yml loaded    (74 keys, for missing keys)
```

> **Note about fallback**: if a key is mistyped or doesn't exist in your `messages_es.yml`, the plugin uses whatever is in `messages_en.yml` so it doesn't show **Missing translation: cmd.common.xxx**.

---

## 3. Typical structure of `messages_xx.yml`

Base file `messages_en.yml` (section summary):

```yaml
prefix: "&6&l[Vault] &r"

cmd:
  common:
    only_players: "&cThis command can only be used in-game."
    invalid_amount: "&cInvalid amount."
    positive_only: "&cAmount must be positive (> 0)."
    insufficient_funds: "&cInsufficient funds."
  balance:
    self: "&7Your balance: &a{amount}"
    other: "&7{player}'s balance: &a{amount}"
  pay:
    usage: "&7Usage: &e/pay <player> <amount>"
    sent: "&aYou sent {amount} &ato &e{player}&a."
    received: "&e{from} &asent you {amount}&a."
  vault:
    no_permission: "&cNo permission. Required: vault.admin"

bank:
  balance:
    self_header: "&6=== Your Accounts ==="
    self_wallet:  "&7Wallet: &f{wallet}"
    self_bank:    "&7Bank:   &f{bank}"
    self_total:   "&7Total:  &a{total}"
    self_interest: "&2Next interest: +{interest_next} &8({interest_pct}% / {interest_min}m)"
    self_tax:     "&4Next tax: -{tax_next} &8(>{tax_threshold} at {tax_pct}% / {tax_min}m)"

note:
  withdraw:
    usage: "&7/vault withdraw <amount> [currency]"
    success: "&aWithdrew {amount} &a({currency}) as a physical note."

loan:
  menu_title: "&8Loans"
  request_title: "&8Request Loan"
  defaulted: "&cYour loan #%id% is DEFAULTED. Repay to clear the effects."

history:
  chat:
    header_top:    "&6================"
    header_title:  "&eTransaction History  &7({page}/{pages})"
    header_bottom: "&6================"
    line_format:   "&8{time} &f{type} {dir}{amount}"
    footer:        "&6================"
```

---

## 4. Translation placeholders (`{key}`)

Inside each `messages_xx.yml` you can use curly braces `{ }` that the plugin replaces **at runtime**. These are **Vault's own syntax**, not PlaceholderAPI.

| Usage example | Replaced by |
| :--- | :--- |
| `{amount}` | Output of `economy.format(value)` (includes symbol, locale, space). |
| `{player}` | Target player name. |
| `{from}` / `{to}` | Sender / recipient in a pay. |
| `{page}` / `{pages}` | Pagination (vaultop, history, top). |
| `{wallet}` / `{bank}` / `{total}` | In bank balance messages. |
| `{currency}` | Currency ID (default, gems, ...) in withdraw note. |
| `{id}` | Loan ID or offline-pay ID. |

---

## 5. Create your own translation (custom language)

Step-by-step to add `messages_it.yml` (Italian, not included):

1. Copy the base language:
   ```
   copy plugins/Vault/messages/messages_en.yml to plugins/Vault/messages/messages_it.yml
   ```
   (PowerShell: `Copy-Item ...`)

2. Edit `messages_it.yml` and translate the values to the right of `:`. **Do not change the keys on the left**. ✅:
   ```yaml
   # OK: translate the VALUE
   cmd.common.only_players: "&cThis command can only be used in-game."
   # ❌ WRONG: DO NOT change the KEY (the left part of the :)
   # cmd.common.only_players_italian: ...
   ```

3. Now tell `config.yml` you want that language:
   ```yaml
   language: it
   ```

4. Run `/vault reload`. If all goes well you'll see:
   ```
   [Vault] Locale: it  → messages_it.yml loaded  (74/74 keys)
   ```
   If it says `(68/74 keys, 6 falling back to en)` it means 6 keys are missing and it's picking up the English text — check for typos.

5. (Optional) Also adjust `currency.locale` so the separators match:
   ```yaml
   currency:
     symbol: "€"
     position: suffix
     locale: "it-IT"   # → 1.234,56 €
   ```

---

## 6. Colors (`&0` … `&f`, `&l`, `&n`, `&k`)

Vault uses the old Bukkit **color codes** with `&`. It also supports **Paper hex codes** (`&#RRGGBB` if your server is Paper 1.16.5+).

Quick reference (put `&` + character):

| Code | Color | Code | Style |
| :--- | :--- | :--- | :--- |
| `&0` | Black | `&k` | Obfuscated (magic) |
| `&1` | Dark Blue | `&l` | **Bold** |
| `&2` | Dark Green | `&m` | ~~Strikethrough~~ |
| `&3` | Dark Aqua | `&n` | Underline |
| `&4` | Dark Red | `&o` | Italic |
| `&5` | Purple | `&r` | Reset (removes color/style) |
| `&6` | Gold | | |
| `&7` | Gray | | |
| `&8` | Dark Gray | | |
| `&9` | Blue | | |
| `&a` | Green | | |
| `&b` | Aqua | | |
| `&c` | Red | | |
| `&d` | Pink | | |
| `&e` | Yellow | | |
| `&f` | White | | |

Example:
```yaml
plugin:
  reloaded: "&a✔ Vault reloaded &7(language: &e{lang}&7)"
```

---

## 7. Checklist: change language from EN → ES (quick)

```yaml
# config.yml
language: es

currency:
  symbol: "€"
  position: suffix
  space: true
  locale: "eu"     # 1.234,56 €
```

```
/vault reload
/eco give YourName 1234.56
/balance
```

You should see: `Your balance: 1.234,56 €` — congratulations, i18n is working.
