package com.example.vault.menu;

import com.example.vault.VaultPlugin;
import com.example.vault.i18n.Messages;
import com.example.vault.loans.LoanService;
import com.example.vault.util.ChatInputSanitizer;
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
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

public class LoanMenuService implements Listener {
    private final VaultPlugin plugin;
    private final Messages messages;
    private final LoanService loanService;
    private final java.util.Map<java.util.UUID, Wizard> wizards = new java.util.concurrent.ConcurrentHashMap<>();

    public LoanMenuService(VaultPlugin plugin, Messages messages, LoanService loanService) {
        this.plugin = plugin;
        this.messages = messages;
        this.loanService = loanService;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    private ItemStack createItemStack(Material material, int amount, short legacyData) {
        if (legacyData == 0) return new ItemStack(material, amount);
        try {
            java.lang.reflect.Constructor<ItemStack> c = ItemStack.class.getConstructor(Material.class, int.class, short.class);
            return c.newInstance(material, amount, legacyData);
        } catch (Throwable ignored) {
            ItemStack it = new ItemStack(material, amount);
            try {
                java.lang.reflect.Method m = ItemStack.class.getMethod("setDurability", short.class);
                m.invoke(it, legacyData);
            } catch (Throwable ignored2) {
            }
            return it;
        }
    }

    public void openLoanMenu(Player player) {
        Inventory inv = Bukkit.createInventory(null, 9, title());
        inv.setItem(2, item(Material.EMERALD, display("loan.menu.item.request", "&aRequest"), lore("loan.menu.lore.request")));
        inv.setItem(4, item(Material.PAPER, display("loan.menu.item.status", "&eStatus"), lore("loan.menu.lore.status")));
        inv.setItem(6, item(Material.GOLD_INGOT, display("loan.menu.item.pay", "&6Pay"), lore("loan.menu.lore.pay")));
        fillEmptyWithGreenGlass(inv);
        player.openInventory(inv);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;
        Player player = (Player) event.getWhoClicked();
        String viewTitle = event.getView().getTitle();
        boolean isMain = viewTitle.equals(title());
        boolean isType = viewTitle.equals(titleRequestType());
        boolean isDelay = viewTitle.equals(titleRequestDelay());
        boolean isMode = viewTitle.equals(titleRequestInstallmentsMode());
        boolean isCount = viewTitle.equals(titleRequestInstallmentsCount());
        boolean isInterval = viewTitle.equals(titleRequestInterval());
        boolean isFirstDelay = viewTitle.equals(titleRequestFirstDelay());
        if (!(isMain || isType || isDelay || isMode || isCount || isInterval || isFirstDelay)) return;
        if (event.getClickedInventory() == null || !event.getClickedInventory().equals(event.getView().getTopInventory())) return;
        event.setCancelled(true);

        ItemStack item = event.getCurrentItem();
        if (item == null || item.getType() == Material.AIR) return;
        ItemMeta meta = item.getItemMeta();
        String name = meta != null ? meta.getDisplayName() : "";

        if (isMain) {
            if (name.equals(display("loan.menu.item.request", "&aRequest"))) {
                startWizard(player);
                return;
            }
            if (name.equals(display("loan.menu.item.status", "&eStatus"))) {
                player.closeInventory();
                loanService.sendStatus(player);
                return;
            }
            if (name.equals(display("loan.menu.item.pay", "&6Pay"))) {
                loanService.openPayFlow(player);
                return;
            }
            return;
        }

        Wizard w = wizards.get(player.getUniqueId());
        if (w == null) {
            player.closeInventory();
            return;
        }

        if (isType) {
            if (event.getSlot() == 11) {
                w.mode = Mode.TOTAL;
                w.step = Step.WAIT_AMOUNT;
                w.installmentsMode = null;
                w.installments = 1;
                w.intervalHours = 0;
                w.firstDelayHours = 0;
                w.amount = null;
                w.installmentAmount = null;
                wizards.put(player.getUniqueId(), w);
                promptAmount(player);
                return;
            }
            if (event.getSlot() == 15) {
                w.mode = Mode.INSTALLMENTS;
                w.step = Step.WAIT_AMOUNT;
                w.installmentsMode = null;
                w.installments = 0;
                w.intervalHours = 0;
                w.firstDelayHours = 0;
                w.amount = null;
                w.installmentAmount = null;
                wizards.put(player.getUniqueId(), w);
                promptAmount(player);
                return;
            }
            if (isCancelSlot(event.getSlot(), name)) {
                cancelWizard(player);
                return;
            }
            if (isBackSlot(event.getSlot(), name)) {
                wizards.remove(player.getUniqueId());
                player.closeInventory();
                openLoanMenu(player);
                return;
            }
        }

        if (isMode) {
            if (event.getSlot() == 11) {
                w.installmentsMode = InstallmentsMode.COUNT;
                w.step = Step.INSTALLMENTS_COUNT;
                wizards.put(player.getUniqueId(), w);
                openInstallmentsCountMenu(player, w);
                return;
            }
            if (event.getSlot() == 15) {
                w.installmentsMode = InstallmentsMode.AMOUNT;
                w.step = Step.WAIT_INSTALLMENT_AMOUNT;
                wizards.put(player.getUniqueId(), w);
                promptInstallmentAmount(player);
                return;
            }
            if (isCancelSlot(event.getSlot(), name)) {
                cancelWizard(player);
                return;
            }
            if (isBackSlot(event.getSlot(), name)) {
                w.step = Step.TYPE;
                wizards.put(player.getUniqueId(), w);
                openTypeMenu(player, w);
                return;
            }
        }

        if (isCount) {
            Integer value = w.choices.get(event.getSlot());
            if (value != null && value > 0) {
                w.installments = value;
                w.step = Step.INTERVAL;
                w.choices.clear();
                wizards.put(player.getUniqueId(), w);
                openIntervalMenu(player, w);
                return;
            }
            if (isCancelSlot(event.getSlot(), name)) {
                cancelWizard(player);
                return;
            }
            if (isBackSlot(event.getSlot(), name)) {
                w.step = Step.MODE;
                w.choices.clear();
                wizards.put(player.getUniqueId(), w);
                openInstallmentsModeMenu(player, w);
                return;
            }
        }

        if (isDelay) {
            Integer hours = w.choices.get(event.getSlot());
            if (hours != null && hours > 0) {
                w.firstDelayHours = hours;
                finalizeWizard(player, w);
                return;
            }
            if (isCancelSlot(event.getSlot(), name)) {
                cancelWizard(player);
                return;
            }
            if (isBackSlot(event.getSlot(), name)) {
                w.step = Step.TYPE;
                w.choices.clear();
                wizards.put(player.getUniqueId(), w);
                openTypeMenu(player, w);
                return;
            }
        }

        if (isInterval) {
            Integer hours = w.choices.get(event.getSlot());
            if (hours != null && hours > 0) {
                w.intervalHours = hours;
                w.step = Step.FIRST_DELAY;
                w.choices.clear();
                wizards.put(player.getUniqueId(), w);
                openFirstDelayMenu(player, w);
                return;
            }
            if (isCancelSlot(event.getSlot(), name)) {
                cancelWizard(player);
                return;
            }
            if (isBackSlot(event.getSlot(), name)) {
                if (w.mode == Mode.TOTAL) {
                    w.step = Step.WAIT_AMOUNT;
                    w.choices.clear();
                    wizards.put(player.getUniqueId(), w);
                    promptAmount(player);
                    return;
                }
                if (w.installmentsMode == InstallmentsMode.COUNT) {
                    w.step = Step.INSTALLMENTS_COUNT;
                    w.choices.clear();
                    wizards.put(player.getUniqueId(), w);
                    openInstallmentsCountMenu(player, w);
                    return;
                }
                w.step = Step.WAIT_INSTALLMENT_AMOUNT;
                w.choices.clear();
                wizards.put(player.getUniqueId(), w);
                promptInstallmentAmount(player);
                return;
            }
        }

        if (isFirstDelay) {
            if (event.getSlot() == 4) {
                w.firstDelayHours = 0;
                finalizeWizard(player, w);
                return;
            }
            Integer hours = w.choices.get(event.getSlot());
            if (hours != null && hours > 0) {
                w.firstDelayHours = hours;
                finalizeWizard(player, w);
                return;
            }
            if (isCancelSlot(event.getSlot(), name)) {
                cancelWizard(player);
                return;
            }
            if (isBackSlot(event.getSlot(), name)) {
                w.step = Step.INTERVAL;
                w.choices.clear();
                wizards.put(player.getUniqueId(), w);
                openIntervalMenu(player, w);
                return;
            }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = false)
    public void onChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        Wizard w = wizards.get(player.getUniqueId());
        if (w == null) return;
        if (!(w.step == Step.WAIT_AMOUNT || w.step == Step.WAIT_INSTALLMENT_AMOUNT)) return;
        event.setCancelled(true);
        String msg = ChatInputSanitizer.sanitizeChatInput(event.getMessage());
        String lower = msg.toLowerCase(java.util.Locale.ROOT);
        for (String word : getCancelWords()) {
            if (lower.equals(word)) {
                Bukkit.getScheduler().runTask(plugin, () -> cancelWizard(player));
                return;
            }
        }
        Bukkit.getScheduler().runTask(plugin, () -> handleWizardAmountInput(player, w, msg));
    }

    private String title() {
        String t = messages.get("loan.menu.title");
        if (t == null || t.isEmpty() || "loan.menu.title".equals(t)) t = "&aLoan";
        return ColorUtil.colorize(t);
    }

    private String titleRequestType() {
        String t = messages.getOptional("loan.request.title.type");
        if (t == null || t.isEmpty()) t = "&aLoan Request";
        return ColorUtil.colorize(t);
    }

    private String titleRequestDelay() {
        String t = messages.getOptional("loan.request.title.delay");
        if (t == null || t.isEmpty()) t = "&eTotal Delay";
        return ColorUtil.colorize(t);
    }

    private String titleRequestInstallmentsMode() {
        String t = messages.getOptional("loan.request.title.installments_mode");
        if (t == null || t.isEmpty()) t = "&eInstallments Mode";
        return ColorUtil.colorize(t);
    }

    private String titleRequestInstallmentsCount() {
        String t = messages.getOptional("loan.request.title.installments_count");
        if (t == null || t.isEmpty()) t = "&eInstallments Count";
        return ColorUtil.colorize(t);
    }

    private String titleRequestInterval() {
        String t = messages.getOptional("loan.request.title.interval");
        if (t == null || t.isEmpty()) t = "&eInterval";
        return ColorUtil.colorize(t);
    }

    private String titleRequestFirstDelay() {
        String t = messages.getOptional("loan.request.title.first_delay");
        if (t == null || t.isEmpty()) t = "&eFirst Payment";
        return ColorUtil.colorize(t);
    }

    private void startWizard(Player player) {
        Wizard w = new Wizard();
        w.step = Step.TYPE;
        w.mode = null;
        w.installmentsMode = null;
        w.installments = 0;
        w.intervalHours = 0;
        w.firstDelayHours = 0;
        w.amount = null;
        w.installmentAmount = null;
        wizards.put(player.getUniqueId(), w);
        openTypeMenu(player, w);
    }

    private void openTypeMenu(Player player, Wizard w) {
        w.step = Step.TYPE;
        w.choices.clear();
        Inventory inv = Bukkit.createInventory(null, 27, titleRequestType());
        inv.setItem(11, item(Material.GOLD_BLOCK, display("loan.request.item.total", "&eTotal"), lore("loan.request.lore.total")));
        inv.setItem(15, item(Material.PAPER, display("loan.request.item.installments", "&aInstallments"), lore("loan.request.lore.installments")));
        inv.setItem(22, item(Material.ARROW, display("loan.request.item.back", "&7Back"), lore("loan.request.lore.back")));
        inv.setItem(26, item(Material.BARRIER, display("loan.request.item.cancel", "&cCancel"), lore("loan.request.lore.cancel")));
        fillEmptyWithGreenGlass(inv);
        player.openInventory(inv);
        wizards.put(player.getUniqueId(), w);
    }

    private void openDelayMenu(Player player, Wizard w) {
        w.step = Step.DELAY;
        w.choices.clear();
        Inventory inv = Bukkit.createInventory(null, 27, titleRequestDelay());
        putHoursOptions(inv, w, lore("loan.request.lore.delay"));
        inv.setItem(22, item(Material.ARROW, display("loan.request.item.back", "&7Back"), lore("loan.request.lore.back")));
        inv.setItem(26, item(Material.BARRIER, display("loan.request.item.cancel", "&cCancel"), lore("loan.request.lore.cancel")));
        fillEmptyWithGreenGlass(inv);
        player.openInventory(inv);
        wizards.put(player.getUniqueId(), w);
    }

    private void openInstallmentsModeMenu(Player player, Wizard w) {
        w.step = Step.MODE;
        w.choices.clear();
        Inventory inv = Bukkit.createInventory(null, 27, titleRequestInstallmentsMode());
        inv.setItem(11, item(Material.PAPER, display("loan.request.item.mode_count", "&eCount"), lore("loan.request.lore.mode_count")));
        inv.setItem(15, item(Material.GOLD_NUGGET, display("loan.request.item.mode_amount", "&eAmount"), lore("loan.request.lore.mode_amount")));
        inv.setItem(22, item(Material.ARROW, display("loan.request.item.back", "&7Back"), lore("loan.request.lore.back")));
        inv.setItem(26, item(Material.BARRIER, display("loan.request.item.cancel", "&cCancel"), lore("loan.request.lore.cancel")));
        fillEmptyWithGreenGlass(inv);
        player.openInventory(inv);
        wizards.put(player.getUniqueId(), w);
    }

    private void openInstallmentsCountMenu(Player player, Wizard w) {
        w.step = Step.INSTALLMENTS_COUNT;
        w.choices.clear();
        Inventory inv = Bukkit.createInventory(null, 27, titleRequestInstallmentsCount());
        putCountOptions(inv, w, lore("loan.request.lore.installments_count"));
        inv.setItem(22, item(Material.ARROW, display("loan.request.item.back", "&7Back"), lore("loan.request.lore.back")));
        inv.setItem(26, item(Material.BARRIER, display("loan.request.item.cancel", "&cCancel"), lore("loan.request.lore.cancel")));
        fillEmptyWithGreenGlass(inv);
        player.openInventory(inv);
        wizards.put(player.getUniqueId(), w);
    }

    private void openIntervalMenu(Player player, Wizard w) {
        w.step = Step.INTERVAL;
        w.choices.clear();
        Inventory inv = Bukkit.createInventory(null, 27, titleRequestInterval());
        putHoursOptions(inv, w, lore("loan.request.lore.interval"));
        inv.setItem(22, item(Material.ARROW, display("loan.request.item.back", "&7Back"), lore("loan.request.lore.back")));
        inv.setItem(26, item(Material.BARRIER, display("loan.request.item.cancel", "&cCancel"), lore("loan.request.lore.cancel")));
        fillEmptyWithGreenGlass(inv);
        player.openInventory(inv);
        wizards.put(player.getUniqueId(), w);
    }

    private void openFirstDelayMenu(Player player, Wizard w) {
        w.step = Step.FIRST_DELAY;
        w.choices.clear();
        Inventory inv = Bukkit.createInventory(null, 27, titleRequestFirstDelay());
        Material clock = getClockMaterial();
        inv.setItem(4, item(clock, display("loan.request.item.same_as_interval", "&eSame as interval"), lore("loan.request.lore.first_delay")));
        putHoursOptions(inv, w, lore("loan.request.lore.first_delay"));
        inv.setItem(22, item(Material.ARROW, display("loan.request.item.back", "&7Back"), lore("loan.request.lore.back")));
        inv.setItem(26, item(Material.BARRIER, display("loan.request.item.cancel", "&cCancel"), lore("loan.request.lore.cancel")));
        fillEmptyWithGreenGlass(inv);
        player.openInventory(inv);
        wizards.put(player.getUniqueId(), w);
    }

    private void promptAmount(Player player) {
        Wizard w = wizards.get(player.getUniqueId());
        if (w == null) return;
        player.closeInventory();
        ClickablePromptUtil.sendPromptWithClickableCancel(player, messages, "loan.prompt.amount",
                java.util.Collections.emptyMap(), getPrimaryCancelWord(), ClickEvent.Action.RUN_COMMAND, "/vault cancel");
    }

    private void promptInstallmentAmount(Player player) {
        Wizard w = wizards.get(player.getUniqueId());
        if (w == null) return;
        player.closeInventory();
        ClickablePromptUtil.sendPromptWithClickableCancel(player, messages, "loan.prompt.installment_amount",
                java.util.Collections.emptyMap(), getPrimaryCancelWord(), ClickEvent.Action.RUN_COMMAND, "/vault cancel");
    }

    private void handleWizardAmountInput(Player player, Wizard w, String msg) {
        Double amount = parsePositiveDouble(msg);
        if (amount == null) {
            player.sendMessage(messages.chat("loan.error.invalid_number"));
            return;
        }

        if (w.step == Step.WAIT_AMOUNT) {
            double min = plugin.getConfig().getDouble("loans.min_amount", 0.0);
            double max = plugin.getConfig().getDouble("loans.max_amount", 0.0);
            if (min > 0 && amount < min) {
                player.sendMessage(messages.formatChat("loan.error.too_small", java.util.Collections.singletonMap("min", loanServiceAmount(min))));
                return;
            }
            if (max > 0 && amount > max) {
                player.sendMessage(messages.formatChat("loan.error.too_large", java.util.Collections.singletonMap("max", loanServiceAmount(max))));
                return;
            }
            w.amount = amount;
            if (w.mode == Mode.TOTAL) {
                w.step = Step.DELAY;
                wizards.put(player.getUniqueId(), w);
                openDelayMenu(player, w);
                return;
            }
            w.step = Step.MODE;
            wizards.put(player.getUniqueId(), w);
            openInstallmentsModeMenu(player, w);
            return;
        }

        if (w.step == Step.WAIT_INSTALLMENT_AMOUNT) {
            w.installmentAmount = amount;
            w.step = Step.INTERVAL;
            wizards.put(player.getUniqueId(), w);
            openIntervalMenu(player, w);
        }
    }

    private void finalizeWizard(Player player, Wizard w) {
        if (w.amount == null || w.amount <= 0.0) {
            promptAmount(player);
            return;
        }
        double principal = w.amount;
        int installments;
        double installmentAmount;
        int interval = w.intervalHours;
        int firstDelay = w.firstDelayHours;

        if (w.mode == Mode.TOTAL) {
            installments = 1;
            installmentAmount = principal;
            interval = Math.max(1, firstDelay);
            boolean ok = loanService.createLoanFromWizard(player, principal, installments, installmentAmount, interval, firstDelay);
            wizards.remove(player.getUniqueId());
            if (ok) {
                Bukkit.getScheduler().runTask(plugin, () -> openLoanMenu(player));
            }
            return;
        }

        if (w.installmentsMode == InstallmentsMode.COUNT) {
            installments = Math.max(1, w.installments);
            installmentAmount = principal / installments;
        } else {
            installments = (w.installments > 0 ? w.installments : Math.max(1, (int) Math.ceil(principal / Math.max(0.01, w.installmentAmount != null ? w.installmentAmount : 0.01))));
            installmentAmount = (w.installmentAmount != null ? w.installmentAmount : (principal / installments));
        }

        boolean ok = loanService.createLoanFromWizard(player, principal, installments, installmentAmount, interval, firstDelay);
        if (ok) {
            wizards.remove(player.getUniqueId());
            Bukkit.getScheduler().runTask(plugin, () -> openLoanMenu(player));
        }
    }

    public boolean cancelWizardFlow(Player player) {
        if (!wizards.containsKey(player.getUniqueId())) return false;
        cancelWizard(player);
        return true;
    }

    private void cancelWizard(Player player) {
        wizards.remove(player.getUniqueId());
        player.closeInventory();
        player.sendMessage(messages.chat("loan.cancelled"));
        openLoanMenu(player);
    }

    private boolean isBackSlot(int slot, String name) {
        return slot == 22 || name.equals(display("loan.request.item.back", "&7Back"));
    }

    private boolean isCancelSlot(int slot, String name) {
        return slot == 26 || name.equals(display("loan.request.item.cancel", "&cCancel"));
    }

    private void putHoursOptions(Inventory inv, Wizard w, java.util.List<String> optionLore) {
        int[] hours = new int[] { 1, 6, 12, 24, 48, 72, 168 };
        int[] slots = new int[] { 10, 11, 12, 13, 14, 15, 16 };
        Material clock = getClockMaterial();
        for (int i = 0; i < hours.length && i < slots.length; i++) {
            int h = hours[i];
            int s = slots[i];
            w.choices.put(s, h);
            inv.setItem(s, item(clock, "&e" + h + "h", optionLore));
        }
    }

    private Material getClockMaterial() {
        Material clock = Material.matchMaterial("CLOCK");
        if (clock == null) clock = Material.matchMaterial("WATCH");
        if (clock == null) clock = Material.PAPER;
        return clock;
    }

    private void putCountOptions(Inventory inv, Wizard w, java.util.List<String> optionLore) {
        int[] counts = new int[] { 2, 3, 4, 6, 8, 12, 24, 36, 60 };
        int[] slots = new int[] { 10, 11, 12, 13, 14, 15, 16, 19, 25 };
        for (int i = 0; i < counts.length && i < slots.length; i++) {
            int c = counts[i];
            int s = slots[i];
            w.choices.put(s, c);
            inv.setItem(s, item(Material.PAPER, "&e" + c, optionLore));
        }
    }

    private void fillEmptyWithGreenGlass(Inventory inv) {
        ItemStack filler = createGreenGlassPane();
        for (int i = 0; i < inv.getSize(); i++) {
            ItemStack it = inv.getItem(i);
            if (it == null || it.getType() == Material.AIR) {
                inv.setItem(i, filler.clone());
            }
        }
    }

    private ItemStack createGreenGlassPane() {
        Material pane = Material.matchMaterial("GREEN_STAINED_GLASS_PANE");
        short data = 0;
        if (pane == null) {
            pane = Material.matchMaterial("STAINED_GLASS_PANE");
            data = 13;
        }
        if (pane == null) {
            pane = Material.matchMaterial("STAINED_GLASS");
            data = 13;
        }
        if (pane == null) {
            pane = Material.GLASS;
            data = 0;
        }
        ItemStack it = createItemStack(pane, 1, data);
        ItemMeta im = it.getItemMeta();
        if (im != null) {
            im.setDisplayName(" ");
            it.setItemMeta(im);
        }
        return it;
    }

    private java.util.List<String> getCancelWords() {
        String raw = messages.getOptional("loan.prompt.cancel_words");
        if (raw == null || raw.isEmpty() || raw.equals("loan.prompt.cancel_words")) {
            raw = messages.getOptional("pay.prompt.cancel_words");
        }
        if (raw == null || raw.isEmpty() || raw.equals("pay.prompt.cancel_words")) {
            raw = "cancel,cancelar";
        }
        String[] parts = raw.split(",");
        java.util.List<String> out = new java.util.ArrayList<>();
        for (String p : parts) {
            String v = org.bukkit.ChatColor.stripColor(com.example.vault.util.ColorUtil.colorize(p));
            if (v == null) continue;
            v = v.trim().toLowerCase(java.util.Locale.ROOT);
            if (!v.isEmpty()) out.add(v);
        }
        if (out.isEmpty()) out.add("cancel");
        return out;
    }

    private String getPrimaryCancelWord() {
        java.util.List<String> list = getCancelWords();
        return list.isEmpty() ? "cancel" : list.get(0);
    }

    private Double parsePositiveDouble(String s) {
        return ChatInputSanitizer.parsePositiveDouble(s);
    }

    private String loanServiceAmount(double amount) {
        try {
            net.milkbowl.vault.economy.Economy econ = net.milkbowl.vault.Vault.getEconomy();
            if (econ != null) return econ.format(amount);
        } catch (Throwable ignored) {
        }
        return String.valueOf(amount);
    }

    private static class Wizard {
        Step step;
        Mode mode;
        InstallmentsMode installmentsMode;
        int installments;
        int intervalHours;
        int firstDelayHours;
        Double amount;
        Double installmentAmount;
        java.util.Map<Integer, Integer> choices = new java.util.HashMap<>();
    }

    private enum Step {
        TYPE,
        WAIT_AMOUNT,
        DELAY,
        MODE,
        WAIT_INSTALLMENT_AMOUNT,
        INSTALLMENTS_COUNT,
        INTERVAL,
        FIRST_DELAY
    }

    private enum Mode {
        TOTAL,
        INSTALLMENTS
    }

    private enum InstallmentsMode {
        COUNT,
        AMOUNT
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
