package com.qmods.kirapvp.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
import com.qmods.kirapvp.core.EventBus;
import com.qmods.kirapvp.core.Module;
import com.qmods.kirapvp.core.ModuleManager;
import com.qmods.kirapvp.core.events.ModuleToggleEvent;
import com.qmods.kirapvp.core.events.SettingChangeEvent;
import com.qmods.kirapvp.settings.BooleanSetting;
import com.qmods.kirapvp.settings.Setting;
import com.qmods.kirapvp.settings.SliderSetting;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Loads and saves mod configuration as plain JSON. Saves are debounced: a
 * change only marks the state dirty, and the actual disk write happens after
 * {@link #DEBOUNCE_MS} of inactivity (checked once per client tick), plus an
 * immediate flush on JVM shutdown and on disconnect. This keeps disk I/O
 * completely off the render/tick hot path.
 */
public final class ConfigManager {

    private static final Logger LOGGER = LogManager.getLogger("KiraPvP");
    private static final long DEBOUNCE_MS = 1500L;

    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private final File modulesFile;
    private final File settingsFile;
    private final File keybindsFile;
    private final File guiStateFile;

    private ModuleManager moduleManager;
    private GuiState guiState;

    private volatile boolean dirty;
    private volatile long lastChangeAt;

    public ConfigManager(File configDirectory) {
        File dir = new File(configDirectory, "KiraPvP");
        if (!dir.exists() && !dir.mkdirs()) {
            LOGGER.warn("Could not create config directory {}", dir.getAbsolutePath());
        }
        modulesFile = new File(dir, "modules.json");
        settingsFile = new File(dir, "settings.json");
        keybindsFile = new File(dir, "keybinds.json");
        guiStateFile = new File(dir, "guistate.json");

        Runtime.getRuntime().addShutdownHook(new Thread("KiraPvP-ConfigFlush") {
            @Override
            public void run() {
                flushIfDirty();
            }
        });
    }

    /** Wires this manager to the live module set/GUI state and loads persisted values into them. */
    public void attach(EventBus eventBus, ModuleManager moduleManager, GuiState guiState) {
        this.moduleManager = moduleManager;
        this.guiState = guiState;

        eventBus.subscribe(ModuleToggleEvent.class, new EventBus.Listener<ModuleToggleEvent>() {
            @Override
            public void onEvent(ModuleToggleEvent event) {
                markDirty();
            }
        });
        eventBus.subscribe(SettingChangeEvent.class, new EventBus.Listener<SettingChangeEvent>() {
            @Override
            public void onEvent(SettingChangeEvent event) {
                markDirty();
            }
        });

        loadAll();
    }

    public void markDirty() {
        dirty = true;
        lastChangeAt = System.currentTimeMillis();
    }

    public void markGuiStateDirty() {
        markDirty();
    }

    /** Call once per client tick. Cheap: a volatile read and, usually, nothing else. */
    public void onTick() {
        if (dirty && System.currentTimeMillis() - lastChangeAt >= DEBOUNCE_MS) {
            flushIfDirty();
        }
    }

    public synchronized void flushIfDirty() {
        if (!dirty || moduleManager == null) {
            return;
        }
        saveModules();
        saveSettings();
        saveKeybinds();
        saveGuiState();
        dirty = false;
    }

    private void loadAll() {
        loadModules();
        loadSettings();
        loadKeybinds();
        loadGuiState();
    }

    @SuppressWarnings("unchecked")
    private void loadModules() {
        Map<String, Boolean> data = readJson(modulesFile, Map.class);
        if (data == null) {
            return;
        }
        for (Module module : moduleManager.getModules()) {
            if (module.isHoldToActivate()) {
                continue;
            }
            Boolean enabled = data.get(module.getName());
            if (enabled != null) {
                moduleManager.applyPersistedState(module, enabled);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private void loadSettings() {
        Map<String, Map<String, Object>> data = readJson(settingsFile, Map.class);
        if (data == null) {
            return;
        }
        for (Module module : moduleManager.getModules()) {
            Map<String, Object> saved = data.get(module.getName());
            if (saved == null) {
                continue;
            }
            for (Setting<?> setting : module.getSettings()) {
                Object raw = saved.get(setting.getName());
                if (raw == null) {
                    continue;
                }
                applySettingValue(setting, raw);
            }
        }
    }

    private void applySettingValue(Setting<?> setting, Object raw) {
        try {
            if (setting instanceof BooleanSetting && raw instanceof Boolean) {
                ((BooleanSetting) setting).setValue((Boolean) raw);
            } else if (setting instanceof SliderSetting && raw instanceof Number) {
                ((SliderSetting) setting).setValue(((Number) raw).doubleValue());
            }
        } catch (Exception e) {
            LOGGER.warn("Ignoring malformed setting '{}': {}", setting.getName(), e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private void loadKeybinds() {
        Map<String, Double> data = readJson(keybindsFile, Map.class);
        if (data == null) {
            return;
        }
        for (Module module : moduleManager.getModules()) {
            Double code = data.get(module.getName());
            if (code != null) {
                module.getKeybind().setCode(code.intValue());
            }
        }
    }

    private void loadGuiState() {
        GuiState loaded = readJson(guiStateFile, GuiState.class);
        if (loaded != null) {
            guiState.scale = loaded.scale;
            guiState.panelX = loaded.panelX;
            guiState.panelY = loaded.panelY;
            guiState.accentColorRgb = loaded.accentColorRgb;
            guiState.notificationsEnabled = loaded.notificationsEnabled;
        }
    }

    private void saveModules() {
        Map<String, Boolean> map = new LinkedHashMap<String, Boolean>();
        for (Module module : moduleManager.getModules()) {
            map.put(module.getName(), module.isEnabled());
        }
        writeJson(modulesFile, map);
    }

    private void saveSettings() {
        Map<String, Map<String, Object>> map = new LinkedHashMap<String, Map<String, Object>>();
        for (Module module : moduleManager.getModules()) {
            if (module.getSettings().isEmpty()) {
                continue;
            }
            Map<String, Object> inner = new LinkedHashMap<String, Object>();
            for (Setting<?> setting : module.getSettings()) {
                inner.put(setting.getName(), setting.getValue());
            }
            map.put(module.getName(), inner);
        }
        writeJson(settingsFile, map);
    }

    private void saveKeybinds() {
        Map<String, Integer> map = new LinkedHashMap<String, Integer>();
        for (Module module : moduleManager.getModules()) {
            map.put(module.getName(), module.getKeybind().getCode());
        }
        writeJson(keybindsFile, map);
    }

    private void saveGuiState() {
        writeJson(guiStateFile, guiState);
    }

    private <T> T readJson(File file, Class<T> type) {
        if (!file.isFile()) {
            return null;
        }
        FileReader reader = null;
        try {
            reader = new FileReader(file);
            return gson.fromJson(reader, type);
        } catch (IOException | JsonSyntaxException e) {
            LOGGER.warn("Could not read {} ({}), using defaults for it", file.getName(), e.getMessage());
            return null;
        } finally {
            closeQuietly(reader);
        }
    }

    private void writeJson(File file, Object value) {
        File tmp = new File(file.getParentFile(), file.getName() + ".tmp");
        Writer writer = null;
        try {
            writer = new FileWriter(tmp);
            gson.toJson(value, writer);
            writer.close();
            writer = null;
            if (file.exists() && !file.delete()) {
                LOGGER.warn("Could not replace {}", file.getName());
                return;
            }
            if (!tmp.renameTo(file)) {
                LOGGER.warn("Could not finalize write of {}", file.getName());
            }
        } catch (IOException e) {
            LOGGER.warn("Could not write {}: {}", file.getName(), e.getMessage());
        } finally {
            closeQuietly(writer);
        }
    }

    private static void closeQuietly(java.io.Closeable closeable) {
        if (closeable != null) {
            try {
                closeable.close();
            } catch (IOException ignored) {
            }
        }
    }
}
