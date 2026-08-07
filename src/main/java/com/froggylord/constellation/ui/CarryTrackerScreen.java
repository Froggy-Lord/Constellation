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

    public CarryTrackerScreen(Screen parent, PegasusParty tracker) {
        super(Component.literal("Carry Tracker"));
        this.parent = parent;
        this.tracker = tracker;
    }

    @Override public boolean isPauseScreen() { return false; }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mx, int my, float delta) {
        g.fill(0, 0, width, height, 0xB8080810);
        int w = Math.min(430, width - 30), x = (width - w) / 2, y = 20;
        g.fill(x, y, x + w, y + 28, 0xEE151524);
        g.text(font, "Carry Tracker", x + 10, y + 10, ConstellationTheme.ACCENT_BRIGHT, false);
        g.text(font, "/carry add <type> <player> <runs> <target> <price> | left: progress, right: total", x + 10, y + 35, ConstellationTheme.TEXT_MUTED, false);
        List<PegasusConfig.CarryData> carries = tracker.activeCarries();
        if (carries.isEmpty()) {
            g.text(font, "No active carries", x + (w - font.width("No active carries")) / 2, height / 2, ConstellationTheme.TEXT_MUTED, false);
            return;
        }
        int rowY = y + 52 - (int) scroll;
        for (PegasusConfig.CarryData c : carries) {
            if (rowY > 48 && rowY < height - 35) drawRow(g, c, x, rowY, w, mx, my);
            rowY += 48;
        }
    }

    private void drawRow(GuiGraphicsExtractor g, PegasusConfig.CarryData c, int x, int y, int w, int mx, int my) {
        g.fill(x, y, x + w, y + 42, 0xDD1B1B2A);
        g.text(font, c.player + "  " + c.type + " " + c.target, x + 9, y + 6, ConstellationTheme.TEXT, false);
        g.text(font, c.completed + "/" + c.total + "  price " + money(c.pricePerRun) + "/run", x + 9, y + 19, ConstellationTheme.TEXT_MUTED, false);
        g.text(font, "paid " + money(c.paid) + "/" + money(expected(c)) + " (" + c.paidRuns + " exact runs)", x + 9, y + 31, ConstellationTheme.TEXT_MUTED, false);
        button(g, x + w - 92, y + 11, 24, "+", 0xFF24543D, mx, my);
        button(g, x + w - 62, y + 11, 24, "-", 0xFF60482A, mx, my);
        button(g, x + w - 32, y + 11, 24, "x", 0xFF5B2931, mx, my);
    }

    private void button(GuiGraphicsExtractor g, int x, int y, int w, String label, int colour, int mx, int my) {
        g.fill(x, y, x + w, y + 20, inside(mx, my, x, y, w, 20) ? 0xFF45455B : colour);
        g.text(font, label, x + (w - font.width(label)) / 2, y + 6, 0xFFFFFFFF, false);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean dbl) {
        int w = Math.min(430, width - 30), x = (width - w) / 2;
        int rowY = 72 - (int) scroll;
        int button = event.button();
        for (PegasusConfig.CarryData c : tracker.activeCarries()) {
            if (inside((int) event.x(), (int) event.y(), x + w - 92, rowY + 11, 24, 20)) {
                if (button == 1) tracker.screenAdjustTotal(c.player, 1); else tracker.screenAdjust(c.player, 1);
                return true;
            }
            if (inside((int) event.x(), (int) event.y(), x + w - 62, rowY + 11, 24, 20)) {
                if (button == 1) tracker.screenAdjustTotal(c.player, -1); else tracker.screenAdjust(c.player, -1);
                return true;
            }
            if (inside((int) event.x(), (int) event.y(), x + w - 32, rowY + 11, 24, 20)) { tracker.screenRemove(c.player); return true; }
            rowY += 48;
        }
        return super.mouseClicked(event, dbl);
    }

    @Override public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        int max = Math.max(0, tracker.activeCarries().size() * 48 - (height - 105));
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
