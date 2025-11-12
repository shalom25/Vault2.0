package com.example.vault.commands;

import com.example.vault.VaultPlugin;
import com.example.vault.i18n.Messages;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class VaultCommand implements CommandExecutor {
    private final VaultPlugin plugin;
    private final Messages messages;

    public VaultCommand(VaultPlugin plugin, Messages messages) {
        this.plugin = plugin;
        this.messages = messages;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sender.sendMessage(messages.prefix() + "Usage: /vault reload|update");
            return true;
        }
        String sub = args[0].toLowerCase();
        if ("reload".equals(sub)) {
            if (sender instanceof Player && !sender.isOp()) {
                sender.sendMessage(messages.chat("cmd.vault.no_permission"));
                return true;
            }
            plugin.reloadPluginState();
            String lang = plugin.getConfig().getString("language", "en");
            sender.sendMessage(messages.formatChat("plugin.reloaded", java.util.Collections.singletonMap("lang", lang)));
            return true;
        }
        if ("update".equals(sub)) {
            if (sender instanceof Player && !sender.isOp()) {
                sender.sendMessage(messages.chat("cmd.vault.no_permission"));
                return true;
            }
            sender.sendMessage(messages.prefix() + "Checking for updates...");
            plugin.runUpdateCheckAndAnnounce(sender);
            return true;
        }
        sender.sendMessage(messages.prefix() + "Usage: /vault reload|update");
        return true;
    }
}