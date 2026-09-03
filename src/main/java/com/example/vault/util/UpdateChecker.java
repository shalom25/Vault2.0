package com.example.vault.util;

import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;

public class UpdateChecker {

    private final JavaPlugin plugin;
    private final String modrinthProjectId;

    public UpdateChecker(JavaPlugin plugin, String modrinthProjectId) {
        this.plugin = plugin;
        this.modrinthProjectId = modrinthProjectId;
    }

    public void getLatestVersion(Consumer<String> consumer) {
        Bukkit.getScheduler().runTaskAsynchronously(this.plugin, () -> {
            try {
                HttpURLConnection connection = (HttpURLConnection) URI
                        .create("https://api.modrinth.com/v2/project/" + this.modrinthProjectId + "/version")
                        .toURL()
                        .openConnection();
                connection.setRequestMethod("GET");
                connection.setRequestProperty("User-Agent", "Vault2.0/" + this.plugin.getDescription().getVersion());
                connection.setConnectTimeout(5000);
                connection.setReadTimeout(5000);

                if (connection.getResponseCode() == 200) {
                    String json;
                    try (BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
                        StringBuilder sb = new StringBuilder();
                        String line;
                        while ((line = reader.readLine()) != null) sb.append(line);
                        json = sb.toString();
                    }
                    String latestVersion = extractFirstJsonStringField(json, "version_number");
                    if (latestVersion != null && !latestVersion.isEmpty()) consumer.accept(latestVersion);
                }
            } catch (IOException exception) {
                this.plugin.getLogger().info("Cannot look for updates: " + exception.getMessage());
            }
        });
    }

    private String extractFirstJsonStringField(String json, String field) {
        if (json == null || json.isEmpty() || field == null || field.isEmpty()) return null;
        String needle = "\"" + field + "\"";
        int i = json.indexOf(needle);
        if (i < 0) return null;
        i = json.indexOf(':', i + needle.length());
        if (i < 0) return null;
        i++;
        while (i < json.length() && Character.isWhitespace(json.charAt(i))) i++;
        if (i >= json.length() || json.charAt(i) != '"') return null;
        i++;
        StringBuilder out = new StringBuilder();
        boolean escaping = false;
        for (; i < json.length(); i++) {
            char c = json.charAt(i);
            if (escaping) {
                out.append(c);
                escaping = false;
                continue;
            }
            if (c == '\\') {
                escaping = true;
                continue;
            }
            if (c == '"') return out.toString();
            out.append(c);
        }
        return null;
    }
}
