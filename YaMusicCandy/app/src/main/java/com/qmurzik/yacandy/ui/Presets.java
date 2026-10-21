package com.qmurzik.yacandy.ui;

import com.qmurzik.yacandy.Keys;

import java.util.HashMap;
import java.util.Map;

/** Ready-made looks. Each preset is a full set of color/shape values. */
final class Presets {
    private Presets() {
    }

    static final String[] NAMES = {"Оригинал", "Карамель", "Мята", "Океан", "Лаванда", "Закат", "AMOLED"};
    static final int[] SWATCH = {0xFFFFDB4D, 0xFFFF6FB5, 0xFF3DDC97, 0xFF4FC3F7, 0xFFB388FF, 0xFFFF8A50, 0xFF000000};

    static Map<String, Object> values(int index) {
        Map<String, Object> v = new HashMap<>();
        if (index == 0) {
            v.put(Keys.COLORS_ON, false);
            v.put(Keys.CORNERS_ON, false);
            v.put(Keys.SATURATION, 100);
            v.put(Keys.HUE_SHIFT, 0);
            return v;
        }
        v.put(Keys.COLORS_ON, true);
        v.put(Keys.HUE_SHIFT, 0);
        v.put(Keys.TEXT_ON, false);
        switch (index) {
            case 1: candy(v, 0xFFFF6FB5, 0xFF1A1020, 0xFFFFF3F8, 115, 150); break;
            case 2: candy(v, 0xFF3DDC97, 0xFF0E1A16, 0xFFF1FFF8, 110, 130); break;
            case 3: candy(v, 0xFF4FC3F7, 0xFF0B1622, 0xFFF0F8FF, 110, 120); break;
            case 4: candy(v, 0xFFB388FF, 0xFF15111F, 0xFFF7F2FF, 110, 150); break;
            case 5: candy(v, 0xFFFF8A50, 0xFF1E1210, 0xFFFFF6F0, 115, 120); break;
            default:
                v.put(Keys.ACCENT_ON, false);
                v.put(Keys.DARK_BG_ON, true);
                v.put(Keys.DARK_BG_COLOR, 0xFF000000);
                v.put(Keys.LIGHT_BG_ON, false);
                v.put(Keys.SATURATION, 100);
                v.put(Keys.CORNERS_ON, false);
                break;
        }
        return v;
    }

    private static void candy(Map<String, Object> v, int accent, int darkBg, int lightBg, int sat, int corners) {
        v.put(Keys.ACCENT_ON, true);
        v.put(Keys.ACCENT_COLOR, accent);
        v.put(Keys.DARK_BG_ON, true);
        v.put(Keys.DARK_BG_COLOR, darkBg);
        v.put(Keys.LIGHT_BG_ON, true);
        v.put(Keys.LIGHT_BG_COLOR, lightBg);
        v.put(Keys.SATURATION, sat);
        v.put(Keys.CORNERS_ON, true);
        v.put(Keys.CORNER_FACTOR, corners);
    }
}
