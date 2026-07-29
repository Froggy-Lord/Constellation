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

public class MapSegments {

    private static final int ENTRANCE_COLOR = 30; 

    public static String lastDebug = "no map scan";
    private static RoomType currentType = RoomType.UNKNOWN;
    private static String currentMapToken = "";

    
    
    private static boolean calibrated = false;
    private static int mapEntranceX, mapEntranceZ;        
    private static int physEntranceX, physEntranceZ;      
    private static int mapStep = 0;                        // pixels per room cell (size + gap)

    public static void reset() { calibrated = false; currentType = RoomType.UNKNOWN; currentMapToken = ""; }
    public static RoomType currentType() { return currentType; }
    public static String currentMapToken() { return currentMapToken; }

    public static int[] worldXZFromMapPixel(int mpx, int mpz) {
        if (!calibrated || mapStep <= 0) return null;
        double cellsX = (mpx - mapEntranceX) / (double) mapStep;
        double cellsZ = (mpz - mapEntranceZ) / (double) mapStep;
        int x = (int) Math.round(physEntranceX + cellsX * 32 + 15); 
        int z = (int) Math.round(physEntranceZ + cellsZ * 32 + 15);
        return new int[]{ x, z };
    }

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

        
        int[] entrance = entranceInfo(map, playerMap);
        if (entrance == null) { lastDebug = "no entrance found (marker " + playerMap[0] + "," + playerMap[1] + ")"; return Set.of(); }
        int roomSize = entrance[2];
        int step = roomSize + 4; 

        
        int offX = Math.floorMod(entrance[0], step);
        int offZ = Math.floorMod(entrance[1], step);
        int alignedX = (playerMap[0] + 2) - offX;
        int alignedZ = (playerMap[1] + 2) - offZ;
        int[] playerCell = {
            alignedX - Math.floorMod(alignedX, step) + offX,
            alignedZ - Math.floorMod(alignedZ, step) + offZ
        };
        
        byte color = colorAt(map, playerCell[0] + roomSize / 2, playerCell[1] + roomSize / 2);
        currentType = roomType(color & 0xFF);
        if (color <= 0) { lastDebug = "no colour @ cell " + playerCell[0] + "," + playerCell[1] + " (size=" + roomSize + " off=" + offX + "," + offZ + ")"; return Set.of(); }

        
        List<int[]> mapCells = floodMapCells(map, playerCell, step, color, roomSize);

        
        
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

    /**
     * Every revealed room cell on the dungeon map, as physical RoomGrid cell keys.
     * Mirrors FunnyMap scanning all revealed rooms so callers can pre-identify rooms
     * ahead of the player. Empty until the map + entrance calibration are available.
     */
    public static Set<Long> revealedCells() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return Set.of();

        MapItemSavedData map = findMap(mc);
        if (map == null || map.colors.length < 128 * 128) return Set.of();

        int[] playerMap = mapPlayerPos(map);
        if (playerMap == null) return Set.of();

        int[] entrance = entranceInfo(map, playerMap);
        if (entrance == null) return Set.of();
        int roomSize = entrance[2];
        int step = roomSize + 4;
        int offX = Math.floorMod(entrance[0], step);
        int offZ = Math.floorMod(entrance[1], step);

        // anchor for map->world conversion (same lineage as footprint())
        int anchorMapX, anchorMapZ, anchorPhysX, anchorPhysZ;
        mapStep = step;
        tryCalibrateMort(mc, entrance[0], entrance[1]);
        if (calibrated) {
            anchorMapX = mapEntranceX; anchorMapZ = mapEntranceZ;
            anchorPhysX = physEntranceX; anchorPhysZ = physEntranceZ;
        } else {
            int alignedX = (playerMap[0] + 2) - offX;
            int alignedZ = (playerMap[1] + 2) - offZ;
            anchorMapX = alignedX - Math.floorMod(alignedX, step) + offX;
            anchorMapZ = alignedZ - Math.floorMod(alignedZ, step) + offZ;
            anchorPhysX = RoomGrid.cornerX(mc.player.position());
            anchorPhysZ = RoomGrid.cornerZ(mc.player.position());
        }

