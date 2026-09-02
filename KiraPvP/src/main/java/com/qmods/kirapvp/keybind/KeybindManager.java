package com.qmods.kirapvp.keybind;

import java.util.ArrayList;
import java.util.List;

/**
 * Polls all registered keybinds once per client tick using press/release edge
 * detection. All entries are registered during mod setup and then frozen into
 * a plain array via {@link #lock()} - the per-tick poll loop never allocates.
 */
public final class KeybindManager {

    public interface KeyAction {
        void onPress();

        void onRelease();
    }

    /** Convenience base: most keybinds only care about the press edge. */
    public static abstract class PressAction implements KeyAction {
        @Override
        public void onRelease() {
        }
    }

    private static final class Entry {
        final Keybind keybind;
        final KeyAction action;
        boolean wasDown;

        Entry(Keybind keybind, KeyAction action) {
            this.keybind = keybind;
            this.action = action;
        }
    }

    private final List<Entry> pending = new ArrayList<Entry>();
    private Entry[] entries = new Entry[0];

    public void register(Keybind keybind, KeyAction action) {
        pending.add(new Entry(keybind, action));
    }

    /** Call once after all modules have registered their keybinds. */
    public void lock() {
        entries = pending.toArray(new Entry[0]);
    }

    public void onTick() {
        for (int i = 0; i < entries.length; i++) {
            Entry entry = entries[i];
            boolean down = entry.keybind.isDown();
            if (down && !entry.wasDown) {
                entry.action.onPress();
            } else if (!down && entry.wasDown) {
                entry.action.onRelease();
            }
            entry.wasDown = down;
        }
    }
}
