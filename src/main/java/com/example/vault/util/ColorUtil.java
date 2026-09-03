package com.example.vault.util;

import org.bukkit.ChatColor;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ColorUtil {
    private static final Pattern HEX_PATTERN = Pattern.compile("(?i)(?:&#|<#|#)([0-9a-f]{6})>?");

    private ColorUtil() {
    }

    public static String colorize(String input) {
        if (input == null || input.isEmpty()) return input;

        String withHex = applyHexColors(input);
        return ChatColor.translateAlternateColorCodes('&', withHex);
    }

    private static String applyHexColors(String input) {
        Matcher matcher = HEX_PATTERN.matcher(input);
        StringBuffer out = new StringBuffer();
        while (matcher.find()) {
            String hex = "#" + matcher.group(1);
            String replacement = toChatColor(hex);
            matcher.appendReplacement(out, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(out);
        return out.toString();
    }

    private static String toChatColor(String hex) {
        try {
            Class<?> bungeeColor = Class.forName("net.md_5.bungee.api.ChatColor");
            java.lang.reflect.Method of = bungeeColor.getMethod("of", String.class);
            Object color = of.invoke(null, hex);
            if (color != null) return color.toString();
        } catch (Throwable ignored) {
        }
        return nearestLegacyColor(hex).toString();
    }

    private static ChatColor nearestLegacyColor(String hex) {
        int rgb = Integer.parseInt(hex.substring(1), 16);
        int r = (rgb >> 16) & 0xFF;
        int g = (rgb >> 8) & 0xFF;
        int b = rgb & 0xFF;

        ChatColor best = ChatColor.WHITE;
        int bestDistance = Integer.MAX_VALUE;
        for (LegacyColor color : LegacyColor.values()) {
            int dr = r - color.r;
            int dg = g - color.g;
            int db = b - color.b;
            int distance = dr * dr + dg * dg + db * db;
            if (distance < bestDistance) {
                bestDistance = distance;
                best = color.chatColor;
            }
        }
        return best;
    }

    private enum LegacyColor {
        BLACK(ChatColor.BLACK, 0x00, 0x00, 0x00),
        DARK_BLUE(ChatColor.DARK_BLUE, 0x00, 0x00, 0xAA),
        DARK_GREEN(ChatColor.DARK_GREEN, 0x00, 0xAA, 0x00),
        DARK_AQUA(ChatColor.DARK_AQUA, 0x00, 0xAA, 0xAA),
        DARK_RED(ChatColor.DARK_RED, 0xAA, 0x00, 0x00),
        DARK_PURPLE(ChatColor.DARK_PURPLE, 0xAA, 0x00, 0xAA),
        GOLD(ChatColor.GOLD, 0xFF, 0xAA, 0x00),
        GRAY(ChatColor.GRAY, 0xAA, 0xAA, 0xAA),
        DARK_GRAY(ChatColor.DARK_GRAY, 0x55, 0x55, 0x55),
        BLUE(ChatColor.BLUE, 0x55, 0x55, 0xFF),
        GREEN(ChatColor.GREEN, 0x55, 0xFF, 0x55),
        AQUA(ChatColor.AQUA, 0x55, 0xFF, 0xFF),
        RED(ChatColor.RED, 0xFF, 0x55, 0x55),
        LIGHT_PURPLE(ChatColor.LIGHT_PURPLE, 0xFF, 0x55, 0xFF),
        YELLOW(ChatColor.YELLOW, 0xFF, 0xFF, 0x55),
        WHITE(ChatColor.WHITE, 0xFF, 0xFF, 0xFF);

        private final ChatColor chatColor;
        private final int r;
        private final int g;
        private final int b;

        LegacyColor(ChatColor chatColor, int r, int g, int b) {
            this.chatColor = chatColor;
            this.r = r;
            this.g = g;
            this.b = b;
        }
    }
}
