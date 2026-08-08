package com.froggylord.constellation.ui;

import com.froggylord.constellation.ConstellationClient;
import com.froggylord.constellation.config.OrionConfig;
import com.froggylord.constellation.constellation.OrionSpiritLeap;
import com.froggylord.constellation.render.ConstellationTheme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.Locale;
import java.util.function.Consumer;

public final class SpiritLeapSettingsScreen extends Screen {
    private static final int[] BACKGROUNDS = {0xE6191919, 0xD0101018, 0xE61D2430, 0xCC000000, 0xF0282028};
    private final Screen parent;
    private final OrionConfig cfg = ConstellationClient.cfg().orion;
    private int lastMouseX;
    private int lastMouseY;

    public SpiritLeapSettingsScreen(Screen parent) {
        super(Component.literal("Spirit Leap Settings"));
        this.parent = parent;
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        lastMouseX = mouseX;
        lastMouseY = mouseY;
        graphics.fill(0, 0, width, height, 0xD0101018);
        int panelWidth = Math.min(430, width - 24);
        int x = (width - panelWidth) / 2;
        int y = 18;
        graphics.fill(x, y, x + panelWidth, height - 18, 0xF0181824);
        graphics.fill(x, y, x + panelWidth, y + 3, ConstellationTheme.ACCENT);
        graphics.text(font, "Spirit Leap interface", x + 12, y + 12, ConstellationTheme.ACCENT_BRIGHT, false);
        graphics.text(font, "Every action is a deliberate click on the original server slot.", x + 12, y + 27, ConstellationTheme.TEXT_MUTED, false);

        int row = y + 48;
        toggle(graphics, x + 12, row, 194, "Replace vanilla menu", cfg.spiritLeapCustomGui, mouseX, mouseY); row += 24;
        toggle(graphics, x + 12, row, 194, "Static role slots", cfg.spiritLeapStaticSlots, mouseX, mouseY);
        toggle(graphics, x + 218, row, 194, "Act on mouse press", cfg.spiritLeapClickOnPress, mouseX, mouseY); row += 24;
        toggle(graphics, x + 12, row, 194, "Show class", cfg.spiritLeapShowClass, mouseX, mouseY);
        toggle(graphics, x + 218, row, 194, "Show dead players", cfg.spiritLeapShowDead, mouseX, mouseY); row += 31;

        graphics.text(font, "Sorting", x + 12, row, ConstellationTheme.TEXT, false); row += 14;
        for (int i = 0; i < OrionSpiritLeap.SORT_NAMES.length; i++) {
            int bx = x + 12 + (i % 3) * 135;
            int by = row + (i / 3) * 23;
            button(graphics, bx, by, 127, OrionSpiritLeap.SORT_NAMES[i], cfg.spiritLeapSorting == i, mouseX, mouseY);
        }
        row += 54;
        graphics.text(font, "Scale", x + 12, row + 5, ConstellationTheme.TEXT, false);
        button(graphics, x + 76, row, 34, "-", false, mouseX, mouseY);
        String scale = Math.clamp(cfg.spiritLeapScalePercent, 50, 200) + "%";
        graphics.text(font, scale, x + 120, row + 5, ConstellationTheme.ACCENT_BRIGHT, false);
        button(graphics, x + 168, row, 34, "+", false, mouseX, mouseY);
        button(graphics, x + 218, row, 194, "Background " + String.format(Locale.ROOT, "%08X", cfg.spiritLeapBackground), false, mouseX, mouseY);
        row += 31;
        graphics.text(font, "Custom order", x + 12, row, ConstellationTheme.TEXT, false);
        row += 14;
        String order = cfg.spiritLeapCustomOrder.isEmpty() ? "Not set" : String.join(" > ", cfg.spiritLeapCustomOrder);
        graphics.text(font, fit(order, panelWidth - 24), x + 12, row, ConstellationTheme.TEXT_MUTED, false);
        row += 15;
        graphics.text(font, "Set exact names with /leapgui order <top-left> <top-right> <bottom-left> <bottom-right>",
            x + 12, row, ConstellationTheme.TEXT_MUTED, false);
        graphics.text(font, "Class keys are configured in Minecraft Controls and remain optional.",
            x + 12, row + 15, ConstellationTheme.TEXT_MUTED, false);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubled) {
        int panelWidth = Math.min(430, width - 24);
        int x = (width - panelWidth) / 2;
        int y = 18;
        int row = y + 48;
        if (hit(x + 12, row, 194, 19)) return flip(value -> cfg.spiritLeapCustomGui = value, cfg.spiritLeapCustomGui);
        row += 24;
        if (hit(x + 12, row, 194, 19)) return flip(value -> cfg.spiritLeapStaticSlots = value, cfg.spiritLeapStaticSlots);
        if (hit(x + 218, row, 194, 19)) return flip(value -> cfg.spiritLeapClickOnPress = value, cfg.spiritLeapClickOnPress);
        row += 24;
        if (hit(x + 12, row, 194, 19)) return flip(value -> cfg.spiritLeapShowClass = value, cfg.spiritLeapShowClass);
        if (hit(x + 218, row, 194, 19)) return flip(value -> cfg.spiritLeapShowDead = value, cfg.spiritLeapShowDead);
        row += 45;
        for (int i = 0; i < OrionSpiritLeap.SORT_NAMES.length; i++) {
            if (hit(x + 12 + (i % 3) * 135, row + (i / 3) * 23, 127, 19)) {
                cfg.spiritLeapSorting = i;
                save();
                return true;
            }
        }
        row += 54;
        if (hit(x + 76, row, 34, 19)) {
            cfg.spiritLeapScalePercent = Math.clamp(cfg.spiritLeapScalePercent - 10, 50, 200);
            save(); return true;
        }
        if (hit(x + 168, row, 34, 19)) {
            cfg.spiritLeapScalePercent = Math.clamp(cfg.spiritLeapScalePercent + 10, 50, 200);
            save(); return true;
        }
        if (hit(x + 218, row, 194, 19)) {
            int index = 0;
            for (int i = 0; i < BACKGROUNDS.length; i++) if (BACKGROUNDS[i] == cfg.spiritLeapBackground) index = i + 1;
            cfg.spiritLeapBackground = BACKGROUNDS[index % BACKGROUNDS.length];
            save(); return true;
        }
        return super.mouseClicked(event, doubled);
    }

