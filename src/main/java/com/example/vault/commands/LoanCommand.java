package com.example.vault.commands;

import com.example.vault.VaultPlugin;
import com.example.vault.i18n.Messages;
import com.example.vault.loans.LoanService;
import com.example.vault.menu.VaultMenuService;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class LoanCommand implements CommandExecutor {

    private final VaultPlugin plugin;
    private final Messages messages;
    private final LoanService loanService;
    private final VaultMenuService vaultMenuService;

    public LoanCommand(VaultPlugin plugin, Messages messages, LoanService loanService, VaultMenuService vaultMenuService) {
        this.plugin = plugin;
        this.messages = messages;
        this.loanService = loanService;
        this.vaultMenuService = vaultMenuService;
    }

    private static Double parsePositiveDouble(String s) {
        if (s == null) return null;
        String v = s.trim();
        if (v.isEmpty()) return null;
        char last = v.charAt(v.length() - 1);
        double mul = 1.0;
        if (last == 'k' || last == 'K') { mul = 1_000.0; v = v.substring(0, v.length() - 1).trim(); }
        else if (last == 'm' || last == 'M') { mul = 1_000_000.0; v = v.substring(0, v.length() - 1).trim(); }
        else if (last == 'b' || last == 'B') { mul = 1_000_000_000.0; v = v.substring(0, v.length() - 1).trim(); }
        if (v.isEmpty()) return null;
        try {
            double d = Double.parseDouble(v);
            if (!Double.isFinite(d) || d <= 0.0) return null;
            return d * mul;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(messages.get("error.players_only") == null ? "Only players can use this command." : messages.chat("error.players_only"));
            return true;
        }
        Player p = (Player) sender;
        if (!plugin.getConfig().getBoolean("loans.enabled", true)) {
            String disabled = messages.get("loan.disabled");
            p.sendMessage(disabled == null || disabled.isEmpty() || disabled.equals("loan.disabled")
                    ? color("&cLoans are disabled on this server.")
                    : disabled);
            return true;
        }
        String perm = plugin.getConfig().getString("permissions.loan_use", "vault.loan");
        boolean bypass = p.isOp() || p.hasPermission("vault.admin") || p.hasPermission("vault.use");
        if (perm != null && !perm.trim().isEmpty()
                && !perm.trim().equalsIgnoreCase("none") && !perm.trim().equalsIgnoreCase("disabled")) {
            if (!bypass && !p.hasPermission(perm.trim())) {
                p.sendMessage(messages.chat("loan.no_permission"));
                return true;
            }
        }

        if (args == null || args.length == 0) {
            if (vaultMenuService != null && vaultMenuService.getLoanMenuService() != null) {
                vaultMenuService.getLoanMenuService().openLoanMenu(p);
            } else {
                Map<String, String> m = new HashMap<>();
                m.put("label", label);
                p.sendMessage(messages.formatChat("loan.cmd.menu_fallback", m));
            }
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);

        if ("help".equals(sub) || "?".equals(sub)) {
            sendHelp(p, label);
            return true;
        }

        if ("status".equals(sub)) {
            loanService.sendStatus(p);
            return true;
        }

        if ("cancel".equals(sub)) {
            if (!loanService.cancelConversation(p)) {
                p.sendMessage(messages.chat("loan.no_active_conversation"));
            }
            return true;
        }

        if ("apply".equals(sub) || "request".equals(sub) || "pedir".equals(sub)) {
            if (args.length < 3) {
                Map<String, String> m = new HashMap<>();
                m.put("label", label);
                String key = messages.get("loan.cmd.apply.usage");
                if (key == null || key.isEmpty() || key.equals("loan.cmd.apply.usage")) {
                    p.sendMessage(color("&eUso: &f/" + label + " apply <total> <pago_por_cuota> [duración]"));
                    p.sendMessage(color("&eEjemplos:"));
                    p.sendMessage(color("&f  /" + label + " apply 10000 1000      &7→ 10 cuotas de 1000"));
                    p.sendMessage(color("&f  /" + label + " apply 5000 2500 1d   &7→ pagar en 1 día, máximo 2 cuotas"));
                } else {
                    p.sendMessage(messages.formatChat("loan.cmd.apply.usage", m));
                }
                return true;
            }
            Double total = parsePositiveDouble(args[1]);
            if (total == null) {
                p.sendMessage(messages.chat("loan.error.invalid_number"));
                return true;
            }
            Double payment = parsePositiveDouble(args[2]);
            if (payment == null) {
                p.sendMessage(messages.chat("loan.error.invalid_number"));
                return true;
            }
            Long durationMs = null;
            if (args.length >= 4) {
                durationMs = LoanService.parseDuration(args[3]);
                if (durationMs == null) {
                    Map<String, String> m = Collections.singletonMap("value", args[3]);
                    String key = messages.get("loan.cmd.invalid_duration");
                    if (key == null || key.isEmpty() || key.equals("loan.cmd.invalid_duration")) {
                        p.sendMessage(color("&cDuración inválida: &f%value%&c. Usa 30m, 1h, 1d, 2w.".replace("%value%", args[3])));
                    } else {
                        p.sendMessage(messages.formatChat("loan.cmd.invalid_duration", m));
                    }
                    return true;
                }
            }
            loanService.applyLoan(p, total, payment, durationMs);
            return true;
        }

        if ("pay".equals(sub) || "pagar".equals(sub)) {
            if (args.length < 2) {
                Map<String, String> m = new HashMap<>();
                m.put("label", label);
                String key = messages.get("loan.cmd.pay.usage");
                if (key == null || key.isEmpty() || key.equals("loan.cmd.pay.usage")) {
                    p.sendMessage(color("&eUso: &f/" + label + " pay <monto>      &7(paga lo que quieras o 'todo'/'all' para pagar el resto)"));
                } else {
                    p.sendMessage(messages.formatChat("loan.cmd.pay.usage", m));
                }
                return true;
            }
            String raw = args[1];
            String lower = raw.toLowerCase(Locale.ROOT);
            double amount;
            if (lower.equals("all") || lower.equals("todo") || lower.equals("full")) {
                var loan = loanService.getLoan(p.getUniqueId());
                if (loan == null || !String.valueOf(loan.getStatus()).equals("ACTIVE")) {
                    amount = 999_999_999_999.0;
                } else {
                    amount = loan.getRemaining() + 0.01;
                }
            } else {
                Double amt = parsePositiveDouble(raw);
                if (amt == null) {
                    p.sendMessage(messages.chat("loan.error.invalid_number"));
                    return true;
                }
                amount = amt;
            }
            loanService.payLoan(p, amount);
            return true;
        }

        sendHelp(p, label);
        return true;
    }

    private void sendHelp(Player p, String label) {
        p.sendMessage(color("&6===== &e/loan Help&6 ====="));
        p.sendMessage(color("&f/" + label + "                  &7Abrir menú préstamos (GUI)"));
        p.sendMessage(color("&f/" + label + " apply <total> <pago> [dur] &7Pedir préstamo. Dur: 30m/1h/1d/2w"));
        p.sendMessage(color("&f/" + label + " pay <amount|all>   &7Pagar una parte (o todo) del préstamo"));
        p.sendMessage(color("&f/" + label + " status            &7Estado de tu préstamo"));
        p.sendMessage(color("&f/" + label + " cancel            &7Cancelar conversación chat activa"));
    }

    private String color(String s) {
        return org.bukkit.ChatColor.translateAlternateColorCodes('&', s == null ? "" : s);
    }
}
