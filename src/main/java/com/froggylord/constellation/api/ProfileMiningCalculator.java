package com.froggylord.constellation.api;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

// ported from SkyBlockPv (modified MIT): data/api/skills/MiningData.kt, data/api/skills/SkillTree.kt, screens/windowed/tabs/mining/MainMiningScreen.kt, GlaciteScreen.kt
// Portions of this code are from the SkyBlockPv mod.
// ported from Skyblocker (LGPL-3.0-or-later): skyblock/profileviewer2/model/MiningCore.java, GlacitePlayerData.java
public final class ProfileMiningCalculator {
    private static final long[] HOTM_XP = {0, 0, 3_000, 12_000, 37_000, 97_000, 197_000, 347_000, 557_000, 847_000, 1_247_000};
    private static final List<String> NUCLEUS = List.of("jade_crystal", "amethyst_crystal", "topaz_crystal", "sapphire_crystal", "amber_crystal");
    private static final List<String> CRYSTALS = List.of("jade_crystal", "amethyst_crystal", "topaz_crystal", "sapphire_crystal",
        "amber_crystal", "ruby_crystal", "jasper_crystal", "opal_crystal", "aquamarine_crystal", "citrine_crystal",
        "peridot_crystal", "onyx_crystal");
    private static final List<String> FOSSILS = List.of("claw", "spine", "clubbed", "ugly", "helix", "footprint", "webbed", "tusk");

    private ProfileMiningCalculator() {}

    public static Result calculate(JsonObject member, String crystalFilter, String crystalSort,
                                   boolean hideNeverFound, boolean hideInactive) {
        JsonObject core = object(member, "mining_core");
        JsonObject skillTree = object(member, "skill_tree");
        JsonObject glacite = object(member, "glacite_player_data");
        Powder mithril = powder(core, "mithril");
        Powder gemstone = powder(core, "gemstone");
        Powder glacitePowder = powder(core, "glacite");

        int selectedSlot = Math.clamp(integer(path(skillTree, "selected_skill_tree_slot.mining")), 1, 5);
        String suffix = selectedSlot == 1 ? "" : "_" + selectedSlot;
        long hotmXp = whole(path(skillTree, "experience.mining"));
        int hotmLevel = level(hotmXp);
        long currentStart = HOTM_XP[hotmLevel];
        long next = hotmLevel >= 10 ? 0 : HOTM_XP[hotmLevel + 1];
        JsonObject nodes = object(object(skillTree, "nodes"), "mining" + suffix);
        int unlockedNodes = 0;
        int nodeLevels = 0;
        int disabledNodes = 0;
        for (var entry : nodes.entrySet()) {
            int value = integer(entry.getValue());
            if (entry.getKey().startsWith("toggle_")) {
                if (value == 0) disabledNodes++;
            } else if (value > 0) {
                unlockedNodes++;
                nodeLevels += value;
            }
        }
        String selectedAbility = string(path(skillTree, "selected_ability.mining" + suffix), "None");

        List<Crystal> crystals = new ArrayList<>();
        JsonObject crystalData = object(core, "crystals");
        Set<String> known = new HashSet<>(CRYSTALS);
        for (String id : CRYSTALS) crystals.add(crystal(id, object(crystalData, id), false));
        for (var entry : crystalData.entrySet())
            if (!known.contains(entry.getKey()) && entry.getValue().isJsonObject())
                crystals.add(crystal(entry.getKey(), entry.getValue().getAsJsonObject(), true));
        String filter = crystalFilter == null ? "ALL" : crystalFilter.toUpperCase(Locale.ROOT);
        crystals.removeIf(value -> hideNeverFound && value.totalFound() == 0
            || hideInactive && !value.active()
            || filter.equals("NUCLEUS") && !NUCLEUS.contains(value.id())
            || filter.equals("GLACITE") && !value.id().matches("aquamarine_crystal|citrine_crystal|peridot_crystal|onyx_crystal")
            || filter.equals("OTHER") && (NUCLEUS.contains(value.id())
                || value.id().matches("aquamarine_crystal|citrine_crystal|peridot_crystal|onyx_crystal")));
        crystals.sort(crystalComparator(crystalSort));

        int nucleusRuns = NUCLEUS.stream().map(id -> object(crystalData, id))
            .mapToInt(value -> integer(value.get("total_placed"))).min().orElse(0);
        long oresMined = whole(path(member, "player_stats.pets.milestone.ores_mined"));
        Rock rock = rock(oresMined);
        Set<String> donated = new HashSet<>();
        JsonElement fossils = glacite.get("fossils_donated");
        if (fossils != null && fossils.isJsonArray())
            for (JsonElement fossil : fossils.getAsJsonArray())
                if (fossil.isJsonPrimitive()) donated.add(fossil.getAsString().toLowerCase(Locale.ROOT));
        List<Fossil> fossilRows = FOSSILS.stream().map(id -> new Fossil(id, title(id), donated.contains(id))).toList();
        JsonObject corpses = object(glacite, "corpses_looted");
        List<Corpse> corpseRows = List.of("lapis", "tungsten", "umber", "vanguard").stream()
            .map(id -> new Corpse(id, title(id), integer(corpses.get(id)))).toList();
        int corpseTotal = corpseRows.stream().mapToInt(Corpse::looted).sum();

        return new Result(!core.isEmpty() || !skillTree.isEmpty() || !glacite.isEmpty(), mithril, gemstone,
            glacitePowder, hotmLevel, hotmXp, hotmLevel >= 10 ? 0 : hotmXp - currentStart,
            hotmLevel >= 10 ? 0 : next - currentStart, selectedSlot, selectedAbility, unlockedNodes, nodeLevels,
            disabledNodes, List.copyOf(crystals), nucleusRuns, oresMined, rock,
            integer(glacite.get("mineshafts_entered")), whole(glacite.get("fossil_dust")),
            fossilRows, donated.size(), corpseRows, corpseTotal);
    }

