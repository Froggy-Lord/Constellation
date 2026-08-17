package com.froggylord.constellation.hud;

import com.froggylord.constellation.ConstellationClient;
import com.froggylord.constellation.data.DungeonScore;
import com.froggylord.constellation.data.DungeonSplits;
import com.froggylord.constellation.render.ConstellationTheme;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;

/**
 * Run panel (skyhanni/devonian-style): deaths counter plus the section split timers
 * (blood open -> boss entry -> clear), each compared against the per-floor personal best
 * from {@link DungeonSplits}. Green when ahead of PB, red when behind.
 */
public class SplitsHudWidget extends ThemedHudWidget {

    private final String id;
    private final BooleanSupplier configEnabled;
    private HudPosition position;
    private boolean enabled;

    public SplitsHudWidget(String id, HudPosition position, BooleanSupplier configEnabled) {
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
    @Override public String editorLabel() { return "Run Splits"; }

    @Override
    public boolean visibleNow() {
        return isEnabled() && ConstellationClient.loc().inDungeons() && DungeonScore.isActive();
    }

    @Override
    protected String title() {
        String floor = DungeonScore.floor();
        return floor == null || floor.isEmpty() ? "Run" : "Run — " + floor;
    }

    @Override
    protected List<Row> rows() {
        List<Row> rows = new ArrayList<>(4);
        int deaths = DungeonScore.deaths();
        rows.add(new Row("", "Deaths", String.valueOf(deaths),
            deaths > 0 ? ConstellationTheme.RED : ConstellationTheme.TEXT));

        long[] pb = DungeonSplits.pb(DungeonScore.floor());
        rows.add(splitRow("Blood", DungeonScore.bloodSplitMs(), pb, DungeonSplits.BLOOD));
        rows.add(splitRow("Boss", DungeonScore.bossSplitMs(), pb, DungeonSplits.BOSS));
        rows.add(splitRow("Clear", DungeonScore.clearSplitMs(), pb, DungeonSplits.CLEAR));
        return rows;
    }

    private Row splitRow(String label, long cur, long[] pb, int idx) {
        long best = pb != null ? pb[idx] : 0;
        if (cur <= 0) {
            // not reached yet — show the PB (if any) as a dim target
            String v = best > 0 ? "PB " + fmt(best) : "—";
            return new Row("", label, v, ConstellationTheme.TEXT_MUTED);
        }
        String value = best > 0 ? fmt(cur) + " / " + fmt(best) : fmt(cur);
        int color = best <= 0 ? ConstellationTheme.TEXT
            : cur <= best ? ConstellationTheme.GREEN : ConstellationTheme.RED;
        return new Row("", label, value, color);
    }

    @Override
    protected List<Row> previewRows() {
        return List.of(
            new Row("", "Deaths", "0", ConstellationTheme.TEXT),
            new Row("", "Blood", "1:12 / 1:20", ConstellationTheme.GREEN),
            new Row("", "Boss", "4:05 / 3:58", ConstellationTheme.RED),
            new Row("", "Clear", "PB 6:41", ConstellationTheme.TEXT_MUTED)
        );
    }

    private static String fmt(long ms) {
        int s = (int) (ms / 1000);
        return s / 60 + ":" + String.format("%02d", s % 60);
    }
}
