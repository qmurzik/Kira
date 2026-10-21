package ru.adblock.lsposed;

import de.robv.android.xposed.XSharedPreferences;

/** Настройки модуля, читаемые в процессе целевого приложения через XSharedPreferences. */
final class Config {
    boolean sdk = true;
    boolean dns = true;
    boolean json = true;
    boolean ui = true;
    boolean debug = false;
    String extraDomains = "";
    String extraJsonKeys = "";

    static Config load() {
        Config c = new Config();
        try {
            XSharedPreferences p = new XSharedPreferences(BuildConfig.APPLICATION_ID, Prefs.FILE);
            p.reload();
            if (!p.getFile().canRead()) {
                L.i("настройки недоступны для чтения, используются значения по умолчанию");
                return c;
            }
            c.sdk = p.getBoolean(Prefs.LAYER_SDK, true);
            c.dns = p.getBoolean(Prefs.LAYER_DNS, true);
            c.json = p.getBoolean(Prefs.LAYER_JSON, true);
            c.ui = p.getBoolean(Prefs.LAYER_UI, true);
            c.debug = p.getBoolean(Prefs.DEBUG_LOG, false);
            c.extraDomains = p.getString(Prefs.EXTRA_DOMAINS, "");
            c.extraJsonKeys = p.getString(Prefs.EXTRA_JSON_KEYS, "");
        } catch (Throwable t) {
            L.e("не удалось прочитать настройки", t);
        }
        return c;
    }
}
