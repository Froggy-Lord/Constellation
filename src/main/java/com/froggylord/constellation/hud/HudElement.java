package com.froggylord.constellation.hud;

import net.minecraft.client.gui.GuiGraphicsExtractor;

public interface HudElement {

    String id();

    HudPosition position();
    void setPosition(HudPosition pos);

    boolean isEnabled();
    void setEnabled(boolean enabled);

    boolean visibleNow();

    int width();
    int height();

    default int previewWidth() { return width(); }
    default int previewHeight() { return height(); }

    void render(GuiGraphicsExtractor g, int px, int py);

    default void renderPreview(GuiGraphicsExtractor g, int px, int py) {
        render(g, px, py);
    }

    default String editorLabel() { return id(); }
}
