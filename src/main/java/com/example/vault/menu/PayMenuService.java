package com.example.vault.menu;

import com.example.vault.i18n.Messages;
import com.example.vault.economy.SimpleEconomy;
import com.example.vault.util.ColorUtil;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.plugin.Plugin;

public class PayMenuService implements Listener {
    private static final double[] QUICK_PAY_AMOUNTS = {
            1000D, 2000D, 3000D, 4000D, 5000D, 6000D, 7000D, 8000D, 9000D,
            10000D, 20000D, 30000D, 40000D, 50000D, 60000D, 70000D, 80000D, 90000D,
            100000D, 200000D, 300000D, 500000D, 600000D, 700000D, 800000D, 900000D, 1000000D
    };
    private static final int QUICK_PAY_SIZE = 36;
    private static final int QUICK_PAY_MANUAL_SLOT = 31;
    private static final int QUICK_PAY_BACK_SLOT = 35;
    private final Plugin plugin;
    private final Economy economy;
    private final Messages messages;
    private final ChargeRequestService chargeRequestService;
    private final java.util.Map<java.util.UUID, String> submenuTargets = new java.util.concurrent.ConcurrentHashMap<>();
    private final java.util.Map<java.util.UUID, String> quickPayTargets = new java.util.concurrent.ConcurrentHashMap<>();

    public PayMenuService(Plugin plugin, Economy economy, Messages messages, ChargeRequestService chargeRequestService) {
        this.plugin = plugin;
        this.economy = economy;
        this.messages = messages;
        this.chargeRequestService = chargeRequestService;
    }

    public ChargeRequestService getChargeRequestService() {
        return chargeRequestService;
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

    // Helper: create a player head item compatible across versions
    private ItemStack createHeadItem() {
        try {
            Material head = Material.valueOf("PLAYER_HEAD");
            return new ItemStack(head, 1);
        } catch (IllegalArgumentException ignored) {
            Material skull = Material.valueOf("SKULL_ITEM");
            return createItemStack(skull, 1, (short) 3);
        }
    }

    // Helper: create a head for a specific player with meta set
    private ItemStack createHeadItemFor(Player target) {
        ItemStack head = createHeadItem();
        ItemMeta meta = head.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(target.getName());
            // Try to set owning player across versions
            try {
                SkullMeta skullMeta = (SkullMeta) meta;
                applyHeadOwner(skullMeta, target);
                head.setItemMeta(skullMeta);
            } catch (ClassCastException e) {
                head.setItemMeta(meta);
            }
        }
        return head;
    }

    private void applyHeadOwner(SkullMeta skullMeta, Player target) {
        if (applyLiveGameProfile(skullMeta, target)) {
            return;
        }

        if (applyLivePlayerProfile(skullMeta, target)) {
            return;
        }

        if (applySkinRestorerSkin(skullMeta, target)) {
            return;
        }

        try {
            java.lang.reflect.Method m = skullMeta.getClass().getMethod(
                    "setOwningPlayer", org.bukkit.OfflinePlayer.class);
            m.invoke(skullMeta, target);
            return;
        } catch (Throwable ignored) {
        }

        try {
            java.lang.reflect.Method m = skullMeta.getClass().getMethod("setOwner", String.class);
            m.invoke(skullMeta, target.getName());
        } catch (Throwable ignored) {
        }
    }

    private boolean applyLiveGameProfile(SkullMeta skullMeta, Player target) {
        try {
            java.lang.reflect.Method getProfile = target.getClass().getMethod("getProfile");
            Object profile = getProfile.invoke(target);
            if (profile == null) return false;
            return setGameProfile(skullMeta, profile);
        } catch (Throwable ignored) {
        }
        return false;
    }

