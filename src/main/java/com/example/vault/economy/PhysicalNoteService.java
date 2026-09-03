package com.example.vault.economy;

import com.example.vault.i18n.Messages;
import com.example.vault.transactions.TxRecord;
import com.example.vault.transactions.TxType;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.Plugin;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class PhysicalNoteService implements Listener {
    private final SimpleEconomy economy;
    private final Messages messages;
    private final File redeemedFile;
    private final Map<String, Boolean> redeemed = new ConcurrentHashMap<>();
    private final byte[] hmacSecret;

    // Runtime reflection handles (1.14+ only; null en 1.8-1.13)
    private final boolean pdcAvailable;
    private final Object noteIdKey;
    private final Object amountKey;
    private final Object currencyKey;
    private final Object issuerKey;
    private final Object issuedAtKey;
    private final Object sigKey;
    private final Object stringType;
    private final Object doubleType;
    private final Object longType;
    private final Method metaGetPdc;
    private final Method pdcSet;
    private final Method pdcHas;
    private final Method pdcGet;

    public PhysicalNoteService(Plugin plugin, SimpleEconomy economy, Messages messages) {
        Objects.requireNonNull(plugin, "plugin");
        this.economy = economy;
        this.messages = Objects.requireNonNull(messages, "messages");
        this.redeemedFile = new File(plugin.getDataFolder(), "redeemed_notes.yml");
        byte[] secret;
        File secretFile = new File(plugin.getDataFolder(), ".note-secret.dat");
        if (secretFile.exists() && secretFile.length() >= 32) {
            try {
                byte[] raw = java.nio.file.Files.readAllBytes(secretFile.toPath());
                if (raw.length >= 32) {
                    secret = new byte[32];
                    System.arraycopy(raw, 0, secret, 0, 32);
                } else secret = generateSecret();
            } catch (Exception ex) { secret = generateSecret(); }
        } else secret = generateSecret();
        try {
            if (!secretFile.exists()) {
                java.nio.file.Files.write(secretFile.toPath(), secret);
                try {
                    String os = System.getProperty("os.name", "").toLowerCase();
                    if (os.contains("win")) {
                        Runtime.getRuntime().exec("attrib +H \"" + secretFile.getAbsolutePath() + "\"");
                    }
                } catch (Throwable ignored) {}
            }
        } catch (Throwable ignored) {}
        this.hmacSecret = secret;

        // --- Intentar inicializar PDC via reflection (solo si la API lo soporta en runtime) ---
        boolean ok = false;
        Object nk = null, ak = null, ck = null, ik = null, iak = null, sk = null;
        Object st = null, dt = null, lt = null;
        Method mgp = null, ps = null, ph = null, pg = null;
        try {
            Class<?> nsKeyClass = Class.forName("org.bukkit.NamespacedKey");
            Class<?> pdcClass = Class.forName("org.bukkit.persistence.PersistentDataContainer");
            Class<?> pdTypeClass = Class.forName("org.bukkit.persistence.PersistentDataType");
            Constructor<?> nsCtor = nsKeyClass.getConstructor(Plugin.class, String.class);
            nk = nsCtor.newInstance(plugin, "note_id");
            ak = nsCtor.newInstance(plugin, "note_amount");
            ck = nsCtor.newInstance(plugin, "note_currency");
            ik = nsCtor.newInstance(plugin, "note_issuer");
            iak = nsCtor.newInstance(plugin, "note_issued_at");
            sk = nsCtor.newInstance(plugin, "note_sig");
            Class<?> pdtClass = Class.forName("org.bukkit.persistence.PersistentDataType$STRING");
            Class<?> pddClass = Class.forName("org.bukkit.persistence.PersistentDataType$DOUBLE");
            Class<?> pdlClass = Class.forName("org.bukkit.persistence.PersistentDataType$LONG");
            st = pdtClass.getField("INSTANCE").get(null);
            dt = pddClass.getField("INSTANCE").get(null);
            lt = pdlClass.getField("INSTANCE").get(null);
            mgp = Class.forName("org.bukkit.inventory.meta.ItemMeta").getMethod("getPersistentDataContainer");
            ps = pdcClass.getMethod("set", nsKeyClass, pdTypeClass, Object.class);
            ph = pdcClass.getMethod("has", nsKeyClass, pdTypeClass);
            pg = pdcClass.getMethod("get", nsKeyClass, pdTypeClass);
            ok = true;
        } catch (Throwable t) {
            ok = false;
        }
        this.pdcAvailable = ok;
        this.noteIdKey = nk;
        this.amountKey = ak;
        this.currencyKey = ck;
        this.issuerKey = ik;
        this.issuedAtKey = iak;
        this.sigKey = sk;
        this.stringType = st;
        this.doubleType = dt;
        this.longType = lt;
        this.metaGetPdc = mgp;
        this.pdcSet = ps;
        this.pdcHas = ph;
        this.pdcGet = pg;

        loadRedeemed();
    }

    private byte[] generateSecret() {
        byte[] r = new byte[32];
        new SecureRandom().nextBytes(r);
        return r;
    }

    public void loadRedeemed() {
        redeemed.clear();
        if (!redeemedFile.exists()) return;
        try {
            YamlConfiguration cfg = YamlConfiguration.loadConfiguration(redeemedFile);
            List<String> list = cfg.getStringList("redeemed");
            for (String s : list) if (s != null && !s.isEmpty()) redeemed.put(s.trim(), Boolean.TRUE);
        } catch (Throwable ignored) {}
    }

    public synchronized void saveRedeemed() {
        YamlConfiguration cfg = new YamlConfiguration();
        List<String> list = new ArrayList<>(redeemed.keySet());
        cfg.set("redeemed", list);
        try { cfg.save(redeemedFile); } catch (IOException ignored) {}
    }

    private String hmac(String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(hmacSecret, "HmacSHA256"));
            byte[] digest = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
        } catch (Throwable t) { throw new RuntimeException("HMAC failed", t); }
    }

    private void setItemHandNull(Player p) {
        p.getInventory().setItemInHand(null);
    }

    public ItemStack withdrawNote(Player issuer, String currencyId, double amount, String noteIdOverride) {
        String cid = (currencyId == null || currencyId.isEmpty()) ? economy.getDefaultCurrencyId() : currencyId;
        if (!Double.isFinite(amount) || amount <= 0) throw new IllegalArgumentException("invalid amount");
        economy.getCurrency(cid); // Valida que exista
        economy.createPlayerAccount(cid, issuer);
        EconomyResponse r = economy.withdrawPlayer(cid, issuer, amount, TxType.NOTE_WITHDRAW, "physical note issued");
        if (!r.transactionSuccess()) return null;
        String noteId = noteIdOverride != null && !noteIdOverride.isEmpty() ? noteIdOverride :
                UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        long issuedAt = System.currentTimeMillis();
        String issuerId = issuer.getUniqueId().toString();
        ItemStack stack = new ItemStack(Material.PAPER, 1);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            if (pdcAvailable) {
                try {
                    Object pdc = metaGetPdc.invoke(meta);
                    pdcSet.invoke(pdc, noteIdKey, stringType, noteId);
                    pdcSet.invoke(pdc, amountKey, doubleType, amount);
                    pdcSet.invoke(pdc, currencyKey, stringType, cid);
                    pdcSet.invoke(pdc, issuerKey, stringType, issuerId);
                    pdcSet.invoke(pdc, issuedAtKey, longType, issuedAt);
                    String payload = noteId + "|" + cid + "|" + amount + "|" + issuerId + "|" + issuedAt;
                    String sig = hmac(payload);
                    pdcSet.invoke(pdc, sigKey, stringType, sig);
                } catch (Throwable ignored) {}
            }
            String amountFmt = economy.format(cid, amount);
            Map<String, String> ctx = new LinkedHashMap<>();
            ctx.put("amount", amountFmt);
            ctx.put("currency", cid);
            ctx.put("issuer", issuer.getName());
            ctx.put("serial", noteId);
            meta.setDisplayName(messages.colorize(messages.format("note.item.name", ctx)));
            List<String> lore = new ArrayList<>();
            lore.add(messages.colorize(messages.format("note.item.lore.currency", ctx)));
            lore.add(messages.colorize(messages.format("note.item.lore.amount", ctx)));
            lore.add(messages.colorize(messages.format("note.item.lore.issuer", ctx)));
            lore.add(messages.colorize(messages.format("note.item.lore.serial", ctx)));
            lore.add("");
            lore.add(messages.color("note.item.lore.hint"));
            try {
                String payload = noteId + "|" + cid + "|" + amount;
                String sig = hmac(payload);
                lore.add(ChatColor.DARK_GRAY + "SIG: " + sig);
            } catch (Throwable ignored) {}
            meta.setLore(lore);
            stack.setItemMeta(meta);
        }
        if (economy.getTransactionLogService() != null) {
            try {
                TxRecord.Builder b = TxRecord.builder()
                        .txType(TxType.NOTE_WITHDRAW).currencyId(cid).amount(amount)
                        .fromUuid(issuer.getUniqueId()).toUuid(issuer.getUniqueId())
                        .putMeta("note_id", noteId);
                economy.getTransactionLogService().record(b);
            } catch (Throwable ignored) {}
        }
        return stack;
    }

    public Map<String, Object> extractNoteData(ItemStack stack) {
        if (stack == null || !stack.hasItemMeta()) return null;
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) return null;
        if (pdcAvailable) {
            try {
                Object pdc = metaGetPdc.invoke(meta);
                Boolean has = (Boolean) pdcHas.invoke(pdc, noteIdKey, stringType);
                if (Boolean.TRUE.equals(has)) {
                    String noteId = (String) pdcGet.invoke(pdc, noteIdKey, stringType);
                    Double amount = (Double) pdcGet.invoke(pdc, amountKey, doubleType);
                    String cid = (String) pdcGet.invoke(pdc, currencyKey, stringType);
                    String issuerId = (String) pdcGet.invoke(pdc, issuerKey, stringType);
                    Long issuedAt = (Long) pdcGet.invoke(pdc, issuedAtKey, longType);
                    String sig = (String) pdcGet.invoke(pdc, sigKey, stringType);
                    if (noteId == null || amount == null || cid == null || issuerId == null || issuedAt == null || sig == null)
                        return extractFallback(meta);
                    String payload = noteId + "|" + cid + "|" + amount + "|" + issuerId + "|" + issuedAt;
                    String expected = hmac(payload);
                    if (!expected.equals(sig)) return extractFallback(meta);
                    Map<String, Object> out = new LinkedHashMap<>();
                    out.put("note_id", noteId);
                    out.put("amount", amount);
                    out.put("currency", cid);
                    out.put("issuer", UUID.fromString(issuerId));
                    out.put("issued_at", issuedAt);
                    return out;
                }
            } catch (Throwable ignored) {}
        }
        return extractFallback(meta);
    }

    private Double parseAmountLoose(String raw) {
        if (raw == null || raw.isEmpty()) return null;
        String clean = raw.replaceAll("[^0-9.,]", "");
        if (clean.isEmpty()) return null;
        int dot = clean.lastIndexOf('.');
        int com = clean.lastIndexOf(',');
        char decSep;
        if (dot >= 0 && com >= 0) decSep = (dot > com) ? '.' : ',';
        else if (com >= 0) decSep = ',';
        else decSep = '.';
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < clean.length(); i++) {
            char c = clean.charAt(i);
            if (c == decSep) sb.append('.');
            else if (c == '.' || c == ',') { /* thousands separator, skip */ }
            else sb.append(c);
        }
        try { return Double.parseDouble(sb.toString()); }
        catch (NumberFormatException ignored) { return null; }
    }

    private String sc(String raw) {
        if (raw == null) return null;
        return ChatColor.stripColor(raw);
    }

    private String getRaw(String key) {
        String s = messages.getOptional(key);
        if (s == null) return "";
        return s;
    }

    private java.util.Set<String> labelStarts(String key) {
        java.util.Set<String> out = new java.util.HashSet<>(6);
        String raw = sc(getRaw(key));
        if (raw == null || raw.isEmpty()) return out;
        int idx = raw.indexOf('%');
        String head = idx >= 0 ? raw.substring(0, idx) : raw;
        head = head.trim();
        if (head.isEmpty()) return out;
        out.add(head);
        out.add(head + ":");
        // Without trailing colon if present
        if (head.endsWith(":")) {
            String noColon = head.substring(0, head.length() - 1).trim();
            if (!noColon.isEmpty()) { out.add(noColon); out.add(noColon + ":"); }
        }
        // With space
        for (String s : new ArrayList<>(out)) {
            out.add(s + " ");
        }
        return out;
    }

    private boolean startsWithAny(String line, java.util.Set<String> prefixes) {
        if (line == null || prefixes == null || prefixes.isEmpty()) return false;
        for (String p : prefixes) {
            if (p == null || p.isEmpty()) continue;
            if (line.startsWith(p)) return true;
        }
        return false;
    }

    private String valueAfter(String line, java.util.Set<String> prefixes) {
        if (line == null || prefixes == null) return null;
        for (String p : prefixes) {
            if (p == null || p.isEmpty()) continue;
            if (line.startsWith(p)) {
                String val = line.substring(p.length());
                if (val != null) val = val.trim();
                // Skip first colon standalone
                if (val != null && val.startsWith(":")) val = val.substring(1).trim();
                return val;
            }
        }
        return null;
    }

    private java.util.Set<String> displayNameStarts() {
        // note.item.name → e.g. "&6&lBillete: &f%amount% %currency%" -> strip -> "Billete: $1000 USD" -> head before first %
        java.util.Set<String> out = new java.util.HashSet<>(labelStarts("note.item.name"));
        // Safety fallback for legacy / mixed-lang setups: original English / Spanish / Portuguese tokens
        out.add("Billete"); out.add("Billete:");
        out.add("Note"); out.add("Note:");
        out.add("Ticket"); out.add("Ticket:");
        out.add("Billet"); out.add("Billet:");
        out.add("Schein"); out.add("Schein:");
        out.add("Nota"); out.add("Nota:");
        out.add("Bilety"); out.add("Bilety:");
        out.add("Билет"); out.add("Билет:");
        out.add("钞票"); out.add("票券"); out.add("紙鈔");
        out.add("नोट"); out.add("नोट:");
        return out;
    }

    private java.util.List<String> loreContainsAnyTokens() {
        // Contains-matchers for detection of "looks-like-note" hint + labels
        java.util.List<String> out = new ArrayList<>();
        // Label tokens from i18n
        addLabelTokens(out, "note.item.lore.currency");
        addLabelTokens(out, "note.item.lore.amount");
        addLabelTokens(out, "note.item.lore.issuer");
        addLabelTokens(out, "note.item.lore.serial");
        addLabelTokens(out, "note.item.lore.hint");
        // Legacy raw hints from i18n full line
        String hint = sc(getRaw("note.item.lore.hint"));
        if (hint != null && !hint.isEmpty()) {
            // Split by common separators to get words
            for (String w : hint.split("[\\s:,.!?()\\[\\]\\-]+")) {
                if (w != null && w.length() >= 3) out.add(w.toLowerCase(Locale.ROOT));
            }
        }
        // Fallback words (legacy safety across 11 langs)
        String[] legacy = {
                "canjear", "redeem", "cambiar", "cobrar", "cash",
                "click derecho", "right click", "right-click", "clique droit", "direito",
                "rechtsklick", "kliknij prawym", "правая кнопка", "пкм",
                "右键", "右鍵", "दाएँ क्लिक", "seri", "serial", "nº", "№", "s/n",
                "moneda", "currency", "moeda", "devise", "währung", "valuta", "waluta", "валюта", "货币", "मुद्रा",
                "importe", "amount", "valor", "montant", "betrag", "kwota", "сумма", "金额", "金額", "राशि",
                "emisor", "issuer", "emitente", "émetteur", "aussteller", "wydawca", "эмитент", "发行方", "發行人", "जारी"
        };
        for (String w : legacy) out.add(w.toLowerCase(Locale.ROOT));
        return out;
    }

    private void addLabelTokens(java.util.List<String> out, String key) {
        for (String s : labelStarts(key)) {
            String t = s == null ? "" : s.trim().replace(":", "").replace(" ", "").toLowerCase(Locale.ROOT);
            if (!t.isEmpty() && t.length() >= 2) out.add(t);
            String t2 = s == null ? "" : s.trim().toLowerCase(Locale.ROOT);
            if (t2.length() >= 3) out.add(t2);
        }
        String whole = sc(getRaw(key));
        if (whole != null) {
            int idx = whole.indexOf('%');
            String head = (idx >= 0 ? whole.substring(0, idx) : whole).trim().toLowerCase(Locale.ROOT);
            if (!head.isEmpty()) out.add(head);
        }
    }

    private Map<String, Object> extractFallback(ItemMeta meta) {
        if (!meta.hasLore()) return null;
        List<String> lore = meta.getLore();
        java.util.Set<String> serialPrefixes = labelStarts("note.item.lore.serial");
        java.util.Set<String> currencyPrefixes = labelStarts("note.item.lore.currency");
        java.util.Set<String> amountPrefixes = labelStarts("note.item.lore.amount");
        // Legacy prefixes fallback (mixed lang / legacy bills already generated pre-v2.1)
        serialPrefixes.add("Nº serie"); serialPrefixes.add("Nº serie:");
        serialPrefixes.add("Serial"); serialPrefixes.add("Serial:"); serialPrefixes.add("Serial #:");
        serialPrefixes.add("S/N"); serialPrefixes.add("S/N:");
        serialPrefixes.add("Número de série"); serialPrefixes.add("Número de série:");
        serialPrefixes.add("Seriennummer"); serialPrefixes.add("Seriennummer:");
        serialPrefixes.add("Nº de série"); serialPrefixes.add("Nº de série:");
        serialPrefixes.add("Numer seryjny"); serialPrefixes.add("Numer seryjny:");
        serialPrefixes.add("Серийный номер"); serialPrefixes.add("Серийный номер:");
        serialPrefixes.add("序列号"); serialPrefixes.add("序號");
        serialPrefixes.add("क्रम संख्या"); serialPrefixes.add("क्रम संख्या:");
        currencyPrefixes.add("Moneda"); currencyPrefixes.add("Moneda:");
        currencyPrefixes.add("Currency"); currencyPrefixes.add("Currency:");
        currencyPrefixes.add("Moeda"); currencyPrefixes.add("Moeda:");
        currencyPrefixes.add("Devise"); currencyPrefixes.add("Devise:");
        currencyPrefixes.add("Währung"); currencyPrefixes.add("Währung:");
        currencyPrefixes.add("Valuta"); currencyPrefixes.add("Valuta:");
        currencyPrefixes.add("Waluta"); currencyPrefixes.add("Waluta:");
        currencyPrefixes.add("Валюта"); currencyPrefixes.add("Валюта:");
        currencyPrefixes.add("货币"); currencyPrefixes.add("貨幣");
        currencyPrefixes.add("मुद्रा"); currencyPrefixes.add("मुद्रा:");
        amountPrefixes.add("Importe"); amountPrefixes.add("Importe:");
        amountPrefixes.add("Amount"); amountPrefixes.add("Amount:");
        amountPrefixes.add("Valor"); amountPrefixes.add("Valor:");
        amountPrefixes.add("Montant"); amountPrefixes.add("Montant:");
        amountPrefixes.add("Betrag"); amountPrefixes.add("Betrag:");
        amountPrefixes.add("Bedrag"); amountPrefixes.add("Bedrag:");
        amountPrefixes.add("Kwota"); amountPrefixes.add("Kwota:");
        amountPrefixes.add("Сумма"); amountPrefixes.add("Сумма:");
        amountPrefixes.add("金额"); amountPrefixes.add("金額");
        amountPrefixes.add("राशि"); amountPrefixes.add("राशि:");

        String noteId = null;
        String currency = economy.getDefaultCurrencyId();
        Double amount = null;
        String sig = null;
        for (String l : lore) {
            String s = sc(l);
            if (s == null) continue;
            String sLow = s.toLowerCase(Locale.ROOT);
            if (startsWithAny(s, serialPrefixes)) {
                String val = valueAfter(s, serialPrefixes);
                if (val != null && !val.isEmpty()) noteId = val;
            } else if (startsWithAny(s, currencyPrefixes)) {
                String val = valueAfter(s, currencyPrefixes);
                if (val != null && !val.isEmpty()) currency = val;
            } else if (startsWithAny(s, amountPrefixes)) {
                String val = valueAfter(s, amountPrefixes);
                if (val != null) {
                    Double d = parseAmountLoose(val);
                    if (d != null) amount = d;
                }
            } else if (sLow.startsWith("sig:") || sLow.startsWith("hmac:")) {
                int idx = s.indexOf(':');
                if (idx > 0) sig = s.substring(idx + 1).trim();
            }
        }
        if (noteId == null || amount == null || sig == null) return null;
        String payloadShort = noteId + "|" + currency + "|" + amount;
        if (hmac(payloadShort).equals(sig)) {
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("note_id", noteId);
            out.put("amount", amount);
            out.put("currency", currency);
            out.put("fallback", Boolean.TRUE);
            return out;
        }
        return null;
    }

    public boolean redeemNote(Player player, ItemStack stack) {
        Map<String, Object> data = extractNoteData(stack);
        if (data == null) return false;
        String noteId = (String) data.get("note_id");
        double amount = (Double) data.get("amount");
        String currency = (String) data.get("currency");
        Map<String, String> ctx = new LinkedHashMap<>();
        ctx.put("amount", economy.format(currency, amount));
        ctx.put("currency", currency);
        ctx.put("txid", noteId);
        if (redeemed.containsKey(noteId)) {
            player.sendMessage(messages.chat("note.redeem.already_redeemed"));
            return false;
        }
        economy.createPlayerAccount(currency, player);
        EconomyResponse r = economy.depositPlayer(currency, player, amount, TxType.NOTE_REDEEM, "note id " + noteId);
        if (!r.transactionSuccess()) {
            ctx.put("error", String.valueOf(r.errorMessage));
            player.sendMessage(messages.formatChat("note.redeem.error", ctx));
            return false;
        }
        redeemed.put(noteId, Boolean.TRUE);
        saveRedeemed();
        if (stack.getAmount() <= 1) {
            setItemHandNull(player);
        } else {
            stack.setAmount(stack.getAmount() - 1);
        }
        player.sendMessage(messages.formatChat("note.redeem.success", ctx));
        return true;
    }

    private boolean looksLikeNote(ItemStack stack) {
        if (stack == null || stack.getType() != Material.PAPER || !stack.hasItemMeta()) return false;
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) return false;
        java.util.Set<String> dnStarts = displayNameStarts();
        if (meta.hasDisplayName()) {
            String dn = sc(meta.getDisplayName());
            if (dn != null) {
                String dnLow = dn.trim().toLowerCase(Locale.ROOT);
                for (String p : dnStarts) {
                    if (p == null || p.isEmpty()) continue;
                    String pp = p.trim().toLowerCase(Locale.ROOT);
                    if (pp.isEmpty()) continue;
                    if (dnLow.startsWith(pp)) return true;
                    if (dnLow.contains(pp)) return true;
                }
            }
        }
        java.util.List<String> tokens = loreContainsAnyTokens();
        if (meta.hasLore()) {
            for (String l : meta.getLore()) {
                String s = sc(l);
                if (s == null) continue;
                String sLow = s.trim().toLowerCase(Locale.ROOT);
                if (sLow.isEmpty()) continue;
                for (String p : dnStarts) {
                    if (p == null || p.isEmpty()) continue;
                    String pp = p.trim().toLowerCase(Locale.ROOT);
                    if (pp.isEmpty()) continue;
                    if (sLow.startsWith(pp) || sLow.contains(pp)) return true;
                }
                for (String tok : tokens) {
                    if (tok == null || tok.isEmpty()) continue;
                    if (sLow.contains(tok)) return true;
                }
            }
        }
        return false;
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        ItemStack stack = event.getItem();
        if (stack == null || stack.getType() != Material.PAPER) return;
        Map<String, Object> data = extractNoteData(stack);
        Player p = event.getPlayer();
        if (data == null) {
            if (looksLikeNote(stack)) {
                p.sendMessage(messages.chat("note.redeem.invalid"));
                event.setCancelled(true);
            }
            return;
        }
        event.setCancelled(true);
        redeemNote(p, stack);
    }
}
