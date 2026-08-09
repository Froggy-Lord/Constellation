package com.froggylord.constellation.hud;

import com.froggylord.constellation.constellation.LyraQuiver;
import java.util.List;
import java.util.function.BooleanSupplier;

// ported from Devonian (GPL-3.0): features/misc/QuiverDisplay.kt
// ported from NoFrills (GPL-3.0): hud/elements/Quiver.java
public final class QuiverHudWidget extends ThemedHudWidget {
    private final BooleanSupplier gate;
    private HudPosition position;
    private boolean enabled = true;

    public QuiverHudWidget(HudPosition position, BooleanSupplier gate) { this.position = position; this.gate = gate; }
    @Override public String id() { return "lyra-quiver"; }
    @Override public HudPosition position() { return position; }
    @Override public void setPosition(HudPosition value) { position = value; }
    @Override public boolean isEnabled() { return enabled && gate.getAsBoolean(); }
    @Override public void setEnabled(boolean value) { enabled = value; }
    @Override public boolean visibleNow() { return isEnabled() && LyraQuiver.visible(); }
    @Override public String editorLabel() { return "Quiver"; }
    @Override protected String title() { return "Quiver"; }
    @Override protected List<Row> rows() {
        LyraQuiver.State state = LyraQuiver.state();
        if (state == null) return List.of();
        String icon = LyraQuiver.config().quiverShowIcon ? "A" : "";
        return List.of(new Row(icon, state.arrowName(), Integer.toString(state.amount()), LyraQuiver.amountColor()));
    }
    @Override protected List<Row> previewRows() { return List.of(new Row("A", "Flint", "2,696", 0xFF55FF55)); }
}
