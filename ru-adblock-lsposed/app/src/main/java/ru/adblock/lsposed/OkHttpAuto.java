package ru.adblock.lsposed;

import java.io.Closeable;
import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.InetAddress;
import java.net.Socket;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.WritableByteChannel;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

/**
 * Слой 3 для OkHttp, устойчивый к обфускации R8.
 * <p>
 * В релизных сборках крупных приложений okhttp3/okio переименованы, поэтому классы
 * ищутся по структуре, а не по именам:
 * <ul>
 * <li>RealInterceptorChain.proceed() — метод, который многократно повторяется в стеке
 * вызовов во время DNS-запроса/подключения сокета (по одному кадру на интерцептор);</li>
 * <li>Response — тип результата proceed(), у которого есть поле абстрактного Closeable-класса
 * с тремя абстрактными методами без аргументов: long (contentLength), класс (contentType)
 * и интерфейс (source → BufferedSource);</li>
 * <li>okio.Buffer — реализация BufferedSource, которая также WritableByteChannel.</li>
 * </ul>
 * Ответ фильтруется после самого внешнего proceed(), то есть после всех интерцепторов
 * приложения. Тело читается через peek() (не потребляется), и только если что-то удалено,
 * источник тела подменяется буфером с новым JSON.
 */
final class OkHttpAuto {
    /** Не фильтруем ответы, связанные с оплатой, подписками и авторизацией. */
    private static final String[] URL_SKIP = {
            "pay", "bank", "billing", "subscri", "checkout", "order", "cart", "auth", "login",
            "token", "tariff", "plus", "wallet",
    };
    private static final int MAX_PROBES = 300;

    private static volatile boolean installed;
    private static volatile int probes;
    private static final ThreadLocal<int[]> DEPTH = new ThreadLocal<int[]>() {
        @Override
        protected int[] initialValue() {
            return new int[1];
        }
    };
    private static final Map<Class<?>, Object> SHAPES = new ConcurrentHashMap<>();
    private static final Object NONE = new Object();
    private static ClassLoader appLoader;

    private OkHttpAuto() {
    }

