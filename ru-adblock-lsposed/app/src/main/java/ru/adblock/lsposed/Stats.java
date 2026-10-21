package ru.adblock.lsposed;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Счётчики срабатываний. Первые несколько событий каждого вида пишутся в лог всегда,
 * чтобы по журналу LSPosed было видно, что слой работает; остальные — только в подробном режиме.
 */
final class Stats {
    private static final int ALWAYS_LOG = 3;
    private static final ConcurrentHashMap<String, AtomicInteger> COUNTS = new ConcurrentHashMap<>();

    private Stats() {
    }

    static void hit(String kind, String msg) {
        AtomicInteger c = COUNTS.get(kind);
        if (c == null) {
            COUNTS.putIfAbsent(kind, new AtomicInteger());
            c = COUNTS.get(kind);
        }
        int n = c.incrementAndGet();
        if (n <= ALWAYS_LOG || L.debug) {
            L.i("[" + kind + " #" + n + "] " + msg);
        }
    }
}
