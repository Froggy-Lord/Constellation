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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

// ported from SkyBlockPv (modified MIT): data/repo/StaticGardenData.kt, utils/codecs/CodecUtils.kt, screens/windowed/tabs/farming/CropScreen.kt
// Portions of this code are from the SkyBlockPv mod.
public final class ProfileGardenData {
    private static final URI MILESTONES = URI.create(
        "https://raw.githubusercontent.com/meowdding/meowdding-repo/master/repo/pv/garden_data/crop_milestones.json");
    private static final URI MISC = URI.create(
        "https://raw.githubusercontent.com/meowdding/meowdding-repo/master/repo/pv/garden_data/misc.json");
    private static final URI VISITORS = URI.create(
        "https://raw.githubusercontent.com/meowdding/meowdding-repo/master/repo/pv/garden_data/visitors.json");
    private static final URI COMPOSTER = URI.create(
        "https://raw.githubusercontent.com/meowdding/meowdding-repo/master/repo/pv/garden_data/composter_data.json");
    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).build();
    private static volatile Catalogue catalogue;
    private static volatile CompletableFuture<Catalogue> loading;

    private ProfileGardenData() {}

    public static synchronized CompletableFuture<Catalogue> load(boolean refresh) {
        long ttl = Math.clamp(ConstellationClient.cfg().lyra.profileGardenCatalogueCacheHours, 1, 168) * 3_600_000L;
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
                JsonObject root = new JsonObject();
                root.add("milestones", fetch(MILESTONES));
                root.add("misc", fetch(MISC));
                root.add("visitors", fetch(VISITORS));
                root.add("composter", fetch(COMPOSTER));
                String body = root.toString();
                Catalogue loaded = parse(body, System.currentTimeMillis(), false);
                Files.writeString(cachePath(), body, StandardCharsets.UTF_8);
                catalogue = loaded;
                return loaded;
            } catch (Exception error) {
                if (cached != null) {
                    catalogue = cached;
                    return cached;
                }
                throw new RuntimeException("Garden progression catalogue is unavailable.", error);
            }
        });
        return loading;
    }

    private static JsonElement fetch(URI uri) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(15))
            .header("User-Agent", "Constellation/0.9/Minecraft-26.2").GET().build();
        HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200)
            throw new IllegalStateException("Garden catalogue request failed (" + response.statusCode() + ").");
        return JsonParser.parseString(response.body());
    }

    private static Catalogue readCache() {
        try {
            Path path = cachePath();
            if (!Files.isRegularFile(path)) return null;
            return parse(Files.readString(path), Files.getLastModifiedTime(path).toMillis(), true);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static Catalogue parse(String body, long loadedAt, boolean cached) {
        JsonObject root = JsonParser.parseString(body).getAsJsonObject();
        JsonObject rawMilestones = object(root, "milestones");
        JsonObject misc = object(root, "misc");
        JsonArray rawVisitors = array(root.get("visitors"));
        JsonObject rawComposter = object(root, "composter");
        Map<String, List<Long>> milestones = new LinkedHashMap<>();
        for (var entry : rawMilestones.entrySet()) {
            if (!entry.getValue().isJsonArray()) continue;
            List<Long> thresholds = cumulative(entry.getValue().getAsJsonArray());
            if (thresholds.size() != 46) throw new IllegalArgumentException("Garden milestone data is incomplete.");
            milestones.put(entry.getKey(), List.copyOf(thresholds));
        }
        List<Integer> upgradeCosts = new ArrayList<>();
        JsonElement rawCosts = misc.get("crop_upgrade_cost");
        if (rawCosts != null && rawCosts.isJsonArray())
            for (JsonElement value : rawCosts.getAsJsonArray()) upgradeCosts.add(value.getAsInt());
        if (milestones.size() != 13 || upgradeCosts.size() != 9)
            throw new IllegalArgumentException("Garden progression catalogue is incomplete.");
        Map<String, Visitor> visitors = new LinkedHashMap<>();
        for (JsonElement value : rawVisitors) {
            if (!value.isJsonObject()) continue;
            JsonObject visitor = value.getAsJsonObject();
            String id = string(visitor.get("id"), "");
            if (!id.isBlank()) visitors.put(id, new Visitor(id, string(visitor.get("name"), id),
                string(visitor.get("rarity"), "UNKNOWN")));
        }
        Map<String, Composter> composter = new LinkedHashMap<>();
        for (var entry : rawComposter.entrySet()) {
            if (!entry.getValue().isJsonObject()) continue;
            JsonObject definition = entry.getValue().getAsJsonObject();
            JsonArray levels = array(definition.get("upgrades"));
            List<Map<String, Integer>> cumulative = cumulativeMaps(levels);
            composter.put(entry.getKey(), new Composter(entry.getKey(),
                string(definition.get("name"), entry.getKey()), cumulative.size(), List.copyOf(cumulative)));
        }
        if (visitors.size() < 130 || composter.size() != 5
            || composter.values().stream().anyMatch(value -> value.maximum() != 25))
            throw new IllegalArgumentException("Garden visitor or composter catalogue is incomplete.");
        return new Catalogue(Map.copyOf(milestones), List.copyOf(upgradeCosts), Map.copyOf(visitors),
            Map.copyOf(composter), loadedAt, cached);
    }

    private static List<Long> cumulative(JsonArray steps) {
        List<Long> values = new ArrayList<>();
        long total = 0;
        for (JsonElement step : steps) {
            total += step.getAsLong();
            values.add(total);
        }
        return values;
    }
    private static List<Map<String, Integer>> cumulativeMaps(JsonArray steps) {
        List<Map<String, Integer>> values = new ArrayList<>();
        Map<String, Integer> total = new LinkedHashMap<>();
        for (JsonElement step : steps) {
            if (!step.isJsonObject()) continue;
            for (var entry : step.getAsJsonObject().entrySet())
                total.merge(entry.getKey(), entry.getValue().getAsInt(), Integer::sum);
            values.add(Map.copyOf(total));
        }
        return values;
    }
    private static JsonArray array(JsonElement value) {
        return value != null && value.isJsonArray() ? value.getAsJsonArray() : new JsonArray();
    }
    private static String string(JsonElement value, String fallback) {
        try { return value == null ? fallback : value.getAsString(); }
        catch (RuntimeException ignored) { return fallback; }
    }
    private static JsonObject object(JsonObject root, String key) {
        JsonElement value = root.get(key);
        return value != null && value.isJsonObject() ? value.getAsJsonObject() : new JsonObject();
    }
    private static Path cachePath() {
        return FabricLoader.getInstance().getConfigDir().resolve("constellation-garden-progression.json");
    }

    public record Visitor(String id, String name, String rarity) {}
    public record Composter(String id, String name, int maximum, List<Map<String, Integer>> costs) {}
    public record Catalogue(Map<String, List<Long>> milestones, List<Integer> cropUpgradeCosts,
                            Map<String, Visitor> visitors, Map<String, Composter> composter,
                            long loadedAt, boolean cached) {}
}
