package com.example.vault.util;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.plugin.PluginDescriptionFile;
import org.bukkit.plugin.java.JavaPlugin;

public final class LogoPrinter {
    private LogoPrinter() {}

    public static void printEnable(JavaPlugin plugin) {
        printBanner(plugin, true);
    }

    public static void printDisable(JavaPlugin plugin) {
        printBanner(plugin, false);
    }

    private static void printBanner(JavaPlugin plugin, boolean enabled) {
        PluginDescriptionFile desc = plugin.getDescription();
        String version = desc.getVersion();

        String line = ChatColor.GOLD + "==============================";
        String status = enabled ? (ChatColor.GREEN + "ENABLED") : (ChatColor.RED + "DISABLED");

        send(line);
        send(ChatColor.YELLOW + "Vault2.0");
        send(ChatColor.GRAY + "Version: " + ChatColor.WHITE + version);
        send(ChatColor.GRAY + "Status: " + status);
        send(line);
    }

    private static void send(String msg) {
        Bukkit.getConsoleSender().sendMessage(msg);
    }
}
