package com.example.vault.transactions;

import com.example.vault.storage.Database;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiConsumer;

public class TransactionLogService {
    private final Plugin plugin;
    private final TransactionIdGenerator idGenerator;
    private final AntiDupeWindow antiDupeWindow;
    private final TransactionLogDAO dao;
    private final DailyJsonLogWriter jsonWriter;
    private final ConcurrentLinkedDeque<TxRecord> pending = new ConcurrentLinkedDeque<>();
    private final AtomicBoolean flushInProgress = new AtomicBoolean(false);
    private final AtomicBoolean shuttingDown = new AtomicBoolean(false);
    private volatile ExecutorService flusher;
    private volatile int flushIntervalTicks = 5 * 20;
    private volatile int batchSize = 500;
    private Integer bukkitTaskId;
    private final List<BiConsumer<TxRecord, Boolean>> dupeListeners = new CopyOnWriteArrayList<>();
    private final List<BiConsumer<List<TxRecord>, Throwable>> flushListeners = new CopyOnWriteArrayList<>();

    public TransactionLogService(Plugin plugin, Database mysql) {
        this.plugin = plugin;
        this.idGenerator = new TransactionIdGenerator(plugin);
        this.antiDupeWindow = new AntiDupeWindow();
        if (mysql != null) {
            this.dao = new MySqlTxDAO(mysql);
        } else {
            this.dao = new FileTxDAO(plugin);
        }
        DailyJsonLogWriter w = null;
        try { w = new DailyJsonLogWriter(plugin); } catch (IOException ex) {
            plugin.getLogger().warning("Failed to create transactions JSON log: " + ex.getMessage());
        }
        this.jsonWriter = w;
        startScheduler();
    }

    public void addDupeListener(BiConsumer<TxRecord, Boolean> listener) {
        if (listener != null) dupeListeners.add(listener);
    }

    public void addFlushListener(BiConsumer<List<TxRecord>, Throwable> listener) {
        if (listener != null) flushListeners.add(listener);
    }

