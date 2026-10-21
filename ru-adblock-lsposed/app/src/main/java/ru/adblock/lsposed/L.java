package ru.adblock.lsposed;

import android.util.Log;

import de.robv.android.xposed.XposedBridge;

/** Логирование в журнал LSPosed с единым тегом. */
final class L {
    static final String TAG = "RuAdBlock";

    static volatile boolean debug;

    private L() {
    }

    static void i(String msg) {
        try {
            XposedBridge.log(TAG + ": " + msg);
        } catch (Throwable ignored) {
        }
    }

    static void d(String msg) {
        if (debug) i(msg);
    }

    static void e(String msg, Throwable t) {
        i(msg + ": " + Log.getStackTraceString(t));
    }
}
