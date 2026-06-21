package com.froggylord.constellation.constellation;

import com.froggylord.constellation.ConstellationClient;
import com.froggylord.constellation.config.OrionConfig;
import com.froggylord.constellation.data.DungeonData;
import com.froggylord.constellation.data.RoomMatch;
import com.froggylord.constellation.data.RoomTransform;
import com.froggylord.constellation.render.WorldRenderer;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.*;

/**
 * Draws secret routes for the current room — a walk path line through the route's waypoints,
 * plus typed action markers (etherwarp, interact, mine, superboom tnt, pearl) and the secret
 * at the end. Coords come from the bundled SecretRoutes data, room-relative, placed in the
 * world with the matcher's rotation transform like the plain secret waypoints.
 *
 * In a room that has a route, the route takes over from the plain secret boxes so you're not
 * looking at two overlays at once.
 */
public final class Routes {

    private static String room = "";
    private static int ax, az;
    private static RoomTransform.Direction dir = RoomTransform.Direction.NW;
    private static final List<RouteR> routes = new ArrayList<>();

    private record Marker(int x, int y, int z, int colour) {}
    private record RouteR(List<Vec3> path, List<Marker> markers) {}

    private Routes() {}

    /** True if the current route source has an entry for this room (used for waypoint takeover). */
    public static boolean hasRouteFor(String roomName) {
        OrionConfig cfg = ConstellationClient.cfg().orion;
        if (cfg == null || roomName == null || roomName.isEmpty()) return false;
        Map<String, List<DungeonData.Route>> src = cfg.pearlRoutes ? DungeonData.PEARL_ROUTES : DungeonData.ROUTES;
        return src.containsKey(roomName.toLowerCase(Locale.ROOT));
    }

    public static void draw(WorldRenderer.Ctx ctx) {
        OrionConfig cfg = ConstellationClient.cfg().orion;
        if (cfg == null || !cfg.routes) return;
        if (!ConstellationClient.loc().inDungeons() || !RoomMatch.isMatched()) { room = ""; routes.clear(); return; }

        sync(cfg);
        if (routes.isEmpty()) return;

        for (RouteR r : routes) {
            if (cfg.routeLines) {
                for (int i = 0; i + 1 < r.path().size(); i++) {
                    ctx.line(r.path().get(i), r.path().get(i + 1), 0xFFFFFFFF, true);
                }
            }
            for (Marker m : r.markers()) {
                ctx.highlight(new AABB(m.x(), m.y(), m.z(), m.x() + 1, m.y() + 1, m.z() + 1), m.colour(), true);
            }
        }
    }

    private static void sync(OrionConfig cfg) {
        String cur = RoomMatch.currentRoom();
        int x = RoomMatch.anchorX(), z = RoomMatch.anchorZ();
        RoomTransform.Direction d = RoomMatch.currentDir();
        if (cur.equals(room) && x == ax && z == az && d == dir) return;

        room = cur; ax = x; az = z; dir = d;
        routes.clear();
        Map<String, List<DungeonData.Route>> source = cfg.pearlRoutes ? DungeonData.PEARL_ROUTES : DungeonData.ROUTES;
        List<DungeonData.Route> src = source.get(cur.toLowerCase(Locale.ROOT));
        if (src == null) return;

        for (DungeonData.Route rt : src) {
            List<Vec3> path = new ArrayList<>();
            for (int[] p : rt.locations()) {
                long[] w = tf(p);
                path.add(new Vec3(w[0] + 0.5, w[1] + 0.5, w[2] + 0.5));
            }
            List<Marker> markers = new ArrayList<>();
            for (int[] p : rt.etherwarps()) markers.add(marker(p, 0xFF55FFFF)); // aqua
            for (int[] p : rt.interacts()) markers.add(marker(p, 0xFF55FF55));  // lime
            for (int[] p : rt.mines()) markers.add(marker(p, 0xFFFFAA00));      // orange
            for (int[] p : rt.tnts()) markers.add(marker(p, 0xFFFF4444));       // superboom red
            for (int[] p : rt.pearls()) markers.add(marker(p, 0xFFAA44FF));     // pearl purple
            if (rt.secret() != null) markers.add(marker(rt.secret(), secretColour(rt.secretType())));
            routes.add(new RouteR(path, markers));
        }
    }

    private static Marker marker(int[] p, int colour) {
        long[] w = tf(p);
        return new Marker((int) w[0], (int) w[1], (int) w[2], colour);
    }

    private static long[] tf(int[] p) {
        return RoomTransform.relativeToActual(dir, ax, az, p[0], p[1], p[2]);
    }

    private static int secretColour(String type) {
        if (type == null) return 0xFFFFFF55;
        return switch (type.toLowerCase(Locale.ROOT)) {
            case "chest" -> 0xFFFFAA00;
            case "item" -> 0xFF55FFFF;
            case "bat" -> 0xFF5555FF;
            case "wither" -> 0xFFAA00FF;
            case "interact", "lever" -> 0xFF55FF55;
            default -> 0xFFFFFF55;
        };
    }
}
