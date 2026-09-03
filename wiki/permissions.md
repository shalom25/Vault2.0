---
title: Permissions
description: Complete permissions table from Vault v2.1.0 plugin.yml: vault.balance, vault.pay (bypass_min/max), vault.eco.* (give/take/set/reset/top), vault.top, vault.loan, vault.withdraw/redeem/history, vault.offlinepay.* and vault.admin — children hierarchy, defaults, description.
---

# 🔐 Permissions Reference (v2.1.0)

All permissions exactly as declared in `src/main/resources/plugin.yml:34-94`. `default:` values per Bukkit: `true` (everyone), `false` (nobody), `op` (operators only), `not op`.

---

## Permissions table

| Permission node | Default | Inherits from / Parent | Description | Commands / Features that use it |
| :--- | :--- | :--- | :--- | :--- |
| `vault.balance` | `true` | — | Allows using `/balance` and viewing your own wallet balance. | `/balance`, PlaceholderAPI `%vault_balance_*%` |
| `vault.pay` | `true` | — | Allows using `/pay` (direct payment), the payments menu and the Pay + Charge sub-flows. | `/pay`, ChargeRequestService, PayMenuService |
| `vault.pay.bypass_min` | `op` | — | Ignores `pay_limits.min` (default `1`) when sending or collecting payments. | `/pay` in GUI and chat-input flow |
| `vault.pay.bypass_max` | `op` | — | Ignores `pay_limits.max` (default `100000`) when sending or collecting payments. | `/pay` in GUI and chat-input flow |
| `vault.eco` | `op` | **parent**: groups all `vault.eco.*` | Global permission for the `/eco` command. Automatically assigned if `vault.eco` is granted. | `/eco` with any subcommand |
| `vault.eco.give` | `op` | `vault.eco` (inherited child) | Allows using `/eco give <player> <amount>` and its alias `/eco add`. | `EcoCommand.java` give branch |
| `vault.eco.take` | `op` | `vault.eco` (inherited child) | Allows using `/eco take <player> <amount>` and its alias `/eco remove`. | `EcoCommand.java` take branch |
| `vault.eco.set` | `op` | `vault.eco` (inherited child) | Allows using `/eco set <player> <amount>`. | `EcoCommand.java` set branch |
| `vault.eco.reset` | `op` | `vault.eco` (inherited child) | Allows using `/eco reset <player>` (equivalent to set 0). | `EcoCommand.java` reset branch |
| `vault.eco.top` | `true` | `vault.eco` (inherited child) | Allows using `/eco top [page]` and `/vault top [page] [currency]`. | TopCacheService + EcoCommand top |
| `vault.top` | `true` | — | Allows using `/vaultop [page]` — the classic /baltop-style ranking. | VaultOpCommand |
| `vault.loan` | `true` | — | Allows using `/vault loan` and `/loan` (loan menu, request, pay, status). | LoanMenuService, LoanService |
| `vault.withdraw` | `true` | — | Allows the `/vault withdraw <amount>` command — generate a PhysicalNote from your balance. | PhysicalNoteService.withdrawNote() |
| `vault.redeem` | `true` | — | Allows redeeming a PhysicalNote (right-click with it in hand). If you don't have it, the item won't be consumed. | `PlayerInteractEvent` handler in VaultPlugin |
| `vault.history` | `true` | — | Allows `/vault history [page]` — open GUI or chat of your own transaction history. | HistoryMenuService, TransactionLogService |
| `vault.offlinepay.view` | `op` | — | Allows `/vault offlinepay` or `/vault offlinepay list` — view the pending offline payments queue. | OfflinePayQueueService.listAll() |
| `vault.offlinepay.refund` | `op` | — | Allows `/vault offlinepay refund <id>` — return the offline payment money to the sender. | OfflinePayQueueService.refund(id) |
| `vault.admin` | `op` | — | **Master** permission for the `/vault` command (admin menu, config editor GUI, reload, update, resetbalances, offlinepay). Required to open the main Vault menu from in-game. | Everything that hangs from `/vault` in VaultCommand.java |

---

## 🌳 Inheritance tree (children)

Declared in `plugin.yml` — granting `vault.eco` automatically grants its 5 children:

```
vault.eco (default: op)
├── vault.eco.give   (auto-granted)
├── vault.eco.take   (auto-granted)
├── vault.eco.set    (auto-granted)
├── vault.eco.reset  (auto-granted)
└── vault.eco.top    (auto-granted)
```

---

## 🎯 Group presets (LuckPerms / GroupManager)

Typical example for LuckPerms:

```yaml
# Default group (everyone)
default:
  permissions:
    - vault.balance        # true by default (redundant, but explicit)
    - vault.pay            # true by default
    - vault.eco.top        # true by default — can see /eco top and /vault top
    - vault.top            # true by default — /vaultop
    - vault.loan           # true by default
    - vault.withdraw
    - vault.redeem
    - vault.history

# VIP group (no payment limits)
vip:
  inherits: [default]
  permissions:
    - vault.pay.bypass_min    # Can pay/collect 0.01
    - vault.pay.bypass_max    # Can pay/collect >100,000

# Admin group
admin:
  inherits: [vip]
  permissions:
    - vault.eco              # + eco.give / .take / .set / .reset / .top
    - vault.offlinepay.view
    - vault.offlinepay.refund
    - vault.admin

# Owner group (includes OP + everything else)
owner:
  inherits: [admin]
  permissions:
    - '*'
```

---

## ⚠️ Implicitly used permissions

Although they don't appear in the table above, the code at `VaultCommand.java:56` applies a double check:

```java
if (!p.isOp() && !p.hasPermission("vault.admin")) {
    sender.sendMessage(messages.chat("cmd.vault.no_permission"));
}
```

That is, **both having OP and having `vault.admin`** unlock:

- `/vault` (main menu)
- `/vault reload`
- `/vault update`
- `/vault resetbalances confirm`
- `/vault offlinepay list` and `refund <id>`
- `/vault bank balance <otherPlayer>`

If you use a permissions plugin, **preferably grant `vault.admin` explicitly** instead of giving `*` or global OP — follow the principle of least privilege.
