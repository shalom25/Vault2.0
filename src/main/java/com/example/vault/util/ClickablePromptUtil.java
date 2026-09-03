package com.example.vault.util;

import com.example.vault.i18n.Messages;
import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;

public final class ClickablePromptUtil {
    private static final String MARKER = "__CANCEL__";

    private ClickablePromptUtil() {
    }

    public static void sendPromptWithClickableCancel(Player player, Messages messages, String key, Map<String, String> values,
                                                     String cancelWord, ClickEvent.Action action, String clickValue) {
        Map<String, String> map = new HashMap<>(values);
        map.put("cancel", MARKER);

        String raw = messages.format(key, map);
        if (raw == null || raw.isEmpty()) {
            Map<String, String> fallback = new HashMap<>(values);
            fallback.put("cancel", cancelWord);
            player.sendMessage(messages.formatChat(key, fallback));
            return;
        }

        raw = raw.replaceFirst("^\\s+", "");
        int markerIndex = raw.indexOf(MARKER);
        if (markerIndex < 0) {
            Map<String, String> fallback = new HashMap<>(values);
            fallback.put("cancel", cancelWord);
            player.sendMessage(messages.formatChat(key, fallback));
            return;
        }

        String before = raw.substring(0, markerIndex);
        String after = raw.substring(markerIndex + MARKER.length());

        TextComponent combined = new TextComponent("");
        String prefix = messages.prefix();
        if (!prefix.isEmpty()) {
            for (BaseComponent bc : legacyComponents(prefix)) combined.addExtra(bc);
            combined.addExtra(new TextComponent(" "));
        }
        for (BaseComponent bc : legacyComponents(messages.colorize(before))) combined.addExtra(bc);

        TextComponent cancelComponent = new TextComponent(cancelWord);
        cancelComponent.setColor(net.md_5.bungee.api.ChatColor.RED);
        cancelComponent.setClickEvent(new ClickEvent(action, clickValue));
        combined.addExtra(cancelComponent);

        for (BaseComponent bc : legacyComponents(messages.colorize(after))) combined.addExtra(bc);
        player.spigot().sendMessage(combined);
    }

    private static BaseComponent[] legacyComponents(String legacy) {
        if (legacy == null || legacy.isEmpty()) return new BaseComponent[0];
        try {
            java.lang.reflect.Method m = TextComponent.class.getMethod("fromLegacy", String.class);
            Object out = m.invoke(null, legacy);
            if (out instanceof BaseComponent[]) return (BaseComponent[]) out;
        } catch (Throwable ignored) {
        }
        try {
            java.lang.reflect.Method m = TextComponent.class.getMethod("fromLegacyText", String.class);
            Object out = m.invoke(null, legacy);
            if (out instanceof BaseComponent[]) return (BaseComponent[]) out;
        } catch (Throwable ignored) {
        }
        return new BaseComponent[] { new TextComponent(legacy) };
    }
}
