package com.example.vault.transactions;

import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

public final class TransactionIdGenerator {
    private final AtomicLong serial;
    private final Object persistLock = new Object();
    private final File serialFile;

    public TransactionIdGenerator(Plugin plugin) {
        Objects.requireNonNull(plugin, "plugin");
        File folder = new File(plugin.getDataFolder(), "transactions");
        if (!folder.exists()) folder.mkdirs();
        this.serialFile = new File(folder, "serial.dat");
        long start = 1L;
        if (serialFile.exists()) {
            try {
                String s = new String(Files.readAllBytes(serialFile.toPath()), StandardCharsets.UTF_8).trim();
                if (!s.isEmpty()) start = Math.max(1L, Long.parseLong(s) + 1L);
            } catch (Exception ignored) {
                start = System.currentTimeMillis();
            }
        }
        this.serial = new AtomicLong(start);
    }

    public long nextSerial() {
        long s = serial.getAndIncrement();
        if ((s & 0x3F) == 0L) { // persist each 64 serials
            persist(s);
        }
        return s;
    }

    public void persistLatest() {
        persist(serial.get());
    }

    private void persist(long s) {
        synchronized (persistLock) {
            try {
                Files.write(serialFile.toPath(), Long.toString(s).getBytes(StandardCharsets.UTF_8));
            } catch (IOException ignored) {}
        }
    }

    public String nextTxId() {
        String hex = Long.toHexString(UUID.randomUUID().getLeastSignificantBits());
        if (hex.length() > 10) hex = hex.substring(0, 10);
        else if (hex.length() < 10) hex = ("0000000000" + hex).substring(hex.length());
        return "tx-" + Long.toString(System.currentTimeMillis(), 36) + "-" + hex;
    }
}
