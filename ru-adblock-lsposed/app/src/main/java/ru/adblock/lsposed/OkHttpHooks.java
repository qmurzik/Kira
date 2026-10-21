package ru.adblock.lsposed;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

/**
 * OkHttp (если в приложении он не обфусцирован и лежит в пакете okhttp3):
 * - слой 2: обёртка над пользовательским okhttp3.Dns (DoH и т.п.);
 * - слой 3: интерцептор, фильтрующий JSON-ответы.
 */
final class OkHttpHooks {
    /** Не фильтруем ответы, связанные с оплатой, подписками и авторизацией. */
    private static final String[] URL_SKIP = {
            "pay", "bank", "billing", "subscri", "checkout", "order", "cart", "auth", "login",
            "token", "tariff", "plus", "wallet",
    };

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

    static void installJson(ClassLoader cl) {
        try {
            Class<?> builder = XposedHelpers.findClassIfExists("okhttp3.OkHttpClient$Builder", cl);
            Class<?> interceptor = XposedHelpers.findClassIfExists("okhttp3.Interceptor", cl);
            Class<?> responseBody = XposedHelpers.findClassIfExists("okhttp3.ResponseBody", cl);
            Class<?> mediaType = XposedHelpers.findClassIfExists("okhttp3.MediaType", cl);
            if (builder == null || interceptor == null || responseBody == null || mediaType == null) {
                L.d("okhttp3 не найден — JSON-интерцептор не установлен");
                return;
            }
            Method create = findCreate(responseBody, mediaType);
            if (create == null) {
                L.i("okhttp3.ResponseBody.create не найден — JSON-интерцептор не установлен");
                return;
            }
            Object proxy = Proxy.newProxyInstance(cl, new Class<?>[]{interceptor},
                    new JsonInterceptor(create));
            XposedBridge.hookAllMethods(builder, "build", new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    try {
                        Object b = param.thisObject;
                        @SuppressWarnings("unchecked")
                        List<Object> list = (List<Object>) Reflect.call(b, "interceptors");
                        for (Object o : list) {
                            if (Proxy.isProxyClass(o.getClass())
                                    && Proxy.getInvocationHandler(o) instanceof JsonInterceptor) return;
                        }
                        Reflect.call(b, "addInterceptor", proxy);
                    } catch (Throwable t) {
                        L.e("не удалось добавить JSON-интерцептор", t);
                    }
                }
            });
            L.d("okhttp3 JSON-интерцептор установлен");
        } catch (Throwable t) {
            L.e("не удалось установить JSON-интерцептор", t);
        }
    }

    private static Method findCreate(Class<?> responseBody, Class<?> mediaType) {
        try {
            return responseBody.getMethod("create", mediaType, String.class);
        } catch (Throwable ignored) {
        }
        try {
            return responseBody.getMethod("create", String.class, mediaType);
        } catch (Throwable ignored) {
        }
        return null;
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
                L.d("DNS (okhttp) заблокирован: " + args[0]);
                throw new UnknownHostException("Blocked by RuAdBlock: " + args[0]);
            }
            try {
                return method.invoke(delegate, args);
            } catch (InvocationTargetException e) {
                throw e.getCause() != null ? e.getCause() : e;
            }
        }
    }

    private static final class JsonInterceptor implements InvocationHandler {
        private final Method create;

        JsonInterceptor(Method create) {
            this.create = create;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            String name = method.getName();
            if ("equals".equals(name)) return proxy == args[0];
            if ("hashCode".equals(name)) return System.identityHashCode(proxy);
            if ("toString".equals(name)) return "RuAdBlockJsonInterceptor";
            if (!"intercept".equals(name) || args == null || args.length != 1) return null;

            Object chain = args[0];
            Object request = Reflect.call(chain, "request");
            // Исключения сети (IOException) пробрасываются как есть.
            Object response = Reflect.call(chain, "proceed", request);
            try {
                Object filtered = filter(request, response);
                return filtered != null ? filtered : response;
            } catch (Throwable t) {
                L.e("ошибка JSON-интерцептора", t);
                return response;
            }
        }

        /** Возвращает новый Response или null, если ответ трогать не нужно. */
        private Object filter(Object request, Object response) throws Throwable {
            String url = String.valueOf(Reflect.call(request, "url")).toLowerCase(Locale.ROOT);
            // Проверяем только путь (без хоста и query): хосты вроде card.wb.ru — это каталог.
            int scheme = url.indexOf("://");
            int slash = url.indexOf('/', scheme >= 0 ? scheme + 3 : 0);
            int q = url.indexOf('?');
            String path = slash < 0 ? "" : url.substring(slash, q > slash ? q : url.length());
            for (String s : URL_SKIP) {
                if (path.contains(s)) return null;
            }
            Object body = Reflect.call(response, "body");
            if (body == null) return null;
            Object mt = Reflect.call(body, "contentType");
            if (mt == null || !mt.toString().toLowerCase(Locale.ROOT).contains("json")) return null;
            long len = (Long) Reflect.call(body, "contentLength");
            if (len > JsonFilter.MAX_SIZE) return null;

            // peekBody не потребляет исходное тело.
            Object peek = Reflect.call(response, "peekBody", (long) JsonFilter.MAX_SIZE + 1);
            byte[] bytes = (byte[]) Reflect.call(peek, "bytes");
            if (bytes.length > JsonFilter.MAX_SIZE) return null;
            String json = new String(bytes, StandardCharsets.UTF_8);
            String out = JsonFilter.filterString(json);
            if (out == null) return null;

            Object newBody = create.getParameterTypes()[0] == String.class
                    ? create.invoke(null, out, mt)
                    : create.invoke(null, mt, out);
            Object rb = Reflect.call(response, "newBuilder");
            Reflect.call(rb, "removeHeader", "Content-Length");
            Reflect.call(rb, "body", newBody);
            Object newResponse = Reflect.call(rb, "build");
            try {
                Reflect.call(body, "close");
            } catch (Throwable ignored) {
            }
            return newResponse;
        }
    }
}
