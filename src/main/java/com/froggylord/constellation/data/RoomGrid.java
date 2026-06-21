package com.froggylord.constellation.data;

import net.minecraft.world.phys.Vec3;

/**
 * Catacombs room grid math. Rooms sit on a 32-block grid.
 * Corner macros and cell-size constants.
 */
public class RoomGrid {

    public static final int CELL = 32;

    /** The grid corner X for a given world X */
    public static int cornerX(double worldX) {
        return cornerX((int) Math.floor(worldX));
    }

    public static int cornerZ(double worldZ) {
        return cornerZ((int) Math.floor(worldZ));
    }

    public static int cornerX(int worldX) {
        int mod = Math.floorMod(worldX, CELL);
        return worldX - mod;
    }

    public static int cornerZ(int worldZ) {
        int mod = Math.floorMod(worldZ, CELL);
        return worldZ - mod;
    }

    /** local x within the cell (0..31) */
    public static int localX(double worldX) { return Math.floorMod((int) Math.floor(worldX), CELL); }
    public static int localZ(double worldZ) { return Math.floorMod((int) Math.floor(worldZ), CELL); }

    /** the cell key (packed corner coords) for fast lookups */
    public static long cellKey(Vec3 pos) {
        return cellKey(cornerX(pos.x), cornerZ(pos.z));
    }

    public static long cellKey(int cx, int cz) {
        return ((long) cx << 32) | (cz & 0xFFFF_FFFFL);
    }
}
