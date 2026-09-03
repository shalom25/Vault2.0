---
title: Quick Start Guide
description: First steps with Vault 2.1. Installation, basic commands /balance /pay /eco and folder structure.
---

# Quick Start Guide

Vault 2.1 is an internal economy plugin compatible with the classic Vault API (**you don't need Vault.jar**). It includes balances, payments, a bank with interest, loans, physical banknotes, player top charts, multi-world and multi-currency support.

## 1. Installation

1. **Download** `vault-2.1.0-java17.jar` (or `java21.jar` if your server uses Java 21)
2. Place it in your server's `plugins/` folder
3. Restart the server
4. Check the console — you should see:
   ```
   [Vault] Enabling Vault v2.1.0
   [Vault] Internal economy loaded — YAML storage
   ```
   (or `MySQL storage` if you enabled `storage.use_mysql: true`)

### Optional dependencies (softdepend)

- **PlaceholderAPI** → enables placeholders like `%vault_balance_formatted%`
- **SkinsRestorer** → correct skin heads in the `/pay` menu
- **LuckPerms** → better permission management (not mandatory)

## 2. Folders and files created

On first boot, `plugins/Vault/` is generated:

| File                     | Purpose                                                                 |
|--------------------------|-------------------------------------------------------------------------|
| `config.yml`             | General settings, currency, bank, loans, discord, multi-world          |
| `balances.yml`           | Player balances (YAML storage)                                         |
| `bank_balances.yml`      | Independent bank balances                                              |
| `messages/`              | 11 `messages_xx.yml` language files (en, es, fr, de... hi)             |
| `logs/daily-YYYY-MM-DD.json` | Daily JSON log with all transactions                               |
| `offline_pay_queue.yml`  | Pending payments to disconnected players                               |
| `redeemed_notes.yml`     | Already redeemed physical note IDs (anti-dupe)                         |
| `.note-secret.dat`       | 32-byte HMAC key (hidden) — DO NOT DELETE (breaks all notes)           |
| `loans.yml`              | Active/paid/defaulted loans                                            |

## 3. Basic player commands

```
/balance        (aliases /bal /money)  → View your balance
/pay            → Open payment menu with player heads
/pay <player> <amount>  → Pay directly
/vault withdraw <amount> → Withdraw physical paper notes
/vault bank     → Open bank menu (deposit / withdraw / interest)
/vault history  → GUI of your transaction history (45 per page)
/vault top / /vaultop [page] → Richest players ranking
/loan  | /vault loan      → Request loan / pay installment / view status
```

## 4. Administration commands

```
/vault reload              → Reload config + messages (without restart)
/vault update              → Check available updates
/eco give <p> <amount>     → Give money (ADMIN)
/eco take <p> <amount>     → Remove money (ADMIN)
/eco set <p> <amount>      → Set exact balance (ADMIN)
/eco reset <p>             → Set balance to 0 (ADMIN)
/vault offlinepay list     → View pending offline payments
/vault offlinepay refund <id> → Refund an offline payment
/vault import essentials   → Import balances from Essentials (if exists)
```

## 5. Quick permissions

| Permission             | Default | What it does                                            |
|------------------------|---------|---------------------------------------------------------|
| `vault.balance`        | true    | Use `/balance`                                          |
| `vault.pay`            | true    | Use full `/pay` + menu                                  |
| `vault.loan`           | true    | Request and pay loans                                   |
| `vault.withdraw`       | true    | Withdraw notes `/vault withdraw`                        |
| `vault.history`        | true    | View their own `/vault history`                         |
| `vault.eco`            | op      | Full `/eco` (give/take/set/reset)                       |
| `vault.top` / `vault.eco.top` | true    | Ranking                                     |
| `vault.offlinepay.view`| op      | List offline payments                                   |
| `vault.offlinepay.refund`| op    | Refund offline payments                                 |
| `vault.admin`          | op      | Full `/vault`                                           |

## 6. Configure the currency in 1 minute

Edit `config.yml`:

```yaml
language: en                    # Language for messages and Discord embeds
currency:
  symbol: "$"
  position: suffix              # "100 $"
  space: true
  locale: "us"                  # Separators: 1,000.00 (US style)
  abbreviate:
    decimals: 1
    suffix: {k: "k", m: "M", b: "B", t: "T"}
```

Save and run **`/vault reload`** — done.
