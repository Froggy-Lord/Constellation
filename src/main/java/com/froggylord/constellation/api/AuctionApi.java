package com.froggylord.constellation.api;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * lowest-BIN price feed. daemon thread, 5-min TTL, fetched from a public api.
 * no api key needed — the endpoint is open.
 * modelled on BazaarApi — same pattern, different source.
 */
public final class AuctionApi {

    private AuctionApi() {}

    // coflnet's public lowest-bin endpoint — one item at a time per request
    private static final String BASE = "https://sky.coflnet.com/api/auctions/tag/";
    private static final String SUFFIX = "/active/bin";
    private static final long TTL = 300_000; // 5 min
    private static final long FAILURE_TTL = 30_000;
    private static final HttpClient CLIENT = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private static final ExecutorService EXECUTOR = Executors.newFixedThreadPool(2, runnable -> {
        Thread thread = new Thread(runnable, "constellation-auction");
        thread.setDaemon(true);
        return thread;
    });

    // lbin = [price, timestamp]
    private static final Map<String, double[]> prices = new ConcurrentHashMap<>();
    private static final Set<String> fetching = ConcurrentHashMap.newKeySet();
    private static final Map<String, Long> failures = new ConcurrentHashMap<>();

    /** try to get the lowest BIN for an item id — returns null if not cached */
    public static Double getLbin(String itemId) {
        double[] entry = prices.get(itemId);
        if (entry == null) return null;
        if (System.currentTimeMillis() - (long) entry[1] > TTL) return null; // stale
        return entry[0];
    }

    /** async pre-fetch — call before you need it, result shows up next time getLbin is called */
    public static void prefetch(String itemId) {
        if (itemId == null || itemId.isBlank()) return;
        double[] cached = prices.get(itemId);
        if (cached != null && System.currentTimeMillis() - (long) cached[1] < TTL) return; // fresh
        Long failedAt = failures.get(itemId);
        if (failedAt != null && System.currentTimeMillis() - failedAt < FAILURE_TTL) return;
        if (!fetching.add(itemId)) return;
        EXECUTOR.execute(() -> fetchOne(itemId));
    }

    private static void fetchOne(String itemId) {
        boolean resolved = false;
        try {
            var req = HttpRequest.newBuilder(URI.create(BASE + itemId + SUFFIX))
                .timeout(Duration.ofSeconds(8))
                .header("User-Agent", "Constellation/1.0")
                .GET().build();
            var res = CLIENT.send(req, HttpResponse.BodyHandlers.ofString());
            if (res.statusCode() != 200) return;
            JsonArray arr = JsonParser.parseString(res.body()).getAsJsonArray();
            if (arr.isEmpty()) {
                prices.put(itemId, new double[]{0, System.currentTimeMillis()});
                resolved = true;
                return;
            }
            // coflnet returns [{"itemName":"...","startingBid":1234,...}, ...] — cheapest first
            double best = Double.MAX_VALUE;
            for (var e : arr) {
                JsonObject o = e.getAsJsonObject();
                double bid = o.has("startingBid") ? o.get("startingBid").getAsDouble() : 0;
                if (bid > 0 && bid < best) best = bid;
            }
            if (best < Double.MAX_VALUE) {
                prices.put(itemId, new double[]{best, System.currentTimeMillis()});
            } else prices.put(itemId, new double[]{0, System.currentTimeMillis()});
            resolved = true;
        } catch (Exception ignored) {
        } finally {
            if (resolved) failures.remove(itemId); else failures.put(itemId, System.currentTimeMillis());
            fetching.remove(itemId);
        }
    }

    public static int cached() { return prices.size(); }
    public static boolean isFetching(String itemId) { return fetching.contains(itemId); }
    public static boolean isCoolingDown(String itemId) { Long at = failures.get(itemId); return at != null && System.currentTimeMillis() - at < FAILURE_TTL; }
}
