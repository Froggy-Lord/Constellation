package com.froggylord.constellation.data;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DungeonDataTest {

    @Test
    void derivesRotatedWaypointsWithCurrentFloorOffset() {
        String room = "unit-test-room";
        DungeonData.SECRETS.put(room, List.of(new DungeonData.Secret("chest", "one", 7, 72, 23)));
        try {
            for (RoomTransform.Direction dir : RoomTransform.Direction.values()) {
                DungeonData.SecretWaypoint waypoint =
                    DungeonData.secretsFor(room, dir, 17, -49, 80).getFirst();
                long[] expected = RoomTransform.relativeToActual(dir, 17, -49, 7, 84, 23);
                assertEquals(expected[0], waypoint.x(), dir.name());
                assertEquals(expected[1], waypoint.y(), dir.name());
                assertEquals(expected[2], waypoint.z(), dir.name());
            }
        } finally {
            DungeonData.SECRETS.remove(room);
        }
    }

    @Test
    void missingRoomReturnsAnEmptyList() {
        assertTrue(DungeonData.secretsFor("not-a-room").isEmpty());
        assertTrue(DungeonData.secretsFor(null).isEmpty());
        DungeonData.load();
        DungeonData.Route pearlRoute = DungeonData.PEARL_ROUTES.get("sewer-7").get(3);
        assertEquals(15.569756090466072, pearlRoute.pearls().getFirst()[0], 0.0000000001);
        assertEquals(18.186706898216386, pearlRoute.pearls().getFirst()[2], 0.0000000001);
        assertEquals(-45.987366, pearlRoute.pearlAngles().getFirst()[0], 0.0000001);
        assertEquals(-4.49593999999999, pearlRoute.pearlAngles().getFirst()[1], 0.0000001);
    }
}
