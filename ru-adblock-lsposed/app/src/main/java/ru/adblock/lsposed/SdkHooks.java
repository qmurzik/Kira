package ru.adblock.lsposed;

import android.os.Handler;
import android.os.Looper;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XC_MethodReplacement;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

/**
 * Слой 1: рекламные SDK. Публичный API SDK не обфусцируется (иначе им нельзя было бы
 * пользоваться), поэтому хуки ставятся по стабильным именам классов/методов.
 * <p>
 * Методы загрузки рекламы заменяются: реальная загрузка не выполняется, а сохранённому
 * слушателю приложения штатно отправляется «нет рекламы» (no fill). Слушатель запоминается
 * при вызовах set*Listener / set*Callback либо берётся из аргументов load-метода.
 */
final class SdkHooks {
    private static final String LISTENERS = "ruadblock.listeners";
    private static final String MESSAGE = "No fill";
    /** Задержка уведомления об ошибке — защита от бесконечного цикла мгновенных повторов. */
    private static final long FAIL_DELAY_MS = 500;

    private static final Set<Method> HOOKED = Collections.synchronizedSet(new HashSet<>());
    private static Handler main;

    private SdkHooks() {
    }

    private static final class Spec {
        final String sdk;
        final String prefix;
        final Set<String> loadMethods;
        final List<String> failMethods;
        final int noFillCode;

        Spec(String sdk, String prefix, String[] load, String[] fail, int noFillCode) {
            this.sdk = sdk;
            this.prefix = prefix;
            this.loadMethods = new HashSet<>(Arrays.asList(load));
            this.failMethods = Arrays.asList(fail);
            this.noFillCode = noFillCode;
        }
    }

    static void install(ClassLoader cl) {
        main = new Handler(Looper.getMainLooper());

        // --- Yandex Mobile Ads (com.yandex.mobile.ads), AdRequestError.Code.NO_FILL = 4 ---
        Spec yandex = new Spec("Yandex Mobile Ads", "com.yandex.mobile.ads.",
                new String[]{"loadAd", "loadAds", "loadPromoAd", "loadSlider", "loadInstreamAd"},
                new String[]{"onAdFailedToLoad", "onAdsFailedToLoad", "onSliderAdFailedToLoad",
                        "onInstreamAdFailedToLoad"},
                yandexNoFill(cl));
        for (String c : new String[]{
                "com.yandex.mobile.ads.banner.BannerAdView",
                "com.yandex.mobile.ads.interstitial.InterstitialAdLoader",
                "com.yandex.mobile.ads.rewarded.RewardedAdLoader",
                "com.yandex.mobile.ads.appopenad.AppOpenAdLoader",
                "com.yandex.mobile.ads.nativeads.NativeAdLoader",
                "com.yandex.mobile.ads.nativeads.NativeBulkAdLoader",
                "com.yandex.mobile.ads.nativeads.SliderAdLoader",
                "com.yandex.mobile.ads.instream.InstreamAdLoader",
                "com.yandex.mobile.ads.feed.FeedAdLoader",
                // Старые версии SDK (до 6.x)
                "com.yandex.mobile.ads.interstitial.InterstitialAd",
                "com.yandex.mobile.ads.rewarded.RewardedAd",
        }) {
            hookClass(cl, c, yandex);
        }

        // --- myTarget / VK Реклама (com.my.target) ---
        Spec mytarget = new Spec("myTarget", "com.my.target.",
                new String[]{"load", "loadFromBid"},
                new String[]{"onNoAd", "onLoadFailed"},
                0);
        for (String c : new String[]{
                "com.my.target.ads.MyTargetView",
                "com.my.target.ads.InterstitialAd",
                "com.my.target.ads.RewardedAd",
                "com.my.target.nativeads.NativeAd",
                "com.my.target.nativeads.NativeBannerAd",
                "com.my.target.nativeads.NativeAppwallAd",
                "com.my.target.nativeads.NativeAdLoader",
                "com.my.target.nativeads.NativeBannerAdLoader",
                "com.my.target.instreamads.InstreamAd",
                "com.my.target.instreamads.InstreamAudioAd",
        }) {
            hookClass(cl, c, mytarget);
        }

        // --- Google Mobile Ads, AdRequest.ERROR_CODE_NO_FILL = 3 ---
        Spec google = new Spec("Google Ads", "com.google.android.gms.ads.",
                new String[]{"loadAd", "loadAds", "load"},
                new String[]{"onAdFailedToLoad"},
                3);
        for (String c : new String[]{
                "com.google.android.gms.ads.BaseAdView",
                "com.google.android.gms.ads.AdView",
                "com.google.android.gms.ads.admanager.AdManagerAdView",
                "com.google.android.gms.ads.AdLoader",
                "com.google.android.gms.ads.interstitial.InterstitialAd",
                "com.google.android.gms.ads.rewarded.RewardedAd",
                "com.google.android.gms.ads.rewardedinterstitial.RewardedInterstitialAd",
                "com.google.android.gms.ads.appopen.AppOpenAd",
                "com.google.android.gms.ads.admanager.AdManagerInterstitialAd",
        }) {
            hookClass(cl, c, google);
        }
        captureGoogleAdLoaderListener(cl);
    }

