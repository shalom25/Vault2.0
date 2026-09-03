---
title: Physical Note "invalid" / Cannot Redeem
description: Why a paper note says note.redeem.invalid. HMAC, PDC, secret.dat, multi-language lore, anti-dupe already_redeemed.
---

# FAQ: Invalid Physical Note (`note.redeem.invalid`)

When **right-clicking** with a `PAPER` note the message `note.redeem.invalid` or `note.redeem.already_redeemed` appears. Here are the cases in order of probability.

## 1. The note was already redeemed (`already_redeemed`)

When a note is successfully redeemed, `redeemNote()` saves the `noteId` in `redeemed_notes.yml`:

```yaml
redeemed:
  - a1b2c3d4e5f60789
  - 73af99c1be22f0a1
```

If they try to redeem the **same ID** again (duplicate via /give, creative inventory, duplicator plugin...):
```java
if (redeemed.containsKey(noteId)) {
    player.sendMessage("note.redeem.already_redeemed");
    return false;
}
```

**This check is GLOBAL**, not per player — if PlayerA withdrew $100, redeemed, then PlayerB tries to redeem a paper with the same HMAC signature, it is rejected.

## 2. `note-secret.dat` was regenerated / does not match

The `HMAC-SHA256` signature uses a 32-byte key stored in `.note-secret.dat` (hidden). If you delete the file, on the next startup `PhysicalNoteService.generateSecret()` creates a **new and different** key.

Consequence: **all old notes generated with the other key** will return wrong HMAC → `invalid`.

**Critical rule**: never delete `.note-secret.dat` without migrating notes first. Make backups.

## 3. Layer failure: PDC not available + corrupt lore

`extractNoteData()` follows this order:

1. **PDC 1.14+** → tries to read `note_id`, `note_amount`, ..., `note_sig` via reflection. If all 6 keys exist and the signature `hmac(noteId|cid|amount|issuerId|issuedAt) == sig` → accepts.
2. **Fallback Lore** → only if PDC does not exist or the signature failed. Parses the lore lines looking for i18n prefixes + the `§8SIG: <hash>` line. Validates `hmac(noteId|currency|amount) == sig`.

If on a Paper 1.20+ server you paste text lore but the PDC was manually edited (NBT-editor) the signature fails → falls back to try via lore and if the lore also doesn't match, `note.redeem.invalid`.

## 4. Lore tags do not match the 11 languages

`extractFallback()` uses dynamic lists generated from `messages_*.yml` but also **hardcoded fallbacks** (Spanish, English, Portuguese, French, German, Dutch, Polish, Russian, Simplified Chinese, Traditional Chinese, Hindi).

If a note was generated with an external translation plugin that changed the text to something NOT contemplated (e.g.: "Value:" instead of "Amount:") → the parser doesn't find the line → returns `null` → `invalid`.

Diagnosis: break a note and paste it with `/item info`. Check that the lines are in the style of:
```
Currency: $
Amount: 500
Issuer: Notch
Serial no: a1b2c3d4e5f60789
Right click to redeem
SIG: abcdef123456...
```

## 5. `parseAmountLoose` does not recognize rare decimals

Lore generated in French locale (`1 000,50 €`) or Chinese (`1,234.56`) with strange separators. The parser accepts `.` as thousands and `,` as decimal, or the other way around, depending on which appears last as the separator.

If the value is `1,,50` or `1.2.3.4` the regex cleanup `[^0-9.,]` + heuristic fails and returns null.

## 6. `looksLikeNote()` but not valid

If the paper carries words like "Note", "Billet", "Schein"..., but does NOT have an HMAC signature (it was created by a player with manual `ItemMeta lore`), `PhysicalNoteService.onInteract()` sends `note.redeem.invalid` and cancels the event to avoid confusion:
```java
if (looksLikeNote(stack)) {
    p.sendMessage(messages.chat("note.redeem.invalid"));
    event.setCancelled(true);
}
```
This is a protection so that arbitrary items cannot be passed off as "fake" notes.
