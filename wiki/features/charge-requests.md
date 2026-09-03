---
title: Clickable Charge Requests
description: Clickable payment requests sent via chat, MySQL or in-memory persistence, and max_on_join limit at login.
---

# Clickable Charge Requests

Unlike **direct payment** (sender sends money), the **charge request** (payment request) sends the target a clickable message so that **they** make the payment. Useful for shops, events, or for a newcomer to request money without touching the `/pay` command.

Initiated from:
- Player sub-menu → **Charge** (Redstone) → chat flow to enter amount
- Or via internal API `ChargeRequestService.startRequest(sender, target)`

## Online Charge Flow

If the recipient is connected, they receive a Spigot `TextComponent` with:
- `ClickEvent.Action.RUN_COMMAND` → `/pay <requester> <amount>`
- `HoverEvent.Action.SHOW_TEXT` → details

The requester sees `pay.request.sent` confirmation; if the recipient clicks and has enough money, the `TxRecord` is written as `CHARGE_PAID`.

## Offline Charge Persistence

If the recipient is disconnected, the request is NOT lost. The chosen storage depends on `storage.use_mysql`:

| Mode        | Store                                                  | Class / Table                    |
|-------------|--------------------------------------------------------|----------------------------------|
| No MySQL    | `ConcurrentHashMap<String,List<PendingRequest>>` in RAM (lost on restart) | `pendingByRecipient` |
| With MySQL  | `vault_charge_requests` table on disk                  | `Database.addChargeRequest()`    |

```sql
vault_charge_requests
├── id            BIGINT PK AUTO_INCREMENT
├── recipient     VARCHAR(16)
├── sender        VARCHAR(16)
├── amount        DECIMAL(19,4)
└── created_at    TIMESTAMP DEFAULT CURRENT_TIMESTAMP
   INDEX idx_vcr_recipient(recipient)
```

When MySQL is enabled and the write fails due to SQLException, there is **silent in-memory fallback** so the request is not lost.

## Delivery on Login: `max_on_join`

`PlayerJoinEvent` → `ChargeRequestService.onJoin()` delivers pending charges but **never more than `pay_pending.max_on_join`** per session:

```yaml
pay_pending:
  max_on_join: 5   # max 5 charges per login
```

If more are pending:
- Memory mode: first N are removed; the rest are re-inserted into `pendingByRecipient`
- MySQL mode: only those delivered by `fetchAndDeletePendingRequests(recipient, limit)` are deleted from the table; others wait for the next login

For each delivered charge the player sees:
```
You have 12 pending charge(s).
[Showing 5/12 - the rest will arrive on your next login]
[Charge] Click HERE to pay $ 500 to ShopNPC
```

## Cancellation and Chat Input

During amount input via chat (before sending the request):
- Typing `cancel` or `cancelar` aborts (configurable: `pay.prompt.cancel_words`)
- `pay_limits.min / max` limits apply — bypass with `vault.pay.bypass_*`

The command `/pay cancel` (and alias cancelar) directly cancels the active session.
