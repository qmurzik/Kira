package com.qmods.kirapvp.hud;

import com.qmods.kirapvp.core.ModuleManager;
import com.qmods.kirapvp.util.RenderUtils;
import net.minecraft.client.Minecraft;
import net.minecraftforge.client.event.RenderGameOverlayEvent;

/**
 * Single entry point for everything drawn on top of the game each frame.
 * Computes the shared {@link net.minecraft.client.gui.ScaledResolution}
 * exactly once per frame (instead of once per HUD element) before
 * dispatching to enabled modules and the notification stack.
 */
public final class HudRenderer {

    private final ModuleManager moduleManager;
    private final NotificationManager notificationManager;

    public HudRenderer(ModuleManager moduleManager, NotificationManager notificationManager) {
        this.moduleManager = moduleManager;
        this.notificationManager = notificationManager;
    }

    public void onRenderOverlay(RenderGameOverlayEvent.Post event) {
        if (event.type != RenderGameOverlayEvent.ElementType.ALL) {
            return;
        }
        RenderUtils.refreshScaledResolution(Minecraft.getMinecraft());
        moduleManager.renderEnabled(event.partialTicks);
        notificationManager.render();
    }
}
