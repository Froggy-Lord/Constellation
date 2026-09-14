package com.froggylord.constellation.data;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

class RoomTransformTest {

    @Test
    void roundTripsEvenAndOddRoomExtentsForEveryRotation() {
        int[] extents = {31, 32, 63, 64};
        for (RoomTransform.Direction dir : RoomTransform.Direction.values()) {
            for (int extent : extents) {
                int[][] points = {{0, 68, 0}, {extent / 2, 73, extent / 2}, {extent, 91, extent}};
                for (int[] point : points) {
                    long[] world = RoomTransform.relativeToActual(
                        dir, -185, -121, point[0], point[1], point[2]);
                    long[] relative = RoomTransform.actualToRelative(
                        dir, -185, -121, (int) world[0], (int) world[1], (int) world[2]);
                    assertArrayEquals(new long[]{point[0], point[1], point[2]}, relative,
                        dir + " extent " + extent);
                }
            }
        }
    }

    @Test
    void secretRoutesConventionProducesTheSameWorldCoordinates() {
        for (RoomTransform.SecretRoutesDirection secretDir : RoomTransform.SecretRoutesDirection.values()) {
            RoomTransform.Direction constellationDir =
                RoomTransform.constellationDirFromSecretRoutes(secretDir);
            long[] expected = secretRoutesRelativeToActual(secretDir, 17, -49, 7, 68, 23);
            assertArrayEquals(expected,
                RoomTransform.relativeToActual(constellationDir, 17, -49, 7, 68, 23),
                secretDir.name());
            double[] exact = RoomTransform.relativeToActual(
                constellationDir, 17, -49, 7.25, 68.5, 23.75);
            assertArrayEquals(secretRoutesRelativeToActual(
                secretDir, 17, -49, 7.25, 68.5, 23.75), exact, 0.0000001, secretDir.name());
            assertArrayEquals(new double[]{7.25, 68.5, 23.75},
                RoomTransform.actualToRelative(constellationDir, 17, -49, exact[0], exact[1], exact[2]),
                0.0000001, secretDir.name());
        }
    }

    private static long[] secretRoutesRelativeToActual(RoomTransform.SecretRoutesDirection dir,
                                                        int cx, int cz, int x, int y, int z) {
        return switch (dir) {
            case W -> new long[]{cx - z, y, cz + x};
            case N -> new long[]{cx - x, y, cz - z};
            case E -> new long[]{cx + z, y, cz - x};
            case S -> new long[]{cx + x, y, cz + z};
        };
    }

    private static double[] secretRoutesRelativeToActual(RoomTransform.SecretRoutesDirection dir,
                                                          int cx, int cz, double x, double y, double z) {
        return switch (dir) {
            case W -> new double[]{cx - z, y, cz + x};
            case N -> new double[]{cx - x, y, cz - z};
            case E -> new double[]{cx + z, y, cz - x};
            case S -> new double[]{cx + x, y, cz + z};
        };
    }
}
