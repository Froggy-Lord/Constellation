package com.froggylord.constellation.hud;

import com.froggylord.constellation.ConstellationClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
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
        g.fill(0, 0, width, height, 0x8812121F);
        Font font = Minecraft.getInstance().font;

        for (HudElement el : ConstellationClient.hudManager().getAll()) {
            HudPosition pos = el.position();
            int px = pos.x() * width / 100;
            int py = pos.y() * height / 100;

            String val = el.getValue() != null ? el.getValue() : "...";
            String text = el.label() + ": " + val;
            int textW = font.width(text);
            int textH = font.lineHeight;

            g.fill(px - 2, py - 2, px + textW + 8, py + textH + 4, 0x8812121F);
            g.text(font, text, px + 3, py + 1, 0xFFFFFFFF, true);

            int borderCol = (dragging == el) ? 0xFFFFCC55 : 0x44FFFFFF;
            g.fill(px - 2, py - 2, px + textW + 8, py - 1, borderCol);
            g.fill(px - 2, py + textH + 3, px + textW + 8, py + textH + 4, borderCol);
            g.fill(px - 2, py - 2, px - 1, py + textH + 4, borderCol);
            g.fill(px + textW + 7, py - 2, px + textW + 8, py + textH + 4, borderCol);
        }

        String hint = "Drag elements to reposition  |  Esc to save & close";
        g.text(font, hint, width / 2 - font.width(hint) / 2, height - 12, 0x88FFFFFF, true);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean dbl) {
        double mx = event.x(), my = event.y();
        for (HudElement el : ConstellationClient.hudManager().getAll()) {
            HudPosition pos = el.position();
            int px = pos.x() * width / 100;
            int py = pos.y() * height / 100;
            Font font = Minecraft.getInstance().font;
            String val = el.getValue() != null ? el.getValue() : "...";
            int textW = font.width(el.label() + ": " + val);

            if (mx >= px && mx <= px + textW + 10 && my >= py && my <= py + font.lineHeight + 4) {
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
            int nx = (int) ((mx - dragOffX) / width * 100);
            int ny = (int) ((my - dragOffY) / height * 100);
            nx = Math.clamp(nx, 0, 95);
            ny = Math.clamp(ny, 0, 95);
            dragging.setPosition(new HudPosition(nx, ny));
            return true;
        }
        return super.mouseDragged(event, dx, dy);
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
}
