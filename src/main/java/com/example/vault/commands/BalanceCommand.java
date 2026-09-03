package com.example.vault.commands;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import com.example.vault.economy.SimpleEconomy;
import com.example.vault.i18n.Messages;


public class BalanceCommand implements CommandExecutor {
    private final Plugin plugin;
    private final Economy economy;
    private final Messages messages;

    public BalanceCommand(Plugin plugin, Economy economy, Messages messages) {
        this.plugin = plugin;
        this.economy = economy;
        this.messages = messages;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(messages.chat("balance.only_players"));
            return true;
        }
        Player player = (Player) sender;
        String permBalance = plugin.getConfig().getString("permissions.balance_use", "vault.balance");
        if (permBalance != null) {
            String p = permBalance.trim();
            if (!(p.isEmpty() || p.equalsIgnoreCase("none") || p.equalsIgnoreCase("disabled"))) {
                if (!player.hasPermission(p)) {
                    player.sendMessage(messages.chat("balance.no_permission"));
                    return true;
                }
            }
        }
        String worldName = player.getWorld() != null ? player.getWorld().getName() : null;
        if (economy instanceof SimpleEconomy) {
            economy.createPlayerAccount(player, worldName);
        } else {
            economy.createPlayerAccount(player);
        }
        double bal = economy instanceof SimpleEconomy
                ? economy.getBalance(player, worldName)
                : economy.getBalance(player);
        player.sendMessage(messages.formatChat("balance.your_balance", java.util.Collections.singletonMap("amount", economy.format(bal))));
        return true;
    }
}
