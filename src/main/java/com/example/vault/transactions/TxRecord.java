package com.example.vault.transactions;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public final class TxRecord {
    private final String txId;
    private final long instantMs;
    private final TxType txType;
    private final String currencyId;
    private final UUID fromUuid;
    private final UUID toUuid;
    private final double amount;
    private final String worldName;
    private final Map<String, String> metadata;
    private final long serial;

    private TxRecord(Builder b) {
        this.txId = Objects.requireNonNull(b.txId, "txId");
        this.instantMs = b.instantMs > 0L ? b.instantMs : System.currentTimeMillis();
        this.txType = Objects.requireNonNull(b.txType, "txType");
        this.currencyId = b.currencyId == null || b.currencyId.isEmpty() ? "default" : b.currencyId;
        this.fromUuid = b.fromUuid;
        this.toUuid = b.toUuid;
        this.amount = Double.isFinite(b.amount) ? b.amount : 0.0;
        this.worldName = b.worldName;
        Map<String, String> md = b.metadata == null ? Collections.emptyMap() : new LinkedHashMap<>(b.metadata);
        this.metadata = Collections.unmodifiableMap(md);
        if (b.serial <= 0L) {
            throw new IllegalArgumentException("serial must be positive");
        }
        this.serial = b.serial;
    }

    public String getTxId() { return txId; }
    public long getInstantMs() { return instantMs; }
    public TxType getTxType() { return txType; }
    public String getCurrencyId() { return currencyId; }
    public UUID getFromUuid() { return fromUuid; }
    public UUID getToUuid() { return toUuid; }
    public double getAmount() { return amount; }
    public String getWorldName() { return worldName; }
    public Map<String, String> getMetadata() { return metadata; }
    public long getSerial() { return serial; }

    public static Builder builder() { return new Builder(); }

    public Builder toBuilder() {
        Builder b = new Builder();
        b.txId = this.txId;
        b.instantMs = this.instantMs;
        b.txType = this.txType;
        b.currencyId = this.currencyId;
        b.fromUuid = this.fromUuid;
        b.toUuid = this.toUuid;
        b.amount = this.amount;
        b.worldName = this.worldName;
        b.metadata = new LinkedHashMap<>(this.metadata);
        b.serial = this.serial;
        return b;
    }

    public static final class Builder {
        String txId;
        long instantMs;
        TxType txType;
        String currencyId = "default";
        UUID fromUuid;
        UUID toUuid;
        double amount;
        String worldName;
        Map<String, String> metadata;
        long serial;

        private Builder() {}

        public Builder txId(String id) { this.txId = id; return this; }
        public Builder instantMs(long ms) { this.instantMs = ms; return this; }
        public Builder txType(TxType t) { this.txType = t; return this; }
        public Builder currencyId(String id) { this.currencyId = id; return this; }
        public Builder fromUuid(UUID u) { this.fromUuid = u; return this; }
        public Builder toUuid(UUID u) { this.toUuid = u; return this; }
        public Builder amount(double a) { this.amount = a; return this; }
        public Builder worldName(String w) { this.worldName = w; return this; }
        public Builder metadata(Map<String, String> m) { this.metadata = m; return this; }
        public Builder putMeta(String k, String v) {
            if (this.metadata == null) this.metadata = new LinkedHashMap<>();
            this.metadata.put(k, v);
            return this;
        }
        public Builder serial(long s) { this.serial = s; return this; }

        public TxRecord build() {
            return new TxRecord(this);
        }
    }
}
