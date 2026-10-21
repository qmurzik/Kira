package com.qmurzik.yacandy.hook;

import com.qmurzik.yacandy.Features;

import java.io.IOException;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Locale;

/**
 * Network-level switches that don't depend on obfuscated code:
 * <ul>
 * <li>Retrofit creates every API as {@link Proxy#newProxyInstance}; for blocked interfaces (kept
 * names like PlayerInformersApi) each returned {@code retrofit2.Call} is replaced by one that fails
 * with an IOException. The app treats it as a failed load and simply shows nothing there.</li>
 * <li>Third-party tracker hosts fail DNS resolution.</li>
 * </ul>
 */
final class NetHooks {
    private NetHooks() {
    }

    private static final String RETROFIT_CALL = "retrofit2.Call";

    static void installApiBlocker(CandyModule m) {
        try {
            Method npi = Proxy.class.getDeclaredMethod("newProxyInstance",
                    ClassLoader.class, Class[].class, InvocationHandler.class);
            m.hookSafely(npi, chain -> {
                Config c = m.config();
                Class<?>[] ifaces = (Class<?>[]) chain.getArg(1);
                InvocationHandler h = (InvocationHandler) chain.getArg(2);
                if (c == null || ifaces == null || ifaces.length != 1 || h == null
                        || h instanceof ApiGate || h instanceof DeadCall) {
                    return chain.proceed();
                }
                String name = ifaces[0].getName();
                if (!name.endsWith("Api") || !isKnown(name)) return chain.proceed();
                return chain.proceed(new Object[]{chain.getArg(0), ifaces, new ApiGate(m, name, h)});
            });
        } catch (Throwable t) {
            m.logE("api blocker", t);
        }
    }

    /** Only interfaces the module knows about are wrapped; everything else is untouched. */
    private static boolean isKnown(String name) {
        for (String[] group : new String[][]{Features.API_POPUPS, Features.API_SPLASH,
                Features.API_SPECIAL_HEADER, Features.API_INFORMERS, Features.API_PB_SIMILAR,
                Features.API_PB_ARTISTS, Features.API_PB_CONCERTS, Features.API_PB_NONMUSIC,
                Features.API_WAVE_WORDS}) {
            for (String s : group) if (s.equals(name)) return true;
        }
        return false;
    }

    /** Wraps a Retrofit service; the blocked state is checked per call so toggles apply live. */
    private static final class ApiGate implements InvocationHandler {
        private final CandyModule m;
        private final String api;
        private final InvocationHandler real;

        ApiGate(CandyModule m, String api, InvocationHandler real) {
            this.m = m;
            this.api = api;
            this.real = real;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            Object result;
            try {
                result = real.invoke(proxy, method, args);
            } catch (InvocationTargetException e) {
                throw e.getCause() != null ? e.getCause() : e;
            }
            Config c = m.config();
            if (result == null || c == null || !c.blockedApis.contains(api)) return result;
            Class<?> callIface = callInterface(method.getReturnType());
            if (callIface == null) return result;
            return Proxy.newProxyInstance(callIface.getClassLoader(), new Class<?>[]{callIface},
                    new DeadCall(callIface, result));
        }
    }

    private static Class<?> callInterface(Class<?> type) {
        return type != null && type.isInterface() && RETROFIT_CALL.equals(type.getName()) ? type : null;
    }

    /**
     * A retrofit2.Call that never hits the network. Method names are obfuscated, so they are told
     * apart by shape: enqueue takes a single callback, execute returns a Response.
     */
    private static final class DeadCall implements InvocationHandler {
        private final Class<?> callIface;
        private final Object original;
        private volatile boolean canceled;

        DeadCall(Class<?> callIface, Object original) {
            this.callIface = callIface;
            this.original = original;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            String name = method.getName();
            Class<?>[] p = method.getParameterTypes();
            Class<?> ret = method.getReturnType();
            if (method.getDeclaringClass() == Object.class) {
                switch (name) {
                    case "equals": return proxy == args[0];
                    case "hashCode": return System.identityHashCode(proxy);
                    default: return "YaCandy blocked call";
                }
            }
            if (p.length == 1 && ret == void.class && p[0].isInterface()) {
                failCallback(proxy, args[0]);
                return null;
            }
            if (name.equals("clone") || ret == callIface) {
                return Proxy.newProxyInstance(callIface.getClassLoader(), new Class<?>[]{callIface},
                        new DeadCall(callIface, original));
            }
            if (name.equals("cancel")) {
                canceled = true;
                return null;
            }
            if (p.length == 0 && "retrofit2.Response".equals(ret.getName())) {
                throw blocked();
            }
            if (ret == boolean.class) return canceled;
            // request(), timeout(): harmless, ask the real call.
            try {
                return method.invoke(original, args);
            } catch (InvocationTargetException e) {
                throw e.getCause() != null ? e.getCause() : e;
            }
        }

        /** Calls the callback method that takes (Call, Throwable), i.e. onFailure. */
        private void failCallback(Object call, Object callback) throws Throwable {
            if (callback == null) return;
            for (Method cb : callback.getClass().getMethods()) {
                Class<?>[] p = cb.getParameterTypes();
                if (p.length == 2 && p[0].isAssignableFrom(callIface) && p[1] == Throwable.class) {
                    cb.setAccessible(true);
                    try {
                        cb.invoke(callback, call, blocked());
                    } catch (InvocationTargetException e) {
                        throw e.getCause() != null ? e.getCause() : e;
                    }
                    return;
                }
            }
        }
    }

    private static IOException blocked() {
        return new IOException("Blocked by YaCandy");
    }

    static void installTrackerBlocker(CandyModule m) {
        try {
            m.hookPassthrough(InetAddress.class.getDeclaredMethod("getAllByName", String.class), chain -> {
                checkHost(m, (String) chain.getArg(0));
                return chain.proceed();
            });
            m.hookPassthrough(InetAddress.class.getDeclaredMethod("getByName", String.class), chain -> {
                checkHost(m, (String) chain.getArg(0));
                return chain.proceed();
            });
        } catch (Throwable t) {
            m.logE("tracker blocker", t);
        }
    }

    private static void checkHost(CandyModule m, String host) throws UnknownHostException {
        Config c = m.config();
        if (c == null || !c.telemetryOff || host == null) return;
        String h = host.toLowerCase(Locale.ROOT);
        for (String t : Features.TRACKER_HOSTS) {
            if (h.contains(t)) throw new UnknownHostException("Blocked by YaCandy: " + host);
        }
    }
}
