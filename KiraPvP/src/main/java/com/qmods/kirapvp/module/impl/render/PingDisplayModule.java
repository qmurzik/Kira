package com.qmods.kirapvp.module.impl.render;

import com.qmods.kirapvp.core.Category;
import com.qmods.kirapvp.core.Module;
import com.qmods.kirapvp.keybind.Keybind;
import com.qmods.kirapvp.util.RenderUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.network.NetHandlerPlayClient;
import net.minecraft.client.network.NetworkPlayerInfo;

/**
 * Shows the player's reported ping. The server only updates ping a few
 * times per second internally, so this module reads it at most once per
 * second (throttled on the client tick) and reuses that cached label for
 * every rendered frame in between - never touches the network itself.
 */
public final class PingDisplayModule extends Module {

    private static final int UPDATE_INTERVAL_TICKS = 20;

    private int ticksSinceUpdate = UPDATE_INTERVAL_TICKS;
    private String cachedLabel = "Ping: --";

    public PingDisplayModule() {
        super("Ping Display", "Shows your connection latency", Category.RENDER, Keybind.none());
    }

    @Override
    public void onTick() {
        ticksSinceUpdate++;
        if (ticksSinceUpdate < UPDATE_INTERVAL_TICKS) {
            return;
        }
        ticksSinceUpdate = 0;

        Minecraft mc = Minecraft.getMinecraft();
        EntityPlayerSP player = mc.thePlayer;
        if (player == null) {
            return;
        }
        NetHandlerPlayClient netHandler = player.sendQueue;
        if (netHandler == null) {
            return;
        }
        NetworkPlayerInfo info = netHandler.getPlayerInfo(player.getName());
        if (info == null) {
            return;
        }
        cachedLabel = "Ping: " + info.getResponseTime() + "ms";
    }

    @Override
    public void onRenderOverlay(float partialTicks) {
        RenderUtils.drawStringShadow(cachedLabel, 4, 28, 0xFFFFFFFF);
    }
}