    private static int yandexNoFill(ClassLoader cl) {
        try {
            Class<?> code = XposedHelpers.findClassIfExists("com.yandex.mobile.ads.common.AdRequestError$Code", cl);
            if (code != null) return code.getField("NO_FILL").getInt(null);
        } catch (Throwable ignored) {
        }
        return 4;
    }

    private static void hookClass(ClassLoader cl, String className, Spec spec) {
        Class<?> c;
        try {
            c = XposedHelpers.findClassIfExists(className, cl);
        } catch (Throwable t) {
            c = null;
        }
        if (c == null) return;
        int loads = 0;
        // Методы могут быть объявлены в базовых классах SDK — идём вверх по иерархии,
        // пока остаёмся в пакете SDK.
        for (Class<?> k = c; k != null && k.getName().startsWith(spec.prefix); k = k.getSuperclass()) {
            for (Method m : k.getDeclaredMethods()) {
                if (Modifier.isAbstract(m.getModifiers()) || !HOOKED.add(m)) continue;
                String n = m.getName();
                try {
                    if (spec.loadMethods.contains(n)) {
                        if (m.getReturnType() != void.class) {
                            HOOKED.remove(m);
                            continue;
                        }
                        XposedBridge.hookMethod(m, loadReplacement(spec));
                        loads++;
                    } else if (n.startsWith("set") && m.getParameterTypes().length == 1
                            && !Modifier.isStatic(m.getModifiers())
                            && (n.endsWith("Listener") || n.endsWith("Callback"))) {
                        XposedBridge.hookMethod(m, LISTENER_CAPTURE);
                    } else {
                        HOOKED.remove(m);
                    }
                } catch (Throwable t) {
                    L.e("не удалось хукнуть " + k.getName() + "." + n, t);
                }
            }
        }
        if (loads > 0) L.d(spec.sdk + ": " + className + " — хуков загрузки: " + loads);
    }

    private static final XC_MethodHook LISTENER_CAPTURE = new XC_MethodHook() {
        @Override
        protected void afterHookedMethod(MethodHookParam param) {
            try {
                remember(param.thisObject, param.args[0]);
            } catch (Throwable t) {
                L.e("ошибка сохранения слушателя рекламы", t);
            }
        }
    };

    @SuppressWarnings("unchecked")
    private static void remember(Object owner, Object listener) {
        if (owner == null || listener == null) return;
        synchronized (owner) {
            List<Object> list = (List<Object>) XposedHelpers.getAdditionalInstanceField(owner, LISTENERS);
            if (list == null) {
                list = new ArrayList<>();
                XposedHelpers.setAdditionalInstanceField(owner, LISTENERS, list);
            }
            if (!list.contains(listener)) list.add(0, listener);
        }
    }

    /** Google AdLoader получает слушатель через AdLoader.Builder.withAdListener(). */
    private static void captureGoogleAdLoaderListener(ClassLoader cl) {
        try {
            Class<?> b = XposedHelpers.findClassIfExists("com.google.android.gms.ads.AdLoader$Builder", cl);
            if (b == null) return;
            XposedBridge.hookAllMethods(b, "withAdListener", LISTENER_CAPTURE);
            XposedBridge.hookAllMethods(b, "build", new XC_MethodHook() {
                @Override
                @SuppressWarnings("unchecked")
                protected void afterHookedMethod(MethodHookParam param) {
                    try {
                        List<Object> list = (List<Object>) XposedHelpers
                                .getAdditionalInstanceField(param.thisObject, LISTENERS);
                        if (list != null && param.getResult() != null) {
                            for (Object l : list) remember(param.getResult(), l);
                        }
                    } catch (Throwable t) {
                        L.e("ошибка AdLoader.Builder.build", t);
                    }
                }
            });
        } catch (Throwable t) {
            L.e("не удалось хукнуть AdLoader.Builder", t);
        }
    }

    private static XC_MethodReplacement loadReplacement(Spec spec) {
        return new XC_MethodReplacement() {
            @Override
            @SuppressWarnings("unchecked")
            protected Object replaceHookedMethod(MethodHookParam param) {
                try {
                    List<Object> candidates = new ArrayList<>();
                    if (param.thisObject != null) {
                        List<Object> saved = (List<Object>) XposedHelpers
                                .getAdditionalInstanceField(param.thisObject, LISTENERS);
                        if (saved != null) {
                            synchronized (param.thisObject) {
                                candidates.addAll(saved);
                            }
                        }
                    }
                    if (param.args != null) {
                        // Колбэк обычно последний аргумент (Google: load(ctx, id, request, callback)).
                        for (int i = param.args.length - 1; i >= 0; i--) {
                            if (param.args[i] != null) candidates.add(param.args[i]);
                        }
                    }
                    L.d(spec.sdk + ": заблокирована загрузка " + param.method.getDeclaringClass().getName()
                            + "." + param.method.getName());
                    dispatchNoFill(candidates, spec, param.thisObject);
                } catch (Throwable t) {
                    L.e(spec.sdk + ": ошибка блокировки загрузки", t);
                }
                return null;
            }
        };
    }

