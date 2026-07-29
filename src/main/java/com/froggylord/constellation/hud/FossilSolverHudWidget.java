package com.froggylord.constellation.hud;

import com.froggylord.constellation.constellation.AquilaFossils;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;

// ported from SkyHanni (LGPL-2.1): features/mining/fossilexcavator/solver/FossilSolverDisplay.kt
public final class FossilSolverHudWidget extends ThemedHudWidget {
    private final BooleanSupplier gate;
    private HudPosition pos;
    private boolean enabled = true;

    public FossilSolverHudWidget(HudPosition pos, BooleanSupplier gate) { this.pos = pos; this.gate = gate; }
    @Override public String id() { return "aquila-fossil-solver"; }
    @Override public HudPosition position() { return pos; }
    @Override public void setPosition(HudPosition position) { pos = position; }
    @Override public boolean isEnabled() { return enabled && gate.getAsBoolean(); }
    @Override public void setEnabled(boolean value) { enabled = value; }
    @Override public boolean visibleNow() { return isEnabled() && AquilaFossils.state().visible(); }
    @Override public String editorLabel() { return "Fossil Solver"; }
    @Override protected String title() { return "Fossil Excavator"; }

    @Override
    protected List<Row> rows() {
        var state = AquilaFossils.state();
        var cfg = AquilaFossils.config();
        if (!state.visible() || cfg == null) return List.of();
        List<Row> rows = new ArrayList<>();
        int normal = state.patterns() == 0 ? cfg.fossilImpossibleColor : cfg.fossilBestColor;
        rows.add(new Row("", "Status", state.status(), normal));
        if (cfg.fossilShowCharges) rows.add(new Row("", "Charges", Integer.toString(state.charges()), state.charges() >= state.minimumTiles() ? 0xFF55FF55 : 0xFFFF5555));
        if (cfg.fossilShowPatterns) rows.add(new Row("", "Patterns", Integer.toString(state.patterns()), normal));
        if (cfg.fossilShowMinimumTiles) rows.add(new Row("", "Minimum tiles", Integer.toString(state.minimumTiles()), normal));
        if (state.nextSlot() >= 0) rows.add(new Row("", "Next chance", Math.round(state.nextChance() * 100) + "%", normal));
        if (!state.fossil().isEmpty()) rows.add(new Row("", "Fossil", state.fossil(), normal));
        if (cfg.fossilShowPossibleTypes && state.types().size() > 1)
            rows.add(new Row("", "Possible types", String.join(", ", state.types()), 0xFFFFFF55));
        return rows;
    }

    @Override
    protected List<Row> previewRows() {
        return List.of(new Row("", "Status", "Solving", 0xFF55FF55),
            new Row("", "Charges", "18", 0xFF55FF55),
            new Row("", "Patterns", "196", 0xFF55FF55),
            new Row("", "Next chance", "41%", 0xFF55FF55));
    }
}
