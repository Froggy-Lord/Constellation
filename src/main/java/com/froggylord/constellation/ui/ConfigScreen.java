package com.froggylord.constellation.ui;

import com.froggylord.constellation.ConstellationClient;
import com.froggylord.constellation.config.*;
import com.froggylord.constellation.core.BaseConstellation;
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

public class ConfigScreen extends Screen {

    // ---- option types matching cryptkit ----
    private static class Opt {
        enum K { BOOL }
        final K kind; final String label;
        BooleanSupplier get; Consumer<Boolean> set;
        Opt(K k, String l) { kind = k; label = l; }
        static Opt bool(String l, BooleanSupplier g, Consumer<Boolean> s) {
            Opt o = new Opt(K.BOOL, l); o.get = g; o.set = s; return o;
        }
    }

    private static class Module {
        final String name, desc;
        final BooleanSupplier get; final Consumer<Boolean> set;
        final List<Opt> opts = new ArrayList<>();
        float knob = 0;
        Module(String n, String d, BooleanSupplier g, Consumer<Boolean> s) {
            name = n; desc = d; get = g; set = s; knob = g.getAsBoolean() ? 1 : 0;
        }
        Module b(String l, BooleanSupplier g, Consumer<Boolean> s) { opts.add(Opt.bool(l, g, s)); return this; }
    }

    private final String constellationId;
    private final Screen parent;
    private final List<Module> modules = new ArrayList<>();
    private int cardW = 200, cardH = 90;
    private int cols = 3;
    private String openModule = null;

    public ConfigScreen(String constellationId, Screen parent) {
        super(Component.literal("Constellation — " + constellationId));
        this.constellationId = constellationId;
        this.parent = parent;
        buildModules();
    }

