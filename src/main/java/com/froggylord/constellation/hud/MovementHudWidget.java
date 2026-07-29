package com.froggylord.constellation.hud;

import com.froggylord.constellation.constellation.ApolloTelemetry;
import net.minecraft.client.Minecraft;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.BooleanSupplier;

// ported from Devonian (GPL-3.0-only): features/misc/SpeedDisplay.kt
public final class MovementHudWidget extends ThemedHudWidget {
    private final BooleanSupplier gate; private HudPosition position; private boolean enabled = true;
    public MovementHudWidget(HudPosition position, BooleanSupplier gate) { this.position = position; this.gate = gate; }
    @Override public String id() { return "apollo-movement"; }
    @Override public HudPosition position() { return position; }
    @Override public void setPosition(HudPosition value) { position = value; }
    @Override public boolean isEnabled() { return enabled && gate.getAsBoolean(); }
    @Override public void setEnabled(boolean value) { enabled = value; }
    @Override public boolean visibleNow() { return isEnabled() && Minecraft.getInstance().player != null && !rows().isEmpty(); }
    @Override public String editorLabel() { return "Movement"; }
    @Override protected String title() { return "Movement"; }
    @Override protected List<Row> rows() {
        var cfg = ApolloTelemetry.config(); var m = ApolloTelemetry.metrics();
        if (cfg == null || Minecraft.getInstance().player == null) return List.of();
        ArrayList<Row> out = new ArrayList<>();
        if (cfg.movementShowSkyblockSpeed) out.add(new Row("", "Speed stat", Integer.toString(m.skyblockSpeed()), cfg.movementColor));
        if (cfg.movementShowBlocksPerSecond) out.add(new Row("", "Horizontal", one(m.blocksPerSecond()) + " b/s", cfg.movementColor));
        if (cfg.movementShowVerticalSpeed) out.add(new Row("", "Vertical", signed(m.verticalSpeed()) + " b/s", cfg.movementColor));
        return out;
    }
    private static String one(double value) { return String.format(Locale.ROOT, "%.1f", value); }
    private static String signed(double value) { return String.format(Locale.ROOT, "%+.1f", value); }
    @Override protected List<Row> previewRows() { return List.of(new Row("", "Speed stat", "400", 0xFF55FF55), new Row("", "Horizontal", "5.6 b/s", 0xFF55FF55)); }
}
