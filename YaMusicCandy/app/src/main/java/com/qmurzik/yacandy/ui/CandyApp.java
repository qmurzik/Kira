package com.qmurzik.yacandy.ui;

import android.app.Application;

public class CandyApp extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        // The LSPosed service binder is delivered via XposedProvider right at process start.
        Store.init(this);
    }
}
