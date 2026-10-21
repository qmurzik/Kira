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

    /** Index of the last applied preset (settings app only). */
    public static final String PRESET = "preset";

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
    public static final String DARK_SURFACE_COLOR = "dark_surface_color";
    public static final String LIGHT_SURFACE_COLOR = "light_surface_color";
    public static final String LIKE_ON = "like_on";
    public static final String LIKE_COLOR = "like_color";
    /** Text on accent buttons: "auto" (by contrast), "black" or "white". */
    public static final String ON_ACCENT = "on_accent";
    public static final String ACCENT_LINKS = "accent_links";

    // Font
    public static final String FONT_SOURCE = "font_source";
    public static final String FONT_WEIGHT_DELTA = "font_weight_delta";
    public static final String FONT_VERSION = "font_version";
    /** Second slot: bold text and headings (weight 600+). Same values as {@link #FONT_SOURCE}. */
    public static final String FONT_BOLD_SOURCE = "font_bold_source";
    public static final String FONT_BOLD_VERSION = "font_bold_version";
    public static final String FONT_BOLD_FILE = "custom_font_bold";

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

    // Opening Yume from Yandex Music
    public static final String ENTRY_BUTTON = "entry_button";
    public static final String ENTRY_SHORTCUT = "entry_shortcut";
    public static final String ACTION_SETTINGS = "com.qmurzik.yume.SETTINGS";
    /** "feature\tdetail" lines describing what the hooks found in the running app build. */
    public static final String EXTRA_STATUS = "yume_status";
    public static final String EXTRA_APP_VERSION = "yume_app_version";
    /** Opens a settings page directly: colors, fonts, shape, clean, player, behaviour, experiments, more. */
    public static final String EXTRA_PAGE = "yume_page";

    // Clean interface: main-screen blocks (by group id, see FeedBlocks), tabs, promo endpoints
    public static final String HIDE_BLOCKS = "hide_blocks";
    public static final String HIDE_BLOCKS_CUSTOM = "hide_blocks_custom";
    public static final String HIDE_TABS = "hide_tabs";
    public static final String NO_POPUPS = "no_popups";
    public static final String NO_SPLASH = "no_splash";
    public static final String NO_SPECIAL_HEADER = "no_special_header";
    public static final String NO_PROMO_RECS = "no_promo_recs";
    public static final String NO_CONCERTS_TAB = "no_concerts_tab";
    public static final String NO_PLUS_QUESTS = "no_plus_quests";
    public static final String NO_PLUS_WIDGET = "no_plus_widget";
    public static final String NO_PROMO_PUSH = "no_promo_push";
    public static final String FEED_LOG = "feed_log";

    // Player
    public static final String NO_INFORMERS = "no_informers";
    public static final String PB_SIMILAR = "pb_similar";
    public static final String PB_ARTISTS = "pb_artists";
    public static final String PB_CONCERTS = "pb_concerts";
    public static final String PB_NONMUSIC = "pb_nonmusic";
    public static final String NO_WAVE_WORDS = "no_wave_words";
    public static final String LYRICS_FULLSCREEN = "lyrics_fullscreen";
    public static final String CALM_WAVE = "calm_wave";
    public static final String ANIM_SCALE = "anim_scale";
    public static final String KEEP_SCREEN_ON = "keep_screen_on";

    // App behaviour
    public static final String NO_TELEMETRY = "no_telemetry";
    public static final String NO_AUTOPLAY = "no_autoplay";
    public static final String NO_UPDATE_PROMPTS = "no_update_prompts";
    public static final String NO_BATTERY_NAG = "no_battery_nag";
    public static final String NO_WIZARD = "no_wizard";
    public static final String AI_REDUCTION = "ai_reduction";
    public static final String MESSENGER_SHARE = "messenger_share";
    public static final String EXP_OVERRIDES = "exp_overrides";
    public static final String EXP_DUMP = "exp_dump";

    /** Animation speed choices; "system" leaves the device setting alone. */
    public static final String ANIM_SYSTEM = "system";
    public static final String[] ANIM_IDS = {ANIM_SYSTEM, "0", "0.5", "0.75", "1", "1.5"};

    public static final String FONT_OFF = "off";
    public static final String FONT_CUSTOM = "custom";
    /** Prefix of fonts shipped in the module's assets/fonts; the file is copied to the remote slot. */
    public static final String FONT_BUNDLED = "bundled:";
    /** "Same as text" for the bold slot. */
    public static final String FONT_SAME = "same";
    public static final String[] BUNDLED_FONT_IDS = {
            "montserrat", "inter", "manrope", "onest", "golos", "rubik", "nunito", "comfortaa",
            "raleway", "jost", "exo2", "unbounded", "jetbrainsmono", "russoone", "caveat", "lobster",
            "pacifico", "pressstart2p"};
    public static final String[] BUNDLED_FONT_NAMES = {
            "Montserrat", "Inter", "Manrope", "Onest", "Golos Text", "Rubik", "Nunito", "Comfortaa",
            "Raleway", "Jost", "Exo 2", "Unbounded", "JetBrains Mono", "Russo One", "Caveat (рукописный)",
            "Lobster", "Pacifico", "Press Start 2P (пиксельный)"};
    /** Font source ids and the system family each one maps to (index-aligned). */
    public static final String[] FONT_IDS = {
            FONT_OFF, "condensed", "serif", "mono", "casual", "cursive", "smallcaps", FONT_CUSTOM};
    public static final String[] FONT_FAMILIES = {
            null, "sans-serif-condensed", "serif", "monospace", "casual", "cursive",
            "sans-serif-smallcaps", null};

    // Defaults: the Yume palette. Yandex Music's signature yellow sits around hue 50-60.
    public static final int DEF_ACCENT = 0xFF9D7BFF;
    public static final int DEF_HUE_FROM = 35;
    public static final int DEF_HUE_TO = 65;
    public static final int DEF_DARK_BG = 0xFF0B0A1A;
    public static final int DEF_LIGHT_BG = 0xFFF7F5FF;
    public static final int DEF_TEXT_DARK = 0xFFEDE7FF;
    public static final int DEF_TEXT_LIGHT = 0xFF1B1530;
    public static final int DEF_DARK_SURFACE = 0xFF17142B;
    public static final int DEF_LIGHT_SURFACE = 0xFFECE7FB;
    public static final int DEF_LIKE = 0xFFFF6FD8;
}
