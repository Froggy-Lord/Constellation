package com.froggylord.constellation.ui;

import com.froggylord.constellation.ConstellationClient;
import com.froggylord.constellation.config.*;
import com.froggylord.constellation.render.NebulaTheme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.*;
import java.util.function.*;

// Matches cryptkit's ModMenuScreen layout. Centered 72% panel, left category rail,
// right card grid with scroll, per-module modal for sub-options.
public class ConfigScreen extends Screen {

    private static class Module {
        final String name, desc, cat;
        final BooleanSupplier get; final Consumer<Boolean> set;
        final List<Runnable> opts = new ArrayList<>(); // sub-options as open/close modal
        float knob = 0, hover = 0;
        Module(String n, String d, String c, BooleanSupplier g, Consumer<Boolean> s) {
            name = n; desc = d; cat = c; get = g; set = s; knob = g.getAsBoolean() ? 1 : 0;
        }
        Module b(Runnable toggle) { opts.add(toggle); return this; }
    }

    private final String constellationId;
    private final Screen parent;
    private final List<Module> modules = new ArrayList<>();

    private String[] cats = {};
    private int selectedCat = 0;
    private String openModuleName = null;
    private Module openModule = null;
    private float modalAnim = 0;

    private float scrollF = 0;
    private int scrollTarget = 0, maxScroll = 0;
    private int lastMx, lastMy;
    private long lastNanos = 0;

    private static final int TB = 34;
    private static final int CARD_H = 44;
    private static final int CARD_GAP = 4;

    public ConfigScreen(String constellationId, Screen parent) {
        super(Component.literal("Constellation — " + constellationId));
        this.constellationId = constellationId;
        this.parent = parent;
        buildModules();
    }

    // ---- layout helpers ----
    private int panelW(int w) { return Math.max(400, Math.min(w, w * 72 / 100)); }
    private int panelH(int h) { return Math.max(280, Math.min(h, h * 82 / 100)); }
    private int sideW(int w) { return Math.max(90, Math.min(120, w * 20 / 100)); }
    private int gridTop() { return TB + 24; }
    private int gridX(int w) { return sideW(w) + 12; }
    private int gridRight(int w) { return w - 10; }
    private int cols(int w) { int a = gridRight(w) - gridX(w) + 6; return Math.max(1, Math.min(3, a / (200 + 6))); }
    private int cardW(int w) { int c = cols(w); return (gridRight(w) - gridX(w) - (c - 1) * CARD_GAP) / c; }

