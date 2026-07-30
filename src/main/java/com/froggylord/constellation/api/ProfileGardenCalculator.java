package com.froggylord.constellation.api;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

// ported from SkyBlockPv (modified MIT): data/api/skills/farming/GardenProfile.kt, FarmingData.kt, screens/windowed/tabs/farming/FarmingScreen.kt, CropScreen.kt, ComposterScreen.kt
// Portions of this code are from the SkyBlockPv mod.
public final class ProfileGardenCalculator {
    private static final int[] GARDEN_XP_STEPS = {0, 70, 70, 140, 240, 600, 1_500, 2_000, 2_500, 3_000,
        10_000, 10_000, 10_000, 10_000, 10_000};
    private static final List<String> CROPS = List.of("WHEAT", "PUMPKIN", "POTATO_ITEM", "SUGAR_CANE", "MELON",
        "CARROT_ITEM", "INK_SACK:3", "NETHER_STALK", "CACTUS", "MUSHROOM_COLLECTION", "MOONFLOWER",
        "DOUBLE_PLANT", "WILD_ROSE");
    private static final List<String> COMPOSTER = List.of("SPEED", "MULTI_DROP", "FUEL_CAP", "ORGANIC_MATTER_CAP", "COST_REDUCTION");

    private ProfileGardenCalculator() {}

    public static Result calculate(JsonObject member, ProfileViewerApi.GardenResult response,
                                   ProfileGardenData.Catalogue catalogue,
                                   String cropSort, String visitorFilter, String visitorSort,
                                   boolean hideZeroCrops, boolean hideZeroVisitors) {
        JsonObject garden = response.garden();
        JsonObject player = object(member, "garden_player_data");
        JsonObject contests = object(member, "jacobs_contest");
        long experience = whole(garden.get("garden_experience"));
        long[] thresholds = cumulative(GARDEN_XP_STEPS);
        int level = 0;
        for (int i = 0; i < thresholds.length; i++) if (experience >= thresholds[i]) level = i + 1;
        level = Math.min(level, thresholds.length);
        long start = thresholds[Math.max(0, level - 1)];
        long next = level >= thresholds.length ? 0 : thresholds[level];

        JsonObject resources = object(garden, "resources_collected");
        JsonObject upgrades = object(garden, "crop_upgrade_levels");
        List<Crop> crops = new ArrayList<>();
        Set<String> known = new HashSet<>(CROPS);
        for (String id : CROPS) crops.add(crop(id, resources, upgrades, catalogue, false));
        for (var entry : resources.entrySet())
            if (!known.contains(entry.getKey()))
                crops.add(crop(entry.getKey(), resources, upgrades, catalogue, true));
        crops.removeIf(value -> hideZeroCrops && value.collected() == 0 && value.upgrade() == 0);
        crops.sort(cropComparator(cropSort));

        JsonObject commission = object(garden, "commission_data");
        JsonObject visits = object(commission, "visits");
        JsonObject completed = object(commission, "completed");
        List<Visitor> visitors = new ArrayList<>();
        Set<String> visitorIds = new HashSet<>();
        visitorIds.addAll(visits.keySet());
        visitorIds.addAll(completed.keySet());
        for (String id : visitorIds) {
            ProfileGardenData.Visitor definition = catalogue == null ? null : catalogue.visitors().get(id);
            Visitor visitor = new Visitor(id, definition == null ? title(id) : definition.name(),
                definition == null ? "UNKNOWN" : definition.rarity(),
                integer(visits.get(id)), integer(completed.get(id)), definition == null);
            String filter = visitorFilter == null ? "ALL" : visitorFilter.toUpperCase(Locale.ROOT);
            if ((!hideZeroVisitors || visitor.visits() > 0 || visitor.completed() > 0)
                && (filter.equals("ALL") || visitor.rarity().equalsIgnoreCase(filter)
                    || filter.equals("UNKNOWN") && visitor.unknown())) visitors.add(visitor);
        }
        visitors.sort(visitorComparator(visitorSort));

        JsonObject composter = object(garden, "composter_data");
        JsonObject composterUpgrades = object(composter, "upgrades");
        List<Upgrade> composterRows = COMPOSTER.stream().map(id -> {
            int upgradeLevel = integer(composterUpgrades.get(id));
            ProfileGardenData.Composter definition = catalogue == null ? null : catalogue.composter().get(id);
            int maximum = definition == null ? 0 : definition.maximum();
            Map<String, Integer> paid = definition == null || upgradeLevel <= 0 ? java.util.Map.of()
                : definition.costs().get(Math.min(upgradeLevel, maximum) - 1);
            Map<String, Integer> total = definition == null ? java.util.Map.of()
                : definition.costs().getLast();
            return new Upgrade(id, definition == null ? title(id) : definition.name(), upgradeLevel, maximum,
                paid.getOrDefault("copper", 0), total.getOrDefault("copper", 0));
        }).toList();
        JsonObject greenhouse = object(garden, "garden_upgrades");
        List<Upgrade> greenhouseRows = List.of("GROWTH_SPEED", "YIELD", "PLOT_LIMIT").stream()
            .map(id -> new Upgrade(id, title(id), integer(greenhouse.get(id)), 0, 0, 0)).toList();

        JsonObject medals = object(contests, "medals_inv");
        JsonObject perks = object(contests, "perks");
        JsonObject contestRows = object(contests, "contests");
        int claimed = 0;
        for (JsonElement value : contestRows.asMap().values())
            if (value.isJsonObject() && bool(value.getAsJsonObject().get("claimed_rewards"))) claimed++;

        return new Result(true, experience, level, level >= thresholds.length ? 0 : experience - start,
            level >= thresholds.length ? 0 : next - start, integer(player.get("copper")),
            integer(player.get("larva_consumed")), arraySize(garden, "unlocked_plots_ids"),
            string(garden.get("selected_barn_skin"), "Unknown"), arraySize(garden, "unlocked_barn_skins"),
            List.copyOf(crops), resources.asMap().values().stream().mapToLong(ProfileGardenCalculator::whole).sum(),
            integer(commission.get("total_completed")), integer(commission.get("unique_npcs_served")),
            List.copyOf(visitors), contestRows.size(), claimed, integer(medals.get("bronze")),
            integer(medals.get("silver")), integer(medals.get("gold")),
            integer(perks.get("farming_level_cap")), integer(perks.get("double_drops")),
            bool(perks.get("personal_bests")), decimal(composter.get("organic_matter")),
            decimal(composter.get("fuel_units")), decimal(composter.get("compost_units")),
            integer(composter.get("compost_items")), integer(composter.get("conversion_ticks")),
            whole(composter.get("last_save")), composterRows, arraySize(garden, "greenhouse_slots"),
            greenhouseRows, response.cached());
    }

