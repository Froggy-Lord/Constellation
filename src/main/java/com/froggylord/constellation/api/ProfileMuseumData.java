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

// ported from SkyBlockPv (modified MIT): buildSrc/museum/CreateMuseumDataTask.kt, ItemParser.kt, ArmorParser.kt, data/museum/MuseumData.kt, CategorizedMuseumScreen.kt
// Portions of this code are from the SkyBlockPv mod.
public final class ProfileMuseumData {
    private static final URI SOURCE = URI.create("https://api.hypixel.net/v2/resources/skyblock/items");
    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).build();
    private static volatile Catalogue catalogue;
    private static volatile CompletableFuture<Catalogue> loading;

    private ProfileMuseumData() {}

    public static synchronized CompletableFuture<Catalogue> load(boolean refresh) {
        long ttl = Math.clamp(ConstellationClient.cfg().lyra.profileMuseumCacheHours, 1, 168) * 3_600_000L;
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
                HttpRequest request = HttpRequest.newBuilder(SOURCE).timeout(Duration.ofSeconds(20))
                    .header("User-Agent", "Constellation/0.9/Minecraft-26.2").GET().build();
                HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() != 200) throw new IllegalStateException("Museum catalogue request failed.");
                Catalogue loaded = parse(response.body(), System.currentTimeMillis(), false);
                Files.writeString(cachePath(), response.body(), StandardCharsets.UTF_8);
                catalogue = loaded;
                return loaded;
            } catch (Exception error) {
                if (cached != null) return catalogue = cached;
                throw new RuntimeException("Museum catalogue is unavailable.", error);
            }
        });
        return loading;
    }

    public static Result calculate(Catalogue data, ProfileViewerApi.MuseumResult museum, String category,
                                   String search, String filter, String sort, boolean includeBorrowed) {
        JsonObject donatedItems = object(museum.member(), "items");
        Set<String> donated = new HashSet<>();
        Set<String> borrowed = new HashSet<>();
        for (var entry : donatedItems.entrySet()) {
            donated.add(entry.getKey());
            if (entry.getValue().isJsonObject() && bool(entry.getValue().getAsJsonObject(), "borrowing"))
                borrowed.add(entry.getKey());
        }
        String wantedCategory = category == null ? "ALL" : category.trim();
        String wantedSearch = search == null ? "" : search.trim().toLowerCase(Locale.ROOT);
        String wantedFilter = filter == null ? "ALL" : filter.toUpperCase(Locale.ROOT);
        Map<String, Definition> definitions = new HashMap<>();
        for (Definition definition : data.entries()) definitions.put(definition.id(), definition);
        List<Entry> rows = new ArrayList<>();
        int direct = 0, throughParent = 0, missing = 0, borrowedCount = 0;
        for (Definition definition : data.entries()) {
            Status status = status(definition, definitions, donated, borrowed, includeBorrowed, new HashSet<>());
            if (status == Status.DONATED) direct++;
            else if (status == Status.BORROWED) borrowedCount++;
            else if (status == Status.PARENT) throughParent++;
            else missing++;
            Entry row = new Entry(definition.category(), definition.id(), definition.name(), definition.armor(),
                definition.pieces(), definition.parent(), status);
            if (include(row, wantedCategory, wantedSearch, wantedFilter)) rows.add(row);
        }
        rows.sort(comparator(sort));
        int specialDonated = (int) museum.specialIds().stream().filter(data.specialIds()::contains).count();
        long unknownSpecial = museum.specialIds().stream().filter(id -> !data.specialIds().contains(id)).count();
        return new Result(List.copyOf(rows), direct, throughParent, missing, borrowedCount, data.entries().size(),
            specialDonated, data.specialIds().size(), unknownSpecial, museum.specialFailures(),
            !donatedItems.isEmpty() || !museum.specialIds().isEmpty(), museum.cached());
    }

    private static Status status(Definition definition, Map<String, Definition> definitions, Set<String> donated,
                                 Set<String> borrowed, boolean includeBorrowed, Set<String> checked) {
        boolean found = donated.contains(definition.id())
            || definition.mappedIds().stream().anyMatch(donated::contains);
        boolean isBorrowed = borrowed.contains(definition.id())
            || definition.mappedIds().stream().anyMatch(borrowed::contains);
        if (found && isBorrowed) return includeBorrowed ? Status.BORROWED : Status.MISSING;
        if (found) return Status.DONATED;
        String parent = definition.parent();
        if (parent == null || !checked.add(parent)) return Status.MISSING;
        Definition parentDefinition = definitions.get(parent);
        boolean parentDonated = donated.contains(parent)
            || parentDefinition != null && parentDefinition.mappedIds().stream().anyMatch(donated::contains);
        boolean parentBorrowed = borrowed.contains(parent)
            || parentDefinition != null && parentDefinition.mappedIds().stream().anyMatch(borrowed::contains);
        if (parentDonated && (!parentBorrowed || includeBorrowed)) return Status.PARENT;
        if (parentDonated) return Status.MISSING;
        return parentDefinition == null ? Status.MISSING
            : status(parentDefinition, definitions, donated, borrowed, includeBorrowed, checked) == Status.MISSING
                ? Status.MISSING : Status.PARENT;
    }

    private static boolean include(Entry row, String category, String search, String filter) {
        if (!category.equalsIgnoreCase("ALL") && !row.category().equalsIgnoreCase(category)) return false;
        if (!search.isEmpty() && !row.name().toLowerCase(Locale.ROOT).contains(search)
            && !row.id().toLowerCase(Locale.ROOT).contains(search)
            && row.pieces().stream().noneMatch(id -> id.toLowerCase(Locale.ROOT).contains(search))) return false;
        return filter.equals("ALL") || row.status().name().equals(filter)
            || filter.equals("COMPLETE") && row.status() != Status.MISSING;
    }

    private static Comparator<Entry> comparator(String sort) {
        return switch (sort == null ? "" : sort.toUpperCase(Locale.ROOT)) {
            case "NAME" -> Comparator.comparing(Entry::name);
            case "STATUS" -> Comparator.comparing(Entry::status).thenComparing(Entry::name);
            case "TYPE" -> Comparator.comparing(Entry::armor).reversed().thenComparing(Entry::name);
            default -> Comparator.comparing(Entry::category).thenComparing(Entry::name);
        };
    }

    private static Catalogue readCache() {
        try {
            Path path = cachePath();
            return Files.isRegularFile(path) ? parse(Files.readString(path), Files.getLastModifiedTime(path).toMillis(), true) : null;
        } catch (Exception ignored) { return null; }
    }

    static Catalogue validateCatalogue(String body) {
        return parse(body, System.currentTimeMillis(), false);
    }

    private static Catalogue parse(String body, long loadedAt, boolean cached) {
        JsonObject root = JsonParser.parseString(body).getAsJsonObject();
        if (!bool(root, "success")) throw new IllegalArgumentException("Museum resource was not successful.");
        List<Definition> entries = new ArrayList<>();
        Map<String, ArmorBuilder> armors = new HashMap<>();
        Set<String> special = new HashSet<>();
        Set<String> categories = new HashSet<>();
        for (JsonElement element : array(root, "items")) {
            if (!element.isJsonObject()) continue;
            JsonObject item = element.getAsJsonObject();
            String id = string(item, "id", "");
            String name = string(item, "name", title(id));
            JsonObject museum = object(item, "museum_data");
            if (museum.isEmpty()) {
                if (bool(item, "museum")) special.add(id);
                continue;
            }
            String category = title(string(museum, "category", "Unknown"));
            categories.add(category);
            JsonObject armorXp = object(museum, "armor_set_donation_xp");
            if (!armorXp.isEmpty()) {
                for (String armorId : armorXp.keySet()) {
                    ArmorBuilder builder = armors.computeIfAbsent(armorId, ignored -> new ArmorBuilder(category, armorId));
                    builder.pieces.add(id);
                    JsonObject parent = object(museum, "parent");
                    if (!parent.isEmpty() && builder.parent == null) builder.parent = parent.keySet().iterator().next();
                }
                continue;
            }
            JsonObject parent = object(museum, "parent");
            String parentId = parent.has(id) ? string(parent.get(id), null) : null;
            List<String> mapped = strings(array(museum, "mapped_item_ids"));
            entries.add(new Definition(category, id, name, parentId, mapped, false, List.of()));
        }
        for (ArmorBuilder armor : armors.values())
            entries.add(new Definition(armor.category, armor.id, title(armor.id), armor.parent,
                List.of(), true, List.copyOf(armor.pieces)));
        if (entries.size() < 600 || special.size() < 250 || categories.size() < 7)
            throw new IllegalArgumentException("Museum catalogue is incomplete.");
        entries.sort(Comparator.comparing(Definition::category).thenComparing(Definition::name));
        List<String> categoryList = categories.stream().sorted().toList();
        return new Catalogue(List.copyOf(entries), Set.copyOf(special), categoryList, loadedAt, cached);
    }

    private static List<String> strings(JsonArray array) {
        List<String> values = new ArrayList<>();
        for (JsonElement value : array) if (value.isJsonPrimitive()) values.add(value.getAsString());
        return List.copyOf(values);
    }

    private static Path cachePath() { return FabricLoader.getInstance().getConfigDir().resolve("constellation-museum-items.json"); }
    private static JsonObject object(JsonObject root, String key) {
        JsonElement value = root == null ? null : root.get(key);
        return value != null && value.isJsonObject() ? value.getAsJsonObject() : new JsonObject();
    }
    private static JsonArray array(JsonObject root, String key) {
        JsonElement value = root == null ? null : root.get(key);
        return value != null && value.isJsonArray() ? value.getAsJsonArray() : new JsonArray();
    }
    private static boolean bool(JsonObject root, String key) {
        try { return root.has(key) && root.get(key).getAsBoolean(); } catch (RuntimeException ignored) { return false; }
    }
    private static String string(JsonObject root, String key, String fallback) {
        try { return root.has(key) ? root.get(key).getAsString() : fallback; } catch (RuntimeException ignored) { return fallback; }
    }
    private static String string(JsonElement value, String fallback) {
        try { return value == null ? fallback : value.getAsString(); } catch (RuntimeException ignored) { return fallback; }
    }
    private static String title(String value) {
        if (value == null) return "";
        StringBuilder out = new StringBuilder();
        for (String part : value.replace('_', ' ').toLowerCase(Locale.ROOT).split(" ")) {
            if (!out.isEmpty()) out.append(' ');
            if (!part.isEmpty()) out.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return out.toString();
    }

    private static final class ArmorBuilder {
        private final String category, id;
        private final List<String> pieces = new ArrayList<>();
        private String parent;
        private ArmorBuilder(String category, String id) { this.category = category; this.id = id; }
    }
    public enum Status { DONATED, BORROWED, PARENT, MISSING }
    public record Definition(String category, String id, String name, String parent, List<String> mappedIds,
                             boolean armor, List<String> pieces) {}
    public record Catalogue(List<Definition> entries, Set<String> specialIds, List<String> categories,
                            long loadedAt, boolean cached) {}
    public record Entry(String category, String id, String name, boolean armor, List<String> pieces,
                        String parent, Status status) {}
    public record Result(List<Entry> entries, int donated, int throughParent, int missing, int borrowed,
                         int total, int specialDonated, int specialTotal, long unknownSpecial,
                         int specialFailures, boolean available, boolean cached) {}
}
