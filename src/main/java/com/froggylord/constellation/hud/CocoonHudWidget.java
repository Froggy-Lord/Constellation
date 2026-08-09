package com.froggylord.constellation.hud;

import com.froggylord.constellation.constellation.SlayerCocoon;

import java.util.List;
import java.util.function.BooleanSupplier;

// ported from Athen (BSD-3-Clause): modules/impl/slayer/CocoonAlert.kt
public final class CocoonHudWidget extends ThemedHudWidget {
    private final HudPosition initial;
    private final BooleanSupplier enabled;

    public CocoonHudWidget(HudPosition initial, BooleanSupplier enabled) { this.initial = initial; this.enabled = enabled; }
    @Override public String id() { return "perseus-cocoon-timer"; }
    @Override public HudPosition position() { return initial; }
    @Override public void setPosition(HudPosition position) {}
    @Override public boolean isEnabled() { return enabled.getAsBoolean(); }
    @Override public void setEnabled(boolean enabled) {}
    @Override public boolean visibleNow() { return isEnabled() && !SlayerCocoon.hudLine().isBlank(); }
    @Override protected String title() { return "Cocoon"; }
    @Override protected List<Row> rows() { String line = SlayerCocoon.hudLine(); return line.isBlank() ? List.of() : List.of(new Row("", "", line)); }
    @Override protected List<Row> previewRows() { return List.of(new Row("", "", "Cocoon: §c4.6s")); }
    @Override public String editorLabel() { return "Cocoon Timer"; }
}
