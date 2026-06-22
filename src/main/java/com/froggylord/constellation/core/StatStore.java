package com.froggylord.constellation.core;

import com.froggylord.constellation.ConstellationClient;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;

import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * A tiny persistent key/number store that survives across sessions and profiles. Trackers that
 * want a lifetime total (slayer PB, kill counts, profit) write here instead of (or alongside) an
 * in-memory session counter. Backed by config/constellation-stats.json, flushed on a debounce so
 * a busy counter doesn't hammer the disk.
 */
public final class StatStore {

    private StatStore() {}

    private static final Path FILE = Path.of("config", "constellation-stats.json");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static JsonObject data = new JsonObject();
    private static boolean loaded = false;
    private static volatile boolean dirty = false;

    public static void init() {
        load();
        // flush every 5s if something changed, and once more on the way out
        ConstellationClient.tick().every(100, "statstore-flush", StatStore::flush);
        Runtime.getRuntime().addShutdownHook(new Thread(StatStore::saveNow, "constellation-stats-save"));
    }

    private static void load() {
        if (loaded) return;
        loaded = true;
        try {
            if (Files.exists(FILE)) {
                try (Reader r = Files.newBufferedReader(FILE)) {
                    JsonObject o = GSON.fromJson(r, JsonObject.class);
                    if (o != null) data = o;
                }
            }
        } catch (Exception e) {
            ConstellationClient.LOGGER.error("Failed to load stats store", e);
        }
    }

    private static void flush() {
        if (dirty) saveNow();
    }

    private static synchronized void saveNow() {
        if (!dirty) return;
        try {
            Files.createDirectories(FILE.getParent());
            try (Writer w = Files.newBufferedWriter(FILE)) { GSON.toJson(data, w); }
            dirty = false;
        } catch (Exception e) {
            ConstellationClient.LOGGER.error("Failed to save stats store", e);
        }
    }

    public static synchronized long getLong(String key, long def) {
        if (!loaded) load();
        return data.has(key) ? data.get(key).getAsLong() : def;
    }

    public static synchronized double getDouble(String key, double def) {
        if (!loaded) load();
        return data.has(key) ? data.get(key).getAsDouble() : def;
    }

    public static synchronized void putLong(String key, long v) {
        if (!loaded) load();
        data.addProperty(key, v);
        dirty = true;
    }

    public static synchronized void putDouble(String key, double v) {
        if (!loaded) load();
        data.addProperty(key, v);
        dirty = true;
    }

    /** add delta to a running total, returns the new value. */
    public static synchronized long add(String key, long delta) {
        long v = getLong(key, 0) + delta;
        putLong(key, v);
        return v;
    }

    public static synchronized double addDouble(String key, double delta) {
        double v = getDouble(key, 0) + delta;
        putDouble(key, v);
        return v;
    }

    /** keep the smallest value seen (best time). returns the stored best. */
    public static synchronized long recordBest(String key, long candidate) {
        long cur = getLong(key, Long.MAX_VALUE);
        if (candidate < cur) { putLong(key, candidate); return candidate; }
        return cur;
    }

    /** keep the largest value seen (high score). returns the stored max. */
    public static synchronized long recordMax(String key, long candidate) {
        long cur = getLong(key, Long.MIN_VALUE);
        if (candidate > cur) { putLong(key, candidate); return candidate; }
        return cur;
    }

    public static boolean has(String key) { return loaded && data.has(key); }
}
