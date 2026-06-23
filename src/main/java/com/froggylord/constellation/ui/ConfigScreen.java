package com.froggylord.constellation.ui;

import com.froggylord.constellation.ConstellationClient;
import com.froggylord.constellation.config.*;
import com.froggylord.constellation.render.ConstellationTheme;
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

public class ConfigScreen extends Screen {

    static class Module {
        record SubOpt(String label, BooleanSupplier get, Consumer<Boolean> set) {}
        final String name, desc, cat;
        final BooleanSupplier get; final Consumer<Boolean> set;
        final List<SubOpt> subs = new ArrayList<>();
        com.froggylord.constellation.config.BaseConfigGroup backing;
        float knob = 0, hover = 0;
        Module(String n, String d, String c, BooleanSupplier g, Consumer<Boolean> s) {
            name = n; desc = d; cat = c; get = g; set = s; knob = g.getAsBoolean() ? 1 : 0;
        }
        
        Module b(String l, BooleanSupplier g, Consumer<Boolean> s) { subs.add(new SubOpt(l, g, s)); return this; }
        
        Module sub(String l, boolean def) {
            String key = name + "." + l;
            subs.add(new SubOpt(l,
                () -> backing != null && backing.getSub(key, def),
                v -> { if (backing != null) { backing.setSub(key, v); ConstellationClient.saveConfig(); } }));
            return this;
        }
    }

    private final String constellationId;
    private final Screen parent;
    private final List<Module> modules = new ArrayList<>();
    private com.froggylord.constellation.config.BaseConfigGroup activeConfig;

    private String[] cats = {};
    private int selectedCat = 0;
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

    
    
