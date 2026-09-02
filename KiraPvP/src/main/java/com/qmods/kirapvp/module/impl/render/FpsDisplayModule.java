package com.qmods.kirapvp.module.impl.render;

import com.qmods.kirapvp.core.Category;
import com.qmods.kirapvp.core.Module;
import com.qmods.kirapvp.keybind.Keybind;
import com.qmods.kirapvp.util.RenderUtils;

/**
 * Self-counted FPS: increments once per rendered frame and recomputes the
 * displayed value (and its cached label string) at most twice a second,
 * instead of formatting a new string on every frame.
 */
public final class FpsDisplayModule extends Module {

    private static final long UPDATE_INTERVAL_MS = 500L;

    private int frameCount;
    private long windowStartMs;
    private String cachedLabel = "FPS: 0";

    public FpsDisplayModule() {
        super("FPS Display", "Shows current frames per second", Category.RENDER, Keybind.none());
    }

    @Override
    public void onEnable() {
        frameCount = 0;
        windowStartMs = System.currentTimeMillis();
    }

    @Override
    public void onRenderOverlay(float partialTicks) {
        frameCount++;
        long now = System.currentTimeMillis();
        long elapsed = now - windowStartMs;
        if (elapsed >= UPDATE_INTERVAL_MS) {
            int fps = (int) (frameCount * 1000L / elapsed);
            cachedLabel = "FPS: " + fps;
            frameCount = 0;
            windowStartMs = now;
        }
        RenderUtils.drawStringShadow(cachedLabel, 4, 4, 0xFFFFFFFF);
    }
}
