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

    private static volatile boolean loaded = false;

    public record Secret(String category, String name, int x, int y, int z) {}

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
                        int[] fp = normalizeY((int[]) in.readObject());
                        ROOMS.computeIfAbsent(shape, k -> new HashMap<>()).put(name, fp);
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

    /** Shift every entry's Y down so the room floor sits at 0, then re-sort for binary search. */
    private static int[] normalizeY(int[] fp) {
        int minY = 255;
        for (int v : fp) { int y = (v >>> 16) & 0xFF; if (y < minY) minY = y; }
        if (minY == 0) { Arrays.sort(fp); return fp; }
        int[] out = new int[fp.length];
        for (int i = 0; i < fp.length; i++) {
            int v = fp[i];
            out[i] = (((v >>> 24) & 0xFF) << 24) | ((((v >>> 16) & 0xFF) - minY) << 16)
                   | (((v >>> 8) & 0xFF) << 8) | (v & 0xFF);
        }
        Arrays.sort(out);
        return out;
    }

    public static List<Secret> secretsFor(String roomName) {
        return SECRETS.get(roomName.toLowerCase(Locale.ROOT));
    }

    public static boolean isLoaded() { return loaded; }
}
