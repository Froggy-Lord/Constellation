package com.froggylord.constellation.constellation;

import com.froggylord.constellation.ConstellationClient;
import com.froggylord.constellation.config.OrionConfig;
import com.froggylord.constellation.render.WorldRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;

// target practice puzzle — highlight the target block (emerald/quartz)
public final class TargetPracticeSolver {

    private TargetPracticeSolver() {}

    public static void draw(WorldRenderer.Ctx ctx) {
        OrionConfig cfg = ConstellationClient.cfg().orion;
        if (cfg == null || !cfg.targetPracticeSolver) return;
        if (!ConstellationClient.loc().inDungeons()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;

        var pp = mc.player.blockPosition();
        for (int dx = -15; dx <= 15; dx++)
            for (int dz = -15; dz <= 15; dz++)
                for (int dy = -3; dy <= 5; dy++) {
                    var bp = pp.offset(dx, dy, dz);
                    var bs = mc.level.getBlockState(bp);
                    String id = bs.getBlock().getDescriptionId();
                    // target blocks are emerald, quartz, gold — anything shiny that stands out
                    if (id.contains("emerald_block") || id.contains("quartz_block") || id.contains("gold_block")) {
                        ctx.highlight(new AABB(bp), 0x60FF3333, true);
                        ctx.beam(bp.getX()+0.5, bp.getY()+2, bp.getZ()+0.5, 0xFFFF3333, 6, true);
                    }
                }
    }
}
