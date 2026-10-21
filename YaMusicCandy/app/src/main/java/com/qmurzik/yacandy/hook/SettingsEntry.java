package com.qmurzik.yacandy.hook;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.ShortcutInfo;
import android.content.pm.ShortcutManager;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Shader;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.Icon;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.qmurzik.yacandy.Keys;

import java.util.Collections;

/**
 * Ways to open Yume from inside Yandex Music that don't depend on its internals:
 * <ul>
 * <li>a floating "Yume" pill on the app's settings screen (an activity declared in the manifest,
 * so its name can't be obfuscated);</li>
 * <li>a "Yume" launcher shortcut on the Yandex Music icon (long-press on the home screen).</li>
 * </ul>
 * The module is started by an implicit intent with its own action; implicit activity starts are
 * not limited by package visibility. The intent also carries a compatibility report.
 */
final class SettingsEntry {
    private SettingsEntry() {
    }

    private static final String TAG_PILL = "yume_entry";
    private static final String SHORTCUT_ID = "yume_settings";
    private static boolean shortcutDone;
    private static Bitmap icon;

    static void onResume(CandyModule m, Activity a) {
        Config c = m.config();
        if (c == null) return;
        if (!shortcutDone) {
            shortcutDone = true;
            publishShortcut(m, a, c.entryShortcut);
        }
        if (c.entryButton && isSettingsScreen(a)) addPill(m, a);
    }

    private static boolean isSettingsScreen(Activity a) {
        String n = a.getClass().getName();
        return n.endsWith(".settings.SettingsActivity") || a.getClass().getSimpleName().equals("SettingsActivity");
    }

    static Intent intent(CandyModule m, Context ctx) {
        Intent i = new Intent(Keys.ACTION_SETTINGS);
        i.addCategory(Intent.CATEGORY_DEFAULT);
        i.putExtra(Keys.EXTRA_STATUS, m.statusReport());
        try {
            PackageInfo pi = ctx.getPackageManager().getPackageInfo(ctx.getPackageName(), 0);
            i.putExtra(Keys.EXTRA_APP_VERSION, pi.versionName + " (" + pi.getLongVersionCode() + ")");
        } catch (Throwable ignored) {
            // version unknown
        }
        return i;
    }

    private static void open(CandyModule m, Activity a) {
        try {
            a.startActivity(intent(m, a));
        } catch (Throwable t) {
            m.logE("open settings", t);
            Toast.makeText(a, "Не удалось открыть Yume. Откройте его из списка приложений.", Toast.LENGTH_LONG).show();
        }
    }

