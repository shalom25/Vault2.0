---
title: Troubleshooting
description: Step-by-step guide to resolve common issues in Vault 2.1.0: invalid notes, lost balances, uncashed offline payments, unrendered colors, and text stuck to the prefix.
---

# Vault 2.1.0 Troubleshooting

Follow these steps in order to diagnose and resolve the most frequent errors.

---

## 1. ❌ Invalid note when redeeming / creating

**Symptom:** The physical note does not validate and shows `INVALID NOTE` or `HMAC mismatch`.

### Step (a): Another language without fallback labels

Notes parse the lore using the `labels` of the active language. If the server switched to another language but the notes were issued in English, parsing fails.

```yaml
# messages_es.yml   →  labels for notes in Spanish
note:
  label_amount: "&fValor:"
  label_currency: "&fMoneda:"
  label_owner: "&fEmitido por:"
  label_date: "&fFecha:"
  label_id: "&fID:"

# messages_en.yml (fallback)
note:
  label_amount: "&fAmount:"
  label_currency: "&fCurrency:"
  label_owner: "&fIssued by:"
  label_date: "&fDate:"
  label_id: "&fID:"
```

✅ **Solution:**
- Keep the `note.label_*` labels consistent across all 11 languages.
- Or use `/vault notes migrate` to re-tag all notes from known inventories to the active language.

### Step (b): HMAC lore mismatch (5-field signature vs 3-field signature)

Vault 2.1.0 signs with **5 fields**:
```
uuid | amount | currency | owner | issuedAt
```

If a note was signed with 3 fields (Vault 1.x) or 6 fields (RC versions), `verifyHmac()` fails.

```
[Vault] ⚠ HMAC lore mismatch
  → Expected: 5 fields (uuid|amount|currency|owner|issuedAt)
  → Received: 3 fields (uuid|amount|currency)
```

✅ **Solution:**
- Do not change the order or number of fields in the HMAC payload.
- If you migrated from v1.x, enable migration mode:

```yaml
hmac:
  version: 2
  migration: true            # accepts v1 (3 fields) and v2 (5 fields)
  previous_secret: ""        # if you rotated the key, put the previous one here
  migration_duration_days: 30
```

Then, force re-signing:
```
/vault notes re-sign-all --dry-run    # Simulate
/vault notes re-sign-all              # Re-issue notes with 2.1.0 signature
```

---

## 2. ❌ Lost balance on restart

**Symptom:** After `/reload` or restart some players have balance set to 0.

### Step (a): Deferred `runTask` was cleaning `CurrencyData`

In pre-2.1.0 versions there was a bug where a `runTask` scheduled without `runTaskAsynchronously` touched `CurrencyData#write` outside the flush cycle, resulting in a partial snapshot.

```
[Vault] ⚠ Incomplete flush detected on previous shutdown
        → 15,420 dirty entries were not persisted.
```

✅ **Fixed in 2.1.0** with:
- `onDisable()` waits up to 5 seconds for the async pool.
- `CurrencyData` uses immutable `CopyOnWriteHashMap` during snapshot.

If it still happens:
```yaml
storage:
  write_behind: false     # Disable write-behind cache
  flush_interval_ticks: 1200   # Flush every 60s more aggressively
  shutdown_wait_seconds: 15    # Wait longer on shutdown
```

### Step (b): Reload without RAM snapshot

Bukkit's `/reload confirm` does not call `onDisable()` cleanly and you lose RAM data if the flush didn't execute.

✅ **Best practices:**
- Never use `/reload`. Use `/plugman reload Vault` or a full restart.
- Before shutting down: `/vault flush now` → forces immediate persistence.
- Enable double-write:

```yaml
storage:
  double_write_yaml: true   # Writes YAML + MySQL in parallel
```

Quick recovery if you lost data:
```
/vault restore snapshot 2025-09-02T18:00
```
(restores the latest JSON snapshot from `plugins/Vault/backups/`)

---

## 3. ❌ Offline payments not cashed (pay pending)

**Symptom:** Player sends `/pay Notch 500` and Notch doesn't receive the 500 when connecting.

### Step (a): `pay_pending.max_on_join` too low

```yaml
pay_pending:
  max_per_player: 20     # Max pending charges per player
  max_on_join: 5         # ⚠ Only processes 5 per join; rest waits
  batch_delay_ticks: 40  # Wait 2s before processing
```

