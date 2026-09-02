package com.qmods.kirapvp;

import com.qmods.kirapvp.config.ConfigManager;
import com.qmods.kirapvp.config.GuiState;
import com.qmods.kirapvp.core.EventBus;
import com.qmods.kirapvp.core.Module;
import com.qmods.kirapvp.core.ModuleManager;
import com.qmods.kirapvp.gui.clickgui.ClickGui;
import com.qmods.kirapvp.handler.ClientEventHandler;
import com.qmods.kirapvp.hud.HudRenderer;
import com.qmods.kirapvp.hud.NotificationManager;
import com.qmods.kirapvp.keybind.Keybind;
import com.qmods.kirapvp.keybind.KeybindManager;
import com.qmods.kirapvp.module.impl.combat.PotionTimersModule;
import com.qmods.kirapvp.module.impl.misc.PerformanceModule;
import com.qmods.kirapvp.module.impl.movement.FreelookModule;
import com.qmods.kirapvp.module.impl.player.ArmorStatusModule;
import com.qmods.kirapvp.module.impl.render.CoordinatesDisplayModule;
import com.qmods.kirapvp.module.impl.render.CpsDisplayModule;
import com.qmods.kirapvp.module.impl.render.FpsDisplayModule;
import com.qmods.kirapvp.module.impl.render.KeystrokesDisplayModule;
import com.qmods.kirapvp.module.impl.render.PingDisplayModule;
import com.qmods.kirapvp.module.impl.render.ZoomModule;
import net.minecraft.client.Minecraft;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lwjgl.input.Keyboard;

import java.util.ArrayList;
import java.util.List;

/**
 * Mod entry point. Only wires things together here - all behavior lives in
 * the core/config/keybind/module/hud/gui packages, kept independent of this
 * class and of each other wherever practical.
 */
@Mod(modid = KiraPvPMod.MODID, name = KiraPvPMod.NAME, version = KiraPvPMod.VERSION, clientSideOnly = true)
public final class KiraPvPMod {

    public static final String MODID = "kirapvp";
    public static final String NAME = "KiraPvP";
    public static final String VERSION = "@VERSION@";

    private static final Logger LOGGER = LogManager.getLogger(NAME);

    private ModuleManager moduleManager;
    private EventBus eventBus;
    private ConfigManager configManager;
    private GuiState guiState;
    private final Keybind clickGuiKeybind = Keybind.key(Keyboard.KEY_RSHIFT);

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        eventBus = new EventBus();
        guiState = new GuiState();

        NotificationManager notificationManager = new NotificationManager(guiState);
        notificationManager.subscribe(eventBus);

        moduleManager = new ModuleManager(eventBus, buildModules());

        configManager = new ConfigManager(event.getModConfigurationDirectory());
        configManager.attach(eventBus, moduleManager, guiState);

        KeybindManager keybindManager = new KeybindManager();
        for (final Module module : moduleManager.getModules()) {
            if (module.isHoldToActivate()) {
                keybindManager.register(module.getKeybind(), new KeybindManager.KeyAction() {
                    @Override
                    public void onPress() {
                        moduleManager.setModuleEnabled(module, true);
                    }

                    @Override
                    public void onRelease() {
                        moduleManager.setModuleEnabled(module, false);
                    }
                });
            } else {
                keybindManager.register(module.getKeybind(), new KeybindManager.PressAction() {
                    @Override
                    public void onPress() {
                        moduleManager.toggleModule(module);
                    }
                });
            }
        }
        keybindManager.register(clickGuiKeybind, new KeybindManager.PressAction() {
            @Override
            public void onPress() {
                openClickGui();
            }
        });
        keybindManager.lock();

        HudRenderer hudRenderer = new HudRenderer(moduleManager, notificationManager);
        ClientEventHandler handler = new ClientEventHandler(moduleManager, keybindManager, configManager, hudRenderer);
        MinecraftForge.EVENT_BUS.register(handler);
        FMLCommonHandler.instance().bus().register(handler);

        LOGGER.info("{} v{} initialized with {} modules", NAME, VERSION, moduleManager.getModules().length);
    }

    private List<Module> buildModules() {
        List<Module> modules = new ArrayList<Module>();
        modules.add(new FreelookModule());
        modules.add(new ZoomModule());
        modules.add(new FpsDisplayModule());
        modules.add(new CpsDisplayModule());
        modules.add(new CoordinatesDisplayModule());
        modules.add(new PingDisplayModule());
        modules.add(new KeystrokesDisplayModule());
        modules.add(new ArmorStatusModule());
        modules.add(new PotionTimersModule());
        modules.add(new PerformanceModule());
        return modules;
    }

    private void openClickGui() {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer == null) {
            return;
        }
        mc.displayGuiScreen(new ClickGui(moduleManager, eventBus, configManager, guiState));
    }
}
