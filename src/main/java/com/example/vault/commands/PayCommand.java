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
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import com.example.vault.menu.PayMenuService;
import com.example.vault.i18n.Messages;


public class PayCommand implements CommandExecutor {
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
        if (permPay != null) {
            String p = permPay.trim();
            if (!(p.isEmpty() || p.equalsIgnoreCase("none") || p.equalsIgnoreCase("disabled"))) {
                if (!player.hasPermission(p)) {
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
                player.sendMessage(messages.formatChat("pay.player_offline", java.util.Collections.singletonMap("player", args[0])));
                return true;
            }
            payMenuService.openPlayerMenu(player, target);
            return true;
        }
        if (args.length >= 2) {
            payMenuService.getChargeRequestService().cancelRequest(player);
            boolean targetOnline;
            Player targetOnlinePlayer = Bukkit.getPlayerExact(args[0]);
            OfflinePlayer targetOffline = null;
            if (targetOnlinePlayer != null && targetOnlinePlayer.isOnline()) {
                targetOnline = true;
                targetOffline = targetOnlinePlayer;
            } else {
                targetOnline = false;
                boolean allow = plugin.getConfig().getBoolean("offline-uuid-fallback", true);
                if (!allow) {
                    player.sendMessage(messages.formatChat("pay.player_offline", java.util.Collections.singletonMap("player", args[0])));
                    return true;
                }
                targetOffline = com.example.vault.util.PlayerResolver.resolveByNameWithOfflineFallback(plugin, args[0]);
                if (targetOffline == null) {
                    player.sendMessage(messages.formatChat("pay.player_offline", java.util.Collections.singletonMap("player", args[0])));
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
            String permBypassMin = plugin.getConfig().getString("permissions.pay_bypass_min", "vault.pay.bypass_min");
            String permBypassMax = plugin.getConfig().getString("permissions.pay_bypass_max", "vault.pay.bypass_max");
            double min = plugin.getConfig().getDouble("pay_limits.min", 0.0);
            double max = plugin.getConfig().getDouble("pay_limits.max", 0.0);
            if (min > 0 && amount < min && !player.hasPermission(permBypassMin)) {
                player.sendMessage(messages.formatChat("pay.amount_too_small", java.util.Collections.singletonMap("min", economy.format(min))));
                return true;
            }
            if (max > 0 && amount > max && !player.hasPermission(permBypassMax)) {
                player.sendMessage(messages.formatChat("pay.amount_too_large", java.util.Collections.singletonMap("max", economy.format(max))));
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
                if (economy instanceof SimpleEconomy) {
                    economy.depositPlayer(targetOnlinePlayer, worldName, amount);
                } else {
                    economy.depositPlayer(targetOnlinePlayer, amount);
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
                player.sendMessage(messages.formatChat("pay.player_offline", java.util.Collections.singletonMap("player", args[0])));
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
            player.sendMessage(messages.prefixed(ColorUtil.colorize("&aPago de " + se.format(cid, amount) + " en cola para " + tname +
                    " (ID #" + q.id + "). Se entregará cuando entre.")));
            return true;
        }
        return true;
    }

}
