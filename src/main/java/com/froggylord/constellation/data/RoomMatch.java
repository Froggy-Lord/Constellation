package com.froggylord.constellation.data;

import com.froggylord.constellation.ConstellationClient;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import java.util.*;

// matching engine ported from Skyblocker (LGPL):
//   de/hysky/skyblocker/skyblock/dungeon/secrets/DungeonManager.java
//   de/hysky/skyblocker/skyblock/dungeon/secrets/Room.java
//   de/hysky/skyblocker/skyblock/dungeon/secrets/DungeonMapUtils.java
public class RoomMatch {

    private static final int DOUBLE_CHECK_BLOCKS = 10;
    private static final int FLOOR_REFERENCE_Y = 68;

    // ---- public state (preserved API) ----
    private static String currentRoom = "";
    private static RoomTransform.Direction currentDir = RoomTransform.Direction.NW;
    private static int anchorX, anchorZ;
    private static int currentFloorY = FLOOR_REFERENCE_Y;
    private static boolean fpValid = false;

    // footprint cache — while inside the same room, skip re-matching
    private static int fpMinX, fpMinZ, fpMaxX, fpMaxZ;
    private static int cellX = Integer.MIN_VALUE, cellZ = Integer.MIN_VALUE;
    private static int retryTick = 0;
    private static int failTick = 0;
    private static long lastVerifiedTick = Long.MIN_VALUE;
    private static long verifiedNoMatchCell = Long.MIN_VALUE;
    private static String mapToken = "";

    public static String lastDebug = "no scan yet";

    // ---- solved-cells cache (preserved) ----
    private record Solved(String name, RoomTransform.Direction dir,
                          int anchorX, int anchorZ,
                          int minX, int minZ, int maxX, int maxZ) {}
    private static final Map<Long, Solved> solvedCells = new HashMap<>();

    // ---- preload (FunnyMap-style progressive room pre-identification) ----
    private static final int PRELOAD_BUDGET = 2;          // cells scanned per tick
    private static final int PRELOAD_REFILL_TICKS = 40;   // rebuild the queue every ~2s
    private static final Deque<Long> preloadQueue = new ArrayDeque<>();
    private static long preloadRefreshTick = Long.MIN_VALUE;

    // ---- ported from Skyblocker: rooms-by-physical-pos map ----
    private record PhysKey(int x, int z) {
        static PhysKey of(int x, int z) { return new PhysKey(x, z); }
    }
    // one matching context per physical cell
    private static final Map<PhysKey, MatchCtx> roomCtxs = new HashMap<>();

    // ---- ported matching state (Skyblocker Room fields) ----
    private enum MatchState { MATCHING, DOUBLE_CHECKING, MATCHED, FAILED }

    // ported from Skyblocker (LGPL): Room.Shape
    enum Shape {
        ONE_BY_ONE("1x1"), ONE_BY_TWO("1x2"), ONE_BY_THREE("1x3"),
        ONE_BY_FOUR("1x4"), L_SHAPE("L-shape"), TWO_BY_TWO("2x2"),
        PUZZLE("puzzle"), TRAP("trap"), MINIBOSS("miniboss");
        final String key;
        Shape(String k) { key = k; }
    }

    // ported from Skyblocker (LGPL): a (direction, cornerX, cornerZ, roomNameList) candidate triple
    private static final class Candidate {
        final RoomTransform.Direction dir;
        final int cornerX, cornerZ;
        final List<String> rooms; // mutable — filtered down during scan

        Candidate(RoomTransform.Direction dir, int cx, int cz, List<String> rooms) {
            this.dir = dir; this.cornerX = cx; this.cornerZ = cz; this.rooms = rooms;
        }
    }

    // ported from Skyblocker (LGPL): per-room matching context
    private static final class MatchCtx {
        final Shape shape;
        final Set<Long> cells;
        final int wMinX, wMinZ, wMaxX, wMaxZ;
        final Set<Long> checkedBlocks = new HashSet<>();
        final List<Candidate> possibleRooms = new ArrayList<>();
        MatchState matchState = MatchState.MATCHING;

