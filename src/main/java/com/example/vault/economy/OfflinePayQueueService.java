package com.example.vault.economy;

import com.example.vault.transactions.TxRecord;
import com.example.vault.transactions.TxType;
import com.example.vault.transactions.TransactionLogService;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public final class OfflinePayQueueService implements Listener {
    public static final class QueuedPay {
        public final long id;
        public final String currencyId;
        public final UUID fromUuid;
        public final String fromName;
        public final UUID toUuid;
        public final String toName;
        public final double amount;
        public final long createdAtMs;
        public final String note;

        public QueuedPay(long id, String currencyId, UUID fromUuid, String fromName,
                          UUID toUuid, String toName, double amount, long createdAtMs, String note) {
            this.id = id;
            this.currencyId = currencyId;
            this.fromUuid = fromUuid;
            this.fromName = fromName;
            this.toUuid = toUuid;
            this.toName = toName;
            this.amount = amount;
            this.createdAtMs = createdAtMs;
            this.note = note;
        }
    }

    private final Plugin plugin;
    private final SimpleEconomy economy;
    private final File storageFile;
    private final Map<Long, QueuedPay> queueById = new ConcurrentHashMap<>();
    private final Map<UUID, List<QueuedPay>> byToUuid = new ConcurrentHashMap<>();
    private final AtomicLong idSeq = new AtomicLong(1L);

    public OfflinePayQueueService(Plugin plugin, SimpleEconomy economy) {
        this.plugin = plugin;
        this.economy = economy;
        this.storageFile = new File(plugin.getDataFolder(), "offline_pay_queue.yml");
        load();
    }

    public void load() {
        queueById.clear();
        byToUuid.clear();
        long maxId = 0L;
        if (!storageFile.exists()) return;
        try {
            YamlConfiguration cfg = YamlConfiguration.loadConfiguration(storageFile);
            ConfigurationSection sec = cfg.getConfigurationSection("pending");
            if (sec == null) return;
            for (String raw : sec.getKeys(false)) {
                try {
                    long id = Long.parseLong(raw);
                    String prefix = "pending." + raw;
                    String currencyId = cfg.getString(prefix + ".currency", economy.getDefaultCurrencyId());
                    UUID from = UUID.fromString(cfg.getString(prefix + ".from"));
                    String fromName = cfg.getString(prefix + ".from_name", "");
                    UUID to = UUID.fromString(cfg.getString(prefix + ".to"));
                    String toName = cfg.getString(prefix + ".to_name", "");
                    double amount = cfg.getDouble(prefix + ".amount", 0.0);
                    long at = cfg.getLong(prefix + ".created_at_ms", System.currentTimeMillis());
                    String note = cfg.getString(prefix + ".note", "");
                    QueuedPay q = new QueuedPay(id, currencyId, from, fromName, to, toName, amount, at, note);
                    queueById.put(id, q);
                    byToUuid.computeIfAbsent(to, k -> new ArrayList<>()).add(q);
                    if (id > maxId) maxId = id;
                } catch (Exception ignored) {}
            }
        } catch (Exception ex) {
            plugin.getLogger().warning("Failed to load offline_pay_queue.yml: " + ex.getMessage());
        } finally {
            if (maxId > 0L) idSeq.set(maxId + 1L);
        }
    }

    public synchronized void save() {
        YamlConfiguration cfg = new YamlConfiguration();
        for (QueuedPay q : queueById.values()) {
            String prefix = "pending." + q.id;
            cfg.set(prefix + ".currency", q.currencyId);
            cfg.set(prefix + ".from", q.fromUuid.toString());
            cfg.set(prefix + ".from_name", q.fromName);
            cfg.set(prefix + ".to", q.toUuid.toString());
            cfg.set(prefix + ".to_name", q.toName);
            cfg.set(prefix + ".amount", q.amount);
            cfg.set(prefix + ".created_at_ms", q.createdAtMs);
            if (q.note != null && !q.note.isEmpty()) cfg.set(prefix + ".note", q.note);
        }
        try { cfg.save(storageFile); } catch (IOException ex) {
            plugin.getLogger().warning("Failed to save offline_pay_queue.yml: " + ex.getMessage());
        }
    }

    public QueuedPay queuePay(String currencyId, OfflinePlayer from, OfflinePlayer to, double amount, String note) {
        String cid = (currencyId == null || currencyId.isEmpty()) ? economy.getDefaultCurrencyId() : currencyId;
        long id = idSeq.getAndIncrement();
        String fromName = from.getName() != null ? from.getName() : from.getUniqueId().toString().substring(0, 8);
        String toName = to.getName() != null ? to.getName() : to.getUniqueId().toString().substring(0, 8);
        QueuedPay q = new QueuedPay(id, cid, from.getUniqueId(), fromName, to.getUniqueId(), toName, amount, System.currentTimeMillis(), note);
        queueById.put(id, q);
        byToUuid.computeIfAbsent(to.getUniqueId(), k -> new ArrayList<>()).add(q);
        save();
        TransactionLogService tx = economy.getTransactionLogService();
        if (tx != null) {
            try {
                TxRecord.Builder b = TxRecord.builder()
                        .txType(TxType.OFFLINE_PAY_SENT).currencyId(cid).amount(amount)
                        .fromUuid(from.getUniqueId()).toUuid(to.getUniqueId())
                        .putMeta("queue_id", String.valueOf(id));
                if (note != null && !note.isEmpty()) b.putMeta("note", note);
                tx.record(b);
            } catch (Throwable ignored) {}
        }
        return q;
    }

    public List<QueuedPay> listForTarget(UUID toUuid) {
        List<QueuedPay> l = byToUuid.get(toUuid);
        return l == null ? Collections.emptyList() : Collections.unmodifiableList(l);
    }

    public List<QueuedPay> listAll() {
        List<QueuedPay> l = new ArrayList<>(queueById.values());
        l.sort((a, b) -> Long.compare(b.createdAtMs, a.createdAtMs));
        return Collections.unmodifiableList(l);
    }

    public boolean refund(long id) {
        QueuedPay q = queueById.remove(id);
        if (q == null) return false;
        List<QueuedPay> l = byToUuid.get(q.toUuid);
        if (l != null) l.removeIf(x -> x.id == id);
        save();
        OfflinePlayer fromP = Bukkit.getOfflinePlayer(q.fromUuid);
        economy.createPlayerAccount(q.currencyId, fromP);
        economy.depositPlayer(q.currencyId, fromP, q.amount, TxType.OFFLINE_PAY_REFUNDED, "refund queue id=" + id);
        return true;
    }

    public int deliverFor(Player p) {
        UUID uuid = p.getUniqueId();
        List<QueuedPay> list = new ArrayList<>(listForTarget(uuid));
        if (list.isEmpty()) return 0;
        int delivered = 0;
        economy.createPlayerAccount(p);
        for (QueuedPay q : list) {
            economy.depositPlayer(q.currencyId, p, q.amount, TxType.OFFLINE_PAY_CLAIMED,
                    "from " + (q.fromName == null ? "player" : q.fromName));
            queueById.remove(q.id);
            delivered++;
        }
        byToUuid.remove(uuid);
        save();
        return delivered;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player p = event.getPlayer();
        Bukkit.getScheduler().runTask(plugin, () -> {
            int n = deliverFor(p);
            if (n > 0) {
                p.sendMessage(com.example.vault.util.ColorUtil.colorize("&aTienes " + n + " pago(s) pendiente(s) entregado(s)."));
            }
        });
    }
}
