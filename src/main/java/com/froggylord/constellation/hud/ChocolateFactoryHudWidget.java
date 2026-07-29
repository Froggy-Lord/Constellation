package com.froggylord.constellation.hud;

import com.froggylord.constellation.constellation.AurigaChocolateFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.BooleanSupplier;

// ported from Skyblocker (LGPL-3.0-or-later): skyblock/chocolatefactory/ChocolateFactorySolver.java, TimeTowerReminder.java
public final class ChocolateFactoryHudWidget extends ThemedHudWidget {
    private final BooleanSupplier gate;
    private HudPosition position;
    private boolean enabled = true;

    public ChocolateFactoryHudWidget(HudPosition position, BooleanSupplier gate) {
        this.position = position;
        this.gate = gate;
    }

    @Override public String id() { return "auriga-chocolate-factory"; }
    @Override public HudPosition position() { return position; }
    @Override public void setPosition(HudPosition value) { position = value; }
    @Override public boolean isEnabled() { return enabled && gate.getAsBoolean(); }
    @Override public void setEnabled(boolean value) { enabled = value; }
    @Override public boolean visibleNow() { return isEnabled() && AurigaChocolateFactory.visible(); }
    @Override public String editorLabel() { return "Chocolate Factory"; }
    @Override protected String title() { return "Chocolate Factory"; }

    @Override
    protected List<Row> rows() {
        var state = AurigaChocolateFactory.state();
        var cfg = AurigaChocolateFactory.config();
        ArrayList<Row> rows = new ArrayList<>();
        if (state.chocolate() >= 0) rows.add(new Row("", "Chocolate", compact(state.chocolate()), 0xFFFFAA00));
        if (state.cps() >= 0) rows.add(new Row("", "Production", compact(state.cps()) + "/s", 0xFFFFAA00));
        AurigaChocolateFactory.Upgrade best = state.upgrades().isEmpty() ? null : state.upgrades().getFirst();
        if (best != null) {
            String name = best.name();
            if (!cfg.chocolateFactoryShowLevels && best.level() >= 0)
                name = name.replaceFirst("\\s+(?:\\[\\d+]|[IVXLC]+)$", "");
            rows.add(new Row("", "Best", name, best.affordable() ? cfg.chocolateFactoryBestColor : cfg.chocolateFactoryUnaffordableColor));
            rows.add(new Row("", "Affordable", best.affordable() ? "Now" :
                AurigaChocolateFactory.formatTime((best.cost() - state.chocolate()) / state.cps()), cfg.chocolateFactoryAffordableColor));
        }
        if (cfg.chocolateFactoryPrestige && !state.maxPrestige()) {
            String value = state.canPrestige() ? "Ready" :
                state.prestigeRemaining() >= 0 && state.cps() > 0
                    ? AurigaChocolateFactory.formatTime(state.prestigeRemaining() / state.cps()) : "Unknown";
            rows.add(new Row("", "Prestige", value, state.canPrestige() ? cfg.chocolateFactoryPrestigeColor : 0xFFAAAAAA));
        }
        long tower = AurigaChocolateFactory.towerExpiry() - System.currentTimeMillis();
        if (cfg.chocolateFactoryTimeTower)
            rows.add(new Row("", "Time Tower", tower > 0 ? AurigaChocolateFactory.formatTime(tower / 1000.0) : "Inactive", 0xFF55FFFF));
        if (cfg.chocolateFactoryShowHitman && state.availableEggs() >= 0) {
            rows.add(new Row("", "Eggs", Integer.toString(state.availableEggs()), 0xFFFF55FF));
            if (state.hitmanSlots() >= 0)
                rows.add(new Row("", "Hitman", state.hitmanSlots() + " / " + state.hitmanMax(), 0xFFFF55FF));
        }
        return rows;
    }

    @Override
    protected List<Row> previewRows() {
        return List.of(
            new Row("", "Chocolate", "12.5M", 0xFFFFAA00),
            new Row("", "Production", "84.2k/s", 0xFFFFAA00),
            new Row("", "Best", "Rabbit Bro", 0xFF55FF55),
            new Row("", "Time Tower", "42m 10s", 0xFF55FFFF)
        );
    }

    private static String compact(double value) {
        if (!Double.isFinite(value) || value < 0) return "Unknown";
        if (value >= 1_000_000_000) return String.format(Locale.ROOT, "%.2fB", value / 1_000_000_000);
        if (value >= 1_000_000) return String.format(Locale.ROOT, "%.2fM", value / 1_000_000);
        if (value >= 1_000) return String.format(Locale.ROOT, "%.1fk", value / 1_000);
        return String.format(Locale.ROOT, "%.1f", value);
    }
}
