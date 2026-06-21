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
 * the detection algorithm — seam-flood footprint, dimension-fit candidate filtering,
 * four-rotation elimination, foundation-Y reference, reverse-verify, confirm-streak —
 * is a fresh implementation.
 */
public class RoomMatch {

    private static final int MIN_MAPPED = 12;

    // committed result
    private static String currentRoom = "";
    private static RoomTransform.Direction currentDir = RoomTransform.Direction.NW;
    private static int anchorX, anchorZ;

    // footprint cache — while inside this box we keep the room without rescanning
    private static int fpMinX, fpMinZ, fpMaxX, fpMaxZ;
    private static boolean fpValid = false;
    private static int cellX = Integer.MIN_VALUE, cellZ = Integer.MIN_VALUE;
    private static int retryTick = 0;

    public static String lastDebug = "no scan yet";

    /** Called every client tick while in a dungeon. Cheap when already identified. */
    public static void update() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || !DungeonData.isLoaded()) {
            currentRoom = ""; fpValid = false; return;
        }
        int cx = RoomGrid.cornerX(mc.player.position());
        int cz = RoomGrid.cornerZ(mc.player.position());

        // still inside the room we already identified? keep it, don't rescan
        if (!currentRoom.isEmpty() && fpValid && cx >= fpMinX && cx <= fpMaxX && cz >= fpMinZ && cz <= fpMaxZ) {
            cellX = cx; cellZ = cz; return;
        }
        // moved into a new cell outside the known room — reset
        if (cx != cellX || cz != cellZ) {
            cellX = cx; cellZ = cz;
            currentRoom = ""; fpValid = false; retryTick = 0;
        }
        if (!currentRoom.isEmpty()) return;
        // retry ~4x/sec until we get it (blocks may not be loaded the instant you walk in)
        if (++retryTick % 5 != 0) return;
        match(mc.level, cx, cz, false);
    }

    public static String debugScan() {
        Minecraft mc = Minecraft.getInstance();
        if (!DungeonData.isLoaded()) return "data not loaded";
        if (mc.player == null || mc.level == null) return "not in world";
        int cx = RoomGrid.cornerX(mc.player.position());
        int cz = RoomGrid.cornerZ(mc.player.position());
        // a one-off diagnostic scan that doesn't disturb the live committed room
        match(mc.level, cx, cz, true);
        return lastDebug;
    }

    private static void match(Level level, int cx, int cz, boolean debug) {
        if (DungeonData.CANDIDATES.isEmpty()) { lastDebug = "no room data"; return; }

        // 1. floor Y
        int scannedFloor = floorVote(level, cx, cz);
        int floorY = scannedFloor == Integer.MIN_VALUE ? 68 : scannedFloor;

        // 2. footprint (seam-connected cells)
        Set<Long> cells = flood(level, cx, cz, floorY);
        int wMinX = Integer.MAX_VALUE, wMinZ = Integer.MAX_VALUE, wMaxX = Integer.MIN_VALUE, wMaxZ = Integer.MIN_VALUE;
        for (long c : cells) {
            int ccx = (int) (c >> 32), ccz = (int) c;
            wMinX = Math.min(wMinX, ccx); wMinZ = Math.min(wMinZ, ccz);
            wMaxX = Math.max(wMaxX, ccx); wMaxZ = Math.max(wMaxZ, ccz);
        }
        wMaxX += 31; wMaxZ += 31;
        int width = wMaxX - wMinX, length = wMaxZ - wMinZ;
        int maxDim = Math.max(width, length);

        // 3. filter candidates by footprint fit (either rotation), ±2 tolerance
        List<DungeonData.Candidate> pool = new ArrayList<>();
        for (var c : DungeonData.CANDIDATES) {
            boolean fit = (close(c.rx(), width) && close(c.rz(), length))
                       || (close(c.rx(), length) && close(c.rz(), width));
            if (fit) pool.add(c);
        }
        if (pool.isEmpty()) {
            lastDebug = "§cno candidates fit§r " + (width + 1) + "x" + (length + 1) + " (cells=" + cells.size() + ")";
            return;
        }

        RoomTransform.Direction[] dirs = RoomTransform.Direction.values();
        int[][] corner = {
            {wMinX, wMinZ}, {wMaxX, wMinZ}, {wMinX, wMaxZ}, {wMaxX, wMaxZ}
        };
        List<List<DungeonData.Candidate>> cands = new ArrayList<>();
        for (int d = 0; d < dirs.length; d++) cands.add(new ArrayList<>(pool));

        // 4. foundation Y — go deep enough for sunken rooms (double-stair, etc.)
        int minY = Integer.MAX_VALUE;
        for (int dy = -50; dy <= 60; dy++) {
            int y = floorY + dy;
            for (int wx = wMinX + 2; wx <= wMaxX - 2; wx += 2)
                for (int wz = wMinZ + 2; wz <= wMaxZ - 2; wz += 2) {
                    if (!cells.contains(RoomGrid.cellKey(RoomGrid.cornerX((double) wx), RoomGrid.cornerZ((double) wz)))) continue;
                    if (inDoorway(wx, y, wz)) continue;
                    if (blockId(level, wx, y, wz) != 0 && y < minY) minY = y;
                }
        }
        if (minY == Integer.MAX_VALUE) minY = floorY;

        // 5. elimination loop — remove candidates that don't contain each observed block.
        // once a single survivor remains with ≥MIN_MAPPED blocks, continue scanning to
        // double-check it (10 more block confirmations within the SAME scan → commit).
        int mapped = 0;
        StringBuilder sample = new StringBuilder();
        DungeonData.Candidate survivor = null;
        RoomTransform.Direction survDir = null;
        int survAX = 0, survAZ = 0;
        int[] survFp = null;
        int doubleChecked = 0;
        boolean committed = false;

        outer:
        for (int dy = -50; dy <= 60; dy++) {
            int y = floorY + dy;
            for (int wx = wMinX + 2; wx <= wMaxX - 2; wx += 2)
                for (int wz = wMinZ + 2; wz <= wMaxZ - 2; wz += 2) {
                    if (!cells.contains(RoomGrid.cellKey(RoomGrid.cornerX((double) wx), RoomGrid.cornerZ((double) wz)))) continue;
                    if (inDoorway(wx, y, wz)) continue;
                    byte id = blockId(level, wx, y, wz);
                    if (id == 0) continue;
                    mapped++;
                    if (debug && sample.length() < 40) sample.append(id).append(' ');

                    if (survivor != null) {
                        // double-check phase: does this block exist in the survivor's fp?
                        long[] rel = RoomTransform.actualToRelative(survDir, survAX, survAZ, wx, y, wz);
                        int rx = (int) rel[0], rz = (int) rel[2];
                        if (rx >= 0 && rx <= maxDim && rz >= 0 && rz <= maxDim) {
                            int enc = RoomTransform.posId(rx, y - minY, rz, id);
                            if (Arrays.binarySearch(survFp, enc) >= 0) {
                                doubleChecked++;
                                if (doubleChecked >= 10) {
                                    committed = true;
                                    break outer;
                                }
                            }
                        }
                        continue; // don't eliminate anything — we already know the room
                    }

                    // elimination phase: remove candidates that don't contain this block
                    for (int di = 0; di < dirs.length; di++) {
                        List<DungeonData.Candidate> list = cands.get(di);
                        if (list.isEmpty()) continue;
                        long[] rel = RoomTransform.actualToRelative(dirs[di], corner[di][0], corner[di][1], wx, y, wz);
                        int rx = (int) rel[0], rz = (int) rel[2];
                        if (rx < 0 || rx > maxDim || rz < 0 || rz > maxDim) continue;
                        int enc = RoomTransform.posId(rx, y - minY, rz, id);
                        list.removeIf(cd -> Arrays.binarySearch(cd.fp(), enc) < 0);
                    }
                    int remaining = cands.stream().mapToInt(List::size).sum();
                    if (remaining == 0) break outer;

                    // found a single survivor? lock it in and switch to double-check
                    if (remaining == 1 && mapped >= MIN_MAPPED) {
                        for (int di = 0; di < dirs.length; di++) {
                            if (cands.get(di).size() == 1) {
                                survivor = cands.get(di).get(0);
                                survDir = dirs[di];
                                survAX = corner[di][0];
                                survAZ = corner[di][1];
                                survFp = survivor.fp();
                                break;
                            }
                        }
                    }
                }
        }

        // 6. single-scan commit: if we locked in a survivor and double-checked ≥10 blocks within
        //    this scan, commit immediately. no multi-scan streak needed.
        if (committed && survivor != null) {
            currentRoom = survivor.name();
            currentDir = survDir;
            anchorX = survAX;
            anchorZ = survAZ;
            fpMinX = wMinX; fpMinZ = wMinZ;
            fpMaxX = wMaxX - 31; fpMaxZ = wMaxZ - 31;
            fpValid = true;
            ConstellationClient.bus().post(new RoomEnteredEvent(currentRoom, currentDir, anchorX, anchorZ));
        }

        int survivors = survivor != null ? 1 : cands.stream().mapToInt(List::size).sum();
        if (debug) {
            lastDebug = "§acells§r=" + cells.size() + " §asize§r=" + (width + 1) + "x" + (length + 1)
                + " §apool§r=" + pool.size() + " §amapped§r=" + mapped + " §aminY§r=" + minY
                + " §asurv§r=" + survivors + " §adbl§r=" + doubleChecked
                + " §aresult§r=" + (committed ? "§a" + currentRoom : (survivor != null ? "§edouble-checking" : "§cnone"))
                + (mapped == 0 ? " §c[0 blocks mapped]§r" : "")
                + " §7ids:[" + sample.toString().trim() + "]";
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

    private static int floorVote(Level level, int cx, int cz) {
        Minecraft mc = Minecraft.getInstance();
        int start = mc.player != null ? (int) Math.floor(mc.player.getY()) + 2 : 80;
        Map<Integer, Integer> votes = new HashMap<>();
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        for (int lx = 6; lx <= 24; lx += 6)
            for (int lz = 6; lz <= 24; lz += 6) {
                int x = cx + lx, z = cz + lz;
                for (int y = start; y > start - 30 && y > 0; y--) {
                    m.set(x, y, z);
                    if (level.getBlockState(m).isAir()) continue;
                    if (blockId(level, x, y, z) == 0) continue;
                    m.set(x, y + 1, z);
                    if (level.getBlockState(m).isAir()) { votes.merge(y, 1, Integer::sum); break; }
                }
            }
        int best = Integer.MIN_VALUE, bestN = 0;
        for (var e : votes.entrySet()) if (e.getValue() > bestN) { bestN = e.getValue(); best = e.getKey(); }
        return best;
    }

    private static Set<Long> flood(Level level, int startCX, int startCZ, int floorY) {
        Set<Long> found = new LinkedHashSet<>();
        Deque<int[]> q = new ArrayDeque<>();
        Set<Long> seen = new HashSet<>();
        q.add(new int[]{startCX, startCZ});
        seen.add(RoomGrid.cellKey(startCX, startCZ));
        int[][] dirs = {{32, 0}, {-32, 0}, {0, 32}, {0, -32}};
        while (!q.isEmpty() && found.size() < 6) {
            int[] c = q.poll();
            found.add(RoomGrid.cellKey(c[0], c[1]));
            for (int[] d : dirs) {
                int nx = c[0] + d[0], nz = c[1] + d[1];
                long nk = RoomGrid.cellKey(nx, nz);
                if (seen.contains(nk)) continue;
                if (connected(level, c[0], c[1], d[0], d[1], floorY)) {
                    seen.add(nk);
                    q.add(new int[]{nx, nz});
                }
            }
        }
        return found;
    }

    private static boolean connected(Level level, int cxA, int czA, int dx, int dz, int floorY) {
        int present = 0, samples = 0;
        if (dx != 0) {
            int seamX = dx > 0 ? cxA + 31 : cxA - 1;
            for (int lz = 2; lz <= 28; lz += 2) { samples++; if (openColumn(level, seamX, czA + lz, floorY)) present++; }
        } else {
            int seamZ = dz > 0 ? czA + 31 : czA - 1;
            for (int lx = 2; lx <= 28; lx += 2) { samples++; if (openColumn(level, cxA + lx, seamZ, floorY)) present++; }
        }
        // at least 35% open = same room. doorway walls are ~3 wide, multi-tile
        // passages 10+. this threshold catches both without merging different rooms.
        return present * 3 >= samples;
    }

    private static boolean openColumn(Level level, int x, int z, int floorY) {
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        for (int y = floorY + 3; y >= floorY - 4; y--) {
            m.set(x, y, z);
            if (level.getBlockState(m).isAir()) continue;
            if (blockId(level, x, y, z) == 0) continue;
            m.set(x, y + 1, z);
            if (level.getBlockState(m).isAir()) return true;
        }
        return false;
    }

    /** Doorway zones: the 4-block gap between rooms (shared blocks, not in any skeleton). */
    private static boolean inDoorway(int x, int y, int z) {
        if (y < 66 || y > 73) return false;
        int lx = Math.floorMod(x, 32);
        int lz = Math.floorMod(z, 32);
        return (lx <= 3 || lx >= 28) || (lz <= 3 || lz >= 28);
    }

    private static byte blockId(Level level, int x, int y, int z) {
        BlockState state = level.getBlockState(new BlockPos(x, y, z));
        if (state.isAir()) return 0;
        return DungeonData.numericId(BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString());
    }

    private static boolean close(int a, int b) { return Math.abs(a - b) <= 2; }

    public static String currentRoom() { return currentRoom; }
    public static RoomTransform.Direction currentDir() { return currentDir; }
    public static int anchorX() { return anchorX; }
    public static int anchorZ() { return anchorZ; }
    public static boolean isMatched() { return fpValid && !currentRoom.isEmpty(); }

    public record RoomEnteredEvent(String name, RoomTransform.Direction dir, int cornerX, int cornerZ) {}
}
