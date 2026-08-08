package com.froggylord.constellation.ui;

import com.froggylord.constellation.render.ConstellationIcons;
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
        int available = Math.max(24, width - 20);
        String shownDetail = detail == null || detail.isBlank() ? "" : fit(font, detail, Math.max(24, available / 2));
        int titleWidth = shownDetail.isBlank() ? available : Math.max(24, available - font.width(shownDetail) - 12);
        graphics.text(font, fit(font, title, titleWidth), 10, 9, ConstellationTheme.ACCENT_BRIGHT, false);
        if (!shownDetail.isBlank())
            graphics.text(font, shownDetail, width - font.width(shownDetail) - 10, 9, ConstellationTheme.TEXT_MUTED, false);
    }

    public static void panel(GuiGraphicsExtractor graphics, int x, int y, int width, int height) {
        ConstellationTheme.surface(graphics, x, y, width, height, 0xE6121222, ConstellationTheme.BORDER_SOFT);
    }

    public static void button(GuiGraphicsExtractor graphics, Font font, int x, int y, int width, int height,
                              String text, boolean hover, boolean active) {
        ConstellationTheme.button(graphics, x, y, width, height, hover, active);
        int color = active || hover ? ConstellationTheme.ACCENT_BRIGHT : ConstellationTheme.TEXT;
        // ported from Skyblocker (LGPL-3.0-or-later): utils/render/gui/CyclingIconButtonWidget.java
        String icon = actionIcon(text);
        boolean drawIcon = icon != null && height >= 18 && width >= font.width(text) + 25;
        int contentWidth = font.width(text) + (drawIcon ? 19 : 0);
        int contentX = x + (width - contentWidth) / 2;
        if (drawIcon) {
            ConstellationIcons.drawAction(graphics, icon, contentX, y + (height - 16) / 2, 16);
            contentX += 19;
        }
        graphics.text(font, text, contentX, y + (height - font.lineHeight) / 2 + 1, color, false);
    }

    private static String actionIcon(String text) {
        String value = text == null ? "" : text.strip().toLowerCase(java.util.Locale.ROOT);
        if (value.startsWith("reset") || value.startsWith("refresh") || value.startsWith("reload")) return "reset";
        if (value.startsWith("save") || value.equals("done") || value.startsWith("apply")) return "save";
        if (value.startsWith("delete") || value.startsWith("remove") || value.startsWith("clear")) return "delete";
        if (value.startsWith("sort")) return "sort";
        if (value.startsWith("add") || value.startsWith("new") || value.startsWith("create")) return "add";
        if (value.startsWith("edit") || value.startsWith("rename")) return "edit";
        if (value.startsWith("export") || value.startsWith("copy")) return "export";
        if (value.startsWith("import") || value.startsWith("load")) return "import";
        if (value.startsWith("back")) return "back";
        if (value.startsWith("close") || value.startsWith("cancel")) return "close";
        if (value.startsWith("filter")) return "filter";
        if (value.startsWith("settings") || value.startsWith("config")) return "settings";
        if (value.startsWith("info") || value.startsWith("stats")) return "info";
        return null;
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
