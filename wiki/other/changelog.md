---
title: Changelog v2.1.0
description: Complete list of new features, fixes, and changes in Vault 2.1.0 compared to the previous version v1.7.1.
---

# Vault Changelog

## Version 2.1.0

Release date: **Q3 2025** | Dual build: Java 17 (legacy) + Java 21 (modern) | Platform: Modrinth `rj9SgaYL`

### ✅ New Features

- ✅ **GUI Bank System**: Complete bank menu with commands, configurable daily interest, and transaction tax.
- ✅ **Physical Notes with PDC + HMAC anti-dupe**: Physical items signed with HMAC-SHA256 (5 fields) and i18n parsing in 11 languages.
- ✅ **GUI Transaction History**: Pagination in `/vault history` with 45 slots per page, daily JSON storage, and MySQL table.
- ✅ **/eco admin command + async /baltop cache**: `give / take / set / reset` with in-memory cache + async rebuild.
- ✅ **Multi-currency**: `balances.yml` with N currency definitions (coins, gems, tokens, etc.), individual symbol and format.
- ✅ **Multi-world MySQL**: `vault_world_balances` table with world-isolated balances + configurable inheritance.
- ✅ **Offline payments + pending charges**: `/pay` queued until recipient connects, `vault_charge_requests` table.
- ✅ **YAML Loans (loans.yml)**: Installments, interest rate, default effects when unpaid, automatic collection.
- ✅ **Discord Webhooks embeds**: 11 languages with images, colors, and custom placeholders.
- ✅ **11 languages**: messages_en/es/pt/fr/de/it/ru/zh/ja/ko/ar.yml with strict i18n lint.
- ✅ **PlaceholderAPI 25+ placeholders**: balance, formatted, fixed, commas, short, top, top_name, top_amount, ecobalance<0-8>dp.
- ✅ **SkinsRestorer GUI heads /pay**: Real skin heads via legacy 1.8 reflection and live premium/non-premium profile.
- ✅ **Essentials import**: `/vault import essentials` from EssentialsX / Essentials 2.x.
- ✅ **Offline UUID fallback**: Premium/non-premium mode compatible, cracked UUID correctly detected.
- ✅ **Modrinth Update checker**: ID `rj9SgaYL`, release/beta/alpha channel, async check every 12h.
- ✅ **Dual Java build**:
  - **Legacy**: Java 17, supports Minecraft 1.8.8 – 1.20.4.
  - **Modern**: Java 21, supports Minecraft 1.20.5 – 1.21+.

### 🔧 Internal Changes

- Refactor `VaultPlugin.getEconomyProvider()` → modern `SimpleEconomy` interface in addition to legacy `Economy`.
- Write-behind cache of 6000 ticks + HikariCP pool (configurable MySQL pool_size).
- `TransactionLogService` with `TxRecord.builder()` builder and 25 `TxType`.
- HikariCP connections `vault_balances`, `vault_world_balances`, `vault_charge_requests`, `vault_transactions`.
- FlywayDB migrations for incremental schema upgrades.
- Services auto-inject (`@Service` annotation) via reflection in `onEnable`.

### 🧪 Performance Improvements

- Balances loaded in bulk at boot (`SELECT * FROM vault_balances` only once).
- Flush dirty batches instead of querying per operation.
- Async /baltop, does not block main tick in 10k+ player mode.
- `Messages.prefixed()` uses per-locale cache, no string regeneration every tick.

### 🛡 Security

- HMAC notes with unique UUID nonce (anti-replay).
- ChatInput sanitization (XSS chat color exploit) using `ChatInputSanitizer`.
- Optional Discord Webhook HMAC signature (shared secret).
- SHA512 checksum in the Modrinth updater before moving the JAR.

---

## Previous Version: v1.7.1 (LTS)

### ✅ Fixes included in 1.7.1 (backported)

| Fix | Description |
|---|---|
| **NaN balances** | `Double.NaN` in `format()` due to incompatible Locale → uses sanitized `BigDecimal.valueOf()`. |
| **ChatInputSanitizer** | Allows color codes (§6) but blocks §§, §k§k, null bytes, and console exploits (sendMessage). |
| **Locale Currency Format** | `NumberFormat` with correct `Locale.getDefault()`, not always US; fallback if symbol is missing in Locale. |
| **Async getOfflinePlayer** | Prevents Mojang API timeout on onJoin with offline cache. |

---

## Upgrade Path 1.7.1 → 2.1.0

```
1. Make a full backup of plugins/Vault/ + DB if using MySQL
2. Install Vault-2.1.0-legacy.jar or -modern.jar depending on your Java
3. Start the server
4. Files are migrated automatically:
     - config.yml      → new keys with comments
     - balances.yml    → multi-currency format (auto-migration V1→V2)
     - messages_es.yml → new i18n keys
5. Run: /vault verify  → OK: 9 checks passed
6. Run: /vault import essentials  (if you came from Essentials)
```

## Database Migrations (2.1.0)

| Flyway File | Applies to |
|---|---|
| `V1__init.sql` | Empty tables |
| `V2__add_world_balances.sql` | Creates `vault_world_balances` |
| `V3__add_charge_requests.sql` | Creates `vault_charge_requests` |
| `V4__add_transactions.sql` | Creates `vault_transactions` 45-slot log |
| `V5__add_loans.sql` | Creates `vault_loans` + `vault_loan_payments` |

## Breaking Changes 1.x → 2.x

- ⚠ `Economy#getBalance(Player)` still works, but `SimpleEconomy#getBalance(OfflinePlayer)` is the new path.
- ⚠ Currencies are no longer just "Vault.Economy": there are N currencies in balances.yml.
- ⚠ Vault 1.x notes do not have an HMAC signature and are invalidated when hmac.secret is changed. Use `/vault notes migrate`.
- ⚠ `plugin.yml` now declares `softdepend: LuckPerms, SkinsRestorer, PlaceholderAPI, Skript`.
