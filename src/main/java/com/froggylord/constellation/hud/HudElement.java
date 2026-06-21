package com.froggylord.constellation.hud;

/**
 * Interface for all HUD elements. Both simple widgets and custom renderers implement this.
 */
public interface HudElement {

    String id();
    HudPosition position();
    void setPosition(HudPosition pos);
    boolean isEnabled();
    void setEnabled(boolean enabled);
    String label();
    String getValue();
}
