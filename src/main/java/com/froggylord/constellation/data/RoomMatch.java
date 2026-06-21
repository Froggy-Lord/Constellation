package com.froggylord.constellation.data;

import com.froggylord.constellation.ConstellationClient;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.*;

/**
 * Room identification engine. Finds the catacombs room the player is standing in
 * by matching the world's blocks against a database of pre-recorded room skeletons.
 *
 * Algorithm:
 *   1. Flood-fill from the player's 32x32 grid cell to find contiguous room footprint
 *   2. Filter candidates by footprint shape (max local x/z)
 *   3. Binary-search each candidate skeleton against world blocks
 *   4. Reverse-verify survivors (≥90% of skeleton blocks found in world)
 *   5. Confirm-streak: require 2 consecutive identical matches before committing
 */
public class RoomMatch {

    private static String currentRoom = "";
    private static RoomTransform.Direction currentDir = RoomTransform.Direction.NW;
    private static int roomCornerX, roomCornerZ;
    private static int footprintW = 1, footprintH = 1;
    private static int confirmCount = 0;
    private static String lastCandidate = "";

    /**
     * Run room detection. Call every ~20 ticks while in a dungeon.
     */
    public static void update() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;

        Vec3 pos = mc.player.position();
        int cx = RoomGrid.cornerX(pos.x);
        int cz = RoomGrid.cornerZ(pos.z);
        int playerY = (int) Math.floor(pos.y);

        // find the footprint — flood from this cell into neighbours
        var footprint = floodFootprint(mc.level, cx, cz, playerY);
        if (footprint.isEmpty()) return;

        // compute bounding box of footprint
        int minCX = Integer.MAX_VALUE, maxCX = Integer.MIN_VALUE;
        int minCZ = Integer.MAX_VALUE, maxCZ = Integer.MIN_VALUE;
        for (var cell : footprint) {
            minCX = Math.min(minCX, cell.cx);
            maxCX = Math.max(maxCX, cell.cx);
            minCZ = Math.min(minCZ, cell.cz);
            maxCZ = Math.max(maxCZ, cell.cz);
        }
        footprintW = (maxCX - minCX) / 32 + 1;
        footprintH = (maxCZ - minCZ) / 32 + 1;

        // from the footprint's NW corner, scan the world for block fingerprints
        int baseX = minCX, baseZ = minCZ;
        Map<Integer, Integer> worldFingerprint = scanWorld(mc.level, baseX, baseZ, playerY, footprintW, footprintH);

        // filter candidates by footprint size
        List<DungeonData.RoomIndex> candidates = new ArrayList<>();
        for (var entry : DungeonData.index.values()) {
            if ((entry.maxX() + 31) / 32 >= footprintW && (entry.maxZ() + 31) / 32 >= footprintH
                || (entry.maxX() + 31) / 32 >= footprintH && (entry.maxZ() + 31) / 32 >= footprintW) {
                candidates.add(entry);
            }
        }

        // try each candidate in all 4 rotations
        String bestName = "";
        RoomTransform.Direction bestDir = RoomTransform.Direction.NW;
        double bestScore = 0;

        for (var candidate : candidates) {
            int[] skeleton = DungeonData.loadSkeleton(candidate.name());
            if (skeleton == null) continue;

            int skW = (candidate.maxX() + 31) / 32;
            int skH = (candidate.maxZ() + 31) / 32;

            for (RoomTransform.Direction dir : RoomTransform.Direction.values()) {
                // if footprint sizes don't match in this rotation, skip
                if (skW != footprintW || skH != footprintH) continue;

                double score = matchSkeleton(skeleton, worldFingerprint, skW, skH);
                if (score > bestScore) {
                    bestScore = score;
                    bestName = candidate.name();
                    bestDir = dir;
                }
            }
        }

        // confirm streak
        if (bestScore >= 0.9 && bestName.equals(lastCandidate)) {
            confirmCount++;
        } else {
            confirmCount = 0;
            lastCandidate = bestScore >= 0.9 ? bestName : "";
        }