    private static Powder powder(JsonObject core, String type) {
        long available = whole(core.get("powder_" + type));
        long spent = whole(core.get("powder_spent_" + type));
        return new Powder(title(type), available, spent, available + spent);
    }

    private static Crystal crystal(String id, JsonObject value, boolean unknown) {
        String state = string(value.get("state"), "NOT_FOUND").toUpperCase(Locale.ROOT);
        int found = integer(value.get("total_found"));
        int placed = integer(value.get("total_placed"));
        return new Crystal(id, title(id.replace("_crystal", "")), state, found, placed,
            state.equals("FOUND") || state.equals("PLACED"), unknown);
    }

    private static Comparator<Crystal> crystalComparator(String sort) {
        return switch (sort == null ? "" : sort.toUpperCase(Locale.ROOT)) {
            case "FOUND" -> Comparator.comparingInt(Crystal::totalFound).reversed().thenComparing(Crystal::name);
            case "PLACED" -> Comparator.comparingInt(Crystal::totalPlaced).reversed().thenComparing(Crystal::name);
            case "STATE" -> Comparator.comparing(Crystal::state).thenComparing(Crystal::name);
            default -> Comparator.<Crystal>comparingInt(value -> crystalOrder(value.id())).thenComparing(Crystal::name);
        };
    }

    private static int crystalOrder(String id) {
        int index = CRYSTALS.indexOf(id);
        return index < 0 ? Integer.MAX_VALUE : index;
    }

    private static int level(long xp) {
        int level = 0;
        for (int i = 1; i < HOTM_XP.length; i++) if (xp >= HOTM_XP[i]) level = i;
        return level;
    }

    private static Rock rock(long ores) {
        if (ores >= 250_000) return new Rock("Legendary", 250_000, 0);
        if (ores >= 100_000) return new Rock("Epic", 100_000, 250_000 - ores);
        if (ores >= 20_000) return new Rock("Rare", 20_000, 100_000 - ores);
        if (ores >= 7_500) return new Rock("Uncommon", 7_500, 20_000 - ores);
        if (ores >= 2_500) return new Rock("Common", 2_500, 7_500 - ores);
        return new Rock("None", 0, 2_500 - ores);
    }

    private static JsonElement path(JsonObject root, String path) {
        JsonElement current = root;
        for (String key : path.split("\\.")) {
            if (current == null || !current.isJsonObject()) return null;
            current = current.getAsJsonObject().get(key);
        }
        return current;
    }

    private static JsonObject object(JsonObject root, String key) {
        JsonElement value = root == null ? null : root.get(key);
        return value != null && value.isJsonObject() ? value.getAsJsonObject() : new JsonObject();
    }

    private static int integer(JsonElement value) {
        try { return value == null ? 0 : value.getAsInt(); }
        catch (RuntimeException ignored) { return 0; }
    }

    private static long whole(JsonElement value) {
        try { return value == null ? 0 : value.getAsLong(); }
        catch (RuntimeException ignored) { return 0; }
    }

    private static String string(JsonElement value, String fallback) {
        try { return value == null ? fallback : value.getAsString(); }
        catch (RuntimeException ignored) { return fallback; }
    }

    private static String title(String value) {
        StringBuilder out = new StringBuilder();
        for (String part : value.replace('_', ' ').toLowerCase(Locale.ROOT).split(" ")) {
            if (!out.isEmpty()) out.append(' ');
            if (!part.isEmpty()) out.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return out.toString();
    }

    public record Powder(String name, long available, long spent, long total) {}
    public record Crystal(String id, String name, String state, int totalFound, int totalPlaced,
                          boolean active, boolean unknown) {}
    public record Rock(String rarity, long threshold, long remaining) {}
    public record Fossil(String id, String name, boolean donated) {}
    public record Corpse(String id, String name, int looted) {}
    public record Result(boolean available, Powder mithril, Powder gemstone, Powder glacite,
                         int hotmLevel, long hotmXp, long hotmProgress, long hotmRequired,
                         int selectedTree, String selectedAbility, int unlockedNodes, int nodeLevels,
                         int disabledNodes, List<Crystal> crystals, int nucleusRuns, long oresMined,
                         Rock rock, int mineshaftsEntered, long fossilDust, List<Fossil> fossils,
                         int fossilsDonated, List<Corpse> corpses, int corpsesLooted) {}
}
