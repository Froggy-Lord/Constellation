package com.froggylord.constellation.api;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.Reader;
import java.io.Writer;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * fetches individual item metadata from the NotEnoughUpdates repo on demand.
 * caches to disk (config/constellation-repo/) with a 24-hour ttl so we
 * don't hammer github. replaces the frozen 4.2mb research snapshot for
 * data that actually drifts (item stats, recipes, npc prices).
 *
 * the NEU repo is public — individual files served raw from github.
 */
public final class NeuRepoLoader {

    private NeuRepoLoader() {}

    private static final String BASE = "https://raw.githubusercontent.com/NotEnoughUpdates/NotEnoughUpdates-REPO/master/items/";
    private static final Path CACHE = Path.of("config", "constellation-repo");
    private static final long TTL = 86_400_000; // 24 hours
    private static final ConcurrentMap<String, JsonObject> memory = new ConcurrentHashMap<>();

    static {
        try { Files.createDirectories(CACHE); } catch (Exception ignored) {}
    }

    /** get full item data for a skyblock item id — e.g. "HYPERION" */
    public static JsonObject get(String itemId) {
        // memory → disk → network
        JsonObject cached = memory.get(itemId);
        if (cached != null) return cached;

        Path disk = CACHE.resolve(itemId + ".json");
        if (Files.exists(disk)) {
            try {
                long age = System.currentTimeMillis() - Files.getLastModifiedTime(disk).toMillis();
                if (age < TTL) {
                    try (Reader r = Files.newBufferedReader(disk)) {
                        JsonObject obj = JsonParser.parseReader(r).getAsJsonObject();
                        memory.put(itemId, obj);
                        return obj;
                    }
                }
            } catch (Exception ignored) {}
        }

        // fetch async — return null this call, data ready next time
        fetch(itemId, disk);
        return null;
    }

    private static void fetch(String itemId, Path disk) {
        Thread t = new Thread(() -> {
            try {
                var client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
                var req = HttpRequest.newBuilder(URI.create(BASE + itemId + ".json"))
                    .timeout(Duration.ofSeconds(10))
                    .header("User-Agent", "Constellation/1.0")
                    .GET().build();
                var res = client.send(req, HttpResponse.BodyHandlers.ofString());
                if (res.statusCode() != 200) return;
                JsonObject obj = JsonParser.parseString(res.body()).getAsJsonObject();
                memory.put(itemId, obj);
                try (Writer w = Files.newBufferedWriter(disk)) {
                    w.write(res.body());
                }
            } catch (Exception ignored) {}
        }, "constellation-neu-" + itemId);
        t.setDaemon(true);
        t.start();
    }

    /** convenience: get the display name of an item */
    public static String displayName(String itemId) {
        JsonObject obj = get(itemId);
        if (obj == null) return itemId;
        String name = obj.has("displayname") ? obj.get("displayname").getAsString() : null;
        return name != null ? name : itemId;
    }

    /** convenience: get npc sell price from the repo */
    public static double npcPrice(String itemId) {
        JsonObject obj = get(itemId);
        if (obj == null) return 0;
        return obj.has("npcsellprice") ? obj.get("npcsellprice").getAsDouble() : 0;
    }

    /** convenience: get bazaar status */
    public static boolean isBazaar(String itemId) {
        JsonObject obj = get(itemId);
        if (obj == null) return false;
        return obj.has("bazaar") && obj.get("bazaar").getAsBoolean();
    }

    public static int memorySize() { return memory.size(); }
}
