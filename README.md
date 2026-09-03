# Vault 2.0

<p align="center">
  <img src="https://i.imgur.com/HxgguHP.png" alt="Vault 2.0" width="720" />
</p>

[![CI](https://github.com/shalom25/Vault2.0/actions/workflows/build.yml/badge.svg?branch=main)](https://github.com/shalom25/Vault2.0/actions/workflows/build.yml) [![Release](https://img.shields.io/github/v/release/shalom25/Vault2.0?display_name=tag)](https://github.com/shalom25/Vault2.0/releases/latest) [![Downloads](https://img.shields.io/github/downloads/shalom25/Vault2.0/total)](https://github.com/shalom25/Vault2.0/releases)

An internal economy provider compatible with the Vault API (no external `Vault.jar` required). It registers a Bukkit `Economy` service via the `ServicesManager`.

## Key Changes
- Plugin name: "Vault 2.0".
- Final JAR: `target/vault-2.0-v1.4.jar`.
- Internal economy with persistence in `plugins/Vault 2.0/balances.yml` (MySQL optional).
- PlaceholderAPI integration available (placeholders para balance).
- Commands: `/balance`, `/pay`, `/eco` (admin) y `/vault reload`.

## Requirements
- Java 17.
- Spigot/Paper 1.8.8 (tested). `api-version: 1.13` is used for broad compatibility of plugin metadata.

## Build
1. From the project folder, run: `mvn -B -U package`
2. The artifact is produced at: `target/vault-2.0-v1.4.jar`

## Installation
1. Copy `target/vault-2.0.jar` into your server `plugins/` folder.
2. Start the server. No official `Vault.jar` or external economy plugin is required.

## Download
- Latest release: https://github.com/shalom25/Vault2.0/releases/latest

## Configuration
- File: `plugins/Vault 2.0/config.yml` (auto-generated on first start via `saveDefaultConfig()`).
- Storage:
  - `storage.use_mysql: false` — por defecto usa `balances.yml`.
  - MySQL block (comentado en el archivo): `# MySQL storage (set use_mysql: true to enable)`
    - `storage.mysql.host`
    - `storage.mysql.port`
    - `storage.mysql.database`
    - `storage.mysql.username`
    - `storage.mysql.password`
    - `storage.mysql.pool_size`
- Import Essentials (opcional):
  - `import.essentials.enabled: false`
  - `import.essentials.replace: false` (si `true`, reemplaza balances existentes; si `false`, hace merge)
- Offline UUID fallback:
  - `offline-uuid-fallback` (default `true`). En `online-mode=true` se ignora; en `offline-mode=true` permite operar con nombres no vistos usando UUIDs determinísticos.

## Usage
- `/balance` — Show your current balance.
- `/pay <player> <amount>` — Send money to another player.
- `/eco <give|take> <player> <amount>` — Admin command para ajustar balances.
- `/vault reload` — Reload configuration and messages. This command is OP-only; it does not use permission nodes.

## PlaceholderAPI
- Si `PlaceholderAPI` está instalado, la expansión se registra automáticamente.
- Placeholders disponibles:
  - `%vault_balance%`
  - `%vault_balance_formatted%`
  - `%vault_balance_<player>%`
  - `%vault_balance_formatted_<player>%`

## Compatibility Notes
- Some plugins hard-check for a plugin named exactly `Vault`. Using the name "Vault 2.0" might fail those checks even though the `Economy` service is available. If you need full compatibility with such plugins, keep `name: Vault` and only bump `version: 2.0.0`.
- This plugin provides economy only (no permissions or chat).

## LuckPerms
- Uses Bukkit's standard permissions API, so LuckPerms works out of the box.
- Default permission nodes:
  - `vault.balance` — use `/balance` (default `op`).
  - `vault.pay` — use `/pay` and open the pay menu (default `op`).
  - `vault.pay.bypass_min` — bypass minimum amount (default `op`).
  - `vault.pay.bypass_max` — bypass maximum amount (default `op`).
- Command-level `permission` entries were removed from `plugin.yml`; permissions are enforced in code to allow custom i18n denial messages.
- `/vault reload` is OP-only and does not rely on LuckPerms or permission nodes.
- Examples with LuckPerms:
  - `lp group default permission set vault.balance true`
  - `lp group default permission set vault.pay true`
  - `lp group vip permission set vault.pay true`
  - `lp group vip permission set vault.pay.bypass_min true`
  - `lp group vip permission set vault.pay.bypass_max true`
- Load order: `softdepend: [LuckPerms]` ensures LuckPerms is ready during startup.
