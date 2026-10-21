package com.qmurzik.yacandy.hook;

import android.content.pm.ApplicationInfo;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.Executable;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Locates obfuscated Yandex Music code by the string constants it uses. The dex scan runs once per
 * installed app build; results are cached in the app's own data dir.
 */
final class Finder {
    static final String EXP_RESOLVER = "Unknown experiment - ";
    static final String EXP_BASE = "_NotReady";
    static final String JSON_DESERIALIZERS = DexScanner.IMPL + "Lcom/google/gson/JsonDeserializer;";
    private static final List<String> QUERIES = Arrays.asList(EXP_RESOLVER, EXP_BASE, JSON_DESERIALIZERS);
    private static final int CACHE_VERSION = 2;

    private final ClassLoader loader;
    private final Map<String, List<DexScanner.MethodRef>> found;

    private Finder(ClassLoader loader, Map<String, List<DexScanner.MethodRef>> found) {
        this.loader = loader;
        this.found = found;
    }

    static Finder load(CandyModule m, ApplicationInfo app, ClassLoader loader) {
        File apk = new File(app.sourceDir);
        String key = CACHE_VERSION + ":" + apk.length() + ":" + apk.lastModified() + ":" + String.join(",", QUERIES);
        File cache = new File(app.dataDir, "files/yacandy_dex.cache");
        Map<String, List<DexScanner.MethodRef>> found = readCache(cache, key);
        if (found == null) {
            long t = System.currentTimeMillis();
            try {
                List<String> apks = new ArrayList<>();
                apks.add(app.sourceDir);
                if (app.splitSourceDirs != null) apks.addAll(Arrays.asList(app.splitSourceDirs));
                found = DexScanner.findStringUsers(apks, QUERIES);
                writeCache(cache, key, found);
                m.logI("dex scan took " + (System.currentTimeMillis() - t) + " ms");
            } catch (Throwable e) {
                m.logE("dex scan failed", e);
                found = new HashMap<>();
            }
        }
        return new Finder(loader, found);
    }

    List<DexScanner.MethodRef> users(String string) {
        List<DexScanner.MethodRef> l = found.get(string);
        return l != null ? l : new ArrayList<>();
    }

    Class<?> classOf(DexScanner.MethodRef ref) throws ClassNotFoundException {
        return Class.forName(ref.className(), false, loader);
    }

    Executable resolve(DexScanner.MethodRef ref) throws ReflectiveOperationException {
        Class<?> owner = classOf(ref);
        Class<?>[] params = new Class<?>[ref.params.length];
        for (int i = 0; i < params.length; i++) params[i] = type(ref.params[i]);
        if (ref.isConstructor()) {
            Constructor<?> c = owner.getDeclaredConstructor(params);
            c.setAccessible(true);
            return c;
        }
        Method method = owner.getDeclaredMethod(ref.name, params);
        method.setAccessible(true);
        return method;
    }

    private Class<?> type(String d) throws ClassNotFoundException {
        switch (d) {
            case "Z": return boolean.class;
            case "B": return byte.class;
            case "C": return char.class;
            case "S": return short.class;
            case "I": return int.class;
            case "J": return long.class;
            case "F": return float.class;
            case "D": return double.class;
            case "V": return void.class;
            default:
                if (d.startsWith("[")) return Class.forName(d.replace('/', '.'), false, loader);
                return Class.forName(DexScanner.descriptorToName(d), false, loader);
        }
    }

    private static Map<String, List<DexScanner.MethodRef>> readCache(File f, String key) {
        if (!f.isFile()) return null;
        try (FileInputStream in = new FileInputStream(f)) {
            byte[] data = new byte[(int) f.length()];
            int off = 0;
            while (off < data.length) {
                int r = in.read(data, off, data.length - off);
                if (r < 0) break;
                off += r;
            }
            String[] lines = new String(data, 0, off, StandardCharsets.UTF_8).split("\n");
            if (lines.length == 0 || !lines[0].equals(key)) return null;
            Map<String, List<DexScanner.MethodRef>> out = new HashMap<>();
            for (String q : QUERIES) out.put(q, new ArrayList<>());
            for (int i = 1; i < lines.length; i++) {
                int tab = lines[i].indexOf('\t');
                if (tab < 0) continue;
                List<DexScanner.MethodRef> l = out.get(lines[i].substring(0, tab));
                if (l != null) l.add(DexScanner.MethodRef.decode(lines[i].substring(tab + 1)));
            }
            return out;
        } catch (Throwable t) {
            return null;
        }
    }

    private static void writeCache(File f, String key, Map<String, List<DexScanner.MethodRef>> found) {
        StringBuilder sb = new StringBuilder(key).append('\n');
        for (Map.Entry<String, List<DexScanner.MethodRef>> e : found.entrySet()) {
            for (DexScanner.MethodRef r : e.getValue()) {
                sb.append(e.getKey()).append('\t').append(r.encode()).append('\n');
            }
        }
        try {
            File dir = f.getParentFile();
            if (dir != null && !dir.isDirectory()) //noinspection ResultOfMethodCallIgnored
                dir.mkdirs();
            try (FileOutputStream out = new FileOutputStream(f)) {
                out.write(sb.toString().getBytes(StandardCharsets.UTF_8));
            }
        } catch (Throwable ignored) {
            // cache is an optimization only
        }
    }
}
