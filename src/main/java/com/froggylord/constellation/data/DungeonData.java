package com.froggylord.constellation.data;

import com.froggylord.constellation.ConstellationClient;
import com.google.gson.*;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.InflaterInputStream;

public class DungeonData {

    
    public static final Map<String, Map<String, int[]>> ROOMS = new HashMap<>();
    
    public static final Map<String, List<Secret>> SECRETS = new HashMap<>();
    
    public static final Map<String, List<Route>> ROUTES = new HashMap<>();
    public static final Map<String, List<Route>> PEARL_ROUTES = new HashMap<>();
    // flat candidate list with preco...
    public static final List<Candidate> CANDIDATES = new ArrayList<>();

    private static volatile boolean loaded = false;

    public record Secret(String category, String name, int x, int y, int z) {}

    public record Route(List<int[]> locations, List<int[]> etherwarps, List<int[]> interacts,
                        List<int[]> mines, List<int[]> tnts, List<int[]> pearls,
                        String secretType, int[] secret) {}

    public record Candidate(String name, int[] fp, int rx, int rz) {}

    // ---- block id table (the on-di...
    private static final Map<String, Byte> NUMERIC_ID = new HashMap<>();
    static {
        String[] names = {
            "stone","diorite","polished_diorite","andesite","polished_andesite","grass_block",
            "dirt","coarse_dirt","cobblestone","bedrock","oak_leaves","gray_wool","double_stone_slab",
            "mossy_cobblestone","clay","stone_bricks","mossy_stone_bricks","chiseled_stone_bricks",
            "gray_terracotta","cyan_terracotta","black_terracotta"
        };
        for (int i = 0; i < names.length; i++) NUMERIC_ID.put("minecraft:" + names[i], (byte) (i + 1));
    }

    public static byte numericId(String blockKey) {
        Byte b = NUMERIC_ID.get(blockKey);
        return b == null ? 0 : b;
    }

    public static void load() {
        if (loaded) return;
        long start = System.currentTimeMillis();
        loadSkeletons();
        loadSecrets();
        loadRoutes("routes.json", ROUTES);
        loadRoutes("pearlroutes.json", PEARL_ROUTES);
        loaded = true;
        int total = ROOMS.values().stream().mapToInt(Map::size).sum();
        ConstellationClient.LOGGER.info("Dungeon data: {} rooms ({} with secrets, {} routed) in {}ms",
            total, SECRETS.size(), ROUTES.size(), System.currentTimeMillis() - start);
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
                        coords(o, "mines"), coords(o, "tnts"), coords(o, "enderpearls"),
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

    private static int[] triple(JsonArray a) {
        return new int[]{ a.get(0).getAsInt(), a.get(1).getAsInt(), a.get(2).getAsInt() };
    }

    private static void loadSkeletons() {
        try (InputStream idxIn = res("index.txt")) {
            if (idxIn == null) { ConstellationClient.LOGGER.error("room index missing"); return; }
            BufferedReader r = new BufferedReader(new InputStreamReader(idxIn, StandardCharsets.UTF_8));
            String line;
            while ((line = r.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) continue;
                int slash = line.indexOf('/');
                if (slash < 0) continue;
                String shape = line.substring(0, slash);
                String name = line.substring(slash + 1).toLowerCase(Locale.ROOT);
                try (InputStream sk = res("catacombs/" + line + ".skeleton")) {
                    if (sk == null) continue;
                    try (ObjectInputStream in = new ObjectInputStream(new InflaterInputStream(sk))) {
                        
                        
                        // the way skyblocker does. just ...
                        int[] fp = (int[]) in.readObject();
                        java.util.Arrays.sort(fp);
                        ROOMS.computeIfAbsent(shape, k -> new HashMap<>()).put(name, fp);
                        
                        int mx = 0, mz = 0;
                        for (int v : fp) {
                            int x = idX(v), z = idZ(v);
                            if (x > mx) mx = x;
                            if (z > mz) mz = z;
                        }
                        CANDIDATES.add(new Candidate(name, fp, mx, mz));
                    }
                } catch (Exception e) {
                    
                }
            }
        } catch (Exception e) {
            ConstellationClient.LOGGER.error("failed loading room skeletons", e);
        }
    }

    private static void loadSecrets() {
        try (InputStream in = res("secretlocations.json")) {
            if (in == null) return;
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

    

    public static int posIdToInt(int relX, int relY, int relZ, byte id) {
        return relX << 24 | relY << 16 | relZ << 8 | (id & 0xFF);
    }

    public static int idX(int v) { return (v >>> 24) & 0xFF; }
    public static int idY(int v) { return (v >>> 16) & 0xFF; }
    public static int idZ(int v) { return (v >>> 8) & 0xFF; }
    public static int idBlock(int v) { return v & 0xFF; }

    public static List<Secret> secretsFor(String roomName) {
        return SECRETS.get(roomName.toLowerCase(Locale.ROOT));
    }

    public static boolean isLoaded() { return loaded; }
}
