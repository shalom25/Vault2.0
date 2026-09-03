package com.example.vault;

import com.example.vault.commands.BalanceCommand;
import com.example.vault.commands.EcoCommand;
import com.example.vault.commands.PayCommand;
import com.example.vault.commands.VaultCommand;
import com.example.vault.commands.VaultOpCommand;
import com.example.vault.economy.BankService;
import com.example.vault.economy.OfflinePayQueueService;
import com.example.vault.economy.PhysicalNoteService;
import com.example.vault.economy.SimpleEconomy;
import com.example.vault.economy.TopCacheService;
import com.example.vault.notifications.DiscordWebhookNotifier;
import com.example.vault.loans.LoanService;
import com.example.vault.loans.YamlLoanStorage;
import com.example.vault.menu.HistoryMenuService;
import com.example.vault.menu.PayMenuService;
import com.example.vault.i18n.Messages;
import com.example.vault.menu.ChargeRequestService;
import com.example.vault.menu.ConfigEditorService;
import com.example.vault.menu.VaultMenuService;
import com.example.vault.transactions.TransactionLogService;
import com.example.vault.util.ColorUtil;
import com.example.vault.util.UpdateChecker;
import org.bstats.bukkit.Metrics;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.event.Listener;
import org.bukkit.event.EventHandler;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.entity.Player;
import org.bukkit.command.CommandSender;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;

import net.byteflux.libby.BukkitLibraryManager;
import net.byteflux.libby.Library;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

public class VaultPlugin extends JavaPlugin implements Listener {
    private Economy economy;
    private PayMenuService payMenuService;
    private Messages messages;
    private LoanService loanService;
    private VaultMenuService vaultMenuService;
    private ConfigEditorService configEditorService;
    private TransactionLogService txLog;
    private TopCacheService topCache;
    private OfflinePayQueueService offlinePay;
    private PhysicalNoteService noteService;
    private BankService bankService;
    private HistoryMenuService historyMenu;
    private DiscordWebhookNotifier discord;
    private org.bukkit.scheduler.BukkitTask autosaveTask;
    private org.bukkit.scheduler.BukkitTask updateCheckTask;
    private volatile boolean updateAvailable = false;
    private volatile String remoteVersion = null;
    private volatile String lastAnnouncedVersion = null;
    private volatile long lastOnJoinUpdateCheckMs = 0L;
    private static final long ON_JOIN_CHECK_COOLDOWN_MS = 5 * 60 * 1000L; // 5 minutos
    private static final String UPDATE_LINK = "https://modrinth.com/plugin/vault-2.0-economy-plugins";
    private static final String MODRINTH_PROJECT_ID = "rj9SgaYL";

    public Economy getEconomyProvider() {
        return economy;
    }

    public TransactionLogService getTxLog() {
        return txLog;
    }

    public Messages getMessages() { return messages; }
    public BankService getBankService() { return bankService; }
    public TopCacheService getTopCache() { return topCache; }

