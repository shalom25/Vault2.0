package com.example.vault.commands;

import com.example.vault.util.ColorUtil;
import com.example.vault.util.ChatInputSanitizer;
import com.example.vault.economy.OfflinePayQueueService;
import com.example.vault.economy.SimpleEconomy;
import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.StringUtil;
import com.example.vault.menu.PayMenuService;
import com.example.vault.i18n.Messages;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;


public class PayCommand implements TabExecutor {
    private final Plugin plugin;
    private final Economy economy;
    private final PayMenuService payMenuService;
    private final Messages messages;
    private volatile OfflinePayQueueService offlinePay;

    public PayCommand(Plugin plugin, Economy economy, PayMenuService payMenuService, Messages messages) {
        this.plugin = plugin;
        this.economy = economy;
        this.payMenuService = payMenuService;
        this.messages = messages;
    }

    public void setOfflinePayQueue(OfflinePayQueueService s) { this.offlinePay = s; }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(messages.chat("pay.only_players"));
            return true;
        }
        Player player = (Player) sender;
        String permPay = plugin.getConfig().getString("permissions.pay_use", "vault.pay");
        boolean bypassAll = player.isOp() || player.hasPermission("vault.admin");
        if (permPay != null && !bypassAll) {
            String p = permPay.trim();
            if (!(p.isEmpty() || p.equalsIgnoreCase("none") || p.equalsIgnoreCase("disabled"))) {
                if (!player.hasPermission(p) && !player.hasPermission("vault.use")) {
                    player.sendMessage(messages.chat("pay.no_permission"));
                    return true;
                }
            }
        }
        if (args.length == 0) {
            payMenuService.getChargeRequestService().cancelRequest(player);
            payMenuService.openMainMenu(player);
            return true;
        }
        if (args.length == 1 && ("cancel".equalsIgnoreCase(args[0]) || "cancelar".equalsIgnoreCase(args[0]))) {
            payMenuService.getChargeRequestService().cancelRequest(player);
            String cancelled = messages.getOptional("pay.cancelled");
            if (cancelled == null || cancelled.isEmpty() || "pay.cancelled".equals(cancelled)) {
                cancelled = "&cPay request cancelled.";
            }
            String text = ColorUtil.colorize(cancelled);
            player.sendMessage(messages.prefix().isEmpty() ? text : messages.prefix() + " " + text);
            return true;
        }
        if (args.length == 1) {
            payMenuService.getChargeRequestService().cancelRequest(player);
            Player target = Bukkit.getPlayerExact(args[0]);
            if (target == null || !target.isOnline()) {
                // Prefer "not found" when the name has never joined; otherwise offline.
                OfflinePlayer known = com.example.vault.util.PlayerResolver.resolveByName(plugin, args[0]);
                if (known == null) {
                    player.sendMessage(messages.formatChat("pay.player_not_found", Collections.singletonMap("player", args[0])));
                } else {
                    player.sendMessage(messages.formatChat("pay.player_offline", Collections.singletonMap("player", args[0])));
                }
                return true;
            }
            payMenuService.openPlayerMenu(player, target);
            return true;
        }
        if (args.length >= 2) {
            payMenuService.getChargeRequestService().cancelRequest(player);
            Player targetOnlinePlayer = Bukkit.getPlayerExact(args[0]);
            boolean targetOnline = targetOnlinePlayer != null && targetOnlinePlayer.isOnline();
            OfflinePlayer targetOffline;
            if (targetOnline) {
                targetOffline = targetOnlinePlayer;
            } else {
                // Only known players (online or have played before). Never invent offline UUIDs for pay.
                targetOffline = com.example.vault.util.PlayerResolver.resolveByName(plugin, args[0]);
                if (targetOffline == null) {
                    player.sendMessage(messages.formatChat("pay.player_not_found", Collections.singletonMap("player", args[0])));
                    return true;
                }
                boolean allowOfflinePay = plugin.getConfig().getBoolean("offline-uuid-fallback", true)
                        && economy instanceof SimpleEconomy
                        && offlinePay != null;
                if (!allowOfflinePay) {
                    player.sendMessage(messages.formatChat("pay.player_offline", Collections.singletonMap("player", args[0])));
                    return true;
                }
            }
            double amount;
            Double parsed = ChatInputSanitizer.parsePositiveDouble(args[1]);
            if (parsed == null) {
                player.sendMessage(messages.chat("pay.invalid_amount"));
                return true;
            }
            amount = parsed;
            double min = plugin.getConfig().getDouble("pay_limits.min", 0.0);
            double max = plugin.getConfig().getDouble("pay_limits.max", 0.0);
            boolean bypassMin = bypassAll;
            boolean bypassMax = bypassAll;
            if (min > 0 && amount < min && !bypassMin) {
                player.sendMessage(messages.formatChat("pay.amount_too_small", Collections.singletonMap("min", economy.format(min))));
                return true;
            }
            if (max > 0 && amount > max && !bypassMax) {
                player.sendMessage(messages.formatChat("pay.amount_too_large", Collections.singletonMap("max", economy.format(max))));
                return true;
            }
            String worldName = player.getWorld() != null ? player.getWorld().getName() : null;
            if (economy instanceof SimpleEconomy) {
                economy.createPlayerAccount(player, worldName);
                economy.createPlayerAccount(targetOffline, worldName);
            } else {
                economy.createPlayerAccount(player);
                economy.createPlayerAccount(targetOffline);
            }
            double senderBalance = economy instanceof SimpleEconomy
                    ? economy.getBalance(player, worldName)
                    : economy.getBalance(player);
            if (senderBalance < amount) {
                player.sendMessage(messages.chat("pay.not_enough_money"));
                return true;
            }

            if (targetOnline && targetOnlinePlayer != null) {
                EconomyResponse resp = economy instanceof SimpleEconomy
                        ? economy.withdrawPlayer(player, worldName, amount)
                        : economy.withdrawPlayer(player, amount);
                if (!resp.transactionSuccess()) {
                    player.sendMessage(messages.chat("pay.withdraw_failed"));
                    return true;
                }
                EconomyResponse dep = economy instanceof SimpleEconomy
                        ? economy.depositPlayer(targetOnlinePlayer, worldName, amount)
                        : economy.depositPlayer(targetOnlinePlayer, amount);
                if (!dep.transactionSuccess()) {
                    if (economy instanceof SimpleEconomy) {
                        economy.depositPlayer(player, worldName, amount);
                    } else {
                        economy.depositPlayer(player, amount);
                    }
                    java.util.Map<String, String> err = new java.util.HashMap<>();
                    err.put("error", dep.errorMessage != null ? dep.errorMessage : "deposit failed");
                    player.sendMessage(messages.formatChat("pay.error.deposit_error", err));
                    return true;
                }
                java.util.Map<String, String> placeholders = new java.util.HashMap<>();
                placeholders.put("player", targetOnlinePlayer.getName());
                placeholders.put("amount", economy.format(amount));
                player.sendMessage(messages.formatChat("pay.sent_ok", placeholders));
                java.util.Map<String, String> placeholders2 = new java.util.HashMap<>();
                placeholders2.put("player", player.getName());
                placeholders2.put("amount", economy.format(amount));
                targetOnlinePlayer.sendMessage(messages.formatChat("pay.received_ok", placeholders2));
                return true;
            }

            if (!(economy instanceof SimpleEconomy) || offlinePay == null) {
                player.sendMessage(messages.formatChat("pay.player_offline", Collections.singletonMap("player", args[0])));
                return true;
            }

            SimpleEconomy se = (SimpleEconomy) economy;
            String cid = se.getDefaultCurrencyId();
            EconomyResponse resp = worldName != null
                    ? se.withdrawPlayer(cid, player, worldName, amount)
                    : se.withdrawPlayer(cid, player, amount, com.example.vault.transactions.TxType.OFFLINE_PAY_SENT, "offline pay -> " + targetOffline.getUniqueId());
            if (!resp.transactionSuccess()) {
                player.sendMessage(messages.chat("pay.withdraw_failed"));
                return true;
            }
            String note = args.length >= 3 ? String.join(" ", java.util.Arrays.copyOfRange(args, 2, args.length)) : null;
            OfflinePayQueueService.QueuedPay q = offlinePay.queuePay(cid, player, targetOffline, amount, note);
            String tname = targetOffline.getName() != null ? targetOffline.getName() : args[0];
            java.util.Map<String, String> ctx = new java.util.HashMap<>();
            ctx.put("amount", se.format(cid, amount));
            ctx.put("player", tname);
            ctx.put("id", String.valueOf(q.id));
            player.sendMessage(messages.formatChat("offlinepay.queued_to_player", ctx));
            return true;
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!(sender instanceof Player)) {
            return Collections.emptyList();
        }
        if (args.length == 1) {
            String prefix = args[0] == null ? "" : args[0].toLowerCase(Locale.ROOT);
            List<String> names = new ArrayList<>();
            for (Player online : Bukkit.getOnlinePlayers()) {
                if (online == null || online.getName() == null) continue;
                names.add(online.getName());
            }
            names.add("cancel");
            List<String> out = new ArrayList<>();
            StringUtil.copyPartialMatches(prefix, names, out);
            Collections.sort(out, String.CASE_INSENSITIVE_ORDER);
            return out;
        }
        return Collections.emptyList();
    }

}
