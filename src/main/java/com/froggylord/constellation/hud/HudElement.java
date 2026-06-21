package com.froggylord.constellation.hud;

import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * Any positioned element on the HUD. Text widgets and custom overlays (like the dungeon
 * map) both implement this so they all live in one registry and one drag editor.
 *
 * Position is stored as a percentage of the screen (0-100) so it survives resolution
 * changes. width()/height() give the on-screen bounding box in pixels (for drag hit
 * detection). visibleNow() reports whether the element would actually draw this frame —
 * the editor only shows elements that are visible now or were within the last 10s.
 */
public interface HudElement {

    String id();

    HudPosition position();
    void setPosition(HudPosition pos);

    /** Master config toggle. */
    boolean isEnabled();
    void setEnabled(boolean enabled);

    /** Would this element actually render this frame (enabled + context, e.g. in a dungeon)? */
    boolean visibleNow();

    /** Bounding box in screen pixels, for the editor and layout. */
    int width();
    int height();

    /** Draw the element with its top-left at (px, py) screen pixels. */
    void render(GuiGraphicsExtractor g, int px, int py);

    /** A short label shown in the editor when the element is being dragged. */
    default String editorLabel() { return id(); }
}
