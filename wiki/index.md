---
title: Vault Economy v2.1.0
description: Complete internal economy system for Spigot/Paper — Bank System, Physical Notes, Multi-currency, MySQL, Loans, PlaceholderAPI, Discord webhooks and 11 languages. Does not require external Vault.jar.
---

# 🏦 Vault Economy v2.1.0

> Internal Economy provider compatible with the Vault API (net.milkbowl.vault.economy.Economy). Does not require external `Vault.jar`: the plugin exposes its own API and serves as a backend for ShopGUIPlus, Jobs, AureliumSkills and any plugin that depends on Vault.

---

## ✨ Feature Cards

### 💼 Bank System
Independent bank account system (`bank_balance`) with **configurable periodic interest** and **progressive tax** on balances above a threshold. Players can `deposit / withdraw / top` and view their wallet, bank and total.

### 🧾 Physical Notes
Withdraw money from your balance in the form of **physical items** (`/vault withdraw <amount>`) and redeem them by right-clicking. Built-in anti-duplication with grace period, daily JSON logging and UUID per note.

### 💎 Multi-currency
Define secondary currencies (e.g. `gems`) with their own `symbol`, `position`, `locale` and `abbreviate`. The `default` currency is the one exposed by the legacy Vault API for compatibility with third-party plugins.

---

## 🚀 Feature List

| Area | Feature | Description |
| :--- | :--- | :--- |
| 🌍 Languages | **11 official languages** | `en`, `es`, `pt`, `de`, `fr`, `nl`, `pl`, `ru`, `hi`, `zh_CN`, `zh_TW` |
| 🔌 PlaceholderAPI | Built-in placeholders | `%vault_balance_formatted%`, `%vault_top_name_n%`, `%vault_ecobalanceXdp%` and more — auto-registered when PAPI is present |
| 👤 SkinsRestorer | Player heads in menus | `/pay` shows online player heads; uses SkinsRestorer for offline resolution |
| 🗄️ Storage | **YAML + MySQL** | `balances.yml` by default; switch to MySQL via HikariCP (`storage.use_mysql: true`) |
| 🪝 Discord | Webhook notifier | Notifies transactions ≥ `discord.threshold_amount`, plus anti-dupe events and critical errors |
| 💰 Loans | Installment loans | Loan system with installments, interest, `max_missed_payments` and `defaulted_effects` (SLOW, SLOW_DIGGING, etc.) |
| 📊 Top cache | `top.refresh_seconds` | Async cache of the player leaderboard to avoid recalculating on every `/vaultop` |
| 🧾 Transactions | Full log | Dual DAO (`FileTxDAO` + `MySqlTxDAO`), `DailyJsonLogWriter` and history GUI (`/vault history`) |
| 🌍 Worlds | `separate_worlds` | Per-world separate balances (e.g. `world_nether`, `mines`) while maintaining the global legacy API |
| 📥 Import | EssentialsX | `import.essentials.enabled` imports balances from Essentials (`/bal` → Vault) without overwriting by default |
| ⚙️ Admin GUI | Config editor | `/vault` opens a menu to edit `currency.*`, `pay_limits`, `loans.*` without touching YAML |

---

## 📦 Quick Commands

```
/balance          → Your current balance (alias: /bal, /money)
/pay <p> <amt>    → Send money to online player (alias: /send)
/vault bank menu  → Open bank GUI (deposit/withdraw/top)
/vault withdraw X → Withdraw X as a physical note
/vault loan       → Open loans menu
/vaultop          → Top players (alias of the old /balancetop)
```

Next: [First Install →](/first-install)
