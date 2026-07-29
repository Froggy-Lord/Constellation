package com.froggylord.constellation.hud;

import com.froggylord.constellation.constellation.HerculesFortune;
import java.util.List;
import java.util.function.BooleanSupplier;

// ported from SkyHanni (LGPL-3.0-or-later): features/garden/FarmingFortuneDisplay.kt
public final class FarmingFortuneHudWidget extends ThemedHudWidget {
    private final BooleanSupplier configEnabled;
    private HudPosition position;
    private boolean enabled = true;

    public FarmingFortuneHudWidget(HudPosition position, BooleanSupplier configEnabled) {
        this.position = position;
        this.configEnabled = configEnabled;
    }

    @Override public String id() { return "garden-farming-fortune"; }
    @Override public HudPosition position() { return position; }
    @Override public void setPosition(HudPosition position) { this.position = position; }
    @Override public boolean isEnabled() { return enabled && configEnabled.getAsBoolean(); }
    @Override public void setEnabled(boolean enabled) { this.enabled = enabled; }
    @Override public boolean visibleNow() { return isEnabled() && HerculesFortune.hudVisible(); }
    @Override public String editorLabel() { return "Farming Fortune"; }
    @Override protected String title() { return "Farming Fortune"; }
    @Override protected List<Row> rows() { return HerculesFortune.hudRows().stream().map(row -> new Row("", row.label(), row.value(), row.color())).toList(); }
    @Override protected List<Row> previewRows() { return List.of(new Row("", "Wheat", "1,234", 0xFFFFFF55), new Row("", "Pests", "-5%", 0xFFFF5555)); }
}
