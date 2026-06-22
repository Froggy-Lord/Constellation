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

public final class BoulderSolver {

    private BoulderSolver() {}

    public static void draw(WorldRenderer.Ctx ctx) {
        OrionConfig cfg = ConstellationClient.cfg().orion;
        if (cfg == null || !cfg.terminalSolvers) return; 
        if (!ConstellationClient.loc().inDungeons()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;

        
        
        var pp = mc.player.blockPosition();
        Vec3 boulderPos = null, goalPos = null;
        java.util.List<Vec3> obstacles = new java.util.ArrayList<>();

        for (int dx = -10; dx <= 10; dx++)
            for (int dz = -10; dz <= 10; dz++)
                for (int dy = -2; dy <= 2; dy++) {
                    var bp = pp.offset(dx, dy, dz);
                    var bs = mc.level.getBlockState(bp);
                    Block b = bs.getBlock();
                    String id = b.getDescriptionId();
                    
                    if (id.contains("anvil") || id.contains("obsidian")) {
                        boulderPos = Vec3.atCenterOf(bp);
                    }
                    
                    else if (id.contains("pressure_plate") || id.contains("light_weighted") || id.contains("heavy_weighted")) {
                        goalPos = Vec3.atCenterOf(bp);
                    }
                    
                    else if (id.contains("iron_bars") || id.contains("fence") || id.contains("wall")) {
                        obstacles.add(Vec3.atCenterOf(bp));
                    }
                }

        
        if (boulderPos != null && goalPos != null) {
            ctx.outline(new AABB(boulderPos.x-0.5,boulderPos.y-0.5,boulderPos.z-0.5,
                boulderPos.x+0.5,boulderPos.y+0.5,boulderPos.z+0.5), 0xFFFF6600, true);
            ctx.label(boulderPos.add(0, 0.8, 0), "BOULDER", 0xFFFF6600, true);
            ctx.outline(new AABB(goalPos.x-0.5,goalPos.y-0.5,goalPos.z-0.5,
                goalPos.x+0.5,goalPos.y+0.5,goalPos.z+0.5), 0xFF55FF55, true);
            ctx.label(goalPos.add(0, 0.8, 0), "GOAL", 0xFF55FF55, true);
            
            ctx.line(boulderPos, goalPos, 0x60FFAA00, true);
        }
    }
}
