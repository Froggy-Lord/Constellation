package com.froggylord.constellation.data;

import com.froggylord.constellation.ConstellationClient;
import com.froggylord.constellation.config.OrionConfig;

/**
 * Personal-best section splits per floor. Timing structure (dungeon start -> blood open ->
 * boss entry -> clear) is ported in spirit from Devonian's api/splits SplitStage tree and
 * Skyblocker's dungeon split timers, but simplified to constellation's flat DungeonScore clock.
 * PBs persist in {@link OrionConfig#splitPBs}.
 */
public final class DungeonSplits {

    private DungeonSplits() {}

    public static final int BLOOD = 0;
    public static final int BOSS = 1;
    public static final int CLEAR = 2;

    /** PB triple {bloodMs, bossMs, clearMs} for a floor, or null if none saved. */
    public static long[] pb(String floor) {
        if (floor == null || floor.isEmpty()) return null;
        return cfg().splitPBs.get(floor);
    }

    /**
     * Compare this run's splits against the stored PBs, keeping the faster of each, and persist
     * if anything improved. Called once when the run ends. The clear split only counts as a PB
     * when the dungeon actually completed (boss cleared), not when the player left early.
     */
    public static void finishRun() {
        if (!DungeonScore.hadRun()) return;
        String floor = DungeonScore.lastFloor();
        if (floor == null) return;

        long blood = DungeonScore.bloodSplitMs();
        long boss = DungeonScore.bossSplitMs();
        long clear = ConstellationClient.dungeon().runEnded() ? DungeonScore.clearSplitMs() : 0;

        OrionConfig cfg = cfg();
        long[] existing = cfg.splitPBs.get(floor);
        long[] pb = existing != null ? existing.clone() : new long[]{0, 0, 0};
        boolean improved = false;
        if (blood > 0 && (pb[BLOOD] == 0 || blood < pb[BLOOD])) { pb[BLOOD] = blood; improved = true; }
        if (boss > 0 && (pb[BOSS] == 0 || boss < pb[BOSS])) { pb[BOSS] = boss; improved = true; }
        if (clear > 0 && (pb[CLEAR] == 0 || clear < pb[CLEAR])) { pb[CLEAR] = clear; improved = true; }

        if (improved) {
            cfg.splitPBs.put(floor, pb);
            ConstellationClient.saveConfig();
        }
    }

    private static OrionConfig cfg() { return ConstellationClient.cfg().orion; }
}
