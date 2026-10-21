package ru.adblock.lsposed;

import java.util.Locale;

/**
 * Процессы банков и платёжных сервисов не хукаются вообще (Ozon Банк, WB Банк, Яндекс Пэй,
 * Яндекс Банк и т.п.), даже если пользователь добавил их в scope.
 */
final class Exclusions {
    /** Если сегмент имени пакета/процесса содержит одно из этих слов — процесс пропускается. */
    private static final String[] SEGMENT_CONTAINS = {
            "bank", "fintech", "finance", "wallet", "payment", "billing", "checkout", "acquiring",
    };

    /** Точные сегменты имени. */
    private static final String[] SEGMENT_EQUALS = {
            "sbp", "nspk", "mir", "card", "cards",
    };

    private Exclusions() {
    }

    static boolean isExcluded(String packageName, String processName) {
        return matches(packageName) || matches(processName);
    }

    private static boolean matches(String name) {
        if (name == null || name.isEmpty()) return false;
        String[] segments = name.toLowerCase(Locale.ROOT).split("[.:_\\-]");
        for (String s : segments) {
            if (s.isEmpty()) continue;
            // pay, payments, paysdk, yandexpay, ozonpay, sberpay, wbpay, mirpay...
            if (s.startsWith("pay") || s.endsWith("pay")) return true;
            for (String c : SEGMENT_CONTAINS) {
                if (s.contains(c)) return true;
            }
            for (String e : SEGMENT_EQUALS) {
                if (s.equals(e)) return true;
            }
        }
        return false;
    }
}
