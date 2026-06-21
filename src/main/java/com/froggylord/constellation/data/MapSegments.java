package com.froggylord.constellation.data;

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

        // calibrate room pixel size from the entrance green block
        int roomSize = entranceRoomSize(map, playerMap);
        if (roomSize <= 0) { lastDebug = "no entrance found (marker " + playerMap[0] + "," + playerMap[1] + ")"; return Set.of(); }
        int step = roomSize + 4; // room size + gap between rooms on the map

        // the player's map cell (top-left pixel of the room they're standing in)
        int[] playerCell = playerMapCell(map, playerMap, step);
        byte color = colorAt(map, playerCell[0] + 1, playerCell[1] + 1);
        if (color <= 0) { lastDebug = "no colour at player cell (roomSize=" + roomSize + ")"; return Set.of(); }

        // flood connected same-colour map cells
        List<int[]> mapCells = floodMapCells(map, playerCell, step, color);

        // anchor: the player's map cell ↔ the player's physical cell
        int physPlayerX = RoomGrid.cornerX(mc.player.position());
        int physPlayerZ = RoomGrid.cornerZ(mc.player.position());

        Set<Long> physCells = new LinkedHashSet<>();
        for (int[] mc2 : mapCells) {
            int dxCells = (mc2[0] - playerCell[0]) / step;
            int dzCells = (mc2[1] - playerCell[1]) / step;
            int px = physPlayerX + dxCells * 32;
            int pz = physPlayerZ + dzCells * 32;
            physCells.add(RoomGrid.cellKey(px, pz));
        }
        lastDebug = "ok roomSize=" + roomSize + " mapCells=" + mapCells.size() + " physCells=" + physCells.size();
        return physCells;
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

    /** Scan outward from the player to find the entrance green block, measure its width. */
    private static int entranceRoomSize(MapItemSavedData map, int[] start) {
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
                if (w > 5) return w;
            }
            for (int[] d : new int[][]{{-10,0},{10,0},{0,-10},{0,10}}) {
                int nx = p[0] + d[0], nz = p[1] + d[1];
                if (nx < 0 || nz < 0 || nx >= 128 || nz >= 128) continue;
                if (seen.add(key(nx, nz))) q.add(new int[]{nx, nz});
            }
        }
        return 0;
    }

    /** Top-left pixel of the room cell the player is standing in. */
    private static int[] playerMapCell(MapItemSavedData map, int[] playerMap, int step) {
        // align to the room grid: shift by 2 (split borders), snap to step, shift back
        int ox = Math.floorMod(playerMap[0], step);
        int oz = Math.floorMod(playerMap[1], step);
        return new int[]{ playerMap[0] - ox, playerMap[1] - oz };
    }

    /** BFS connected same-colour room cells on the map. */
    private static List<int[]> floodMapCells(MapItemSavedData map, int[] start, int step, byte color) {
        List<int[]> cells = new ArrayList<>();
        Deque<int[]> q = new ArrayDeque<>();
        Set<Long> seen = new HashSet<>();
        q.add(start); seen.add(key(start[0], start[1]));
        while (!q.isEmpty() && cells.size() < 12) {
            int[] c = q.poll();
            cells.add(c);
            for (int[] d : new int[][]{{-step,0},{step,0},{0,-step},{0,step}}) {
                int nx = c[0] + d[0], nz = c[1] + d[1];
                long k = key(nx, nz);
                if (seen.contains(k)) continue;
                // check the centre of the neighbour cell is the same room colour
                if (colorAt(map, nx + step / 2, nz + step / 2) == color
                    || colorAt(map, nx + 1, nz + 1) == color) {
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
