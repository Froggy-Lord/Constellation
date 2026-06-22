package com.froggylord.constellation.constellation;

import com.froggylord.constellation.ConstellationClient;
import com.froggylord.constellation.config.OrionConfig;
import com.froggylord.constellation.render.WorldRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

// arrow align puzzle — show which arrows point the wrong way
public final class ArrowAlignSolver {

    private ArrowAlignSolver() {}

    public static void draw(WorldRenderer.Ctx ctx) {
        OrionConfig cfg = ConstellationClient.cfg().orion;
        if (cfg == null || !cfg.arrowAlignSolver) return;
        if (!ConstellationClient.loc().inDungeons()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;

        var pp = mc.player.blockPosition();
        for (int dx = -10; dx <= 10; dx++)
            for (int dz = -10; dz <= 10; dz++)
                for (int dy = -3; dy <= 3; dy++) {
                    var bp = pp.offset(dx, dy, dz);
                    var bs = mc.level.getBlockState(bp);
                    if (!bs.hasProperty(BlockStateProperties.FACING)) continue;
                    var facing = bs.getValue(BlockStateProperties.FACING);
                    // draw a line in the direction the arrow points
                    Vec3 center = Vec3.atCenterOf(bp);
                    Vec3 dir = Vec3.atLowerCornerOf(facing.getNormal()).scale(0.6);
                    ctx.line(center, center.add(dir), 0x60FFAA00, true);
                }
    }
}
