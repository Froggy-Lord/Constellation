package com.froggylord.constellation.constellation;

import com.froggylord.constellation.ConstellationClient;
import com.froggylord.constellation.config.OrionConfig;
import com.froggylord.constellation.render.WorldRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.*;

// tele maze — highlight end portal frames + link matching pairs
public final class TeleportMazeSolver {

    private TeleportMazeSolver() {}

    public static void draw(WorldRenderer.Ctx ctx) {
        OrionConfig cfg = ConstellationClient.cfg().orion;
        if (cfg == null || !cfg.teleportMazeSolver) return;
        if (!ConstellationClient.loc().inDungeons()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;

        var pp = mc.player.blockPosition();
        List<BlockPos> portals = new ArrayList<>();
        for (int dx = -12; dx <= 12; dx++)
            for (int dz = -12; dz <= 12; dz++)
                for (int dy = -3; dy <= 3; dy++) {
                    var bp = pp.offset(dx, dy, dz);
                    var bs = mc.level.getBlockState(bp);
                    if (bs.getBlock().getDescriptionId().contains("end_portal_frame"))
                        portals.add(bp);
                }

        // if exactly 2, link them. if more, highlight all.
        for (BlockPos p : portals) {
            ctx.highlight(new AABB(p), 0x60FF55FF, true);
            ctx.beam(p.getX()+0.5, p.getY()+1.5, p.getZ()+0.5, 0xFFFF55FF, 5, true);
        }
        if (portals.size() == 2) {
            Vec3 a = Vec3.atCenterOf(portals.get(0));
            Vec3 b = Vec3.atCenterOf(portals.get(1));
            ctx.line(a, b, 0x80FF55FF, true);
        }
    }
}
