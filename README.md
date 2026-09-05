<p align="center">
  <a href="https://bstats.org/plugin/bukkit/vault2/28342">
    <img src="https://img.shields.io/bstats/servers/28342?label=Servers&logo=bstats&color=blue" alt="bStats Servers" />
  </a>
  <img src="https://img.shields.io/github/v/release/shalom25/Vault2.0?display_name=tag" alt="Release" />
  <a href="https://github.com/shalom25/Vault2.0/releases">
    <img src="https://img.shields.io/github/downloads/shalom25/Vault2.0/total?label=GitHub&logo=github&color=gray" alt="GitHub Downloads" />
  </a>
  <a href="https://www.spigotmc.org/resources/129605/">
    <img src="https://img.shields.io/spiget/downloads/129605?label=SpigotMC&logo=spigotmc&color=orange" alt="SpigotMC Downloads" />
  </a>
  <a href="https://modrinth.com/plugin/vault-2.0-economy-plugins">
    <img src="https://img.shields.io/modrinth/dt/vault-2-0-economy-plugins?label=Modrinth&logo=modrinth&color=green" alt="Modrinth Downloads" />
  </a>
  <a href="https://github.com/shalom25/Vault2.0/wiki">
    <img src="https://img.shields.io/badge/Wiki-2563EB?logo=book&logoColor=white" alt="Wiki" />
  </a>
</p>

<p align="center">
  <img src="https://i.imgur.com/HxgguHP.png" alt="Vault 2.0 Banner" />
</p>

---

## What is Vault2.0?

**Vault2.0** is an economy plugin that registers a Bukkit Economy service compatible with the Vault API, allowing other plugins (shops, ranks, etc.) to use money without depending on the original Vault.jar. It includes menus, pay/charge flows, loans, and safe configuration and message reloads.

---

## ❗ IMPORTANT

> **Do NOT run this plugin alongside the original Vault.jar (same plugin name). Remove Vault.jar before starting.**

---

## ✨ Features

- Internal economy with persistence (file storage; optional MySQL).
- `/pay` with GUI and per-player submenu (pay, charge, view balance, loans).
- Loans with GUI wizard (amounts via chat only).
- Defaulted effects configurable (slowness/fatigue, etc.) when a loan defaults.
- `/vault` main menu (Pay / Loan / Settings / Reload / Update).
- Safe reload: `/vault reload` updates `config.yml` and `messages_*.yml` without overwriting your values.
- Multi-language: en, es, fr, de, nl, pt, ru, zh_TW, hi.
- GUI History
- Physical Money
- Offline Pay Queue
- Multi-currency support
- Bank + Interest + Tax
- Clan Accounts / Team Vault (SMP and Factions servers)
- Discord webhook — transactions.log anti-duplicate

---

## 🚀 Installation

1. Copy the `.jar` file to the plugins folder on your server. Start the server to generate the configuration.
2. MySQL compatibility: compatibility with MySQL, allowing users to integrate and manage databases more efficiently.

---

## 🎮 Interactive Menu

### Submenu:
1. **Pay** — send money to a player
2. **Balance** — shows the player's money
3. **Charge** — sends an interactive message to the player with the designated amount (clicking on the message automatically sends the money without using commands).
4. **Bank** — opens the bank menu (deposit/withdraw funds, view interest and tax).

---

## 💸 Loan System

The loan system helps manage the game's finances. Players can apply for loans, manage payments, and view their financial status.

### Request a Loan
To request a loan, open the menu with `/loan` or `/prestamo` and select **Request**. Specify the amount and, if there are installments, also the amount of each one.

### Money Delivery
Upon confirmation, the money is instantly deposited, and the loan is recorded as "active."

### Automatic Collection
The system attempts to collect installments automatically. If there's enough balance, it deducts from the balance.

### View Status
In the menu, the **Status** option shows the outstanding balance and the next payment date.

### Pay Manually
You can use the **Pay** option to pay part or all of the loan at any time.

### Debt
If there's not enough balance to collect, the loan goes into debt. This can cause negative effects until the debt is settled.

This system simplifies financial management in the game, offering control and dynamism.

---

<details>
<summary>GILF</summary>

<p align="center">
  <img src="https://i.imgur.com/4eqasJB.gif" alt="GILF" />
</p>

</details>

---

## 📝 Commands

<details>
<summary>Click to view all Commands</summary>

### Main Commands
```
/balance | /bal | /dinero      -> Show your current balance
/pay | /pagar                   -> Send money to another player (GUI or command)
/loan | /prestamo               -> Open the loan menu GUI
/vaultop                        -> Show top players with the most money
/vault | /v | /cofre | /vault2  -> Vault menu / admin command
```

