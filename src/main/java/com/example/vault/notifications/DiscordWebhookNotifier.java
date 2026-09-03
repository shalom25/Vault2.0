package com.example.vault.notifications;

import com.example.vault.economy.SimpleEconomy;
import com.example.vault.i18n.Messages;
import com.example.vault.transactions.TxRecord;
import com.example.vault.transactions.TxType;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.Plugin;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

public final class DiscordWebhookNotifier {
    private final Plugin plugin;
    private final SimpleEconomy economy;
    private final Messages messages;
    private final Set<String> recentlySent = Collections.synchronizedSet(new RollingLinkedHashSet(500));
    private final HttpClient client;
    private volatile String webhookUrl;
    private volatile double thresholdAmount;
    private volatile String username;
    private volatile String avatarUrl;
    private volatile boolean enabled;

    public DiscordWebhookNotifier(Plugin plugin, SimpleEconomy economy, Messages messages) {
        this.plugin = plugin;
        this.economy = economy;
        this.messages = messages;
        HttpClient c = null;
        try {
            c = HttpClient.newBuilder()
                    .connectTimeout(java.time.Duration.ofSeconds(5))
                    .followRedirects(HttpClient.Redirect.NORMAL)
                    .build();
        } catch (Throwable t) {
            c = null;
        }
        this.client = c;
        reload();
    }

    public void reload() {
        FileConfiguration cfg = plugin.getConfig();
        String url = cfg.getString("discord.webhook_url", "");
        this.webhookUrl = (url == null) ? "" : url.trim();
        this.thresholdAmount = cfg.getDouble("discord.threshold_amount", 100000.0);
        String u = cfg.getString("discord.username", "Vault Bot");
        this.username = (u == null || u.isEmpty()) ? "Vault Bot" : u;
        String a = cfg.getString("discord.avatar_url", "");
        this.avatarUrl = (a == null) ? "" : a.trim();
        this.enabled = !webhookUrl.isEmpty() && client != null;
    }

    public boolean isEnabled() { return enabled; }

    public void onTransactionBatch(List<TxRecord> batch, Throwable err) {
        if (!enabled) return;
        if (err != null) {
            sendAsync(buildErrorEmbed(err, batch == null ? 0 : batch.size()));
            return;
        }
        if (batch == null || batch.isEmpty()) return;
        int sent = 0;
        for (TxRecord tx : batch) {
            if (sent >= 5) break;
            if (tx == null) continue;
            double amt = Math.abs(tx.getAmount());
            if (amt < thresholdAmount) continue;
            if (recentlySent.contains(tx.getTxId())) continue;
            recentlySent.add(tx.getTxId());
            sent++;
            sendAsync(buildThresholdEmbed(tx));
        }
    }

    public void onDupeDetected(TxRecord tx, boolean hard) {
        if (!enabled || tx == null) return;
        String key = "DUPE:" + tx.getTxId();
        if (recentlySent.contains(key)) return;
        recentlySent.add(key);
        sendAsync(buildDupeEmbed(tx, hard));
    }

    // ---------- i18n helpers ----------
    private String msg(String key) {
        String s = messages.getOptional(key);
        return s == null ? key : s;
    }

    private String fmt(String key, Map<String, String> ctx) {
        String raw = msg(key);
        if (ctx != null) {
            for (Map.Entry<String, String> e : ctx.entrySet()) {
                raw = raw.replace("%" + e.getKey() + "%", e.getValue() == null ? "" : e.getValue());
            }
        }
        return raw;
    }

    private Map<String, String> ctx(TxRecord tx) {
        HashMap<String, String> m = new HashMap<>();
        boolean in = isInFlow(tx);
        String amtStr = economy != null
                ? economy.format(tx.getCurrencyId(), Math.abs(tx.getAmount()))
                : String.format(Locale.ROOT, "%.2f", Math.abs(tx.getAmount()));
        String sign = in ? "+" : "-";
        String txId = tx.getTxId();
        String txIdShort = txId == null ? "?" : txId.substring(0, Math.min(16, txId.length()));
        String world = tx.getWorldName() == null || tx.getWorldName().isEmpty() ? "global" : tx.getWorldName();
        String metaStr = formatMetadata(tx.getMetadata());
        m.put("type", prettyType(tx.getTxType()));
        m.put("type_key", tx.getTxType() == null ? "UNKNOWN" : tx.getTxType().name());
        m.put("sign", sign);
        m.put("amount", amtStr);
        m.put("amount_raw", String.format(Locale.ROOT, "%.2f", Math.abs(tx.getAmount())));
        m.put("currency", tx.getCurrencyId());
        m.put("from", pretty(tx.getFromUuid()));
        m.put("to", pretty(tx.getToUuid()));
        m.put("world", world);
        m.put("txid", txId == null ? "?" : txId);
        m.put("txid_short", txIdShort);
        m.put("serial", String.valueOf(tx.getSerial()));
        m.put("metadata", metaStr.isEmpty() ? "-" : metaStr);
        return m;
    }

