package com.froggylord.constellation.constellation;

import com.froggylord.constellation.ConstellationClient;
import com.froggylord.constellation.config.OrionConfig;
import com.froggylord.constellation.data.RoomMatch;
import com.froggylord.constellation.data.RoomTransform;
import com.froggylord.constellation.render.WorldRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.*;

// ported from Skyblocker (LGPL-3.0-or-later):
// src/main/java/de/hysky/skyblocker/skyblock/dungeon/puzzle/IceFill.java
public final class IceFillHelper {

    private static final BlockPos[] ORIGINS = {
        new BlockPos(16, 70, 9),
        new BlockPos(17, 71, 16),
        new BlockPos(18, 72, 25)
    };
    private static final int[] SIZES = {3, 5, 7};
    private static final List<List<int[]>> paths = new ArrayList<>(List.of(List.of(), List.of(), List.of()));
    private static String roomKey = "";

    private IceFillHelper() {}

    public static void draw(WorldRenderer.Ctx ctx) {
        if (!RoomMatch.isMatched() || !RoomMatch.currentRoom().contains("ice-path")) return;
        OrionConfig cfg = ConstellationClient.cfg().orion;
        if (cfg == null || !cfg.iceFillSolver) return;
        if (!ConstellationClient.loc().inDungeons()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;

        String key = RoomMatch.currentRoom() + ':' + RoomMatch.anchorX() + ':' + RoomMatch.anchorZ() + ':' + RoomMatch.currentDir();
        if (!key.equals(roomKey)) {
            roomKey = key;
            paths.set(0, List.of());
            paths.set(1, List.of());
            paths.set(2, List.of());
        }

        for (int board = 0; board < 3; board++) {
            List<int[]> solved = scanAndSolve(mc, board);
            if (!solved.isEmpty()) paths.set(board, solved);
            render(ctx, board, paths.get(board));
        }
    }

    private static List<int[]> scanAndSolve(Minecraft mc, int boardIndex) {
        int size = SIZES[boardIndex];
        boolean[][] blocked = new boolean[size][size];
        BlockPos origin = ORIGINS[boardIndex];
        for (int row = 0; row < size; row++) {
            for (int col = 0; col < size; col++) {
                BlockPos pos = worldPos(origin.getX() - col, origin.getY(), origin.getZ() - row);
                if (mc.level.getBlockState(pos.below()).isAir()) return List.of();
                blocked[row][col] = !mc.level.getBlockState(pos).isAir();
            }
        }
        return solve(blocked);
    }

    private static List<int[]> solve(boolean[][] blocked) {
        int size = blocked.length;
        int startRow = size - 1;
        int startCol = size / 2;
        int open = size * size;
        for (boolean[] row : blocked) for (boolean cell : row) if (cell) open--;
        if (blocked[startRow][startCol] || open == 0) return List.of();

        boolean[][] visited = new boolean[size][size];
        List<int[]> path = new ArrayList<>();
        visited[startRow][startCol] = true;
        path.add(new int[]{startRow, startCol});
        return dfs(blocked, visited, path, open - 1) ? List.copyOf(path) : List.of();
    }

    private static final int[][] DIRS = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};

    private static boolean dfs(boolean[][] blocked, boolean[][] visited, List<int[]> path, int remaining) {
        int[] current = path.getLast();
        if (remaining == 0) return current[0] == 0 && current[1] == blocked.length / 2;

        for (int[] dir : DIRS) {
            int row = current[0] + dir[0];
            int col = current[1] + dir[1];
            if (row < 0 || row >= blocked.length || col < 0 || col >= blocked.length) continue;
            if (blocked[row][col] || visited[row][col]) continue;
            visited[row][col] = true;
            path.add(new int[]{row, col});
            if (dfs(blocked, visited, path, remaining - 1)) return true;
            path.removeLast();
            visited[row][col] = false;
        }
        return false;
    }

    private static void render(WorldRenderer.Ctx ctx, int boardIndex, List<int[]> path) {
        if (path.isEmpty()) return;
        BlockPos origin = ORIGINS[boardIndex];
        for (int i = 0; i < path.size() - 1; i++) {
            int[] a = path.get(i);
            int[] b = path.get(i + 1);
            ctx.line(center(origin, a), center(origin, b), 0xFF55FFFF, false);
        }
        int[] start = path.getFirst();
        BlockPos pos = worldPos(origin.getX() - start[1], origin.getY(), origin.getZ() - start[0]);
        ctx.highlight(new AABB(pos.getX(), pos.getY(), pos.getZ(), pos.getX() + 1, pos.getY() + .1, pos.getZ() + 1),
            0x8055FF55, false);
    }

    private static Vec3 center(BlockPos origin, int[] point) {
        return Vec3.atCenterOf(worldPos(origin.getX() - point[1], origin.getY(), origin.getZ() - point[0])).add(0, .1, 0);
    }

    private static BlockPos worldPos(int x, int y, int z) {
        long[] world = RoomTransform.relativeToActual(RoomMatch.currentDir(), RoomMatch.anchorX(), RoomMatch.anchorZ(), x, y, z);
        return new BlockPos((int) world[0], (int) world[1], (int) world[2]);
    }
}
