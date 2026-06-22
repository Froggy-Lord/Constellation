package com.froggylord.constellation.constellation;

import com.froggylord.constellation.ConstellationClient;
import com.froggylord.constellation.config.OrionConfig;
import com.froggylord.constellation.render.WorldRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class GoldorWaypoints {

    private GoldorWaypoints() {}

    // phase 0 positions (first goldor section)
    private static final int[][] PHASE0 = {
        {110,121,91},  
        {111,113,73},  // terminal #1
        {111,119,79},  // terminal #2
        {89,112,92},   
        {89,122,101},  
        {106,124,113}, 
        {94,124,113},  
    };
    
    private static final int[][] PHASE1 = {
        {60,132,143},  
        {68,109,121},  
        {59,120,122},  
        {47,109,121},  // terminal #3
        {40,124,122},  
        {39,108,143},  
        {27,124,127},  
        {23,132,138},  
    };
    
    private static final int[][] PHASE2 = {
        {0,120,77},    
        {-3,109,112},  
        {-3,119,93},   
        {19,123,93},   // terminal #3
        {-3,109,77},   
        {14,122,55},   
        {2,122,55},    
    };
    
    private static final int[][] PHASE3 = {
        {63,127,35},   
        {41,109,29},   
        {59,121,29},   
        {77,109,29},   
        {95,121,29},   
        {69,126,58},   
        {57,126,58},   
    };

    public static void draw(WorldRenderer.Ctx ctx) {
        OrionConfig cfg = ConstellationClient.cfg().orion;
        if (cfg == null || !cfg.terminalSolvers) return;
        if (!ConstellationClient.loc().inDungeons()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;

        
        var pp = mc.player.position();
        int[][] positions = null;
        if (pp.x > 50) positions = PHASE0;
        else if (pp.x > 20) positions = PHASE1;
        else if (pp.x > -10) positions = PHASE2;
        else positions = PHASE3;
        if (positions == null) return;

        for (int[] p : positions) {
            double x = p[0], y = p[1], z = p[2];
            
            
            ctx.highlight(new AABB(x - 0.5, y - 0.5, z - 0.5, x + 0.5, y + 0.5, z + 0.5),
                0x40FFFF00, true);
            ctx.beam(x, y + 2, z, 0xFFFFFF00, 8, true);
            ctx.label(new Vec3(x, y + 1.5, z), "Term", 0xFFFFFF00, true);
        }
    }
}
