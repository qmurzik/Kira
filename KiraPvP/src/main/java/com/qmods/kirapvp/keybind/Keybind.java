package com.qmods.kirapvp.keybind;

import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

/**
 * A single rebindable trigger: keyboard key, mouse button, or none.
 * Uses the same negative-code convention as vanilla's own KeyBinding
 * (code &lt; 0 means mouse button {@code -code - 100}) so no extra mapping
 * table is needed. A plain mutable int field - rebinding never allocates.
 */
public final class Keybind {

    public static final int NONE = Keyboard.KEY_NONE;

    private int code;

    public Keybind(int code) {
        this.code = code;
    }

    public static Keybind none() {
        return new Keybind(NONE);
    }

    public static Keybind key(int keyboardCode) {
        return new Keybind(keyboardCode);
    }

    public static Keybind mouse(int mouseButton) {
        return new Keybind(-100 - mouseButton);
    }

    public int getCode() {
        return code;
    }

    public void setCode(int code) {
        this.code = code;
    }

    public boolean isSet() {
        return code != NONE;
    }

    public boolean isMouse() {
        return code < 0;
    }

    public boolean isDown() {
        if (code == NONE) {
            return false;
        }
        if (code < 0) {
            return Mouse.isButtonDown(-code - 100);
        }
        return Keyboard.isKeyDown(code);
    }

    public String displayName() {
        if (code == NONE) {
            return "NONE";
        }
        if (code < 0) {
            return "MOUSE" + (-code - 100);
        }
        return Keyboard.getKeyName(code);
    }
}
