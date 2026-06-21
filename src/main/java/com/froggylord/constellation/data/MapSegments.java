package com.froggylord.constellation.data;

import com.froggylord.constellation.ConstellationClient;
import com.froggylord.constellation.mixin.MapDataAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.saveddata.maps.MapDecoration;
import net.minecraft.world.level.saveddata.maps.MapDecorationTypes;
import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;

import java.util.*;

/**
 * Determines a room's footprint (its 32-grid cells) from the dungeon map item instead of
 * world-block flooding. The map shows room shapes deterministically via pixel colours, so
 * this works for multi-level rooms (sewer/stairs) where world-flood fails — the map doesn't
 * care what Y the room's cells sit at.
 *
 * Math is the standard SkyBlock dungeon-mod approach (Skyblocker's DungeonMapUtils): find the
 * entrance green block to calibrate room pixel size, read the player's map marker, flood
 * connected same-colour map cells, then convert map cells → physical cells anchored on the
 * player's own known position.
 */
public class MapSegments {

    private static final int ENTRANCE_COLOR = 30; // MapColor.PLANT / HIGH

    public static String lastDebug = "no map scan";

    // cached entrance anchor (calibrated once from Mort's armor stand + the map's green block).
    // gives a fixed map↔world transform that doesn't depend on the player marker each scan.
    private static boolean calibrated = false;
    private static int mapEntranceX, mapEntranceZ;        // map pixel of entrance top-left
    private static int physEntranceX, physEntranceZ;      // world NW corner of entrance room
    private static int mapStep = 0;                        // pixels per room cell (size + gap)

    /** Reset on dungeon enter/leave so a new run re-calibrates. */
    public static void reset() { calibrated = false; }

    /** Map pixel (0-128) → approximate world x,z, using the cached Mort anchor. Null if not ready. */
    public static int[] worldXZFromMapPixel(int mpx, int mpz) {
        if (!calibrated || mapStep <= 0) return null;
        double cellsX = (mpx - mapEntranceX) / (double) mapStep;
        double cellsZ = (mpz - mapEntranceZ) / (double) mapStep;
        int x = (int) Math.round(physEntranceX + cellsX * 32 + 15); // +15 ≈ room centre
        int z = (int) Math.round(physEntranceZ + cellsZ * 32 + 15);
        return new int[]{ x, z };
    }