If `max_on_join: 5` and the player has 12 pending payments, the last 7 stay in queue until the next login.

✅ **Solution:**
```yaml
pay_pending:
  max_on_join: 50
  batch_delay_ticks: 20
  expire_after_days: 30
  max_total_queue: 1000
```

Check queue:
```
/vault pending list Notch
---
Notch pending payments: 12
  #1  +500 coins  PlayerA  2h
  #2  +120 coins  PlayerB  5h
  ...
```

Force delivery:
```
/vault pending deliver Notch  (delivers all pending)
```

### Step (b): `charge.*` permissions blocked

If the payer does not have `vault.charge.request` or the recipient does not have `vault.charge.accept`, the charge is marked `DENIED`.

```
/vault pending inspect 12345
---
ChargeRequest id=12345
  from: PlayerA
  to: Notch
  amount: 500 coins
  status: DENIED  (recipient lacks vault.charge.accept)
```

✅ **Solution:**
```
/lp group default permission set vault.charge.accept true
/lp group default permission set vault.charge.request true
```

---

## 4. ❌ Colors do not render on notes (§6 not visible)

**Symptom:** The physical note shows literal `§6$1,500` instead of gold color.

**Typical cause:** Only `economy.format()` is used, without `colorize()` before rendering.

```java
// ❌ WRONG - without colorize
meta.setLore(List.of(economy.format(amount)));

// ✅ CORRECT - colorize iterates § and ChatColor.translateAlternate
String loreLine = colorize(economy.format(amount));
meta.setLore(List.of(loreLine));
```

If you touch third-party plugins (Skript, DeluxeMenus):

```skript
# Skript - use %colored_{expr}%
set lore of item to "&6%colored_formatted balance of player%"
```

```yaml
# DeluxeMenus - color by default OK but if RAW:
lore:
  - "color: '&6%vault_balance_formatted%'"
```

Quick check:
```
/vault debug note test-sample
→ Returns the exact lore with visible §.
```

---

## 5. ❌ Text stuck to [Vault] prefix

**Symptom:** In chat it shows `[Vault]Your message` instead of `[Vault] Your message` (missing a space).

**Cause:** Manual prefix concatenation.

```java
// ❌ WRONG - concat without space
player.sendMessage("[Vault] " + message);  // looks ok
// BUT if someone changes messages.prefix = "[Vault]" without trailing → collides

// Even WRONGER:
String prefix = plugin.getConfig().getString("prefix");
player.sendMessage(prefix + message);      // Sticks the text.
```

✅ **Always use `Messages.prefixed()`:**

```java
@Inject Messages messages;

// ✅ CORRECT - guarantees "prefix + ' ' + body"
player.sendMessage(messages.prefixed("cmd.pay.success", amount, target));
```

`messages.prefixed()` internally:
```java
return prefix + " " + format(key, args);
```

If you develop addons, use:
```java
VaultPlugin.getInstance().getMessages().prefixed("my.key");
```

---

## Support Flow Diagram

```
User reports bug
  │
  ├─ /vault admin info  → confirm version 2.1.0, provider, storage
  │
  ├─ /vault debug pastebin  → upload logs/config/locale
  │
  ├─ Symptom = invalid note    → §1
  ├─ Symptom = lost balance     → §2
  ├─ Symptom = offline payment  → §3
  ├─ Symptom = § colors         → §4
  └─ Symptom = stuck prefix     → §5
```

## Diagnostic Commands

```
/vault admin info         → Status summary
/vault admin storage      → MySQL/YAML status + dirty
/vault verify             → 9 automatic checks
/vault debug pastebin     → Upload logs to dpaste
/vault flush now          → Force write
/vault restore list       → Available snapshots
/vault notes migrate      → Notes migration
/vault pending list       → Pending payments
```

## Quick Ticket Closure Checklist

- [ ] Plugin version (2.1.0 legacy or modern)
- [ ] Java 17 / 21 (according to profile)
- [ ] `/vault verify` 9/9 passed
- [ ] Log with DEBUG level in `config.yml → logger: DEBUG`
- [ ] `storage:` and `hmac:` config
- [ ] Output of `/vault admin storage`
- [ ] Active language and content of `messages_xx.yml` note labels
