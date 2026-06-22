package com.froggylord.constellation.api;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Reads the public Hypixel bazaar feed (no API key needed) so item tooltips can show live
 * buy/sell prices. The fetch runs on a daemon thread and refreshes a couple of times an hour;
 * everything that reads {@link #get} just hits the cached map, never the network.
 */
public final class BazaarApi {

    private BazaarApi() {}

    private static final String URL = "https://api.hypixel.net/v2/skyblock/bazaar";
    private static final long TTL = 180_000; // 3 min
    private static final Map<String, double[]> prices = new ConcurrentHashMap<>(); // id -> {buy, sell}
    private static volatile long lastFetch = 0;
    private static volatile boolean fetching = false;

    /** kick a refresh if the cache is stale; cheap to call every frame. */
    public static void ensureFresh() {
        long now = System.currentTimeMillis();
        if (fetching || now - lastFetch < TTL) return;
        fetching = true;
        Thread t = new Thread(BazaarApi::fetch, "constellation-bazaar");
        t.setDaemon(true);
        t.start();
    }

    private static void fetch() {
        try {
            HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).build();
            HttpRequest req = HttpRequest.newBuilder(URI.create(URL))
                .timeout(Duration.ofSeconds(12)).GET().build();
            HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() != 200) return;
            JsonObject root = JsonParser.parseString(resp.body()).getAsJsonObject();
            JsonObject products = root.getAsJsonObject("products");
            if (products == null) return;
            Map<String, double[]> fresh = new HashMap<>();
            for (var e : products.entrySet()) {
                JsonObject p = e.getValue().getAsJsonObject();
                JsonObject qs = p.getAsJsonObject("quick_status");
                if (qs == null) continue;
                double buy = qs.has("buyPrice") ? qs.get("buyPrice").getAsDouble() : 0;
                double sell = qs.has("sellPrice") ? qs.get("sellPrice").getAsDouble() : 0;
                fresh.put(e.getKey(), new double[]{buy, sell});
            }
            if (!fresh.isEmpty()) { prices.clear(); prices.putAll(fresh); lastFetch = System.currentTimeMillis(); }
        } catch (Exception ignored) {
            // offline, rate-limited, whatever — try again next interval
        } finally {
            fetching = false;
        }
    }

    /** {buyPrice, sellPrice} for a bazaar product id, or null if not traded / not loaded yet. */
    public static double[] get(String id) { return prices.get(id); }
}
