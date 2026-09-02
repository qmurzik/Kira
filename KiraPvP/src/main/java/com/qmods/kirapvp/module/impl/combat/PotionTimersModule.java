package com.qmods.kirapvp.module.impl.combat;

import com.qmods.kirapvp.core.Category;
import com.qmods.kirapvp.core.Module;
import com.qmods.kirapvp.keybind.Keybind;
import com.qmods.kirapvp.util.RenderUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.StatCollector;

import java.util.Collection;

/**
 * Shows remaining durations of active potion effects - the same information
 * already visible via the vanilla inventory overlay, just always on screen.
 * Formatting only happens once per client tick (20/s), never per frame: the
 * render call just blits the strings built last tick.
 */
public final class PotionTimersModule extends Module {

    private static final String[] EMPTY = new String[0];

    private String[] cachedLines = EMPTY;

    public PotionTimersModule() {
        super("Potion Timers", "Shows remaining duration of active potion effects", Category.COMBAT, Keybind.none());
    }

    @Override
    public void onTick() {
        EntityPlayerSP player = Minecraft.getMinecraft().thePlayer;
        if (player == null) {
            cachedLines = EMPTY;
            return;
        }
        Collection<PotionEffect> effects = player.getActivePotionEffects();
        if (effects.isEmpty()) {
            cachedLines = EMPTY;
            return;
        }
        String[] lines = new String[effects.size()];
        int i = 0;
        for (PotionEffect effect : effects) {
            Potion potion = Potion.potionTypes[effect.getPotionID()];
            String name = potion == null ? "Effect" : StatCollector.translateToLocal(potion.getName()).trim();
            int seconds = effect.getDuration() / 20;
            lines[i++] = name + " " + (effect.getAmplifier() + 1) + " " + (seconds / 60) + ":" + String.format("%02d", seconds % 60);
        }
        cachedLines = lines;
    }

    @Override
    public void onRenderOverlay(float partialTicks) {
        if (cachedLines.length == 0) {
            return;
        }
        ScaledResolution res = RenderUtils.scaledResolution();
        if (res == null) {
            return;
        }
        int x = res.getScaledWidth() - 4;
        int y = 4;
        for (String line : cachedLines) {
            int width = RenderUtils.stringWidth(line);
            RenderUtils.drawStringShadow(line, x - width, y, 0xFFFFFFFF);
            y += RenderUtils.fontHeight() + 2;
        }
    }
}
