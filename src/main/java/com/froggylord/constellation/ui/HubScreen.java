package com.froggylord.constellation.ui;

import com.froggylord.constellation.ConstellationClient;
import com.froggylord.constellation.render.NebulaTheme;
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

    @Override
    protected void init() { this.openTime = System.currentTimeMillis(); }

    @Override public boolean isPauseScreen() { return false; }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float delta) {
        Minecraft mc = Minecraft.getInstance();
        int w = mc.getWindow().getGuiScaledWidth();
        int h = mc.getWindow().getGuiScaledHeight();
        SpaceBackground.render(g, w, h, delta);
        Font font = mc.font;

        g.fill(0, 0, w, h, NebulaTheme.BG_DEEP);

        String title = "✧ Constellation ✧";
        g.text(font, title, w / 2 - font.width(title) / 2, 6, NebulaTheme.ACCENT_GOLD, false);

        
        int cols = Math.max(1, Math.min(4, (w - 20) / 220));
        int cardW = ((w - 20) - (cols - 1) * 6) / cols;
        int cardH = 54;
        int gapX = 6, gapY = 4;
        int gridX = 10, gridY = 22;

        var allIds = ConstellationClient.featureManager().getAllIds();

        int row = 0, col = 0;
        for (String id : allIds) {
            var opt = ConstellationClient.featureManager().get(id);
            if (opt.isEmpty()) continue;
            var c = opt.get();
            int cx = gridX + col * (cardW + gapX);
            int cy = gridY + row * (cardH + gapY) - scrollOff;

            
            if (cy + cardH > 0 && cy < h - 50) {
                boolean enabled = c.isEnabled();
                boolean hover = mx >= cx && mx <= cx + cardW && my >= cy && my <= cy + cardH;
                int bg = hover ? 0xFF2A2A3A : 0xFF1A1A28;
                g.fill(cx, cy, cx + cardW, cy + cardH, bg);
                if (hover || enabled) g.fill(cx, cy, cx + 3, cy + cardH, enabled ? NebulaTheme.ACCENT_GOLD : NebulaTheme.ACCENT_DIM);

                String name = c.displayName();
                g.text(font, enabled ? "✦ " + name : "✧ " + name, cx + 8, cy + 5,
                    enabled ? NebulaTheme.STAR_WHITE : NebulaTheme.STAR_MUTED, false);

                String desc = c.description();
                
                int textW = cardW - 16;
                if (font.width(desc) > textW) {
                    for (String part : desc.split(" — ")) {
                        String trimmed = part.trim();
                        if (trimmed.isEmpty()) continue;
                        if (font.width(trimmed) > textW) trimmed = font.plainSubstrByWidth(trimmed, textW);
                        g.text(font, trimmed, cx + 8, cy + 20, NebulaTheme.STAR_MUTED, false);
                        break; 
                    }
                } else {
                    g.text(font, desc, cx + 8, cy + 20, NebulaTheme.STAR_MUTED, false);
                }

                
                int sw = 16, sh = 10, sx = cx + cardW - sw - 10, sy = cy + cardH - sh - 6;
                int swCol = enabled ? NebulaTheme.ACCENT_GOLD : 0xFF444466;
                g.fill(sx, sy, sx + sw, sy + sh, swCol);
                int knobX = enabled ? sx + sw - 6 : sx + 1;
                g.fill(knobX, sy + 1, knobX + 5, sy + sh - 1, 0xFFFFFFFF);
            }

            col++;
            if (col >= cols) { col = 0; row++; }
        }

        int totalRows = (int) Math.ceil((double) allIds.size() / cols);
        maxScroll = Math.max(0, totalRows * (cardH + gapY) - (h - 60));
        if (scrollOff > maxScroll) scrollOff = maxScroll;
        if (scrollOff < 0) scrollOff = 0;

        
        if (maxScroll > 0) {
            int sbX = w - 6, sbH = h - 56, sbY = gridY;
            g.fill(sbX, sbY, sbX + 4, sbY + sbH, 0xFF2A2A3A);
            float ratio = (float) sbH / (sbH + maxScroll);
            int thumbH = Math.max(20, (int) (sbH * ratio));
            int thumbY = sbY + (int) ((float) scrollOff / maxScroll * (sbH - thumbH));
            g.fill(sbX, thumbY, sbX + 4, thumbY + thumbH, NebulaTheme.ACCENT_DIM);
        }

        
        int btnW = 120, btnH = 22, hudBtnX = w / 2 - btnW / 2, hudBtnY = h - 58;
        boolean hoverHud = mx >= hudBtnX && mx <= hudBtnX + btnW && my >= hudBtnY && my <= hudBtnY + btnH;
        g.fill(hudBtnX, hudBtnY, hudBtnX + btnW, hudBtnY + btnH, hoverHud ? 0xFF3A3050 : 0xFF1A1428);
        g.fill(hudBtnX, hudBtnY, hudBtnX + btnW, hudBtnY + 2, NebulaTheme.ACCENT_GOLD);
        String hudLabel = "HUD Editor";
        g.text(font, hudLabel, w / 2 - font.width(hudLabel) / 2, hudBtnY + 6,
            hoverHud ? NebulaTheme.ACCENT_BRIGHT : NebulaTheme.STAR_WHITE, false);

        
        int setBtnY = h - 32;
        boolean hoverSet = mx >= hudBtnX && mx <= hudBtnX + btnW && my >= setBtnY && my <= setBtnY + btnH;
        g.fill(hudBtnX, setBtnY, hudBtnX + btnW, setBtnY + btnH, hoverSet ? 0xFF3A3050 : 0xFF1A1428);
        g.fill(hudBtnX, setBtnY, hudBtnX + btnW, setBtnY + 2, NebulaTheme.ACCENT_GOLD);
        String setLabel = "Settings";
        g.text(font, setLabel, w / 2 - font.width(setLabel) / 2, setBtnY + 6,
            hoverSet ? NebulaTheme.ACCENT_BRIGHT : NebulaTheme.STAR_WHITE, false);

        String esc = "Right Shift or ESC to close";
        g.text(font, esc, w / 2 - font.width(esc) / 2, h - 10, NebulaTheme.STAR_MUTED, false);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean dbl) {
        int mx = (int) event.x(), my = (int) event.y();
        Minecraft mc = Minecraft.getInstance();
        int w = mc.getWindow().getGuiScaledWidth();
        int h = mc.getWindow().getGuiScaledHeight();

        int btnW = 120, hudBtnY = h - 58, setBtnY = h - 32, btnX = w / 2 - btnW / 2;

        if (mx >= btnX && mx <= btnX + btnW && my >= setBtnY && my <= setBtnY + 22) {
            var ids = ConstellationClient.featureManager().getLoadedIds();
            String first = ids.isEmpty() ? "apollo" : ids.iterator().next();
            mc.execute(() -> mc.setScreenAndShow(new ConfigScreen(first, this)));
            return true;
        }

        if (mx >= btnX && mx <= btnX + btnW && my >= hudBtnY && my <= hudBtnY + 22) {
            mc.execute(() -> mc.setScreenAndShow(new com.froggylord.constellation.hud.HudEditScreen(this)));
            return true;
        }

        
        int cols = Math.max(1, Math.min(4, (w - 20) / 220));
        int cardW = ((w - 20) - (cols - 1) * 6) / cols;
        int cardH = 54;
        int gapX = 6, gapY = 4;
        int gridX = 10, gridY = 22;

        var allIds = ConstellationClient.featureManager().getAllIds();
        int col = 0, row = 0, idx = 0;
        for (String id : allIds) {
            int cx = gridX + col * (cardW + gapX);
            int cy = gridY + row * (cardH + gapY) - scrollOff;
            if (mx >= cx && mx <= cx + cardW && my >= cy && my <= cy + cardH) {
                final String fid = id;
                mc.execute(() -> mc.setScreenAndShow(new ConfigScreen(fid, parent)));
                return true;
            }
            idx++;
            col = idx % cols;
            row = idx / cols;
        }

        
        if (maxScroll > 0 && mx >= w - 6) {
            scrolling = true;
            scrollGrabY = my;
            scrollGrabOff = scrollOff;
            return true;
        }
        return super.mouseClicked(event, dbl);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        if (scrolling) {
            int my = (int) event.y();
            float pct = (float) (my - scrollGrabY) / (Minecraft.getInstance().getWindow().getGuiScaledHeight() - 80);
            scrollOff = Math.clamp(scrollGrabOff + (int) (pct * maxScroll), 0, maxScroll);
            return true;
        }
        return super.mouseDragged(event, dx, dy);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        scrolling = false;
        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double scrollX, double scrollY) {
        scrollOff = Math.clamp(scrollOff - (int) (scrollY * 20), 0, maxScroll);
        return true;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.key() == GLFW.GLFW_KEY_ESCAPE) {
            if (System.currentTimeMillis() - openTime < 400) return true;
            onClose();
            return true;
        }
        if (event.key() == GLFW.GLFW_KEY_RIGHT_SHIFT) {
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
}
