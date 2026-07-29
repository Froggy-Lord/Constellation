package com.froggylord.constellation.hud;

import com.froggylord.constellation.constellation.ApolloTelemetry;
import net.minecraft.client.Minecraft;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.BooleanSupplier;

public final class LocationHudWidget extends ThemedHudWidget {
    private final BooleanSupplier gate; private HudPosition position; private boolean enabled = true;
    public LocationHudWidget(HudPosition position, BooleanSupplier gate) { this.position = position; this.gate = gate; }
    @Override public String id() { return "apollo-location"; }
    @Override public HudPosition position() { return position; }
    @Override public void setPosition(HudPosition value) { position = value; }
    @Override public boolean isEnabled() { return enabled && gate.getAsBoolean(); }
    @Override public void setEnabled(boolean value) { enabled = value; }
    @Override public boolean visibleNow() { return isEnabled() && Minecraft.getInstance().player != null && !rows().isEmpty(); }
    @Override public String editorLabel() { return "Location"; }
    @Override protected String title() { return "Location"; }
    @Override protected List<Row> rows() {
        var cfg = ApolloTelemetry.config(); var mc = Minecraft.getInstance();
        if (cfg == null || mc.player == null || mc.level == null) return List.of();
        ArrayList<Row> out = new ArrayList<>();
        if (cfg.locationShowCoordinates) {
            String value = cfg.locationDecimalCoordinates
                ? String.format(Locale.ROOT, "%.1f, %.1f, %.1f", mc.player.getX(), mc.player.getY(), mc.player.getZ())
                : mc.player.getBlockX() + ", " + mc.player.getBlockY() + ", " + mc.player.getBlockZ();
            out.add(new Row("", "XYZ", value, cfg.locationCoordinateColor));
        }
        if (cfg.locationShowFacing) out.add(new Row("", "Facing", ApolloTelemetry.facing(mc.player.getYRot()), cfg.locationFacingColor));
        if (cfg.locationShowYaw) out.add(new Row("", "Yaw", String.format(Locale.ROOT, "%.1f", mc.player.getYRot()), cfg.locationFacingColor));
        if (cfg.locationShowPitch) out.add(new Row("", "Pitch", String.format(Locale.ROOT, "%.1f", mc.player.getXRot()), cfg.locationFacingColor));
        if (cfg.locationShowDimension) out.add(new Row("", "Dimension", mc.level.dimension().identifier().getPath(), cfg.locationCoordinateColor));
        if (cfg.locationShowLocalClock) out.add(new Row("", "Clock", ApolloTelemetry.localClock(), cfg.locationClockColor));
        return out;
    }
    @Override protected List<Row> previewRows() { return List.of(new Row("", "XYZ", "125, 72, -340", 0xFF55FFFF), new Row("", "Facing", "North", 0xFFFFFF55), new Row("", "Clock", "14:32", 0xFFFFFFFF)); }
}
