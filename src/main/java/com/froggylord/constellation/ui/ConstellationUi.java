package com.froggylord.constellation.ui;

import com.froggylord.constellation.render.ConstellationTheme;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

public final class ConstellationUi {
    private ConstellationUi() {}

    public static void background(GuiGraphicsExtractor graphics, int width, int height, float delta) {
        SpaceBackground.renderConfig(graphics, width, height, delta);
        graphics.fill(0, 0, width, height, 0xB8080814);
    }

    public static void header(GuiGraphicsExtractor graphics, Font font, String title, String detail, int width) {
        graphics.fill(0, 0, width, 27, 0xF20E0E1A);
        graphics.fill(0, 26, width, 27, ConstellationTheme.BORDER_SOFT);
        graphics.fill(0, 26, Math.min(72, width), 27, ConstellationTheme.ACCENT);
        graphics.text(font, title, 10, 9, ConstellationTheme.ACCENT_BRIGHT, false);
        if (detail != null && !detail.isBlank())
            graphics.text(font, detail, width - font.width(detail) - 10, 9, ConstellationTheme.TEXT_MUTED, false);
    }

    public static void panel(GuiGraphicsExtractor graphics, int x, int y, int width, int height) {
        ConstellationTheme.surface(graphics, x, y, width, height, 0xE6121222, ConstellationTheme.BORDER_SOFT);
    }

    public static void button(GuiGraphicsExtractor graphics, Font font, int x, int y, int width, int height,
                              String text, boolean hover, boolean active) {
        ConstellationTheme.button(graphics, x, y, width, height, hover, active);
        int color = active || hover ? ConstellationTheme.ACCENT_BRIGHT : ConstellationTheme.TEXT;
        graphics.text(font, text, x + (width - font.width(text)) / 2, y + (height - font.lineHeight) / 2 + 1, color, false);
    }

    public static void scrollbar(GuiGraphicsExtractor graphics, int x, int y, int height,
                                 int viewport, int content, int scroll) {
        if (content <= viewport || height <= 0) return;
        int thumb = Math.max(18, viewport * height / content);
        int maxScroll = Math.max(1, content - viewport);
        int thumbY = y + (height - thumb) * Math.clamp(scroll, 0, maxScroll) / maxScroll;
        graphics.fill(x, y, x + 3, y + height, 0xAA151526);
        graphics.fill(x, thumbY, x + 3, thumbY + thumb, ConstellationTheme.ACCENT_DIM);
    }

    public static String fit(Font font, String value, int width) {
        if (font.width(value) <= width) return value;
        return font.plainSubstrByWidth(value, Math.max(1, width - font.width("..."))) + "...";
    }
}
