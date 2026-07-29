package com.froggylord.constellation.hud;

import com.froggylord.constellation.constellation.AquilaDeepCavernsGuide;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.BooleanSupplier;

// ported from SkyHanni (LGPL-3.0-or-later): features/mining/DeepCavernsGuide.kt
// ported from SkyHanni (LGPL-3.0-or-later): utils/ParkourHelper.kt
public final class DeepCavernsGuideHudWidget extends ThemedHudWidget {
    private final BooleanSupplier gate;
    private HudPosition pos;
    private boolean enabled = true;

    public DeepCavernsGuideHudWidget(HudPosition pos, BooleanSupplier gate) {
        this.pos = pos;
        this.gate = gate;
    }

    @Override public String id() { return "aquila-deep-caverns-guide"; }
    @Override public HudPosition position() { return pos; }
    @Override public void setPosition(HudPosition value) { pos = value; }
    @Override public boolean isEnabled() { return enabled && gate.getAsBoolean(); }
    @Override public void setEnabled(boolean value) { enabled = value; }
    @Override public boolean visibleNow() { return isEnabled() && AquilaDeepCavernsGuide.state() != null; }
    @Override public String editorLabel() { return "Deep Caverns Guide"; }
    @Override protected String title() { return "Deep Caverns Guide"; }

    @Override
    protected List<Row> rows() {
        var state = AquilaDeepCavernsGuide.state();
        var cfg = AquilaDeepCavernsGuide.config();
        if (state == null || cfg == null) return List.of();
        List<Row> rows = new ArrayList<>();
        int color = cfg.deepCavernsGuideRainbow ? 0xFF55FFFF : 0xFF000000 | (cfg.deepCavernsGuideLineColor & 0xFFFFFF);
        if (cfg.deepCavernsGuideHudShowProgress)
            rows.add(new Row("", "Progress", state.current() + "/" + state.total(), color));
        if (cfg.deepCavernsGuideHudShowNext)
            rows.add(new Row("", "Next", "Point " + state.next(), color));
        if (cfg.deepCavernsGuideHudShowRemaining)
            rows.add(new Row("", "Remaining", Integer.toString(state.remaining()), color));
        if (cfg.deepCavernsGuideHudShowDistance)
            rows.add(new Row("", "Distance", String.format(Locale.ROOT, "%.1fm", state.distance()), color));
        return rows;
    }

    @Override
    protected List<Row> previewRows() {
        return List.of(
            new Row("", "Progress", "24/92", 0xFF55FFFF),
            new Row("", "Next", "Point 25", 0xFF55FFFF),
            new Row("", "Remaining", "68", 0xFF55FFFF),
            new Row("", "Distance", "8.4m", 0xFF55FFFF)
        );
    }
}
