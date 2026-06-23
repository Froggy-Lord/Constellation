package com.froggylord.constellation.core;

import java.util.List;

/**
 * typed events posted to EventBus by the scraper/data layer.
 * constellations subscribe instead of re-parsing the same data.
 * modelled on skyblocker's DungeonEvents / SkyblockEvents pattern.
 */
public final class GameEvents {

    private GameEvents() {}

    /** fires when the sidebar changes — carries the full current sidebar */
    public record SidebarUpdate(List<String> lines) {}

    /** fires when the player enters a new SkyBlock area */
    public record AreaChange(LocationManager.SkyblockArea area) {}

    /** fires when the action bar updates (health/mana/defense etc) */
    public record ActionBarUpdate(int health, int maxHealth, int mana, int maxMana, int defense) {}

    /** fires when a dungeon room is entered */
    public record RoomEntered(String roomName, String direction, int anchorX, int anchorZ) {}

    /** fires when dungeon score updates */
    public record ScoreUpdate(int score, String grade, double secretPct, int crypts, int deaths) {}
}
