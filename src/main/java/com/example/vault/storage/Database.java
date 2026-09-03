package com.example.vault.storage;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.bukkit.plugin.Plugin;

import java.sql.*;
import java.util.*;

public class Database {
    private final HikariDataSource dataSource;

    public Database(Plugin plugin) {
        org.bukkit.configuration.file.FileConfiguration cfg = plugin.getConfig();
        String host = cfg.getString("storage.mysql.host", "localhost");
        int port = cfg.getInt("storage.mysql.port", 3306);
        String dbName = cfg.getString("storage.mysql.database", "vault");
        String params = cfg.getString("storage.mysql.params", "useSSL=false&serverTimezone=UTC");
        String user = cfg.getString("storage.mysql.user", cfg.getString("storage.mysql.username", "root"));
        String pass = cfg.getString("storage.mysql.password", "");

        String jdbcUrl = "jdbc:mysql://" + host + ":" + port + "/" + dbName +
                (params != null && !params.isEmpty() ? ("?" + params) : "");

        HikariConfig hc = new HikariConfig();
        hc.setJdbcUrl(jdbcUrl);
        hc.setUsername(user);
        hc.setPassword(pass);
        hc.setMaximumPoolSize(cfg.getInt("storage.mysql.pool.max", cfg.getInt("storage.mysql.pool_size", 10)));
        hc.setMinimumIdle(cfg.getInt("storage.mysql.pool.min_idle", 2));
        hc.setConnectionTimeout(cfg.getLong("storage.mysql.pool.connection_timeout_ms", 10000));
        hc.setIdleTimeout(cfg.getLong("storage.mysql.pool.idle_timeout_ms", 600000));
        hc.setMaxLifetime(cfg.getLong("storage.mysql.pool.max_lifetime_ms", 1800000));
        this.dataSource = new HikariDataSource(hc);
    }

    public HikariDataSource getDataSource() {
        return dataSource;
    }

