package com.qmods.kirapvp.gui.clickgui;

import com.qmods.kirapvp.config.ConfigManager;
import com.qmods.kirapvp.config.GuiState;
import com.qmods.kirapvp.core.Category;
import com.qmods.kirapvp.core.EventBus;
import com.qmods.kirapvp.core.Module;
import com.qmods.kirapvp.core.ModuleManager;
import com.qmods.kirapvp.util.MathUtil;
import com.qmods.kirapvp.util.RenderUtils;
import net.minecraft.client.gui.GuiScreen;
import org.lwjgl.opengl.GL11;

import java.util.ArrayList;
import java.util.List;

/**
 * A single draggable, scalable window: a category sidebar on the left and
 * the selected category's modules on the right. Rows are rebuilt only when
 * the category changes or the GUI (re)opens - never per frame. Scaling is
 * applied as a real GL transform, with mouse coordinates de-scaled once per
 * input callback so hit-testing stays in the same logical coordinate space
 * used for layout.
 */
public final class ClickGui extends GuiScreen {

    private static final int SIDEBAR_WIDTH = 64;
    private static final int MODULE_AREA_WIDTH = 200;
    private static final int PANEL_WIDTH = SIDEBAR_WIDTH + MODULE_AREA_WIDTH;
    private static final int HEADER_HEIGHT = 22;
    private static final int TAB_HEIGHT = 24;
    private static final long OPEN_ANIM_MS = 150L;

    private final ModuleManager moduleManager;
    private final EventBus eventBus;
    private final ConfigManager configManager;
    private final GuiState guiState;
    private final ClickGuiTheme theme;

    private Category activeCategory = Category.COMBAT;
    private final List<ModuleRow> rows = new ArrayList<ModuleRow>();

    private boolean draggingPanel;
    private int dragOffsetX;
    private int dragOffsetY;
    private long openedAtMs;

    public ClickGui(ModuleManager moduleManager, EventBus eventBus, ConfigManager configManager, GuiState guiState) {
        this.moduleManager = moduleManager;
        this.eventBus = eventBus;
        this.configManager = configManager;
        this.guiState = guiState;
        this.theme = new ClickGuiTheme(guiState);
    }

    @Override
    public void initGui() {
        openedAtMs = System.currentTimeMillis();
        rebuildRows();
    }

    private void rebuildRows() {
        rows.clear();
        for (Module module : moduleManager.getModulesByCategory(activeCategory)) {
            rows.add(new ModuleRow(module));
        }
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }

    @Override
    public void drawScreen(int rawMouseX, int rawMouseY, float partialTicks) {
        float scale = guiState.scale <= 0 ? 1f : guiState.scale;
        int mouseX = (int) (rawMouseX / scale);
        int mouseY = (int) (rawMouseY / scale);

        float openProgress = MathUtil.easeOutQuad((System.currentTimeMillis() - openedAtMs) / (float) OPEN_ANIM_MS);

        GL11.glPushMatrix();
        GL11.glScalef(scale, scale, 1f);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);

        int panelX = guiState.panelX;
        int panelY = guiState.panelY - (int) ((1 - openProgress) * 12);
        int bodyHeight = Math.max(Category.values().length * TAB_HEIGHT, totalRowsHeight()) + 8;

        drawPanel(panelX, panelY, bodyHeight, mouseX, mouseY);

