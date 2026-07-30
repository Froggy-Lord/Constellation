package com.froggylord.constellation.api;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

// ported from SkyBlockPv (modified MIT): data/api/skills/FishData.kt, screens/windowed/tabs/FishingScreen.kt
// Portions of this code are from the SkyBlockPv mod.
public final class ProfileFishingCalculator {
    private static final List<FishDefinition> FISH = List.of(
        fish("sulphur_skitter", "Sulphur Skitter"), fish("obfuscated_fish_1", "Obfuscated 1"),
        fish("steaming_hot_flounder", "Steaming-Hot Flounder"), fish("gusher", "Gusher"),
        fish("blobfish", "Blobfish"), fish("obfuscated_fish_2", "Obfuscated 2"),
        fish("slugfish", "Slugfish"), fish("flyfish", "Flyfish"),
        fish("obfuscated_fish_3", "Obfuscated 3"), fish("lava_horse", "Lavahorse"),
        fish("mana_ray", "Mana Ray"), fish("volcanic_stonefish", "Volcanic Stonefish"),
        fish("vanille", "Vanille"), fish("skeleton_fish", "Skeleton Fish"),
        fish("moldfin", "Moldfin"), fish("soul_fish", "Soul Fish"),
        fish("karate_fish", "Karate Fish"), fish("golden_fish", "Golden Fish"));
    private static final List<String> TIERS = List.of("bronze", "silver", "gold", "diamond");

    private ProfileFishingCalculator() {}

    public static Result calculate(JsonObject member, String sort, String minimumTier,
                                   boolean hideUncaught, boolean hideDiamond) {
        JsonObject trophy = object(member, "trophy_fish");
        JsonObject playerStats = object(member, "player_stats");
        JsonObject items = object(playerStats, "items_fished");
        JsonObject leveling = object(member, "leveling");
        JsonObject playerData = object(member, "player_data");
        int seaCreatures = integer(path(playerStats, "pets.milestone.sea_creatures_killed"));
        List<TrophyFish> fish = new ArrayList<>();
        Set<String> known = new HashSet<>();
        for (FishDefinition definition : FISH) {
            known.add(definition.id());
            fish.add(readFish(definition.id(), definition.name(), trophy, false));
        }
        for (var entry : trophy.entrySet()) {
            String base = trophyBase(entry.getKey());
            if (base == null || known.contains(base)) continue;
            known.add(base);
            fish.add(readFish(base, title(base), trophy, true));
        }
        int requiredTier = switch (minimumTier == null ? "" : minimumTier.toUpperCase(Locale.ROOT)) {
            case "SILVER" -> 2;
            case "GOLD" -> 3;
            case "DIAMOND" -> 4;
            default -> 0;
        };
        fish.removeIf(value -> hideUncaught && value.total() == 0
            || hideDiamond && value.diamond() > 0
            || value.highestTier() < requiredTier);
        fish.sort(comparator(sort));

        String lastRaw = string(trophy.get("last_caught"), "");
        String lastCatch = lastRaw.contains("/") ? trophyName(lastRaw.substring(0, lastRaw.indexOf('/')))
            + " " + title(lastRaw.substring(lastRaw.indexOf('/') + 1)) : "None";
        int reward = 0;
        JsonElement rewards = trophy.get("rewards");
        if (rewards != null && rewards.isJsonArray())
            for (JsonElement value : rewards.getAsJsonArray()) reward = Math.max(reward, Math.min(4, integer(value)));
        String rank = switch (reward) {
            case 1 -> "Novice";
            case 2 -> "Adept";
            case 3 -> "Expert";
            case 4 -> "Master";
            default -> "None";
        };
        String dolphin = seaCreatures >= 10_000 ? "Legendary" : seaCreatures >= 5_000 ? "Epic"
            : seaCreatures >= 2_500 ? "Rare" : seaCreatures >= 1_000 ? "Uncommon"
            : seaCreatures >= 250 ? "Common" : "None";
        int dolphinNext = seaCreatures >= 10_000 ? 0 : seaCreatures >= 5_000 ? 10_000
            : seaCreatures >= 2_500 ? 5_000 : seaCreatures >= 1_000 ? 2_500
            : seaCreatures >= 250 ? 1_000 : 250;

        return new Result(!trophy.isEmpty() || !items.isEmpty(), List.copyOf(fish),
            integer(trophy.get("total_caught")), lastCatch, rank, reward, seaCreatures, dolphin,
            dolphinNext == 0 ? 0 : dolphinNext - seaCreatures,
            integer(leveling.get("fishing_festival_sharks_killed")),
            integer(playerData.get("fishing_treasure_caught")), integer(items.get("total")),
            integer(items.get("normal")), integer(items.get("treasure")), integer(items.get("large_treasure")),
            integer(items.get("trophy_fish")));
    }

