package com.example.vault.menu;

import com.example.vault.VaultPlugin;
import com.example.vault.i18n.Messages;
import com.example.vault.util.ChatInputSanitizer;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;

import java.io.File;
import java.io.IOException;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class ConfigEditorService implements Listener {
    public enum EditType {
        CONFIG_INT,
        CONFIG_DOUBLE,
        MESSAGE_STRING
    }

    private static class Session {
        EditType type;
        String key;
        String titleAfter;
    }

    private final VaultPlugin plugin;
    private final Messages messages;
    private final Map<UUID, Session> sessions = new ConcurrentHashMap<>();

    public ConfigEditorService(VaultPlugin plugin, Messages messages) {
        this.plugin = plugin;
        this.messages = messages;
    }

    public boolean isEditing(Player player) {
        return sessions.containsKey(player.getUniqueId());
    }

    public void startEdit(Player player, EditType type, String key, String titleAfter) {
        Session s = new Session();
        s.type = type;
        s.key = key;
        s.titleAfter = titleAfter;
        sessions.put(player.getUniqueId(), s);
    }

    public boolean cancelEdit(Player player) {
        Session s = sessions.remove(player.getUniqueId());
        if (s == null) return false;
        player.sendMessage(messages.chat("settings.cancelled"));
        Bukkit.getScheduler().runTask(plugin, () -> reopen(player, s.titleAfter));
        return true;
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = false)
    public void onChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        Session s = sessions.get(player.getUniqueId());
        if (s == null) return;
        event.setCancelled(true);

        String msg = ChatInputSanitizer.sanitizeChatInput(event.getMessage());
        String lower = msg.toLowerCase(Locale.ROOT);
        if (lower.equals("cancel") || lower.equals("cancelar")) {
            cancelEdit(player);
            return;
        }

        Bukkit.getScheduler().runTask(plugin, () -> handleInput(player, s, msg));
    }

    private void handleInput(Player player, Session s, String msg) {
        if (s.type == EditType.CONFIG_INT) {
            Integer n = tryParseInt(msg);
            if (n == null) {
                player.sendMessage(messages.chat("settings.invalid_number"));
                return;
            }
            plugin.getConfig().set(s.key, n);
            plugin.saveConfig();
            plugin.reloadPluginState();
            sessions.remove(player.getUniqueId());
            player.sendMessage(messages.formatChat("settings.saved", java.util.Collections.singletonMap("key", s.key)));
            reopen(player, s.titleAfter);
            return;
        }
        if (s.type == EditType.CONFIG_DOUBLE) {
            Double d = tryParseDouble(msg);
            if (d == null) {
                player.sendMessage(messages.chat("settings.invalid_number"));
                return;
            }
            plugin.getConfig().set(s.key, d);
            plugin.saveConfig();
            plugin.reloadPluginState();
            sessions.remove(player.getUniqueId());
            player.sendMessage(messages.formatChat("settings.saved", java.util.Collections.singletonMap("key", s.key)));
            reopen(player, s.titleAfter);
            return;
        }
        if (s.type == EditType.MESSAGE_STRING) {
            String lang = plugin.getConfig().getString("language", "en");
            String normalizedLanguage = Messages.normalizeLanguageFileCode(lang);
            File file = new File(plugin.getDataFolder(), "messages/messages_" + normalizedLanguage + ".yml");
            if (!file.exists()) {
                file = new File(plugin.getDataFolder(), "messages/messages_en.yml");
            }
            YamlConfiguration cfg = YamlConfiguration.loadConfiguration(file);
            cfg.set(s.key, msg);
            try {
                cfg.save(file);
            } catch (IOException e) {
                player.sendMessage(messages.chat("settings.save_failed"));
                sessions.remove(player.getUniqueId());
                reopen(player, s.titleAfter);
                return;
            }
            messages.reload(lang);
            sessions.remove(player.getUniqueId());
            player.sendMessage(messages.formatChat("settings.saved", java.util.Collections.singletonMap("key", s.key)));
            reopen(player, s.titleAfter);
        }
    }

    private void reopen(Player player, String titleAfter) {
        if (titleAfter == null) return;
        if (titleAfter.equals("vault_settings_main")) {
            plugin.getVaultMenuService().openSettingsMenu(player);
            return;
        }
        if (titleAfter.equals("vault_settings_loans")) {
            plugin.getVaultMenuService().openLoanSettingsMenu(player);
            return;
        }
        if (titleAfter.equals("vault_settings_pay")) {
            plugin.getVaultMenuService().openPaySettingsMenu(player);
            return;
        }
        if (titleAfter.equals("vault_settings_texts")) {
            plugin.getVaultMenuService().openTextSettingsMenu(player);
        }
    }

    private Integer tryParseInt(String s) {
        return ChatInputSanitizer.parseInt(s);
    }

    private Double tryParseDouble(String s) {
        return ChatInputSanitizer.parseDouble(s);
    }
}
