package com.example.vault.placeholder;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.OfflinePlayer;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;
import org.bukkit.plugin.Plugin;
import com.example.vault.i18n.Messages;
import com.example.vault.util.ColorUtil;
import com.example.vault.util.PlayerResolver;
import com.example.vault.economy.SimpleEconomy;
import org.bukkit.Bukkit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class Vault2PlaceholderExpansion extends PlaceholderExpansion {
    private final Plugin plugin;
    private final Economy economy;
    private final Messages messages;

    public Vault2PlaceholderExpansion(Plugin plugin, Economy economy, Messages messages) {
        this.plugin = plugin;
        this.economy = economy;
        this.messages = messages;
    }

    private String formatWithCommas(double value) {
        DecimalFormatSymbols sym = DecimalFormatSymbols.getInstance(Locale.US);
        sym.setGroupingSeparator(',');
        sym.setDecimalSeparator('.');
        DecimalFormat df = new DecimalFormat("#,###.##", sym);
        df.setGroupingUsed(true);
        return df.format(value);
    }

    @Override
    public String getIdentifier() {
        return "vault2";
    }

    @Override
    public String getAuthor() {
        return String.join(", ", plugin.getDescription().getAuthors());
    }

    @Override
    public String getVersion() {
        return plugin.getDescription().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public boolean canRegister() {
        return true;
    }

    @Override
    public String onRequest(OfflinePlayer player, String params) {
        if (params == null) return "";
        String key = params.toLowerCase();
        if (key.startsWith("top_")) {
            return handleTopParam(key);
        }
        if ("top".equals(key)) {
            return buildTop(10);
        }
        if ("currency_symbol".equals(key)) {
            String raw = plugin.getConfig().getString("currency.symbol", "");
            if (raw == null) raw = "";
            return ColorUtil.colorize(raw);
        }
        if (player == null) return "";
        switch (key) {
            case "balance": {
                ensureAccountForRequest(player, player);
                double bal = getBalanceForRequest(player, player);
                return String.valueOf(bal);
            }
            case "balance_formatted": {
                ensureAccountForRequest(player, player);
                double bal = getBalanceForRequest(player, player);
                return economy.format(bal);
            }
            case "eco_balance": {
                ensureAccountForRequest(player, player);
                double bal = getBalanceForRequest(player, player);
                return String.valueOf(bal);
            }
            case "eco_balance_formatted": {
                ensureAccountForRequest(player, player);
                double bal = getBalanceForRequest(player, player);
                return economy.format(bal);
            }
            case "eco_balance_fixed": {
                ensureAccountForRequest(player, player);
                double bal = getBalanceForRequest(player, player);
                return String.format(Locale.ROOT, "%.2f", bal);
            }
            case "eco_balance_commas": {
                ensureAccountForRequest(player, player);
                double bal = getBalanceForRequest(player, player);
                return formatWithCommas(bal);
            }
            case "eco_balance_short": {
                ensureAccountForRequest(player, player);
                double bal = getBalanceForRequest(player, player);
                if (economy instanceof com.example.vault.economy.SimpleEconomy) {
                    com.example.vault.economy.SimpleEconomy se = (com.example.vault.economy.SimpleEconomy) economy;
                    return se.formatShort(se.getDefaultCurrencyId(), bal);
                }
                return economy.format(bal);
            }
            default:
                if (key.startsWith("balance_formatted_")) {
                    String name = key.substring("balance_formatted_".length());
                    OfflinePlayer other = PlayerResolver.resolveByNameWithOfflineFallback(plugin, name);
                    if (other == null) return "";
                    ensureAccountForRequest(other, player);
                    return economy.format(getBalanceForRequest(other, player));
                }
                if (key.startsWith("balance_")) {
                    String name = key.substring("balance_".length());
                    OfflinePlayer other = PlayerResolver.resolveByNameWithOfflineFallback(plugin, name);
                    if (other == null) return "";
                    ensureAccountForRequest(other, player);
                    return String.valueOf(getBalanceForRequest(other, player));
                }
                if (key.startsWith("ecobalance") && key.endsWith("dp")) {
                    String middle = key.substring("ecobalance".length(), key.length() - 2);
                    try {
                        int dp = Integer.parseInt(middle);
                        if (dp < 0) dp = 0;
                        if (dp > 8) dp = 8;
                        ensureAccountForRequest(player, player);
                        double bal = getBalanceForRequest(player, player);
                        return String.format(Locale.ROOT, "%" + "." + dp + "f", bal);
                    } catch (NumberFormatException ignored) {
                        return "";
                    }
                }
                return "";
        }
    }

    private String handleTopParam(String key) {
        String rest = key.substring("top_".length());
        if (rest.isEmpty()) return "";
        if (rest.startsWith("name_")) {
            Integer rank = tryParsePositiveInt(rest.substring("name_".length()));
            if (rank == null) return "";
            return getTopName(rank);
        }
        if (rest.startsWith("amount_")) {
            Integer rank = tryParsePositiveInt(rest.substring("amount_".length()));
            if (rank == null) return "";
            return getTopAmount(rank);
        }
        if (rest.startsWith("uuid_")) {
            Integer rank = tryParsePositiveInt(rest.substring("uuid_".length()));
            if (rank == null) return "";
            return getTopUuid(rank);
        }
        Integer rank = tryParsePositiveInt(rest);
        if (rank == null) return "";
        return getTopLine(rank);
    }

    private Integer tryParsePositiveInt(String s) {
        try {
            int n = Integer.parseInt(s);
            if (n <= 0) return null;
            return n;
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private String getTopLine(int rank) {
        List<Map.Entry<UUID, Double>> entries = sortedBalanceEntries();
        if (entries.isEmpty() || rank > entries.size()) return "";
        Map.Entry<UUID, Double> e = entries.get(rank - 1);
        String name = resolveName(e.getKey());
        return rank + ". " + name + ": " + economy.format(e.getValue());
    }

    private String getTopName(int rank) {
        List<Map.Entry<UUID, Double>> entries = sortedBalanceEntries();
        if (entries.isEmpty() || rank > entries.size()) return "";
        return resolveName(entries.get(rank - 1).getKey());
    }

    private String getTopAmount(int rank) {
        List<Map.Entry<UUID, Double>> entries = sortedBalanceEntries();
        if (entries.isEmpty() || rank > entries.size()) return "";
        return economy.format(entries.get(rank - 1).getValue());
    }

    private String getTopUuid(int rank) {
        List<Map.Entry<UUID, Double>> entries = sortedBalanceEntries();
        if (entries.isEmpty() || rank > entries.size()) return "";
        return entries.get(rank - 1).getKey().toString();
    }

    private String buildTop(int limit) {
        List<Map.Entry<UUID, Double>> entries = sortedBalanceEntries();
        if (entries.isEmpty()) return "";
        int n = Math.min(limit, entries.size());
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < n; i++) {
            Map.Entry<UUID, Double> e = entries.get(i);
            String name = resolveName(e.getKey());
            if (i > 0) sb.append("\n");
            sb.append(i + 1).append(". ").append(name).append(": ").append(economy.format(e.getValue()));
        }
        return sb.toString();
    }

    private List<Map.Entry<UUID, Double>> sortedBalanceEntries() {
        if (!(economy instanceof SimpleEconomy)) return java.util.Collections.emptyList();
        SimpleEconomy se = (SimpleEconomy) economy;
        Map<UUID, Double> balances = se.snapshotBalances(se.getDefaultCurrencyId());
        if (balances.isEmpty()) return java.util.Collections.emptyList();
        List<Map.Entry<UUID, Double>> entries = new ArrayList<>(balances.entrySet());
        entries.sort(new Comparator<Map.Entry<UUID, Double>>() {
            @Override
            public int compare(Map.Entry<UUID, Double> a, Map.Entry<UUID, Double> b) {
                return Double.compare(b.getValue(), a.getValue());
            }
        });
        return entries;
    }

    private String resolveName(UUID uuid) {
        String name = Bukkit.getOfflinePlayer(uuid).getName();
        if (name == null || name.trim().isEmpty()) {
            String unknown = messages != null ? messages.get("top.unknown_player") : null;
            if (unknown == null || unknown.isEmpty() || "top.unknown_player".equals(unknown)) unknown = "Player";
            return unknown;
        }
        return name;
    }

    private void ensureAccountForRequest(OfflinePlayer subject, OfflinePlayer requester) {
        if (economy instanceof SimpleEconomy) {
            economy.createPlayerAccount(subject, requestWorldName(requester));
            return;
        }
        economy.createPlayerAccount(subject);
    }

    private double getBalanceForRequest(OfflinePlayer subject, OfflinePlayer requester) {
        if (economy instanceof SimpleEconomy) {
            return economy.getBalance(subject, requestWorldName(requester));
        }
        return economy.getBalance(subject);
    }

    private String requestWorldName(OfflinePlayer requester) {
        if (requester == null || !requester.isOnline() || requester.getPlayer() == null || requester.getPlayer().getWorld() == null) {
            return null;
        }
        return requester.getPlayer().getWorld().getName();
    }
}
