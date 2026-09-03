package com.example.vault.economy;

import com.example.vault.transactions.TxRecord;
import com.example.vault.transactions.TxType;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public final class BankService {
    private final Plugin plugin;
    private final SimpleEconomy economy;
    private final File file;
    private final ConcurrentMap<UUID, Double> bankBalances = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Double> teamBankBalances = new ConcurrentHashMap<>();
    private volatile org.bukkit.scheduler.BukkitTask interestTask;
    private volatile org.bukkit.scheduler.BukkitTask taxTask;

    public BankService(Plugin plugin, SimpleEconomy economy) {
        this.plugin = plugin;
        this.economy = economy;
        this.file = new File(plugin.getDataFolder(), "bank_balances.yml");
        load();
    }

    public void load() {
        bankBalances.clear();
        teamBankBalances.clear();
        if (!file.exists()) return;
        try {
            YamlConfiguration cfg = YamlConfiguration.loadConfiguration(file);
            ConfigurationSection sec = cfg.getConfigurationSection("balances");
            if (sec != null) {
                for (String k : sec.getKeys(false)) {
                    try {
                        UUID u = UUID.fromString(k);
                        bankBalances.put(u, sanitize(sec.getDouble(k, 0.0)));
                    } catch (IllegalArgumentException ignored) {}
                }
            }
            ConfigurationSection teams = cfg.getConfigurationSection("teams");
            if (teams != null) {
                for (String tid : teams.getKeys(false)) {
                    try {
                        teamBankBalances.put(tid, sanitize(teams.getDouble(tid, 0.0)));
                    } catch (Exception ignored) {}
                }
            }
        } catch (Throwable t) {
            plugin.getLogger().warning("Failed to load bank_balances.yml: " + t.getMessage());
        }
    }

    public synchronized void save() {
        YamlConfiguration cfg = new YamlConfiguration();
        for (Map.Entry<UUID, Double> e : bankBalances.entrySet()) {
            cfg.set("balances." + e.getKey(), e.getValue());
        }
        for (Map.Entry<String, Double> e : teamBankBalances.entrySet()) {
            cfg.set("teams." + e.getKey(), e.getValue());
        }
        try { cfg.save(file); } catch (IOException ex) {
            plugin.getLogger().warning("Failed to save bank_balances.yml: " + ex.getMessage());
        }
    }

    private double sanitize(double v) { return Double.isFinite(v) ? v : 0.0; }

    public double getBankBalance(UUID uuid) {
        Double v = bankBalances.get(uuid);
        return v == null ? 0.0 : sanitize(v);
    }

    public boolean depositBank(UUID uuid, double amount) {
        String cid = economy.getDefaultCurrencyId();
        OfflinePlayer p = Bukkit.getOfflinePlayer(uuid);
        if (!Double.isFinite(amount) || amount <= 0) return false;
        EconomyResponse r = economy.withdrawPlayer(cid, p, amount, TxType.BANK_DEPOSIT, "bank deposit");
        if (!r.transactionSuccess()) return false;
        bankBalances.merge(uuid, amount, (a, b) -> sanitize(a + b));
        save();
        return true;
    }

    public boolean withdrawBank(UUID uuid, double amount) {
        String cid = economy.getDefaultCurrencyId();
        OfflinePlayer p = Bukkit.getOfflinePlayer(uuid);
        if (!Double.isFinite(amount) || amount <= 0) return false;
        double bal = getBankBalance(uuid);
        if (bal < amount) return false;
        EconomyResponse r = economy.depositPlayer(cid, p, amount, TxType.BANK_WITHDRAW, "bank withdraw");
        if (!r.transactionSuccess()) return false;
        bankBalances.merge(uuid, -amount, (a, b) -> sanitize(a + b));
        save();
        return true;
    }

    public boolean isInterestEnabled() {
        return plugin.getConfig().getBoolean("bank.interest.enabled", true);
    }

    public boolean isTaxEnabled() {
        return plugin.getConfig().getBoolean("bank.tax.enabled", false);
    }

    public void start() {
        stop();
        boolean interestEnabled = isInterestEnabled();
        long interestMinutes = Math.max(1L, plugin.getConfig().getLong("bank.interest.every_minutes", 60L));
        double interestPct = plugin.getConfig().getDouble("bank.interest.percent_per_period", 0.5);
        if (interestEnabled && interestPct > 0 && interestMinutes > 0) {
            long ticks = interestMinutes * 60L * 20L;
            interestTask = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, () -> applyInterest(interestPct), ticks, ticks);
        }
        boolean taxEnabled = isTaxEnabled();
        long taxMinutes = Math.max(1L, plugin.getConfig().getLong("bank.tax.every_minutes", 180L));
        double taxPct = plugin.getConfig().getDouble("bank.tax.percent_per_period", 0.0);
        double taxThreshold = plugin.getConfig().getDouble("bank.tax.threshold", 1000000.0);
        if (taxEnabled && taxPct > 0 && taxMinutes > 0) {
            long ticks = taxMinutes * 60L * 20L;
            taxTask = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, () -> applyTax(taxPct, taxThreshold), ticks, ticks);
        }
    }

    public void stop() {
        if (interestTask != null) {
            try { interestTask.cancel(); } catch (Throwable ignored) {}
            interestTask = null;
        }
        if (taxTask != null) {
            try { taxTask.cancel(); } catch (Throwable ignored) {}
            taxTask = null;
        }
    }

    private void applyInterest(double percentPerPeriod) {
        if (percentPerPeriod <= 0) return;
        double rate = percentPerPeriod / 100.0;
        String cid = economy.getDefaultCurrencyId();
        Map<UUID, Double> toCredit = new HashMap<>();
        for (Map.Entry<UUID, Double> e : bankBalances.entrySet()) {
            double bal = sanitize(e.getValue());
            if (bal <= 0) continue;
            double interest = sanitize(bal * rate);
            if (interest <= 0) continue;
            toCredit.put(e.getKey(), interest);
        }
        if (toCredit.isEmpty()) { save(); return; }
        for (Map.Entry<UUID, Double> e : toCredit.entrySet()) {
            bankBalances.merge(e.getKey(), e.getValue(), (a, b) -> sanitize(a + b));
            com.example.vault.transactions.TransactionLogService tx = economy.getTransactionLogService();
            if (tx != null) {
                try {
                    TxRecord.Builder b = TxRecord.builder()
                            .txType(TxType.INTEREST).currencyId(cid).amount(e.getValue())
                            .toUuid(e.getKey()).putMeta("bank_balance_after",
                                    String.valueOf(getBankBalance(e.getKey())));
                    tx.record(b);
                } catch (Throwable ignored) {}
            }
        }
        save();
    }

    private void applyTax(double percentPerPeriod, double threshold) {
        if (percentPerPeriod <= 0) return;
        double rate = percentPerPeriod / 100.0;
        String cid = economy.getDefaultCurrencyId();
        Map<UUID, Double> toTax = new LinkedHashMap<>();
        for (Map.Entry<UUID, Double> e : bankBalances.entrySet()) {
            double bal = sanitize(e.getValue());
            if (bal <= threshold) continue;
            double taxable = bal - threshold;
            double tax = sanitize(taxable * rate);
            if (tax <= 0) continue;
            toTax.put(e.getKey(), tax);
        }
        if (toTax.isEmpty()) { save(); return; }
        for (Map.Entry<UUID, Double> e : toTax.entrySet()) {
            bankBalances.merge(e.getKey(), -e.getValue(), (a, b) -> sanitize(Math.max(0.0, a + b)));
            com.example.vault.transactions.TransactionLogService tx = economy.getTransactionLogService();
            if (tx != null) {
                try {
                    TxRecord.Builder b = TxRecord.builder()
                            .txType(TxType.TAX).currencyId(cid).amount(e.getValue())
                            .fromUuid(e.getKey()).putMeta("bank_balance_after",
                                    String.valueOf(getBankBalance(e.getKey())));
                    tx.record(b);
                } catch (Throwable ignored) {}
            }
        }
        save();
    }
}