    static void install(ClassLoader cl) {
        appLoader = cl;
        // Необфусцированный OkHttp — сразу.
        Class<?> named = XposedHelpers.findClassIfExists("okhttp3.internal.http.RealInterceptorChain", cl);
        if (named != null) {
            for (Method m : named.getDeclaredMethods()) {
                if (m.getName().equals("proceed") && isChainMethod(m)) {
                    hookChain(m, "okhttp3");
                    break;
                }
            }
        }
        if (installed) return;

        // Обфусцированный — ищем по стеку при первом сетевом подключении.
        XC_MethodHook probe = new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                if (installed || probes > MAX_PROBES) return;
                probes++;
                try {
                    detect(new Throwable().getStackTrace());
                } catch (Throwable t) {
                    L.e("ошибка поиска OkHttp", t);
                }
            }
        };
        try {
            XposedBridge.hookAllMethods(InetAddress.class, "getAllByName", probe);
            XposedHelpers.findAndHookMethod(Socket.class, "connect", SocketAddress.class, int.class, probe);
        } catch (Throwable t) {
            L.e("не удалось установить поиск OkHttp", t);
        }
    }

    static boolean isInstalled() {
        return installed;
    }

    private static void detect(StackTraceElement[] st) {
        Method m = findChainMethod(st, appLoader);
        if (m != null) hookChain(m, "по стеку вызовов");
    }

    /** Ищет RealInterceptorChain.proceed среди кадров, повторяющихся в стеке не меньше 3 раз. */
    static Method findChainMethod(StackTraceElement[] st, ClassLoader loader) {
        Map<String, Integer> counts = new HashMap<>();
        for (StackTraceElement e : st) {
            String k = e.getClassName() + '#' + e.getMethodName();
            Integer c = counts.get(k);
            counts.put(k, c == null ? 1 : c + 1);
        }
        List<Map.Entry<String, Integer>> list = new ArrayList<>(counts.entrySet());
        list.sort((a, b) -> b.getValue() - a.getValue());
        for (Map.Entry<String, Integer> e : list) {
            if (e.getValue() < 3) break;
            String key = e.getKey();
            int h = key.indexOf('#');
            String cls = key.substring(0, h);
            String method = key.substring(h + 1);
            if (cls.startsWith("java.") || cls.startsWith("android.") || cls.startsWith("com.android.")
                    || cls.startsWith("dalvik.") || cls.startsWith("kotlin")) continue;
            Class<?> k;
            try {
                k = Class.forName(cls, false, loader);
            } catch (Throwable t) {
                continue;
            }
            for (Method m : k.getDeclaredMethods()) {
                if (m.getName().equals(method) && isChainMethod(m)) return m;
            }
        }
        return null;
    }

    private static boolean isChainMethod(Method m) {
        if (Modifier.isStatic(m.getModifiers()) || m.getParameterTypes().length != 1) return false;
        Class<?> r = m.getReturnType();
        return !r.isPrimitive() && !r.isInterface() && shape(r) != null;
    }

    private static synchronized void hookChain(Method m, String how) {
        if (installed) return;
        try {
            XposedBridge.hookMethod(m, chainHook());
            installed = true;
            L.i("OkHttp найден (" + how + "): " + m.getDeclaringClass().getName() + "." + m.getName()
                    + " — JSON-фильтр ответов активен");
        } catch (Throwable t) {
            L.e("не удалось хукнуть цепочку OkHttp", t);
        }
    }

    private static XC_MethodHook chainHook() {
        return new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                DEPTH.get()[0]++;
            }

            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                int[] d = DEPTH.get();
                if (d[0] > 0) d[0]--;
                if (d[0] != 0 || param.hasThrowable()) return;
                Object response = param.getResult();
                if (response == null) return;
                try {
                    process(param.args[0], response);
                } catch (Throwable t) {
                    L.e("ошибка фильтрации ответа OkHttp", t);
                }
            }
        };
    }

    /** Фильтрует тело ответа на месте; true — если тело было подменено. */
    static boolean process(Object request, Object response) throws Throwable {
        Shape s = shape(response.getClass());
        if (s == null) return false;
        Object body = s.bodyField.get(response);
        if (body == null) return false;
        Object mt = s.contentType.invoke(body);
        if (mt == null || !mt.toString().toLowerCase(Locale.ROOT).contains("json")) return false;
        long len = (Long) s.contentLength.invoke(body);
        if (len > JsonFilter.MAX_SIZE) return false;
        if (skipUrl(String.valueOf(request))) return false;

        Object src = s.source.invoke(body);
        if (src == null) return false;
        // Подменить тело можно только у тел с одним полем-источником (RealResponseBody).
        BodyFields bf = bodyFields(body.getClass(), s.sourceIface, src, body);
        if (bf == null) return false;

        Object peek = s.peek.invoke(src);
        byte[] bytes;
        try (InputStream in = (InputStream) s.inputStream.invoke(peek)) {
            bytes = readUpTo(in, JsonFilter.MAX_SIZE + 1);
        }
        if (bytes == null) return false;
        String out = JsonFilter.filterString(new String(bytes, StandardCharsets.UTF_8));
        if (out == null) return false;

        byte[] nb = out.getBytes(StandardCharsets.UTF_8);
        Object buf = s.bufferCtor.newInstance();
        ((WritableByteChannel) buf).write(ByteBuffer.wrap(nb));
        bf.source.set(body, buf);
        if (bf.length != null) bf.length.setLong(body, nb.length);
        try {
            ((Closeable) src).close();
        } catch (Throwable ignored) {
        }
        return true;
    }

    private static boolean skipUrl(String request) {
        // Request.toString(): Request{method=GET, url=https://host/path?..., ...}
        String s = request.toLowerCase(Locale.ROOT);
        int u = s.indexOf("url=");
        if (u >= 0) s = s.substring(u + 4);
        int scheme = s.indexOf("://");
        int slash = s.indexOf('/', scheme >= 0 ? scheme + 3 : 0);
        if (slash < 0) return false;
        int end = s.length();
        for (char c : new char[]{'?', ',', '}', ' '}) {
            int i = s.indexOf(c, slash);
            if (i >= 0 && i < end) end = i;
        }
        String path = s.substring(slash, end);
        for (String k : URL_SKIP) {
            if (path.contains(k)) return true;
        }
        return false;
    }

    private static byte[] readUpTo(InputStream in, int max) throws Exception {
        java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream(8192);
        byte[] b = new byte[8192];
        int n;
        while ((n = in.read(b)) > 0) {
            bos.write(b, 0, n);
            if (bos.size() >= max) return null;
        }
        return bos.toByteArray();
    }

    // --- Структурный анализ ---

    private static final class Shape {
        Field bodyField;
        Method contentType;
        Method contentLength;
        Method source;
        Class<?> sourceIface;
        Method peek;
        Method inputStream;
        Constructor<?> bufferCtor;
    }

    private static final class BodyFields {
        Field source;
        Field length;
    }

    private static final Map<Class<?>, Object> BODY_FIELDS = new ConcurrentHashMap<>();

    private static Shape shape(Class<?> responseClass) {
        Object cached = SHAPES.get(responseClass);
        if (cached != null) return cached == NONE ? null : (Shape) cached;
        Shape s = null;
        try {
            s = analyze(responseClass);
        } catch (Throwable ignored) {
        }
        SHAPES.put(responseClass, s == null ? NONE : s);
        return s;
    }

    private static Shape analyze(Class<?> rc) throws Exception {
        for (Class<?> k = rc; k != null && k != Object.class; k = k.getSuperclass()) {
            for (Field f : k.getDeclaredFields()) {
                if (Modifier.isStatic(f.getModifiers())) continue;
                Class<?> b = f.getType();
                if (b.isInterface() || b.isPrimitive() || !Modifier.isAbstract(b.getModifiers())
                        || !Closeable.class.isAssignableFrom(b)) continue;
                Shape s = new Shape();
                for (Method m : b.getDeclaredMethods()) {
                    if (!Modifier.isAbstract(m.getModifiers()) || m.getParameterTypes().length != 0) continue;
                    Class<?> r = m.getReturnType();
                    if (r == long.class) s.contentLength = m;
                    else if (r.isInterface()) {
                        s.source = m;
                        s.sourceIface = r;
                    } else if (!r.isPrimitive()) s.contentType = m;
                }
                if (s.contentLength == null || s.source == null || s.contentType == null) continue;
                for (Method m : s.sourceIface.getMethods()) {
                    if (m.getParameterTypes().length != 0) continue;
                    Class<?> r = m.getReturnType();
                    if (r == s.sourceIface) s.peek = m;
                    else if (r == InputStream.class) s.inputStream = m;
                    else if (!r.isInterface() && s.sourceIface.isAssignableFrom(r)
                            && WritableByteChannel.class.isAssignableFrom(r) && s.bufferCtor == null) {
                        s.bufferCtor = r.getDeclaredConstructor();
                    }
                }
                if (s.peek == null || s.inputStream == null || s.bufferCtor == null) continue;
                f.setAccessible(true);
                s.bodyField = f;
                s.contentType.setAccessible(true);
                s.contentLength.setAccessible(true);
                s.source.setAccessible(true);
                s.peek.setAccessible(true);
                s.inputStream.setAccessible(true);
                s.bufferCtor.setAccessible(true);
                return s;
            }
        }
        return null;
    }

    private static BodyFields bodyFields(Class<?> bodyClass, Class<?> sourceIface, Object src, Object body) {
        Object cached = BODY_FIELDS.get(bodyClass);
        if (cached == NONE) return null;
        BodyFields bf = (BodyFields) cached;
        if (bf == null) {
            bf = new BodyFields();
            int sources = 0;
            int longs = 0;
            for (Field f : bodyClass.getDeclaredFields()) {
                if (Modifier.isStatic(f.getModifiers())) continue;
                if (f.getType() == long.class) {
                    longs++;
                    bf.length = f;
                } else if (!f.getType().isPrimitive() && f.getType().isAssignableFrom(sourceIface)
                        && sourceIface.isAssignableFrom(f.getType())) {
                    sources++;
                    bf.source = f;
                }
            }
            if (sources != 1 || longs > 1) {
                BODY_FIELDS.put(bodyClass, NONE);
                return null;
            }
            bf.source.setAccessible(true);
            if (bf.length != null) bf.length.setAccessible(true);
            BODY_FIELDS.put(bodyClass, bf);
        }
        try {
            // Убедимся, что source() возвращает именно это поле.
            return bf.source.get(body) == src ? bf : null;
        } catch (Throwable t) {
            return null;
        }
    }
}
