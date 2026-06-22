package com.froggylord.constellation.constellation;

import com.froggylord.constellation.ConstellationClient;
import com.froggylord.constellation.config.OrionConfig;
import com.froggylord.constellation.render.WorldRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;

// lights on puzzle — highlight redstone lamps that need clicking
public final class LightsOnSolver {

    private LightsOnSolver() {}

    public static void draw(WorldRenderer.Ctx ctx) {
        OrionConfig cfg = ConstellationClient.cfg().orion;
        if (cfg == null || !cfg.lightsOnSolver) return;
        if (!ConstellationClient.loc().inDungeons()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;

        var pp = mc.player.blockPosition();
        for (int dx = -12; dx <= 12; dx++)
            for (int dz = -12; dz <= 12; dz++)
                for (int dy = -3; dy <= 3; dy++) {
                    var bp = pp.offset(dx, dy, dz);
                    var bs = mc.level.getBlockState(bp);
                    String id = bs.getBlock().getDescriptionId();
                    // highlight redstone lamps — lit ones are "done", unlit need clicking
                    if (id.contains("redstone_lamp")) {
                        boolean lit = bs.getValue(net.minecraft.world.level.block.RedstoneLampBlock.LIT);
                        int col = lit ? 0x4055FF55 : 0x60FF5555;
                        ctx.highlight(new AABB(bp), col, true);
                        if (!lit) ctx.beam(bp.getX()+0.5, bp.getY()+1.5, bp.getZ()+0.5, 0xFFFF5555, 4, true);
                    }
                }
    }
}
