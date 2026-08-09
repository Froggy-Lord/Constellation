package com.froggylord.constellation.hud;

import com.froggylord.constellation.constellation.PegasusParty;

import java.util.List;
import java.util.function.BooleanSupplier;

// ported from Athen (BSD-3-Clause): modules/impl/kuudra/carry/KuudraCarryTracker.kt HUD
public final class CarryHudWidget extends ThemedHudWidget {
    private final HudPosition initial;
    private final BooleanSupplier enabled;

    public CarryHudWidget(HudPosition initial, BooleanSupplier enabled) {
        this.initial = initial;
        this.enabled = enabled;
    }

    @Override public String id() { return "pegasus-carry-tracker"; }
    @Override public HudPosition position() { return initial; }
    @Override public void setPosition(HudPosition position) {}
    @Override public boolean isEnabled() { return enabled.getAsBoolean(); }
    @Override public void setEnabled(boolean enabled) {}
    @Override public boolean visibleNow() { return isEnabled() && !PegasusParty.hudLines().isEmpty(); }
    @Override protected String title() { return "Carries"; }
    @Override protected List<Row> rows() {
        return PegasusParty.hudLines().stream().map(line -> new Row("", "", line)).toList();
    }
    @Override protected List<Row> previewRows() {
        List<String> live = PegasusParty.hudLines();
        if (!live.isEmpty()) return live.stream().map(line -> new Row("", "", line)).toList();
        return List.of(new Row("", "", "Example [T5] 3/10 (5m 30s | 12/hr) paid 30m/100m"));
    }
    @Override public String editorLabel() { return "Carry Tracker"; }
}
