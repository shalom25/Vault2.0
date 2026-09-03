---
title: Support
description: How to get help for Vault 2.1.0: official Discord, GitHub / Modrinth issues, and what to do before reporting.
---

# Support & Contact

## Before Reporting a Bug

1. Run `/vault verify` – all **9 checks** must pass ✓.
2. Read the **[Troubleshooting](/other/troubleshooting)** guide – 80% of cases are covered there.
3. Make sure you're using the **latest version** from Modrinth (ID `rj9SgaYL`).
4. Update dependencies: LuckPerms, PlaceholderAPI, SkinsRestorer to their stable builds.
5. Run `/vault debug pastebin` and save the link.

```
/vault verify
---
✓ 1/9: Vault 2.1.0 loaded (build.abc123)
✓ 2/9: MySQL storage active
✓ 3/9: HMAC secret configured (not default)
✓ 4/9: 11 languages loaded (0 missing keys)
✓ 5/9: SimpleEconomy provider OK
✓ 6/9: PlaceholderAPI hooks OK (25/25)
✓ 7/9: SkinsRestorer (optional) connected
✓ 8/9: LuckPerms vault.* permissions loaded
✓ 9/9: Modrinth Updater reachable
```

If 1 or more fail → fix what it says before opening a ticket.

## Official Channels

| Channel | URL | Response Time |
|---|---|---|
| 🟢 **Discord** (recommended) | https://discord.gg/tu-invitacion-vault | 24–72h |
| 🟡 **Modrinth Issues** | https://modrinth.com/plugin/rj9SgaYL/issues | 3–7 days |
| 🔴 **GitHub Issues** | https://github.com/tuorg/Vault/issues | Technical bug reports |

### Which Channel to Use?

- **Usage questions, configuration, skripts, placeholders** → **Discord** `#support`
- **Reproducible bugs** + **crash logs** → **Modrinth Issues**
- **Pull request / code / architecture** → **GitHub Issues**

---

## Reporting a Bug (Modrinth / GitHub)

Use this template so we can help you quickly:

```markdown
### Versions
- Vault: `2.1.0` (put exact build)
- Build: `legacy` (Java 17) / `modern` (Java 21)
- Server: Paper `1.20.4` (put your fork and build)
- Java: `OpenJDK 17.0.11`
- Relevant dependencies:
  - LuckPerms `5.4.102`
  - PlaceholderAPI `2.11.5`
  - SkinsRestorer `15.0.3`
  - Skript `2.8.0`
  - (others)

### Affected Configuration
```yaml
# Paste the relevant section from config.yml
storage:
  use_mysql: true
  ...
hmac:
  ...
```

### Steps to Reproduce
1. `/vault note issue 1000 coins`
2. Close and open inventory
3. Right-click the note → **crash**

### Expected Behavior
The note should redeem and add 1000 to the balance.

### Actual Behavior
```
[ERROR] Could not pass event PlayerInteractEvent to Vault v2.1.0
org.bukkit.event.EventException: null
  at org.bukkit.plugin.java.JavaPluginLoader$1.execute(JavaPluginLoader.java:310) ~[?:?]
Caused by: net.milkbowl.vault.services.HMACValidationException
  at ...
```

### Debug Command Outputs
- `/vault verify` → pastebin or text
- `/vault admin info` →
- `/vault admin storage` →
- `/vault debug pastebin` → link

### Attachments
- Full `latest.log` (not trimmed)
- Full `config.yml` (hide passwords)
- Screenshot if GUI / visual issue
```

---

## Report Template (Discord)

Paste this in `#support`:

```
**Platform:** Paper 1.20.1
**Vault:** 2.1.0 legacy
**Java:** 17
**Dependencies:** LuckPerms, PlaceholderAPI, Skript

Problem:
> [Vault] HMAC lore mismatch 5 fields vs 3

Already tried:
- /vault verify → 9/9 OK ✅
- Troubleshooting §1 → did migrate but it fails

Logs: https://paste.gg/...
```

---

## SLA and Response Times

| Issue | Priority | Response SLA |
|---|---|---|
| Crash / data loss | 🔴 P0 | < 8 business hours |
| Invalid note / dupe | 🟠 P1 | < 24h |
| Inconsistent balance | 🟠 P1 | < 24h |
| Placeholder not working | 🟡 P2 | 3–5 days |
| Missing translation | 🟢 P3 | Weekly |
| Feature request | 🔵 Backlog | Sprint to sprint |

---

## Frequently Asked Questions

**Is Vault 2.1 compatible with Vault 1.x plugins?**
Yes. `VaultPlugin.getEconomyProvider()` returns the legacy `Economy` interface and the modern `SimpleEconomy`. Your plugins won't notice the change.

**Can I change `hmac.secret` without breaking notes?**
Yes, use `previous_secret` and `migration: true` for 30 days (Changelog / Contribute).

**Does Vault touch the old net.milkbowl.vault packages?**
Yes, they are kept for binary compatibility, but new code goes under `net.milkbowl.vault.*`.

---

## Security Disclosures (Critical)

If you find a critical flaw (duplicate notes, HMAC bypass, SQL injection, remote execution): **DO NOT** open a public issue.

Send an email to:
```
security [at] vault-economy.dev
```
with the PGP key fingerprint published in the README. Include step-by-step reproduction and a patch if you have one.

Response time **72h** for critical issues.
