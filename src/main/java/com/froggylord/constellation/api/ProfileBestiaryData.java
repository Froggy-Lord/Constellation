package com.froggylord.constellation.api;

import com.froggylord.constellation.ConstellationClient;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

// ported from NotEnoughUpdates-REPO (MIT): constants/bestiary.json
// ported from SkyBlockPv (modified MIT): data/repo/BestiaryCodecs.kt, data/api/skills/combat/MobData.kt, screens/windowed/tabs/combat/BestiaryScreen.kt
// Portions of this code are from the SkyBlockPv mod.
public final class ProfileBestiaryData {
    private static final URI SOURCE = URI.create(
        "https://raw.githubusercontent.com/NotEnoughUpdates/NotEnoughUpdates-REPO/master/constants/bestiary.json");
    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).build();
    private static volatile Catalogue catalogue;
    private static volatile CompletableFuture<Catalogue> loading;

    private ProfileBestiaryData() {}

    public static synchronized CompletableFuture<Catalogue> load(boolean refresh) {
        long ttl = Math.clamp(ConstellationClient.cfg().lyra.profileBestiaryCacheHours, 1, 168) * 3_600_000L;
        if (!refresh && catalogue != null && System.currentTimeMillis() - catalogue.loadedAt() < ttl)
            return CompletableFuture.completedFuture(catalogue);
        if (loading != null && !loading.isDone()) return loading;
        loading = CompletableFuture.supplyAsync(() -> {
            Catalogue cached = readCache();
            if (!refresh && cached != null && System.currentTimeMillis() - cached.loadedAt() < ttl) {
                catalogue = cached;
                return cached;
            }
            try {
                HttpRequest request = HttpRequest.newBuilder(SOURCE).timeout(Duration.ofSeconds(15))
                    .header("User-Agent", "Constellation/0.9/Minecraft-26.2").GET().build();
                HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() != 200) throw new IllegalStateException("Bestiary data request failed (" + response.statusCode() + ").");
                Catalogue loaded = parse(response.body(), System.currentTimeMillis(), false);
                Files.writeString(cachePath(), response.body(), StandardCharsets.UTF_8);
                catalogue = loaded;
                return loaded;
            } catch (Exception error) {
                if (cached != null) {
                    catalogue = cached;
                    return cached;
                }
                throw new RuntimeException("Bestiary catalogue is unavailable.", error);
            }
        });
        return loading;
    }

    public static Result calculate(Catalogue data, JsonObject member, String category, String search,
                                   String sort, boolean hideZero, boolean hideMaxed) {
        JsonObject bestiary = object(member, "bestiary");
        JsonObject kills = object(bestiary, "kills");
        JsonObject deaths = object(bestiary, "deaths");
        String cleanCategory = category == null ? "ALL" : category.trim();
        String cleanSearch = search == null ? "" : search.trim().toLowerCase(Locale.ROOT);
        List<Family> families = new ArrayList<>();
        Set<String> knownIds = new HashSet<>();
        for (Definition definition : data.families()) {
            knownIds.addAll(definition.mobIds());
            long familyKills = definition.mobIds().stream().mapToLong(id -> whole(kills.get(id))).sum();
            long familyDeaths = definition.mobIds().stream().mapToLong(id -> whole(deaths.get(id))).sum();
            int level = 0;
            while (level < definition.thresholds().size() && familyKills >= definition.thresholds().get(level)) level++;
            int max = definition.thresholds().size();
            long next = level >= max ? 0 : definition.thresholds().get(level);
            long required = max == 0 ? 0 : definition.thresholds().get(max - 1);
            Family family = new Family(definition.category(), definition.name(), familyKills, familyDeaths,
                level, max, next, Math.max(0, next - familyKills), required,
                required <= 0 ? 0 : Math.min(1, familyKills / (double) required));
            if (!cleanCategory.equalsIgnoreCase("ALL") && !family.category().equalsIgnoreCase(cleanCategory)) continue;
            if (!cleanSearch.isEmpty() && !family.name().toLowerCase(Locale.ROOT).contains(cleanSearch)) continue;
            if (hideZero && family.kills() == 0) continue;
            if (hideMaxed && family.level() >= family.maxLevel()) continue;
            families.add(family);
        }
        families.sort(comparator(sort));
        int levels = data.families().stream().mapToInt(definition -> {
            long value = definition.mobIds().stream().mapToLong(id -> whole(kills.get(id))).sum();
            int level = 0;
            while (level < definition.thresholds().size() && value >= definition.thresholds().get(level)) level++;
            return level;
        }).sum();
        int maxLevels = data.families().stream().mapToInt(definition -> definition.thresholds().size()).sum();
        long totalKills = kills.entrySet().stream().mapToLong(entry -> whole(entry.getValue())).sum();
        long totalDeaths = deaths.entrySet().stream().mapToLong(entry -> whole(entry.getValue())).sum();
        long unknown = kills.entrySet().stream().filter(entry -> !knownIds.contains(entry.getKey()))
            .mapToLong(entry -> whole(entry.getValue())).sum();
        return new Result(List.copyOf(families), levels, maxLevels, totalKills, totalDeaths, unknown);
    }

    private static Catalogue readCache() {
        try {
            Path cache = cachePath();
            if (!Files.isRegularFile(cache)) return null;
            return parse(Files.readString(cache), Files.getLastModifiedTime(cache).toMillis(), true);
        } catch (Exception ignored) {
            return null;
        }
    }

    static Catalogue validateCatalogue(String body) {
        return parse(body, System.currentTimeMillis(), false);
    }

    private static Path cachePath() {
        return FabricLoader.getInstance().getConfigDir().resolve("constellation-bestiary.json");
    }

    private static Catalogue parse(String body, long loadedAt, boolean cached) {
        JsonObject root = JsonParser.parseString(body).getAsJsonObject();
        JsonObject brackets = object(root, "brackets");
        if (brackets.size() < 8) throw new IllegalArgumentException("Bestiary bracket data is incomplete.");
        List<Definition> families = new ArrayList<>();
        Set<String> categories = new HashSet<>();
        for (var categoryEntry : root.entrySet()) {
            if (categoryEntry.getKey().equals("brackets") || !categoryEntry.getValue().isJsonObject()) continue;
            JsonObject category = categoryEntry.getValue().getAsJsonObject();
            String categoryName = clean(string(category, "name", categoryEntry.getKey()));
            categories.add(categoryName);
            collect(category, categoryName, brackets, families);
            for (var child : category.entrySet()) {
                if (child.getValue().isJsonObject()) collect(child.getValue().getAsJsonObject(), categoryName, brackets, families);
            }
        }
        if (families.size() < 100 || categories.size() < 15)
            throw new IllegalArgumentException("Bestiary catalogue is incomplete.");
        List<String> sortedCategories = categories.stream().sorted().toList();
        return new Catalogue(List.copyOf(families), sortedCategories, loadedAt, cached);
    }

    private static void collect(JsonObject category, String categoryName, JsonObject brackets, List<Definition> out) {
        JsonArray mobs = array(category, "mobs");
        for (JsonElement element : mobs) {
            if (!element.isJsonObject()) continue;
            JsonObject mob = element.getAsJsonObject();
            int bracket = integer(mob, "bracket");
            int cap = integer(mob, "cap");
            JsonArray rawThresholds = array(brackets, Integer.toString(bracket));
            List<Long> thresholds = new ArrayList<>();
            for (JsonElement threshold : rawThresholds) {
                long value = whole(threshold);
                if (value <= cap) thresholds.add(value);
            }
            List<String> ids = new ArrayList<>();
            for (JsonElement id : array(mob, "mobs")) if (id.isJsonPrimitive()) ids.add(id.getAsString());
            if (!ids.isEmpty() && !thresholds.isEmpty())
                out.add(new Definition(categoryName, clean(string(mob, "name", ids.getFirst())), List.copyOf(ids), List.copyOf(thresholds)));
        }
    }

    private static Comparator<Family> comparator(String sort) {
        return switch (sort == null ? "" : sort.toUpperCase(Locale.ROOT)) {
            case "KILLS" -> Comparator.comparingLong(Family::kills).reversed().thenComparing(Family::name);
            case "LEVEL" -> Comparator.comparingInt(Family::level).reversed().thenComparing(Family::name);
            case "REMAINING" -> Comparator.comparingLong(family -> family.nextThreshold() == 0 ? Long.MAX_VALUE : family.remaining());
            case "COMPLETION" -> Comparator.comparingDouble(Family::completion).reversed().thenComparing(Family::name);
            default -> Comparator.comparing(Family::category).thenComparing(Family::name);
        };
    }

    private static String clean(String value) {
        return value.replaceAll("§.", "").trim();
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

    private static int integer(JsonObject root, String key) {
        try { return root.has(key) ? root.get(key).getAsInt() : 0; }
        catch (RuntimeException ignored) { return 0; }
    }

    private static long whole(JsonElement value) {
        try { return value != null && value.isJsonPrimitive() ? value.getAsLong() : 0; }
        catch (RuntimeException ignored) { return 0; }
    }

    public record Catalogue(List<Definition> families, List<String> categories, long loadedAt, boolean cached) {}
    public record Definition(String category, String name, List<String> mobIds, List<Long> thresholds) {}
    public record Result(List<Family> families, int levels, int maxLevels, long totalKills, long totalDeaths, long unknownKills) {}
    public record Family(String category, String name, long kills, long deaths, int level, int maxLevel,
                         long nextThreshold, long remaining, long required, double completion) {}
}
