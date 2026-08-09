package com.froggylord.constellation.hud;

import com.froggylord.constellation.constellation.HerculesCharmedVisitors;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;

// ported from SkyHanni (LGPL-3.0-or-later): features/garden/visitor/GardenCharmedVisitors.kt
public final class CharmedVisitorsHudWidget extends ThemedHudWidget {
    private final BooleanSupplier configEnabled;
    private HudPosition position;
    private boolean enabled = true;

    public CharmedVisitorsHudWidget(HudPosition position, BooleanSupplier configEnabled) {
        this.position = position;
        this.configEnabled = configEnabled;
    }

    @Override public String id() { return "garden-charmed-visitors"; }
    @Override public HudPosition position() { return position; }
    @Override public void setPosition(HudPosition position) { this.position = position; }
    @Override public boolean isEnabled() { return enabled && configEnabled.getAsBoolean(); }
    @Override public void setEnabled(boolean enabled) { this.enabled = enabled; }
    @Override public boolean visibleNow() { return isEnabled() && !HerculesCharmedVisitors.rows().isEmpty(); }
    @Override public String editorLabel() { return "Charmed Visitors"; }
    @Override protected String title() {
        return HerculesCharmedVisitors.showCount() && HerculesCharmedVisitors.count() > 0
            ? "Charmed Visitors (" + HerculesCharmedVisitors.count() + ")" : "Charmed Visitors";
    }
    @Override protected List<Row> rows() {
        List<Row> out = new ArrayList<>();
        for (String name : HerculesCharmedVisitors.rows()) out.add(new Row("", name, "", 0xFFFF55FF));
        return out;
    }
    @Override protected List<Row> previewRows() {
        return List.of(new Row("", "Librarian", ""), new Row("", "Spaceman", ""), new Row("", "Leo", ""));
    }
}
