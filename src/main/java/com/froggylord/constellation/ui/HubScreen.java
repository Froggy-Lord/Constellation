package com.froggylord.constellation.ui;

import com.froggylord.constellation.ConstellationClient;
import com.froggylord.constellation.render.ConstellationIcons;
import com.froggylord.constellation.render.ConstellationTheme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

public class HubScreen extends Screen {

    private final Screen parent;
    private long openTime;
    private long lastFrame;
    private EditBox search;
    private Filter filter = Filter.ALL;
    private float scrollOff = 0;
    private float scrollTarget = 0;
    private int maxScroll = 0;
    private boolean scrolling = false;
    private int scrollGrabY = 0;
    private float scrollGrabOff = 0;
    private float scrollVelocity = 0;
    private long hudFlashAt = 0, visualFlashAt = 0, cfgFlashAt = 0; // brief flash on button click

    private static final java.util.Map<String, Float> toggleAnim = new java.util.HashMap<>();
    private static final java.util.Map<String, Float> cardHover = new java.util.HashMap<>();

    public HubScreen(Screen parent) {
        super(Component.literal("Constellation"));
        this.parent = parent;
    }

    @Override protected void init() {
        this.openTime = System.currentTimeMillis();
        this.lastFrame = openTime;
        search = new EditBox(font, 141, 11, Math.max(54, Math.min(184, width - 241)), 18, Component.literal("Search modules"));
        search.setBordered(false);
        search.setHint(Component.literal("search modules"));
        search.setMaxLength(48);
        search.setResponder(value -> { scrollOff = 0; scrollTarget = 0; });
        addRenderableWidget(search);
    }
    @Override public boolean isPauseScreen() { return false; }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float delta) {
        Minecraft mc = Minecraft.getInstance();
        int w = mc.getWindow().getGuiScaledWidth();
        int h = mc.getWindow().getGuiScaledHeight();
        Font font = mc.font;
        long now = System.currentTimeMillis();
        float frameDelta = Math.min(0.1f, Math.max(0f, (now - lastFrame) / 1000f));
        lastFrame = now;

        SpaceBackground.render(g, w, h, delta);

        int headerH = 42;
        ConstellationTheme.panel(g, 0, 0, w, headerH);
        String title = "Constellation";
        g.text(font, title, 12, 9, ConstellationTheme.ACCENT_BRIGHT, false);
        g.text(font, "SkyBlock client", 12, 23, ConstellationTheme.TEXT_MUTED, false);
        ConstellationTheme.search(g, 119, 7, search.getWidth() + 12, 24, search.isFocused());
        ConstellationIcons.drawAction(g, "search", 122, 11, 16);

        int filterW = 68;
        int filterX = w - filterW - 10;
        boolean filterHover = inside(mx, my, filterX, 8, filterW, 22);
        ConstellationTheme.button(g, filterX, 8, filterW, 22, filterHover, filter != Filter.ALL);
        String filterText = filter.label;
        int filterContent = 16 + 4 + font.width(filterText);
        ConstellationIcons.drawAction(g, "filter", filterX + (filterW - filterContent) / 2, 11, 16);
        g.text(font, filterText, filterX + (filterW - filterContent) / 2 + 20, 15,
            filter == Filter.ALL ? ConstellationTheme.TEXT_DIM : ConstellationTheme.ACCENT_BRIGHT, false);

        var visibleIds = visibleIds();
        long enabledCount = ConstellationClient.featureManager().getAllIds().stream()
            .flatMap(id -> ConstellationClient.featureManager().get(id).stream()).filter(c -> c.isEnabled()).count();
        String count = enabledCount + " enabled  " + visibleIds.size() + " shown";
        if (w > 600) g.text(font, count, filterX - font.width(count) - 10, 15, ConstellationTheme.TEXT_MUTED, false);

        int cols = Math.max(1, Math.min(4, (w - 20) / 240));
        int cardW = Math.min(280, ((w - 20) - (cols - 1) * 8) / cols);
        int cardH = 50;
        int gridX = 10, gridY = headerH + 10;

        int idx = 0;
        for (String id : visibleIds) {
            var opt = ConstellationClient.featureManager().get(id);
            if (opt.isEmpty()) continue;
            var c = opt.get();
            int col = idx % cols;
            int cx = gridX + col * (cardW + 8);
            int cy = cardY(idx, scrollOff);

            if (cy + cardH > headerH && cy < h - 80) {
                boolean enabled = c.isEnabled();
                boolean hover = mx >= cx && mx <= cx + cardW && my >= cy && my <= cy + cardH;
                ConstellationTheme.card(g, cx, cy, cardW, cardH, enabled || hover);

                // hover glow fade
                float hGlow = cardHover.getOrDefault(id, 0f);
                hGlow = ConstellationTheme.approach(hGlow, hover ? 1f : 0f, frameDelta, hover ? 16f : 10f);
                cardHover.put(id, hGlow);
                if (hGlow > 0.01f) ConstellationTheme.glow(g, cx, cy, cardW, cardH, hGlow);

                ConstellationIcons.draw(g, id, cx + 7, cy + 9, 28);
                String name = c.displayName();
                if (font.width(name) > cardW - 82) name = font.plainSubstrByWidth(name, cardW - 88) + "...";
                g.text(font, name, cx + 42, cy + 7,
                    enabled ? ConstellationTheme.ACCENT_BRIGHT : ConstellationTheme.TEXT, false);

                String desc = c.description();
                int maxW = cardW - 50;
                if (font.width(desc) > maxW) desc = font.plainSubstrByWidth(desc, maxW - 6) + "…";
                g.text(font, desc, cx + 42, cy + 23, ConstellationTheme.TEXT_MUTED, false);

                // toggle indicator with smooth animation
                int tx = cx + cardW - 32, ty = cy + 6;
                float target = enabled ? 1f : 0f;
                float cur = toggleAnim.getOrDefault(id, target);
                cur = ConstellationTheme.approach(cur, target, frameDelta, 16f);
                if (Math.abs(cur - target) < 0.01f) cur = target;
                toggleAnim.put(id, cur);
                ConstellationTheme.toggle(g, tx, ty, cur);
            }
            idx++;
        }

        int totalRows = (int) Math.ceil((double) idx / cols);
        maxScroll = Math.max(0, totalRows * (cardH + 6) - (h - headerH - 90));
        scrollTarget = Math.clamp(scrollTarget, 0, maxScroll);
        scrollOff = ConstellationTheme.approach(scrollOff, scrollTarget, frameDelta, 18f);
        if (Math.abs(scrollOff - scrollTarget) < 0.5f) scrollOff = scrollTarget;

        // ---- scrollbar ----
        if (maxScroll > 0) {
            int sbX = w - 6, sbY = gridY, sbH = h - gridY - 80;
            g.fill(sbX, sbY, sbX + 4, sbY + sbH, ConstellationTheme.BORDER);
            float ratio = (float) sbH / (sbH + maxScroll);
            int thumbH = Math.max(20, (int) (sbH * ratio));
            int thumbY = sbY + (int) (scrollOff / maxScroll * (sbH - thumbH));
            g.fill(sbX, thumbY, sbX + 4, thumbY + thumbH, ConstellationTheme.ACCENT);
        }

        int btnGap = 8, btnH = 24;
        int btnW = Math.max(76, Math.min(132, (w - 36 - btnGap * 2) / 3));
        int footerW = btnW * 3 + btnGap * 2;
        int hudX = (w - footerW) / 2, visualX = hudX + btnW + btnGap, cfgX = visualX + btnW + btnGap;
        int btnY = h - btnH - font.lineHeight - 18;

        boolean hoverHud = mx >= hudX && mx <= hudX + btnW && my >= btnY && my <= btnY + btnH;
        boolean hoverVisual = mx >= visualX && mx <= visualX + btnW && my >= btnY && my <= btnY + btnH;
        boolean hoverCfg = mx >= cfgX && mx <= cfgX + btnW && my >= btnY && my <= btnY + btnH;

        ConstellationTheme.button(g, hudX, btnY, btnW, btnH, hoverHud, false);
        // click flash
        long hudAge = System.currentTimeMillis() - hudFlashAt;
        if (hudAge < 200) g.fill(hudX, btnY, hudX + btnW, btnY + btnH, ((int)((1f-hudAge/200f)*40) << 24) | 0xFFCC33);
        String hudLabel = "HUD Editor";
        int hudContent = 16 + 5 + font.width(hudLabel);
        ConstellationIcons.drawAction(g, "hud", hudX + (btnW - hudContent) / 2, btnY + 4, 16);
        g.text(font, hudLabel, hudX + (btnW - hudContent) / 2 + 21, btnY + 7,
            hoverHud ? ConstellationTheme.ACCENT_BRIGHT : ConstellationTheme.TEXT, false);

        ConstellationTheme.button(g, visualX, btnY, btnW, btnH, hoverVisual, false);
        long visualAge = System.currentTimeMillis() - visualFlashAt;
        if (visualAge < 200) g.fill(visualX, btnY, visualX + btnW, btnY + btnH, ((int)((1f-visualAge/200f)*40) << 24) | 0xFFCC33);
        String visualLabel = "Visuals";
        int visualContent = 16 + 5 + font.width(visualLabel);
        ConstellationIcons.drawAction(g, "settings", visualX + (btnW - visualContent) / 2, btnY + 4, 16);
        g.text(font, visualLabel, visualX + (btnW - visualContent) / 2 + 21, btnY + 7,
            hoverVisual ? ConstellationTheme.ACCENT_BRIGHT : ConstellationTheme.TEXT, false);

        ConstellationTheme.button(g, cfgX, btnY, btnW, btnH, hoverCfg, false);
        long cfgAge = System.currentTimeMillis() - cfgFlashAt;
        if (cfgAge < 200) g.fill(cfgX, btnY, cfgX + btnW, btnY + btnH, ((int)((1f-cfgAge/200f)*40) << 24) | 0xFFCC33);
        String cfgLabel = "Config";
        int cfgContent = 16 + 5 + font.width(cfgLabel);
        ConstellationIcons.drawAction(g, "settings", cfgX + (btnW - cfgContent) / 2, btnY + 4, 16);
        g.text(font, cfgLabel, cfgX + (btnW - cfgContent) / 2 + 21, btnY + 7,
            hoverCfg ? ConstellationTheme.ACCENT_BRIGHT : ConstellationTheme.TEXT, false);

        // hint
        String hint = "right shift · esc to close · scroll to browse";
        g.text(font, hint, w / 2 - font.width(hint) / 2, h - 2 - font.lineHeight,
            ConstellationTheme.TEXT_FAINT, false);

        SpaceBackground.fadeIn(g, w, h, openTime);

        // subtle pulsing indicator dot — shows the overlay is active
        double pulse = (Math.sin(now / 650.0) + 1.0) * 0.5;
        int dotAlpha = 55 + (int) (pulse * 65);
        g.fill(6, h - 8, 10, h - 4, (dotAlpha << 24) | ConstellationTheme.ACCENT);
        super.extractRenderState(g, mx, my, delta);
    }

    // ---- input (unchanged logic) ----

    @Override public boolean mouseClicked(MouseButtonEvent event, boolean dbl) {
        int mx = (int) event.x(), my = (int) event.y();
        Minecraft mc = Minecraft.getInstance();
        int w = mc.getWindow().getGuiScaledWidth(), h = mc.getWindow().getGuiScaledHeight();
        int btnGap = 8, btnH = 24;
        int btnW = Math.max(76, Math.min(132, (w - 36 - btnGap * 2) / 3));
        int footerW = btnW * 3 + btnGap * 2;
        int hudX = (w - footerW) / 2, visualX = hudX + btnW + btnGap, cfgX = visualX + btnW + btnGap;
        int btnY = h - btnH - Minecraft.getInstance().font.lineHeight - 18;

        if (mx >= cfgX && mx <= cfgX + btnW && my >= btnY && my <= btnY + btnH) {
            cfgFlashAt = System.currentTimeMillis();
            var ids = ConstellationClient.featureManager().getLoadedIds();
            String first = ids.isEmpty() ? "apollo" : ids.iterator().next();
            mc.execute(() -> mc.setScreenAndShow(new ConfigScreen(first, this)));
            return true;
        }
        if (mx >= hudX && mx <= hudX + btnW && my >= btnY && my <= btnY + btnH) {
            hudFlashAt = System.currentTimeMillis();
            mc.execute(() -> mc.setScreenAndShow(new com.froggylord.constellation.hud.HudEditScreen(this)));
            return true;
        }
        if (mx >= visualX && mx <= visualX + btnW && my >= btnY && my <= btnY + btnH) {
            visualFlashAt = System.currentTimeMillis();
            mc.execute(() -> mc.setScreenAndShow(new AdvancedConfigScreen(this, "visual")));
            return true;
        }

        int filterW = 68;
        int filterX = w - filterW - 10;
        if (inside(mx, my, filterX, 8, filterW, 22)) {
            filter = filter.next();
            scrollOff = 0;
            scrollTarget = 0;
            return true;
        }

        int toggleW = 40;
        var visibleIds = visibleIds();
        int cols = Math.max(1, Math.min(4, (w - 20) / 240));
        int cardW = Math.min(280, ((w - 20) - (cols - 1) * 8) / cols);
        for (int idx = 0; idx < visibleIds.size(); idx++) {
            String id = visibleIds.get(idx);
            int cx = 10 + idx % cols * (cardW + 8);
            int cy = cardY(idx, scrollOff);
            if (inside(mx, my, cx + cardW - toggleW, cy, toggleW, 31)) {
                ConstellationClient.featureManager().get(id).ifPresent(c ->
                    ConstellationClient.featureManager().setEnabled(id, !c.isEnabled()));
                return true;
            }
        }

        int idx = 0;
        for (String id : visibleIds) {
            int col = idx % cols;
            int cx = 10 + col * (cardW + 8);
            int cy = cardY(idx, scrollOff);
            var opt = ConstellationClient.featureManager().get(id);
            if (opt.isEmpty()) continue;
            if (mx >= cx && mx <= cx + cardW && my >= cy && my <= cy + 50) {
                final String fid = id;
                mc.execute(() -> mc.setScreenAndShow(new ConfigScreen(fid, parent)));
                return true;
            }
            idx++;
        }
        if (maxScroll > 0 && mx >= w - 6) { scrolling = true; scrollGrabY = my; scrollGrabOff = scrollTarget; return true; }
        return super.mouseClicked(event, dbl);
    }

    @Override public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        if (scrolling) {
            int my = (int) event.y();
            float pct = (float) (my - scrollGrabY) / (Minecraft.getInstance().getWindow().getGuiScaledHeight() - 80);
            scrollTarget = Math.clamp(scrollGrabOff + pct * maxScroll, 0, maxScroll);
            scrollOff = scrollTarget; // snap on drag
            return true;
        }
        return super.mouseDragged(event, dx, dy);
    }

    @Override public boolean mouseReleased(MouseButtonEvent event) { scrolling = false; return super.mouseReleased(event); }
    @Override public boolean mouseScrolled(double mx, double my, double scrollX, double scrollY) {
        scrollTarget = Math.clamp(scrollTarget - (float) (scrollY * 24), 0, maxScroll);
        return true;
    }

    @Override public boolean keyPressed(KeyEvent event) {
        if (event.hasControlDown() && event.key() == GLFW.GLFW_KEY_F) {
            search.setFocused(true);
            setFocused(search);
            return true;
        }
        if (event.key() == GLFW.GLFW_KEY_ESCAPE) {
            if (!search.getValue().isBlank()) {
                search.setValue("");
                search.setFocused(false);
                setFocused(null);
                return true;
            }
            if (System.currentTimeMillis() - openTime < 400) return true;
            onClose(); return true;
        }
        if (event.key() == GLFW.GLFW_KEY_RIGHT_SHIFT) { onClose(); return true; }
        return super.keyPressed(event);
    }

    @Override public void onClose() {
        var p = parent;
        Minecraft.getInstance().execute(() -> Minecraft.getInstance().setScreenAndShow(p));
    }

    private java.util.List<String> visibleIds() {
        String query = search == null ? "" : search.getValue().trim().toLowerCase(java.util.Locale.ROOT);
        return ConstellationClient.featureManager().getAllIds().stream().filter(id ->
            ConstellationClient.featureManager().get(id).map(c -> filter.accepts(c.isEnabled())
                && (query.isBlank() || id.toLowerCase(java.util.Locale.ROOT).contains(query)
                || c.displayName().toLowerCase(java.util.Locale.ROOT).contains(query)
                || c.description().toLowerCase(java.util.Locale.ROOT).contains(query))).orElse(false)).toList();
    }

    private int cardY(int index, float offset) {
        int cols = Math.max(1, Math.min(4, (width - 20) / 240));
        int y = 52 + index / cols * 56 - (int) offset;
        long age = System.currentTimeMillis() - openTime;
        if (age < 600) {
            float shown = ConstellationTheme.easeOutCubic(Math.clamp((age - index * 28) / 260f, 0f, 1f));
            y += (int) ((1f - shown) * 14);
        }
        return y;
    }

    private static boolean inside(int mx, int my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    private enum Filter {
        ALL("All"), ENABLED("Enabled"), DISABLED("Disabled");
        final String label;
        Filter(String label) { this.label = label; }
        boolean accepts(boolean enabled) {
            return this == ALL || this == ENABLED && enabled || this == DISABLED && !enabled;
        }
        Filter next() { return values()[(ordinal() + 1) % values().length]; }
    }
}