    private boolean applyLivePlayerProfile(SkullMeta skullMeta, Player target) {
        // Modern APIs expose a PlayerProfile interface; look up the setter by name instead of concrete class.
        try {
            java.lang.reflect.Method getProfile = target.getClass().getMethod("getPlayerProfile");
            Object profile = getProfile.invoke(target);
            if (profile == null) return false;

            try {
                java.lang.reflect.Method setOwnerProfile = findSingleArgMethod(skullMeta.getClass(), "setOwnerProfile");
                if (setOwnerProfile != null && setOwnerProfile.getParameterTypes()[0].isInstance(profile)) {
                    setOwnerProfile.invoke(skullMeta, profile);
                    return true;
                }
            } catch (Throwable ignored) {
            }
            try {
                java.lang.reflect.Method setPlayerProfile = findSingleArgMethod(skullMeta.getClass(), "setPlayerProfile");
                if (setPlayerProfile != null && setPlayerProfile.getParameterTypes()[0].isInstance(profile)) {
                    setPlayerProfile.invoke(skullMeta, profile);
                    return true;
                }
            } catch (Throwable ignored) {
            }
        } catch (Throwable ignored) {
        }
        return false;
    }

    private java.lang.reflect.Method findSingleArgMethod(Class<?> type, String name) {
        for (java.lang.reflect.Method method : type.getMethods()) {
            if (!method.getName().equals(name) || method.getParameterCount() != 1) continue;
            method.setAccessible(true);
            return method;
        }
        return null;
    }

    private boolean applySkinRestorerSkin(SkullMeta skullMeta, Player target) {
        if (plugin.getServer().getPluginManager().getPlugin("SkinsRestorer") == null) {
            return false;
        }
        try {
            Class<?> providerClass = Class.forName("net.skinsrestorer.api.SkinsRestorerProvider");
            Object api = providerClass.getMethod("get").invoke(null);
            Object playerStorage = api.getClass().getMethod("getPlayerStorage").invoke(api);
            Object optional = getSkinPropertyFromSkinsRestorer(playerStorage, target);
            if (optional == null) return false;

            Boolean present = (Boolean) optional.getClass().getMethod("isPresent").invoke(optional);
            if (!Boolean.TRUE.equals(present)) return false;

            Object skinProperty = optional.getClass().getMethod("get").invoke(optional);
            String value = (String) skinProperty.getClass().getMethod("getValue").invoke(skinProperty);
            String signature = null;
            try {
                signature = (String) skinProperty.getClass().getMethod("getSignature").invoke(skinProperty);
            } catch (Throwable ignored) {
            }
            return applyTextureProperty(skullMeta, target, value, signature);
        } catch (Throwable ignored) {
            return false;
        }
    }

    private Object getSkinPropertyFromSkinsRestorer(Object playerStorage, Player target) throws Exception {
        try {
            java.lang.reflect.Method m = playerStorage.getClass().getMethod("getSkinForPlayer", java.util.UUID.class, String.class);
            return m.invoke(playerStorage, target.getUniqueId(), target.getName());
        } catch (NoSuchMethodException ignored) {
        }
        try {
            java.lang.reflect.Method m = playerStorage.getClass().getMethod("getSkinForPlayer", java.util.UUID.class, String.class, boolean.class);
            return m.invoke(playerStorage, target.getUniqueId(), target.getName(), false);
        } catch (NoSuchMethodException ignored) {
        }
        return null;
    }

    private boolean applyTextureProperty(SkullMeta skullMeta, Player target, String value, String signature) {
        if (value == null || value.isEmpty()) return false;
        try {
            Class<?> gameProfileClass = Class.forName("com.mojang.authlib.GameProfile");
            Class<?> propertyClass = Class.forName("com.mojang.authlib.properties.Property");
            Object profile = gameProfileClass
                    .getConstructor(java.util.UUID.class, String.class)
                    .newInstance(target.getUniqueId(), target.getName());

            Object propertyMap = gameProfileClass.getMethod("getProperties").invoke(profile);
            Object property = signature != null && !signature.isEmpty()
                    ? propertyClass.getConstructor(String.class, String.class, String.class)
                            .newInstance("textures", value, signature)
                    : propertyClass.getConstructor(String.class, String.class)
                            .newInstance("textures", value);

            propertyMap.getClass().getMethod("put", Object.class, Object.class).invoke(propertyMap, "textures", property);
            return setGameProfile(skullMeta, profile);
        } catch (Throwable ignored) {
        }
        return false;
    }

