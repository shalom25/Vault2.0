---
title: Commands
description: Complete command table for Vault v2.1.2: /balance, /pay, /vault, /vault bank (menu by default + balance/deposit/withdraw/top), /vault top, /vaultop, /vault history, /vault withdraw, /vault offlinepay, /vault reload, /vault update, /vault resetbalances, /vault loan, /eco, /loan — with Usage, Permission, Alias and Description.
---

# 📜 Commands Reference (v2.1.2)

All commands registered in `plugin.yml` and their sub-actions implemented in `VaultCommand.java`, `EcoCommand.java`, `BalanceCommand.java`, `PayCommand.java`, and `VaultOpCommand.java`.

---

## Command table

| Command | Usage | Required permission | Aliases | Description |
| :--- | :--- | :--- | :--- | :--- |
| **`/balance`** | `/balance` | `vault.balance` | `bal`, `money` | Shows your current (wallet) balance formatted with `economy.format()`. |
| **`/pay`** | `/pay <player> <amount>` <br> `/pay` (menu) <br> `/pay <player>` (submenu) | `vault.pay` | `pay` | Sends money to an online player. Opens player menu / Pay+Charge+View submenu. |
| **`/vault`** | `/vault` <br> `/vault reload` <br> `/vault update` <br> `/vault resetbalances confirm` <br> `/vault cancel` | `vault.admin` | `v`, `chest`, `vault2` | Root admin command. Without args opens the main menu (GUI Config Editor). |
| **`/vault bank`** | `/vault bank` (default → GUI) | `vault.bank` | — | **Opens the bank GUI menu directly by default** (same as `/vault bank menu`). |
| **`/vault bank balance`** | `/vault bank balance [player]` | `vault.bank` + `vault.admin` to view others | `bal` | Shows Wallet + Bank + Total, next interest and estimated tax. |
| **`/vault bank deposit`** | `/vault bank deposit <amount>` | `vault.bank` | `dep` | Transfers `<amount>` from wallet → `bank_balance`. |
| **`/vault bank withdraw`** | `/vault bank withdraw <amount>` | `vault.bank` | `with`, `wd` | Transfers `<amount>` from `bank_balance` → wallet. |
| **`/vault bank top`** | `/vault bank top [page]` | `vault.bank` | — | Player ranking sorted by `bank_balance` (10 per page). |
| **`/vault bank menu`** | `/vault bank menu` | `vault.bank` (in-game) | `gui`, `open` | Opens the bank GUI menu (BankMenuService) with deposit/withdraw/top buttons. |
| **`/vault top`** | `/vault top [page] [currencyId]` | `vault.top` | — | Currency balance top (default = `default`). Uses the `top.refresh_seconds` cache. |
| **`/vaultop`** | `/vaultop [page]` | `vault.top` | — | Shows top players by money (equivalent to the old `/baltop`). |
| **`/vault history`** | `/vault history [page]` | `vault.history` | `history` | Opens transaction history GUI or paginated chat format if no `HistoryMenuService`. |
| **`/vault withdraw`** | `/vault withdraw <amount> [currencyId]` | `vault.withdraw` | `withdraw` | Generates a **PhysicalNote** item redeemable for `<amount>`. Right-click to redeem. |
| **`/vault offlinepay list`** | `/vault offlinepay` | `vault.admin` (implicit) | — | Lists pending offline payment queue (name, id, amount, age). |
| **`/vault offlinepay refund`** | `/vault offlinepay refund <id>` | `vault.admin` (implicit) | — | Refunds a specific offline payment (returns money to the original sender). |
| **`/vault reload`** | `/vault reload` | OP or `vault.admin` | — | Reloads `config.yml`, `messages_*.yml`, invalidates the top cache and restarts services without a global `/reload confirm`. |
| **`/vault update`** | `/vault update` | OP or `vault.admin` | — | Manually triggers the update check and announces to the sender if a new release is available. |
| **`/vault resetbalances`** | `/vault resetbalances confirm` <br> (alias `clearbalances`) | OP or `vault.admin` | `clearbalances` | ⚠️ Deletes ALL balances (YAML + MySQL). Requires the word `confirm` after the sub. Also disables `import.essentials.enabled`. |
| **`/vault loan`** | `/vault loan` <br> `/vault loan request` <br> `/vault loan pay` <br> `/vault loan status` | `vault.loan` | — | Opens loan GUI menu or triggers the request/pay/status chat flow. |
| **`/eco` (all subcommands)** | `/eco give/add` <br> `/eco take/remove` <br> `/eco set` <br> `/eco reset` <br> `/eco top` | `vault.eco.admin` (single permission) | `economy`, `economia` | **One permission covers all 5 `/eco` subcommands.** Works online or offline, auto-creates missing accounts. |
| **`/loan`** | `/loan` | `vault.loan` | `prestamo` | Direct alias to the `/vault loan` menu (opens LoanMenuService GUI). |

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
│   ├─ (list)            → OfflinePayQueueService.listAll()       (vault.admin only)
│   └─ refund <id>       → OfflinePayQueueService.refund(id)      (vault.admin only)
├─ bank
│   ├─ (no args)         → BankMenu GUI **(default, no extra word needed!)**
│   ├─ balance [player]  → shows wallet+bank+total
│   ├─ deposit <amt>     → wallet → bank
│   ├─ withdraw <amt>    → bank → wallet
│   ├─ top [page]        → bank_balance ranking
│   └─ menu | gui | open → BankMenu GUI
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
/vault bank                    # → opens GUI directly!
/vault bank deposit 1000
/vault withdraw 500
/vault history
/loan request 10000

# Admin
/eco give Alex 100000          # vault.eco.admin single perm
/eco take Alex 5000
/eco set Alex 0
/eco reset Alex
/vault top gems
/vault resetbalances confirm
/vault offlinepay refund 42
/vault reload
```