    private static void addPill(CandyModule m, Activity a) {
        View decor = a.getWindow().getDecorView();
        if (!(decor instanceof ViewGroup) || decor.findViewWithTag(TAG_PILL) != null) return;
        Context ctx = a;
        TextView pill = new TextView(ctx);
        pill.setTag(TAG_PILL);
        pill.setText("Yume");
        pill.setTextColor(Color.WHITE);
        pill.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        pill.setTypeface(android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL));
        pill.setGravity(Gravity.CENTER_VERTICAL);
        int p = dp(ctx, 14);
        pill.setPadding(dp(ctx, 8), dp(ctx, 8), dp(ctx, 18), dp(ctx, 8));
        GradientDrawable bg = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{0xFF8B6CFF, 0xFFC77DFF, 0xFF5B7CFF});
        bg.setCornerRadius(dp(ctx, 28));
        pill.setBackground(bg);
        pill.setElevation(dp(ctx, 8));
        Bitmap b = icon(m, ctx);
        if (b != null) {
            BitmapDrawable d = new BitmapDrawable(ctx.getResources(), b);
            int s = dp(ctx, 30);
            d.setBounds(0, 0, s, s);
            pill.setCompoundDrawables(d, null, null, null);
            pill.setCompoundDrawablePadding(dp(ctx, 8));
        } else {
            pill.setPadding(p, dp(ctx, 12), p, dp(ctx, 12));
        }
        pill.setOnClickListener(v -> open(m, a));
        pill.setOnLongClickListener(v -> {
            ((ViewGroup) v.getParent()).removeView(v);
            Toast.makeText(a, "Кнопку Yume можно отключить в настройках модуля", Toast.LENGTH_SHORT).show();
            return true;
        });
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM | Gravity.END);
        int margin = dp(ctx, 20);
        lp.setMargins(margin, margin, margin, margin);
        // Stay above the navigation bar.
        pill.setOnApplyWindowInsetsListener((v, insets) -> {
            @SuppressWarnings("deprecation") int bottom = insets.getSystemWindowInsetBottom();
            FrameLayout.LayoutParams l = (FrameLayout.LayoutParams) v.getLayoutParams();
            l.bottomMargin = margin + bottom;
            v.setLayoutParams(l);
            return insets;
        });
        ((ViewGroup) decor).addView(pill, lp);
        pill.setAlpha(0f);
        pill.setTranslationY(dp(ctx, 24));
        pill.animate().alpha(1f).translationY(0).setDuration(350).start();
        pill.requestApplyInsets();
    }

    private static void publishShortcut(CandyModule m, Activity a, boolean enabled) {
        try {
            ShortcutManager sm = a.getSystemService(ShortcutManager.class);
            if (sm == null) return;
            if (!enabled) {
                sm.removeDynamicShortcuts(Collections.singletonList(SHORTCUT_ID));
                return;
            }
            Intent i = intent(m, a);
            i.removeExtra(Keys.EXTRA_STATUS); // shortcuts are persisted; the report would go stale
            ShortcutInfo.Builder b = new ShortcutInfo.Builder(a, SHORTCUT_ID)
                    .setShortLabel("Yume")
                    .setLongLabel("Настройки Yume")
                    .setIntent(i);
            Bitmap bmp = icon(m, a);
            if (bmp != null) b.setIcon(Icon.createWithAdaptiveBitmap(adaptive(bmp)));
            sm.addDynamicShortcuts(Collections.singletonList(b.build()));
        } catch (Throwable t) {
            m.logE("shortcut", t);
        }
    }

    /** The module's launcher artwork, read from its own APK resources. */
    private static Bitmap icon(CandyModule m, Context ctx) {
        if (icon != null) return icon;
        try {
            Resources res = ctx.getPackageManager().getResourcesForApplication(m.getModuleApplicationInfo());
            int id = res.getIdentifier("ic_launcher_foreground", "mipmap", m.getModuleApplicationInfo().packageName);
            Bitmap src = id != 0 ? BitmapFactory.decodeResource(res, id) : null;
            if (src == null) return null;
            // Center 66% is the visible part of an adaptive icon layer; clip it to a circle.
            int w = src.getWidth();
            int crop = Math.round(w * 0.66f);
            int off = (w - crop) / 2;
            Bitmap inner = Bitmap.createBitmap(src, off, off, crop, crop);
            Bitmap out = Bitmap.createBitmap(crop, crop, Bitmap.Config.ARGB_8888);
            Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
            paint.setShader(new BitmapShader(inner, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP));
            new Canvas(out).drawCircle(crop / 2f, crop / 2f, crop / 2f, paint);
            icon = out;
            return out;
        } catch (Throwable t) {
            m.logE("module icon", t);
            return null;
        }
    }

    /** Pads a circular icon to the 108dp adaptive canvas (72dp visible). */
    private static Bitmap adaptive(Bitmap circle) {
        int s = Math.round(circle.getWidth() * 108f / 72f);
        Bitmap out = Bitmap.createBitmap(s, s, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(out);
        c.drawColor(0xFF0C0B22);
        int off = (s - circle.getWidth()) / 2;
        c.drawBitmap(circle, off, off, null);
        return out;
    }

    private static int dp(Context c, float v) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v,
                c.getResources().getDisplayMetrics()));
    }
}
