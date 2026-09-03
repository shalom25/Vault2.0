---
title: First Install
description: Requirements (Java 17/21, Spigot 1.8.8+), step-by-step installation, JAR placement, first boot, PlaceholderAPI and MySQL checks.
---

# 🚀 First Install

Follow these steps to set up **Vault v2.1.0** from scratch on your Spigot / Paper / Purpur server.

---

## ✅ Minimum requirements

| Layer | Requirement | Supported range | Note |
| :--- | :--- | :--- | :--- |
| ☕ Java | JDK / JRE | **17 LTS** or **21 LTS** | The JAR is distributed in two flavors: `vault-2.1.0-java17.jar` and `vault-2.1.0-java21.jar`. Do not mix versions. |
| 📦 Server | Spigot / Paper / Purpur / Folia | **1.8.8 – 1.21.x** | `api-version: 1.13` in plugin.yml, but the plugin shims handlers for 1.8.8. |
| 🔌 SoftDepends | LuckPerms, PlaceholderAPI, SkinsRestorer | (any stable) | Not mandatory; if present, Vault auto-integrates. |
| 🗄️ Database | YAML (default) / MySQL 8.0+ / MariaDB 10.6+ | optional | MySQL requires `storage.use_mysql: true` and a schema + user with `CREATE / INSERT / SELECT / UPDATE / DELETE` privileges. |
| 💾 RAM heap | Recommended minimum | **1 GB** for active MySQL | Default HikariCP pool = 10 connections. |

---

## 📥 Installation (step by step)

1. **Choose the correct JAR**
   - For Java 17 servers → `vault-2.1.0-java17.jar`
   - For Java 21 servers → `vault-2.1.0-java21.jar`

2. **Stop the server** and place the JAR in your `plugins/` folder:
   ```
   my_server/
   ├── spigot-1.21.1.jar
   ├── plugins/
   │   ├── PlaceholderAPI/
   │   ├── LuckPerms/
   │   └── Vault/           ← created on first boot
   │   └── vault-2.1.0-java21.jar   ← PASTE HERE
   └── ...
   ```

3. **(Optional) Install neighboring plugins** to activate integrations:
   - `PlaceholderAPI.jar` → enables `%vault_balance_formatted%`, etc.
   - `SkinsRestorer.jar`  → heads in `/pay` work even if the server is in offline mode.
   - `LuckPerms.jar`      → to manage `vault.*` permissions comfortably.

4. **Start the server**. You will see in console:
   ```
   [Vault] ========================================
   [Vault]   Vault v2.1.0 (Java 21 build)
   [Vault]   Internal economy + Vault API bridge
   [Vault] ========================================
   [Vault] Locale: en  → messages_en.yml loaded
   [Vault] Storage: YAML  (balances.yml)
   [Vault] PlaceholderAPI detected → registered 40+ placeholders
   [Vault] SkinsRestorer detected → enabled head resolution
   [Vault] Update check: latest = v2.1.0  (you are up to date)
   ```

5. **Stop and edit `plugins/Vault/config.yml`** with your values (see [Config Reference →](/wiki/config-reference)).

6. **Start again** and run from in-game or console:
   ```
   /eco give YourName 10000
   /balance
   ```

---

## 🔧 First boot: generated files

The first `/reload confirm` or first boot creates inside `plugins/Vault/`:

```yaml
# Default structure (v2.1.0)
config.yml                  # General settings, currency, storage, loans, bank, discord
balances.yml                # YAML balance storage (when use_mysql: false)
bank.yml                    # Banking system balances (bank_balance per UUID)
loans.yml                   # Active / closed loans (YamlLoanStorage)
transactions/               # Folder with daily JSON logs (DailyJsonLogWriter)
    ├── 2026-09-02.json
    └── ...
messages/
    ├── messages_en.yml
    ├── messages_es.yml
    ├── messages_pt.yml
    ├── messages_de.yml
    ├── messages_fr.yml
    ├── messages_nl.yml
    ├── messages_pl.yml
    ├── messages_ru.yml
    ├── messages_hi.yml
    ├── messages_zh_CN.yml
    └── messages_zh_TW.yml
```

---

## ✅ Post-installation checks

Run this checklist in-game (with OP or `vault.admin`):

| Step | Command | Expected result |
| :--- | :--- | :--- |
| 1 | `/balance` | Shows `Your balance: 0.00 $` |
| 2 | `/eco give Steve 5000` | `Steve +5000.00 $ (admin give)` |
| 3 | `/pay Steve 1000` | If Steve is online, 1000 arrives instantly |
| 4 | `/vault top` | `#1 Steve 5000.00 $` |
| 5 | `/vault bank deposit 3000` | `Deposited: 3000.00 $` |
| 6 | `/vault bank balance` | `Wallet: 3000.00 $ · Bank: 3000.00 $ · Total: 6000.00 $` |
| 7 | `/vault withdraw 500` | You receive a paper (PhysicalNote) in inventory |
| 8 | `/vault reload` | `Vault reloaded (language: en)` |

If everything works → proceed to configure [Config Reference →](/wiki/config-reference) and choose your language in [Languages →](/wiki/languages).

---

## ⚙️ Enable MySQL (optional)

Edit `config.yml`:

```yaml
storage:
  use_mysql: true
  mysql:
    host: db.myserver.com
    port: 3306
    database: vault_prod
    username: vault_user
    password: "YourSecurePassword123!"
    pool_size: 10
```

Restart and look in the log: `Vault: MySQL connection pool OK (10 idle)`. If you see **SQLException**, check credentials, firewall, and that the schema exists (the plugin **does not create** the database; it does create the `balances` and `tx_log` tables).
