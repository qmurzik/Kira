package com.qmurzik.yacandy.ui;

import com.qmurzik.yacandy.Keys;

import java.util.HashMap;
import java.util.Map;

/** Ready-made looks. Each preset is a full palette: accent, like, backgrounds, surfaces, text. */
final class Presets {
    private Presets() {
    }

    static final String[] NAMES = {"Оригинал", "Yume", "Карамель", "Мята", "Океан", "Закат", "Графит", "AMOLED"};
    static final int[] SWATCH = {0xFFFFDB4D, 0xFF9D7BFF, 0xFFFF6FB5, 0xFF3DDC97, 0xFF4FC3F7, 0xFFFF8A50,
            0xFFB0BEC5, 0xFF000000};

    // accent, like, dark bg, dark surface, light bg, light surface, text on dark, text on light
    private static final int[][] PALETTES = {
            null,
            {0xFF9D7BFF, 0xFFFF6FD8, 0xFF0B0A1A, 0xFF17142B, 0xFFF7F5FF, 0xFFECE7FB, 0xFFEDE7FF, 0xFF1B1530},
            {0xFFFF6FB5, 0xFFFF4F8B, 0xFF1A1020, 0xFF2A1A31, 0xFFFFF3F8, 0xFFF9E3EE, 0xFFFFE9F4, 0xFF2A1630},
            {0xFF3DDC97, 0xFFFF6B6B, 0xFF0B1512, 0xFF15241F, 0xFFF1FFF8, 0xFFE0F4EA, 0xFFE6FFF4, 0xFF0F2A1F},
            {0xFF4FC3F7, 0xFFFF6B8A, 0xFF08111C, 0xFF12202F, 0xFFF0F8FF, 0xFFE0EDF8, 0xFFE6F4FF, 0xFF0D2236},
            {0xFFFF8A50, 0xFFFF4F6D, 0xFF1A0F0C, 0xFF2B1A15, 0xFFFFF6F0, 0xFFF8E6DC, 0xFFFFEDE4, 0xFF33190F},
            {0xFFB0BEC5, 0xFFEF5350, 0xFF121417, 0xFF1E2226, 0xFFF5F6F7, 0xFFE8EAEC, 0xFFE8EBEE, 0xFF1E2226},
            null,
    };

    /** accent, like, dark bg, dark surface, light bg, light surface, text on dark, text on light; null = stock. */
    static int[] palette(int index) {
        return PALETTES[index];
    }

    static Map<String, Object> values(int index) {
        Map<String, Object> v = new HashMap<>();
        v.put(Keys.HUE_SHIFT, 0);
        v.put(Keys.SATURATION, 100);
        v.put(Keys.ON_ACCENT, "auto");
        v.put(Keys.PRESET, index);
        if (index == 0) {
            v.put(Keys.COLORS_ON, false);
            v.put(Keys.CORNERS_ON, false);
            return v;
        }
        v.put(Keys.COLORS_ON, true);
        if (index == NAMES.length - 1) {
            // AMOLED: pure black everywhere, original accent.
            v.put(Keys.ACCENT_ON, false);
            v.put(Keys.LIKE_ON, false);
            v.put(Keys.DARK_BG_ON, true);
            v.put(Keys.DARK_BG_COLOR, 0xFF000000);
            v.put(Keys.DARK_SURFACE_COLOR, 0xFF0A0A0A);
            v.put(Keys.LIGHT_BG_ON, false);
            v.put(Keys.TEXT_ON, false);
            v.put(Keys.CORNERS_ON, false);
            return v;
        }
        int[] p = PALETTES[index];
        v.put(Keys.ACCENT_ON, true);
        v.put(Keys.ACCENT_COLOR, p[0]);
        v.put(Keys.LIKE_ON, true);
        v.put(Keys.LIKE_COLOR, p[1]);
        v.put(Keys.DARK_BG_ON, true);
        v.put(Keys.DARK_BG_COLOR, p[2]);
        v.put(Keys.DARK_SURFACE_COLOR, p[3]);
        v.put(Keys.LIGHT_BG_ON, true);
        v.put(Keys.LIGHT_BG_COLOR, p[4]);
        v.put(Keys.LIGHT_SURFACE_COLOR, p[5]);
        v.put(Keys.TEXT_ON, true);
        v.put(Keys.TEXT_DARK_COLOR, p[6]);
        v.put(Keys.TEXT_LIGHT_COLOR, p[7]);
        v.put(Keys.CORNERS_ON, true);
        v.put(Keys.CORNER_FACTOR, index == 1 ? 140 : 120);
        return v;
    }
}
