# Vault 2.0

[![CI](https://github.com/shalom25/Vault2.0/actions/workflows/build.yml/badge.svg?branch=main)](https://github.com/shalom25/Vault2.0/actions/workflows/build.yml) [![Release](https://img.shields.io/github/v/release/shalom25/Vault2.0?display_name=tag)](https://github.com/shalom25/Vault2.0/releases/latest) [![Downloads](https://img.shields.io/github/downloads/shalom25/Vault2.0/total)](https://github.com/shalom25/Vault2.0/releases)

An internal economy provider compatible with the Vault API (no external `Vault.jar` required). It registers a Bukkit `Economy` service via the `ServicesManager`.

## Key Changes
- Plugin name: "Vault 2.0".
- Final JAR: `target/vault-2.0.jar`.
- Internal economy with persistence in `plugins/Vault 2.0/balances.yml`.
- Commands: `/balance`, `/pay`, and `/vault reload`.

## Requirements
- Java 8.
- Spigot/Paper 1.8.8 (tested). `api-version: 1.13` is used for broad compatibility of plugin metadata.

## Build
1. From the project folder, run: `mvn package`
2. The artifact is produced at: `target/vault-2.0.jar`

## Installation
1. Copy `target/vault-2.0.jar` into your server `plugins/` folder.
2. Start the server. No official `Vault.jar` or external economy plugin is required.

## Download
- Latest JAR: https://github.com/shalom25/Vault2.0/releases/latest/download/vault-2.0.jar

## Configuration
- File: `plugins/Vault 2.0/config.yml` (auto-generated on first start via `saveDefaultConfig()`).
- Key: `offline-uuid-fallback` (default `true`).
- Behavior by server mode:
  - `online-mode=true`: fallback is ignored; names never seen and not online will yield "Player not found".
  - `offline-mode=true`: if `offline-uuid-fallback=true`, a deterministic offline UUID (Bukkit-style: `OfflinePlayer:<name>`) is used for unseen names to allow operations.
- Notes:
  - Players use names; internally the plugin resolves them to `UUID` for persistence.
  - Offline and online UUIDs are not mixed; fallback only applies in `offline-mode`.

## Usage
- `/balance` — Show your current balance.
- `/pay <player> <amount>` — Send money to another player.
- `/vault reload` — Reload configuration and messages. This command is OP-only; it does not use permission nodes.

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