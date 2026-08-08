package com.froggylord.constellation.ui;

import com.froggylord.constellation.ConstellationClient;
import com.froggylord.constellation.config.LyraConfig;
import com.froggylord.constellation.constellation.LyraInventoryButtons;
import com.froggylord.constellation.render.ConstellationTheme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.List;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

// ported from SkyOcean (MIT): features/inventory/buttons/ButtonConfigScreen.kt
public final class InventoryButtonEditorScreen extends Screen {
    private final Screen parent;
    private int selected;
    private boolean layoutPage;
    private boolean loading;
    private EditBox icon;
    private EditBox command;
    private EditBox title;
    private EditBox tooltip;

    public InventoryButtonEditorScreen(Screen parent, int selected) {
        super(Component.literal("Inventory Buttons"));
        this.parent = parent instanceof AbstractContainerScreen<?> ? null : parent;
        this.selected = selected;
    }

    @Override protected void init() {
        int x = 12, w = Math.max(100, width - 24);
        icon = field(x, 90, w, 80, "minecraft:item_id", value -> current().icon = value);
        command = field(x, 121, w, 256, "command without slash", value -> current().command = value);
        title = field(x, 152, w, 120, "screen-title regular expression", value -> current().title = value);
        tooltip = field(x, 183, w, 120, "tooltip", value -> current().tooltip = value);
        select(selected >= 0 && selected < entries().size() ? selected : 0);
        updateFieldVisibility();
    }

    private EditBox field(int x, int y, int width, int max, String hint, java.util.function.Consumer<String> consumer) {
        EditBox box = new EditBox(font, x, y, width, 18, Component.literal(hint));
        box.setMaxLength(max);
        box.setHint(Component.literal(hint));
        box.setResponder(value -> { if (!loading && selected >= 0) consumer.accept(value); });
        addRenderableWidget(box);
        return box;
    }

