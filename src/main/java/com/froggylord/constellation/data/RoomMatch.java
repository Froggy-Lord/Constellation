package com.froggylord.constellation.data;

import com.froggylord.constellation.ConstellationClient;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.*;

/**
 * Identifies the catacombs room the player stands in by matching world blocks against
 * the bundled room skeleton database.
 *
 * Reads the shared DungeonRooms on-disk format (block ids + encoding live in DungeonData),
 * but the detection algorithm here — footprint flood, candidate elimination across the four
 * rotations, foundation-Y reference, and reverse-verify — is a fresh implementation.
 */
public class RoomMatch {

    private static final int MIN_MAPPED = 30;

    private static String currentRoom = "";
    private static RoomTransform.Direction currentDir = RoomTransform.Direction.NW;
    private static int roomCornerX, roomCornerZ;
    private static int confirmCount = 0;
    private static String lastCandidate = "";

    /** Run detection. Call every ~20 ticks while in a dungeon. */
    public static void update() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || !DungeonData.isLoaded()) return;

        Vec3 pos = mc.player.position();
        int floorY = (int) Math.floor(pos.y);
        int startCX = RoomGrid.cornerX(pos.x), startCZ = RoomGrid.cornerZ(pos.z);

        // 1. flood the footprint
        Set<Long> cells = flood(mc.level, startCX, startCZ, floorY);
        if (cells.isEmpty()) return;

        int wMinX = Integer.MAX_VALUE, wMaxX = Integer.MIN_VALUE;
        int wMinZ = Integer.MAX_VALUE, wMaxZ = Integer.MIN_VALUE;
        for (long c : cells) {
            int cx = (int) (c >> 32), cz = (int) c;
            wMinX = Math.min(wMinX, cx); wMaxX = Math.max(wMaxX, cx + 31);
            wMinZ = Math.min(wMinZ, cz); wMaxZ = Math.max(wMaxZ, cz + 31);
        }
        int spanX = (wMaxX - wMinX + 1) / 32;
        int spanZ = (wMaxZ - wMinZ + 1) / 32;
        String shape = Math.min(spanX, spanZ) + "x" + Math.max(spanX, spanZ);
        int maxDim = Math.max(wMaxX - wMinX, wMaxZ - wMinZ);

        Map<String, int[]> pool = DungeonData.ROOMS.get(shape);
        if (pool == null || pool.isEmpty()) return;

        // 2. set up candidate lists for all four rotations
        RoomTransform.Direction[] dirs = RoomTransform.Direction.values();
        int[][] corner = {
            {wMinX, wMinZ}, {wMinX, wMaxZ}, {wMaxX, wMaxZ}, {wMaxX, wMinZ}
        };
        List<List<String>> cands = new ArrayList<>();
        for (int d = 0; d < dirs.length; d++) cands.add(new ArrayList<>(pool.keySet()));

        // 3. find foundation Y (lowest tracked block)
        int minY = Integer.MAX_VALUE;
        for (int dy = -8; dy <= 35; dy++) {
            int y = floorY + dy;
            for (int wx = wMinX + 2; wx <= wMaxX - 2; wx += 2)
                for (int wz = wMinZ + 2; wz <= wMaxZ - 2; wz += 2) {
                    if (!cells.contains(RoomGrid.cellKey(RoomGrid.cornerX((double) wx), RoomGrid.cornerZ((double) wz)))) continue;
                    if (blockId(mc.level, wx, y, wz) != 0 && y < minY) minY = y;
                }
        }
        if (minY == Integer.MAX_VALUE) minY = floorY;

        // 4. eliminate candidates that don't contain each observed block
        int mapped = 0;
        outer:
        for (int dy = -8; dy <= 35; dy++) {
            int y = floorY + dy;
            for (int wx = wMinX + 2; wx <= wMaxX - 2; wx += 2)
                for (int wz = wMinZ + 2; wz <= wMaxZ - 2; wz += 2) {
                    if (!cells.contains(RoomGrid.cellKey(RoomGrid.cornerX((double) wx), RoomGrid.cornerZ((double) wz)))) continue;
                    byte id = blockId(mc.level, wx, y, wz);
                    if (id == 0) continue;
                    mapped++;
                    for (int di = 0; di < dirs.length; di++) {
                        List<String> list = cands.get(di);
                        if (list.isEmpty()) continue;
                        Vec3 rel = dirs[di].worldToLocal(corner[di][0], corner[di][1], wx, y, wz);
                        int rx = (int) rel.x, rz = (int) rel.z;
                        if (rx < 0 || rx > maxDim || rz < 0 || rz > maxDim) continue;
                        int enc = DungeonData.posIdToInt(rx, y - minY, rz, id);
                        list.removeIf(name -> Arrays.binarySearch(pool.get(name), enc) < 0);
                    }
                    int remaining = cands.stream().mapToInt(List::size).sum();
                    if (remaining == 0) break outer;
                    if (remaining == 1 && mapped >= MIN_MAPPED) break outer;
                }
        }

        // 5. pick best survivor by reverse-verify
        String best = null; RoomTransform.Direction bestDir = null; double bestScore = 0;
        for (int di = 0; di < dirs.length; di++) {
            for (String name : cands.get(di)) {
                double score = reverseVerify(mc.level, pool.get(name), dirs[di], corner[di], minY, maxDim, cells);
                if (score > bestScore) { bestScore = score; best = name; bestDir = dirs[di]; }
            }
        }

        // 6. confirm-streak
        if (best != null && bestScore >= 0.85) {
            if (best.equals(lastCandidate)) confirmCount++;
            else { confirmCount = 0; lastCandidate = best; }
            if (confirmCount >= 1 && !best.equals(currentRoom)) {
                currentRoom = best;
                currentDir = bestDir;
                roomCornerX = corner[bestDir.ordinal()][0];
                roomCornerZ = corner[bestDir.ordinal()][1];
                ConstellationClient.bus().post(new RoomEnteredEvent(best, bestDir, roomCornerX, roomCornerZ));
            }
        } else {
            confirmCount = 0;
            lastCandidate = "";
        }
    }

    /** reverse check: how much of the candidate's own fingerprint rebuilds in the world */
    private static double reverseVerify(Level level, int[] fp, RoomTransform.Direction dir,
                                        int[] corner, int minY, int maxDim, Set<Long> cells) {
        int found = 0, checked = 0;
        // sample up to ~200 entries spread across the fingerprint
        int step = Math.max(1, fp.length / 200);
        for (int i = 0; i < fp.length; i += step) {
            int v = fp[i];
            int rx = DungeonData.idX(v), ry = DungeonData.idY(v), rz = DungeonData.idZ(v), id = DungeonData.idBlock(v);
            Vec3 world = dir.localToWorld(corner[0], corner[1], rx, ry + minY, rz);
            int wx = (int) world.x, wy = (int) world.y, wz = (int) world.z;
            checked++;
            if (blockId(level, wx, wy, wz) == (byte) id) found++;
        }
        return checked == 0 ? 0 : (double) found / checked;
    }

    /** flood-fill the 32-grid from the start cell into room-floor neighbours */
    private static Set<Long> flood(Level level, int startCX, int startCZ, int floorY) {
        Set<Long> found = new LinkedHashSet<>();
        Deque<long[]> q = new ArrayDeque<>();
        q.add(new long[]{startCX, startCZ});
        Set<Long> visited = new HashSet<>();
        while (!q.isEmpty() && found.size() < 16) {
            long[] c = q.poll();
            int cx = (int) c[0], cz = (int) c[1];
            long key = RoomGrid.cellKey(cx, cz);
            if (!visited.add(key)) continue;
            if (!hasFloor(level, cx, cz, floorY)) continue;
            found.add(key);
            q.add(new long[]{cx - 32, cz});
            q.add(new long[]{cx + 32, cz});
            q.add(new long[]{cx, cz - 32});
            q.add(new long[]{cx, cz + 32});
        }
        return found;
    }

    private static boolean hasFloor(Level level, int cx, int cz, int floorY) {
        int solid = 0;
        for (int dy = -3; dy <= 1; dy++) {
            if (level.getBlockState(new BlockPos(cx + 15, floorY + dy, cz + 15)).isSolid()) solid++;
            if (level.getBlockState(new BlockPos(cx + 16, floorY + dy, cz + 16)).isSolid()) solid++;
        }
        return solid >= 4;
    }

    /** numeric id for the block at a world position, 0 if untracked */
    private static byte blockId(Level level, int x, int y, int z) {
        BlockState state = level.getBlockState(new BlockPos(x, y, z));
        if (state.isAir()) return 0;
        var key = BuiltInRegistries.BLOCK.getKey(state.getBlock());
        return DungeonData.numericId(key.toString());
    }

    // accessors
    public static String currentRoom() { return currentRoom; }
    public static RoomTransform.Direction currentDir() { return currentDir; }
    public static int roomCornerX() { return roomCornerX; }
    public static int roomCornerZ() { return roomCornerZ; }

    public record RoomEnteredEvent(String name, RoomTransform.Direction dir, int cornerX, int cornerZ) {}
}
