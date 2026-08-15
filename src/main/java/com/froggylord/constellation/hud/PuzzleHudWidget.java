package com.froggylord.constellation.hud;

import com.froggylord.constellation.ConstellationClient;
import com.froggylord.constellation.data.TabList;
import com.froggylord.constellation.render.ConstellationTheme;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class PuzzleHudWidget extends ThemedHudWidget {

    private static final Pattern COUNT = Pattern.compile("^Puzzles: \\((\\d+)\\)$");
    private static final Pattern PUZZLE = Pattern.compile(
        "^([\\w ?]+): \\[([\\u2726\\u2714\\u2716])](?: \\((\\w{1,16})\\))?$");

    private final String id;
    private final BooleanSupplier configEnabled;
    private final BooleanSupplier compact;
    private HudPosition position;
    private boolean enabled;

    public PuzzleHudWidget(String id, HudPosition position, BooleanSupplier configEnabled, BooleanSupplier compact) {
        this.id = id;
        this.position = position;
        this.configEnabled = configEnabled;
        this.compact = compact;
        this.enabled = true;
    }

    @Override public String id() { return id; }
    @Override public HudPosition position() { return position; }
    @Override public void setPosition(HudPosition pos) { position = pos; }
    @Override public boolean isEnabled() { return enabled && configEnabled.getAsBoolean(); }
    @Override public void setEnabled(boolean value) { enabled = value; }
    @Override public String editorLabel() { return "Puzzle Status"; }

    @Override
    public boolean visibleNow() {
        return isEnabled() && ConstellationClient.loc().inDungeons()
            && ConstellationClient.dungeon().runStarted() && !ConstellationClient.dungeon().inBoss()
            && snapshot().count() > 0;
    }

    @Override
    protected String title() {
        return "Puzzles (" + snapshot().count() + ")";
    }

    @Override
    protected List<Row> rows() {
        Snapshot snapshot = snapshot();
        if (snapshot.count() == 0) return List.of();
        if (compact.getAsBoolean()) {
            return List.of(
                new Row("", "Open", String.valueOf(snapshot.open()),
                    snapshot.open() == 0 ? ConstellationTheme.GREEN : ConstellationTheme.TEXT),
                new Row("", "Failed", String.valueOf(snapshot.failed()),
                    snapshot.failed() == 0 ? ConstellationTheme.GREEN : ConstellationTheme.RED)
            );
        }

        List<Row> rows = new ArrayList<>(snapshot.count());
        for (Puzzle puzzle : snapshot.puzzles()) {
            String value;
            int colour;
            if (puzzle.state() == '\u2714') {
                value = "Done";
                colour = ConstellationTheme.GREEN;
            } else if (puzzle.state() == '\u2716') {
                value = puzzle.player().isEmpty() ? "Failed" : "Failed: " + puzzle.player();
                colour = ConstellationTheme.RED;
            } else {
                value = "Open";
                colour = ConstellationTheme.ACCENT_BRIGHT;
            }
            rows.add(new Row("", puzzle.name(), value, colour));
        }
        for (int i = snapshot.puzzles().size(); i < snapshot.count(); i++) {
            rows.add(new Row("", "Unknown", "Open", ConstellationTheme.ACCENT_BRIGHT));
        }
        return rows;
    }

    @Override
    protected List<Row> previewRows() {
        return compact.getAsBoolean()
            ? List.of(
                new Row("", "Open", "2", ConstellationTheme.TEXT),
                new Row("", "Failed", "0", ConstellationTheme.GREEN))
            : List.of(
                new Row("", "Boulder", "Done", ConstellationTheme.GREEN),
                new Row("", "Three Weirdos", "Open", ConstellationTheme.ACCENT_BRIGHT),
                new Row("", "Unknown", "Open", ConstellationTheme.ACCENT_BRIGHT));
    }

    // ported from devonian (GPL-3.0): features/dungeons/clear/PuzzlesDisplay.kt
    // cross-checked with Odin (BSD-3-Clause): features/impl/dungeon/PuzzleHud.kt
    private static Snapshot snapshot() {
        int count = 0;
        Map<String, Puzzle> puzzles = new LinkedHashMap<>();
        for (String line : TabList.lines()) {
            Matcher countMatch = COUNT.matcher(line);
            if (countMatch.matches()) {
                count = Integer.parseInt(countMatch.group(1));
                continue;
            }
            Matcher puzzleMatch = PUZZLE.matcher(line);
            if (!puzzleMatch.matches()) continue;
            String name = puzzleMatch.group(1).trim();
            if (name.equals("???")) continue;
            String player = puzzleMatch.group(3) == null ? "" : puzzleMatch.group(3);
            puzzles.put(name, new Puzzle(name, puzzleMatch.group(2).charAt(0), player));
        }

        int completed = 0;
        int failed = 0;
        for (Puzzle puzzle : puzzles.values()) {
            if (puzzle.state() == '\u2714') completed++;
            else if (puzzle.state() == '\u2716') failed++;
        }
        int open = Math.max(0, count - completed - failed);
        return new Snapshot(count, List.copyOf(puzzles.values()), open, failed);
    }

    private record Puzzle(String name, char state, String player) {}
    private record Snapshot(int count, List<Puzzle> puzzles, int open, int failed) {}
}
