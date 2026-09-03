package com.example.vault.menu;

import com.example.vault.i18n.Messages;
import com.example.vault.util.ChatInputSanitizer;
import com.example.vault.util.ClickablePromptUtil;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.chat.BaseComponent;

public class ChargeRequestService implements Listener {
    private final Plugin plugin;
    private final Economy economy;
    private final Messages messages;
    private final com.example.vault.storage.Database database;

    private final Map<String, List<PendingRequest>> pendingByRecipient = new ConcurrentHashMap<>();
    private final Set<String> awaitingAmount = ConcurrentHashMap.newKeySet();
    private final Map<String, String> targetBySender = new ConcurrentHashMap<>();
    // Enum de modo para distinguir entre pago directo y solicitud (cobro)
    private enum Mode { PAY, CHARGE }
    private final Map<String, Mode> modeBySender = new ConcurrentHashMap<>();

    public ChargeRequestService(Plugin plugin, Economy economy, Messages messages) {
        this(plugin, economy, messages, null);
    }

    public ChargeRequestService(Plugin plugin, Economy economy, Messages messages, com.example.vault.storage.Database database) {
        this.plugin = plugin;
        this.economy = economy;
        this.messages = messages;
        this.database = database;
    }

    private static class PendingRequest {
        final String sender;
        final double amount;

        PendingRequest(String sender, double amount) {
            this.sender = sender;
            this.amount = amount;
        }
    }

    private BaseComponent[] legacyComponents(String legacy) {
        if (legacy == null || legacy.isEmpty()) return new BaseComponent[0];
        try {
            java.lang.reflect.Method m = TextComponent.class.getMethod("fromLegacy", String.class);
            Object out = m.invoke(null, legacy);
            if (out instanceof BaseComponent[]) return (BaseComponent[]) out;
        } catch (Throwable ignored) {
        }
        try {
            java.lang.reflect.Method m = TextComponent.class.getMethod("fromLegacyText", String.class);
            Object out = m.invoke(null, legacy);
            if (out instanceof BaseComponent[]) return (BaseComponent[]) out;
        } catch (Throwable ignored) {
        }
        return new BaseComponent[] { new TextComponent(legacy) };
    }

    private HoverEvent hoverShowText(String legacyText) {
        BaseComponent[] components = legacyComponents(legacyText);
        try {
            java.lang.reflect.Constructor<HoverEvent> ctor = HoverEvent.class.getConstructor(HoverEvent.Action.class, BaseComponent[].class);
            return ctor.newInstance(HoverEvent.Action.SHOW_TEXT, components);
        } catch (Throwable ignored) {
        }
        try {
            Class<?> contentClass = Class.forName("net.md_5.bungee.api.chat.hover.content.Content");
            Class<?> textClass = Class.forName("net.md_5.bungee.api.chat.hover.content.Text");
            java.lang.reflect.Constructor<?> textCtor = textClass.getConstructor(BaseComponent[].class);
            Object text = textCtor.newInstance((Object) components);
            Object arr = java.lang.reflect.Array.newInstance(contentClass, 1);
            java.lang.reflect.Array.set(arr, 0, text);
            java.lang.reflect.Constructor<?> hoverCtor = HoverEvent.class.getConstructor(HoverEvent.Action.class, arr.getClass());
            return (HoverEvent) hoverCtor.newInstance(HoverEvent.Action.SHOW_TEXT, arr);
        } catch (Throwable ignored) {
        }
        return null;
    }

    private void addLegacyText(TextComponent parent, String legacyText) {
        for (BaseComponent bc : legacyComponents(legacyText)) {
            parent.addExtra(bc);
        }
    }

    private void addClickableLegacyText(TextComponent parent, String legacyText, ClickEvent clickEvent, HoverEvent hoverEvent) {
        for (BaseComponent bc : legacyComponents(legacyText)) {
            bc.setClickEvent(clickEvent);
            if (hoverEvent != null) bc.setHoverEvent(hoverEvent);
            parent.addExtra(bc);
        }
    }

    public void addPending(String recipientName, String senderName, double amount) {
        pendingByRecipient.computeIfAbsent(recipientName.toLowerCase(Locale.ROOT), k -> new ArrayList<>())
                .add(new PendingRequest(senderName, amount));
    }

