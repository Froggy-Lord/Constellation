package com.froggylord.constellation.data;

import com.froggylord.constellation.ConstellationClient;
import net.minecraft.resources.Identifier;

import java.io.*;
import java.util.*;
import java.util.zip.InflaterInputStream;

/**
 * Loads room skeletons, secret locations, and route data from bundled assets.
 * Skeletons are binary int[] arrays (compressed with InflaterOutputStream).
 * Secrets and routes are JSON.
 */
public class DungeonData {

    private static final String BASE = "dungeons";

    // roomName -> int[] skeleton fingerprint
    public static final Map<String, int[]> skeletons = new LinkedHashMap<>();
    // roomName -> index entry (shape hint for fast filtering)
    public static final Map<String, RoomIndex> index = new LinkedHashMap<>();
    // roomName -> list of secret entries
    public static final Map<String, List<SecretEntry>> secrets = new LinkedHashMap<>();
    // roomName -> route (list of segments)
    public static final Map<String, RouteEntry> routes = new LinkedHashMap<>();
    // roomName -> pearl route
    public static final Map<String, RouteEntry> pearlRoutes = new LinkedHashMap<>();

    private static boolean loaded = false;

    public record RoomIndex(String name, int maxX, int maxZ, int count) {}
    public record SecretEntry(int x, int y, int z, String type, String name) {}
    public record RouteEntry(String room, List<RouteSegment> segments) {}
    public record RouteSegment(List<RoutePoint> path, List<RoutePoint> etherwarps,
                                List<RoutePoint> mines, List<RoutePoint> interacts,
                                List<RoutePoint> tnts, List<RoutePoint> pearls,
                                SecretEntry secret) {}
    public record RoutePoint(int x, int y, int z) {}

    public static void load() {
        if (loaded) return;
        loaded = true;
        long start = System.currentTimeMillis();

        loadIndex();
        loadSecrets();
        loadRoutes("routes.json", routes);
        loadRoutes("pearlroutes.json", pearlRoutes);

        ConstellationClient.LOGGER.info("Dungeon data: {} room skeletons, {} secret sets, {} routes ({}ms)",
            index.size(), secrets.size(), routes.size(), System.currentTimeMillis() - start);
    }

    private static void loadIndex() {
        try (var in = new BufferedReader(new InputStreamReader(
                DungeonData.class.getClassLoader().getResourceAsStream(
                    "assets/constellation/" + BASE + "/index.txt")))) {
            String line;
            while ((line = in.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) continue;
                String[] parts = line.split(" ");
                if (parts.length >= 4) {
                    index.put(parts[0], new RoomIndex(parts[0],
                        Integer.parseInt(parts[1]), Integer.parseInt(parts[2]),
                        Integer.parseInt(parts[3])));
                }
            }
        } catch (Exception e) {
            ConstellationClient.LOGGER.error("Failed to load dungeon index", e);
        }
    }

    public static int[] loadSkeleton(String name) {
        if (skeletons.containsKey(name)) return skeletons.get(name);
        try (var in = new DataInputStream(new InflaterInputStream(
                DungeonData.class.getClassLoader().getResourceAsStream(
                    "assets/constellation/" + BASE + "/catacombs/" + name + ".skeleton")))) {
            int count = in.readInt();
            int[] data = new int[count];
            for (int i = 0; i < count; i++) data[i] = in.readInt();
            skeletons.put(name, data);
            return data;
        } catch (Exception e) {
            return null;
        }
    }

    private static void loadSecrets() {
        try (var in = new InputStreamReader(
                DungeonData.class.getClassLoader().getResourceAsStream(
                    "assets/constellation/" + BASE + "/secretlocations.json"))) {
            // simple line-based format: room:x,y,z,type,name
            // actual format from DungeonRooms mod is JSON — parse accordingly
            // for now, stub
        } catch (Exception e) {
            ConstellationClient.LOGGER.error("Failed to load secret locations", e);
        }
    }

    @SuppressWarnings("unchecked")
    private static void loadRoutes(String file, Map<String, RouteEntry> target) {
        try (var in = new InputStreamReader(
                DungeonData.class.getClassLoader().getResourceAsStream(
                    "assets/constellation/" + BASE + "/" + file))) {
            // JSON format: { "roomName": [ { "locations": [[x,y,z],...], "etherwarps": [...], ... } ] }
            // stub — parse later with Gson
        } catch (Exception e) {
            ConstellationClient.LOGGER.info("No {} found (expected if routes not bundled yet)", file);
        }
    }

    public static List<SecretEntry> getSecrets(String room) {
        return secrets.getOrDefault(room, Collections.emptyList());
    }

    public static RouteEntry getRoute(String room, boolean preferPearl) {
        if (preferPearl && pearlRoutes.containsKey(room)) return pearlRoutes.get(room);
        return routes.get(room);
    }

    public static boolean hasRoute(String room) { return routes.containsKey(room); }
    public static boolean hasPearlRoute(String room) { return pearlRoutes.containsKey(room); }
}
