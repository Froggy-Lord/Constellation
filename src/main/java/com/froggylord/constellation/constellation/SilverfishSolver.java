package com.froggylord.constellation.constellation;

import com.froggylord.constellation.ConstellationClient;
import com.froggylord.constellation.config.OrionConfig;
import com.froggylord.constellation.render.WorldRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.monster.Silverfish;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.*;

// silverfish maze. the fish runs to a button — trace the actual corridor path with bfs
// through walkable blocks (not a straight line through walls). my own pathfind.
public final class SilverfishSolver {

    private SilverfishSolver() {}

    public static void draw(WorldRenderer.Ctx ctx) {
        OrionConfig cfg = ConstellationClient.cfg().orion;
        if (cfg == null || !cfg.silverfishSolver) return;
        if (!ConstellationClient.loc().inDungeons()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;

        Silverfish fish = null;
        double best = 900;
        for (var e : mc.level.entitiesForRendering()) {
            if (e instanceof Silverfish sf) {
                double d = e.distanceToSqr(mc.player.position());
                if (d < best) { best = d; fish = sf; }
            }
        }
        if (fish == null) return;
        ctx.outline(fish.getBoundingBox().inflate(0.1), 0xFFAAAAFF, false);

        // target = the button/lever in range
        BlockPos goal = null;
        var pp = fish.blockPosition();
        for (int dx = -14; dx <= 14 && goal == null; dx++)
            for (int dz = -14; dz <= 14 && goal == null; dz++)
                for (int dy = -3; dy <= 3; dy++) {
                    var bp = pp.offset(dx, dy, dz);
                    String id = mc.level.getBlockState(bp).getBlock().getDescriptionId();
                    if (id.contains("button") || id.contains("lever")) { goal = bp.immutable(); break; }
                }
        if (goal == null) return;

        List<BlockPos> route = bfs(mc.level, pp, goal);
        if (route == null) { ctx.line(fish.position(), Vec3.atCenterOf(goal), 0x60AAAAFF, false); return; }
        for (int i = 0; i < route.size() - 1; i++)
            ctx.line(Vec3.atCenterOf(route.get(i)), Vec3.atCenterOf(route.get(i+1)), 0xFFAAAAFF, false);
        ctx.outline(new AABB(goal), 0xFF55FF55, false);
    }

    // bfs over walkable cells (block passable + headroom). 6-dir so it can step up/down a level.
    private static List<BlockPos> bfs(net.minecraft.world.level.Level lvl, BlockPos start, BlockPos goal) {
        Deque<BlockPos> q = new ArrayDeque<>();
        Map<BlockPos, BlockPos> prev = new HashMap<>();
        q.add(start); prev.put(start, null);
        int[][] dirs = {{1,0,0},{-1,0,0},{0,0,1},{0,0,-1},{0,1,0},{0,-1,0}};
        int budget = 4000;
        while (!q.isEmpty() && budget-- > 0) {
            BlockPos c = q.poll();
            if (c.closerThan(goal, 1.8)) return rebuild(prev, c);
            for (int[] d : dirs) {
                BlockPos n = c.offset(d[0], d[1], d[2]);
                if (prev.containsKey(n) || !c.closerThan(n, 1.5)) continue;
                if (n.distSqr(start) > 400) continue;
                if (!passable(lvl, n)) continue;
                prev.put(n, c);
                q.add(n);
            }
        }
        return null;
    }

    private static boolean passable(net.minecraft.world.level.Level lvl, BlockPos p) {
        return lvl.getBlockState(p).getCollisionShape(lvl, p).isEmpty()
            && lvl.getBlockState(p.above()).getCollisionShape(lvl, p.above()).isEmpty();
    }

    private static List<BlockPos> rebuild(Map<BlockPos, BlockPos> prev, BlockPos end) {
        LinkedList<BlockPos> out = new LinkedList<>();
        for (BlockPos c = end; c != null; c = prev.get(c)) out.addFirst(c);
        return out;
    }
}
