---
title: Balances Appear Reset to 0
description: Causes of why balances disappear: messy reload, storage backend change, default currency, offline vs online UUIDs.
---

# FAQ: Balances Appear Reset to 0

When a `/balance` suddenly returns `0 $`, it's usually one of these 5 causes (from most to least frequent).

## 1. You changed `storage.use_mysql` without migrating data

By default `use_mysql: false` → `balances.yml`.
If you set it to `true` **without migrating**, the plugin loads the `vault_balances` table which will be empty.

**Solution**:
1. Set `use_mysql: false` back temporarily.
2. Export the current balances (there are plugins or scripts to dump YAML → CSV).
3. Import into MySQL with `LOAD DATA LOCAL INFILE` or similar.
4. Re-enable `use_mysql: true`.

This also works the other way around: if you switched from MySQL to YAML without a dump, `balances.yml` will be empty.

## 2. Reload error: Spigot/Paper `/reload` breaks the maps

**`/reload` (PlugMan, etc.) is NOT supported.** During `onDisable → onEnable`, SimpleEconomy has this critical block in `loadCurrencyDefinitions()`:

```java
Map<String, Map<UUID, Double>> savedBalances = new HashMap<>(currencyData.size() * 2);
// current RAM balances are copied into savedBalances
// then currencyDefs.clear(); currencyData.clear();
// and they are re-inserted reading from disk.
```

If an external `/reload` aborts mid-process, the copy does not happen and RAM balances remain empty before `save()`, **being permanently lost**.

**NEVER USE `/RELOAD`**. Restart the entire server or, at most, **`/vault reload`** which only reloads `config.yml` + `messages.yml` without touching structures.

## 3. `offline-uuid-fallback` changed from `false → true` (or the other way)

The key is the UUID. If your server was `online-mode=true` and you change to `online-mode=false` (or change the server IP and `server.properties` gets regenerated), all UUIDs change.

Example: **Notch in online-mode** = UUID `069a79f4-44e9-4726-a5be-fca90e38aaf5`. **Notch in offline-mode** = UUID `b27b1b5e-1d16-3f6b-8f0b-16f9a30c14e2`. To Vault they are two DIFFERENT players.

**Recovery**: use the `EssentialsImportService` technique (same concept: iterate `balances.yml`, resolve names to UUIDs in current mode, and write to the new destination).

## 4. You accidentally deleted `balances.yml` / `bank_balances.yml`

It's the most obvious cause but happens by carelessness. The plugin regenerates empty files if they don't exist → everything goes to 0.

**Preventive measures**:
- Make automatic daily backups of `plugins/Vault/`
- Enable `storage.use_mysql: true` (less risk of loss due to file deletion)
- Bank notes and physical notes have HMAC, but if you delete `.note-secret.dat`, upon regeneration no old notes will validate (see Notes FAQ).

## 5. You changed the `currencies.default` section to another name

Before:
```yaml
# No currencies section → everything under currency.*
```
Now:
```yaml
currencies:
  euros:  # <- NOT called "default"
    symbol: "€"
```

`SimpleEconomy` picks as `defaultCurrencyId` the first entry or the one marked `default: true`. If balances were stored under the `default` currency and you rename the ID to `euros`, the data is still there but under a different key.

**Rule**: the first currency you define (and the one you want the legacy API to use) **must still be called `default`**, or mark one with `default: true`.
