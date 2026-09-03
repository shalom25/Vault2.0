package com.example.vault.transactions;

public final class AntiDupeWindow {
    private final int capacity;
    private volatile long rejections = 0L;

    static final class Window<T> {
        private final java.util.LinkedHashMap<T, Boolean> set;

        Window(int cap) {
            this.set = new java.util.LinkedHashMap<T, Boolean>(cap + 1, 0.75f) {
                private static final long serialVersionUID = 1L;
                @Override
                protected boolean removeEldestEntry(java.util.Map.Entry<T, Boolean> eldest) {
                    return size() > cap;
                }
            };
        }

        boolean addIfAbsent(T t) {
            synchronized (set) {
                if (set.containsKey(t)) return false;
                return set.put(t, Boolean.TRUE) == null;
            }
        }

        boolean contains(T t) {
            synchronized (set) { return set.containsKey(t); }
        }

        void clear() {
            synchronized (set) { set.clear(); }
        }
    }

    private final Window<Long> serialWindow;
    private final Window<String> txIdWindow;

    public AntiDupeWindow() { this(200_000); }

    public AntiDupeWindow(int capacity) {
        this.capacity = Math.max(1000, capacity);
        this.serialWindow = new Window<>(this.capacity);
        this.txIdWindow = new Window<>(Math.max(1000, this.capacity / 2));
    }

    public boolean tryAccept(long serial, String txId) {
        boolean serialOk = serialWindow.addIfAbsent(serial);
        if (!serialOk) {
            rejections++;
            return false;
        }
        boolean txOk = txIdWindow.addIfAbsent(txId);
        if (!txOk) {
            rejections++;
            return false;
        }
        return true;
    }

    public boolean containsSerial(long serial) {
        return serialWindow.contains(serial);
    }

    public long getRejections() { return rejections; }

    public void clear() {
        serialWindow.clear();
        txIdWindow.clear();
    }
}
