package com.example.vault.menu;

import com.example.vault.VaultPlugin;
import com.example.vault.economy.BankService;
import com.example.vault.economy.SimpleEconomy;
import com.example.vault.i18n.Messages;
import com.example.vault.loans.LoanService;
import com.example.vault.util.ClickablePromptUtil;
import com.example.vault.util.ColorUtil;
import net.md_5.bungee.api.chat.ClickEvent;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class VaultMenuService implements Listener {
    private final VaultPlugin plugin;
    private final Messages messages;
    private final LoanMenuService loanMenuService;
    private final BankMenuService bankMenuService;
    private PayMenuService payMenuService;
    private ConfigEditorService configEditorService;
    private final ConcurrentMap<UUID, Map<Integer, String>> textKeysByPlayer = new ConcurrentHashMap<>();

    public VaultMenuService(VaultPlugin plugin, Messages messages, LoanService loanService, BankService bankService) {
        this.plugin = plugin;
        this.messages = messages;
        this.loanMenuService = new LoanMenuService(plugin, messages, loanService);
        this.bankMenuService = new BankMenuService(plugin, messages, bankService);
    }

    public LoanMenuService getLoanMenuService() {
        return loanMenuService;
    }

    public BankMenuService getBankMenuService() { return bankMenuService; }

    public void openBankMenu(Player player) {
        bankMenuService.openBankMenu(player);
    }

    public void setPayMenuService(PayMenuService payMenuService) {
        this.payMenuService = payMenuService;
    }

    public void setConfigEditorService(ConfigEditorService configEditorService) {
        this.configEditorService = configEditorService;
    }

    public boolean cancelAdminEdit(Player player) {
        return configEditorService != null && configEditorService.cancelEdit(player);
    }

    public void openMainMenu(Player player) {
        int size = 27;
        Inventory inv = Bukkit.createInventory(null, size, titleMain());

        SimpleEconomy se = null;
        if (plugin.getEconomyProvider() instanceof SimpleEconomy) se = (SimpleEconomy) plugin.getEconomyProvider();
        BankService b = plugin.getBankService();

        inv.setItem(10, item(Material.EMERALD, display("vault.menu.item.pay", "&aPay"), lore("vault.menu.lore.pay")));
        inv.setItem(12, item(Material.EMERALD_BLOCK, display("vault.menu.item.loan", "&aLoan"), lore("vault.menu.lore.loan")));

        // 🏦 Bank item (slot 13)
        if (se != null && b != null) {
            String cid = se.getDefaultCurrencyId();
            double bankBal = b.getBankBalance(player.getUniqueId());
            double irate = plugin.getConfig().getDouble("bank.interest.percent_per_period", 0.5);
            Map<String, String> c = new LinkedHashMap<>();
            c.put("bank", se.format(cid, bankBal));
            c.put("interest_next", se.format(cid, Math.max(0, bankBal) * irate / 100.0));
            // Build item with placeholders in lore
            String bankName = messages.getOptional("bank.menu.item_name");
            if (bankName == null || bankName.isEmpty()) bankName = "&6🏦 Banco";
            String dispName = replaceAndColor(bankName, c);
            List<String> baseLore = messages.formatList("bank.menu.item_lore", c);
            List<String> coloredLore = new ArrayList<>(baseLore.size());
            for (String l : baseLore) coloredLore.add(ColorUtil.colorize(l));
            Material gold = Material.matchMaterial("GOLD_BLOCK");
            if (gold == null) gold = Material.GOLD_BLOCK;
            inv.setItem(13, item(gold, dispName, coloredLore));
        }

        if (player.isOp()) {
            Material comparator = Material.matchMaterial("COMPARATOR");
            if (comparator == null) comparator = Material.matchMaterial("REDSTONE_COMPARATOR");
            if (comparator == null) comparator = Material.REDSTONE;
            inv.setItem(14, item(comparator, display("vault.menu.item.settings", "&cSettings"), lore("vault.menu.lore.settings")));
            inv.setItem(16, item(Material.BOOK, display("vault.menu.item.reload", "&eReload"), lore("vault.menu.lore.reload")));
            inv.setItem(18, item(Material.NETHER_STAR, display("vault.menu.item.update", "&bUpdate"), lore("vault.menu.lore.update")));
        }

        player.openInventory(inv);
    }

    public void openSettingsMenu(Player player) {
        Inventory inv = Bukkit.createInventory(null, 27, titleSettingsMain());
        inv.setItem(10, item(Material.EMERALD_BLOCK, display("settings.item.loans", "&aLoans"), lore("settings.lore.loans")));
        inv.setItem(12, item(Material.EMERALD, display("settings.item.pay", "&aPay"), lore("settings.lore.pay")));
        inv.setItem(14, item(Material.NAME_TAG, display("settings.item.texts", "&aTexts"), lore("settings.lore.texts")));
        inv.setItem(16, item(Material.ARROW, display("settings.item.back", "&7Back"), lore("settings.lore.back")));
        player.openInventory(inv);
    }

    public void openLoanSettingsMenu(Player player) {
        Inventory inv = Bukkit.createInventory(null, 27, titleSettingsLoans());

        boolean enabled = plugin.getConfig().getBoolean("loans.enabled", true);
        inv.setItem(10, item(Material.LEVER, messages.color("settings.loans.enabled") + " " + (enabled ? "&aON" : "&cOFF"), lore("settings.lore.loans_enabled")));

        inv.setItem(11, item(Material.GOLD_NUGGET, messages.color("settings.loans.min") + " &7(" + plugin.getConfig().getDouble("loans.min_amount", 0.0) + ")", lore("settings.lore.loans_min")));
        inv.setItem(12, item(Material.GOLD_INGOT, messages.color("settings.loans.max") + " &7(" + plugin.getConfig().getDouble("loans.max_amount", 0.0) + ")", lore("settings.lore.loans_max")));
        inv.setItem(13, item(Material.PAPER, messages.color("settings.loans.max_installments") + " &7(" + plugin.getConfig().getInt("loans.max_installments", 60) + ")", lore("settings.lore.loans_max_installments")));
        Material clock = Material.matchMaterial("CLOCK");
        if (clock == null) clock = Material.matchMaterial("WATCH");
        if (clock == null) clock = Material.PAPER;
        inv.setItem(14, item(clock, messages.color("settings.loans.default_interval_hours") + " &7(" + plugin.getConfig().getInt("loans.default_interval_hours", 24) + ")", lore("settings.lore.loans_default_interval_hours")));
        Material repeater = Material.matchMaterial("REPEATER");
        if (repeater == null) repeater = Material.matchMaterial("DIODE");
        if (repeater == null) repeater = Material.REDSTONE;
        inv.setItem(15, item(repeater, messages.color("settings.loans.charge_check_seconds") + " &7(" + plugin.getConfig().getInt("loans.charge_check_seconds", 60) + ")", lore("settings.lore.loans_charge_check_seconds")));
        inv.setItem(16, item(Material.BARRIER, messages.color("settings.loans.max_missed") + " &7(" + plugin.getConfig().getInt("loans.max_missed_payments", 3) + ")", lore("settings.lore.loans_max_missed")));

        inv.setItem(22, item(Material.ARROW, display("settings.item.back", "&7Back"), lore("settings.lore.back")));
        player.openInventory(inv);
    }

    public void openPaySettingsMenu(Player player) {
        Inventory inv = Bukkit.createInventory(null, 27, titleSettingsPay());

        inv.setItem(11, item(Material.GOLD_NUGGET, messages.color("settings.pay.min") + " &7(" + plugin.getConfig().getDouble("pay_limits.min", 0.0) + ")", lore("settings.lore.pay_min")));
        inv.setItem(12, item(Material.GOLD_INGOT, messages.color("settings.pay.max") + " &7(" + plugin.getConfig().getDouble("pay_limits.max", 0.0) + ")", lore("settings.lore.pay_max")));
        inv.setItem(13, item(Material.CHEST, messages.color("settings.pay.menu_size") + " &7(" + plugin.getConfig().getInt("pay_menu.size", 27) + ")", lore("settings.lore.pay_menu_size")));
        Material head = Material.matchMaterial("PLAYER_HEAD");
        if (head == null) head = Material.matchMaterial("SKULL_ITEM");
        if (head == null) head = Material.PAPER;
        inv.setItem(14, item(head, messages.color("settings.pay.show_self") + " &7(" + plugin.getConfig().getBoolean("pay_menu.show_self", false) + ")", lore("settings.lore.pay_show_self")));

        inv.setItem(22, item(Material.ARROW, display("settings.item.back", "&7Back"), lore("settings.lore.back")));
        player.openInventory(inv);
    }

    public void openTextSettingsMenu(Player player) {
        Inventory inv = Bukkit.createInventory(null, 54, titleSettingsTexts());
        java.util.Map<Integer, String> map = new java.util.HashMap<>();

        int slot = 0;
        slot = addTextItem(inv, map, slot, Material.PAPER, "vault.menu.title");
        slot = addTextItem(inv, map, slot, Material.PAPER, "vault.menu.item.pay");
        slot = addTextItem(inv, map, slot, Material.PAPER, "vault.menu.item.loan");
        slot = addTextItem(inv, map, slot, Material.PAPER, "vault.menu.item.settings");
        slot = addTextItem(inv, map, slot, Material.PAPER, "vault.menu.item.reload");
        slot = addTextItem(inv, map, slot, Material.PAPER, "vault.menu.item.update");
        slot = addTextItem(inv, map, slot, Material.NAME_TAG, "loan.menu.title");
        slot = addTextItem(inv, map, slot, Material.NAME_TAG, "loan.menu.item.request");
        slot = addTextItem(inv, map, slot, Material.NAME_TAG, "loan.menu.item.status");
        slot = addTextItem(inv, map, slot, Material.NAME_TAG, "loan.menu.item.pay");
        slot = addTextItem(inv, map, slot, Material.BOOK, "pay.menu.title_main");
        slot = addTextItem(inv, map, slot, Material.BOOK, "pay.menu.item.pay");
        slot = addTextItem(inv, map, slot, Material.BOOK, "pay.menu.item.view_balance");
        slot = addTextItem(inv, map, slot, Material.BOOK, "pay.menu.item.charge");

        inv.setItem(53, item(Material.ARROW, display("settings.item.back", "&7Back"), lore("settings.lore.back")));
        textKeysByPlayer.put(player.getUniqueId(), map);
        player.openInventory(inv);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;
        Player player = (Player) event.getWhoClicked();
        if (event.getClickedInventory() == null || !event.getClickedInventory().equals(event.getView().getTopInventory())) return;
        String title = event.getView().getTitle();
        boolean isMain = title.equals(titleMain());
        boolean isSettingsMain = title.equals(titleSettingsMain());
        boolean isSettingsLoans = title.equals(titleSettingsLoans());
        boolean isSettingsPay = title.equals(titleSettingsPay());
        boolean isSettingsTexts = title.equals(titleSettingsTexts());
        if (!(isMain || isSettingsMain || isSettingsLoans || isSettingsPay || isSettingsTexts)) return;
        event.setCancelled(true);

        ItemStack item = event.getCurrentItem();
        if (item == null || item.getType() == Material.AIR) return;
        ItemMeta meta = item.getItemMeta();
        String name = meta != null ? meta.getDisplayName() : "";

        if (isMain) {
            if (name.equals(display("vault.menu.item.pay", "&aPay"))) {
                if (payMenuService != null) {
                    payMenuService.openMainMenu(player);
                }
                return;
            }
            if (name.equals(display("vault.menu.item.loan", "&aLoan"))) {
                loanMenuService.openLoanMenu(player);
                return;
            }
            // Bank menu item matches on placeholder-resolved bank.menu.item_name raw
            String rawBankName = messages.getOptional("bank.menu.item_name");
            String fallbackBankName = "&6🏦 Banco";
            SimpleEconomy se = null;
            if (plugin.getEconomyProvider() instanceof SimpleEconomy) se = (SimpleEconomy) plugin.getEconomyProvider();
            BankService bs = plugin.getBankService();
            boolean bankMatch = false;
            if (se != null && bs != null && rawBankName != null && !rawBankName.isEmpty()) {
                Map<String, String> c = new LinkedHashMap<>();
                String cid = se.getDefaultCurrencyId();
                c.put("bank", se.format(cid, bs.getBankBalance(player.getUniqueId())));
                double irate = plugin.getConfig().getDouble("bank.interest.percent_per_period", 0.5);
                c.put("interest_next", se.format(cid, Math.max(0, bs.getBankBalance(player.getUniqueId())) * irate / 100.0));
                bankMatch = name.equals(replaceAndColor(rawBankName, c));
            }
            if (!bankMatch && (name.equals(ColorUtil.colorize(fallbackBankName)))) bankMatch = true;
            if (bankMatch) {
                openBankMenu(player);
                return;
            }
            if (player.isOp() && name.equals(display("vault.menu.item.settings", "&cSettings"))) {
                openSettingsMenu(player);
                return;
            }
            if (player.isOp() && name.equals(display("vault.menu.item.reload", "&eReload"))) {
                plugin.reloadPluginState();
                String lang = plugin.getConfig().getString("language", "en");
                player.sendMessage(messages.formatChat("plugin.reloaded", java.util.Collections.singletonMap("lang", lang)));
                player.closeInventory();
                return;
            }
            if (player.isOp() && name.equals(display("vault.menu.item.update", "&bUpdate"))) {
                player.sendMessage(messages.prefixed("Checking for updates..."));
                plugin.runUpdateCheckAndAnnounce(player);
                player.closeInventory();
            }
            return;
        }

        if (!player.isOp()) return;

        if (isSettingsMain) {
            if (name.equals(display("settings.item.loans", "&aLoans"))) {
                openLoanSettingsMenu(player);
                return;
            }
            if (name.equals(display("settings.item.pay", "&aPay"))) {
                openPaySettingsMenu(player);
                return;
            }
            if (name.equals(display("settings.item.texts", "&aTexts"))) {
                openTextSettingsMenu(player);
                return;
            }
            if (name.equals(display("settings.item.back", "&7Back"))) {
                openMainMenu(player);
                return;
            }
        }

        if (isSettingsLoans) {
            if (name.startsWith(ColorUtil.colorize(messages.get("settings.loans.enabled")))) {
                boolean enabled = !plugin.getConfig().getBoolean("loans.enabled", true);
                plugin.getConfig().set("loans.enabled", enabled);
                plugin.saveConfig();
                plugin.reloadPluginState();
                openLoanSettingsMenu(player);
                return;
            }
            if (name.startsWith(ColorUtil.colorize(messages.get("settings.loans.min")))) {
                startConfigEdit(player, "loans.min_amount", ConfigEditorService.EditType.CONFIG_DOUBLE, "vault_settings_loans");
                return;
            }
            if (name.startsWith(ColorUtil.colorize(messages.get("settings.loans.max")))) {
                startConfigEdit(player, "loans.max_amount", ConfigEditorService.EditType.CONFIG_DOUBLE, "vault_settings_loans");
                return;
            }
            if (name.startsWith(ColorUtil.colorize(messages.get("settings.loans.max_installments")))) {
                startConfigEdit(player, "loans.max_installments", ConfigEditorService.EditType.CONFIG_INT, "vault_settings_loans");
                return;
            }
            if (name.startsWith(ColorUtil.colorize(messages.get("settings.loans.default_interval_hours")))) {
                startConfigEdit(player, "loans.default_interval_hours", ConfigEditorService.EditType.CONFIG_INT, "vault_settings_loans");
                return;
            }
            if (name.startsWith(ColorUtil.colorize(messages.get("settings.loans.charge_check_seconds")))) {
                startConfigEdit(player, "loans.charge_check_seconds", ConfigEditorService.EditType.CONFIG_INT, "vault_settings_loans");
                return;
            }
            if (name.startsWith(ColorUtil.colorize(messages.get("settings.loans.max_missed")))) {
                startConfigEdit(player, "loans.max_missed_payments", ConfigEditorService.EditType.CONFIG_INT, "vault_settings_loans");
                return;
            }
            if (name.equals(display("settings.item.back", "&7Back"))) {
                openSettingsMenu(player);
                return;
            }
        }

        if (isSettingsPay) {
            if (name.startsWith(ColorUtil.colorize(messages.get("settings.pay.min")))) {
                startConfigEdit(player, "pay_limits.min", ConfigEditorService.EditType.CONFIG_DOUBLE, "vault_settings_pay");
                return;
            }
            if (name.startsWith(ColorUtil.colorize(messages.get("settings.pay.max")))) {
                startConfigEdit(player, "pay_limits.max", ConfigEditorService.EditType.CONFIG_DOUBLE, "vault_settings_pay");
                return;
            }
            if (name.startsWith(ColorUtil.colorize(messages.get("settings.pay.menu_size")))) {
                startConfigEdit(player, "pay_menu.size", ConfigEditorService.EditType.CONFIG_INT, "vault_settings_pay");
                return;
            }
            if (name.startsWith(ColorUtil.colorize(messages.get("settings.pay.show_self")))) {
                boolean v = !plugin.getConfig().getBoolean("pay_menu.show_self", false);
                plugin.getConfig().set("pay_menu.show_self", v);
                plugin.saveConfig();
                plugin.reloadPluginState();
                openPaySettingsMenu(player);
                return;
            }
            if (name.equals(display("settings.item.back", "&7Back"))) {
                openSettingsMenu(player);
                return;
            }
        }

        if (isSettingsTexts) {
            if (name.equals(display("settings.item.back", "&7Back"))) {
                openSettingsMenu(player);
                return;
            }
            java.util.Map<Integer, String> map = textKeysByPlayer.get(player.getUniqueId());
            if (map == null) return;
            String key = map.get(event.getSlot());
            if (key == null || key.isEmpty()) return;
            startMessageEdit(player, key, "vault_settings_texts");
        }
    }

    private void startConfigEdit(Player player, String key, ConfigEditorService.EditType type, String back) {
        if (configEditorService == null) return;
        player.closeInventory();
        configEditorService.startEdit(player, type, key, back);
        java.util.Map<String, String> m = new java.util.HashMap<>();
        m.put("key", key);
        ClickablePromptUtil.sendPromptWithClickableCancel(player, messages, "settings.prompt.value", m,
                "cancel", ClickEvent.Action.RUN_COMMAND, "/vault cancel");
    }

    private void startMessageEdit(Player player, String key, String back) {
        if (configEditorService == null) return;
        player.closeInventory();
        configEditorService.startEdit(player, ConfigEditorService.EditType.MESSAGE_STRING, key, back);
        java.util.Map<String, String> m = new java.util.HashMap<>();
        m.put("key", key);
        ClickablePromptUtil.sendPromptWithClickableCancel(player, messages, "settings.prompt.text", m,
                "cancel", ClickEvent.Action.RUN_COMMAND, "/vault cancel");
    }

    private int addTextItem(Inventory inv, java.util.Map<Integer, String> map, int slot, Material mat, String key) {
        if (slot >= inv.getSize() - 1) return slot;
        String current = messages.getOptional(key);
        if (current == null || current.isEmpty()) current = key;
        String name = current;
        java.util.List<String> lore = new java.util.ArrayList<>();
        lore.add("&7Key: &f" + key);
        lore.add("&7Current: &r" + current);
        ItemStack it = item(mat, ColorUtil.colorize(name), translateLore(lore));
        inv.setItem(slot, it);
        map.put(slot, key);
        return slot + 1;
    }

    private java.util.List<String> translateLore(java.util.List<String> lore) {
        java.util.List<String> out = new java.util.ArrayList<>();
        for (String l : lore) out.add(ColorUtil.colorize(l));
        return out;
    }

    private String titleMain() {
        String t = messages.get("vault.menu.title");
        if (t == null || t.isEmpty() || "vault.menu.title".equals(t)) t = "&bVault Menu";
        return ColorUtil.colorize(t);
    }

    private String titleSettingsMain() {
        String t = messages.get("settings.title");
        if (t == null || t.isEmpty() || "settings.title".equals(t)) t = "&cSettings";
        return ColorUtil.colorize(t);
    }

    private String titleSettingsLoans() {
        String t = messages.get("settings.title_loans");
        if (t == null || t.isEmpty() || "settings.title_loans".equals(t)) t = "&cLoan Settings";
        return ColorUtil.colorize(t);
    }

    private String titleSettingsPay() {
        String t = messages.get("settings.title_pay");
        if (t == null || t.isEmpty() || "settings.title_pay".equals(t)) t = "&cPay Settings";
        return ColorUtil.colorize(t);
    }

    private String titleSettingsTexts() {
        String t = messages.get("settings.title_texts");
        if (t == null || t.isEmpty() || "settings.title_texts".equals(t)) t = "&cText Settings";
        return ColorUtil.colorize(t);
    }

    private String replaceAndColor(String raw, Map<String, String> ctx) {
        if (raw == null) return "";
        String r = raw;
        for (Map.Entry<String, String> e : ctx.entrySet()) {
            String v = e.getValue() == null ? "" : e.getValue();
            r = r.replace("%" + e.getKey() + "%", v);
        }
        return ColorUtil.colorize(r);
    }

    private String display(String key, String fallback) {
        String raw = messages.getOptional(key);
        if (raw == null || raw.isEmpty()) {
            String alt = alternateKey(key);
            if (!alt.equals(key)) raw = messages.getOptional(alt);
        }
        if (raw == null || raw.isEmpty()) raw = fallback;
        return ColorUtil.colorize(raw);
    }

    private java.util.List<String> lore(String key) {
        java.util.List<String> list = messages.colorList(key);
        if (!list.isEmpty()) return list;
        String alt = alternateKey(key);
        if (!alt.equals(key)) return messages.colorList(alt);
        return java.util.Collections.emptyList();
    }

    private String alternateKey(String key) {
        if (key.contains(".items.")) return key.replace(".items.", ".item.");
        if (key.contains(".item.")) return key.replace(".item.", ".items.");
        return key;
    }

    private ItemStack item(Material mat, String displayName, java.util.List<String> lore) {
        ItemStack it = new ItemStack(mat);
        ItemMeta im = it.getItemMeta();
        if (im != null) {
            im.setDisplayName(ColorUtil.colorize(displayName));
            if (lore != null && !lore.isEmpty()) {
                java.util.List<String> out = new java.util.ArrayList<>(lore.size());
                for (String s : lore) {
                    if (s == null) continue;
                    out.add(ColorUtil.colorize(s));
                }
                im.setLore(out);
            }
            it.setItemMeta(im);
        }
        return it;
    }
}
