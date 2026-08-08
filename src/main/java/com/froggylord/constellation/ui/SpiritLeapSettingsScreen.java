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
    private double scroll;
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
        ConstellationUi.background(graphics, width, height, delta);
        ConstellationUi.header(graphics, font, "Spirit Leap",
            cfg.spiritLeapCustomGui ? "Custom interface enabled" : "Vanilla interface", width);
        int panelWidth = Math.min(430, width - 24);
        int x = (width - panelWidth) / 2;
        int panelY = 34;
        int top = panelY + 7;
        int bottom = height - 18;
        int inner = panelWidth - 24;
        int gap = 12;
        int columnWidth = (inner - gap) / 2;
        int y = panelY - (int) scroll;
        ConstellationUi.panel(graphics, x, panelY, panelWidth, height - panelY - 12);
        text(graphics, "Every action uses the original server slot you click.", x + 12, y + 12,
            ConstellationTheme.TEXT_MUTED, top, bottom);

        int row = y + 31;
        toggle(graphics, x + 12, row, inner, "Replace vanilla menu", cfg.spiritLeapCustomGui, mouseX, mouseY, top, bottom); row += 24;
        toggle(graphics, x + 12, row, columnWidth, "Static role slots", cfg.spiritLeapStaticSlots, mouseX, mouseY, top, bottom);
        toggle(graphics, x + 12 + columnWidth + gap, row, columnWidth, "Act on mouse press", cfg.spiritLeapClickOnPress, mouseX, mouseY, top, bottom); row += 24;
        toggle(graphics, x + 12, row, columnWidth, "Show class", cfg.spiritLeapShowClass, mouseX, mouseY, top, bottom);
        toggle(graphics, x + 12 + columnWidth + gap, row, columnWidth, "Show dead players", cfg.spiritLeapShowDead, mouseX, mouseY, top, bottom); row += 31;

        text(graphics, "Sorting", x + 12, row, ConstellationTheme.TEXT, top, bottom); row += 14;
        int sortWidth = (inner - 16) / 3;
        for (int i = 0; i < OrionSpiritLeap.SORT_NAMES.length; i++) {
            int bx = x + 12 + (i % 3) * (sortWidth + 8);
            int by = row + (i / 3) * 23;
            button(graphics, bx, by, sortWidth, OrionSpiritLeap.SORT_NAMES[i], cfg.spiritLeapSorting == i, mouseX, mouseY, top, bottom);
        }
        row += 54;
        text(graphics, "Scale", x + 12, row + 5, ConstellationTheme.TEXT, top, bottom);
        button(graphics, x + 76, row, 34, "-", false, mouseX, mouseY, top, bottom);
        String scale = Math.clamp(cfg.spiritLeapScalePercent, 50, 200) + "%";
        text(graphics, scale, x + 120, row + 5, ConstellationTheme.ACCENT_BRIGHT, top, bottom);
        button(graphics, x + 168, row, 34, "+", false, mouseX, mouseY, top, bottom);
        button(graphics, x + 12 + columnWidth + gap, row, columnWidth, "Background " + String.format(Locale.ROOT, "%08X", cfg.spiritLeapBackground), false, mouseX, mouseY, top, bottom);
        row += 31;
        text(graphics, "Custom order", x + 12, row, ConstellationTheme.TEXT, top, bottom);
        row += 14;
        String order = cfg.spiritLeapCustomOrder.isEmpty() ? "Not set" : String.join(" > ", cfg.spiritLeapCustomOrder);
        text(graphics, ConstellationUi.fit(font, order, panelWidth - 24), x + 12, row,
            ConstellationTheme.TEXT_MUTED, top, bottom);
        row += 15;
        text(graphics, ConstellationUi.fit(font,
            "Set exact names with /leapgui order <top-left> <top-right> <bottom-left> <bottom-right>", inner),
            x + 12, row, ConstellationTheme.TEXT_MUTED, top, bottom);
        text(graphics, ConstellationUi.fit(font,
            "Class keys are configured in Minecraft Controls and remain optional.", inner),
            x + 12, row + 15, ConstellationTheme.TEXT_MUTED, top, bottom);
        int viewport = Math.max(1, bottom - top);
        ConstellationUi.scrollbar(graphics, x + panelWidth - 7, top, viewport,
            viewport, Math.max(viewport, 259), (int) scroll);
        scroll = Math.clamp(scroll, 0, maxScroll(panelY, bottom));
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubled) {
        int panelWidth = Math.min(430, width - 24);
        int x = (width - panelWidth) / 2;
        int inner = panelWidth - 24;
        int gap = 12;
        int columnWidth = (inner - gap) / 2;
        int y = 34 - (int) scroll;
        if (lastMouseY < 41 || lastMouseY >= height - 18) return super.mouseClicked(event, doubled);
        int row = y + 31;
        if (hitVisible(x + 12, row, inner, 19)) return flip(value -> cfg.spiritLeapCustomGui = value, cfg.spiritLeapCustomGui);
        row += 24;
        if (hitVisible(x + 12, row, columnWidth, 19)) return flip(value -> cfg.spiritLeapStaticSlots = value, cfg.spiritLeapStaticSlots);
        if (hitVisible(x + 12 + columnWidth + gap, row, columnWidth, 19)) return flip(value -> cfg.spiritLeapClickOnPress = value, cfg.spiritLeapClickOnPress);
        row += 24;
        if (hitVisible(x + 12, row, columnWidth, 19)) return flip(value -> cfg.spiritLeapShowClass = value, cfg.spiritLeapShowClass);
        if (hitVisible(x + 12 + columnWidth + gap, row, columnWidth, 19)) return flip(value -> cfg.spiritLeapShowDead = value, cfg.spiritLeapShowDead);
        row += 45;
        int sortWidth = (inner - 16) / 3;
        for (int i = 0; i < OrionSpiritLeap.SORT_NAMES.length; i++) {
            if (hitVisible(x + 12 + (i % 3) * (sortWidth + 8), row + (i / 3) * 23, sortWidth, 19)) {
                cfg.spiritLeapSorting = i;
                save();
                return true;
            }
        }
        row += 54;
        if (hitVisible(x + 76, row, 34, 19)) {
            cfg.spiritLeapScalePercent = Math.clamp(cfg.spiritLeapScalePercent - 10, 50, 200);
            save(); return true;
        }
        if (hitVisible(x + 168, row, 34, 19)) {
            cfg.spiritLeapScalePercent = Math.clamp(cfg.spiritLeapScalePercent + 10, 50, 200);
            save(); return true;
        }
        if (hitVisible(x + 12 + columnWidth + gap, row, columnWidth, 19)) {
            int index = 0;
            for (int i = 0; i < BACKGROUNDS.length; i++) if (BACKGROUNDS[i] == cfg.spiritLeapBackground) index = i + 1;
            cfg.spiritLeapBackground = BACKGROUNDS[index % BACKGROUNDS.length];
            save(); return true;
        }
        return super.mouseClicked(event, doubled);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
        if (mouseY < 41 || mouseY >= height - 18) return super.mouseScrolled(mouseX, mouseY, horizontal, vertical);
        scroll = Math.clamp(scroll - vertical * 24, 0, maxScroll(34, height - 18));
        return true;
    }

    private int maxScroll(int panelY, int bottom) {
        return Math.max(0, panelY + 266 - bottom);
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

    private boolean hitVisible(int x, int y, int w, int h) {
        return y >= 41 && y + h <= height - 18 && hit(x, y, w, h);
    }

    private void toggle(GuiGraphicsExtractor graphics, int x, int y, int w, String text, boolean value, int mouseX, int mouseY, int top, int bottom) {
        button(graphics, x, y, w, (value ? "ON  " : "OFF ") + text, value, mouseX, mouseY, top, bottom);
    }

    private void button(GuiGraphicsExtractor graphics, int x, int y, int w, String text, boolean selected, int mouseX, int mouseY, int top, int bottom) {
        if (y < top || y + 19 > bottom) return;
        boolean hover = mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + 19;
        ConstellationUi.button(graphics, font, x, y, w, 19,
            ConstellationUi.fit(font, text, w - 8), hover, selected);
    }

    private void text(GuiGraphicsExtractor graphics, String value, int x, int y, int colour, int top, int bottom) {
        if (y < top || y + font.lineHeight > bottom) return;
        graphics.text(font, value, x, y, colour, false);
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
