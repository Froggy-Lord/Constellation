package com.froggylord.constellation.api;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

// ported from Skyblocker (LGPL-3.0-or-later): skyblock/profileviewer2/utils/PetLoader.java, model/PetsData.java
// ported from NotEnoughUpdates-REPO (MIT): constants/pets.json
// ported from SkyBlockPv (modified MIT): data/api/skills/PetsData.kt, data/repo/PetCodecs.kt, screens/windowed/tabs/PetScreen.kt
// Portions of this code are from the SkyBlockPv mod.
public final class ProfilePetCalculator {
    private static final int[] RARITY_OFFSETS = {0, 6, 11, 16, 20, 20};
    private static final int[] XP_COSTS = {
        100,110,120,130,145,160,175,190,210,230,250,275,300,330,360,400,440,490,540,600,
        660,730,800,880,960,1050,1150,1260,1380,1510,1650,1800,1960,2130,2310,2500,2700,
        2920,3160,3420,3700,4000,4350,4750,5200,5700,6300,7000,7800,8700,9700,10800,
        12000,13300,14700,16200,17800,19500,21300,23200,25200,27400,29800,32400,35200,
        38200,41400,44800,48400,52200,56200,60400,64800,69400,74200,79200,84700,90700,
        97200,104200,111700,119700,128200,137200,146700,156700,167700,179700,192700,
        206700,221700,237700,254700,272700,291700,311700,333700,357700,383700,411700,
        441700,476700,516700,561700,611700,666700,726700,791700,861700,936700,1016700,
        1101700,1191700,1286700,1386700,1496700,1616700,1746700,1886700
    };
    private static final List<String> RARITIES = List.of("COMMON", "UNCOMMON", "RARE", "EPIC", "LEGENDARY", "MYTHIC");

    private ProfilePetCalculator() {}

    public static Result calculate(JsonObject member, String sort, boolean activeFirst) {
        JsonArray array = array(object(member, "pets_data"), "pets");
        if (array.isEmpty()) array = array(member, "pets");
        boolean available = member.has("pets_data") || member.has("pets");
        List<Pet> pets = new ArrayList<>();
        int active = 0;
        int maxed = 0;
        double totalXp = 0;
        for (JsonElement element : array) {
            if (!element.isJsonObject()) continue;
            JsonObject value = element.getAsJsonObject();
            String type = string(value, "type", "UNKNOWN");
            String tier = string(value, "tier", "COMMON").toUpperCase(Locale.ROOT);
            String held = nullableString(value, "heldItem");
            boolean isActive = bool(value, "active");
            double xp = Math.max(0, number(value.get("exp")));
            Level level = level(type, effectiveRarity(tier, held), xp);
            Pet pet = new Pet(type, tier, effectiveRarity(tier, held), xp, isActive, held,
                nullableString(value, "skin"), integer(value, "candyUsed"), nullableString(value, "uuid"),
                nullableString(value, "uniqueId"), level);
            pets.add(pet);
            if (isActive) active++;
            if (level.maxed()) maxed++;
            totalXp += xp;
        }
        pets.sort(comparator(sort, activeFirst));
        return new Result(available, List.copyOf(pets), pets.size(), active, maxed, totalXp);
    }

    private static Level level(String type, String rarity, double xp) {
        int rarityIndex = Math.max(0, RARITIES.indexOf(rarity));
        int offset = type.equalsIgnoreCase("BINGO") ? 0 : RARITY_OFFSETS[Math.min(rarityIndex, RARITY_OFFSETS.length - 1)];
        boolean dragon = type.equalsIgnoreCase("GOLDEN_DRAGON") || type.equalsIgnoreCase("JADE_DRAGON")
            || type.equalsIgnoreCase("ROSE_DRAGON");
        int cap = dragon ? 200 : 100;
        double spent = 0;
        int level = 1;
        while (level < cap) {
            double cost;
            if (level < 100) cost = XP_COSTS[offset + level - 1];
            else if (level == 100) cost = XP_COSTS[offset + 99];
            else if (level == 101) cost = 0;
            else if (level == 102) cost = 5555;
            else cost = 1_886_700;
            if (xp < spent + cost) {
                double into = Math.max(0, xp - spent);
                double progress = cost <= 0 ? 1 : into / cost;
                return new Level(level + progress, level, cap, progress, into, cost,
                    Math.max(0, totalCost(offset, cap) - xp), xp / Math.max(1, totalCost(offset, cap)), false, 0);
            }
            spent += cost;
            level++;
        }
        double required = totalCost(offset, cap);
        return new Level(cap, cap, cap, 1, 0, 0, 0, 1, true, Math.max(0, xp - required));
    }

