package com.froggylord.constellation.hud;

import com.froggylord.constellation.constellation.ApolloTelemetry;
import net.minecraft.client.Minecraft;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;

public final class EffectsHudWidget extends ThemedHudWidget {
    private final BooleanSupplier gate; private HudPosition position; private boolean enabled = true;
    public EffectsHudWidget(HudPosition position, BooleanSupplier gate) { this.position = position; this.gate = gate; }
    @Override public String id() { return "apollo-effects"; }
    @Override public HudPosition position() { return position; }
    @Override public void setPosition(HudPosition value) { position = value; }
    @Override public boolean isEnabled() { return enabled && gate.getAsBoolean(); }
    @Override public void setEnabled(boolean value) { enabled = value; }
    @Override public boolean visibleNow() { return isEnabled() && Minecraft.getInstance().player != null && !rows().isEmpty(); }
    @Override public String editorLabel() { return "Active Effects"; }
    @Override protected String title() { return "Active Effects"; }
    @Override protected List<Row> rows() {
        var cfg = ApolloTelemetry.config();
        if (cfg == null) return List.of();
        ArrayList<Row> out = new ArrayList<>();
        for (var effect : ApolloTelemetry.effects()) {
            int color = effect.infinite() ? cfg.effectsActiveColor
                : effect.seconds() <= 0 ? cfg.effectsExpiredColor
                : effect.seconds() <= cfg.effectsWarningSeconds ? cfg.effectsWarningColor : cfg.effectsActiveColor;
            out.add(new Row("", effect.name(), cfg.effectsShowDuration ? effect.duration() : "", color));
        }
        return out;
    }
    @Override protected List<Row> previewRows() { return List.of(new Row("", "Speed II", "1:42", 0xFF55FF55), new Row("", "Night Vision", "0:18", 0xFFFFFF55)); }
}
