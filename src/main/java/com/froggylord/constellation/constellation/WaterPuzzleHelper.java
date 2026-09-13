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
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;

// ported from Odin (BSD-3-Clause):
// src/main/kotlin/com/odtheking/odin/features/impl/dungeon/puzzlesolvers/WaterSolver.kt
// src/main/resources/assets/odin/puzzles/waterSolutions.json
public final class WaterPuzzleHelper {

    private record Step(Lever lever, double seconds) {}

    private enum Lever {
        COAL("coal_block", new BlockPos(20, 61, 10)),
        GOLD("gold_block", new BlockPos(20, 61, 15)),
        QUARTZ("quartz_block", new BlockPos(20, 61, 20)),
        DIAMOND("diamond_block", new BlockPos(10, 61, 20)),
        EMERALD("emerald_block", new BlockPos(10, 61, 15)),
        CLAY("hardened_clay", new BlockPos(10, 61, 10)),
        WATER("water", new BlockPos(15, 60, 5));

        final String jsonName;
        final BlockPos relativePos;
        int used;

        Lever(String jsonName, BlockPos relativePos) {
            this.jsonName = jsonName;
            this.relativePos = relativePos;
        }
    }

    private static final BlockPos[] WOOL = {
        new BlockPos(15, 56, 19), new BlockPos(15, 56, 18), new BlockPos(15, 56, 17),
        new BlockPos(15, 56, 16), new BlockPos(15, 56, 15)
    };
    private static final Map<Lever, List<Double>> solution = new EnumMap<>(Lever.class);
    private static JsonObject solutionData;
    private static String roomKey = "";
    private static int pattern = -1;
    private static long openedWaterTick = -1;
    private static boolean initialized;

    private WaterPuzzleHelper() {}

    public static void draw(WorldRenderer.Ctx ctx) {
        if (!RoomMatch.isMatched() || !RoomMatch.currentRoom().contains("water-puzzle")) return;
        OrionConfig cfg = ConstellationClient.cfg().orion;
        if (cfg == null || !cfg.waterboardSolver) return;
        if (!ConstellationClient.loc().inDungeons()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;

        init();
        syncRoom();
        if (pattern == -1) scan(mc);
        Step next = nextStep();
        if (next == null) return;

        BlockPos pos = worldPos(next.lever.relativePos);
        long remainingTicks = openedWaterTick < 0 ? 0 : openedWaterTick + Math.round(next.seconds * 20) - mc.level.getGameTime();
        boolean ready = next.seconds == 0 || openedWaterTick >= 0 && remainingTicks <= 0;
        int colour = ready ? 0xFF55FF55 : 0xFFFFAA00;
        String label = ready ? "PULL NEXT" : String.format(Locale.ROOT, "PULL IN %.1fs", remainingTicks / 20.0);
        ctx.highlight(new AABB(pos), ready ? 0x8055FF55 : 0x80FFAA00, true);
        ctx.label(Vec3.atCenterOf(pos).add(0, .9, 0), label, colour, true);
        ctx.line(mc.player.getEyePosition(), Vec3.atCenterOf(pos), colour, true);
    }

    private static void init() {
        if (initialized) return;
        initialized = true;
        loadSolutions();
        UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
            if (!RoomMatch.isMatched() || !RoomMatch.currentRoom().contains("water-puzzle") || solution.isEmpty())
                return InteractionResult.PASS;
            for (Lever lever : Lever.values()) {
                if (!worldPos(lever.relativePos).equals(hit.getBlockPos())) continue;
                List<Double> times = solution.get(lever);
                if (times != null && lever.used < times.size()) lever.used++;
                if (lever == Lever.WATER && openedWaterTick < 0) openedWaterTick = level.getGameTime();
                break;
            }
            return InteractionResult.PASS;
        });
    }

    private static void syncRoom() {
        String key = RoomMatch.currentRoom() + ':' + RoomMatch.anchorX() + ':' + RoomMatch.anchorZ() + ':' + RoomMatch.currentDir();
        if (key.equals(roomKey)) return;
        roomKey = key;
        pattern = -1;
        openedWaterTick = -1;
        solution.clear();
        for (Lever lever : Lever.values()) lever.used = 0;
    }

    private static void scan(Minecraft mc) {
        StringBuilder extended = new StringBuilder();
        for (int i = 0; i < WOOL.length; i++)
            if (!mc.level.getBlockState(worldPos(WOOL[i])).isAir()) extended.append(i);
        if (extended.length() != 3) return;

        if (mc.level.getBlockState(worldPos(new BlockPos(14, 77, 27))).is(Blocks.TERRACOTTA)) pattern = 0;
        else if (mc.level.getBlockState(worldPos(new BlockPos(16, 78, 27))).is(Blocks.EMERALD_BLOCK)) pattern = 1;
        else if (mc.level.getBlockState(worldPos(new BlockPos(14, 78, 27))).is(Blocks.DIAMOND_BLOCK)) pattern = 2;
        else if (mc.level.getBlockState(worldPos(new BlockPos(14, 78, 27))).is(Blocks.QUARTZ_BLOCK)) pattern = 3;
        else return;

        if (solutionData == null) return;
        JsonObject patterns = solutionData.getAsJsonObject("false");
        JsonObject variants = patterns == null ? null : patterns.getAsJsonObject(Integer.toString(pattern));
        JsonObject selected = variants == null ? null : variants.getAsJsonObject(extended.toString());
        if (selected == null) return;
        solution.clear();
        for (Lever lever : Lever.values()) {
            JsonArray times = selected.getAsJsonArray(lever.jsonName);
            if (times == null) continue;
            List<Double> values = new ArrayList<>();
            for (JsonElement time : times) values.add(time.getAsDouble());
            solution.put(lever, List.copyOf(values));
        }
    }

    private static Step nextStep() {
        return solution.entrySet().stream()
            .flatMap(entry -> entry.getValue().subList(Math.min(entry.getKey().used, entry.getValue().size()), entry.getValue().size())
                .stream().map(time -> new Step(entry.getKey(), time)))
            .min(Comparator.comparing((Step step) -> step.seconds != 0)
                .thenComparingInt(step -> step.seconds == 0 ? step.lever.ordinal() : Integer.MAX_VALUE)
                .thenComparingDouble(Step::seconds))
            .orElse(null);
    }

    private static void loadSolutions() {
        try (var in = WaterPuzzleHelper.class.getResourceAsStream("/assets/constellation/dungeons/waterSolutions.json")) {
            if (in != null) solutionData = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (Exception e) {
            ConstellationClient.LOGGER.error("failed loading water puzzle solutions", e);
        }
    }

    private static BlockPos worldPos(BlockPos relative) {
        long[] world = RoomTransform.relativeToActual(RoomMatch.currentDir(), RoomMatch.anchorX(), RoomMatch.anchorZ(),
            relative.getX(), relative.getY(), relative.getZ());
        return new BlockPos((int) world[0], (int) world[1], (int) world[2]);
    }
}
