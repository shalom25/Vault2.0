<p align="center">
  <a href="https://bstats.org/plugin/bukkit/vault2/28342"><img src="https://img.shields.io/bstats/servers/28342?label=Servers&logo=bstats&color=blue" alt="bStats Servers"></a>
  <img src="https://img.shields.io/github/v/release/shalom25/Vault2.0?display_name=tag" alt="Release">
  <a href="https://github.com/shalom25/Vault2.0/releases"><img src="https://img.shields.io/github/downloads/shalom25/Vault2.0/total?label=GitHub&logo=github&color=gray" alt="GitHub Downloads"></a>
  <a href="https://www.spigotmc.org/resources/129605/"><img src="https://img.shields.io/spiget/downloads/129605?label=SpigotMC&logo=spigotmc&color=orange" alt="SpigotMC Downloads"></a>
  <a href="https://modrinth.com/plugin/vault-2.0-economy-plugins"><img src="https://img.shields.io/modrinth/dt/vault-2.0-economy-plugins?label=Modrinth&logo=modrinth&color=green" alt="Modrinth Downloads"></a>
  <a href="https://github.com/shalom25/Vault2.0/wiki"><img src="https://img.shields.io/badge/Wiki-2563EB?logo=book&logoColor=white" alt="Wiki"></a>
  <a href="https://vault2-0.mintlifysite.com"><img src="https://img.shields.io/badge/Docs-Mintlify-2563EB?logo=readme&logoColor=white" alt="Mintlify Docs"></a>
  <img src="https://img.shields.io/badge/Java-17%20%2F%2021-orange?logo=openjdk" alt="Java 17 / 21">
  <img src="https://img.shields.io/badge/Spigot-1.8.8%20--%201.21.x-red?logo=spigotmc" alt="Spigot 1.8.8 – 1.21.x">
</p>

<p align="center">
  <img src="https://i.imgur.com/HxgguHP.png" alt="Vault2.0 Banner">
</p>

## 📋 What is Vault2.0?

**Vault2.0** is an economy plugin that registers a Bukkit Economy service compatible with the Vault API, allowing other plugins (shops, ranks, etc.) to use money without depending on the original `Vault.jar`. It includes **Banking System**, menus, pay/charge flows, loans, HMAC anti-dupe physical notes, top-players cache, multi-world, EssentialsX importer, Discord webhooks and safe configuration/message reloads.

---

## 🔴 IMPORTANT

> ⚠️ **Do NOT run this plugin alongside the original Vault.jar (same plugin name). Remove `Vault.jar` before starting your server!**

---

## ✨ Features (v2.1.0 complete)

- 🏦 **COMPLETE BANKING SYSTEM** with GUI, Quick-Pick buttons & master toggles `bank.interest/tax.enabled`
- 💳 **Player Loans** with auto installments + DEFAULTED debuff effects (SLOW / Mining Fatigue)
- 💵 **HMAC Anti-Dupe Physical Banknotes** with i18n label validation across 11 languages
- 🧾 **Transaction History GUI** (YAML or MySQL backends) · 🏆 **Async cached Baltop / Top Players**
- 💱 **Multi-Currency** · 🗺️ **Per-World Separate Balances** · 🚚 **EssentialsX Migration Importer**
- 🔔 **Discord Webhook Notifications** · 📜 **24 brand-new PlaceholderAPI expansions**
- Internal economy with persistence (file storage; optional MySQL).
- `/pay` with GUI and per-player submenu (pay, charge, view balance, loans).
- Loans with GUI wizard (amounts via chat only).
- Defaulted effects configurable when a loan defaults.
- `/vault` main menu (Pay / Loan / Settings / Bank / Reload / Update).
- **Safe reload:** `/vault reload` updates `config.yml` and `messages_*.yml` without overwriting your values.
- **Multi-language (11):** EN · ES · DE · FR · IT · PT · RU · ZH · JA · KO · PL
- GUI History
- Physical Money
- Offline Pay Queue
- Multi-currency support
- Bank + Interest + Tax
- Clan Accounts / Team Vault (SMP and Factions servers)
- Discord webhook · transactions.log anti-duplicate

