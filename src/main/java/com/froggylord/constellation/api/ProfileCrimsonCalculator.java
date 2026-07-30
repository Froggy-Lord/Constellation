package com.froggylord.constellation.api;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

// ported from SkyBlockPv (modified MIT): data/api/skills/combat/CrimsonIsleData.kt, data/repo/CrimsonIsleCodecs.kt, screens/windowed/tabs/combat/CrimsonIsleScreen.kt
// Portions of this code are from the SkyBlockPv mod.
// ported from Skyblocker (LGPL-3.0-or-later): skyblock/profileviewer2/model/NetherIslandPlayerData.java
public final class ProfileCrimsonCalculator {
    private static final List<String> KUUDRA = List.of("none", "hot", "burning", "fiery", "infernal");
    private static final List<String> DOJO = List.of("mob_kb", "wall_jump", "archer", "snake", "sword_swap", "fireball", "lock_head");
    private static final int[] COLLECTION = {10, 100, 500, 2_000, 5_000};
    private static final int[] BELTS = {0, 1_000, 2_000, 4_000, 6_000, 7_000};

    private ProfileCrimsonCalculator() {}

    public static Result calculate(JsonObject member, String kuudraSort, String dojoSort,
                                   boolean hideZeroKuudra, boolean hideUnattemptedDojo) {
        JsonObject data = object(member, "nether_island_player_data");
        Reputation mage = reputation("mages", "Mage", integer(data.get("mages_reputation")));
        Reputation barbarian = reputation("barbarians", "Barbarian", integer(data.get("barbarians_reputation")));
        int highestReputation = Math.max(mage.reputation(), barbarian.reputation());
        String selected = string(data.get("selected_faction"), "none");

        JsonObject completed = object(data, "kuudra_completed_tiers");
        List<Kuudra> kuudra = new ArrayList<>();
        Set<String> knownKuudra = new HashSet<>(KUUDRA);
        for (int i = 0; i < KUUDRA.size(); i++) {
            String id = KUUDRA.get(i);
            kuudra.add(new Kuudra(id, kuudraName(id), integer(completed.get(id)),
                integer(completed.get("highest_wave_" + id)), i + 1, false));
        }
        for (var entry : completed.entrySet()) {
            String id = entry.getKey();
            if (!id.startsWith("highest_wave_") && !knownKuudra.contains(id))
                kuudra.add(new Kuudra(id, title(id), integer(entry.getValue()),
                    integer(completed.get("highest_wave_" + id)), 0, true));
        }
        int totalRuns = kuudra.stream().mapToInt(Kuudra::completions).sum();
        int collectionPoints = kuudra.stream().mapToInt(value -> value.completions() * value.weight()).sum();
        int collectionTier = thresholdLevel(collectionPoints, COLLECTION);
        int collectionNext = collectionTier >= COLLECTION.length ? 0 : COLLECTION[collectionTier];
        int highestWave = kuudra.stream().mapToInt(Kuudra::highestWave).max().orElse(0);
        kuudra.removeIf(value -> hideZeroKuudra && value.completions() == 0 && value.highestWave() == 0);
        kuudra.sort(kuudraComparator(kuudraSort));

        JsonObject dojoData = object(data, "dojo");
        List<Dojo> dojo = new ArrayList<>();
        Set<String> knownDojo = new HashSet<>(DOJO);
        for (String id : DOJO) dojo.add(dojo(id, dojoData, false));
        for (var entry : dojoData.entrySet()) {
            if (!entry.getKey().startsWith("dojo_points_")) continue;
            String id = entry.getKey().substring("dojo_points_".length());
            if (!knownDojo.contains(id)) dojo.add(dojo(id, dojoData, true));
        }
        int totalDojo = dojo.stream().mapToInt(value -> Math.max(0, value.points())).sum();
        boolean attemptedDojo = dojo.stream().anyMatch(value -> value.points() >= 0);
        Belt belt = belt(attemptedDojo ? totalDojo : -1);
        dojo.removeIf(value -> hideUnattemptedDojo && value.points() < 0 && value.time() < 0);
        dojo.sort(dojoComparator(dojoSort));

        return new Result(!data.isEmpty(), factionName(selected), mage, barbarian,
            kuudraName(unlockedKuudra(highestReputation)), List.copyOf(kuudra), totalRuns,
            collectionPoints, collectionTier, COLLECTION.length,
            collectionNext == 0 ? 0 : Math.max(0, collectionNext - collectionPoints), highestWave,
            List.copyOf(dojo), attemptedDojo ? totalDojo : -1, belt);
    }

    private static Reputation reputation(String id, String name, int reputation) {
        int value = Math.max(0, reputation);
        int next = value < 1_000 ? 1_000 : value < 3_000 ? 3_000 : value < 7_000 ? 7_000 : value < 12_000 ? 12_000 : 0;
        String rank = value >= 12_000 ? "Hero" : value >= 7_000 ? "Honored"
            : value >= 3_000 ? "Trusted" : value >= 1_000 ? "Friendly" : "Neutral";
        return new Reputation(id, name, value, rank, next == 0 ? 0 : next - value);
    }

