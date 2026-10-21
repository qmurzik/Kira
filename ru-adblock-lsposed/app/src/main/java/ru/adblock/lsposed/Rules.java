package ru.adblock.lsposed;

import android.content.res.XModuleResources;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Списки доменов и JSON-ключей. Основной источник — res/raw/ad_domains.txt и
 * res/raw/json_ad_keys.txt модуля, плюс дополнения из настроек.
 */
final class Rules {
    // --- домены ---
    final Set<String> domains = new HashSet<>();
    final List<String> domainKeywords = new ArrayList<>();

    // --- JSON ---
    /** Маркеры (lowercase): ключ присутствует и не пуст. */
    final Set<String> jsonMarkers = new HashSet<>();
    /** Флаги (lowercase): значение == true. */
    final Set<String> jsonFlags = new HashSet<>();
    /** Защитные ключи (lowercase). */
    final Set<String> jsonKeep = new HashSet<>();
    /** Варианты написания ключей с закрывающей кавычкой — для дешёвой предпроверки через indexOf. */
    String[] jsonNeedles = new String[0];

    private static final String[] FALLBACK_DOMAINS = {
            "an.yandex.ru", "yabs.yandex.ru", "adfox.ru", "mobile.yandexadexchange.net",
            "yandexadexchange.net", "ad.mail.ru", "target.my.com", "~mytarget",
            "doubleclick.net", "googlesyndication.com", "googleadservices.com",
    };

    private static final String[] FALLBACK_JSON = {
            "marker:erid", "flag:isAd", "flag:isAdvert", "flag:advert", "flag:promoted",
            "flag:sponsored", "flag:adMarker", "flag:isPromo",
            "keep:subscription", "keep:tariff", "keep:paymentMethod", "keep:purchaseToken",
    };

    static Rules load(String modulePath, Config cfg) {
        List<String> domainLines = null;
        List<String> jsonLines = null;
        if (modulePath != null) {
            try {
                XModuleResources res = XModuleResources.createInstance(modulePath, null);
                domainLines = readLines(res.openRawResource(R.raw.ad_domains));
                jsonLines = readLines(res.openRawResource(R.raw.json_ad_keys));
            } catch (Throwable t) {
                L.e("не удалось прочитать ресурсы модуля, используется встроенный список", t);
            }
        }
        if (domainLines == null) domainLines = java.util.Arrays.asList(FALLBACK_DOMAINS);
        if (jsonLines == null) jsonLines = java.util.Arrays.asList(FALLBACK_JSON);
        return fromLines(domainLines, jsonLines, cfg);
    }

    static Rules fromLines(List<String> domainLines, List<String> jsonLines, Config cfg) {
        Rules r = new Rules();
        for (String l : domainLines) r.addDomain(l);
        for (String l : splitLines(cfg.extraDomains)) r.addDomain(l);
        for (String l : jsonLines) r.addJsonKey(l);
        for (String l : splitLines(cfg.extraJsonKeys)) r.addJsonKey(l);
        r.buildNeedles();
        return r;
    }

    private void addDomain(String line) {
        String s = clean(line);
        if (s == null) return;
        s = s.toLowerCase(Locale.ROOT);
        if (s.startsWith("~")) {
            if (s.length() > 1) domainKeywords.add(s.substring(1));
        } else {
            if (s.startsWith("*.")) s = s.substring(2);
            if (s.endsWith(".")) s = s.substring(0, s.length() - 1);
            if (!s.isEmpty()) domains.add(s);
        }
    }

    /** Ключи в исходном написании из файла — для построения вариантов предпроверки. */
    private final Set<String> rawDetectKeys = new HashSet<>();

    private void addJsonKey(String line) {
        String s = clean(line);
        if (s == null) return;
        int colon = s.indexOf(':');
        String kind = colon > 0 ? s.substring(0, colon).trim().toLowerCase(Locale.ROOT) : "flag";
        String raw = (colon > 0 ? s.substring(colon + 1) : s).trim();
        String key = raw.toLowerCase(Locale.ROOT);
        if (key.isEmpty()) return;
        switch (kind) {
            case "marker":
                jsonMarkers.add(key);
                rawDetectKeys.add(raw);
                break;
            case "keep":
                jsonKeep.add(key);
                break;
            default:
                jsonFlags.add(key);
                rawDetectKeys.add(raw);
                break;
        }
    }

    private void buildNeedles() {
        // Предпроверка ищет `key"` (конец ключа и закрывающую кавычку) в нескольких
        // вариантах регистра. Это также ловит составные маркеры: advertErid, ad_erid.
        Set<String> needles = new HashSet<>();
        for (String k : rawDetectKeys) {
            String lower = k.toLowerCase(Locale.ROOT);
            needles.add(k + "\"");
            needles.add(lower + "\"");
            needles.add(Character.toUpperCase(k.charAt(0)) + k.substring(1) + "\"");
            needles.add(Character.toUpperCase(lower.charAt(0)) + lower.substring(1) + "\"");
            needles.add(k.toUpperCase(Locale.ROOT) + "\"");
        }
        jsonNeedles = needles.toArray(new String[0]);
    }

    boolean isDomainBlocked(String host) {
        if (host == null || host.isEmpty()) return false;
        String h = host.toLowerCase(Locale.ROOT);
        if (h.endsWith(".")) h = h.substring(0, h.length() - 1);
        // IP-адреса не блокируем.
        char c0 = h.charAt(0);
        if ((c0 >= '0' && c0 <= '9' && h.indexOf(':') < 0 && isIpv4(h)) || h.indexOf(':') >= 0) {
            return false;
        }
        String cur = h;
        while (true) {
            if (domains.contains(cur)) return true;
            int dot = cur.indexOf('.');
            if (dot < 0) break;
            cur = cur.substring(dot + 1);
        }
        for (String k : domainKeywords) {
            if (h.contains(k)) return true;
        }
        return false;
    }

    private static boolean isIpv4(String h) {
        for (int i = 0; i < h.length(); i++) {
            char c = h.charAt(i);
            if (c != '.' && (c < '0' || c > '9')) return false;
        }
        return true;
    }

    private static String clean(String line) {
        if (line == null) return null;
        int hash = line.indexOf('#');
        if (hash >= 0) line = line.substring(0, hash);
        line = line.trim();
        return line.isEmpty() ? null : line;
    }

    private static List<String> splitLines(String s) {
        List<String> out = new ArrayList<>();
        if (s == null) return out;
        for (String l : s.split("[\\r\\n,;]+")) out.add(l);
        return out;
    }

    private static List<String> readLines(InputStream in) throws Exception {
        List<String> out = new ArrayList<>();
        try (BufferedReader br = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String l;
            while ((l = br.readLine()) != null) out.add(l);
        }
        return out;
    }
}
