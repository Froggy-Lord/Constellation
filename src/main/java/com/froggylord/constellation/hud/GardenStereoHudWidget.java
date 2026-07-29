package com.froggylord.constellation.hud;

import com.froggylord.constellation.constellation.HerculesStereoHarmony;
import java.util.List;
import java.util.function.BooleanSupplier;

// ported from SkyHanni (LGPL-3.0-or-later): features/garden/pests/stereo/StereoHarmonyDisplay.kt
public final class GardenStereoHudWidget extends ThemedHudWidget {
    private final BooleanSupplier configEnabled;
    private HudPosition position;
    private boolean enabled = true;

    public GardenStereoHudWidget(HudPosition position, BooleanSupplier configEnabled) {
        this.position = position;
        this.configEnabled = configEnabled;
    }

    @Override public String id() { return "garden-stereo-harmony"; }
    @Override public HudPosition position() { return position; }
    @Override public void setPosition(HudPosition position) { this.position = position; }
    @Override public boolean isEnabled() { return enabled && configEnabled.getAsBoolean(); }
    @Override public void setEnabled(boolean enabled) { this.enabled = enabled; }
    @Override public boolean visibleNow() { return isEnabled() && HerculesStereoHarmony.hudVisible(); }
    @Override public String editorLabel() { return "Stereo Harmony"; }
    @Override protected String title() { return "Stereo Harmony"; }
    @Override protected List<Row> rows() { return HerculesStereoHarmony.hudRows().stream().map(row -> new Row("", row.label(), row.value())).toList(); }
    @Override protected List<Row> previewRows() { return List.of(new Row("", "Playing", "Wings of Harmony"), new Row("", "Pest", "Moth"), new Row("", "Crop", "Cocoa Beans")); }
}