    private static Dojo dojo(String id, JsonObject data, boolean unknown) {
        int points = integer(data.get("dojo_points_" + id), -1);
        int time = integer(data.get("dojo_time_" + id), -1);
        return new Dojo(id, dojoName(id), points, time, points < 0 ? "None" : grade(points), unknown);
    }

    private static Belt belt(int points) {
        if (points < 0) return new Belt("None", 0, 1_000);
        String[] names = {"White", "Yellow", "Green", "Blue", "Brown", "Black"};
        int level = thresholdLevel(points, BELTS);
        int index = Math.max(0, level - 1);
        int next = level >= BELTS.length ? 0 : BELTS[level];
        return new Belt(names[index], BELTS[index], next == 0 ? 0 : Math.max(0, next - points));
    }

    private static String grade(int points) {
        return points >= 1_000 ? "S" : points >= 800 ? "A" : points >= 600 ? "B"
            : points >= 400 ? "C" : points >= 200 ? "D" : "F";
    }

    private static int thresholdLevel(int value, int[] thresholds) {
        int level = 0;
        for (int threshold : thresholds) if (value >= threshold) level++;
        return level;
    }

    private static String unlockedKuudra(int reputation) {
        return reputation >= 12_000 ? "infernal" : reputation >= 7_000 ? "fiery"
            : reputation >= 3_000 ? "burning" : reputation >= 1_000 ? "hot" : "none";
    }

    private static Comparator<Kuudra> kuudraComparator(String sort) {
        Comparator<Kuudra> canonical = Comparator.comparingInt(value -> value.unknown() ? Integer.MAX_VALUE : value.weight());
        return switch (upper(sort)) {
            case "COMPLETIONS" -> Comparator.comparingInt(Kuudra::completions).reversed().thenComparing(Kuudra::name);
            case "WAVE" -> Comparator.comparingInt(Kuudra::highestWave).reversed().thenComparing(Kuudra::name);
            case "NAME" -> Comparator.comparing(Kuudra::name);
            default -> canonical.thenComparing(Kuudra::name);
        };
    }

    private static Comparator<Dojo> dojoComparator(String sort) {
        Comparator<Dojo> canonical = Comparator.comparingInt(value -> {
            int index = DOJO.indexOf(value.id());
            return index < 0 ? Integer.MAX_VALUE : index;
        });
        return switch (upper(sort)) {
            case "POINTS" -> Comparator.comparingInt(Dojo::points).reversed().thenComparing(Dojo::name);
            case "TIME" -> Comparator.<Dojo>comparingInt(value -> value.time() < 0 ? Integer.MAX_VALUE : value.time()).thenComparing(Dojo::name);
            case "NAME" -> Comparator.comparing(Dojo::name);
            default -> canonical.thenComparing(Dojo::name);
        };
    }

    private static String factionName(String id) {
        return switch (id.toLowerCase(Locale.ROOT)) {
            case "mages" -> "Mage";
            case "barbarians" -> "Barbarian";
            case "none", "" -> "None";
            default -> title(id);
        };
    }

    private static String kuudraName(String id) {
        return switch (id) {
            case "none" -> "Basic";
            case "hot" -> "Hot";
            case "burning" -> "Burning";
            case "fiery" -> "Fiery";
            case "infernal" -> "Infernal";
            default -> title(id);
        };
    }

    private static String dojoName(String id) {
        return switch (id) {
            case "mob_kb" -> "Force";
            case "wall_jump" -> "Stamina";
            case "archer" -> "Mastery";
            case "snake" -> "Swiftness";
            case "sword_swap" -> "Discipline";
            case "fireball" -> "Tenacity";
            case "lock_head" -> "Control";
            default -> title(id);
        };
    }

    private static JsonObject object(JsonObject root, String key) {
        JsonElement value = root == null ? null : root.get(key);
        return value != null && value.isJsonObject() ? value.getAsJsonObject() : new JsonObject();
    }

    private static int integer(JsonElement value) { return integer(value, 0); }
    private static int integer(JsonElement value, int fallback) {
        try { return value == null ? fallback : value.getAsInt(); }
        catch (RuntimeException ignored) { return fallback; }
    }
    private static String string(JsonElement value, String fallback) {
        try { return value == null ? fallback : value.getAsString(); }
        catch (RuntimeException ignored) { return fallback; }
    }
    private static String upper(String value) { return value == null ? "" : value.toUpperCase(Locale.ROOT); }
    private static String title(String value) {
        StringBuilder out = new StringBuilder();
        for (String part : value.replace('_', ' ').toLowerCase(Locale.ROOT).split(" ")) {
            if (!out.isEmpty()) out.append(' ');
            if (!part.isEmpty()) out.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return out.toString();
    }

    public record Reputation(String id, String name, int reputation, String rank, int remaining) {}
    public record Kuudra(String id, String name, int completions, int highestWave, int weight, boolean unknown) {}
    public record Dojo(String id, String name, int points, int time, String grade, boolean unknown) {}
    public record Belt(String name, int threshold, int remaining) {}
    public record Result(boolean available, String selectedFaction, Reputation mage, Reputation barbarian,
                         String highestKuudra, List<Kuudra> kuudra, int kuudraRuns, int collectionPoints,
                         int collectionTier, int collectionMaxTier, int collectionRemaining, int highestWave,
                         List<Dojo> dojo, int dojoPoints, Belt belt) {}
}
