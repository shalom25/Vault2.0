---
title: SkinsRestorer
description: Integration with SkinsRestorer to display player-skull heads in the payment menu (/pay), with premium and non-premium support.
---

# SkinsRestorer

Vault 2.1.0 natively integrates with SkinsRestorer to render player heads with their real skin in the /pay GUI menu.

## Features

- ✅ Skinned heads in /pay GUI
- ✅ Premium support (Mojang profile)
- ✅ Non-premium support (skins via SkinsRestorer SkinStorage)
- ✅ Live profile via reflection for maximum compatibility
- ✅ Automatic fallback to Steve/Alex head if skin fails
- ✅ Legacy 1.8.8 compatibility via reflection

## PayMenuService

The `PayMenuService` manages skin resolution.

```java
// Internal usage example:
@Service
public class PayMenuService {
    private final SkinResolver skinResolver;

    public ItemStack buildPlayerHead(OfflinePlayer target) {
        return skinResolver.resolveHead(target);
    }
}
```

## Skin Resolution Modes

| Mode | Description |
|---|---|
| **Premium** | Uses `Player#getPlayerProfile()` (1.18+) or native GameProfile |
| **Non Premium** | Queries `SkinsRestorerAPI#getSkinData()` |
| **Offline** | `Bukkit.createInventory.setItemMeta(skullMeta) + setOwningPlayer()` |
| **Legacy 1.8** | Reflection on NMS `GameProfile` + `PropertyMap` |

## Reflection 1.8 (SkullMeta)

```java
// Legacy 1.8.8 compatibility via reflection:

public ItemStack resolveLegacy(UUID uuid, String name) {
    try {
        Class<?> gameProfileClass = Class.forName("com.mojang.authlib.GameProfile");
        Object profile = gameProfileClass
            .getConstructor(UUID.class, String.class)
            .newInstance(uuid, name);

        // Inject texture from SkinsRestorer
        Method getSkinData(playerName = skinRestorerAPI.getSkinData(name);

        return injectTexture(profile, skinData.getValue(), skinData.getSignature());

        SkullMeta meta = (SkullMeta) Bukkit.getItemFactory()
            .getItemMeta(Material.SKULL_ITEM);
        Field profileField = meta.getClass().getDeclaredField("profile");
        profileField.setAccessible(true);
        profileField.set(meta, profile);
    } catch (Exception e) {
        // Fallback to Steve
    }
}
```

## Configuration

```yaml
# config.yml
skins:
  use_skinsrestorer: true
  fallback_skin: "MHF_Steve"
  cache_duration_minutes: 60
  live_profile_refresh: true
```

## Dependency in plugin.yml

```yaml
softdepend:
  - SkinsRestorer
```

## Plugin Detection

```java
if (Bukkit.getPluginManager().isPluginEnabled("SkinsRestorer")) {
    SkinsRestorerAPI api = SkinsRestorer.getInstance().getAPI();
    skinResolver = new SkinsRestorerSkinResolver(api);
} else {
    skinResolver = new DefaultSkinResolver();
}
```

## Resolution Flow in /pay

```
1. User runs /pay <player>
2. PayMenuService opens GUI inventory
3. SkinResolver.resolveHead(targetPlayer)
   ├─ Premium → GameProfile with Mojang texture
   ├─ Non Premium → SkinsRestorer SkinData
   └─ Fallback → Default head
4. Renders head in confirmation slot
5. User confirms payment
```

## Skin Troubleshooting

| Issue | Solution |
|---|---|
| Heads without skin in 1.8 | Ensure the server has the correct `authlib` |
| Texture does not load on non-premium | Run `/sr applyskin <player>` |
| Steve heads in GUI | Verify `skins.use_skinsrestorer: true` |
| Lag when opening /pay | Increase `cache_duration_minutes` |
