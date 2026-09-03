package com.example.vault.loans;

import com.example.vault.i18n.Messages;
import com.example.vault.util.ChatInputSanitizer;
import com.example.vault.util.ClickablePromptUtil;
import net.md_5.bungee.api.chat.ClickEvent;
import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;

import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class LoanService implements Listener {
    private final Plugin plugin;
    private final Economy economy;
    private final Messages messages;
    private final LoanStorage storage;
    private final Map<UUID, Loan> loans = new ConcurrentHashMap<>();
    private final Map<UUID, Conversation> conversations = new ConcurrentHashMap<>();
    private BukkitTask task;
    private BukkitTask defaultedEffectTask;

    public LoanService(Plugin plugin, Economy economy, Messages messages, LoanStorage storage) {
        this.plugin = plugin;
        this.economy = economy;
        this.messages = messages;
        this.storage = storage;
        try {
            this.loans.putAll(storage.loadAll());
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to load loans.yml: " + e.getMessage());
        }
    }

    public void start() {
        stop();
        if (!plugin.getConfig().getBoolean("loans.enabled", true)) {
            return;
        }
        int seconds = plugin.getConfig().getInt("loans.charge_check_seconds", 60);
        if (seconds <= 0) seconds = 60;
        long period = 20L * seconds;
        task = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, this::scanAndCharge, period, period);
        startDefaultedEffectTask();
    }

    public void stop() {
        if (task != null) {
            try { task.cancel(); } catch (Exception ignored) {}
            task = null;
        }
        if (defaultedEffectTask != null) {
            try { defaultedEffectTask.cancel(); } catch (Exception ignored) {}
            defaultedEffectTask = null;
        }
    }

    private void startDefaultedEffectTask() {
        if (!plugin.getConfig().getBoolean("loans.defaulted_effects.enabled", false)) return;
        int refreshSeconds = plugin.getConfig().getInt("loans.defaulted_effects.refresh_seconds", 5);
        if (refreshSeconds <= 0) refreshSeconds = 5;
        int durationSeconds = plugin.getConfig().getInt("loans.defaulted_effects.duration_seconds", 8);
        if (durationSeconds <= 0) durationSeconds = 8;

        java.util.List<String> raw = plugin.getConfig().getStringList("loans.defaulted_effects.effects");
        if (raw == null || raw.isEmpty()) {
            raw = java.util.Arrays.asList("SLOW:1", "SLOW_DIGGING:1");
        }
        java.util.List<PotionEffectSpec> specs = new java.util.ArrayList<>();
        for (String s : raw) {
            if (s == null) continue;
            String v = s.trim();
            if (v.isEmpty()) continue;
            String[] parts = v.split(":", 2);
            String typeName = parts[0].trim().toUpperCase(java.util.Locale.ROOT);
            if ("FATIGUE".equals(typeName)) typeName = "SLOW_DIGGING";
            if ("SLOWNESS".equals(typeName)) typeName = "SLOW";
            int amplifier = 0;
            if (parts.length == 2) {
                try {
                    amplifier = Math.max(0, Integer.parseInt(parts[1].trim()) - 1);
                } catch (NumberFormatException ignored) {
                    amplifier = 0;
                }
            }
            PotionEffectType type = resolvePotionEffectType(typeName);
            if (type == null) continue;
            specs.add(new PotionEffectSpec(type, amplifier));
        }
        if (specs.isEmpty()) return;

        int durationTicks = durationSeconds * 20;
        long period = refreshSeconds * 20L;
        defaultedEffectTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> applyDefaultedEffects(specs, durationTicks), period, period);
    }

    private void applyDefaultedEffects(java.util.List<PotionEffectSpec> specs, int durationTicks) {
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p == null || !p.isOnline()) continue;
            Loan loan = loans.get(p.getUniqueId());
            if (loan == null || loan.getStatus() != LoanStatus.DEFAULTED) continue;
            for (PotionEffectSpec spec : specs) {
                try {
                    PotionEffect effect = new PotionEffect(spec.type, durationTicks, spec.amplifier, true, false);
                    p.removePotionEffect(spec.type);
                    p.addPotionEffect(effect);
                } catch (Throwable ignored) {
                }
            }
        }
    }

    private PotionEffectType resolvePotionEffectType(String typeName) {
        if (typeName == null || typeName.isEmpty()) return null;
        try {
            java.lang.reflect.Method m = PotionEffectType.class.getMethod("getByName", String.class);
            Object out = m.invoke(null, typeName);
            if (out instanceof PotionEffectType) return (PotionEffectType) out;
        } catch (Throwable ignored) {
        }
        try {
            String key = legacyEffectKey(typeName);
            Class<?> namespacedKeyClass = Class.forName("org.bukkit.NamespacedKey");
            java.lang.reflect.Method minecraft = namespacedKeyClass.getMethod("minecraft", String.class);
            Object namespacedKey = minecraft.invoke(null, key.toLowerCase(Locale.ROOT));
            java.lang.reflect.Method byKey = PotionEffectType.class.getMethod("getByKey", namespacedKeyClass);
            Object out = byKey.invoke(null, namespacedKey);
            if (out instanceof PotionEffectType) return (PotionEffectType) out;
        } catch (Throwable ignored) {
        }
        return null;
    }

    private String legacyEffectKey(String typeName) {
        String t = typeName.trim().toUpperCase(Locale.ROOT);
        return switch (t) {
            case "SLOW" -> "slowness";
            case "SLOW_DIGGING" -> "mining_fatigue";
            case "FAST_DIGGING" -> "haste";
            case "INCREASE_DAMAGE" -> "strength";
            case "HEAL" -> "instant_health";
            case "HARM" -> "instant_damage";
            case "JUMP" -> "jump_boost";
            case "CONFUSION" -> "nausea";
            case "DAMAGE_RESISTANCE" -> "resistance";
            case "FIRE_RESISTANCE" -> "fire_resistance";
            case "WATER_BREATHING" -> "water_breathing";
            case "INVISIBILITY" -> "invisibility";
            case "BLINDNESS" -> "blindness";
            case "NIGHT_VISION" -> "night_vision";
            case "HUNGER" -> "hunger";
            case "WEAKNESS" -> "weakness";
            case "POISON" -> "poison";
            case "WITHER" -> "wither";
            case "REGENERATION" -> "regeneration";
            case "ABSORPTION" -> "absorption";
            case "SATURATION" -> "saturation";
            case "GLOWING" -> "glowing";
            case "LEVITATION" -> "levitation";
            case "LUCK" -> "luck";
            case "UNLUCK" -> "unluck";
            default -> typeName.trim().toLowerCase(Locale.ROOT);
        };
    }

    private static class PotionEffectSpec {
        private final PotionEffectType type;
        private final int amplifier;

        private PotionEffectSpec(PotionEffectType type, int amplifier) {
            this.type = type;
            this.amplifier = amplifier;
        }
    }

    public void shutdown() {
        stop();
        try {
            storage.saveAll(loans);
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to save loans.yml: " + e.getMessage());
        }
    }

    public Loan getLoan(UUID uuid) {
        return loans.get(uuid);
    }

    public void openRequestFlow(Player player) {
        if (!canUseLoans(player)) {
            player.sendMessage(messages.chat("loan.no_permission"));
            return;
        }
        int maxActive = plugin.getConfig().getInt("loans.max_active_per_player", 1);
        if (maxActive <= 0) maxActive = 1;
        Loan existing = loans.get(player.getUniqueId());
        if (existing != null && existing.getStatus() == LoanStatus.ACTIVE) {
            player.sendMessage(messages.chat("loan.already_active"));
            return;
        }
        Conversation c = new Conversation();
        c.state = ConversationState.ASK_AMOUNT;
        conversations.put(player.getUniqueId(), c);
        player.closeInventory();
        ClickablePromptUtil.sendPromptWithClickableCancel(player, messages, "loan.prompt.amount",
                java.util.Collections.emptyMap(), getPrimaryCancelWord(), ClickEvent.Action.RUN_COMMAND, "/vault cancel");
    }

    public void openPayFlow(Player player) {
        if (!canUseLoans(player)) {
            player.sendMessage(messages.chat("loan.no_permission"));
            return;
        }
        Loan loan = loans.get(player.getUniqueId());
        if (loan == null || loan.getStatus() != LoanStatus.ACTIVE) {
            player.sendMessage(messages.chat("loan.none"));
            return;
        }
        Conversation c = new Conversation();
        c.state = ConversationState.ASK_PAY_AMOUNT;
        conversations.put(player.getUniqueId(), c);
        player.closeInventory();
        Map<String, String> m = new java.util.HashMap<>();
        m.put("remaining", economy.format(loan.getRemaining()));
        ClickablePromptUtil.sendPromptWithClickableCancel(player, messages, "loan.prompt.pay_amount", m,
                getPrimaryCancelWord(), ClickEvent.Action.RUN_COMMAND, "/vault cancel");
    }

    public void sendStatus(Player player) {
        Loan loan = loans.get(player.getUniqueId());
        if (loan == null || loan.getStatus() != LoanStatus.ACTIVE) {
            player.sendMessage(messages.chat("loan.none"));
            return;
        }
        Map<String, String> m = new java.util.HashMap<>();
        m.put("principal", economy.format(loan.getPrincipal()));
        m.put("remaining", economy.format(loan.getRemaining()));
        m.put("installment", economy.format(loan.getInstallmentAmount()));
        m.put("installments_left", String.valueOf(loan.getInstallmentsLeft()));
        m.put("next_in_minutes", String.valueOf(Math.max(0, (loan.getNextChargeAtMs() - System.currentTimeMillis()) / 60000L)));
        player.sendMessage(messages.formatChat("loan.status", m));
    }

    public boolean cancelConversation(Player player) {
        Conversation removed = conversations.remove(player.getUniqueId());
        if (removed == null) return false;
        player.sendMessage(messages.chat("loan.cancelled"));
        return true;
    }

    public boolean createLoanFromWizard(Player player, double amount, int installments, double installmentAmount, int intervalHours, int firstDelayHours) {
        if (!canUseLoans(player)) {
            player.sendMessage(messages.chat("loan.no_permission"));
            return false;
        }
        int maxActive = plugin.getConfig().getInt("loans.max_active_per_player", 1);
        if (maxActive <= 0) maxActive = 1;
        Loan existing = loans.get(player.getUniqueId());
        if (existing != null && existing.getStatus() == LoanStatus.ACTIVE) {
            player.sendMessage(messages.chat("loan.already_active"));
            return false;
        }
        long intervalMs;
        if (intervalHours <= 0) {
            intervalMs = configIntervalMs();
        } else {
            intervalMs = intervalHours * 3600000L;
        }
        long firstDelayMs;
        if (firstDelayHours <= 0) {
            firstDelayMs = intervalMs;
        } else {
            firstDelayMs = firstDelayHours * 3600000L;
        }
        int before = loans.size();
        createLoan(player, amount, installments, installmentAmount, intervalMs, firstDelayMs);
        Loan after = loans.get(player.getUniqueId());
        return after != null && after.getStatus() == LoanStatus.ACTIVE && (loans.size() >= before);
    }

    private boolean canUseLoans(Player player) {
        if (!plugin.getConfig().getBoolean("loans.enabled", true)) return false;
        String perm = plugin.getConfig().getString("permissions.loan_use", "vault.loan");
        if (perm == null) return true;
        String p = perm.trim();
        if (p.isEmpty() || p.equalsIgnoreCase("none") || p.equalsIgnoreCase("disabled")) return true;
        return player.hasPermission(p);
    }

    private void scanAndCharge() {
        long now = System.currentTimeMillis();
        for (Loan loan : loans.values()) {
            if (loan == null) continue;
            if (loan.getStatus() != LoanStatus.ACTIVE) continue;
            if (loan.getNextChargeAtMs() <= 0) continue;
            if (loan.getNextChargeAtMs() > now) continue;
            UUID uuid = loan.getBorrower();
            Bukkit.getScheduler().runTask(plugin, () -> chargeOnce(uuid));
        }
    }

    private void chargeOnce(UUID uuid) {
        Loan loan = loans.get(uuid);
        if (loan == null) return;
        if (loan.getStatus() != LoanStatus.ACTIVE) return;
        long now = System.currentTimeMillis();
        if (loan.getNextChargeAtMs() > now) return;

        double due = Math.min(loan.getInstallmentAmount(), loan.getRemaining());
        if (due <= 0.0) {
            loan.setStatus(LoanStatus.PAID);
            loan.setNextChargeAtMs(0L);
            loan.setInstallmentsLeft(0);
            persist();
            notifyPaid(uuid);
            return;
        }

        OfflinePlayer op = Bukkit.getOfflinePlayer(uuid);
        economy.createPlayerAccount(op);
        double bal = economy.getBalance(op);
        if (bal >= due) {
            EconomyResponse r = economy.withdrawPlayer(op, due);
            if (r != null && r.transactionSuccess()) {
                loan.setRemaining(Math.max(0.0, loan.getRemaining() - due));
                loan.setInstallmentsLeft(Math.max(0, loan.getInstallmentsLeft() - 1));
                loan.setMissedPayments(0);
                if (loan.getRemaining() <= 0.0 || loan.getInstallmentsLeft() <= 0) {
                    loan.setStatus(LoanStatus.PAID);
                    loan.setNextChargeAtMs(0L);
                    persist();
                    notifyPaid(uuid);
                    return;
                }
                loan.setNextChargeAtMs(now + Math.max(60000L, loan.getIntervalMs()));
                persist();
                notifyCharged(uuid, due, loan.getRemaining());
                return;
            }
        }

        int missed = loan.getMissedPayments() + 1;
        loan.setMissedPayments(missed);
        loan.setNextChargeAtMs(now + Math.max(60000L, loan.getIntervalMs()));
        int maxMissed = plugin.getConfig().getInt("loans.max_missed_payments", 3);
        if (maxMissed < 1) maxMissed = 1;
        if (missed >= maxMissed) {
            loan.setStatus(LoanStatus.DEFAULTED);
            loan.setNextChargeAtMs(0L);
            persist();
            notifyDefaulted(uuid, loan.getRemaining());
            return;
        }
        persist();
        notifyMissed(uuid, due, missed, maxMissed);
    }

    private void persist() {
        try {
            storage.saveAll(loans);
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to save loans.yml: " + e.getMessage());
        }
    }

    private void notifyCharged(UUID uuid, double amount, double remaining) {
        Player p = Bukkit.getPlayer(uuid);
        if (p == null || !p.isOnline()) return;
        Map<String, String> m = new java.util.HashMap<>();
        m.put("amount", economy.format(amount));
        m.put("remaining", economy.format(remaining));
        p.sendMessage(messages.formatChat("loan.charge.success", m));
    }

    private void notifyMissed(UUID uuid, double amount, int missed, int maxMissed) {
        Player p = Bukkit.getPlayer(uuid);
        if (p == null || !p.isOnline()) return;
        Map<String, String> m = new java.util.HashMap<>();
        m.put("amount", economy.format(amount));
        m.put("missed", String.valueOf(missed));
        m.put("max_missed", String.valueOf(maxMissed));
        p.sendMessage(messages.formatChat("loan.charge.missed", m));
    }

    private void notifyDefaulted(UUID uuid, double remaining) {
        Player p = Bukkit.getPlayer(uuid);
        if (p == null || !p.isOnline()) return;
        Map<String, String> m = new java.util.HashMap<>();
        m.put("remaining", economy.format(remaining));
        p.sendMessage(messages.formatChat("loan.charge.defaulted", m));
    }

    private void notifyPaid(UUID uuid) {
        Player p = Bukkit.getPlayer(uuid);
        if (p == null || !p.isOnline()) return;
        p.sendMessage(messages.chat("loan.paid"));
    }

    @EventHandler(priority = org.bukkit.event.EventPriority.LOWEST, ignoreCancelled = false)
    public void onChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        Conversation c = conversations.get(player.getUniqueId());
        if (c == null) return;
        event.setCancelled(true);

        String msg = ChatInputSanitizer.sanitizeChatInput(event.getMessage());
        String lower = msg.toLowerCase(Locale.ROOT);
        for (String w : getCancelWords()) {
            if (lower.equals(w)) {
                cancelConversation(player);
                return;
            }
        }

        Bukkit.getScheduler().runTask(plugin, () -> handleConversationInput(player, c, msg));
    }

    private void handleConversationInput(Player player, Conversation c, String msg) {
        if (c.state == ConversationState.ASK_AMOUNT) {
            Double amount = parsePositiveDouble(msg);
            if (amount == null) {
                player.sendMessage(messages.chat("loan.error.invalid_number"));
                return;
            }
            double min = plugin.getConfig().getDouble("loans.min_amount", 0.0);
            double max = plugin.getConfig().getDouble("loans.max_amount", 0.0);
            if (min > 0 && amount < min) {
                player.sendMessage(messages.formatChat("loan.error.too_small", java.util.Collections.singletonMap("min", economy.format(min))));
                return;
            }
            if (max > 0 && amount > max) {
                player.sendMessage(messages.formatChat("loan.error.too_large", java.util.Collections.singletonMap("max", economy.format(max))));
                return;
            }
            c.amount = amount;
            c.state = ConversationState.ASK_TYPE;
            ClickablePromptUtil.sendPromptWithClickableCancel(player, messages, "loan.prompt.type",
                    java.util.Collections.emptyMap(), getPrimaryCancelWord(), ClickEvent.Action.RUN_COMMAND, "/vault cancel");
            return;
        }

        if (c.state == ConversationState.ASK_TYPE) {
            String lower = msg.toLowerCase(Locale.ROOT);
            if (lower.equals("total")) {
                c.state = ConversationState.ASK_TOTAL_DELAY_HOURS;
                ClickablePromptUtil.sendPromptWithClickableCancel(player, messages, "loan.prompt.total_delay_hours",
                        java.util.Collections.emptyMap(), getPrimaryCancelWord(), ClickEvent.Action.RUN_COMMAND, "/vault cancel");
                return;
            }
            if (lower.equals("cuotas") || lower.equals("installments")) {
                c.state = ConversationState.ASK_INSTALLMENTS_MODE;
                ClickablePromptUtil.sendPromptWithClickableCancel(player, messages, "loan.prompt.installments_mode",
                        java.util.Collections.emptyMap(), getPrimaryCancelWord(), ClickEvent.Action.RUN_COMMAND, "/vault cancel");
                return;
            }
            player.sendMessage(messages.chat("loan.error.invalid_type"));
            return;
        }

        if (c.state == ConversationState.ASK_INSTALLMENTS_MODE) {
            String lower = msg.toLowerCase(Locale.ROOT);
            if (lower.equals("cantidad") || lower.equals("count")) {
                c.state = ConversationState.ASK_INSTALLMENTS;
                ClickablePromptUtil.sendPromptWithClickableCancel(player, messages, "loan.prompt.installments_count",
                        java.util.Collections.emptyMap(), getPrimaryCancelWord(), ClickEvent.Action.RUN_COMMAND, "/vault cancel");
                return;
            }
            if (lower.equals("monto") || lower.equals("amount")) {
                c.state = ConversationState.ASK_INSTALLMENT_AMOUNT;
                ClickablePromptUtil.sendPromptWithClickableCancel(player, messages, "loan.prompt.installment_amount",
                        java.util.Collections.emptyMap(), getPrimaryCancelWord(), ClickEvent.Action.RUN_COMMAND, "/vault cancel");
                return;
            }
            player.sendMessage(messages.chat("loan.error.invalid_installments_mode"));
            return;
        }

        if (c.state == ConversationState.ASK_TOTAL_DELAY_HOURS) {
            Integer hours = parsePositiveInt(msg);
            if (hours == null) {
                player.sendMessage(messages.chat("loan.error.invalid_number"));
                return;
            }
            createLoan(player, c.amount, 1, c.amount, configIntervalMs(), hours * 3600000L);
            conversations.remove(player.getUniqueId());
            return;
        }

        if (c.state == ConversationState.ASK_INSTALLMENT_AMOUNT) {
            Double installment = parsePositiveDouble(msg);
            if (installment == null) {
                player.sendMessage(messages.chat("loan.error.invalid_number"));
                return;
            }
            if (!validateInstallmentAmount(player, c.amount, installment)) {
                return;
            }
            int max = plugin.getConfig().getInt("loans.max_installments", 60);
            if (max < 1) max = 60;
            int count = (int) Math.ceil(c.amount / installment);
            if (count <= 0) count = 1;
            if (count > max) {
                player.sendMessage(messages.formatChat("loan.error.too_many_installments", java.util.Collections.singletonMap("max", String.valueOf(max))));
                return;
            }
            c.installments = count;
            c.installmentAmount = installment;
            c.state = ConversationState.ASK_INTERVAL_HOURS;
            Map<String, String> m = new java.util.HashMap<>();
            m.put("default_hours", String.valueOf(plugin.getConfig().getInt("loans.default_interval_hours", 24)));
            ClickablePromptUtil.sendPromptWithClickableCancel(player, messages, "loan.prompt.interval_hours", m,
                    getPrimaryCancelWord(), ClickEvent.Action.RUN_COMMAND, "/vault cancel");
            return;
        }

        if (c.state == ConversationState.ASK_INSTALLMENTS) {
            Integer n = parsePositiveInt(msg);
            if (n == null) {
                player.sendMessage(messages.chat("loan.error.invalid_number"));
                return;
            }
            int max = plugin.getConfig().getInt("loans.max_installments", 60);
            if (max < 1) max = 60;
            if (n > max) {
                player.sendMessage(messages.formatChat("loan.error.too_many_installments", java.util.Collections.singletonMap("max", String.valueOf(max))));
                return;
            }
            double installment = c.amount / n;
            if (!validateInstallmentAmount(player, c.amount, installment)) {
                return;
            }
            c.installments = n;
            c.state = ConversationState.ASK_INTERVAL_HOURS;
            Map<String, String> m = new java.util.HashMap<>();
            m.put("default_hours", String.valueOf(plugin.getConfig().getInt("loans.default_interval_hours", 24)));
            ClickablePromptUtil.sendPromptWithClickableCancel(player, messages, "loan.prompt.interval_hours", m,
                    getPrimaryCancelWord(), ClickEvent.Action.RUN_COMMAND, "/vault cancel");
            return;
        }

        if (c.state == ConversationState.ASK_INTERVAL_HOURS) {
            Integer hours = parseNonNegativeInt(msg);
            if (hours == null) {
                player.sendMessage(messages.chat("loan.error.invalid_number"));
                return;
            }
            if (hours <= 0) {
                hours = plugin.getConfig().getInt("loans.default_interval_hours", 24);
                if (hours <= 0) hours = 24;
            }
            c.intervalHours = hours;
            c.state = ConversationState.ASK_FIRST_DELAY_HOURS;
            Map<String, String> m = new java.util.HashMap<>();
            m.put("default_hours", String.valueOf(hours));
            ClickablePromptUtil.sendPromptWithClickableCancel(player, messages, "loan.prompt.first_delay_hours", m,
                    getPrimaryCancelWord(), ClickEvent.Action.RUN_COMMAND, "/vault cancel");
            return;
        }

        if (c.state == ConversationState.ASK_FIRST_DELAY_HOURS) {
            Integer hours = parseNonNegativeInt(msg);
            if (hours == null) {
                player.sendMessage(messages.chat("loan.error.invalid_number"));
                return;
            }
            if (hours <= 0) hours = c.intervalHours;
            double installment = c.installmentAmount != null ? c.installmentAmount : (c.amount / c.installments);
            createLoan(player, c.amount, c.installments, installment, c.intervalHours * 3600000L, hours * 3600000L);
            conversations.remove(player.getUniqueId());
            return;
        }

        if (c.state == ConversationState.ASK_PAY_AMOUNT) {
            Loan loan = loans.get(player.getUniqueId());
            if (loan == null || loan.getStatus() != LoanStatus.ACTIVE) {
                conversations.remove(player.getUniqueId());
                player.sendMessage(messages.chat("loan.none"));
                return;
            }
            double toPay;
            String lower = msg.toLowerCase(Locale.ROOT);
            if (lower.equals("all") || lower.equals("todo")) {
                toPay = loan.getRemaining();
            } else {
                Double amt = parsePositiveDouble(msg);
                if (amt == null) {
                    player.sendMessage(messages.chat("loan.error.invalid_number"));
                    return;
                }
                toPay = Math.min(amt, loan.getRemaining());
            }
            if (toPay <= 0.0) {
                player.sendMessage(messages.chat("loan.error.invalid_number"));
                return;
            }
            EconomyResponse r = economy.withdrawPlayer(player, toPay);
            if (r == null || !r.transactionSuccess()) {
                player.sendMessage(messages.chat("loan.error.not_enough_money"));
                return;
            }
            loan.setRemaining(Math.max(0.0, loan.getRemaining() - toPay));
            if (loan.getRemaining() <= 0.0) {
                loan.setStatus(LoanStatus.PAID);
                loan.setNextChargeAtMs(0L);
                loan.setInstallmentsLeft(0);
            }
            persist();
            Map<String, String> m = new java.util.HashMap<>();
            m.put("amount", economy.format(toPay));
            m.put("remaining", economy.format(loan.getRemaining()));
            player.sendMessage(messages.formatChat("loan.pay.success", m));
            conversations.remove(player.getUniqueId());
            return;
        }
    }

    private void createLoan(Player player, double amount, int installments, double installmentAmount, long intervalMs, long firstDelayMs) {
        Loan existing = loans.get(player.getUniqueId());
        if (existing != null && existing.getStatus() == LoanStatus.ACTIVE) {
            player.sendMessage(messages.chat("loan.already_active"));
            return;
        }
        if (!validateInstallmentAmount(player, amount, installmentAmount)) {
            return;
        }
        EconomyResponse r = economy.depositPlayer(player, amount);
        if (r == null || !r.transactionSuccess()) {
            player.sendMessage(messages.chat("loan.error.deposit_failed"));
            return;
        }
        Loan loan = new Loan(player.getUniqueId());
        loan.setPrincipal(amount);
        loan.setRemaining(amount);
        loan.setInstallmentAmount(Math.max(0.01, installmentAmount));
        loan.setIntervalMs(Math.max(60000L, intervalMs));
        loan.setInstallmentsLeft(Math.max(1, installments));
        loan.setNextChargeAtMs(System.currentTimeMillis() + Math.max(60000L, firstDelayMs));
        loan.setStatus(LoanStatus.ACTIVE);
        loan.setMissedPayments(0);
        loans.put(player.getUniqueId(), loan);
        persist();

        Map<String, String> m = new java.util.HashMap<>();
        m.put("amount", economy.format(amount));
        m.put("installments", String.valueOf(loan.getInstallmentsLeft()));
        m.put("installment_amount", economy.format(loan.getInstallmentAmount()));
        m.put("interval_hours", String.valueOf(Math.max(1, loan.getIntervalMs() / 3600000L)));
        player.sendMessage(messages.formatChat("loan.created", m));
    }

    private long configIntervalMs() {
        int hours = plugin.getConfig().getInt("loans.default_interval_hours", 24);
        if (hours <= 0) hours = 24;
        return hours * 3600000L;
    }

    private boolean validateInstallmentAmount(Player player, double loanAmount, double installmentAmount) {
        double requiredMin = minimumInstallmentFor(loanAmount);
        if (installmentAmount + 1.0E-9 < requiredMin) {
            Map<String, String> m = new java.util.HashMap<>();
            m.put("loan_amount", economy.format(loanAmount));
            m.put("min_installment", economy.format(requiredMin));
            player.sendMessage(messages.formatChat("loan.error.installment_too_small", m));
            return false;
        }
        return true;
    }

    private double minimumInstallmentFor(double loanAmount) {
        double required = plugin.getConfig().getDouble("loans.min_installment", plugin.getConfig().getDouble("loans.min_installment_amount", 0.01));
        if (required <= 0.0) required = 0.01;

        ConfigurationSection simple = plugin.getConfig().getConfigurationSection("loans.min_installment_by_amount");
        if (simple != null) {
            for (String key : simple.getKeys(false)) {
                double threshold = toDouble(key);
                double minInstallment = toDouble(simple.get(key));
                if (threshold <= 0.0 || minInstallment <= 0.0) continue;
                if (loanAmount + 1.0E-9 >= threshold) {
                    required = Math.max(required, minInstallment);
                }
            }
        }

        List<Map<?, ?>> tiers = plugin.getConfig().getMapList("loans.installment_minimum_tiers");
        for (Map<?, ?> tier : tiers) {
            double threshold = toDouble(tier.get("loan_amount_at_least"));
            double minInstallment = toDouble(tier.get("min_installment_amount"));
            if (threshold <= 0.0 || minInstallment <= 0.0) continue;
            if (loanAmount + 1.0E-9 >= threshold) {
                required = Math.max(required, minInstallment);
            }
        }

        ConfigurationSection legacy = plugin.getConfig().getConfigurationSection("loans.minimum_installment_by_amount");
        if (legacy != null) {
            for (String key : legacy.getKeys(false)) {
                ConfigurationSection entry = legacy.getConfigurationSection(key);
                if (entry == null) continue;
                double threshold = entry.getDouble("loan_amount_at_least", 0.0);
                double minInstallment = entry.getDouble("min_installment_amount", 0.0);
                if (threshold <= 0.0 || minInstallment <= 0.0) continue;
                if (loanAmount + 1.0E-9 >= threshold) {
                    required = Math.max(required, minInstallment);
                }
            }
        }

        return required;
    }

    private double toDouble(Object raw) {
        if (raw instanceof Number) {
            return ((Number) raw).doubleValue();
        }
        if (raw == null) {
            return 0.0;
        }
        try {
            return Double.parseDouble(String.valueOf(raw));
        } catch (NumberFormatException ignored) {
            return 0.0;
        }
    }

    private Double parsePositiveDouble(String s) {
        return ChatInputSanitizer.parsePositiveDouble(s);
    }

    private Integer parsePositiveInt(String s) {
        return ChatInputSanitizer.parsePositiveInt(s);
    }

    private Integer parseNonNegativeInt(String s) {
        return ChatInputSanitizer.parseNonNegativeInt(s);
    }

    private java.util.List<String> getCancelWords() {
        String raw = messages.get("loan.prompt.cancel_words");
        if (raw == null || raw.isEmpty() || raw.equals("loan.prompt.cancel_words")) {
            raw = messages.get("pay.prompt.cancel_words");
        }
        if (raw == null || raw.isEmpty() || raw.equals("pay.prompt.cancel_words")) {
            raw = "cancel,cancelar";
        }
        String[] parts = raw.split(",");
        java.util.List<String> out = new java.util.ArrayList<>();
        for (String p : parts) {
            String v = org.bukkit.ChatColor.stripColor(com.example.vault.util.ColorUtil.colorize(p));
            if (v == null) continue;
            v = v.trim().toLowerCase(Locale.ROOT);
            if (!v.isEmpty()) out.add(v);
        }
        if (out.isEmpty()) out.add("cancel");
        return out;
    }

    private String getPrimaryCancelWord() {
        java.util.List<String> list = getCancelWords();
        return list.isEmpty() ? "cancel" : list.get(0);
    }

    private static class Conversation {
        ConversationState state;
        double amount;
        int installments;
        Double installmentAmount;
        int intervalHours;
    }

    private enum ConversationState {
        ASK_AMOUNT,
        ASK_TYPE,
        ASK_TOTAL_DELAY_HOURS,
        ASK_INSTALLMENTS_MODE,
        ASK_INSTALLMENT_AMOUNT,
        ASK_INSTALLMENTS,
        ASK_INTERVAL_HOURS,
        ASK_FIRST_DELAY_HOURS,
        ASK_PAY_AMOUNT
    }
}
