package com.froggylord.constellation.data;

import com.froggylord.constellation.ConstellationClient;
import com.google.gson.*;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.InflaterInputStream;

// detection data loading ported from cryptkit (GPL-3.0): core/RoomData.java
// room block-data now from Skyblocker (LGPL): DungeonRoomData
public class DungeonData {

    // ---- secrets (kept from dungeon-routes-mod data) ----
    public static final Map<String, List<Secret>> SECRETS = new HashMap<>();

    // route schema ported from SecretRoutes (GPL-3.0): routes.json and pearlroutes.json
    public static final Map<String, List<Route>> ROUTES = new HashMap<>();
    public static final Map<String, List<Route>> PEARL_ROUTES = new HashMap<>();

    private static volatile boolean loaded = false;
    public static final int SECRET_SOURCE_FLOOR_Y = 68;

    public record Secret(String category, String name, int x, int y, int z) {}
    public record SecretWaypoint(String category, String name, int x, int y, int z) {}

    public record Route(List<int[]> locations, List<int[]> etherwarps, List<int[]> interacts,
                        List<int[]> mines, List<int[]> tnts, List<double[]> pearls,
                        List<double[]> pearlAngles,
                        String secretType, int[] secret) {}

    // ---- block id table (delegated to DungeonRoomData) ----
    public static byte numericId(String blockKey) {
        return DungeonRoomData.numericId(blockKey);
    }

    // ---- block encoding (delegated to DungeonRoomData) ----
    public static int posIdToInt(int relX, int relY, int relZ, byte id) {
        return DungeonRoomData.posIdToInt(relX, relY, relZ, id);
    }

    public static int idX(int v) { return DungeonRoomData.idX(v); }
    public static int idY(int v) { return DungeonRoomData.idY(v); }
    public static int idZ(int v) { return DungeonRoomData.idZ(v); }
    public static int idBlock(int v) { return DungeonRoomData.idBlock(v); }

    public static void load() {
        if (loaded) return;
        long start = System.currentTimeMillis();
        DungeonRoomData.load();   // Skyblocker .skeleton block-data
        loadSecrets();
        loadRoutes("routes.json", ROUTES);
        loadRoutes("pearlroutes.json", PEARL_ROUTES);
        loaded = true;
        ConstellationClient.LOGGER.info("Dungeon data: {} rooms ({} with secrets, {} routed) in {}ms",
            DungeonRoomData.roomCount(), SECRETS.size(), ROUTES.size(), System.currentTimeMillis() - start);
    }

    private static void loadRoutes(String file, Map<String, List<Route>> into) {
        try (InputStream in = res(file)) {
            if (in == null) return;
            JsonObject root = new Gson().fromJson(new InputStreamReader(in, StandardCharsets.UTF_8), JsonObject.class);
            for (var entry : root.entrySet()) {
                if (entry.getKey().startsWith("#") || !entry.getValue().isJsonArray()) continue;
                List<Route> variants = new ArrayList<>();
                for (var el : entry.getValue().getAsJsonArray()) {
                    JsonObject o = el.getAsJsonObject();
                    String secType = "secret";
                    int[] secLoc = null;
                    if (o.has("secret") && o.get("secret").isJsonObject()) {
                        JsonObject s = o.getAsJsonObject("secret");
                        if (s.has("type")) secType = s.get("type").getAsString();
                        if (s.has("location")) secLoc = triple(s.getAsJsonArray("location"));
                    }
                    variants.add(new Route(
                        coords(o, "locations"), coords(o, "etherwarps"), coords(o, "interacts"),
                        coords(o, "mines"), coords(o, "tnts"), coords3d(o, "enderpearls"),
                        coords2d(o, "enderpearlangles"),
                        secType, secLoc));
                }
                into.put(entry.getKey().toLowerCase(Locale.ROOT), variants);
            }
        } catch (Exception e) {
            ConstellationClient.LOGGER.error("failed loading {}", file, e);
        }
    }