    private boolean setGameProfile(SkullMeta skullMeta, Object profile) {
        Class<?> gameProfileClass = profile.getClass();
        try {
            java.lang.reflect.Method setProfile = skullMeta.getClass().getDeclaredMethod("setProfile", gameProfileClass);
            setProfile.setAccessible(true);
            setProfile.invoke(skullMeta, profile);
            return true;
        } catch (Throwable ignored) {
        }

        try {
            java.lang.reflect.Field profileField = skullMeta.getClass().getDeclaredField("profile");
            profileField.setAccessible(true);
            profileField.set(skullMeta, profile);
            return true;
        } catch (Throwable ignored) {
        }
        return false;
    }

    private boolean isHeadMaterial(Material m) {
        String n = m.name();
        return n.equals("PLAYER_HEAD") || n.equals("SKULL_ITEM");
    }

    private String getTitleMain() {
        return messages.color("menu.title_main");
    }

    private String formatTitlePlayer(String playerName) {
        return formatTitle("menu.title_player", "&8&lActions: &f%player%", playerName);
    }

    private String formatTitleQuickPay(String playerName) {
        return formatTitle("pay.menu.title_quick_pay", "&8&lQuick pay: &f%player%", playerName);
    }

    private String formatTitle(String key, String fallback, String playerName) {
        java.util.Map<String, String> map = new java.util.HashMap<>();
        map.put("player", playerName);
        String raw = messages.getOptional(key);
        if (raw == null || raw.isEmpty()) raw = fallback;
        for (java.util.Map.Entry<String, String> entry : map.entrySet()) {
            raw = raw.replace("%" + entry.getKey() + "%", entry.getValue());
        }
        return ColorUtil.colorize(raw);
    }

    public void openMainMenu(Player player) {
        int size = plugin.getConfig().getInt("pay_menu.size", 27);
        if (size % 9 != 0) size = 27;
        Inventory inv = Bukkit.createInventory(null, size, getTitleMain());

        boolean showSelf = plugin.getConfig().getBoolean("pay_menu.show_self", false);
        int slot = 0;
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (!showSelf && p.getUniqueId().equals(player.getUniqueId())) continue;
            if (slot >= size) break;
            ItemStack head = createHeadItemFor(p);
            inv.setItem(slot++, head);
        }

