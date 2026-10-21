package ru.adblock.lsposed;

import java.net.InetAddress;
import java.net.UnknownHostException;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;

/** Слой 2: для рекламных доменов резолвинг завершается UnknownHostException. */
final class DnsHooks {
    private DnsHooks() {
    }

    static void install(ClassLoader cl, Rules rules) {
        XC_MethodHook hook = new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                try {
                    if (param.args.length == 0 || !(param.args[0] instanceof String)) return;
                    String host = (String) param.args[0];
                    if (rules.isDomainBlocked(host)) {
                        L.d("DNS заблокирован: " + host);
                        param.setThrowable(new UnknownHostException("Blocked by RuAdBlock: " + host));
                    }
                } catch (Throwable t) {
                    L.e("ошибка DNS-хука", t);
                }
            }
        };
        // getAllByNameOnNet / getByNameOnNet — скрытые варианты, используемые android.net.Network.
        for (String m : new String[]{"getAllByName", "getByName", "getAllByNameOnNet", "getByNameOnNet"}) {
            try {
                XposedBridge.hookAllMethods(InetAddress.class, m, hook);
            } catch (Throwable t) {
                L.e("не удалось хукнуть InetAddress." + m, t);
            }
        }
        OkHttpHooks.installDns(cl, rules);
        L.d("DNS-хуки установлены, доменов: " + rules.domains.size()
                + ", ключевых слов: " + rules.domainKeywords.size());
    }
}
