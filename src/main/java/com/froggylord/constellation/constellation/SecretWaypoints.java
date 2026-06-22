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

    public static int collectedCount() { return collected.size(); }
    public static int totalCount() { return wps.size(); }

    public static void draw(WorldRenderer.Ctx ctx) {
        OrionConfig cfg = ConstellationClient.cfg().orion;
        if (cfg == null || !cfg.secretWaypoints) return;
        if (!ConstellationClient.loc().inDungeons() || !RoomMatch.isMatched()) { room = ""; wps.clear(); return; }
        
        if (cfg.routes && Routes.hasRouteFor(RoomMatch.currentRoom())) return;

        sync();
        if (wps.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        Vec3 pp = mc.player.position();

        
        for (Wp w : wps) {
            if (collected.contains(w.idx())) continue;
            if (dist2(pp, w) <= REACH * REACH) {
                collected.add(w.idx());
                if (cfg.echoOnCollect) mc.player.playSound(SoundEvents.NOTE_BLOCK_PLING.value(), 0.6f, 1.6f);
            }
        }

        
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

    
    private static int colourFor(String cat) {
        if (cat == null) return 0xFFFFFF55;
        return switch (cat.toLowerCase(Locale.ROOT)) {
            case "chest" -> 0xFFFFAA00;                     
            case "item" -> 0xFF55FFFF;                      // aqua
            case "bat" -> 0xFF5555FF;                       
            case "wither", "wither_essence" -> 0xFFAA00FF;  
            case "lever" -> 0xFF00FF88;                     
            case "fairysoul", "fairy" -> 0xFFFF66CC;        // pink
            case "superboom" -> 0xFFFF5500;                 
            case "entrance" -> 0xFFFFFFFF;                  
            case "stonk", "pearl", "aotv" -> 0xFFAAFF00;    
            default -> 0xFFFFFF55;                          
        };
    }
}
