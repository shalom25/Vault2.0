---
title: Transaction Log
description: Transaction logging system with 24 TxType values, dual file/MySQL DAO, daily JSON logs, and paginated /vault history interface.
---

# Transaction Log

`TransactionLogService` records every economic operation of the plugin. It uses an asynchronous queue architecture with anti-duplicate support and two persistence backends: **YAML file** (`FileTxDAO`) or **MySQL** (`MySqlTxDAO`).

## `TxType` Enum — 24 Actual Values

Defined in `TxType.java`:

| Category        | Values                                                                     |
|-----------------|---------------------------------------------------------------------------|
| Player Payments | `PLAYER_PAY`, `CHARGE_PAID`                                           |
| Admin           | `ADMIN_SET`, `ADMIN_ADD`, `ADMIN_REMOVE`, `ADMIN_RESET`                |
| Wallet          | `DEPOSIT`, `WITHDRAW`                                                   |
| Notes           | `NOTE_WITHDRAW`, `NOTE_REDEEM`                                         |
| Offline Pay     | `OFFLINE_PAY_SENT`, `OFFLINE_PAY_CLAIMED`, `OFFLINE_PAY_REFUNDED`       |
| Bank            | `BANK_DEPOSIT`, `BANK_WITHDRAW`, `INTEREST`, `TAX`                     |
| Loans           | `LOAN_DISBURSE`, `LOAN_REPAY`, `LOAN_DEFAULT`, `LOAN_COLLATERAL_SEIZED`  |
| Teams           | `TEAM_DEPOSIT`, `TEAM_WITHDRAW`, `TEAM_DISBAND_REFUND`                 |

Each `TxRecord` carries the fields: `txId`, `serial`, `instantMs`, `txType`, `currencyId`, `fromUuid`, `toUuid`, `amount`, `worldName`, `metadata` (key/value Map).

## Asynchronous Write Architecture

```
record(r) → antiDupeWindow → pending queue (ConcurrentLinkedDeque)
                                    ↓ (flush every 5s × 20 ticks or when batchSize=500 is reached)
                           TransactionLogFlusher thread
                                    ↓
                      ┌────────────────────────────────┐
                      │  insertBatch dao (File/MySQL)        │
                      └────────────────────────────────┘
                                    ↓
                           DailyJsonLogWriter
```

- **Anti-dupe window**: `AntiDupeWindow` based on `serial` + `txId`. Duplicates trigger `fireDupe()` (subscribers include Discord webhook).
- **Batch size**: 500 records; reaching this threshold forces an immediate flush.
- **Dedicated thread**: `VaultTxFlusher` (SingleThreadExecutor, daemon).
- **Retry**: if DAO write fails, records are re-queued at the front for later retry.

## Persistence Backends

### FileTxDAO (YAML files)

Local persistence without dependencies. Useful for backups, development, or small servers.

### MySqlTxDAO (MySQL)

Requires `storage.use_mysql: true`. Table automatically created by `Database.ensureSchema()`:

```sql
CREATE TABLE IF NOT EXISTS vault_transactions (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  tx_id VARCHAR(64) UNIQUE,
  serial BIGINT UNIQUE,
  ts TIMESTAMP,
  tx_type VARCHAR(32),
  currency_id VARCHAR(32) DEFAULT 'default',
  from_uuid CHAR(36),
  to_uuid CHAR(36),
  amount DECIMAL(19,4),
  world_name VARCHAR(64),
  metadata_json TEXT
);
```

Automatic indexes: `idx_vt_from`, `idx_vt_to`, `idx_vt_ts`, `idx_vt_currency`.

## Daily JSON Logs

`DailyJsonLogWriter` creates one file per day at `plugins/Vault/logs/daily-yyyy-MM-dd.json`:
```json
{"txId":"...","serial":42,"txType":"PLAYER_PAY","from":"...","to":"...","amount":100.0,...}
```

A new file is opened automatically when crossing midnight. Each flusher batch is appended to the current day.

## History GUI: `/vault history`

Permission `vault.history` (default `true`). Opens `HistoryMenuService` with a **54-slot** inventory and **45 transactions per page**:

| Slot    | Function                                                             |
|---------|--------------------------------------------------------------------|
| 0–44    | FILLED_MAP / MAP items per transaction                              |
| 45      | Previous page                                                      |
| 49      | Info (page / total)                                                |
| 50      | Close                                                              |
| 53      | Next page                                                          |

Each item shows:
- Color/title by direction: incoming (+), outgoing (-), neutral (admin set)
- Lore with txId (12 chars), from/to, world, date `dd/MM/yyyy HH:mm`, metadata
- Placeholders `%sign%` `%amount%` `%currency%` `%txid%` `%time%`

## `vault_charge_requests` Table (MySQL)

Independent of the log, stores pending charges for offline players:

```sql
CREATE TABLE IF NOT EXISTS vault_charge_requests (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  recipient VARCHAR(16),
  sender VARCHAR(16),
  amount DECIMAL(19,4),
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_vcr_recipient (recipient)
);
```
