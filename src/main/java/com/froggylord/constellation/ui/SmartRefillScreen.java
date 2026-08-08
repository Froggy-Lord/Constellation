package com.froggylord.constellation.ui;

import com.froggylord.constellation.ConstellationClient;
import com.froggylord.constellation.constellation.SmartRefill;
import com.froggylord.constellation.render.ConstellationTheme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class SmartRefillScreen extends Screen {
    private final Screen parent;
    private double scroll;

    public SmartRefillScreen(Screen parent) {
        super(Component.literal("Smart Refill"));
        this.parent = parent;
    }

    @Override public boolean isPauseScreen() { return false; }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mx, int my, float delta) {
        ConstellationUi.background(g, width, height, delta);
        int w = Math.min(430, width - 24), x = (width - w) / 2;
        String mode = ConstellationClient.cfg().orion.smartRefillOneAtATime ? "one item per press" : "paced refill all";
        ConstellationUi.header(g, font, "Smart Sack Refill", mode, width);
        ConstellationUi.panel(g, x, 36, w, height - 48);
        g.text(font, ConstellationUi.fit(font, "Pulls the enabled item with the lowest fill ratio.", w - 20), x + 10, 46, ConstellationTheme.TEXT, false);
        g.text(font, ConstellationUi.fit(font, "Bind Smart Refill in Controls. /refill all handles every deficit.", w - 20), x + 10, 59, ConstellationTheme.TEXT_MUTED, false);
        button(g, x + w - 126, 74, 116, mode, mx, my, true);
        int top = 101, bottom = height - 19;
        List<Map.Entry<String, Integer>> rows = rows();
        scroll = Math.clamp(scroll, 0, Math.max(0, rows.size() * 38 - (height - 120)));
        int y = top - (int) scroll;
        g.enableScissor(x + 4, top, x + w - 4, bottom);
        for (Map.Entry<String, Integer> e : rows) {
            if (y >= top && y + 32 <= bottom) row(g, e.getKey(), e.getValue(), x + 7, y, w - 14, mx, my);
            y += 38;
        }
        g.disableScissor();
        ConstellationUi.scrollbar(g, x + w - 7, top, bottom - top, bottom - top, rows.size() * 38, (int) scroll);
    }

    private void row(GuiGraphicsExtractor g, String id, int target, int x, int y, int w, int mx, int my) {
        boolean enabled = SmartRefill.enabled(id);
        ConstellationTheme.surface(g, x, y, w, 32, enabled ? 0xD8203C34 : 0xD81B1B28,
            enabled ? 0xFF315D4C : ConstellationTheme.BORDER_SOFT);
        int textWidth = Math.max(24, w - 134);
        g.text(font, ConstellationUi.fit(font, display(id), textWidth), x + 9, y + 7, enabled ? 0xFF77FFAA : ConstellationTheme.TEXT_MUTED, false);
        g.text(font, ConstellationUi.fit(font, "target " + target, textWidth), x + 9, y + 19, ConstellationTheme.TEXT_MUTED, false);
        button(g, x + w - 116, y + 6, 42, enabled ? "on" : "off", mx, my, enabled);
        button(g, x + w - 68, y + 6, 26, "-", mx, my, false);
        button(g, x + w - 36, y + 6, 26, "+", mx, my, false);
    }

    private void button(GuiGraphicsExtractor g, int x, int y, int w, String label, int mx, int my, boolean active) {
        ConstellationUi.button(g, font, x, y, w, 20, label, inside(mx, my, x, y, w, 20), active);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean dbl) {
        int w = Math.min(430, width - 24), x = (width - w) / 2, top = 101, bottom = height - 19, y = top - (int) scroll;
        if (inside((int) event.x(), (int) event.y(), x + w - 126, 74, 116, 20)) {
            var cfg = ConstellationClient.cfg().orion;
            cfg.smartRefillOneAtATime = !cfg.smartRefillOneAtATime;
            ConstellationClient.saveConfig();
            return true;
        }
        for (Map.Entry<String, Integer> e : rows()) {
            int mx = (int) event.x(), my = (int) event.y();
            boolean visible = y >= top && y + 32 <= bottom;
            if (visible && inside(mx, my, x + 7 + w - 14 - 116, y + 6, 42, 20)) { SmartRefill.toggle(e.getKey()); return true; }
            int step = e.getValue() <= 16 ? 1 : event.button() == 1 ? 16 : 8;
            if (visible && inside(mx, my, x + 7 + w - 14 - 68, y + 6, 26, 20)) { SmartRefill.change(e.getKey(), -step); return true; }
            if (visible && inside(mx, my, x + 7 + w - 14 - 36, y + 6, 26, 20)) { SmartRefill.change(e.getKey(), step); return true; }
            y += 38;
        }
        return super.mouseClicked(event, dbl);
    }

    @Override public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        int max = Math.max(0, rows().size() * 38 - (height - 120));
        scroll = Math.clamp(scroll - sy * 24, 0, max);
        return true;
    }

    private static List<Map.Entry<String, Integer>> rows() { return new ArrayList<>(SmartRefill.targets().entrySet()); }
    private static boolean inside(int mx, int my, int x, int y, int w, int h) { return mx >= x && mx < x + w && my >= y && my < y + h; }
    private static String display(String id) { String s = id.toLowerCase(Locale.ROOT).replace('_', ' '); return Character.toUpperCase(s.charAt(0)) + s.substring(1); }
    @Override public void onClose() { ConstellationClient.saveConfig(); Minecraft.getInstance().setScreenAndShow(parent); }
}
