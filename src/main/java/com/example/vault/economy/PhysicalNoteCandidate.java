package com.example.vault.economy;

import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Identifies only signed legacy banknote-looking lore; never generic PAPER instructions. */
final class PhysicalNoteCandidate {
    private PhysicalNoteCandidate() {}

    static boolean hasSignedLegacyLore(List<String> lore, Set<String> serialLabels) {
        if (lore == null || serialLabels == null || serialLabels.isEmpty()) return false;
        boolean hasSerial = false;
        boolean hasSignature = false;
        for (String line : lore) {
            if (line == null) continue;
            String trimmed = line.trim();
            String lower = trimmed.toLowerCase(Locale.ROOT);
            if (lower.startsWith("sig:") || lower.startsWith("hmac:")) {
                if (!trimmed.substring(trimmed.indexOf(':') + 1).trim().isEmpty()) {
                    hasSignature = true;
                }
            }
            for (String prefix : serialLabels) {
                if (prefix == null || prefix.isBlank()) continue;
                String marker = prefix.trim();
                if (!lower.startsWith(marker.toLowerCase(Locale.ROOT))) continue;
                String remainder = trimmed.substring(marker.length()).trim();
                if (remainder.startsWith(":")) remainder = remainder.substring(1).trim();
                if (!remainder.isEmpty()) {
                    hasSerial = true;
                    break;
                }
            }
        }
        return hasSerial && hasSignature;
    }
}
