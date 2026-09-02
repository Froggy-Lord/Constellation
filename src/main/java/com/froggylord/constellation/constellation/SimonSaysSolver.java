package com.froggylord.constellation.constellation;

import com.froggylord.constellation.ConstellationClient;
import com.froggylord.constellation.config.OrionConfig;
import com.froggylord.constellation.render.WorldRenderer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundSectionBlocksUpdatePacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

public final class SimonSaysSolver {
    private static final BlockPos START_BUTTON = new BlockPos(110, 121, 91);
    private static final List<BlockPos> PATTERN = new ArrayList<>();
    private static OrionConfig cfg;
    private static boolean initialized;
    private static int nextIndex;
    private static boolean canBreak;
    private static boolean wasBroken;
    private static int breakDelay;

    private SimonSaysSolver() {}

    // ported from Devonian (GPL-3.0):
    // features/dungeons/solvers/SimonSaysSolver.kt
    // interaction progress and the two-button render are cross-checked against
    // Skyblocker (LGPL): skyblock/dungeon/device/SimonSays.java
    public static void init(OrionConfig config) {
        cfg = config;
        if (initialized) return;
        initialized = true;
        ConstellationClient.instance().packets().register(SimonSaysSolver::onPacket);
        ConstellationClient.tick().every(1, "orion-simon-break", SimonSaysSolver::tickBreakState);
        UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
            if (!active() || hand != InteractionHand.MAIN_HAND) return InteractionResult.PASS;
            BlockPos pos = hit.getBlockPos();
            if (pos.equals(START_BUTTON)) {
                reset();
            } else if (isButton(pos) && level.getBlockState(pos).is(Blocks.STONE_BUTTON)) {
                if (nextIndex < PATTERN.size() && PATTERN.get(nextIndex).equals(pos)) {
                    nextIndex++;
                    if (nextIndex == PATTERN.size())
                        PartyMessages.send("simon-progress", java.util.Map.of("round", PATTERN.size(), "total", 5));
                }
            }
            return InteractionResult.PASS;
        });
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> resetAll());
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> resetAll());
    }

    public static void draw(WorldRenderer.Ctx ctx) {
        if (!active()) {
            resetAll();
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || !mc.level.hasChunkAt(START_BUTTON)) return;

        int rendered = 0;
        for (int i = nextIndex; i < PATTERN.size(); i++) {
            BlockPos pos = PATTERN.get(i);
            if (!mc.level.getBlockState(pos).is(Blocks.STONE_BUTTON)) continue;
            int colour = rendered == 0 ? 0xFF55FF55 : 0xFFFFFF55;
            ctx.highlight(buttonBox(pos), colour, true);
            if (++rendered == 2) break;
        }
    }

    private static void onPacket(Object packet) {
        if (!active()) return;
        if (packet instanceof ClientboundBlockUpdatePacket update) {
            onBlock(update.getPos(), update.getBlockState().getBlock());
        } else if (packet instanceof ClientboundSectionBlocksUpdatePacket updates) {
            List<BlockPos> resets = new ArrayList<>();
            List<BlockPos> lanterns = new ArrayList<>();
            updates.runUpdates((pos, state) -> {
                BlockPos copy = pos.immutable();
                if (isButton(copy) && state.isAir()) resets.add(copy);
                if (isBoard(copy) && state.is(Blocks.SEA_LANTERN)) lanterns.add(copy);
            });
            if (!resets.isEmpty()) reset();
            for (BlockPos pos : lanterns) addLantern(pos);
        }
    }

    private static void onBlock(BlockPos pos, net.minecraft.world.level.block.Block block) {
        if (isButton(pos) && block == Blocks.AIR) reset();
        if (isBoard(pos) && block == Blocks.SEA_LANTERN) addLantern(pos);
    }

    private static void addLantern(BlockPos lantern) {
        BlockPos button = lantern.west().immutable();
        if (PATTERN.isEmpty() || !PATTERN.get(PATTERN.size() - 1).equals(button)) PATTERN.add(button);
    }

    // ported from NoammAddons (CC0-1.0): features/impl/floor7/devices/SimonSays.kt
    private static void tickBreakState() {
        if (!active()) { resetAll(); return; }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || !mc.level.hasChunkAt(START_BUTTON)) return;
        boolean boardLit = false;
        for (int y = 120; y <= 123; y++) for (int z = 92; z <= 95; z++)
            if (!mc.level.getBlockState(new BlockPos(111, y, z)).is(Blocks.OBSIDIAN)) boardLit = true;
        if (boardLit) {
            breakDelay = 12;
            canBreak = true;
            if (wasBroken) {
                wasBroken = false;
                PartyMessages.send("simon-restarted");
            }
            return;
        }
        if (breakDelay > 0) { breakDelay--; return; }
        if (!canBreak) return;
        for (int y = 120; y <= 123; y++) for (int z = 92; z <= 95; z++)
            if (!mc.level.getBlockState(new BlockPos(110, y, z)).isAir()) return;
        canBreak = false;
        wasBroken = true;
        PartyMessages.send("simon-broken");
    }

    private static boolean isBoard(BlockPos pos) {
        return pos.getX() == 111 && pos.getY() >= 120 && pos.getY() <= 123
            && pos.getZ() >= 92 && pos.getZ() <= 95;
    }

    private static boolean isButton(BlockPos pos) {
        return pos.getX() == 110 && pos.getY() >= 120 && pos.getY() <= 123
            && pos.getZ() >= 92 && pos.getZ() <= 95;
    }

    private static boolean active() {
        if (cfg == null || !cfg.simonSaysSolver || !ConstellationClient.loc().inDungeons()) return false;
        var dungeon = ConstellationClient.dungeon();
        return dungeon.inBoss() && dungeon.floor().endsWith("7") && "Maxor".equals(dungeon.bossPhase());
    }

    private static AABB buttonBox(BlockPos pos) {
        double e = 0.002;
        return new AABB(
            pos.getX() + 0.875 - e, pos.getY() + 0.375 - e, pos.getZ() + 0.3125 - e,
            pos.getX() + 1.0 + e, pos.getY() + 0.625 + e, pos.getZ() + 0.6875 + e
        );
    }

    private static void reset() {
        PATTERN.clear();
        nextIndex = 0;
    }

    private static void resetAll() {
        reset();
        canBreak = false;
        wasBroken = false;
        breakDelay = 0;
    }
}
