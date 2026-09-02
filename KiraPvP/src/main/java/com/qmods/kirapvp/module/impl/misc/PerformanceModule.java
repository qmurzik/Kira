package com.qmods.kirapvp.module.impl.misc;

import com.qmods.kirapvp.core.Category;
import com.qmods.kirapvp.core.Module;
import com.qmods.kirapvp.keybind.Keybind;
import com.qmods.kirapvp.settings.BooleanSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.GameSettings;

import java.util.Arrays;

/**
 * Applies a handful of safe, fully reversible vanilla render-quality
 * trade-offs while enabled, restoring the exact previous values on disable.
 * This does not touch chunk building, entity culling or any other deeper
 * render-engine internals - that would need a coremod/ASM to hook safely;
 * this module intentionally sticks to the same options already exposed
 * (just automated) via vanilla's own Video Settings screen.
 */
public final class PerformanceModule extends Module {

    private final BooleanSetting disableClouds;
    private final BooleanSetting disableFancyGraphics;
    private final BooleanSetting disableAmbientOcclusion;
    private final BooleanSetting reduceParticles;

    private int savedClouds;
    private boolean savedFancy;
    private int savedAo;
    private int savedParticles;
    private boolean savedApplied;

    public PerformanceModule() {
        this(new BooleanSetting("Disable Clouds", true), new BooleanSetting("Fast Graphics", true),
                new BooleanSetting("Disable AO", true), new BooleanSetting("Minimal Particles", true));
    }

    private PerformanceModule(BooleanSetting disableClouds, BooleanSetting disableFancyGraphics,
                               BooleanSetting disableAmbientOcclusion, BooleanSetting reduceParticles) {
        super("Performance", "Applies safe render trade-offs for stable FPS", Category.MISC, Keybind.none(),
                Arrays.<com.qmods.kirapvp.settings.Setting<?>>asList(
                        disableClouds, disableFancyGraphics, disableAmbientOcclusion, reduceParticles));
        this.disableClouds = disableClouds;
        this.disableFancyGraphics = disableFancyGraphics;
        this.disableAmbientOcclusion = disableAmbientOcclusion;
        this.reduceParticles = reduceParticles;
    }

    @Override
    public void onEnable() {
        GameSettings settings = Minecraft.getMinecraft().gameSettings;
        savedClouds = settings.clouds;
        savedFancy = settings.fancyGraphics;
        savedAo = settings.ambientOcclusion;
        savedParticles = settings.particleSetting;
        savedApplied = true;

        if (disableClouds.getValue()) {
            settings.clouds = 0;
        }
        if (disableFancyGraphics.getValue()) {
            settings.fancyGraphics = false;
        }
        if (disableAmbientOcclusion.getValue()) {
            settings.ambientOcclusion = 0;
        }
        if (reduceParticles.getValue()) {
            settings.particleSetting = 2;
        }
    }

    @Override
    public void onDisable() {
        if (!savedApplied) {
            return;
        }
        GameSettings settings = Minecraft.getMinecraft().gameSettings;
        settings.clouds = savedClouds;
        settings.fancyGraphics = savedFancy;
        settings.ambientOcclusion = savedAo;
        settings.particleSetting = savedParticles;
        savedApplied = false;
    }
}
