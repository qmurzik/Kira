package com.qmurzik.yacandy.hook;

import android.content.SharedPreferences;

import com.qmurzik.yacandy.Keys;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Immutable snapshot of the module settings, re-created on every preference change. */
final class Config {
    final boolean colorsOn;
    final boolean accentOn;
    final int accentColor;
    final int hueFrom;
    final int hueTo;
    final int hueShift;
    final float saturation;
    final boolean darkBgOn;
    final int darkBg;
    final boolean lightBgOn;
    final int lightBg;
    final boolean textOn;
    final int textDark;
    final int textLight;
    /**
     * rgb (no alpha) -> replacement. The low 32 bits hold the argb; {@link #EXPLICIT_ALPHA} is set
     * when the replacement alpha was given explicitly, otherwise the original alpha is kept.
     */
    final Map<Integer, Long> colorMap;
    static final long EXPLICIT_ALPHA = 1L << 32;

    final String fontSource;
    final int fontWeightDelta;
    final int fontVersion;

    final float uiScale;
    final float fontScale;
    final boolean cornersOn;
    final float cornerFactor;
    final boolean keepCircles;

    final Set<String> hideIds;
    final Set<String> hideTexts;
    final boolean inspector;
    final boolean liveApply;

    Config(SharedPreferences p) {
        colorsOn = p.getBoolean(Keys.COLORS_ON, false);
        accentOn = p.getBoolean(Keys.ACCENT_ON, true);
        accentColor = p.getInt(Keys.ACCENT_COLOR, Keys.DEF_ACCENT);
        hueFrom = p.getInt(Keys.ACCENT_HUE_FROM, Keys.DEF_HUE_FROM);
        hueTo = p.getInt(Keys.ACCENT_HUE_TO, Keys.DEF_HUE_TO);
        hueShift = p.getInt(Keys.HUE_SHIFT, 0);
        saturation = p.getInt(Keys.SATURATION, 100) / 100f;
        darkBgOn = p.getBoolean(Keys.DARK_BG_ON, false);
        darkBg = p.getInt(Keys.DARK_BG_COLOR, Keys.DEF_DARK_BG);
        lightBgOn = p.getBoolean(Keys.LIGHT_BG_ON, false);
        lightBg = p.getInt(Keys.LIGHT_BG_COLOR, Keys.DEF_LIGHT_BG);
        textOn = p.getBoolean(Keys.TEXT_ON, false);
        textDark = p.getInt(Keys.TEXT_DARK_COLOR, Keys.DEF_TEXT_DARK);
        textLight = p.getInt(Keys.TEXT_LIGHT_COLOR, Keys.DEF_TEXT_LIGHT);
        colorMap = parseColorMap(p.getString(Keys.COLOR_MAP, ""));

        fontSource = p.getString(Keys.FONT_SOURCE, Keys.FONT_OFF);
        fontWeightDelta = p.getInt(Keys.FONT_WEIGHT_DELTA, 0);
        fontVersion = p.getInt(Keys.FONT_VERSION, 0);

        uiScale = p.getInt(Keys.UI_SCALE, 100) / 100f;
        fontScale = p.getInt(Keys.FONT_SCALE, 100) / 100f;
        cornersOn = p.getBoolean(Keys.CORNERS_ON, false);
        cornerFactor = p.getInt(Keys.CORNER_FACTOR, 100) / 100f;
        keepCircles = p.getBoolean(Keys.KEEP_CIRCLES, true);

        hideIds = parseLines(p.getString(Keys.HIDE_IDS, ""), false);
        hideTexts = parseLines(p.getString(Keys.HIDE_TEXTS, ""), true);
        inspector = p.getBoolean(Keys.INSPECTOR, false);
        liveApply = p.getBoolean(Keys.LIVE_APPLY, true);
    }

    boolean fontOn() {
        return !Keys.FONT_OFF.equals(fontSource) || fontWeightDelta != 0;
    }

    boolean scaleOn() {
        return Math.abs(uiScale - 1f) > 0.001f || Math.abs(fontScale - 1f) > 0.001f;
    }

    boolean viewsOn() {
        return !hideIds.isEmpty() || !hideTexts.isEmpty() || inspector;
    }

    private static Set<String> parseLines(String s, boolean lower) {
        if (s == null || s.trim().isEmpty()) return Collections.emptySet();
        Set<String> out = new HashSet<>();
        for (String line : s.split("[\\n,]")) {
            String t = line.trim();
            if (t.isEmpty() || t.startsWith("#")) continue;
            out.add(lower ? t.toLowerCase(Locale.ROOT) : t);
        }
        return out;
    }

    /** Parses lines like {@code #FFDB4D = #FF6FB5} or {@code FFDB4D->80FF6FB5}. */
    static Map<Integer, Long> parseColorMap(String s) {
        if (s == null || s.trim().isEmpty()) return Collections.emptyMap();
        Map<Integer, Long> out = new HashMap<>();
        for (String line : s.split("\\n")) {
            String t = line.trim();
            if (t.isEmpty() || t.startsWith("//")) continue;
            String[] parts = t.split("\\s*(=|->|→)\\s*");
            if (parts.length != 2) continue;
            Long from = parseHex(parts[0]);
            Long to = parseHex(parts[1]);
            if (from == null || to == null) continue;
            boolean toHasAlpha = clean(parts[1]).length() == 8;
            long value = toHasAlpha ? (to | EXPLICIT_ALPHA) : (to & 0xFFFFFFL);
            out.put((int) (from & 0xFFFFFF), value);
        }
        return out;
    }

    private static String clean(String s) {
        return s.trim().replace("#", "").replace("0x", "").replace("0X", "");
    }

    private static Long parseHex(String s) {
        String c = clean(s);
        if (c.length() != 6 && c.length() != 8) return null;
        try {
            return Long.parseLong(c, 16);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
