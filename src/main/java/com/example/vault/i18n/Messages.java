package com.example.vault.i18n;

import com.example.vault.util.ColorUtil;
import org.bukkit.ChatColor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

public class Messages {
    private static final Pattern TRAILING_WHITESPACE = Pattern.compile("\\s+$");
    private static final Pattern LEADING_WHITESPACE = Pattern.compile("^\\s+");
    private final Plugin plugin;
    private FileConfiguration primary;
    private FileConfiguration fallback;

    public Messages(Plugin plugin, String language) {
        this.plugin = plugin;
        reload(language);
    }

    private void saveResourceOnce(String relativePath) {
        File out = new File(plugin.getDataFolder(), relativePath);
        File parent = out.getParentFile();
        if (parent != null && !parent.exists()) {
            parent.mkdirs();
        }
        if (!out.exists()) {
            plugin.saveResource(relativePath, false);
        }
    }

    private void syncYamlDefaults(String resourcePath) {
        saveResourceOnce(resourcePath);
        InputStream in = plugin.getResource(resourcePath);
        if (in == null) {
            return;
        }
        try {
            File out = new File(plugin.getDataFolder(), resourcePath);
            YamlConfiguration current = YamlConfiguration.loadConfiguration(out);
            YamlConfiguration defaults = YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8));
            boolean changed = false;
            for (String path : defaults.getKeys(true)) {
                if (path == null || path.isEmpty()) continue;
                if (!current.contains(path)) {
                    current.set(path, defaults.get(path));
                    changed = true;
                }
            }
            if (changed) {
                current.save(out);
            }
        } catch (Exception ignored) {
        }
    }

    public void reload(String language) {
<<<<<<< Updated upstream
        syncYamlDefaults("messages/messages_en.yml");
        syncYamlDefaults("messages/messages_es.yml");
        syncYamlDefaults("messages/messages_fr.yml");
        syncYamlDefaults("messages/messages_de.yml");
        syncYamlDefaults("messages/messages_nl.yml");
        syncYamlDefaults("messages/messages_pl.yml");
        syncYamlDefaults("messages/messages_zh_CN.yml");
        syncYamlDefaults("messages/messages_zh_TW.yml");
        syncYamlDefaults("messages/messages_ru.yml");
        syncYamlDefaults("messages/messages_pt.yml");
        syncYamlDefaults("messages/messages_hi.yml");
        this.fallback = YamlConfiguration.loadConfiguration(new File(plugin.getDataFolder(), "messages/messages_en.yml"));
        String normalizedLanguage = normalizeLanguageFileCode(language);
        File langFile = new File(plugin.getDataFolder(), "messages/messages_" + normalizedLanguage + ".yml");
        if (!langFile.exists()) {
            plugin.getLogger().warning("Language file messages/messages_" + normalizedLanguage + ".yml not found. Using English.");
=======
        // Ensure files exist in data/messages/
        saveResourceOnce("messages/messages_en.yml");
        saveResourceOnce("messages/messages_es.yml");
        // Load fallback (English) and selected language from messages/ subfolder
        this.fallback = YamlConfiguration.loadConfiguration(new File(plugin.getDataFolder(), "messages/messages_en.yml"));
        File langFile = new File(plugin.getDataFolder(), "messages/messages_" + language.toLowerCase() + ".yml");
        if (!langFile.exists()) {
            plugin.getLogger().warning("Language file messages/messages_" + language + ".yml not found. Using English.");
>>>>>>> Stashed changes
            langFile = new File(plugin.getDataFolder(), "messages/messages_en.yml");
        }
        this.primary = YamlConfiguration.loadConfiguration(langFile);
    }

    public static String normalizeLanguageFileCode(String language) {
        if (language == null || language.trim().isEmpty()) return "en";
        String normalized = language.trim().replace('-', '_');
        if (normalized.regionMatches(true, 0, "zh_", 0, 3) && normalized.length() > 3) {
            return "zh_" + normalized.substring(3).toUpperCase(Locale.ROOT);
        }
        return normalized.toLowerCase(Locale.ROOT);
    }

    public String getOptional(String key) {
        String s = primary.getString(key);
        if (s == null) s = fallback.getString(key);
        return s;
    }

    public List<String> getListOptional(String key) {
        if (primary.isList(key)) return primary.getStringList(key);
        if (fallback.isList(key)) return fallback.getStringList(key);
        return Collections.emptyList();
    }

    public List<String> colorList(String key) {
        List<String> list = getListOptional(key);
        if (list.isEmpty()) return Collections.emptyList();
        java.util.List<String> out = new java.util.ArrayList<>(list.size());
        for (String s : list) {
            if (s == null) continue;
            String line = s;
            line = line.replace("%currency_symbol%", currencySymbol());
            line = line.replace("%currency_position%", currencyPosition());
            line = line.replace("%currency_space%", String.valueOf(currencySpace()));
            out.add(ColorUtil.colorize(line));
        }
        return out;
    }

    public List<String> formatList(String key, Map<String, String> values) {
        List<String> list = getListOptional(key);
        if (list.isEmpty()) return Collections.emptyList();
        java.util.List<String> out = new java.util.ArrayList<>(list.size());
        for (String s : list) {
            if (s == null) continue;
            String line = s;
            line = line.replace("%currency_symbol%", currencySymbol());
            line = line.replace("%currency_position%", currencyPosition());
            line = line.replace("%currency_space%", String.valueOf(currencySpace()));
            if (values != null) {
                for (Map.Entry<String, String> e : values.entrySet()) {
                    line = line.replace("%" + e.getKey() + "%", e.getValue() == null ? "" : e.getValue());
                }
            }
            out.add(ColorUtil.colorize(line));
        }
        return out;
    }

    public String get(String key) {
        String s = primary.getString(key);
        if (s == null) s = fallback.getString(key, key);
        return s;
    }

    public String color(String key) {
        return ColorUtil.colorize(get(key));
    }

    public String format(String key, Map<String, String> values) {
        String s = get(key);
        s = s.replace("%currency_symbol%", currencySymbol());
        s = s.replace("%currency_position%", currencyPosition());
        s = s.replace("%currency_space%", String.valueOf(currencySpace()));
        for (Map.Entry<String, String> e : values.entrySet()) {
            s = s.replace("%" + e.getKey() + "%", e.getValue());
        }
        return s;
    }

    public String prefix() {
        String raw = primary.getString("prefix");
        if (raw == null) raw = fallback.getString("prefix", "");
        if (raw.isEmpty()) return "";
        String prefix = ColorUtil.colorize(raw);
        prefix = TRAILING_WHITESPACE.matcher(prefix).replaceFirst("");
        return prefix + ChatColor.RESET;
    }

    public String prefixed(String colorizedText) {
        String text = colorizedText == null ? "" : colorizedText;
        text = LEADING_WHITESPACE.matcher(text).replaceFirst("");
        return prefix().isEmpty() ? text : prefix() + " " + text;
    }

    public String chat(String key) {
        String text = ColorUtil.colorize(get(key));
        text = LEADING_WHITESPACE.matcher(text).replaceFirst("");
        return prefix().isEmpty() ? text : prefix() + " " + text;
    }

    public String formatChat(String key, Map<String, String> values) {
        String text = ColorUtil.colorize(format(key, values));
        text = LEADING_WHITESPACE.matcher(text).replaceFirst("");
        return prefix().isEmpty() ? text : prefix() + " " + text;
    }

    public String colorize(String raw) {
        return ColorUtil.colorize(raw);
    }

    private String currencySymbol() {
        String raw = plugin.getConfig().getString("currency.symbol", "");
        if (raw == null) raw = "";
        return ColorUtil.colorize(raw);
    }

    private String currencyPosition() {
        String raw = plugin.getConfig().getString("currency.position", "none");
        if (raw == null) raw = "none";
        return raw.trim().toLowerCase(Locale.ROOT);
    }

    private boolean currencySpace() {
        return plugin.getConfig().getBoolean("currency.space", true);
    }
}
