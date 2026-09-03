---
title: Player-to-Player Payments (/pay)
description: Direct payment system between players with interactive GUI menu, configurable limits, offline payments, and charge requests.
---

# Player-to-Player Payments (`/pay`)

The `/pay` command (alias: `/pagar`) allows transferring money between players either directly or via an interactive GUI menu. It integrates **SkinsRestorer** skins to display custom player heads in the menu.

## Usage Methods

### 1. Direct Command Payment

```
/pay <player> <amount> [note]
```

Transfers money instantly to the target player if they are online. If the player is offline and `offline-uuid-fallback: true`, the payment is queued as an **offline payment** (see corresponding section).

**Applicable Permissions**:
- `vault.pay` — Enables general use of `/pay` (default: `true`)
- `vault.pay.bypass_min` — Bypasses the minimum limit (default: `op`)
- `vault.pay.bypass_max` — Bypasses the maximum limit (default: `op`)

### 2. Main Menu (`/pay` without arguments)

Run `/pay` by itself to open the menu listing all online players as **player heads** (`PLAYER_HEAD`). The inventory size is configured in `pay_menu.size` (multiple of 9, default 27).

Head skin resolution follows this priority order (PayMenuService.java:92):
1. Live player `GameProfile` (reflection)
2. Modern `PlayerProfile` API
3. **SkinsRestorer API** — If the plugin is installed, extracts skin texture/signature
4. Legacy `setOwningPlayer` / `setOwner` fallback

With `pay_menu.show_self: false` (default) your own head is excluded from the list.

### 3. Player Sub-menu (`/pay <player>`)

Clicking on a head or running `/pay Notch` opens a 9-slot sub-menu with 4 actions (PayMenuService.java:424-477):

| Slot | Item           | Action                                           |
|------|----------------|--------------------------------------------------|
| 1    | Emerald        | **Pay** — Opens Quick Pay menu                   |
| 3    | Paper          | **View balance** — Shows target's balance        |
| 5    | Redstone       | **Charge** — Request payment (clickable)         |
| 7    | Gold Ingot     | **Loan** — Direct access to loan menu            |

### 4. Quick Pay and Chat Input Charge

After selecting **Pay** or **Charge**, the plugin enters chat input mode. Type the amount and press Enter. To cancel, type `cancel` or `cancelar` (configurable in `pay.prompt.cancel_words`).

The limits `pay_limits.min` (1) and `pay_limits.max` (100000) are applied both to chat input and the direct command. The `bypass_min` / `bypass_max` permissions (or the wildcard `vault.pay.bypass_limits`) ignore them.

## Payment Limits

In `config.yml`:

```yaml
pay_limits:
  min: 1
  max: 100000
```

If the value is `≤ 0`, the limit is disabled. The `pay.amount_too_small` / `pay.amount_too_large` errors use `%min%` / `%max%` placeholders formatted with the currency symbol.

## Charge Requests

When choosing **Charge** in the sub-menu, the plugin sends the recipient a **clickable message with `RUN_COMMAND`**:
```
/pay <requester> <amount>
```

If the recipient is offline, the request is stored:
- **In memory** (`pendingByRecipient`) if no MySQL
- **In MySQL table `vault_charge_requests`** if `storage.use_mysql: true`

On login, a maximum of `pay_pending.max_on_join: 5` requests are delivered; the rest remain queued for the next connection.
