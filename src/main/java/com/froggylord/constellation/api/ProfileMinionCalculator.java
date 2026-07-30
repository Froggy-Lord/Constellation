package com.froggylord.constellation.api;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

// ported from SkyBlockPv (modified MIT): api/data/profile/SkyBlockProfile.kt, data/repo/MinionCodecs.kt, screens/windowed/tabs/collection/MinionScreen.kt
// Portions of this code are from the SkyBlockPv mod.
public final class ProfileMinionCalculator {
    private static final Map<String, String> CATEGORIES = new HashMap<>();
    private static final Map<String, Integer> MAX_TIERS = new HashMap<>();
    // Hypixel SkyBlock Wiki, Minions: Minion Slots
    private static final int[] SLOT_THRESHOLDS = {0, 5, 15, 30, 50, 75, 100, 125, 150, 175, 200, 225,
        250, 275, 300, 350, 400, 450, 500, 550, 600, 650, 700};

    static {
        category("Farming", "COCOA,PUMPKIN,CHICKEN,MUSHROOM,CACTUS,PIG,WHEAT,COW,RABBIT,SUGAR_CANE,MELON,NETHER_WARTS,CARROT,POTATO,SHEEP,SUNFLOWER");
        category("Mining", "HARD_STONE,RED_SAND,MYCELIUM,COBBLESTONE,OBSIDIAN,GLOWSTONE,GRAVEL,SAND,ICE,SNOW,COAL,IRON,GOLD,DIAMOND,LAPIS,REDSTONE,EMERALD,QUARTZ,ENDER_STONE,MITHRIL");
        category("Combat", "ZOMBIE,REVENANT,SKELETON,CREEPER,SPIDER,CAVESPIDER,TARANTULA,BLAZE,MAGMA_CUBE,ENDERMAN,GHAST,SLIME,VOIDLING,INFERNO,VAMPIRE");
        category("Foraging", "OAK,SPRUCE,BIRCH,DARK_OAK,ACACIA,JUNGLE");
        category("Misc", "CLAY,FISHING,FLOWER");
        for (String family : CATEGORIES.keySet()) MAX_TIERS.put(family, 12);
        for (String family : "GRAVEL,SAND,ENDER_STONE,ZOMBIE,SKELETON,CREEPER,SPIDER,CAVESPIDER,ENDERMAN,SLIME,VOIDLING,INFERNO,VAMPIRE,OAK,SPRUCE,BIRCH,DARK_OAK,ACACIA,JUNGLE".split(","))
            MAX_TIERS.put(family, 11);
    }

    private ProfileMinionCalculator() {}

    public static Result calculate(JsonObject profile, String memberId, String category, String search,
                                   String sort, boolean personalOnly, boolean hideUncrafted, boolean hideMaxed) {
        Set<String> coop = new HashSet<>();
        Set<String> personal = new HashSet<>();
        JsonObject members = object(profile, "members");
        for (var member : members.entrySet()) {
            Set<String> target = member.getKey().equalsIgnoreCase(memberId) ? personal : Set.of();
            JsonArray crafted = array(object(member.getValue().getAsJsonObject(), "player_data"), "crafted_generators");
            for (JsonElement value : crafted) {
                if (!value.isJsonPrimitive()) continue;
                String id = value.getAsString().toUpperCase(Locale.ROOT);
                if (id.isBlank()) continue;
                coop.add(id);
                if (target == personal) personal.add(id);
            }
        }
        Set<String> selected = personalOnly ? personal : coop;
        String wantedCategory = category == null ? "ALL" : category.trim();
        String wantedSearch = search == null ? "" : search.trim().toLowerCase(Locale.ROOT);
        List<Minion> rows = new ArrayList<>();
        for (String family : CATEGORIES.keySet()) {
            int max = MAX_TIERS.get(family);
            int highest = highest(selected, family);
            int crafted = crafted(selected, family, max);
            Minion row = new Minion(CATEGORIES.get(family), family, title(family), highest, max, crafted,
                Math.max(0, max - crafted), crafted / (double) max, false);
            if (include(row, wantedCategory, wantedSearch, hideUncrafted, hideMaxed)) rows.add(row);
        }
        Set<String> unknownFamilies = new HashSet<>();
        for (String id : selected) {
            Parsed parsed = parse(id);
            if (parsed == null || CATEGORIES.containsKey(parsed.family())) continue;
            unknownFamilies.add(parsed == null ? id : parsed.family());
        }
        for (String family : unknownFamilies) {
            Minion row = new Minion("Unknown", family, title(family), highest(selected, family), 0,
                countPrefix(selected, family), 0, 0, true);
            if (include(row, wantedCategory, wantedSearch, hideUncrafted, hideMaxed)) rows.add(row);
        }
        rows.sort(comparator(sort));
        int unique = selected.size();
        int personalUnique = personal.size();
        int coopUnique = coop.size();
        int bonus = 0;
        for (int threshold : SLOT_THRESHOLDS) if (unique >= threshold) bonus++;
        bonus = Math.max(0, bonus - 1);
        int next = 0;
        for (int threshold : SLOT_THRESHOLDS) if (threshold > unique) { next = threshold; break; }
        int knownMaximum = MAX_TIERS.values().stream().mapToInt(Integer::intValue).sum();
        return new Result(List.copyOf(rows), unique, personalUnique, coopUnique, knownMaximum,
            5 + bonus, next, next == 0 ? 0 : next - unique, unknownFamilies.size(), !members.isEmpty());
    }

