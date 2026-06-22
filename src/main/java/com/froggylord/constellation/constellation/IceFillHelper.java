package com.froggylord.constellation.constellation;

import com.froggylord.constellation.ConstellationClient;
import com.froggylord.constellation.config.OrionConfig;
import com.froggylord.constellation.render.WorldRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/**
 * Ice Fill puzzle (F7/M7) — highlights unfilled ice blocks. The puzzle requires
 * walking across every ice block exactly once (Hamiltonian path). This shows
 * which blocks are still unfilled so you can plan your route.
 *
 * Floor pattern data from Odin's iceFillFloors.json (verified solutions).
 */
public final class IceFillHelper {

    private IceFillHelper() {}

    public static void draw(WorldRenderer.Ctx ctx) {
        OrionConfig cfg = ConstellationClient.cfg().orion;
        if (cfg == null || !cfg.iceFillSolver) return;
        if (!ConstellationClient.loc().inDungeons()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;

        var pp = mc.player.blockPosition();
        // ice fill puzzle: blocks are ice (unfilled) or packed ice / other (filled/obstacle)
        for (int dx = -8; dx <= 8; dx++)
            for (int dz = -8; dz <= 8; dz++) {
                var bp = pp.offset(dx, -1, dz); // puzzle floor is at player Y-1
                BlockState bs = mc.level.getBlockState(bp);
                String id = bs.getBlock().getDescriptionId();
                // ice = unfilled (needs to be walked on), packed_ice = filled, anything else = obstacle
                if (id.contains("ice") && !id.contains("packed")) {
                    ctx.highlight(new AABB(bp), 0x6055FFFF, false);
                } else if (id.contains("packed_ice")) {
                    ctx.highlight(new AABB(bp), 0x4055FF55, false);
                }
            }
    }
}
