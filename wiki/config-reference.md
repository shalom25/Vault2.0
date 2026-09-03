---
title: Config Reference
description: Exhaustive breakdown of Vault v2.1.0's config.yml — every key with its type, default value, detailed explanation, and related source code. Covers language, currency.*, storage, currencies, top.*, bank (interest+tax), discord.*, world_balances, import.essentials, offline-uuid-fallback, update_check*, pay_menu, pay_pending, pay_limits and loans.* (defaulted_effects).
---

# ⚙️ Config Reference (v2.1.0)

We examine **every single key** in `src/main/resources/config.yml:4-206`. Each entry shows:

- **Path** (e.g. `currency.symbol`)
- **Type** (String / int / double / boolean / List / Map)
- **Default** (exact value generated the first time config.yml is created)
- **Explanation** + Java example where relevant.

---

## 📋 Summary Table of Sections

| Section | Key count | Purpose |
| :--- | :--- | :--- |
| `language` + `plugin_version` | 2 | Metadata + selection of which `messages_xx.yml` to load |
| `currency.*` | 10 | Default formatting (symbol, position, space, format, locale, abbreviate) |
| `storage.*` | 8 | YAML vs MySQL — HikariCP host/port/db/user/pass/pool_size |
| `currencies.*` | ∞ | Multi-currency definitions (default, gems, tokens, etc.) |
| `top.*` | 2 | Async cache for /vaultop |
| `bank.interest.*` | 3 | Periodic interest on bank_balance |
| `bank.tax.*` | 4 | Progressive tax on the portion exceeding threshold |
| `discord.*` | 4 | Webhook for large transactions + anti-dupe events |
| `world_balances.*` | 1 | List of worlds with independent balances |
| `import.essentials.*` | 2 | One-shot migrator from EssentialsX |
| `offline-uuid-fallback` | 1 | Generate offline UUID for players never seen before |
| `update_check*` | 2 | Communication with update API (optional, debug) |
| `pay_menu.*` | 2 | Inventory size and "show myself" in /pay |
| `pay_pending.*` | 1 | Limit of charge-requests delivered on login |
| `pay_limits.*` | 2 | Min / max per pay/charge operation |
| `loans.*` | 13 | Full loan system (amounts, terms, default effects) |

---

## 1) Metadata + Language

| Key | Type | Default | Explanation |
| :--- | :--- | :--- | :--- |
| `plugin_version` | String | `v2.1.0` | Informational only (never edit by hand). Used by `VaultPlugin.onEnable()` to print the startup logo. |
| `language` | String | `en` | ISO 639-1 / BCP-47 code of the `messages_<code>.yml` to load. Supported values: `en`, `es`, `pt`, `de`, `fr`, `nl`, `pl`, `ru`, `hi`, `zh_CN`, `zh_TW`. If you set a nonexistent code, **fallback to messages_en.yml**. |

```java
// Messages.java — initial load
String lang = plugin.getConfig().getString("language", "en");
File msgFile = new File(plugin.getDataFolder(), "messages/messages_" + lang + ".yml");
if (!msgFile.exists()) msgFile = new File(plugin.getDataFolder(), "messages/messages_en.yml");
```

---

## 2) `currency.*` (global default format)

Applied when `SimpleEconomy.format(currencyId, amount)` does not find an override in `currencies.<id>.*`.

