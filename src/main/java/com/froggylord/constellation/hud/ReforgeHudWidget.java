package com.froggylord.constellation.hud;

import com.froggylord.constellation.constellation.AurigaReforgeHelper;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.BooleanSupplier;

// ported from SkyblockAddons (LGPL-3.0-only): mixin/hooks/AbstractContainerScreenHook.java, utils/ItemUtils.java, utils/Utils.java
public final class ReforgeHudWidget extends ThemedHudWidget {
    private final BooleanSupplier gate;
    private HudPosition position;
    private boolean enabled = true;
    public ReforgeHudWidget(HudPosition position, BooleanSupplier gate) { this.position = position; this.gate = gate; }
    @Override public String id() { return "auriga-reforge"; }
    @Override public HudPosition position() { return position; }
    @Override public void setPosition(HudPosition value) { position = value; }
    @Override public boolean isEnabled() { return enabled && gate.getAsBoolean(); }
    @Override public void setEnabled(boolean value) { enabled = value; }
    @Override public boolean visibleNow() { return isEnabled() && AurigaReforgeHelper.visible(); }
    @Override public String editorLabel() { return "Reforge Helper"; }
    @Override protected String title() { return "Reforge Helper"; }
    @Override protected List<Row> rows() {
        var state = AurigaReforgeHelper.state();
        var cfg = AurigaReforgeHelper.config();
        ArrayList<Row> rows = new ArrayList<>();
        rows.add(new Row("", "Current", state.current(), state.matched() ? cfg.reforgeMatchColor : cfg.reforgeMissColor));
        if (cfg.reforgeShowItem && !state.item().equals("Empty")) rows.add(new Row("", "Item", state.item(), cfg.reforgeCurrentColor));
        if (cfg.reforgeShowCost && state.cost() > 0) rows.add(new Row("", "Cost", coins(state.cost()), 0xFFFFAA00));
        if (cfg.reforgeShowAttempts) rows.add(new Row("", "Attempts", Integer.toString(state.attempts()), 0xFF55FFFF));
        if (cfg.reforgeShowSpent) rows.add(new Row("", "Spent", coins(state.spent()), 0xFFFFAA00));
        if (cfg.reforgeShowLastResult && !state.lastResult().isBlank())
            rows.add(new Row("", "Last", state.lastResult(), cfg.reforgeCurrentColor));
        if (state.matched() && cfg.reforgeControlBypass) rows.add(new Row("", "Bypass", "Hold Control", 0xFFAAAAAA));
        return rows;
    }
    private static String coins(long value) {
        if (value >= 1_000_000_000) return String.format(Locale.ROOT, "%.2fB", value / 1_000_000_000.0);
        if (value >= 1_000_000) return String.format(Locale.ROOT, "%.2fM", value / 1_000_000.0);
        if (value >= 1_000) return String.format(Locale.ROOT, "%.1fk", value / 1_000.0);
        return Long.toString(value);
    }
    @Override protected List<Row> previewRows() {
        return List.of(new Row("", "Current", "Ancient", 0xFF55FF55),
            new Row("", "Cost", "50.0k", 0xFFFFAA00), new Row("", "Attempts", "12", 0xFF55FFFF));
    }
}
