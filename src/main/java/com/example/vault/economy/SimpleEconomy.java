package com.example.vault.economy;

import com.example.vault.transactions.TxRecord;
import com.example.vault.transactions.TxType;
import com.example.vault.transactions.TransactionLogService;
import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import net.milkbowl.vault.economy.EconomyResponse.ResponseType;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

<<<<<<< Updated upstream
import java.io.File;
import java.io.IOException;
=======
import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.FileInputStream;
>>>>>>> Stashed changes
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
<<<<<<< Updated upstream
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class SimpleEconomy implements Economy {
    private final Plugin plugin;

    private final ConcurrentMap<String, CurrencyDef> currencyDefs = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, CurrencyData> currencyData = new ConcurrentHashMap<>();
    private volatile String defaultCurrencyId = "default";

    private final ConcurrentMap<MultiBalanceKey, Double> pendingDatabaseWrites = new ConcurrentHashMap<>();
    private final AtomicBoolean asyncDatabaseFlushScheduled = new AtomicBoolean(false);
    private final AtomicBoolean shuttingDown = new AtomicBoolean(false);
    private com.example.vault.storage.Database database = null;
    private volatile ExecutorService databaseWriter;
    private volatile boolean asyncDatabaseWriteEnabled = true;
    private volatile long asyncDatabaseWriteDelayMs = 250L;
    private volatile Set<String> separatedWorlds = Collections.emptySet();
    private volatile TransactionLogService txLog = null;

    private static final class MultiBalanceKey {
        private final UUID uuid;
        private final String worldName;
        private final String currencyId;

        private MultiBalanceKey(UUID uuid, String worldName, String currencyId) {
            this.uuid = uuid;
            this.worldName = worldName;
            this.currencyId = currencyId;
        }

        private boolean isGlobal() { return worldName == null; }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (!(obj instanceof MultiBalanceKey other)) return false;
            return Objects.equals(uuid, other.uuid)
                    && Objects.equals(worldName, other.worldName)
                    && Objects.equals(currencyId, other.currencyId);
        }

        @Override
        public int hashCode() { return Objects.hash(uuid, worldName, currencyId); }
    }

    public SimpleEconomy(Plugin plugin) {
        this.plugin = plugin;
        loadCurrencyDefinitions();
        reloadStorageSettings();
=======
// visible storage; no hidden attributes required

public class SimpleEconomy implements Economy {
    private final Plugin plugin;
    private final Map<UUID, Double> balances = new HashMap<>();
    private final DecimalFormat formatter = new DecimalFormat("#,##0.00");
    private final java.io.File storeFile;
    private final boolean saveOnTransaction;

    public SimpleEconomy(Plugin plugin) {
        this.plugin = plugin;
        // Defaults
        org.bukkit.configuration.file.FileConfiguration cfg = plugin.getConfig();
        // Visible YAML storage at data root: balances.yml
        this.storeFile = new java.io.File(plugin.getDataFolder(), "balances.yml");
        this.saveOnTransaction = cfg.getBoolean("storage.save_on_transaction", false);
>>>>>>> Stashed changes
    }

    public void setTransactionLogService(TransactionLogService txLog) {
        this.txLog = txLog;
    }

    public TransactionLogService getTransactionLogService() { return txLog; }

    public String getDefaultCurrencyId() { return defaultCurrencyId; }

    public List<String> getCurrencyIds() {
        List<String> ids = new ArrayList<>(currencyDefs.keySet());
        ids.sort((a, b) -> {
            if (defaultCurrencyId.equals(a)) return -1;
            if (defaultCurrencyId.equals(b)) return 1;
            return a.compareTo(b);
        });
        return Collections.unmodifiableList(ids);
    }

    public CurrencyDef getCurrency(String id) {
        CurrencyDef c = currencyDefs.get(id);
        return c != null ? c : currencyDefs.get(defaultCurrencyId);
    }

    public CurrencyDef getDefaultCurrency() { return getCurrency(defaultCurrencyId); }

    private CurrencyData getData(String id) {
        CurrencyData d = currencyData.get(id);
        if (d == null) d = currencyData.get(defaultCurrencyId);
        return d;
    }

    private void loadCurrencyDefinitions() {
        // Preserve all balances in memory so we don't lose them when reloading defs.
        // Without this, /vault reload or reloadCurrencyFormat on config change nuked RAM
        // balances -> onDisable saved empty map -> balances.yml permanently wiped.
        Map<String, Map<UUID, Double>> savedBalances = new HashMap<>(currencyData.size() * 2);
        Map<String, Map<String, Map<UUID, Double>>> savedWorld = new HashMap<>(currencyData.size() * 2);
        for (Map.Entry<String, CurrencyData> e : currencyData.entrySet()) {
            savedBalances.put(e.getKey(), e.getValue().snapshotBalances());
            savedWorld.put(e.getKey(), e.getValue().snapshotWorldBalances());
        }
        String oldDefault = this.defaultCurrencyId;

        currencyDefs.clear();
        currencyData.clear();
        boolean currenciesSection = plugin.getConfig().isConfigurationSection("currencies");
        CurrencyDef first = null;
        CurrencyDef legacyDefault = null;
        if (currenciesSection) {
            ConfigurationSection sec = plugin.getConfig().getConfigurationSection("currencies");
            for (String rawId : sec.getKeys(false)) {
                String path = "currencies." + rawId;
                String id = rawId.trim().toLowerCase(Locale.ROOT);
                if (id.isEmpty()) continue;
                boolean isDefault = sec.getBoolean(path + ".default", false);
                CurrencyDef c = readCurrencyDefFromConfig(id, path, isDefault);
                if (!sec.contains(path + ".persist_to_mysql")) {
                    c = CurrencyDef.builder()
                            .id(c.id).symbol(c.symbol).position(c.position).space(c.space)
                            .singular(c.singular).plural(c.plural).numberPattern(c.numberPattern)
                            .localeTag(c.localeTag).abbreviateEnabled(c.abbreviateEnabled)
                            .abbreviateDecimals(c.abbreviateDecimals).suffixK(c.suffixK).suffixM(c.suffixM)
                            .suffixB(c.suffixB).suffixT(c.suffixT).fractionalDigits(c.fractionalDigits)
                            .defaultCurrency(isDefault).persistToMySQL(id.equals("coins") || id.equals(oldDefault) || isDefault)
                            .build();
                }
                currencyDefs.put(id, c);
                CurrencyData d = new CurrencyData(c);
                // Restore previously held balances in memory for this currency id
                Map<UUID, Double> prev = savedBalances.get(id);
                if (prev != null && !prev.isEmpty()) d.balances.putAll(prev);
                Map<String, Map<UUID, Double>> prevW = savedWorld.get(id);
                if (prevW != null && !prevW.isEmpty()) {
                    for (Map.Entry<String, Map<UUID, Double>> we : prevW.entrySet()) {
                        if (we.getKey() == null) continue;
                        Map<UUID, Double> m = we.getValue();
                        if (m == null || m.isEmpty()) continue;
                        d.worldBalances.computeIfAbsent(we.getKey(), k -> new ConcurrentHashMap<>()).putAll(m);
                    }
                }
                currencyData.put(id, d);
                if (first == null) first = c;
                if (isDefault) legacyDefault = c;
            }
        }
        boolean hasAny = !currencyDefs.isEmpty();
        if (!hasAny) {
            CurrencyDef def = readLegacyCurrencyDef();
            currencyDefs.put("default", def);
            CurrencyData d = new CurrencyData(def);
            Map<UUID, Double> prev = savedBalances.get("default");
            if (prev == null || prev.isEmpty()) prev = savedBalances.get(oldDefault);
            if (prev != null && !prev.isEmpty()) d.balances.putAll(prev);
            Map<String, Map<UUID, Double>> prevW = savedWorld.get("default");
            if (prevW == null || prevW.isEmpty()) prevW = savedWorld.get(oldDefault);
            if (prevW != null && !prevW.isEmpty()) {
                for (Map.Entry<String, Map<UUID, Double>> we : prevW.entrySet()) {
                    if (we.getKey() == null) continue;
                    Map<UUID, Double> m = we.getValue();
                    if (m == null || m.isEmpty()) continue;
                    d.worldBalances.computeIfAbsent(we.getKey(), k -> new ConcurrentHashMap<>()).putAll(m);
                }
            }
            currencyData.put("default", d);
            first = def;
            legacyDefault = def;
        }
        CurrencyDef chosen = legacyDefault != null ? legacyDefault : first;
        this.defaultCurrencyId = chosen.id;
    }

    private CurrencyDef readLegacyCurrencyDef() {
        String symbol = plugin.getConfig().getString("currency.symbol", "$");
        String position = plugin.getConfig().getString("currency.position", "suffix");
        boolean space = plugin.getConfig().getBoolean("currency.space", true);
        String format = plugin.getConfig().getString("currency.format", "auto");
        String locale = plugin.getConfig().getString("currency.locale", "auto");
        boolean abbrEnabled = plugin.getConfig().getBoolean("currency.abbreviate.enabled", false);
        int abbrDec = plugin.getConfig().getInt("currency.abbreviate.decimals", 1);
        String sk = plugin.getConfig().getString("currency.abbreviate.suffix.k", "k");
        String sm = plugin.getConfig().getString("currency.abbreviate.suffix.m", "m");
        String sb = plugin.getConfig().getString("currency.abbreviate.suffix.b", "b");
        String st = plugin.getConfig().getString("currency.abbreviate.suffix.t", "t");
        String numberPattern = ("auto".equalsIgnoreCase(format) || format == null || format.trim().isEmpty()) ? "#,##0.00" : format;
        int fracDigits = 2;
        try {
            DecimalFormat df = new DecimalFormat(numberPattern);
            fracDigits = Math.max(0, Math.min(8, df.getMaximumFractionDigits()));
        } catch (IllegalArgumentException ignored) {}
        return CurrencyDef.builder()
                .id("default")
                .symbol(symbol)
                .position(position)
                .space(space)
                .singular("dollar")
                .plural("dollars")
                .numberPattern(numberPattern)
                .localeTag(locale)
                .abbreviateEnabled(abbrEnabled)
                .abbreviateDecimals(abbrDec)
                .suffixK(sk).suffixM(sm).suffixB(sb).suffixT(st)
                .fractionalDigits(fracDigits)
                .defaultCurrency(true)
                .persistToMySQL(true)
                .build();
    }

    private CurrencyDef readCurrencyDefFromConfig(String id, String path, boolean isDefault) {
        String symbol = plugin.getConfig().getString(path + ".symbol", "");
        String position = plugin.getConfig().getString(path + ".position", "none");
        boolean space = plugin.getConfig().getBoolean(path + ".space", true);
        String singular = plugin.getConfig().getString(path + ".singular", "coin");
        String plural = plugin.getConfig().getString(path + ".plural", "coins");
        String format = plugin.getConfig().getString(path + ".format", "auto");
        String locale = plugin.getConfig().getString(path + ".locale", "auto");
        boolean abbrEnabled = plugin.getConfig().getBoolean(path + ".abbreviate.enabled", false);
        int abbrDec = plugin.getConfig().getInt(path + ".abbreviate.decimals", 1);
        String sk = plugin.getConfig().getString(path + ".abbreviate.suffix.k", "k");
        String sm = plugin.getConfig().getString(path + ".abbreviate.suffix.m", "m");
        String sb = plugin.getConfig().getString(path + ".abbreviate.suffix.b", "b");
        String st = plugin.getConfig().getString(path + ".abbreviate.suffix.t", "t");
        boolean persistMysql = plugin.getConfig().getBoolean(path + ".persist_to_mysql", isDefault);
        String numberPattern = ("auto".equalsIgnoreCase(format) || format == null || format.trim().isEmpty()) ? "#,##0.00" : format;
        int fracDigits = 2;
        try {
            DecimalFormat df = new DecimalFormat(numberPattern);
            fracDigits = Math.max(0, Math.min(8, df.getMaximumFractionDigits()));
        } catch (IllegalArgumentException ignored) {}
        return CurrencyDef.builder()
                .id(id).symbol(symbol).position(position).space(space)
                .singular(singular).plural(plural).numberPattern(numberPattern)
                .localeTag(locale).abbreviateEnabled(abbrEnabled).abbreviateDecimals(abbrDec)
                .suffixK(sk).suffixM(sm).suffixB(sb).suffixT(st).fractionalDigits(fracDigits)
                .defaultCurrency(isDefault).persistToMySQL(persistMysql)
                .build();
    }

    private double sanitizeBalance(double value) {
        return Double.isFinite(value) ? value : 0.0;
    }

    private boolean isValidTransactionAmount(double amount) {
        return Double.isFinite(amount) && amount >= 0.0;
    }

    public void reloadCurrencyFormat() {
        loadCurrencyDefinitions();
    }

    public void setDatabase(com.example.vault.storage.Database db) {
        this.database = db;
        reloadStorageSettings();
    }

    public com.example.vault.storage.Database getDatabase() { return database; }

    public CurrencyData getCurrencyData(String currencyId) {
        return getData(currencyId);
    }

    public void clearPendingDatabaseWrites() {
        pendingDatabaseWrites.clear();
    }

    @Deprecated
    public void clearAllBalances() {
        for (CurrencyData d : currencyData.values()) {
            d.balances.clear();
            d.worldBalances.clear();
        }
        pendingDatabaseWrites.clear();
    }

    @Deprecated
    public java.util.Map<java.util.UUID, Double> snapshotBalances() {
        return getData(defaultCurrencyId).snapshotBalances();
    }

    public Map<UUID, Double> snapshotBalances(String currencyId) {
        return getData(currencyId).snapshotBalances();
    }

    @Deprecated
    public java.util.Map<String, java.util.Map<java.util.UUID, Double>> snapshotWorldBalances() {
        return getData(defaultCurrencyId).snapshotWorldBalances();
    }

    public Map<String, Map<UUID, Double>> snapshotWorldBalances(String currencyId) {
        return getData(currencyId).snapshotWorldBalances();
    }

    public Map<String, Map<UUID, Double>> snapshotAllCurrenciesBalances() {
        Map<String, Map<UUID, Double>> out = new LinkedHashMap<>();
        for (Map.Entry<String, CurrencyData> e : currencyData.entrySet()) {
            out.put(e.getKey(), e.getValue().snapshotBalances());
        }
        return out;
    }

    public Map<String, Map<String, Map<UUID, Double>>> snapshotAllCurrenciesWorldBalances() {
        Map<String, Map<String, Map<UUID, Double>>> out = new LinkedHashMap<>();
        for (Map.Entry<String, CurrencyData> e : currencyData.entrySet()) {
            out.put(e.getKey(), e.getValue().snapshotWorldBalances());
        }
        return out;
    }

    @Deprecated
    public void bulkSetBalances(java.util.Map<java.util.UUID, Double> map) {
        bulkSetBalances(defaultCurrencyId, map);
    }

    public void bulkSetBalances(String currencyId, Map<UUID, Double> map) {
        CurrencyData d = getData(currencyId);
        d.balances.clear();
        if (map != null) d.balances.putAll(map);
        pendingDatabaseWrites.clear();
    }

    @Deprecated
    public void bulkSetWorldBalances(Map<String, Map<UUID, Double>> map) {
        bulkSetWorldBalances(defaultCurrencyId, map);
    }

    public void bulkSetWorldBalances(String currencyId, Map<String, Map<UUID, Double>> map) {
        CurrencyData d = getData(currencyId);
        d.worldBalances.clear();
        if (map != null) {
            for (Map.Entry<String, Map<UUID, Double>> entry : map.entrySet()) {
                String normalizedWorld = normalizeWorldName(entry.getKey());
                if (normalizedWorld == null) continue;
                ConcurrentMap<UUID, Double> byPlayer = new ConcurrentHashMap<>();
                if (entry.getValue() != null) byPlayer.putAll(entry.getValue());
                d.worldBalances.put(normalizedWorld, byPlayer);
            }
        }
        pendingDatabaseWrites.clear();
    }

    public void reloadStorageSettings() {
        asyncDatabaseWriteEnabled = plugin.getConfig().getBoolean("storage.mysql.async_save.enabled", true);
        asyncDatabaseWriteDelayMs = Math.max(0L, plugin.getConfig().getLong("storage.mysql.async_save.delay_ms", 250L));
        java.util.List<String> configuredWorlds = plugin.getConfig().getStringList("world_balances.separate_worlds");
        java.util.Set<String> normalizedWorlds = new java.util.LinkedHashSet<>();
        for (String worldName : configuredWorlds) {
            String normalized = normalizeWorldName(worldName);
            if (normalized != null) normalizedWorlds.add(normalized);
        }
        separatedWorlds = Collections.unmodifiableSet(normalizedWorlds);
        if (database != null && asyncDatabaseWriteEnabled) {
            ensureDatabaseWriter();
        } else if (databaseWriter != null && (database == null || !asyncDatabaseWriteEnabled)) {
            shutdownDatabaseWriter(false);
        }
    }

    @Override public boolean isEnabled() { return true; }
    @Override public String getName() { return "VaultEconomy"; }
    @Override public boolean hasBankSupport() { return false; }
    @Override public java.util.List<String> getBanks() { return java.util.Collections.emptyList(); }

    @Override public int fractionalDigits() { return fractionalDigits(defaultCurrencyId); }
    public int fractionalDigits(String currencyId) { return getCurrency(currencyId).fractionalDigits; }

    private String formatNumberPattern(String currencyId, double amount) {
        DecimalFormat df = getCurrency(currencyId).buildDecimalFormat();
        return df.format(amount);
    }

    @Override
    public String format(double amount) {
        return format(defaultCurrencyId, amount);
    }

    public String format(String currencyId, double amount) {
        CurrencyDef c = getCurrency(currencyId);
        return c.applySymbol(formatShortNumber(currencyId, amount, false));
    }

    @Deprecated
    public String formatShort(double amount) {
        return formatShort(defaultCurrencyId, amount);
    }

    public String formatShort(String currencyId, double amount) {
        CurrencyDef c = getCurrency(currencyId);
        return c.applySymbol(formatShortNumber(currencyId, amount, true));
    }

    private String formatShortNumber(String currencyId, double amount, boolean force) {
        CurrencyDef c = getCurrency(currencyId);
        boolean enabled = force || c.abbreviateEnabled;
        if (!enabled) return formatNumberPattern(currencyId, amount);
        int decimals = c.abbreviateDecimals;
        boolean negative = amount < 0;
        double v = Math.abs(amount);
        if (v < 1000.0) return formatNumberPattern(currencyId, amount);
        int idx = 0;
        while (v >= 1000.0 && idx < 4) {
            v /= 1000.0;
            idx++;
        }
        Locale loc = c.resolveLocale();
        DecimalFormatSymbols syms = new DecimalFormatSymbols(loc);
        if (c.localeTag != null) {
            String key = c.localeTag.trim().toLowerCase(Locale.ROOT);
            switch (key) {
                case "ch":
                    syms.setGroupingSeparator('\u2019');
                    syms.setDecimalSeparator('.');
                    break;
                case "fr":
                    syms.setGroupingSeparator('\u202F');
                    syms.setDecimalSeparator(',');
                    break;
                default: break;
            }
        }
        StringBuilder pattern = new StringBuilder("#0");
        if (decimals > 0) {
            pattern.append('.');
            for (int i = 0; i < decimals; i++) pattern.append('#');
        }
        DecimalFormat df;
        try { df = new DecimalFormat(pattern.toString(), syms); }
        catch (IllegalArgumentException ex) {
            df = new DecimalFormat("#0." + repeatChar('#', decimals), syms);
        }
        String num = df.format(v);
        if ("1000".equals(num) && idx < 4) { idx++; num = "1"; }
        String suffix = switch (idx) {
            case 1 -> c.suffixK; case 2 -> c.suffixM; case 3 -> c.suffixB; case 4 -> c.suffixT; default -> "";
        };
        return (negative ? "-" : "") + num + suffix;
    }

    @Override public String currencyNamePlural() { return getDefaultCurrency().plural; }
    public String currencyNamePlural(String currencyId) { return getCurrency(currencyId).plural; }
    @Override public String currencyNameSingular() { return getDefaultCurrency().singular; }
    public String currencyNameSingular(String currencyId) { return getCurrency(currencyId).singular; }

    @Override public boolean hasAccount(OfflinePlayer player) {
        return hasAccount(defaultCurrencyId, player);
    }
    public boolean hasAccount(String currencyId, OfflinePlayer player) {
        return getData(currencyId).balances.containsKey(player.getUniqueId());
    }

    @Override public boolean hasAccount(OfflinePlayer player, String worldName) {
        return hasAccount(defaultCurrencyId, player, worldName);
    }
    public boolean hasAccount(String currencyId, OfflinePlayer player, String worldName) {
        String normalizedWorld = normalizeSeparatedWorld(worldName);
        if (normalizedWorld == null) return hasAccount(currencyId, player);
        Map<UUID, Double> byPlayer = getData(currencyId).worldBalances.get(normalizedWorld);
        return byPlayer != null && byPlayer.containsKey(player.getUniqueId());
    }

    @Override public boolean createPlayerAccount(OfflinePlayer player) {
        return createPlayerAccount(defaultCurrencyId, player);
    }
    public boolean createPlayerAccount(String currencyId, OfflinePlayer player) {
        UUID uuid = player.getUniqueId();
        ConcurrentMap<UUID, Double> bals = getData(currencyId).balances;
        Double previous = bals.putIfAbsent(uuid, 0.0);
        if (previous == null) {
            queueDatabaseWrite(currencyId, uuid, 0.0);
            if (database == null && shouldSaveOnTransaction()) {
                try { save(); } catch (IOException ex) {
                    plugin.getLogger().warning("Failed to save after account creation: " + ex.getMessage());
                }
            }
        }
        return true;
    }

    @Override public boolean createPlayerAccount(OfflinePlayer player, String worldName) {
        return createPlayerAccount(defaultCurrencyId, player, worldName);
    }
    public boolean createPlayerAccount(String currencyId, OfflinePlayer player, String worldName) {
        String normalizedWorld = normalizeSeparatedWorld(worldName);
        if (normalizedWorld == null) return createPlayerAccount(currencyId, player);
        ConcurrentMap<UUID, Double> byPlayer = getData(currencyId).worldBalances
                .computeIfAbsent(normalizedWorld, k -> new ConcurrentHashMap<>());
        Double previous = byPlayer.putIfAbsent(player.getUniqueId(), 0.0);
        if (previous == null) {
            queueDatabaseWrite(currencyId, player.getUniqueId(), normalizedWorld, 0.0);
            if (database == null && shouldSaveOnTransaction()) {
                try { save(); } catch (IOException ex) {
                    plugin.getLogger().warning("Failed to save after world account creation: " + ex.getMessage());
                }
            }
        }
        return true;
    }

    @Override public double getBalance(OfflinePlayer player) {
        return getBalance(defaultCurrencyId, player);
    }
    public double getBalance(String currencyId, OfflinePlayer player) {
        Double v = getData(currencyId).balances.get(player.getUniqueId());
        if (v == null) return 0.0;
        double s = sanitizeBalance(v);
        if (s != v) {
            getData(currencyId).balances.put(player.getUniqueId(), s);
            queueDatabaseWrite(currencyId, player.getUniqueId(), s);
        }
        return s;
    }

    private double getWorldBalance(String currencyId, OfflinePlayer player, String normalizedWorld) {
        Map<UUID, Double> byPlayer = getData(currencyId).worldBalances.get(normalizedWorld);
        if (byPlayer == null) return 0.0;
        Double v = byPlayer.get(player.getUniqueId());
        if (v == null) return 0.0;
        double s = sanitizeBalance(v);
        if (s != v && byPlayer instanceof ConcurrentMap<?, ?> concurrentMap) {
            @SuppressWarnings("unchecked")
            ConcurrentMap<UUID, Double> typed = (ConcurrentMap<UUID, Double>) concurrentMap;
            typed.put(player.getUniqueId(), s);
            queueDatabaseWrite(currencyId, player.getUniqueId(), normalizedWorld, s);
        }
        return s;
    }

    @Override public double getBalance(OfflinePlayer player, String worldName) {
        return getBalance(defaultCurrencyId, player, worldName);
    }
    public double getBalance(String currencyId, OfflinePlayer player, String worldName) {
        String normalizedWorld = normalizeSeparatedWorld(worldName);
        if (normalizedWorld == null) return getBalance(currencyId, player);
        return getWorldBalance(currencyId, player, normalizedWorld);
    }

    @Override public boolean has(OfflinePlayer player, double amount) {
        return has(defaultCurrencyId, player, amount);
    }
    public boolean has(String currencyId, OfflinePlayer player, double amount) {
        return getBalance(currencyId, player) >= amount;
    }
    @Override public boolean has(OfflinePlayer player, String worldName, double amount) {
        return has(defaultCurrencyId, player, worldName, amount);
    }
    public boolean has(String currencyId, OfflinePlayer player, String worldName, double amount) {
        return getBalance(currencyId, player, worldName) >= amount;
    }

    private void emitTx(String currencyId, TxType type, UUID from, UUID to, double amount, String world, String extra) {
        TransactionLogService s = txLog;
        if (s == null) return;
        try {
            TxRecord.Builder b = TxRecord.builder()
                    .txType(type).currencyId(currencyId).amount(amount)
                    .fromUuid(from).toUuid(to).worldName(world);
            if (extra != null) b.putMeta("note", extra);
            s.record(b);
        } catch (Throwable ignored) {}
    }

    @Override public EconomyResponse withdrawPlayer(OfflinePlayer player, double amount) {
        return withdrawPlayer(defaultCurrencyId, player, amount, null, TxType.WITHDRAW, null);
    }
    public EconomyResponse withdrawPlayer(String currencyId, OfflinePlayer player, double amount, TxType type, String note) {
        return withdrawPlayer(currencyId, player, amount, null, type != null ? type : TxType.WITHDRAW, note);
    }

    private EconomyResponse withdrawPlayer(String currencyId, OfflinePlayer player, double amount, String worldName, TxType type, String note) {
        String normalizedWorld = normalizeSeparatedWorld(worldName);
        if (normalizedWorld == null) {
            if (!isValidTransactionAmount(amount)) {
                double bal = getBalance(currencyId, player);
                return new EconomyResponse(0.0, bal, ResponseType.FAILURE, "Invalid amount");
            }
            double bal = getBalance(currencyId, player);
            if (bal < amount) {
                return new EconomyResponse(0.0, bal, ResponseType.FAILURE, "Insufficient funds");
            }
            double newBal = sanitizeBalance(bal - amount);
            getData(currencyId).balances.put(player.getUniqueId(), newBal);
            persistBalanceChange(currencyId, player.getUniqueId(), newBal, "withdraw");
            emitTx(currencyId, type, player.getUniqueId(), null, amount, null, note);
            return new EconomyResponse(amount, newBal, ResponseType.SUCCESS, "");
        }
        return withdrawPlayer(currencyId, player, worldName, amount);
    }

    @Override public EconomyResponse withdrawPlayer(OfflinePlayer player, String worldName, double amount) {
        return withdrawPlayer(defaultCurrencyId, player, worldName, amount);
    }
    public EconomyResponse withdrawPlayer(String currencyId, OfflinePlayer player, String worldName, double amount) {
        String normalizedWorld = normalizeSeparatedWorld(worldName);
        if (normalizedWorld == null) return withdrawPlayer(currencyId, player, amount, TxType.WITHDRAW, null);
        if (!isValidTransactionAmount(amount)) {
            double bal = getBalance(currencyId, player, normalizedWorld);
            return new EconomyResponse(0.0, bal, ResponseType.FAILURE, "Invalid amount");
        }
        createPlayerAccount(currencyId, player, normalizedWorld);
        double bal = getWorldBalance(currencyId, player, normalizedWorld);
        if (bal < amount) {
            return new EconomyResponse(0.0, bal, ResponseType.FAILURE, "Insufficient funds");
        }
<<<<<<< Updated upstream
        double newBal = sanitizeBalance(bal - amount);
        getData(currencyId).worldBalances
                .computeIfAbsent(normalizedWorld, k -> new ConcurrentHashMap<>())
                .put(player.getUniqueId(), newBal);
        persistBalanceChange(currencyId, player.getUniqueId(), normalizedWorld, newBal, "withdraw");
        emitTx(currencyId, TxType.WITHDRAW, player.getUniqueId(), null, amount, normalizedWorld, null);
        return new EconomyResponse(amount, newBal, ResponseType.SUCCESS, "");
    }

    @Override public EconomyResponse depositPlayer(OfflinePlayer player, double amount) {
        return depositPlayer(defaultCurrencyId, player, amount, TxType.DEPOSIT, null);
    }
    public EconomyResponse depositPlayer(String currencyId, OfflinePlayer player, double amount, TxType type, String note) {
        if (!isValidTransactionAmount(amount)) {
            double bal = getBalance(currencyId, player);
            return new EconomyResponse(0.0, bal, ResponseType.FAILURE, "Invalid amount");
        }
        double bal = getBalance(currencyId, player);
        double newBal = sanitizeBalance(bal + amount);
        getData(currencyId).balances.put(player.getUniqueId(), newBal);
        persistBalanceChange(currencyId, player.getUniqueId(), newBal, "deposit");
        emitTx(currencyId, type != null ? type : TxType.DEPOSIT, null, player.getUniqueId(), amount, null, note);
        return new EconomyResponse(amount, newBal, ResponseType.SUCCESS, "");
=======
        balances.put(player.getUniqueId(), bal - amount);
        if (saveOnTransaction) saveQuietly(player.getUniqueId());
        return new EconomyResponse(amount, bal - amount, ResponseType.SUCCESS, "");
    }

    @Override
    public EconomyResponse depositPlayer(OfflinePlayer player, double amount) {
        double bal = getBalance(player);
        balances.put(player.getUniqueId(), bal + amount);
        if (saveOnTransaction) saveQuietly(player.getUniqueId());
        return new EconomyResponse(amount, bal + amount, ResponseType.SUCCESS, "");
>>>>>>> Stashed changes
    }

    @Override public EconomyResponse depositPlayer(OfflinePlayer player, String worldName, double amount) {
        return depositPlayer(defaultCurrencyId, player, worldName, amount);
    }
    public EconomyResponse depositPlayer(String currencyId, OfflinePlayer player, String worldName, double amount) {
        String normalizedWorld = normalizeSeparatedWorld(worldName);
        if (normalizedWorld == null) return depositPlayer(currencyId, player, amount, TxType.DEPOSIT, null);
        if (!isValidTransactionAmount(amount)) {
            double bal = getBalance(currencyId, player, normalizedWorld);
            return new EconomyResponse(0.0, bal, ResponseType.FAILURE, "Invalid amount");
        }
        createPlayerAccount(currencyId, player, normalizedWorld);
        double bal = getWorldBalance(currencyId, player, normalizedWorld);
        double newBal = sanitizeBalance(bal + amount);
        getData(currencyId).worldBalances
                .computeIfAbsent(normalizedWorld, k -> new ConcurrentHashMap<>())
                .put(player.getUniqueId(), newBal);
        persistBalanceChange(currencyId, player.getUniqueId(), normalizedWorld, newBal, "deposit");
        emitTx(currencyId, TxType.DEPOSIT, null, player.getUniqueId(), amount, normalizedWorld, null);
        return new EconomyResponse(amount, newBal, ResponseType.SUCCESS, "");
    }

    public EconomyResponse transfer(String currencyId, OfflinePlayer from, OfflinePlayer to, double amount, TxType type, String note) {
        if (!isValidTransactionAmount(amount)) {
            double bal = getBalance(currencyId, from);
            return new EconomyResponse(0.0, bal, ResponseType.FAILURE, "Invalid amount");
        }
        double fromBal = getBalance(currencyId, from);
        if (fromBal < amount) {
            return new EconomyResponse(0.0, fromBal, ResponseType.FAILURE, "Insufficient funds");
        }
        createPlayerAccount(currencyId, to);
        double newFromBal = sanitizeBalance(fromBal - amount);
        double toBal = getBalance(currencyId, to);
        double newToBal = sanitizeBalance(toBal + amount);
        getData(currencyId).balances.put(from.getUniqueId(), newFromBal);
        getData(currencyId).balances.put(to.getUniqueId(), newToBal);
        persistBalanceChange(currencyId, from.getUniqueId(), newFromBal, "withdraw");
        persistBalanceChange(currencyId, to.getUniqueId(), newToBal, "deposit");
        emitTx(currencyId, type != null ? type : TxType.PLAYER_PAY, from.getUniqueId(), to.getUniqueId(), amount, null, note);
        return new EconomyResponse(amount, newFromBal, ResponseType.SUCCESS, "");
    }

    @Deprecated
    public void setBalance(OfflinePlayer player, double amount) {
        setBalance(defaultCurrencyId, player, amount);
    }

    public void setBalance(String currencyId, OfflinePlayer player, double amount) {
        double sanitized = sanitizeBalance(amount);
        getData(currencyId).balances.put(player.getUniqueId(), sanitized);
        queueDatabaseWrite(currencyId, player.getUniqueId(), sanitized);
    }

    @Override
    public boolean hasAccount(String playerName) { return false; }
    @Override
    public boolean createPlayerAccount(String playerName) {
        OfflinePlayer p = com.example.vault.util.PlayerResolver.resolveByNameWithOfflineFallback(plugin, playerName);
        if (p == null) return false;
        return createPlayerAccount(p);
    }
    @Override
    public boolean createPlayerAccount(String playerName, String worldName) {
        OfflinePlayer p = com.example.vault.util.PlayerResolver.resolveByNameWithOfflineFallback(plugin, playerName);
        if (p == null) return false;
        return createPlayerAccount(p, worldName);
    }
    @Override public double getBalance(String playerName) { return 0; }
    @Override public boolean has(String playerName, double amount) { return false; }
    @Override public EconomyResponse withdrawPlayer(String playerName, double amount) { return new EconomyResponse(0,0,ResponseType.NOT_IMPLEMENTED,"Not implemented"); }
    @Override public EconomyResponse depositPlayer(String playerName, double amount) { return new EconomyResponse(0,0,ResponseType.NOT_IMPLEMENTED,"Not implemented"); }

    @Override public boolean hasAccount(String playerName, String worldName) {
        OfflinePlayer p = com.example.vault.util.PlayerResolver.resolveByNameWithOfflineFallback(plugin, playerName);
        return p != null && hasAccount(p, worldName);
    }
    @Override public double getBalance(String playerName, String worldName) {
        OfflinePlayer p = com.example.vault.util.PlayerResolver.resolveByNameWithOfflineFallback(plugin, playerName);
        return p != null ? getBalance(p, worldName) : 0.0;
    }
    @Override public boolean has(String playerName, String worldName, double amount) {
        OfflinePlayer p = com.example.vault.util.PlayerResolver.resolveByNameWithOfflineFallback(plugin, playerName);
        return p != null && has(p, worldName, amount);
    }
    @Override public EconomyResponse withdrawPlayer(String playerName, String worldName, double amount) {
        OfflinePlayer p = com.example.vault.util.PlayerResolver.resolveByNameWithOfflineFallback(plugin, playerName);
        if (p == null) return new EconomyResponse(0,0,ResponseType.FAILURE,"Player not found");
        return withdrawPlayer(p, worldName, amount);
    }
    @Override public EconomyResponse depositPlayer(String playerName, String worldName, double amount) {
        OfflinePlayer p = com.example.vault.util.PlayerResolver.resolveByNameWithOfflineFallback(plugin, playerName);
        if (p == null) return new EconomyResponse(0,0,ResponseType.FAILURE,"Player not found");
        return depositPlayer(p, worldName, amount);
    }

    @Override public EconomyResponse bankBalance(String bank) { return new EconomyResponse(0,0,ResponseType.NOT_IMPLEMENTED,"Not implemented"); }
    @Override public EconomyResponse bankHas(String bank, double amount) { return new EconomyResponse(0,0,ResponseType.NOT_IMPLEMENTED,"Not implemented"); }
    @Override public EconomyResponse bankWithdraw(String bank, double amount) { return new EconomyResponse(0,0,ResponseType.NOT_IMPLEMENTED,"Not implemented"); }
    @Override public EconomyResponse bankDeposit(String bank, double amount) { return new EconomyResponse(0,0,ResponseType.NOT_IMPLEMENTED,"Not implemented"); }
    @Override public EconomyResponse isBankOwner(String bank, String playerName) { return new EconomyResponse(0,0,ResponseType.NOT_IMPLEMENTED,"Not implemented"); }
    @Override public EconomyResponse isBankMember(String bank, String playerName) { return new EconomyResponse(0,0,ResponseType.NOT_IMPLEMENTED,"Not implemented"); }
    @Override public net.milkbowl.vault.economy.EconomyResponse createBank(String bank, String player) {
        return new net.milkbowl.vault.economy.EconomyResponse(0, 0, net.milkbowl.vault.economy.EconomyResponse.ResponseType.NOT_IMPLEMENTED, "Not implemented");
    }
    @Override public net.milkbowl.vault.economy.EconomyResponse createBank(String bank, org.bukkit.OfflinePlayer player) {
        return new net.milkbowl.vault.economy.EconomyResponse(0, 0, net.milkbowl.vault.economy.EconomyResponse.ResponseType.NOT_IMPLEMENTED, "Not implemented");
    }
    @Override public net.milkbowl.vault.economy.EconomyResponse deleteBank(String bank) {
        return new net.milkbowl.vault.economy.EconomyResponse(0, 0, net.milkbowl.vault.economy.EconomyResponse.ResponseType.NOT_IMPLEMENTED, "Not implemented");
    }
    public EconomyResponse bankWithdraw(String bank, org.bukkit.OfflinePlayer player, double amount) { return new net.milkbowl.vault.economy.EconomyResponse(0,0,net.milkbowl.vault.economy.EconomyResponse.ResponseType.NOT_IMPLEMENTED,"Not implemented"); }
    public EconomyResponse bankDeposit(String bank, org.bukkit.OfflinePlayer player, double amount) { return new net.milkbowl.vault.economy.EconomyResponse(0,0,net.milkbowl.vault.economy.EconomyResponse.ResponseType.NOT_IMPLEMENTED,"Not implemented"); }
    public EconomyResponse isBankOwner(String bank, org.bukkit.OfflinePlayer player) { return new net.milkbowl.vault.economy.EconomyResponse(0,0,net.milkbowl.vault.economy.EconomyResponse.ResponseType.NOT_IMPLEMENTED,"Not implemented"); }
    public EconomyResponse isBankMember(String bank, org.bukkit.OfflinePlayer player) { return new net.milkbowl.vault.economy.EconomyResponse(0,0,net.milkbowl.vault.economy.EconomyResponse.ResponseType.NOT_IMPLEMENTED,"Not implemented"); }

<<<<<<< Updated upstream
    public void load() {
        if (plugin.getConfig().getBoolean("storage.use_mysql", false)) {
            return;
        }
        try {
            File file = new File(plugin.getDataFolder(), "balances.yml");
            if (!file.exists()) return;
            YamlConfiguration cfg = YamlConfiguration.loadConfiguration(file);

            boolean hasCurrenciesSection = cfg.isConfigurationSection("currencies");
            boolean hasFlatSection = cfg.isConfigurationSection("balances");

            if (hasCurrenciesSection) {
                ConfigurationSection currenciesSec = cfg.getConfigurationSection("currencies");
                for (String cid : currenciesSec.getKeys(false)) {
                    String currencyId = cid.trim().toLowerCase(Locale.ROOT);
                    if (!currencyData.containsKey(currencyId)) continue;
                    CurrencyData d = currencyData.get(currencyId);
                    ConfigurationSection balSec = currenciesSec.getConfigurationSection(cid + ".balances");
                    if (balSec != null) {
                        for (String key : balSec.getKeys(false)) {
                            try {
                                UUID uuid = UUID.fromString(key);
                                double amount = sanitizeBalance(balSec.getDouble(key, 0.0));
                                d.balances.put(uuid, amount);
                            } catch (IllegalArgumentException ignored) {}
                        }
                    }
                    ConfigurationSection wSec = currenciesSec.getConfigurationSection(cid + ".world_balances");
                    if (wSec != null) {
                        for (String w : wSec.getKeys(false)) {
                            String normalizedWorld = normalizeWorldName(w);
                            if (normalizedWorld == null) continue;
                            ConfigurationSection entries = wSec.getConfigurationSection(w);
                            if (entries == null) continue;
                            ConcurrentMap<UUID, Double> byPlayer = d.worldBalances.computeIfAbsent(normalizedWorld, k -> new ConcurrentHashMap<>());
                            for (String key : entries.getKeys(false)) {
                                try {
                                    UUID uuid = UUID.fromString(key);
                                    double amount = sanitizeBalance(entries.getDouble(key, 0.0));
                                    byPlayer.put(uuid, amount);
                                } catch (IllegalArgumentException ignored) {}
                            }
                        }
                    }
                }
            }

            if (hasFlatSection) {
                CurrencyData d = currencyData.get(defaultCurrencyId);
                ConfigurationSection sec = cfg.getConfigurationSection("balances");
                if (sec != null) {
                    for (String key : sec.getKeys(false)) {
                        try {
                            UUID uuid = UUID.fromString(key);
                            double amount = sanitizeBalance(sec.getDouble(key, 0.0));
                            d.balances.putIfAbsent(uuid, amount);
                        } catch (IllegalArgumentException ignored) {}
                    }
                }
                ConfigurationSection worldSec = cfg.getConfigurationSection("world_balances");
                if (worldSec != null) {
                    for (String worldName : worldSec.getKeys(false)) {
                        String normalizedWorld = normalizeWorldName(worldName);
                        if (normalizedWorld == null) continue;
                        ConfigurationSection entries = worldSec.getConfigurationSection(worldName);
                        if (entries == null) continue;
                        ConcurrentMap<UUID, Double> byPlayer = d.worldBalances.computeIfAbsent(normalizedWorld, k -> new ConcurrentHashMap<>());
                        for (String key : entries.getKeys(false)) {
                            try {
                                UUID uuid = UUID.fromString(key);
                                double amount = sanitizeBalance(entries.getDouble(key, 0.0));
                                byPlayer.putIfAbsent(uuid, amount);
                            } catch (IllegalArgumentException ignored) {}
                        }
                    }
                }
            }
        } catch (Exception ex) {
            plugin.getLogger().warning("Failed to load balances.yml: " + ex.getMessage());
        }
    }

    public void save() throws IOException {
        if (database != null) {
            try { flushPendingDatabaseWrites(); }
            catch (java.sql.SQLException ex) {
                plugin.getLogger().warning("Failed to save balances to MySQL: " + ex.getMessage());
            }
            return;
        }
        saveToFileSnapshot(snapshotAllCurrenciesBalances(), snapshotAllCurrenciesWorldBalances());
    }

    public void saveAllNow() throws IOException {
        if (database != null) {
            shuttingDown.set(true);
            shutdownDatabaseWriter(true);
            try {
                CurrencyData d = currencyData.get(defaultCurrencyId);
                Map<UUID, Double> snapshot = d.snapshotBalances();
                Map<String, Map<UUID, Double>> worldSnapshot = d.snapshotWorldBalances();
                database.saveAllBalances(snapshot);
                database.saveAllWorldBalances(worldSnapshot);
                pendingDatabaseWrites.clear();
            } catch (java.sql.SQLException ex) {
                plugin.getLogger().warning("Failed to save balances to MySQL: " + ex.getMessage());
            }
            return;
        }
        saveToFileSnapshot(snapshotAllCurrenciesBalances(), snapshotAllCurrenciesWorldBalances());
    }

    private void saveToFileSnapshot(Map<String, Map<UUID, Double>> allCurrencies,
                                     Map<String, Map<String, Map<UUID, Double>>> allWorldBalances) throws IOException {
        File file = new File(plugin.getDataFolder(), "balances.yml");
        YamlConfiguration cfg = new YamlConfiguration();

        CurrencyData defData = currencyData.get(defaultCurrencyId);
        if (defData != null) {
            Map<UUID, Double> flat = allCurrencies.get(defaultCurrencyId);
            if (flat == null) flat = defData.snapshotBalances();
            for (Map.Entry<UUID, Double> e : flat.entrySet()) {
                cfg.set("balances." + e.getKey().toString(), e.getValue());
            }
            Map<String, Map<UUID, Double>> worldFlat = allWorldBalances.get(defaultCurrencyId);
            if (worldFlat == null) worldFlat = defData.snapshotWorldBalances();
            for (Map.Entry<String, Map<UUID, Double>> worldEntry : worldFlat.entrySet()) {
                String worldName = worldEntry.getKey();
                if (worldName == null || worldName.isEmpty()) continue;
                for (Map.Entry<UUID, Double> balanceEntry : worldEntry.getValue().entrySet()) {
                    cfg.set("world_balances." + worldName + "." + balanceEntry.getKey(), balanceEntry.getValue());
                }
            }
        }

        for (Map.Entry<String, CurrencyData> e : currencyData.entrySet()) {
            String cid = e.getKey();
            Map<UUID, Double> bals = allCurrencies.get(cid);
            if (bals == null) bals = e.getValue().snapshotBalances();
            for (Map.Entry<UUID, Double> b : bals.entrySet()) {
                cfg.set("currencies." + cid + ".balances." + b.getKey().toString(), b.getValue());
            }
            Map<String, Map<UUID, Double>> wb = allWorldBalances.get(cid);
            if (wb == null) wb = e.getValue().snapshotWorldBalances();
            for (Map.Entry<String, Map<UUID, Double>> w : wb.entrySet()) {
                String worldName = w.getKey();
                if (worldName == null || worldName.isEmpty()) continue;
                for (Map.Entry<UUID, Double> be : w.getValue().entrySet()) {
                    cfg.set("currencies." + cid + ".world_balances." + worldName + "." + be.getKey(), be.getValue());
                }
            }
        }

        cfg.save(file);
    }

    public void saveToFile() throws IOException {
        saveToFileSnapshot(snapshotAllCurrenciesBalances(), snapshotAllCurrenciesWorldBalances());
    }

    public void close() {
        shuttingDown.set(true);
        shutdownDatabaseWriter(true);
        if (database != null) {
            database.close();
        }
    }

    private boolean shouldSaveOnTransaction() {
        return plugin.getConfig().getBoolean("storage.save_on_transaction", true);
    }

    private void persistBalanceChange(String currencyId, UUID uuid, double balance, String operation) {
        persistBalanceChange(currencyId, uuid, null, balance, operation);
    }

    private void persistBalanceChange(String currencyId, UUID uuid, String worldName, double balance, String operation) {
        queueDatabaseWrite(currencyId, uuid, worldName, balance);
        if (database != null) {
            if (shouldSaveOnTransaction()) {
                if (asyncDatabaseWriteEnabled) {
                    scheduleAsyncDatabaseFlush();
                } else {
                    try { flushPendingDatabaseWrites(); }
                    catch (java.sql.SQLException ex) {
                        plugin.getLogger().warning("Failed to save balances to MySQL after " + operation + ": " + ex.getMessage());
                    }
                }
            }
            return;
        }
        if (shouldSaveOnTransaction()) {
            try { save(); }
            catch (IOException ex) {
                plugin.getLogger().warning("Failed to save after " + operation + ": " + ex.getMessage());
            }
        }
    }

    private void queueDatabaseWrite(String currencyId, UUID uuid, double balance) {
        queueDatabaseWrite(currencyId, uuid, null, balance);
    }

    private void queueDatabaseWrite(String currencyId, UUID uuid, String worldName, double balance) {
        if (database == null || uuid == null || currencyId == null) return;
        CurrencyDef c = currencyDefs.get(currencyId);
        if (c == null || !c.persistToMySQL) return;
        pendingDatabaseWrites.put(new MultiBalanceKey(uuid, normalizeWorldName(worldName), currencyId), sanitizeBalance(balance));
    }

    private void scheduleAsyncDatabaseFlush() {
        if (database == null || !asyncDatabaseWriteEnabled || shuttingDown.get()) return;
        ensureDatabaseWriter();
        if (!asyncDatabaseFlushScheduled.compareAndSet(false, true)) return;
        databaseWriter.execute(() -> {
            try {
                if (asyncDatabaseWriteDelayMs > 0L) {
                    try { Thread.sleep(asyncDatabaseWriteDelayMs); }
                    catch (InterruptedException ex) { Thread.currentThread().interrupt(); }
                }
                flushPendingDatabaseWrites();
            } catch (java.sql.SQLException ex) {
                plugin.getLogger().warning("Async MySQL balance save failed: " + ex.getMessage());
            } finally {
                asyncDatabaseFlushScheduled.set(false);
                if (!pendingDatabaseWrites.isEmpty() && !shuttingDown.get()) scheduleAsyncDatabaseFlush();
            }
        });
    }

    private void flushPendingDatabaseWrites() throws java.sql.SQLException {
        if (database == null) return;
        Map<MultiBalanceKey, Double> batch = drainPendingDatabaseWrites();
        if (batch.isEmpty()) return;
        Map<UUID, Double> globalBatch = new HashMap<>();
        Map<String, Map<UUID, Double>> worldBatch = new HashMap<>();
        for (Map.Entry<MultiBalanceKey, Double> entry : batch.entrySet()) {
            MultiBalanceKey key = entry.getKey();
            if (key == null || key.uuid == null) continue;
            if (!defaultCurrencyId.equals(key.currencyId)) continue;
            if (key.isGlobal()) {
                globalBatch.put(key.uuid, entry.getValue());
                continue;
            }
            worldBatch.computeIfAbsent(key.worldName, ignored -> new HashMap<>()).put(key.uuid, entry.getValue());
        }
        try {
            database.saveBalances(globalBatch);
            database.saveWorldBalances(worldBatch);
        } catch (java.sql.SQLException ex) {
            pendingDatabaseWrites.putAll(batch);
            throw ex;
        }
    }

    private Map<MultiBalanceKey, Double> drainPendingDatabaseWrites() {
        Map<MultiBalanceKey, Double> drained = new HashMap<>();
        for (Map.Entry<MultiBalanceKey, Double> entry : pendingDatabaseWrites.entrySet()) {
            MultiBalanceKey key = entry.getKey();
            Double value = entry.getValue();
            if (key == null || key.uuid == null || value == null) continue;
            double sanitized = sanitizeBalance(value);
            if (pendingDatabaseWrites.remove(key, value)) drained.put(key, sanitized);
        }
        return drained;
    }

    private String normalizeWorldName(String worldName) {
        if (worldName == null) return null;
        String trimmed = worldName.trim();
        if (trimmed.isEmpty()) return null;
        return trimmed.toLowerCase(Locale.ROOT);
    }

    private String normalizeSeparatedWorld(String worldName) {
        String normalized = normalizeWorldName(worldName);
        if (normalized == null) return null;
        return separatedWorlds.contains(normalized) ? normalized : null;
    }

    public boolean isSeparatedWorld(String worldName) {
        return normalizeSeparatedWorld(worldName) != null;
    }

    private void ensureDatabaseWriter() {
        ExecutorService current = databaseWriter;
        if (current != null && !current.isShutdown() && !current.isTerminated()) return;
        synchronized (this) {
            current = databaseWriter;
            if (current != null && !current.isShutdown() && !current.isTerminated()) return;
            databaseWriter = Executors.newSingleThreadExecutor(r -> {
                Thread thread = new Thread(r, "vault-mysql-save");
                thread.setDaemon(true);
                return thread;
            });
        }
    }

    private void shutdownDatabaseWriter(boolean waitForCompletion) {
        ExecutorService current = databaseWriter;
        databaseWriter = null;
        if (current == null) return;
        current.shutdown();
        if (!waitForCompletion) return;
        try {
            if (!current.awaitTermination(5, TimeUnit.SECONDS)) current.shutdownNow();
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            current.shutdownNow();
        }
    }

    private static String repeatChar(char c, int times) {
        if (times <= 0) return "";
        char[] arr = new char[times];
        java.util.Arrays.fill(arr, c);
        return new String(arr);
    }
}
=======
    // Persistence API
    public void load() {
        // Load YAML from root if present
        if (storeFile.exists()) {
            org.bukkit.configuration.file.FileConfiguration cfg = org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(storeFile);
            for (String key : cfg.getKeys(false)) {
                try {
                    java.util.UUID uuid = java.util.UUID.fromString(key);
                    double value = cfg.getDouble(key, 0.0);
                    balances.put(uuid, value);
                } catch (IllegalArgumentException ignored) {
                }
            }
            return;
        }

        // Migration: from .internal/balances.dat (binary) or balances/balances.yml (YAML)
        java.io.File internalFile = new java.io.File(new java.io.File(plugin.getDataFolder(), ".internal"), "balances.dat");
        java.io.File oldSubFile = new java.io.File(new java.io.File(plugin.getDataFolder(), "balances"), "balances.yml");
        boolean migrated = false;
        if (internalFile.exists()) {
            try (java.io.DataInputStream in = new java.io.DataInputStream(new java.io.BufferedInputStream(new java.io.FileInputStream(internalFile)))) {
                int count = in.readInt();
                for (int i = 0; i < count; i++) {
                    long msb = in.readLong();
                    long lsb = in.readLong();
                    double value = in.readDouble();
                    balances.put(new java.util.UUID(msb, lsb), value);
                }
                migrated = true;
            } catch (Exception ex) {
                plugin.getLogger().warning("Failed to migrate from internal store: " + ex.getMessage());
            }
        } else if (oldSubFile.exists()) {
            org.bukkit.configuration.file.FileConfiguration cfg = org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(oldSubFile);
            for (String key : cfg.getKeys(false)) {
                try {
                    java.util.UUID uuid = java.util.UUID.fromString(key);
                    double value = cfg.getDouble(key, 0.0);
                    balances.put(uuid, value);
                } catch (IllegalArgumentException ignored) {
                }
            }
            migrated = true;
        }

        if (migrated) {
            try {
                save();
                if (internalFile.exists() && !internalFile.delete()) {
                    plugin.getLogger().info("Kept .internal/balances.dat (could not delete). Data migrated to balances.yml.");
                }
                if (oldSubFile.exists() && !oldSubFile.delete()) {
                    plugin.getLogger().info("Kept balances/balances.yml (could not delete). Data migrated to balances.yml.");
                }
            } catch (Exception ex) {
                plugin.getLogger().warning("Failed to write migrated balances to balances.yml: " + ex.getMessage());
            }
        }
    }

    public void save() throws java.io.IOException {
        org.bukkit.configuration.file.YamlConfiguration cfg = new org.bukkit.configuration.file.YamlConfiguration();
        for (java.util.Map.Entry<java.util.UUID, Double> e : balances.entrySet()) {
            cfg.set(e.getKey().toString(), e.getValue());
        }
        cfg.save(storeFile);
    }

    private void saveQuietly(java.util.UUID changedUuid) {
        try {
            save();
        } catch (Exception ex) {
            plugin.getLogger().warning("Failed to save balances: " + ex.getMessage());
        }
    }

    public void close() {
        // No-op: SQL support removido
    }
}
>>>>>>> Stashed changes
