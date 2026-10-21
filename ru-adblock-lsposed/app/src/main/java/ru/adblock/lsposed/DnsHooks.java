package ru.adblock.lsposed;

import android.net.Uri;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import java.io.ByteArrayInputStream;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;

/** Слой 2: для рекламных доменов резолвинг завершается UnknownHostException. */
final class DnsHooks {
    private static final Set<Class<?>> HOOKED_CLIENTS = Collections.synchronizedSet(new HashSet<>());

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
                        Stats.hit("dns", "заблокирован " + host);
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
        installWebView(rules);
        L.i("DNS-слой: доменов " + rules.domains.size() + ", ключевых слов " + rules.domainKeywords.size());
    }

    /**
     * WebView ходит в сеть через Chromium и не использует InetAddress, поэтому рекламные
     * запросы из WebView (креативы SDK, рекламные iframe) режутся через shouldInterceptRequest.
     */
    private static void installWebView(Rules rules) {
        XC_MethodHook intercept = new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                try {
                    Object a = param.args.length > 1 ? param.args[1] : null;
                    Uri uri = a instanceof WebResourceRequest ? ((WebResourceRequest) a).getUrl()
                            : a instanceof String ? Uri.parse((String) a) : null;
                    if (uri == null || !rules.isDomainBlocked(uri.getHost())) return;
                    Stats.hit("webview", "заблокирован " + uri.getHost());
                    param.setResult(new WebResourceResponse("text/plain", "utf-8", 403, "Blocked",
                            Collections.emptyMap(), new ByteArrayInputStream(new byte[0])));
                } catch (Throwable t) {
                    L.e("ошибка WebView-хука", t);
                }
            }
        };
        try {
            XposedBridge.hookAllMethods(WebViewClient.class, "shouldInterceptRequest", intercept);
            // Подклассы приложения переопределяют метод без вызова super — хукаем и их.
            XposedBridge.hookAllMethods(WebView.class, "setWebViewClient", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    try {
                        Object client = param.args[0];
                        if (client == null) return;
                        for (Class<?> k = client.getClass(); k != null && k != WebViewClient.class;
                             k = k.getSuperclass()) {
                            if (!HOOKED_CLIENTS.add(k)) break;
                            for (Method m : k.getDeclaredMethods()) {
                                if (m.getName().equals("shouldInterceptRequest")
                                        && !Modifier.isAbstract(m.getModifiers())) {
                                    XposedBridge.hookMethod(m, intercept);
                                }
                            }
                        }
                    } catch (Throwable t) {
                        L.e("ошибка хука WebViewClient", t);
                    }
                }
            });
        } catch (Throwable t) {
            L.e("не удалось установить WebView-хуки", t);
        }
    }
}