    private static String autoLabel(String camel) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < camel.length(); i++) {
            char c = camel.charAt(i);
            if (i > 0 && Character.isUpperCase(c)) sb.append(' ');
            sb.append(i == 0 ? Character.toUpperCase(c) : Character.toLowerCase(c));
        }
        return sb.toString().trim();
    }

    private int panelW(int w) { return Math.min(w - 20, Math.max(Math.min(460, w - 20), w * 68 / 100)); }
    private int panelH(int h) { return Math.min(h - 20, Math.max(Math.min(300, h - 20), h * 78 / 100)); }
    private int sideW(int w) { return Math.max(70, Math.min(100, panelW(w) * 18 / 100)); }
    private int gridTop() { return TB + 24; }
    private int gridX(int w) { return sideW(w) + 12; }
    private int gridRight(int w) { return panelW(w) - 10; }
    private int cols(int w) { int a = gridRight(w) - gridX(w) + 4; return Math.max(1, Math.min(4, a / (210 + 4))); }
    private int cardW(int w) { int c = cols(w); return (gridRight(w) - gridX(w) - (c - 1) * CARD_GAP) / c; }

    private void buildModules() {
        modules.clear();
        var cfg = ConstellationClient.cfg();

        switch (constellationId) {
            case "apollo" -> {
                cats = new String[]{"HUD", "Display"};
                ApolloConfig c = cfg.apollo;
                var names = List.of("fps","ping","tps","clock","coords","health","mana","defense","speed");
                for (String n : names) {
                    try {
                        var field = ApolloConfig.class.getField(n);
                        ApolloConfig.HudEntry he = (ApolloConfig.HudEntry) field.get(c);
                        String label = n.substring(0,1).toUpperCase() + n.substring(1);
                        modules.add(new Module(n, label, "HUD",
                            () -> he.visible, v -> { he.visible = v; ConstellationClient.saveConfig(); })
                            .b("Show label", () -> he.visible, v -> he.visible = v)
                            .sub("Compact mode", false));
                    } catch (Exception e) {}
                }
                modules.add(new Module("customScoreboard", "Replace vanilla sidebar", "Display",
                    () -> c.customScoreboard, v -> { c.customScoreboard = v; ConstellationClient.saveConfig(); })
                    .b("Clean design", () -> c.customScoreboard, v -> c.customScoreboard = v)
                    .sub("Hide red scores", false));
                modules.add(new Module("customTabList", "Replace vanilla tab list", "Display",
                    () -> c.customTabList, v -> { c.customTabList = v; ConstellationClient.saveConfig(); })
                    .b("Compact mode", () -> c.customTabList, v -> c.customTabList = v)
                    .sub("Hide NPCs", false));
                modules.add(new Module("compactDamage", "Shorten damage numbers", "Display",
                    () -> c.compactDamage, v -> { c.compactDamage = v; ConstellationClient.saveConfig(); })
                    .b("Show full in boss", () -> c.compactDamage, v -> c.compactDamage = v)
                    .sub("1.2M format", true));
                modules.add(new Module("rainbowActionBar", "Rainbow action bar", "Display",
                    () -> c.rainbowActionBar, v -> { c.rainbowActionBar = v; ConstellationClient.saveConfig(); })
                    .b("Speed", () -> c.rainbowActionBar, v -> c.rainbowActionBar = v)
                    .sub("Pulsing", false));
            }
            case "phoenix" -> {
                cats = new String[]{"Visual", "Gameplay"};
                PhoenixConfig c = cfg.phoenix;
                var visFields = Set.of("fullbright","hideLightning","hideFallingBlocks","hideFireOverlay","hideUnderwaterBlur","disableVignette","disableFog","hideStatusEffects");
                for (var field : PhoenixConfig.class.getFields()) {
                    if (field.getName().equals("enabled") || field.getName().equals("version")) continue;
                    if (field.getType() != boolean.class) continue;
                    String cat = visFields.contains(field.getName()) ? "Visual" : "Gameplay";
                    try {
                        boolean val = field.getBoolean(c);
                        String label = field.getName().replaceAll("([A-Z])", " $1").trim();
                        if (!label.isEmpty()) label = label.substring(0,1).toUpperCase() + label.substring(1);
                        modules.add(new Module(field.getName(), label, cat,
                            () -> { try { return field.getBoolean(c); } catch (Exception e) { return false; } },
                            v -> { try { field.setBoolean(c, v); ConstellationClient.saveConfig(); } catch (Exception e) {} })
                            .b("Master", () -> { try { return field.getBoolean(c); } catch (Exception e) { return false; } },
                                v -> { try { field.setBoolean(c, v); ConstellationClient.saveConfig(); } catch (Exception e) {} })
                            .sub("Also in dungeons", false));
                    } catch (Exception e) {}
                }
            }
            case "cassiopeia" -> {
                cats = new String[]{"Filters", "Chat", "Commands", "Party"};
                CassiopeiaConfig c = cfg.cassiopeia;
                for (var field : CassiopeiaConfig.class.getFields()) {
                    String n = field.getName();
                    if (!n.startsWith("clean")) continue;
                    if (field.getType() != boolean.class) continue;
                    try {
                        boolean val = field.getBoolean(c);
                        String label = n.substring(5).replaceAll("([A-Z])", " $1").trim();
                        if (!label.isEmpty()) label = label.substring(0,1).toUpperCase() + label.substring(1);
                        modules.add(new Module(n, label, "Filters",
                            () -> { try { return field.getBoolean(c); } catch (Exception e) { return false; } },
                            v -> { try { field.setBoolean(c, v); ConstellationClient.saveConfig(); } catch (Exception e) {} })
                            .b("In dungeons", () -> { try { return field.getBoolean(c); } catch (Exception e) { return false; } },
                                v -> { try { field.setBoolean(c, v); ConstellationClient.saveConfig(); } catch (Exception e) {} })
                            .sub("On private island", false));
                    } catch (Exception e) {}
                }
                modules.add(new Module("timestamps", "[HH:MM] on every line", "Chat",
                    () -> c.timestamps, v -> { c.timestamps = v; ConstellationClient.saveConfig(); })
                    .sub("Show seconds", false)
                    .sub("Colour", true));
                modules.add(new Module("clickableLinks", "URLs clickable", "Chat",
                    () -> c.clickableLinks, v -> { c.clickableLinks = v; ConstellationClient.saveConfig(); })
                    .b("Underline", () -> c.clickableLinks, v -> c.clickableLinks = v)
                    .sub("Auto-shorten", false));
                modules.add(new Module("copyOnRightClick", "Right-click to copy", "Chat",
                    () -> c.copyOnRightClick, v -> { c.copyOnRightClick = v; ConstellationClient.saveConfig(); })
                    .b("Copy timestamp", () -> c.copyOnRightClick, v -> c.copyOnRightClick = v)
                    .sub("Copy without colour", false));
                modules.add(new Module("mentionAlert", "Ping when named", "Chat",
                    () -> c.mentionAlert, v -> { c.mentionAlert = v; ConstellationClient.saveConfig(); })
                    .b("Sound", () -> c.mentionAlert, v -> c.mentionAlert = v)
                    .sub("Title", true));
                modules.add(new Module("floorShortcuts", "/f1-/f7 /m1-/m7", "Commands",
                    () -> c.floorShortcuts, v -> { c.floorShortcuts = v; ConstellationClient.saveConfig(); })
                    .b("Floors", () -> c.floorShortcuts, v -> c.floorShortcuts = v)
                    .b("Master mode", () -> c.floorShortcuts, v -> c.floorShortcuts = v));
                modules.add(new Module("warpShortcuts", "/h /i /dh /l", "Commands",
                    () -> c.warpShortcuts, v -> { c.warpShortcuts = v; ConstellationClient.saveConfig(); })
                    .b("Hub", () -> c.warpShortcuts, v -> c.warpShortcuts = v)
                    .b("Island", () -> c.warpShortcuts, v -> c.warpShortcuts = v));
                modules.add(new Module("warpShortener", "/drag → /warp drag", "Commands",
                    () -> c.warpShortener, v -> { c.warpShortener = v; ConstellationClient.saveConfig(); })
                    .b("Enabled", () -> c.warpShortener, v -> c.warpShortener = v)
                    .sub("Show in chat", false));
                modules.add(new Module("partyShortcuts", "/pi /pw /pl ...", "Party",
                    () -> c.partyShortcuts, v -> { c.partyShortcuts = v; ConstellationClient.saveConfig(); })
                    .b("Invite", () -> c.partyShortcuts, v -> c.partyShortcuts = v)
                    .b("Kick", () -> c.partyShortcuts, v -> c.partyShortcuts = v));
                modules.add(new Module("partyTriggers", "!warp !join !dt ...", "Party",
                    () -> c.partyTriggers, v -> { c.partyTriggers = v; ConstellationClient.saveConfig(); })
                    .b("Enabled", () -> c.partyTriggers, v -> c.partyTriggers = v)
                    .b("Safe mode", () -> c.triggerSafeMode, v -> { c.triggerSafeMode = v; ConstellationClient.saveConfig(); }));
            }
            case "orion" -> {
                cats = new String[]{"HUD", "Map", "Secrets", "Party", "Solvers", "Combat"};
                OrionConfig c = cfg.orion;
                modules.add(new Module("scoreHud", "Live 0-300 score", "HUD",
                    () -> c.scoreHud, v -> { c.scoreHud = v; ConstellationClient.saveConfig(); })
                    .b("Show letter grade", () -> c.scoreHud, v -> c.scoreHud = v)
                    .sub("Milestone pings", true));
                modules.add(new Module("secretsHud", "Secrets found / total", "HUD",
                    () -> c.secretsHud, v -> { c.secretsHud = v; ConstellationClient.saveConfig(); })
                    .b("Per-room count", () -> c.perRoomCount, v -> { c.perRoomCount = v; ConstellationClient.saveConfig(); })
                    .b("Percentage", () -> c.secretsHud, v -> c.secretsHud = v));
                modules.add(new Module("cryptsHud", "Crypt count", "HUD",
                    () -> c.cryptsHud, v -> { c.cryptsHud = v; ConstellationClient.saveConfig(); })
                    .sub("Warn under 5", true)
                    .sub("Show in chat", false));
                modules.add(new Module("deathsHud", "Death counter", "HUD",
                    () -> c.deathsHud, v -> { c.deathsHud = v; ConstellationClient.saveConfig(); })
                    .sub("Score impact", true)
                    .sub("Per-player", false));
                modules.add(new Module("timerHud", "Run timer", "HUD",
                    () -> c.timerHud, v -> { c.timerHud = v; ConstellationClient.saveConfig(); })
                    .b("Show splits", () -> c.splitsHud, v -> { c.splitsHud = v; ConstellationClient.saveConfig(); })
                    .sub("Milliseconds", false));
                modules.add(new Module("roomNameHud", "Current room name", "HUD",
                    () -> c.roomNameHud, v -> { c.roomNameHud = v; ConstellationClient.saveConfig(); })
                    .sub("Cleared indicator", true)
                    .b("Show secrets in room", () -> c.perRoomCount, v -> { c.perRoomCount = v; ConstellationClient.saveConfig(); }));
                modules.add(new Module("mimicIndicator", "Mimic killed marker", "HUD",
                    () -> c.mimicIndicator, v -> { c.mimicIndicator = v; ConstellationClient.saveConfig(); })
                    .sub("Ping party", true)
                    .sub("Sound", true));
                modules.add(new Module("dungeonMap", "Hypixel map overlay", "Map",
                    () -> c.dungeonMap, v -> { c.dungeonMap = v; ConstellationClient.saveConfig(); })
                    .sub("Room names", true)
                    .sub("Player heads", true)
                    .sub("Auto-hide in boss", true));
                modules.add(new Module("secretWaypoints", "In-world secret boxes", "Secrets",
                    () -> c.secretWaypoints, v -> { c.secretWaypoints = v; ConstellationClient.saveConfig(); })
                    .b("Progressive reveal", () -> c.progressiveReveal, v -> { c.progressiveReveal = v; ConstellationClient.saveConfig(); })
                    .b("One at a time", () -> c.oneSecretAtATime, v -> { c.oneSecretAtATime = v; ConstellationClient.saveConfig(); }));
                modules.add(new Module("routes", "Secret routes", "Secrets",
                    () -> c.routes, v -> { c.routes = v; ConstellationClient.saveConfig(); })
                    .b("Route lines", () -> c.routeLines, v -> { c.routeLines = v; ConstellationClient.saveConfig(); })
                    .b("Prefer pearl-clips", () -> c.pearlRoutes, v -> { c.pearlRoutes = v; ConstellationClient.saveConfig(); }));
                modules.add(new Module("echoOnCollect", "Chime on secret", "Secrets",
                    () -> c.echoOnCollect, v -> { c.echoOnCollect = v; ConstellationClient.saveConfig(); })
                    .b("Sound", () -> c.echoOnCollect, v -> c.echoOnCollect = v)
                    .b("Custom waypoints", () -> c.customWaypoints, v -> { c.customWaypoints = v; ConstellationClient.saveConfig(); }));
                modules.add(new Module("partyFinderGui", "Fancy party finder", "Party",
                    () -> c.partyFinderGui, v -> { c.partyFinderGui = v; ConstellationClient.saveConfig(); })
                    .b("Class filter", () -> c.partyFinderGui, v -> c.partyFinderGui = v)
                    .b("Secret filter", () -> c.partyFinderGui, v -> c.partyFinderGui = v));
                modules.add(new Module("autoRequeue", "Auto-requeue after run", "Party",
                    () -> c.autoRequeue, v -> { c.autoRequeue = v; ConstellationClient.saveConfig(); })
                    .b("Safe mode", () -> c.requeueSafeMode, v -> { c.requeueSafeMode = v; ConstellationClient.saveConfig(); })
                    .b("Confirm first", () -> c.requeueSafeMode, v -> { c.requeueSafeMode = v; ConstellationClient.saveConfig(); }));
                
                modules.add(new Module("terminalSolvers", "F7 phase-3 terminals", "Solvers",
                    () -> c.terminalSolvers, v -> { c.terminalSolvers = v; ConstellationClient.saveConfig(); })
                    .b("Show numbers", () -> c.terminalNumbers, v -> { c.terminalNumbers = v; ConstellationClient.saveConfig(); })
                    .b("Block wrong clicks", () -> c.blockWrongTerminalClicks, v -> { c.blockWrongTerminalClicks = v; ConstellationClient.saveConfig(); }));
                modules.add(new Module("blazeSolver", "F3/M3 blaze puzzle", "Solvers",
                    () -> c.blazeSolver, v -> { c.blazeSolver = v; ConstellationClient.saveConfig(); })
                    .sub("Show labels", true));
                modules.add(new Module("simonSaysSolver", "Simon Says — highlight button", "Solvers",
                    () -> c.simonSaysSolver, v -> { c.simonSaysSolver = v; ConstellationClient.saveConfig(); })
                    .sub("Sound", true));
                modules.add(new Module("threeWeirdosSolver", "Three Weirdos — correct chest", "Solvers",
                    () -> c.threeWeirdosSolver, v -> { c.threeWeirdosSolver = v; ConstellationClient.saveConfig(); })
                    .sub("Highlight all", false));
                modules.add(new Module("triviaSolver", "Trivia — highlight answer", "Solvers",
                    () -> c.triviaSolver, v -> { c.triviaSolver = v; ConstellationClient.saveConfig(); })
                    .sub("Show question", false));
                modules.add(new Module("creeperBeamsSolver", "Creeper Beams — lantern links", "Solvers",
                    () -> c.creeperBeamsSolver, v -> { c.creeperBeamsSolver = v; ConstellationClient.saveConfig(); })
                    .sub("Beam colour", true));
                
                modules.add(new Module("starredMobs", "Starred / objective mobs", "Combat",
                    () -> c.starredMobs, v -> { c.starredMobs = v; ConstellationClient.saveConfig(); })
                    .b("Through walls", () -> false, v -> {}) 
                    .sub("Show ✯", true));
                modules.add(new Module("secretBats", "Secret bat highlights", "Combat",
                    () -> c.secretBats, v -> { c.secretBats = v; ConstellationClient.saveConfig(); })
                    .b("Filter doors", () -> true, v -> {}) 
                    .sub("Glow", false));
                modules.add(new Module("lividFinder", "F5/M5 — hide fake clones", "Combat",
                    () -> c.lividFinder, v -> { c.lividFinder = v; ConstellationClient.saveConfig(); })
                    .b("Show HP bar", () -> true, v -> {})
                    .sub("Box colour", true));
                modules.add(new Module("teammateBoxes", "Teammate highlights", "Combat",
                    () -> c.teammateBoxes, v -> { c.teammateBoxes = v; ConstellationClient.saveConfig(); })
                    .b("Names", () -> true, v -> {})
                    .sub("Colour by class", false));
                modules.add(new Module("dropEsp", "Dropped items ESP", "Combat",
                    () -> c.dropEsp, v -> { c.dropEsp = v; ConstellationClient.saveConfig(); })
                    .sub("Show labels", true)
                    .sub("Distance", true));
                modules.add(new Module("blessingDisplay", "Blessing levels HUD", "Combat",
                    () -> c.blessingDisplay, v -> { c.blessingDisplay = v; ConstellationClient.saveConfig(); })
                    .sub("Compact", true));
                modules.add(new Module("doorTracker", "Wither/blood key + door ESP", "Combat",
                    () -> c.doorTracker, v -> { c.doorTracker = v; ConstellationClient.saveConfig(); })
                    .b("Key beam", () -> c.doorTracker, v -> c.doorTracker = v)
                    .sub("Door colours", true));
            }
            
            // builds modules from every bool...
            default -> {
                BaseConfigGroup c = cfg.getSubConfig(constellationId);
                if (c != null) {
                    cats = new String[]{"Features"};
                    for (var field : c.getClass().getFields()) {
                        String n = field.getName();
                        if (n.equals("enabled") || n.equals("version")) continue;
                        if (field.getType() != boolean.class) continue;
                        try {
                            boolean val = field.getBoolean(c);
                            String label = autoLabel(n);
                            modules.add(new Module(n, label, "Features",
                                () -> { try { return field.getBoolean(c); } catch (Exception e) { return false; } },
                                v -> { try { field.setBoolean(c, v); ConstellationClient.saveConfig(); } catch (Exception e) {} }));
                        } catch (Exception ignored) {}
                    }
                }
            }
        }
        
        if (cats.length == 0) cats = new String[]{"General"};
        selectedCat = Math.clamp(selectedCat, 0, cats.length - 1);

        
        activeConfig = ConstellationClient.instance().configManager().getGroup(constellationId);
        for (Module m : modules) {
            m.backing = activeConfig;
            m.knob = m.get.getAsBoolean() ? 1 : 0;
        }
    }

    @Override public boolean isPauseScreen() { return false; }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float delta) {
        Minecraft mc = Minecraft.getInstance();
        int fullW = mc.getWindow().getGuiScaledWidth(), fullH = mc.getWindow().getGuiScaledHeight();
        SpaceBackground.renderConfig(g, fullW, fullH, delta);
        int w = panelW(fullW), h = panelH(fullH);
        int px = (fullW - w) / 2, py = (fullH - h) / 2;
        long now = System.nanoTime();
        float dt = lastNanos == 0 ? 0.016f : Math.min(0.05f, (now - lastNanos) / 1_000_000_000f);
        lastNanos = now; lastMx = mx; lastMy = my;

        g.fill(0, 0, fullW, fullH, 0x880A0A14);
        g.pose().pushMatrix();
        g.pose().translate(px, py);
        mx -= px; my -= py;

        g.fill(0, 0, w, h, 0xF212121F);

        int sw = sideW(w), navY0 = TB + 4;
        g.fill(0, 0, sw, h, 0xFF0E0E1A);
        g.fill(sw - 1, 0, sw, h, ConstellationTheme.ACCENT_DIM);
        for (int i = 0; i < cats.length; i++) {
            int y = navY0 + i * 24;
            boolean sel = i == selectedCat, hov = mx >= 0 && mx < sw && my >= y && my < y + 24;
            if (sel) g.fill(0, y, sw, y + 24, 0xFF1A1A28);
            else if (hov) g.fill(0, y, sw, y + 24, 0xFF252535);
            if (sel) g.fill(0, y, 3, y + 24, ConstellationTheme.ACCENT);
            g.text(mc.font, cats[i], 12, y + 7, sel ? ConstellationTheme.ACCENT_BRIGHT : (hov ? ConstellationTheme.TEXT : ConstellationTheme.TEXT_MUTED), false);
        }

        g.fill(0, 0, w, TB, 0xFF0E0E1A);
        g.fill(0, TB - 1, w, TB, ConstellationTheme.ACCENT);
        g.text(mc.font, "✧ " + constellationId, 12, 10, ConstellationTheme.ACCENT_BRIGHT, false);
        String esc = "esc to close  ·  right-click for settings";
        g.text(mc.font, esc, w - mc.font.width(esc) - 10, 12, ConstellationTheme.TEXT_MUTED, false);

        var vis = visibleModules();
        int cols = cols(w), cardW = cardW(w);
        int rows = (vis.size() + cols - 1) / cols;
        int contentH = rows * (CARD_H + CARD_GAP);
        int viewH = h - gridTop() - 6;
        maxScroll = Math.max(0, contentH - viewH);
        if (scrollTarget > maxScroll) scrollTarget = maxScroll;
        if (scrollTarget < 0) scrollTarget = 0;
        scrollF += (scrollTarget - scrollF) * Math.min(1, dt * 16);
        if (Math.abs(scrollF - scrollTarget) < 0.5f) scrollF = scrollTarget;

        g.text(mc.font, cats[selectedCat], gridX(w), TB + 8, ConstellationTheme.TEXT, false);
        g.text(mc.font, vis.size() + " modules", gridX(w) + mc.font.width(cats[selectedCat]) + 10, TB + 9, ConstellationTheme.TEXT_MUTED, false);

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

        if (maxScroll > 0) {
            int tx = w - 5, barH = Math.max(20, viewH * viewH / contentH);
            g.fill(tx, gTop, tx + 3, gTop + viewH, 0xFF15111f);
            g.fill(tx, gTop + (int)((viewH - barH) * (scrollF / maxScroll)), tx + 3, gTop + (int)((viewH - barH) * (scrollF / maxScroll)) + barH, ConstellationTheme.ACCENT_DIM);
        }

        
        boolean modalUp = openModule != null;
        modalAnim += ((modalUp ? 1f : 0f) - modalAnim) * Math.min(1, dt * 14);
        if (modalAnim > 0.01f && openModule != null) {
            int mx2 = w/2 - 180, my2 = h/2 - 120;
            g.fill(mx2, my2, mx2 + 360, my2 + 240, 0xF21A1A28);
            g.fill(mx2, my2, mx2 + 360, my2 + 3, ConstellationTheme.ACCENT);
            g.text(mc.font, openModule.name.replaceAll("([A-Z])", " $1").trim(), mx2 + 10, my2 + 10, ConstellationTheme.ACCENT_BRIGHT, false);
            g.text(mc.font, openModule.desc, mx2 + 10, my2 + 24, ConstellationTheme.TEXT_MUTED, false);
            int oy = my2 + 44;
            for (var sub : openModule.subs) {
                boolean on = sub.get.getAsBoolean();
                g.text(mc.font, (on ? "✦ " : "  ") + sub.label, mx2 + 10, oy, on ? ConstellationTheme.ACCENT_BRIGHT : ConstellationTheme.TEXT_MUTED, false);
                oy += 18;
            }
            g.text(mc.font, "click to close", mx2 + 360 - mc.font.width("click to close") - 10, my2 + 230, ConstellationTheme.TEXT_MUTED, false);
        }

        g.fill(0, 0, w, 1, ConstellationTheme.ACCENT);
        g.fill(0, h - 1, w, h, ConstellationTheme.ACCENT_DIM);
        g.fill(0, 0, 1, h, ConstellationTheme.ACCENT_DIM);
        g.fill(w - 1, 0, w, h, ConstellationTheme.ACCENT_DIM);
        g.pose().popMatrix();
    }

    private void drawCard(GuiGraphicsExtractor g, Module m, int cx, int cy, int cardW, boolean hov, float dt) {
        boolean on = m.get.getAsBoolean();
        m.knob += ((on ? 1f : 0f) - m.knob) * Math.min(1, dt * 16);
        m.hover += ((hov ? 1f : 0f) - m.hover) * Math.min(1, dt * 14);
        g.fill(cx, cy, cx + cardW, cy + CARD_H, on ? 0xFF222240 : (hov ? 0xFF252535 : 0xFF1A1A28));
        int knobCol = lerp(0xFF333333, ConstellationTheme.ACCENT, m.knob);
        g.fill(cx, cy, cx + cardW, cy + 3, knobCol);
        String name = m.name.replaceAll("([A-Z])", " $1").trim();
        if (!name.isEmpty()) name = name.substring(0, 1).toUpperCase() + name.substring(1);
        if (name.length() > 18) name = name.substring(0, 17) + "…";
        Minecraft mc = Minecraft.getInstance();
        g.text(mc.font, name, cx + 5, cy + 8, m.knob > 0.5f ? ConstellationTheme.ACCENT_BRIGHT : ConstellationTheme.TEXT, false);
        g.text(mc.font, m.desc, cx + 5, cy + 22, ConstellationTheme.TEXT_MUTED, false);
        
        if (!m.subs.isEmpty()) {
            String badge = m.subs.size() + " opts";
            g.text(mc.font, badge, cx + cardW - mc.font.width(badge) - 5, cy + 32, ConstellationTheme.ACCENT_DIM, false);
        }
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

        
        if (openModule != null) {
            int mx2 = w/2 - 180, my2 = h/2 - 120, oy = my2 + 44;
            for (var sub : openModule.subs) {
                if (mx >= mx2 + 10 && mx <= mx2 + 350 && my >= oy && my < oy + 18) {
                    sub.set.accept(!sub.get.getAsBoolean());
                    ConstellationClient.saveConfig();
                    return true;
                }
                oy += 18;
            }
            openModule = null;
            return true;
        }

        
        int sw = sideW(w), navY0 = TB + 4;
        for (int i = 0; i < cats.length; i++) {
            if (mx >= 0 && mx < sw && my >= navY0 + i * 24 && my < navY0 + i * 24 + 24) {
                selectedCat = i; openModule = null; scrollTarget = 0; return true;
            }
        }

        
        var vis = visibleModules();
        int cols = cols(w), cardW = cardW(w);
        int gx = gridX(w), gTop = gridTop(), sc = Math.round(scrollF);
        boolean rightClick = event.button() == 1;
        for (int idx = 0; idx < vis.size(); idx++) {
            Module m = vis.get(idx);
            int cx = gx + (idx % cols) * (cardW + CARD_GAP);
            int cy = gTop + (idx / cols) * (CARD_H + CARD_GAP) - sc;
            if (mx >= cx && mx < cx + cardW && my >= cy && my < cy + CARD_H && my >= gTop) {
                if (rightClick) {
                    
                    if (!m.subs.isEmpty()) openModule = (openModule == m) ? null : m;
                } else {
                    // left-click: toggle master switch
                    boolean next = !m.get.getAsBoolean();
                    m.set.accept(next);
                    m.knob = next ? 1 : 0;
                    ConstellationClient.saveConfig();
                }
                return true;
            }
        }

        return super.mouseClicked(event, dbl);
    }

    
    public boolean mouseScrolled(double mx, double my, double scrollX, double scrollY) {
        if (openModule != null) return true;
        scrollTarget -= (int)(scrollY * 30);
        scrollTarget = Math.clamp(scrollTarget, 0, maxScroll);
        return true;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.key() == GLFW.GLFW_KEY_ESCAPE) {
            if (openModule != null) { openModule = null; return true; }
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
        int ar = (a>>16)&0xFF, ag = (a>>8)&0xFF, ab = a&0xFF;
        int br = (b>>16)&0xFF, bg = (b>>8)&0xFF, bb = b&0xFF;
        return 0xFF000000 | ((int)(ar+(br-ar)*t)<<16) | ((int)(ag+(bg-ag)*t)<<8) | (int)(ab+(bb-ab)*t);
    }
}
