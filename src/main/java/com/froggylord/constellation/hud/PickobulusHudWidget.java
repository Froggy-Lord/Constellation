package com.froggylord.constellation.hud;

import com.froggylord.constellation.constellation.AquilaPickobulus;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;

// ported from Skyblocker (LGPL-3.0-or-later): skyblock/dwarven/PickobulusHudWidget.java
public final class PickobulusHudWidget extends ThemedHudWidget {
    private final BooleanSupplier gate;
    private HudPosition pos;
    private boolean enabled = true;

    public PickobulusHudWidget(HudPosition pos, BooleanSupplier gate) {
        this.pos = pos;
        this.gate = gate;
    }

    @Override public String id() { return "aquila-pickobulus"; }
    @Override public HudPosition position() { return pos; }
    @Override public void setPosition(HudPosition position) { pos = position; }
    @Override public boolean isEnabled() { return enabled && gate.getAsBoolean(); }
    @Override public void setEnabled(boolean value) { enabled = value; }
    @Override public boolean visibleNow() { return isEnabled() && AquilaPickobulus.state().visible(); }
    @Override public String editorLabel() { return "Pickobulus"; }
    @Override protected String title() { return "Pickobulus"; }

    @Override
    protected List<Row> rows() {
        var state = AquilaPickobulus.state();
        var cfg = AquilaPickobulus.config();
        if (!state.visible() || cfg == null) return List.of();
        if (!state.error().isEmpty())
            return cfg.pickobulusShowErrors ? List.of(new Row("", "Status", state.error(), 0xFFFF5555)) : List.of();
        List<Row> rows = new ArrayList<>();
        if (cfg.pickobulusShowTotalBlocks) rows.add(new Row("", "Total Blocks", Integer.toString(state.totalBlocks()), cfg.pickobulusColor));
        for (AquilaPickobulus.Drop drop : AquilaPickobulus.Drop.values()) {
            int amount = state.drops().getOrDefault(drop, 0);
            if (amount <= 0) continue;
            if (drop == AquilaPickobulus.Drop.MINESHAFT_PITY && !cfg.pickobulusShowMineshaftPity) continue;
            if (drop == AquilaPickobulus.Drop.MITHRIL_POWDER && !cfg.pickobulusShowPowder) continue;
            if (drop != AquilaPickobulus.Drop.MINESHAFT_PITY && drop != AquilaPickobulus.Drop.MITHRIL_POWDER && !cfg.pickobulusShowDrops) continue;
            rows.add(new Row("", drop.display(), Integer.toString(amount), cfg.pickobulusColor));
        }
        return rows;
    }

    @Override
    protected List<Row> previewRows() {
        return List.of(
            new Row("", "Total Blocks", "27", 0xFF55FFFF),
            new Row("", "Mineshaft Pity", "42", 0xFF55FFFF),
            new Row("", "Mithril Powder", "15", 0xFF55FFFF)
        );
    }
}
