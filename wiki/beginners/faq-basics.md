---
title: Beginner Basics FAQ
description: Frequently asked questions when installing Vault 2.1 for the first time: currency, MySQL, Vault.jar, permissions.
---

# FAQ: Basic Concepts

## Do I need the classic Vault plugin (`Vault.jar`)?

**No.** Vault 2.1 **already internally contains** the `net.milkbowl.vault.economy.Economy` interface. Any plugin that depends on Vault (ShopGUIPlus, Jobs, JobsReborn, AuctionHouse...) will automatically detect the API. In fact, if you have both, disable the old one to avoid `Economy` registration conflicts.

## Can I use MySQL instead of YAML?

Yes. Edit `config.yml`:

```yaml
storage:
  use_mysql: true
  mysql:
    host: localhost
    port: 3306
    database: vault
    username: root
    password: "your_password"
    pool_size: 10
```

On restart, `Database.ensureSchema()` **automatically** creates tables `vault_balances`, `vault_world_balances`, `vault_transactions`, and `vault_charge_requests`. You don't need to run SQL manually.

## How do I change the language of messages?

In `config.yml`:
```yaml
language: en     # Available languages: en, es, fr, de, nl, pl, pt, ru, zh_CN, zh_TW, hi
```
Save and run `/vault reload`. Texts are taken from `messages/messages_en.yml`. You can edit that file to customize.

## Can I have more than one currency?

Yes. In `config.yml` define the `currencies` section:
```yaml
currencies:
  default:
    symbol: "$"
    position: suffix
  gems:
    symbol: "💎"
    position: suffix
    space: false
```
The first one (or the one marked with `default: true`) is what external plugins use through the legacy Vault API. Balances for other currencies are stored in `balances.yml` (not in MySQL in this version).

## Why don't I see the balance from other worlds?

Make sure to activate **separate world balances**:
```yaml
world_balances:
  separate_worlds:
    - world_nether
    - world_the_end
```
Only the worlds listed here will have their own balance. The rest share the global balance.

## How do I update without losing balances?

Vault 2.x stores everything in `balances.yml` + `bank_balances.yml` (or MySQL tables if you use that backend). **Simply replace the JAR and restart** — file formats are stable and backward-compatible.

If you're doing a large migration, back up `plugins/Vault/` and, if you use MySQL, run `mysqldump vault` first.

## Are /eco and /vault the same thing?

No, they complement each other:
- **`/eco`** — pure administration (give/take/set/reset/top). Requires `vault.eco` (OP by default).
- **`/vault`** — main menu, reload, update, bank, loan, withdraw, history, offlinepay, import.
- **`/eco top`** and **`/vault top`** use the exact same `TopCacheService`.

## My server is in offline-mode. Do payments to unknown players work?

Yes, enable:
```yaml
offline-uuid-fallback: true
```
Then `/pay PlayerCurrentlyOffline 100` will generate the UUID in offline mode and they will be able to collect it when they join (thanks to `OfflinePayQueueService`). If you leave it at `false`, payments to names never seen before are rejected with `pay.player_offline`.
