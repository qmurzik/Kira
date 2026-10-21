package ru.adblock.lsposed;

/** Ключи настроек, общие для Activity настроек и хуков. */
public final class Prefs {
    public static final String FILE = "settings";

    public static final String LAYER_SDK = "layer_sdk";
    public static final String LAYER_DNS = "layer_dns";
    public static final String LAYER_JSON = "layer_json";
    public static final String LAYER_UI = "layer_ui";
    public static final String DEBUG_LOG = "debug_log";
    /** Дополнительные домены, по одному на строку (тот же формат, что и res/raw/ad_domains.txt). */
    public static final String EXTRA_DOMAINS = "extra_domains";
    /** Дополнительные JSON-ключи, по одному на строку (формат res/raw/json_ad_keys.txt). */
    public static final String EXTRA_JSON_KEYS = "extra_json_keys";

    private Prefs() {
    }
}