    public void cancelRequest(Player player) {
        awaitingAmount.remove(player.getName());
        targetBySender.remove(player.getName());
        modeBySender.remove(player.getName());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        cancelRequest(event.getPlayer());
    }

    // New: start PAY flow – set target and mode
    public void startPay(Player sender, Player target) {
        targetBySender.put(sender.getName(), target.getName());
        modeBySender.put(sender.getName(), Mode.PAY);
    }

    // New: prompt for amount to PAY via chat
    public void requestAmountAndPay(Player sender) {
        awaitingAmount.add(sender.getName());
        sender.closeInventory();
        java.util.Map<String, String> map = new java.util.HashMap<>();
        map.put("player", targetBySender.getOrDefault(sender.getName(), ""));
        ClickablePromptUtil.sendPromptWithClickableCancel(sender, messages, "pay.prompt.enter_amount_pay", map,
                getPrimaryCancelWord(), ClickEvent.Action.RUN_COMMAND, "/pay cancel");
    }

    // New: start CHARGE flow – set target and mode
    public void startRequest(Player sender, Player target) {
        targetBySender.put(sender.getName(), target.getName());
        modeBySender.put(sender.getName(), Mode.CHARGE);
    }

    // New: prompt for amount to CHARGE via chat
    public void requestAmountAndCharge(Player sender) {
        awaitingAmount.add(sender.getName());
        sender.closeInventory();
        java.util.Map<String, String> map = new java.util.HashMap<>();
        map.put("player", targetBySender.getOrDefault(sender.getName(), ""));
        ClickablePromptUtil.sendPromptWithClickableCancel(sender, messages, "pay.prompt.enter_amount_charge", map,
                getPrimaryCancelWord(), ClickEvent.Action.RUN_COMMAND, "/pay cancel");
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        int max = plugin.getConfig().getInt("pay_pending.max_on_join", 5);

        java.util.List<PendingRequest> list = new java.util.ArrayList<>();

        if (database != null) {
            try {
                for (com.example.vault.storage.Database.ChargeRequest cr :
                        database.fetchAndDeletePendingRequests(player.getName(), max)) {
                    list.add(new PendingRequest(cr.sender, cr.amount));
                }
            } catch (java.sql.SQLException ex) {
                plugin.getLogger().warning("Failed to fetch charge requests from MySQL: " + ex.getMessage());
            }
        } else {
            java.util.List<PendingRequest> stored = pendingByRecipient.get(player.getName().toLowerCase(java.util.Locale.ROOT));
            if (stored == null || stored.isEmpty()) return;
            int total = stored.size();
            int shown = Math.min(max, total);
            player.sendMessage(messages.formatChat("pay.pending.header", java.util.Collections.singletonMap("count", String.valueOf(total))));
            if (total > shown) {
                java.util.Map<String, String> m = new java.util.HashMap<>();
                m.put("shown", String.valueOf(shown));
                m.put("total", String.valueOf(total));
                player.sendMessage(messages.formatChat("pay.pending.limit_notice", m));
            }
            for (PendingRequest pr : stored.subList(0, shown)) {
                java.util.Map<String, String> m2 = new java.util.HashMap<>();
                m2.put("player", pr.sender);
                m2.put("amount", toPlainNumber(pr.amount));
                m2.put("amount_formatted", economy.format(pr.amount));
                net.md_5.bungee.api.chat.TextComponent combined = new net.md_5.bungee.api.chat.TextComponent("");
                String prefix = messages.prefix();
                if (!prefix.isEmpty()) {
                    for (net.md_5.bungee.api.chat.BaseComponent bc : legacyComponents(prefix)) combined.addExtra(bc);
                    combined.addExtra(new net.md_5.bungee.api.chat.TextComponent(" "));
                }
                addLegacyText(combined, messages.colorize(messages.get("pay.request.prefix")));
                net.md_5.bungee.api.chat.ClickEvent clickEvent = new net.md_5.bungee.api.chat.ClickEvent(
                        net.md_5.bungee.api.chat.ClickEvent.Action.RUN_COMMAND, "/pay " + pr.sender + " " + toPlainNumber(pr.amount));
                HoverEvent hoverEvent = hoverShowText(messages.colorize(messages.format("pay.request.hover", m2)));
                addClickableLegacyText(combined, messages.colorize(messages.format("pay.request.click", m2)), clickEvent, hoverEvent);
                player.spigot().sendMessage(combined);
            }
            if (total > shown) {
                pendingByRecipient.put(player.getName().toLowerCase(java.util.Locale.ROOT), new java.util.ArrayList<>(stored.subList(shown, total)));
            } else {
                pendingByRecipient.remove(player.getName().toLowerCase(java.util.Locale.ROOT));
            }
            return;
        }

        if (list.isEmpty()) return;
        int total = list.size();
        int shown = Math.min(max, total);
        player.sendMessage(messages.formatChat("pay.pending.header", java.util.Collections.singletonMap("count", String.valueOf(total))));
        if (total > shown) {
            java.util.Map<String, String> m = new java.util.HashMap<>();
            m.put("shown", String.valueOf(shown));
            m.put("total", String.valueOf(total));
            player.sendMessage(messages.formatChat("pay.pending.limit_notice", m));
        }
        for (PendingRequest pr : list.subList(0, shown)) {
            java.util.Map<String, String> m2 = new java.util.HashMap<>();
            m2.put("player", pr.sender);
            m2.put("amount", toPlainNumber(pr.amount));
            m2.put("amount_formatted", economy.format(pr.amount));
            net.md_5.bungee.api.chat.TextComponent combined = new net.md_5.bungee.api.chat.TextComponent("");
            String prefix = messages.prefix();
            if (!prefix.isEmpty()) {
                for (net.md_5.bungee.api.chat.BaseComponent bc : legacyComponents(prefix)) combined.addExtra(bc);
                combined.addExtra(new net.md_5.bungee.api.chat.TextComponent(" "));
            }
            addLegacyText(combined, messages.colorize(messages.get("pay.request.prefix")));
            net.md_5.bungee.api.chat.ClickEvent clickEvent = new net.md_5.bungee.api.chat.ClickEvent(
                    net.md_5.bungee.api.chat.ClickEvent.Action.RUN_COMMAND, "/pay " + pr.sender + " " + toPlainNumber(pr.amount));
            HoverEvent hoverEvent = hoverShowText(messages.colorize(messages.format("pay.request.hover", m2)));
            addClickableLegacyText(combined, messages.colorize(messages.format("pay.request.click", m2)), clickEvent, hoverEvent);
            player.spigot().sendMessage(combined);
        }
        // En modo MySQL, las no mostradas permanecen en DB (ya hicimos delete tras fetch de mostradas).
    }

