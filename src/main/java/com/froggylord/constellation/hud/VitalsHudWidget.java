package com.froggylord.constellation.hud;

import com.froggylord.constellation.constellation.ApolloTelemetry;
import com.froggylord.constellation.core.ActionBar;
import net.minecraft.client.Minecraft;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.BooleanSupplier;

public final class VitalsHudWidget extends ThemedHudWidget {
    private final BooleanSupplier gate; private HudPosition position; private boolean enabled = true;
    public VitalsHudWidget(HudPosition position, BooleanSupplier gate) { this.position = position; this.gate = gate; }
    @Override public String id() { return "apollo-vitals"; }
    @Override public HudPosition position() { return position; }
    @Override public void setPosition(HudPosition value) { position = value; }
    @Override public boolean isEnabled() { return enabled && gate.getAsBoolean(); }
    @Override public void setEnabled(boolean value) { enabled = value; }
    @Override public boolean visibleNow() { var c = ApolloTelemetry.config(); return isEnabled() && c != null && ApolloTelemetry.scope(c.vitalsHypixelOnly) && (ActionBar.hasData() || Minecraft.getInstance().player != null); }
    @Override public String editorLabel() { return "Vitals"; }
    @Override protected String title() { return "Vitals"; }
    @Override protected List<Row> rows() {
        var cfg = ApolloTelemetry.config(); var mc = Minecraft.getInstance();
        if (cfg == null || !ApolloTelemetry.scope(cfg.vitalsHypixelOnly)) return List.of();
        ArrayList<Row> out = new ArrayList<>();
        int health = ActionBar.hasData() ? ActionBar.health() : mc.player == null ? 0 : Math.round(mc.player.getHealth());
        int maxHealth = ActionBar.hasData() ? ActionBar.maxHealth() : mc.player == null ? 0 : Math.round(mc.player.getMaxHealth());
        if (cfg.vitalsShowHealth) out.add(new Row("", "Health", cfg.vitalsShowHealthPercent ? percent(health, maxHealth) : health + " / " + maxHealth, cfg.vitalsHealthColor));
        if (ActionBar.hasData() && cfg.vitalsShowMana) out.add(new Row("", "Mana", cfg.vitalsShowManaPercent ? percent(ActionBar.mana(), ActionBar.maxMana()) : ActionBar.mana() + " / " + ActionBar.maxMana(), cfg.vitalsManaColor));
        if (ActionBar.hasData() && cfg.vitalsShowOverflowMana && ActionBar.overflowMana() > 0) out.add(new Row("", "Overflow", Integer.toString(ActionBar.overflowMana()), cfg.vitalsManaColor));
        if (ActionBar.hasData() && cfg.vitalsShowDefense) out.add(new Row("", "Defense", Integer.toString(ActionBar.defense()), cfg.vitalsDefenseColor));
        if (ActionBar.hasData() && cfg.vitalsShowEffectiveHealth) out.add(new Row("", "Effective health", Long.toString(Math.round(ActionBar.effectiveHealth())), cfg.vitalsDefenseColor));
        return out;
    }
    private static String percent(int value, int max) { return max <= 0 ? "0%" : String.format(Locale.ROOT, "%.1f%%", value * 100.0 / max); }
    @Override protected List<Row> previewRows() { return List.of(new Row("", "Health", "4,805 / 4,805", 0xFFFF5555), new Row("", "Mana", "781 / 781", 0xFF55FFFF), new Row("", "Defense", "915", 0xFF55FF55)); }
}