    public void ensureSchema() throws SQLException {
        try (Connection c = dataSource.getConnection()) {
            try (Statement st = c.createStatement()) {
                st.execute("CREATE TABLE IF NOT EXISTS vault_balances (" +
                        "uuid CHAR(36) NOT NULL PRIMARY KEY," +
                        "balance DECIMAL(19,4) NOT NULL DEFAULT 0" +
                        ")");
            }
            try (Statement st = c.createStatement()) {
                st.execute("CREATE TABLE IF NOT EXISTS vault_world_balances (" +
                        "world_name VARCHAR(64) NOT NULL," +
                        "uuid CHAR(36) NOT NULL," +
                        "balance DECIMAL(19,4) NOT NULL DEFAULT 0," +
                        "PRIMARY KEY (world_name, uuid)" +
                        ")");
            }
            try (Statement st = c.createStatement()) {
                st.execute("CREATE TABLE IF NOT EXISTS vault_charge_requests (" +
                        "id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY," +
                        "recipient VARCHAR(16) NOT NULL," +
                        "sender VARCHAR(16) NOT NULL," +
                        "amount DECIMAL(19,4) NOT NULL," +
                        "created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP" +
                        ")");
                // Index para búsquedas rápidas por recipient
                try {
                    try (Statement st2 = c.createStatement()) {
                        st2.execute("CREATE INDEX IF NOT EXISTS idx_vcr_recipient ON vault_charge_requests(recipient)");
                    }
                } catch (SQLException ignore) { /* MySQL versiones antiguas no soportan IF NOT EXISTS en índices */ }
            }
            try (Statement st = c.createStatement()) {
                st.execute("CREATE TABLE IF NOT EXISTS vault_transactions (" +
                        "id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY," +
                        "tx_id VARCHAR(64) NOT NULL UNIQUE KEY," +
                        "serial BIGINT NOT NULL UNIQUE KEY," +
                        "ts TIMESTAMP NOT NULL," +
                        "tx_type VARCHAR(32) NOT NULL," +
                        "currency_id VARCHAR(32) NOT NULL DEFAULT 'default'," +
                        "from_uuid CHAR(36)," +
                        "to_uuid CHAR(36)," +
                        "amount DECIMAL(19,4) NOT NULL," +
                        "world_name VARCHAR(64)," +
                        "metadata_json TEXT" +
                        ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
                try {
                    try (Statement st2 = c.createStatement()) {
                        st2.execute("CREATE INDEX IF NOT EXISTS idx_vt_from ON vault_transactions(from_uuid)");
                    }
                } catch (SQLException ignore) {}
                try {
                    try (Statement st2 = c.createStatement()) {
                        st2.execute("CREATE INDEX IF NOT EXISTS idx_vt_to ON vault_transactions(to_uuid)");
                    }
                } catch (SQLException ignore) {}
                try {
                    try (Statement st2 = c.createStatement()) {
                        st2.execute("CREATE INDEX IF NOT EXISTS idx_vt_ts ON vault_transactions(ts)");
                    }
                } catch (SQLException ignore) {}
                try {
                    try (Statement st2 = c.createStatement()) {
                        st2.execute("CREATE INDEX IF NOT EXISTS idx_vt_currency ON vault_transactions(currency_id)");
                    }
                } catch (SQLException ignore) {}
            }
        }
    }

    public Map<UUID, Double> loadAllBalances() throws SQLException {
        Map<UUID, Double> out = new HashMap<>();
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement("SELECT uuid, balance FROM vault_balances")) {
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    try {
                        UUID uuid = UUID.fromString(rs.getString(1));
                        double bal = rs.getBigDecimal(2).doubleValue();
                        out.put(uuid, bal);
                    } catch (IllegalArgumentException ignored) {}
                }
            }
        }
        return out;
    }

    public Map<String, Map<UUID, Double>> loadAllWorldBalances() throws SQLException {
        Map<String, Map<UUID, Double>> out = new HashMap<>();
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement("SELECT world_name, uuid, balance FROM vault_world_balances")) {
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    try {
                        String worldName = rs.getString(1);
                        UUID uuid = UUID.fromString(rs.getString(2));
                        double bal = rs.getBigDecimal(3).doubleValue();
                        if (worldName == null || worldName.trim().isEmpty()) continue;
                        out.computeIfAbsent(worldName, ignored -> new HashMap<>()).put(uuid, bal);
                    } catch (IllegalArgumentException ignored) {}
                }
            }
        }
        return out;
    }

    public void clearAllBalances() throws SQLException {
        try (Connection c = dataSource.getConnection()) {
            try (Statement st = c.createStatement()) {
                st.executeUpdate("DELETE FROM vault_balances");
            }
            try (Statement st = c.createStatement()) {
                st.executeUpdate("DELETE FROM vault_world_balances");
            }
        }
    }

    public void saveBalances(Map<UUID, Double> balances) throws SQLException {
        if (balances == null || balances.isEmpty()) return;
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "INSERT INTO vault_balances (uuid, balance) VALUES (?, ?) " +
                             "ON DUPLICATE KEY UPDATE balance = VALUES(balance)")) {
            for (Map.Entry<UUID, Double> e : balances.entrySet()) {
                ps.setString(1, e.getKey().toString());
                ps.setBigDecimal(2, java.math.BigDecimal.valueOf(e.getValue()));
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    public void saveAllBalances(Map<UUID, Double> balances) throws SQLException {
        saveBalances(balances);
    }

    public void saveWorldBalances(Map<String, Map<UUID, Double>> balancesByWorld) throws SQLException {
        if (balancesByWorld == null || balancesByWorld.isEmpty()) return;
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "INSERT INTO vault_world_balances (world_name, uuid, balance) VALUES (?, ?, ?) " +
                             "ON DUPLICATE KEY UPDATE balance = VALUES(balance)")) {
            for (Map.Entry<String, Map<UUID, Double>> worldEntry : balancesByWorld.entrySet()) {
                String worldName = worldEntry.getKey();
                if (worldName == null || worldName.trim().isEmpty() || worldEntry.getValue() == null) continue;
                for (Map.Entry<UUID, Double> entry : worldEntry.getValue().entrySet()) {
                    if (entry.getKey() == null || entry.getValue() == null) continue;
                    ps.setString(1, worldName);
                    ps.setString(2, entry.getKey().toString());
                    ps.setBigDecimal(3, java.math.BigDecimal.valueOf(entry.getValue()));
                    ps.addBatch();
                }
            }
            ps.executeBatch();
        }
    }

    public void saveAllWorldBalances(Map<String, Map<UUID, Double>> balancesByWorld) throws SQLException {
        saveWorldBalances(balancesByWorld);
    }

    public static class ChargeRequest {
        public final String sender;
        public final double amount;
        public ChargeRequest(String sender, double amount) {
            this.sender = sender; this.amount = amount;
        }
    }

    public void addChargeRequest(String recipient, String sender, double amount) throws SQLException {
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "INSERT INTO vault_charge_requests (recipient, sender, amount) VALUES (?, ?, ?)")) {
            ps.setString(1, recipient);
            ps.setString(2, sender);
            ps.setBigDecimal(3, java.math.BigDecimal.valueOf(amount));
            ps.executeUpdate();
        }
    }

    public java.util.List<ChargeRequest> fetchAndDeletePendingRequests(String recipient, int limit) throws SQLException {
        java.util.List<ChargeRequest> list = new ArrayList<>();
        java.util.List<Long> ids = new ArrayList<>();
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT id, sender, amount FROM vault_charge_requests WHERE recipient = ? ORDER BY id ASC LIMIT ?")) {
            ps.setString(1, recipient);
            ps.setInt(2, Math.max(1, limit));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    ids.add(rs.getLong(1));
                    list.add(new ChargeRequest(rs.getString(2), rs.getBigDecimal(3).doubleValue()));
                }
            }
        }
        if (!ids.isEmpty()) {
            try (Connection c = dataSource.getConnection();
                 PreparedStatement ps = c.prepareStatement("DELETE FROM vault_charge_requests WHERE id = ?")) {
                for (Long id : ids) {
                    ps.setLong(1, id);
                    ps.addBatch();
                }
                ps.executeBatch();
            }
        }
        return list;
    }

    public void close() {
        try {
            if (dataSource != null) dataSource.close();
        } catch (Exception ignored) {}
    }
}
