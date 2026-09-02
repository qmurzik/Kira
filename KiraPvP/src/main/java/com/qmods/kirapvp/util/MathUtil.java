package com.qmods.kirapvp.util;

public final class MathUtil {

    private MathUtil() {
    }

    public static float clamp(float value, float min, float max) {
        return value < min ? min : (Math.min(value, max));
    }

    public static double clamp(double value, double min, double max) {
        return value < min ? min : (Math.min(value, max));
    }

    public static float lerp(float start, float end, float progress) {
        return start + (end - start) * progress;
    }

    /** Quadratic ease-out. Cheap and allocation-free; used for GUI open/close and slide-in animations. */
    public static float easeOutQuad(float t) {
        float clamped = clamp(t, 0f, 1f);
        return 1f - (1f - clamped) * (1f - clamped);
    }

    public static double wrapDegrees(double value) {
        double result = value % 360.0;
        if (result >= 180.0) {
            result -= 360.0;
        }
        if (result < -180.0) {
            result += 360.0;
        }
        return result;
    }
}