    private static TrophyFish readFish(String id, String name, JsonObject trophy, boolean unknown) {
        int bronze = integer(trophy.get(id + "_bronze"));
        int silver = integer(trophy.get(id + "_silver"));
        int gold = integer(trophy.get(id + "_gold"));
        int diamond = integer(trophy.get(id + "_diamond"));
        int total = integer(trophy.get(id));
        if (total == 0) total = bronze + silver + gold + diamond;
        int highest = diamond > 0 ? 4 : gold > 0 ? 3 : silver > 0 ? 2 : bronze > 0 ? 1 : 0;
        return new TrophyFish(id, name, bronze, silver, gold, diamond, total, highest, unknown);
    }

    private static Comparator<TrophyFish> comparator(String sort) {
        Comparator<TrophyFish> canonical = Comparator.comparingInt(value -> {
            for (int i = 0; i < FISH.size(); i++) if (FISH.get(i).id().equals(value.id())) return i;
            return Integer.MAX_VALUE;
        });
        return switch (sort == null ? "" : sort.toUpperCase(Locale.ROOT)) {
            case "TOTAL" -> Comparator.comparingInt(TrophyFish::total).reversed().thenComparing(TrophyFish::name);
            case "TIER" -> Comparator.comparingInt(TrophyFish::highestTier).reversed()
                .thenComparing(Comparator.comparingInt(TrophyFish::total).reversed()).thenComparing(TrophyFish::name);
            case "DIAMOND" -> Comparator.comparingInt(TrophyFish::diamond).reversed().thenComparing(TrophyFish::name);
            case "NAME" -> Comparator.comparing(TrophyFish::name);
            default -> canonical.thenComparing(TrophyFish::name);
        };
    }

    private static String trophyBase(String key) {
        if (key.equals("total_caught") || key.equals("last_caught") || key.equals("rewards")) return null;
        for (String tier : TIERS) if (key.endsWith("_" + tier)) return key.substring(0, key.length() - tier.length() - 1);
        return key.matches("[a-z0-9_]+") ? key : null;
    }

    private static String trophyName(String raw) {
        String id = raw.toLowerCase(Locale.ROOT);
        for (FishDefinition definition : FISH)
            if (definition.id().equals(id)) return definition.name();
        return title(id);
    }

    private static FishDefinition fish(String id, String name) { return new FishDefinition(id, name); }
    private static JsonElement path(JsonObject root, String dotted) {
        JsonElement value = root;
        for (String key : dotted.split("\\.")) {
            if (value == null || !value.isJsonObject()) return null;
            value = value.getAsJsonObject().get(key);
        }
        return value;
    }
    private static JsonObject object(JsonObject root, String key) {
        JsonElement value = root == null ? null : root.get(key);
        return value != null && value.isJsonObject() ? value.getAsJsonObject() : new JsonObject();
    }
    private static int integer(JsonElement value) {
        try { return value == null ? 0 : value.getAsInt(); } catch (RuntimeException ignored) { return 0; }
    }
    private static String string(JsonElement value, String fallback) {
        try { return value == null ? fallback : value.getAsString(); } catch (RuntimeException ignored) { return fallback; }
    }
    private static String title(String value) {
        StringBuilder out = new StringBuilder();
        for (String part : value.replace('_', ' ').toLowerCase(Locale.ROOT).split(" ")) {
            if (!out.isEmpty()) out.append(' ');
            if (!part.isEmpty()) out.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return out.toString();
    }

    private record FishDefinition(String id, String name) {}
    public record TrophyFish(String id, String name, int bronze, int silver, int gold, int diamond,
                             int total, int highestTier, boolean unknown) {}
    public record Result(boolean available, List<TrophyFish> fish, int trophyCatches, String lastCatch,
                         String trophyRank, int trophyReward, int seaCreatureKills, String dolphinRarity,
                         int dolphinRemaining, int festivalSharks, int treasuresCaught, int totalItemsFished,
                         int normalItemsFished, int treasureItemsFished, int largeTreasures,
                         int trophyItemsFished) {}
}
