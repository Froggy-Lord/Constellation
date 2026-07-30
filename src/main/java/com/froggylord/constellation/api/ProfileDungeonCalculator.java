package com.froggylord.constellation.api;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;

// ported from Skyblocker (LGPL-3.0-or-later): skyblock/profileviewer/dungeons/DungeonsPage.java, DungeonFloorRunsWidget.java, profileviewer2/model/GenericCatacombs.java
// ported from SkyBlockPv (modified MIT): data/api/skills/combat/DungeonData.kt, screens/windowed/tabs/combat/DungeonScreen.kt
// Portions of this code are from the SkyBlockPv mod.
public final class ProfileDungeonCalculator {
    private static final double[] XP = {
        50, 75, 110, 160, 230, 330, 470, 670, 950, 1340, 1890, 2665, 3760, 5260, 7380, 10300,
        14400, 20000, 27600, 38000, 52500, 71500, 97000, 132000, 180000, 243000, 328000,
        445000, 600000, 800000, 1065000, 1410000, 1900000, 2500000, 3300000, 4300000,
        5600000, 7200000, 9200000, 12000000, 15000000, 19000000, 24000000, 30000000,
        38000000, 48000000, 60000000, 75000000, 93000000, 116250000
    };
    private static final double OVERFLOW_LEVEL_XP = 200_000_000;
    private static final List<String> CLASSES = List.of("healer", "mage", "berserk", "archer", "tank");

    private ProfileDungeonCalculator() {}

    public static Result calculate(JsonObject member) {
        boolean available = member.has("dungeons") && member.get("dungeons").isJsonObject();
        JsonObject dungeons = object(member, "dungeons");
        JsonObject types = object(dungeons, "dungeon_types");
        JsonObject normal = object(types, "catacombs");
        JsonObject master = object(types, "master_catacombs");
        Level catacombs = level(number(normal.get("experience")));
        List<ClassLevel> classes = new ArrayList<>();
        JsonObject classData = object(dungeons, "player_classes");
        String selected = string(dungeons, "selected_dungeon_class");
        for (String id : CLASSES) {
            JsonObject data = object(classData, id);
            classes.add(new ClassLevel(id, level(number(data.get("experience"))), id.equalsIgnoreCase(selected)));
        }
        List<Floor> floors = new ArrayList<>();
        long runs = 0;
        for (int floor = 0; floor <= 7; floor++) {
            Floor regular = floor(normal, false, floor);
            floors.add(regular);
            runs += regular.completions();
            if (floor > 0) {
                Floor mm = floor(master, true, floor);
                floors.add(mm);
                runs += mm.completions();
            }
        }
        long secrets = Math.round(first(number(path(member, "player_stats.secrets")), number(member.get("secrets"))));
        return new Result(available, catacombs, classes, floors, runs, secrets);
    }

    public static double classAverage(Result result, boolean overflow) {
        return result.classes().stream().mapToDouble(entry ->
            overflow ? entry.level().levelWithOverflow() : Math.min(50, entry.level().level())).average().orElse(0);
    }

    private static Floor floor(JsonObject type, boolean master, int floor) {
        String key = Integer.toString(floor);
        return new Floor(master, floor,
            Math.round(number(path(type, "tier_completions." + key))),
            Math.round(number(path(type, "fastest_time." + key))),
            Math.round(number(path(type, "fastest_time_s." + key))),
            Math.round(number(path(type, "fastest_time_s_plus." + key))),
            Math.round(number(path(type, "best_score." + key))));
    }

    private static Level level(double totalXp) {
        double xp = Math.max(0, totalXp);
        double spent = 0;
        int whole = 0;
        while (whole < XP.length && xp >= spent + XP[whole]) {
            spent += XP[whole];
            whole++;
        }
        if (whole >= XP.length) {
            double overflow = Math.max(0, xp - spent);
            return new Level(50, 1, xp, 0, 0, overflow, 50 + overflow / OVERFLOW_LEVEL_XP);
        }
        double into = xp - spent;
        double needed = XP[whole];
        return new Level(whole + into / needed, into / needed, xp, into, needed, 0, whole + into / needed);
    }

    private static JsonElement path(JsonObject root, String path) {
        JsonElement current = root;
        for (String part : path.split("\\.")) {
            if (current == null || !current.isJsonObject()) return null;
            current = current.getAsJsonObject().get(part);
        }
        return current;
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

    private static double first(double... values) {
        for (double value : values) if (value != 0) return value;
        return 0;
    }

    private static String string(JsonObject root, String key) {
        try {
            return root.has(key) ? root.get(key).getAsString() : "";
        } catch (RuntimeException ignored) {
            return "";
        }
    }

    public record Result(boolean available, Level catacombs, List<ClassLevel> classes, List<Floor> floors,
                         long runs, long secrets) {}
    public record ClassLevel(String id, Level level, boolean selected) {}
    public record Level(double level, double progress, double xp, double into, double needed,
                        double overflowXp, double levelWithOverflow) {}
    public record Floor(boolean master, int floor, long completions, long fastest, long fastestS,
                        long fastestSPlus, long bestScore) {
        public String name() {
            return master ? "M" + floor : floor == 0 ? "Entrance" : "F" + floor;
        }
    }
}
