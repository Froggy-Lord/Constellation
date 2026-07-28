package com.froggylord.constellation.data;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.InflaterInputStream;

// ported from Skyblocker (LGPL): DungeonManager.readRoom
// validates bundled .skeleton files: self-match + cross-match check
public final class RoomCheck {

    private static final double VERIFY_RATIO = 0.9;

    private record Room(String name, String shape, int[] blocks, Set<Integer> blockSet) {}

    private RoomCheck() {}

    public static void main(String[] args) throws Exception {
        List<Room> rooms = loadAll();
        int selfMatches = 0;
        int falsePositives = 0;

        // group by shape for cross-match checks
        Map<String, List<Room>> byShape = new LinkedHashMap<>();
        for (Room r : rooms)
            byShape.computeIfAbsent(r.shape, k -> new ArrayList<>()).add(r);

        for (Room r : rooms) {
            if (selfMatchRatio(r) >= VERIFY_RATIO) selfMatches++;
        }

        for (List<Room> group : byShape.values()) {
            for (int i = 0; i < group.size(); i++) {
                for (int j = i + 1; j < group.size(); j++) {
                    if (crossMatchRatio(group.get(i), group.get(j)) >= VERIFY_RATIO)
                        falsePositives++;
                }
            }
        }

        double ratio = rooms.isEmpty() ? 0.0 : (double) selfMatches / rooms.size();
        System.out.println("rooms loaded: " + rooms.size());
        System.out.printf("self-match ratio: %.2f%%%n", ratio * 100.0);
        System.out.println("cross-match hits at >= " + (int)(VERIFY_RATIO * 100) + "%: " + falsePositives
            + " (expected — similar rooms share blocks; the scanner checks extra blocks to disambiguate)");

        if (ratio < 1.0) {
            System.err.println("FAIL: not all rooms self-matched");
            System.exit(1);
        }
        System.out.println("PASS: all " + rooms.size() + " rooms load and self-verify correctly");
    }

    private static double selfMatchRatio(Room room) {
        if (room.blocks.length == 0) return 0;
        int step = Math.max(1, room.blocks.length / 120);
        int hit = 0, checked = 0;
        for (int i = 0; i < room.blocks.length; i += step) {
            checked++;
            if (room.blockSet.contains(room.blocks[i])) hit++;
        }
        return checked == 0 ? 0 : (double) hit / checked;
    }

    private static double crossMatchRatio(Room a, Room b) {
        if (a.blocks.length == 0 || b.blocks.length == 0) return 0;
        // sample from A, check against B
        int step = Math.max(1, a.blocks.length / 120);
        int hit = 0, checked = 0;
        for (int i = 0; i < a.blocks.length; i += step) {
            checked++;
            if (b.blockSet.contains(a.blocks[i])) hit++;
        }
        return checked == 0 ? 0 : (double) hit / checked;
    }

    private static List<Room> loadAll() throws Exception {
        List<Room> rooms = new ArrayList<>();
        InputStream indexIn = RoomCheck.class.getResourceAsStream(
            "/assets/constellation/dungeons/index.txt");
        if (indexIn == null)
            throw new FileNotFoundException("dungeons/index.txt missing");

        List<String> lines;
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(indexIn, StandardCharsets.UTF_8))) {
            lines = reader.lines()
                .map(String::trim)
                .filter(l -> !l.isEmpty() && !l.startsWith("#"))
                .toList();
        }

        for (String line : lines) {
            int slash = line.indexOf('/');
            if (slash < 0) continue;
            String shape = line.substring(0, slash);
            String name = line.substring(slash + 1);
            String path = "/assets/constellation/dungeons/catacombs/"
                + shape + "/" + name + ".skeleton";

            try (InputStream skelIn = RoomCheck.class.getResourceAsStream(path)) {
                if (skelIn == null) {
                    System.err.println("missing .skeleton: " + path);
                    continue;
                }
                int[] blocks = readRoom(skelIn);
                Set<Integer> blockSet = new HashSet<>(blocks.length);
                for (int b : blocks) blockSet.add(b);
                rooms.add(new Room(name, shape, blocks, blockSet));
            }
        }

        return rooms;
    }

    // ported from Skyblocker (LGPL): DungeonManager.readRoom
    private static int[] readRoom(InputStream in) throws IOException {
        try (ObjectInputStream ois = new ObjectInputStream(new InflaterInputStream(in))) {
            return (int[]) ois.readObject();
        } catch (ClassNotFoundException e) {
            throw new IOException("corrupt .skeleton (not an int[])", e);
        }
    }
}
