package com.example.vault.commands;

import com.example.vault.economy.SimpleEconomy;
import com.example.vault.i18n.Messages;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class VaultOpCommand implements CommandExecutor {
    private final Plugin plugin;
    private final Economy economy;
    private final Messages messages;

    public VaultOpCommand(Plugin plugin, Economy economy, Messages messages) {
        this.plugin = plugin;
        this.economy = economy;
        this.messages = messages;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String perm = plugin.getConfig().getString("permissions.vaultop_use", "vault.top");
        if (perm != null) {
            String p = perm.trim();
            if (!(p.isEmpty() || p.equalsIgnoreCase("none") || p.equalsIgnoreCase("disabled"))) {
                if (!sender.hasPermission(p)) {
                    sender.sendMessage(messages.chat("vaultop.no_permission"));
                    return true;
                }
            }
        }

        int page = 1;
        if (args.length >= 1) {
            try {
                page = Integer.parseInt(args[0]);
            } catch (NumberFormatException ignored) {
                page = 1;
            }
        }
        if (page < 1) page = 1;

        if (!(economy instanceof SimpleEconomy)) {
            sender.sendMessage(messages.chat("vaultop.top_unavailable"));
            return true;
        }

        SimpleEconomy se = (SimpleEconomy) economy;
        Map<UUID, Double> balances = se.snapshotBalances(se.getDefaultCurrencyId());
        List<Map.Entry<UUID, Double>> entries = new ArrayList<>(balances.entrySet());
        entries.sort(new Comparator<Map.Entry<UUID, Double>>() {
            @Override
            public int compare(Map.Entry<UUID, Double> a, Map.Entry<UUID, Double> b) {
                return Double.compare(b.getValue(), a.getValue());
            }
        });

        int pageSize = 10;
        int total = entries.size();
        int totalPages = (total + pageSize - 1) / pageSize;
        if (totalPages == 0) totalPages = 1;
        if (page > totalPages) page = totalPages;

        int start = (page - 1) * pageSize;
        int end = Math.min(start + pageSize, total);

        java.util.Map<String, String> headerVars = new java.util.HashMap<>();
        headerVars.put("page", String.valueOf(page));
        headerVars.put("pages", String.valueOf(totalPages));
        sender.sendMessage(messages.formatChat("vaultop.top_header", headerVars));
        if (total == 0) {
            sender.sendMessage(messages.chat("vaultop.no_data"));
            return true;
        }

        for (int i = start; i < end; i++) {
            Map.Entry<UUID, Double> e = entries.get(i);
            int rank = i + 1;
            String name = resolveName(e.getKey());
            String amount = economy.format(e.getValue());
            sender.sendMessage(ChatColor.YELLOW + "" + rank + ". " + ChatColor.AQUA + name + ChatColor.DARK_GRAY + " - " + ChatColor.GOLD + amount);
        }
        return true;
    }

    private String resolveName(UUID uuid) {
        OfflinePlayer p = Bukkit.getOfflinePlayer(uuid);
        String name = p != null ? p.getName() : null;
        if (name == null || name.trim().isEmpty()) {
            String unknown = messages.get("top.unknown_player");
            if (unknown == null || unknown.isEmpty() || "top.unknown_player".equals(unknown)) unknown = "Jugador";
            return unknown;
        }
        return name;
    }
}
