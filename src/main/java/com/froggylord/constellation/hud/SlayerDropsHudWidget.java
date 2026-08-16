package com.froggylord.constellation.hud;

import com.froggylord.constellation.constellation.SlayerStatistics;

import java.util.List;
import java.util.function.BooleanSupplier;

// ported from Athen (BSD-3-Clause): modules/impl/slayer/SlayerDropsData.kt
public final class SlayerDropsHudWidget extends ThemedHudWidget {
    private final HudPosition initial;
    private final BooleanSupplier enabled;

    public SlayerDropsHudWidget(HudPosition initial, BooleanSupplier enabled) { this.initial = initial; this.enabled = enabled; }
    @Override public String id() { return "perseus-slayer-drops"; }
    @Override public HudPosition position() { return initial; }
    @Override public void setPosition(HudPosition position) {}
    @Override public boolean isEnabled() { return enabled.getAsBoolean(); }
    @Override public void setEnabled(boolean enabled) {}
    @Override public boolean visibleNow() { return isEnabled() && !SlayerStatistics.dropHudLines().isEmpty(); }
    @Override protected String title() { return "Slayer RNG"; }
    @Override protected List<Row> rows() { return SlayerStatistics.dropHudLines().stream().map(line -> new Row("", "", line)).toList(); }
    @Override protected List<Row> previewRows() { return List.of(new Row("", "", "Voidgloom T4: Judgement core"), new Row("", "", "422,100/885,562 XP | 928 bosses"), new Row("", "", "0.15321% | MF 171"), new Row("", "", "Since last: 324")); }
    @Override public String editorLabel() { return "Slayer RNG"; }
}
