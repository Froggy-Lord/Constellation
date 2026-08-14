package com.froggylord.constellation.hud;

import com.froggylord.constellation.constellation.KuudraSplits;

import java.util.List;
import java.util.function.BooleanSupplier;

public final class KuudraSplitsHudWidget extends ThemedHudWidget {
    private final HudPosition initial;
    private final BooleanSupplier enabled;

    public KuudraSplitsHudWidget(HudPosition initial, BooleanSupplier enabled) {
        this.initial = initial;
        this.enabled = enabled;
    }

    @Override public String id() { return "draco-kuudra-splits"; }
    @Override public HudPosition position() { return initial; }
    @Override public void setPosition(HudPosition position) {}
    @Override public boolean isEnabled() { return enabled.getAsBoolean(); }
    @Override public void setEnabled(boolean enabled) {}
    @Override public boolean visibleNow() { return isEnabled() && !KuudraSplits.hudLines().isEmpty(); }
    @Override protected String title() { return "Kuudra Splits"; }
    @Override protected List<Row> rows() { return KuudraSplits.hudLines().stream().map(line -> new Row("", "", line)).toList(); }
    @Override protected List<Row> previewRows() {
        List<String> live = KuudraSplits.hudLines();
        if (!live.isEmpty()) return live.stream().map(line -> new Row("", "", line)).toList();
        return List.of(new Row("", "", "Supply: 34.0s [33.8s]"), new Row("", "", "Overall: 1m 40.0s"));
    }
    @Override public String editorLabel() { return "Kuudra Splits"; }
}
