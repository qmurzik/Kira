package com.qmods.kirapvp.module.impl.render;

import com.qmods.kirapvp.core.Category;
import com.qmods.kirapvp.core.Module;
import com.qmods.kirapvp.keybind.Keybind;
import com.qmods.kirapvp.util.RenderUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;

/**
 * Shows the player's block position. The label string is only rebuilt when
 * the rounded coordinates actually change, and that check runs on the
 * client tick (20/s) rather than the render loop (up to hundreds/s).
 */
public final class CoordinatesDisplayModule extends Module {

    private int lastX = Integer.MIN_VALUE;
    private int lastY = Integer.MIN_VALUE;
    private int lastZ = Integer.MIN_VALUE;
    private String cachedLabel = "XYZ: 0 0 0";

    public CoordinatesDisplayModule() {
        super("Coordinates", "Shows your current block position", Category.RENDER, Keybind.none());
    }

    @Override
    public void onTick() {
        EntityPlayerSP player = Minecraft.getMinecraft().thePlayer;
        if (player == null) {
            return;
        }
        int x = (int) Math.floor(player.posX);
        int y = (int) Math.floor(player.posY);
        int z = (int) Math.floor(player.posZ);
        if (x != lastX || y != lastY || z != lastZ) {
            lastX = x;
            lastY = y;
            lastZ = z;
            cachedLabel = "XYZ: " + x + " " + y + " " + z;
        }
    }

    @Override
    public void onRenderOverlay(float partialTicks) {
        RenderUtils.drawStringShadow(cachedLabel, 4, 16, 0xFFFFFFFF);
    }
}
