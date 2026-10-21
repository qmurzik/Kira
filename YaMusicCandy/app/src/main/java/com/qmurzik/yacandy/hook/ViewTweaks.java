package com.qmurzik.yacandy.hook;

import android.app.Activity;
import android.content.res.Resources;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Locale;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Hides classic Views by resource id / text and dumps the view tree for the inspector.
 * Jetpack Compose content is a single AndroidComposeView and can't be split up from here.
 */
final class ViewTweaks {
    private ViewTweaks() {
    }

    private static final Map<View, Boolean> attached = new WeakHashMap<>();

    static void onResume(CandyModule m, Activity activity) {
        Config c = m.config();
        if (c == null || !c.viewsOn()) return;
        View decor = activity.getWindow().getDecorView();
        if (!attached.containsKey(decor)) {
            attached.put(decor, Boolean.TRUE);
            final boolean[] pending = {false};
            Runnable sweep = () -> {
                pending[0] = false;
                Config cur = m.config();
                if (cur != null && (!cur.hideIds.isEmpty() || !cur.hideTexts.isEmpty())) {
                    hide(decor, cur);
                }
            };
            decor.getViewTreeObserver().addOnGlobalLayoutListener(() -> {
                if (!pending[0]) {
                    pending[0] = true;
                    decor.post(sweep);
                }
            });
        }
        if (c.inspector) {
            decor.postDelayed(() -> {
                StringBuilder sb = new StringBuilder("Screen ")
                        .append(activity.getClass().getName()).append('\n');
                dump(decor, 0, sb);
                // LSPosed log lines are limited, split the dump.
                for (int i = 0; i < sb.length(); i += 3000) {
                    m.logI(sb.substring(i, Math.min(sb.length(), i + 3000)));
                }
                Toast.makeText(activity, "Yume: дерево экрана записано в лог LSPosed",
                        Toast.LENGTH_SHORT).show();
            }, 1500);
        }
    }

    private static void hide(View v, Config c) {
        if (v.getVisibility() != View.GONE && matches(v, c)) {
            v.setVisibility(View.GONE);
            return;
        }
        if (v instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) hide(g.getChildAt(i), c);
        }
    }

    private static boolean matches(View v, Config c) {
        if (!c.hideIds.isEmpty()) {
            String id = idName(v);
            if (id != null && c.hideIds.contains(id)) return true;
        }
        if (!c.hideTexts.isEmpty()) {
            CharSequence text = null;
            if (v instanceof TextView) {
                text = ((TextView) v).getText();
            } else if (v.getContentDescription() != null) {
                text = v.getContentDescription();
            }
            if (text != null && text.length() > 0) {
                String t = text.toString().toLowerCase(Locale.ROOT);
                for (String needle : c.hideTexts) if (t.contains(needle)) return true;
            }
        }
        return false;
    }

    private static String idName(View v) {
        int id = v.getId();
        if (id == View.NO_ID || (id >>> 24) == 0) return null;
        try {
            return v.getResources().getResourceEntryName(id);
        } catch (Resources.NotFoundException e) {
            return null;
        }
    }

    private static void dump(View v, int depth, StringBuilder sb) {
        for (int i = 0; i < depth; i++) sb.append("  ");
        sb.append(v.getClass().getSimpleName());
        String id = idName(v);
        if (id != null) sb.append(" id=").append(id);
        if (v instanceof TextView) {
            CharSequence t = ((TextView) v).getText();
            if (t != null && t.length() > 0) {
                String s = t.toString().replace('\n', ' ');
                sb.append(" text=\"").append(s.length() > 40 ? s.substring(0, 40) + "…" : s).append('"');
            }
        }
        if (v.getVisibility() != View.VISIBLE) sb.append(" [hidden]");
        if (v.getClass().getName().contains("ComposeView")) sb.append(" [Compose]");
        sb.append('\n');
        if (v instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) dump(g.getChildAt(i), depth + 1, sb);
        }
    }
}