    @Override public boolean isPauseScreen() { return false; }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        graphics.fill(0, 0, width, height, 0xE6080810);
        graphics.fill(0, 0, width, 24, 0xFF12121E);
        graphics.fill(0, 23, width, 24, ConstellationTheme.ACCENT);
        graphics.text(font, "Inventory Button Editor", 10, 8, ConstellationTheme.ACCENT_BRIGHT, false);
        button(graphics, width - 82, 3, 72, layoutPage ? "Buttons" : "Layout", false, mouseX, mouseY);
        if (layoutPage) drawLayout(graphics, mouseX, mouseY);
        else drawButtonEditor(graphics, mouseX, mouseY);
    }

    private void drawButtonEditor(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        drawButtonGrid(graphics, mouseX, mouseY);
        LyraConfig.InventoryButtonEntry value = current();
        label(graphics, "Icon item ID", 12, 80);
        label(graphics, "Command", 12, 111);
        label(graphics, "Highlighted screen title regex", 12, 142);
        label(graphics, "Tooltip", 12, 173);
        int actionsY = Math.min(207, height - 32);
        int third = Math.max(72, (width - 40) / 3);
        button(graphics, 12, actionsY, third, value.enabled ? "Enabled" : "Disabled", value.enabled, mouseX, mouseY);
        button(graphics, 16 + third, actionsY, third, "Reset button", false, mouseX, mouseY);
        button(graphics, 20 + third * 2, actionsY, Math.max(60, width - (20 + third * 2) - 12), "Reset all", false, mouseX, mouseY);
        boolean valid = validRegex(value.title);
        String status = valid ? "Button " + (selected + 1) + " selected" : "Invalid regex; literal title matching will be used";
        graphics.text(font, fit(status, width - 24), 12, height - 11, valid ? ConstellationTheme.TEXT_MUTED : 0xFFFF7777, false);
    }

    private void drawButtonGrid(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        int size = 22, gap = 2, total = 7 * size + 6 * gap, startX = (width - total) / 2;
        for (int i = 0; i < 14; i++) {
            int x = startX + (i % 7) * (size + gap), y = i < 7 ? 29 : 54;
            boolean hover = inside(mouseX, mouseY, x, y, size, size);
            LyraConfig.InventoryButtonEntry value = entries().get(i);
            graphics.fill(x, y, x + size, y + size, i == selected ? 0xFF3A315C : hover ? 0xFF29293D : 0xFF191925);
            graphics.fill(x, y, x + size, y + 1, i == selected ? ConstellationTheme.ACCENT_BRIGHT : 0xFF504860);
            graphics.item(LyraInventoryButtons.iconStack(value.icon), x + 3, y + 3);
            if (!value.enabled) graphics.fill(x + 2, y + 2, x + size - 2, y + size - 2, 0x99000000);
        }
    }

    private void drawLayout(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        LyraConfig cfg = ConstellationClient.cfg().lyra;
        int x = 12, gap = 6, col = Math.max(100, (width - 24 - gap) / 2), y = 34;
        toggle(graphics, x, y, col, "Feature enabled", cfg.inventoryButtons, mouseX, mouseY);
        toggle(graphics, x + col + gap, y, col, "Top row", cfg.inventoryButtonsTop, mouseX, mouseY); y += 24;
        toggle(graphics, x, y, col, "Bottom row", cfg.inventoryButtonsBottom, mouseX, mouseY);
        toggle(graphics, x + col + gap, y, col, "Tooltips", cfg.inventoryButtonsShowTooltips, mouseX, mouseY); y += 24;
        toggle(graphics, x, y, col, "Highlight menu", cfg.inventoryButtonsHighlightCurrent, mouseX, mouseY);
        toggle(graphics, x + col + gap, y, col, "Hover movement", cfg.inventoryButtonsHoverAnimation, mouseX, mouseY); y += 24;
        toggle(graphics, x, y, col, "Close after click", cfg.inventoryButtonsCloseAfterCommand, mouseX, mouseY);
        toggle(graphics, x + col + gap, y, col, "Inventory only", cfg.inventoryButtonsOnlyPlayerInventory, mouseX, mouseY); y += 24;
        toggle(graphics, x, y, col, "Hide in creative", cfg.inventoryButtonsHideInCreative, mouseX, mouseY);
        numeric(graphics, x + col + gap, y, col, "Size", cfg.inventoryButtonsSize + " px", mouseX, mouseY); y += 24;
        numeric(graphics, x, y, col, "Gap", cfg.inventoryButtonsGap + " px", mouseX, mouseY);
        numeric(graphics, x + col + gap, y, col, "Overlap", cfg.inventoryButtonsOffset + " px", mouseX, mouseY); y += 24;
        numeric(graphics, x, y, col, "Tooltip delay", cfg.inventoryButtonsTooltipDelayMs + " ms", mouseX, mouseY);
        graphics.text(font, fit("Colors remain editable as ARGB values in Lyra configuration.", width - 24), 12, height - 24, ConstellationTheme.TEXT_MUTED, false);
        graphics.text(font, "Every command requires a deliberate left click.", 12, height - 11, ConstellationTheme.TEXT_MUTED, false);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubled) {
        int mouseX = (int) event.x(), mouseY = (int) event.y();
        if (inside(mouseX, mouseY, width - 82, 3, 72, 19)) { layoutPage = !layoutPage; updateFieldVisibility(); return true; }
        if (layoutPage) return clickLayout(mouseX, mouseY) || super.mouseClicked(event, doubled);
        int index = gridIndex(mouseX, mouseY);
        if (index >= 0) { select(index); return true; }
        int actionsY = Math.min(207, height - 32), third = Math.max(72, (width - 40) / 3);
        if (inside(mouseX, mouseY, 12, actionsY, third, 19)) { current().enabled = !current().enabled; save(); return true; }
        if (inside(mouseX, mouseY, 16 + third, actionsY, third, 19)) { LyraInventoryButtons.reset(selected); select(selected); return true; }
        if (inside(mouseX, mouseY, 20 + third * 2, actionsY, Math.max(60, width - (20 + third * 2) - 12), 19)) { LyraInventoryButtons.resetAll(); select(selected); return true; }
        return super.mouseClicked(event, doubled);
    }

    private boolean clickLayout(int mouseX, int mouseY) {
        LyraConfig cfg = ConstellationClient.cfg().lyra;
        int x = 12, gap = 6, col = Math.max(100, (width - 24 - gap) / 2), y = 34;
        if (hit(mouseX, mouseY, x, y, col)) cfg.inventoryButtons = !cfg.inventoryButtons;
        else if (hit(mouseX, mouseY, x + col + gap, y, col)) cfg.inventoryButtonsTop = !cfg.inventoryButtonsTop;
        else if (hit(mouseX, mouseY, x, y += 24, col)) cfg.inventoryButtonsBottom = !cfg.inventoryButtonsBottom;
        else if (hit(mouseX, mouseY, x + col + gap, y, col)) cfg.inventoryButtonsShowTooltips = !cfg.inventoryButtonsShowTooltips;
        else if (hit(mouseX, mouseY, x, y += 24, col)) cfg.inventoryButtonsHighlightCurrent = !cfg.inventoryButtonsHighlightCurrent;
        else if (hit(mouseX, mouseY, x + col + gap, y, col)) cfg.inventoryButtonsHoverAnimation = !cfg.inventoryButtonsHoverAnimation;
        else if (hit(mouseX, mouseY, x, y += 24, col)) cfg.inventoryButtonsCloseAfterCommand = !cfg.inventoryButtonsCloseAfterCommand;
        else if (hit(mouseX, mouseY, x + col + gap, y, col)) cfg.inventoryButtonsOnlyPlayerInventory = !cfg.inventoryButtonsOnlyPlayerInventory;
        else if (hit(mouseX, mouseY, x, y += 24, col)) cfg.inventoryButtonsHideInCreative = !cfg.inventoryButtonsHideInCreative;
        else if (numericClick(mouseX, mouseY, x + col + gap, y, col, value -> cfg.inventoryButtonsSize = Math.clamp(cfg.inventoryButtonsSize + value, 18, 32), 1)) {}
        else if (numericClick(mouseX, mouseY, x, y += 24, col, value -> cfg.inventoryButtonsGap = Math.clamp(cfg.inventoryButtonsGap + value, -4, 12), 1)) {}
        else if (numericClick(mouseX, mouseY, x + col + gap, y, col, value -> cfg.inventoryButtonsOffset = Math.clamp(cfg.inventoryButtonsOffset + value, 0, 24), 1)) {}
        else if (numericClick(mouseX, mouseY, x, y += 24, col, value -> cfg.inventoryButtonsTooltipDelayMs = Math.clamp(cfg.inventoryButtonsTooltipDelayMs + value, 0, 2000), 50)) {}
        else return false;
        save(); return true;
    }

    private boolean numericClick(int mouseX, int mouseY, int x, int y, int width, java.util.function.IntConsumer change, int step) {
        if (inside(mouseX, mouseY, x, y, 22, 19)) { change.accept(-step); return true; }
        if (inside(mouseX, mouseY, x + width - 22, y, 22, 19)) { change.accept(step); return true; }
        return false;
    }

    private int gridIndex(int mouseX, int mouseY) {
        int size = 22, gap = 2, total = 7 * size + 6 * gap, startX = (width - total) / 2;
        for (int i = 0; i < 14; i++) if (inside(mouseX, mouseY, startX + (i % 7) * (size + gap), i < 7 ? 29 : 54, size, size)) return i;
        return -1;
    }

    private void select(int index) {
        selected = Math.clamp(index, 0, entries().size() - 1);
        LyraConfig.InventoryButtonEntry value = current();
        loading = true;
        icon.setValue(value.icon); command.setValue(value.command); title.setValue(value.title); tooltip.setValue(value.tooltip);
        loading = false;
    }

    private void updateFieldVisibility() { if (icon == null) return; icon.visible = command.visible = title.visible = tooltip.visible = !layoutPage; }
    private LyraConfig.InventoryButtonEntry current() { return entries().get(Math.clamp(selected, 0, entries().size() - 1)); }
    private List<LyraConfig.InventoryButtonEntry> entries() { return LyraInventoryButtons.entries(); }
    private void save() { LyraInventoryButtons.save(); }
    private boolean validRegex(String value) { if (value.isBlank()) return true; try { Pattern.compile(value); return true; } catch (PatternSyntaxException ignored) { return false; } }
    private void label(GuiGraphicsExtractor graphics, String value, int x, int y) { graphics.text(font, value, x, y, ConstellationTheme.TEXT_MUTED, false); }
    private void toggle(GuiGraphicsExtractor graphics, int x, int y, int width, String text, boolean value, int mouseX, int mouseY) { button(graphics, x, y, width, (value ? "ON  " : "OFF ") + text, value, mouseX, mouseY); }
    private void numeric(GuiGraphicsExtractor graphics, int x, int y, int width, String name, String value, int mouseX, int mouseY) { button(graphics, x, y, 22, "-", false, mouseX, mouseY); button(graphics, x + width - 22, y, 22, "+", false, mouseX, mouseY); graphics.text(font, fit(name + " " + value, width - 52), x + 27, y + 5, ConstellationTheme.TEXT, false); }
    private void button(GuiGraphicsExtractor graphics, int x, int y, int width, String text, boolean selected, int mouseX, int mouseY) { boolean hover = inside(mouseX, mouseY, x, y, width, 19); graphics.fill(x, y, x + width, y + 19, selected ? 0xFF30305A : hover ? 0xFF29293D : 0xFF222233); graphics.text(font, fit(text, width - 8), x + 4, y + 5, selected ? ConstellationTheme.ACCENT_BRIGHT : ConstellationTheme.TEXT, false); }
    private String fit(String value, int maxWidth) { if (font.width(value) <= maxWidth) return value; String out = value; while (!out.isEmpty() && font.width(out + "...") > maxWidth) out = out.substring(0, out.length() - 1); return out + "..."; }
    private static boolean hit(int mouseX, int mouseY, int x, int y, int width) { return inside(mouseX, mouseY, x, y, width, 19); }
    private static boolean inside(int mouseX, int mouseY, int x, int y, int width, int height) { return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height; }

    @Override public boolean keyPressed(KeyEvent event) { if (event.key() == GLFW.GLFW_KEY_ESCAPE) { onClose(); return true; } return super.keyPressed(event); }
    @Override public void removed() { save(); super.removed(); }
    @Override public void onClose() { save(); Minecraft.getInstance().setScreenAndShow(parent); }
}
