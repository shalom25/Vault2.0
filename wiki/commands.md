---
title: Commands
description: Complete command table for Vault v2.1.0: /balance, /pay, /vault, /vault bank (balance/deposit/withdraw/top/menu), /vault top, /vaultop, /vault history, /vault withdraw, /vault offlinepay, /vault reload, /vault update, /vault resetbalances, /vault loan, /eco, /loan — with Usage, Permission, Alias and Description.
---

# 📜 Commands Reference (v2.1.0)

All commands registered in `plugin.yml` and their sub-actions implemented in `VaultCommand.java`, `EcoCommand.java`, `BalanceCommand.java`, `PayCommand.java`, and `VaultOpCommand.java`.

---

## Command table

| Command | Usage | Required permission | Aliases | Description |
| :--- | :--- | :--- | :--- | :--- |
| **`/balance`** | `/balance` | `vault.balance` (default: `true`) | `bal`, `money` | Shows your current (wallet) balance formatted with `economy.format()`. |
| **`/pay`** | `/pay <player> <amount>` <br> `/pay` (menu) <br> `/pay <player>` (submenu) | `vault.pay` (default: `true`) | `pay` | Sends money to an online player. Opens player menu / Pay+Charge+View submenu. |
| **`/vault`** | `/vault` <br> `/vault reload` <br> `/vault update` <br> `/vault resetbalances confirm` <br> `/vault cancel` | `vault.admin` (default: `op`) | `v`, `chest`, `vault2` | Root admin command. Without args opens the main menu (GUI Config Editor). |
| **`/vault bank balance`** | `/vault bank balance [player]` | `vault.admin` (to view others) <br> anyone for self | `bal` | Shows Wallet + Bank + Total, next interest and estimated tax. |
| **`/vault bank deposit`** | `/vault bank deposit <amount>` | `vault.admin` or anyone (auto) | `dep` | Transfers `<amount>` from wallet → `bank_balance`. |
| **`/vault bank withdraw`** | `/vault bank withdraw <amount>` | anyone | `with`, `wd` | Transfers `<amount>` from `bank_balance` → wallet. |
| **`/vault bank top`** | `/vault bank top [page]` | anyone | — | Player ranking sorted by `bank_balance` (10 per page). |
| **`/vault bank menu`** | `/vault bank menu` | anyone (in-game) | `gui`, `open` | Opens the bank GUI menu (BankMenuService) with deposit/withdraw/top buttons. |
| **`/vault top`** | `/vault top [page] [currencyId]` | `vault.eco.top` (default: `true`) | — | Currency balance top (default = `default`). Uses the `top.refresh_seconds` cache. |
| **`/vaultop`** | `/vaultop [page]` | `vault.top` (default: `true`) | — | Shows top players by money (equivalent to the old `/baltop`). |
| **`/vault history`** | `/vault history [page]` | `vault.history` (default: `true`) | `history` | Opens transaction history GUI or paginated chat format if no `HistoryMenuService`. |
| **`/vault withdraw`** | `/vault withdraw <amount> [currencyId]` | `vault.withdraw` (default: `true`) | `withdraw` | Generates a **PhysicalNote** item redeemable for `<amount>`. Right-click to redeem (permission `vault.redeem`). |
| **`/vault offlinepay list`** | `/vault offlinepay` | `vault.offlinepay.view` (default: `op`) | — | Lists pending offline payment queue (name, id, amount, age). |
| **`/vault offlinepay refund`** | `/vault offlinepay refund <id>` | `vault.offlinepay.refund` (default: `op`) | — | Refunds a specific offline payment (returns money to the original sender). |
| **`/vault reload`** | `/vault reload` | OP or `vault.admin` | — | Reloads `config.yml`, `messages_*.yml`, invalidates the top cache and restarts services without a global `/reload confirm`. |
| **`/vault update`** | `/vault update` | OP or `vault.admin` | — | Manually triggers the update check and announces to the sender if a new release is available. |
| **`/vault resetbalances`** | `/vault resetbalances confirm` <br> (alias `clearbalances`) | OP or `vault.admin` | `clearbalances` | ⚠️ Deletes ALL balances (YAML + MySQL). Requires the word `confirm` after the sub. Also disables `import.essentials.enabled`. |
| **`/vault loan`** | `/vault loan` <br> `/vault loan request` <br> `/vault loan pay` <br> `/vault loan status` | `vault.loan` (default: `true`) | — | Opens loan GUI menu or triggers the request/pay/status chat flow. |
| **`/eco give`** <br> **`/eco add`** | `/eco give <player> <amount>` <br> `/eco add <player> <amount>` | `vault.eco.give` (default: `op`) | — | Adds `<amount>` to the player's balance (online or offline). Log TxType `ADMIN_GIVE`. |
| **`/eco take`** <br> **`/eco remove`** | `/eco take <player> <amount>` <br> `/eco remove <player> <amount>` | `vault.eco.take` (default: `op`) | — | Subtracts `<amount>` from the player's balance (can go negative). Log TxType `ADMIN_TAKE`. |
| **`/eco set`** | `/eco set <player> <amount>` | `vault.eco.set` (default: `op`) | — | Sets `<player>`'s balance to exactly `<amount>`. Log TxType `ADMIN_SET`. |
| **`/eco reset`** | `/eco reset <player>` | `vault.eco.reset` (default: `op`) | — | Resets `<player>`'s balance to 0. Equivalent to `/eco set <player> 0`. |
| **`/eco top`** | `/eco top [page]` | `vault.eco.top` (default: `true`) | — | Balance ranking (same as `/vault top`). Pageable, 10 per page. |
| **`/loan`** | `/loan` | `vault.loan` (default: `true`) | `loan` | Direct alias to the `/vault loan` menu (opens LoanMenuService GUI). |

---

## 🔑 `/vault` command hierarchy

```
/vault
├─ (no args)             → VaultMenuService.openMainMenu()
├─ cancel                → Cancels admin-edit / loan-wizard / loan-conversation flows
├─ reload                → plugin.reloadPluginState()
├─ update                → runUpdateCheckAndAnnounce()
├─ resetbalances confirm → ⚠️ deletes balances.yml + MySQL.balances
├─ top [page] [currency] → baltop ranking with cache
├─ withdraw <amt> [cur]  → PhysicalNoteService.withdrawNote()
├─ history [page]        → HistoryMenuService.openHistory()
├─ offlinepay
│   ├─ (list)            → OfflinePayQueueService.listAll()
│   └─ refund <id>       → OfflinePayQueueService.refund(id)
├─ bank
│   ├─ balance [player]  → shows wallet+bank+total
│   ├─ deposit <amt>     → wallet → bank
│   ├─ withdraw <amt>    → bank → wallet
│   ├─ top [page]        → bank_balance ranking
│   └─ menu              → BankMenu GUI
└─ loan
    ├─ (no args)         → LoanMenuService.openLoanMenu()
    ├─ request           → LoanService.openRequestFlow()
    ├─ pay               → LoanService.openPayFlow()
    └─ status            → LoanService.sendStatus()
```

---

## 💡 Daily usage examples

```yaml
# Regular player
/balance
/pay Alex 250
/vault bank balance
/vault bank deposit 1000
/vault withdraw 500
/vault history
/loan request 10000

# Admin
/eco give Alex 100000
/eco take Alex 5000
/eco set Alex 0
/eco reset Alex
/vault top gems
/vault resetbalances confirm
/vault offlinepay refund 42
/vault reload
```
