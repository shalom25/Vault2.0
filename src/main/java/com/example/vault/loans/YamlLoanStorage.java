package com.example.vault.loans;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class YamlLoanStorage implements LoanStorage {
    private final File file;

    public YamlLoanStorage(Plugin plugin) {
        this.file = new File(plugin.getDataFolder(), "loans.yml");
    }

    @Override
    public Map<UUID, Loan> loadAll() throws IOException {
        if (!file.exists()) {
            File parent = file.getParentFile();
            if (parent != null && !parent.exists()) parent.mkdirs();
            file.createNewFile();
            return new HashMap<>();
        }
        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection root = cfg.getConfigurationSection("loans");
        if (root == null) return new HashMap<>();
        Map<UUID, Loan> out = new HashMap<>();
        for (String key : root.getKeys(false)) {
            try {
                UUID uuid = UUID.fromString(key);
                ConfigurationSection s = root.getConfigurationSection(key);
                if (s == null) continue;
                Loan loan = new Loan(uuid);
                loan.setPrincipal(s.getDouble("principal", 0.0));
                loan.setRemaining(s.getDouble("remaining", 0.0));
                loan.setInstallmentAmount(s.getDouble("installment_amount", 0.0));
                loan.setIntervalMs(s.getLong("interval_ms", 0L));
                loan.setNextChargeAtMs(s.getLong("next_charge_at_ms", 0L));
                loan.setInstallmentsLeft(s.getInt("installments_left", 0));
                loan.setMissedPayments(s.getInt("missed_payments", 0));
                loan.setCreatedAtMs(s.getLong("created_at_ms", System.currentTimeMillis()));
                String statusRaw = s.getString("status", "ACTIVE");
                try {
                    loan.setStatus(LoanStatus.valueOf(statusRaw == null ? "ACTIVE" : statusRaw));
                } catch (IllegalArgumentException ignored) {
                    loan.setStatus(LoanStatus.ACTIVE);
                }
                out.put(uuid, loan);
            } catch (IllegalArgumentException ignored) {
            }
        }
        return out;
    }

    @Override
    public void saveAll(Map<UUID, Loan> loans) throws IOException {
        YamlConfiguration cfg = new YamlConfiguration();
        ConfigurationSection root = cfg.createSection("loans");
        if (loans != null) {
            for (Map.Entry<UUID, Loan> e : loans.entrySet()) {
                UUID uuid = e.getKey();
                Loan loan = e.getValue();
                if (uuid == null || loan == null) continue;
                ConfigurationSection s = root.createSection(uuid.toString());
                s.set("principal", loan.getPrincipal());
                s.set("remaining", loan.getRemaining());
                s.set("installment_amount", loan.getInstallmentAmount());
                s.set("interval_ms", loan.getIntervalMs());
                s.set("next_charge_at_ms", loan.getNextChargeAtMs());
                s.set("installments_left", loan.getInstallmentsLeft());
                s.set("missed_payments", loan.getMissedPayments());
                s.set("created_at_ms", loan.getCreatedAtMs());
                s.set("status", loan.getStatus().name());
            }
        }
        cfg.save(file);
    }
}

