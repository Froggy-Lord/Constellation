package com.froggylord.constellation.ui;

import com.froggylord.constellation.constellation.PhoenixSlotBinding;
import com.froggylord.constellation.render.ConstellationTheme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.List;
import java.util.Map;

// ported from Athen (BSD-3-Clause): modules/impl/general/slotbinds/SlotBindsGUI.kt
public final class SlotBindingEditorScreen extends Screen {
    private final Screen parent;
    private EditBox profileName;
    private int profileScroll;
    private boolean creating;
    private String confirmDelete;

    public SlotBindingEditorScreen(Screen parent) {
        super(Component.literal("Slot Binding Editor"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        String draft = profileName == null ? "" : profileName.getValue();
        boolean focused = profileName != null && profileName.isFocused();
        profileName = new EditBox(font, 10, height - 31, 104, 18, Component.literal("profile name"));
        profileName.setMaxLength(24);
        profileName.setHint(Component.literal("profile name"));
        profileName.setValue(draft);
        profileName.visible = creating;
        addRenderableWidget(profileName);
        if (creating && focused) { profileName.setFocused(true); setFocused(profileName); }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        ConstellationUi.background(graphics, width, height, delta);
        ConstellationUi.header(graphics, font, "Slot Binding Editor",
            PhoenixSlotBinding.profileNames().size() + " profiles", width);
        drawProfiles(graphics, mouseX, mouseY);
        drawInventory(graphics, mouseX, mouseY);
        String help = PhoenixSlotBinding.editorSelection() == null
            ? "Left: select   Middle: cycle color   Right: unbind   Shift-left in inventory: swap"
            : "Select one slot from the other inventory section";
        graphics.text(font, ConstellationUi.fit(font, help, width - 136), 126, height - 11,
            ConstellationTheme.TEXT_MUTED, false);
    }

    private void drawProfiles(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        int x = 8, y = 30, width = 108, height = this.height - 68;
        ConstellationUi.panel(graphics, x, y, width, height);
        graphics.text(font, "Profiles", x + 5, y + 5, ConstellationTheme.TEXT, false);
        int rowY = y + 18 - profileScroll * 19;
        for (String name : PhoenixSlotBinding.profileNames()) {
            if (rowY + 18 >= y + 17 && rowY < y + height) {
                boolean selected = name.equals(PhoenixSlotBinding.selectedProfile());
                boolean hover = inside(mouseX, mouseY, x + 3, rowY, width - 6, 18);
                int color = name.equals(confirmDelete) ? 0xFF5A252B : selected ? 0xFF363052 : hover ? 0xFF29293B : 0xFF1C1C2A;
                ConstellationTheme.surface(graphics, x + 3, rowY, width - 6, 18, color,
                    selected ? ConstellationTheme.ACCENT_DIM : ConstellationTheme.BORDER_SOFT);
                graphics.text(font, ConstellationUi.fit(font, name, width - 14), x + 7, rowY + 5,
                    selected ? ConstellationTheme.ACCENT_BRIGHT : ConstellationTheme.TEXT, false);
            }
            rowY += 19;
        }
        if (!creating) button(graphics, x + 3, this.height - 31, width - 6, "New profile", mouseX, mouseY);
        else {
            graphics.text(font, ConstellationUi.fit(font, "Enter create  Esc cancel", width - 10), x + 5,
                this.height - 44, ConstellationTheme.TEXT_MUTED, false);
            ConstellationTheme.search(graphics, profileName.getX() - 2, profileName.getY() - 2,
                profileName.getWidth() + 4, profileName.getHeight() + 4, profileName.isFocused());
        }
    }

    private void drawInventory(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        int cell = 21, size = 18;
        int gridWidth = 9 * cell - 3;
        int startX = Math.max(126, 126 + (width - 126 - gridWidth) / 2);
        int startY = Math.max(39, (height - 4 * cell) / 2);
        ConstellationUi.panel(graphics, startX - 9, startY - 22, gridWidth + 18, 4 * cell + 49);
        graphics.text(font, "Inventory", startX, startY - 13, ConstellationTheme.TEXT_MUTED, false);
        Map<Integer, List<Integer>> binds = PhoenixSlotBinding.bindings();
        Integer selected = PhoenixSlotBinding.editorSelection();
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                int canonical = 9 + row * 9 + column;
                slot(graphics, canonical, startX + column * cell, startY + row * cell, size,
                    boundInventory(binds, canonical), selected, mouseX, mouseY);
            }
        }
        int hotbarY = startY + 3 * cell + 8;
        graphics.fill(startX, hotbarY - 5, startX + gridWidth, hotbarY - 4, 0xFF3C3C54);
        for (int column = 0; column < 9; column++)
            slot(graphics, column, startX + column * cell, hotbarY, size,
                binds.containsKey(column), selected, mouseX, mouseY);
        graphics.text(font, "Hotbar", startX, hotbarY + 23, ConstellationTheme.TEXT_MUTED, false);
    }

