package com.froggylord.constellation.constellation;

import com.froggylord.constellation.ConstellationClient;
import com.froggylord.constellation.data.TabList;

// ported from devonian (GPL-3.0): features/dungeons/MilestoneDisplay.kt
// cross-checked with SkyHanni (LGPL-2.1): features/dungeon/DungeonMilestonesDisplay.kt
public final class DungeonMilestone {
    private static int milestone;
    private static boolean initialized;

    private DungeonMilestone() {}

    public static void init() {
        if (initialized) return;
        initialized = true;
        ConstellationClient.tick().every(5, "orion-dungeon-milestone", DungeonMilestone::update);
    }

    public static String hudText() {
        if (!ConstellationClient.loc().inDungeons()) return null;
        String colour = milestone >= 3 ? "§a" : milestone == 2 ? "§e" : "§c";
        return colour + milestone;
    }

    private static void update() {
        if (!ConstellationClient.loc().inDungeons()) {
            milestone = 0;
            return;
        }
        for (String line : TabList.lines()) {
            if (!line.startsWith("Your Milestone:")) continue;
            int separator = line.indexOf(':');
            if (separator < 0 || separator + 1 >= line.length()) continue;
            String value = line.substring(separator + 1).trim();
            if (value.isEmpty()) continue;
            int parsed = Character.getNumericValue(value.codePointAt(value.offsetByCodePoints(0, value.codePointCount(0, value.length()) - 1)));
            if (parsed >= 0 && parsed <= 9) milestone = parsed;
            return;
        }
    }
}
