package com.froggylord.constellation.hud;

import com.froggylord.constellation.render.NebulaTheme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

public class HudManager {

    private final List<HudElement> elements = new CopyOnWriteArrayList<>();
    private boolean editorOpen = false;

    public void register(HudElement element) { elements.add(element); }
    public void unregister(HudElement element) { elements.remove(element); }
    public List<HudElement> getAll() { return Collections.unmodifiableList(elements); }

    public void render(GuiGraphicsExtractor g, Font font, int screenW, int screenH) {
        if (editorOpen) return;
        for (HudElement el : elements) {
            if (!el.isEnabled() || el.getValue() == null) continue;
            HudPosition pos = el.position();
            int px = pos.x() * screenW / 100;
            int py = pos.y() * screenH / 100;

            String text = el.label() + ": " + el.getValue();
            int textW = font.width(text);

            // accent line
            if (px + textW + 6 <= screenW && py - 2 >= 0) {
                g.fill(px, py - 2, px + textW + 6, py - 1, NebulaTheme.HUD_ACCENT);
            }
            g.text(font, text, px + 3, py + 1, NebulaTheme.STAR_WHITE, true);
        }
    }

    public void setEditorOpen(boolean open) { this.editorOpen = open; }
    public boolean isEditorOpen() { return editorOpen; }
    public int elementCount() { return elements.size(); }

    public Optional<HudElement> get(String id) {
        return elements.stream().filter(e -> e.id().equals(id)).findFirst();
    }
}
