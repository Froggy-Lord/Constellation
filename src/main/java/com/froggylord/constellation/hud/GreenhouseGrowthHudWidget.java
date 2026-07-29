package com.froggylord.constellation.hud;

import com.froggylord.constellation.constellation.HerculesGreenhouse;
import java.util.List;
import java.util.function.BooleanSupplier;

// ported from SkyHanni (LGPL-3.0-or-later): features/garden/greenhouse/GrowthCycle.kt
public final class GreenhouseGrowthHudWidget extends ThemedHudWidget {
    private final BooleanSupplier configEnabled;
    private HudPosition position;
    private boolean enabled = true;

    public GreenhouseGrowthHudWidget(HudPosition position, BooleanSupplier configEnabled) {
        this.position = position;
        this.configEnabled = configEnabled;
    }

    @Override public String id() { return "garden-greenhouse-growth"; }
    @Override public HudPosition position() { return position; }
    @Override public void setPosition(HudPosition position) { this.position = position; }
    @Override public boolean isEnabled() { return enabled && configEnabled.getAsBoolean(); }
    @Override public void setEnabled(boolean enabled) { this.enabled = enabled; }
    @Override public boolean visibleNow() { return isEnabled() && HerculesGreenhouse.hudVisible(); }
    @Override public String editorLabel() { return "Greenhouse Growth"; }
    @Override protected String title() { return "Greenhouse"; }
    @Override protected List<Row> rows() { var row = HerculesGreenhouse.hudRow(); return row == null ? List.of() : List.of(new Row("", row.label(), row.value(), row.color())); }
    @Override protected List<Row> previewRows() { return List.of(new Row("", "Next stage", "1h 40m", 0xFF55FF55)); }
}
