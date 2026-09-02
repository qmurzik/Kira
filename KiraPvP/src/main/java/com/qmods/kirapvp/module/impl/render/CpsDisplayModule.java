package com.qmods.kirapvp.module.impl.render;

import com.qmods.kirapvp.core.Category;
import com.qmods.kirapvp.core.Module;
import com.qmods.kirapvp.keybind.Keybind;
import com.qmods.kirapvp.util.ClickTracker;
import com.qmods.kirapvp.util.RenderUtils;
import org.lwjgl.input.Mouse;

/**
 * Click-per-second counter for both mouse buttons. Click edges are detected
 * once per rendered frame (not per tick) so fast clicks are never missed,
 * and each button's recent clicks live in a fixed-size {@link ClickTracker}
 * ring buffer - no growing lists, no per-click allocation.
 */
public final class CpsDisplayModule extends Module {

    private final ClickTracker leftClicks = new ClickTracker();
    private final ClickTracker rightClicks = new ClickTracker();

    private boolean wasLeftDown;
    private boolean wasRightDown;

    private int lastLeftCps = -1;
    private int lastRightCps = -1;
    private String leftLabel = "LMB: 0";
    private String rightLabel = "RMB: 0";

    public CpsDisplayModule() {
        super("CPS Display", "Shows clicks per second for both mouse buttons", Category.RENDER, Keybind.none());
    }

    @Override
    public void onEnable() {
        wasLeftDown = Mouse.isButtonDown(0);
        wasRightDown = Mouse.isButtonDown(1);
    }

    @Override
    public void onRenderOverlay(float partialTicks) {
        long now = System.currentTimeMillis();

        boolean leftDown = Mouse.isButtonDown(0);
        if (leftDown && !wasLeftDown) {
            leftClicks.recordClick(now);
        }
        wasLeftDown = leftDown;

        boolean rightDown = Mouse.isButtonDown(1);
        if (rightDown && !wasRightDown) {
            rightClicks.recordClick(now);
        }
        wasRightDown = rightDown;

        int leftCps = leftClicks.getCps(now);
        if (leftCps != lastLeftCps) {
            lastLeftCps = leftCps;
            leftLabel = "LMB: " + leftCps;
        }
        int rightCps = rightClicks.getCps(now);
        if (rightCps != lastRightCps) {
            lastRightCps = rightCps;
            rightLabel = "RMB: " + rightCps;
        }

        RenderUtils.drawStringShadow(leftLabel, 4, 40, 0xFFFFFFFF);
        RenderUtils.drawStringShadow(rightLabel, 4, 52, 0xFFFFFFFF);
    }
}
