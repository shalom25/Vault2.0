package com.example.vault.menu;

import com.example.vault.VaultPlugin;
import com.example.vault.economy.BankService;
import com.example.vault.economy.SimpleEconomy;
import com.example.vault.i18n.Messages;
import com.example.vault.util.ColorUtil;
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
import java.util.Locale;
import java.util.Map;

public final class BankMenuService implements Listener {

    private static final int INV_SIZE = 45;
    private static final int SLOT_WALLET = 10;
    private static final int SLOT_BANK = 13;
    private static final int SLOT_TOTAL = 16;
    private static final int SLOT_DEPOSIT = 20;
    private static final int SLOT_WITHDRAW = 24;
    private static final int SLOT_INFO = 22;
    private static final int SLOT_QP1 = 30;
    private static final int SLOT_QP4 = 33;
    private static final int SLOT_BACK = 36;

    private final VaultPlugin plugin;
    private final Messages messages;
    private final BankService bank;

    public BankMenuService(VaultPlugin plugin, Messages messages, BankService bank) {
        this.plugin = plugin;
        this.messages = messages;
        this.bank = bank;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    public void openBankMenu(Player player) {
        if (!(plugin.getEconomyProvider() instanceof SimpleEconomy)) {
            player.sendMessage(messages.chat("bank.unavailable"));
            return;
        }
        SimpleEconomy se = (SimpleEconomy) plugin.getEconomyProvider();
        String cid = se.getDefaultCurrencyId();
        double wallet = se.getBalance(cid, player);
        double bankBal = bank.getBankBalance(player.getUniqueId());
        double total = wallet + bankBal;

        boolean interestOn = bank.isInterestEnabled();
        double irateRaw = plugin.getConfig().getDouble("bank.interest.percent_per_period", 0.5);
        double irate = interestOn ? irateRaw : 0.0;
        long imin = Math.max(1L, plugin.getConfig().getLong("bank.interest.every_minutes", 60L));
        boolean taxOn = bank.isTaxEnabled();
        double trateRaw = plugin.getConfig().getDouble("bank.tax.percent_per_period", 0.0);
        double trate = taxOn ? trateRaw : 0.0;
        long tmin = Math.max(1L, plugin.getConfig().getLong("bank.tax.every_minutes", 180L));
        double tthresh = plugin.getConfig().getDouble("bank.tax.threshold", 1000000.0);
        double interestNext = interestOn ? Math.max(0, bankBal) * irate / 100.0 : 0.0;
        double taxNext = taxOn ? Math.max(0, Math.max(0, bankBal - tthresh) * trate / 100.0) : 0.0;
        String taxDesc;
        if (!taxOn || trate <= 0) {
            String t = messages.getOptional("bank.gui.tax_disabled");
            taxDesc = messages.colorize(t == null || t.isEmpty() ? "&aDesactivado" : t);
        } else if (bankBal <= tthresh) {
            String t = messages.getOptional("bank.gui.tax_under_threshold");
            taxDesc = messages.colorize(t == null || t.isEmpty() ? "&7Umbral no alcanzado" : t);
        } else {
            Map<String, String> tctx = new LinkedHashMap<>();
            tctx.put("tax_next", se.format(cid, taxNext));
            tctx.put("tax_pct", String.format("%.2f", trateRaw));
            String fmt = messages.getOptional("bank.gui.tax_over_threshold");
            if (fmt == null || fmt.isEmpty()) fmt = "%tax_next% &7(%tax_pct% sobre el excedente)";
            for (Map.Entry<String, String> e : tctx.entrySet()) {
                fmt = fmt.replace("%" + e.getKey() + "%", e.getValue());
            }
            taxDesc = messages.colorize(fmt);
        }
        String interestNextFmt;
        if (!interestOn || irate <= 0) {
            String t = messages.getOptional("bank.gui.interest_disabled");
            interestNextFmt = messages.colorize(t == null || t.isEmpty() ? "&aDesactivado" : t);
        } else {
            interestNextFmt = se.format(cid, interestNext);
        }

        Map<String, String> ctx = new LinkedHashMap<>();
        ctx.put("wallet", se.format(cid, wallet));
        ctx.put("bank", se.format(cid, bankBal));
        ctx.put("total", se.format(cid, total));
        ctx.put("interest_next", interestNextFmt);
        ctx.put("interest_pct", String.format("%.2f", irateRaw));
        ctx.put("interest_min", String.valueOf(imin));
        ctx.put("tax_pct", String.format("%.2f", trateRaw));
        ctx.put("tax_min", String.valueOf(tmin));
        ctx.put("tax_threshold", se.format(cid, tthresh));
        ctx.put("tax_next", se.format(cid, taxNext));
        ctx.put("tax_desc", messages.colorize(taxDesc));

        // Quick picks (monedero para deposit; banco para withdraw)
        double[] wqp = quickPicks(wallet, false);
        double[] bqp = quickPicks(bankBal, true);
        String qp1 = pct(wqp[0]);
        String qp2 = pct(wqp[1]);
        String qp3 = pct(wqp[2]);
        String qp4 = pct(wqp[3]);
        String qp10 = pct(10 * wqp[0]);
        String qp20 = pct(10 * wqp[1]);
        String qp50 = pct(10 * wqp[2]);

        ctx.put("qp1", qp1); ctx.put("qp2", qp2); ctx.put("qp3", qp3); ctx.put("qp4", qp4);
        ctx.put("qp10", qp10); ctx.put("qp20", qp20); ctx.put("qp50", qp50);

        Inventory inv = Bukkit.createInventory(null, INV_SIZE, title());
        inv.setItem(SLOT_WALLET, item(
                material(Material.CHEST, "CHEST_MINECART", "MINECART_CHEST", "STORAGE_MINECART"),
                fmtName("bank.gui.wallet_name", ctx),
                fmtList("bank.gui.wallet_lore", ctx)));
        inv.setItem(SLOT_BANK, item(
                material(Material.ENDER_CHEST, "ENDER_CHEST"),
                fmtName("bank.gui.bank_name", ctx),
                fmtList("bank.gui.bank_lore", ctx)));
        inv.setItem(SLOT_TOTAL, item(
                material(Material.BEACON, "BEACON"),
                fmtName("bank.gui.total_name", ctx),
                fmtList("bank.gui.total_lore", ctx)));
        inv.setItem(SLOT_DEPOSIT, item(
                Material.HOPPER,
                fmtName("bank.gui.deposit_name", ctx),
                fmtList("bank.gui.deposit_lore", ctx)));
        inv.setItem(SLOT_WITHDRAW, item(
                material(Material.DISPENSER, "DROPPER", "DISPENSER"),
                fmtName("bank.gui.withdraw_name", ctx),
                fmtList("bank.gui.withdraw_lore", ctx)));
        inv.setItem(SLOT_INFO, item(
                Material.PAPER,
                fmtName("bank.gui.info_name", ctx),
                fmtList("bank.gui.info_lore", ctx)));

        // Quick picks row (deposit: green glass pane; withdraw: red glass pane)
        Material paneDep = material(Material.matchMaterial("THIN_GLASS"), "GREEN_STAINED_GLASS_PANE", "STAINED_GLASS_PANE:13", "THIN_GLASS");
        Material paneWit = material(Material.matchMaterial("THIN_GLASS"), "RED_STAINED_GLASS_PANE", "STAINED_GLASS_PANE:14", "THIN_GLASS");

        short paneDepData = paneDep == Material.matchMaterial("THIN_GLASS") ? (short) 13 : 0;
        short paneWitData = paneWit == Material.matchMaterial("THIN_GLASS") ? (short) 14 : 0;

        for (int i = 0; i < 4; i++) {
            int slot = SLOT_QP1 + i;
            int n = i + 1;
            Map<String, String> cq = copy(ctx);
            double d = wqp[i];
            double amt = Math.max(0, Math.min(d, wallet));
            String keyAmt = "qp" + n + "_amount";
            String keyNew = "qp" + n + "_new";
            String keyWlt = "qp" + n + "_wallet";
            String keyBnk = "qp" + n + "_bank";
            String keyPct = "qp" + n;
            cq.put(keyAmt, se.format(cid, amt));
            cq.put(keyNew, se.format(cid, bankBal + amt));
            cq.put(keyWlt, se.format(cid, Math.max(0, wallet - amt)));
            cq.put(keyBnk, se.format(cid, bankBal + amt));
            if (d < 0.0001 && wallet > 0.0001) {
                cq.put(keyAmt, se.format(cid, 0));
                cq.put(keyNew, se.format(cid, bankBal));
            }
            cq.put(keyPct, pct(d));
            String shiftKey;
            double shiftBase;
            if (i == 0) { shiftKey = "qp10"; shiftBase = wqp[0]; }
            else if (i == 1) { shiftKey = "qp20"; shiftBase = wqp[1]; }
            else if (i == 2) { shiftKey = "qp50"; shiftBase = wqp[2]; }
            else { shiftKey = null; shiftBase = 0; }
            if (shiftKey != null) {
                double s = Math.min(wallet, Math.max(shiftBase * 10, shiftBase));
                cq.put(shiftKey, pct(s));
            }
            inv.setItem(slot, item(paneDep, 1, paneDepData,
                    fmtName("bank.gui.deposit_qp" + n + "_name", cq),
                    fmtList("bank.gui.deposit_qp" + n + "_lore", cq)));
        }
        for (int i = 0; i < 4; i++) {
            int slot = SLOT_QP1 + 9 + i; // row below QP deposit
            if (slot >= INV_SIZE) break;
            int n = i + 1;
            Map<String, String> cq = copy(ctx);
            double d = bqp[i];
            double amt = Math.max(0, Math.min(d, bankBal));
            String keyAmt = "qp" + n + "_amount";
            String keyNew = "qp" + n + "_new";
            String keyWlt = "qp" + n + "_wallet";
            String keyBnk = "qp" + n + "_bank";
            String keyPct = "qp" + n;
            cq.put(keyAmt, se.format(cid, amt));
            cq.put(keyNew, se.format(cid, Math.max(0, bankBal - amt)));
            cq.put(keyWlt, se.format(cid, wallet + amt));
            cq.put(keyBnk, se.format(cid, Math.max(0, bankBal - amt)));
            cq.put(keyPct, pct(d));
            String shiftKey;
            double shiftBase;
            if (i == 0) { shiftKey = "qp10"; shiftBase = bqp[0]; }
            else if (i == 1) { shiftKey = "qp20"; shiftBase = bqp[1]; }
            else if (i == 2) { shiftKey = "qp50"; shiftBase = bqp[2]; }
            else { shiftKey = null; shiftBase = 0; }
            if (shiftKey != null) {
                double s = Math.min(bankBal, Math.max(shiftBase * 10, shiftBase));
                cq.put(shiftKey, pct(s));
            }
            inv.setItem(slot, item(paneWit, 1, paneWitData,
                    fmtName("bank.gui.withdraw_qp" + n + "_name", cq),
                    fmtList("bank.gui.withdraw_qp" + n + "_lore", cq)));
        }

        inv.setItem(SLOT_BACK, item(
                material(Material.ARROW, "ARROW"),
                display("bank.gui.back_name", "&8◀ Volver"),
                lore("bank.gui.back_lore")));

        player.openInventory(inv);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;
        Player player = (Player) event.getWhoClicked();
        if (event.getClickedInventory() == null || !event.getClickedInventory().equals(event.getView().getTopInventory())) return;
        if (!event.getView().getTitle().equals(title())) return;
        event.setCancelled(true);
        ItemStack current = event.getCurrentItem();
        if (current == null || current.getType() == Material.AIR) return;
        ItemMeta meta = current.getItemMeta();
        String name = meta == null ? "" : meta.getDisplayName();
        if (name == null) name = "";

        if (!(plugin.getEconomyProvider() instanceof SimpleEconomy)) return;
        SimpleEconomy se = (SimpleEconomy) plugin.getEconomyProvider();
        String cid = se.getDefaultCurrencyId();
        double wallet = se.getBalance(cid, player);
        double bankBal = bank.getBankBalance(player.getUniqueId());

        if (name.equals(display("bank.gui.back_name", "&8◀ Volver"))) {
            try {
                plugin.getClass().getMethod("getVaultMenu").invoke(plugin);
            } catch (Throwable ignored) {}
            Object msvc = null;
            try {
                java.lang.reflect.Field f = plugin.getClass().getDeclaredField("vaultMenuService");
                f.setAccessible(true);
                msvc = f.get(plugin);
            } catch (Throwable ignored) {}
            if (msvc instanceof VaultMenuService) {
                ((VaultMenuService) msvc).openMainMenu(player);
            } else {
                player.closeInventory();
            }
            return;
        }

        // Deposit quick picks (row 4, slots SLOT_QP1..4)
        int slot = event.getSlot();
        boolean shift = event.isShiftClick();
        if (slot >= SLOT_QP1 && slot <= SLOT_QP4) {
            int i = slot - SLOT_QP1;
            double[] qp = quickPicks(wallet, false);
            double d;
            if (i < 3) {
                double base = Math.min(3, qp.length - 1) >= i ? qp[i] : 0;
                d = shift ? Math.min(wallet, Math.max(base * 10, base)) : base;
            } else {
                d = shift ? wallet : (qp.length > 3 ? qp[3] : wallet * 0.5);
            }
            d = Math.max(0, Math.min(d, wallet));
            if (d <= 0.0) {
                player.sendMessage(messages.chat("bank.positive_only"));
                return;
            }
            boolean ok = bank.depositBank(player.getUniqueId(), d);
            Map<String, String> ctx = new LinkedHashMap<>();
            ctx.put("amount", se.format(cid, d));
            ctx.put("bank_new", se.format(cid, bank.getBankBalance(player.getUniqueId())));
            if (!ok) { ctx.put("error", "deposit rejected"); player.sendMessage(messages.formatChat("bank.deposit.error", ctx)); }
            else player.sendMessage(messages.formatChat("bank.deposit.success", ctx));
            openBankMenu(player);
            return;
        }
        int withdrawFirst = SLOT_QP1 + 9;
        int withdrawLast = withdrawFirst + 3;
        if (slot >= withdrawFirst && slot <= withdrawLast && slot < INV_SIZE) {
            int i = slot - withdrawFirst;
            double[] qp = quickPicks(bankBal, true);
            double d;
            if (i < 3) {
                double base = Math.min(3, qp.length - 1) >= i ? qp[i] : 0;
                d = shift ? Math.min(bankBal, Math.max(base * 10, base)) : base;
            } else {
                d = shift ? bankBal : (qp.length > 3 ? qp[3] : bankBal);
            }
            d = Math.max(0, Math.min(d, bankBal));
            if (d <= 0.0) {
                player.sendMessage(messages.chat("bank.positive_only"));
                return;
            }
            boolean ok = bank.withdrawBank(player.getUniqueId(), d);
            Map<String, String> ctx = new LinkedHashMap<>();
            ctx.put("amount", se.format(cid, d));
            ctx.put("wallet_new", se.format(cid, se.getBalance(cid, player)));
            if (!ok) { ctx.put("error", "withdraw rejected"); player.sendMessage(messages.formatChat("bank.withdraw.error", ctx)); }
            else player.sendMessage(messages.formatChat("bank.withdraw.success", ctx));
            openBankMenu(player);
        }
    }

    private static double[] quickPicks(double amount, boolean includeAllLast) {
        // 10%, 25%, 50%, and either 100% (bank) or 75% (wallet, leave buffer)
        if (amount <= 0) return new double[]{0, 0, 0, 0};
        double tier1 = niceRound(amount * 0.10);
        double tier2 = niceRound(amount * 0.25);
        double tier3 = niceRound(amount * 0.50);
        double tier4 = includeAllLast ? amount : niceRound(amount * 0.75);
        return new double[]{
                Math.max(0, Math.min(amount, tier1)),
                Math.max(0, Math.min(amount, tier2)),
                Math.max(0, Math.min(amount, tier3)),
                Math.max(0, Math.min(amount, tier4))
        };
    }

    private static double niceRound(double x) {
        if (x <= 0) return 0;
        if (x >= 1_000_000_000) return Math.round(x / 100_000_000.0) * 100_000_000.0;
        if (x >= 1_000_000) return Math.round(x / 100_000.0) * 100_000.0;
        if (x >= 10_000) return Math.round(x / 1_000.0) * 1_000.0;
        if (x >= 1_000) return Math.round(x / 100.0) * 100.0;
        if (x >= 100) return Math.round(x / 10.0) * 10.0;
        if (x >= 10) return Math.round(x / 5.0) * 5.0;
        return Math.round(x * 100.0) / 100.0;
    }

    private static String pct(double d) {
        if (d < 0) d = 0;
        if (d >= 1_000_000_000) return String.format(Locale.ROOT, "%.1fB", d / 1_000_000_000.0);
        if (d >= 1_000_000) return String.format(Locale.ROOT, "%.1fM", d / 1_000_000.0);
        if (d >= 1_000) return String.format(Locale.ROOT, "%.0fK", d / 1_000.0);
        if (d >= 100) return String.format(Locale.ROOT, "%.0f", d);
        if (d >= 10) return String.format(Locale.ROOT, "%.1f", d);
        return String.format(Locale.ROOT, "%.2f", d);
    }

    private static Map<String, String> copy(Map<String, String> orig) {
        return new LinkedHashMap<>(orig);
    }

    private String title() {
        String t = messages.getOptional("bank.gui.title");
        if (t == null || t.isEmpty() || t.equals("bank.gui.title")) t = "&8🏦 Banco";
        return ColorUtil.colorize(t);
    }

    private String display(String key, String fallback) {
        String raw = messages.getOptional(key);
        if (raw == null || raw.isEmpty()) raw = fallback;
        return ColorUtil.colorize(raw);
    }

    private String fmtName(String key, Map<String, String> ctx) {
        String raw = messages.getOptional(key);
        if (raw == null || raw.isEmpty()) return ColorUtil.colorize("&f" + key);
        String replaced = raw;
        for (Map.Entry<String, String> e : ctx.entrySet()) {
            String v = e.getValue() == null ? "" : e.getValue();
            replaced = replaced.replace("%" + e.getKey() + "%", v);
        }
        return ColorUtil.colorize(replaced);
    }

    private List<String> fmtList(String key, Map<String, String> ctx) {
        List<String> base = messages.formatList(key, ctx);
        if (base == null) base = new ArrayList<>();
        List<String> out = new ArrayList<>(base.size());
        for (String s : base) out.add(ColorUtil.colorize(s));
        return out;
    }

    private List<String> lore(String key) {
        List<String> list = messages.colorList(key);
        if (list == null || list.isEmpty()) return new ArrayList<>();
        List<String> out = new ArrayList<>(list.size());
        for (String s : list) out.add(ColorUtil.colorize(s));
        return out;
    }

    private static Material material(Material fallback, String... names) {
        for (String n : names) {
            if (n == null) continue;
            Material m = Material.matchMaterial(n);
            if (m != null) return m;
        }
        return fallback != null ? fallback : Material.PAPER;
    }

    private ItemStack item(Material mat, String displayName, List<String> lore) {
        return item(mat, 1, (short) 0, displayName, lore);
    }

    private ItemStack item(Material mat, int amount, short legacyData, String displayName, List<String> lore) {
        ItemStack it;
        if (legacyData == 0) it = new ItemStack(mat, amount);
        else {
            try {
                java.lang.reflect.Constructor<ItemStack> c = ItemStack.class.getConstructor(Material.class, int.class, short.class);
                it = c.newInstance(mat, amount, legacyData);
            } catch (Throwable t) {
                it = new ItemStack(mat, amount);
                try {
                    java.lang.reflect.Method m = ItemStack.class.getMethod("setDurability", short.class);
                    m.invoke(it, legacyData);
                } catch (Throwable ignored) {}
            }
        }
        ItemMeta im = it.getItemMeta();
        if (im != null) {
            if (displayName != null) im.setDisplayName(ColorUtil.colorize(displayName));
            if (lore != null && !lore.isEmpty()) {
                List<String> out = new ArrayList<>(lore.size());
                for (String s : lore) out.add(ColorUtil.colorize(s));
                im.setLore(out);
            }
            it.setItemMeta(im);
        }
        return it;
    }
}