    // ---- module definitions ----
    private void buildModules() {
        modules.clear();
        var cfg = ConstellationClient.cfg();

        switch (constellationId) {
            case "apollo" -> {
                cats = new String[]{"HUD Widgets", "Display"};
                ApolloConfig c = cfg.apollo;
                for (var field : ApolloConfig.class.getFields()) {
                    if (!field.getName().equals("fps") && !field.getName().equals("ping") &&
                        !field.getName().equals("tps") && !field.getName().equals("clock") &&
                        !field.getName().equals("coords") && !field.getName().equals("health") &&
                        !field.getName().equals("mana") && !field.getName().equals("defense") &&
                        !field.getName().equals("speed")) continue;
                    if (field.getType() != ApolloConfig.HudEntry.class) continue;
                    try {
                        ApolloConfig.HudEntry he = (ApolloConfig.HudEntry) field.get(c);
                        String label = field.getName().substring(0, 1).toUpperCase() + field.getName().substring(1);
                        modules.add(new Module(field.getName(), label, "HUD Widgets",
                            () -> he.visible, v -> { he.visible = v; ConstellationClient.saveConfig(); }));
                    } catch (Exception e) {}
                }
                modules.add(new Module("customScoreboard", "Custom scoreboard", "Display",
                    () -> c.customScoreboard, v -> { c.customScoreboard = v; ConstellationClient.saveConfig(); }));
                modules.add(new Module("customTabList", "Fully custom tab list", "Display",
                    () -> c.customTabList, v -> { c.customTabList = v; ConstellationClient.saveConfig(); }));
                modules.add(new Module("compactDamage", "1.2M instead of 1200000", "Display",
                    () -> c.compactDamage, v -> { c.compactDamage = v; ConstellationClient.saveConfig(); }));
            }
            case "phoenix" -> {
                cats = new String[]{"Visual", "Gameplay"};
                PhoenixConfig c = cfg.phoenix;
                String[] visual = {"fullbright","hideLightning","hideFallingBlocks","hideFireOverlay","hideUnderwaterBlur","disableVignette","disableFog","hideStatusEffects"};
                String[] gameplay = {"noHurtCam","noViewBob","autoSprint","etherwarpOverlay","scrollableTooltips","instantSneak","noDeathAnimation","signCalculator","preventPlacingWeapons"};
                var visSet = Set.of(visual);
                for (var field : PhoenixConfig.class.getFields()) {
                    if (field.getName().equals("enabled") || field.getName().equals("version")) continue;
                    if (field.getType() != boolean.class) continue;
                    String cat = visSet.contains(field.getName()) ? "Visual" : "Gameplay";
                    try {
                        boolean val = field.getBoolean(c);
                        String label = field.getName().replaceAll("([A-Z])", " $1").trim();
                        if (!label.isEmpty()) label = label.substring(0, 1).toUpperCase() + label.substring(1);
                        modules.add(new Module(field.getName(), label, cat,
                            () -> { try { return field.getBoolean(c); } catch (Exception e) { return false; } },
                            v -> { try { field.setBoolean(c, v); ConstellationClient.saveConfig(); } catch (Exception e) {} }));
                    } catch (Exception e) {}
                }
            }
            case "cassiopeia" -> {
                cats = new String[]{"Filters", "Chat", "Commands", "Party"};
                CassiopeiaConfig c = cfg.cassiopeia;
                // Filters
                for (var field : CassiopeiaConfig.class.getFields()) {
                    String n = field.getName();
                    if (!n.startsWith("clean")) continue;
                    if (field.getType() != boolean.class) continue;
                    try {
                        boolean val = field.getBoolean(c);
                        String label = n.substring(5).replaceAll("([A-Z])", " $1").trim();
                        if (!label.isEmpty()) label = label.substring(0, 1).toUpperCase() + label.substring(1);
                        modules.add(new Module(n, label, "Filters",
                            () -> { try { return field.getBoolean(c); } catch (Exception e) { return false; } },
                            v -> { try { field.setBoolean(c, v); ConstellationClient.saveConfig(); } catch (Exception e) {} }));
                    } catch (Exception e) {}
                }
                modules.add(new Module("timestamps", "[HH:MM] prefix", "Chat",
                    () -> c.timestamps, v -> { c.timestamps = v; ConstellationClient.saveConfig(); }));
                modules.add(new Module("clickableLinks", "URLs blue + underlined", "Chat",
                    () -> c.clickableLinks, v -> { c.clickableLinks = v; ConstellationClient.saveConfig(); }));
                modules.add(new Module("copyOnRightClick", "Right-click to copy", "Chat",
                    () -> c.copyOnRightClick, v -> { c.copyOnRightClick = v; ConstellationClient.saveConfig(); }));
                modules.add(new Module("mentionAlert", "Ping + sound on name", "Chat",
                    () -> c.mentionAlert, v -> { c.mentionAlert = v; ConstellationClient.saveConfig(); }));
                modules.add(new Module("floorShortcuts", "/f1-/f7, /m1-/m7", "Commands",
                    () -> c.floorShortcuts, v -> { c.floorShortcuts = v; ConstellationClient.saveConfig(); }));
                modules.add(new Module("warpShortcuts", "/h, /i, /dh, /l", "Commands",
                    () -> c.warpShortcuts, v -> { c.warpShortcuts = v; ConstellationClient.saveConfig(); }));
                modules.add(new Module("warpShortener", "/drag -> /warp drag", "Commands",
                    () -> c.warpShortener, v -> { c.warpShortener = v; ConstellationClient.saveConfig(); }));
                modules.add(new Module("partyShortcuts", "/pi, /pw, /pl", "Party",
                    () -> c.partyShortcuts, v -> { c.partyShortcuts = v; ConstellationClient.saveConfig(); }));
                modules.add(new Module("partyTriggers", "!warp, !join, !dt", "Party",
                    () -> c.partyTriggers, v -> { c.partyTriggers = v; ConstellationClient.saveConfig(); }));
            }
        }
        // sync knob state
        for (Module m : modules) m.knob = m.get.getAsBoolean() ? 1 : 0;
    }

