package com.qmods.kirapvp.gui.clickgui;

import com.qmods.kirapvp.config.GuiState;
import com.qmods.kirapvp.util.RenderUtils;

/** Small set of colors derived from the persisted accent color. */
public final class ClickGuiTheme {

    private final GuiState guiState;

    public ClickGuiTheme(GuiState guiState) {
        this.guiState = guiState;
    }

    public int accent() {
        return RenderUtils.withAlpha(guiState.accentColorRgb, 255);
    }

    public int accentTranslucent(int alpha) {
        return RenderUtils.withAlpha(guiState.accentColorRgb, alpha);
    }

    public int panelBackground() {
        return RenderUtils.argb(235, 18, 18, 22);
    }

    public int headerBackground() {
        return RenderUtils.argb(255, 12, 12, 15);
    }

    public int rowBackground(boolean hovered) {
        return hovered ? RenderUtils.argb(200, 34, 34, 40) : RenderUtils.argb(160, 26, 26, 31);
    }

    public int textPrimary() {
        return 0xFFEDEDED;
    }

    public int textMuted() {
        return 0xFF9A9AA5;
    }
}
