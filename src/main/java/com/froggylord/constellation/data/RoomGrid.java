package com.froggylord.constellation.data;

import net.minecraft.world.phys.Vec3;

/**
 * Catacombs room grid math. Hypixel lays rooms on a 32-block grid, but the grid is
 * offset: each room's NW corner sits at (x,z) ≡ 24 (mod 32), because SkyBlock 0.12.3
 * shifted dungeons by 8 blocks. So the corner is computed from a +8.5 shifted position,
 * snapped to the 32-grid, then shifted back by 8.
 */
public class RoomGrid {

    public static final int ROOM = 31;   // a room is 31x31 blocks
    public static final int GRID = 32;   // on a 32 grid (1-block wall between)

    /** NW-corner X of the room a world X sits in */
    public static int cornerX(double x) {
        int p = (int) (x + 8.5);
        return p - Math.floorMod(p, GRID) - 8;
    }

    public static int cornerZ(double z) {
        int p = (int) (z + 8.5);
        return p - Math.floorMod(p, GRID) - 8;
    }

    public static int cornerX(Vec3 pos) { return cornerX(pos.x); }
    public static int cornerZ(Vec3 pos) { return cornerZ(pos.z); }

    /** pack a cell's corner coords into a long key */
    public static long cellKey(Vec3 pos) {
        return cellKey(cornerX(pos.x), cornerZ(pos.z));
    }

    public static long cellKey(int cx, int cz) {
        return ((long) cx << 32) | (cz & 0xFFFF_FFFFL);
    }
}
