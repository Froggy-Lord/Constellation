package com.froggylord.constellation.api;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

// ported from Skyblocker (LGPL-3.0-or-later): skyblock/profileviewer2/utils/LevelCalculator.java, utils/Skill.java
// ported from SkyBlockPv (modified MIT): api/SkillAPI.kt
// Portions of this code are from the SkyBlockPv mod.
public final class ProfileSkillCalculator {
    private static final double[] REGULAR = {
        50, 125, 200, 300, 500, 750, 1000, 1500, 2000, 3500, 5000, 7500, 10000, 15000, 20000,
        30000, 50000, 75000, 100000, 200000, 300000, 400000, 500000, 600000, 700000, 800000,
        900000, 1000000, 1100000, 1200000, 1300000, 1400000, 1500000, 1600000, 1700000,
        1800000, 1900000, 2000000, 2100000, 2200000, 2300000, 2400000, 2500000, 2600000,
        2750000, 2900000, 3100000, 3400000, 3700000, 4000000, 4300000, 4600000, 4900000,
        5200000, 5500000, 5800000, 6100000, 6400000, 6700000, 7000000
    };
    private static final double[] RUNECRAFTING = {
        50, 100, 125, 160, 200, 250, 315, 400, 500, 625, 785, 1000, 1250, 1600, 2000, 2465,
        3125, 4000, 5000, 6200, 7800, 9800, 12200, 15300, 19050
    };
    private static final double[] SOCIAL = {
        50, 100, 150, 250, 500, 750, 1000, 1250, 1500, 2000, 2500, 3000, 3750, 4500, 6000,
        8000, 10000, 12500, 15000, 20000, 25000, 30000, 35000, 40000, 50000
    };
    private static final double[] HUNTING = {
        50, 125, 200, 300, 500, 750, 1000, 1500, 2000, 3500, 5000, 7500, 10000, 15000, 20000,
        30000, 50000, 75000, 100000, 200000, 300000, 400000, 500000, 600000, 700000
    };
    private static final List<String> ORDER = List.of(
        "farming", "mining", "combat", "foraging", "fishing", "enchanting", "alchemy", "taming",
        "carpentry", "hunting", "runecrafting", "social"
    );

    private ProfileSkillCalculator() {}

    public static List<Skill> calculate(JsonObject member) {
        List<Skill> out = new ArrayList<>();
        for (String id : ORDER) {
            JsonElement element = findXp(member, id);
            int cap = cap(member, id);
            double[] curve = curve(id);
            out.add(level(id, element == null ? 0 : number(element), element != null, cap, curve));
        }
        return out;
    }

    public static double average(List<Skill> skills, boolean carpentry, boolean hunting, boolean cosmetic) {
        double total = 0;
        int count = 0;
        for (Skill skill : skills) {
            if (!skill.available()) continue;
            if (skill.id().equals("carpentry") && !carpentry) continue;
            if (skill.id().equals("hunting") && !hunting) continue;
            if ((skill.id().equals("runecrafting") || skill.id().equals("social")) && !cosmetic) continue;
            total += skill.level();
            count++;
        }
        return count == 0 ? 0 : total / count;
    }

    private static Skill level(String id, double xp, boolean available, int cap, double[] curve) {
        xp = Math.max(0, xp);
        double spent = 0;
        int whole = 0;
        while (whole < cap && whole < curve.length && xp >= spent + curve[whole]) {
            spent += curve[whole];
            whole++;
        }
        if (whole >= cap || whole >= curve.length) {
            double overflow = Math.max(0, xp - spent);
            return new Skill(id, available, cap, cap, xp, 0, 0, overflow, true);
        }
        double into = xp - spent;
        double needed = curve[whole];
        return new Skill(id, available, whole + into / needed, cap, xp, into, needed, 0, false);
    }

    private static int cap(JsonObject member, String id) {
        return switch (id) {
            case "combat", "mining", "enchanting" -> 60;
            case "foraging" -> 54;
            case "runecrafting", "social", "hunting" -> 25;
            case "farming" -> Math.min(60, 50 + (int) number(path(member, "jacobs_contest.perks.farming_level_cap")));
            case "taming" -> Math.min(60, 50 + size(path(member, "pets_data.pet_care.pet_types_sacrificed")));
            default -> 50;
        };
    }

    private static double[] curve(String id) {
        return switch (id) {
            case "runecrafting" -> RUNECRAFTING;
            case "social" -> SOCIAL;
            case "hunting" -> HUNTING;
            default -> REGULAR;
        };
    }

    private static JsonElement findXp(JsonObject member, String id) {
        JsonElement current = path(member, "player_data.experience.SKILL_" + id.toUpperCase(Locale.ROOT));
        if (current != null && current.isJsonPrimitive() && current.getAsJsonPrimitive().isNumber()) return current;
        JsonElement legacy = path(member, "experience_skill_" + id);
        return legacy != null && legacy.isJsonPrimitive() && legacy.getAsJsonPrimitive().isNumber() ? legacy : null;
    }

    private static JsonElement path(JsonObject root, String path) {
        JsonElement current = root;
        for (String part : path.split("\\.")) {
            if (current == null || !current.isJsonObject()) return null;
            current = current.getAsJsonObject().get(part);
        }
        return current;
    }

    private static int size(JsonElement element) {
        if (element == null) return 0;
        if (element.isJsonArray()) return element.getAsJsonArray().size();
        if (element.isJsonObject()) return element.getAsJsonObject().size();
        return 0;
    }

    private static double number(JsonElement element) {
        try {
            return element == null || !element.isJsonPrimitive() ? 0 : element.getAsDouble();
        } catch (RuntimeException ignored) {
            return 0;
        }
    }

    public record Skill(String id, boolean available, double level, int cap, double xp, double into,
                        double needed, double overflow, boolean maxed) {
        public double progress() {
            return maxed || needed <= 0 ? 1 : Math.clamp(into / needed, 0, 1);
        }

        public double remaining() {
            return maxed ? 0 : Math.max(0, needed - into);
        }
    }
}
