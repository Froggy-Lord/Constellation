package com.froggylord.constellation.hud;

import com.froggylord.constellation.ConstellationClient;
import com.froggylord.constellation.data.DungeonScore;
import com.froggylord.constellation.render.ConstellationTheme;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;

/**
 * consolidated dungeon score panel (skyhanni-style): the live 0-300 score + grade
 * with a compact breakdown of secrets %, crypts, deaths, and room completion %.
 * all values come from the skyblocker-ported {@link DungeonScore} engine.
 */
public class ScoreHudWidget extends ThemedHudWidget {

    private final String id;
    private final BooleanSupplier configEnabled;
    private HudPosition position;
    private boolean enabled;

    public ScoreHudWidget(String id, HudPosition position, BooleanSupplier configEnabled) {
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
    @Override public String editorLabel() { return "Dungeon Score"; }

    @Override
    public boolean visibleNow() {
        return isEnabled() && ConstellationClient.loc().inDungeons() && DungeonScore.isActive();
    }

    @Override
    protected String title() {
        String floor = DungeonScore.floor();
        return floor == null || floor.isEmpty() ? "Dungeon Score" : "Score — " + floor;
    }

    @Override
    protected List<Row> rows() {
        List<Row> rows = new ArrayList<>(5);
        int score = DungeonScore.score();
        rows.add(new Row(">", "Score", score + " (" + DungeonScore.grade() + ")", DungeonScore.gradeColor()));

        int secrets = DungeonScore.secretPercent();
        rows.add(new Row("", "Secrets", secrets + "%",
            secrets >= 100 ? ConstellationTheme.GREEN : ConstellationTheme.TEXT));

        int crypts = DungeonScore.crypts();
        rows.add(new Row("", "Crypts", String.valueOf(crypts),
            crypts >= 5 ? ConstellationTheme.GREEN : ConstellationTheme.TEXT));

        int deaths = DungeonScore.deaths();
        rows.add(new Row("", "Deaths", String.valueOf(deaths),
            deaths > 0 ? ConstellationTheme.RED : ConstellationTheme.TEXT));

        int cleared = DungeonScore.roomsCleared();
        int total = DungeonScore.roomsTotal();
        String completion = total > 0
            ? cleared + "/" + total + "  " + DungeonScore.completionPercent() + "%"
            : DungeonScore.completionPercent() + "%";
        rows.add(new Row("", "Rooms", completion, ConstellationTheme.TEXT));
        return rows;
    }

    @Override
    protected List<Row> previewRows() {
        return List.of(
            new Row(">", "Score", "271 (S)", 0xFFF1E252),
            new Row("", "Secrets", "92%", ConstellationTheme.GREEN),
            new Row("", "Crypts", "5", ConstellationTheme.GREEN),
            new Row("", "Deaths", "0", ConstellationTheme.TEXT),
            new Row("", "Rooms", "34/36  94%", ConstellationTheme.TEXT)
        );
    }
}
