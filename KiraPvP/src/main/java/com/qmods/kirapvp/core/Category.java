package com.qmods.kirapvp.core;

/**
 * Groups modules for the ClickGUI. Fixed, small enum -&gt; no allocations at lookup time.
 */
public enum Category {
    COMBAT("Combat"),
    MOVEMENT("Movement"),
    RENDER("Render"),
    PLAYER("Player"),
    MISC("Misc");

    private final String displayName;

    Category(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