        fillEmptyWithGreenGlass(inv);
        player.openInventory(inv);
    }

    @EventHandler(priority = org.bukkit.event.EventPriority.HIGHEST, ignoreCancelled = false)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;
        Player player = (Player) event.getWhoClicked();

        String title = event.getView().getTitle();
        org.bukkit.inventory.Inventory clickedInv = event.getClickedInventory();
        if (clickedInv == null) return;

        // Main menu: only react to clicks in the top inventory
        if (title.equals(getTitleMain())) {
            if (!clickedInv.equals(event.getView().getTopInventory())) return;
            event.setCancelled(true);
            ItemStack item = event.getCurrentItem();
            if (item == null || item.getType() == Material.AIR) {
                item = clickedInv.getItem(event.getSlot());
                if (item == null || item.getType() == Material.AIR) return;
            }
            if (isHeadMaterial(item.getType())) {
                String targetName = item.getItemMeta() != null ? item.getItemMeta().getDisplayName() : null;
                if (targetName != null) {
                    Player target = Bukkit.getPlayerExact(targetName);
                    if (target != null && target.isOnline()) {
                        openPlayerMenu(player, target);
                    } else {
                        player.sendMessage(messages.formatChat("pay.player_offline", java.util.Collections.singletonMap("player", targetName)));
                    }
                }
            }
            return;
        }

        // Quick pay submenu: only react to clicks in the top inventory
        String quickTargetName = quickPayTargets.get(player.getUniqueId());
        if (quickTargetName != null && title.equals(formatTitleQuickPay(quickTargetName))) {
            if (!clickedInv.equals(event.getView().getTopInventory())) return;
            event.setCancelled(true);
            Player target = Bukkit.getPlayerExact(quickTargetName);
            if (target == null || !target.isOnline()) {
                player.sendMessage(messages.formatChat("pay.player_offline", java.util.Collections.singletonMap("player", quickTargetName)));
                return;
            }

            int slot = event.getSlot();
            if (slot >= 0 && slot < QUICK_PAY_AMOUNTS.length) {
                payQuickAmount(player, target, QUICK_PAY_AMOUNTS[slot]);
                return;
            }
            if (slot == QUICK_PAY_MANUAL_SLOT) {
                chargeRequestService.startPay(player, target);
                chargeRequestService.requestAmountAndPay(player);
                return;
            }
            if (slot == QUICK_PAY_BACK_SLOT) {
                openPlayerMenu(player, target);
            }
            return;
        }

        // Submenu: ensure clicks are in the top inventory and resolve target
        String targetName = submenuTargets.get(player.getUniqueId());
        if (targetName != null && title.equals(formatTitlePlayer(targetName))) {
            if (!clickedInv.equals(event.getView().getTopInventory())) return;
            event.setCancelled(true);
            Player target = Bukkit.getPlayerExact(targetName);
            if (target == null || !target.isOnline()) {
                player.sendMessage(messages.formatChat("pay.player_offline", java.util.Collections.singletonMap("player", targetName)));
                return;
            }
            ItemStack item = event.getCurrentItem();
            if (item == null || item.getType() == Material.AIR) {
                item = clickedInv.getItem(event.getSlot());
                if (item == null || item.getType() == Material.AIR) return;
            }
            ItemMeta meta = item.getItemMeta();
            String name = meta != null ? meta.getDisplayName() : "";
            if (display("pay.menu.item.view_balance", "View balance").equals(name)) {
                String worldName = currentWorldName(player);
                if (economy instanceof SimpleEconomy) {
                    economy.createPlayerAccount(target, worldName);
                } else {
                    economy.createPlayerAccount(target);
                }
                java.util.Map<String, String> m = new java.util.HashMap<>();
                m.put("player", target.getName());
                double balance = economy instanceof SimpleEconomy
                        ? economy.getBalance(target, worldName)
                        : economy.getBalance(target);
                m.put("amount", economy.format(balance));
                player.sendMessage(messages.formatChat("pay.view.balance", m));
            } else if (display("pay.menu.item.pay", "Pay").equals(name)) {
                openQuickPayMenu(player, target);
            } else if (display("pay.menu.item.charge", "Charge").equals(name)) {
                chargeRequestService.startRequest(player, target);
                chargeRequestService.requestAmountAndCharge(player);
            } else if (display("pay.menu.item.loan", "Loan").equals(name)) {
                player.closeInventory();
                if (!player.hasPermission("vault.loan")) {
                    player.sendMessage(messages.chat("loan.no_permission"));
                    return;
                }
                if (plugin instanceof com.example.vault.VaultPlugin) {
                    com.example.vault.menu.VaultMenuService vms = ((com.example.vault.VaultPlugin) plugin).getVaultMenuService();
                    if (vms != null && vms.getLoanMenuService() != null) {
                        vms.getLoanMenuService().openLoanMenu(player);
                    }
                }
            }
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (event.getPlayer() instanceof Player) {
            java.util.UUID playerId = ((Player) event.getPlayer()).getUniqueId();
            submenuTargets.remove(playerId);
            quickPayTargets.remove(playerId);
        }
    }

    private void payQuickAmount(Player player, Player target, double amount) {
        chargeRequestService.cancelRequest(player);
        player.closeInventory();
        player.performCommand("pay " + target.getName() + " " + toPlainNumber(amount));
    }

    public void openPlayerMenu(Player player, Player target) {
        int size = 9;
        Inventory inv = Bukkit.createInventory(null, size, formatTitlePlayer(target.getName()));

        ItemStack payItem = new ItemStack(Material.EMERALD);
        applyMeta(payItem, display("pay.menu.item.pay", "Pay"), lore("pay.menu.lore.pay"));

        ItemStack viewItem = new ItemStack(Material.PAPER);
        ItemMeta viewMeta = viewItem.getItemMeta();
        if (viewMeta != null) {
            viewMeta.setDisplayName(display("pay.menu.item.view_balance", "View balance"));
            // Add lore with current balance of target
            String worldName = currentWorldName(player);
            if (economy instanceof SimpleEconomy) {
                economy.createPlayerAccount(target, worldName);
            } else {
                economy.createPlayerAccount(target);
            }
            double bal = economy instanceof SimpleEconomy
                    ? economy.getBalance(target, worldName)
                    : economy.getBalance(target);
            String formatted = economy.format(bal);
            String raw = String.valueOf(bal);
            String loreTemplate = messages.get("pay.menu.item.view_balance_lore");
            if (loreTemplate == null || loreTemplate.isEmpty() || "pay.menu.item.view_balance_lore".equals(loreTemplate)) {
                loreTemplate = "%player%: %vault_balance_formatted%";
            }
            String loreLine = resolveMenuPlaceholders(target, loreTemplate, bal, formatted, raw);
            java.util.List<String> lore = new java.util.ArrayList<>();
            lore.add(ColorUtil.colorize(loreLine));
            lore.addAll(resolveMenuLore(target, "pay.menu.lore.view_balance", bal, formatted, raw));
            viewMeta.setLore(lore);
            viewItem.setItemMeta(viewMeta);
        }

        ItemStack chargeItem = new ItemStack(Material.REDSTONE);
        applyMeta(chargeItem, display("pay.menu.item.charge", "Charge"), lore("pay.menu.lore.charge"));

        ItemStack loanItem = new ItemStack(Material.GOLD_INGOT);
        applyMeta(loanItem, display("pay.menu.item.loan", "Loan"), lore("pay.menu.lore.loan"));

        inv.setItem(1, payItem);
        inv.setItem(3, viewItem);
        inv.setItem(5, chargeItem);
        inv.setItem(7, loanItem);
        fillEmptyWithGreenGlass(inv);

        // Abrir primero, luego registrar el objetivo (evita que onClose limpie el mapa)
        player.openInventory(inv);
        Bukkit.getScheduler().runTask(plugin, () -> {
            quickPayTargets.remove(player.getUniqueId());
            submenuTargets.put(player.getUniqueId(), target.getName());
        });
    }

    public void openQuickPayMenu(Player player, Player target) {
        Inventory inv = Bukkit.createInventory(null, QUICK_PAY_SIZE, formatTitleQuickPay(target.getName()));

        for (int i = 0; i < QUICK_PAY_AMOUNTS.length; i++) {
            ItemStack amountItem = new ItemStack(Material.PAPER);
            applyMeta(amountItem, quickAmountName(QUICK_PAY_AMOUNTS[i]), quickAmountLore());
            inv.setItem(i, amountItem);
        }

        ItemStack manualItem = new ItemStack(Material.BOOK);
        applyMeta(manualItem, display("pay.menu.item.manual_amount", "&a&lCustom amount"), lore("pay.menu.lore.manual_amount"));
        inv.setItem(QUICK_PAY_MANUAL_SLOT, manualItem);

        ItemStack backItem = new ItemStack(Material.ARROW);
        applyMeta(backItem, display("pay.menu.item.back", "&c&lBack"), lore("pay.menu.lore.back"));
        inv.setItem(QUICK_PAY_BACK_SLOT, backItem);

        fillEmptyWithGreenGlass(inv);
        player.openInventory(inv);
        Bukkit.getScheduler().runTask(plugin, () -> {
            submenuTargets.remove(player.getUniqueId());
            quickPayTargets.put(player.getUniqueId(), target.getName());
        });
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

    private java.util.List<String> resolveMenuLore(Player target, String key, double balance, String formatted, String raw) {
        java.util.List<String> list = messages.getListOptional(key);
        if (list.isEmpty()) {
            String alt = alternateKey(key);
            if (!alt.equals(key)) list = messages.getListOptional(alt);
        }
        if (list.isEmpty()) return java.util.Collections.emptyList();

        java.util.List<String> out = new java.util.ArrayList<>(list.size());
        for (String line : list) {
            if (line == null || line.isEmpty()) continue;
            out.add(ColorUtil.colorize(resolveMenuPlaceholders(target, line, balance, formatted, raw)));
        }
        return out;
    }

    private String alternateKey(String key) {
        if (key.contains(".items.")) return key.replace(".items.", ".item.");
        if (key.contains(".item.")) return key.replace(".item.", ".items.");
        return key;
    }

    private void applyMeta(ItemStack it, String displayName, java.util.List<String> lore) {
        ItemMeta im = it.getItemMeta();
        if (im == null) return;
        im.setDisplayName(displayName);
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

    private String quickAmountName(double amount) {
        return ColorUtil.colorize("&6&l" + quickAmountLabel(amount));
    }

    private java.util.List<String> quickAmountLore() {
        java.util.List<String> lore = lore("pay.menu.lore.quick_pay");
        if (!lore.isEmpty()) return lore;
        return java.util.Collections.singletonList(ColorUtil.colorize("&7Click to pay this amount instantly."));
    }

    private String resolveMenuPlaceholders(Player target, String text, double balance, String formatted, String raw) {
        if (text == null || text.isEmpty()) return "";

        String resolved = text
                .replace("%player%", target.getName())
                .replace("%vault_balance_formatted%", formatted)
                .replace("%vault_balance%", raw)
                .replace("%vault_eco_balance%", raw)
                .replace("%vault_eco_balance_formatted%", formatted)
                .replace("%vault_eco_balance_fixed%", String.format(java.util.Locale.ROOT, "%.2f", balance))
                .replace("%vault_eco_balance_commas%", formatWithCommas(balance))
                .replace("%vault_eco_balance_short%", formatShort(balance))
                .replace("%vault_currency_symbol%", currencySymbol())
                .replace("%amount%", formatted)
                .replace("%amount_raw%", raw);

        return applyPlaceholderApi(target, resolved);
    }

    private String applyPlaceholderApi(Player target, String text) {
        if (text == null || text.isEmpty()) return text;
        if (plugin.getServer().getPluginManager().getPlugin("PlaceholderAPI") == null) return text;
        try {
            Class<?> papi = Class.forName("me.clip.placeholderapi.PlaceholderAPI");
            java.lang.reflect.Method method = papi.getMethod("setPlaceholders", org.bukkit.OfflinePlayer.class, String.class);
            Object resolved = method.invoke(null, target, text);
            if (resolved instanceof String) return (String) resolved;
        } catch (Throwable ignored) {
        }
        try {
            Class<?> papi = Class.forName("me.clip.placeholderapi.PlaceholderAPI");
            java.lang.reflect.Method method = papi.getMethod("setPlaceholders", Player.class, String.class);
            Object resolved = method.invoke(null, target, text);
            if (resolved instanceof String) return (String) resolved;
        } catch (Throwable ignored) {
        }
        return text;
    }

    private String formatShort(double balance) {
        if (economy instanceof SimpleEconomy) {
            SimpleEconomy se = (SimpleEconomy) economy;
            return se.formatShort(se.getDefaultCurrencyId(), balance);
        }
        return economy.format(balance);
    }

    private String formatWithCommas(double value) {
        java.text.DecimalFormatSymbols sym = java.text.DecimalFormatSymbols.getInstance(java.util.Locale.US);
        sym.setGroupingSeparator(',');
        sym.setDecimalSeparator('.');
        java.text.DecimalFormat df = new java.text.DecimalFormat("#,###.##", sym);
        df.setGroupingUsed(true);
        return df.format(value);
    }

    private String currencySymbol() {
        String raw = plugin.getConfig().getString("currency.symbol", "");
        if (raw == null) raw = "";
        return ColorUtil.colorize(raw);
    }

    private String currentWorldName(Player player) {
        return player != null && player.getWorld() != null ? player.getWorld().getName() : null;
    }

    private String quickAmountLabel(double amount) {
        if (amount >= 1000000D) {
            return trimTrailingZero(amount / 1000000D) + "M";
        }
        if (amount >= 1000D) {
            return trimTrailingZero(amount / 1000D) + "k";
        }
        return toPlainNumber(amount);
    }

    private String trimTrailingZero(double value) {
        java.math.BigDecimal bd = java.math.BigDecimal.valueOf(value).stripTrailingZeros();
        return bd.toPlainString();
    }

    private String toPlainNumber(double value) {
        java.math.BigDecimal bd = java.math.BigDecimal.valueOf(value).stripTrailingZeros();
        return bd.toPlainString();
    }
}
