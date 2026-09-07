package com.froggylord.constellation.constellation;

import com.froggylord.constellation.ConstellationClient;
import com.froggylord.constellation.config.OrionConfig;
import com.froggylord.constellation.data.RoomMatch;
import com.froggylord.constellation.data.RoomTransform;
import com.froggylord.constellation.render.WorldRenderer;
import com.google.gson.*;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;

// ported from Odin (BSD-3-Clause):
// src/main/kotlin/com/odtheking/odin/features/impl/dungeon/puzzlesolvers/BoulderSolver.kt
// src/main/resources/assets/odin/puzzles/boulderSolutions.json
public final class BoulderSolver {

    private record Click(BlockPos render, BlockPos click) {}

    private static final List<Click> current = new ArrayList<>();
    private static JsonObject solutions;
    private static String roomKey = "";
    private static boolean scanned;
    private static boolean initialized;

    private BoulderSolver() {}

    public static void draw(WorldRenderer.Ctx ctx) {
        if (!RoomMatch.isMatched() || !RoomMatch.currentRoom().contains("boxes-room")) return;
        OrionConfig cfg = ConstellationClient.cfg().orion;
        if (cfg == null || !cfg.terminalSolvers) return;
        if (!ConstellationClient.loc().inDungeons()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;

        init();
        syncRoom();
        if (!scanned) scan(mc);
        if (current.isEmpty()) return;

        Click next = current.getFirst();
        ctx.highlight(new AABB(next.render), 0x8055FF55, false);
        ctx.label(Vec3.atCenterOf(next.render).add(0, .9, 0), "CLICK NEXT", 0xFF55FF55, false);
        ctx.line(mc.player.getEyePosition(), Vec3.atCenterOf(next.render), 0xFF55FF55, false);
    }

    private static void init() {
        if (initialized) return;
        initialized = true;
        loadSolutions();
        UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
            if (!RoomMatch.isMatched() || !RoomMatch.currentRoom().contains("boxes-room"))
                return InteractionResult.PASS;
            current.removeIf(step -> step.click.equals(hit.getBlockPos()));
            return InteractionResult.PASS;
        });
    }

    private static void syncRoom() {
        String key = RoomMatch.currentRoom() + ':' + RoomMatch.anchorX() + ':' + RoomMatch.anchorZ() + ':' + RoomMatch.currentDir();
        if (key.equals(roomKey)) return;
        roomKey = key;
        current.clear();
        scanned = false;
    }

    private static void scan(Minecraft mc) {
        if (solutions == null) return;
        StringBuilder fingerprint = new StringBuilder(42);
        for (int z = 24; z >= 9; z -= 3)
            for (int x = 24; x >= 6; x -= 3)
                fingerprint.append(mc.level.getBlockState(worldPos(x, 66, z)).isAir() ? '0' : '1');

        JsonArray steps = solutions.getAsJsonArray(fingerprint.toString());
        if (steps == null) return;
        scanned = true;
        current.clear();
        for (JsonElement element : steps) {
            JsonArray step = element.getAsJsonArray();
            current.add(new Click(worldPos(step.get(0).getAsInt(), 65, step.get(1).getAsInt()),
                worldPos(step.get(2).getAsInt(), 65, step.get(3).getAsInt())));
        }
    }

    private static void loadSolutions() {
        try (var in = BoulderSolver.class.getResourceAsStream("/assets/constellation/dungeons/boulderSolutions.json")) {
            if (in != null) solutions = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (Exception e) {
            ConstellationClient.LOGGER.error("failed loading boulder puzzle solutions", e);
        }
    }

    private static BlockPos worldPos(int x, int y, int z) {
        long[] world = RoomTransform.relativeToActual(RoomMatch.currentDir(), RoomMatch.anchorX(), RoomMatch.anchorZ(), x, y, z);
        return new BlockPos((int) world[0], (int) world[1], (int) world[2]);
    }
}