    @Override
    public void onEnable() {
        // Ensure data folder
        if (!getDataFolder().exists()) {
            getDataFolder().mkdirs();
        }
        // Recreate missing config.yml or merge in any new default keys.
        syncConfigFile();

        // Load runtime dependencies (HikariCP, MySQL) if enabled
        if (getConfig().getBoolean("storage.use_mysql", false)) {
            loadDependencies();
        }

        // Load messages based on config language
        String lang = getConfig().getString("language", "en");
        messages = new Messages(this, lang);

        boolean useMySQL = getConfig().getBoolean("storage.use_mysql", false);
        com.example.vault.storage.Database db = null;

        // Create our internal Economy provider and register it in ServicesManager
        SimpleEconomy provider = new SimpleEconomy(this);
        if (useMySQL) {
            try {
                db = new com.example.vault.storage.Database(this);
                db.ensureSchema();
                java.util.Map<java.util.UUID, Double> loaded = db.loadAllBalances();
                java.util.Map<String, java.util.Map<java.util.UUID, Double>> worldLoaded = db.loadAllWorldBalances();
                String defId = provider.getDefaultCurrencyId();
                provider.bulkSetBalances(defId, loaded);
                provider.bulkSetWorldBalances(defId, worldLoaded);
                provider.setDatabase(db);
                getLogger().info("Loaded " + loaded.size() + " global balances and " + worldLoaded.size() + " world balance groups from MySQL.");
            } catch (java.sql.SQLException ex) {
                getLogger().severe("Failed to initialize MySQL storage: " + ex.getMessage());
                // Fallback a archivo si falla
                provider.load();
            }
        } else {
            // Load persisted balances (file)
            provider.load();
        }

        // Importar saldos de Essentials según config (una sola vez)
        if (getConfig().getBoolean("import.essentials.enabled", false)) {
            boolean replace = getConfig().getBoolean("import.essentials.replace", false);
            int mode = replace ? 1 : 0; // 0=merge, 1=replace
            new com.example.vault.importer.EssentialsImportService(this, provider).runOnce(mode);
        }
        txLog = new TransactionLogService(this, db);
        provider.setTransactionLogService(txLog);

        // --- NUEVOS SERVICIOS v2.1 ---
        topCache = new TopCacheService(this, provider);
        topCache.start(getConfig().getLong("top.refresh_seconds", 300L));
        offlinePay = new OfflinePayQueueService(this, provider);
        noteService = new PhysicalNoteService(this, provider, messages);
        bankService = new BankService(this, provider);
        bankService.start();
        historyMenu = new HistoryMenuService(this, messages);
        discord = new DiscordWebhookNotifier(this, provider, messages);
        txLog.addFlushListener(discord::onTransactionBatch);
        txLog.addDupeListener(discord::onDupeDetected);
        getServer().getPluginManager().registerEvents(offlinePay, this);
        getServer().getPluginManager().registerEvents(noteService, this);
        getServer().getPluginManager().registerEvents(historyMenu, this);

        this.economy = provider;
        getServer().getServicesManager().register(Economy.class, provider, this, ServicePriority.Highest);
        // NO llamar reloadCurrencyFormat aquí: constructor ya ejecutó loadCurrencyDefinitions() y
        // load() acaba de poblar currencyData.balances desde disco.
        // Un segundo loadCurrencyDefinitions crearía CurrencyData NUEVOS VACÍOS y borraría todo.

        // Schedule autosave
        scheduleAutosave(provider);

        // Register PlaceholderAPI expansion if plugin is present
        if (getServer().getPluginManager().getPlugin("PlaceholderAPI") != null) {
            new com.example.vault.placeholder.VaultPlaceholderExpansion(this, economy, messages).register();
            new com.example.vault.placeholder.Vault2PlaceholderExpansion(this, economy, messages).register();
            getLogger().info("PlaceholderAPI expansions registered: %vault_*% and %vault2_*%.");
        }

        loanService = new LoanService(this, economy, messages, new YamlLoanStorage(this));
        getServer().getPluginManager().registerEvents(loanService, this);
        loanService.start();

        vaultMenuService = new VaultMenuService(this, messages, loanService, bankService);
        getServer().getPluginManager().registerEvents(vaultMenuService, this);
        configEditorService = new ConfigEditorService(this, messages);
        getServer().getPluginManager().registerEvents(configEditorService, this);
        vaultMenuService.setConfigEditorService(configEditorService);

        // CMI integration removed per request.

        // Register commands using our Economy
        PayCommand payCmd = null;
        VaultCommand vaultCmd = null;
        EcoCommand ecoCmd = null;

        if (getCommand("balance") != null) {
            getCommand("balance").setExecutor(new BalanceCommand(this, economy, messages));
        }
        if (getCommand("pay") != null) {
            // After economy initialization
            ChargeRequestService chargeRequestService = new ChargeRequestService(this, economy, messages, db);
            getServer().getPluginManager().registerEvents(chargeRequestService, this);
            payMenuService = new PayMenuService(this, economy, messages, chargeRequestService);
            getServer().getPluginManager().registerEvents(payMenuService, this);
            // Register commands
            payCmd = new PayCommand(this, economy, payMenuService, messages);
            getCommand("pay").setExecutor(payCmd);
            if (vaultMenuService != null) {
                vaultMenuService.setPayMenuService(payMenuService);
            }
        }
        if (getCommand("vault") != null) {
            vaultCmd = new VaultCommand(this, messages, vaultMenuService, loanService);
            getCommand("vault").setExecutor(vaultCmd);
        }
        if (getCommand("loan") != null) {
            getCommand("loan").setExecutor((sender, command, label, args) -> {
                if (!(sender instanceof org.bukkit.entity.Player)) {
                    sender.sendMessage("Only players can use this command.");
                    return true;
                }
                org.bukkit.entity.Player p = (org.bukkit.entity.Player) sender;
                if (!p.hasPermission("vault.loan")) {
                    p.sendMessage(messages.chat("loan.no_permission"));
                    return true;
                }
                if (vaultMenuService != null && vaultMenuService.getLoanMenuService() != null) {
                    vaultMenuService.getLoanMenuService().openLoanMenu(p);
                }
                return true;
            });
        }
        if (getCommand("vaultop") != null) {
            getCommand("vaultop").setExecutor(new VaultOpCommand(this, economy, messages));
        }
        // Register /eco admin command (give/take)
        if (getCommand("eco") != null) {
            ecoCmd = new EcoCommand(this, economy, messages);
            getCommand("eco").setExecutor(ecoCmd);
        }

        // --- WIRING SERVICES -> COMMANDS ---
        if (ecoCmd != null) {
            ecoCmd.setTopCache(topCache);
        }
        if (payCmd != null) {
            payCmd.setOfflinePayQueue(offlinePay);
        }
        if (vaultCmd != null) {
            vaultCmd.topCache = topCache;
            vaultCmd.offlinePay = offlinePay;
            vaultCmd.noteService = noteService;
            vaultCmd.bankService = bankService;
            vaultCmd.historyMenu = historyMenu;
        }

        // Register listener for OP join notifications
        getServer().getPluginManager().registerEvents(this, this);

        // Schedule update check and run one immediately
        if (getConfig().getBoolean("update_check", true)) {
            scheduleUpdateCheck();
            getServer().getScheduler().runTaskAsynchronously(this, new Runnable() {
                @Override public void run() { checkForUpdate(); }
            });
        }

        // Initialize bStats Metrics
        // IMPORTANT: Replace 12345 with your own plugin ID from https://bstats.org
        int pluginId = 28342;
        Metrics metrics = new Metrics(this, pluginId);

        // Custom charts
        metrics.addCustomChart(new org.bstats.charts.SimplePie("storage_type", () -> 
            getConfig().getBoolean("storage.use_mysql", false) ? "MySQL" : "Flatfile"
        ));
        
        metrics.addCustomChart(new org.bstats.charts.SimplePie("language", () -> 
            getConfig().getString("language", "en")
        ));

        com.example.vault.util.LogoPrinter.printEnable(this);
        getLogger().info(messages.get("plugin.enabled"));
    }

