---
title: Installation Errors
description: Solutions to the most common failures when installing or starting Vault 2.1. Economy error, duplicate plugin, MySQL Hikari, Java version.
---

# FAQ: Installation and Startup Errors

## 1. `[Vault] ERROR: Could not register Economy — another plugin already did it`

Another plugin has already registered the `net.milkbowl.vault.economy.Economy` interface.

**Typical causes**:
- You have classic `Vault.jar` + `EssentialsX Economy.jar` (Essentials also registers Economy)
- You have an old economy plugin (CMI, Gringotts, etc.)

**Solution**:
1. Choose **only one** economy plugin: if you are going to use Vault 2.1, **remove the EssentialsX Economy jar** and/or the old `Vault.jar`.
2. Or the other way around: if you need EssentialsX Economy, remove `vault-2.x.x.jar`.
3. Restart the server.

## 2. `HikariPool-1 - Exception during pool initialization` (MySQL)

`Database` cannot connect to the HikariCP pool.

**Steps to check**:
```yaml
storage:
  use_mysql: true
  mysql:
    host: 127.0.0.1      # Make sure it's not localhost if you use socket
    port: 3306
    database: vault      # Does the DB exist? Run CREATE DATABASE vault;
    username: vaultuser
    password: "correct"  # Double quotes if it has special characters
```

**Check order**:
1. Open a shell and test: `mysql -h127.0.0.1 -uvaultuser -p vault -e "SELECT 1"`
2. Verify `useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true` in `params:` if you use MySQL 8.
3. Make sure the pool is not exhausted (`pool_size: 10` by default; don't raise it unnecessarily).
4. Look at the stacktrace: if it says `Communications link failure` it's network/firewall; if `Access denied` it's credentials.

## 3. `NoSuchMethodError: PlayerProfile / SkinsRestorer`

The head in `/pay` does not render.

**Causes**:
- You are on Spigot 1.12 or earlier without SkinsRestorer.
- The `applyLivePlayerProfile` / `applySkinRestorerSkin` reflection cannot find the API.

**Solution**:
- Install **SkinsRestorer** (the plugin tries to load `net.skinsrestorer.api.SkinsRestorerProvider` dynamically).
- Make sure `softdepend: [SkinsRestorer]` is loaded earlier in the startup order.

## 4. `UnsupportedClassVersionError` on load

You are using a Java version older than the one the JAR was compiled for.

| JAR                     | Minimum Java |
|-------------------------|--------------|
| `vault-2.1.0-java17.jar`| Java 17      |
| `vault-2.1.0-java21.jar`| Java 21      |

Check with `java -version`. If your panel/host only provides Java 16 or lower, update the server runtime.

## 5. Messages appear as `pay.only_players` (not translated)

`Messages` did not find the language file.

**Solution**:
1. Confirm that `plugins/Vault/messages/messages_en.yml` (or the corresponding one) exists and is not corrupted. If you deleted it by accident, **reinstall the JAR** so they are generated again.
2. In `config.yml` check `language: en` (must be one of: `en, es, fr, de, nl, pl, pt, ru, zh_CN, zh_TW, hi`).
3. Run `/vault reload`. If it still fails, delete the `messages/` folder and let the plugin regenerate it.

## 6. `WARN: Failed to create transactions JSON log`

Failed to create `logs/daily-YYYY-MM-DD.json`.

**Common cause**: write permissions of the Windows/Linux user over `plugins/Vault/`.

**Solution**:
- On Linux: `chown -R minecraft:minecraft plugins/Vault` and `chmod -R u+rwX plugins/Vault`
- On Windows: right-click → Properties → Security → Ensure the server user has "Write" permission
- The daily log is not **mandatory** for operation; without it you still have FileTxDAO/MySqlTxDAO.
