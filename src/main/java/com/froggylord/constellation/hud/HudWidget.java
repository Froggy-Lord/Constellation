package com.froggylord.constellation.hud;

import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

public class HudWidget extends ThemedHudWidget {

    private final String id;
    private final String label;
    private final Supplier<String> value;
    private final BooleanSupplier configEnabled;
    private HudPosition position;
    private boolean enabled;
    private java.util.function.Consumer<HudPosition> onMove;

    public HudWidget(String id, String label, Supplier<String> value, HudPosition position, boolean enabled) {
        this(id, label, value, position, () -> enabled);
    }

    public HudWidget(String id, String label, Supplier<String> value, HudPosition position, BooleanSupplier configEnabled) {
        this.id = id;
        this.label = label;
        this.value = value;
        this.position = position;
        this.configEnabled = configEnabled;
        this.enabled = true;
    }

    public HudWidget onMove(java.util.function.Consumer<HudPosition> cb) { this.onMove = cb; return this; }

    private String currentValue() {
        try { return value.get(); } catch (Exception e) { return null; }
    }

    @Override public String id() { return id; }
    @Override public HudPosition position() { return position; }
    @Override public void setPosition(HudPosition pos) {
        this.position = pos;
        if (onMove != null) onMove.accept(pos);
    }
    @Override public boolean isEnabled() { return enabled && configEnabled.getAsBoolean(); }
    @Override public void setEnabled(boolean e) { this.enabled = e; }

    @Override
    public boolean visibleNow() {
        return isEnabled() && currentValue() != null;
    }

    @Override
    public int width() {
        String v = currentValue();
        return super.width();
    }

    @Override
    public int height() {
        return super.height();
    }

    @Override
    protected String title() {
        return "Dungeon";
    }

    @Override
    protected List<Row> rows() {
        String v = currentValue();
        if (v == null) return List.of();
        return List.of(new Row(">", label, v));
    }

    @Override
    protected List<Row> previewRows() {
        String v = currentValue();
        return List.of(new Row(">", label, v == null ? "Preview" : v));
    }

    @Override
    public String editorLabel() { return label; }
}