    private void loadDependencies() {
        BukkitLibraryManager libraryManager = new BukkitLibraryManager(this);
        libraryManager.addMavenCentral();

        // SLF4J Simple (Required by HikariCP)
        Library slf4j = Library.builder()
                .groupId("org.slf4j")
                .artifactId("slf4j-simple")
                .version("1.7.36")
                .build();
        
        // HikariCP
        Library hikari = Library.builder()
                .groupId("com.zaxxer")
                .artifactId("HikariCP")
                .version("5.1.0")
                .build();
        
        // MySQL Connector/J
        Library mysql = Library.builder()
                .groupId("com.mysql")
                .artifactId("mysql-connector-j")
                .version("8.0.33")
                .build();

        getLogger().info("Loading libraries (HikariCP, MySQL)... this might take a moment on first run.");
        libraryManager.loadLibrary(slf4j);
        libraryManager.loadLibrary(hikari);
        libraryManager.loadLibrary(mysql);
        getLogger().info("Libraries loaded successfully.");
    }

    public void reloadPluginState() {
        // Recreate missing config.yml or merge in any new default keys.
        syncConfigFile();
        String lang = getConfig().getString("language", "en");
        messages.reload(lang);
        // Reschedule autosave with new config
        if (economy instanceof SimpleEconomy) {
            SimpleEconomy se = (SimpleEconomy) economy;
            // PRIMERO: guardar balances actuales a disco (YAML) o flush MySQL, por si hay
            // cambios en RAM sin persistir desde último autosave, NO perderlos al reload defs.
            try {
                se.saveAllNow();
            } catch (java.io.IOException ex) {
                getLogger().warning("Failed to save balances before /vault reload: " + ex.getMessage());
            }
            // Recargar defs de monedas (símbolo, format, ...) 100% safe: loadCurrencyDefinitions
            // ahora preserva los balances en memoria.
            se.reloadCurrencyFormat();
            se.reloadStorageSettings();
            scheduleAutosave(se);
        }
        if (loanService != null) {
            loanService.start();
        }
        // Reload services v2.1
        if (bankService != null) {
            bankService.stop();
            bankService.start();
        }
        if (topCache != null) {
            topCache.invalidateAll();
        }
        if (discord != null) {
            discord.reload();
        }
        // Reschedule update check
        if (updateCheckTask != null) {
            updateCheckTask.cancel();
            updateCheckTask = null;
        }
        if (getConfig().getBoolean("update_check", true)) {
            scheduleUpdateCheck();
        }
    }

