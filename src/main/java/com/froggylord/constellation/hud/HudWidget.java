package com.froggylord.constellation.hud;

import java.util.function.Supplier;

/**
 * A single HUD element owned by a constellation.
 * Has a label, a value supplier, a position, and an enabled state.
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

    @Override
    public String id() { return id; }

    @Override
    public HudPosition position() { return position; }

    @Override
    public void setPosition(HudPosition pos) { this.position = pos; }

    @Override
    public boolean isEnabled() { return enabled; }

    @Override
    public void setEnabled(boolean e) { this.enabled = e; }

    @Override
    public String label() { return label; }

    @Override
    public String getValue() { return value.get(); }

    // for editor — provide values for the edit overlay
    public String getLabel() { return label; }
}