        if (confirmCount >= 2 && !bestName.equals(currentRoom)) {
            currentRoom = bestName;
            currentDir = bestDir;
            roomCornerX = minCX;
            roomCornerZ = minCZ;
            ConstellationClient.bus().post(new RoomEnteredEvent(bestName, bestDir, minCX, minCZ));
        } else if (bestScore < 0.9) {
            currentRoom = "";
        }
    }

    private record Cell(int cx, int cz) {}

    /** Flood-fill from the starting cell to find contiguous room footprint */
    private static Set<Cell> floodFootprint(Level level, int startCX, int startCZ, int playerY) {
        Set<Cell> found = new LinkedHashSet<>();
        Deque<Cell> queue = new ArrayDeque<>();
        queue.add(new Cell(startCX, startCZ));
        Set<Long> visited = new HashSet<>();

        while (!queue.isEmpty()) {
            Cell c = queue.poll();
            long key = ((long) c.cx << 32) | (c.cz & 0xFFFF_FFFFL);
            if (!visited.add(key)) continue;

            // check if this cell has room-floor blocks
            if (!hasRoomFloor(level, c.cx, c.cz, playerY)) continue;
            found.add(c);

            // check 4 neighbours
            for (int[] d : new int[][]{{-32, 0}, {32, 0}, {0, -32}, {0, 32}}) {
                queue.add(new Cell(c.cx + d[0], c.cz + d[1]));
            }
        }
        return found;
    }

    private static boolean hasRoomFloor(Level level, int cx, int cz, int playerY) {
        // check a column of blocks at the seam to see if this cell is part of a room
        int seamX = cx + 31; // right edge
        int seamZ = cz + 31; // bottom edge
        int floorCount = 0;
        for (int dy = -2; dy <= 2; dy++) {
            BlockState state = level.getBlockState(new BlockPos(seamX, playerY + dy, cz + 16));
            if (state.isSolid()) floorCount++;
            state = level.getBlockState(new BlockPos(cx + 16, playerY + dy, seamZ));
            if (state.isSolid()) floorCount++;
        }
        return floorCount >= 6; // ~60% solid = room cell
    }

    private static Map<Integer, Integer> scanWorld(Level level, int baseX, int baseZ, int playerY, int w, int h) {
        Map<Integer, Integer> fp = new LinkedHashMap<>();
        for (int lx = 0; lx < w * 32; lx++) {
            for (int lz = 0; lz < h * 32; lz++) {
                for (int dy = -4; dy <= 4; dy++) {
                    BlockState state = level.getBlockState(new BlockPos(baseX + lx, playerY + dy, baseZ + lz));
                    if (!state.isAir()) {
                        int id = state.getBlock().hashCode() & 0xFF;
                        int key = RoomTransform.invariantKey(lx, playerY + dy, lz, id & 0xFF, w * 32 - 1, h * 32 - 1);
                        fp.merge(key, 1, Integer::sum);
                    }
                }
            }
        }
        return fp;
    }

    private static double matchSkeleton(int[] skeleton, Map<Integer, Integer> world, int w, int h) {
        int matched = 0;
        for (int v : skeleton) {
            if (world.containsKey(RoomTransform.posId(RoomTransform.posIdX(v),
                    RoomTransform.posIdY(v), RoomTransform.posIdZ(v), RoomTransform.posIdBlock(v)))) {
                matched++;
            }
        }
        return (double) matched / skeleton.length;
    }

    // public accessors
    public static String currentRoom() { return currentRoom; }
    public static RoomTransform.Direction currentDir() { return currentDir; }
    public static int roomCornerX() { return roomCornerX; }
    public static int roomCornerZ() { return roomCornerZ; }
    public static int footprintW() { return footprintW; }
    public static int footprintH() { return footprintH; }

    public record RoomEnteredEvent(String name, RoomTransform.Direction dir, int cornerX, int cornerZ) {}
}
