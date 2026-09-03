---
title: Discord Webhook
description: Discord notifications without external libraries using java.net.http.HttpClient. Alerts for large transactions, anti-dupe, and persistence failures.
---

# Discord Webhook Notifier

`DiscordWebhookNotifier` sends embedded alerts to a Discord channel when relevant events occur. All using the standard **`java.net.http.HttpClient` (Java 11+)** API — no JDA, no OkHttp, no external dependencies.

## Configuration

```yaml
discord:
  webhook_url: ""              # ← Leave empty to disable
  threshold_amount: 100000     # Notify if |tx| > threshold
  username: "Vault Bot"
  avatar_url: ""               # optional avatar URL
```

**Activation condition**: `enabled = !webhook_url.isEmpty() && HttpClient != null`. If the JVM cannot instantiate `HttpClient.newBuilder()`, the notifier is silently disabled.

## `HttpClient` (No External Libraries)

```java
HttpClient.newBuilder()
  .connectTimeout(Duration.ofSeconds(5))
  .followRedirects(HttpClient.Redirect.NORMAL)
  .build();
```

Each request uses:
- `HttpRequest.BodyPublishers.ofString(json, UTF_8)` — POST JSON method
- `HttpResponse.BodyHandlers.discarding()` — does not parse response
- Global 8s timeout + `CompletableFuture.orTimeout(10s, TimeUnit.SECONDS)`

HTTP codes outside the `2xx` range are logged as a warning: `[Discord] Webhook returned HTTP 404`.

## Events That Trigger an Embed

`DiscordWebhookNotifier` is registered as a listener of `TransactionLogService`:

| Trigger Event               | Embed Color | Rule / Key Fields                                                    |
|--------------------------|-------------|-----------------------------------------------------------------------|
| Transaction > threshold | Green/Red   | `|amount| >= threshold_amount`. Max 5 per batch.                    |
| Anti-dupe window detects  | Orange/Red  | `hard=true` red 0xff0000, `soft=false` orange 0xffa500.                 |
| Batch flush with error    | Red         | `buildErrorEmbed` — `batch_size` + 400-char truncated exception    |

## Embed Structure: Transaction > Threshold

```json
{
  "username": "Vault Bot",
  "avatar_url": "...",
  "embeds": [{
    "title": "💸 Large Transaction: PLAYER_PAY",
    "description": "From Notch → Player123",
    "color": 65372,
    "timestamp": "2024-01-01T12:00:00.000Z",
    "fields": [
      { "name": "TxID",     "value": "`a1b2c3d4e5f60789`", "inline": true  },
      { "name": "Currency", "value": "default",             "inline": true  },
      { "name": "Amount",   "value": "**$ 1.5M**",          "inline": true  },
      { "name": "From",     "value": "`Notch`",              "inline": true  },
      { "name": "To",       "value": "`Player123`",          "inline": true  },
      { "name": "World",    "value": "world",                "inline": true  },
      { "name": "Metadata", "value": "• note: rent payment\n• qp: 50%", "inline": false }
    ]
  }]
}
```

Color depends on flow (green if recipient gains, red if loses). i18n keys for titles/fields are looked up in:
- `discord.threshold.title`, `discord.threshold.desc`
- `discord.dupe.title`, `discord.error.title`
- `discord.tx_type.PLAYER_PAY` etc. (24+ keys, one per TxType)
- `discord.field.txid`, `discord.field.currency`, `discord.field.amount`...

All embeds use the **language defined in `config.language`** (of the 11 supported). If a key is missing, the raw TxType name is shown as a fallback.

## Anti-spam LRU (500 IDs)

To avoid spam from repeated flushes, a **`RollingLinkedHashSet extends LinkedHashSet<String>` of 500 max entries** is used. When it reaches max size, it evicts the oldest by insertion order.

Registered keys:
- Normal transactions → raw `txId`
- Duplicates → `DUPE:` prefix + `txId`

If the key already exists in the set, the embed is silently discarded.