    @Override public boolean isPauseScreen() { return false; }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float delta) {
        Minecraft mc = Minecraft.getInstance();
        int fullW = mc.getWindow().getGuiScaledWidth(), fullH = mc.getWindow().getGuiScaledHeight();
        int w = panelW(fullW), h = panelH(fullH);
        int px = (fullW - w) / 2, py = (fullH - h) / 2;
        long now = System.nanoTime();
        float dt = lastNanos == 0 ? 0.016f : Math.min(0.05f, (now - lastNanos) / 1_000_000_000f);
        lastNanos = now;
        lastMx = mx; lastMy = my;

        // dim world behind, shift into panel
        g.fill(0, 0, fullW, fullH, 0x880A0A14);
        g.pose().pushMatrix();
        g.pose().translate(px, py);
        mx -= px; my -= py;

        // panel background
        g.fill(0, 0, w, h, 0xF212121F);

        // ---- sidebar ----
        int sw = sideW(w), navY0 = TB + 4;
        g.fill(0, 0, sw, h, 0xFF0E0E1A);
        g.fill(sw - 1, 0, sw, h, NebulaTheme.ACCENT_DIM);
        for (int i = 0; i < cats.length; i++) {
            int y = navY0 + i * 24;
            boolean sel = i == selectedCat;
            boolean hov = mx >= 0 && mx < sw && my >= y && my < y + 24;
            if (sel) g.fill(0, y, sw, y + 24, 0xFF1A1A28);
            else if (hov) g.fill(0, y, sw, y + 24, 0xFF252535);
            if (sel) g.fill(0, y, 3, y + 24, NebulaTheme.ACCENT_GOLD);
            int col = sel ? NebulaTheme.ACCENT_BRIGHT : (hov ? NebulaTheme.STAR_WHITE : NebulaTheme.STAR_MUTED);
            g.text(mc.font, cats[i], 12, y + 7, col, false);
        }

        // ---- top bar ----
        g.fill(0, 0, w, TB, 0xFF0E0E1A);
        g.fill(0, TB - 1, w, TB, NebulaTheme.ACCENT_DIM);
        g.text(mc.font, "✧ " + constellationId, 12, 10, NebulaTheme.ACCENT_GOLD, false);
        String esc = "esc to close";
        g.text(mc.font, esc, w - mc.font.width(esc) - 10, 12, NebulaTheme.STAR_MUTED, false);

        // ---- section header ----
        var vis = visibleModules();
        g.text(mc.font, cats[selectedCat], gridX(w), TB + 8, NebulaTheme.STAR_WHITE, false);
        String count = vis.size() + " modules";
        g.text(mc.font, count, gridX(w) + mc.font.width(cats[selectedCat]) + 10, TB + 9, NebulaTheme.STAR_MUTED, false);

        // ---- card grid ----
        int cols = cols(w), cardW = cardW(w);
        int rows = (vis.size() + cols - 1) / cols;
        int contentH = rows * (CARD_H + CARD_GAP);
        int viewH = h - gridTop() - 6;
        maxScroll = Math.max(0, contentH - viewH);
        if (scrollTarget > maxScroll) scrollTarget = maxScroll;
        if (scrollTarget < 0) scrollTarget = 0;
        scrollF += (scrollTarget - scrollF) * Math.min(1, dt * 16);
        if (Math.abs(scrollF - scrollTarget) < 0.5f) scrollF = scrollTarget;

        g.enableScissor(sw, gridTop(), w, h);
        int gx = gridX(w), gTop = gridTop(), sc = Math.round(scrollF);
        for (int idx = 0; idx < vis.size(); idx++) {
            Module m = vis.get(idx);
            int colI = idx % cols, rowI = idx / cols;
            int cx = gx + colI * (cardW + CARD_GAP);
            int cy = gTop + rowI * (CARD_H + CARD_GAP) - sc;
            boolean hov = mx >= cx && mx < cx + cardW && my >= cy && my < cy + CARD_H && my >= gTop;
            drawCard(g, m, cx, cy, cardW, hov, dt);
        }
        g.disableScissor();

        // ---- scrollbar ----
        if (maxScroll > 0) {
            int tx = w - 5, barH = Math.max(20, viewH * viewH / contentH);
            g.fill(tx, gTop, tx + 3, gTop + viewH, 0xFF15111f);
            int barY = gTop + (int)((viewH - barH) * (scrollF / maxScroll));
            g.fill(tx, barY, tx + 3, barY + barH, NebulaTheme.ACCENT_DIM);
        }

        // ---- modal (open module) ----
        boolean modalUp = openModuleName != null;
        modalAnim += ((modalUp ? 1f : 0f) - modalAnim) * Math.min(1, dt * 14);
        if (selectedCat >= 0 && selectedCat < cats.length && modalAnim > 0.01f && openModule != null) {
            // simple info panel below the card
            int mx2 = w / 2 - 160, my2 = h / 2 - 100;
            g.fill(mx2, my2, mx2 + 320, my2 + 200, 0xF21A1A28);
            g.fill(mx2, my2, mx2 + 320, my2 + 2, NebulaTheme.ACCENT_GOLD);
            g.text(mc.font, openModule.name.replaceAll("([A-Z])", " $1").trim(), mx2 + 8, my2 + 8, NebulaTheme.ACCENT_BRIGHT, false);
            g.text(mc.font, openModule.desc, mx2 + 8, my2 + 22, NebulaTheme.STAR_MUTED, false);
            if (!openModule.opts.isEmpty()) {
                int oy = my2 + 40;
                for (Runnable r : openModule.opts) {
                    g.text(mc.font, "· sub-option", mx2 + 8, oy, NebulaTheme.STAR_WHITE, false);
                    oy += 14;
                }
            }
            String ok = "click anywhere to close";
            g.text(mc.font, ok, mx2 + 320 - mc.font.width(ok) - 8, my2 + 190, NebulaTheme.STAR_MUTED, false);
        }

        // ---- panel border ----
        g.fill(0, 0, w, 1, NebulaTheme.ACCENT_GOLD);
        g.fill(0, h - 1, w, h, NebulaTheme.ACCENT_DIM);
        g.fill(0, 0, 1, h, NebulaTheme.ACCENT_DIM);
        g.fill(w - 1, 0, w, h, NebulaTheme.ACCENT_DIM);
        g.pose().popMatrix();
    }

    private void drawCard(GuiGraphicsExtractor g, Module m, int cx, int cy, int cardW, boolean hov, float dt) {
        boolean on = m.get.getAsBoolean();
        m.knob += ((on ? 1f : 0f) - m.knob) * Math.min(1, dt * 16);
        m.hover += ((hov ? 1f : 0f) - m.hover) * Math.min(1, dt * 14);
        int fill = on ? 0xFF222240 : (hov ? 0xFF252535 : 0xFF1A1A28);
        g.fill(cx, cy, cx + cardW, cy + CARD_H, fill);
        // accent knob
        int knobCol = lerp(0xFF333333, NebulaTheme.ACCENT_GOLD, m.knob);
        g.fill(cx, cy, cx + cardW, cy + 3, knobCol);
        String name = m.name.replaceAll("([A-Z])", " $1").trim();
        if (!name.isEmpty()) name = name.substring(0, 1).toUpperCase() + name.substring(1);
        if (name.length() > 18) name = name.substring(0, 17) + "…";
        g.text(Minecraft.getInstance().font, name, cx + 5, cy + 8,
            m.knob > 0.5f ? NebulaTheme.ACCENT_BRIGHT : NebulaTheme.STAR_WHITE, false);
        g.text(Minecraft.getInstance().font, m.desc, cx + 5, cy + 22, NebulaTheme.STAR_MUTED, false);
    }

    private List<Module> visibleModules() {
        if (cats.length == 0) return modules;
        List<Module> out = new ArrayList<>();
        for (Module m : modules) if (m.cat.equals(cats[selectedCat])) out.add(m);
        return out;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean dbl) {
        int mx = lastMx, my = lastMy;
        int fullW = Minecraft.getInstance().getWindow().getGuiScaledWidth(), fullH = Minecraft.getInstance().getWindow().getGuiScaledHeight();
        int w = panelW(fullW), h = panelH(fullH);
        int px = (fullW - w) / 2, py = (fullH - h) / 2;
        mx -= px; my -= py;

        // sidebar click
        int sw = sideW(w), navY0 = TB + 4;
        for (int i = 0; i < cats.length; i++) {
            int y = navY0 + i * 24;
            if (mx >= 0 && mx < sw && my >= y && my < y + 24) {
                if (i == selectedCat && openModuleName == null) {
                    selectedCat = i;
                } else {
                    selectedCat = i; openModuleName = null; openModule = null;
                }
                return true;
            }
        }

        // card click
        var vis = visibleModules();
        int cols = cols(w), cardW = cardW(w);
        int gx = gridX(w), gTop = gridTop(), sc = Math.round(scrollF);
        for (int idx = 0; idx < vis.size(); idx++) {
            Module m = vis.get(idx);
            int colI = idx % cols, rowI = idx / cols;
            int cx = gx + colI * (cardW + CARD_GAP);
            int cy = gTop + rowI * (CARD_H + CARD_GAP) - sc;
            if (mx >= cx && mx < cx + cardW && my >= cy && my < cy + CARD_H && my >= gTop) {
                if (m.opts.isEmpty()) {
                    boolean next = !m.get.getAsBoolean();
                    m.set.accept(next);
                    m.knob = next ? 1 : 0;
                } else {
                    openModuleName = m.name.equals(openModuleName) ? null : m.name;
                    openModule = openModuleName != null ? m : null;
                }
                return true;
            }
        }

        // modal dismiss
        if (openModuleName != null) { openModuleName = null; openModule = null; return true; }

        return super.mouseClicked(event, dbl);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.key() == GLFW.GLFW_KEY_ESCAPE) {
            if (openModuleName != null) { openModuleName = null; openModule = null; return true; }
            onClose();
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public void onClose() {
        var p = parent;
        Minecraft.getInstance().execute(() -> Minecraft.getInstance().setScreenAndShow(p));
    }

    private static int lerp(int a, int b, float t) {
        t = Math.clamp(t, 0, 1);
        int ar = (a >> 16) & 0xFF, ag = (a >> 8) & 0xFF, ab = a & 0xFF;
        int br = (b >> 16) & 0xFF, bg = (b >> 8) & 0xFF, bb = b & 0xFF;
        return 0xFF000000 | ((int)(ar + (br - ar) * t) << 16) | ((int)(ag + (bg - ag) * t) << 8) | (int)(ab + (bb - ab) * t);
    }
}
