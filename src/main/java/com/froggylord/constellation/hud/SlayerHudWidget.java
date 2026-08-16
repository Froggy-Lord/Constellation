package com.froggylord.constellation.hud;

import com.froggylord.constellation.constellation.PerseusSlayers;

import java.util.List;
import java.util.function.BooleanSupplier;

// HUD behavior ported from devonian (GPL-3.0): features/slayers/SlayerDisplay.kt
// cross-checked with Athen (BSD-3-Clause): modules/impl/slayer/SlayerDisplay.kt
public final class SlayerHudWidget extends ThemedHudWidget {
    private final HudPosition initial;
    private final BooleanSupplier enabled;

    public SlayerHudWidget(HudPosition initial, BooleanSupplier enabled) {
        this.initial = initial;
        this.enabled = enabled;
    }

    @Override public String id() { return "perseus-slayer-display"; }
    @Override public HudPosition position() { return initial; }
    @Override public void setPosition(HudPosition position) {}
    @Override public boolean isEnabled() { return enabled.getAsBoolean(); }
    @Override public void setEnabled(boolean enabled) {}
    @Override public boolean visibleNow() { return isEnabled() && !PerseusSlayers.hudLines().isEmpty(); }
    @Override protected String title() { return "Slayer"; }
    @Override protected List<Row> rows() {
        return PerseusSlayers.hudLines().stream().map(line -> new Row("", "", line)).toList();
    }
    @Override protected List<Row> previewRows() {
        List<String> live = PerseusSlayers.hudLines();
        if (!live.isEmpty()) return live.stream().map(line -> new Row("", "", line)).toList();
        return List.of(new Row("", "", "02:46"), new Row("", "", "Void T4"), new Row("", "", "64.2m HP"));
    }
    @Override public String editorLabel() { return "Slayer Display"; }
}
