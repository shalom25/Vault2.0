---
title: Currency Abbreviation
description: Automatic abbreviation of large balances — decimals, suffix (k/m/b/t), when abbreviation kicks in, thresholds (k=1000, m=1_000_000, etc.) and PlaceholderAPI %vault_eco_balance_short%.
---

# 🔢 Currency Abbreviation

When a balance reaches the **thousands**, Vault can automatically abbreviate it using suffixes (`k`, `m`, `b`, `t`) instead of showing the full figure. Used mainly in leaderboards (Tablist, Scoreboards via PlaceholderAPI) and in the `%vault_eco_balance_short%` placeholder.

---

## 1. `currency.abbreviate.*` block

```yaml
currency:
  abbreviate:
    decimals: 1                 # decimals after abbreviating
    suffix:
      k: "k"                    # 10^3 = thousands
      m: "m"                    # 10^6 = millions
      b: "b"                    # 10^9 = billions
      t: "t"                    # 10^12 = trillions
```

---

## 2. Abbreviation rule (thresholds)

The abbreviation function compares the absolute value against **1000**, **1 000 000**, **1 000 000 000**, **1 000 000 000 000** and picks the smallest suffix that can represent the number. If it is `< 1000` **no abbreviation** happens and it returns the normal number (with 2 decimals).

```
|value| < 1000            → not abbreviated  (normal format)
|value| ≥ 1000            → k
|value| ≥ 1 000 000       → m
|value| ≥ 1 000 000 000   → b
|value| ≥ 1 000 000 000 000 → t
```

Formula: `displayValue = |value| / divisor` (rounded to `abbreviate.decimals` decimals).

---

## 3. Examples with `decimals: 1` and default suffixes

Let `amount = X`, `suffix.k = "k"`, `suffix.m = "m"`, `suffix.b = "b"`, `suffix.t = "t"`:

| Original value | Abbreviated | Explanation |
| :--- | :--- | :--- |
| `500` | `500,00` | < 1000 → not abbreviated |
| `1 234` | `1,2 k` | 1234 / 1000 = 1,234 → 1,2 |
| `9 999` | `10,0 k` | 9999 / 1000 = 9,999 → 10,0 |
| `15 000` | `15,0 k` | |
| `123 456,78` | `123,5 k` | 123456,78 / 1000 = 123,45678 → 123,5 |
| `1 234 567,89` | `1,2 m` | 1 234 567,89 / 1e6 = 1,23456789 → 1,2 |
| `9 999 999` | `10,0 m` | |
| `987 654 321` | `987,7 m` | (still < 1e9, so still in m) |
| `1 500 000 000` | `1,5 b` | / 1e9 |
| `4 200 000 000 000` | `4,2 t` | / 1e12 |

---

## 4. Varying `abbreviate.decimals`

| Value | `decimals: 0` | `decimals: 1` | `decimals: 2` |
| :--- | :--- | :--- | :--- |
| `1 234` | `1 k` | `1,2 k` | `1,23 k` |
| `1 234 567` | `1 m` | `1,2 m` | `1,23 m` |
| `987 654` | `988 k` | `987,7 k` | `987,65 k` |

Recommendation:
- **Tablist / Scoreboard** (tight spaces): `decimals: 0` or `1`.
- **Interactive chat / item lore** (more detail): `decimals: 1` or `2`.

---

## 5. Overriding suffixes per currency

In multi-currency, each `<cid>` can have its own table:

```yaml
currencies:
  default:
    symbol: "$"
    position: suffix
    space: true
    abbreviate:
      decimals: 1
      suffix:
        k: "k"
        m: "M"
        b: "B"
        t: "T"

  gems:
    symbol: "💎"
    abbreviate:
      decimals: 0
      suffix:
        k: "K"
        m: "Mio"
        b: "B"
        t: "T"
```

Result:
| Currency | Value | Abbreviated |
| :--- | :--- | :--- |
| `default` | 1 234 567 | `1,2 M $` |
| `gems` | 1 234 567 | `1Mio💎` |

---

## 6. Placeholders that use abbreviation

- `%vault_eco_balance_short%` — abbreviates `default` currency, 1 decimal (per `abbreviate.decimals`).
- `%vault_top_amount_<n>%` — top entries use abbreviation by default if the config has it enabled.
- `%vault2_balance_formatted_<cid>%` — uses `economy.format(cid, amount)` → abbreviates per `currencies.<cid>.abbreviate.*`.

---

## 7. Java implementation (reference)

Pseudocode of `SimpleEconomy.abbreviate(double amount, CurrencyDef def)`:

```java
public String abbreviate(double amount, CurrencyDef def) {
    double abs = Math.abs(amount);
    String sign = amount < 0 ? "-" : "";
    long[] divs      = {1_000L, 1_000_000L, 1_000_000_000L, 1_000_000_000_000L};
    String[] sufs    = {def.suffixK(), def.suffixM(), def.suffixB(), def.suffixT()};

    int decimals = def.getAbbreviateDecimals();
    String pattern = decimals == 0 ? "0" : ("0." + "0".repeat(decimals));
    DecimalFormat df = new DecimalFormat(pattern, symbolsFromLocale(def));

    for (int i = divs.length - 1; i >= 0; i--) {
        if (abs >= divs[i]) {
            double v = abs / (double) divs[i];
            return sign + df.format(v) + sufs[i];
        }
    }
    // didn't reach 1000 → return with normal format
    return sign + regularFormat(abs);
}
```

If your server operates with figures above **1 quadrillion** (`1e15`) manually add an extra suffix by editing `SimpleEconomy.java` (in v2.1 `t` is the ceiling).
