package com.froggylord.constellation.hud;

import com.froggylord.constellation.constellation.ApolloTelemetry;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.BooleanSupplier;

// ported from Devonian (GPL-3.0-only): features/misc/FPSDisplay.kt, PingDisplay.kt, TpsDisplay.kt
// ported from NoFrills (GPL-3.0-only): hud/elements/FPS.java, hud/elements/Ping.java
public final class PerformanceHudWidget extends ThemedHudWidget {
    private final BooleanSupplier gate; private HudPosition position; private boolean enabled = true;
    public PerformanceHudWidget(HudPosition position, BooleanSupplier gate) { this.position = position; this.gate = gate; }
    @Override public String id() { return "apollo-performance"; }
    @Override public HudPosition position() { return position; }
    @Override public void setPosition(HudPosition value) { position = value; }
    @Override public boolean isEnabled() { return enabled && gate.getAsBoolean(); }
    @Override public void setEnabled(boolean value) { enabled = value; }
    @Override public boolean visibleNow() {
        var cfg = ApolloTelemetry.config();
        return isEnabled() && cfg != null && ApolloTelemetry.scope(cfg.performanceHypixelOnly) && !rows().isEmpty();
    }
    @Override public String editorLabel() { return "Performance"; }
    @Override protected String title() { return "Performance"; }
    @Override protected List<Row> rows() {
        var cfg = ApolloTelemetry.config(); var m = ApolloTelemetry.metrics();
        if (cfg == null || !ApolloTelemetry.scope(cfg.performanceHypixelOnly)) return List.of();
        ArrayList<Row> out = new ArrayList<>();
        if (cfg.performanceShowFps) out.add(new Row("", "FPS", Integer.toString(m.fps()), fpsColor(m.fps())));
        if (cfg.performanceShowFpsAverage) out.add(new Row("", "Average FPS", rounded(m.fpsAverage()), fpsColor(m.fpsAverage())));
        if (cfg.performanceShowPing) out.add(new Row("", "Ping", m.ping() + " ms", pingColor(m.ping())));
        if (cfg.performanceShowPingAverage) out.add(new Row("", "Average ping", rounded(m.pingAverage()) + " ms", pingColor(m.pingAverage())));
        if (cfg.performanceShowPingMedian) out.add(new Row("", "Median ping", rounded(m.pingMedian()) + " ms", pingColor(m.pingMedian())));
        if (cfg.performanceShowTpsCurrent) out.add(new Row("", "TPS", Integer.toString(m.tpsCurrent()), tpsColor(m.tpsCurrent())));
        if (cfg.performanceShowTpsAverage) out.add(new Row("", "Average TPS", one(m.tpsAverage()), tpsColor(m.tpsAverage())));
        if (cfg.performanceShowTpsMinimum) out.add(new Row("", "Minimum TPS", Integer.toString(m.tpsMinimum()), tpsColor(m.tpsMinimum())));
        if (cfg.performanceShowTpsMaximum) out.add(new Row("", "Maximum TPS", Integer.toString(m.tpsMaximum()), tpsColor(m.tpsMaximum())));
        return out;
    }
    private int fpsColor(double value) { var c = ApolloTelemetry.config(); return value >= c.performanceGoodFps ? c.performanceGoodColor : value >= c.performanceGoodFps * .65 ? c.performanceWarningColor : c.performanceBadColor; }
    private int pingColor(double value) { var c = ApolloTelemetry.config(); return value <= c.performanceGoodPing ? c.performanceGoodColor : value <= c.performanceGoodPing * 1.75 ? c.performanceWarningColor : c.performanceBadColor; }
    private int tpsColor(double value) { var c = ApolloTelemetry.config(); double good = c.performanceGoodTpsHundredths / 100.0; return value >= good ? c.performanceGoodColor : value >= good - 3 ? c.performanceWarningColor : c.performanceBadColor; }
    private static String rounded(double value) { return Long.toString(Math.round(value)); }
    private static String one(double value) { return String.format(Locale.ROOT, "%.1f", value); }
    @Override protected List<Row> previewRows() { return List.of(new Row("", "FPS", "144", 0xFF55FF55), new Row("", "Ping", "42 ms", 0xFF55FF55), new Row("", "Average TPS", "19.8", 0xFF55FF55)); }
}
