package com.froggylord.constellation.hud;

import com.froggylord.constellation.constellation.SlayerStatistics;

import java.util.List;
import java.util.function.BooleanSupplier;

// ported from Athen (BSD-3-Clause): modules/impl/slayer/SlayerStats.kt
public final class SlayerStatsHudWidget extends ThemedHudWidget {
    private final HudPosition initial;
    private final BooleanSupplier enabled;

    public SlayerStatsHudWidget(HudPosition initial, BooleanSupplier enabled) { this.initial = initial; this.enabled = enabled; }
    @Override public String id() { return "perseus-slayer-stats"; }
    @Override public HudPosition position() { return initial; }
    @Override public void setPosition(HudPosition position) {}
    @Override public boolean isEnabled() { return enabled.getAsBoolean(); }
    @Override public void setEnabled(boolean enabled) {}
    @Override public boolean visibleNow() { return isEnabled() && !SlayerStatistics.hudLines().isEmpty(); }
    @Override protected String title() { return "Slayer Stats"; }
    @Override protected List<Row> rows() { return SlayerStatistics.hudLines().stream().map(line -> new Row("", "", line)).toList(); }
    @Override protected List<Row> previewRows() { return List.of(new Row("", "", "Bosses: 67"), new Row("", "", "Bosses/hr: 104"), new Row("", "", "XP/hr: 60,000"), new Row("", "", "Kill: 23.4s"), new Row("", "", "Session: 21m 24s")); }
    @Override public String editorLabel() { return "Slayer Stats"; }
}
