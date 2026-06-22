package com.froggylord.constellation.hud;

import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

public class HudManager {

    private static final long EDIT_GRACE_MS = 10_000;

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
            int px = el.position().x() * screenW / 100;
            int py = el.position().y() * screenH / 100;
            el.render(g, px, py);
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

    public Optional<HudElement> get(String id) {
        return elements.stream().filter(e -> e.id().equals(id)).findFirst();
    }
}
