package com.froggylord.constellation.ui;

import com.froggylord.constellation.config.PegasusConfig;
import com.froggylord.constellation.constellation.PegasusParty;
import com.froggylord.constellation.render.ConstellationTheme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.Locale;

// layout behavior ported from Athen (BSD-3-Clause): modules/common/carry/ICarryGUI.kt
public final class CarryTrackerScreen extends Screen {
    private final Screen parent;
    private final PegasusParty tracker;
    private double scroll;
    private String confirmRemovePlayer = "";

    public CarryTrackerScreen(Screen parent, PegasusParty tracker) {
        super(Component.literal("Carry Tracker"));
        this.parent = parent;
        this.tracker = tracker;
    }

    @Override public boolean isPauseScreen() { return false; }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mx, int my, float delta) {
        ConstellationUi.background(g, width, height, delta);
        int w = Math.min(430, width - 30), x = (width - w) / 2, y = 35;
        List<PegasusConfig.CarryData> carries = tracker.activeCarries();
        ConstellationUi.header(g, font, "Carry Tracker", carries.size() + " active", width);
        ConstellationUi.panel(g, x, y, w, height - y - 12);
        g.text(font, ConstellationUi.fit(font, "/carry add <type> <player> <runs> <target> <price>", w - 20),
            x + 10, y + 11, ConstellationTheme.TEXT_MUTED, false);
        g.text(font, ConstellationUi.fit(font, "Left click changes progress. Right click changes total.", w - 20),
            x + 10, y + 24, ConstellationTheme.TEXT_MUTED, false);
        if (carries.isEmpty()) {
            g.text(font, "No active carries", x + (w - font.width("No active carries")) / 2, height / 2, ConstellationTheme.TEXT_MUTED, false);
            return;
        }
        int top = y + 44, bottom = height - 19;
        scroll = Math.clamp(scroll, 0, Math.max(0, carries.size() * 48 - (height - 98)));
        int rowY = top - (int) scroll;
        g.enableScissor(x + 4, top, x + w - 4, bottom);
        for (PegasusConfig.CarryData c : carries) {
            if (rowY + 42 >= top && rowY < bottom) drawRow(g, c, x + 7, rowY, w - 14, mx, my);
            rowY += 48;
        }
        g.disableScissor();
        ConstellationUi.scrollbar(g, x + w - 7, top, bottom - top,
            bottom - top, carries.size() * 48, (int) scroll);
    }

    private void drawRow(GuiGraphicsExtractor g, PegasusConfig.CarryData c, int x, int y, int w, int mx, int my) {
        ConstellationTheme.surface(g, x, y, w, 42, 0xDD1B1B2A, ConstellationTheme.BORDER_SOFT);
        int textWidth = Math.max(24, w - 140);
        g.text(font, ConstellationUi.fit(font, c.player + "  " + c.type + " " + c.target, textWidth), x + 9, y + 6, ConstellationTheme.TEXT, false);
        g.text(font, ConstellationUi.fit(font, c.completed + "/" + c.total + "  price " + money(c.pricePerRun) + "/run", textWidth), x + 9, y + 19, ConstellationTheme.TEXT_MUTED, false);
        g.text(font, ConstellationUi.fit(font, "paid " + money(c.paid) + "/" + money(expected(c)) + " (" + c.paidRuns + " exact runs)", textWidth), x + 9, y + 31, ConstellationTheme.TEXT_MUTED, false);
        button(g, x + w - 122, y + 11, 24, "+", mx, my);
        button(g, x + w - 92, y + 11, 24, "-", mx, my);
        button(g, x + w - 62, y + 11, 54, confirmRemovePlayer.equalsIgnoreCase(c.player) ? "confirm" : "remove", mx, my);
    }

    private void button(GuiGraphicsExtractor g, int x, int y, int w, String label, int mx, int my) {
        ConstellationUi.button(g, font, x, y, w, 20, label, inside(mx, my, x, y, w, 20), false);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean dbl) {
        int w = Math.min(430, width - 30), x = (width - w) / 2;
        int top = 79, bottom = height - 19, rowY = top - (int) scroll;
        int button = event.button();
        for (PegasusConfig.CarryData c : tracker.activeCarries()) {
            int mouseY = (int) event.y();
            if (mouseY >= top && mouseY < bottom
                && inside((int) event.x(), mouseY, x + w - 129, rowY + 11, 24, 20)) {
                confirmRemovePlayer = "";
                if (button == 1) tracker.screenAdjustTotal(c.player, 1); else tracker.screenAdjust(c.player, 1);
                return true;
            }
            if (mouseY >= top && mouseY < bottom
                && inside((int) event.x(), mouseY, x + w - 99, rowY + 11, 24, 20)) {
                confirmRemovePlayer = "";
                if (button == 1) tracker.screenAdjustTotal(c.player, -1); else tracker.screenAdjust(c.player, -1);
                return true;
            }
            if (mouseY >= top && mouseY < bottom
                && inside((int) event.x(), mouseY, x + w - 69, rowY + 11, 54, 20)) {
                if (button != 0) { confirmRemovePlayer = ""; return true; }
                if (confirmRemovePlayer.equalsIgnoreCase(c.player)) { tracker.screenRemove(c.player); confirmRemovePlayer = ""; }
                else confirmRemovePlayer = c.player;
                return true;
            }
            rowY += 48;
        }
        confirmRemovePlayer = "";
        return super.mouseClicked(event, dbl);
    }

    @Override public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        int max = Math.max(0, tracker.activeCarries().size() * 48 - (height - 98));
        scroll = Math.clamp(scroll - sy * 24, 0, max);
        return true;
    }

    private static boolean inside(int mx, int my, int x, int y, int w, int h) { return mx >= x && mx < x + w && my >= y && my < y + h; }
    private static long expected(PegasusConfig.CarryData carry) {
        try { return Math.multiplyExact(carry.pricePerRun, (long) carry.total); }
        catch (ArithmeticException ignored) { return Long.MAX_VALUE; }
    }
    private static String money(long value) { if (value >= 1_000_000) return String.format(Locale.ROOT, "%.2fm", value / 1e6); if (value >= 1_000) return String.format(Locale.ROOT, "%.1fk", value / 1e3); return Long.toString(value); }
    @Override public void onClose() { Minecraft.getInstance().setScreenAndShow(parent); }
}
