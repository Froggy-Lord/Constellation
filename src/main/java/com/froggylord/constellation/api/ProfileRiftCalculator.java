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

// ported from SkyBlockPv (modified MIT): data/api/RiftData.kt, data/repo/RiftCodecs.kt, screens/windowed/tabs/rift/MainRiftScreen.kt
// Portions of this code are from the SkyBlockPv mod.
public final class ProfileRiftCalculator {
    private static final List<String> EYES = List.of("wizard_tower", "fisherman_hut", "dreadfarm", "plaza",
        "colosseum", "castle", "mountaintop");
    private static final List<String> CATS = List.of("first", "second", "third", "fourth", "fifth", "sixth",
        "seventh", "eight", "ninth");
    private static final List<TrophyDefinition> TROPHIES = List.of(
        new TrophyDefinition("wyldly_supreme", "Supreme Timecharm"),
        new TrophyDefinition("chicken_n_egg", "Chicken N Egg Timecharm"),
        new TrophyDefinition("mirrored", "mrahcemiT esrevrorriM"),
        new TrophyDefinition("citizen", "SkyBlock Citizen Timecharm"),
        new TrophyDefinition("lazy_living", "Living Timecharm"),
        new TrophyDefinition("slime", "Globulate Timecharm"),
        new TrophyDefinition("vampiric", "Vampiric Timecharm"),
        new TrophyDefinition("mountain", "Celestial Timecharm"));

    private ProfileRiftCalculator() {}

    public static Result calculate(JsonObject member, String trophySort, boolean hideMissingTrophies) {
        JsonObject rift = object(member, "rift");
        JsonObject stats = object(object(member, "player_stats"), "rift");
        JsonObject currencies = object(member, "currencies");
        Set<String> eyes = strings(path(rift, "wither_cage.killed_eyes"));
        Set<String> cats = strings(path(rift, "dead_cats.found_cats"));
        Set<String> souls = strings(path(rift, "enigma.found_souls"));
        JsonArray secured = array(path(rift, "gallery.secured_trophies"));
        List<Trophy> trophies = new ArrayList<>();
        Set<String> known = new HashSet<>();
        for (TrophyDefinition definition : TROPHIES) {
            known.add(definition.id());
            JsonObject found = findTrophy(secured, definition.id());
            trophies.add(new Trophy(definition.id(), definition.name(), !found.isEmpty(),
                whole(found.get("timestamp")), integer(found.get("visits")), false));
        }
        for (JsonElement value : secured) {
            if (!value.isJsonObject()) continue;
            JsonObject found = value.getAsJsonObject();
            String id = string(found.get("type"), "");
            if (id.isBlank() || known.contains(id)) continue;
            trophies.add(new Trophy(id, title(id), true, whole(found.get("timestamp")),
                integer(found.get("visits")), true));
        }
        if (hideMissingTrophies) trophies.removeIf(value -> !value.unlocked());
        trophies.sort(trophyComparator(trophySort));

        return new Result(!rift.isEmpty(), whole(path(currencies, "motes")),
            whole(stats.get("lifetime_motes_earned")), integer(stats.get("visits")),
            integer(path(rift, "village_plaza.lonely.seconds_sitting")),
            Set.copyOf(souls), 52, ordered(EYES, eyes), EYES.size(), ordered(CATS, cats), CATS.size(),
            integer(path(rift, "castle.grubber_stacks")), List.copyOf(trophies),
            (int) trophies.stream().filter(Trophy::unlocked).count());
    }

    private static List<Collectible> ordered(List<String> canonical, Set<String> found) {
        List<Collectible> rows = new ArrayList<>();
        Set<String> known = new HashSet<>(canonical);
        for (String id : canonical) rows.add(new Collectible(id, title(id), found.contains(id), false));
        for (String id : found)
            if (!known.contains(id)) rows.add(new Collectible(id, title(id), true, true));
        return List.copyOf(rows);
    }

    private static JsonObject findTrophy(JsonArray values, String id) {
        for (JsonElement value : values)
            if (value.isJsonObject() && id.equals(string(value.getAsJsonObject().get("type"), "")))
                return value.getAsJsonObject();
        return new JsonObject();
    }

    private static Comparator<Trophy> trophyComparator(String sort) {
        Comparator<Trophy> canonical = Comparator.comparingInt(value -> {
            for (int i = 0; i < TROPHIES.size(); i++) if (TROPHIES.get(i).id().equals(value.id())) return i;
            return Integer.MAX_VALUE;
        });
        return switch (sort == null ? "" : sort.toUpperCase(Locale.ROOT)) {
            case "STATUS" -> Comparator.comparing(Trophy::unlocked).reversed().thenComparing(Trophy::name);
            case "VISITS" -> Comparator.comparingInt(Trophy::visits).reversed().thenComparing(Trophy::name);
            case "DATE" -> Comparator.comparingLong(Trophy::timestamp).reversed().thenComparing(Trophy::name);
            case "NAME" -> Comparator.comparing(Trophy::name);
            default -> canonical.thenComparing(Trophy::name);
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

    private record TrophyDefinition(String id, String name) {}
    public record Collectible(String id, String name, boolean found, boolean unknown) {}
    public record Trophy(String id, String name, boolean unlocked, long timestamp, int visits, boolean unknown) {}
    public record Result(boolean available, long motes, long lifetimeMotes, int visits, int secondsSitting,
                         Set<String> souls, int soulMaximum, List<Collectible> eyes, int eyeMaximum,
                         List<Collectible> cats, int catMaximum, int grubberStacks, List<Trophy> trophies,
                         int trophiesUnlocked) {}
}