    private static double totalCost(int offset, int cap) {
        double total = 0;
        for (int level = 1; level < cap; level++) {
            if (level < 100) total += XP_COSTS[offset + level - 1];
            else if (level == 100) total += XP_COSTS[offset + 99];
            else if (level == 101) total += 0;
            else if (level == 102) total += 5555;
            else total += 1_886_700;
        }
        return total;
    }

    private static String effectiveRarity(String tier, String held) {
        int index = RARITIES.indexOf(tier);
        if ("PET_ITEM_TIER_BOOST".equals(held) && index >= 0 && index < RARITIES.indexOf("LEGENDARY"))
            return RARITIES.get(index + 1);
        return index < 0 ? "COMMON" : tier;
    }

    private static Comparator<Pet> comparator(String sort, boolean activeFirst) {
        Comparator<Pet> active = Comparator.comparing(Pet::active).reversed();
        Comparator<Pet> selected = switch (sort == null ? "" : sort.toUpperCase(Locale.ROOT)) {
            case "LEVEL" -> Comparator.comparingDouble((Pet p) -> p.level().level()).reversed()
                .thenComparing(Comparator.comparingDouble(Pet::xp).reversed());
            case "XP" -> Comparator.comparingDouble(Pet::xp).reversed();
            case "NAME" -> Comparator.comparing(Pet::type);
            default -> Comparator.comparingInt((Pet p) -> rarityIndex(p.effectiveTier())).reversed()
                .thenComparing(Comparator.comparingDouble((Pet p) -> p.level().level()).reversed())
                .thenComparing(Pet::type);
        };
        return activeFirst ? active.thenComparing(selected) : selected;
    }

    private static int rarityIndex(String rarity) {
        return Math.max(0, RARITIES.indexOf(rarity));
    }

    private static JsonObject object(JsonObject root, String key) {
        JsonElement value = root.get(key);
        return value != null && value.isJsonObject() ? value.getAsJsonObject() : new JsonObject();
    }

    private static JsonArray array(JsonObject root, String key) {
        JsonElement value = root.get(key);
        return value != null && value.isJsonArray() ? value.getAsJsonArray() : new JsonArray();
    }

    private static String string(JsonObject root, String key, String fallback) {
        try { return root.has(key) ? root.get(key).getAsString() : fallback; }
        catch (RuntimeException ignored) { return fallback; }
    }

    private static String nullableString(JsonObject root, String key) {
        try { return root.has(key) && !root.get(key).isJsonNull() ? root.get(key).getAsString() : null; }
        catch (RuntimeException ignored) { return null; }
    }

    private static boolean bool(JsonObject root, String key) {
        try { return root.has(key) && root.get(key).getAsBoolean(); }
        catch (RuntimeException ignored) { return false; }
    }

    private static int integer(JsonObject root, String key) {
        try { return root.has(key) ? root.get(key).getAsInt() : 0; }
        catch (RuntimeException ignored) { return 0; }
    }

    private static double number(JsonElement value) {
        try { return value != null && value.isJsonPrimitive() ? value.getAsDouble() : 0; }
        catch (RuntimeException ignored) { return 0; }
    }

    public record Result(boolean available, List<Pet> pets, int count, int active, int maxed, double totalXp) {}
    public record Pet(String type, String tier, String effectiveTier, double xp, boolean active, String heldItem,
                      String skin, int candyUsed, String uuid, String uniqueId, Level level) {}
    public record Level(double level, int whole, int cap, double progress, double into, double needed,
                        double remainingToMax, double progressToMax, boolean maxed, double overflow) {}
}
