package com.qmurzik.yacandy.hook;

import android.content.SharedPreferences;

import com.qmurzik.yacandy.Features;
import com.qmurzik.yacandy.Keys;

import java.util.Collections;
import java.util.HashMap;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
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

    /** Skeleton block DTO simple names and backend block types to drop. */
    final Set<String> hiddenDtos;
    final Set<String> hiddenTypes;
    /** Lower-cased titles of main-screen tabs to drop. */
    final Set<String> hiddenTabs;
    final boolean feedLog;
    /** Full names of Retrofit API interfaces whose calls are failed. */
    final Set<String> blockedApis;
    final boolean telemetryOff;
    final boolean promoPushOff;
    final boolean keepScreenOn;
    /** Animator duration scale to force, or NaN to leave the system value. */
    final float animScale;
    /** Experiment name -> forced value. */
    final Map<String, String> expOverrides;
    final boolean expDump;

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

        Set<String> dtos = new HashSet<>();
        Set<String> groups = parseLines(p.getString(Keys.HIDE_BLOCKS, ""), false);
        for (Features.BlockGroup g : Features.BLOCK_GROUPS) {
            if (groups.contains(g.id)) dtos.addAll(Arrays.asList(g.dtos));
        }
        Set<String> types = new HashSet<>();
        for (String s : parseLines(p.getString(Keys.HIDE_BLOCKS_CUSTOM, ""), false)) {
            if (s.endsWith("Dto")) {
                dtos.add(s);
            } else {
                types.add(s.toLowerCase(Locale.ROOT));
            }
        }
        hiddenDtos = dtos;
        hiddenTypes = types;
        hiddenTabs = parseLines(p.getString(Keys.HIDE_TABS, ""), true);
        feedLog = p.getBoolean(Keys.FEED_LOG, false);

        Set<String> apis = new HashSet<>();
        block(apis, p, Keys.NO_POPUPS, Features.API_POPUPS);
        block(apis, p, Keys.NO_SPLASH, Features.API_SPLASH);
        block(apis, p, Keys.NO_SPECIAL_HEADER, Features.API_SPECIAL_HEADER);
        block(apis, p, Keys.NO_INFORMERS, Features.API_INFORMERS);
        block(apis, p, Keys.PB_SIMILAR, Features.API_PB_SIMILAR);
        block(apis, p, Keys.PB_ARTISTS, Features.API_PB_ARTISTS);
        block(apis, p, Keys.PB_CONCERTS, Features.API_PB_CONCERTS);
        block(apis, p, Keys.PB_NONMUSIC, Features.API_PB_NONMUSIC);
        block(apis, p, Keys.NO_WAVE_WORDS, Features.API_WAVE_WORDS);
        blockedApis = apis;

        telemetryOff = p.getBoolean(Keys.NO_TELEMETRY, false);
        promoPushOff = p.getBoolean(Keys.NO_PROMO_PUSH, false);
        keepScreenOn = p.getBoolean(Keys.KEEP_SCREEN_ON, false);
        String anim = p.getString(Keys.ANIM_SCALE, Keys.ANIM_SYSTEM);
        float scale = Float.NaN;
        try {
            if (!Keys.ANIM_SYSTEM.equals(anim)) scale = Float.parseFloat(anim);
        } catch (NumberFormatException ignored) {
            // keep system value
        }
        animScale = scale;

        // Yandex's own feature flags. "default" is the "off" value of their boolean experiments.
        Map<String, String> exp = new LinkedHashMap<>();
        if (p.getBoolean(Keys.NO_SPLASH, false)) exp.put("androidSplashScreen", OFF);
        if (p.getBoolean(Keys.NO_PROMO_RECS, false)) exp.put("androidPromoVeryBestRecommendations", OFF);
        if (p.getBoolean(Keys.NO_CONCERTS_TAB, false)) exp.put("AndroidConcertsTab", OFF);
        if (p.getBoolean(Keys.NO_PLUS_QUESTS, false)) exp.put("androidPlusQuests", OFF);
        if (p.getBoolean(Keys.NO_PLUS_WIDGET, false)) exp.put("androidPlusDailyWidget", OFF);
        if (p.getBoolean(Keys.LYRICS_FULLSCREEN, false)) {
            exp.put("androidManualFullscreenLyricsModeExperiment", ON);
            exp.put("androidLyricsDisableExperiment", OFF);
        }
        if (p.getBoolean(Keys.CALM_WAVE, false)) {
            for (String n : new String[]{"androidWaveShake", "androidWaveMetaballPlayer",
                    "androidWaveScreenVolumeBackground", "androidShaderWaveAgent", "androidQ2vGlow"}) {
                exp.put(n, OFF);
            }
        }
        if (telemetryOff) {
            for (String n : new String[]{"androidScrollFrameTracking", "androidImageMetricsConfig",
                    "androidPlayerDebugLogEvents", "androidReportDownloadsAnalytics",
                    "androidNetworkMetricsExperiment_Dev"}) {
                exp.put(n, OFF);
            }
        }
        // The autoplay launcher treats default/off/control as disabled.
        if (p.getBoolean(Keys.NO_AUTOPLAY, false)) exp.put("androidAutoplay", OFF);
        if (p.getBoolean(Keys.NO_UPDATE_PROMPTS, false)) exp.put("androidInAppUpdates", OFF);
        if (p.getBoolean(Keys.NO_BATTERY_NAG, false)) {
            exp.put("androidBatteryRestrictionBottomSheet", OFF);
            exp.put("androidShowBatteryPermissionsDialog", OFF);
        }
        if (p.getBoolean(Keys.NO_WIZARD, false)) exp.put("AndroidUnskippableWizard", OFF);
        if (p.getBoolean(Keys.AI_REDUCTION, false)) exp.put("AndroidAIContentReductionSetting", ON);
        if (p.getBoolean(Keys.MESSENGER_SHARE, false)) exp.put("androidTelegramWhatsappSharing", ON);
        exp.putAll(parseOverrides(p.getString(Keys.EXP_OVERRIDES, "")));
        expOverrides = exp;
        expDump = p.getBoolean(Keys.EXP_DUMP, false);
    }

    static final String ON = "on";
    static final String OFF = "default";

    boolean feedOn() {
        return !hiddenDtos.isEmpty() || !hiddenTypes.isEmpty() || !hiddenTabs.isEmpty() || feedLog;
    }

    boolean experimentsOn() {
        return !expOverrides.isEmpty() || expDump;
    }

    private static void block(Set<String> out, SharedPreferences p, String key, String[] apis) {
        if (p.getBoolean(key, false)) out.addAll(Arrays.asList(apis));
    }

    /** Lines like {@code androidWaveShake = on}. */
    static Map<String, String> parseOverrides(String s) {
        if (s == null || s.trim().isEmpty()) return Collections.emptyMap();
        Map<String, String> out = new LinkedHashMap<>();
        for (String line : s.split("\\n")) {
            String t = line.trim();
            if (t.isEmpty() || t.startsWith("#") || t.startsWith("//")) continue;
            String[] parts = t.split("\\s*[=:]\\s*", 2);
            if (parts.length != 2 || parts[0].isEmpty() || parts[1].isEmpty()) continue;
            out.put(parts[0], parts[1]);
        }
        return out;
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
