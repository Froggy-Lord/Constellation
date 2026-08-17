package com.froggylord.constellation.hud;

import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

public class HudManager {

    private static final long EDIT_GRACE_MS = 5_000;

    private final List<HudElement> elements = new CopyOnWriteArrayList<>();
    private final Map<String, Long> lastVisible = new ConcurrentHashMap<>();
    private boolean editorOpen = false;

    public void register(HudElement element) { elements.add(element); }
    public void unregister(HudElement element) { elements.remove(element); }
    public void removeIf(java.util.function.Predicate<HudElement> p) { elements.removeIf(p); }
    public List<HudElement> getAll() { return Collections.unmodifiableList(elements); }

    public void render(GuiGraphicsExtractor g, net.minecraft.client.gui.Font font, int screenW, int screenH) {
        if (editorOpen) return; 
        long now = System.currentTimeMillis();
        for (HudElement el : elements) {
            if (!el.visibleNow()) continue;
            lastVisible.put(el.id(), now);
            HudPosition position = position(el);
            int px = position.x() * screenW / 100;
            int py = position.y() * screenH / 100;
            float scale = scale(el);
            // ported from Athen (BSD-3-Clause): hud/HUDEditor.kt
            g.pose().pushMatrix();
            g.pose().translate(px, py);
            g.pose().scale(scale, scale);
            el.render(g, 0, 0);
            g.pose().popMatrix();
        }
    }

    public List<HudElement> getEditable() {
        long now = System.currentTimeMillis();
        List<HudElement> out = new ArrayList<>();
        for (HudElement el : elements) {
            if (el.visibleNow()) { lastVisible.put(el.id(), now); out.add(el); continue; }
            Long last = lastVisible.get(el.id());
            if (last != null && now - last < EDIT_GRACE_MS) out.add(el);
        }
        return out;
    }

    public void setEditorOpen(boolean open) { this.editorOpen = open; }
    public boolean isEditorOpen() { return editorOpen; }
    public int elementCount() { return elements.size(); }

    public float scale(HudElement element) {
        Float value = com.froggylord.constellation.ConstellationClient.cfg().hudScales.get(element.id());
        return value == null ? 1.0f : Math.clamp(value, 0.5f, 3.0f);
    }

    public void setScale(HudElement element, float scale) {
        com.froggylord.constellation.ConstellationClient.cfg().hudScales.put(
            element.id(), Math.clamp(scale, 0.5f, 3.0f));
    }

    // ported from Athen (BSD-3-Clause): hud/HUDElement.kt
    public HudPosition position(HudElement element) {
        HudPosition saved = com.froggylord.constellation.ConstellationClient.cfg().hudPositions.get(element.id());
        HudPosition value = saved == null ? element.position() : saved;
        return new HudPosition(Math.clamp(value.x(), 0, 98), Math.clamp(value.y(), 0, 92));
    }

    public void setPosition(HudElement element, HudPosition position) {
        HudPosition safe = new HudPosition(Math.clamp(position.x(), 0, 98), Math.clamp(position.y(), 0, 92));
        com.froggylord.constellation.ConstellationClient.cfg().hudPositions.put(element.id(), safe);
        element.setPosition(safe);
    }

    public Optional<HudElement> get(String id) {
        return elements.stream().filter(e -> e.id().equals(id)).findFirst();
    }
}