    private void fulfillRequest(Player sender, double amount) {
        String recipientName = targetBySender.get(sender.getName());
        if (recipientName == null || recipientName.isEmpty()) {
            sender.sendMessage(messages.chat("cmd.pay.usage"));
            return;
        }
        Player recipient = Bukkit.getPlayerExact(recipientName);
        if (recipient != null && recipient.isOnline()) {
            String senderName = sender.getName();
            Map<String, String> m = new HashMap<>();
            m.put("player", senderName);
            m.put("amount", toPlainNumber(amount));
            m.put("amount_formatted", economy.format(amount));
            TextComponent combined = new TextComponent("");
            String prefix = messages.prefix();
            if (!prefix.isEmpty()) {
                for (BaseComponent bc : legacyComponents(prefix)) combined.addExtra(bc);
                combined.addExtra(new TextComponent(" "));
            }
            addLegacyText(combined, messages.colorize(messages.get("pay.request.prefix")));
            ClickEvent clickEvent = new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/pay " + senderName + " " + toPlainNumber(amount));
            HoverEvent hoverEvent = hoverShowText(messages.colorize(messages.format("pay.request.hover", m)));
            addClickableLegacyText(combined, messages.colorize(messages.format("pay.request.click", m)), clickEvent, hoverEvent);
            recipient.spigot().sendMessage(combined);
            Map<String, String> ms = new HashMap<>();
            ms.put("player", recipientName);
            ms.put("amount", toPlainNumber(amount));
            ms.put("amount_formatted", economy.format(amount));
            sender.sendMessage(messages.formatChat("pay.request.sent", ms));
            awaitingAmount.remove(sender.getName());
            targetBySender.remove(sender.getName());
            return;
        }
        // Recipient offline: store request
        if (database != null) {
            try {
                database.addChargeRequest(recipientName, sender.getName(), amount);
            } catch (java.sql.SQLException ex) {
                plugin.getLogger().warning("Failed to store charge request in MySQL: " + ex.getMessage());
                // fallback a memoria si falla
                addPending(recipientName, sender.getName(), amount);
            }
        } else {
            addPending(recipientName, sender.getName(), amount);
        }
        Map<String, String> ms2 = new HashMap<>();
        ms2.put("player", recipientName);
        ms2.put("amount", toPlainNumber(amount));
        ms2.put("amount_formatted", economy.format(amount));
        sender.sendMessage(messages.formatChat("pay.request.stored", ms2));
        awaitingAmount.remove(sender.getName());
        targetBySender.remove(sender.getName());
    }

