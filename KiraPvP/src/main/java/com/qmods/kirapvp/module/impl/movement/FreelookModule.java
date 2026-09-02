package com.qmods.kirapvp.module.impl.movement;

import com.qmods.kirapvp.core.Category;
import com.qmods.kirapvp.core.Module;
import com.qmods.kirapvp.keybind.Keybind;
import com.qmods.kirapvp.settings.SliderSetting;
import com.qmods.kirapvp.util.MathUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraftforge.client.event.EntityViewRenderEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.lwjgl.input.Keyboard;

import java.util.Arrays;

/**
 * Decouples the rendered camera from the player's actual facing direction.
 *
 * <h2>Why this approach (no ASM/mixin)</h2>
 * A "true" freelook that steals the raw mouse delta before vanilla applies it
 * would need to run between {@code Minecraft.runTick()}'s internal mouse
 * handling and its consumption of {@code Mouse.getDX()/getDY()} - a point
 * with no public Forge event in 1.8.9 (this is why historical freelook mods
 * for this era used a coremod). Fighting the raw input is unnecessary here
 * though: vanilla's own per-frame mouse handling already turns mouse motion
 * into a change of {@code rotationYaw}/{@code rotationPitch}. Instead of
 * intercepting the input, this module *observes* that change every render
 * frame ({@link TickEvent.RenderTickEvent}, camera-accurate, allocation-free)
 * and accumulates it into independent {@code freelookYaw}/{@code freelookPitch}
 * fields used only for rendering (via {@link EntityViewRenderEvent.CameraSetup},
 * the exact hook vanilla itself uses to orient the render camera in both first
 * and third person). Once per tick ({@link TickEvent.ClientTickEvent}, before
 * that tick's movement is processed) the real {@code rotationYaw/Pitch} is
 * snapped back to the locked facing captured when Freelook was enabled, so
 * WASD movement and interaction stay tied to where the body actually faces -
 * exactly like vanilla, just visually decoupled. The bookkeeping baseline is
 * re-synced at the same instant, so the periodic snap never shows up as a
 * false jump in the accumulated camera angles: the camera itself is never
 * touched between ticks, so it stays perfectly smooth.
 */
public final class FreelookModule extends Module {

    private final SliderSetting sensitivity = new SliderSetting("Sensitivity", 1.0, 0.1, 3.0, 0.05, "x");
    private final SliderSetting pitchLimit = new SliderSetting("Pitch Limit", 90, 45, 90, 1, "°");

    private float lockedYaw;
    private float lockedPitch;
    private float freelookYaw;
    private float freelookPitch;
    private float lastObservedYaw;
    private float lastObservedPitch;

    public FreelookModule() {
        super("Freelook", "Look around independently of your movement direction",
                Category.MOVEMENT, Keybind.key(Keyboard.KEY_C),
                Arrays.<com.qmods.kirapvp.settings.Setting<?>>asList(sensitivity, pitchLimit));
    }

    @Override
    public void onEnable() {
        syncToCurrentRotation();
        MinecraftForge.EVENT_BUS.register(this);
        FMLCommonHandler.instance().bus().register(this);
    }

    @Override
    public void onDisable() {
        MinecraftForge.EVENT_BUS.unregister(this);
        FMLCommonHandler.instance().bus().unregister(this);
    }

    @Override
    public void onReset() {
        if (isEnabled()) {
            syncToCurrentRotation();
        }
    }

    private void syncToCurrentRotation() {
        EntityPlayerSP player = Minecraft.getMinecraft().thePlayer;
        if (player == null) {
            return;
        }
        lockedYaw = player.rotationYaw;
        lockedPitch = player.rotationPitch;
        freelookYaw = lockedYaw;
        freelookPitch = lockedPitch;
        lastObservedYaw = lockedYaw;
        lastObservedPitch = lockedPitch;
    }

    @SubscribeEvent
    public void onRenderTick(TickEvent.RenderTickEvent event) {
        if (event.phase != TickEvent.Phase.START) {
            return;
        }
        EntityPlayerSP player = Minecraft.getMinecraft().thePlayer;
        if (player == null) {
            return;
        }

        float sens = (float) sensitivity.getValue();
        float deltaYaw = (float) MathUtil.wrapDegrees(player.rotationYaw - lastObservedYaw);
        float deltaPitch = player.rotationPitch - lastObservedPitch;

        freelookYaw += deltaYaw * sens;
        float limit = (float) pitchLimit.getValue();
        freelookPitch = MathUtil.clamp(freelookPitch + deltaPitch * sens, -limit, limit);

        lastObservedYaw = player.rotationYaw;
        lastObservedPitch = player.rotationPitch;
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.START) {
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();
        EntityPlayerSP player = mc.thePlayer;
        if (player == null || mc.currentScreen != null) {
            return;
        }

        player.rotationYaw = lockedYaw;
        player.rotationPitch = lockedPitch;
        player.prevRotationYaw = lockedYaw;
        player.prevRotationPitch = lockedPitch;

        lastObservedYaw = lockedYaw;
        lastObservedPitch = lockedPitch;
    }

    @SubscribeEvent
    public void onCameraSetup(EntityViewRenderEvent.CameraSetup event) {
        event.yaw = freelookYaw;
        event.pitch = freelookPitch;
    }
}
