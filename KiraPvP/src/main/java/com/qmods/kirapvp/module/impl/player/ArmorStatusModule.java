package com.qmods.kirapvp.module.impl.player;

import com.qmods.kirapvp.core.Category;
import com.qmods.kirapvp.core.Module;
import com.qmods.kirapvp.keybind.Keybind;
import com.qmods.kirapvp.util.RenderUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.item.ItemStack;

/**
 * Surfaces armor durability that is already visible by opening the
 * inventory - purely informational, no automation or advantage.
 */
public final class ArmorStatusModule extends Module {

    private static final String[] SLOT_LABELS = {"Boots", "Legs", "Chest", "Helmet"};

    public ArmorStatusModule() {
        super("Armor Status", "Shows equipped armor durability", Category.PLAYER, Keybind.none());
    }

    @Override
    public void onRenderOverlay(float partialTicks) {
        EntityPlayerSP player = Minecraft.getMinecraft().thePlayer;
        ScaledResolution res = RenderUtils.scaledResolution();
        if (player == null || res == null) {
            return;
        }

        ItemStack[] armor = player.inventory.armorInventory;
        int x = 4;
        int y = res.getScaledHeight() - 70;

        for (int i = armor.length - 1; i >= 0; i--) {
            ItemStack stack = armor[i];
            if (stack == null || !stack.isItemStackDamageable()) {
                continue;
            }
            double fraction = 1.0 - (stack.getItemDamage() / (double) stack.getMaxDamage());
            int barColor = fraction > 0.5 ? 0xFF4ADE80 : (fraction > 0.2 ? 0xFFFACC15 : 0xFFEF4444);

            String label = SLOT_LABELS[i] + ": " + (int) Math.round(fraction * 100) + "%";
            RenderUtils.drawStringShadow(label, x, y, 0xFFFFFFFF);
            RenderUtils.rect(x, y + 10, 60, 2, 0x55000000);
            RenderUtils.rect(x, y + 10, 60 * fraction, 2, barColor);
            y += 14;
        }
    }
}