    @EventHandler(priority = org.bukkit.event.EventPriority.LOWEST, ignoreCancelled = false)
    public void onChat(AsyncPlayerChatEvent event) {
        Player sender = event.getPlayer();
        if (!awaitingAmount.contains(sender.getName())) return;
        String message = ChatInputSanitizer.sanitizeChatInput(event.getMessage());

        // Allow cancel in chat
        String lower = message.toLowerCase(Locale.ROOT);
        for (String w : getCancelWords()) {
            if (lower.equals(w)) {
                cancelRequest(sender);
                event.setCancelled(true);
                return;
            }
        }

        Double parsed = ChatInputSanitizer.parsePositiveDouble(message);
        if (parsed == null) {
            sender.sendMessage(messages.chat("pay.invalid_amount"));
            event.setCancelled(true);
            return;
        }
        double amount = parsed;

        Mode m = modeBySender.get(sender.getName());
        String targetName = targetBySender.get(sender.getName());
        if (m == null || targetName == null || targetName.trim().isEmpty()) {
            cancelRequest(sender);
            sender.sendMessage(messages.chat("cmd.pay.usage"));
            event.setCancelled(true);
            return;
        }

        double min = plugin.getConfig().getDouble("pay_limits.min", 0.0);
        double max = plugin.getConfig().getDouble("pay_limits.max", 0.0);
        boolean bypass = sender.hasPermission("vault.pay.bypass_limits") || sender.hasPermission("vault.pay.bypass_min") || sender.hasPermission("vault.pay.bypass_max");
        if (!bypass && min > 0 && amount < min) {
            sender.sendMessage(messages.formatChat("pay.amount_too_small", Collections.singletonMap("min", economy.format(min))));
            event.setCancelled(true);
            return;
        }
        if (!bypass && max > 0 && amount > max) {
            sender.sendMessage(messages.formatChat("pay.amount_too_large", Collections.singletonMap("max", economy.format(max))));
            event.setCancelled(true);
            return;
        }

        // Decide mode: direct PAY or CHARGE (request)
        if (m == Mode.PAY) {
            final String target = targetName;
            final double amt = amount;
            // cleanup first
            awaitingAmount.remove(sender.getName());
            modeBySender.remove(sender.getName());
            targetBySender.remove(sender.getName());
            if (target == null || target.isEmpty()) {
                sender.sendMessage(messages.chat("cmd.pay.usage"));
                event.setCancelled(true);
                return;
            }
            // Execute /pay target amount on main thread
            Bukkit.getScheduler().runTask(plugin, () -> sender.performCommand("pay " + target + " " + amt));
            event.setCancelled(true);
            return;
        }

        // Otherwise: CHARGE request flow
        fulfillRequest(sender, amount);
        event.setCancelled(true);
    }

    private java.util.List<String> getCancelWords() {
        String raw = messages.get("pay.prompt.cancel_words");
        if (raw == null || raw.isEmpty() || raw.equals("pay.prompt.cancel_words")) {
            raw = "cancel,cancelar";
        }
        String[] parts = raw.split(",");
        java.util.List<String> out = new java.util.ArrayList<>();
        for (String p : parts) {
            String s = org.bukkit.ChatColor.stripColor(com.example.vault.util.ColorUtil.colorize(p));
            if (s == null) continue;
            s = s.trim().toLowerCase(java.util.Locale.ROOT);
            if (!s.isEmpty()) out.add(s);
        }
        if (out.isEmpty()) out.add("cancel");
        return out;
    }

    private String getPrimaryCancelWord() {
        java.util.List<String> words = getCancelWords();
        return words.isEmpty() ? "cancel" : words.get(0);
    }

    private String toPlainNumber(double value) {
        java.math.BigDecimal bd = java.math.BigDecimal.valueOf(value).stripTrailingZeros();
        return bd.toPlainString();
    }

}
