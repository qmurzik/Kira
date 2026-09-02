package com.qmods.kirapvp.core.events;

import com.qmods.kirapvp.core.Module;

public final class ModuleToggleEvent extends KiraEvent {

    private final Module module;
    private final boolean enabled;

    public ModuleToggleEvent(Module module, boolean enabled) {
        this.module = module;
        this.enabled = enabled;
    }

    public Module getModule() {
        return module;
    }

    public boolean isEnabled() {
        return enabled;
    }
}
