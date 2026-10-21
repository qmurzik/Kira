package com.qmurzik.yacandy.ui;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.os.ParcelFileDescriptor;
import android.system.Os;

import com.qmurzik.yacandy.Keys;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

import io.github.libxposed.service.XposedService;
import io.github.libxposed.service.XposedServiceHelper;

/**
 * Settings storage. Local SharedPreferences are the source of truth for the UI; every write is
 * mirrored into LSPosed remote preferences, which is what the hooked app reads (live).
 */
public final class Store implements XposedServiceHelper.OnServiceListener {
    public interface Listener {
        void onServiceChanged();
    }

    private static Store instance;

    private final SharedPreferences local;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Set<Listener> listeners = new CopyOnWriteArraySet<>();
    private volatile XposedService service;
    private volatile SharedPreferences remote;

    private Store(Context context) {
        local = context.getSharedPreferences("candy_local", Context.MODE_PRIVATE);
    }

    static synchronized void init(Context context) {
        if (instance != null) return;
        instance = new Store(context.getApplicationContext());
        XposedServiceHelper.registerListener(instance);
    }

    static Store get() {
        return instance;
    }

    @Override
    public void onServiceBind(XposedService s) {
        service = s;
        try {
            remote = s.getRemotePreferences(Keys.GROUP);
            pushAll();
        } catch (Throwable t) {
            remote = null;
        }
        notifyListeners();
    }

    @Override
    public void onServiceDied(XposedService s) {
        if (service == s) {
            service = null;
            remote = null;
        }
        notifyListeners();
    }

    private void notifyListeners() {
        main.post(() -> {
            for (Listener l : listeners) l.onServiceChanged();
        });
    }

    void addListener(Listener l) {
        listeners.add(l);
    }

    void removeListener(Listener l) {
        listeners.remove(l);
    }

    XposedService service() {
        return service;
    }

    boolean connected() {
        return remote != null;
    }

    boolean getBoolean(String key, boolean def) {
        return local.getBoolean(key, def);
    }

    int getInt(String key, int def) {
        return local.getInt(key, def);
    }

    String getString(String key, String def) {
        return local.getString(key, def);
    }

    void putBoolean(String key, boolean v) {
        local.edit().putBoolean(key, v).apply();
        SharedPreferences r = remote;
        if (r != null) r.edit().putBoolean(key, v).apply();
    }

    void putInt(String key, int v) {
        local.edit().putInt(key, v).apply();
        SharedPreferences r = remote;
        if (r != null) r.edit().putInt(key, v).apply();
    }

    void putString(String key, String v) {
        local.edit().putString(key, v).apply();
        SharedPreferences r = remote;
        if (r != null) r.edit().putString(key, v).apply();
    }

    /** Applies many values in one remote transaction (one reload in the hooked app). */
    void putAll(Map<String, Object> values) {
        SharedPreferences.Editor le = local.edit();
        SharedPreferences r = remote;
        SharedPreferences.Editor re = r != null ? r.edit() : null;
        for (Map.Entry<String, Object> e : values.entrySet()) {
            put(le, e.getKey(), e.getValue());
            if (re != null) put(re, e.getKey(), e.getValue());
        }
        le.apply();
        if (re != null) re.apply();
    }

    void clearAll() {
        Set<String> keys = local.getAll().keySet();
        local.edit().clear().apply();
        SharedPreferences r = remote;
        if (r != null) {
            SharedPreferences.Editor re = r.edit();
            for (String k : keys) re.remove(k);
            re.apply();
        }
    }

    private void pushAll() {
        SharedPreferences r = remote;
        if (r == null) return;
        // Only push differences: every remote write makes the hooked app reload its screen.
        Map<String, ?> current = r.getAll();
        SharedPreferences.Editor re = r.edit();
        boolean dirty = false;
        for (Map.Entry<String, ?> e : local.getAll().entrySet()) {
            Object old = current.get(e.getKey());
            if (old == null ? e.getValue() == null : old.equals(e.getValue())) continue;
            put(re, e.getKey(), e.getValue());
            dirty = true;
        }
        if (dirty) re.apply();
    }

    private static void put(SharedPreferences.Editor ed, String key, Object v) {
        if (v instanceof Boolean) {
            ed.putBoolean(key, (Boolean) v);
        } else if (v instanceof Integer) {
            ed.putInt(key, (Integer) v);
        } else if (v instanceof String) {
            ed.putString(key, (String) v);
        } else if (v instanceof Long) {
            ed.putLong(key, (Long) v);
        } else if (v instanceof Float) {
            ed.putFloat(key, (Float) v);
        } else if (v == null) {
            ed.remove(key);
        }
    }

    /** Copies a font into the module's shared files so the hooked app can open it. */
    void writeFont(boolean bold, InputStream in) throws IOException {
        XposedService s = service;
        if (s == null) throw new IOException("LSPosed не подключён");
        try (ParcelFileDescriptor pfd = s.openRemoteFile(bold ? Keys.FONT_BOLD_FILE : Keys.FONT_FILE);
             FileOutputStream out = new FileOutputStream(pfd.getFileDescriptor())) {
            byte[] buf = new byte[64 * 1024];
            long total = 0;
            int n;
            while ((n = in.read(buf)) > 0) {
                out.write(buf, 0, n);
                total += n;
            }
            out.flush();
            Os.ftruncate(pfd.getFileDescriptor(), total);
        } catch (IOException e) {
            throw e;
        } catch (Exception e) {
            throw new IOException(e);
        }
        String version = bold ? Keys.FONT_BOLD_VERSION : Keys.FONT_VERSION;
        putInt(version, getInt(version, 0) + 1);
    }
}
