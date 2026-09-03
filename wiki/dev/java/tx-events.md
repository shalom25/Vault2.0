---
title: Transaction Logs & Events
description: Record transactions with TransactionLogService and listen to events using TxRecord.Builder and TxType types in Vault 2.1.0.
---

# Transaction Logs & Events

Transactional audit system with `TransactionLogService`, custom events, and `TxRecord.Builder`.

## TransactionLogService

Singleton service for recording transactions to daily JSON + MySQL.

```java
import net.milkbowl.vault.services.transaction.TransactionLogService;
import net.milkbowl.vault.services.transaction.TxRecord;
import net.milkbowl.vault.services.transaction.TxType;

@Service
public class MyService {

    private final TransactionLogService tx;

    public MyService(TransactionLogService tx) {
        this.tx = tx;
    }

    public void recordTransfer(UUID from, UUID to, double amount, String currency) {
        TxRecord record = TxRecord.builder()
            .type(TxType.TRANSFER)
            .fromUuid(from)
            .toUuid(to)
            .amount(new BigDecimal(amount))
            .currency(currency)
            .reason("Manual payment")
            .timestamp(System.currentTimeMillis())
            .build();

        tx.record(record); // Persisted asynchronously
    }
}
```

## TxType

Enum with all supported transaction types:

| TxType | Description |
|---|---|
| `DEPOSIT` | Deposit via /deposit or admin |
| `WITHDRAW` | Withdraw via /withdraw or admin |
| `TRANSFER` | /pay payment between players |
| `PAY_PENDING` | Pending offline payment |
| `PAY_PENDING_CLAIMED` | Payment claimed on connect |
| `CHARGE_REQUEST` | Pending charge (request) |
| `CHARGE_PAID` | Approved charge |
| `BANK_DEPOSIT` | Deposit in bank GUI |
| `BANK_WITHDRAW` | Withdrawal from bank GUI |
| `INTEREST` | Bank interest (credit) |
| `TAX` | Movement tax (debit) |
| `NOTE_ISSUED` | Physical bank note created |
| `NOTE_REDEEMED` | Note redeemed |
| `NOTE_DUPE_DETECTED` | HMAC duplicate attempt |
| `LOAN_GRANTED` | Loan granted |
| `LOAN_PAYMENT` | Installment paid |
| `LOAN_DEFAULT` | Loan in default |
| `LOAN_EFFECT_APPLIED` | Default effect activated |
| `ECO_GIVE` | `/eco give` |
| `ECO_TAKE` | `/eco take` |
| `ECO_SET` | `/eco set` |
| `ECO_RESET` | `/eco reset` |
| `IMPORT_FROM_ESSENTIALS` | Essentials import |
| `WORLD_TRANSFER` | Inter-world transfer |
| `ADMIN_ADJUSTMENT` | Manual admin adjustment |

## TxRecord.Builder

```java
// Complete example: loan with installment
TxRecord loan = TxRecord.builder()
    .type(TxType.LOAN_GRANTED)
    .fromUuid(UUID_ZERO)           // From the system
    .toUuid(borrower.getUniqueId())
    .amount(new BigDecimal("5000.00"))
    .currency("coins")
    .reason("Personal loan - 12 installments")
    .extra(extra -> extra
        .put("loan_id", loan.getId())
        .put("installments", 12)
        .put("interest_rate_pct", 15.5)
    )
    .metadata(meta -> meta
        .world("survival")
        .server("srv01")
        .plugin("MyAuthorizerPlugin")
        .ipAddress("192.168.1.45")
    )
    .timestamp(System.currentTimeMillis())
    .build();

tx.record(loan);
```

Fields `extra` and `metadata` are `Map<String, Object>` and serialize freely to JSON.

## Listening to Events

Vault fires `TransactionEvent` events through the EventBus channel.

### TransactionEvent

```java
import net.milkbowl.vault.api.events.TransactionEvent;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

public class MyListener implements Listener {

    @EventHandler
    public void onTransaction(TransactionEvent event) {
        TxRecord tx = event.getTxRecord();

        // Filter by type:
        if (tx.type() == TxType.NOTE_DUPE_DETECTED) {
            Player admin = Bukkit.getPlayer(tx.toUuid());
            if (admin != null) {
                admin.sendMessage("&c⚠ Fraudulent note detected!");
            }
            return;
        }

        // Log to Discord via webhook (your own hooks):
        if (tx.type() == TxType.TRANSFER && tx.amount().doubleValue() > 100_000) {
            DiscordWebhook.send(tx.toString());
        }

        // Cancel (supports Cancellable):
        if (tx.type() == TxType.TRANSFER && isBlacklist(tx.toUuid())) {
            event.setCancelled(true);
            event.setCancelReason("Destination is blacklisted");
        }
    }
}
```

### Register in Plugin

```java
@Override
public void onEnable() {
    getServer().getPluginManager().registerEvents(new MyListener(), this);
}
```

## Specific Event Types

| Event | Fires |
|---|---|
| `BankDepositEvent` | Deposit in bank GUI |
| `BankWithdrawEvent` | Withdrawal in bank GUI |
| `LoanGrantedEvent` | Loan approved |
| `LoanPaymentEvent` | Installment paid |
| `NoteIssuedEvent` | Note created |
| `NoteRedeemedEvent` | Note redeemed |
| `ChargeRequestedEvent` | Pending charge created |
| `ChargePaidEvent` | Charge paid |
| `OfflinePayQueuedEvent` | Offline payment queued |
| `OfflinePayClaimedEvent` | Offline payment claimed |
| `BalanceChangeEvent` | Any balance change |

### Example: Tax on Transfer

```java
import net.milkbowl.vault.api.events.BalanceChangeEvent;

@EventHandler
public void transferTax(BalanceChangeEvent event) {
    if (!event.isWithdraw()) return;

    double amount = event.getAmount();
    double tax = amount * 0.02;   // 2%

    // Apply tax
    SimpleEconomy eco = MyPlugin.getEconomy();
    eco.depositPlayer(
        Bukkit.getOfflinePlayer(UUID.fromString("00000000-0000-0000-0000-000000000000")),
        tax
    );
}
```

## History Queries (TransactionLogService)

```java
// Last 20 transactions for a player
List<TxRecord> recent = tx.lastN(player.getUniqueId(), 20);

// Filtering + pagination (for 45-slot GUI)
Page<TxRecord> page = tx.query()
    .where(player = player.getUniqueId())
    .where(type = TxType.TRANSFER)
    .orderByDesc("timestamp")
    .page(1, 45);   // 45 items for paginated GUI

page.items().forEach(System.out::println);
page.totalPages();
```

## Consistency

- The `TransactionEvent` is **before** persistence. Canceling prevents writing to the log.
- `tx.record()` is asynchronous, non-blocking.
- Daily JSON is written to `plugins/Vault/logs/yyyy-MM-dd.json`.
- MySQL stores in the `vault_transactions` table (if `use_mysql: true`).
