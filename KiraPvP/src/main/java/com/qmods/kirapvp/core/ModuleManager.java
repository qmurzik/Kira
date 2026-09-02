package com.qmods.kirapvp.core;

import com.qmods.kirapvp.core.events.ModuleToggleEvent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Owns the fixed set of registered modules. Everything is built once at
 * startup into plain arrays/lists so the hot paths (tick, render, GUI lookup)
 * never allocate or resize a collection.
 */
public final class ModuleManager {

    private final EventBus eventBus;
    private final Module[] modules;
    private final Map<Category, List<Module>> byCategory;

    public ModuleManager(EventBus eventBus, List<Module> registered) {
        this.eventBus = eventBus;
        this.modules = registered.toArray(new Module[0]);

        Map<Category, List<Module>> map = new EnumMap<Category, List<Module>>(Category.class);
        for (Category category : Category.values()) {
            map.put(category, new ArrayList<Module>());
        }
        for (Module module : modules) {
            map.get(module.getCategory()).add(module);
        }
        for (Category category : Category.values()) {
            map.put(category, Collections.unmodifiableList(map.get(category)));
        }
        this.byCategory = Collections.unmodifiableMap(map);
    }

    public void toggleModule(Module module) {
        setModuleEnabled(module, !module.isEnabled());
    }

    /**
     * Applies a persisted enabled state during config load. Unlike
     * {@link #setModuleEnabled}, this never posts a {@link ModuleToggleEvent},
     * so restoring state on startup does not spam toggle notifications or
     * re-trigger a config save.
     */
    public void applyPersistedState(Module module, boolean enabled) {
        if (module.isEnabled() == enabled) {
            return;
        }
        module.setEnabledInternal(enabled);
        if (enabled) {
            module.onEnable();
        } else {
            module.onDisable();
        }
    }

    public void setModuleEnabled(Module module, boolean enabled) {
        if (module.isEnabled() == enabled) {
            return;
        }
        module.setEnabledInternal(enabled);
        if (enabled) {
            module.onEnable();
        } else {
            module.onDisable();
        }
        eventBus.post(new ModuleToggleEvent(module, enabled));
    }

    /** Resets transient state on every module without changing enabled flags. */
    public void resetAll() {
        for (Module module : modules) {
            module.onReset();
        }
    }

    public void tickEnabled() {
        for (Module module : modules) {
            if (module.isEnabled()) {
                module.onTick();
            }
        }
    }

    public void renderEnabled(float partialTicks) {
        for (Module module : modules) {
            if (module.isEnabled()) {
                module.onRenderOverlay(partialTicks);
            }
        }
    }

    public Module[] getModules() {
        return modules;
    }

    public List<Module> getModulesByCategory(Category category) {
        return byCategory.get(category);
    }

    @SuppressWarnings("unchecked")
    public <T extends Module> T get(Class<T> type) {
        for (Module module : modules) {
            if (type.isInstance(module)) {
                return (T) module;
            }
        }
        return null;
    }
}
