package com.froggylord.constellation.api;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

// ported from SkyBlockPv (modified MIT): api/data/profile/SkyBlockProfile.kt, data/api/skills/combat/MobData.kt, screens/windowed/tabs/combat/MobScreen.kt
// Portions of this code are from the SkyBlockPv mod.
public final class ProfileMobCalculator {
    private ProfileMobCalculator() {}

    public static Result calculate(JsonObject member, boolean combineVariants, String filter, String sort,
                                   String search, long minimumKills, long minimumDeaths) {
        JsonObject stats = object(member, "player_stats");
        JsonObject rawKills = object(stats, "kills");
        JsonObject rawDeaths = object(stats, "deaths");
        if (rawKills.isEmpty() && rawDeaths.isEmpty())
            return new Result(false, 0, 0, 0, 0, List.of());

        Set<String> ids = new HashSet<>(rawKills.keySet());
        ids.addAll(rawDeaths.keySet());
        Map<String, long[]> grouped = new LinkedHashMap<>();
        for (String id : ids) {
            String key = combineVariants ? combinedId(id) : id;
            long[] values = grouped.computeIfAbsent(key, ignored -> new long[2]);
            values[0] += whole(rawKills.get(id));
            values[1] += whole(rawDeaths.get(id));
        }

        List<Mob> all = new ArrayList<>();
        for (var entry : grouped.entrySet())
            all.add(new Mob(entry.getKey(), name(entry.getKey()), entry.getValue()[0], entry.getValue()[1]));
        long totalKills = all.stream().mapToLong(Mob::kills).sum();
        long totalDeaths = all.stream().mapToLong(Mob::deaths).sum();
        long mobsKilled = all.stream().filter(value -> value.kills() > 0).count();
        long causesOfDeath = all.stream().filter(value -> value.deaths() > 0).count();

        String query = search == null ? "" : search.strip().toLowerCase(Locale.ROOT);
        String mode = filter == null ? "ALL" : filter.toUpperCase(Locale.ROOT);
        List<Mob> rows = all.stream().filter(value -> {
            if (value.kills() < Math.max(0, minimumKills) || value.deaths() < Math.max(0, minimumDeaths))
                return false;
            if (!query.isEmpty() && !value.id().toLowerCase(Locale.ROOT).contains(query)
                && !value.name().toLowerCase(Locale.ROOT).contains(query)) return false;
            return switch (mode) {
                case "KILLS" -> value.kills() > 0;
                case "DEATHS" -> value.deaths() > 0;
                case "BOTH" -> value.kills() > 0 && value.deaths() > 0;
                case "KILLED_NOT_DIED" -> value.kills() > 0 && value.deaths() == 0;
                case "DIED_NOT_KILLED" -> value.deaths() > 0 && value.kills() == 0;
                default -> true;
            };
        }).sorted(comparator(sort)).toList();
        return new Result(true, totalKills, totalDeaths, mobsKilled, causesOfDeath, rows);
    }

    private static Comparator<Mob> comparator(String sort) {
        Comparator<Mob> name = Comparator.comparing(Mob::name);
        return switch (sort == null ? "" : sort.toUpperCase(Locale.ROOT)) {
            case "NAME" -> name;
            case "DEATHS" -> Comparator.comparingLong(Mob::deaths).reversed()
                .thenComparing(Comparator.comparingLong(Mob::kills).reversed()).thenComparing(name);
            case "KD" -> Comparator.comparingDouble(ProfileMobCalculator::ratio).reversed()
                .thenComparing(Comparator.comparingLong(Mob::kills).reversed()).thenComparing(name);
            case "TOTAL" -> Comparator.<Mob>comparingLong(value -> value.kills() + value.deaths()).reversed()
                .thenComparing(name);
            default -> Comparator.comparingLong(Mob::kills).reversed()
                .thenComparing(Comparator.comparingLong(Mob::deaths).reversed()).thenComparing(name);
        };
    }

    private static double ratio(Mob mob) {
        return mob.deaths() == 0 ? mob.kills() > 0 ? Double.POSITIVE_INFINITY : 0
            : mob.kills() / (double) mob.deaths();
    }

    private static String combinedId(String id) {
        int separator = id.lastIndexOf('_');
        if (separator <= 0 || separator == id.length() - 1) return id;
        String suffix = id.substring(separator + 1);
        for (int i = 0; i < suffix.length(); i++)
            if (!Character.isDigit(suffix.charAt(i))) return id;
        return id.substring(0, separator);
    }

    private static String name(String id) {
        StringBuilder out = new StringBuilder();
        for (String part : id.replace('_', ' ').toLowerCase(Locale.ROOT).split(" ")) {
            if (!out.isEmpty()) out.append(' ');
            if (!part.isEmpty()) out.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return out.toString();
    }

    private static JsonObject object(JsonObject root, String key) {
        JsonElement value = root == null ? null : root.get(key);
        return value != null && value.isJsonObject() ? value.getAsJsonObject() : new JsonObject();
    }

    private static long whole(JsonElement value) {
        try { return value == null ? 0 : value.getAsLong(); }
        catch (RuntimeException ignored) { return 0; }
    }

    public record Mob(String id, String name, long kills, long deaths) {}
    public record Result(boolean available, long totalKills, long totalDeaths, long mobsKilled,
                         long causesOfDeath, List<Mob> mobs) {}
}
