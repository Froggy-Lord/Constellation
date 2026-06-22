package com.froggylord.constellation.constellation;

import com.froggylord.constellation.ConstellationClient;
import com.froggylord.constellation.config.OrionConfig;
import com.froggylord.constellation.render.WorldRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class WaterPuzzleHelper {

    private WaterPuzzleHelper() {}

    
    private static final String[] GATE_TYPES = {
        "terracotta", "gold", "diamond", "emerald", "quartz", "coal", "water"
    };

    public static void draw(WorldRenderer.Ctx ctx) {
        OrionConfig cfg = ConstellationClient.cfg().orion;
        if (cfg == null || !cfg.waterboardSolver) return;
        if (!ConstellationClient.loc().inDungeons()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;

        
        var pp = mc.player.blockPosition();
        java.util.List<BlockPos> gates = new java.util.ArrayList<>();
        for (int dx = -20; dx <= 20; dx++)
            for (int dz = -20; dz <= 20; dz++)
                for (int dy = -5; dy <= 5; dy++) {
                    var bp = pp.offset(dx, dy, dz);
                    var bs = mc.level.getBlockState(bp);
                    Block b = bs.getBlock();
                    String id = b.getDescriptionId();
                    
                    if (id.contains("terracotta") || id.contains("concrete") || id.contains("wool")) {
                        if (!id.contains("light_gray") && !id.contains("gray_")) {
                            gates.add(bp);
                        }
                    }
                }

        
        
        java.util.Set<String> seen = new java.util.HashSet<>();
        for (var bp : gates) {
            String key = bp.getX() + "," + bp.getZ();
            if (!seen.add(key)) continue;
            var bs = mc.level.getBlockState(bp);
            int colour = 0x40FFFF00;
            String id = bs.getBlock().getDescriptionId();
            if (id.contains("red")) colour = 0x40FF3333;
            else if (id.contains("gold") || id.contains("yellow")) colour = 0x40FFAA00;
            else if (id.contains("diamond") || id.contains("light_blue")) colour = 0x4055FFFF;
            else if (id.contains("emerald") || id.contains("lime")) colour = 0x4055FF55;
            else if (id.contains("quartz") || id.contains("white")) colour = 0x40FFFFFF;
            else if (id.contains("coal") || id.contains("black")) colour = 0x40333333;

            ctx.highlight(new AABB(bp), colour, true);
            ctx.beam(bp.getX() + 0.5, bp.getY() + 1, bp.getZ() + 0.5,
                colour | 0xFF000000, 3, true);
        }
    }
}
