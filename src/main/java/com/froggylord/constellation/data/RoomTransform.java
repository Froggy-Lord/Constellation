package com.froggylord.constellation.data;

/**
 * Room-relative ↔ world coordinate transforms for the four catacombs room rotations.
 * The mapping is the standard one used across the SkyBlock dungeon-mod ecosystem
 * (derived from DungeonMapUtils): NW/NE/SW/SE name the room's anchor corner, and each
 * rotation has its own world↔local pair.
 */
public class RoomTransform {

    public enum Direction { NW, NE, SW, SE }

    /** local (room-relative) → world, anchored at corner (cx,cz) */
    public static long[] relativeToActual(Direction dir, int cx, int cz, int lx, int ly, int lz) {
        return switch (dir) {
            case NW -> new long[]{ lx + cx,  ly,  lz + cz };
            case NE -> new long[]{ -lz + cx, ly,  lx + cz };
            case SW -> new long[]{ lz + cx,  ly, -lx + cz };
            case SE -> new long[]{ -lx + cx, ly, -lz + cz };
        };
    }

    /** world → local (room-relative), anchored at corner (cx,cz) */
    public static long[] actualToRelative(Direction dir, int cx, int cz, int x, int y, int z) {
        return switch (dir) {
            case NW -> new long[]{ x - cx,  y,  z - cz };
            case NE -> new long[]{ z - cz,  y, -x + cx };
            case SW -> new long[]{ -z + cz, y,  x - cx };
            case SE -> new long[]{ -x + cx, y, -z + cz };
        };
    }

    // ---- on-disk encoding helpers (mirror DungeonData) ----
    public static int posId(int relX, int relY, int relZ, int blockId) {
        return (relX << 24) | ((relY & 0xFF) << 16) | (relZ << 8) | (blockId & 0xFF);
    }
}
