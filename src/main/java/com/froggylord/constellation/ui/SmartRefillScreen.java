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
        g.fill(0, 0, width, height, 0xD0080810);
        int w = Math.min(430, width - 24), x = (width - w) / 2;
        g.fill(x, 14, x + w, 43, 0xEE151524);
        g.text(font, "Smart Sack Refill", x + 10, 25, ConstellationTheme.ACCENT_BRIGHT, false);
        g.text(font, "The key pulls whichever enabled item has the lowest fill ratio.", x + 10, 50, ConstellationTheme.TEXT_MUTED, false);
        g.text(font, "Bind Smart Refill in Minecraft Controls. /refill all handles every deficit.", x + 10, 63, ConstellationTheme.TEXT_MUTED, false);
        String mode = ConstellationClient.cfg().orion.smartRefillOneAtATime ? "one item per press" : "paced refill all";
        button(g, x + w - 126, 20, 116, mode, 0xFF292944, mx, my);
        int y = 82 - (int) scroll;
        for (Map.Entry<String, Integer> e : rows()) {
            if (y > 68 && y < height - 30) row(g, e.getKey(), e.getValue(), x, y, w, mx, my);
            y += 38;
        }
    }

    private void row(GuiGraphicsExtractor g, String id, int target, int x, int y, int w, int mx, int my) {
        boolean enabled = SmartRefill.enabled(id);
        g.fill(x, y, x + w, y + 32, enabled ? 0xD8203C34 : 0xD81B1B28);
        g.text(font, display(id), x + 9, y + 7, enabled ? 0xFF77FFAA : ConstellationTheme.TEXT_MUTED, false);
        g.text(font, "target " + target, x + 9, y + 19, ConstellationTheme.TEXT_MUTED, false);
        button(g, x + w - 116, y + 6, 42, enabled ? "on" : "off", enabled ? 0xFF24543D : 0xFF343442, mx, my);
        button(g, x + w - 68, y + 6, 26, "-", 0xFF60482A, mx, my);
        button(g, x + w - 36, y + 6, 26, "+", 0xFF24543D, mx, my);
    }

    private void button(GuiGraphicsExtractor g, int x, int y, int w, String label, int colour, int mx, int my) {
        g.fill(x, y, x + w, y + 20, inside(mx, my, x, y, w, 20) ? 0xFF4A4A60 : colour);
        g.text(font, label, x + (w - font.width(label)) / 2, y + 6, 0xFFFFFFFF, false);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean dbl) {
        int w = Math.min(430, width - 24), x = (width - w) / 2, y = 82 - (int) scroll;
        if (inside((int) event.x(), (int) event.y(), x + w - 126, 20, 116, 20)) {
            var cfg = ConstellationClient.cfg().orion;
            cfg.smartRefillOneAtATime = !cfg.smartRefillOneAtATime;
            ConstellationClient.saveConfig();
            return true;
        }
        for (Map.Entry<String, Integer> e : rows()) {
            int mx = (int) event.x(), my = (int) event.y();
            if (inside(mx, my, x + w - 116, y + 6, 42, 20)) { SmartRefill.toggle(e.getKey()); return true; }
            int step = e.getValue() <= 16 ? 1 : event.button() == 1 ? 16 : 8;
            if (inside(mx, my, x + w - 68, y + 6, 26, 20)) { SmartRefill.change(e.getKey(), -step); return true; }
            if (inside(mx, my, x + w - 36, y + 6, 26, 20)) { SmartRefill.change(e.getKey(), step); return true; }
            y += 38;
        }
        return super.mouseClicked(event, dbl);
    }

    @Override public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        int max = Math.max(0, rows().size() * 38 - (height - 115));
        scroll = Math.clamp(scroll - sy * 24, 0, max);
        return true;
    }

    private static List<Map.Entry<String, Integer>> rows() { return new ArrayList<>(SmartRefill.targets().entrySet()); }
    private static boolean inside(int mx, int my, int x, int y, int w, int h) { return mx >= x && mx < x + w && my >= y && my < y + h; }
    private static String display(String id) { String s = id.toLowerCase(Locale.ROOT).replace('_', ' '); return Character.toUpperCase(s.charAt(0)) + s.substring(1); }
    @Override public void onClose() { ConstellationClient.saveConfig(); Minecraft.getInstance().setScreenAndShow(parent); }
}
