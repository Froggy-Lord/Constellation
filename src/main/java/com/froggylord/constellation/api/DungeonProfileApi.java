package com.froggylord.constellation.api;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Collection;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

// ported from devonian (GPL-3.0): api/dungeon/DungeonsApi.kt
public final class DungeonProfileApi {
    private static final String BASE = "https://api.docilelm.top/v2/dungeons/";
    private static final long TTL = 600_000;
    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private static final Map<String, Cached> CACHE = new ConcurrentHashMap<>();
    private static final Set<String> QUEUE = ConcurrentHashMap.newKeySet();
    private static final AtomicBoolean FETCHING = new AtomicBoolean();
    private static volatile long lastFetch;

    private DungeonProfileApi() {}

    public static Profile get(String name) {
        Cached cached = CACHE.get(name.toLowerCase(Locale.ROOT));
        return cached == null || System.currentTimeMillis() - cached.time > TTL ? null : cached.profile;
    }

    public static void request(Collection<String> names) {
        long now = System.currentTimeMillis();
        for (String name : names) {
            String key = name.toLowerCase(Locale.ROOT);
            Cached cached = CACHE.get(key);
            if (cached == null || now - cached.time > TTL) QUEUE.add(key);
        }
        if (QUEUE.isEmpty() || now - lastFetch < 5_000 || !FETCHING.compareAndSet(false, true)) return;
        Thread thread = new Thread(DungeonProfileApi::fetch, "constellation-dungeon-profiles");
        thread.setDaemon(true);
        thread.start();
    }

    private static void fetch() {
        try {
            lastFetch = System.currentTimeMillis();
            String[] names = QUEUE.stream().limit(25).toArray(String[]::new);
            if (names.length == 0) return;
            HttpRequest request = HttpRequest.newBuilder(URI.create(BASE + String.join(",", names)))
                .timeout(Duration.ofSeconds(8)).header("User-Agent", "Constellation/1.0").GET().build();
            HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) return;
            JsonObject root = JsonParser.parseString(response.body()).getAsJsonObject();
            JsonObject result = root.has("result") && root.get("result").isJsonObject()
                ? root.getAsJsonObject("result") : null;
            if (result == null) return;
            long now = System.currentTimeMillis();
            for (String requested : names) {
                JsonObject value = find(result, requested);
                if (value == null || !value.has("success") || !value.get("success").getAsBoolean()
                    || !value.has("data") || !value.get("data").isJsonObject()) continue;
                JsonObject data = value.getAsJsonObject("data");
                CACHE.put(requested, new Cached(parse(data), now));
                QUEUE.remove(requested);
            }
        } catch (Exception ignored) {
        } finally {
            FETCHING.set(false);
        }
    }

    private static JsonObject find(JsonObject result, String name) {
        for (var entry : result.entrySet())
            if (entry.getKey().equalsIgnoreCase(name) && entry.getValue().isJsonObject()) return entry.getValue().getAsJsonObject();
        return null;
    }

    private static Profile parse(JsonObject data) {
        double cata = number(data, "level");
        int secrets = (int) number(data, "secrets");
        double average = number(data, "averageSecrets");
        int magicalPower = (int) number(data, "magical_power");
        JsonObject normal = object(data, "personal_best_normal");
        JsonObject master = object(data, "personal_best_master");
        return new Profile(cata, secrets, average, magicalPower, normal, master);
    }

    public static String personalBest(Profile profile, boolean master, int floor) {
        JsonObject modes = master ? profile.masterPbs : profile.normalPbs;
        if (modes == null) return "-";
        String key = "floor_" + floor;
        for (String mode : new String[]{"s_plus", "s"}) {
            JsonObject values = object(modes, mode);
            if (values != null && values.has(key)) return values.get(key).getAsString();
        }
        return "-";
    }

    private static double number(JsonObject object, String key) {
        try { return object.has(key) ? object.get(key).getAsDouble() : 0; }
        catch (Exception ignored) { return 0; }
    }

    private static JsonObject object(JsonObject object, String key) {
        return object != null && object.has(key) && object.get(key).isJsonObject() ? object.getAsJsonObject(key) : null;
    }

    public record Profile(double cata, int secrets, double averageSecrets, int magicalPower,
                          JsonObject normalPbs, JsonObject masterPbs) {}
    private record Cached(Profile profile, long time) {}
}
