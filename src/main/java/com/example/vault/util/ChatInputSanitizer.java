package com.example.vault.util;

public final class ChatInputSanitizer {
    private ChatInputSanitizer() {
    }

    public static String sanitizeChatInput(String raw) {
        if (raw == null) return "";
        String s = raw;
        s = s.replace('\u00A0', ' ').replace('\u2007', ' ').replace('\u202F', ' ');
        s = org.bukkit.ChatColor.stripColor(s);
        s = s.replaceAll("(?i)&#[0-9a-f]{6}", "");
        s = s.replaceAll("(?i)<#[0-9a-f]{6}>", "");
        s = s.replaceAll("(?i)&[0-9a-fk-orx]", "");
        s = s.replaceAll("(?i)§[0-9a-fk-orx]", "");
        return s.trim();
    }

    public static String sanitizeNumberInput(String raw) {
        String s = sanitizeChatInput(raw).replace(" ", "");
        if (s.isEmpty()) return s;

        int commas = 0;
        int dots = 0;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == ',') commas++;
            else if (c == '.') dots++;
        }

        if (commas > 0 && dots > 0) {
            int lastComma = s.lastIndexOf(',');
            int lastDot = s.lastIndexOf('.');
            if (lastComma > lastDot) s = s.replace(".", "").replace(',', '.');
            else s = s.replace(",", "");
        } else if (commas > 0) {
            if (commas == 1) {
                int idx = s.indexOf(',');
                int after = s.length() - idx - 1;
                s = (after == 3) ? s.replace(",", "") : s.replace(',', '.');
            } else {
                s = s.replace(",", "");
            }
        } else if (dots > 1) {
            String[] parts = s.split("\\.");
            boolean grouping = true;
            for (int i = 1; i < parts.length; i++) {
                if (parts[i].length() != 3) {
                    grouping = false;
                    break;
                }
            }
            if (grouping) s = s.replace(".", "");
            else {
                int lastDot = s.lastIndexOf('.');
                s = s.substring(0, lastDot).replace(".", "") + s.substring(lastDot);
            }
        }
        return s;
    }

    public static String sanitizeIntegerInput(String raw) {
        return sanitizeChatInput(raw).replace(" ", "").replace(",", "").replace(".", "");
    }

    public static Double parsePositiveDouble(String raw) {
        try {
            double d = Double.parseDouble(sanitizeNumberInput(raw));
            if (!Double.isFinite(d) || d <= 0.0) return null;
            return d;
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    public static Integer parsePositiveInt(String raw) {
        try {
            int n = Integer.parseInt(sanitizeIntegerInput(raw));
            if (n <= 0) return null;
            return n;
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    public static Integer parseNonNegativeInt(String raw) {
        try {
            int n = Integer.parseInt(sanitizeIntegerInput(raw));
            if (n < 0) return null;
            return n;
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    public static Integer parseInt(String raw) {
        try {
            return Integer.parseInt(sanitizeIntegerInput(raw));
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    public static Double parseDouble(String raw) {
        try {
            double d = Double.parseDouble(sanitizeNumberInput(raw));
            if (!Double.isFinite(d)) return null;
            return d;
        } catch (NumberFormatException ignored) {
            return null;
        }
    }
}