    /** Returns the set of physical NW-corner cell keys for the room the player is in, or empty. */
    public static Set<Long> footprint() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) { lastDebug = "no player"; return Set.of(); }

        MapItemSavedData map = findMap(mc);
        if (map == null || map.colors.length < 128 * 128) { lastDebug = "no map item"; return Set.of(); }

        int[] playerMap = mapPlayerPos(map);
        if (playerMap == null) {
            int decoCount = decoCount(map);
            lastDebug = "no player marker (" + decoCount + " decos)";
            return Set.of();
        }

        // calibrate from the entrance green block: its top-left position + room pixel size
        int[] entrance = entranceInfo(map, playerMap);
        if (entrance == null) { lastDebug = "no entrance found (marker " + playerMap[0] + "," + playerMap[1] + ")"; return Set.of(); }
        int roomSize = entrance[2];
        int step = roomSize + 4; // room size + gap between rooms on the map

        // the player's map cell (top-left pixel), aligned to the grid the entrance defines
        int offX = Math.floorMod(entrance[0], step);
        int offZ = Math.floorMod(entrance[1], step);
        int alignedX = (playerMap[0] + 2) - offX;
        int alignedZ = (playerMap[1] + 2) - offZ;
        int[] playerCell = {
            alignedX - Math.floorMod(alignedX, step) + offX,
            alignedZ - Math.floorMod(alignedZ, step) + offZ
        };
        // sample the cell centre, not the corner
        byte color = colorAt(map, playerCell[0] + roomSize / 2, playerCell[1] + roomSize / 2);
        if (color <= 0) { lastDebug = "no colour @ cell " + playerCell[0] + "," + playerCell[1] + " (size=" + roomSize + " off=" + offX + "," + offZ + ")"; return Set.of(); }

        // flood connected same-colour map cells
        List<int[]> mapCells = floodMapCells(map, playerCell, step, color, roomSize);

        // anchor the map↔world transform. prefer Mort (fixed entrance, calibrated once);
        // otherwise anchor on the player's own map cell ↔ physical cell this scan.
        int anchorMapX, anchorMapZ, anchorPhysX, anchorPhysZ;
        String anchorSrc;
        mapStep = step;
        tryCalibrateMort(mc, entrance[0], entrance[1]);
        if (calibrated) {
            anchorMapX = mapEntranceX; anchorMapZ = mapEntranceZ;
            anchorPhysX = physEntranceX; anchorPhysZ = physEntranceZ;
            anchorSrc = "mort";
        } else {
            anchorMapX = playerCell[0]; anchorMapZ = playerCell[1];
            anchorPhysX = RoomGrid.cornerX(mc.player.position());
            anchorPhysZ = RoomGrid.cornerZ(mc.player.position());
            anchorSrc = "player";
        }

        Set<Long> physCells = new LinkedHashSet<>();
        for (int[] mcell : mapCells) {
            int dxCells = Math.round((mcell[0] - anchorMapX) / (float) step);
            int dzCells = Math.round((mcell[1] - anchorMapZ) / (float) step);
            int px = anchorPhysX + dxCells * 32;
            int pz = anchorPhysZ + dzCells * 32;
            physCells.add(RoomGrid.cellKey(px, pz));
        }
        lastDebug = "ok " + anchorSrc + " roomSize=" + roomSize + " mapCells=" + mapCells.size() + " physCells=" + physCells.size();
        return physCells;
    }

    /** Find Mort's armor stand once and cache the entrance map↔world anchor. */
    private static void tryCalibrateMort(Minecraft mc, int mapEntX, int mapEntZ) {
        if (calibrated) return;
        var area = mc.player.getBoundingBox().inflate(120);
        for (var stand : mc.level.getEntitiesOfClass(
                net.minecraft.world.entity.decoration.ArmorStand.class, area)) {
            var name = stand.getCustomName();
            if (name == null) continue;
            if (!name.getString().contains("Mort")) continue;
            // Mort stands in the entrance room — snap his pos to the room grid
            physEntranceX = RoomGrid.cornerX(stand.position());
            physEntranceZ = RoomGrid.cornerZ(stand.position());
            mapEntranceX = mapEntX;
            mapEntranceZ = mapEntZ;
            calibrated = true;
            ConstellationClient.LOGGER.info("[room] calibrated entrance from Mort: map {},{} -> world {},{}",
                mapEntranceX, mapEntranceZ, physEntranceX, physEntranceZ);
            return;
        }
    }

    private static int decoCount(MapItemSavedData map) {
        var decos = ((MapDataAccessor) (Object) map).constellation$decorations();
        return decos == null ? -1 : decos.size();
    }

    private static byte colorAt(MapItemSavedData map, int x, int z) {
        if (x < 0 || z < 0 || x >= 128 || z >= 128) return -1;
        return map.colors[x + (z << 7)];
    }

    /** Player's pixel position on the map, from the FRAME/PLAYER decoration. */
    private static int[] mapPlayerPos(MapItemSavedData map) {
        var decos = ((MapDataAccessor) (Object) map).constellation$decorations();
        if (decos == null) return null;
        for (MapDecoration d : decos.values()) {
            var type = d.type();
            if (type.equals(MapDecorationTypes.FRAME) || type.equals(MapDecorationTypes.PLAYER)) {
                return new int[]{ (d.x() >> 1) + 64, (d.y() >> 1) + 64 };
            }
        }
        return null;
    }

    /** Scan outward from the player to find the entrance green block: [topLeftX, topLeftZ, size]. */
    private static int[] entranceInfo(MapItemSavedData map, int[] start) {
        Deque<int[]> q = new ArrayDeque<>();
        Set<Long> seen = new HashSet<>();
        q.add(start); seen.add(key(start[0], start[1]));
        while (!q.isEmpty()) {
            int[] p = q.poll();
            if (colorAt(map, p[0], p[1]) == ENTRANCE_COLOR) {
                // walk left+up to the entrance's top-left, then measure its width
                int x = p[0], z = p[1];
                while (colorAt(map, x - 1, z) == ENTRANCE_COLOR) x--;
                while (colorAt(map, x, z - 1) == ENTRANCE_COLOR) z--;
                int w = 0;
                while (colorAt(map, x + w, z) == ENTRANCE_COLOR) w++;
                if (w > 5) return new int[]{ x, z, w };
            }
            for (int[] d : new int[][]{{-10,0},{10,0},{0,-10},{0,10}}) {
                int nx = p[0] + d[0], nz = p[1] + d[1];
                if (nx < 0 || nz < 0 || nx >= 128 || nz >= 128) continue;
                if (seen.add(key(nx, nz))) q.add(new int[]{nx, nz});
            }
        }
        return null;
    }

    /** BFS connected same-colour room cells on the map (sampling each cell's centre). */
    private static List<int[]> floodMapCells(MapItemSavedData map, int[] start, int step, byte color, int roomSize) {
        List<int[]> cells = new ArrayList<>();
        Deque<int[]> q = new ArrayDeque<>();
        Set<Long> seen = new HashSet<>();
        q.add(start); seen.add(key(start[0], start[1]));
        int half = roomSize / 2;
        while (!q.isEmpty() && cells.size() < 12) {
            int[] c = q.poll();
            cells.add(c);
            for (int[] d : new int[][]{{-step,0},{step,0},{0,-step},{0,step}}) {
                int nx = c[0] + d[0], nz = c[1] + d[1];
                long k = key(nx, nz);
                if (seen.contains(k)) continue;
                if (colorAt(map, nx + half, nz + half) == color) {
                    seen.add(k);
                    q.add(new int[]{nx, nz});
                }
            }
        }
        return cells;
    }

    private static long key(int x, int z) { return ((long) x << 32) | (z & 0xFFFF_FFFFL); }

    private static MapItemSavedData findMap(Minecraft mc) {
        Inventory inv = mc.player.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack stack = inv.getItem(i);
            if (stack.isEmpty()) continue;
            MapId id = stack.get(DataComponents.MAP_ID);
            if (id == null) continue;
            MapItemSavedData data = mc.level.getMapData(id);
            if (data != null) return data;
        }
        return null;
    }
}