    private static List<int[]> coords(JsonObject o, String key) {
        List<int[]> out = new ArrayList<>();
        if (!o.has(key) || !o.get(key).isJsonArray()) return out;
        for (var el : o.getAsJsonArray(key)) {
            if (el.isJsonArray() && el.getAsJsonArray().size() >= 3) out.add(triple(el.getAsJsonArray()));
        }
        return out;
    }

    private static List<double[]> coords3d(JsonObject o, String key) {
        List<double[]> out = new ArrayList<>();
        if (!o.has(key) || !o.get(key).isJsonArray()) return out;
        for (var el : o.getAsJsonArray(key)) {
            if (!el.isJsonArray() || el.getAsJsonArray().size() < 3) continue;
            JsonArray a = el.getAsJsonArray();
            out.add(new double[]{a.get(0).getAsDouble(), a.get(1).getAsDouble(), a.get(2).getAsDouble()});
        }
        return out;
    }

    private static List<double[]> coords2d(JsonObject o, String key) {
        List<double[]> out = new ArrayList<>();
        if (!o.has(key) || !o.get(key).isJsonArray()) return out;
        for (var el : o.getAsJsonArray(key)) {
            if (!el.isJsonArray() || el.getAsJsonArray().size() < 2) continue;
            JsonArray a = el.getAsJsonArray();
            out.add(new double[]{a.get(0).getAsDouble(), a.get(1).getAsDouble()});
        }
        return out;
    }

    private static int[] triple(JsonArray a) {
        return new int[]{ a.get(0).getAsInt(), a.get(1).getAsInt(), a.get(2).getAsInt() };
    }

    private static void loadSecrets() {
        try (InputStream in = res("secretlocations.json")) {
            if (in == null) return;
            SECRETS.clear();
            JsonObject root = new Gson().fromJson(new InputStreamReader(in, StandardCharsets.UTF_8), JsonObject.class);
            for (var entry : root.entrySet()) {
                if (!entry.getValue().isJsonArray()) continue;
                JsonArray arr = entry.getValue().getAsJsonArray();
                List<Secret> list = new ArrayList<>();
                for (var el : arr) {
                    JsonObject o = el.getAsJsonObject();
                    list.add(new Secret(
                        o.has("category") ? o.get("category").getAsString() : "secret",
                        o.has("secretName") ? o.get("secretName").getAsString() : null,
                        o.get("x").getAsInt(), o.get("y").getAsInt(), o.get("z").getAsInt()));
                }
                SECRETS.put(entry.getKey().toLowerCase(Locale.ROOT), list);
            }
        } catch (Exception e) {
            ConstellationClient.LOGGER.error("failed loading secret locations", e);
        }
    }

    private static InputStream res(String path) {
        return DungeonData.class.getResourceAsStream("/assets/constellation/dungeons/" + path);
    }

    public static List<Secret> secretsFor(String roomName) {
        if (roomName == null) return List.of();
        List<Secret> secrets = SECRETS.get(roomName.toLowerCase(Locale.ROOT));
        if (secrets == null) {
            ConstellationClient.LOGGER.debug("matched room has no secret entry: {}", roomName);
            return List.of();
        }
        return secrets;
    }

    public static List<SecretWaypoint> secretsFor(String roomName, RoomTransform.Direction dir,
                                                   int anchorX, int anchorZ, int currentFloorY) {
        List<Secret> secrets = secretsFor(roomName);
        if (secrets.isEmpty()) return List.of();
        List<SecretWaypoint> waypoints = new ArrayList<>(secrets.size());
        for (Secret secret : secrets) {
            int y = secret.y() - SECRET_SOURCE_FLOOR_Y + currentFloorY;
            long[] world = RoomTransform.relativeToActual(dir, anchorX, anchorZ, secret.x(), y, secret.z());
            waypoints.add(new SecretWaypoint(secret.category(), secret.name(),
                (int) world[0], (int) world[1], (int) world[2]));
        }
        return List.copyOf(waypoints);
    }

    public static boolean isLoaded() { return loaded; }
}
