package com.froggylord.constellation.data;

import com.froggylord.constellation.ConstellationClient;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.InflaterInputStream;

// ported from Skyblocker (LGPL): de/hysky/skyblocker/skyblock/dungeon/secrets/DungeonManager.java
// — NUMERIC_ID map, readRoom(), the ROOMS_DATA load-into-shapes logic
public final class DungeonRoomData {

    // ported from Skyblocker (LGPL): DungeonManager.NUMERIC_ID
    // maps block registry names to byte ids used in .skeleton files
    public static final Map<String, Byte> NUMERIC_ID = Map.ofEntries(
        Map.entry("minecraft:stone", (byte) 1),
        Map.entry("minecraft:diorite", (byte) 2),
        Map.entry("minecraft:polished_diorite", (byte) 3),
        Map.entry("minecraft:andesite", (byte) 4),
        Map.entry("minecraft:polished_andesite", (byte) 5),
        Map.entry("minecraft:grass_block", (byte) 6),
        Map.entry("minecraft:dirt", (byte) 7),
        Map.entry("minecraft:coarse_dirt", (byte) 8),
        Map.entry("minecraft:cobblestone", (byte) 9),
        Map.entry("minecraft:bedrock", (byte) 10),
        Map.entry("minecraft:oak_leaves", (byte) 11),
        Map.entry("minecraft:gray_wool", (byte) 12),
        Map.entry("minecraft:double_stone_slab", (byte) 13),
        Map.entry("minecraft:mossy_cobblestone", (byte) 14),
        Map.entry("minecraft:clay", (byte) 15),
        Map.entry("minecraft:stone_bricks", (byte) 16),
        Map.entry("minecraft:mossy_stone_bricks", (byte) 17),
        Map.entry("minecraft:chiseled_stone_bricks", (byte) 18),
        Map.entry("minecraft:gray_terracotta", (byte) 19),
        Map.entry("minecraft:cyan_terracotta", (byte) 20),
        Map.entry("minecraft:black_terracotta", (byte) 21)
    );

    // ported from Skyblocker (LGPL): DungeonManager.ROOMS_DATA
    // dungeon → shape → roomName → sorted int[] of encoded blocks
    // we only load "catacombs" dungeon
    public static final Map<String, Map<String, int[]>> ROOMS_DATA = new LinkedHashMap<>();

    private static volatile boolean loaded = false;
    private static int roomCount = 0;

    // ---- block encoding (same bit packing as Skyblocker posIdToInt) ----
    // ported from Skyblocker (LGPL): Room.posIdToInt
    public static int posIdToInt(int relX, int relY, int relZ, byte id) {
        return (relX << 24) | (relY << 16) | (relZ << 8) | (id & 0xFF);
    }

    public static int idX(int v) { return (v >>> 24) & 0xFF; }
    public static int idY(int v) { return (v >>> 16) & 0xFF; }
    public static int idZ(int v) { return (v >>>  8) & 0xFF; }
    public static int idBlock(int v) { return v & 0xFF; }

    // ported from Skyblocker (LGPL): DungeonManager.readRoom
    // reads a zlib-compressed Java-serialized int[] from a .skeleton resource
    private static int[] readRoom(InputStream in) throws IOException {
        try (ObjectInputStream ois = new ObjectInputStream(new InflaterInputStream(in))) {
            return (int[]) ois.readObject();
        } catch (ClassNotFoundException e) {
            throw new IOException("corrupt .skeleton (not an int[])", e);
        }
    }

    public static void load() {
        if (loaded) return;
        long start = System.currentTimeMillis();
        try {
            InputStream indexIn = DungeonRoomData.class.getResourceAsStream(
                "/assets/constellation/dungeons/index.txt");
            if (indexIn == null)
                throw new FileNotFoundException("dungeons/index.txt missing");
            List<String> lines;
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(indexIn, StandardCharsets.UTF_8))) {
                lines = reader.lines()
                    .map(String::trim)
                    .filter(l -> !l.isEmpty() && !l.startsWith("#"))
                    .toList();
            }

            for (String line : lines) {
                // line format: "shape/roomName" e.g. "1x1/admin-0"
                int slash = line.indexOf('/');
                if (slash < 0) continue;
                String shape = line.substring(0, slash);
                String room = line.substring(slash + 1);
                String resourcePath = "/assets/constellation/dungeons/catacombs/"
                    + shape + "/" + room + ".skeleton";

                try (InputStream skelIn = DungeonRoomData.class.getResourceAsStream(resourcePath)) {
                    if (skelIn == null) {
                        ConstellationClient.LOGGER.warn(
                            "[rooms] missing .skeleton: {}", resourcePath);
                        continue;
                    }
                    int[] blocks = readRoom(skelIn);
                    ROOMS_DATA.computeIfAbsent(shape, k -> new LinkedHashMap<>())
                        .put(room, blocks);
                    roomCount++;
                }
            }
        } catch (Exception e) {
            ConstellationClient.LOGGER.error("[rooms] failed loading .skeleton data", e);
        }
        loaded = true;
        ConstellationClient.LOGGER.info(
            "[rooms] loaded {} rooms across {} shapes in {}ms",
            roomCount, ROOMS_DATA.size(), System.currentTimeMillis() - start);
    }

    public static boolean isLoaded() { return loaded; }
    public static int roomCount() { return roomCount; }

    /** returns the rooms data for a given shape, or empty map if none */
    public static Map<String, int[]> roomsForShape(String shape) {
        return ROOMS_DATA.getOrDefault(shape, Collections.emptyMap());
    }

    /** returns the numeric block id, or 0 if not mapped */
    public static byte numericId(String blockKey) {
        Byte b = NUMERIC_ID.get(blockKey);
        return b == null ? 0 : b;
    }
}
