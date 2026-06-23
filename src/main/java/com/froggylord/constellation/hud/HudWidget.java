package com.froggylord.constellation.hud;

import com.froggylord.constellation.render.NebulaTheme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.function.Supplier;

public class HudWidget implements HudElement {

    private final String id;
    private final String label;
    private final Supplier<String> value;
    private HudPosition position;
    private boolean enabled;
    private java.util.function.Consumer<HudPosition> onMove;

    public HudWidget(String id, String label, Supplier<String> value, HudPosition position, boolean enabled) {
        this.id = id;
        this.label = label;
        this.value = value;
        this.position = position;
        this.enabled = enabled;
    }

    public HudWidget onMove(java.util.function.Consumer<HudPosition> cb) { this.onMove = cb; return this; }

    private String currentValue() {
        try { return value.get(); } catch (Exception e) { return null; }
    }

    @Override public String id() { return id; }
    @Override public HudPosition position() { return position; }
    @Override public void setPosition(HudPosition pos) { this.position = pos; }
    @Override public boolean isEnabled() { return enabled; }
    @Override public void setEnabled(boolean e) { this.enabled = e; }

    @Override
    public boolean visibleNow() {
        return enabled && currentValue() != null;
    }

    @Override
    public int width() {
        String v = currentValue();
        return Minecraft.getInstance().font.width(v == null ? label : v) + 10;
    }

    @Override
    public int height() {
        return Minecraft.getInstance().font.lineHeight + 6;
    }

    @Override
    public void render(GuiGraphicsExtractor g, int px, int py) {
        String v = currentValue();
        if (v == null) return;
        var font = Minecraft.getInstance().font;
        String text = v; // just the value, no label prefix
        int w = font.width(text);
        int pad = 4;

        // subtle dark background panel so text is readable over game world
        g.fill(px, py, px + w + pad * 2, py + font.lineHeight + pad, 0x88101018);
        // thin accent line along the top edge
        g.fill(px, py, px + w + pad * 2, py + 1, NebulaTheme.HUD_ACCENT);
        // value text — white with shadow for readability
        g.text(font, text, px + pad, py + pad - 1, NebulaTheme.STAR_WHITE, true);
    }

    @Override
    public String editorLabel() { return label; }
}
