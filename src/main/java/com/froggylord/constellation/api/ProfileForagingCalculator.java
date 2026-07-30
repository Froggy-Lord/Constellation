package com.froggylord.constellation.api;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

// ported from SkyBlockPv (modified MIT): data/api/skills/ForagingCore.kt, SkillTree.kt, data/api/AttributesData.kt, screens/windowed/tabs/foraging/MainForagingScreen.kt, AttributeScreen.kt, ForagingSkillTreeScreen.kt
// Portions of this code are from the SkyBlockPv mod.
public final class ProfileForagingCalculator {
    private static final long[] FOREST_XP = {0, 3_000, 12_000, 37_000, 97_000, 197_000, 347_000, 557_000, 847_000, 1_247_000};

    private ProfileForagingCalculator() {}

    public static Result calculate(JsonObject member, String attributeSort, boolean hideZeroAttributes,
                                   boolean hideEmptyShards) {
        JsonObject foraging = object(member, "foraging");
        JsonObject core = object(member, "foraging_core");
        JsonObject skillTree = object(member, "skill_tree");
        JsonObject perks = object(object(member, "player_data"), "perks");
        JsonObject gifts = object(foraging, "tree_gifts");
        JsonObject giftTiers = object(gifts, "milestone_tier_claimed");
        JsonObject bests = object(object(foraging, "starlyn"), "personal_bests");

        int selectedTree = Math.clamp(integer(path(skillTree, "selected_skill_tree_slot.foraging")), 1, 5);
        String suffix = selectedTree == 1 ? "" : "_" + selectedTree;
        JsonObject nodes = object(object(skillTree, "nodes"), "foraging" + suffix);
        int unlocked = 0;
        int nodeLevels = 0;
        int disabled = 0;
        List<Node> nodeRows = new ArrayList<>();
        for (var entry : nodes.entrySet()) {
            if (entry.getKey().startsWith("toggle_")) {
                if (!bool(entry.getValue())) disabled++;
                continue;
            }
            int level = integer(entry.getValue());
            if (level > 0) {
                unlocked++;
                nodeLevels += level;
            }
            nodeRows.add(new Node(entry.getKey(), title(entry.getKey()), level));
        }
        nodeRows.sort(Comparator.comparing(Node::name));
        long forestXp = whole(path(skillTree, "experience.foraging"));
        int forestLevel = 1;
        for (int i = 0; i < FOREST_XP.length; i++) if (forestXp >= FOREST_XP[i]) forestLevel = i + 1;
        forestLevel = Math.min(10, forestLevel);
        long forestStart = FOREST_XP[forestLevel - 1];
        long forestNext = forestLevel >= 10 ? 0 : FOREST_XP[forestLevel];

        JsonObject stacks = object(object(member, "attributes"), "stacks");
        List<Attribute> attributes = new ArrayList<>();
        for (var entry : stacks.entrySet()) {
            Attribute attribute = new Attribute(entry.getKey(), title(entry.getKey()), integer(entry.getValue()));
            if (!hideZeroAttributes || attribute.syphoned() != 0) attributes.add(attribute);
        }
        attributes.sort(attributeComparator(attributeSort));

        List<Shard> shards = new ArrayList<>();
        for (JsonElement value : array(path(member, "shards.owned"))) {
            if (!value.isJsonObject()) continue;
            JsonObject shard = value.getAsJsonObject();
            Shard row = new Shard(string(shard.get("type"), "unknown"),
                title(string(shard.get("type"), "unknown")), integer(shard.get("amount_owned")),
                whole(shard.get("captured")));
            if (!hideEmptyShards || row.owned() > 0) shards.add(row);
        }
        shards.sort(Comparator.comparingInt(Shard::owned).reversed().thenComparing(Shard::name));

        List<Trap> traps = new ArrayList<>();
        for (JsonElement value : array(path(member, "shards.traps.active_traps"))) {
            if (!value.isJsonObject()) continue;
            JsonObject trap = value.getAsJsonObject();
            traps.add(new Trap(string(trap.get("trap_item"), "Unknown"), string(trap.get("mode"), "Unknown"),
                string(trap.get("shard"), "None"), string(trap.get("location"), "Unknown"),
                bool(trap.get("captured")), whole(trap.get("capture_time")), whole(trap.get("placed_at")),
                integer(trap.get("hunting_toolkit_index"))));
        }

        int whispers = integer(core.get("forests_whispers"));
        int whispersSpent = integer(core.get("forests_whispers_spent"));
        return new Result(!foraging.isEmpty() || !core.isEmpty() || !skillTree.isEmpty() || !stacks.isEmpty(),
            gift("Fig", gifts, giftTiers, "FIG"), gift("Mangrove", gifts, giftTiers, "MANGROVE"),
            best("Fig", bests, perks, "FIG_LOG", "agatha_fig"), best("Mangrove", bests, perks,
                "MANGROVE_LOG", "agatha_mangrove"),
            whispers, Math.max(0, whispers - whispersSpent), whispersSpent,
            integer(core.get("daily_trees_cut")), integer(core.get("daily_trees_cut_day")),
            strings(core.get("daily_log_cut")).size(), integer(core.get("daily_log_cut_day")),
            integer(core.get("daily_gifts")), strings(foraging.get("fish_family")).size(),
            selectedTree, string(path(skillTree, "selected_ability.foraging" + suffix), "None"),
            forestLevel, forestXp, forestLevel >= 10 ? 0 : forestXp - forestStart,
            forestLevel >= 10 ? 0 : forestNext - forestStart, whole(path(skillTree, "last_reset.foraging")),
            unlocked, nodeLevels, disabled, List.copyOf(nodeRows), List.copyOf(attributes),
            List.copyOf(shards), integer(path(member, "shards.fused")), List.copyOf(traps));
    }