        MatchCtx(Shape shape, Set<Long> cells, int wMinX, int wMinZ, int wMaxX, int wMaxZ) {
            this.shape = shape;
            this.cells = cells;
            this.wMinX = wMinX; this.wMinZ = wMinZ;
            this.wMaxX = wMaxX; this.wMaxZ = wMaxZ;
        }
        int doubleCheckCount = 0;
        String matchedName = null;
        RoomTransform.Direction matchedDir = null;
        int matchedAnchorX, matchedAnchorZ;
    }

    // ======================== PUBLIC API ========================

    public static void init() {
        ConstellationClient.bus().subscribe(DungeonState.DungeonEnter.class, ignored -> resetCache());
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents.JOIN.register(
            (handler, sender, client) -> resetCache());
    }

    public static void resetCache() {
        leaveCurrent();
        solvedCells.clear();
        roomCtxs.clear();
        preloadQueue.clear();
        preloadRefreshTick = Long.MIN_VALUE;
        cellX = Integer.MIN_VALUE; cellZ = Integer.MIN_VALUE;
        verifiedNoMatchCell = Long.MIN_VALUE;
    }

    public static String currentRoom() { return currentRoom; }
    public static RoomTransform.Direction currentDir() { return currentDir; }
    public static int floorY() { return currentFloorY; }
    public static int anchorX() { return anchorX; }
    public static int anchorZ() { return anchorZ; }
    public static boolean isMatched() { return fpValid && !currentRoom.isEmpty(); }

