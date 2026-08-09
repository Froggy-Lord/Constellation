package com.froggylord.constellation.hud;

import com.froggylord.constellation.ConstellationClient;
import com.froggylord.constellation.constellation.OrionBlessings;
import com.froggylord.constellation.render.ConstellationTheme;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;

/**
 * Themed blessings panel: parses Power/Time/Stone/Life/Wisdom/Healing levels from chat
 * (via {@link OrionBlessings}) and shows each with its own colour. Per-type colours are
 * ported from Devonian's BlessingsDisplay.
 */
public class BlessingsHudWidget extends ThemedHudWidget {

    private final String id;
    private final BooleanSupplier configEnabled;
    private HudPosition position;
    private boolean enabled;

    public BlessingsHudWidget(String id, HudPosition position, BooleanSupplier configEnabled) {
        this.id = id;
        this.position = position;
        this.configEnabled = configEnabled;
        this.enabled = true;
    }

    @Override public String id() { return id; }
    @Override public HudPosition position() { return position; }
    @Override public void setPosition(HudPosition pos) { this.position = pos; }
    @Override public boolean isEnabled() { return enabled && configEnabled.getAsBoolean(); }
    @Override public void setEnabled(boolean e) { this.enabled = e; }
    @Override public String editorLabel() { return "Blessings"; }

    @Override
    public boolean visibleNow() {
        return isEnabled() && ConstellationClient.loc().inDungeons() && !OrionBlessings.levels().isEmpty();
    }

    @Override protected String title() { return "Blessings"; }

    @Override
    protected List<Row> rows() {
        List<Row> rows = new ArrayList<>(6);
        for (Map.Entry<String, Integer> e : OrionBlessings.levels().entrySet()) {
            rows.add(new Row(">", e.getKey(), String.valueOf(e.getValue()), colorFor(e.getKey())));
        }
        return rows;
    }

    @Override
    protected List<Row> previewRows() {
        return List.of(
            new Row(">", "Power", "29", colorFor("Power")),
            new Row(">", "Time", "5", colorFor("Time")),
            new Row(">", "Wisdom", "11", colorFor("Wisdom")),
            new Row(">", "Stone", "10", colorFor("Stone")),
            new Row(">", "Life", "36", colorFor("Life"))
        );
    }

    // devonian palette: Power=red, Time=gold, Wisdom=aqua, Life=green, Stone=grey, Healing=green
    private static int colorFor(String type) {
        return switch (type) {
            case "Power" -> ConstellationTheme.RED;
            case "Time" -> ConstellationTheme.ACCENT;
            case "Wisdom" -> ConstellationTheme.AQUA;
            case "Life", "Healing" -> ConstellationTheme.GREEN;
            case "Stone" -> ConstellationTheme.TEXT_DIM;
            default -> ConstellationTheme.TEXT;
        };
    }
}
