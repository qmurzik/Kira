package com.qmods.kirapvp.core;

import com.qmods.kirapvp.keybind.Keybind;
import com.qmods.kirapvp.settings.Setting;

import java.util.Collections;
import java.util.List;

/**
 * Base class for every feature. Settings lists are fixed at construction time
 * (no dynamic growth at runtime) so iterating them in render/tick hooks never
 * allocates.
 */
public abstract class Module {

    private final String name;
    private final String description;
    private final Category category;
    private final Keybind keybind;
    private final List<Setting<?>> settings;
    private boolean enabled;

    protected Module(String name, String description, Category category, Keybind keybind, List<Setting<?>> settings) {
        this.name = name;
        this.description = description;
        this.category = category;
        this.keybind = keybind;
        this.settings = settings == null ? Collections.<Setting<?>>emptyList() : settings;
    }

    protected Module(String name, String description, Category category, Keybind keybind) {
        this(name, description, category, keybind, Collections.<Setting<?>>emptyList());
    }

    /** Called once, right after the module state changes to enabled. */
    public void onEnable() {
    }

    /** Called once, right after the module state changes to disabled. */
    public void onDisable() {
    }

    /** Called on every client tick (20/s) while enabled. Keep cheap. */
    public void onTick() {
    }

    /** Called on every rendered frame while enabled. Must be allocation-free. */
    public void onRenderOverlay(float partialTicks) {
    }

    /** Resets any transient per-world/per-life state without changing the enabled flag. */
    public void onReset() {
    }

    /**
     * When true, the module's keybind is wired as hold-to-activate (enabled
     * while held, disabled on release) instead of the default press-to-toggle.
     */
    public boolean isHoldToActivate() {
        return false;
    }

    final void setEnabledInternal(boolean value) {
        this.enabled = value;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public Category getCategory() {
        return category;
    }

    public Keybind getKeybind() {
        return keybind;
    }

    public List<Setting<?>> getSettings() {
        return settings;
    }
}
