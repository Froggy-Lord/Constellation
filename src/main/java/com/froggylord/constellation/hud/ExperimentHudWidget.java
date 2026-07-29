package com.froggylord.constellation.hud;

import com.froggylord.constellation.constellation.AurigaExperiments;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;

// ported from Skyblocker (LGPL-3.0-or-later): skyblock/experiment/ExperimentSolver.java
public final class ExperimentHudWidget extends ThemedHudWidget {
    private final BooleanSupplier gate; private HudPosition position; private boolean enabled = true;
    public ExperimentHudWidget(HudPosition position, BooleanSupplier gate) { this.position = position; this.gate = gate; }
    @Override public String id() { return "auriga-experiment"; }
    @Override public HudPosition position() { return position; }
    @Override public void setPosition(HudPosition value) { position = value; }
    @Override public boolean isEnabled() { return enabled && gate.getAsBoolean(); }
    @Override public void setEnabled(boolean value) { enabled = value; }
    @Override public boolean visibleNow() { return isEnabled() && AurigaExperiments.visible(); }
    @Override public String editorLabel() { return "Experiment Solver"; }
    @Override protected String title() { return "Experiment"; }
    @Override protected List<Row> rows() {
        if (!AurigaExperiments.visible()) return List.of();
        var status = AurigaExperiments.status();
        var cfg = AurigaExperiments.config();
        ArrayList<Row> out = new ArrayList<>();
        out.add(new Row("", "Type", name(status.type()), cfg.experimentNextColor));
        out.add(new Row("", "Phase", name(status.phase()), phaseColor(status.phase(), cfg)));
        if (status.type() == AurigaExperiments.Type.SUPERPAIRS)
            out.add(new Row("", "Remembered", Integer.toString(status.remembered()), cfg.superpairsKnownPairColor));
        else {
            out.add(new Row("", "Step", Math.min(status.total(), status.current() + 1) + " / " + status.total(), cfg.experimentNextColor));
            out.add(new Row("", "Remaining", Integer.toString(Math.max(0, status.total() - status.current())), cfg.experimentSecondColor));
        }
        if (cfg.experimentControlBypass) out.add(new Row("", "Bypass", "Hold Control", 0xFFAAAAAA));
        return out;
    }
    private static int phaseColor(AurigaExperiments.Phase phase, com.froggylord.constellation.config.AurigaConfig cfg) {
        return switch (phase) {
            case SHOW -> cfg.experimentNextColor;
            case REMEMBER, WAIT -> cfg.experimentSecondColor;
            case END -> cfg.superpairsKnownPairColor;
        };
    }
    private static String name(Enum<?> value) {
        String raw = value.name().toLowerCase(java.util.Locale.ROOT);
        return Character.toUpperCase(raw.charAt(0)) + raw.substring(1);
    }
    @Override protected List<Row> previewRows() { return List.of(new Row("", "Type", "Ultrasequencer", 0xFF55FF55), new Row("", "Phase", "Show", 0xFF55FF55), new Row("", "Step", "3 / 12", 0xFFFFFF55)); }
}