        GL11.glDisable(GL11.GL_BLEND);
        GL11.glPopMatrix();
    }

    private int totalRowsHeight() {
        int height = 0;
        for (ModuleRow row : rows) {
            height += row.totalHeight();
        }
        return height;
    }

    private void drawPanel(int panelX, int panelY, int bodyHeight, int mouseX, int mouseY) {
        RenderUtils.rect(panelX, panelY, PANEL_WIDTH, HEADER_HEIGHT, theme.headerBackground());
        RenderUtils.drawStringShadow("KiraPvP", panelX + 8, panelY + 7, theme.accent());

        String bell = "Alerts: " + (guiState.notificationsEnabled ? "ON" : "OFF");
        int bellColor = guiState.notificationsEnabled ? theme.textPrimary() : theme.textMuted();
        RenderUtils.drawStringShadow(bell, panelX + PANEL_WIDTH - RenderUtils.stringWidth(bell) - 8, panelY + 7, bellColor);

        int bodyY = panelY + HEADER_HEIGHT;
        RenderUtils.rect(panelX, bodyY, SIDEBAR_WIDTH, bodyHeight, theme.panelBackground());
        RenderUtils.rect(panelX + SIDEBAR_WIDTH, bodyY, MODULE_AREA_WIDTH, bodyHeight, theme.rowBackground(false));

        int tabY = bodyY + 4;
        for (Category category : Category.values()) {
            boolean active = category == activeCategory;
            boolean hovered = mouseX >= panelX && mouseX < panelX + SIDEBAR_WIDTH && mouseY >= tabY && mouseY < tabY + TAB_HEIGHT - 2;
            int bg = active ? theme.accentTranslucent(120) : theme.rowBackground(hovered);
            RenderUtils.rect(panelX + 2, tabY, SIDEBAR_WIDTH - 4, TAB_HEIGHT - 2, bg);
            int textColor = active ? theme.textPrimary() : theme.textMuted();
            RenderUtils.drawStringShadow(category.getDisplayName(), panelX + 8, tabY + 6, textColor);
            tabY += TAB_HEIGHT;
        }

        int rowY = bodyY + 4;
        int rowX = panelX + SIDEBAR_WIDTH;
        for (ModuleRow row : rows) {
            row.render(theme, rowX, rowY, MODULE_AREA_WIDTH, mouseX, mouseY);
            rowY += row.totalHeight();
        }
    }

    @Override
    protected void mouseClicked(int rawMouseX, int rawMouseY, int mouseButton) {
        float scale = guiState.scale <= 0 ? 1f : guiState.scale;
        int mouseX = (int) (rawMouseX / scale);
        int mouseY = (int) (rawMouseY / scale);

        int panelX = guiState.panelX;
        int panelY = guiState.panelY;

        String bell = "Alerts: " + (guiState.notificationsEnabled ? "ON" : "OFF");
        int bellX = panelX + PANEL_WIDTH - RenderUtils.stringWidth(bell) - 8;
        if (mouseButton == 0 && mouseY >= panelY && mouseY < panelY + HEADER_HEIGHT
                && mouseX >= bellX - 4 && mouseX < panelX + PANEL_WIDTH) {
            guiState.notificationsEnabled = !guiState.notificationsEnabled;
            configManager.markGuiStateDirty();
            return;
        }

        if (mouseButton == 0 && mouseY >= panelY && mouseY < panelY + HEADER_HEIGHT
                && mouseX >= panelX && mouseX < panelX + PANEL_WIDTH) {
            draggingPanel = true;
            dragOffsetX = mouseX - panelX;
            dragOffsetY = mouseY - panelY;
            return;
        }

        int bodyY = panelY + HEADER_HEIGHT;
        int tabY = bodyY + 4;
        for (Category category : Category.values()) {
            if (mouseX >= panelX && mouseX < panelX + SIDEBAR_WIDTH && mouseY >= tabY && mouseY < tabY + TAB_HEIGHT - 2) {
                if (category != activeCategory) {
                    activeCategory = category;
                    rebuildRows();
                }
                return;
            }
            tabY += TAB_HEIGHT;
        }

        // At most one row may await a keybind press at a time; clicking any
        // control cancels a stale "..." left over from a previously clicked row.
        for (ModuleRow row : rows) {
            row.awaitingKeybind = false;
        }

        int rowY = bodyY + 4;
        int rowX = panelX + SIDEBAR_WIDTH;
        for (ModuleRow row : rows) {
            if (row.mouseClicked(rowX, rowY, MODULE_AREA_WIDTH, mouseX, mouseY, mouseButton, moduleManager, eventBus)) {
                return;
            }
            rowY += row.totalHeight();
        }
    }

    @Override
    protected void mouseClickMove(int rawMouseX, int rawMouseY, int clickedMouseButton, long timeSinceLastClick) {
        float scale = guiState.scale <= 0 ? 1f : guiState.scale;
        int mouseX = (int) (rawMouseX / scale);
        int mouseY = (int) (rawMouseY / scale);

        if (draggingPanel) {
            guiState.panelX = mouseX - dragOffsetX;
            guiState.panelY = mouseY - dragOffsetY;
            return;
        }

        int rowX = guiState.panelX + SIDEBAR_WIDTH;
        for (ModuleRow row : rows) {
            row.mouseDragged(rowX, MODULE_AREA_WIDTH, mouseX, eventBus);
        }
    }

    @Override
    protected void mouseReleased(int mouseX, int mouseY, int state) {
        if (draggingPanel) {
            draggingPanel = false;
            configManager.markGuiStateDirty();
        }
        for (ModuleRow row : rows) {
            row.mouseReleased();
        }
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) {
        for (ModuleRow row : rows) {
            if (row.awaitingKeybind) {
                int resolved = keyCode == org.lwjgl.input.Keyboard.KEY_ESCAPE ? com.qmods.kirapvp.keybind.Keybind.NONE : keyCode;
                row.applyKeybind(resolved, eventBus);
                return;
            }
        }
        if (keyCode == org.lwjgl.input.Keyboard.KEY_ESCAPE) {
            mc.displayGuiScreen(null);
        }
    }

    @Override
    public void onGuiClosed() {
        configManager.markGuiStateDirty();
    }
}