---

## 📥 Installation

1. Copy the `.jar` file to the `plugins` folder on your server. Start the server to generate the configuration.
2. MySQL compatibility: compatibility with MySQL, allowing users to integrate and manage databases more efficiently.
3. Choose your build:
   - 🟠 **Java 21** (Paper 1.20.6 – 1.21.x): `vault-2.1.0-java21.jar` (RECOMMENDED)
   - 🟡 **Java 17** (Legacy 1.8.8 – 1.20.4): `vault-2.1.0-java17.jar`

---

## 🖱️ Interactive Menu

### Submenu:
1. **Pay** → send money to a player
2. **Balance** → shows the player's money
3. **Charge** → sends an interactive message to the player with the designated amount (clicking on the message automatically sends the money without using commands).
4. **Bank** → open banking GUI deposit/withdraw/top.

---

## 💳 Loan System

The loan system helps manage the game's finances. Players can apply for loans, manage payments, and view their financial status.

**Request a Loan**  
To request a loan, open the menu with `/loan` or `/prestamo` and select **Request**. Specify the amount and, if there are installments, also the amount of each one.

**Money Delivery**  
Upon confirmation, the money is instantly deposited, and the loan is recorded as *"active."*

**Automatic Collection**  
The system attempts to collect installments automatically. If there's enough balance, it deducts from the balance.

**View Status**  
In the menu, the **Status** option shows the outstanding balance and the next payment date.

**Pay Manually**  
You can use the **Pay** option to pay part or all of the loan at any time.

**Debt / Default**  
If there's not enough balance to collect, the loan goes into debt. This can cause negative effects until the debt is settled.

This system simplifies financial management in the game, offering control and dynamism.

<details>
<summary>🎞️ Click to see GIF demo</summary>
<p align="center">
  <img src="https://i.imgur.com/4eqasJB.gif" alt="Vault2.0 GIF Preview">
</p>
</details>

---

## 🔧 Commands (v2.1.0 · 14 NEW commands!)

<details>
<summary>👉 Click to view ALL Commands</summary>

### Vault Admin command (`/vault` — aliases: `v`, `cofre`, `vault2`)

| Command | Alias | What it does | Permission |
|---|---|---|---|
| `/vault` *(no args)* | — | **Open the Vault MAIN MENU GUI** | `vault.balance` (players) |
| `/vault reload` | — | Reload config.yml + messages_*.yml, adding missing sections | `vault.admin` (OP) |
| `/vault update` | — | Check Modrinth for updates | `vault.admin` (OP) |
| `/vault resetbalances confirm` | `clearbalances confirm` | **WIPE all balances** (irreversible) | `vault.admin` (OP) |
| `/vault clearbalances confirm` | — | (alias of resetbalances) | `vault.admin` (OP) |
| `/vault top [page] [currency]` | — | **Baltop / Top Players** async cached (10 per page) | `vault.eco.top` (default true) |
| `/vault withdraw <amount> [currency]` | `retirar` | **Withdraw PHYSICAL HMAC BANKNOTES** (anti-duplicate) | `vault.withdraw` (default true) |
| `/vault history [page]` | `historial` | **Transaction History** GUI / chat fallback | `vault.history` (default true) |
| `/vault offlinepay` | — | List pending offline-pay queue | `vault.offlinepay.view` (OP) |
| `/vault offlinepay refund <id>` | — | Refund an offline payment by ID | `vault.offlinepay.refund` (OP) |

### 🏦 Banking subcommand (`/vault bank` — aliases: `bal`, `dep`, `with`, `wd`, `gui`, `open`, `menu`)

