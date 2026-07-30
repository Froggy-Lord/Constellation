package com.froggylord.constellation.data;

// Rotation convention mapped from Secret Routes Mod (GPL-3.0): RoomRotationUtils.java
public final class RoomTransform {

    public enum Direction { NW, NE, SW, SE }
    public enum SecretRoutesDirection { N, S, E, W }

    private RoomTransform() {}

    public static Direction constellationDirFromSecretRoutes(SecretRoutesDirection dir) {
        return switch (dir) {
            case S -> Direction.NW;
            case W -> Direction.NE;
            case E -> Direction.SW;
            case N -> Direction.SE;
        };
    }

    public static long[] relativeToActual(Direction dir, int cx, int cz, int lx, int ly, int lz) {
        return switch (dir) {
            case NW -> new long[]{ lx + cx,  ly,  lz + cz };
            case NE -> new long[]{ -lz + cx, ly,  lx + cz };
            case SW -> new long[]{ lz + cx,  ly, -lx + cz };
            case SE -> new long[]{ -lx + cx, ly, -lz + cz };
        };
    }

    public static double[] relativeToActual(Direction dir, int cx, int cz, double lx, double ly, double lz) {
        return switch (dir) {
            case NW -> new double[]{lx + cx, ly, lz + cz};
            case NE -> new double[]{-lz + cx, ly, lx + cz};
            case SW -> new double[]{lz + cx, ly, -lx + cz};
            case SE -> new double[]{-lx + cx, ly, -lz + cz};
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

    public static double[] actualToRelative(Direction dir, int cx, int cz, double x, double y, double z) {
        return switch (dir) {
            case NW -> new double[]{x - cx, y, z - cz};
            case NE -> new double[]{z - cz, y, -x + cx};
            case SW -> new double[]{-z + cz, y, x - cx};
            case SE -> new double[]{-x + cx, y, -z + cz};
        };
    }

    // ---- on-disk encoding helpers ...
    public static int posId(int relX, int relY, int relZ, int blockId) {
        if (relY < 0 || relY > 255) throw new IllegalArgumentException("relY outside fingerprint range: " + relY);
        return (relX << 24) | (relY << 16) | (relZ << 8) | (blockId & 0xFF);
    }
}
