package com.example.vault.economy;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

public final class TopCacheService {
    public static final class TopEntry {
        public final int rank;
        public final UUID uuid;
        public final String name;
        public final double balance;

        public TopEntry(int rank, UUID uuid, String name, double balance) {
            this.rank = rank;
            this.uuid = uuid;
            this.name = name;
            this.balance = balance;
        }
    }

    private final Plugin plugin;
    private final SimpleEconomy economy;
    private final Map<String, CachedTop> caches = new ConcurrentHashMap<>();
    private final AtomicBoolean refreshInProgress = new AtomicBoolean(false);
    private volatile org.bukkit.scheduler.BukkitTask taskId;

    private static final class CachedTop {
        final List<TopEntry> list;
        final Map<UUID, Integer> rankByUuid;

        CachedTop(List<TopEntry> list) {
            this.list = Collections.unmodifiableList(list);
            Map<UUID, Integer> m = new LinkedHashMap<>();
            for (TopEntry e : list) m.put(e.uuid, e.rank);
            this.rankByUuid = Collections.unmodifiableMap(m);
        }
    }

    public TopCacheService(Plugin plugin, SimpleEconomy economy) {
        this.plugin = plugin;
        this.economy = economy;
    }

    public void start(long refreshSeconds) {
        if (taskId != null) {
            try { taskId.cancel(); } catch (Throwable ignored) {}
            taskId = null;
        }
        long ticks = 20L * Math.max(1L, refreshSeconds);
        taskId = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, this::refreshAll, ticks, ticks);
        Bukkit.getScheduler().runTaskAsynchronously(plugin, this::refreshAll);
    }

    public void shutdown() {
        if (taskId != null) {
            try { taskId.cancel(); } catch (Throwable ignored) {}
            taskId = null;
        }
    }

    public void invalidateAll() {
        caches.clear();
        Bukkit.getScheduler().runTaskAsynchronously(plugin, this::refreshAll);
    }

    public void invalidate(String currencyId) {
        if (currencyId == null) { invalidateAll(); return; }
        caches.remove(currencyId);
    }

    public List<TopEntry> getTop(String currencyId, int limit) {
        CachedTop c = getOrBuild(currencyId);
        List<TopEntry> l = c.list;
        if (limit <= 0 || limit >= l.size()) return l;
        return Collections.unmodifiableList(l.subList(0, limit));
    }

    public TopEntry getRank(String currencyId, UUID uuid) {
        CachedTop c = getOrBuild(currencyId);
        Integer r = c.rankByUuid.get(uuid);
        if (r == null) return null;
        for (TopEntry e : c.list) if (e.uuid.equals(uuid)) return e;
        return null;
    }

    public int getRankNumber(String currencyId, UUID uuid) {
        CachedTop c = getOrBuild(currencyId);
        Integer r = c.rankByUuid.get(uuid);
        return r == null ? 0 : r;
    }

    private CachedTop getOrBuild(String currencyId) {
        String cid = (currencyId == null || currencyId.isEmpty()) ? economy.getDefaultCurrencyId() : currencyId;
        CachedTop c = caches.get(cid);
        if (c == null) {
            c = build(cid);
            caches.put(cid, c);
        }
        return c;
    }

    private void refreshAll() {
        if (!refreshInProgress.compareAndSet(false, true)) return;
        try {
            List<String> ids = economy.getCurrencyIds();
            for (String cid : ids) {
                CachedTop c = build(cid);
                caches.put(cid, c);
            }
        } finally {
            refreshInProgress.set(false);
        }
    }

    private CachedTop build(String currencyId) {
        Map<UUID, Double> snap = economy.snapshotBalances(currencyId);
        List<Map.Entry<UUID, Double>> entries = new ArrayList<>(snap.entrySet());
        entries.sort(Comparator.<Map.Entry<UUID, Double>, Double>comparing(Map.Entry::getValue).reversed());
        List<TopEntry> out = new ArrayList<>(entries.size());
        int rank = 1;
        for (Map.Entry<UUID, Double> e : entries) {
            UUID uuid = e.getKey();
            OfflinePlayer p = Bukkit.getOfflinePlayer(uuid);
            String name = p != null ? p.getName() : null;
            if (name == null || name.trim().isEmpty()) name = uuid.toString().substring(0, 8);
            out.add(new TopEntry(rank, uuid, name, e.getValue()));
            rank++;
        }
        return new CachedTop(out);
    }
}
