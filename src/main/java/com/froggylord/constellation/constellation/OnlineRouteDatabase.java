package com.froggylord.constellation.constellation;

import com.froggylord.constellation.ConstellationClient;
import com.froggylord.constellation.data.DungeonData;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

// ported from SecretRoutes (GPL-3.0): utils/RouteUtils.java
// ported from SecretRoutes (GPL-3.0): utils/FileUtils.java (downloadFile)
public final class OnlineRouteDatabase {
    private static final String BASE = "https://raw.githubusercontent.com/yourboykyle/SecretRoutes/main/";
    private static final Path CACHE = Path.of("config", "constellation", "online-routes");
    private static final int MAX_BYTES = 8 * 1024 * 1024;
    private static final Gson GSON = new Gson();
    private static final AtomicBoolean STARTED = new AtomicBoolean();
    private static final HttpClient HTTP = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(8)).followRedirects(HttpClient.Redirect.NORMAL).build();

    private OnlineRouteDatabase() {}

    public static void init() {
        var cfg = ConstellationClient.cfg().orion;
        if (cfg == null || !cfg.secretRoutesOnlineDb || !STARTED.compareAndSet(false, true)) return;
        CompletableFuture.runAsync(() -> refresh("routes.json", DungeonData.ROUTES));
        CompletableFuture.runAsync(() -> refresh("pearlroutes.json", DungeonData.PEARL_ROUTES));
    }

    private static void refresh(String name, Map<String, List<DungeonData.Route>> destination) {
        Path cache = CACHE.resolve(name);
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(BASE + name))
                .timeout(Duration.ofSeconds(15))
                .header("User-Agent", "Constellation/0.9.599")
                .GET().build();
            HttpResponse<byte[]> response = HTTP.send(request, HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() != 200 || response.body().length == 0 || response.body().length > MAX_BYTES)
                throw new IllegalStateException("http " + response.statusCode() + ", " + response.body().length + " bytes");
            String json = new String(response.body(), StandardCharsets.UTF_8);
            Map<String, List<DungeonData.Route>> parsed = parse(json);
            Files.createDirectories(CACHE);
            Files.writeString(cache, json, StandardCharsets.UTF_8);
            applyValidated(name, parsed, destination);
        } catch (Exception fetchError) {
            try {
                if (!Files.exists(cache)) throw fetchError;
                String json = Files.readString(cache, StandardCharsets.UTF_8);
                applyValidated(name, parse(json), destination);
                ConstellationClient.LOGGER.info("online route refresh failed for {}, using validated cache", name);
            } catch (Exception cacheError) {
                ConstellationClient.LOGGER.warn("online route database unavailable for {}: {}", name, fetchError.getMessage());
            }
        }
    }

    private static void applyValidated(String name, Map<String, List<DungeonData.Route>> parsed,
                                       Map<String, List<DungeonData.Route>> destination) {
        Minecraft.getInstance().execute(() -> {
            destination.clear();
            destination.putAll(parsed);
            Routes.routeDatabaseChanged(destination == DungeonData.ROUTES);
            ConstellationClient.LOGGER.info("online {} validated and applied: {} room keys", name, parsed.size());
        });
    }

    private static Map<String, List<DungeonData.Route>> parse(String json) {
        JsonObject root = GSON.fromJson(json, JsonObject.class);
        if (root == null || !root.has("Version")) throw new IllegalArgumentException("missing Version");
        Map<String, List<DungeonData.Route>> result = new HashMap<>();
        for (var entry : root.entrySet()) {
            if (entry.getKey().startsWith("#") || entry.getKey().equals("Version") || !entry.getValue().isJsonArray()) continue;
            List<DungeonData.Route> variants = new ArrayList<>();
            for (var element : entry.getValue().getAsJsonArray()) {
                if (!element.isJsonObject()) throw new IllegalArgumentException("invalid route in " + entry.getKey());
                JsonObject route = element.getAsJsonObject();
                String secretType = "secret";
                int[] secret = null;
                if (route.has("secret") && route.get("secret").isJsonObject()) {
                    JsonObject value = route.getAsJsonObject("secret");
                    if (value.has("type")) secretType = value.get("type").getAsString();
                    if (value.has("location")) secret = triple(value.getAsJsonArray("location"));
                }
                variants.add(new DungeonData.Route(
                    coords(route, "locations"), coords(route, "etherwarps"),
                    coords(route, "interacts"), coords(route, "mines"),
                    coords(route, "tnts"), coords3d(route, "enderpearls"),
                    coords2d(route, "enderpearlangles"),
                    secretType, secret));
            }
            if (!variants.isEmpty()) result.put(entry.getKey().toLowerCase(Locale.ROOT), List.copyOf(variants));
        }
        if (result.size() < 50) throw new IllegalArgumentException("route database too small: " + result.size());
        return result;
    }

    private static List<int[]> coords(JsonObject route, String key) {
        if (!route.has(key) || !route.get(key).isJsonArray()) return List.of();
        List<int[]> result = new ArrayList<>();
        for (var element : route.getAsJsonArray(key)) {
            if (!element.isJsonArray() || element.getAsJsonArray().size() < 3)
                throw new IllegalArgumentException("invalid " + key + " coordinate");
            result.add(triple(element.getAsJsonArray()));
        }
        return List.copyOf(result);
    }

    private static int[] triple(JsonArray value) {
        if (value == null || value.size() < 3) throw new IllegalArgumentException("invalid coordinate");
        int x = value.get(0).getAsInt();
        int y = value.get(1).getAsInt();
        int z = value.get(2).getAsInt();
        if (Math.abs(x) > 512 || Math.abs(y) > 512 || Math.abs(z) > 512)
            throw new IllegalArgumentException("coordinate outside dungeon bounds");
        return new int[]{x, y, z};
    }

    private static List<double[]> coords3d(JsonObject route, String key) {
        if (!route.has(key) || !route.get(key).isJsonArray()) return List.of();
        List<double[]> result = new ArrayList<>();
        for (var element : route.getAsJsonArray(key)) {
            JsonArray value = element.getAsJsonArray();
            if (value.size() < 3) throw new IllegalArgumentException("invalid " + key + " coordinate");
            double x = value.get(0).getAsDouble(), y = value.get(1).getAsDouble(), z = value.get(2).getAsDouble();
            if (Math.abs(x) > 512 || Math.abs(y) > 512 || Math.abs(z) > 512)
                throw new IllegalArgumentException("coordinate outside dungeon bounds");
            result.add(new double[]{x, y, z});
        }
        return List.copyOf(result);
    }

    private static List<double[]> coords2d(JsonObject route, String key) {
        if (!route.has(key) || !route.get(key).isJsonArray()) return List.of();
        List<double[]> result = new ArrayList<>();
        for (var element : route.getAsJsonArray(key)) {
            JsonArray value = element.getAsJsonArray();
            if (value.size() < 2) throw new IllegalArgumentException("invalid " + key + " angle");
            result.add(new double[]{value.get(0).getAsDouble(), value.get(1).getAsDouble()});
        }
        return List.copyOf(result);
    }
}
