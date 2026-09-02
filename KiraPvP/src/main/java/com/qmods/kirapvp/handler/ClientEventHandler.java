package com.qmods.kirapvp.handler;

import com.qmods.kirapvp.config.ConfigManager;
import com.qmods.kirapvp.core.ModuleManager;
import com.qmods.kirapvp.hud.HudRenderer;
import com.qmods.kirapvp.keybind.KeybindManager;
import net.minecraft.client.Minecraft;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.world.WorldEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.common.network.FMLNetworkEvent;

/**
 * The single object registered on both Forge event buses. Every hot-path
 * hook (tick, render overlay) is a thin, allocation-free dispatch into the
 * relevant manager; every lifecycle hook (world load/unload, disconnect,
 * death, respawn) resets transient module state so nothing carries stale
 * data - and, in particular, nothing referencing the old world/player -
 * across those transitions.
 */
public final class ClientEventHandler {

    private final ModuleManager moduleManager;
    private final KeybindManager keybindManager;
    private final ConfigManager configManager;
    private final HudRenderer hudRenderer;

    public ClientEventHandler(ModuleManager moduleManager, KeybindManager keybindManager,
                               ConfigManager configManager, HudRenderer hudRenderer) {
        this.moduleManager = moduleManager;
        this.keybindManager = keybindManager;
        this.configManager = configManager;
        this.hudRenderer = hudRenderer;
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.START) {
            return;
        }
        keybindManager.onTick();
        moduleManager.tickEnabled();
        configManager.onTick();
    }

    @SubscribeEvent
    public void onRenderOverlay(RenderGameOverlayEvent.Post event) {
        hudRenderer.onRenderOverlay(event);
    }

    @SubscribeEvent
    public void onWorldLoad(WorldEvent.Load event) {
        if (event.world.isRemote) {
            moduleManager.resetAll();
        }
    }

    @SubscribeEvent
    public void onWorldUnload(WorldEvent.Unload event) {
        if (event.world.isRemote) {
            moduleManager.resetAll();
        }
    }

    @SubscribeEvent
    public void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (isClientPlayer(event.player)) {
            moduleManager.resetAll();
        }
    }

    @SubscribeEvent
    public void onLivingDeath(LivingDeathEvent event) {
        if (isClientPlayer(event.entity)) {
            moduleManager.resetAll();
        }
    }

    @SubscribeEvent
    public void onDisconnect(FMLNetworkEvent.ClientDisconnectionFromServerEvent event) {
        moduleManager.resetAll();
        configManager.flushIfDirty();
    }

    private boolean isClientPlayer(net.minecraft.entity.Entity entity) {
        Minecraft mc = Minecraft.getMinecraft();
        return mc.thePlayer != null && entity == mc.thePlayer;
    }
}
