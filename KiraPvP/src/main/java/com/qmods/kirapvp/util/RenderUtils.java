package com.qmods.kirapvp.util;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.ScaledResolution;

/**
 * Thin, allocation-free wrappers around the vanilla drawing primitives used
 * by the HUD and ClickGUI, so every renderer shares one code path instead of
 * duplicating GL state juggling.
 */
public final class RenderUtils {

    private static ScaledResolution scaledResolution;

    private RenderUtils() {
    }

    /**
     * Refreshed exactly once per rendered frame by the central overlay
     * dispatcher, so every HUD element reads the same instance instead of
     * each allocating its own {@link ScaledResolution}.
     */
    public static void refreshScaledResolution(Minecraft mc) {
        scaledResolution = new ScaledResolution(mc);
    }

    public static ScaledResolution scaledResolution() {
        return scaledResolution;
    }

    public static FontRenderer font() {
        return Minecraft.getMinecraft().fontRendererObj;
    }

    public static int drawStringShadow(String text, float x, float y, int argb) {
        return font().drawStringWithShadow(text, x, y, argb);
    }

    public static int drawString(String text, float x, float y, int argb) {
        return font().drawString(text, (int) x, (int) y, argb);
    }

    public static int stringWidth(String text) {
        return font().getStringWidth(text);
    }

    public static int fontHeight() {
        return font().FONT_HEIGHT;
    }

    public static void rect(double x, double y, double width, double height, int argb) {
        Gui.drawRect((int) x, (int) y, (int) (x + width), (int) (y + height), argb);
    }

    public static void border(double x, double y, double width, double height, double thickness, int argb) {
        rect(x, y, width, thickness, argb);
        rect(x, y + height - thickness, width, thickness, argb);
        rect(x, y, thickness, height, argb);
        rect(x + width - thickness, y, thickness, height, argb);
    }

    public static int argb(int alpha, int red, int green, int blue) {
        return (alpha << 24) | (red << 16) | (green << 8) | blue;
    }

    public static int withAlpha(int rgb, int alpha) {
        return (alpha << 24) | (rgb & 0x00FFFFFF);
    }
}
