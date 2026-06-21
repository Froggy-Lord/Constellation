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
 * the bundled room skeleton database. Reads the shared on-disk format (DungeonData);
 * the detection algorithm — footprint flood, candidate elimination across the four
 * rotations, foundation-Y reference, reverse-verify — is a fresh implementation.
 */
public class RoomMatch {

    private static final int MIN_MAPPED = 25;

    private static String currentRoom = "";
    private static RoomTransform.Direction currentDir = RoomTransform.Direction.NW;
    private static int anchorX, anchorZ;
    private static int confirmCount = 0;
    private static String lastCandidate = "";

    // diagnostics from the last scan
    public static String lastDebug = "no scan yet";

    public static void update() { scan(false); }

    public static String debugScan() { scan(true); return lastDebug; }

    private static void scan(boolean debug) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || !DungeonData.isLoaded()) {
            lastDebug = "no player/level/data";
            return;
        }

        Vec3 pos = mc.player.position();
        int floorY = (int) Math.floor(pos.y);
        int startCX = RoomGrid.cornerX(pos.x), startCZ = RoomGrid.cornerZ(pos.z);

        // 1. flood the footprint
        Set<Long> cells = flood(mc.level, startCX, startCZ, floorY);
        if (cells.isEmpty()) {
            lastDebug = "§cflood found 0 cells§r (floorY=" + floorY + " cell=" + startCX + "," + startCZ + ")";
            return;
        }

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
        if (pool == null || pool.isEmpty()) {
            lastDebug = "§cno rooms for shape " + shape + "§r (cells=" + cells.size() + ")";
            return;
        }

        // 2. candidate lists per rotation
        RoomTransform.Direction[] dirs = RoomTransform.Direction.values();
        int[][] corner = {
            {wMinX, wMinZ}, // NW
            {wMaxX, wMinZ}, // NE
            {wMinX, wMaxZ}, // SW
            {wMaxX, wMaxZ}  // SE
        };
        List<List<String>> cands = new ArrayList<>();
        for (int d = 0; d < dirs.length; d++) cands.add(new ArrayList<>(pool.keySet()));

        // 3. foundation Y (lowest tracked block)
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

        // 4. eliminate candidates by observed blocks
        int mapped = 0;
        StringBuilder sample = new StringBuilder();
        outer:
        for (int dy = -8; dy <= 35; dy++) {
            int y = floorY + dy;
            for (int wx = wMinX + 2; wx <= wMaxX - 2; wx += 2)
                for (int wz = wMinZ + 2; wz <= wMaxZ - 2; wz += 2) {
                    if (!cells.contains(RoomGrid.cellKey(RoomGrid.cornerX((double) wx), RoomGrid.cornerZ((double) wz)))) continue;
                    byte id = blockId(mc.level, wx, y, wz);
                    if (id == 0) continue;
                    mapped++;
                    if (debug && sample.length() < 40) sample.append(id).append(' ');
                    for (int di = 0; di < dirs.length; di++) {
                        List<String> list = cands.get(di);
                        if (list.isEmpty()) continue;
                        long[] rel = RoomTransform.actualToRelative(dirs[di], corner[di][0], corner[di][1], wx, y, wz);
                        int rx = (int) rel[0], rz = (int) rel[2];
                        if (rx < 0 || rx > maxDim || rz < 0 || rz > maxDim) continue;
                        int enc = RoomTransform.posId(rx, y - minY, rz, id);
                        list.removeIf(name -> Arrays.binarySearch(pool.get(name), enc) < 0);
                    }
                    int remaining = cands.stream().mapToInt(List::size).sum();
                    if (remaining == 0) break outer;
                    if (remaining == 1 && mapped >= MIN_MAPPED) break outer;
                }
        }

        // 5. reverse-verify survivors
        String best = null; RoomTransform.Direction bestDir = null; double bestScore = 0;
        for (int di = 0; di < dirs.length; di++) {
            for (String name : cands.get(di)) {
                double score = verifyRatio(mc.level, pool.get(name), dirs[di], corner[di], minY);
                if (score > bestScore) { bestScore = score; best = name; bestDir = dirs[di]; }
            }
        }

        int survivors = cands.stream().mapToInt(List::size).sum();
        if (debug) {
            lastDebug = "§acells§r=" + cells.size() + " §ashape§r=" + shape
                + " §apool§r=" + pool.size() + " §amapped§r=" + mapped
                + " §aminY§r=" + minY + " §asurvivors§r=" + survivors
                + " §abest§r=" + (best == null ? "none" : best + " " + String.format("%.0f%%", bestScore * 100))
                + " §7ids:[" + sample.toString().trim() + "]";
        }

        // 6. confirm-streak
        if (best != null && bestScore >= 0.80) {
            if (best.equals(lastCandidate)) confirmCount++;
            else { confirmCount = 0; lastCandidate = best; }
            if (confirmCount >= 1 && !best.equals(currentRoom)) {
                currentRoom = best;
                currentDir = bestDir;
                anchorX = corner[bestDir.ordinal()][0];
                anchorZ = corner[bestDir.ordinal()][1];
                ConstellationClient.bus().post(new RoomEnteredEvent(best, bestDir, anchorX, anchorZ));
            }
        } else {
            confirmCount = 0;
            lastCandidate = "";
        }
    }

    private static double verifyRatio(Level level, int[] fp, RoomTransform.Direction dir, int[] corner, int refY) {
        if (fp.length == 0) return 0;
        int step = Math.max(1, fp.length / 120);
        int checked = 0, hit = 0;
        for (int i = 0; i < fp.length; i += step) {
            int v = fp[i];
            int rx = DungeonData.idX(v), wy = DungeonData.idY(v), rz = DungeonData.idZ(v);
            byte id = (byte) DungeonData.idBlock(v);
            long[] wp = RoomTransform.relativeToActual(dir, corner[0], corner[1], rx, refY + wy, rz);
            checked++;
            if (blockId(level, (int) wp[0], (int) wp[1], (int) wp[2]) == id) hit++;
        }
        return checked == 0 ? 0 : (double) hit / checked;
    }

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
        // scan a wide Y window at the cell centre — dungeon floors sit around y=68-70
        // but the player can be standing higher, so look from well below to a bit above
        for (int probe : new int[]{cx + 15, cx + 16}) {
            for (int probz : new int[]{cz + 15, cz + 16}) {
                int solid = 0;
                for (int y = floorY - 6; y <= floorY + 1; y++) {
                    if (level.getBlockState(new BlockPos(probe, y, probz)).isSolid()) solid++;
                }
                if (solid >= 3) return true;
            }
        }
        return false;
    }

    private static byte blockId(Level level, int x, int y, int z) {
        BlockState state = level.getBlockState(new BlockPos(x, y, z));
        if (state.isAir()) return 0;
        return DungeonData.numericId(BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString());
    }

    public static String currentRoom() { return currentRoom; }
    public static RoomTransform.Direction currentDir() { return currentDir; }
    public static int anchorX() { return anchorX; }
    public static int anchorZ() { return anchorZ; }

    public record RoomEnteredEvent(String name, RoomTransform.Direction dir, int cornerX, int cornerZ) {}
}
