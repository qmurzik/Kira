package com.qmods.kirapvp.settings;

public final class BooleanSetting extends Setting<Boolean> {

    public BooleanSetting(String name, boolean defaultValue) {
        super(name, defaultValue);
    }

    public void toggle() {
        setValue(!getValue());
    }

    @Override
    public String displayValue() {
        return getValue() ? "ON" : "OFF";
    }
}
