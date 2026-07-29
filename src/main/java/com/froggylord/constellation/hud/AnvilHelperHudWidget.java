package com.froggylord.constellation.hud;

import com.froggylord.constellation.constellation.AurigaAnvilHelper;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;

// ported from Skyblocker (LGPL-3.0-or-later): skyblock/item/AnvilHelper.java
// ported from NoFrills (GPL-3.0-only): features/solvers/AnvilHelper.java
public final class AnvilHelperHudWidget extends ThemedHudWidget {
    private final BooleanSupplier gate;
    private HudPosition position;
    private boolean enabled = true;

    public AnvilHelperHudWidget(HudPosition position, BooleanSupplier gate) {
        this.position = position;
        this.gate = gate;
    }

    @Override public String id() { return "auriga-anvil-helper"; }
    @Override public HudPosition position() { return position; }
    @Override public void setPosition(HudPosition value) { position = value; }
    @Override public boolean isEnabled() { return enabled && gate.getAsBoolean(); }
    @Override public void setEnabled(boolean value) { enabled = value; }
    @Override public boolean visibleNow() { return isEnabled() && AurigaAnvilHelper.visible(); }
    @Override public String editorLabel() { return "Anvil Helper"; }
    @Override protected String title() { return "Anvil Helper"; }

    @Override
    protected List<Row> rows() {
        var state = AurigaAnvilHelper.state();
        var cfg = AurigaAnvilHelper.config();
        ArrayList<Row> rows = new ArrayList<>();
        rows.add(new Row("", "State", label(state.mode()), color(state.mode(), cfg)));
        if (!state.left().equals("Empty")) rows.add(new Row("", "Left", state.left(), cfg.anvilInputColor));
        if (!state.right().equals("Empty")) rows.add(new Row("", "Right", state.right(), cfg.anvilInputColor));
        if (state.mode() == AurigaAnvilHelper.Mode.SINGLE_BOOK)
            rows.add(new Row("", "Matches", Integer.toString(state.matches()), cfg.anvilMatchColor));
        if (state.mode() == AurigaAnvilHelper.Mode.MISMATCH && cfg.anvilControlBypass)
            rows.add(new Row("", "Bypass", "Hold Control", 0xFFAAAAAA));
        return rows;
    }

    private static int color(AurigaAnvilHelper.Mode mode, com.froggylord.constellation.config.AurigaConfig cfg) {
        return switch (mode) {
            case MISMATCH -> cfg.anvilMismatchColor;
            case MATCH -> cfg.anvilResultColor;
            case SINGLE_BOOK -> cfg.anvilMatchColor;
            default -> cfg.anvilInputColor;
        };
    }

    private static String label(AurigaAnvilHelper.Mode mode) {
        return switch (mode) {
            case EMPTY -> "Empty";
            case SINGLE_BOOK -> "Find matching book";
            case MATCH -> "Matching books";
            case MISMATCH -> "Mismatched books";
            case OTHER -> "Item combination";
        };
    }

    @Override
    protected List<Row> previewRows() {
        return List.of(
            new Row("", "State", "Matching books", 0xFF55FF55),
            new Row("", "Left", "Ultimate Wise IV", 0xFF55FFFF),
            new Row("", "Right", "Ultimate Wise IV", 0xFF55FFFF)
        );
    }
}