    public static void update() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || !DungeonData.isLoaded()) {
            leaveCurrent(); return;
        }

        String nextMapToken = MapSegments.currentMapToken();
        if (!nextMapToken.isEmpty() && !mapToken.isEmpty() && !nextMapToken.equals(mapToken)) resetCache();
        if (!nextMapToken.isEmpty()) mapToken = nextMapToken;

        updateCurrentCell(mc);
        // FunnyMap-style preload: after the current cell, pre-identify revealed rooms
        // whose chunks are already loaded and cache them into solvedCells, so walking in
        // becomes an instant cache hit. Pure cache population — no current-room side effects.
        preloadRevealedCells(mc.level);
    }

    // handles ONLY the player's current cell (lazy match on entry) — unchanged behaviour
    private static void updateCurrentCell(Minecraft mc) {
        int cx = RoomGrid.cornerX(mc.player.position());
        int cz = RoomGrid.cornerZ(mc.player.position());

        // still inside the same confirmed room? skip
        if (!currentRoom.isEmpty() && fpValid && cx >= fpMinX && cx <= fpMaxX && cz >= fpMinZ && cz <= fpMaxZ) {
            if (mc.level.getGameTime() - lastVerifiedTick >= 20) {
                leaveCurrent();
                return;
            }
            lastVerifiedTick = mc.level.getGameTime();
            cellX = cx; cellZ = cz; return;
        }
        if (!currentRoom.isEmpty()) leaveCurrent();

        // moved into a new cell
        if (cx != cellX || cz != cellZ) {
            cellX = cx; cellZ = cz;
            retryTick = 0;
            // check solved cache first
            Solved s = solvedCells.get(RoomGrid.cellKey(cx, cz));
            if (s != null) {
                currentRoom = s.name(); currentDir = s.dir();
                anchorX = s.anchorX(); anchorZ = s.anchorZ();
                fpMinX = s.minX(); fpMinZ = s.minZ(); fpMaxX = s.maxX(); fpMaxZ = s.maxZ();
                fpValid = true;
                lastVerifiedTick = mc.level.getGameTime();
                ConstellationClient.dungeon().enterRoom(currentRoom, currentDir, anchorX, anchorZ);
                return;
            }
        }
        if (!currentRoom.isEmpty()) return;

        // skyblocker retries matching aggressively - every 20 ticks was way too slow to lock a room
        if (++retryTick % 2 != 0) return;
        match(mc.level, cx, cz, false);
    }

    // ======================== PRELOAD (FunnyMap-style) ========================

    // Pre-identify revealed rooms whose chunks are already loaded, caching confident
    // matches into solvedCells so the player walks into an instant hit. Mirrors
    // FunnyMap/IllegalMap: scan every room within render distance, progressively. Pure
    // cache population — never mutates currentRoom / currentDir / fpValid / enterRoom.
    private static void preloadRevealedCells(Level level) {
        long now = level.getGameTime();
        if (now - preloadRefreshTick >= PRELOAD_REFILL_TICKS) {
            refillPreloadQueue();
            preloadRefreshTick = now;
        }

        int budget = PRELOAD_BUDGET;
        int attempts = preloadQueue.size();     // at most one pass over the queue per tick
        while (budget > 0 && attempts-- > 0 && !preloadQueue.isEmpty()) {
            long cellKey = preloadQueue.pollFirst();
            if (solvedCells.containsKey(cellKey)) continue;   // already solved (current match may have)

            int pcx = (int) (cellKey >> 32);
            int pcz = (int) cellKey;

            // chunk-loaded gate: can only scan a cell whose chunks are present (the same
            // render-distance limit FunnyMap has). Re-queue unloaded cells to retry later.
            int chunkX = (pcx + 15) >> 4, chunkZ = (pcz + 15) >> 4;
            if (!level.getChunkSource().hasChunk(chunkX, chunkZ)) {
                preloadQueue.addLast(cellKey);
                continue;
            }

            boolean resolved = matchCell(level, pcx, pcz);
            if (!resolved) preloadQueue.addLast(cellKey);     // retry next round
            budget--;
        }
    }

    // rebuild the round-robin queue from the map's currently-revealed cells, skipping
    // ones already solved or already queued
    private static void refillPreloadQueue() {
        Set<Long> revealed = MapSegments.revealedCells();
        if (revealed.isEmpty()) return;
        Set<Long> queued = new HashSet<>(preloadQueue);
        for (long cell : revealed) {
            if (solvedCells.containsKey(cell)) continue;
            if (queued.add(cell)) preloadQueue.addLast(cell);
        }
    }

    // Identify an ARBITRARY cell in a single pass and, on a confident (double-checked)
    // match, write it into solvedCells. Does NOT touch currentRoom / currentDir / fpValid
    // or fire enterRoom — pure cache population. Returns true once the cell is resolved.
    private static boolean matchCell(Level level, int cx, int cz) {
        int floorY = floorVote(level, cx, cz);
        if (floorY == Integer.MIN_VALUE) return false;    // no floor -> chunks empty / not a room

        Set<Long> cells = flood(level, cx, cz, floorY);
        if (cells.isEmpty()) return false;

        int wMinX = Integer.MAX_VALUE, wMinZ = Integer.MAX_VALUE, wMaxX = Integer.MIN_VALUE, wMaxZ = Integer.MIN_VALUE;
        Set<Integer> segX = new HashSet<>(), segZ = new HashSet<>();
        for (long c : cells) {
            int ccx = (int) (c >> 32), ccz = (int) c;
            wMinX = Math.min(wMinX, ccx); wMinZ = Math.min(wMinZ, ccz);
            wMaxX = Math.max(wMaxX, ccx); wMaxZ = Math.max(wMaxZ, ccz);
            segX.add(ccx); segZ.add(ccz);
        }

        Shape shape = shapeFor(cells.size(), segX, segZ);
        if (shape == null) return false;

        MatchCtx ctx = new MatchCtx(shape, cells, wMinX, wMinZ, wMaxX, wMaxZ);
        initCandidates(ctx, segX, segZ);
        if (ctx.possibleRooms.isEmpty()) return false;

        int scanBottom = floorY, scanTop = floorY + 40;
        int mappedBlockCount = 0;
        // scan each cell's interior once; confident match = a single candidate surviving
        // DOUBLE_CHECK_BLOCKS consecutive identifying blocks (single-pass equivalent of the
        // current-cell matcher's per-tick double-check).
        for (long c : cells) {
            int ccx = (int) (c >> 32), ccz = (int) c;
            for (int wx = ccx; wx <= ccx + 30; wx++) {
                for (int wz = ccz; wz <= ccz + 30; wz++) {
                    for (int wy = scanBottom; wy <= scanTop; wy++) {
                        if (notInDoorway(wx, wy, wz)) continue;
                        long bk = blockKey(wx, wy, wz);
                        if (!ctx.checkedBlocks.add(bk)) continue;
                        byte id = blockId(level, wx, wy, wz);
                        if (id == 0) continue;
                        mappedBlockCount++;

                        int totalLeft = checkBlockAgainstCandidates(ctx, id, wx, wy, wz);
                        if (totalLeft == 0) return false;    // contradiction -> not this room
                        if (totalLeft == 1 && ctx.matchState == MatchState.MATCHING) {
                            ctx.matchState = MatchState.DOUBLE_CHECKING;
                            promoteToDoubleCheck(ctx);
                        } else if (ctx.matchState == MatchState.DOUBLE_CHECKING) {
                            if (++ctx.doubleCheckCount >= DOUBLE_CHECK_BLOCKS) {
                                storePreloaded(ctx, cx, cz, wMinX, wMinZ, wMaxX, wMaxZ);
                                return true;
                            }
                        }
                        if (mappedBlockCount >= 400) return false;   // scanned plenty, ambiguous
                    }
                }
            }
        }
        return false;
    }

    // write a preloaded match into solvedCells WITHOUT any current-room side effects
    private static void storePreloaded(MatchCtx ctx, int cx, int cz,
                                       int wMinX, int wMinZ, int wMaxX, int wMaxZ) {
        Solved solved = new Solved(ctx.matchedName, ctx.matchedDir,
            ctx.matchedAnchorX, ctx.matchedAnchorZ,
            wMinX, wMinZ, wMaxX + 30, wMaxZ + 30);
        for (long cell : ctx.cells) solvedCells.put(cell, solved);
        ConstellationClient.LOGGER.info("[room] preloaded {} ({}) cell {},{}",
            ctx.matchedName, ctx.matchedDir, cx, cz);
    }

    public static String debugScan() {
        Minecraft mc = Minecraft.getInstance();
        if (!DungeonData.isLoaded()) return "data not loaded";
        if (mc.player == null || mc.level == null) return "not in world";
        int cx = RoomGrid.cornerX(mc.player.position());
        int cz = RoomGrid.cornerZ(mc.player.position());
        match(mc.level, cx, cz, true);
        return lastDebug;
    }

    /** public flood access for SkeletonScraper */
    public static Set<Long> floodPublic(Level level, int startCX, int startCZ) {
        int fy = floorVote(level, startCX, startCZ);
        return flood(level, startCX, startCZ, fy == Integer.MIN_VALUE ? 68 : fy);
    }

    // ======================== MATCHING CORE ========================

    // ported from Skyblocker (LGPL): DungeonManager.update + Room.tick + Room.checkBlock
    private static void match(Level level, int cx, int cz, boolean debug) {
        int scannedFloor = floorVote(level, cx, cz);
        int floorY = scannedFloor == Integer.MIN_VALUE ? FLOOR_REFERENCE_Y : scannedFloor;
        currentFloorY = floorY;

        // ---- footprint (same as before) ----
        Set<Long> cells = MapSegments.footprint();
        boolean fromMap = !cells.isEmpty() && cells.contains(RoomGrid.cellKey(cx, cz));
        if (fromMap) {
            Set<Long> connectedCells = flood(level, cx, cz, floorY);
            cells.retainAll(connectedCells);
            if (cells.isEmpty()) cells = connectedCells;
            RoomType type = MapSegments.currentType();
            if (type == RoomType.BLOOD && ConstellationClient.dungeon().inBoss()) type = RoomType.BOSS;
            if (type.skipsFingerprint()) {
                leaveCurrent();
                ConstellationClient.dungeon().enterTypedRoom(type, cx, cz);
                lastDebug = "typed " + type + " cell=" + cx + "," + cz + " floor=" + ConstellationClient.dungeon().floor();
                return;
            }
        } else {
            cells = flood(level, cx, cz, floorY);
        }

        // ---- bounding box & shape ----
        int wMinX = Integer.MAX_VALUE, wMinZ = Integer.MAX_VALUE, wMaxX = Integer.MIN_VALUE, wMaxZ = Integer.MIN_VALUE;
        for (long c : cells) {
            int ccx = (int) (c >> 32), ccz = (int) c;
            wMinX = Math.min(wMinX, ccx); wMinZ = Math.min(wMinZ, ccz);
            wMaxX = Math.max(wMaxX, ccx); wMaxZ = Math.max(wMaxZ, ccz);
        }

        // determine cell columns/rows for shape + orientation hint
        Set<Integer> segX = new HashSet<>(), segZ = new HashSet<>();
        for (long c : cells) { segX.add((int)(c >> 32)); segZ.add((int) c); }

        // ported from Skyblocker (LGPL): Shape.determineShape
        int segCount = cells.size();
        Shape shape = shapeFor(segCount, segX, segZ);
        if (shape == null) {
            lastDebug = "unexpected cell count " + segCount;
            return;
        }

        // ---- get or create matching context for this cell ----
        PhysKey pk = PhysKey.of(cx, cz);
        MatchCtx ctx = roomCtxs.get(pk);
        if (ctx == null) {
            ctx = new MatchCtx(shape, cells, wMinX, wMinZ, wMaxX, wMaxZ);
            initCandidates(ctx, segX, segZ);
            roomCtxs.put(pk, ctx);
        }

        int mappedBlockCount = 0;
        StringBuilder sample = new StringBuilder();
        int scanBottom = floorY;
        int scanTop = floorY + 40;

        // ---- scan blocks (11x11x11 box around player, same as Skyblocker) ----
        BlockPos playerPos = Minecraft.getInstance().player.blockPosition();
        scanLoop:
        for (BlockPos pos : BlockPos.betweenClosed(
                playerPos.offset(-5, -5, -5), playerPos.offset(5, 5, 5))) {
            int wx = pos.getX(), wy = pos.getY(), wz = pos.getZ();
            if (wy < scanBottom || wy > scanTop) continue;
            if (!cellContains(ctx.cells, wx, wz)) continue;
            if (notInDoorway(wx, wy, wz)) continue;

            long blockKey = blockKey(wx, wy, wz);
            if (!ctx.checkedBlocks.add(blockKey)) continue;

            byte id = blockId(level, wx, wy, wz);
            if (id == 0) continue;
            mappedBlockCount++;
            if (debug && sample.length() < 40) sample.append(id).append(' ');

            // ported from Skyblocker (LGPL): Room.checkBlock
            // For each candidate direction, filter rooms that contain this block
            int totalLeft = checkBlockAgainstCandidates(ctx, id, wx, wy, wz);

            if (totalLeft == 0) {
                // no rooms match — fail and retry later
                ctx.matchState = MatchState.FAILED;
                ConstellationClient.LOGGER.info("[room] failed match at {},{} after {} blocks",
                    cx, cz, ctx.checkedBlocks.size());
                if (debug) lastDebug = "failed — no rooms match after " + ctx.checkedBlocks.size() + " blocks";
                return;
            } else if (totalLeft == 1 && ctx.matchState == MatchState.MATCHING) {
                // unique match — enter double-check phase
                ctx.matchState = MatchState.DOUBLE_CHECKING;
                promoteToDoubleCheck(ctx);
                ConstellationClient.LOGGER.info("[room] unique match candidate {} at {},{} — double-checking",
                    ctx.matchedName, cx, cz);
            }

            if (mappedBlockCount >= 200) break;
        }

        // ---- check double-confirm or confirm ----
        if (ctx.matchState == MatchState.DOUBLE_CHECKING) {
            ctx.doubleCheckCount++;
            if (ctx.doubleCheckCount >= DOUBLE_CHECK_BLOCKS) {
                // confirmed!
                ctx.matchState = MatchState.MATCHED;
                confirmMatch(ctx, wMinX, wMinZ, wMaxX, wMaxZ, level);
                ConstellationClient.LOGGER.info("[room] MATCHED {} ({}) after {} blocks (double-checked {})",
                    ctx.matchedName, ctx.matchedDir, ctx.checkedBlocks.size(), ctx.doubleCheckCount);
                if (debug) lastDebug = "matched " + ctx.matchedName + " " + ctx.matchedDir
                    + " blocks=" + mappedBlockCount + " shape=" + ctx.shape.key;
                return;
            }
        }

        if (debug) {
            int survivors = ctx.possibleRooms.stream().mapToInt(c -> c.rooms.size()).sum();
            lastDebug = (fromMap ? "MAP" : "flood") + " cells=" + cells.size()
                + " shape=" + ctx.shape.key + " mapped=" + mappedBlockCount
                + " surv=" + survivors + " state=" + ctx.matchState
                + (ctx.matchedName != null ? " cand=" + ctx.matchedName : "")
                + " ids:[" + sample.toString().trim() + "]";
        }

        // if no match after scan and no pending confirmation, just keep trying
        if (ctx.matchState == MatchState.MATCHING && mappedBlockCount >= 180) {
            if (++failTick % 8 == 0) {
                int surv = ctx.possibleRooms.stream().mapToInt(c -> c.rooms.size()).sum();
                ConstellationClient.LOGGER.info("[room] no unique match cell {},{} shape={} mapped={} surv={}",
                    cx, cz, ctx.shape.key, mappedBlockCount, surv);
            }
        }

        // cleanup failed contexts after enough ticks
        if (ctx.matchState == MatchState.FAILED && ctx.checkedBlocks.size() > 200) {
            roomCtxs.remove(pk);
        }
    }

    // ported from Skyblocker (LGPL): initialize (direction, corner) candidates
    private static void initCandidates(MatchCtx ctx, Set<Integer> segX, Set<Integer> segZ) {
        Map<String, int[]> shapeData = DungeonRoomData.roomsForShape(ctx.shape.key);
        if (shapeData.isEmpty()) return;
        List<String> allRooms = new ArrayList<>(shapeData.keySet());

        int firstX = segX.stream().min(Integer::compare).orElse(0);
        int lastX  = segX.stream().max(Integer::compare).orElse(0);
        int firstZ = segZ.stream().min(Integer::compare).orElse(0);
        int lastZ  = segZ.stream().max(Integer::compare).orElse(0);

        // ported from Skyblocker (LGPL): Room.getPossibleDirections + DungeonMapUtils.getPhysicalCornerPos
        RoomTransform.Direction[] dirs = new RoomTransform.Direction[0];
        switch (ctx.shape) {
            case ONE_BY_ONE, TWO_BY_TWO, PUZZLE, TRAP, MINIBOSS ->
                dirs = RoomTransform.Direction.values();
            case ONE_BY_TWO, ONE_BY_THREE, ONE_BY_FOUR -> {
                if (segX.size() > 1 && segZ.size() == 1)
                    dirs = new RoomTransform.Direction[]{RoomTransform.Direction.NW, RoomTransform.Direction.SE};
                else if (segX.size() == 1 && segZ.size() > 1)
                    dirs = new RoomTransform.Direction[]{RoomTransform.Direction.NE, RoomTransform.Direction.SW};
                else dirs = new RoomTransform.Direction[0];
            }
            case L_SHAPE -> {
                if (!cellContains(ctx.cells, firstX, firstZ))
                    dirs = new RoomTransform.Direction[]{RoomTransform.Direction.SW};
                else if (!cellContains(ctx.cells, firstX, lastZ))
                    dirs = new RoomTransform.Direction[]{RoomTransform.Direction.SE};
                else if (!cellContains(ctx.cells, lastX, firstZ))
                    dirs = new RoomTransform.Direction[]{RoomTransform.Direction.NW};
                else if (!cellContains(ctx.cells, lastX, lastZ))
                    dirs = new RoomTransform.Direction[]{RoomTransform.Direction.NE};
                else dirs = new RoomTransform.Direction[0];
            }
        }

        for (RoomTransform.Direction dir : dirs) {
            int cornerX, cornerZ;
            // ported from Skyblocker (LGPL): DungeonMapUtils.getPhysicalCornerPos
            switch (dir) {
                case NW -> { cornerX = firstX;     cornerZ = firstZ; }
                case NE -> { cornerX = lastX + 30; cornerZ = firstZ; }
                case SW -> { cornerX = firstX;     cornerZ = lastZ + 30; }
                case SE -> { cornerX = lastX + 30; cornerZ = lastZ + 30; }
                default -> { cornerX = firstX; cornerZ = firstZ; }
            }
            ctx.possibleRooms.add(new Candidate(dir, cornerX, cornerZ, new ArrayList<>(allRooms)));
        }
    }

    // ported from Skyblocker (LGPL): Shape.determineShape — returns null for an unexpected cell count
    private static Shape shapeFor(int segCount, Set<Integer> segX, Set<Integer> segZ) {
        if (segCount == 1) return Shape.ONE_BY_ONE;
        if (segCount == 2) return Shape.ONE_BY_TWO;
        if (segCount == 3)
            return (segX.size() == 2 && segZ.size() == 2) ? Shape.L_SHAPE : Shape.ONE_BY_THREE;
        if (segCount == 4)
            return (segX.size() == 2 && segZ.size() == 2) ? Shape.TWO_BY_TWO : Shape.ONE_BY_FOUR;
        return null;
    }

    // ported from Skyblocker (LGPL): Room.checkBlock — filter candidate rooms by this block,
    // returns the total surviving room count across all candidate directions
    private static int checkBlockAgainstCandidates(MatchCtx ctx, byte id, int wx, int wy, int wz) {
        int totalLeft = 0;
        for (Candidate cand : ctx.possibleRooms) {
            if (cand.rooms.isEmpty()) continue;
            int encoded = encodeBlock(id, cand.dir, cand.cornerX, cand.cornerZ, wx, wy, wz);
            List<String> keep = new ArrayList<>();
            for (String room : cand.rooms) {
                int[] skeleton = DungeonRoomData.roomsForShape(ctx.shape.key).get(room);
                if (skeleton != null && Arrays.binarySearch(skeleton, encoded) >= 0) {
                    keep.add(room);
                }
            }
            cand.rooms.clear();
            cand.rooms.addAll(keep);
            totalLeft += cand.rooms.size();
        }
        return totalLeft;
    }

    // ported from Skyblocker (LGPL): promote the unique surviving candidate to the double-check phase
    private static void promoteToDoubleCheck(MatchCtx ctx) {
        for (Candidate cand : ctx.possibleRooms) {
            if (cand.rooms.size() == 1) {
                ctx.matchedName = cand.rooms.getFirst();
                ctx.matchedDir = cand.dir;
                ctx.matchedAnchorX = cand.cornerX;
                ctx.matchedAnchorZ = cand.cornerZ;
                break;
            }
        }
    }

    // ported from Skyblocker (LGPL): encode a world block into room-relative int for a candidate direction
    private static int encodeBlock(byte id, RoomTransform.Direction dir,
                                   int cornerX, int cornerZ, int wx, int wy, int wz) {
        int rx, rz;
        // ported from Skyblocker (LGPL): DungeonMapUtils.actualToRelative (BlockPos variant)
        switch (dir) {
            case NW -> { rx = wx - cornerX; rz = wz - cornerZ; }
            case NE -> { rx = wz - cornerZ; rz = -wx + cornerX; }
            case SW -> { rx = -wz + cornerZ; rz = wx - cornerX; }
            case SE -> { rx = -wx + cornerX; rz = -wz + cornerZ; }
            default -> { rx = wx - cornerX; rz = wz - cornerZ; }
        }
        return DungeonRoomData.posIdToInt(rx, wy, rz, id);
    }

    // ported from Skyblocker (LGPL): Room.notInDoorway
    // returns true if the position IS in a doorway (should be excluded)
    private static boolean notInDoorway(int x, int y, int z) {
        if (y < 66 || y > 73) return false;
        int lx = Math.floorMod(x - 8, 32);
        int lz = Math.floorMod(z - 8, 32);
        return (lx >= 13 && lx <= 17 && (lz <= 2 || lz >= 28))
            || (lz >= 13 && lz <= 17 && (lx <= 2 || lx >= 28));
    }

    private static void confirmMatch(MatchCtx ctx, int wMinX, int wMinZ, int wMaxX, int wMaxZ, Level level) {
        currentRoom = ctx.matchedName;
        currentDir = ctx.matchedDir;
        anchorX = ctx.matchedAnchorX;
        anchorZ = ctx.matchedAnchorZ;
        // footprint: the room spans from wMinX to wMaxX+30 (each segment: corner to corner+30)
        fpMinX = wMinX; fpMinZ = wMinZ;
        fpMaxX = wMaxX + 30; fpMaxZ = wMaxZ + 30;
        fpValid = true;

        Solved solved = new Solved(currentRoom, currentDir, anchorX, anchorZ, fpMinX, fpMinZ, fpMaxX, fpMaxZ);
        for (long cell : ctx.cells) solvedCells.put(cell, solved);
        lastVerifiedTick = level.getGameTime();
        ConstellationClient.dungeon().enterRoom(currentRoom, currentDir, anchorX, anchorZ);
    }

    // ======================== HELPERS (mostly preserved) ========================

    private static byte blockId(Level level, int x, int y, int z) {
        BlockState state = level.getBlockState(new BlockPos(x, y, z));
        if (state.isAir()) return 0;
        return DungeonRoomData.numericId(BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString());
    }

    private static long blockKey(int x, int y, int z) {
        return ((long) x << 42) ^ ((long) y << 21) ^ z;
    }

    private static boolean cellContains(Set<Long> cells, int wx, int wz) {
        return cells.contains(RoomGrid.cellKey(RoomGrid.cornerX((double) wx), RoomGrid.cornerZ((double) wz)));
    }

    // ---- floor vote (preserved) ----
    private static int floorVote(Level level, int cx, int cz) {
        List<Integer> samples = new ArrayList<>();
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        for (int lx = 6; lx <= 24; lx += 6)
            for (int lz = 6; lz <= 24; lz += 6) {
                int x = cx + lx, z = cz + lz;
                int best = Integer.MIN_VALUE, bestDistance = Integer.MAX_VALUE;
                for (int y = FLOOR_REFERENCE_Y - 16; y <= FLOOR_REFERENCE_Y + 16; y++) {
                    m.set(x, y, z);
                    if (level.getBlockState(m).isAir()) continue;
                    if (blockId(level, x, y, z) == 0) continue;
                    m.set(x, y + 1, z);
                    if (level.getBlockState(m).isAir() && Math.abs(y - FLOOR_REFERENCE_Y) < bestDistance) {
                        best = y; bestDistance = Math.abs(y - FLOOR_REFERENCE_Y);
                    }
                }
                if (best != Integer.MIN_VALUE) samples.add(best);
            }
        if (samples.isEmpty()) return Integer.MIN_VALUE;
        Collections.sort(samples);
        int middle = samples.size() / 2;
        return samples.size() % 2 == 1 ? samples.get(middle)
            : (samples.get(middle - 1) + samples.get(middle)) / 2;
    }

    // ---- flood fill (preserved) ----
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
        int present = 0, samples = 0, closedRun = 0, longestClosedRun = 0;
        if (dx != 0) {
            int seamX = dx > 0 ? cxA + 31 : cxA - 1;
            for (int lz = 2; lz <= 28; lz += 2) {
                samples++;
                if (openColumn(level, seamX, czA + lz, floorY)) { present++; closedRun = 0; }
                else longestClosedRun = Math.max(longestClosedRun, ++closedRun);
            }
        } else {
            int seamZ = dz > 0 ? czA + 31 : czA - 1;
            for (int lx = 2; lx <= 28; lx += 2) {
                samples++;
                if (openColumn(level, cxA + lx, seamZ, floorY)) { present++; closedRun = 0; }
                else longestClosedRun = Math.max(longestClosedRun, ++closedRun);
            }
        }
        return longestClosedRun < 3 && present * 5 >= samples * 4;
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

    private static void verifyNoMatch(int cx, int cz, String context) {
        long cell = RoomGrid.cellKey(cx, cz);
        if (ConstellationClient.verify() && verifiedNoMatchCell != cell) {
            verifiedNoMatchCell = cell;
            ConstellationClient.verifyNoMatch(context + " cell " + cx + "," + cz);
        }
    }

    private static void leaveCurrent() {
        if (!currentRoom.isEmpty()) ConstellationClient.dungeon().leaveRoom();
        currentRoom = "";
        fpValid = false;
        lastVerifiedTick = Long.MIN_VALUE;
    }
}
