package com.froggylord.constellation.constellation;

import com.froggylord.constellation.ConstellationClient;
import com.froggylord.constellation.config.OrionConfig;
import com.froggylord.constellation.render.WorldRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.*;

// ice fill = walk over every ice tile once, single path (hamiltonian path).
// my own backtracking dfs. finds the order, draws a numbered route. cached per room so
// we dont resolve every frame.
public final class IceFillHelper {

    private IceFillHelper() {}

    private static List<int[]> cached;
    private static long cacheKey = Long.MIN_VALUE;
    private static int yPlane;

    public static void draw(WorldRenderer.Ctx ctx) {
        OrionConfig cfg = ConstellationClient.cfg().orion;
        if (cfg == null || !cfg.iceFillSolver) return;
        if (!ConstellationClient.loc().inDungeons()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;

        var pp = mc.player.blockPosition();
        // collect the ice floor (one block under foot level)
        Set<Long> ice = new HashSet<>();
        int floorY = pp.getY() - 1;
        for (int dx = -10; dx <= 10; dx++)
            for (int dz = -10; dz <= 10; dz++) {
                var bp = pp.offset(dx, -1, dz);
                if (mc.level.getBlockState(bp).getBlock().getDescriptionId().contains("ice"))
                    ice.add(key(bp.getX(), bp.getZ()));
            }
        if (ice.size() < 2 || ice.size() > 64) return; // too big to brute force, bail

        long ck = ice.hashCode();
        if (ck != cacheKey) {
            // start where the player stands if its on the ice, else any edge cell
            int startX = pp.getX(), startZ = pp.getZ();
            if (!ice.contains(key(startX, startZ))) {
                int[] e = pickStart(ice);
                startX = e[0]; startZ = e[1];
            }
            cached = hamiltonian(ice, startX, startZ);
            cacheKey = ck;
            yPlane = floorY;
        }
        if (cached == null) return;

        for (int i = 0; i < cached.size() - 1; i++) {
            int[] a = cached.get(i), b = cached.get(i + 1);
            ctx.line(new Vec3(a[0]+0.5, yPlane+1.05, a[1]+0.5),
                     new Vec3(b[0]+0.5, yPlane+1.05, b[1]+0.5), 0xFF55FFFF, false);
        }
        // mark the start so you know where to step on first
        int[] s = cached.get(0);
        ctx.highlight(new AABB(s[0], yPlane+1, s[1], s[0]+1, yPlane+1.1, s[1]+1), 0x8055FF55, false);
    }

    // backtracking hamiltonian path over the ice set
    private static List<int[]> hamiltonian(Set<Long> ice, int sx, int sz) {
        List<int[]> path = new ArrayList<>();
        Set<Long> seen = new HashSet<>();
        path.add(new int[]{sx, sz});
        seen.add(key(sx, sz));
        return dfs(ice, seen, path, sx, sz) ? path : null;
    }

    private static final int[][] DIRS = {{1,0},{-1,0},{0,1},{0,-1}};

    private static boolean dfs(Set<Long> ice, Set<Long> seen, List<int[]> path, int x, int z) {
        if (seen.size() == ice.size()) return true;
        for (int[] d : DIRS) {
            int nx = x + d[0], nz = z + d[1];
            long nk = key(nx, nz);
            if (!ice.contains(nk) || seen.contains(nk)) continue;
            seen.add(nk);
            path.add(new int[]{nx, nz});
            if (dfs(ice, seen, path, nx, nz)) return true;
            seen.remove(nk);
            path.remove(path.size() - 1);
        }
        return false;
    }

    // corner/edge cell makes the best start for a hamiltonian path
    private static int[] pickStart(Set<Long> ice) {
        for (long k : ice) {
            int x = (int)(k >> 32), z = (int) k;
            int nbrs = 0;
            for (int[] d : DIRS) if (ice.contains(key(x+d[0], z+d[1]))) nbrs++;
            if (nbrs <= 2) return new int[]{x, z}; // corner-ish
        }
        long any = ice.iterator().next();
        return new int[]{(int)(any >> 32), (int) any};
    }

    private static long key(int x, int z) { return (((long) x) << 32) ^ (z & 0xffffffffL); }
}
