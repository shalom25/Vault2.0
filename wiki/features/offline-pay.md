---
title: Offline Payments
description: Pending payment queue for disconnected players with automatic delivery on login, refund commands, and OFFLINE_PAY_* logs.
---

# Offline Payments (`OfflinePayQueueService`)

When sending a `/pay <player> <amount>` to a disconnected player and `offline-uuid-fallback: true`, instead of rejecting the operation, **money is deducted from the sender immediately** and the payment stays queued for delivery when the recipient connects.

Admin permissions:
- `vault.offlinepay.view` — List the queue (`/vault offlinepay list`, default `op`)
- `vault.offlinepay.refund` — Refund a payment (`/vault offlinepay refund <id>`, default `op`)

## Internal Operation

`OfflinePayQueueService` maintains:

| Structure                 | Type            | Key              | Value               |
|----------------------------|-----------------|------------------|---------------------|
| `queueById`                | ConcurrentHashMap| `id:Long`        | `QueuedPay`         |
| `byToUuid`                 | ConcurrentHashMap| `to:UUID`        | `List<QueuedPay>`    |
| `idSeq`                    | AtomicLong      | —                | Auto-incremental ID |

Persistence in `plugins/Vault/offline_pay_queue.yml`:
```yaml
pending:
  7:
    currency: default
    from: 9b2b...
    from_name: Notch
    to: 550e...
    to_name: Player123
    amount: 250.0
    created_at_ms: 1700000000000
    note: "Last week's debt"
```

### Enqueue Flow

1. `PayCommand` detects offline recipient + `offline-uuid-fallback: true`
2. `SimpleEconomy.withdrawPlayer(...)` withdraws immediately from the sender with `TxType.OFFLINE_PAY_SENT` and metadata `queue_id=<id>`
3. `OfflinePayQueueService.queuePay()` generates `QueuedPay` with sequential numeric ID
4. Message to sender: `Payment of $ 250 queued for Player123 (ID #7). Will be delivered when they log in.`

### Automatic Delivery on Connect

On `PlayerJoinEvent`:
```java
Bukkit.getScheduler().runTask(plugin, () -> deliverFor(p));
```

`deliverFor()` iterates all pending payments for the UUID and for each one:
- `economy.depositPlayer(...)` with `TxType.OFFLINE_PAY_CLAIMED` and note "from <sender>"
- Removes the record from `queueById` and `byToUuid`
- At the end saves the YAML and sends: `You have N pending payment(s) delivered.`

## Admin Commands

### `/vault offlinepay list`
Returns all pending payments ordered by `createdAtMs DESC` (most recent first). Each entry shows ID, sender, recipient, currency, amount, date, and note if present.

### `/vault offlinepay refund <id>`
Refunds the specific payment to the sender:
1. `queueById.remove(id)` and clears from `byToUuid`
2. `economy.depositPlayer()` → `TxType.OFFLINE_PAY_REFUNDED` with reason `refund queue id=<id>`
3. Persists the updated YAML

## Associated Transaction Types

| Event                                    | TxType                  |
|----------------------------------------|-------------------------|
| Payment queued (sender withdrawal)    | `OFFLINE_PAY_SENT`      |
| Recipient claims on login              | `OFFLINE_PAY_CLAIMED`   |
| Admin refunds                          | `OFFLINE_PAY_REFUNDED`  |
