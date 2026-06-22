package com.froggylord.constellation.constellation;

import com.froggylord.constellation.ConstellationClient;
import com.froggylord.constellation.config.OrionConfig;
import com.froggylord.constellation.render.WorldRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

public final class IceFillHelper {

    private IceFillHelper() {}

    public static void draw(WorldRenderer.Ctx ctx) {
        OrionConfig cfg = ConstellationClient.cfg().orion;
        if (cfg == null || !cfg.iceFillSolver) return;
        if (!ConstellationClient.loc().inDungeons()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;

        var pp = mc.player.blockPosition();
        
        for (int dx = -8; dx <= 8; dx++)
            for (int dz = -8; dz <= 8; dz++) {
                var bp = pp.offset(dx, -1, dz); 
                BlockState bs = mc.level.getBlockState(bp);
                String id = bs.getBlock().getDescriptionId();
                
                if (id.contains("ice") && !id.contains("packed")) {
                    ctx.highlight(new AABB(bp), 0x6055FFFF, false);
                } else if (id.contains("packed_ice")) {
                    ctx.highlight(new AABB(bp), 0x4055FF55, false);
                }
            }
    }
}
