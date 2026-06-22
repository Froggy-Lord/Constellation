package com.froggylord.constellation.data;

import net.minecraft.world.phys.Vec3;

public class RoomGrid {

    public static final int ROOM = 31;   // a room is 31x31 blocks
    public static final int GRID = 32;   

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

    public static long cellKey(Vec3 pos) {
        return cellKey(cornerX(pos.x), cornerZ(pos.z));
    }

    public static long cellKey(int cx, int cz) {
        return ((long) cx << 32) | (cz & 0xFFFF_FFFFL);
    }
}
