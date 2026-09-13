package com.froggylord.constellation.constellation;

import com.froggylord.constellation.ConstellationClient;
import com.froggylord.constellation.config.OrionConfig;
import com.froggylord.constellation.data.RoomMatch;
import com.froggylord.constellation.data.RoomTransform;
import com.froggylord.constellation.render.WorldRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.monster.Silverfish;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.*;

// ported from Skyblocker (LGPL-3.0-or-later):
// src/main/java/de/hysky/skyblocker/skyblock/dungeon/puzzle/IcePath.java
public final class SilverfishSolver {

    private static final boolean[][] board = new boolean[17][17];
    private static final List<int[]> path = new ArrayList<>();

    private SilverfishSolver() {}

    public static void draw(WorldRenderer.Ctx ctx) {
        if (!RoomMatch.isMatched() || !RoomMatch.currentRoom().contains("ice-silverfish-room")) return;
        OrionConfig cfg = ConstellationClient.cfg().orion;
        if (cfg == null || !cfg.silverfishSolver) return;
        if (!ConstellationClient.loc().inDungeons()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;

        scanBoard(mc);
        Silverfish fish = findFish(mc);
        if (fish == null) return;

        long[] relative = RoomTransform.actualToRelative(RoomMatch.currentDir(), RoomMatch.anchorX(), RoomMatch.anchorZ(),
            fish.blockPosition().getX(), fish.blockPosition().getY(), fish.blockPosition().getZ());
        int row = 24 - (int) relative[2];
        int col = 23 - (int) relative[0];
        if (row < 0 || row >= 17 || col < 0 || col >= 17) return;

        solve(row, col);
        ctx.outline(fish.getBoundingBox().inflate(0.1), 0xFFAAAAFF, false);
        for (int i = 0; i < path.size() - 1; i++) {
            Vec3 from = worldCenter(path.get(i));
            Vec3 to = worldCenter(path.get(i + 1));
            ctx.line(from, to, 0xFFAAAAFF, false);
        }
    }

    private static void scanBoard(Minecraft mc) {
        for (int row = 0; row < 17; row++) {
            for (int col = 0; col < 17; col++) {
                BlockPos pos = worldPos(23 - col, 67, 24 - row);
                board[row][col] = !mc.level.getBlockState(pos).isAir();
            }
        }
    }

    private static Silverfish findFish(Minecraft mc) {
        BlockPos center = worldPos(15, 66, 16);
        List<Silverfish> fish = mc.level.getEntitiesOfClass(Silverfish.class,
            AABB.ofSize(Vec3.atCenterOf(center), 16, 16, 16), ignored -> true);
        return fish.isEmpty() ? null : fish.getFirst();
    }

    private static void solve(int startRow, int startCol) {
        path.clear();
        Set<Long> visited = new HashSet<>();
        Deque<List<int[]>> queue = new ArrayDeque<>();
        queue.add(List.of(new int[]{startRow, startCol}));
        visited.add(key(startRow, startCol));

        while (!queue.isEmpty()) {
            List<int[]> current = queue.poll();
            int[] pos = current.getLast();
            if (pos[0] == 0 && pos[1] >= 7 && pos[1] <= 9) {
                path.addAll(current);
                return;
            }

            int row = pos[0];
            while (row < 17 && !board[row][pos[1]]) row++;
            add(queue, visited, current, row - 1, pos[1]);
            row = pos[0];
            while (row >= 0 && !board[row][pos[1]]) row--;
            add(queue, visited, current, row + 1, pos[1]);

            int col = pos[1];
            while (col < 17 && !board[pos[0]][col]) col++;
            add(queue, visited, current, pos[0], col - 1);
            col = pos[1];
            while (col >= 0 && !board[pos[0]][col]) col--;
            add(queue, visited, current, pos[0], col + 1);
        }
    }

    private static void add(Deque<List<int[]>> queue, Set<Long> visited, List<int[]> current, int row, int col) {
        if (row < 0 || row >= 17 || col < 0 || col >= 17 || !visited.add(key(row, col))) return;
        List<int[]> next = new ArrayList<>(current);
        next.add(new int[]{row, col});
        queue.add(next);
    }

    private static Vec3 worldCenter(int[] point) {
        return Vec3.atCenterOf(worldPos(23 - point[1], 67, 24 - point[0]));
    }

    private static BlockPos worldPos(int x, int y, int z) {
        long[] world = RoomTransform.relativeToActual(RoomMatch.currentDir(), RoomMatch.anchorX(), RoomMatch.anchorZ(), x, y, z);
        return new BlockPos((int) world[0], (int) world[1], (int) world[2]);
    }

    private static long key(int row, int col) {
        return ((long) row << 32) ^ (col & 0xffffffffL);
    }
}
