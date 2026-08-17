package com.froggylord.constellation.hud;

import com.froggylord.constellation.render.ConstellationTheme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.List;

/** Shared compact panel used by dungeon information HUDs. */
public abstract class ThemedHudWidget implements HudElement {

    protected record Row(String icon, String label, String value, int valueColor) {
        public Row(String icon, String label, String value) {
            this(icon, label, value, ConstellationTheme.TEXT);
        }
    }

    private static final int PAD_X = 5;
    private static final int PAD_Y = 4;
    private static final int TITLE_GAP = 3;
    private static final int ROW_GAP = 2;

    protected abstract String title();
    protected abstract List<Row> rows();

    protected List<Row> previewRows() {
        return rows();
    }

    @Override
    public int width() {
        return dimensions(rows())[0];
    }

    @Override
    public int height() {
        return dimensions(rows())[1];
    }

    @Override
    public int previewWidth() {
        return dimensions(previewRows())[0];
    }

    @Override
    public int previewHeight() {
        return dimensions(previewRows())[1];
    }

    @Override
    public void render(GuiGraphicsExtractor g, int px, int py) {
        drawPanel(g, px, py, rows());
    }

    @Override
    public void renderPreview(GuiGraphicsExtractor g, int px, int py) {
        drawPanel(g, px, py, previewRows());
    }

    private void drawPanel(GuiGraphicsExtractor g, int x, int y, List<Row> content) {
        if (content.isEmpty()) return;
        Font font = Minecraft.getInstance().font;
        int[] size = dimensions(content);
        int w = size[0];
        int h = size[1];

        g.fill(x, y, x + w, y + h, ConstellationTheme.PANEL);
        g.fill(x, y, x + 2, y + h, ConstellationTheme.ACCENT);
        g.fill(x + 2, y, x + w, y + 1, ConstellationTheme.BORDER);

        int textX = x + PAD_X + 2;
        int cursorY = y + PAD_Y - 1;
        g.text(font, title(), textX, cursorY, ConstellationTheme.ACCENT_BRIGHT, true);
        cursorY += font.lineHeight + TITLE_GAP;
        g.fill(textX, cursorY - 2, x + w - PAD_X, cursorY - 1, ConstellationTheme.BORDER);

        for (Row row : content) {
            int rowX = textX;
            if (!row.icon().isEmpty()) {
                g.text(font, row.icon(), rowX, cursorY, ConstellationTheme.ACCENT_BRIGHT, true);
                rowX += font.width(row.icon()) + 3;
            }
            g.text(font, row.label(), rowX, cursorY, ConstellationTheme.TEXT_DIM, true);
            int valueX = x + w - PAD_X - font.width(row.value());
            g.text(font, row.value(), valueX, cursorY, row.valueColor(), true);
            cursorY += font.lineHeight + ROW_GAP;
        }
    }

    private int[] dimensions(List<Row> content) {
        Font font = Minecraft.getInstance().font;
        int inner = font.width(title());
        for (Row row : content) {
            int iconWidth = row.icon().isEmpty() ? 0 : font.width(row.icon()) + 3;
            inner = Math.max(inner, iconWidth + font.width(row.label()) + 12 + font.width(row.value()));
        }
        int w = inner + PAD_X * 2 + 2;
        int h = PAD_Y * 2 + font.lineHeight + TITLE_GAP
            + content.size() * (font.lineHeight + ROW_GAP) - ROW_GAP;
        return new int[]{w, h};
    }
}
