package com.froggylord.constellation.constellation;

import com.froggylord.constellation.ConstellationClient;
import com.froggylord.constellation.render.WorldRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

// ported from NoFrills (GPL-3.0): features/dungeons/DeviceSolvers.java (Sharpshooter)
public final class TargetPracticeSolver {
    private static final AABB ACTIVE_AREA = new AABB(63.2, 127, 35.2, 63.8, 128, 35.8);

    private TargetPracticeSolver() {}

    public static void draw(WorldRenderer.Ctx ctx) {
        var cfg = ConstellationClient.cfg().orion;
        var dungeon = ConstellationClient.dungeon();
        if (cfg == null || !cfg.targetPracticeSolver) return;
        if (!ConstellationClient.loc().inDungeons() || !dungeon.inBoss() || !dungeon.floor().endsWith("7")) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || !ACTIVE_AREA.intersects(mc.player.getBoundingBox())) return;

        BlockPos target = findTarget(mc);
        if (target == null) return;
        AABB box = new AABB(target.getX(), target.getY(), target.getZ(),
            target.getX() + 1, target.getY() + 1, target.getZ() + 1);
        ctx.highlight(box, 0xFF55FF55, true);
        ctx.label(Vec3.atCenterOf(target).add(0, .8, 0), "Target", 0xFF55FF55, true);
    }

    private static BlockPos findTarget(Minecraft mc) {
        for (int x = 64; x <= 68; x++) {
            for (int y = 126; y <= 130; y++) {
                BlockPos pos = new BlockPos(x, y, 50);
                if (mc.level.getBlockState(pos).is(Blocks.EMERALD_BLOCK)) return pos;
            }
        }
        return null;
    }
}