    private void startScheduler() {
        if (Bukkit.getPluginManager().isPluginEnabled(plugin)) {
            try {
                bukkitTaskId = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, this::asyncFlushTick,
                        flushIntervalTicks, flushIntervalTicks).getTaskId();
            } catch (Throwable ignored) {
                // Bukkit scheduler not yet available
            }
        }
    }

    private synchronized ExecutorService flusher() {
        if (flusher == null || flusher.isShutdown()) {
            flusher = Executors.newSingleThreadExecutor(r -> {
                Thread t = new Thread(r, "VaultTxFlusher");
                t.setDaemon(true);
                return t;
            });
        }
        return flusher;
    }

    public boolean record(TxRecord.Builder builder) {
        if (builder == null) return false;
        if (builder.serial <= 0L) builder.serial(idGenerator.nextSerial());
        if (builder.txId == null) builder.txId(idGenerator.nextTxId());
        if (builder.instantMs <= 0L) builder.instantMs(System.currentTimeMillis());
        TxRecord r;
        try { r = builder.build(); } catch (Exception ex) {
            plugin.getLogger().warning("Invalid transaction builder: " + ex.getMessage());
            return false;
        }
        if (!antiDupeWindow.tryAccept(r.getSerial(), r.getTxId())) {
            fireDupe(r, false);
            return false;
        }
        pending.add(r);
        if (pending.size() >= batchSize) triggerAsyncFlush();
        return true;
    }

    private void fireDupe(TxRecord r, boolean hard) {
        for (BiConsumer<TxRecord, Boolean> l : dupeListeners) {
            try { l.accept(r, hard); } catch (Throwable ignored) {}
        }
    }

    public List<TxRecord> recentForPlayer(UUID player, int limit) {
        try {
            List<TxRecord> list = dao.recentForPlayer(player, limit);
            if (list != null) return list;
        } catch (IOException ex) {
            plugin.getLogger().warning("Failed to load recent transactions for player " + player + ": " + ex.getMessage());
        }
        return new ArrayList<>();
    }

    public List<TxRecord> recentForTeam(String teamId, int limit) {
        try {
            List<TxRecord> list = dao.recentForTeam(teamId, limit);
            if (list != null) return list;
        } catch (IOException ex) {
            plugin.getLogger().warning("Failed to load recent transactions for team " + teamId + ": " + ex.getMessage());
        }
        return new ArrayList<>();
    }

    public long getRecordedCount() {
        try { return dao.countAll(); } catch (IOException ex) { return -1L; }
    }

    public long getPendingCount() { return pending.size(); }

    public long getDupeRejections() { return antiDupeWindow.getRejections(); }

    public void triggerAsyncFlush() {
        if (shuttingDown.get()) {
            flushAllSynchronous();
            return;
        }
        if (!flushInProgress.compareAndSet(false, true)) return;
        try {
            flusher().submit(this::runFlush);
        } catch (Throwable t) {
            flushInProgress.set(false);
        }
    }

    void asyncFlushTick() {
        if (!pending.isEmpty()) triggerAsyncFlush();
    }

    private void runFlush() {
        ArrayList<TxRecord> batch = new ArrayList<>(Math.min(batchSize, pending.size() + 16));
        TxRecord r;
        while ((r = pending.pollFirst()) != null && batch.size() < batchSize) batch.add(r);
        Throwable err = null;
        if (!batch.isEmpty()) {
            try {
                dao.insertBatch(batch);
            } catch (IOException ex) {
                err = ex;
                plugin.getLogger().warning("Failed to persist transaction batch: " + ex.getMessage());
                // Re-enqueue at front for retry if not shutting down
                if (!shuttingDown.get()) {
                    for (int i = batch.size() - 1; i >= 0; i--) pending.addFirst(batch.get(i));
                }
            }
            if (err == null && jsonWriter != null) {
                try { jsonWriter.appendBatch(batch); }
                catch (IOException ex) { plugin.getLogger().warning("Failed to append JSON batch: " + ex.getMessage()); }
            }
        }
        for (BiConsumer<List<TxRecord>, Throwable> l : flushListeners) {
            try { l.accept(batch, err); } catch (Throwable ignored) {}
        }
        flushInProgress.set(false);
        if (!pending.isEmpty() && !shuttingDown.get()) {
            triggerAsyncFlush();
        }
    }

    public synchronized void flushAllSynchronous() {
        flushInProgress.getAndSet(true);
        try {
            ArrayList<TxRecord> all = new ArrayList<>(pending.size() + 32);
            TxRecord r;
            while ((r = pending.pollFirst()) != null) all.add(r);
            if (!all.isEmpty()) {
                try { dao.insertBatch(all); }
                catch (IOException ex) {
                    plugin.getLogger().warning("Failed to persist final transaction batch: " + ex.getMessage());
                }
                if (jsonWriter != null) {
                    try { jsonWriter.appendBatch(all); }
                    catch (IOException ex) { plugin.getLogger().warning("Failed to append final JSON batch: " + ex.getMessage()); }
                }
            }
            idGenerator.persistLatest();
        } finally {
            flushInProgress.set(false);
        }
    }

    public synchronized void shutdown() {
        shuttingDown.set(true);
        if (bukkitTaskId != null) {
            try { Bukkit.getScheduler().cancelTask(bukkitTaskId); } catch (Throwable ignored) {}
            bukkitTaskId = null;
        }
        flushAllSynchronous();
        ExecutorService f = flusher;
        if (f != null) {
            f.shutdown();
            try { f.awaitTermination(3L, TimeUnit.SECONDS); } catch (InterruptedException ignored) {}
            f.shutdownNow();
        }
        if (jsonWriter != null) jsonWriter.close();
        try { dao.close(); } catch (IOException ignored) {}
    }

    public TransactionIdGenerator getIdGenerator() { return idGenerator; }

    public AntiDupeWindow getAntiDupeWindow() { return antiDupeWindow; }
}
