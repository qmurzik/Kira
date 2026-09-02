package com.qmods.kirapvp.gui.clickgui;

import com.qmods.kirapvp.core.EventBus;
import com.qmods.kirapvp.core.Module;
import com.qmods.kirapvp.core.ModuleManager;
import com.qmods.kirapvp.core.events.SettingChangeEvent;
import com.qmods.kirapvp.settings.BooleanSetting;
import com.qmods.kirapvp.settings.Setting;
import com.qmods.kirapvp.settings.SliderSetting;
import com.qmods.kirapvp.util.RenderUtils;

/**
 * Renders and hit-tests one module: its header row (toggle + expand arrow)
 * plus, when expanded, a keybind editor and its settings. Created fresh each
 * time the ClickGUI opens - purely UI state, cheap and infrequent.
 */
final class ModuleRow {

    static final int ROW_HEIGHT = 20;
    private static final int SETTING_HEIGHT = 18;

    final Module module;
    private boolean expanded;
    boolean awaitingKeybind;
    private SliderSetting draggingSlider;

    ModuleRow(Module module) {
        this.module = module;
    }

    int totalHeight() {
        if (!expanded) {
            return ROW_HEIGHT;
        }
        return ROW_HEIGHT + SETTING_HEIGHT + module.getSettings().size() * SETTING_HEIGHT;
    }

    void render(ClickGuiTheme theme, int x, int y, int width, int mouseX, int mouseY) {
        boolean hovered = mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + ROW_HEIGHT;
        RenderUtils.rect(x, y, width, ROW_HEIGHT, theme.rowBackground(hovered));

        int dotColor = module.isEnabled() ? theme.accent() : 0xFF55555C;
        RenderUtils.rect(x + 6, y + ROW_HEIGHT / 2 - 3, 6, 6, dotColor);

        RenderUtils.drawStringShadow(module.getName(), x + 18, y + 6, theme.textPrimary());

        String arrow = expanded ? "-" : "+";
        RenderUtils.drawStringShadow(arrow, x + width - 14, y + 6, theme.textMuted());

        if (!expanded) {
            return;
        }

        int rowY = y + ROW_HEIGHT;
        renderKeybindRow(theme, x, rowY, width, mouseX, mouseY);
        rowY += SETTING_HEIGHT;

        for (Setting<?> setting : module.getSettings()) {
            if (setting instanceof BooleanSetting) {
                renderBooleanRow(theme, (BooleanSetting) setting, x, rowY, width, mouseX, mouseY);
            } else if (setting instanceof SliderSetting) {
                renderSliderRow(theme, (SliderSetting) setting, x, rowY, width);
            }
            rowY += SETTING_HEIGHT;
        }
    }

    private void renderKeybindRow(ClickGuiTheme theme, int x, int y, int width, int mouseX, int mouseY) {
        RenderUtils.rect(x, y, width, SETTING_HEIGHT, theme.panelBackground());
        RenderUtils.drawStringShadow("Keybind", x + 8, y + 5, theme.textMuted());
        String label = awaitingKeybind ? "..." : module.getKeybind().displayName();
        int labelWidth = RenderUtils.stringWidth(label);
        boolean hovered = mouseX >= x + width - labelWidth - 16 && mouseX < x + width - 4 && mouseY >= y && mouseY < y + SETTING_HEIGHT;
        int color = awaitingKeybind ? theme.accent() : (hovered ? theme.textPrimary() : theme.textMuted());
        RenderUtils.drawStringShadow(label, x + width - labelWidth - 8, y + 5, color);
    }

    private void renderBooleanRow(ClickGuiTheme theme, BooleanSetting setting, int x, int y, int width, int mouseX, int mouseY) {
        RenderUtils.rect(x, y, width, SETTING_HEIGHT, theme.panelBackground());
        RenderUtils.drawStringShadow(setting.getName(), x + 8, y + 5, theme.textMuted());
        int switchWidth = 22;
        int switchX = x + width - switchWidth - 6;
        int switchColor = setting.getValue() ? theme.accent() : 0xFF3A3A42;
        RenderUtils.rect(switchX, y + 4, switchWidth, 10, switchColor);
        int knobX = setting.getValue() ? switchX + switchWidth - 8 : switchX;
        RenderUtils.rect(knobX, y + 3, 8, 12, 0xFFEDEDED);
    }

    private void renderSliderRow(ClickGuiTheme theme, SliderSetting setting, int x, int y, int width) {
        RenderUtils.rect(x, y, width, SETTING_HEIGHT, theme.panelBackground());
        RenderUtils.drawStringShadow(setting.getName(), x + 8, y + 5, theme.textMuted());
        String value = setting.displayValue();
        RenderUtils.drawStringShadow(value, x + width - RenderUtils.stringWidth(value) - 8, y + 5, theme.textPrimary());

        int trackX = x + 8;
        int trackY = y + SETTING_HEIGHT - 4;
        int trackWidth = width - 16;
        RenderUtils.rect(trackX, trackY, trackWidth, 2, 0xFF3A3A42);
        RenderUtils.rect(trackX, trackY, trackWidth * setting.getFraction(), 2, theme.accent());
    }

    /**
     * @return true if this click was consumed by this row.
     */
    boolean mouseClicked(int x, int y, int width, int mouseX, int mouseY, int button,
                          ModuleManager moduleManager, EventBus eventBus) {
        if (mouseY >= y && mouseY < y + ROW_HEIGHT && mouseX >= x && mouseX < x + width) {
            if (mouseX >= x + width - 14) {
                expanded = !expanded;
            } else {
                moduleManager.toggleModule(module);
            }
            return true;
        }
        if (!expanded) {
            return false;
        }

        int rowY = y + ROW_HEIGHT;
        if (mouseY >= rowY && mouseY < rowY + SETTING_HEIGHT && mouseX >= x && mouseX < x + width) {
            awaitingKeybind = true;
            return true;
        }
        rowY += SETTING_HEIGHT;

        for (Setting<?> setting : module.getSettings()) {
            if (mouseY >= rowY && mouseY < rowY + SETTING_HEIGHT && mouseX >= x && mouseX < x + width) {
                if (setting instanceof BooleanSetting) {
                    ((BooleanSetting) setting).toggle();
                    eventBus.post(new SettingChangeEvent(setting));
                } else if (setting instanceof SliderSetting) {
                    SliderSetting slider = (SliderSetting) setting;
                    int trackX = x + 8;
                    int trackWidth = width - 16;
                    slider.setFromFraction((mouseX - trackX) / (double) trackWidth);
                    eventBus.post(new SettingChangeEvent(setting));
                    draggingSlider = slider;
                }
                return true;
            }
            rowY += SETTING_HEIGHT;
        }
        return false;
    }

    void mouseDragged(int x, int width, int mouseX, EventBus eventBus) {
        if (draggingSlider == null) {
            return;
        }
        int trackX = x + 8;
        int trackWidth = width - 16;
        draggingSlider.setFromFraction((mouseX - trackX) / (double) trackWidth);
        eventBus.post(new SettingChangeEvent(draggingSlider));
    }

    void mouseReleased() {
        draggingSlider = null;
    }

    /** @return true if a rebind was in progress and is now resolved. */
    boolean applyKeybind(int keyCode, EventBus eventBus) {
        if (!awaitingKeybind) {
            return false;
        }
        module.getKeybind().setCode(keyCode);
        awaitingKeybind = false;
        eventBus.post(new SettingChangeEvent());
        return true;
    }
}