        int half = roomSize / 2;
        Set<Long> physCells = new LinkedHashSet<>();
        for (int gx = offX; gx + roomSize <= 128; gx += step) {
            for (int gz = offZ; gz + roomSize <= 128; gz += step) {
                byte color = colorAt(map, gx + half, gz + half);
                if (color <= 0) continue;   // unrevealed / gap between rooms
                int dxCells = Math.round((gx - anchorMapX) / (float) step);
                int dzCells = Math.round((gz - anchorMapZ) / (float) step);
                int px = anchorPhysX + dxCells * 32;
                int pz = anchorPhysZ + dzCells * 32;
                physCells.add(RoomGrid.cellKey(px, pz));
            }
        }
        return physCells;
    }

    private static void tryCalibrateMort(Minecraft mc, int mapEntX, int mapEntZ) {        if (calibrated) return;
        var area = mc.player.getBoundingBox().inflate(120);
        for (var stand : mc.level.getEntitiesOfClass(
                net.minecraft.world.entity.decoration.ArmorStand.class, area)) {
            var name = stand.getCustomName();
            if (name == null) continue;
            if (!name.getString().contains("Mort")) continue;
            // mort stands in the entrance ro...
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

    private static int[] entranceInfo(MapItemSavedData map, int[] start) {
        Deque<int[]> q = new ArrayDeque<>();
        Set<Long> seen = new HashSet<>();
        q.add(start); seen.add(key(start[0], start[1]));
        while (!q.isEmpty()) {
            int[] p = q.poll();
            if (colorAt(map, p[0], p[1]) == ENTRANCE_COLOR) {
                
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

    private static List<int[]> floodMapCells(MapItemSavedData map, int[] start, int step, byte color, int roomSize) {
        List<int[]> cells = new ArrayList<>();
        Deque<int[]> q = new ArrayDeque<>();
        Set<Long> seen = new HashSet<>();
        q.add(start); seen.add(key(start[0], start[1]));
        int half = roomSize / 2;
        int border = roomSize + 2; 
        while (!q.isEmpty() && cells.size() < 12) {
            int[] c = q.poll();
            cells.add(c);
            
            tryFlood(map, c, new int[]{c[0] + step, c[1]},
                new int[]{c[0] + border, c[1] + half}, color, half, q, seen);
            
            tryFlood(map, c, new int[]{c[0] - step, c[1]},
                new int[]{c[0] - (step - border), c[1] + half}, color, half, q, seen);
            
            tryFlood(map, c, new int[]{c[0], c[1] + step},
                new int[]{c[0] + half, c[1] + border}, color, half, q, seen);
            
            tryFlood(map, c, new int[]{c[0], c[1] - step},
                new int[]{c[0] + half, c[1] - (step - border)}, color, half, q, seen);
        }
        return cells;
    }

    private static void tryFlood(MapItemSavedData map, int[] from, int[] to,
                                  int[] seam, byte color, int half, Deque<int[]> q, Set<Long> seen) {
        long k = key(to[0], to[1]);
        if (seen.contains(k)) return;
        if (colorAt(map, seam[0], seam[1]) == color
            && colorAt(map, to[0] + half, to[1] + half) == color) {
            seen.add(k);
            q.add(to);
        }
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
            if (data != null) {
                currentMapToken = id.toString();
                return data;
            }
        }
        return null;
    }

    private static RoomType roomType(int color) {
        return switch (color) {
            case 66 -> RoomType.PUZZLE;
            case 62 -> RoomType.TRAP;
            case 18 -> RoomType.BLOOD;
            case 82 -> RoomType.FAIRY;
            case 74 -> RoomType.MINIBOSS;
            case 30 -> RoomType.NORMAL;
            case 63 -> RoomType.NORMAL;
            default -> RoomType.UNKNOWN;
        };
    }
}
