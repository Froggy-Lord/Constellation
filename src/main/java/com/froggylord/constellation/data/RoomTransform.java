package com.froggylord.constellation.data;

import net.minecraft.world.phys.Vec3;

/**
 * Room-relative ↔ world coordinate transforms, supporting 4 rotations.
 * The dungeon rooms data uses a local coordinate system where x/z are 0..30
 * (or scaled for larger rooms) and y is absolute. The transform maps between
 * that local space and world space given the room's NW corner and rotation.
 */
public class RoomTransform {

    public enum Direction {
        NW, NE, SE, SW;

        public Direction next() { return values()[(ordinal() + 1) % 4]; }
        public Direction prev() { return values()[(ordinal() + 3) % 4]; }

        /** Map a local x/z from room-local into world-space for a given corner */
        public Vec3 localToWorld(int cornerX, int cornerZ, int localX, int localY, int localZ) {
            return switch (this) {
                case NW -> new Vec3(cornerX + localX, localY, cornerZ + localZ);
                case NE -> new Vec3(cornerX + 31 - localZ, localY, cornerZ + localX);
                case SE -> new Vec3(cornerX + 31 - localX, localY, cornerZ + 31 - localZ);
                case SW -> new Vec3(cornerX + localZ, localY, cornerZ + 31 - localX);
            };
        }

        /** Map a world position into room-local (0..31, 0..31) for a given corner */
        public Vec3 worldToLocal(int cornerX, int cornerZ, int worldX, int worldY, int worldZ) {
            int lx = worldX - cornerX;
            int lz = worldZ - cornerZ;
            return switch (this) {
                case NW -> new Vec3(lx, worldY, lz);
                case NE -> new Vec3(lz, worldY, 31 - lx);
                case SE -> new Vec3(31 - lx, worldY, 31 - lz);
                case SW -> new Vec3(31 - lz, worldY, lx);
            };
        }

        /** Transform local position for a given footprint width (for rooms larger than 1x1) */
        public Vec3 footprintLocalToWorld(int cornerX, int cornerZ, int widthX, int widthZ,
                                          int localX, int localY, int localZ) {
            int maxX = widthX * 32 - 1;
            int maxZ = widthZ * 32 - 1;
            return switch (this) {
                case NW -> new Vec3(cornerX + localX, localY, cornerZ + localZ);
                case NE -> new Vec3(cornerX + maxZ - localZ, localY, cornerZ + localX);
                case SE -> new Vec3(cornerX + maxX - localX, localY, cornerZ + maxZ - localZ);
                case SW -> new Vec3(cornerX + localZ, localY, cornerZ + maxX - localX);
            };
        }
    }

    /**
     * Encode a world block position into the int format used by room skeletons.
     * Format: (relX << 24) | (worldY << 16) | (relZ << 8) | blockId
     */
    public static int posId(int relX, int worldY, int relZ, int blockId) {
        return (relX << 24) | ((worldY & 0xFF) << 16) | (relZ << 8) | (blockId & 0xFF);
    }

    public static int posIdX(int v) { return (v >>> 24) & 0xFF; }
    public static int posIdY(int v) { return (v >>> 16) & 0xFF; }
    public static int posIdZ(int v) { return (v >>> 8) & 0xFF; }
    public static int posIdBlock(int v) { return v & 0xFF; }

    /** Build a lookup key that is rotation-invariant (for room matching) */
    public static int invariantKey(int x, int y, int z, int block, int maxX, int maxZ) {
        // mirror x/z so the fingerprint is the same for any rotation
        int mx = Math.min(x, maxX - x);
        int mz = Math.min(z, maxZ - z);
        return (mx << 24) | ((y & 0xFF) << 16) | (mz << 8) | (block & 0xFF);
    }
}
