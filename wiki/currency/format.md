---
title: Currency Format
description: Controls how money is displayed — symbol, position (suffix/prefix), space, locale (us, eu, in, ch, fr) and rendered examples. Usage of currency.format() in Java and PlaceholderAPI %vault_balance_formatted%.
---

# 💱 Currency Format

Vault v2.1.0 uses 4 keys to build the formatted string the player sees: `currency.symbol`, `currency.position`, `currency.space` and `currency.locale`. Both the legacy API `economy.format(double)` and the Placeholders `%vault_balance_formatted%` respect these 4 keys.

---

## 1. The 4 format keys

```yaml
currency:
  symbol: "$"          # String (supports & color codes and emojis)
  position: suffix     # suffix | prefix
  space: true          # true → 10 $ · false → 10$
  locale: "auto"       # us | eu | uk | in | ch | fr | BCP-47 (es-ES, de-DE, pt-BR...)
```

---

## 2. `symbol` + `position` + `space` (combinations)

Let `amount = 1234567.89`. Typical combinations:

| symbol | position | space | Visual result (before locale) |
| :--- | :--- | :--- | :--- |
| `$` | `prefix` | `true` | `$ 1234567.89` |
| `$` | `prefix` | `false` | `$1234567.89` |
| `$` | `suffix` | `true` | `1234567.89 $` |
| `$` | `suffix` | `false` | `1234567.89$` |
| `€` | `suffix` | `true` | `1234567.89 €` |
| `&6💰` | `suffix` | `true` | `1234567.89 &6💰` (then colored via ChatColor.translateAlternateColorCodes) |
| `coins` | `suffix` | `true` | `1234567.89 coins` |

---

## 3. `currency.locale` — separator presets

The locale **only affects the numeric part** (thousands grouping and decimal character). It does not touch the symbol or position.

| Locale preset | Example with 1234567.89 | Notes |
| :--- | :--- | :--- |
| `us` or `uk` | `1,234,567.89` | English style (comma thousands, dot decimal). |
| `eu` | `1.234.567,89` | Continental European (ES, DE, IT, PT). |
| `in` | `12,34,567.89` | Indian grouping (lakhs: `1,00,000` = 1 lakh; `1,00,00,000` = 1 crore). |
| `ch` | `1'234'567.89` | Swiss (apostrophe thousands). |
| `fr` | `1 234 567,89` | French (thin-space as thousands separator). |
| `auto` or `""` | (the host JVM's) | Uses `Locale.getDefault()`. Beware if your host is in `en_US` but your community is French-speaking. |

Besides presets you can pass **BCP-47** directly and `DecimalFormatSymbols.getInstance(locale)` will resolve it:

```yaml
locale: "es-ES"     # → equivalent to preset eu: 1.234.567,89
locale: "de-DE"     # → preset eu
locale: "it-IT"     # → preset eu
locale: "pt-BR"     # → 1.234.567,89 (dot thousands, comma decimal)
locale: "fr-FR"     # → 1 234 567,89 (preset fr)
locale: "ja-JP"     # → 1,234,567.89 (preset us)
```

---

## 4. Full combination: `locale` + `symbol` + `position`

Example for a French server:

```yaml
currency:
  symbol: "€"
  position: suffix
  space: true
  locale: "eu"
```

Result:

| Unformatted value | Formatted value |
| :--- | :--- |
| `0.0` | `0,00 €` |
| `12.5` | `12,50 €` |
| `1234.56` | `1.234,56 €` |
| `999999.99` | `999.999,99 €` |
| `1234567.89` | `1.234.567,89 €` |

---

## 5. Programmatic usage in Java

Your plugin can (and should) use the `Economy.format()` API instead of formatting manually, so it automatically respects changes in `config.yml` without restart:

```java
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.entity.Player;

public class ShopCommand {

    private Economy eco;

    public boolean hookVault() {
        RegisteredServiceProvider<Economy> rsp =
                Bukkit.getServicesManager().getRegistration(Economy.class);
        if (rsp == null) return false;
        eco = rsp.getProvider();
        return eco != null;
    }

    public void showPrice(Player p, double price) {
        // eco.format() already applies symbol + position + space + locale and abbreviates if applicable
        p.sendMessage("Costs: " + eco.format(price));
        // → "Costs: 1.234,56 €"
    }
}
```

---

## 6. Placeholders that respect currency format

PlaceholderAPI is a soft-dependency (`softdepend: [PlaceholderAPI, ...]`) and when present these are registered automatically:

```
%vault_balance%                → 1234567.89  (raw, no symbol, no separators)
%vault_balance_formatted%      → 1.234.567,89 €  (full format = eco.format())
%vault_currency_symbol%        → €
%vault_eco_balance_fixed%      → 1234567.89  (always 2 decimals)
%vault_eco_balance_commas%     → 1,234,567.89  (hardcoded Locale.US, ignores config)
%vault_eco_balance_short%      → 1,2m  (abbreviation, see currency/abbreviation.md)
```

Next: [Multi-currency →](/wiki/currency/multi-currency) and [Abbreviation →](/wiki/currency/abbreviation).
