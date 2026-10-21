package com.qmurzik.yacandy;

/**
 * Preference keys and defaults shared by the settings app and the hook side.
 * Values are stored in libxposed remote preferences group {@link #GROUP}.
 */
public final class Keys {
    private Keys() {
    }

    public static final String TARGET_PACKAGE = "ru.yandex.music";
    public static final String GROUP = "candy";
    public static final String FONT_FILE = "custom_font";

    // Colors
    public static final String COLORS_ON = "colors_on";
    public static final String ACCENT_ON = "accent_on";
    public static final String ACCENT_COLOR = "accent_color";
    public static final String ACCENT_HUE_FROM = "accent_hue_from";
    public static final String ACCENT_HUE_TO = "accent_hue_to";
    public static final String HUE_SHIFT = "hue_shift";
    public static final String SATURATION = "saturation";
    public static final String DARK_BG_ON = "dark_bg_on";
    public static final String DARK_BG_COLOR = "dark_bg_color";
    public static final String LIGHT_BG_ON = "light_bg_on";
    public static final String LIGHT_BG_COLOR = "light_bg_color";
    public static final String TEXT_ON = "text_on";
    public static final String TEXT_DARK_COLOR = "text_dark_color";
    public static final String TEXT_LIGHT_COLOR = "text_light_color";
    public static final String COLOR_MAP = "color_map";

    // Font
    public static final String FONT_SOURCE = "font_source";
    public static final String FONT_WEIGHT_DELTA = "font_weight_delta";
    public static final String FONT_VERSION = "font_version";

    // Geometry
    public static final String UI_SCALE = "ui_scale";
    public static final String FONT_SCALE = "font_scale";
    public static final String CORNERS_ON = "corners_on";
    public static final String CORNER_FACTOR = "corner_factor";
    public static final String KEEP_CIRCLES = "keep_circles";

    // Views
    public static final String HIDE_IDS = "hide_ids";
    public static final String HIDE_TEXTS = "hide_texts";
    public static final String INSPECTOR = "inspector";

    // Behaviour
    public static final String LIVE_APPLY = "live_apply";

    public static final String FONT_OFF = "off";
    public static final String FONT_CUSTOM = "custom";
    /** Font source ids and the system family each one maps to (index-aligned). */
    public static final String[] FONT_IDS = {
            FONT_OFF, "condensed", "serif", "mono", "casual", "cursive", "smallcaps", FONT_CUSTOM};
    public static final String[] FONT_FAMILIES = {
            null, "sans-serif-condensed", "serif", "monospace", "casual", "cursive",
            "sans-serif-smallcaps", null};

    // Defaults. Yandex Music's signature yellow sits around hue 45-50.
    public static final int DEF_ACCENT = 0xFFFF6FB5;
    public static final int DEF_HUE_FROM = 35;
    public static final int DEF_HUE_TO = 65;
    public static final int DEF_DARK_BG = 0xFF14101C;
    public static final int DEF_LIGHT_BG = 0xFFFFF5FA;
    public static final int DEF_TEXT_DARK = 0xFFFFE9F4;
    public static final int DEF_TEXT_LIGHT = 0xFF2A1630;
}