| Command | Alias | What it does | Permission |
|---|---|---|---|
| `/vault bank` · `/vault bank menu` | `gui` / `open` | **BANK MAIN GUI** (Quick Picks deposit/withdraw + interest/tax status) | `vault.balance` |
| `/vault bank balance [player]` | `bal` | View Wallet + Bank + Total (yours or other player's) | `vault.balance` |
| `/vault bank deposit <amount>` | `dep` | Deposit from your wallet → bank | `vault.balance` |
| `/vault bank withdraw <amount>` | `with` / `wd` | Withdraw from bank → your wallet | `vault.balance` |
| `/vault bank top [page]` | — | **Top Bank Balances** leaderboard | `vault.eco.top` |

### 💳 Loans subcommand (`/vault loan` · `/loan` · aliases: `prestamo`)

| Command | Alias | What it does | Permission |
|---|---|---|---|
| `/vault loan` · `/loan` | `prestamo` | **Open Loans MENU GUI** | `vault.loan` (default true) |
| `/vault loan request` | — | Wizard: request a new loan (amount + installments) | `vault.loan` |
| `/vault loan pay` | — | Wizard: manually pay a loan installment | `vault.loan` |
| `/vault loan status` | — | View your active loans / next payment date | `vault.loan` |

### Other commands

| Command | Alias | What it does | Permission |
|---|---|---|---|
| `/pay <player> <amount>` | `pagar` | **Send money** or open pay GUI / charge GUI | `vault.pay` (default true) |
| `/pay` *(no args)* | — | Open Pay GUI with online player list | `vault.pay` |
| `/balance` · `/bal` · `/dinero` | — | Show your current formatted balance | `vault.balance` (default true) |
| `/vaultop [page]` | — | Legacy **baltop** alias | `vault.top` (default true) |
| `/eco give <player> <amount>` · `/eco add` | `economy` / `economia` | Admin: add money to a player | `vault.eco.give` (OP) |
| `/eco take <player> <amount>` · `/eco remove` | `economy` / `economia` | Admin: take money from a player | `vault.eco.take` (OP) |
| `/eco set <player> <amount>` | `economy` / `economia` | Admin: SET exact balance | `vault.eco.set` (OP) |
| `/eco reset <player>` | `economy` / `economia` | Admin: reset a player balance to 0 | `vault.eco.reset` (OP) |
| `/eco top [page]` | `economy` / `economia` | Admin: baltop | `vault.eco.top` |
| `/vault cancel` | — | **Cancel wizard flow** (loan request / charge / admin menu) | any |

</details>

---

## 🔐 Permissions (v2.1.0 · 16 NEW nodes!)

<details>
<summary>👉 Click to view ALL Permissions (granular!)</summary>

| Permission | Default | What it does |
|---|---|---|
| `vault.balance` | `true` | Use `/balance` · deposit/withdraw/balance bank |
| `vault.pay` | `true` | Use `/pay` (both send money + GUI + Charge request flow) |
| `vault.pay.bypass_min` | `op` | **BYPASS minimum** pay_limits.min amount |
| `vault.pay.bypass_max` | `op` | **BYPASS maximum** pay_limits.max amount |
| `vault.loan` | `true` | Use `/vault loan` · `/loan` (request/pay/status + GUI) |
| `vault.withdraw` | `true` | Use `/vault withdraw` (physical HMAC notes) |
| `vault.redeem` | `true` | Right-click a physical note to redeem it |
| `vault.history` | `true` | Use `/vault history` (transactions GUI) |
| `vault.top` | `true` | Use `/vaultop` (legacy baltop) |
| `vault.eco.top` | `true` | Use `/vault top` + `/eco top` + `/vault bank top` |
| `vault.eco` | `op` | **Parent** for all admin `/eco` commands |
| `vault.eco.give` | `op` | `/eco give / add` |
| `vault.eco.take` | `op` | `/eco take / remove` |
| `vault.eco.set` | `op` | `/eco set` |
| `vault.eco.reset` | `op` | `/eco reset` |
| `vault.offlinepay.view` | `op` | `/vault offlinepay` list pending queue |
| `vault.offlinepay.refund` | `op` | `/vault offlinepay refund <id>` |
| `vault.admin` | `op` | `/vault reload` · `/vault update` · `/vault resetbalances` |

</details>

---

## 🧩 Placeholders (v2.1.0 · 24 NEW expansions!)

<details>
<summary>👉 Click to view ALL Placeholders (PlaceholderAPI required)</summary>

| Placeholder | Description |
|---|---|
| `%vault_balance%` | Current player's raw numeric balance. |
| `%vault_balance_formatted%` | Current player's balance FULLY formatted with the plugin's economy format (symbol + locale + thousands separators). |
| `%vault_eco_balance%` | Alias: current player's raw numeric economy balance. |
| `%vault_eco_balance_formatted%` | Alias formatted economy balance. |
| `%vault_eco_balance_fixed%` | Current player's balance with EXACTLY **2 decimal places**. |
| `%vault_eco_balance_commas%` | Current player's balance with **comma thousand separators** (US style). |
| `%vault_eco_balance_short%` | Current player's **ABBREVIATED balance**, such as `1.2k` · `3.4m` · `5.6b` · `7.8t`. Uses currency.abbreviate suffixes from config.yml. |
| `%vault_currency_symbol%` | Currency symbol from the plugin config (e.g. `$`, `€`, `💎`). |
| `%vault_balance_<player>%` | **CROSS-QUERY:** raw numeric balance of the specified player. |
| `%vault_balance_formatted_<player>%` | **CROSS-QUERY:** formatted balance of the specified player. |
| `%vault_ecobalance<0-8>dp%` | **CUSTOM DECIMALS:** balance with any number of decimal places from 0 to 8 (e.g. `%vault_ecobalance3dp%` → `12.456`). |
| `%vault_top%` | **Top 10 richest players** as a multi-line block list (perfect for Scoreboards/Holograms). |
| `%vault_top_<n>%` | **Full top entry** for rank *n* (formatted line: rank# · player name · amount). |
| `%vault_top_name_<n>%` | **Just the player NAME** at baltop rank *n*. |
| `%vault_top_amount_<n>%` | **Just the FORMATTED BALANCE** at baltop rank *n*. |
| `%vault_bank_balance%` | **Current bank balance raw** (v2.1+ new). |
| `%vault_bank_balance_formatted%` | Bank balance formatted (v2.1+ new). |
| `%vault_total_assets%` | Wallet + Bank = TOTAL assets raw (v2.1+ new). |
| `%vault_total_assets_formatted%` | Total assets formatted (v2.1+ new). |
| `%vault_loan_count%` | Number of active loans the player has (v2.1+ new). |
| `%vault_loan_debt_total%` | Total outstanding loan debt raw (v2.1+ new). |
| `%vault_loan_debt_total_formatted%` | Total loan debt formatted (v2.1+ new). |
| `%vault_world%` | Current balance world context name (per-world bal feature v2.1+ new). |
| `%vault_currency_name%` | Active currency name ("default", "gems", etc. for multi-currency v2.1+ new). |

</details>

---

## 🔴 Bug Reports

> **Please do not report or post bugs or errors in the review section / comments.**  
> All reports should be submitted on our **Discord server** or via **GitHub Issues** in this repository.

<p align="left">
  <a href="https://discord.gg/SfKvR4CbUj"><img src="https://i.imgur.com/fT5fdFB.png" alt="Vault Discord Banner"></a>
</p>

---

<p align="center">
  <a href="https://bstats.org/plugin/bukkit/vault2/28342">
    <img src="https://bstats.org/signatures/bukkit/vault2.svg" alt="bStats Signature — Vault2">
  </a>
</p>