    private boolean flip(Consumer<Boolean> setter, boolean value) {
        setter.accept(!value);
        save();
        return true;
    }

    private void save() { ConstellationClient.saveConfig(); }
    private boolean hit(int x, int y, int w, int h) {
        return lastMouseX >= x && lastMouseX < x + w && lastMouseY >= y && lastMouseY < y + h;
    }

    private void toggle(GuiGraphicsExtractor graphics, int x, int y, int w, String text, boolean value, int mouseX, int mouseY) {
        button(graphics, x, y, w, (value ? "ON  " : "OFF ") + text, value, mouseX, mouseY);
    }

    private void button(GuiGraphicsExtractor graphics, int x, int y, int w, String text, boolean selected, int mouseX, int mouseY) {
        boolean hover = mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + 19;
        int colour = selected ? 0xFF30305A : hover ? 0xFF29293D : 0xFF222233;
        graphics.fill(x, y, x + w, y + 19, colour);
        graphics.text(font, fit(text, w - 8), x + 4, y + 5,
            selected ? ConstellationTheme.ACCENT_BRIGHT : ConstellationTheme.TEXT, false);
    }

    private String fit(String text, int maxWidth) {
        if (font.width(text) <= maxWidth) return text;
        String value = text;
        while (!value.isEmpty() && font.width(value + "...") > maxWidth) value = value.substring(0, value.length() - 1);
        return value + "...";
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.key() == GLFW.GLFW_KEY_ESCAPE) { onClose(); return true; }
        return super.keyPressed(event);
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreenAndShow(parent);
    }
}