    private static Gift gift(String name, JsonObject gifts, JsonObject tiers, String id) {
        return new Gift(name, integer(gifts.get(id)), integer(tiers.get(id)), 7);
    }

    private static PersonalBest best(String name, JsonObject bests, JsonObject perks, String id, String perk) {
        return new PersonalBest(name, integer(bests.get(id)), 100_000,
            integer(perks.get(perk + "_fortune")), 50, perks.has(perk + "_personal_best"));
    }

    private static Comparator<Attribute> attributeComparator(String sort) {
        return switch (sort == null ? "" : sort.toUpperCase(Locale.ROOT)) {
            case "SYPHONED" -> Comparator.comparingInt(Attribute::syphoned).reversed().thenComparing(Attribute::name);
            default -> Comparator.comparing(Attribute::name);
        };
    }

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
    private static JsonArray array(JsonElement value) {
        return value != null && value.isJsonArray() ? value.getAsJsonArray() : new JsonArray();
    }
    private static Set<String> strings(JsonElement value) {
        Set<String> values = new HashSet<>();
        if (value != null && value.isJsonArray())
            for (JsonElement entry : value.getAsJsonArray())
                if (entry.isJsonPrimitive()) values.add(entry.getAsString());
        return values;
    }
    private static int integer(JsonElement value) {
        try { return value == null ? 0 : value.getAsInt(); } catch (RuntimeException ignored) { return 0; }
    }
    private static long whole(JsonElement value) {
        try { return value == null ? 0 : value.getAsLong(); } catch (RuntimeException ignored) { return 0; }
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

    public record Gift(String name, int total, int claimedTier, int maxTier) {}
    public record PersonalBest(String name, int value, int maximum, int fortuneLevel, int fortuneMaximum,
                               boolean unlocked) {}
    public record Node(String id, String name, int level) {}
    public record Attribute(String id, String name, int syphoned) {}
    public record Shard(String id, String name, int owned, long capturedAt) {}
    public record Trap(String item, String mode, String shard, String location, boolean captured,
                       long captureTime, long placedAt, int toolkitIndex) {}
    public record Result(boolean available, Gift figGifts, Gift mangroveGifts, PersonalBest figBest,
                         PersonalBest mangroveBest, int totalWhispers, int availableWhispers,
                         int spentWhispers, int dailyTrees, int dailyTreesDay, int dailyLogTypes,
                         int dailyLogDay, int dailyGifts, int fishFamily, int selectedTree,
                         String selectedAbility, int forestLevel, long forestXp, long forestProgress,
                         long forestRequired, long lastReset, int unlockedNodes, int nodeLevels,
                         int disabledNodes, List<Node> nodes, List<Attribute> attributes,
                         List<Shard> shards, int fusions, List<Trap> traps) {}
}
