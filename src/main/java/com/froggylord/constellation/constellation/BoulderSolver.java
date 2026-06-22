package com.froggylord.constellation.constellation;

import com.froggylord.constellation.ConstellationClient;
import com.froggylord.constellation.config.OrionConfig;
import com.froggylord.constellation.render.WorldRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.*;

// boulder push puzzle. my own little sokoban bfs over push states, no copied tables.
// scans the real room blocks, figures out the push sequence, draws each step.
public final class BoulderSolver {

    private BoulderSolver() {}

    public static void draw(WorldRenderer.Ctx ctx) {
        OrionConfig cfg = ConstellationClient.cfg().orion;
        if (cfg == null || !cfg.terminalSolvers) return;
        if (!ConstellationClient.loc().inDungeons()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;

        var pp = mc.player.blockPosition();
        BlockPos boulder = null, goal = null;
        Set<Long> walls = new HashSet<>();
        int yLevel = pp.getY();

        // grab the room — boulder (anvil/obsidian), goal (plate), walls. work on one y slice.
        for (int dx = -12; dx <= 12; dx++)
            for (int dz = -12; dz <= 12; dz++)
                for (int dy = -1; dy <= 1; dy++) {
                    var bp = pp.offset(dx, dy, dz);
                    String id = mc.level.getBlockState(bp).getBlock().getDescriptionId();
                    if (id.contains("anvil") || id.contains("obsidian")) { boulder = bp.immutable(); yLevel = bp.getY(); }
                    else if (id.contains("pressure_plate") || id.contains("weighted")) goal = bp.immutable();
                    else if (id.contains("iron_bars") || id.contains("fence") || id.contains("wall") || id.contains("cobblestone")) walls.add(key(bp.getX(), bp.getZ()));
                }

        if (boulder == null || goal == null) return;

        // always show start + goal
        ctx.outline(box(boulder), 0xFFFF6600, true);
        ctx.label(Vec3.atCenterOf(boulder).add(0, 0.8, 0), "BOULDER", 0xFFFF6600, true);
        ctx.outline(box(goal), 0xFF55FF55, true);
        ctx.label(Vec3.atCenterOf(goal).add(0, 0.8, 0), "GOAL", 0xFF55FF55, true);

        // bfs the push path. state = boulder xz. moves = push N/S/E/W (needs empty behind to stand).
        List<int[]> path = solve(boulder.getX(), boulder.getZ(), goal.getX(), goal.getZ(), walls);
        if (path == null || path.size() < 2) return;

        // draw the route the boulder takes, numbering each push
        for (int i = 0; i < path.size() - 1; i++) {
            int[] a = path.get(i), b = path.get(i + 1);
            Vec3 va = new Vec3(a[0] + 0.5, yLevel + 0.5, a[1] + 0.5);
            Vec3 vb = new Vec3(b[0] + 0.5, yLevel + 0.5, b[1] + 0.5);
            ctx.line(va, vb, 0xFFFFAA00, true);
            // where you stand to push (the block behind the boulder this step)
            int sx = a[0] - (b[0] - a[0]), sz = a[1] - (b[1] - a[1]);
            ctx.highlight(new AABB(sx, yLevel, sz, sx + 1, yLevel + 0.2, sz + 1), 0x6000AAFF, true);
        }
    }

    // plain bfs, 4-dir pushes, walls block both the boulder cell and the stand cell
    private static List<int[]> solve(int bx, int bz, int gx, int gz, Set<Long> walls) {
        Deque<int[]> q = new ArrayDeque<>();
        Map<Long, int[]> prev = new HashMap<>();
        long start = key(bx, bz);
        q.add(new int[]{bx, bz});
        prev.put(start, null);
        int[][] dirs = {{1,0},{-1,0},{0,1},{0,-1}};
        while (!q.isEmpty()) {
            int[] cur = q.poll();
            if (cur[0] == gx && cur[1] == gz) return rebuild(prev, cur);
            for (int[] d : dirs) {
                int nx = cur[0] + d[0], nz = cur[1] + d[1];
                int standX = cur[0] - d[0], standZ = cur[1] - d[1];
                long nk = key(nx, nz);
                // boulder cell must be clear, and you must be able to stand behind it
                if (walls.contains(nk) || walls.contains(key(standX, standZ))) continue;
                if (Math.abs(nx - bx) > 14 || Math.abs(nz - bz) > 14) continue;
                if (prev.containsKey(nk)) continue;
                prev.put(nk, cur);
                q.add(new int[]{nx, nz});
            }
        }
        return null;
    }

    private static List<int[]> rebuild(Map<Long, int[]> prev, int[] end) {
        LinkedList<int[]> out = new LinkedList<>();
        int[] c = end;
        while (c != null) { out.addFirst(c); c = prev.get(key(c[0], c[1])); }
        return out;
    }

    private static long key(int x, int z) { return (((long) x) << 32) ^ (z & 0xffffffffL); }
    private static AABB box(BlockPos p) { return new AABB(p.getX(), p.getY(), p.getZ(), p.getX()+1, p.getY()+1, p.getZ()+1); }
}