    private static Crop crop(String id, JsonObject resources, JsonObject upgrades,
                             ProfileGardenData.Catalogue catalogue, boolean unknown) {
        long collected = whole(resources.get(id));
        int upgrade = integer(upgrades.get(id));
        List<Long> milestones = catalogue == null ? List.of() : catalogue.milestones().getOrDefault(id, List.of());
        int milestone = 0;
        while (milestone < milestones.size() && collected >= milestones.get(milestone)) milestone++;
        long start = milestone == 0 ? 0 : milestones.get(milestone - 1);
        long next = milestone >= milestones.size() ? 0 : milestones.get(milestone);
        int copperPaid = catalogue == null ? 0 : catalogue.cropUpgradeCosts().stream()
            .limit(Math.clamp(upgrade, 0, catalogue.cropUpgradeCosts().size())).mapToInt(Integer::intValue).sum();
        int copperTotal = catalogue == null ? 0 : catalogue.cropUpgradeCosts().stream().mapToInt(Integer::intValue).sum();
        return new Crop(id, cropName(id), collected, upgrade, milestone, milestones.size(),
            milestone >= milestones.size() ? 0 : collected - start,
            milestone >= milestones.size() ? 0 : next - start,
            milestones.isEmpty() ? 0 : milestones.getLast(), copperPaid, copperTotal, unknown);
    }

    private static Comparator<Crop> cropComparator(String sort) {
        Comparator<Crop> canonical = Comparator.comparingInt(value -> {
            int index = CROPS.indexOf(value.id());
            return index < 0 ? Integer.MAX_VALUE : index;
        });
        return switch (sort == null ? "" : sort.toUpperCase(Locale.ROOT)) {
            case "COLLECTED" -> Comparator.comparingLong(Crop::collected).reversed().thenComparing(Crop::name);
            case "UPGRADE" -> Comparator.comparingInt(Crop::upgrade).reversed().thenComparing(Crop::name);
            case "MILESTONE" -> Comparator.comparingInt(Crop::milestone).reversed().thenComparing(Crop::name);
            case "NAME" -> Comparator.comparing(Crop::name);
            default -> canonical.thenComparing(Crop::name);
        };
    }

