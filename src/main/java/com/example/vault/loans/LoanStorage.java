package com.example.vault.loans;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;

public interface LoanStorage {
    Map<UUID, Loan> loadAll() throws IOException;
    void saveAll(Map<UUID, Loan> loans) throws IOException;
}

