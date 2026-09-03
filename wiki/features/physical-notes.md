---
title: Physical Notes
description: Withdraw money as PAPER items with HMAC-SHA256 anti-duplicate cryptographic signature. PersistentDataContainer support 1.14+ and legacy lore.
---

# Physical Notes (`/vault withdraw`)

Converts digital balance into **redeemable physical paper items**. Each note carries an **HMAC-SHA256 signature** that makes duplication via NBT / external plugins infeasible.

## Withdrawing Notes

```
/vault withdraw <amount>
```

Required permission: `vault.withdraw` (default `true`).

Flow:
1. Validates positive amount and withdraws from wallet with `TxType.NOTE_WITHDRAW`
2. Generates a random 16-char hex `noteId` (truncated UUID)
3. Builds an `ItemStack` of `Material.PAPER` with display name + i18n lore
4. Stores signed metadata and delivers the item to the player

## Secure Storage — Dual Layer

### Layer 1: PersistentDataContainer (1.14+)

If the `NamespacedKey` + `PersistentDataContainer` API is available at runtime, 6 keys are saved via reflection (PhysicalNoteService.java:89–132):

| NamespacedKey          | Type    | Content                                      |
|------------------------|---------|----------------------------------------------|
| `vault:note_id`        | STRING  | Unique note ID                               |
| `vault:note_amount`    | DOUBLE  | Face value                                   |
| `vault:note_currency`  | STRING  | Currency ID (e.g. `default`)                 |
| `vault:note_issuer`    | STRING  | Issuer UUID                                  |
| `vault:note_issued_at` | LONG    | Issue timestamp (epoch ms)                   |
| `vault:note_sig`       | STRING  | HMAC(payload) in Base64 URL-safe             |

Signed payload: `noteId|cid|amount|issuerId|issuedAt`

### Layer 2: Legacy Lore HMAC (1.8 – 1.13)

If PDC does not exist, the signature **written as the last lore line** is validated:
```
§8SIG: <HMAC(noteId|currency|amount)>
```

Lore tags are extracted via dynamic parsers.

## Dynamic i18n Label Extraction

For compatibility with **11 languages** and pre-v2.1 legacy notes, `extractFallback()` detects prefixes such as "Nº serie", "Importe", "Moneda" using `labelStarts()` + hardcoded fallback lists (PhysicalNoteService.java:408–482):

**Serial** — Detects: `Nº serie` / `Serial` / `S/N` / `Número de série` / `Seriennummer` / `Numer seryjny` / `Серийный номер` / `序列号` / `序號` / `क्रम संख्या`

**Amount** — Detects: `Importe` / `Amount` / `Valor` / `Montant` / `Betrag` / `Bedrag` / `Kwota` / `Сумма` / `金额` / `金額` / `राशि`

**Currency** — Detects: `Moneda` / `Currency` / `Moeda` / `Devise` / `Währung` / `Valuta` / `Waluta` / `Валюта` / `货币` / `貨幣` / `मुद्रा`

Additionally `parseAmountLoose()` interprets decimal/thousand separators `.` and `,` based on position (last occurrence = decimal).

## Redeeming Notes

**Right-click** while holding the note (air or block) triggers `PlayerInteractEvent`. Flow:

1. `extractNoteData()` validates PDC first, then lore fallback
2. Checks `redeemed_notes.yml` table for already-redeemed IDs
3. If new: deposits the amount → `TxType.NOTE_REDEEM` → marks as redeemed
4. Removes 1 unit from the stack (or `setItemInHand(null)` if quantity = 1)

### Anti-dupe Window + redeemed_notes

- **redeemed_notes.yml** stores the global list of redeemed IDs (persisted on disk)
- `ConcurrentHashMap` in memory as a fast cache
- If HMAC does not match or the ID was already used, returns `note.redeem.invalid` or `note.redeem.already_redeemed`

### HMAC Secret

Generated via `SecureRandom` → 32 bytes and saved in `.note-secret.dat` (hidden with `attrib +H` on Windows). **Without this file, no existing note can be validated.**
