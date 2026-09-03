package com.example.vault.importer;

import com.example.vault.economy.SimpleEconomy;
import com.example.vault.util.PlayerResolver;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class EssentialsImportService {
    private final Plugin plugin;
    private final SimpleEconomy economy;

    public EssentialsImportService(Plugin plugin, SimpleEconomy economy) {
        this.plugin = plugin;
        this.economy = economy;
    }

    public void runOnce(int mode) {
        // Legacy marker (visible) and new hidden/internal marker
        File legacyMarker = new File(plugin.getDataFolder(), "essentials_import.done");
        File internalDir = new File(plugin.getDataFolder(), ".internal");
        File marker = new File(internalDir, "essentials_import.done");
        if (legacyMarker.exists() || marker.exists()) {
            plugin.getLogger().info("Importación de Essentials ya realizada; se omite.");
            return;
        }

        File pluginsDir = plugin.getDataFolder().getParentFile(); // 'plugins/'
        File essentialsUserdata = new File(pluginsDir, "Essentials/userdata");
        if (!essentialsUserdata.exists() || !essentialsUserdata.isDirectory()) {
            plugin.getLogger().info("No se encontró el directorio userdata de Essentials: " + essentialsUserdata.getAbsolutePath());
            plugin.getLogger().info("No se creó marcador de importación; se volverá a intentar en el próximo inicio si habilitas import.essentials.enabled.");
            return;
        }
        File[] files = essentialsUserdata.listFiles((dir, name) -> name.endsWith(".yml"));
        if (files == null || files.length == 0) {
            plugin.getLogger().info("No hay archivos de Essentials para importar.");
            plugin.getLogger().info("No se creó marcador de importación; se volverá a intentar en el próximo inicio si habilitas import.essentials.enabled.");
            return;
        }

        ImportStats stats = importFromEssentialsDetailed(mode);
        // Persistencia según config: import.essentials.target: file|auto (auto = MySQL si está activo)
        String target = plugin.getConfig().getString("import.essentials.target", "auto");
        boolean forceFile = "file".equalsIgnoreCase(target);
        try {
            if (forceFile) {
                economy.saveToFile();
            } else {
                economy.save();
            }
        } catch (IOException e) {
            plugin.getLogger().warning("Fallo al guardar balances tras importación: " + e.getMessage());
        }
        if (stats.failed == 0) {
            try {
                if (!internalDir.exists()) internalDir.mkdirs();
                if (!marker.exists() && !marker.createNewFile()) {
                    plugin.getLogger().warning("No se pudo crear el marcador de importación; podría ejecutarse de nuevo en el próximo inicio.");
                }
            } catch (IOException e) {
                plugin.getLogger().warning("Error al escribir el marcador: " + e.getMessage());
            }
        } else {
            plugin.getLogger().warning("La importación de Essentials terminó con errores; no se creó el marcador para permitir reintentar.");
        }
        // Intentar ocultar en Windows (atributo DOS hidden); en Unix basta prefijo '.'
        try {
            Path dirPath = internalDir.toPath();
            Path markerPath = marker.toPath();
            Files.setAttribute(dirPath, "dos:hidden", true);
            Files.setAttribute(markerPath, "dos:hidden", true);
        } catch (Exception ignored) { /* atributo oculto no soportado; se mantiene prefijo '.' */ }

        plugin.getLogger().info("Importación de Essentials finalizada. Escaneados: " + stats.scanned +
                ", importados: " + stats.imported +
                ", omitidos: " + stats.skipped +
                ", duplicados: " + stats.duplicates +
                ", errores: " + stats.failed +
                (forceFile ? " (guardado en balances.yml)" : "") +
                (stats.failed == 0 ? " (marcador interno oculto)" : ""));
    }

    public int importFromEssentials(int mode) {
        return importFromEssentialsDetailed(mode).imported;
    }

    private ImportStats importFromEssentialsDetailed(int mode) {
        File pluginsDir = plugin.getDataFolder().getParentFile(); // 'plugins/'
        File essentialsUserdata = new File(pluginsDir, "Essentials/userdata");
        if (!essentialsUserdata.exists() || !essentialsUserdata.isDirectory()) {
            plugin.getLogger().info("No se encontró el directorio userdata de Essentials: " + essentialsUserdata.getAbsolutePath());
            return new ImportStats();
        }
        File[] files = essentialsUserdata.listFiles((dir, name) -> name.endsWith(".yml"));
        if (files == null || files.length == 0) {
            plugin.getLogger().info("No hay archivos de Essentials para importar.");
            return new ImportStats();
        }
        Arrays.sort(files, Comparator.comparing(File::getName, String.CASE_INSENSITIVE_ORDER));

        ImportStats stats = new ImportStats();
        String defId = economy.getDefaultCurrencyId();
        Map<UUID, Double> currentBalances = economy.snapshotBalances(defId);
        Set<UUID> seen = new HashSet<>();
        for (File f : files) {
            stats.scanned++;
            try {
                YamlConfiguration cfg = YamlConfiguration.loadConfiguration(f);
                ResolvedPlayer resolved = resolvePlayer(f, cfg);
                if (resolved == null || resolved.player == null) {
                    stats.failed++;
                    plugin.getLogger().warning("Fallo al importar " + f.getName() + ": no se pudo resolver el jugador.");
                    continue;
                }
                UUID uuid = resolved.player.getUniqueId();
                if (!seen.add(uuid)) {
                    stats.duplicates++;
                    continue;
                }

                Double money = parseMoney(cfg.get("money"));
                if (money == null) {
                    stats.failed++;
                    plugin.getLogger().warning("Fallo al importar " + f.getName() + ": valor 'money' inválido.");
                    continue;
                }

                boolean exists = currentBalances.containsKey(uuid);
                double current = exists ? currentBalances.get(uuid) : 0.0;

                boolean shouldReplace = (mode == 1); // 0=merge, 1=replace
                boolean canBackfillEmpty = !shouldReplace && exists && Math.abs(current) < 1.0E-9 && Math.abs(money) > 1.0E-9;
                if (shouldReplace || !exists || canBackfillEmpty) {
                    economy.setBalance(defId, resolved.player, money);
                    currentBalances.put(uuid, money);
                    stats.imported++;
                } else {
                    stats.skipped++;
                }
            } catch (Exception ex) {
                stats.failed++;
                plugin.getLogger().warning("Fallo al importar " + f.getName() + ": " + ex.getMessage());
            }
        }
        return stats;
    }

    private ResolvedPlayer resolvePlayer(File file, YamlConfiguration cfg) {
        String fileBase = file.getName().substring(0, file.getName().length() - 4);

        OfflinePlayer byUuid = resolveByUuid(fileBase);
        if (byUuid == null) byUuid = resolveByUuid(cfg.getString("uuid"));
        if (byUuid == null) byUuid = resolveByUuid(cfg.getString("last-account-uuid"));
        if (byUuid != null) return new ResolvedPlayer(byUuid, "uuid");

        String playerName = firstNonBlank(
                cfg.getString("last-account-name"),
                cfg.getString("player-name"),
                cfg.getString("name"),
                fileBase
        );
        if (playerName == null) return null;

        OfflinePlayer byName = PlayerResolver.resolveByNameWithOfflineFallback(plugin, playerName);
        if (byName != null) return new ResolvedPlayer(byName, "name");
        return null;
    }

    private OfflinePlayer resolveByUuid(String raw) {
        if (raw == null || raw.trim().isEmpty()) return null;
        try {
            return Bukkit.getOfflinePlayer(UUID.fromString(raw.trim()));
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.trim().isEmpty()) {
                return value.trim();
            }
        }
        return null;
    }

    private Double parseMoney(Object raw) {
        if (raw == null) return 0.0;
        if (raw instanceof Number) {
            double value = ((Number) raw).doubleValue();
            return Double.isFinite(value) ? value : null;
        }
        try {
            String text = String.valueOf(raw).trim();
            if (text.isEmpty()) return 0.0;
            text = text.replace(" ", "").replace(",", "");
            double value = Double.parseDouble(text);
            return Double.isFinite(value) ? value : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static final class ResolvedPlayer {
        final OfflinePlayer player;

        private ResolvedPlayer(OfflinePlayer player, String source) {
            this.player = player;
        }
    }

    private static final class ImportStats {
        int scanned;
        int imported;
        int skipped;
        int duplicates;
        int failed;
    }
}
