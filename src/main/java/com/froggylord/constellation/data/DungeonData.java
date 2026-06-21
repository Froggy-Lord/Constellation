package com.froggylord.constellation.data;

import com.froggylord.constellation.ConstellationClient;
import com.google.gson.*;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.InflaterInputStream;

/**
 * Loads the bundled dungeon room database (skeletons + secrets + routes).
 *
 * The skeleton format is the shared DungeonRooms community format: a Java-serialized
 * int[] per room, deflate-compressed, where each int packs a block position + numeric
 * block id as (relX<<24 | worldY<<16 | relZ<<8 | id). The 21-entry block id table and
 * the floor-relative Y normalisation are part of that on-disk format spec.
 */
public class DungeonData {

    // shape (e.g. "1x1") -> roomName -> sorted fingerprint
    public static final Map<String, Map<String, int[]>> ROOMS = new HashMap<>();
    // roomName -> secrets list
    public static final Map<String, List<Secret>> SECRETS = new HashMap<>();
    // flat candidate list with precomputed local dims (max relX / relZ), for fit-filtering
    public static final List<Candidate> CANDIDATES = new ArrayList<>();

    private static volatile boolean loaded = false;

    public record Secret(String category, String name, int x, int y, int z) {}

    /** A room fingerprint with its local footprint dims (max relX, relZ). */
    public record Candidate(String name, int[] fp, int rx, int rz) {}

    // ---- block id table (the on-disk format's fixed numeric ids; 0 = untracked) ----
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
        loaded = true;
        int total = ROOMS.values().stream().mapToInt(Map::size).sum();
        ConstellationClient.LOGGER.info("Dungeon data: {} rooms ({} with secrets) in {}ms",
            total, SECRETS.size(), System.currentTimeMillis() - start);
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
                        // skeletons use ABSOLUTE world Y (dungeon floor sits at a fixed height),
                        // so no normalization — match world blocks against absolute Y directly,
                        // the way Skyblocker does. just sort for binary search.
                        int[] fp = (int[]) in.readObject();
                        java.util.Arrays.sort(fp);
                        ROOMS.computeIfAbsent(shape, k -> new HashMap<>()).put(name, fp);
                        // compute local footprint dims for fit-filtering
                        int mx = 0, mz = 0;
                        for (int v : fp) {
                            int x = idX(v), z = idZ(v);
                            if (x > mx) mx = x;
                            if (z > mz) mz = z;
                        }
                        CANDIDATES.add(new Candidate(name, fp, mx, mz));
                    }
                } catch (Exception e) {
                    // skip unreadable rooms
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

    // ---- format helpers (the on-disk encoding) ----

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
