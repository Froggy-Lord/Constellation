package com.froggylord.constellation.data;

public class RoomTransform {

    public enum Direction { NW, NE, SW, SE }

    public static long[] relativeToActual(Direction dir, int cx, int cz, int lx, int ly, int lz) {
        return switch (dir) {
            case NW -> new long[]{ lx + cx,  ly,  lz + cz };
            case NE -> new long[]{ -lz + cx, ly,  lx + cz };
            case SW -> new long[]{ lz + cx,  ly, -lx + cz };
            case SE -> new long[]{ -lx + cx, ly, -lz + cz };
        };
    }

    public static long[] actualToRelative(Direction dir, int cx, int cz, int x, int y, int z) {
        return switch (dir) {
            case NW -> new long[]{ x - cx,  y,  z - cz };
            case NE -> new long[]{ z - cz,  y, -x + cx };
            case SW -> new long[]{ -z + cz, y,  x - cx };
            case SE -> new long[]{ -x + cx, y, -z + cz };
        };
    }

    // ---- on-disk encoding helpers ...
    public static int posId(int relX, int relY, int relZ, int blockId) {
        return (relX << 24) | ((relY & 0xFF) << 16) | (relZ << 8) | (blockId & 0xFF);
    }
}
