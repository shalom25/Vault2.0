package com.example.vault.transactions;

import org.bukkit.plugin.Plugin;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class DailyJsonLogWriter implements AutoCloseable {
    private final Path logsDir;
    private volatile String currentDay;
    private volatile BufferedWriter currentWriter;
    private final Object lock = new Object();

    public DailyJsonLogWriter(Plugin plugin) throws IOException {
        Objects.requireNonNull(plugin, "plugin");
        Path dir = plugin.getDataFolder().toPath().resolve("logs");
        if (!Files.isDirectory(dir)) Files.createDirectories(dir);
        this.logsDir = dir;
        rotateIfNeeded(System.currentTimeMillis());
    }

    private void rotateIfNeeded(long nowMs) throws IOException {
        String day = LocalDate.ofInstant(java.time.Instant.ofEpochMilli(nowMs),
                ZoneId.systemDefault()).format(DateTimeFormatter.ISO_LOCAL_DATE);
        if (day.equals(currentDay) && currentWriter != null) return;
        synchronized (lock) {
            if (day.equals(currentDay) && currentWriter != null) return;
            if (currentWriter != null) {
                try { currentWriter.flush(); currentWriter.close(); } catch (IOException ignored) {}
                currentWriter = null;
            }
            Path f = logsDir.resolve("transactions-" + day + ".log");
            currentWriter = Files.newBufferedWriter(f, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND, StandardOpenOption.WRITE);
            currentDay = day;
        }
    }

    public void appendBatch(List<TxRecord> records) throws IOException {
        if (records == null || records.isEmpty()) return;
        synchronized (lock) {
            rotateIfNeeded(System.currentTimeMillis());
            for (TxRecord r : records) {
                currentWriter.write(toJson(r));
                currentWriter.newLine();
            }
            currentWriter.flush();
        }
    }

    public void appendRaw(String json) throws IOException {
        if (json == null || json.isEmpty()) return;
        synchronized (lock) {
            rotateIfNeeded(System.currentTimeMillis());
            currentWriter.write(json);
            currentWriter.newLine();
            currentWriter.flush();
        }
    }

    private static String toJson(TxRecord r) {
        StringBuilder sb = new StringBuilder(160);
        sb.append('{');
        append(sb, "tx_id", r.getTxId(), true);
        sb.append(',');
        sb.append("\"serial\":").append(r.getSerial()).append(',');
        sb.append("\"ts\":").append(r.getInstantMs()).append(',');
        append(sb, "type", r.getTxType().name(), false); sb.append(',');
        append(sb, "currency", r.getCurrencyId(), false); sb.append(',');
        if (r.getFromUuid() != null) { append(sb, "from", r.getFromUuid().toString(), false); sb.append(','); }
        if (r.getToUuid() != null) { append(sb, "to", r.getToUuid().toString(), false); sb.append(','); }
        sb.append("\"amount\":").append(r.getAmount());
        if (r.getWorldName() != null && !r.getWorldName().isEmpty()) {
            sb.append(','); append(sb, "world", r.getWorldName(), false);
        }
        if (r.getMetadata() != null && !r.getMetadata().isEmpty()) {
            sb.append(",\"meta\":{");
            boolean first = true;
            for (Map.Entry<String, String> e : r.getMetadata().entrySet()) {
                if (!first) sb.append(',');
                append(sb, e.getKey(), e.getValue(), true);
                first = false;
            }
            sb.append('}');
        }
        sb.append('}');
        return sb.toString();
    }

    private static void append(StringBuilder sb, String k, String v, boolean quoteKey) {
        if (quoteKey) sb.append('"').append(esc(k)).append("\":");
        sb.append('"').append(esc(v)).append('"');
    }

    private static String esc(String s) {
        if (s == null) return "";
        StringBuilder sb = new StringBuilder(s.length() + 8);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"': sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                case '\t': sb.append("\\t"); break;
                default: sb.append(c);
            }
        }
        return sb.toString();
    }

    @Override
    public void close() {
        synchronized (lock) {
            if (currentWriter != null) {
                try { currentWriter.flush(); currentWriter.close(); } catch (IOException ignored) {}
                currentWriter = null;
            }
        }
    }
}