    private static void dispatchNoFill(List<Object> candidates, Spec spec, Object ad) {
        for (Object listener : candidates) {
            Method m = findFailMethod(listener.getClass(), spec);
            if (m == null) continue;
            Object[] args = buildArgs(m.getParameterTypes(), spec, ad, listener.getClass().getClassLoader());
            if (args == null) continue;
            main.postDelayed(() -> {
                try {
                    m.setAccessible(true);
                    m.invoke(listener, args);
                } catch (Throwable t) {
                    L.e(spec.sdk + ": ошибка вызова " + m.getName(), t);
                }
            }, FAIL_DELAY_MS);
            return;
        }
        L.d(spec.sdk + ": слушатель не найден, загрузка просто отменена");
    }

    private static Method findFailMethod(Class<?> c, Spec spec) {
        Method best = null;
        for (String name : spec.failMethods) {
            for (Method m : c.getMethods()) {
                if (!m.getName().equals(name) || Modifier.isStatic(m.getModifiers())) continue;
                if (best == null || score(m) > score(best)) best = m;
            }
            if (best != null) return best;
        }
        return null;
    }

    /** Предпочитаем современные сигнатуры с объектом ошибки, а не устаревшие (int). */
    private static int score(Method m) {
        int s = 0;
        for (Class<?> p : m.getParameterTypes()) {
            if (!p.isPrimitive() && p != String.class) s += 2;
            else s += 1;
        }
        return s;
    }

    private static Object[] buildArgs(Class<?>[] types, Spec spec, Object ad, ClassLoader cl) {
        Object[] out = new Object[types.length];
        for (int i = 0; i < types.length; i++) {
            out[i] = buildValue(types[i], spec, ad, cl, 0);
        }
        return out;
    }

    private static Object buildValue(Class<?> t, Spec spec, Object ad, ClassLoader cl, int depth) {
        if (t == int.class || t == Integer.class) return spec.noFillCode;
        if (t == long.class || t == Long.class) return (long) spec.noFillCode;
        if (t == boolean.class || t == Boolean.class) return false;
        if (t == String.class || t == CharSequence.class) return MESSAGE;
        if (t.isPrimitive()) return defaultPrimitive(t);
        if (ad != null && t.isInstance(ad)) return ad;
        if (depth > 1) return null;
        if (t.isInterface()) return errorProxy(t, spec, cl);
        return construct(t, spec, cl, depth);
    }

    private static Object defaultPrimitive(Class<?> t) {
        if (t == float.class) return 0f;
        if (t == double.class) return 0d;
        if (t == short.class) return (short) 0;
        if (t == byte.class) return (byte) 0;
        if (t == char.class) return (char) 0;
        return 0;
    }

    /** Ошибка-интерфейс (например, myTarget IAdLoadingError): getCode() / getMessage(). */
    private static Object errorProxy(Class<?> iface, Spec spec, ClassLoader cl) {
        try {
            ClassLoader loader = iface.getClassLoader() != null ? iface.getClassLoader() : cl;
            return Proxy.newProxyInstance(loader, new Class<?>[]{iface}, (proxy, method, args) -> {
                String n = method.getName();
                Class<?> r = method.getReturnType();
                if ("toString".equals(n)) return "RuAdBlock: " + MESSAGE;
                if ("hashCode".equals(n)) return System.identityHashCode(proxy);
                if ("equals".equals(n)) return proxy == args[0];
                if (r == int.class || r == Integer.class) return spec.noFillCode;
                if (r == String.class || r == CharSequence.class) return MESSAGE;
                if (r == boolean.class) return false;
                if (r.isPrimitive()) return r == void.class ? null : defaultPrimitive(r);
                return null;
            });
        } catch (Throwable t) {
            return null;
        }
    }

    /** Ошибка-класс (Yandex AdRequestError(int, String), Google LoadAdError(int, String, String, ...)). */
    private static Object construct(Class<?> t, Spec spec, ClassLoader cl, int depth) {
        if (Modifier.isAbstract(t.getModifiers())) return null;
        Constructor<?>[] ctors = t.getConstructors();
        Arrays.sort(ctors, (a, b) -> a.getParameterTypes().length - b.getParameterTypes().length);
        for (Constructor<?> c : ctors) {
            Class<?>[] p = c.getParameterTypes();
            boolean hasCode = false;
            for (Class<?> x : p) if (x == int.class) hasCode = true;
            if (!hasCode) continue;
            try {
                Object[] a = new Object[p.length];
                for (int i = 0; i < p.length; i++) {
                    a[i] = p[i].isPrimitive() || p[i] == String.class
                            ? buildValue(p[i], spec, null, cl, depth + 1)
                            : null;
                }
                return c.newInstance(a);
            } catch (Throwable ignored) {
            }
        }
        return null;
    }
}
