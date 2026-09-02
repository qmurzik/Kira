package com.qmods.kirapvp.module.impl.render;

import com.qmods.kirapvp.core.Category;
import com.qmods.kirapvp.core.Module;
import com.qmods.kirapvp.keybind.Keybind;
import com.qmods.kirapvp.settings.SliderSetting;
import net.minecraft.client.Minecraft;
import org.lwjgl.input.Keyboard;

import java.util.Collections;

/**
 * Hold-to-zoom. Modeled as a plain hold-while-enabled module: the bound key
 * is registered as a HOLD action (enable on press, disable on release), so
 * {@link #isEnabled()} doubles as "currently zoomed in", with no separate
 * state to track. Zooms by scaling the vanilla FOV setting directly and
 * restoring it on release - deliberately avoids guessing at a Forge FOV
 * event API, since {@code gameSettings.fovSetting} is a plain, stable
 * vanilla field present unchanged since 1.8.
 */
public final class ZoomModule extends Module {

    private final SliderSetting zoomFov = new SliderSetting("Zoom FOV", 20, 5, 50, 1, "°");

    private float savedFov;

    public ZoomModule() {
        super("Zoom", "Hold to zoom in", Category.RENDER, Keybind.key(Keyboard.KEY_Z),
                Collections.<com.qmods.kirapvp.settings.Setting<?>>singletonList(zoomFov));
    }

    @Override
    public void onEnable() {
        Minecraft mc = Minecraft.getMinecraft();
        savedFov = mc.gameSettings.fovSetting;
        mc.gameSettings.fovSetting = (float) zoomFov.getValue();
    }

    @Override
    public void onDisable() {
        Minecraft.getMinecraft().gameSettings.fovSetting = savedFov;
    }

    @Override
    public void onReset() {
        if (isEnabled()) {
            onDisable();
        }
    }

    @Override
    public boolean isHoldToActivate() {
        return true;
    }
}