    private void migrateConfig() {
        org.bukkit.configuration.file.FileConfiguration cfg = getConfig();
        boolean changed = false;
        if (cfg.isConfigurationSection("permissions")) {
            cfg.set("permissions", null);
            changed = true;
        }
        // Ensure config reflects current plugin version
        String currentVersion = getDescription().getVersion();
        if (!currentVersion.equals(cfg.getString("plugin_version"))) {
            cfg.set("plugin_version", currentVersion);
            changed = true;
        }
        if (changed) {
            saveConfig();
            getLogger().info("Removed obsolete 'permissions' section from config.yml");
            getLogger().info("Synchronized 'plugin_version' in config.yml to " + currentVersion);
        }
    }

    private void syncConfigFile() {
        File configFile = new File(getDataFolder(), "config.yml");
        if (!configFile.exists()) {
            saveDefaultConfig();
            reloadConfig();
            migrateConfig();
            getLogger().info("Recreated missing config.yml from plugin defaults.");
            return;
        }

        reloadConfig();
        InputStream in = getResource("config.yml");
        YamlConfiguration defaults = null;
        if (in != null) {
            defaults = YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8));
        }

        boolean changed = false;
        if (defaults != null) {
            YamlConfiguration fileCfg = YamlConfiguration.loadConfiguration(configFile);
            for (String path : defaults.getKeys(true)) {
                if (path == null || path.isEmpty()) continue;
                if (defaults.isConfigurationSection(path)) {
                    if (!fileCfg.isConfigurationSection(path)) {
                        fileCfg.createSection(path);
                        changed = true;
                    }
                    continue;
                }
                if (!fileCfg.contains(path)) {
                    fileCfg.set(path, defaults.get(path));
                    changed = true;
                }
            }
            if (changed) {
                try {
                    fileCfg.save(configFile);
                } catch (Exception ex) {
                    getLogger().warning("Failed to update config.yml with new defaults: " + ex.getMessage());
                }
                reloadConfig();
                getLogger().info("Added missing entries to config.yml while preserving existing values.");
            }
        }

        migrateConfig();
    }

    @Override
    public void onDisable() {
        com.example.vault.util.LogoPrinter.printDisable(this);
        // Unregister our Economy service
        getServer().getServicesManager().unregister(Economy.class, economy);
        if (loanService != null) {
            loanService.shutdown();
            loanService = null;
        }
        // Shutdown services v2.1
        if (bankService != null) {
            try { bankService.stop(); } catch (Throwable ignored) {}
            bankService = null;
        }
        if (topCache != null) {
            try { topCache.shutdown(); } catch (Throwable ignored) {}
            topCache = null;
        }
        // Cancel autosave
        if (autosaveTask != null) {
            try { autosaveTask.cancel(); } catch (Exception ignored) {}
            autosaveTask = null;
        }
        // Cancel update check
        if (updateCheckTask != null) {
            try { updateCheckTask.cancel(); } catch (Exception ignored) {}
            updateCheckTask = null;
        }
        // Persist balances on shutdown
        if (economy instanceof SimpleEconomy) {
            try {
                ((SimpleEconomy) economy).saveAllNow();
            } catch (java.io.IOException ex) {
                getLogger().warning("Failed to save balances: " + ex.getMessage());
            }
            // Close SQL connection if used
            ((SimpleEconomy) economy).close();
        }
        // Shutdown transaction logger (flushes pending + persists serial)
        if (txLog != null) {
            try {
                txLog.shutdown();
            } catch (Throwable t) {
                getLogger().warning("Failed to shutdown transaction logger: " + t.getMessage());
            }
            txLog = null;
        }
        getLogger().info(messages.get("plugin.disabled"));
    }

    public VaultMenuService getVaultMenuService() {
        return vaultMenuService;
    }

    private void scheduleAutosave(SimpleEconomy provider) {
        // Cancel previous task if any
        if (autosaveTask != null) {
            try { autosaveTask.cancel(); } catch (Exception ignored) {}
            autosaveTask = null;
        }
        int seconds = getConfig().getInt("storage.autosave_seconds", 60);
        if (seconds <= 0) {
            return; // disabled
        }
        long ticks = 20L * seconds;
        autosaveTask = getServer().getScheduler().runTaskTimerAsynchronously(this, new Runnable() {
            @Override public void run() {
                try {
                    provider.save();
                } catch (java.io.IOException ex) {
                    getLogger().warning("Autosave failed: " + ex.getMessage());
                }
            }
        }, ticks, ticks);
    }

    private void scheduleUpdateCheck() {
        // Cancel previous if any
        if (updateCheckTask != null) {
            try { updateCheckTask.cancel(); } catch (Exception ignored) {}
            updateCheckTask = null;
        }
        long periodTicks = 20L * 60L * 30L; // cada 30 minutos
        updateCheckTask = getServer().getScheduler().runTaskTimerAsynchronously(this, new Runnable() {
            @Override public void run() { checkForUpdate(); }
        }, periodTicks, periodTicks);
    }

    public void runUpdateCheckAndAnnounce(CommandSender requester) {
        new UpdateChecker(this, MODRINTH_PROJECT_ID).getLatestVersion(remote -> {
            String current = getDescription().getVersion();
            
            String normRemote = normalizeVersion(remote);
            String normCurrent = normalizeVersion(current);
            boolean update = !normRemote.equalsIgnoreCase(normCurrent);

            if (update) {
                String msg = buildUpdateMessage(remote);
                Bukkit.getScheduler().runTask(this, () -> {
                   if (requester instanceof Player) {
                       sendClickableUpdateMessage(requester, msg);
                   } else {
                       requester.sendMessage(msg);
                   }
                });
            } else {
                // Mensaje opcional si está actualizado
                // requester.sendMessage("Plugin is up to date.");
            }
        });
    }

    private void checkForUpdate() {
        new UpdateChecker(this, MODRINTH_PROJECT_ID).getLatestVersion(version -> {
            remoteVersion = version;
            String current = getDescription().getVersion();
            
            // Debug log detallado
            String normRemote = normalizeVersion(remoteVersion);
            String normCurrent = normalizeVersion(current);
            if (getConfig().getBoolean("update_check_debug", false)) {
                getLogger().fine("[UpdateCheck Debug] Raw Local: '" + current + "' | Norm Local: '" + normCurrent + "'");
                getLogger().fine("[UpdateCheck Debug] Raw Remote: '" + remoteVersion + "' | Norm Remote: '" + normRemote + "'");
            }

            updateAvailable = !normRemote.equalsIgnoreCase(normCurrent);
            
            if (updateAvailable && (lastAnnouncedVersion == null || !lastAnnouncedVersion.equals(remoteVersion))) {
                 Bukkit.getScheduler().runTask(this, () -> {
                      notifyOnlineOps(remoteVersion);
                      sendConsoleUpdateMessage(normRemote);
                 });
                 lastAnnouncedVersion = remoteVersion;
            }
        });
    }

    private void sendConsoleUpdateMessage(String latestVersion) {
        Bukkit.getConsoleSender().sendMessage(ColorUtil.colorize("&cThere is a new version available. &e(&7" + latestVersion + "&e)"));
        Bukkit.getConsoleSender().sendMessage(ColorUtil.colorize("&cYou can download it at: &f " + UPDATE_LINK));
    }

    private String buildUpdateMessage(String version) {
        String lang = getConfig().getString("language", "en");
        String norm = (version != null ? normalizeVersion(version) : null);
        String ver;
        if (norm != null && !norm.isEmpty()) {
            ver = "v" + norm;
        } else {
            String lname = lang.toLowerCase(java.util.Locale.ROOT);
            if ("es".equals(lname)) {
                ver = "desconocida";
            } else if ("zh_cn".equals(lname)) {
                ver = "未知";
            } else if ("zh_tw".equals(lname)) {
                ver = "未知";
            } else if ("fr".equals(lname)) {
                ver = "inconnue";
            } else if ("de".equals(lname)) {
                ver = "unbekannt";
            } else {
                ver = "unknown";
            }
        }
        String lname = lang.toLowerCase(java.util.Locale.ROOT);
        if ("es".equals(lname)) {
            return "§eHay una nueva actualización de Vault 2.0 (" + ver + ") disponible. §bDescárgala: ";
        } else if ("zh_cn".equals(lname)) {
            return "§eVault 2.0 有新更新 (" + ver + ") 可用。§b下载: ";
        } else if ("zh_tw".equals(lname)) {
            return "§eVault 2.0 有新更新 (" + ver + ") 可用。§b下載: ";
        } else if ("fr".equals(lname)) {
            return "§eUne nouvelle mise à jour de Vault 2.0 (" + ver + ") est disponible. §bTélécharger : ";
        } else if ("de".equals(lname)) {
            return "§eEin neues Update für Vault 2.0 (" + ver + ") ist verfügbar. §bDownload: ";
        } else {
            return "§eA new Vault 2.0 update (" + ver + ") is available. §bDownload: ";
        }
    }

    private String normalizeVersion(String s) {
        if (s == null) return "";
        String input = s.trim();
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("(?i)(?:^|[^\\d.])v?(\\d+(?:\\.\\d+)+)(?!\\d)").matcher(input);
        String candidate = null;
        while (m.find()) {
            candidate = m.group(1);
        }
        if (candidate == null) {
             java.util.regex.Matcher m2 = java.util.regex.Pattern.compile("(?i)v?(\\d+(?:\\.\\d+)*)").matcher(input);
             while (m2.find()) {
                 candidate = m2.group(1);
             }
        }
        return candidate != null ? candidate : input;
    }

    private void notifyOnlineOps(String newVersion) {
        String msg = buildUpdateMessage(newVersion);
        for (Player p : getServer().getOnlinePlayers()) {
            if (p.isOp()) {
                sendClickableUpdateMessage(p, msg);
            }
        }
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        if (!getConfig().getBoolean("update_check", true)) return;
        Player p = event.getPlayer();
        if (!p.isOp()) return;
        if (updateAvailable) {
            p.sendMessage(buildUpdateMessage(remoteVersion) + " " + UPDATE_LINK);
            return;
        }
        long now = System.currentTimeMillis();
        if ((remoteVersion == null) || (now - lastOnJoinUpdateCheckMs >= ON_JOIN_CHECK_COOLDOWN_MS)) {
            lastOnJoinUpdateCheckMs = now;
            runUpdateCheckAndAnnounce(p);
        }
    }

    private void sendClickableUpdateMessage(org.bukkit.command.CommandSender sender, String message) {
        if (sender instanceof org.bukkit.entity.Player) {
            org.bukkit.entity.Player p = (org.bukkit.entity.Player) sender;
            try {
                net.md_5.bungee.api.chat.TextComponent tc = new net.md_5.bungee.api.chat.TextComponent(message + " " + UPDATE_LINK);
                tc.setClickEvent(new net.md_5.bungee.api.chat.ClickEvent(
                    net.md_5.bungee.api.chat.ClickEvent.Action.OPEN_URL, UPDATE_LINK));
                String lang = getConfig().getString("language", "en");
                String hover = switch (lang.toLowerCase(java.util.Locale.ROOT)) {
                    case "es" -> "Abrir Modrinth";
                    case "zh_cn" -> "打开 Modrinth";
                    case "zh_tw" -> "開啟 Modrinth";
                    case "fr" -> "Ouvrir Modrinth";
                    case "de" -> "Modrinth öffnen";
                    default -> "Open Modrinth";
                };
                net.md_5.bungee.api.chat.HoverEvent hoverEvent = hoverShowText(hover);
                if (hoverEvent != null) tc.setHoverEvent(hoverEvent);
                p.spigot().sendMessage(tc);
            } catch (Throwable t) {
                sender.sendMessage(message + " " + UPDATE_LINK);
            }
        } else {
            sender.sendMessage(message + " " + UPDATE_LINK);
        }
    }

    private net.md_5.bungee.api.chat.HoverEvent hoverShowText(String legacyText) {
        net.md_5.bungee.api.chat.BaseComponent[] components = net.md_5.bungee.api.chat.TextComponent.fromLegacyText(legacyText);
        try {
            java.lang.reflect.Constructor<net.md_5.bungee.api.chat.HoverEvent> ctor =
                net.md_5.bungee.api.chat.HoverEvent.class.getConstructor(net.md_5.bungee.api.chat.HoverEvent.Action.class, net.md_5.bungee.api.chat.BaseComponent[].class);
            return ctor.newInstance(net.md_5.bungee.api.chat.HoverEvent.Action.SHOW_TEXT, components);
        } catch (Throwable ignored) {
        }
        try {
            Class<?> contentClass = Class.forName("net.md_5.bungee.api.chat.hover.content.Content");
            Class<?> textClass = Class.forName("net.md_5.bungee.api.chat.hover.content.Text");
            java.lang.reflect.Constructor<?> textCtor = textClass.getConstructor(net.md_5.bungee.api.chat.BaseComponent[].class);
            Object text = textCtor.newInstance((Object) components);
            Object arr = java.lang.reflect.Array.newInstance(contentClass, 1);
            java.lang.reflect.Array.set(arr, 0, text);
            java.lang.reflect.Constructor<?> hoverCtor = net.md_5.bungee.api.chat.HoverEvent.class.getConstructor(
                net.md_5.bungee.api.chat.HoverEvent.Action.class, arr.getClass());
            return (net.md_5.bungee.api.chat.HoverEvent) hoverCtor.newInstance(net.md_5.bungee.api.chat.HoverEvent.Action.SHOW_TEXT, arr);
        } catch (Throwable ignored) {
        }
        return null;
    }
}