    private static boolean include(Minion row, String category, String search, boolean hideUncrafted, boolean hideMaxed) {
        if (!category.equalsIgnoreCase("ALL") && !row.category().equalsIgnoreCase(category)) return false;
        if (!search.isEmpty() && !row.name().toLowerCase(Locale.ROOT).contains(search)
            && !row.family().toLowerCase(Locale.ROOT).contains(search)) return false;
        if (hideUncrafted && row.crafted() == 0) return false;
        return !hideMaxed || row.unknown() || row.crafted() < row.maxTier();
    }

    private static Comparator<Minion> comparator(String sort) {
        return switch (sort == null ? "" : sort.toUpperCase(Locale.ROOT)) {
            case "NAME" -> Comparator.comparing(Minion::name);
            case "TIER" -> Comparator.comparingInt(Minion::highestTier).reversed().thenComparing(Minion::name);
            case "MISSING" -> Comparator.comparingInt(Minion::missing).reversed().thenComparing(Minion::name);
            case "COMPLETION" -> Comparator.comparingDouble(Minion::completion).reversed().thenComparing(Minion::name);
            default -> Comparator.comparing(Minion::category).thenComparing(Minion::name);
        };
    }

    private static int highest(Set<String> ids, String family) {
        int highest = 0;
        for (String id : ids) {
            Parsed parsed = parse(id);
            if (parsed != null && parsed.family().equals(family)) highest = Math.max(highest, parsed.tier());
        }
        return highest;
    }

    private static int crafted(Set<String> ids, String family, int max) {
        boolean[] tiers = new boolean[max + 1];
        for (String id : ids) {
            Parsed parsed = parse(id);
            if (parsed != null && parsed.family().equals(family) && parsed.tier() > 0 && parsed.tier() <= max)
                tiers[parsed.tier()] = true;
        }
        int count = 0;
        for (boolean tier : tiers) if (tier) count++;
        return count;
    }

    private static int countPrefix(Set<String> ids, String family) {
        int count = 0;
        for (String id : ids) {
            Parsed parsed = parse(id);
            if (parsed != null && parsed.family().equals(family)) count++;
        }
        return count;
    }

    private static Parsed parse(String id) {
        int split = id.lastIndexOf('_');
        if (split <= 0 || split == id.length() - 1) return null;
        try {
            String family = id.substring(0, split);
            if (family.endsWith("_GENERATOR")) family = family.substring(0, family.length() - 10);
            return new Parsed(family, Integer.parseInt(id.substring(split + 1)));
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static void category(String category, String families) {
        for (String family : families.split(",")) CATEGORIES.put(family, category);
    }

    private static String title(String value) {
        String clean = value.replace('_', ' ').toLowerCase(Locale.ROOT);
        StringBuilder out = new StringBuilder();
        for (String part : clean.split(" ")) {
            if (!out.isEmpty()) out.append(' ');
            if (!part.isEmpty()) out.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return out.toString();
    }

    private static JsonObject object(JsonObject root, String key) {
        JsonElement value = root == null ? null : root.get(key);
        return value != null && value.isJsonObject() ? value.getAsJsonObject() : new JsonObject();
    }

    private static JsonArray array(JsonObject root, String key) {
        JsonElement value = root.get(key);
        return value != null && value.isJsonArray() ? value.getAsJsonArray() : new JsonArray();
    }

    private record Parsed(String family, int tier) {}
    public record Minion(String category, String family, String name, int highestTier, int maxTier,
                         int crafted, int missing, double completion, boolean unknown) {}
    public record Result(List<Minion> minions, int unique, int personalUnique, int coopUnique,
                         int knownMaximum, int slotsFromCrafts, int nextSlotAt, int nextSlotRemaining,
                         int unknownFamilies, boolean available) {}
}
