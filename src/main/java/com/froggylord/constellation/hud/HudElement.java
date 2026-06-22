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

    void render(GuiGraphicsExtractor g, int px, int py);

    default String editorLabel() { return id(); }
}
