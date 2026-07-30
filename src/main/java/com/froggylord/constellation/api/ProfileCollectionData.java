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
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

// ported from Skyblocker (LGPL-3.0-or-later): skyblock/profileviewer/utils/Collection.java, collections/GenericCategory.java
// ported from SkyBlockPv (modified MIT): api/CollectionAPI.kt, data/api/CollectionData.kt, api/data/profile/SkyBlockProfile.kt, screens/windowed/tabs/collection/CommonCollectionScreen.kt
// Portions of this code are from the SkyBlockPv mod.
public final class ProfileCollectionData {
    private static final URI SOURCE = URI.create("https://api.hypixel.net/v2/resources/skyblock/collections");
    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).build();
    private static volatile Catalogue catalogue;
    private static volatile CompletableFuture<Catalogue> loading;

    private ProfileCollectionData() {}

    public static synchronized CompletableFuture<Catalogue> load(boolean refresh) {
        long ttl = Math.clamp(ConstellationClient.cfg().lyra.profileCollectionsCacheHours, 1, 168) * 3_600_000L;
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
                if (response.statusCode() != 200) throw new IllegalStateException("Collection data request failed (" + response.statusCode() + ").");
                Catalogue loaded = parse(response.body(), System.currentTimeMillis(), false);
                Files.writeString(cachePath(), response.body(), StandardCharsets.UTF_8);
                catalogue = loaded;
                return loaded;
            } catch (Exception error) {
                if (cached != null) {
                    catalogue = cached;
                    return cached;
                }
                throw new RuntimeException("Collection catalogue is unavailable.", error);
            }
        });
        return loading;
    }

    public static Result calculate(Catalogue data, JsonObject profile, String memberId, String category,
                                   String search, String sort, boolean hideZero, boolean hideMaxed) {
        JsonObject members = object(profile, "members");
        Map<String, Long> totals = new HashMap<>();
        Map<String, Long> personal = new HashMap<>();
        boolean available = false;
        for (var memberEntry : members.entrySet()) {
            if (!memberEntry.getValue().isJsonObject()) continue;
            JsonObject collection = object(memberEntry.getValue().getAsJsonObject(), "collection");
            if (!collection.isEmpty()) available = true;
            for (var item : collection.entrySet()) {
                long amount = whole(item.getValue());
                totals.merge(item.getKey(), amount, Long::sum);
                if (memberEntry.getKey().equalsIgnoreCase(memberId)) personal.put(item.getKey(), amount);
            }
        }
        String cleanCategory = category == null ? "ALL" : category.trim();
        String cleanSearch = search == null ? "" : search.trim().toLowerCase(Locale.ROOT);
        List<Collection> collections = new ArrayList<>();
        Set<String> known = new HashSet<>();
        for (Definition definition : data.collections()) {
            known.add(definition.id());
            long total = totals.getOrDefault(definition.id(), 0L);
            int tier = 0;
            while (tier < definition.tiers().size() && total >= definition.tiers().get(tier).amount()) tier++;
            Tier next = tier >= definition.tiers().size() ? null : definition.tiers().get(tier);
            long required = definition.tiers().isEmpty() ? 0 : definition.tiers().getLast().amount();
            Collection value = new Collection(definition.category(), definition.id(), definition.name(),
                personal.getOrDefault(definition.id(), 0L), total, tier, definition.tiers().size(),
                next == null ? 0 : next.amount(), next == null ? 0 : Math.max(0, next.amount() - total),
                required <= 0 ? 0 : Math.min(1, total / (double) required),
                next == null ? List.of() : next.unlocks(), false);
            if (include(value, cleanCategory, cleanSearch, hideZero, hideMaxed)) collections.add(value);
        }
        for (var entry : totals.entrySet()) {
            if (known.contains(entry.getKey())) continue;
            Collection value = new Collection("Unknown", entry.getKey(), title(entry.getKey()),
                personal.getOrDefault(entry.getKey(), 0L), entry.getValue(), 0, 0,
                0, 0, 0, List.of(), true);
            if (include(value, cleanCategory, cleanSearch, hideZero, hideMaxed)) collections.add(value);
        }
        collections.sort(comparator(sort));
        int tiers = data.collections().stream().mapToInt(definition -> {
            long total = totals.getOrDefault(definition.id(), 0L);
            int level = 0;
            while (level < definition.tiers().size() && total >= definition.tiers().get(level).amount()) level++;
            return level;
        }).sum();
        int maxTiers = data.collections().stream().mapToInt(definition -> definition.tiers().size()).sum();
        int maxed = (int) data.collections().stream().filter(definition ->
            !definition.tiers().isEmpty() && totals.getOrDefault(definition.id(), 0L) >= definition.tiers().getLast().amount()).count();
        return new Result(available, List.copyOf(collections), tiers, maxTiers, maxed,
            data.collections().size(), totals.keySet().stream().filter(id -> !known.contains(id)).count());
    }

    private static boolean include(Collection value, String category, String search, boolean hideZero, boolean hideMaxed) {
        if (!category.equalsIgnoreCase("ALL") && !value.category().equalsIgnoreCase(category)) return false;
        if (!search.isEmpty() && !value.name().toLowerCase(Locale.ROOT).contains(search)
            && !value.id().toLowerCase(Locale.ROOT).contains(search)) return false;
        if (hideZero && value.total() == 0) return false;
        return !hideMaxed || value.maxTier() == 0 || value.tier() < value.maxTier();
    }

    private static Comparator<Collection> comparator(String sort) {
        return switch (sort == null ? "" : sort.toUpperCase(Locale.ROOT)) {
            case "AMOUNT" -> Comparator.comparingLong(Collection::total).reversed().thenComparing(Collection::name);
            case "TIER" -> Comparator.comparingInt(Collection::tier).reversed().thenComparing(Collection::name);
            case "REMAINING" -> Comparator.comparingLong(value -> value.nextThreshold() == 0 ? Long.MAX_VALUE : value.remaining());
            case "COMPLETION" -> Comparator.comparingDouble(Collection::completion).reversed().thenComparing(Collection::name);
            case "NAME" -> Comparator.comparing(Collection::name);
            default -> Comparator.comparing(Collection::category).thenComparing(Collection::name);
        };
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

    private static Catalogue parse(String body, long loadedAt, boolean cached) {
        JsonObject root = JsonParser.parseString(body).getAsJsonObject();
        if (!bool(root, "success")) throw new IllegalArgumentException("Collection resource was not successful.");
        JsonObject categories = object(root, "collections");
        List<Definition> definitions = new ArrayList<>();
        List<String> categoryNames = new ArrayList<>();
        for (var categoryEntry : categories.entrySet()) {
            if (!categoryEntry.getValue().isJsonObject()) continue;
            JsonObject category = categoryEntry.getValue().getAsJsonObject();
            String categoryName = string(category, "name", title(categoryEntry.getKey()));
            categoryNames.add(categoryName);
            JsonObject items = object(category, "items");
            for (var itemEntry : items.entrySet()) {
                if (!itemEntry.getValue().isJsonObject()) continue;
                JsonObject item = itemEntry.getValue().getAsJsonObject();
                List<Tier> tiers = new ArrayList<>();
                for (JsonElement tierElement : array(item, "tiers")) {
                    if (!tierElement.isJsonObject()) continue;
                    JsonObject tier = tierElement.getAsJsonObject();
                    List<String> unlocks = new ArrayList<>();
                    for (JsonElement unlock : array(tier, "unlocks"))
                        if (unlock.isJsonPrimitive()) unlocks.add(unlock.getAsString());
                    tiers.add(new Tier(integer(tier, "tier"), whole(tier.get("amountRequired")), List.copyOf(unlocks)));
                }
                tiers.sort(Comparator.comparingInt(Tier::tier));
                definitions.add(new Definition(categoryName, itemEntry.getKey(),
                    string(item, "name", title(itemEntry.getKey())), List.copyOf(tiers)));
            }
        }
        if (categoryNames.size() < 6 || definitions.size() < 80)
            throw new IllegalArgumentException("Collection catalogue is incomplete.");
        categoryNames.sort(String::compareTo);
        return new Catalogue(List.copyOf(definitions), List.copyOf(categoryNames), loadedAt, cached);
    }

    private static Path cachePath() {
        return FabricLoader.getInstance().getConfigDir().resolve("constellation-collections.json");
    }

    private static String title(String value) {
        String clean = value.replace('_', ' ').toLowerCase(Locale.ROOT);
        return clean.isEmpty() ? clean : Character.toUpperCase(clean.charAt(0)) + clean.substring(1);
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

    private static boolean bool(JsonObject root, String key) {
        try { return root.has(key) && root.get(key).getAsBoolean(); }
        catch (RuntimeException ignored) { return false; }
    }

    public record Catalogue(List<Definition> collections, List<String> categories, long loadedAt, boolean cached) {}
    public record Definition(String category, String id, String name, List<Tier> tiers) {}
    public record Tier(int tier, long amount, List<String> unlocks) {}
    public record Result(boolean available, List<Collection> collections, int tiers, int maxTiers,
                         int maxed, int known, long unknown) {}
    public record Collection(String category, String id, String name, long personal, long total,
                             int tier, int maxTier, long nextThreshold, long remaining,
                             double completion, List<String> nextUnlocks, boolean unknown) {}
}