    // ---------- Builders ----------
    private String buildThresholdEmbed(TxRecord tx) {
        Map<String, String> c = ctx(tx);
        StringBuilder sb = new StringBuilder();
        sb.append("{");
        appendUser(sb);
        String title = escape(fmt("discord.threshold.title", c));
        String desc = escape(fmt("discord.threshold.desc", c));
        int color = (isInFlow(tx) ? 0x00ff7f : 0xff4444);
        sb.append("\"embeds\":[{");
        sb.append("\"title\":\"").append(title).append("\",");
        sb.append("\"description\":\"").append(desc).append("\",");
        sb.append("\"color\":").append(color).append(",");
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.ROOT);
        String iso = sdf.format(new Date(tx.getInstantMs()));
        sb.append("\"timestamp\":\"").append(iso).append("\",");
        sb.append("\"fields\":[");
        field(sb, msg("discord.field.txid"), "`" + c.get("txid_short") + "`", true);
        sb.append(",");
        field(sb, msg("discord.field.currency"), c.get("currency"), true);
        sb.append(",");
        field(sb, msg("discord.field.amount"), "**" + escape(c.get("amount")) + "**", true);
        sb.append(",");
        field(sb, msg("discord.field.from"), "`" + c.get("from") + "`", true);
        sb.append(",");
        field(sb, msg("discord.field.to"), "`" + c.get("to") + "`", true);
        sb.append(",");
        field(sb, msg("discord.field.world"), c.get("world"), true);
        sb.append(",");
        field(sb, msg("discord.field.metadata"), c.get("metadata"), false);
        sb.append("]");
        sb.append("}]}");
        return sb.toString();
    }

    private String buildDupeEmbed(TxRecord tx, boolean hard) {
        Map<String, String> c = ctx(tx);
        c.put("hard", hard ? msg("discord.dupe.hard_label") : msg("discord.dupe.soft_label"));
        StringBuilder sb = new StringBuilder();
        sb.append("{");
        appendUser(sb);
        String title = escape(fmt("discord.dupe.title", c));
        String desc = escape(fmt("discord.dupe.desc", c));
        int color = hard ? 0xff0000 : 0xffa500;
        sb.append("\"embeds\":[{");
        sb.append("\"title\":\"").append(title).append("\",");
        sb.append("\"description\":\"").append(desc).append("\",");
        sb.append("\"color\":").append(color).append(",");
        sb.append("\"fields\":[");
        field(sb, msg("discord.field.from"), "`" + c.get("from") + "`", true);
        sb.append(",");
        field(sb, msg("discord.field.to"), "`" + c.get("to") + "`", true);
        sb.append(",");
        field(sb, msg("discord.dupe.field.rejections"), String.valueOf(1), true);
        sb.append("]");
        sb.append("}]}");
        return sb.toString();
    }

    private String buildErrorEmbed(Throwable err, int batchSize) {
        HashMap<String, String> c = new HashMap<>();
        String msgStr = err == null ? "(null)" : err.getMessage();
        if (msgStr == null) msgStr = err == null ? "(null)" : err.getClass().getSimpleName();
        c.put("batch_size", String.valueOf(batchSize));
        c.put("error", truncate(msgStr, 400));
        StringBuilder sb = new StringBuilder();
        sb.append("{");
        appendUser(sb);
        String title = escape(fmt("discord.error.title", c));
        String desc = escape(fmt("discord.error.desc", c));
        int color = 0xff2222;
        sb.append("\"embeds\":[{");
        sb.append("\"title\":\"").append(title).append("\",");
        sb.append("\"description\":\"").append(desc).append("\",");
        sb.append("\"color\":").append(color).append("}]}");
        return sb.toString();
    }

    // ---------- Helpers ----------
    private void appendUser(StringBuilder sb) {
        sb.append("\"username\":\"").append(escape(username)).append("\"");
        if (avatarUrl != null && !avatarUrl.isEmpty()) {
            sb.append(",\"avatar_url\":\"").append(escape(avatarUrl)).append("\"");
        }
        sb.append(",");
    }

    private static void field(StringBuilder sb, String name, String value, boolean inline) {
        sb.append("{\"name\":\"").append(escape(name)).append("\",")
                .append("\"value\":\"").append(value == null ? "" : value).append("\",")
                .append("\"inline\":").append(inline).append("}");
    }

    private static boolean isInFlow(TxRecord tx) {
        if (tx == null || tx.getTxType() == null) return false;
        switch (tx.getTxType()) {
            case DEPOSIT:
            case INTEREST:
            case NOTE_REDEEM:
            case OFFLINE_PAY_CLAIMED:
            case ADMIN_ADD:
            case LOAN_DISBURSE:
            case TEAM_DEPOSIT:
            case BANK_WITHDRAW:
            case CHARGE_PAID:
                return true;
            default:
                return false;
        }
    }

    private String prettyType(TxType t) {
        if (t == null) return "UNKNOWN";
        String s = messages.getOptional("discord.tx_type." + t.name());
        if (s == null || s.isEmpty() || s.startsWith("discord.tx_type.")) {
            return t.name();
        }
        return s;
    }

    private static String pretty(UUID u) {
        if (u == null) return "N/A";
        if (new UUID(0L, 0L).equals(u)) return "CONSOLE";
        try {
            OfflinePlayer op = Bukkit.getOfflinePlayer(u);
            if (op != null) {
                String n = op.getName();
                if (n != null && !n.trim().isEmpty()) return n;
            }
        } catch (Throwable ignored) {}
        return u.toString().substring(0, 8) + "...";
    }

    private static String formatMetadata(Map<String, String> md) {
        if (md == null || md.isEmpty()) return "";
        StringBuilder notes = new StringBuilder();
        int n = 0;
        for (Map.Entry<String, String> e : md.entrySet()) {
            if (n++ >= 6) break;
            notes.append("• ").append(escape(e.getKey())).append(": `")
                    .append(escape(truncate(e.getValue(), 80))).append("`\\n");
        }
        return notes.toString();
    }

    private static String truncate(String s, int max) {
        if (s == null) return "";
        if (s.length() <= max) return s;
        return s.substring(0, max - 3) + "...";
    }

    private static String escape(String s) {
        if (s == null) return "";
        StringBuilder sb = new StringBuilder(s.length() + 16);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"': sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                case '\t': sb.append("\\t"); break;
                case '\b': sb.append("\\b"); break;
                case '\f': sb.append("\\f"); break;
                default:
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
            }
        }
        return sb.toString();
    }

    private void sendAsync(String json) {
        if (!enabled || json == null || client == null) return;
        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(webhookUrl))
                    .timeout(java.time.Duration.ofSeconds(8))
                    .header("Content-Type", "application/json; charset=utf-8")
                    .header("User-Agent", "Vault-2.1-Economy")
                    .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8))
                    .build();
            CompletableFuture<HttpResponse<Void>> f = client.sendAsync(req, HttpResponse.BodyHandlers.discarding());
            f.orTimeout(10L, TimeUnit.SECONDS).whenComplete((resp, t) -> {
                if (t != null) {
                    plugin.getLogger().warning("[Discord] Failed to deliver webhook: " + t.getMessage());
                    return;
                }
                if (resp != null) {
                    int code = resp.statusCode();
                    if (code < 200 || code >= 300) {
                        plugin.getLogger().warning("[Discord] Webhook returned HTTP " + code);
                    }
                }
            });
        } catch (Throwable t) {
            plugin.getLogger().warning("[Discord] Failed to queue webhook: " + t.getMessage());
        }
    }

    private static final class RollingLinkedHashSet extends LinkedHashSet<String> {
        private final int max;
        RollingLinkedHashSet(int max) { this.max = max; }
        @Override
        public boolean add(String s) {
            boolean added = super.add(s);
            if (size() > max) {
                Iterator<String> it = iterator();
                if (it.hasNext()) it.next(); it.remove();
            }
            return added;
        }
    }
}