    private void slot(GuiGraphicsExtractor graphics, int canonical, int x, int y, int size,
                      boolean bound, Integer selected, int mouseX, int mouseY) {
        boolean chosen = selected != null && selected == canonical;
        boolean hover = inside(mouseX, mouseY, x, y, size, size);
        int color = chosen ? 0xFF40345F : bound ? 0xFF2B3340 : hover ? 0xFF29293B : 0xFF1C1C2A;
        ConstellationTheme.surface(graphics, x, y, size, size, color, chosen ? ConstellationTheme.ACCENT_BRIGHT
            : bound ? PhoenixSlotBinding.bindingColor(canonical) : 0xFF4A4A62);
        graphics.outline(x, y, size, size, chosen ? ConstellationTheme.ACCENT_BRIGHT
            : bound ? PhoenixSlotBinding.bindingColor(canonical) : 0xFF4A4A62);
        String label = Integer.toString(canonical);
        graphics.text(font, label, x + (size - font.width(label)) / 2, y + 5,
            chosen ? ConstellationTheme.ACCENT_BRIGHT : ConstellationTheme.TEXT, false);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubled) {
        int mouseX = (int) event.x(), mouseY = (int) event.y(), button = event.button();
        if (creating) {
            if (inside(mouseX, mouseY, 10, height - 31, 104, 18)) return super.mouseClicked(event, doubled);
            createProfile();
            return true;
        }
        int profile = profileAt(mouseX, mouseY);
        if (profile >= 0) {
            String name = PhoenixSlotBinding.profileNames().get(profile);
            if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
                confirmDelete = name.equals(confirmDelete) ? null : name;
            } else if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
                if (name.equals(confirmDelete)) {
                    PhoenixSlotBinding.deleteProfileFromEditor(name);
                    confirmDelete = null;
                } else PhoenixSlotBinding.selectProfileFromEditor(name);
                PhoenixSlotBinding.clearEditorSelection();
            }
            return true;
        }
        if (inside(mouseX, mouseY, 11, height - 31, 102, 18) && button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            creating = true;
            profileName.visible = true;
            profileName.setFocused(true);
            setFocused(profileName);
            return true;
        }
        int slot = slotAt(mouseX, mouseY);
        if (slot >= 0 && (button == GLFW.GLFW_MOUSE_BUTTON_LEFT || button == GLFW.GLFW_MOUSE_BUTTON_RIGHT)) {
            PhoenixSlotBinding.editorClick(slot, button == GLFW.GLFW_MOUSE_BUTTON_RIGHT);
            return true;
        }
        if (slot >= 0 && button == GLFW.GLFW_MOUSE_BUTTON_MIDDLE) {
            PhoenixSlotBinding.editorCycleColor(slot);
            return true;
        }
        confirmDelete = null;
        return super.mouseClicked(event, doubled);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
        if (mouseX >= 8 && mouseX < 116) {
            int max = Math.max(0, PhoenixSlotBinding.profileNames().size() - Math.max(1, (height - 88) / 19));
            profileScroll = Math.clamp(profileScroll - (int) Math.signum(vertical), 0, max);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontal, vertical);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (creating && event.key() == GLFW.GLFW_KEY_ENTER) {
            createProfile();
            return true;
        }
        if (creating && event.key() == GLFW.GLFW_KEY_ESCAPE) {
            creating = false;
            profileName.visible = false;
            profileName.setValue("");
            profileName.setFocused(false);
            setFocused(null);
            return true;
        }
        if (event.key() == GLFW.GLFW_KEY_ESCAPE) {
            onClose();
            return true;
        }
        return super.keyPressed(event);
    }

    private void createProfile() {
        String name = profileName.getValue();
        if (!name.isBlank()) PhoenixSlotBinding.createProfileFromEditor(name);
        profileName.setValue("");
        profileName.visible = false;
        profileName.setFocused(false);
        setFocused(null);
        creating = false;
        PhoenixSlotBinding.clearEditorSelection();
    }

    private int profileAt(int mouseX, int mouseY) {
        int rowY = 48 - profileScroll * 19;
        List<String> names = PhoenixSlotBinding.profileNames();
        for (int i = 0; i < names.size(); i++, rowY += 19)
            if (inside(mouseX, mouseY, 11, rowY, 102, 18)) return i;
        return -1;
    }

    private int slotAt(int mouseX, int mouseY) {
        int cell = 21, size = 18, gridWidth = 9 * cell - 3;
        int startX = Math.max(126, 126 + (width - 126 - gridWidth) / 2);
        int startY = Math.max(39, (height - 4 * cell) / 2);
        for (int row = 0; row < 3; row++) for (int column = 0; column < 9; column++)
            if (inside(mouseX, mouseY, startX + column * cell, startY + row * cell, size, size))
                return 9 + row * 9 + column;
        int hotbarY = startY + 3 * cell + 8;
        for (int column = 0; column < 9; column++)
            if (inside(mouseX, mouseY, startX + column * cell, hotbarY, size, size)) return column;
        return -1;
    }

    private static boolean boundInventory(Map<Integer, List<Integer>> binds, int canonical) {
        for (List<Integer> values : binds.values()) if (values.contains(canonical)) return true;
        return false;
    }

    private void button(GuiGraphicsExtractor graphics, int x, int y, int width, String text, int mouseX, int mouseY) {
        boolean hover = inside(mouseX, mouseY, x, y, width, 18);
        ConstellationUi.button(graphics, font, x, y, width, 18, text, hover, false);
    }

    private static boolean inside(double mouseX, double mouseY, int x, int y, int width, int height) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }

    @Override
    public void removed() {
        PhoenixSlotBinding.clearEditorSelection();
        super.removed();
    }

    @Override
    public void onClose() {
        PhoenixSlotBinding.clearEditorSelection();
        Minecraft.getInstance().setScreenAndShow(parent);
    }
}
