package com.froggylord.constellation.constellation;

import com.froggylord.constellation.ConstellationClient;
import com.froggylord.constellation.config.OrionConfig;
import com.froggylord.constellation.render.WorldRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.AABB;

import java.util.*;

// waterboard. the lever->gate wiring isnt in the block geometry so i cant auto-solve it
// blind — but the live water IS in the world, so trace it: flood the actual water blocks
// from the source and show which coloured goal it currently reaches. plus label the goals.
public final class WaterPuzzleHelper {

    private WaterPuzzleHelper() {}

    public static void draw(WorldRenderer.Ctx ctx) {
        OrionConfig cfg = ConstellationClient.cfg().orion;
        if (cfg == null || !cfg.waterboardSolver) return;
        if (!ConstellationClient.loc().inDungeons()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;

        var pp = mc.player.blockPosition();

        // label every coloured goal so you know which lever you're aiming for
        Map<Long, Integer> goals = new HashMap<>();
        for (int dx = -20; dx <= 20; dx++)
            for (int dz = -20; dz <= 20; dz++)
                for (int dy = -6; dy <= 6; dy++) {
                    var bp = pp.offset(dx, dy, dz);
                    String id = mc.level.getBlockState(bp).getBlock().getDescriptionId();
                    int col = goalColour(id);
                    if (col != 0) {
                        goals.put(k(bp), col);
                        ctx.highlight(new AABB(bp), (col & 0xFFFFFF) | 0x50000000, true);
                    }
                }

        // trace the live water — flood from any water block near the top, mark where it lands
        BlockPos src = null;
        for (int dx = -20; dx <= 20 && src == null; dx++)
            for (int dz = -20; dz <= 20 && src == null; dz++)
                for (int dy = 6; dy >= 0; dy--) {
                    var bp = pp.offset(dx, dy, dz);
                    if (!mc.level.getFluidState(bp).getType().isSame(Fluids.EMPTY) && mc.level.getFluidState(bp).getType().isSame(Fluids.WATER)) { src = bp.immutable(); break; }
                }
        if (src == null) return;

        Set<Long> water = new HashSet<>();
        Deque<BlockPos> q = new ArrayDeque<>();
        q.add(src); water.add(k(src));
        int budget = 3000;
        int[][] flow = {{1,0,0},{-1,0,0},{0,0,1},{0,0,-1},{0,-1,0}}; // water spreads sideways + down
        while (!q.isEmpty() && budget-- > 0) {
            BlockPos c = q.poll();
            // did the stream hit a goal? light it bright
            for (int[] d : new int[][]{{0,-1,0},{1,0,0},{-1,0,0},{0,0,1},{0,0,-1}}) {
                Long gk = k(c.offset(d[0], d[1], d[2]));
                if (goals.containsKey(gk)) {
                    ctx.beam(c.getX()+0.5, c.getY()+2, c.getZ()+0.5, goals.get(gk) | 0xFF000000, 12, true);
                }
            }
            for (int[] d : flow) {
                BlockPos n = c.offset(d[0], d[1], d[2]);
                if (water.contains(k(n)) || n.distSqr(src) > 600) continue;
                if (mc.level.getFluidState(n).getType().isSame(Fluids.WATER)) { water.add(k(n)); q.add(n); }
            }
        }
        // faint trace of where the water actually is right now
        for (long wk : water) ctx.highlight(box(wk, src.getY()), 0x3055AAFF, true);
    }

    private static int goalColour(String id) {
        if (id.contains("red_terracotta") || id.contains("red_wool") || id.contains("red_concrete")) return 0xFF3333;
        if (id.contains("orange") || id.contains("gold")) return 0xFFAA00;
        if (id.contains("green") || id.contains("emerald")) return 0x55FF55;
        if (id.contains("blue") || id.contains("diamond")) return 0x55FFFF;
        if (id.contains("white") || id.contains("quartz")) return 0xFFFFFF;
        return 0;
    }

    private static long k(BlockPos p) { return p.asLong(); }
    private static AABB box(long packed, int approxY) { BlockPos p = BlockPos.of(packed); return new AABB(p); }
}
