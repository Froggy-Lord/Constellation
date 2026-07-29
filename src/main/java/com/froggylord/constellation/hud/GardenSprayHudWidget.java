package com.froggylord.constellation.hud;

import com.froggylord.constellation.constellation.HerculesSprays;
import java.util.List;
import java.util.function.BooleanSupplier;

// ported from SkyHanni (LGPL-3.0-or-later): features/garden/pests/SprayDisplay.kt
public final class GardenSprayHudWidget extends ThemedHudWidget {
    private final BooleanSupplier configEnabled;
    private HudPosition position;
    private boolean enabled = true;

    public GardenSprayHudWidget(HudPosition position, BooleanSupplier configEnabled) {
        this.position = position;
        this.configEnabled = configEnabled;
    }

    @Override public String id() { return "garden-spray"; }
    @Override public HudPosition position() { return position; }
    @Override public void setPosition(HudPosition position) { this.position = position; }
    @Override public boolean isEnabled() { return enabled && configEnabled.getAsBoolean(); }
    @Override public void setEnabled(boolean enabled) { this.enabled = enabled; }
    @Override public boolean visibleNow() { return isEnabled() && HerculesSprays.hudVisible(); }
    @Override public String editorLabel() { return "Plot Spray"; }
    @Override protected String title() { return "Plot Spray"; }
    @Override protected List<Row> rows() { return HerculesSprays.hudRows().stream().map(row -> new Row("", row.label(), row.value())).toList(); }
    @Override protected List<Row> previewRows() { return List.of(new Row("", "Compost", "12:34")); }
}