    private void buildModules() {
        modules.clear();
        var cfg = ConstellationClient.cfg();
        if (cfg == null) return;

        switch (constellationId) {
            case "apollo" -> {
                ApolloConfig c = cfg.apollo;
                // each hud element is a module
                for (var field : ApolloConfig.class.getFields()) {
                    if (!field.getName().equals("fps") && !field.getName().equals("ping") &&
                        !field.getName().equals("tps") && !field.getName().equals("clock") &&
                        !field.getName().equals("coords") && !field.getName().equals("health") &&
                        !field.getName().equals("mana") && !field.getName().equals("defense") &&
                        !field.getName().equals("speed")) continue;
                    if (field.getType() != ApolloConfig.HudEntry.class) continue;
                    try {
                        ApolloConfig.HudEntry he = (ApolloConfig.HudEntry) field.get(c);
                        Module m = new Module(field.getName(), "HUD element", () -> he.visible, v -> he.visible = v);
                        modules.add(m);
                    } catch (Exception e) {}
                }
                Module scoreboard = new Module("customScoreboard", "Replace vanilla scoreboard", () -> c.customScoreboard, v -> c.customScoreboard = v);
                Module tablist = new Module("customTabList", "Fully custom tab list", () -> c.customTabList, v -> c.customTabList = v);
                Module compactDmg = new Module("compactDamage", "1.2M instead of 1200000", () -> c.compactDamage, v -> c.compactDamage = v);
                Module rainbow = new Module("rainbowActionBar", "Rainbow action bar text", () -> c.rainbowActionBar, v -> c.rainbowActionBar = v);
                modules.addAll(List.of(scoreboard, tablist, compactDmg, rainbow));
            }
            case "phoenix" -> {
                PhoenixConfig c = cfg.phoenix;
                for (var field : PhoenixConfig.class.getFields()) {
                    if (field.getName().equals("enabled") || field.getName().equals("version")) continue;
                    if (field.getType() != boolean.class) continue;
                    try {
                        boolean val = field.getBoolean(c);
                        String label = field.getName().replaceAll("([A-Z])", " $1").trim();
                        if (!label.isEmpty()) label = label.substring(0, 1).toUpperCase() + label.substring(1);
                        Module m = new Module(field.getName(), label, () -> {
                            try { return field.getBoolean(c); } catch (Exception e) { return false; }
                        }, v -> {
                            try { field.setBoolean(c, v); ConstellationClient.saveConfig(); } catch (Exception e) {}
                        });
                        m.knob = val ? 1 : 0;
                        modules.add(m);
                    } catch (Exception e) {}
                }
            }
            case "cassiopeia" -> {
                CassiopeiaConfig c = cfg.cassiopeia;
                // chat cleaner sub-settings grouped into one module
                Module cleaner = new Module("chatCleaner", "Hide spam messages", () -> true, v -> {});
                for (var field : CassiopeiaConfig.class.getFields()) {
                    String n = field.getName();
                    if (!n.startsWith("clean")) continue;
                    if (field.getType() != boolean.class) continue;
                    try {
                        boolean val = field.getBoolean(c);
                        String label = n.substring(5).replaceAll("([A-Z])", " $1").trim();
                        if (!label.isEmpty()) label = label.substring(0, 1).toUpperCase() + label.substring(1);
                        cleaner.b(label, () -> { try { return field.getBoolean(c); } catch (Exception e) { return false; } },
                            v -> { try { field.setBoolean(c, v); ConstellationClient.saveConfig(); } catch (Exception e) {} });
                    } catch (Exception e) {}
                }
                modules.add(cleaner);

                Module stamps = new Module("timestamps", "Dim [HH:MM] on every line", () -> c.timestamps, v -> { c.timestamps = v; ConstellationClient.saveConfig(); });
                Module links = new Module("clickableLinks", "URLs turn blue and underlined", () -> c.clickableLinks, v -> { c.clickableLinks = v; ConstellationClient.saveConfig(); });
                Module copy = new Module("copyOnRightClick", "Right-click to copy messages", () -> c.copyOnRightClick, v -> { c.copyOnRightClick = v; ConstellationClient.saveConfig(); });
                Module mentions = new Module("mentionAlert", "Ping + sound when your name is said", () -> c.mentionAlert, v -> { c.mentionAlert = v; ConstellationClient.saveConfig(); });
                Module floors = new Module("floorShortcuts", "/f1-/f7, /m1-/m7, /e, /r", () -> c.floorShortcuts, v -> { c.floorShortcuts = v; ConstellationClient.saveConfig(); });
                Module warps = new Module("warpShortcuts", "/h, /i, /dh, /l", () -> c.warpShortcuts, v -> { c.warpShortcuts = v; ConstellationClient.saveConfig(); });
                Module shortener = new Module("warpShortener", "/drag -> /warp drag", () -> c.warpShortener, v -> { c.warpShortener = v; ConstellationClient.saveConfig(); });
                Module pshort = new Module("partyShortcuts", "/pi, /pw, /pl, etc.", () -> c.partyShortcuts, v -> { c.partyShortcuts = v; ConstellationClient.saveConfig(); });
                Module trig = new Module("partyTriggers", "!warp, !join, !dt, etc.", () -> c.partyTriggers, v -> { c.partyTriggers = v; ConstellationClient.saveConfig(); });
                modules.addAll(List.of(stamps, links, copy, mentions, floors, warps, shortener, pshort, trig));
            }
        }

        // sync knob animation state
        for (Module m : modules) m.knob = m.get.getAsBoolean() ? 1 : 0;
    }

