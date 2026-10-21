package ru.adblock.lsposed;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.net.UnknownHostException;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

/**
 * Слой 2 для необфусцированного OkHttp: обёртка над пользовательским okhttp3.Dns (DoH и т.п.).
 * Системный DNS OkHttp идёт через InetAddress и покрывается DnsHooks.
 */
final class OkHttpHooks {
    private OkHttpHooks() {
    }

    static void installDns(ClassLoader cl, Rules rules) {
        try {
            Class<?> builder = XposedHelpers.findClassIfExists("okhttp3.OkHttpClient$Builder", cl);
            Class<?> dnsIface = XposedHelpers.findClassIfExists("okhttp3.Dns", cl);
            if (builder == null || dnsIface == null) {
                L.d("okhttp3 не найден — DNS-обёртка не нужна (InetAddress покрывает системный DNS)");
                return;
            }
            XposedBridge.hookAllMethods(builder, "dns", new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    try {
                        if (param.args.length != 1) return;
                        Object dns = param.args[0];
                        if (dns == null || !dnsIface.isInstance(dns)) return;
                        if (Proxy.isProxyClass(dns.getClass())
                                && Proxy.getInvocationHandler(dns) instanceof DnsHandler) return;
                        param.args[0] = Proxy.newProxyInstance(cl, new Class<?>[]{dnsIface},
                                new DnsHandler(dns, rules));
                    } catch (Throwable t) {
                        L.e("ошибка обёртки okhttp3.Dns", t);
                    }
                }
            });
            L.d("okhttp3.Dns обёртка установлена");
        } catch (Throwable t) {
            L.e("не удалось установить хук okhttp3.Dns", t);
        }
    }

    private static final class DnsHandler implements InvocationHandler {
        private final Object delegate;
        private final Rules rules;

        DnsHandler(Object delegate, Rules rules) {
            this.delegate = delegate;
            this.rules = rules;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            if ("lookup".equals(method.getName()) && args != null && args.length == 1
                    && args[0] instanceof String && rules.isDomainBlocked((String) args[0])) {
                Stats.hit("dns", "заблокирован (okhttp3.Dns) " + args[0]);
                throw new UnknownHostException("Blocked by RuAdBlock: " + args[0]);
            }
            try {
                return method.invoke(delegate, args);
            } catch (InvocationTargetException e) {
                throw e.getCause() != null ? e.getCause() : e;
            }
        }
    }
}
