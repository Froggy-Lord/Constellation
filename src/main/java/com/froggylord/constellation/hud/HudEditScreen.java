package com.froggylord.constellation.hud;

import com.froggylord.constellation.ConstellationClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

public class HudEditScreen extends Screen {

    private final Screen parent;
    private HudElement dragging = null;
    private double dragOffX, dragOffY;

    public HudEditScreen(Screen parent) {
        super(Component.literal("HUD Editor"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        ConstellationClient.hudManager().setEditorOpen(true);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        // ported from Athen (BSD-3-Clause): hud/HUDEditor.kt
        g.fill(0, 0, width, height, 0x20000000);

        var manager = ConstellationClient.hudManager();
        var editable = manager.getEditable();
        for (HudElement el : editable) {
            HudPosition position = manager.position(el);
            int px = position.x() * width / 100;
            int py = position.y() * height / 100;
            float scale = manager.scale(el);
            int w = Math.max(Math.round(el.previewWidth() * scale), 12);
            int h = Math.max(Math.round(el.previewHeight() * scale), 8);

            boolean pushed = false;
            try {
                g.pose().pushMatrix();
                pushed = true;
                g.pose().translate(px, py);
                g.pose().scale(scale, scale);
                el.renderPreview(g, 0, 0);
            } catch (Exception ignored) {
            } finally {
                if (pushed) g.pose().popMatrix();
            }
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean dbl) {
        double mx = event.x(), my = event.y();
        var manager = ConstellationClient.hudManager();
        for (HudElement el : manager.getEditable()) {
            HudPosition position = manager.position(el);
            int px = position.x() * width / 100;
            int py = position.y() * height / 100;
            float scale = manager.scale(el);
            int w = Math.max(Math.round(el.previewWidth() * scale), 12);
            int h = Math.max(Math.round(el.previewHeight() * scale), 8);
            if (mx >= px - 2 && mx <= px + w + 2 && my >= py - 2 && my <= py + h + 2) {
                dragging = el;
                dragOffX = mx - px;
                dragOffY = my - py;
                return true;
            }
        }
        return super.mouseClicked(event, dbl);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        if (dragging != null) {
            double mx = event.x(), my = event.y();
            int nx = (int) Math.round((mx - dragOffX) / width * 100);
            int ny = (int) Math.round((my - dragOffY) / height * 100);
            nx = Math.clamp(nx, 0, 98);
            ny = Math.clamp(ny, 0, 92);
            ConstellationClient.hudManager().setPosition(dragging, new HudPosition(nx, ny));
            return true;
        }
        return super.mouseDragged(event, dx, dy);
    }

    // ported from Athen (BSD-3-Clause): hud/HUDEditor.kt
    @Override
    public boolean mouseScrolled(double mx, double my, double horizontalAmount, double verticalAmount) {
        var manager = ConstellationClient.hudManager();
        var editable = manager.getEditable();
        for (int i = editable.size() - 1; i >= 0; i--) {
            HudElement el = editable.get(i);
            HudPosition position = manager.position(el);
            int px = position.x() * width / 100;
            int py = position.y() * height / 100;
            float scale = manager.scale(el);
            int w = Math.max(Math.round(el.previewWidth() * scale), 12);
            int h = Math.max(Math.round(el.previewHeight() * scale), 8);
            if (mx < px - 2 || mx > px + w + 2 || my < py - 2 || my > py + h + 2) continue;
            float delta = verticalAmount > 0 ? 0.1f : -0.1f;
            manager.setScale(el, scale + delta);
            return true;
        }
        return super.mouseScrolled(mx, my, horizontalAmount, verticalAmount);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        dragging = null;
        return super.mouseReleased(event);
    }

    @Override
    public void onClose() {
        ConstellationClient.hudManager().setEditorOpen(false);
        ConstellationClient.saveConfig();
        var p = parent;
        Minecraft.getInstance().execute(() -> Minecraft.getInstance().setScreenAndShow(p));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