| Key | Type | Default | Explanation |
| :--- | :--- | :--- | :--- |
| `currency.symbol` | String | `"$"` | Text or emoji representing the currency. Supports `&6` color codes, e.g. `&6💰` |
| `currency.position` | enum String | `suffix` | `suffix` → `10 $` · `prefix` → `$ 10` (before the number). |
| `currency.space` | boolean | `true` | `true` adds a space between symbol and number (`10 $`); `false` → `10$`. |
| `currency.format` | String | `auto` | Reserved for v2.2. In v2.1 it is ignored and `currency.locale` is always used instead. |
| `currency.locale` | String | `"auto"` | Style of thousands and decimal separators. Valid presets: `us` (1,000.00), `eu` (1.000,00), `uk`, `in` (1,00,000.00), `ch` (1'000.00), `fr` (1 000,00). Also accepts BCP-47: `es-ES`, `de-DE`, `it-IT`, `pt-BR`, `fr-FR`. `"auto"` or `""` uses the JVM's `Locale.getDefault()`. |
| `currency.abbreviate.decimals` | int | `1` | Visible decimals after abbreviating (e.g. `1.2k`, `3.5m`). 0 = `1k`, `4m`. |
| `currency.abbreviate.suffix.k` | String | `"k"` | Suffix for thousands (kilo). |
| `currency.abbreviate.suffix.m` | String | `"m"` | Suffix for millions. |
| `currency.abbreviate.suffix.b` | String | `"b"` | Suffix for billions. |
| `currency.abbreviate.suffix.t` | String | `"t"` | Suffix for trillions. |

Practical example:

```yaml
currency:
  symbol: "€"
  position: suffix
  space: true
  locale: "eu"          # → 1.234,56 €
  abbreviate:
    decimals: 2
    suffix: {k: "k", m: "M", b: "B", t: "T"}   # → 1.234.567 → 1,23M €
```

---

## 3) `storage.*` (persistence backend)

| Key | Type | Default | Explanation |
| :--- | :--- | :--- | :--- |
| `storage.use_mysql` | boolean | `false` | `false` → everything saved in `balances.yml` + `bank.yml`. `true` → uses MySQL with HikariCP (you must fill in `storage.mysql.*`). |
| `storage.mysql.host` | String | `localhost` | FQDN or IP of the MySQL / MariaDB server. |
| `storage.mysql.port` | int | `3306` | TCP port (3306 standard; MariaDB SkySQL typically uses 5001). |
| `storage.mysql.database` | String | `vault` | Schema / database. **The plugin does not create it**; create it first with `CREATE DATABASE vault CHARACTER SET utf8mb4;`. |
| `storage.mysql.username` | String | `root` | User with `CREATE TABLE, SELECT, INSERT, UPDATE, DELETE` privileges on the schema. |
| `storage.mysql.password` | String | `""` | Password in plain text. If using Docker/K8s it's better to override with the `VAULT_MYSQL_PASSWORD` environment variable (the file takes priority). |
| `storage.mysql.pool_size` | int | `10` | Maximum HikariCP pool size (`maximumPoolSize`). `minimumIdle` = same value in v2.1. |

---

## 4) `currencies.*` (Multi-currency v2.1+)

**Map `<id>: <CurrencyDef>`** — each child key of `currencies:` is a currency. The first one (or the one literally named `default`) becomes the legacy currency returned by `net.milkbowl.vault.economy.Economy.getBalance()`.

| Key | Type | Default | Explanation |
| :--- | :--- | :--- | :--- |
| `currencies.default` | Map | `{symbol: "$", position: suffix, space: true}` | Primary currency (required if the `currencies:` section exists). Accepts optional overrides for `locale`, `format`, `abbreviate.decimals`, `abbreviate.suffix.*`. |
| `currencies.<other>.symbol` | String | (inherits `currency.symbol`) | Symbol specific to the secondary currency. |
| `currencies.<other>.position` | enum | (inherits `currency.position`) | suffix / prefix override. |
| `currencies.<other>.space` | bool | (inherits `currency.space`) | With / without space. |
| `currencies.<other>.locale` | String | (inherits `currency.locale`) | Separator style. |
| `currencies.<other>.abbreviate.decimals` | int | (inherits) | Abbreviation decimals. |
| `currencies.<other>.abbreviate.suffix.*` | Map | (inherits) | k/m/b/t overrides. |

Example gems + tokens:

```yaml
currencies:
  default:
    symbol: "$"
    position: suffix
    space: true
  gems:
    symbol: "💎"
    position: suffix
    space: false
    abbreviate: {decimals: 0, suffix: {k: "K", m: "M", b: "B", t: "T"}}
  tokens:
    symbol: "🪙"
    position: prefix
    space: true
    locale: "ch"       # → 🪙 1'000.00
```

⚠️ v2.1 limitation: secondary (non-default) currencies **are only saved in YAML** (not in MySQL) — the MySQL DAO only stores `currencyId = "default"`.

---

## 5) `top.*` (baltop cache)

| Key | Type | Default | Explanation |
| :--- | :--- | :--- | :--- |
| `top.refresh_seconds` | int | `300` (5 min) | Period in seconds with which `TopCacheService` regenerates the sorted list in the background. Lower values = fresher top list, more CPU. |
| `top.change_threshold` | double | `0` | Minimum balance delta to **invalidate the cache instantly** (in addition to the periodic refresh). `0` = always invalidates after any `/eco give/take/set`. Set `1000` to ignore micro-operations. |

---

## 6) `bank.interest.*` + `bank.tax.*`

Players have two balances: `wallet` (the usual one) + `bank_balance` (deposited in the bank, subject to interest and tax).

### `bank.interest`

| Key | Type | Default | Explanation |
| :--- | :--- | :--- | :--- |
| `bank.interest.enabled` | boolean | `true` | Master switch — `false` = interest is never applied, even if `percent_per_period > 0`. |
| `bank.interest.every_minutes` | long | `60` | How often (in minutes) interest is applied to ALL players with `bank_balance > 0`. |
| `bank.interest.percent_per_period` | double | `0.5` | Percentage added to bank_balance each period. **0.5 = +0.5% / hour**. Formula: `new = current + (current * percent / 100.0)`. |

### `bank.tax`

| Key | Type | Default | Explanation |
| :--- | :--- | :--- | :--- |
| `bank.tax.enabled` | boolean | `false` | Master switch — `false` = never applied. |
| `bank.tax.every_minutes` | long | `180` | Tax period. Default every 3h. |
| `bank.tax.threshold` | double | `1 000 000` | Exempt threshold. Tax **only affects the fraction strictly > threshold**. Example: balance = 1,500,000, threshold = 1M → tax on 500,000. |
| `bank.tax.percent_per_period` | double | `0.1` | Percentage per period on the excess. 0.1 = 0.1% every 180 min on the portion > threshold. |

Numerical example:
```
balance = 3 000 000
threshold = 1 000 000
tax_percent = 0.1

taxable = 2 000 000
tax     = 2 000 000 × 0.1 / 100 = 2 000
new     = 3 000 000 − 2 000 = 2 998 000
```

---

## 7) `discord.*` (webhook notifier)

| Key | Type | Default | Explanation |
| :--- | :--- | :--- | :--- |
| `discord.webhook_url` | String | `""` | Full Discord webhook URL (`https://discord.com/api/webhooks/...`). **Empty = feature disabled** (no outgoing requests). |
| `discord.threshold_amount` | double | `100000` | Individual transactions ≥ this value trigger an embed to the channel. Anti-dupe events and DB errors also fire **regardless of the threshold**. |
| `discord.username` | String | `"Vault Bot"` | Name the webhook signs messages with (Discord overrides this if the webhook already has a fixed name). |
| `discord.avatar_url` | String | `""` | Webhook avatar (URL to PNG/JPG image). Empty = default webhook avatar. |

---

## 8) `world_balances.*` (per-world balances)

| Key | Type | Default | Explanation |
| :--- | :--- | :--- | :--- |
| `world_balances.separate_worlds` | List\<String\> | `[]` (empty) | List of exact world names whose balances will be independent. E.g. `- world_nether`, `- mines`, `- skyblock`. **The legacy API (Economy without world) still returns the global balance for compatibility with ShopGUIPlus et al.** Only Vault's player-facing commands respect this separation. |

---

## 9) `import.essentials.*` (one-shot EssentialsX migrator)

| Key | Type | Default | Explanation |
| :--- | :--- | :--- | :--- |
| `import.essentials.enabled` | boolean | `false` | `true` for ONE startup to read `plugins/Essentials/userdata/*.yml` and dump `money: ` into Vault. |
| `import.essentials.replace` | boolean | `false` | `false` (safe by default): only creates balances that DO NOT exist in Vault. `true`: overwrites any that already exist — use only during initial migration! |

After running successfully once, `VaultCommand.resetbalances` sets `import.essentials.enabled = false` automatically to avoid re-importing on every restart.

---

## 10) `offline-uuid-fallback`

| Key | Type | Default | Explanation |
| :--- | :--- | :--- | :--- |
| `offline-uuid-fallback` | boolean | `true` | `true` = for a player never seen before, generate an offline-mode UUID (Bukkit `UUID.nameUUIDFromBytes("OfflinePlayer:<name>".getBytes())`). `false` = rejects operations with unknown names (you need the player to have logged in at least once). |

---

## 11) `update_check*`

| Key | Type | Default | Explanation |
| :--- | :--- | :--- | :--- |
| `update_check` | boolean | `true` | Checks on startup whether a new release exists in the repository. Notifies OPs when they join the game (chat message). 1 HTTPS request per boot. |
| `update_check_debug` | boolean | `false` | If `true`, prints the raw JSON response of the check to the console (for developers / support). |

---

## 12) `pay_menu.*`

| Key | Type | Default | Explanation |
| :--- | :--- | :--- | :--- |
| `pay_menu.size` | int | `27` | Slots of the `/pay` inventory (main menu with the player list). Must be **a multiple of 9** and ≥ 9 (Bukkit Inventory validation). Use 54 if your server has >20 concurrent players and you want more heads per page. |
| `pay_menu.show_self` | boolean | `false` | `true` = your own head appears in the `/pay` list (for paying yourself, normally not useful). |

---

## 13) `pay_pending.*`

| Key | Type | Default | Explanation |
| :--- | :--- | :--- | :--- |
| `pay_pending.max_on_join` | int | `5` | When a player connects, at most N pending charge-requests (Charge / request money) are delivered as clickable chat. The rest remain in queue for the next login (prevents 50 messages spammed at once). |

---

## 14) `pay_limits.*`

| Key | Type | Default | Explanation |
| :--- | :--- | :--- | :--- |
| `pay_limits.min` | double | `1` | Minimum amount per Pay or Charge operation. `≤ 0` disables the lower limit. Bypassable with `vault.pay.bypass_min`. |
| `pay_limits.max` | double | `100000` | Maximum amount per Pay or Charge operation. `≤ 0` disables the upper limit. Bypassable with `vault.pay.bypass_max`. |

---

## 15) `loans.*` (loan system v2.1+)

### Main block

| Key | Type | Default | Explanation |
| :--- | :--- | :--- | :--- |
| `loans.enabled` | boolean | `true` | Master switch — `false` hides `/loan`, `/vault loan`, and `LoanService` does not start its timers. |
| `loans.max_active_per_player` | int | `1` | SIMULTANEOUS loans a player can have open. 1 = single loan; 3 = up to 3 active credit lines. |
| `loans.min_amount` | double | `1` | Minimum amount that can be requested. |
| `loans.max_amount` | double | `100000` | Maximum amount that can be requested. |
| `loans.min_installment` | double | `1` | Minimum fixed value per installment (floor). |
| `loans.min_installment_by_amount` | Map\<Double, Double\> | `{1000: 50, 5000: 250}` | Progressive scaling: if the loan is ≥ 1000 → the minimum installment becomes 50. If ≥ 5000 → 250. The largest applicable entry is used (implicit ascending order). |
| `loans.max_installments` | int | `60` | Maximum number of installments allowed when creating a loan (60 × 24h = ~2 months). |
| `loans.default_interval_hours` | int | `24` | Suggested interval between installment collections (24h = daily). The wizard uses this as a pre-selected value; the player can change it in the GUI LoanMenuService. |
| `loans.charge_check_seconds` | int | `60` | How often (in seconds) the `Bukkit.getScheduler().runTaskTimerAsynchronously(...)` timer scans active loans to check if an installment is due. |
| `loans.max_missed_payments` | int | `3` | Consecutive failed automatic installments (due to insufficient money in wallet + bank) before marking the loan as **DEFAULTED** and imposing `defaulted_effects`. |

### `loans.defaulted_effects.*` (default / late-payment effects)

| Key | Type | Default | Explanation |
| :--- | :--- | :--- | :--- |
| `loans.defaulted_effects.enabled` | boolean | `true` | `false` = a defaulted loan does NOT receive potion effects (still recorded as DEFAULTED internally). |
| `loans.defaulted_effects.refresh_seconds` | int | `5` | Period with which effects are re-applied (so they don't expire even if the player stays online for a long time). |
| `loans.defaulted_effects.duration_seconds` | int | `8` | Duration of the `PotionEffect` on each application (must be > `refresh_seconds` so there are no "gaps" without effect). |
| `loans.defaulted_effects.effects` | List\<String "NAME:LEVEL"\> | `["SLOW:1", "SLOW_DIGGING:1"]` | List of 1-based `PotionEffectType`. Valid names = Bukkit constants from `org.bukkit.potion.PotionEffectType` (uppercase, underscore). E.g.: `SLOW:2`, `BLINDNESS:1`, `CONFUSION:1`, `POISON:1`, `WEAKNESS:1`. |

---

## 🔗 Cross-references with source code

| Java File | Reads these keys |
| :--- | :--- |
| `Messages.java` | `language` |
| `SimpleEconomy.java` | `currency.*`, `storage.*`, `currencies.*`, `world_balances.separate_worlds`, `offline-uuid-fallback`, `import.essentials.*` |
| `TopCacheService.java` | `top.refresh_seconds`, `top.change_threshold` |
| `BankService.java` | `bank.interest.*`, `bank.tax.*` |
| `DiscordWebhookNotifier.java` | `discord.*` |
| `UpdateChecker.java` | `update_check`, `update_check_debug` |
| `PayMenuService.java` | `pay_menu.size`, `pay_menu.show_self`, `pay_pending.max_on_join` |
| `ChargeRequestService.java` | `pay_limits.*` |
| `LoanService.java`, `LoanMenuService.java` | Full `loans.*`, especially `defaulted_effects.effects` which is parsed by splitting on `:` |
