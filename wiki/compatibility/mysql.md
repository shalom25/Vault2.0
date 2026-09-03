---
title: MySQL
description: MySQL storage configuration in Vault 2.1.0: vault_balances, vault_world_balances, vault_charge_requests tables, connection pool, and asynchronous operations.
---

# MySQL (Storage)

Vault 2.1.0 supports MySQL storage with a HikariCP connection pool and 100% asynchronous operations.

## Enable MySQL

```yaml
# config.yml
storage:
  use_mysql: true

  mysql:
    host: "localhost"
    port: 3306
    database: "minecraft"
    username: "root"
    password: "passw0rd"
    table_prefix: "vault_"
    pool_size: 10
    use_ssl: false
    connection_timeout: 30000
    idle_timeout: 600000
    max_lifetime: 1800000
```

## Automatically Created Tables

### Table `vault_balances` (Global per currency)

| Column | Type | Description |
|---|---|---|
| `uuid` | `VARCHAR(36)` | Player UUID (PK, together with currency) |
| `currency` | `VARCHAR(32)` | Currency identifier |
| `balance` | `DECIMAL(28,8)` | Current balance |

```sql
CREATE TABLE IF NOT EXISTS vault_balances (
    uuid VARCHAR(36) NOT NULL,
    currency VARCHAR(32) NOT NULL DEFAULT 'coins',
    balance DECIMAL(28,8) NOT NULL DEFAULT 0.00000000,
    PRIMARY KEY (uuid, currency),
    INDEX idx_uuid (uuid),
    INDEX idx_currency (currency)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

### Table `vault_world_balances` (Multi-world)

| Column | Type | Description |
|---|---|---|
| `world` | `VARCHAR(64)` | World name |
| `uuid` | `VARCHAR(36)` | Player UUID |
| `currency` | `VARCHAR(32)` | Currency identifier |
| `balance` | `DECIMAL(28,8)` | Balance per world |

```sql
CREATE TABLE IF NOT EXISTS vault_world_balances (
    world VARCHAR(64) NOT NULL,
    uuid VARCHAR(36) NOT NULL,
    currency VARCHAR(32) NOT NULL DEFAULT 'coins',
    balance DECIMAL(28,8) NOT NULL DEFAULT 0.00000000,
    PRIMARY KEY (world, uuid, currency)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

### Table `vault_charge_requests` (Pending charges)

| Column | Type |
|---|---|
| `id` | `BIGINT AUTO_INCREMENT PK` |
| `from_uuid` | `VARCHAR(36)` |
| `to_uuid` | `VARCHAR(36)` |
| `amount` | `DECIMAL(28,8)` |
| `reason` | `VARCHAR(255)` |
| `currency` | `VARCHAR(32)` |
| `created_at` | `BIGINT` (epoch ms) |
| `expires_at` | `BIGINT` |
| `status` | `VARCHAR(16)` (PENDING/PAID/EXPIRED) |

```sql
CREATE TABLE IF NOT EXISTS vault_charge_requests (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    from_uuid VARCHAR(36) NOT NULL,
    to_uuid VARCHAR(36) NOT NULL,
    amount DECIMAL(28,8) NOT NULL,
    reason VARCHAR(255),
    currency VARCHAR(32) NOT NULL DEFAULT 'coins',
    created_at BIGINT NOT NULL,
    expires_at BIGINT NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    INDEX idx_to_status (to_uuid, status),
    INDEX idx_expires (expires_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

## Connection Pool (HikariCP)

Recommended `pool_size` based on CPU cores:

| CPU Cores | Recommended pool_size |
|---|---|
| 2 | 6 |
| 4 | 10 |
| 8 | 16 |
| 16+ | 24 |

## Lifecycle (onEnable / onDisable)

```java
// onEnable → Asynchronous, does not block tick

@Override
public void onEnable() {
    // 1. HikariDataSource on async scheduler
    this.asyncExecutor.execute(() -> {
        hikariDataSource = setupHikari(config);
        runFlywayMigrations(hikariDataSource);
        bulkLoadAllBalances();  // SELECT * FROM vault_balances
    });
}

// onDisable → RAM Snapshot → Bulk UPDATE

@Override
public void onDisable() {
    // Close everything asynchronously with safe timeout:
    snapshotAllCurrencyDataToMySQL();
    hikariDataSource.close();
    asyncExecutor.shutdown();
    asyncExecutor.awaitTermination(5, TimeUnit.SECONDS);
}
```

## Bulk Loads at Startup

```java
// Bulk load avoids N+1 queries per player:
private CompletableFuture<Void> bulkLoadAllBalances() {
    return CompletableFuture.runAsync(() -> {
        try (Connection conn = hikariDataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "SELECT uuid, currency, balance FROM vault_balances");
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                UUID uuid = UUID.fromString(rs.getString("uuid"));
                String currency = rs.getString("currency");
                BigDecimal balance = rs.getBigDecimal("balance");
                currencyDataCache.put(uuid, currency, balance);
            }
        }
    }, asyncExecutor);
}
```

## Write-Behind Cache

```yaml
storage:
  write_behind: true
  flush_interval_ticks: 6000   # every 5 min
  dirty_queue_size: 1000            # or when 1000 changes accumulate
```

```java
// Each deposit/withdraw marks dirty → periodic flush:
public void markDirty(UUID uuid, String currency) {
    dirtySet.add(new Pair<>(uuid, currency));
    if (dirtySet.size() >= dirty_queue_size) {
        flushDirtyBatch();
    }
}
```

## ACID Transactions

```java
public void transfer(UUID from, UUID to, BigDecimal amount, String currency) {
    try (Connection conn = hikariDataSource.getConnection()) {
        conn.setAutoCommit(false);
        try {
            updateBalance(conn, from, currency, amount.negate());
            updateBalance(conn, to,   currency, amount);
            conn.commit();
        } catch (SQLException e) {
            conn.rollback();
            throw e;
        }
    }
}
```

## YAML Failover

If MySQL fails at runtime, Vault enters **temporary YAML mode**:

```
[Vault] ⚠ MySQL disconnected. Activating YAML fallback (read only)
[Vault] Reconnecting in 30s...
```

## Verification

```
/vault admin storage
---
Storage: MySQL (HikariCP)
  ✓ Active: 10/10 connections
  ✓ Tables: 3/3 OK
  ✓ Last flush: 2s ago (dirty: 45)
  ✓ Write-behind: 6000 ticks
```
