package com.froggylord.constellation.data;

import com.froggylord.constellation.ConstellationClient;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.*;

public class RoomMatch {

    private static final int MIN_MAPPED = 12;
    private static final double VERIFY_MIN = 0.85;

    
    private static String pendName = null;
    private static RoomTransform.Direction pendDir = null;
    private static int pendAnchorX, pendAnchorZ, pendCount = 0;

    
    private static String currentRoom = "";
    private static RoomTransform.Direction currentDir = RoomTransform.Direction.NW;
    private static int anchorX, anchorZ;

    // footprint cache — while inside...
    private static int fpMinX, fpMinZ, fpMaxX, fpMaxZ;
    private static boolean fpValid = false;
    private static int cellX = Integer.MIN_VALUE, cellZ = Integer.MIN_VALUE;
    private static int retryTick = 0;
    private static int failTick = 0;

    
    
    
    private record Solved(String name, RoomTransform.Direction dir, int anchorX, int anchorZ,
                          int minX, int minZ, int maxX, int maxZ) {}
    private static final Map<Long, Solved> solvedCells = new HashMap<>();

    public static void resetCache() {
        solvedCells.clear();
        currentRoom = ""; fpValid = false;
        cellX = Integer.MIN_VALUE; cellZ = Integer.MIN_VALUE;
        pendName = null; pendDir = null; pendCount = 0;
    }

    public static String lastDebug = "no scan yet";

