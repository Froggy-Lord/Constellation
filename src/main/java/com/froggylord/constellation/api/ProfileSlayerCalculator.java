package com.froggylord.constellation.api;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;

// ported from Skyblocker (LGPL-3.0-or-later): skyblock/profileviewer/slayers/SlayerWidget.java, profileviewer2/model/SlayerData.java, SlayerBoss.java, slayers/SlayerType.java
// ported from SkyBlockPv (modified MIT): data/api/skills/combat/SlayerData.kt, data/repo/SlayerCodecs.kt, screens/windowed/tabs/MainScreen.kt
// Portions of this code are from the SkyBlockPv mod.
public final class ProfileSlayerCalculator {
    private static final long[] NORMAL_LEVELS = {5, 15, 200, 1000, 5000, 20000, 100000, 400000, 1000000};
    private static final long[] VAMPIRE_LEVELS = {20, 75, 240, 840, 2400};
    private static final List<Type> TYPES = List.of(
        new Type("zombie", "Revenant", 5, NORMAL_LEVELS),
        new Type("spider", "Tarantula", 5, NORMAL_LEVELS),
        new Type("wolf", "Sven", 4, NORMAL_LEVELS),
        new Type("enderman", "Voidgloom", 4, NORMAL_LEVELS),
        new Type("blaze", "Inferno", 4, NORMAL_LEVELS),
        new Type("vampire", "Vampire", 5, VAMPIRE_LEVELS)
    );

    private ProfileSlayerCalculator() {}

    public static Result calculate(JsonObject member) {
        JsonObject slayer = object(member, "slayer");
        JsonObject bosses = object(slayer, "slayer_bosses");
        if (bosses.isEmpty()) bosses = object(member, "slayer_bosses");
        boolean available = !bosses.isEmpty();
        List<Slayer> values = new ArrayList<>();
        long totalXp = 0;
        long totalKills = 0;
        long totalAttempts = 0;
        for (Type type : TYPES) {
            JsonObject boss = object(bosses, type.id());
            long xp = Math.round(number(boss.get("xp")));
            List<Tier> tiers = new ArrayList<>();
            long kills = 0;
            long attempts = 0;
            for (int tier = 0; tier < type.bossTiers(); tier++) {
                long tierKills = Math.round(number(boss.get("boss_kills_tier_" + tier)));
                long tierAttempts = Math.round(number(boss.get("boss_attempts_tier_" + tier)));
                tiers.add(new Tier(tier + 1, tierKills, tierAttempts));
                kills += tierKills;
                attempts += tierAttempts;
            }
            boolean rewardsAvailable = boss.has("claimed_levels") && boss.get("claimed_levels").isJsonObject();
            int claimed = claimed(object(boss, "claimed_levels"));
            Level level = level(xp, type.levels());
            values.add(new Slayer(type.id(), type.name(), xp > 0 || kills > 0 || attempts > 0 || !boss.isEmpty(),
                xp, level, kills, attempts, rewardsAvailable, claimed,
                rewardsAvailable ? Math.max(0, Math.min(level.whole(), level.max()) - claimed) : 0, tiers));
            totalXp += xp;
            totalKills += kills;
            totalAttempts += attempts;
        }
        return new Result(available, values, totalXp, totalKills, totalAttempts);
    }

    private static Level level(long xp, long[] milestones) {
        int whole = 0;
        while (whole < milestones.length && xp >= milestones[whole]) whole++;
        if (whole >= milestones.length) {
            return new Level(milestones.length, milestones.length, milestones.length, 1, 0, 0,
                Math.max(0, xp - milestones[milestones.length - 1]));
        }
        long previous = whole == 0 ? 0 : milestones[whole - 1];
        long needed = milestones[whole] - previous;
        long into = Math.max(0, xp - previous);
        return new Level(whole + into / (double) needed, whole, milestones.length,
            into / (double) needed, into, needed, 0);
    }

    private static int claimed(JsonObject levels) {
        int count = 0;
        for (var entry : levels.entrySet()) {
            try {
                if (entry.getValue().getAsBoolean()) count++;
            } catch (RuntimeException ignored) {}
        }
        return count;
    }

    private static JsonObject object(JsonObject root, String key) {
        if (root == null) return new JsonObject();
        JsonElement value = root.get(key);
        return value != null && value.isJsonObject() ? value.getAsJsonObject() : new JsonObject();
    }

    private static double number(JsonElement value) {
        try {
            return value != null && value.isJsonPrimitive() ? value.getAsDouble() : 0;
        } catch (RuntimeException ignored) {
            return 0;
        }
    }

    private record Type(String id, String name, int bossTiers, long[] levels) {}
    public record Result(boolean available, List<Slayer> slayers, long totalXp, long totalKills, long totalAttempts) {}
    public record Slayer(String id, String name, boolean played, long xp, Level level, long kills, long attempts,
                         boolean rewardsAvailable, int claimedRewards, int unclaimedRewards, List<Tier> tiers) {}
    public record Tier(int tier, long kills, long attempts) {}
    public record Level(double level, int whole, int max, double progress, long into, long needed, long overflow) {
        public long remaining() {
            return needed == 0 ? 0 : Math.max(0, needed - into);
        }
    }
}
