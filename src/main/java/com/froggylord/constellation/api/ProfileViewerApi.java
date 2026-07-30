package com.froggylord.constellation.api;

import com.froggylord.constellation.ConstellationClient;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.Minecraft;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

// Portions of this code are from the SkyBlockPv mod.
// ported from SkyBlockPv (modified MIT): api/PvAPI.kt, CachedApi.kt, CachedApis.kt
public final class ProfileViewerApi {
    private static final String API = "https://skyblock-pv.thatgravyboat.tech";
    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).build();
    private static final Map<UUID, Cached> CACHE = new ConcurrentHashMap<>();
    private static volatile String key;

    private ProfileViewerApi() {}

    public static CompletableFuture<Result> load(String name, boolean refresh) {
        String clean = name == null ? "" : name.trim();
        if (!clean.matches("[A-Za-z0-9_]{1,16}")) return CompletableFuture.failedFuture(new IllegalArgumentException("Enter a valid Minecraft name."));
        return CompletableFuture.supplyAsync(() -> {
            try {
                GameProfile profile = lookup(clean);
                Cached cached = CACHE.get(profile.uuid);
                long ttl = Math.clamp(ConstellationClient.cfg().lyra.profileCacheMinutes, 1, 60) * 60_000L;
                if (!refresh && cached != null && System.currentTimeMillis() - cached.time < ttl)
                    return new Result(profile.name, profile.uuid, cached.profiles, cached.time, true);
                ensureAuthenticated(refresh);
                JsonObject response = get("/profiles/" + profile.uuid);
                JsonArray profiles = response.has("profiles") && response.get("profiles").isJsonArray()
                    ? response.getAsJsonArray("profiles") : new JsonArray();
                if (profiles.isEmpty()) throw new IllegalStateException("No SkyBlock profiles were returned.");
                CACHE.put(profile.uuid, new Cached(profiles, System.currentTimeMillis()));
                return new Result(profile.name, profile.uuid, profiles, System.currentTimeMillis(), false);
            } catch (Exception e) {
                throw new RuntimeException(readable(e), e);
            }
        });
    }

    private static GameProfile lookup(String name) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create(
            "https://api.minecraftservices.com/minecraft/profile/lookup/name/" + URLEncoder.encode(name, StandardCharsets.UTF_8)))
            .timeout(Duration.ofSeconds(8)).header("User-Agent", "Constellation/0.9").GET().build();
        HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() == 404) throw new IllegalArgumentException("That Minecraft player does not exist.");
        if (response.statusCode() != 200) throw new IllegalStateException("Minecraft profile lookup failed (" + response.statusCode() + ").");
        JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();
        String id = json.get("id").getAsString();
        UUID uuid = UUID.fromString(id.replaceFirst(
            "([0-9a-fA-F]{8})([0-9a-fA-F]{4})([0-9a-fA-F]{4})([0-9a-fA-F]{4})([0-9a-fA-F]{12})", "$1-$2-$3-$4-$5"));
        return new GameProfile(json.get("name").getAsString(), uuid);
    }

    private static synchronized void ensureAuthenticated(boolean refresh) throws Exception {
        if (key != null && !refresh) return;
        Minecraft mc = Minecraft.getInstance();
        String server = UUID.randomUUID().toString();
        mc.services().sessionService().joinServer(mc.getUser().getProfileId(), mc.getUser().getAccessToken(), server);
        HttpRequest request = HttpRequest.newBuilder(URI.create(API + "/authenticate" + (refresh ? "?bypassCache=true" : "")))
            .timeout(Duration.ofSeconds(12))
            .header("User-Agent", "Constellation/0.9/Minecraft-26.2")
            .header("x-minecraft-username", mc.getUser().getName())
            .header("x-minecraft-server", server).GET().build();
        HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200 || response.body().isBlank())
            throw new IllegalStateException("Profile service authentication failed (" + response.statusCode() + ").");
        key = response.body().trim();
    }

    private static JsonObject get(String path) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create(API + path)).timeout(Duration.ofSeconds(20))
            .header("User-Agent", "Constellation/0.9/Minecraft-26.2")
            .header("Authorization", key).header("X-Intent", "profile-viewer").GET().build();
        HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() == 401) {
            key = null;
            ensureAuthenticated(true);
            return get(path);
        }
        if (response.statusCode() != 200) throw new IllegalStateException("Profile service failed (" + response.statusCode() + ").");
        return JsonParser.parseString(response.body()).getAsJsonObject();
    }

    private static String readable(Exception error) {
        Throwable cause = error;
        while (cause.getCause() != null && cause.getCause() != cause) cause = cause.getCause();
        String message = cause.getMessage();
        if (message == null || message.isBlank()) message = cause.getClass().getSimpleName();
        ConstellationClient.LOGGER.warn("Profile viewer request failed: {}", message);
        return message;
    }

    public record Result(String name, UUID uuid, JsonArray profiles, long fetchedAt, boolean cached) {}
    private record Cached(JsonArray profiles, long time) {}
    private record GameProfile(String name, UUID uuid) {}
}
