package net.milkbowl.vault;

import com.example.vault.VaultPlugin;
import org.bukkit.Bukkit;
import org.bukkit.plugin.ServicesManager;
import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.permission.Permission;
import net.milkbowl.vault.chat.Chat;

public class Vault extends VaultPlugin {

    public static Economy getEconomy() {
        ServicesManager sm = Bukkit.getServer().getServicesManager();
        var reg = sm.getRegistration(Economy.class);
        return reg != null ? reg.getProvider() : null;
    }

    public static Permission getPermission() {
        ServicesManager sm = Bukkit.getServer().getServicesManager();
        var reg = sm.getRegistration(Permission.class);
        return reg != null ? reg.getProvider() : null;
    }

    public static Chat getChat() {
        ServicesManager sm = Bukkit.getServer().getServicesManager();
        var reg = sm.getRegistration(Chat.class);
        return reg != null ? reg.getProvider() : null;
    }
}
