package com.example.vault.commands;

import com.example.vault.economy.SimpleEconomy;
import com.example.vault.economy.TopCacheService;
import com.example.vault.i18n.Messages;
import com.example.vault.transactions.TxRecord;
import com.example.vault.transactions.TxType;
import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class EcoCommand implements CommandExecutor {
    private final Economy economy;
    private final Messages messages;
    private final Plugin plugin;
    private volatile TopCacheService topCache;

    public EcoCommand(Plugin plugin, Economy economy, Messages messages) {
        this.plugin = plugin;
        this.economy = economy;
        this.messages = messages;
    }

    public void setTopCache(TopCacheService cache) { this.topCache = cache; }

    private UUID senderUuid(CommandSender s) {
        return s instanceof Player ? ((Player) s).getUniqueId() : new UUID(0L, 0L);
    }

    private void emitAdminTx(SimpleEconomy se, TxType t, String currencyId, UUID admin, UUID target, double amount, String note) {
        if (se == null) return;
        com.example.vault.transactions.TransactionLogService tx = se.getTransactionLogService();
        if (tx == null) return;
        try {
            TxRecord.Builder b = TxRecord.builder()
                    .txType(t).currencyId(currencyId).amount(amount)
                    .fromUuid(admin).toUuid(target);
            if (note != null && !note.isEmpty()) b.putMeta("note", note);
            tx.record(b);
        } catch (Throwable ignored) {}
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length < 3) {
            sendUsage(sender);
            return true;
        }
        String action = args[0].toLowerCase();
        String playerName = args[1];
        String amountStr = args[2];
        String currencyId = args.length >= 4 ? args[3].toLowerCase() : null;

        boolean isTopCmd = "top".equalsIgnoreCase(action);
        if (isTopCmd) {
            return handleTop(sender, args);
        }

        double amount = 0;
        if (!"reset".equalsIgnoreCase(action)) {
            try {
                amount = Double.parseDouble(amountStr);
            } catch (NumberFormatException ex) {
                sender.sendMessage(messages.formatChat("economy.eco.invalid_amount", Collections.singletonMap("amount", amountStr)));
                return true;
            }
            if (!Double.isFinite(amount) || amount <= 0) {
                sender.sendMessage(messages.chat("economy.eco.positive_only"));
                return true;
            }
        }

        OfflinePlayer target = com.example.vault.util.PlayerResolver.resolveByNameWithOfflineFallback(plugin, playerName);
        if (target == null) {
            sender.sendMessage(messages.formatChat("economy.eco.player_not_found", Collections.singletonMap("player", playerName)));
            return true;
        }
        String worldName = sender instanceof Player player && player.getWorld() != null
                ? player.getWorld().getName() : null;

        SimpleEconomy se = economy instanceof SimpleEconomy ? (SimpleEconomy) economy : null;
        String cid = se != null && currencyId != null ? currencyId :
                se != null ? se.getDefaultCurrencyId() : "default";
        if (se != null) {
            se.createPlayerAccount(cid, target, worldName);
        } else {
            economy.createPlayerAccount(target);
        }

        UUID admin = senderUuid(sender);
        switch (action) {
            case "give":
            case "add": {
                if (!checkPerm(sender, "vault.eco.give")) return true;
                EconomyResponse res;
                if (se != null) {
                    res = worldName != null
                            ? se.depositPlayer(cid, target, worldName, amount)
                            : se.depositPlayer(cid, target, amount, TxType.ADMIN_ADD, "admin add by " + sender.getName());
                } else {
                    res = worldName != null ? economy.depositPlayer(target, worldName, amount) : economy.depositPlayer(target, amount);
                }
                if (res.type == EconomyResponse.ResponseType.SUCCESS) {
                    emitAdminTx(se, TxType.ADMIN_ADD, cid, admin, target.getUniqueId(), amount, "from " + sender.getName());
                    Map<String, String> m = new HashMap<>();
                    m.put("player", target.getName() != null ? target.getName() : playerName);
                    m.put("amount", se != null ? se.format(cid, amount) : economy.format(amount));
                    sender.sendMessage(messages.formatChat("economy.eco.deposit_ok", m));
                    if (topCache != null) topCache.invalidate(cid);
                } else {
                    sender.sendMessage(messages.formatChat("economy.eco.deposit_error", Collections.singletonMap("error", res.errorMessage == null ? "unknown" : res.errorMessage)));
                }
                return true;
            }
            case "take":
            case "remove": {
                if (!checkPerm(sender, "vault.eco.take")) return true;
                EconomyResponse res;
                if (se != null) {
                    res = worldName != null
                            ? se.withdrawPlayer(cid, target, worldName, amount)
                            : se.withdrawPlayer(cid, target, amount, TxType.ADMIN_REMOVE, "admin remove by " + sender.getName());
                } else {
                    res = worldName != null ? economy.withdrawPlayer(target, worldName, amount) : economy.withdrawPlayer(target, amount);
                }
                if (res.type == EconomyResponse.ResponseType.SUCCESS) {
                    emitAdminTx(se, TxType.ADMIN_REMOVE, cid, admin, target.getUniqueId(), amount, "by " + sender.getName());
                    Map<String, String> m = new HashMap<>();
                    m.put("player", target.getName() != null ? target.getName() : playerName);
                    m.put("amount", se != null ? se.format(cid, amount) : economy.format(amount));
                    sender.sendMessage(messages.formatChat("economy.eco.withdraw_ok", m));
                    if (topCache != null) topCache.invalidate(cid);
                } else {
                    sender.sendMessage(messages.formatChat("economy.eco.withdraw_error", Collections.singletonMap("error", res.errorMessage == null ? "unknown" : res.errorMessage)));
                }
                return true;
            }
            case "set": {
                if (!checkPerm(sender, "vault.eco.set")) return true;
                if (se == null) {
                    sender.sendMessage(messages.chat("economy.eco.top_unavailable"));
                    return true;
                }
                se.setBalance(cid, target, amount);
                emitAdminTx(se, TxType.ADMIN_SET, cid, admin, target.getUniqueId(), amount, "set by " + sender.getName());
                Map<String, String> m = new HashMap<>();
                m.put("player", target.getName() != null ? target.getName() : playerName);
                m.put("amount", se.format(cid, amount));
                sender.sendMessage(messages.formatChat("eco.set.success", m));
                if (topCache != null) topCache.invalidate(cid);
                return true;
            }
            case "reset": {
                if (!checkPerm(sender, "vault.eco.reset")) return true;
                if (se == null) {
                    sender.sendMessage(messages.chat("economy.eco.top_unavailable"));
                    return true;
                }
                double current = se.getBalance(cid, target);
                se.setBalance(cid, target, 0.0);
                emitAdminTx(se, TxType.ADMIN_RESET, cid, admin, target.getUniqueId(), current, "reset by " + sender.getName());
                Map<String, String> m = new HashMap<>();
                m.put("player", target.getName() != null ? target.getName() : playerName);
                m.put("old_amount", se.format(cid, current));
                m.put("amount", se.format(cid, 0.0));
                sender.sendMessage(messages.formatChat("eco.reset.success", m));
                if (topCache != null) topCache.invalidate(cid);
                return true;
            }
            default:
                sendUsage(sender);
                return true;
        }
    }

    private boolean checkPerm(CommandSender s, String node) {
        if (s instanceof Player && !s.hasPermission("vault.eco") && !s.hasPermission(node)) {
            s.sendMessage(messages.chat("economy.eco.no_permission"));
            return false;
        }
        return true;
    }

    private void sendUsage(CommandSender s) {
        String raw = messages.getOptional("eco.usage");
        if (raw != null && !raw.isEmpty()) {
            String[] lines = raw.split("\\r?\\n");
            String p = messages.prefix();
            for (String line : lines) {
                if (line == null) continue;
                String colored = messages.colorize(line);
                s.sendMessage(p.isEmpty() ? colored : p + " " + colored);
            }
            return;
        }
        String p = messages.prefix();
        s.sendMessage(p + messages.color("cmd.common.usage_prefix"));
        s.sendMessage(p + ChatColor.GRAY + "  /eco give   <player> <amount> [currency]");
        s.sendMessage(p + ChatColor.GRAY + "  /eco take   <player> <amount> [currency]");
        s.sendMessage(p + ChatColor.GRAY + "  /eco set    <player> <amount> [currency]");
        s.sendMessage(p + ChatColor.GRAY + "  /eco reset  <player> [currency]");
        s.sendMessage(p + ChatColor.GRAY + "  /eco top    [page] [currency]");
    }

    private boolean handleTop(CommandSender sender, String[] args) {
        if (!(sender instanceof Player) && !sender.isOp()) {
            sender.sendMessage(messages.chat("vaultop.no_permission"));
            return true;
        }
        if (sender instanceof Player && !sender.hasPermission("vault.top")) {
            sender.sendMessage(messages.chat("vaultop.no_permission"));
            return true;
        }
        String cid = economy instanceof SimpleEconomy ? ((SimpleEconomy) economy).getDefaultCurrencyId() : "default";
        int page = 1;
        if (args.length >= 2) {
            try { page = Integer.parseInt(args[1]); }
            catch (NumberFormatException ignored) {
                if (economy instanceof SimpleEconomy && ((SimpleEconomy) economy).getCurrencyIds().contains(args[1].toLowerCase())) {
                    cid = args[1].toLowerCase();
                }
            }
        }
        if (args.length >= 3) {
            if (economy instanceof SimpleEconomy && ((SimpleEconomy) economy).getCurrencyIds().contains(args[2].toLowerCase())) {
                cid = args[2].toLowerCase();
            }
        }
        if (page < 1) page = 1;

        List<TopCacheService.TopEntry> entries;
        if (topCache != null) {
            entries = topCache.getTop(cid, 10000);
        } else if (economy instanceof SimpleEconomy) {
            Map<UUID, Double> balances = ((SimpleEconomy) economy).snapshotBalances(cid);
            List<Map.Entry<UUID, Double>> raw = new ArrayList<>(balances.entrySet());
            raw.sort(Comparator.<Map.Entry<UUID, Double>, Double>comparing(Map.Entry::getValue).reversed());
            entries = new ArrayList<>();
            int rank = 1;
            for (Map.Entry<UUID, Double> e : raw) {
                UUID u = e.getKey();
                OfflinePlayer p = Bukkit.getOfflinePlayer(u);
                String n = p != null ? p.getName() : null;
                if (n == null || n.trim().isEmpty()) n = u.toString().substring(0, 8);
                entries.add(new TopCacheService.TopEntry(rank, u, n, e.getValue()));
                rank++;
            }
        } else {
            sender.sendMessage(messages.chat("vaultop.top_unavailable"));
            return true;
        }
        int pageSize = 10;
        int total = entries.size();
        int totalPages = Math.max(1, (total + pageSize - 1) / pageSize);
        if (page > totalPages) page = totalPages;
        int start = (page - 1) * pageSize;
        int end = Math.min(start + pageSize, total);

        Map<String, String> header = new HashMap<>();
        header.put("page", String.valueOf(page));
        header.put("pages", String.valueOf(totalPages));
        sender.sendMessage(messages.formatChat("vaultop.top_header", header));
        if (total == 0) {
            sender.sendMessage(messages.chat("vaultop.no_data"));
            return true;
        }
        SimpleEconomy se = economy instanceof SimpleEconomy ? (SimpleEconomy) economy : null;
        for (int i = start; i < end; i++) {
            TopCacheService.TopEntry e = entries.get(i);
            String amount = se != null ? se.format(cid, e.balance) : economy.format(e.balance);
            sender.sendMessage(ChatColor.YELLOW + "" + e.rank + ". " + ChatColor.AQUA + e.name + ChatColor.DARK_GRAY + " - " + ChatColor.GOLD + amount);
        }
        return true;
    }
}
