package com.example.vault.transactions;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.locks.ReentrantLock;

public class FileTxDAO implements TransactionLogDAO {
    private final File file;
    private final ReentrantLock lock = new ReentrantLock();

    public FileTxDAO(Plugin plugin) {
        Objects.requireNonNull(plugin, "plugin");
        File dir = new File(plugin.getDataFolder(), "transactions");
        if (!dir.exists()) dir.mkdirs();
        this.file = new File(dir, "transactions.db.yml");
        if (!file.exists()) {
            try {
                file.createNewFile();
                YamlConfiguration cfg = YamlConfiguration.loadConfiguration(file);
                cfg.set("version", 1);
                cfg.set("next_index", 0);
                cfg.set("txs", new ArrayList<String>());
                cfg.save(file);
            } catch (IOException ex) {
                throw new RuntimeException("Failed to initialize transactions.db.yml", ex);
            }
        }
    }

    private TxRecord fromLine(String line) {
        int s1 = line.indexOf('|');
        if (s1 < 0) return null;
        int s2 = line.indexOf('|', s1 + 1);
        if (s2 < 0) return null;
        int s3 = line.indexOf('|', s2 + 1);
        if (s3 < 0) return null;
        int s4 = line.indexOf('|', s3 + 1);
        if (s4 < 0) return null;
        int s5 = line.indexOf('|', s4 + 1);
        if (s5 < 0) return null;
        int s6 = line.indexOf('|', s5 + 1);
        if (s6 < 0) return null;
        int s7 = line.indexOf('|', s6 + 1);
        if (s7 < 0) return null;
        int s8 = line.indexOf('|', s7 + 1);
        if (s8 < 0) return null;
        long serial = Long.parseLong(line.substring(0, s1));
        long ms = Long.parseLong(line.substring(s1 + 1, s2));
        String txId = line.substring(s2 + 1, s3);
        String type = line.substring(s3 + 1, s4);
        String cur = line.substring(s4 + 1, s5);
        String fromS = line.substring(s5 + 1, s6);
        String toS = line.substring(s6 + 1, s7);
        double amt = Double.parseDouble(line.substring(s7 + 1, s8));
        String rest = line.substring(s8 + 1);
        String world = null;
        String md = null;
        int pipe = rest.indexOf('|');
        if (pipe >= 0) {
            world = rest.substring(0, pipe).isEmpty() ? null : rest.substring(0, pipe);
            md = rest.substring(pipe + 1);
        } else {
            world = rest.isEmpty() ? null : rest;
        }
        Map<String, String> metadata = new LinkedHashMap<>();
        if (md != null && !md.isEmpty()) {
            for (String kv : md.split("\\u0001")) {
                int eq = kv.indexOf('=');
                if (eq <= 0) continue;
                metadata.put(kv.substring(0, eq), kv.substring(eq + 1));
            }
        }
        TxRecord.Builder b = TxRecord.builder();
        b.serial(serial);
        b.instantMs(ms);
        b.txId(txId);
        try { b.txType(TxType.valueOf(type)); } catch (Exception ignored) { b.txType(TxType.PLAYER_PAY); }
        b.currencyId(cur);
        if (!fromS.isEmpty()) b.fromUuid(UUID.fromString(fromS));
        if (!toS.isEmpty()) b.toUuid(UUID.fromString(toS));
        b.amount(amt);
        b.worldName(world);
        b.metadata(metadata);
        return b.build();
    }

    private String toLine(TxRecord r) {
        StringBuilder sb = new StringBuilder();
        sb.append(r.getSerial()).append('|');
        sb.append(r.getInstantMs()).append('|');
        sb.append(r.getTxId()).append('|');
        sb.append(r.getTxType().name()).append('|');
        sb.append(r.getCurrencyId()).append('|');
        sb.append(r.getFromUuid() == null ? "" : r.getFromUuid().toString()).append('|');
        sb.append(r.getToUuid() == null ? "" : r.getToUuid().toString()).append('|');
        sb.append(Double.toString(r.getAmount())).append('|');
        sb.append(r.getWorldName() == null ? "" : r.getWorldName());
        if (!r.getMetadata().isEmpty()) {
            sb.append('|');
            boolean first = true;
            for (Map.Entry<String, String> e : r.getMetadata().entrySet()) {
                if (!first) sb.append('\u0001');
                sb.append(e.getKey()).append('=').append(e.getValue() == null ? "" : e.getValue());
                first = false;
            }
        }
        return sb.toString();
    }

    @Override
    public void insertBatch(List<TxRecord> records) throws IOException {
        if (records == null || records.isEmpty()) return;
        lock.lock();
        try {
            YamlConfiguration cfg = YamlConfiguration.loadConfiguration(file);
            List<String> lines = cfg.getStringList("txs");
            if (lines == null) lines = new ArrayList<>();
            for (TxRecord r : records) {
                lines.add(toLine(r));
            }
            cfg.set("txs", lines);
            cfg.set("next_index", cfg.getInt("next_index", 0) + records.size());
            cfg.save(file);
        } finally {
            lock.unlock();
        }
    }

    @Override
    public List<TxRecord> recentForPlayer(UUID player, int limit) throws IOException {
        List<TxRecord> recent = new ArrayList<>();
        if (player == null) return recent;
        lock.lock();
        try {
            YamlConfiguration cfg = YamlConfiguration.loadConfiguration(file);
            List<String> lines = cfg.getStringList("txs");
            if (lines == null) return recent;
            int take = Math.max(1, limit);
            for (int i = lines.size() - 1; i >= 0 && recent.size() < take; i--) {
                TxRecord r = fromLine(lines.get(i));
                if (r == null) continue;
                if (player.equals(r.getFromUuid()) || player.equals(r.getToUuid())) {
                    recent.add(r);
                }
            }
        } finally {
            lock.unlock();
        }
        return recent;
    }

    @Override
    public List<TxRecord> recentForTeam(String teamId, int limit) throws IOException {
        List<TxRecord> recent = new ArrayList<>();
        if (teamId == null || teamId.isEmpty()) return recent;
        lock.lock();
        try {
            YamlConfiguration cfg = YamlConfiguration.loadConfiguration(file);
            List<String> lines = cfg.getStringList("txs");
            if (lines == null) return recent;
            int take = Math.max(1, limit);
            for (int i = lines.size() - 1; i >= 0 && recent.size() < take; i--) {
                TxRecord r = fromLine(lines.get(i));
                if (r == null) continue;
                if (teamId.equals(r.getMetadata().get("team_id"))) recent.add(r);
            }
        } finally {
            lock.unlock();
        }
        return recent;
    }

    @Override
    public long countAll() throws IOException {
        lock.lock();
        try {
            YamlConfiguration cfg = YamlConfiguration.loadConfiguration(file);
            return cfg.getInt("next_index", 0);
        } finally {
            lock.unlock();
        }
    }
}
