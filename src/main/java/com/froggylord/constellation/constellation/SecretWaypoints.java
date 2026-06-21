package com.froggylord.constellation.constellation;

import com.froggylord.constellation.ConstellationClient;
import com.froggylord.constellation.config.OrionConfig;
import com.froggylord.constellation.data.DungeonData;
import com.froggylord.constellation.data.RoomMatch;
import com.froggylord.constellation.data.RoomTransform;
import com.froggylord.constellation.render.WorldRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.*;

/**
 * Draws the bundled secret locations for whatever room you're standing in, colour-coded by
 * type. The DB stores each secret room-relative (NW corner, canonical rotation) so we run it
 * back through the same rotation transform the matcher uses to land them in the world.
 *
 * Reveal modes mirror the usual dungeon-mod behaviour: show everything, reveal-as-you-approach,
 * or one-at-a-time. Secrets count as collected when you get close to them — a rough stand-in
 * until per-type collection (chest open, bat kill, etc.) lands.
 */
public final class SecretWaypoints {

    private static final double REACH = 4.0;    // collected when you're this close
    private static final double REVEAL = 14.0;  // progressive-reveal radius

    private static String room = "";
    private static int anchorX, anchorZ;
    private static RoomTransform.Direction dir = RoomTransform.Direction.NW;
    private static final List<Wp> wps = new ArrayList<>();
    private static final Set<Integer> collected = new HashSet<>();

    private record Wp(int idx, int x, int y, int z, String category) {}

    private SecretWaypoints() {}

    /** Number of secrets collected / total in the current room (for the HUD). */
    public static int collectedCount() { return collected.size(); }
    public static int totalCount() { return wps.size(); }

    public static void draw(WorldRenderer.Ctx ctx) {
        OrionConfig cfg = ConstellationClient.cfg().orion;
        if (cfg == null || !cfg.secretWaypoints) return;
        if (!ConstellationClient.loc().inDungeons() || !RoomMatch.isMatched()) { room = ""; wps.clear(); return; }

        sync();
        if (wps.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        Vec3 pp = mc.player.position();

        // proximity collection
        for (Wp w : wps) {
            if (collected.contains(w.idx())) continue;
            if (dist2(pp, w) <= REACH * REACH) {
                collected.add(w.idx());
                if (cfg.echoOnCollect) mc.player.playSound(SoundEvents.NOTE_BLOCK_PLING.value(), 0.6f, 1.6f);
            }
        }

        // one-at-a-time: only the nearest uncollected secret
        Wp only = null;
        if (cfg.oneSecretAtATime) {
            double best = Double.MAX_VALUE;
            for (Wp w : wps) {
                if (collected.contains(w.idx())) continue;
                double d = dist2(pp, w);
                if (d < best) { best = d; only = w; }
            }
        }

        for (Wp w : wps) {
            if (collected.contains(w.idx())) continue;
            if (only != null && w != only) continue;
            if (cfg.progressiveReveal && only == null && dist2(pp, w) > REVEAL * REVEAL) continue;
            AABB box = new AABB(w.x(), w.y(), w.z(), w.x() + 1, w.y() + 1, w.z() + 1);
            ctx.highlight(box, colourFor(w.category()), true);
        }
    }

    /** Recompute world positions when the room (or its anchor/rotation) changes. */
    private static void sync() {
        String cur = RoomMatch.currentRoom();
        int ax = RoomMatch.anchorX(), az = RoomMatch.anchorZ();
        RoomTransform.Direction d = RoomMatch.currentDir();
        if (cur.equals(room) && ax == anchorX && az == anchorZ && d == dir) return;

        room = cur; anchorX = ax; anchorZ = az; dir = d;
        wps.clear(); collected.clear();
        List<DungeonData.Secret> secrets = DungeonData.SECRETS.get(cur.toLowerCase(Locale.ROOT));
        if (secrets == null) return;
        int i = 0;
        for (DungeonData.Secret s : secrets) {
            long[] w = RoomTransform.relativeToActual(dir, anchorX, anchorZ, s.x(), s.y(), s.z());
            wps.add(new Wp(i++, (int) w[0], (int) w[1], (int) w[2], s.category()));
        }
    }

    private static double dist2(Vec3 p, Wp w) {
        double dx = p.x - (w.x() + 0.5), dy = p.y - (w.y() + 0.5), dz = p.z - (w.z() + 0.5);
        return dx * dx + dy * dy + dz * dz;
    }

    // colour-coded by secret type (alpha 0xFF; the renderer caps the fill alpha itself)
    private static int colourFor(String cat) {
        if (cat == null) return 0xFFFFFF55;
        return switch (cat.toLowerCase(Locale.ROOT)) {
            case "chest" -> 0xFFFFAA00;                     // gold
            case "item" -> 0xFF55FFFF;                      // aqua
            case "bat" -> 0xFF5555FF;                       // blue
            case "wither", "wither_essence" -> 0xFFAA00FF;  // purple
            case "lever" -> 0xFF00FF88;                     // green
            case "fairysoul", "fairy" -> 0xFFFF66CC;        // pink
            case "superboom" -> 0xFFFF5500;                 // orange
            case "entrance" -> 0xFFFFFFFF;                  // white
            case "stonk", "pearl", "aotv" -> 0xFFAAFF00;    // lime
            default -> 0xFFFFFF55;                          // yellow
        };
    }
}
