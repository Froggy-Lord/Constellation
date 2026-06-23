package com.froggylord.constellation.ui;

import com.froggylord.constellation.ConstellationClient;
import com.froggylord.constellation.render.ConstellationTheme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

public class HubScreen extends Screen {

    private final Screen parent;
    private long openTime;
    private int scrollOff = 0;
    private int maxScroll = 0;
    private boolean scrolling = false;
    private int scrollGrabY = 0, scrollGrabOff = 0;

    public HubScreen(Screen parent) {
        super(Component.literal("Constellation"));
        this.parent = parent;
    }

    @Override protected void init() { this.openTime = System.currentTimeMillis(); }
    @Override public boolean isPauseScreen() { return false; }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float delta) {
        Minecraft mc = Minecraft.getInstance();
        int w = mc.getWindow().getGuiScaledWidth();
        int h = mc.getWindow().getGuiScaledHeight();
        Font font = mc.font;

        SpaceBackground.render(g, w, h, delta);

        // ---- header panel ----
        int headerH = 36;
        ConstellationTheme.panel(g, 0, 0, w, headerH);
        String title = "Constellation";
        int tw = font.width(title);
        g.text(font, title, 12, 10, ConstellationTheme.ACCENT_BRIGHT, false);
        String sub = "14 modules — right shift to open, esc to close";
        g.text(font, sub, 14 + tw, 14, ConstellationTheme.TEXT_MUTED, false);

        // ---- constellation cards ----
        int cols = Math.max(1, Math.min(4, (w - 20) / 240));
        int cardW = Math.min(280, ((w - 20) - (cols - 1) * 8) / cols);
        int cardH = 50;
        int gridX = 10, gridY = headerH + 10;

        var allIds = ConstellationClient.featureManager().getAllIds();
        int idx = 0;
        for (String id : allIds) {
            var opt = ConstellationClient.featureManager().get(id);
            if (opt.isEmpty()) continue;
            var c = opt.get();
            int col = idx % cols, row = idx / cols;
            int cx = gridX + col * (cardW + 8);
            int cy = gridY + row * (cardH + 6) - scrollOff;

            if (cy + cardH > headerH && cy < h - 80) {
                boolean enabled = c.isEnabled();
                boolean hover = mx >= cx && mx <= cx + cardW && my >= cy && my <= cy + cardH;
                ConstellationTheme.card(g, cx, cy, cardW, cardH, enabled || hover);

                String name = c.displayName();
                g.text(font, name, cx + 6, cy + 6,
                    enabled ? ConstellationTheme.ACCENT_BRIGHT : ConstellationTheme.TEXT, false);

                String desc = c.description();
                int maxW = cardW - 14;
                if (font.width(desc) > maxW) desc = font.plainSubstrByWidth(desc, maxW - 6) + "…";
                g.text(font, desc, cx + 6, cy + 22, ConstellationTheme.TEXT_MUTED, false);

                // toggle indicator
                int tx = cx + cardW - 32, ty = cy + 6;
                ConstellationTheme.toggle(g, tx, ty, enabled);
            }
            idx++;
        }

        int totalRows = (int) Math.ceil((double) idx / cols);
        maxScroll = Math.max(0, totalRows * (cardH + 6) - (h - headerH - 90));
        if (scrollOff > maxScroll) scrollOff = maxScroll;
        if (scrollOff < 0) scrollOff = 0;

        // ---- scrollbar ----
        if (maxScroll > 0) {
            int sbX = w - 6, sbY = gridY, sbH = h - gridY - 80;
            g.fill(sbX, sbY, sbX + 4, sbY + sbH, ConstellationTheme.BORDER);
            float ratio = (float) sbH / (sbH + maxScroll);
            int thumbH = Math.max(20, (int) (sbH * ratio));
            int thumbY = sbY + (int) ((float) scrollOff / maxScroll * (sbH - thumbH));
            g.fill(sbX, thumbY, sbX + 4, thumbY + thumbH, ConstellationTheme.ACCENT);
        }

        // ---- bottom buttons ----
        int btnW = 140, btnH = 24, btnGap = 10;
        int hudX = w / 2 - btnW - btnGap / 2, cfgX = w / 2 + btnGap / 2;
        int btnY = h - btnH - 10;

        boolean hoverHud = mx >= hudX && mx <= hudX + btnW && my >= btnY && my <= btnY + btnH;
        boolean hoverCfg = mx >= cfgX && mx <= cfgX + btnW && my >= btnY && my <= btnY + btnH;

        ConstellationTheme.panel(g, hudX, btnY, btnW, btnH);
        if (hoverHud) g.fill(hudX, btnY, hudX + 3, btnY + btnH, ConstellationTheme.ACCENT);
        String hudLabel = "HUD Editor";
        g.text(font, hudLabel, hudX + btnW / 2 - font.width(hudLabel) / 2, btnY + 7,
            hoverHud ? ConstellationTheme.ACCENT_BRIGHT : ConstellationTheme.TEXT, false);

        ConstellationTheme.panel(g, cfgX, btnY, btnW, btnH);
        if (hoverCfg) g.fill(cfgX, btnY, cfgX + 3, btnY + btnH, ConstellationTheme.ACCENT);
        String cfgLabel = "Config";
        g.text(font, cfgLabel, cfgX + btnW / 2 - font.width(cfgLabel) / 2, btnY + 7,
            hoverCfg ? ConstellationTheme.ACCENT_BRIGHT : ConstellationTheme.TEXT, false);

        // hint
        String hint = "right shift · esc to close · scroll to browse";
        g.text(font, hint, w / 2 - font.width(hint) / 2, h - 2 - font.lineHeight,
            ConstellationTheme.TEXT_FAINT, false);
    }

    // ---- input (unchanged logic) ----

    @Override public boolean mouseClicked(MouseButtonEvent event, boolean dbl) {
        int mx = (int) event.x(), my = (int) event.y();
        Minecraft mc = Minecraft.getInstance();
        int w = mc.getWindow().getGuiScaledWidth(), h = mc.getWindow().getGuiScaledHeight();
        int btnW = 140, btnH = 24, btnGap = 10;
        int hudX = w / 2 - btnW - btnGap / 2, cfgX = w / 2 + btnGap / 2, btnY = h - btnH - 10;

        if (mx >= cfgX && mx <= cfgX + btnW && my >= btnY && my <= btnY + btnH) {
            var ids = ConstellationClient.featureManager().getLoadedIds();
            String first = ids.isEmpty() ? "apollo" : ids.iterator().next();
            mc.execute(() -> mc.setScreenAndShow(new ConfigScreen(first, this)));
            return true;
        }
        if (mx >= hudX && mx <= hudX + btnW && my >= btnY && my <= btnY + btnH) {
            mc.execute(() -> mc.setScreenAndShow(new com.froggylord.constellation.hud.HudEditScreen(this)));
            return true;
        }

        int cols = Math.max(1, Math.min(4, (w - 20) / 240));
        int cardW = Math.min(280, ((w - 20) - (cols - 1) * 8) / cols);
        var allIds = ConstellationClient.featureManager().getAllIds();
        int idx = 0;
        for (String id : allIds) {
            int col = idx % cols, row = idx / cols;
            int cx = 10 + col * (cardW + 8);
            int cy = 46 + row * 56 - scrollOff;
            var opt = ConstellationClient.featureManager().get(id);
            if (opt.isEmpty()) continue;
            if (mx >= cx && mx <= cx + cardW && my >= cy && my <= cy + 50) {
                final String fid = id;
                mc.execute(() -> mc.setScreenAndShow(new ConfigScreen(fid, parent)));
                return true;
            }
            idx++;
        }
        if (maxScroll > 0 && mx >= w - 6) { scrolling = true; scrollGrabY = my; scrollGrabOff = scrollOff; return true; }
        return super.mouseClicked(event, dbl);
    }

    @Override public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        if (scrolling) {
            int my = (int) event.y();
            float pct = (float) (my - scrollGrabY) / (Minecraft.getInstance().getWindow().getGuiScaledHeight() - 80);
            scrollOff = Math.clamp(scrollGrabOff + (int) (pct * maxScroll), 0, maxScroll);
            return true;
        }
        return super.mouseDragged(event, dx, dy);
    }

    @Override public boolean mouseReleased(MouseButtonEvent event) { scrolling = false; return super.mouseReleased(event); }
    @Override public boolean mouseScrolled(double mx, double my, double scrollX, double scrollY) {
        scrollOff = Math.clamp(scrollOff - (int) (scrollY * 20), 0, maxScroll);
        return true;
    }

    @Override public boolean keyPressed(KeyEvent event) {
        if (event.key() == GLFW.GLFW_KEY_ESCAPE) {
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
}