    public static void update() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || !DungeonData.isLoaded()) {
            currentRoom = ""; fpValid = false; return;
        }
        int cx = RoomGrid.cornerX(mc.player.position());
        int cz = RoomGrid.cornerZ(mc.player.position());

        
        if (!currentRoom.isEmpty() && fpValid && cx >= fpMinX && cx <= fpMaxX && cz >= fpMinZ && cz <= fpMaxZ) {
            cellX = cx; cellZ = cz; return;
        }
        // moved into a new cell outside ...
        if (cx != cellX || cz != cellZ) {
            cellX = cx; cellZ = cz;
            currentRoom = ""; fpValid = false; retryTick = 0;
            pendName = null; pendDir = null; pendCount = 0;

            
            Solved s = solvedCells.get(RoomGrid.cellKey(cx, cz));
            if (s != null) {
                currentRoom = s.name(); currentDir = s.dir();
                anchorX = s.anchorX(); anchorZ = s.anchorZ();
                fpMinX = s.minX(); fpMinZ = s.minZ(); fpMaxX = s.maxX(); fpMaxZ = s.maxZ();
                fpValid = true;
                ConstellationClient.bus().post(new RoomEnteredEvent(currentRoom, currentDir, anchorX, anchorZ));
                return;
            }
        }
        if (!currentRoom.isEmpty()) return;
        
        if (++retryTick % 5 != 0) return;
        match(mc.level, cx, cz, false);
    }

    public static String debugScan() {
        Minecraft mc = Minecraft.getInstance();
        if (!DungeonData.isLoaded()) return "data not loaded";
        if (mc.player == null || mc.level == null) return "not in world";
        int cx = RoomGrid.cornerX(mc.player.position());
        int cz = RoomGrid.cornerZ(mc.player.position());
        // a one-off diagnostic scan that...
        match(mc.level, cx, cz, true);
        return lastDebug;
    }

    private static void match(Level level, int cx, int cz, boolean debug) {
        if (DungeonData.CANDIDATES.isEmpty()) { lastDebug = "no room data"; return; }

        
        int scannedFloor = floorVote(level, cx, cz);
        int floorY = scannedFloor == Integer.MIN_VALUE ? 68 : scannedFloor;

        
        
        Set<Long> cells = MapSegments.footprint();
        boolean fromMap = !cells.isEmpty() && cells.contains(RoomGrid.cellKey(cx, cz));
        if (!fromMap) cells = flood(level, cx, cz, floorY);
        int wMinX = Integer.MAX_VALUE, wMinZ = Integer.MAX_VALUE, wMaxX = Integer.MIN_VALUE, wMaxZ = Integer.MIN_VALUE;
        for (long c : cells) {
            int ccx = (int) (c >> 32), ccz = (int) c;
            wMinX = Math.min(wMinX, ccx); wMinZ = Math.min(wMinZ, ccz);
            wMaxX = Math.max(wMaxX, ccx); wMaxZ = Math.max(wMaxZ, ccz);
        }
        wMaxX += 30; wMaxZ += 30;
        int width = wMaxX - wMinX, length = wMaxZ - wMinZ;

        
        List<DungeonData.Candidate> pool = new ArrayList<>();
        for (var c : DungeonData.CANDIDATES) {
            boolean fit = (close(c.rx(), width) && close(c.rz(), length))
                       || (close(c.rx(), length) && close(c.rz(), width));
            if (fit) pool.add(c);
        }
        if (pool.isEmpty()) {
            lastDebug = "§cno candidates fit§r " + width + "x" + length + " (cells=" + cells.size() + ")";
            if (++failTick % 8 == 0)
                ConstellationClient.LOGGER.info("[room] no-fit cell {},{} | {}x{} cells={} (over-merge?)",
                    cx, cz, width, length, cells.size());
            return;
        }

        // count distinct cell columns/ro...
        
        
        java.util.Set<Integer> segX = new java.util.HashSet<>(), segZ = new java.util.HashSet<>();
        for (long c : cells) { segX.add((int)(c >> 32)); segZ.add((int) c); }
        boolean horizontal = segX.size() > 1 && segZ.size() == 1;
        boolean vertical   = segX.size() == 1 && segZ.size() > 1;

        
        RoomTransform.Direction[] dirs = RoomTransform.Direction.values();
        int[][] corner = {
            {wMinX, wMinZ}, {wMaxX, wMinZ}, {wMinX, wMaxZ}, {wMaxX, wMaxZ}
        };
        boolean[] active = {true, true, true, true};
        if (horizontal) { active[1] = active[2] = false; }      
        else if (vertical) { active[0] = active[3] = false; }   

        List<List<DungeonData.Candidate>> cands = new ArrayList<>();
        for (int d = 0; d < dirs.length; d++) cands.add(active[d] ? new ArrayList<>(pool) : new ArrayList<>());

        
        
        
        
        
        
        int mapped = 0;
        StringBuilder sample = new StringBuilder();
        int maxDim = Math.max(width, length);
        // scan a fixed absolute y band t...
        
        
        
        int scanBottom = 35;
        int scanTop = Math.max(floorY + 40, 120);
        outer:
        for (int y = scanBottom; y <= scanTop; y++) {
            for (int wx = wMinX + 2; wx <= wMaxX - 2; wx += 2)
                for (int wz = wMinZ + 2; wz <= wMaxZ - 2; wz += 2) {
                    if (!cells.contains(RoomGrid.cellKey(RoomGrid.cornerX((double) wx), RoomGrid.cornerZ((double) wz)))) continue;
                    if (inDoorway(wx, y, wz)) continue;
                    byte id = blockId(level, wx, y, wz);
                    if (id == 0) continue;
                    mapped++;
                    if (debug && sample.length() < 40) sample.append(id).append(' ');
                    for (int di = 0; di < dirs.length; di++) {
                        List<DungeonData.Candidate> list = cands.get(di);
                        if (list.isEmpty()) continue;
                        long[] rel = RoomTransform.actualToRelative(dirs[di], corner[di][0], corner[di][1], wx, y, wz);
                        int rx = (int) rel[0], rz = (int) rel[2];
                        if (rx < 0 || rx > maxDim || rz < 0 || rz > maxDim) continue;
                        int enc = RoomTransform.posId(rx, y, rz, id); 
                        list.removeIf(cd -> Arrays.binarySearch(cd.fp(), enc) < 0);
                    }
                    int remaining = cands.stream().mapToInt(List::size).sum();
                    if (remaining == 0) break outer;
                    if (remaining == 1 && mapped >= MIN_MAPPED) break outer;
                }
        }

        
        String best = null; RoomTransform.Direction bestDir = null;
        double bestScore = 0; int bestAX = 0, bestAZ = 0;
        for (int di = 0; di < dirs.length; di++) {
            for (var c : cands.get(di)) {
                double score = verifyRatio(level, c.fp(), dirs[di], corner[di]);
                if (score > bestScore) {
                    bestScore = score; best = c.name(); bestDir = dirs[di];
                    bestAX = corner[di][0]; bestAZ = corner[di][1];
                }
            }
        }

        int survivors = cands.stream().mapToInt(List::size).sum();
        boolean confident = best != null && mapped >= MIN_MAPPED && bestScore >= VERIFY_MIN;

        if (debug) {
            lastDebug = "§a" + (fromMap ? "MAP" : "flood[" + MapSegments.lastDebug + "]") + "§r cells=" + cells.size() + " §asize§r=" + width + "x" + length
                + " §apool§r=" + pool.size() + " §amapped§r=" + mapped + " §afloorY§r=" + floorY
                + " §asurv§r=" + survivors + " §abest§r=" + (best == null ? "none" : best + " " + String.format("%.0f%%", bestScore * 100))
                + (mapped == 0 ? " §c[0 blocks mapped]§r" : "")
                + " §7ids:[" + sample.toString().trim() + "]";
        }

        
        if (confident && best.equals(pendName) && bestDir == pendDir && bestAX == pendAnchorX && bestAZ == pendAnchorZ) {
            pendCount++;
        } else if (confident) {
            pendName = best; pendDir = bestDir; pendAnchorX = bestAX; pendAnchorZ = bestAZ; pendCount = 1;
        } else {
            pendName = null; pendDir = null; pendCount = 0;
        }

        if (pendCount >= 2) {
            currentRoom = best; currentDir = bestDir; anchorX = bestAX; anchorZ = bestAZ;
            fpMinX = wMinX; fpMinZ = wMinZ; fpMaxX = wMaxX - 30; fpMaxZ = wMaxZ - 30; fpValid = true;
            
            Solved solved = new Solved(currentRoom, currentDir, anchorX, anchorZ, fpMinX, fpMinZ, fpMaxX, fpMaxZ);
            for (long cell : cells) solvedCells.put(cell, solved);
            ConstellationClient.bus().post(new RoomEnteredEvent(currentRoom, currentDir, anchorX, anchorZ));
            ConstellationClient.LOGGER.info("[room] MATCHED {} ({}) src={} {}x{} mapped={} verify={}%",
                best, bestDir, fromMap ? "MAP" : "flood", width, length, mapped, Math.round(bestScore * 100));
        } else if (!fpValid) {
            // quiet diagnostic for auto-dete...
            failTick++;
            if (failTick % 8 == 0) {
                ConstellationClient.LOGGER.info("[room] no-match cell {},{} | src={} {}x{} cells={} pool={} mapped={} surv={} best={} {}% | map[{}]",
                    cx, cz, fromMap ? "MAP" : "flood", width, length, cells.size(), pool.size(), mapped, survivors,
                    best == null ? "none" : best, Math.round(bestScore * 100), MapSegments.lastDebug);
            }
        }
    }

    private static double verifyRatio(Level level, int[] fp, RoomTransform.Direction dir, int[] corner) {
        if (fp.length == 0) return 0;
        int step = Math.max(1, fp.length / 120);
        int checked = 0, hit = 0;
        for (int i = 0; i < fp.length; i += step) {
            int v = fp[i];
            int rx = DungeonData.idX(v), wy = DungeonData.idY(v), rz = DungeonData.idZ(v);
            byte id = (byte) DungeonData.idBlock(v);
            
            long[] wp = RoomTransform.relativeToActual(dir, corner[0], corner[1], rx, wy, rz);
            checked++;
            if (blockId(level, (int) wp[0], (int) wp[1], (int) wp[2]) == id) hit++;
        }
        return checked == 0 ? 0 : (double) hit / checked;
    }

    private static boolean inDoorway(int x, int y, int z) {
        if (y < 66 || y > 73) return false;
        int lx = Math.floorMod(x - 8, 32);
        int lz = Math.floorMod(z - 8, 32);
        
        boolean notDoor = (lx < 13 || lx > 17 || lz > 2 && lz < 28) && (lz < 13 || lz > 17 || lx > 2 && lx < 28);
        return !notDoor;
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

    public static Set<Long> floodPublic(Level level, int startCX, int startCZ) {
        int fy = floorVote(level, startCX, startCZ);
        return flood(level, startCX, startCZ, fy == Integer.MIN_VALUE ? 68 : fy);
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
        
        
        return present * 2 >= samples;
    }

    private static boolean openColumn(Level level, int x, int z, int floorY) {
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        for (int y = floorY + 3; y >= floorY - 4; y--) {
            m.set(x, y, z);
            if (level.getBlockState(m).isAir()) continue;
            m.set(x, y + 1, z);
            if (level.getBlockState(m).isAir()) return true; 
            return false; 
        }
        return false;
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
