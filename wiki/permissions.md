---
title: Permissions
description: Complete permissions table from Vault v2.1.2 plugin.yml: 10 declared perms only — vault.balance/pay/top/loan/withdraw/history/use/bank/eco.admin/admin — plus 4 implicit-only via vault.admin. Children hierarchy, defaults, description.
---

# 🔐 Permissions Reference (v2.1.2)

All permissions exactly as declared in `src/main/resources/plugin.yml:34-83`.

**⚠️ IMPORTANT (v2.1.2 cleanup):**
- ❌ Removed: `vault.eco`, `vault.eco.give`, `vault.eco.take`, `vault.eco.set`, `vault.eco.reset`, `vault.eco.top` — merged into ONE single permission.
- ❌ Removed: `vault.pay.bypass_min` / `vault.pay.bypass_max` — not available as individual perms.
- ❌ Removed: `vault.offlinepay.view` / `vault.offlinepay.refund` — not available as individual perms.
- ❌ Removed: `vault.redeem` — right-click note redeem now requires simply `vault.withdraw` (same permission as generate).

All four features are now obtained **ONLY through `vault.admin` or being OP.**

---

## Permissions table (10 declared + 4 implicit = 14 features total)

| Permission node | Default | Parent / Inherits | Description | Commands / Features that use it |
| :--- | :--- | :--- | :--- | :--- |
| `vault.balance` | `true` | — | Use `/balance` and view your own wallet balance. | `/balance`, PlaceholderAPI `%vault_balance_*%` |
| `vault.pay` | `true` | — | Use `/pay` (direct + menu + Pay + Charge flows). | `/pay`, ChargeRequestService, PayMenuService |
| `vault.top` | `true` | — | Top rankings: `/vaultop` + `/eco top` + `/vault top`. | VaultOpCommand, TopCacheService |
| `vault.loan` | `true` | — | Loan menu + request/pay/status. | `/loan`, `/vault loan`, LoanMenuService, LoanService |
| `vault.withdraw` | `true` | — | **Generate AND redeem physical notes.** <br> (Withdraw command: `/vault withdraw <amount>`. <br>Redeem: right-click any PhysicalNote in hand. | PhysicalNoteService.withdrawNote() + VaultPlugin PlayerInteractEvent |
| `vault.history` | `true` | — | Transaction history GUI or chat. | HistoryMenuService, TransactionLogService |
| `vault.use` | `true` | **Parent meta-permission (grants balance, pay, top, loan, withdraw, history, bank | "User" master. Convenience bundle all player features. | — purely meta: `children=all player features (grants balance/pay/top/loan/withdraw/history/bank simultaneously) |
| `vault.bank` | `true` | Automatically granted if `vault.use` is granted | Bank system: menu, deposit/withdraw, bank_balance, bank top. | `/vault bank` (all subcommands), BankMenuService, BankService |
| `vault.eco.admin` | `op` | — | **Single permission** for ALL of `/eco`. Covers ALL subcommands.** | `/eco` give/add/take/remove/set/reset/top` |
| `vault.admin` | `op` | **Grants EVERY vault.* (all 9 permissions above + all implicit ones below)** | **Master** permission for `/vault` admin. Required to open the main Vault menu. | Everything in VaultCommand.java: config editor GUI, reload, update, resetbalances, offlinepay, offlinepay, bypass limits. |

---

## 🌳 Inheritance tree (children)

Declared in `plugin.yml`:

```
vault.use (default: true — bundle)
├── vault.balance      ← auto-granted
├── vault.pay        ← auto-granted
├── vault.top        ← auto-granted
├── vault.loan       ← auto-granted
├── vault.withdraw   ← auto-granted
├── vault.history   ← auto-granted
└── vault.bank      ← auto-granted

vault.admin (default: op)
├── vault.use       ← all player features
├── vault.eco.admin  ← all /eco
├── [implicit: pay bypass min + max
└── [implicit: offlinepay list + refund)
```

---

## 🎯 Group presets (LuckPerms / GroupManager)

```yaml
# Default group (all players get these anyway because default:true; explicit for clarity):
default:
  permissions:
    - vault.balance
    - vault.pay
    - vault.top
    - vault.loan
    - vault.withdraw
    - vault.history
    - vault.bank

# Or just use ONE line above instead of 7 above.
#   - vault.use

# VIP group (formerly you could give vault.pay.bypass_min/max — now only via vault.admin — or leave it as OP):
vip:
  inherits: [default]
  permissions:
    # No more vault.pay.bypass_* — in v2.1.2 requires vault.admin.

# Admin group:
admin:
  inherits: [default]
  permissions:
    - vault.eco.admin        # ALL /eco
    - vault.admin            # /vault admin + offlinepay + bypass limits + everything above
    # (vault.admin already GIVES vault.use internally, so default can be omitted)
    # Offlinepay view+refund, pay bypass min/max — no longer separate permission anymore

# Owner group (OP or *:
owner:
  inherits: [admin]
  permissions:
    - '*'
```

---

## ⚠️ Implicitly-gated features (NO longer separate perms in v2.1.2)

The following four features in the code check ONLY `isOp() || hasPermission("vault.admin")`:

| Feature | Old separate permission (v2.1.0) — REMOVED | New gate (v2.1.2)
| :--- | :--- | :--- |
| Ignore `pay_limits.min` bypass in /pay + Charge | `vault.pay.bypass_min` | **OP or vault.admin ONLY |
| Ignore `pay_limits.max` bypass in /pay + Charge | `vault.pay.bypass_max` | **OP or vault.admin ONLY |
| `/vault offlinepay` list pending queue | `vault.offlinepay.view` | **OP or vault.admin ONLY |
| `/vault offlinepay refund <id>` | `vault.offlinepay.refund` | **OP or vault.admin ONLY |

If you use a permissions plugin, **grant `vault.admin` explicitly** instead of giving `*` or OP — least privilege.

---

## Permission flow cheatsheet

Quick lookup:

```
✅ Give to normal players (default:true / vault.use or OP/vault.admin
   vault.balance
   vault.pay
   vault.top
   vault.loan
   vault.withdraw  (+ redeem notes → + right-click redeem)
   vault.history
   vault.bank

🔒 Give only admins (vault.eco.admin + vault.admin):
   /eco (all)
   /vault main menu GUI editor + reload/update/resetbalances/offlinepay list|refund
   + pay_limits bypass min and max
```
