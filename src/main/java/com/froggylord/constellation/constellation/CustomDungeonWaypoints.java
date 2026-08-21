package com.froggylord.constellation.constellation;

import com.froggylord.constellation.ConstellationClient;
import com.froggylord.constellation.data.RoomMatch;
import com.froggylord.constellation.data.RoomTransform;
import com.froggylord.constellation.render.ConstellationTheme;
import com.froggylord.constellation.render.WorldRenderer;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

// ported from devonian (GPL-3.0): features/dungeons/CustomDungeonWaypoints.kt
public final class CustomDungeonWaypoints {
    private static final Path PATH = Path.of("config", "constellation-custom-dungeon-waypoints.json");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Map<String, List<SavedWaypoint>> ROOMS = new HashMap<>();
    private static boolean loaded;

    public record SavedWaypoint(int x, int y, int z, String name) {}

    private CustomDungeonWaypoints() {}

    public static void init() {
        if (loaded) return;
        loaded = true;
        if (!Files.exists(PATH)) return;
        try (Reader reader = Files.newBufferedReader(PATH)) {
            Data data = GSON.fromJson(reader, Data.class);
            if (data != null && data.rooms != null) ROOMS.putAll(data.rooms);
        } catch (Exception e) {
            ConstellationClient.LOGGER.warn("Could not load custom dungeon waypoints", e);
        }
    }

    public static void draw(WorldRenderer.Ctx ctx) {
        if (!RoomMatch.isMatched()) return;
        List<SavedWaypoint> waypoints = ROOMS.get(RoomMatch.currentRoom());
        if (waypoints == null) return;
        var dungeon = ConstellationClient.dungeon();
        for (SavedWaypoint waypoint : waypoints) {
            long[] pos = RoomTransform.relativeToActual(dungeon.roomDirection(), dungeon.roomCornerX(),
                dungeon.roomCornerZ(), waypoint.x(), waypoint.y(), waypoint.z());
            AABB box = new AABB(pos[0], pos[1], pos[2], pos[0] + 1, pos[1] + 1, pos[2] + 1);
            ctx.highlight(box, ConstellationTheme.AQUA, true);
            ctx.label(new Vec3(pos[0] + .5, pos[1] + 1.45, pos[2] + .5),
                waypoint.name(), ConstellationTheme.AQUA, true);
        }
    }

    public static int add(String name) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || !RoomMatch.isMatched()) return fail("Enter a matched dungeon room first.");
        var dungeon = ConstellationClient.dungeon();
        var pos = mc.player.blockPosition();
        long[] rel = RoomTransform.actualToRelative(dungeon.roomDirection(), dungeon.roomCornerX(),
            dungeon.roomCornerZ(), pos.getX(), pos.getY(), pos.getZ());
        ROOMS.computeIfAbsent(RoomMatch.currentRoom(), ignored -> new ArrayList<>())
            .add(new SavedWaypoint((int) rel[0], (int) rel[1], (int) rel[2], cleanName(name)));
        save();
        return ok("Added waypoint " + cleanName(name) + ".");
    }

    public static int removeNearest() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || !RoomMatch.isMatched()) return fail("Enter a matched dungeon room first.");
        List<SavedWaypoint> waypoints = ROOMS.get(RoomMatch.currentRoom());
        if (waypoints == null || waypoints.isEmpty()) return fail("This room has no custom waypoints.");
        var dungeon = ConstellationClient.dungeon();
        SavedWaypoint nearest = null;
        double best = 36;
        for (SavedWaypoint waypoint : waypoints) {
            long[] pos = RoomTransform.relativeToActual(dungeon.roomDirection(), dungeon.roomCornerX(),
                dungeon.roomCornerZ(), waypoint.x(), waypoint.y(), waypoint.z());
            double distance = mc.player.distanceToSqr(pos[0] + .5, pos[1] + .5, pos[2] + .5);
            if (distance < best) { best = distance; nearest = waypoint; }
        }
        if (nearest == null) return fail("No custom waypoint within 6 blocks.");
        waypoints.remove(nearest);
        save();
        return ok("Removed waypoint " + nearest.name() + ".");
    }

    public static int list() {
        if (!RoomMatch.isMatched()) return fail("Enter a matched dungeon room first.");
        List<SavedWaypoint> waypoints = ROOMS.getOrDefault(RoomMatch.currentRoom(), List.of());
        message("§bCustom waypoints for " + RoomMatch.currentRoom() + ": §f" + waypoints.size());
        for (int i = 0; i < waypoints.size(); i++) {
            SavedWaypoint w = waypoints.get(i);
            message("§7" + (i + 1) + ". §f" + w.name() + " §8(" + w.x() + ", " + w.y() + ", " + w.z() + ")");
        }
        return 1;
    }

    public static int exportRoom() {
        if (!RoomMatch.isMatched()) return fail("Enter a matched dungeon room first.");
        String json = GSON.toJson(ROOMS.getOrDefault(RoomMatch.currentRoom(), List.of()));
        Minecraft.getInstance().keyboardHandler.setClipboard(json);
        return ok("Copied this room's custom waypoints to the clipboard.");
    }

    public static int importRoom(String json) {
        if (!RoomMatch.isMatched()) return fail("Enter a matched dungeon room first.");
        try {
            SavedWaypoint[] parsed = GSON.fromJson(json, SavedWaypoint[].class);
            if (parsed == null || parsed.length > 256) return fail("Invalid waypoint data.");
            List<SavedWaypoint> clean = new ArrayList<>();
            for (SavedWaypoint waypoint : parsed) {
                if (waypoint == null || Math.abs(waypoint.x()) > 256 || Math.abs(waypoint.z()) > 256
                    || waypoint.y() < -64 || waypoint.y() > 320) return fail("Waypoint coordinates are out of range.");
                clean.add(new SavedWaypoint(waypoint.x(), waypoint.y(), waypoint.z(), cleanName(waypoint.name())));
            }
            ROOMS.put(RoomMatch.currentRoom(), clean);
            save();
            return ok("Imported " + clean.size() + " custom waypoints.");
        } catch (JsonSyntaxException e) {
            return fail("Invalid waypoint JSON.");
        }
    }

    private static String cleanName(String name) {
        String clean = name == null ? "Waypoint" : name.replace('§', ' ').trim();
        if (clean.isEmpty()) clean = "Waypoint";
        return clean.length() > 48 ? clean.substring(0, 48) : clean;
    }

    private static void save() {
        try {
            Files.createDirectories(PATH.getParent());
            try (Writer writer = Files.newBufferedWriter(PATH)) { GSON.toJson(new Data(ROOMS), writer); }
        } catch (Exception e) {
            ConstellationClient.LOGGER.warn("Could not save custom dungeon waypoints", e);
        }
    }

    private static int ok(String text) { message("§a" + text); return 1; }
    private static int fail(String text) { message("§c" + text); return 0; }
    private static void message(String text) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) mc.player.sendSystemMessage(Component.literal(text));
    }

    private static final class Data {
        final Map<String, List<SavedWaypoint>> rooms;
        Data(Map<String, List<SavedWaypoint>> rooms) { this.rooms = rooms; }
    }
}
