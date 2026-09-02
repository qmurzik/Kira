package com.qmods.kirapvp.core.events;

import com.qmods.kirapvp.settings.Setting;

/** Fired whenever a setting value is edited from the ClickGUI. */
public final class SettingChangeEvent extends KiraEvent {

    private final Setting<?> setting;

    public SettingChangeEvent() {
        this(null);
    }

    public SettingChangeEvent(Setting<?> setting) {
        this.setting = setting;
    }

    public Setting<?> getSetting() {
        return setting;
    }
}