    @Override
    public boolean isPauseScreen() { return false; }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float delta) {
        Minecraft mc = Minecraft.getInstance();
        int w = mc.getWindow().getGuiScaledWidth(), h = mc.getWindow().getGuiScaledHeight();
        Font font = mc.font;

        g.fill(0, 0, w, h, NebulaTheme.BG_DEEP);

        // title
        String title = "✧ " + constellationId + " Settings";
        g.text(font, title, 8, 6, NebulaTheme.ACCENT_GOLD, false);

        // module cards in a grid
        int pad = 8, x0 = pad, y0 = 20;
        for (int i = 0; i < modules.size(); i++) {
            Module m = modules.get(i);
            int col = i % cols, row = i / cols;
            int cx = x0 + col * (cardW + pad), cy = y0 + row * (cardH + pad);

            // animate knob toward target
            float target = m.get.getAsBoolean() ? 1 : 0;
            m.knob += (target - m.knob) * 0.3f;

            // card background
            boolean hover = mx >= cx && mx <= cx + cardW && my >= cy && my <= cy + cardH;
            int bg = hover ? 0xFF2A2A3A : 0xFF1A1A28;
            g.fill(cx, cy, cx + cardW, cy + cardH, bg);
            // accent knob
            int knobCol = lerp(0xFF333333, NebulaTheme.ACCENT_GOLD, m.knob);
            g.fill(cx, cy, cx + cardW, cy + 3, knobCol);

            // name
            String name = m.name.replaceAll("([A-Z])", " $1").trim();
            if (!name.isEmpty()) name = name.substring(0, 1).toUpperCase() + name.substring(1);
            if (name.length() > 20) name = name.substring(0, 19) + "…";
            g.text(font, name, cx + 5, cy + 8, m.knob > 0.5f ? NebulaTheme.ACCENT_BRIGHT : NebulaTheme.STAR_WHITE, false);
            // desc
            g.text(font, m.desc, cx + 5, cy + 22, NebulaTheme.STAR_MUTED, false);

            // inner options (if open)
            if (openModule != null && openModule.equals(m.name)) {
                int oy = cy + cardH + 4;
                int oh = m.opts.size() * 20 + 8;
                g.fill(cx, oy, cx + cardW, oy + oh, 0xFF1A1A28);
                g.fill(cx, oy, cx + 3, oy + oh, NebulaTheme.ACCENT_GOLD);
                int ox = cx + 6;
                for (Opt o : m.opts) {
                    boolean val = o.get.getAsBoolean();
                    String state = val ? "✦" : "  ";
                    g.text(font, state + " " + o.label, ox, oy + 6, val ? NebulaTheme.ACCENT_BRIGHT : NebulaTheme.STAR_MUTED, false);
                    oy += 20;
                }
            }
        }

        // footer
        String esc = "ESC to close";
        g.text(font, esc, w / 2 - font.width(esc) / 2, h - 10, NebulaTheme.STAR_MUTED, false);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean dbl) {
        int mx = (int) event.x(), my = (int) event.y();
        int pad = 8, x0 = pad, y0 = 20;

        for (int i = 0; i < modules.size(); i++) {
            Module m = modules.get(i);
            int col = i % cols, row = i / cols;
            int cx = x0 + col * (cardW + pad), cy = y0 + row * (cardH + pad);

            if (mx >= cx && mx <= cx + cardW && my >= cy && my <= cy + cardH) {
                if (m.opts.isEmpty()) {
                    // no inner options — just toggle
                    boolean next = !m.get.getAsBoolean();
                    m.set.accept(next);
                    m.knob = next ? 1 : 0;
                    ConstellationClient.saveConfig();
                } else {
                    // has inner options — toggle open/close
                    openModule = openModule != null && openModule.equals(m.name) ? null : m.name;
                }
                return true;
            }

            // check inner options
            if (openModule != null && openModule.equals(m.name)) {
                int oy = cy + cardH + 4;
                for (Opt o : m.opts) {
                    if (mx >= cx + 6 && mx <= cx + cardW && my >= oy && my < oy + 20) {
                        boolean next = !o.get.getAsBoolean();
                        o.set.accept(next);
                        ConstellationClient.saveConfig();
                        return true;
                    }
                    oy += 20;
                }
            }
        }

        return super.mouseClicked(event, dbl);
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
        int ar = (a >> 16) & 0xFF, ag = (a >> 8) & 0xFF, ab = a & 0xFF;
        int br = (b >> 16) & 0xFF, bg = (b >> 8) & 0xFF, bb = b & 0xFF;
        int rr = (int)(ar + (br - ar) * t);
        int rg = (int)(ag + (bg - ag) * t);
        int rb = (int)(ab + (bb - ab) * t);
        return 0xFF000000 | (rr << 16) | (rg << 8) | rb;
    }
}
