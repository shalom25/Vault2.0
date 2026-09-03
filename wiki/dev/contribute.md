---
title: Contribute
description: Maven setup, legacy/modern profiles (Java 17/21), i18n rules for 11 languages, prefix + space rule, HMAC notes.
---

# Contributing to Vault 2.1.0

Guide for developers who want to contribute to the project.

## Requirements

- Git
- JDK 17 (legacy profile) + JDK 21 (modern profile)
- Maven 3.9+ (via included Maven Wrapper)
- Spigot BuildTools (for servers 1.8.8 to 1.21)

## Clone Repository

```bash
git clone https://github.com/yourorg/Vault.git
cd Vault
```

## Build with Maven Wrapper

Vault includes **Maven profiles** for dual builds (Java 17 legacy + Java 21 modern).

| Profile | Java Target | Minecraft | Command |
|---|---|---|---|
| `legacy` | 17 | 1.8.8 - 1.20.4 | `mvnw -DskipTests package -P legacy` |
| `modern` | 21 | 1.20.5 - 1.21+ | `mvnw -DskipTests package -P modern` |
| `all` | both | complete | `mvnw -DskipTests package -P all` |

```bash
# Legacy build (1.8.x - 1.20.x with Java 17)
./mvnw clean package -DskipTests -P legacy
# Output: target/Vault-2.1.0-legacy.jar

# Modern build (1.20.5+ with Java 21)
./mvnw clean package -DskipTests -P modern
# Output: target/Vault-2.1.0-modern.jar

# Full build (both jars)
./mvnw clean package -DskipTests -P all
```

## Maven Profiles (pom.xml)

```xml
<profiles>
    <!-- Java 17 for legacy versions -->
    <profile>
        <id>legacy</id>
        <properties>
            <maven.compiler.source>17</maven.compiler.source>
            <maven.compiler.target>17</maven.compiler.target>
            <maven.compiler.release>17</maven.compiler.release>
            <classifier>legacy</classifier>
        </properties>
    </profile>

    <!-- Java 21 for modern versions -->
    <profile>
        <id>modern</id>
        <properties>
            <maven.compiler.source>21</maven.compiler.source>
            <maven.compiler.target>21</maven.compiler.target>
            <maven.compiler.release>21</maven.compiler.release>
            <classifier>modern</classifier>
        </properties>
    </profile>
</profiles>
```

## Run Tests

```bash
./mvnw test -P legacy

# Coverage
./mvnw test jacoco:report -P legacy
```

## i18n Rules (11 languages)

Vault supports 11 official languages. Each new message **must** be added to all 11 `messages_xx.yml` files:

| File | Language | Code |
|---|---|---|
| `messages_en.yml` | English | en |
| `messages_es.yml` | Español | es |
| `messages_pt.yml` | Português | pt |
| `messages_fr.yml` | Français | fr |
| `messages_de.yml` | Deutsch | de |
| `messages_it.yml` | Italiano | it |
| `messages_ru.yml` | Русский | ru |
| `messages_zh.yml` | 中文 | zh |
| `messages_ja.yml` | 日本語 | ja |
| `messages_ko.yml` | 한국어 | ko |
| `messages_ar.yml` | العربية | ar |

### Example new message

```yaml
# messages_en.yml
cmd.pay.success: "&aYou sent %amount% to %player%"
cmd.pay.received: "&a%player% sent you %amount%"

# messages_es.yml (REQUIRED, build fails if missing)
cmd.pay.success: "&aEnviaste %amount% a %player%"
cmd.pay.received: "&a%player% te envió %amount%"
```

If your PR touches messages and translations are **missing** in all 11 languages, CI fails with `I18nLinter: 4 messages missing`.

## Rule: Prefix + Space

Every chat message sent **NEVER** concatenates the prefix manually. Use `messages.prefixed(key)`:

```java
// ❌ WRONG
player.sendMessage("[Vault] " + message);      // No - causes "Text stuck to the prefix"

// ✅ CORRECT - Messages API manages the space automatically
player.sendMessage(messages.prefixed("cmd.pay.success", amount, target));
```

```java
// Implementation in Messages.java:
public String prefixed(String key, Object... args) {
    String prefix = get("prefix");              // "[Vault]"
    String body = format(key, args);            // "You sent $50 to Notch"
    return prefix + " " + body;                 // ✅ correct space always
}
```

## Rule: No `StringBuilder` without `colorize()` + `format()`

```java
// ❌ WRONG - unrendered colors in bank notes
ItemMeta meta = ...;
meta.setLore(List.of(economy.format(amount)));

// ✅ CORRECT
String line = colorize(economy.format(amount));
meta.setLore(List.of(line));
```

## HMAC Anti-Dupe (Notes)

**Important** when touching physical bank notes (PDC Paper / legacy ItemMeta).

The bank note is **not** just lore. It has an HMAC-SHA256 signature over 5 fields:

```
1) uuid     (unique UUID.random)
2) amount   (BigDecimal)
3) currency (String)
4) owner    (UUID that created it)
5) issuedAt (epoch ms)
```

It is signed with `Vault.hmacSecret` from `config.yml`:

```java
// Example HMAC signature:
String payload = uuid + "|" + amount + "|" + currency + "|" + owner + "|" + issuedAt;
Mac hmac = Mac.getInstance("HmacSHA256");
hmac.init(new SecretKeySpec(secret.getBytes(), "HmacSHA256"));
String signature = Base64.encode(hmac.doFinal(payload.getBytes()));
```

On redemption:

```java
if (!verifyHmac(reconstructedPayload, signatureFromLoreOrPDC)) {
    throw new NoteTamperedException("COUNTERFEIT NOTE");
}
```

### ⚠ Do not change the order of the 5 fields

If you add a field and the signer still signs 5 fields but the validator expects 6, `HMAC lore mismatch (5 fields vs 6)` and all existing notes become invalid. To add fields, use the HMAC version:

```yaml
hmac:
  version: 2       # v1 = 5 fields, v2 = 6 (new field)
  migration: true  # accepts both for 1 month
```

## Pull Request Checklist

- [ ] `mvnw -DskipTests package -P legacy` compiles OK
- [ ] `mvnw -DskipTests package -P modern` compiles OK
- [ ] `mvnw test` passes
- [ ] Messages added in all 11 `messages_xx.yml`
- [ ] Do not manually concatenate prefixes; use `messages.prefixed()`
- [ ] New notes go through correct HMAC (5 fields)
- [ ] Use `colorize(economy.format(...))` in item lore
- [ ] DB schema if changed: new Flyway migration in `src/main/resources/db/migration/`
- [ ] Changelog entry for the PR

## HMAC Secret Rotation

```yaml
# config.yml
hmac:
  secret: "CHANGE_THIS_STRING_TO_A_SECURE_32_BYTE_ONE"
  rotate_in_progress: false
  previous_secret: ""   # When rotating, put the old one here for 30 days
```

```bash
# Generate secure key:
python -c "import secrets; print(secrets.token_urlsafe(32))"
```

## IDE Setup

Import the project as a **Maven project** in IntelliJ / Eclipse / VSCode.

```
File → Open... → pom.xml
Maven → Profiles → enable 'legacy' by default
Run → Edit Config → 'Bukkit' with 1.20.4 server for test
```
