package com.qmurzik.yacandy.hook;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Turns off AppMetrica (Yandex's analytics SDK, which also carries the app's own event tracking)
 * through its public, non-obfuscated API: activation configs get dataSendingEnabled=false and the
 * global switch is forced off. Other trackers are cut at DNS level in {@link NetHooks}.
 * Play statistics ("plays") are a separate music API and stay untouched: they feed the history,
 * recommendations and artist royalties.
 */
final class TelemetryHooks {
    private TelemetryHooks() {
    }

    private static final String[] OFF_FIELDS = {
            "dataSendingEnabled", "locationTracking", "advIdentifiersTracking", "crashReporting",
            "nativeCrashReporting", "anrMonitoring", "revenueAutoTrackingEnabled",
    };

    static void install(CandyModule m, ClassLoader cl) {
        try {
            Class<?> metrica = Class.forName("io.appmetrica.analytics.AppMetrica", false, cl);
            for (Method method : metrica.getDeclaredMethods()) {
                String n = method.getName();
                Class<?>[] p = method.getParameterTypes();
                if ((n.equals("activate") || n.equals("activateReporter")) && p.length == 2) {
                    m.hookSafely(method, chain -> {
                        if (off(m)) patchConfig(chain.getArg(1));
                        Object r = chain.proceed();
                        if (off(m)) disableSending(m, metrica);
                        return r;
                    });
                } else if (n.equals("setDataSendingEnabled") && p.length == 1 && p[0] == boolean.class) {
                    m.hookSafely(method, chain -> off(m) ? chain.proceed(new Object[]{false}) : chain.proceed());
                } else if (n.equals("setLocationTracking") && p.length == 1 && p[0] == boolean.class) {
                    m.hookSafely(method, chain -> off(m) ? chain.proceed(new Object[]{false}) : chain.proceed());
                }
            }
            m.status("Телеметрия", "AppMetrica найдена");
        } catch (Throwable t) {
            m.status("Телеметрия", "AppMetrica не найдена, только DNS");
            m.logE("AppMetrica hooks", t);
        }
        try {
            Class<?> yandex = Class.forName("io.appmetrica.analytics.AppMetricaYandex", false, cl);
            for (Method method : yandex.getDeclaredMethods()) {
                String n = method.getName();
                if (n.equals("activateReporter") && method.getParameterCount() == 2) {
                    m.hookSafely(method, chain -> {
                        if (off(m)) patchConfig(chain.getArg(1));
                        return chain.proceed();
                    });
                } else if (n.equals("activatePulse") && method.getReturnType() == void.class) {
                    // Pulse is performance telemetry only.
                    m.hookSafely(method, chain -> off(m) ? null : chain.proceed());
                }
            }
        } catch (Throwable t) {
            m.logE("AppMetricaYandex hooks", t);
        }
    }

    private static boolean off(CandyModule m) {
        Config c = m.config();
        return c != null && c.telemetryOff;
    }

    /** Config objects are immutable builders' output; their public final fields are rewritten. */
    private static void patchConfig(Object config) {
        if (config == null) return;
        for (Class<?> k = config.getClass(); k != null && k != Object.class; k = k.getSuperclass()) {
            for (String name : OFF_FIELDS) {
                try {
                    Field f = k.getDeclaredField(name);
                    if (f.getType() != Boolean.class) continue;
                    f.setAccessible(true);
                    f.set(config, Boolean.FALSE);
                } catch (NoSuchFieldException ignored) {
                    // not in this class
                } catch (Throwable ignored) {
                    // best effort
                }
            }
        }
    }

    private static void disableSending(CandyModule m, Class<?> metrica) {
        try {
            metrica.getDeclaredMethod("setDataSendingEnabled", boolean.class).invoke(null, false);
        } catch (Throwable t) {
            m.logE("setDataSendingEnabled", t);
        }
    }
}
