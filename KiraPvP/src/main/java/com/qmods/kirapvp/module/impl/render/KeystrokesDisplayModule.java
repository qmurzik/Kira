package com.qmods.kirapvp.module.impl.render;

import com.qmods.kirapvp.core.Category;
import com.qmods.kirapvp.core.Module;
import com.qmods.kirapvp.keybind.Keybind;
import com.qmods.kirapvp.util.RenderUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.settings.GameSettings;
import org.lwjgl.input.Mouse;

/**
 * Minimalist WASD + mouse-button keystroke HUD. Every value drawn here is a
 * plain boolean read from vanilla's own {@link GameSettings} key bindings or
 * LWJGL's mouse state - no allocation, no caching needed since there is
 * nothing expensive to cache: it is just a handful of colored rectangles and
 * four constant single-character labels.
 */
public final class KeystrokesDisplayModule extends Module {

    private static final int BOX = 18;
    private static final int GAP = 2;

    private static final String LABEL_W = "W";
    private static final String LABEL_A = "A";
    private static final String LABEL_S = "S";
    private static final String LABEL_D = "D";
    private static final String LABEL_LMB = "L";
    private static final String LABEL_RMB = "R";

    public KeystrokesDisplayModule() {
        super("Keystrokes", "Shows WASD and mouse button state", Category.RENDER, Keybind.none());
    }

    @Override
    public void onRenderOverlay(float partialTicks) {
        ScaledResolution res = RenderUtils.scaledResolution();
        if (res == null) {
            return;
        }
        GameSettings settings = Minecraft.getMinecraft().gameSettings;

        int baseX = res.getScaledWidth() - (BOX * 3 + GAP * 2) - 8;
        int baseY = res.getScaledHeight() - (BOX * 2 + GAP) - 8;

        drawKey(baseX + BOX + GAP, baseY, BOX, settings.keyBindForward.isKeyDown(), LABEL_W);
        drawKey(baseX, baseY + BOX + GAP, BOX, settings.keyBindLeft.isKeyDown(), LABEL_A);
        drawKey(baseX + BOX + GAP, baseY + BOX + GAP, BOX, settings.keyBindBack.isKeyDown(), LABEL_S);
        drawKey(baseX + (BOX + GAP) * 2, baseY + BOX + GAP, BOX, settings.keyBindRight.isKeyDown(), LABEL_D);

        int mouseY = baseY + (BOX + GAP) * 2 + GAP;
        drawKey(baseX, mouseY, BOX, Mouse.isButtonDown(0), LABEL_LMB);
        drawKey(baseX + (BOX + GAP) * 2, mouseY, BOX, Mouse.isButtonDown(1), LABEL_RMB);
    }

    private void drawKey(int x, int y, int size, boolean active, String label) {
        int bg = active ? RenderUtils.argb(200, 60, 130, 246) : RenderUtils.argb(140, 20, 20, 24);
        RenderUtils.rect(x, y, size, size, bg);
        int textX = x + size / 2 - RenderUtils.stringWidth(label) / 2;
        int textY = y + size / 2 - RenderUtils.fontHeight() / 2;
        RenderUtils.drawStringShadow(label, textX, textY, 0xFFFFFFFF);
    }
}
