package com.example.vault.menu;

import com.example.vault.VaultPlugin;
import com.example.vault.economy.SimpleEconomy;
import com.example.vault.i18n.Messages;
import com.example.vault.transactions.TxRecord;
import com.example.vault.transactions.TxType;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.Plugin;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class HistoryMenuService implements Listener {
    private static final int INVENTORY_SIZE = 54;
    private static final int TXS_PER_PAGE = 45;
    private static final int SLOT_PREV = 45;
    private static final int SLOT_INFO = 49;
    private static final int SLOT_NEXT = 53;
    private static final int SLOT_CLOSE = 50;

    private final Plugin plugin;
    private final Messages messages;
    private final Map<UUID, Integer> playerPages = new ConcurrentHashMap<>();
    private final Map<UUID, List<TxRecord>> playerCaches = new ConcurrentHashMap<>();
    private final Map<UUID, String> inventoryTitle = new ConcurrentHashMap<>();

    public HistoryMenuService(Plugin plugin, Messages messages) {
        this.plugin = plugin;
        this.messages = messages;
    }

    public void openHistory(Player player, int page0, List<TxRecord> txs) {
        if (txs == null) txs = new ArrayList<>();
        UUID id = player.getUniqueId();
        playerCaches.put(id, txs);
        int totalPages = Math.max(1, (txs.size() + TXS_PER_PAGE - 1) / TXS_PER_PAGE);
        int page = Math.max(1, Math.min(totalPages, page0));
        playerPages.put(id, page);
        String title = messages.color("history.gui.title");
        inventoryTitle.put(id, title);
        Inventory inv = Bukkit.createInventory(null, INVENTORY_SIZE, title);
        renderPage(inv, id, page, totalPages, txs, player);
        player.openInventory(inv);
    }

    private void renderPage(Inventory inv, UUID ownerId, int page, int totalPages, List<TxRecord> txs, Player viewer) {
        inv.clear();
        int start = (page - 1) * TXS_PER_PAGE;
        int end = Math.min(start + TXS_PER_PAGE, txs.size());
        int slot = 0;
        SimpleEconomy se = plugin instanceof VaultPlugin && ((VaultPlugin) plugin).getEconomyProvider() instanceof SimpleEconomy
                ? (SimpleEconomy) ((VaultPlugin) plugin).getEconomyProvider() : null;
        SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy HH:mm");
        for (int i = start; i < end; i++) {
            TxRecord tx = txs.get(i);
            inv.setItem(slot, buildTxItem(tx, se, viewer, sdf, i + 1));
            slot++;
        }
        Map<String, String> ctx = new LinkedHashMap<>();
        ctx.put("page", String.valueOf(page));
        ctx.put("pages", String.valueOf(totalPages));
        ctx.put("total", String.valueOf(txs.size()));
        inv.setItem(SLOT_PREV, buildNavItem(
                messages.color("history.gui.prev_name"),
                messages.colorize(messages.format("history.gui.prev_lore", ctx))));
        inv.setItem(SLOT_INFO, buildInfoItem(ctx));
        inv.setItem(SLOT_CLOSE, buildCloseItem());
        inv.setItem(SLOT_NEXT, buildNavItem(
                messages.color("history.gui.next_name"),
                messages.colorize(messages.format("history.gui.next_lore", ctx))));
    }

    private ItemStack buildTxItem(TxRecord tx, SimpleEconomy se, Player viewer, SimpleDateFormat sdf, int index) {
        Material type = Material.matchMaterial("FILLED_MAP");
        if (type == null) type = Material.matchMaterial("MAP");
        if (type == null) type = Material.PAPER;
        ItemStack stack = new ItemStack(type, 1);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            String cid = tx.getCurrencyId();
            TxType t = tx.getTxType();
            boolean inFlow = isInFlow(t, viewer.getUniqueId(), tx);
            String prefixKey = inFlow ? "history.gui.entry.title_prefix_in"
                    : (isNeutralFlow(t) ? "history.gui.entry.title_prefix_neutral"
                    : "history.gui.entry.title_prefix_out");
            String sign = inFlow ? "+" : "-";
            String amountStr = se != null ? se.format(cid, Math.abs(tx.getAmount()))
                    : String.format("%.2f", Math.abs(tx.getAmount()));
            String pretty = prettyType(t);
            meta.setDisplayName(messages.color(prefixKey) + ChatColor.WHITE + amountStr +
                    ChatColor.DARK_GRAY + "  [" + index + "] " + ChatColor.GRAY + pretty);
            List<String> lore = new ArrayList<>();
            lore.add(messages.color("history.gui.entry.lore.divider"));
            Map<String, String> ctx = new LinkedHashMap<>();
            ctx.put("sign", sign);
            ctx.put("amount", amountStr);
            ctx.put("currency", cid);
            ctx.put("from", prettyPlayer(tx.getFromUuid()));
            ctx.put("to", prettyPlayer(tx.getToUuid()));
            ctx.put("world", tx.getWorldName() == null || tx.getWorldName().isEmpty() ? "N/A" : tx.getWorldName());
            ctx.put("time", sdf.format(new Date(tx.getInstantMs())));
            ctx.put("txid", tx.getTxId().substring(0, Math.min(12, tx.getTxId().length())));
            lore.add(messages.colorize(messages.format("history.gui.entry.lore.txid", ctx)));
            lore.add(messages.colorize(messages.format("history.gui.entry.lore.amount", ctx)));
            lore.add(messages.colorize(messages.format("history.gui.entry.lore.from", ctx)));
            lore.add(messages.colorize(messages.format("history.gui.entry.lore.to", ctx)));
            if (tx.getWorldName() != null && !tx.getWorldName().isEmpty())
                lore.add(messages.colorize(messages.format("history.gui.entry.lore.world", ctx)));
            lore.add(messages.colorize(messages.format("history.gui.entry.lore.time", ctx)));
            if (tx.getMetadata() != null && !tx.getMetadata().isEmpty()) {
                lore.add("");
                lore.add(messages.color("history.gui.entry.lore.meta_header"));
                for (Map.Entry<String, String> e : tx.getMetadata().entrySet()) {
                    ctx.put("key", e.getKey());
                    ctx.put("value", safe(e.getValue()));
                    lore.add(messages.colorize(messages.format("history.gui.entry.lore.meta_entry", ctx)));
                }
            }
            meta.setLore(lore);
            stack.setItemMeta(meta);
        }
        return stack;
    }

    private static boolean isNeutralFlow(TxType t) { return t == null || t == TxType.ADMIN_SET; }

    private static boolean isInFlow(TxType t, UUID me, TxRecord tx) {
        if (t == null) return me != null && me.equals(tx.getToUuid());
        switch (t) {
            case DEPOSIT:
            case INTEREST:
            case NOTE_REDEEM:
            case OFFLINE_PAY_CLAIMED:
            case ADMIN_ADD:
            case LOAN_DISBURSE:
            case TEAM_DEPOSIT:
                return true;
            case WITHDRAW:
            case TAX:
            case NOTE_WITHDRAW:
            case PLAYER_PAY:
            case OFFLINE_PAY_SENT:
            case OFFLINE_PAY_REFUNDED:
            case ADMIN_REMOVE:
            case ADMIN_RESET:
            case LOAN_REPAY:
            case LOAN_DEFAULT:
            case LOAN_COLLATERAL_SEIZED:
            case CHARGE_PAID:
            case BANK_DEPOSIT:
            case TEAM_WITHDRAW:
            case TEAM_DISBAND_REFUND:
                return false;
            case BANK_WITHDRAW:
                return true;
            case ADMIN_SET:
                return tx.getAmount() >= 0;
            default:
                return me != null && me.equals(tx.getToUuid());
        }
    }

    private String prettyType(TxType t) {
        if (t == null) return "UNKNOWN";
        String k1 = "history.tx_type." + t.name();
        String v = messages.getOptional(k1);
        if (v != null && !v.isEmpty() && !v.equals(k1)) return messages.colorize(v);
        String k2 = "discord.tx_type." + t.name();
        v = messages.getOptional(k2);
        if (v != null && !v.isEmpty() && !v.equals(k2)) return messages.colorize(v);
        return t.name();
    }

    private static String prettyPlayer(UUID u) {
        if (u == null) return "N/A";
        // UUID 0L es consola/admin
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

    private static String safe(String s) {
        if (s == null) return "";
        return s.length() <= 60 ? s : s.substring(0, 57) + "...";
    }

    private static ItemStack buildNavItem(String name, String loreLine) {
        Material arrow = Material.matchMaterial("ARROW");
        if (arrow == null) arrow = Material.matchMaterial("SPECTRAL_ARROW");
        if (arrow == null) arrow = Material.PAPER;
        ItemStack s = new ItemStack(arrow, 1);
        ItemMeta m = s.getItemMeta();
        if (m != null) {
            m.setDisplayName(name);
            List<String> lore = new ArrayList<>();
            if (loreLine != null && !loreLine.isEmpty()) lore.add(loreLine);
            m.setLore(lore);
            s.setItemMeta(m);
        }
        return s;
    }

    private ItemStack buildInfoItem(Map<String, String> ctx) {
        Material paper = Material.matchMaterial("PAPER");
        ItemStack s = new ItemStack(paper, 1);
        ItemMeta m = s.getItemMeta();
        if (m != null) {
            m.setDisplayName(messages.color("history.gui.info_name"));
            List<String> lore = messages.formatList("history.gui.info_lore", ctx);
            if (lore.isEmpty()) {
                lore = new ArrayList<>();
                lore.add(ChatColor.GRAY + "Página " + ctx.get("page") + " / " + ctx.get("pages"));
                lore.add(ChatColor.GRAY + "Total transacciones: " + ChatColor.AQUA + ctx.get("total"));
            }
            m.setLore(lore);
            s.setItemMeta(m);
        }
        return s;
    }

    private ItemStack buildCloseItem() {
        Material bar = Material.matchMaterial("BARRIER");
        if (bar == null) bar = Material.matchMaterial("REDSTONE_BLOCK");
        if (bar == null) bar = Material.PAPER;
        ItemStack s = new ItemStack(bar, 1);
        ItemMeta m = s.getItemMeta();
        if (m != null) {
            m.setDisplayName(messages.color("history.gui.close_name"));
            List<String> lore = messages.colorList("history.gui.close_lore");
            if (lore.isEmpty()) {
                lore = new ArrayList<>();
                lore.add(ChatColor.GRAY + "Cierra esta ventana.");
            }
            m.setLore(lore);
            s.setItemMeta(m);
        }
        return s;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player)) return;
        Player p = (Player) e.getWhoClicked();
        UUID id = p.getUniqueId();
        if (e.getInventory() == null || e.getCurrentItem() == null) return;
        String title = inventoryTitle.get(id);
        if (title == null) return;
        try {
            // Comparar título de forma robusta (Inventory no siempre expone title en todas las versiones, por eso usamos nuestro Map + InventoryView
            String invTitle = e.getView().getTitle();
            if (invTitle == null || !title.equals(invTitle)) return;
        } catch (Throwable ignored) { return; }
        e.setCancelled(true);
        int raw = e.getRawSlot();
        if (raw < 0 || raw >= INVENTORY_SIZE) return;
        List<TxRecord> txs = playerCaches.get(id);
        if (txs == null) return;
        int totalPages = Math.max(1, (txs.size() + TXS_PER_PAGE - 1) / TXS_PER_PAGE);
        Integer current = playerPages.get(id);
        int cur = current == null ? 1 : current;
        if (raw == SLOT_PREV) {
            int np = Math.max(1, cur - 1);
            playerPages.put(id, np);
            renderPage(e.getInventory(), id, np, totalPages, txs, p);
        } else if (raw == SLOT_NEXT) {
            int np = Math.min(totalPages, cur + 1);
            playerPages.put(id, np);
            renderPage(e.getInventory(), id, np, totalPages, txs, p);
        } else if (raw == SLOT_CLOSE) {
            p.closeInventory();
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent e) {
        if (!(e.getPlayer() instanceof Player)) return;
        UUID id = e.getPlayer().getUniqueId();
        inventoryTitle.remove(id);
        playerPages.remove(id);
        playerCaches.remove(id);
    }
}
