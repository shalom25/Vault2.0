---
title: Updater
description: Configure the Vault 2.1.0 update checker that queries Modrinth (ID rj9SgaYL), and the manual /vault update command.
---

# Updater (Modrinth)

Vault 2.1.0 checks for updates against **Modrinth** (not SpigotMC). It never downloads the JAR automatically without your consent.

## Modrinth Project ID

- **Slug:** `vault-economy`
- **Project ID:** `rj9SgaYL`
- **URL:** `https://modrinth.com/plugin/rj9SgaYL`

## Enable / Disable

```yaml
# config.yml
updater:
  update_check: true         # Enable checking
  check_interval_minutes: 720   # Every 12 hours
  notify_ops_on_join: true     # Notify OPs on connect
  notify_console: true         # Notify console on enable
  auto_download: false         # Does NOT download alone! (default: false)
  download_channel: release    # release / beta / alpha
```

## Why Modrinth and not Spigot?

| Feature | Modrinth API | Spigot API |
|---|---|---|
| Documented public API | ✅ Official v3 JSON | ⚠ Unofficial web scraping |
| Rate limit | 300 req/minute | ~1 req/minute blocks |
| SemVer Versioning | ✅ `2.1.0-beta+build.123` | Free (strings) |
| MD5 / SHA512 hashes | ✅ Every build | ⚠ Not always |
| Channel (release/beta) | ✅ Native | No |
| CORS friendly | ✅ Global CDN | No |

## Default behavior

When `update_check: true`:

```
1. Vault.onEnable()
2.   └─ async scheduler after 120 ticks (~6s)
3.       └─ GET https://api.modrinth.com/v3/project/rj9SgaYL/version
4.           ├─ filters versions by download_channel (release)
5.           ├─ compares latest.version() vs Vault.getInstance().getVersion()
6.           └─ if update available → log + broadcast to ops with notify_ops_on_join
```

## Console Log Example

```
> [Vault] Checking Modrinth for updates...
> [Vault] ✓ You have the latest version (2.1.0)
```

If there is an update:

```
> [Vault] ⚠ NEW VERSION AVAILABLE
> [Vault]   Installed : 2.0.9
> [Vault]   Available: 2.1.0
> [Vault]   Download : https://modrinth.com/plugin/rj9SgaYL/version/2.1.0
> [Vault]   Note: Run /vault update for assisted manual update
```

## Command /vault update

Permission: `vault.update` (default: op only)

```
/vault update            # Check now and show info
/vault update check      # Same but without prompt
/vault update download   # Download the new jar into /plugins/Vault/update/
/vault update notes      # View changelog for the new version
```

### Step-by-step execution

```
> /vault update
[Vault] Checking for updates on Modrinth (ID: rj9SgaYL)...
[Vault] ✓ Available version: 2.1.0
[Vault]   Changelog: /vault update notes
[Vault]   Install? Type /vault update download in the next 45s.
```

Then:

```
> /vault update download
[Vault] Downloading Vault-2.1.0.jar (8.4 MB)
[Vault] 10% ███░░░░░░░░░░░░░░░░░
[Vault] ...
[Vault] 100% ███████████████████
[Vault] ✓ Download OK → plugins/Vault/update/Vault-2.1.0.jar
[Vault] SHA512 verified ✓
[Vault] ℹ Restart the server to apply (or /reload confirm)
```

## Automatic File Installation

`auto_download: true` only downloads, never hot-loads:

```
plugins/Vault/update/
  └─ Vault-2.1.0.jar  (ready to replace on next restart)
```

## Internal Implementation

```java
@Service
public class ModrinthUpdater {

    private static final String MODRINTH_ID = "rj9SgaYL";
    private static final String API = "https://api.modrinth.com/v3/project/%s/version";

    public CompletableFuture<Optional<Version>> check() {
        return CompletableFuture.supplyAsync(() -> {
            try (var client = HttpClient.newHttpClient()) {
                var req = HttpRequest.newBuilder()
                    .uri(URI.create(API.formatted(MODRINTH_ID)))
                    .header("User-Agent", "VaultEconomy/2.1 (admin@you.com)")
                    .timeout(Duration.ofSeconds(15))
                    .build();

                var res = client.send(req, HttpResponse.BodyHandlers.ofString());
                JsonArray arr = new Gson().fromJson(res.body(), JsonArray.class);

                Version latest = filterChannel(arr, channel, semverComparator);
                if (latest.isNewerThan(current)) {
                    return Optional.of(latest);
                }
            } catch (Exception e) {
                logger.warn("Could not check for updates", e);
            }
            return Optional.empty();
        });
    }
}
```

## Proxies / Offline

If your server has no internet access:

```yaml
updater:
  update_check: false

# Console:
# [Vault] Update checker disabled by config.
```

For corporate environments with a proxy:

```yaml
updater:
  proxy_host: proxy.corp.local
  proxy_port: 3128
  proxy_user: user
  proxy_pass: pass
  update_check: true
```

## SHA512 Hash Verification

Vault never installs a .jar without validating the hash published by Modrinth:

```
GET /v3/project/rj9SgaYL/version/2.1.0
→ files[0].hashes.sha512
   vs
   sha512(downloaded file)
   ✓ match OK → move to /update/
   ✗ mismatch → delete + log "FRAUDULENT HASH"
```

## Modrinth Rate limits

Public API: 300 requests/minute per IP. Vault only checks twice a day so you'll never hit it.

If you have 1000+ instances, you can set `check_interval_minutes: 1440` (once per day).

## Comparison `rj9SgaYL` vs `Vault` on Spigot

- **rj9SgaYL** = Official current Vault 2.x project on Modrinth.
- **spigotmc.org/resources/vault.34315/** = Vault 1.x legacy (unmaintained).

That's why the download URL should always point to Modrinth.