    private static Comparator<Visitor> visitorComparator(String sort) {
        return switch (sort == null ? "" : sort.toUpperCase(Locale.ROOT)) {
            case "NAME" -> Comparator.comparing(Visitor::name);
            case "RARITY" -> Comparator.<Visitor>comparingInt(value -> rarityOrder(value.rarity())).reversed()
                .thenComparing(Visitor::name);
            case "VISITS" -> Comparator.comparingInt(Visitor::visits).reversed().thenComparing(Visitor::name);
            default -> Comparator.comparingInt(Visitor::completed).reversed()
                .thenComparing(Comparator.comparingInt(Visitor::visits).reversed()).thenComparing(Visitor::name);
        };
    }

    private static int rarityOrder(String rarity) {
        return switch (rarity.toUpperCase(Locale.ROOT)) {
            case "SPECIAL" -> 5;
            case "MYTHIC" -> 4;
            case "LEGENDARY" -> 3;
            case "RARE" -> 2;
            case "UNCOMMON" -> 1;
            default -> 0;
        };
    }

    private static long[] cumulative(int[] steps) {
        long[] values = new long[steps.length];
        long total = 0;
        for (int i = 0; i < steps.length; i++) {
            total += steps[i];
            values[i] = total;
        }
        return values;
    }

    private static String cropName(String id) {
        return switch (id) {
            case "POTATO_ITEM" -> "Potato";
            case "CARROT_ITEM" -> "Carrot";
            case "INK_SACK:3" -> "Cocoa Beans";
            case "NETHER_STALK" -> "Nether Wart";
            case "MUSHROOM_COLLECTION" -> "Mushroom";
            case "DOUBLE_PLANT" -> "Sunflower";
            default -> title(id);
        };
    }

    private static JsonObject object(JsonObject root, String key) {
        JsonElement value = root == null ? null : root.get(key);
        return value != null && value.isJsonObject() ? value.getAsJsonObject() : new JsonObject();
    }
    private static int arraySize(JsonObject root, String key) {
        JsonElement value = root.get(key);
        return value != null && value.isJsonArray() ? value.getAsJsonArray().size() : 0;
    }
    private static int integer(JsonElement value) {
        try { return value == null ? 0 : value.getAsInt(); } catch (RuntimeException ignored) { return 0; }
    }
    private static long whole(JsonElement value) {
        try { return value == null ? 0 : value.getAsLong(); } catch (RuntimeException ignored) { return 0; }
    }
    private static double decimal(JsonElement value) {
        try { return value == null ? 0 : value.getAsDouble(); } catch (RuntimeException ignored) { return 0; }
    }
    private static boolean bool(JsonElement value) {
        try { return value != null && value.getAsBoolean(); } catch (RuntimeException ignored) { return false; }
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

    public record Crop(String id, String name, long collected, int upgrade, int milestone, int maxMilestone,
                       long milestoneProgress, long milestoneRequired, long maximumRequired,
                       int copperPaid, int copperTotal, boolean unknown) {}
    public record Visitor(String id, String name, String rarity, int visits, int completed, boolean unknown) {}
    public record Upgrade(String id, String name, int level, int maximum, int copperPaid, int copperTotal) {}
    public record Result(boolean available, long gardenXp, int gardenLevel, long levelProgress, long levelRequired,
                         int copper, int larvaConsumed, int unlockedPlots, String selectedBarnSkin,
                         int unlockedBarnSkins, List<Crop> crops, long cropsCollected, int visitorsCompleted,
                         int uniqueVisitors, List<Visitor> visitors, int contests, int claimedContests,
                         int bronzeMedals, int silverMedals, int goldMedals, int farmingCapUpgrades,
                         int doubleDropUpgrades, boolean personalBests, double organicMatter, double fuel,
                         double compostUnits, int compostItems, int conversionTicks, long composterLastSave,
                         List<Upgrade> composterUpgrades, int greenhouseSlots, List<Upgrade> greenhouseUpgrades,
                         boolean cached) {}
}
