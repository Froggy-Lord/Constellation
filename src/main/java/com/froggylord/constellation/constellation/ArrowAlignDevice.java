package com.froggylord.constellation.constellation;

import com.froggylord.constellation.ConstellationClient;
import com.froggylord.constellation.render.WorldRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

import java.util.Arrays;

// ported from Odin (BSD-3-Clause): features/impl/boss/ArrowAlign.kt
// cross-checked with NoFrills (GPL-3.0): features/dungeons/DeviceSolvers.java (ArrowAlign)
public final class ArrowAlignDevice {
    private static final BlockPos CORNER = new BlockPos(-2, 120, 75);
    private static final BlockPos CENTRE = new BlockPos(0, 120, 77);
    private static final int[][] SOLUTIONS = {
        {7,7,-1,-1,-1, 1,-1,-1,-1,-1, 1,3,3,3,3, -1,-1,-1,-1,1, -1,-1,-1,7,1},
        {-1,-1,7,7,5, -1,7,1,-1,5, -1,-1,-1,-1,-1, -1,7,5,-1,1, -1,-1,7,7,1},
        {7,7,-1,-1,-1, 1,-1,-1,-1,-1, 1,3,-1,7,5, -1,-1,-1,-1,5, -1,-1,-1,3,3},
        {5,3,3,3,-1, 5,-1,-1,-1,-1, 7,7,-1,-1,-1, 1,-1,-1,-1,-1, 1,3,3,3,-1},
        {5,3,3,3,3, 5,-1,-1,-1,1, 7,7,-1,-1,1, -1,-1,-1,-1,1, -1,7,7,7,1},
        {7,7,7,7,-1, 1,-1,-1,-1,-1, 1,3,3,3,3, -1,-1,-1,-1,1, -1,7,7,7,1},
        {-1,-1,-1,-1,-1, 1,-1,1,-1,1, 1,-1,1,-1,1, 1,-1,1,-1,1, -1,-1,-1,-1,-1},
        {-1,-1,-1,-1,-1, 1,3,3,3,3, -1,-1,-1,-1,1, 7,7,7,7,1, -1,-1,-1,-1,-1},
        {-1,-1,-1,-1,-1, -1,1,-1,1,-1, 7,1,7,1,3, 1,-1,1,-1,1, -1,-1,-1,-1,-1}
    };

    private ArrowAlignDevice() {}

    public static void draw(WorldRenderer.Ctx ctx) {
        var cfg = ConstellationClient.cfg().orion;
        var dungeon = ConstellationClient.dungeon();
        if (cfg == null || !cfg.arrowAlignSolver) return;
        if (!ConstellationClient.loc().inDungeons() || !dungeon.inBoss() || !dungeon.floor().endsWith("7")) return;
        if (!dungeon.bossPhase().equals("Goldor")) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || mc.player.blockPosition().distSqr(CENTRE) > 200) return;

        ItemFrame[] frames = new ItemFrame[25];
        int[] rotations = new int[25];
        Arrays.fill(rotations, -1);
        for (var entity : mc.level.entitiesForRendering()) {
            if (!(entity instanceof ItemFrame frame) || frame.getItem().getItem() != Items.ARROW) continue;
            int index = index(frame.blockPosition());
            if (index < 0) continue;
            frames[index] = frame;
            rotations[index] = frame.getRotation();
        }

        int[] solution = findSolution(rotations);
        if (solution == null) return;
        for (int i = 0; i < solution.length; i++) {
            if (frames[i] == null) continue;
            int clicks = (8 - rotations[i] + solution[i]) % 8;
            if (clicks == 0) continue;
            int colour = clicks < 3 ? 0xFF55FF55 : clicks < 5 ? 0xFFFFAA00 : 0xFFFF5555;
            Vec3 pos = Vec3.atCenterOf(framePosition(i)).add(-.3, .1, 0);
            ctx.label(pos, Integer.toString(clicks), colour, false);
        }
    }

    private static int index(BlockPos pos) {
        if (pos.getX() != CORNER.getX()) return -1;
        int y = pos.getY() - CORNER.getY();
        int z = pos.getZ() - CORNER.getZ();
        return y < 0 || y >= 5 || z < 0 || z >= 5 ? -1 : y + z * 5;
    }

    private static BlockPos framePosition(int index) {
        return CORNER.offset(0, index % 5, index / 5);
    }

    private static int[] findSolution(int[] rotations) {
        for (int[] solution : SOLUTIONS) {
            boolean matches = true;
            for (int i = 0; i < solution.length; i++) {
                if ((solution[i] == -1) != (rotations[i] == -1)) {
                    matches = false;
                    break;
                }
            }
            if (matches) return solution;
        }
        return null;
    }
}
