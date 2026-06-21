package com.froggylord.constellation.hud;

import com.froggylord.constellation.render.NebulaTheme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.function.Supplier;

/**
 * A simple "Label: value" HUD element. The value supplier returns null when the element
 * shouldn't show (e.g. dungeon stats outside a dungeon), which makes visibleNow() false.
 */
public class HudWidget implements HudElement {

    private final String id;
    private final String label;
    private final Supplier<String> value;
    private HudPosition position;
    private boolean enabled;

    public HudWidget(String id, String label, Supplier<String> value, HudPosition position, boolean enabled) {
        this.id = id;
        this.label = label;
        this.value = value;
        this.position = position;
        this.enabled = enabled;
    }

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
        return Minecraft.getInstance().font.width(label + ": " + (v == null ? "" : v)) + 6;
    }

    @Override
    public int height() {
        return Minecraft.getInstance().font.lineHeight + 2;
    }

    @Override
    public void render(GuiGraphicsExtractor g, int px, int py) {
        String v = currentValue();
        if (v == null) return;
        var font = Minecraft.getInstance().font;
        String text = label + ": " + v;
        int w = font.width(text);
        // accent line + text
        g.fill(px, py - 2, px + w + 6, py - 1, NebulaTheme.HUD_ACCENT);
        g.text(font, text, px + 3, py + 1, NebulaTheme.STAR_WHITE, true);
    }

    @Override
    public String editorLabel() { return label; }
}
