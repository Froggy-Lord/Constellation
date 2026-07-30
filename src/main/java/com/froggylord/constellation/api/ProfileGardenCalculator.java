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
    private static final List<String> CHIPS = List.of("cropshot", "evergreen", "hypercharge", "mechamind",
        "overdrive", "quickdraw", "rarefinder", "sowledge", "synthesis", "vermin_vaporizer");

    private ProfileGardenCalculator() {}

    public static Result calculate(JsonObject member, ProfileViewerApi.GardenResult response,
                                   ProfileGardenData.Catalogue catalogue,
                                   String cropSort, String visitorFilter, String visitorSort,
                                   boolean hideZeroCrops, boolean hideZeroVisitors,
                                   String mutationFilter, String mutationRarity, String mutationSort,
                                   String mutationSearch) {
        JsonObject garden = response.garden();
        JsonObject player = object(member, "garden_player_data");
        JsonObject contests = object(member, "jacobs_contest");
        long experience = whole(garden.get("garden_experience"));
        List<Long> thresholds = catalogue != null && !catalogue.gardenLevels().isEmpty()
            ? catalogue.gardenLevels() : cumulativeList(GARDEN_XP_STEPS);
        int level = 0;
        for (int i = 0; i < thresholds.size(); i++) if (experience >= thresholds.get(i)) level = i + 1;
        level = Math.min(level, thresholds.size());
        long start = thresholds.get(Math.max(0, level - 1));
        long next = level >= thresholds.size() ? 0 : thresholds.get(level);

        JsonObject resources = object(garden, "resources_collected");
        JsonObject upgrades = object(garden, "crop_upgrade_levels");
        JsonObject personalBestValues = object(contests, "personal_bests");
        List<Crop> crops = new ArrayList<>();
        Set<String> known = new HashSet<>(CROPS);
        for (String id : CROPS) crops.add(crop(id, resources, upgrades, personalBestValues, catalogue, false));
        for (var entry : resources.entrySet())
            if (!known.contains(entry.getKey()))
                crops.add(crop(entry.getKey(), resources, upgrades, personalBestValues, catalogue, true));
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

        // ported from SkyBlockPv (modified MIT): data/api/skills/farming/GardenProfile.kt, screens/windowed/tabs/farming/ComposterScreen.kt
        // Portions of this code are from the SkyBlockPv mod.
        Set<String> unlockedPlotIds = new HashSet<>();
        JsonElement rawUnlockedPlots = garden.get("unlocked_plots_ids");
        if (rawUnlockedPlots != null && rawUnlockedPlots.isJsonArray())
            for (JsonElement value : rawUnlockedPlots.getAsJsonArray())
                if (value.isJsonPrimitive()) unlockedPlotIds.add(value.getAsString());
        Map<String, Integer> unlockedByType = new java.util.HashMap<>();
        if (catalogue != null)
            for (String id : unlockedPlotIds) {
                ProfileGardenData.Plot plot = catalogue.plots().get(id);
                if (plot != null) unlockedByType.merge(plot.type(), 1, Integer::sum);
            }
        List<Plot> plotRows = catalogue == null ? List.of() : catalogue.plots().values().stream()
            .sorted(Comparator.comparingInt(ProfileGardenData.Plot::number))
            .map(plot -> {
                boolean unlocked = unlockedPlotIds.contains(plot.id());
                List<ProfileGardenData.PlotCost> costs = catalogue.plotCosts()
                    .getOrDefault(plot.type(), List.of());
                int unlockedType = unlockedByType.getOrDefault(plot.type(), 0);
                ProfileGardenData.PlotCost nextCost = unlocked || unlockedType >= costs.size()
                    ? null : costs.get(unlockedType);
                return new Plot(plot.id(), plot.type(), plot.number(), plot.x(), plot.z(), unlocked,
                    nextCost == null ? "" : nextCost.item(), nextCost == null ? 0 : nextCost.amount());
            }).toList();

        JsonObject greenhouse = object(garden, "garden_upgrades");
        List<GreenhouseUpgrade> greenhouseRows = List.of("GROWTH_SPEED", "YIELD", "PLOT_LIMIT").stream()
            .map(id -> {
                int upgradeLevel = integer(greenhouse.get(id));
                ProfileGardenData.Greenhouse definition = catalogue == null ? null : catalogue.greenhouse().get(id);
                int maximum = definition == null ? 0 : definition.maximum();
                Map<String, Integer> paid = definition == null || upgradeLevel <= 0 ? Map.of()
                    : definition.costs().get(Math.min(upgradeLevel, maximum) - 1);
                Map<String, Integer> total = definition == null ? Map.of() : definition.costs().getLast();
                int reward = switch (id) {
                    case "GROWTH_SPEED" -> 5 * upgradeLevel + (upgradeLevel >= 9 ? 5 : 0);
                    case "YIELD" -> 2 * upgradeLevel + (upgradeLevel >= 9 ? 2 : 0);
                    default -> upgradeLevel;
                };
                return new GreenhouseUpgrade(id, definition == null ? title(id) : definition.name(),
                    upgradeLevel, maximum, reward, Map.copyOf(paid), Map.copyOf(total));
            }).toList();

        // ported from SkyBlockPv (modified MIT): api/data/profile/SkyBlockProfile.kt, data/api/skills/farming/ChipsData.kt, screens/windowed/tabs/farming/MutationScreen.kt, FarmingScreen.kt
        // Portions of this code are from the SkyBlockPv mod.
        Set<String> analyzedIds = stringSet(player.get("analyzed_greenhouse_crops"));
        Set<String> discoveredIds = stringSet(player.get("discovered_greenhouse_crops"));
        Set<String> mutationIds = new HashSet<>(analyzedIds);
        mutationIds.addAll(discoveredIds);
        if (catalogue != null) mutationIds.addAll(catalogue.mutations().keySet());
        List<Mutation> allMutations = new ArrayList<>();
        for (String id : mutationIds) {
            ProfileGardenData.Mutation definition = catalogue == null ? null : catalogue.mutations().get(id);
            boolean analyzable = definition == null || definition.analyzable();
            allMutations.add(new Mutation(id, definition == null ? title(id) : definition.name(),
                definition == null ? "UNKNOWN" : definition.rarity(), discoveredIds.contains(id),
                analyzedIds.contains(id) || !analyzable, analyzable, definition == null));
        }
        int mutationsDiscovered = (int) allMutations.stream().filter(Mutation::discovered).count();
        int mutationsAnalyzed = (int) allMutations.stream().filter(value -> value.analyzed() && value.analyzable()).count();
        List<Mutation> mutations = allMutations.stream()
            .filter(value -> mutationMatches(value, mutationFilter, mutationRarity, mutationSearch))
            .sorted(mutationComparator(mutationSort)).toList();

        JsonObject playerData = object(member, "player_data");
        JsonObject rawChips = object(playerData, "garden_chips");
        Set<String> chipIds = new java.util.LinkedHashSet<>(CHIPS);
        chipIds.addAll(rawChips.keySet());
        int chipMaximum = catalogue == null ? 0 : catalogue.chipCosts().size();
        long chipSowdustMaximum = catalogue == null || catalogue.chipCosts().isEmpty()
            ? 0 : catalogue.chipCosts().getLast();
        List<Chip> chips = chipIds.stream().map(id -> {
            int chipLevel = integer(rawChips.get(id));
            long sowdust = catalogue == null || chipLevel <= 0 ? 0
                : catalogue.chipCosts().get(Math.min(chipLevel, chipMaximum) - 1);
            return new Chip(id, title(id), chipLevel, chipMaximum, sowdust, chipSowdustMaximum,
                !CHIPS.contains(id));
        }).toList();

        JsonObject medals = object(contests, "medals_inv");
        JsonObject perks = object(contests, "perks");
        JsonObject contestRows = object(contests, "contests");
        int claimed = 0;
        for (JsonElement value : contestRows.asMap().values())
            if (value.isJsonObject() && bool(value.getAsJsonObject().get("claimed_rewards"))) claimed++;

        Progress offerProgress = progression(integer(commission.get("total_completed")),
            catalogue == null ? List.of() : catalogue.offerMilestones());
        Progress uniqueVisitorProgress = progression(integer(commission.get("unique_npcs_served")),
            catalogue == null ? List.of() : catalogue.uniqueVisitorMilestones());
        int farmingCapLevel = integer(perks.get("farming_level_cap"));
        int fortuneLevel = integer(perks.get("double_drops"));
        CostUpgrade farmingCap = costUpgrade("Farming Level Cap", farmingCapLevel,
            catalogue == null ? List.of() : catalogue.farmingCapCosts());
        CostUpgrade farmingFortune = costUpgrade("Extra Farming Fortune", fortuneLevel,
            catalogue == null ? List.of() : catalogue.farmingFortuneCosts());
        String selectedBarnSkin = string(garden.get("selected_barn_skin"), "");
        Set<String> unlockedSkinIds = rawStringSet(garden.get("unlocked_barn_skins"));
        Set<String> skinIds = new java.util.LinkedHashSet<>();
        if (catalogue != null) skinIds.addAll(catalogue.barnSkins().keySet());
        skinIds.addAll(unlockedSkinIds);
        if (!selectedBarnSkin.isBlank()) skinIds.add(selectedBarnSkin);
        List<BarnSkin> barnSkins = skinIds.stream().map(id -> {
            ProfileGardenData.BarnSkin definition = catalogue == null ? null : catalogue.barnSkins().get(id);
            return new BarnSkin(id, definition == null ? title(id) : definition.name(),
                id.equals(selectedBarnSkin), unlockedSkinIds.contains(id), definition == null);
        }).toList();

        return new Result(true, experience, level, level >= thresholds.size() ? 0 : experience - start,
            level >= thresholds.size() ? 0 : next - start, thresholds.size(), thresholds.getLast(),
            integer(player.get("copper")),
            integer(player.get("larva_consumed")), arraySize(garden, "unlocked_plots_ids"),
            string(garden.get("selected_barn_skin"), "Unknown"), arraySize(garden, "unlocked_barn_skins"),
            catalogue == null ? 0 : catalogue.maxLarva(), barnSkins,
            List.copyOf(crops), resources.asMap().values().stream().mapToLong(ProfileGardenCalculator::whole).sum(),
            integer(commission.get("total_completed")), integer(commission.get("unique_npcs_served")),
            offerProgress, uniqueVisitorProgress, List.copyOf(visitors), contestRows.size(), claimed,
            integer(medals.get("bronze")),
            integer(medals.get("silver")), integer(medals.get("gold")),
            farmingCapLevel, fortuneLevel, farmingCap, farmingFortune,
            bool(perks.get("personal_bests")), decimal(composter.get("organic_matter")),
            decimal(composter.get("fuel_units")), decimal(composter.get("compost_units")),
            integer(composter.get("compost_items")), integer(composter.get("conversion_ticks")),
            whole(composter.get("last_save")), composterRows, plotRows,
            arraySize(garden, "greenhouse_slots"), greenhouseRows,
            whole(object(member, "player_stats").get("glowing_mushrooms_broken")),
            mutationsDiscovered, mutationsAnalyzed, allMutations.size(), mutations, chips, response.cached());
    }

    private static Crop crop(String id, JsonObject resources, JsonObject upgrades, JsonObject personalBests,
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
        int unlockLevel = catalogue == null ? 0 : catalogue.cropRequirements().getOrDefault(id, 0);
        int personalBest = integer(personalBests.get(id));
        int personalBestTarget = catalogue == null ? 0 : catalogue.personalBests().getOrDefault(id, 0);
        return new Crop(id, cropName(id), collected, upgrade, milestone, milestones.size(),
            milestone >= milestones.size() ? 0 : collected - start,
            milestone >= milestones.size() ? 0 : next - start,
            milestones.isEmpty() ? 0 : milestones.getLast(), copperPaid, copperTotal,
            unlockLevel, personalBest, personalBestTarget, unknown);
    }

    private static Progress progression(long amount, List<Long> thresholds) {
        int level = 0;
        while (level < thresholds.size() && amount >= thresholds.get(level)) level++;
        long start = level == 0 ? 0 : thresholds.get(level - 1);
        long next = level >= thresholds.size() ? 0 : thresholds.get(level);
        return new Progress(level, thresholds.size(), level >= thresholds.size() ? 0 : amount - start,
            level >= thresholds.size() ? 0 : next - start, thresholds.isEmpty() ? 0 : thresholds.getLast());
    }

    private static CostUpgrade costUpgrade(String name, int level, List<Map<String, Integer>> costs) {
        Map<String, Integer> paid = level <= 0 || costs.isEmpty() ? Map.of()
            : costs.get(Math.min(level, costs.size()) - 1);
        return new CostUpgrade(name, level, costs.size(), Map.copyOf(paid),
            costs.isEmpty() ? Map.of() : costs.getLast());
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

    private static boolean mutationMatches(Mutation value, String filter, String rarity, String search) {
        String mode = filter == null ? "ALL" : filter.toUpperCase(Locale.ROOT);
        if (mode.equals("DISCOVERED") && !value.discovered()
            || mode.equals("ANALYZED") && !value.analyzed()
            || (mode.equals("PENDING") || mode.equals("DISCOVERED_NOT_ANALYZED"))
                && (!value.discovered() || value.analyzed())
            || mode.equals("UNDISCOVERED") && value.discovered()) return false;
        String wantedRarity = rarity == null ? "ALL" : rarity.toUpperCase(Locale.ROOT);
        if (!wantedRarity.equals("ALL") && !value.rarity().equalsIgnoreCase(wantedRarity)
            && !(wantedRarity.equals("UNKNOWN") && value.unknown())) return false;
        String query = search == null ? "" : search.strip().toLowerCase(Locale.ROOT);
        return query.isEmpty() || value.name().toLowerCase(Locale.ROOT).contains(query)
            || value.id().toLowerCase(Locale.ROOT).contains(query);
    }

    private static Comparator<Mutation> mutationComparator(String sort) {
        Comparator<Mutation> rarity = Comparator.<Mutation>comparingInt(value -> rarityOrder(value.rarity()))
            .reversed().thenComparing(Mutation::name);
        return switch (sort == null ? "" : sort.toUpperCase(Locale.ROOT)) {
            case "NAME" -> Comparator.comparing(Mutation::name);
            case "STATUS" -> Comparator.<Mutation>comparingInt(value -> value.analyzed() ? 2
                : value.discovered() ? 1 : 0).thenComparing(rarity);
            case "RARITY" -> rarity;
            default -> rarity;
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

    private static List<Long> cumulativeList(int[] steps) {
        List<Long> values = new ArrayList<>();
        long total = 0;
        for (int step : steps) {
            total += step;
            values.add(total);
        }
        return List.copyOf(values);
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

    private static Set<String> stringSet(JsonElement value) {
        Set<String> values = new HashSet<>();
        if (value == null || !value.isJsonArray()) return values;
        for (JsonElement element : value.getAsJsonArray()) {
            try { values.add(element.getAsString().toUpperCase(Locale.ROOT)); }
            catch (RuntimeException ignored) {}
        }
        return values;
    }
    private static Set<String> rawStringSet(JsonElement value) {
        Set<String> values = new java.util.LinkedHashSet<>();
        if (value == null || !value.isJsonArray()) return values;
        for (JsonElement element : value.getAsJsonArray()) {
            try { values.add(element.getAsString()); }
            catch (RuntimeException ignored) {}
        }
        return values;
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
                       int copperPaid, int copperTotal, int unlockLevel, int personalBest,
                       int personalBestTarget, boolean unknown) {}
    public record Visitor(String id, String name, String rarity, int visits, int completed, boolean unknown) {}
    public record Upgrade(String id, String name, int level, int maximum, int copperPaid, int copperTotal) {}
    public record Plot(String id, String type, int number, int x, int z, boolean unlocked,
                       String nextCostItem, int nextCostAmount) {}
    public record GreenhouseUpgrade(String id, String name, int level, int maximum, int reward,
                                    Map<String, Integer> paid, Map<String, Integer> total) {}
    public record Mutation(String id, String name, String rarity, boolean discovered, boolean analyzed,
                           boolean analyzable, boolean unknown) {}
    public record Chip(String id, String name, int level, int maximum, long sowdustPaid,
                       long sowdustMaximum, boolean unknown) {}
    public record BarnSkin(String id, String name, boolean selected, boolean unlocked, boolean unknown) {}
    public record Progress(int level, int maximum, long progress, long required, long totalRequired) {}
    public record CostUpgrade(String name, int level, int maximum, Map<String, Integer> paid,
                              Map<String, Integer> total) {}
    public record Result(boolean available, long gardenXp, int gardenLevel, long levelProgress, long levelRequired,
                         int gardenLevelMaximum, long gardenXpMaximum, int copper, int larvaConsumed,
                         int unlockedPlots, String selectedBarnSkin, int unlockedBarnSkins, int maxLarva,
                         List<BarnSkin> barnSkins, List<Crop> crops, long cropsCollected, int visitorsCompleted,
                         int uniqueVisitors, Progress offerProgress, Progress uniqueVisitorProgress,
                         List<Visitor> visitors, int contests, int claimedContests,
                         int bronzeMedals, int silverMedals, int goldMedals, int farmingCapUpgrades,
                         int doubleDropUpgrades, CostUpgrade farmingCap, CostUpgrade farmingFortune,
                         boolean personalBests, double organicMatter, double fuel,
                         double compostUnits, int compostItems, int conversionTicks, long composterLastSave,
                         List<Upgrade> composterUpgrades, List<Plot> plots,
                         int greenhouseSlots, List<GreenhouseUpgrade> greenhouseUpgrades,
                         long glowingMushrooms, int mutationsDiscovered, int mutationsAnalyzed,
                         int mutationTotal, List<Mutation> mutations, List<Chip> chips,
                         boolean cached) {}
}
