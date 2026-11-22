package com.example.vault.util;

import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.Server;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * Safe player name resolution without deprecated APIs.
 */
public final class PlayerResolver {
    private PlayerResolver() {}

    /**
     * Resolve a player by name, preferring online players, then offline players
     * known to the server. Returns null if not found.
     */
    public static OfflinePlayer resolveByName(Plugin plugin, String name) {
        if (name == null || name.isEmpty()) return null;
        Player online = plugin.getServer().getPlayerExact(name);
        if (online != null) return online;
        for (OfflinePlayer p : plugin.getServer().getOfflinePlayers()) {
            String n = p.getName();
            if (n != null && n.equalsIgnoreCase(name)) {
                return p;
            }
        }
        return null;
    }

    public static OfflinePlayer resolveByName(org.bukkit.Server server, String name) {
        if (name == null || name.isEmpty()) return null;
        Player online = server.getPlayerExact(name);
        if (online != null) return online;
        for (OfflinePlayer p : server.getOfflinePlayers()) {
            String n = p.getName();
            if (n != null && n.equalsIgnoreCase(name)) {
                return p;
            }
        }
        return null;
    }

    /**
     * Same as resolveByName, but if the server is in offline-mode and the player
     * is not found, generates a deterministic offline UUID and returns that OfflinePlayer.
     * Do not use this for payments — inventing UUIDs for unknown names can take money.
     */
    public static OfflinePlayer resolveByNameWithOfflineFallback(Server server, String name) {
        OfflinePlayer found = resolveByName(server, name);
        if (found != null) return found;
        if (!server.getOnlineMode()) {
            UUID offlineUuid = UUID.nameUUIDFromBytes(("OfflinePlayer:" + name).getBytes(StandardCharsets.UTF_8));
            return server.getOfflinePlayer(offlineUuid);
        }
        return null;
    }

    public static OfflinePlayer resolveByNameWithOfflineFallback(Plugin plugin, String name) {
        org.bukkit.Server server = plugin.getServer();
        OfflinePlayer found = resolveByName(server, name);
        if (found != null) return found;
        boolean allowFallback = true;
        try {
            if (plugin instanceof org.bukkit.plugin.java.JavaPlugin) {
                org.bukkit.plugin.java.JavaPlugin jp = (org.bukkit.plugin.java.JavaPlugin) plugin;
                allowFallback = jp.getConfig().getBoolean("offline-uuid-fallback", true);
            }
        } catch (Exception ignored) {}
        if (!server.getOnlineMode() && allowFallback) {
            UUID offlineUuid = UUID.nameUUIDFromBytes(("OfflinePlayer:" + name).getBytes(StandardCharsets.UTF_8));
            return server.getOfflinePlayer(offlineUuid);
        }
        return null;
    }
}
