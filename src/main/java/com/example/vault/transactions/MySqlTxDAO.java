package com.example.vault.transactions;

import com.example.vault.storage.Database;

import java.io.IOException;
import java.sql.*;
import java.util.*;

public class MySqlTxDAO implements TransactionLogDAO {
    private final javax.sql.DataSource dataSource;

    public MySqlTxDAO(Database database) {
        this.dataSource = Objects.requireNonNull(database, "database").getDataSource();
    }

    @Override
    public void insertBatch(List<TxRecord> records) throws IOException {
        if (records == null || records.isEmpty()) return;
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "INSERT IGNORE INTO vault_transactions (tx_id, serial, ts, tx_type, currency_id, from_uuid, to_uuid, amount, world_name, metadata_json) " +
                             "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
            for (TxRecord r : records) {
                ps.setString(1, r.getTxId());
                ps.setLong(2, r.getSerial());
                ps.setTimestamp(3, new Timestamp(r.getInstantMs()));
                ps.setString(4, r.getTxType().name());
                ps.setString(5, r.getCurrencyId());
                if (r.getFromUuid() == null) ps.setNull(6, Types.VARCHAR);
                else ps.setString(6, r.getFromUuid().toString());
                if (r.getToUuid() == null) ps.setNull(7, Types.VARCHAR);
                else ps.setString(7, r.getToUuid().toString());
                ps.setBigDecimal(8, java.math.BigDecimal.valueOf(r.getAmount()));
                ps.setString(9, r.getWorldName());
                ps.setString(10, metadataJson(r.getMetadata()));
                ps.addBatch();
            }
            ps.executeBatch();
        } catch (SQLException ex) {
            throw new IOException(ex.getMessage(), ex);
        }
    }

    private static String metadataJson(Map<String, String> md) {
        if (md == null || md.isEmpty()) return "{}";
        StringBuilder sb = new StringBuilder();
        sb.append('{');
        boolean first = true;
        for (Map.Entry<String, String> e : md.entrySet()) {
            if (!first) sb.append(',');
            sb.append('"').append(escape(e.getKey())).append("\":\"").append(escape(e.getValue())).append('"');
            first = false;
        }
        sb.append('}');
        return sb.toString();
    }

    private static String escape(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static Map<String, String> parseMeta(String s) {
        Map<String, String> out = new LinkedHashMap<>();
        if (s == null || s.isEmpty() || s.equals("{}")) return out;
        String inner = s.startsWith("{") ? s.substring(1, s.length() - (s.endsWith("}") ? 1 : 0)) : s;
        int i = 0;
        while (i < inner.length()) {
            if (inner.charAt(i) == '"') {
                i++;
                String k = readString(inner, i);
                i += k.length() + 2;
                while (i < inner.length() && inner.charAt(i) != ':') i++;
                i++;
                while (i < inner.length() && inner.charAt(i) != '"') i++;
                i++;
                String v = readString(inner, i);
                i += v.length() + 2;
                out.put(k, v);
                while (i < inner.length() && inner.charAt(i) != ',') i++;
                i++;
            } else i++;
        }
        return out;
    }

    private static String readString(String s, int from) {
        StringBuilder sb = new StringBuilder();
        int i = from;
        while (i < s.length()) {
            char c = s.charAt(i);
            if (c == '"') break;
            if (c == '\\' && i + 1 < s.length()) {
                char n = s.charAt(i + 1);
                if (n == '"') sb.append('"');
                else if (n == '\\') sb.append('\\');
                else { sb.append(c); sb.append(n); }
                i += 2;
            } else { sb.append(c); i++; }
        }
        return sb.toString();
    }

    private List<TxRecord> queryRecords(String sql, BiConsumerPSSetter binder, int limit) throws IOException {
        List<TxRecord> out = new ArrayList<>();
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            if (binder != null) binder.accept(ps);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next() && out.size() < limit) {
                    TxRecord.Builder b = TxRecord.builder();
                    b.serial(rs.getLong("serial"));
                    b.txId(rs.getString("tx_id"));
                    Timestamp ts = rs.getTimestamp("ts");
                    b.instantMs(ts == null ? 0L : ts.getTime());
                    String t = rs.getString("tx_type");
                    try { b.txType(TxType.valueOf(t)); } catch (Exception ignored) { b.txType(TxType.PLAYER_PAY); }
                    b.currencyId(rs.getString("currency_id"));
                    String fu = rs.getString("from_uuid");
                    if (fu != null) try { b.fromUuid(UUID.fromString(fu)); } catch (Exception ignored) {}
                    String tu = rs.getString("to_uuid");
                    if (tu != null) try { b.toUuid(UUID.fromString(tu)); } catch (Exception ignored) {}
                    b.amount(rs.getBigDecimal("amount") == null ? 0.0 : rs.getBigDecimal("amount").doubleValue());
                    b.worldName(rs.getString("world_name"));
                    b.metadata(parseMeta(rs.getString("metadata_json")));
                    out.add(b.build());
                }
            }
        } catch (SQLException ex) {
            throw new IOException(ex.getMessage(), ex);
        }
        return out;
    }

    @Override
    public List<TxRecord> recentForPlayer(UUID player, int limit) throws IOException {
        if (player == null) return new ArrayList<>();
        int take = Math.max(1, limit);
        return queryRecords(
                "SELECT serial, tx_id, ts, tx_type, currency_id, from_uuid, to_uuid, amount, world_name, metadata_json " +
                        "FROM vault_transactions WHERE from_uuid = ? OR to_uuid = ? ORDER BY serial DESC LIMIT ?",
                ps -> {
                    ps.setString(1, player.toString());
                    ps.setString(2, player.toString());
                    ps.setInt(3, take);
                },
                take
        );
    }

    @Override
    public List<TxRecord> recentForTeam(String teamId, int limit) throws IOException {
        if (teamId == null || teamId.isEmpty()) return new ArrayList<>();
        int take = Math.max(1, limit);
        return queryRecords(
                "SELECT serial, tx_id, ts, tx_type, currency_id, from_uuid, to_uuid, amount, world_name, metadata_json " +
                        "FROM vault_transactions WHERE JSON_UNQUOTE(JSON_EXTRACT(metadata_json, '$.team_id')) = ? " +
                        "ORDER BY serial DESC LIMIT ?",
                ps -> {
                    ps.setString(1, teamId);
                    ps.setInt(2, take);
                },
                take
        );
    }

    @Override
    public long countAll() throws IOException {
        try (Connection c = dataSource.getConnection();
             Statement st = c.createStatement();
             ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM vault_transactions")) {
            if (rs.next()) return rs.getLong(1);
        } catch (SQLException ex) {
            throw new IOException(ex.getMessage(), ex);
        }
        return 0L;
    }

    interface BiConsumerPSSetter { void accept(PreparedStatement ps) throws SQLException; }
}
