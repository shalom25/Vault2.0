package com.example.vault.economy;

import java.text.DecimalFormat;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public final class CurrencyData {
    public final CurrencyDef def;
    public final ConcurrentMap<UUID, Double> balances;
    public final ConcurrentMap<String, ConcurrentMap<UUID, Double>> worldBalances;

    CurrencyData(CurrencyDef def) {
        this.def = def;
        this.balances = new ConcurrentHashMap<>();
        this.worldBalances = new ConcurrentHashMap<>();
    }

    public Map<UUID, Double> snapshotBalances() {
        return new HashMap<>(balances);
    }

    public Map<String, Map<UUID, Double>> snapshotWorldBalances() {
        Map<String, Map<UUID, Double>> out = new HashMap<>();
        for (Map.Entry<String, ConcurrentMap<UUID, Double>> e : worldBalances.entrySet()) {
            out.put(e.getKey(), new HashMap<>(e.getValue()));
        }
        return out;
    }

    public DecimalFormat buildDf() { return def.buildDecimalFormat(); }
}
