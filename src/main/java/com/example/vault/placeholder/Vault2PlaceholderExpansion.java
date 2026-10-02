package com.example.vault.placeholder;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.OfflinePlayer;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;
import org.bukkit.plugin.Plugin;
import com.example.vault.i18n.Messages;
import com.example.vault.util.ColorUtil;
import com.example.vault.util.PlayerResolver;
import com.example.vault.economy.SimpleEconomy;
import com.example.vault.economy.BankService;
import com.example.vault.loans.Loan;
import com.example.vault.loans.LoanService;
import org.bukkit.Bukkit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class Vault2PlaceholderExpansion extends PlaceholderExpansion {
    private final Plugin plugin;
    private final Economy economy;
    private final Messages messages;

    public Vault2PlaceholderExpansion(Plugin plugin, Economy economy, Messages messages) {
        this.plugin = plugin;
        this.economy = economy;
        this.messages = messages;
    }

    private String formatWithCommas(double value) {
        DecimalFormatSymbols sym = DecimalFormatSymbols.getInstance(Locale.US);
        sym.setGroupingSeparator(',');
        sym.setDecimalSeparator('.');
        DecimalFormat df = new DecimalFormat("#,###.##", sym);
        df.setGroupingUsed(true);
        return df.format(value);
    }

    @Override
    public String getIdentifier() {
        return "vault2";
    }

    @Override
    public String getAuthor() {
        return String.join(", ", plugin.getDescription().getAuthors());
    }

    @Override
    public String getVersion() {
        return plugin.getDescription().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public boolean canRegister() {
        return true;
    }

    private SimpleEconomy getSimpleEconomy() {
        return economy instanceof SimpleEconomy ? (SimpleEconomy) economy : null;
    }

    private BankService getBankService() {
        if (!(plugin instanceof com.example.vault.VaultPlugin)) return null;
        return ((com.example.vault.VaultPlugin) plugin).getBankService();
    }

    private LoanService getLoanService() {
        if (!(plugin instanceof com.example.vault.VaultPlugin)) return null;
        try {
            java.lang.reflect.Method m = com.example.vault.VaultPlugin.class.getMethod("getLoanService");
            Object out = m.invoke(plugin);
            return out instanceof LoanService ? (LoanService) out : null;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private double sanitize(double v) { return Double.isFinite(v) ? v : 0.0; }

    private enum Style { RAW, FIXED, COMMAS, SHORT, FORMATTED, INT }

    private Style styleFromSuffix(String key, String base) {
        String suffix = key.substring(base.length());
        if (suffix.isEmpty()) return Style.FIXED;
        if (suffix.equals("_raw")) return Style.RAW;
        if (suffix.equals("_int")) return Style.INT;
        if (suffix.equals("_fixed")) return Style.FIXED;
        if (suffix.equals("_commas")) return Style.COMMAS;
        if (suffix.equals("_short")) return Style.SHORT;
        if (suffix.equals("_formatted")) return Style.FORMATTED;
        return null;
    }

    private String formatMoney(double amount, Style style) {
        double a = sanitize(amount);
        return switch (style) {
            case RAW -> String.valueOf(a);
            case FIXED -> String.format(Locale.ROOT, "%.2f", a);
            case COMMAS -> formatWithCommas(a);
            case SHORT -> {
                SimpleEconomy se = getSimpleEconomy();
                if (se != null) yield se.formatShort(se.getDefaultCurrencyId(), a);
                yield economy.format(a);
            }
            case FORMATTED -> economy.format(a);
            case INT -> String.valueOf(Math.round(a));
        };
    }

    private boolean isFiniteNonNegative(double v) { return Double.isFinite(v) && v >= 0.0; }

    @Override
    public String onRequest(OfflinePlayer player, String params) {
        if (params == null) return "";
        String key = params.toLowerCase();
        if (key.startsWith("top_")) {
            return handleTopParam(key);
        }
        if ("top".equals(key)) {
            return buildTop(10);
        }
        if ("currency_symbol".equals(key)) {
            String raw = plugin.getConfig().getString("currency.symbol", "");
            if (raw == null) raw = "";
            return ColorUtil.colorize(raw);
        }

        String interestPrefix = "interest";
        if (key.equals(interestPrefix) || key.startsWith(interestPrefix + "_")) {
            if (player == null) return "";
            Style style = styleFromSuffix(key, interestPrefix);
            if (style == null) return "";
            BankService bank = getBankService();
            if (bank == null) return formatMoney(0.0, style);
            SimpleEconomy se = getSimpleEconomy();
            if (se == null) return formatMoney(0.0, style);
            if (!bank.isInterestEnabled()) return formatMoney(0.0, style);
            double bankBal = sanitize(bank.getBankBalance(player.getUniqueId()));
            double pct = plugin.getConfig().getDouble("bank.interest.percent_per_period", 0.5);
            if (pct <= 0 || bankBal <= 0) return formatMoney(0.0, style);
            return formatMoney(bankBal * pct / 100.0, style);
        }

        String taxPrefix = "tax";
        if (key.equals(taxPrefix) || key.startsWith(taxPrefix + "_")) {
            if (player == null) return "";
            Style style = styleFromSuffix(key, taxPrefix);
            if (style == null) return "";
            BankService bank = getBankService();
            if (bank == null) return formatMoney(0.0, style);
            SimpleEconomy se = getSimpleEconomy();
            if (se == null) return formatMoney(0.0, style);
            if (!bank.isTaxEnabled()) return formatMoney(0.0, style);
            double bankBal = sanitize(bank.getBankBalance(player.getUniqueId()));
            double pct = plugin.getConfig().getDouble("bank.tax.percent_per_period", 0.0);
            double threshold = plugin.getConfig().getDouble("bank.tax.threshold", 1000000.0);
            if (pct <= 0 || bankBal <= threshold) return formatMoney(0.0, style);
            return formatMoney((bankBal - threshold) * pct / 100.0, style);
        }

        String netPrefix = "net";
        if (key.equals(netPrefix) || key.startsWith(netPrefix + "_")) {
            if (player == null) return "";
            Style style = styleFromSuffix(key, netPrefix);
            if (style == null) return "";
            ensureAccountForRequest(player, player);
            double wallet = sanitize(getBalanceForRequest(player, player));
            BankService bank = getBankService();
            double bankBal = bank != null ? sanitize(bank.getBankBalance(player.getUniqueId())) : 0.0;
            LoanService loans = getLoanService();
            double loanRemaining = 0.0;
            if (loans != null) {
                Loan loan = loans.getLoan(player.getUniqueId());
                if (loan != null) loanRemaining = sanitize(loan.getRemaining());
            }
            return formatMoney(wallet + bankBal - loanRemaining, style);
        }

        String walletPrefix = "wallet";
        if (key.equals(walletPrefix) || key.startsWith(walletPrefix + "_")) {
            if (player == null) return "";
            Style style = styleFromSuffix(key, walletPrefix);
            if (style == null) return "";
            ensureAccountForRequest(player, player);
            return formatMoney(getBalanceForRequest(player, player), style);
        }

        String bankPrefix = "bank";
        if (key.equals(bankPrefix) || key.startsWith(bankPrefix + "_")) {
            Style style = styleFromSuffix(key, bankPrefix);
            if (style == null) return "";
            if (player == null) return formatMoney(0.0, style);
            BankService bank = getBankService();
            if (bank == null) return formatMoney(0.0, style);
            return formatMoney(bank.getBankBalance(player.getUniqueId()), style);
        }

        if (key.startsWith("loan_")) {
            if (player == null) return "";
            return resolveLoanPlaceholder(key, player);
        }

        if (player == null) return "";
        switch (key) {
            case "balance": {
                ensureAccountForRequest(player, player);
                double bal = getBalanceForRequest(player, player);
                return String.format(Locale.ROOT, "%.2f", bal);
            }
            case "balance_formatted": {
                ensureAccountForRequest(player, player);
                double bal = getBalanceForRequest(player, player);
                return economy.format(bal);
            }
            case "eco_balance": {
                ensureAccountForRequest(player, player);
                double bal = getBalanceForRequest(player, player);
                return String.format(Locale.ROOT, "%.2f", bal);
            }
            case "eco_balance_formatted": {
                ensureAccountForRequest(player, player);
                double bal = getBalanceForRequest(player, player);
                return economy.format(bal);
            }
            case "eco_balance_fixed": {
                ensureAccountForRequest(player, player);
                double bal = getBalanceForRequest(player, player);
                return String.format(Locale.ROOT, "%.2f", bal);
            }
            case "eco_balance_commas": {
                ensureAccountForRequest(player, player);
                double bal = getBalanceForRequest(player, player);
                return formatWithCommas(bal);
            }
            case "eco_balance_short": {
                ensureAccountForRequest(player, player);
                double bal = getBalanceForRequest(player, player);
                if (economy instanceof com.example.vault.economy.SimpleEconomy) {
                    com.example.vault.economy.SimpleEconomy se = (com.example.vault.economy.SimpleEconomy) economy;
                    return se.formatShort(se.getDefaultCurrencyId(), bal);
                }
                return economy.format(bal);
            }
            default:
                if (key.startsWith("balance_formatted_")) {
                    String name = key.substring("balance_formatted_".length());
                    OfflinePlayer other = PlayerResolver.resolveByNameWithOfflineFallback(plugin, name);
                    if (other == null) return "";
                    ensureAccountForRequest(other, player);
                    return economy.format(getBalanceForRequest(other, player));
                }
                if (key.startsWith("balance_")) {
                    String name = key.substring("balance_".length());
                    OfflinePlayer other = PlayerResolver.resolveByNameWithOfflineFallback(plugin, name);
                    if (other == null) return "";
                    ensureAccountForRequest(other, player);
                    return String.format(Locale.ROOT, "%.2f", getBalanceForRequest(other, player));
                }
                if (key.startsWith("ecobalance") && key.endsWith("dp")) {
                    String middle = key.substring("ecobalance".length(), key.length() - 2);
                    try {
                        int dp = Integer.parseInt(middle);
                        if (dp < 0) dp = 0;
                        if (dp > 8) dp = 8;
                        ensureAccountForRequest(player, player);
                        double bal = getBalanceForRequest(player, player);
                        return String.format(Locale.ROOT, "%" + "." + dp + "f", bal);
                    } catch (NumberFormatException ignored) {
                        return "";
                    }
                }
                return "";
        }
    }

    private String resolveLoanPlaceholder(String key, OfflinePlayer player) {
        LoanService loans = getLoanService();
        Loan loan = loans != null ? loans.getLoan(player.getUniqueId()) : null;
        String rest = key.substring("loan_".length());
        switch (rest) {
            case "total":
            case "total_formatted":
            case "total_commas":
            case "total_short":
            case "total_fixed": {
                Style style = styleFromSuffix("loan_" + rest, "loan_total");
                if (style == null) return "";
                double principal = loan != null && isFiniteNonNegative(loan.getPrincipal()) ? loan.getPrincipal() : 0.0;
                if (loan != null && isFiniteNonNegative(loan.getInstallmentsLeft()) && loan.getInstallmentsLeft() > 0
                        && isFiniteNonNegative(loan.getInstallmentAmount())) {
                    double withInstallments = loan.getInstallmentAmount() * loan.getInstallmentsLeft();
                    if (withInstallments > principal) principal = withInstallments;
                }
                return formatMoney(principal, style);
            }
            case "remainder":
            case "remainder_formatted":
            case "remainder_commas":
            case "remainder_short":
            case "remainder_fixed": {
                Style style = styleFromSuffix("loan_" + rest, "loan_remainder");
                if (style == null) return "";
                double v = loan != null && isFiniteNonNegative(loan.getRemaining()) ? loan.getRemaining() : 0.0;
                return formatMoney(v, style);
            }
            case "installment_amount":
            case "installment_amount_formatted":
            case "installment_amount_commas":
            case "installment_amount_short":
            case "installment_amount_fixed": {
                Style style = styleFromSuffix("loan_" + rest, "loan_installment_amount");
                if (style == null) return "";
                double v = loan != null && isFiniteNonNegative(loan.getInstallmentAmount()) ? loan.getInstallmentAmount() : 0.0;
                return formatMoney(v, style);
            }
            case "installments_left":
            case "installments_left_formatted": {
                int left = loan != null && loan.getInstallmentsLeft() > 0 ? loan.getInstallmentsLeft() : 0;
                if (rest.equals("installments_left_formatted")) {
                    return formatWithCommas(left);
                }
                return String.valueOf(left);
            }
            case "interest":
            case "interest_formatted":
            case "interest_commas":
            case "interest_short":
            case "interest_fixed": {
                Style style = styleFromSuffix("loan_" + rest, "loan_interest");
                if (style == null) return "";
                double interest = 0.0;
                if (loan != null
                        && isFiniteNonNegative(loan.getPrincipal())
                        && isFiniteNonNegative(loan.getRemaining())
                        && isFiniteNonNegative(loan.getInstallmentsLeft())
                        && loan.getInstallmentsLeft() > 0
                        && isFiniteNonNegative(loan.getInstallmentAmount())) {
                    double total = loan.getInstallmentAmount() * loan.getInstallmentsLeft();
                    interest = sanitize(total - loan.getRemaining());
                }
                return formatMoney(interest, style);
            }
            default:
                return "";
        }
    }

    private String handleTopParam(String key) {
        String rest = key.substring("top_".length());
        if (rest.isEmpty()) return "";
        if (rest.startsWith("name_")) {
            Integer rank = tryParsePositiveInt(rest.substring("name_".length()));
            if (rank == null) return "";
            return getTopName(rank);
        }
        if (rest.startsWith("amount_")) {
            Integer rank = tryParsePositiveInt(rest.substring("amount_".length()));
            if (rank == null) return "";
            return getTopAmount(rank);
        }
        if (rest.startsWith("uuid_")) {
            Integer rank = tryParsePositiveInt(rest.substring("uuid_".length()));
            if (rank == null) return "";
            return getTopUuid(rank);
        }
        Integer rank = tryParsePositiveInt(rest);
        if (rank == null) return "";
        return getTopLine(rank);
    }

    private Integer tryParsePositiveInt(String s) {
        try {
            int n = Integer.parseInt(s);
            if (n <= 0) return null;
            return n;
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private String getTopLine(int rank) {
        List<Map.Entry<UUID, Double>> entries = sortedBalanceEntries();
        if (entries.isEmpty() || rank > entries.size()) return "";
        Map.Entry<UUID, Double> e = entries.get(rank - 1);
        String name = resolveName(e.getKey());
        return rank + ". " + name + ": " + economy.format(e.getValue());
    }

    private String getTopName(int rank) {
        List<Map.Entry<UUID, Double>> entries = sortedBalanceEntries();
        if (entries.isEmpty() || rank > entries.size()) return "";
        return resolveName(entries.get(rank - 1).getKey());
    }

    private String getTopAmount(int rank) {
        List<Map.Entry<UUID, Double>> entries = sortedBalanceEntries();
        if (entries.isEmpty() || rank > entries.size()) return "";
        return economy.format(entries.get(rank - 1).getValue());
    }

    private String getTopUuid(int rank) {
        List<Map.Entry<UUID, Double>> entries = sortedBalanceEntries();
        if (entries.isEmpty() || rank > entries.size()) return "";
        return entries.get(rank - 1).getKey().toString();
    }

    private String buildTop(int limit) {
        List<Map.Entry<UUID, Double>> entries = sortedBalanceEntries();
        if (entries.isEmpty()) return "";
        int n = Math.min(limit, entries.size());
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < n; i++) {
            Map.Entry<UUID, Double> e = entries.get(i);
            String name = resolveName(e.getKey());
            if (i > 0) sb.append("\n");
            sb.append(i + 1).append(". ").append(name).append(": ").append(economy.format(e.getValue()));
        }
        return sb.toString();
    }

    private List<Map.Entry<UUID, Double>> sortedBalanceEntries() {
        if (!(economy instanceof SimpleEconomy)) return java.util.Collections.emptyList();
        SimpleEconomy se = (SimpleEconomy) economy;
        Map<UUID, Double> balances = se.snapshotBalances(se.getDefaultCurrencyId());
        if (balances.isEmpty()) return java.util.Collections.emptyList();
        List<Map.Entry<UUID, Double>> entries = new ArrayList<>(balances.entrySet());
        entries.sort(new Comparator<Map.Entry<UUID, Double>>() {
            @Override
            public int compare(Map.Entry<UUID, Double> a, Map.Entry<UUID, Double> b) {
                return Double.compare(b.getValue(), a.getValue());
            }
        });
        return entries;
    }

    private String resolveName(UUID uuid) {
        String name = Bukkit.getOfflinePlayer(uuid).getName();
        if (name == null || name.trim().isEmpty()) {
            String unknown = messages != null ? messages.get("top.unknown_player") : null;
            if (unknown == null || unknown.isEmpty() || "top.unknown_player".equals(unknown)) unknown = "Player";
            return unknown;
        }
        return name;
    }

    private void ensureAccountForRequest(OfflinePlayer subject, OfflinePlayer requester) {
        if (economy instanceof SimpleEconomy) {
            economy.createPlayerAccount(subject, requestWorldName(requester));
            return;
        }
        economy.createPlayerAccount(subject);
    }

    private double getBalanceForRequest(OfflinePlayer subject, OfflinePlayer requester) {
        if (economy instanceof SimpleEconomy) {
            return sanitize(economy.getBalance(subject, requestWorldName(requester)));
        }
        return sanitize(economy.getBalance(subject));
    }

    private String requestWorldName(OfflinePlayer requester) {
        if (requester == null || !requester.isOnline() || requester.getPlayer() == null || requester.getPlayer().getWorld() == null) {
            return null;
        }
        return requester.getPlayer().getWorld().getName();
    }
}
