package com.froggylord.constellation.hud;

import com.froggylord.constellation.ConstellationClient;
import com.froggylord.constellation.render.NebulaTheme;
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

        var editable = ConstellationClient.hudManager().getEditable();
        for (HudElement el : editable) {
            int px = el.position().x() * width / 100;
            int py = el.position().y() * height / 100;
            int w = Math.max(el.width(), 12), h = Math.max(el.height(), 8);

            
            try { el.render(g, px, py); } catch (Exception ignored) {}

            
            int border = (dragging == el) ? NebulaTheme.ACCENT_GOLD : 0x55FFFFFF;
            g.fill(px - 2, py - 2, px + w + 2, py - 1, border);
            g.fill(px - 2, py + h + 1, px + w + 2, py + h + 2, border);
            g.fill(px - 2, py - 2, px - 1, py + h + 2, border);
            g.fill(px + w + 1, py - 2, px + w + 2, py + h + 2, border);

            
            String tag = el.editorLabel();
            g.text(font, tag, px, py - 11, dragging == el ? NebulaTheme.ACCENT_BRIGHT : NebulaTheme.STAR_MUTED, true);
        }

        String hint = editable.isEmpty()
            ? "No HUD elements visible right now — they appear here when on-screen (e.g. in a dungeon)"
            : "Drag elements to reposition  |  Esc to save & close";
        g.text(font, hint, width / 2 - font.width(hint) / 2, height - 12, 0xCCFFFFFF, true);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean dbl) {
        double mx = event.x(), my = event.y();
        for (HudElement el : ConstellationClient.hudManager().getEditable()) {
            int px = el.position().x() * width / 100;
            int py = el.position().y() * height / 100;
            int w = Math.max(el.width(), 12), h = Math.max(el.height(), 8);
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
            ny = Math.clamp(ny, 0, 98);
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
