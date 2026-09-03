package com.example.vault.transactions;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

public interface TransactionLogDAO {
    void insertBatch(List<TxRecord> records) throws IOException;

    List<TxRecord> recentForPlayer(UUID player, int limit) throws IOException;

    List<TxRecord> recentForTeam(String teamId, int limit) throws IOException;

    long countAll() throws IOException;

    default void close() throws IOException {}
}
