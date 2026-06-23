package com.froggylord.constellation.hud;

import com.froggylord.constellation.render.ConstellationTheme;
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
    private String lastValue = null;
    private long lastChangedAt = 0;

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

        // track value changes for pulse animation
        if (!v.equals(lastValue)) {
            lastValue = v;
            lastChangedAt = System.currentTimeMillis();
        }

        var font = Minecraft.getInstance().font;
        String text = v;
        int w = font.width(text);
        int pad = 4;

        // subtle dark background panel
        g.fill(px, py, px + w + pad * 2, py + font.lineHeight + pad, 0xCC101018);

        // pulse glow when value just changed — fades over 800ms
        long age = System.currentTimeMillis() - lastChangedAt;
        if (age < 800) {
            float pulse = 1f - (float) age / 800f;
            int glowAlpha = (int) (pulse * 50);
            g.fill(px, py, px + w + pad * 2, py + font.lineHeight + pad, (glowAlpha << 24) | 0xFFCC33);
        }

        // thin gold accent line along the top edge
        g.fill(px, py, px + w + pad * 2, py + 1, ConstellationTheme.ACCENT);
        // value text — white with shadow for readability
        g.text(font, text, px + pad, py + pad - 1, ConstellationTheme.TEXT, true);
    }

    @Override
    public String editorLabel() { return label; }
}
