package com.froggylord.constellation.constellation;

import com.froggylord.constellation.ConstellationClient;
import com.froggylord.constellation.config.AndromedaConfig;
import com.froggylord.constellation.core.LocationManager.SkyblockArea;
import com.froggylord.constellation.render.WorldRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class RiftWaypoints {

    private RiftWaypoints() {}

    // mirrorverse sections with thei...
    private static final int[][][] MIRRORVERSE = {
        
        {{-101,52,-116},{-99,52,-110},{-95,52,-108},{-88,52,-107},{-95,52,-114},{-95,52,-109},{-84,52,-112},{-91,52,-108},{-82,52,-118},{-76,52,-113},{-70,52,-113},{-64,52,-113}},
        
        {{-106,40,-26},{-103,40,-20},{-97,40,-14},{-92,40,-13},{-86,40,-13},{-80,40,-17},{-75,40,-24},{-69,40,-29}},
        
        {{-69,40,-29},{-63,40,-32},{-56,40,-32},{-50,40,-30},{-44,40,-26},{-39,40,-21},{-37,40,-15},{-36,40,-8}},
        
        {{-36,40,-8},{-36,40,0},{-36,40,8},{-36,40,16},{-36,40,23},{-36,40,30}},
        
        {{-36,40,30},{-37,52,38},{-39,52,45},{-43,52,52},{-50,52,57},{-57,52,59},{-64,52,60},{-71,52,59},{-78,52,56},{-84,52,52},{-90,52,52},{-95,52,55},{-100,52,60},{-106,52,62},{-112,52,63},{-118,52,65},{-125,52,65},{-131,52,65}},
        
        {{-131,52,65},{-137,52,65},{-143,52,65},{-149,52,65}},
    };

    
    private static final int[][] ENIGMA_SOULS = {
        {-15,91,94},{-27,71,90},{-6,60,226},{-142,68,174},{-137,51,120},{-129,72,77},
        {-27,89,136},{-137,133,156},{-108,117,123},{-115,69,61},{43,91,56},{-168,81,12},
        {-204,75,27},{-201,71,52},{-142,62,6},{-140,48,20},{-114,52,-5},{-93,56,-6},
        {-68,42,28},{-23,41,67},{29,72,96},{16,32,82},{-18,42,54},{-53,66,81},
        {-72,62,42},{-93,52,28},{-102,52,14},{-128,118,62},{-107,88,88},{-86,63,28},
        {-82,66,69},{-90,111,131},{-63,82,119},{-76,42,-3},{-29,42,8},{22,51,112},
        {17,32,141},{-3,62,144},{-23,45,92},{-50,80,84},{-72,46,98},
    };

    public static void draw(WorldRenderer.Ctx ctx) {
        AndromedaConfig cfg = ConstellationClient.cfg().andromeda;
        if (cfg == null || !cfg.timeHud) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;
        boolean inRift = ConstellationClient.loc().area() == SkyblockArea.THE_RIFT;
        if (!inRift) return;

        // mirrorverse waypoints — show a...
        for (int[][] section : MIRRORVERSE) {
            for (int i = 0; i < section.length; i++) {
                int[] p = section[i];
                double x = p[0], y = p[1], z = p[2];
                ctx.highlight(new AABB(x-0.3,y-0.3,z-0.3,x+0.3,y+0.3,z+0.3), 0x40FF55FF, true);
                
                if (i + 1 < section.length) {
                    int[] nxt = section[i+1];
                    ctx.line(new Vec3(x,y,z), new Vec3(nxt[0],nxt[1],nxt[2]), 0x60FF55FF, true);
                }
            }
        }

        // enigma souls — render purple beams
        boolean showSouls = cfg.enigmaSoulTracker;
        if (showSouls) {
            for (int[] p : ENIGMA_SOULS) {
                double x = p[0], y = p[1], z = p[2];
                double dist = mc.player.position().distanceToSqr(x, y, z);
                if (dist > 2500) continue; 
                ctx.beam(x, y+1, z, 0xFFAA00FF, 6, true);
                ctx.label(new Vec3(x, y+2, z), "Soul", 0xFFAA00FF, true);
            }
        }
    }
}
