package com.example.vault.economy;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

public final class CurrencyDef {
    public final String id;
    public final String symbol;
    public final String position;
    public final boolean space;
    public final String singular;
    public final String plural;
    public final String numberPattern;
    public final String localeTag;
    public final boolean abbreviateEnabled;
    public final int abbreviateDecimals;
    public final String suffixK;
    public final String suffixM;
    public final String suffixB;
    public final String suffixT;
    public final int fractionalDigits;
    public final boolean defaultCurrency;
    public final boolean persistToMySQL;

    CurrencyDef(Builder b) {
        this.id = b.id;
        this.symbol = b.symbol;
        this.position = b.position;
        this.space = b.space;
        this.singular = b.singular;
        this.plural = b.plural;
        this.numberPattern = b.numberPattern;
        this.localeTag = b.localeTag;
        this.abbreviateEnabled = b.abbreviateEnabled;
        this.abbreviateDecimals = b.abbreviateDecimals;
        this.suffixK = b.suffixK;
        this.suffixM = b.suffixM;
        this.suffixB = b.suffixB;
        this.suffixT = b.suffixT;
        this.fractionalDigits = b.fractionalDigits;
        this.defaultCurrency = b.defaultCurrency;
        this.persistToMySQL = b.persistToMySQL;
    }

    public Locale resolveLocale() {
        if (localeTag == null || localeTag.isEmpty() || "auto".equalsIgnoreCase(localeTag)) {
            return Locale.getDefault(Locale.Category.FORMAT);
        }
        String lower = localeTag.toLowerCase(Locale.ROOT);
        switch (lower) {
            case "us": case "en": case "uk": return Locale.US;
            case "eu": case "es": case "de": case "nl": case "it": case "pt": return Locale.GERMANY;
            case "in": return Locale.forLanguageTag("en-IN");
            case "ch": return Locale.forLanguageTag("de-CH");
            case "fr": return Locale.FRANCE;
            case "pl": return Locale.forLanguageTag("pl-PL");
            case "ru": return Locale.forLanguageTag("ru-RU");
            case "hi": return Locale.forLanguageTag("hi-IN");
            case "zh_cn": case "zh-cn": return Locale.SIMPLIFIED_CHINESE;
            case "zh_tw": case "zh-tw": return Locale.TRADITIONAL_CHINESE;
            default: break;
        }
        try {
            Locale parsed = Locale.forLanguageTag(localeTag);
            if (parsed != null && !parsed.toLanguageTag().isEmpty()) return parsed;
        } catch (Exception ignored) {}
        return Locale.getDefault(Locale.Category.FORMAT);
    }

    public DecimalFormat buildDecimalFormat() {
        DecimalFormatSymbols symbols = new DecimalFormatSymbols(resolveLocale());
        if (localeTag != null) {
            String lower = localeTag.trim().toLowerCase(Locale.ROOT);
            switch (lower) {
                case "ch":
                    symbols.setGroupingSeparator('\u2019');
                    symbols.setDecimalSeparator('.');
                    break;
                case "fr":
                    symbols.setGroupingSeparator('\u202F');
                    symbols.setDecimalSeparator(',');
                    break;
                default: break;
            }
        }
        try {
            return new DecimalFormat(numberPattern, symbols);
        } catch (IllegalArgumentException ex) {
            return new DecimalFormat("#,##0.00", symbols);
        }
    }

    public String applySymbol(String formattedNumber) {
        if (symbol == null || symbol.isEmpty()) return formattedNumber;
        String sep = space ? " " : "";
        String pos = (position == null) ? "none" : position.trim().toLowerCase(Locale.ROOT);
        if ("prefix".equals(pos)) return symbol + sep + formattedNumber;
        if ("suffix".equals(pos)) return formattedNumber + sep + symbol;
        return formattedNumber;
    }

    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        String id = "default";
        String symbol = "";
        String position = "none";
        boolean space = true;
        String singular = "dollar";
        String plural = "dollars";
        String numberPattern = "#,##0.00";
        String localeTag = "auto";
        boolean abbreviateEnabled = false;
        int abbreviateDecimals = 1;
        String suffixK = "k";
        String suffixM = "m";
        String suffixB = "b";
        String suffixT = "t";
        int fractionalDigits = 2;
        boolean defaultCurrency = false;
        boolean persistToMySQL = true;

        public Builder id(String v) { if (v != null) id = v; return this; }
        public Builder symbol(String v) { if (v != null) symbol = v; return this; }
        public Builder position(String v) { if (v != null) position = v; return this; }
        public Builder space(boolean v) { space = v; return this; }
        public Builder singular(String v) { if (v != null) singular = v; return this; }
        public Builder plural(String v) { if (v != null) plural = v; return this; }
        public Builder numberPattern(String v) { if (v != null && !v.isEmpty()) numberPattern = v; return this; }
        public Builder localeTag(String v) { if (v != null) localeTag = v; return this; }
        public Builder abbreviateEnabled(boolean v) { abbreviateEnabled = v; return this; }
        public Builder abbreviateDecimals(int v) { abbreviateDecimals = Math.max(0, Math.min(8, v)); return this; }
        public Builder suffixK(String v) { if (v != null) suffixK = v; return this; }
        public Builder suffixM(String v) { if (v != null) suffixM = v; return this; }
        public Builder suffixB(String v) { if (v != null) suffixB = v; return this; }
        public Builder suffixT(String v) { if (v != null) suffixT = v; return this; }
        public Builder fractionalDigits(int v) { fractionalDigits = Math.max(0, Math.min(8, v)); return this; }
        public Builder defaultCurrency(boolean v) { defaultCurrency = v; return this; }
        public Builder persistToMySQL(boolean v) { persistToMySQL = v; return this; }

        public CurrencyDef build() {
            if (id == null || id.isEmpty()) id = "default";
            return new CurrencyDef(this);
        }
    }
}
