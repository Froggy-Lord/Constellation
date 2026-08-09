package com.froggylord.constellation.hud;

import com.froggylord.constellation.constellation.SlayerSpecialInfo;

import java.util.List;
import java.util.function.BooleanSupplier;

// ported from Athen (BSD-3-Clause): modules/impl/slayer/AttunementDisplay.kt
public final class AttunementHudWidget extends ThemedHudWidget {
    private final HudPosition initial;
    private final BooleanSupplier enabled;

    public AttunementHudWidget(HudPosition initial, BooleanSupplier enabled) {
        this.initial = initial;
        this.enabled = enabled;
    }

    @Override public String id() { return "perseus-inferno-attunement"; }
    @Override public HudPosition position() { return initial; }
    @Override public void setPosition(HudPosition position) {}
    @Override public boolean isEnabled() { return enabled.getAsBoolean(); }
    @Override public void setEnabled(boolean enabled) {}
    @Override public boolean visibleNow() { return isEnabled() && !SlayerSpecialInfo.hudLine().isBlank(); }
    @Override protected String title() { return "Attunement"; }
    @Override protected List<Row> rows() {
        String line = SlayerSpecialInfo.hudLine();
        return line.isBlank() ? List.of() : List.of(new Row("", "", line));
    }
    @Override protected List<Row> previewRows() { return List.of(new Row("", "", "§l§eAURIC x5")); }
    @Override public String editorLabel() { return "Inferno Attunement"; }
}
