package com.froggylord.constellation.data;

import com.froggylord.constellation.ConstellationClient;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.zip.DeflaterOutputStream;

public class SkeletonScraper {

    private static final Path DIR = FabricLoader.getInstance().getConfigDir().resolve("constellation-skeletons");
    private static final int COLS_PER_TICK = 8;

    private static Session session = null;

    private static final class Session {
        final String name;
        final Set<Long> cells;
        final int wMinX, wMinZ, wMaxX, wMaxZ;
        final int shapeW, shapeH;
        int cx, cz;                 
        final Set<Integer> blocks = new TreeSet<>();
        Session(String name, Set<Long> cells, int wMinX, int wMinZ, int wMaxX, int wMaxZ) {
            this.name = name; this.cells = cells;
            this.wMinX = wMinX; this.wMinZ = wMinZ; this.wMaxX = wMaxX; this.wMaxZ = wMaxZ;
            this.cx = wMinX; this.cz = wMinZ;
            this.shapeW = (wMaxX - wMinX + 1) / 32;
            this.shapeH = (wMaxZ - wMinZ + 1) / 32;
        }
    }

    public static String capture(String name) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return "§cnot in a world";
        if (!ConstellationClient.loc().inDungeons()) return "§cnot in a dungeon";

        
        Set<Long> cells = MapSegments.footprint();
        int pcx = RoomGrid.cornerX(mc.player.position());
        int pcz = RoomGrid.cornerZ(mc.player.position());
        if (cells.isEmpty() || !cells.contains(RoomGrid.cellKey(pcx, pcz))) {
            cells = RoomMatch.floodPublic(mc.level, pcx, pcz);
        }
        if (cells.isEmpty()) return "§ccouldn't determine room footprint";

        int wMinX = Integer.MAX_VALUE, wMinZ = Integer.MAX_VALUE, wMaxX = Integer.MIN_VALUE, wMaxZ = Integer.MIN_VALUE;
        for (long c : cells) {
            int ccx = (int) (c >> 32), ccz = (int) c;
            wMinX = Math.min(wMinX, ccx); wMinZ = Math.min(wMinZ, ccz);
            wMaxX = Math.max(wMaxX, ccx + 30); wMaxZ = Math.max(wMaxZ, ccz + 30);
        }
        session = new Session(name.toLowerCase(Locale.ROOT).replace(' ', '-'), cells, wMinX, wMinZ, wMaxX, wMaxZ);
        return "§acapturing '" + session.name + "' (" + session.shapeW + "x" + session.shapeH + ", " + cells.size() + " cells)…";
    }

    public static void tick() {
        if (session == null) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) { session = null; return; }
        Level level = mc.level;

        int done = 0;
        while (session.cz <= session.wMaxZ && done < COLS_PER_TICK) {
            
            if (session.cells.contains(RoomGrid.cellKey(RoomGrid.cornerX((double) session.cx), RoomGrid.cornerZ((double) session.cz)))) {
                scanColumn(level, session);
                done++;
            }
            session.cx++;
            if (session.cx > session.wMaxX) { session.cx = session.wMinX; session.cz++; }
        }

        if (session.cz > session.wMaxZ) {
            save(session, mc);
            session = null;
        }
    }

    private static void scanColumn(Level level, Session s) {
        int relX = s.cx - s.wMinX, relZ = s.cz - s.wMinZ;
        if (relX < 0 || relX > 255 || relZ < 0 || relZ > 255) return;
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        for (int y = 35; y <= 130; y++) {
            m.set(s.cx, y, s.cz);
            BlockState st = level.getBlockState(m);
            if (st.isAir()) continue;
            byte id = DungeonData.numericId(BuiltInRegistries.BLOCK.getKey(st.getBlock()).toString());
            if (id == 0) continue;
            s.blocks.add(DungeonData.posIdToInt(relX, y, relZ, id));
        }
    }

    private static void save(Session s, Minecraft mc) {
        try {
            String shape = s.shapeW + "x" + s.shapeH;
            Path dir = DIR.resolve(shape);
            Files.createDirectories(dir);
            Path file = dir.resolve(s.name + ".skeleton");

            int[] arr = new int[s.blocks.size()];
            int i = 0;
            for (int v : s.blocks) arr[i++] = v;
            Arrays.sort(arr);

            try (ObjectOutputStream out = new ObjectOutputStream(
                    new DeflaterOutputStream(Files.newOutputStream(file)))) {
                out.writeObject(arr);
            }

            String msg = "§asaved §e" + arr.length + "§a blocks → §7" + file
                + "\n§7add line to index.txt: §f" + shape + "/" + s.name;
            ConstellationClient.LOGGER.info("[scraper] saved {} ({} blocks) to {}", s.name, arr.length, file);
            if (mc.player != null) mc.player.sendSystemMessage(net.minecraft.network.chat.Component.literal(msg));
        } catch (Exception e) {
            ConstellationClient.LOGGER.error("[scraper] failed to save {}", s.name, e);
            if (mc.player != null) mc.player.sendSystemMessage(
                net.minecraft.network.chat.Component.literal("§cscraper save failed: " + e.getMessage()));
        }
    }

    public static boolean isCapturing() { return session != null; }
}
