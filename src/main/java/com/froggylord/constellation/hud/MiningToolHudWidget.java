package com.froggylord.constellation.hud;

import com.froggylord.constellation.constellation.AquilaMiningTools;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;

// ported from Devonian (GPL-3.0-only): features/misc/ActionbarParser.kt
public final class MiningToolHudWidget extends ThemedHudWidget {
    private final BooleanSupplier gate;
    private HudPosition pos;
    private boolean enabled = true;

    public MiningToolHudWidget(HudPosition pos, BooleanSupplier gate) {
        this.pos = pos;
        this.gate = gate;
    }

    @Override public String id() { return "aquila-mining-tool"; }
    @Override public HudPosition position() { return pos; }
    @Override public void setPosition(HudPosition position) { pos = position; }
    @Override public boolean isEnabled() { return enabled && gate.getAsBoolean(); }
    @Override public void setEnabled(boolean value) { enabled = value; }
    @Override public boolean visibleNow() { return isEnabled() && AquilaMiningTools.state() != null; }
    @Override public String editorLabel() { return "Mining Tool"; }
    @Override protected String title() { return "Mining Tool"; }

    @Override
    protected List<Row> rows() {
        AquilaMiningTools.Durability state = AquilaMiningTools.state();
        var cfg = AquilaMiningTools.config();
        if (state == null || cfg == null) return List.of();
        List<Row> rows = new ArrayList<>();
        int color = color(state.percent());
        if (cfg.miningToolShowName) rows.add(new Row("", "Tool", state.name(), color));
        if (cfg.miningToolShowCurrent) rows.add(new Row("", state.type() == AquilaMiningTools.Type.DRILL ? "Fuel" : "Durability",
            format(state.current()) + "/" + format(state.maximum()), color));
        if (cfg.miningToolShowPercent) rows.add(new Row("", "Remaining", state.percent() + "%", color));
        if (cfg.miningToolShowBar) rows.add(new Row("", "Bar", bar(state.percent(), cfg.miningToolBarWidth), color));
        return rows;
    }

    @Override
    protected List<Row> previewRows() {
        return List.of(new Row("", "Tool", "Titanium Drill", 0xFFFFFF55),
            new Row("", "Fuel", "18,420/50,000", 0xFFFFFF55),
            new Row("", "Remaining", "37%", 0xFFFFFF55),
            new Row("", "Bar", "####--------", 0xFFFFFF55));
    }

    private static int color(int percent) {
        var cfg = AquilaMiningTools.config();
        if (cfg == null) return 0xFFFFFFFF;
        if (percent <= Math.clamp(cfg.miningToolLowPercent, 1, 100)) return cfg.miningToolDangerColor;
        if (percent <= 35) return cfg.miningToolWarningColor;
        return cfg.miningToolGoodColor;
    }

    private static String bar(int percent, int width) {
        int count = Math.clamp(width, 5, 30);
        int filled = Math.clamp((int) Math.round(percent * count / 100.0), 0, count);
        return "#".repeat(filled) + "-".repeat(count - filled);
    }

    private static String format(int value) { return String.format(java.util.Locale.ROOT, "%,d", value); }
}