### /vault Subcommands
```
/vault [main menu]              -> Open the main vault menu
/vault reload                   -> Reload config + messages and add missing sections
/vault update                   -> Check for updates
/vault resetbalances confirm    -> Wipe ALL balances (confirm required)
/vault loan request|pay|status  -> Loan management
/vault top [page]               -> Top richest players
/vault withdraw <amount>        -> Withdraw physical banknotes (HMAC signed)
/vault history                  -> Open transaction history GUI
/vault offlinepay list|refund   -> Manage pending offline payments
/vault bank                     -> Open bank system (see below)
```

### /vault bank Subcommands
```
/vault bank menu|gui|open       -> Open the bank GUI menu
/vault bank balance|bal         -> Show your bank balance
/vault bank deposit|dep <amount>      -> Deposit money into the bank
/vault bank withdraw|with|wd <amount> -> Withdraw money from the bank
/vault bank top [page]          -> Top bank balance rankings
```

### Admin /eco Command
```
/eco give|add <player> <amount>    -> Add money to a player
/eco take|remove <player> <amount> -> Take money from a player
/eco set <player> <amount>         -> Set a player's balance
/eco reset <player>                -> Reset a player's balance to 0
/eco top [page]                    -> Top richest players
```

</details>

---

## 🔐 Permissions

<details>
<summary>Click to view all Permissions</summary>

### Player Permissions (default: true)
```
vault.balance             -> /balance, /bal, /dinero
vault.pay                 -> /pay menu and direct payments
vault.loan                -> /loan, /prestamo and loan features
vault.eco.top             -> /eco top and /vault top
vault.top                 -> /vaultop (top players command)
vault.withdraw            -> /vault withdraw (physical banknotes)
vault.redeem              -> Right-click physical notes to redeem
vault.history             -> /vault history (transaction GUI)
```

### Pay Limits Bypass (default: op)
```
vault.pay.bypass_min      -> Bypass minimum amount limit
vault.pay.bypass_max      -> Bypass maximum amount limit
vault.pay.bypass_limits   -> Alias to bypass min/max limits
```

### Admin Permissions (default: op)
```
vault.eco                 -> Full access to /eco command
  └─ vault.eco.give       -> /eco give | add
  └─ vault.eco.take       -> /eco take | remove
  └─ vault.eco.set        -> /eco set
  └─ vault.eco.reset      -> /eco reset
  └─ vault.eco.top        -> /eco top (also default: true)

vault.admin               -> /vault admin command (reload, update, resetbalances, offlinepay)
vault.offlinepay.view     -> /vault offlinepay list
vault.offlinepay.refund   -> /vault offlinepay refund <id>
```

</details>

---

## 🎯 Placeholders

<details>
<summary>Click to view all Placeholders</summary>

| Placeholder | Description |
|---|---|
| `%vault_balance%` | Current player's raw balance. |
| `%vault_balance_formatted%` | Current player's balance formatted with the plugin's economy format. |
| `%vault_eco_balance%` | Current player's raw economy balance. |
| `%vault_eco_balance_formatted%` | Current player's formatted economy balance. |
| `%vault_eco_balance_fixed%` | Current player's balance with exactly 2 decimal places. |
| `%vault_eco_balance_commas%` | Current player's balance with comma thousand separators. |
| `%vault_eco_balance_short%` | Current player's abbreviated balance, such as `1.2k` or `3.4m`. |
| `%vault_currency_symbol%` | Currency symbol from the plugin config. |
| `%vault_balance_<player>%` | Raw balance of the specified player. |
| `%vault_balance_formatted_<player>%` | Formatted balance of the specified player. |
| `%vault_ecobalance<0-8>dp%` | Current player's balance with a custom number of decimal places. |
| `%vault_top%` | Top 10 richest players as a multiline list. |
| `%vault_top_<n>%` | Full top entry for rank *n*. |
| `%vault_top_name_<n>%` | Player name at rank *n*. |
| `%vault_top_amount_<n>%` | Formatted balance at rank *n*. |

</details>

---

## 🐛 Bug Reports

**Please do not report or post bugs or errors in the review section / comments.**
All reports should be submitted on our **Discord server** or via **GitHub Issues** in this repository.

<p align="center">
  <a href="https://discord.gg/SfKvR4CbUj">
    <img src="https://i.imgur.com/fT5fdFB.png" alt="Discord Invite" width="320" />
  </a>
</p>

---

<p align="center">
  <a href="https://bstats.org/plugin/bukkit/vault2/28342">
    <img src="https://bstats.org/signatures/bukkit/vault2.svg" alt="bStats Plugin Signature" />
  </a>
</p>
