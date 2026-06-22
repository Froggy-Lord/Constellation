package com.froggylord.constellation.constellation;

import com.froggylord.constellation.ConstellationClient;
import com.froggylord.constellation.config.OrionConfig;
import com.froggylord.constellation.render.WorldRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.monster.Silverfish;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Silverfish puzzle (F7/M7) — highlights silverfish entities in the ice
 * puzzle room and draws lines to the nearest pressure plate target.
 * The puzzle requires guiding silverfish to plates by breaking ice blocks.
 */
public final class SilverfishSolver {

    private SilverfishSolver() {}

    public static void draw(WorldRenderer.Ctx ctx) {
        OrionConfig cfg = ConstellationClient.cfg().orion;
        if (cfg == null || !cfg.silverfishSolver) return;
        if (!ConstellationClient.loc().inDungeons()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;

        // scan for silverfish entities
        java.util.List<Silverfish> fish = new java.util.ArrayList<>();
        for (var e : mc.level.entitiesForRendering()) {
            if (e instanceof Silverfish sf && e.distanceToSqr(mc.player.position()) < 400) {
                fish.add(sf);
            }
        }
        if (fish.isEmpty()) return;

        // scan for pressure plates (targets)
        java.util.List<Vec3> targets = new java.util.ArrayList<>();
        var pp = mc.player.blockPosition();
        for (int dx = -10; dx <= 10; dx++)
            for (int dz = -10; dz <= 10; dz++)
                for (int dy = -2; dy <= 2; dy++) {
                    var bp = pp.offset(dx, dy, dz);
                    var bs = mc.level.getBlockState(bp);
                    if (bs.getBlock().getDescriptionId().contains("pressure_plate")) {
                        targets.add(Vec3.atCenterOf(bp));
                    }
                }

        // draw each silverfish → nearest target
        for (Silverfish sf : fish) {
            Vec3 pos = sf.position();
            ctx.outline(sf.getBoundingBox().inflate(0.1), 0xFFAAAAFF, false);
            if (!targets.isEmpty()) {
                Vec3 nearest = targets.get(0);
                double best = pos.distanceToSqr(nearest);
                for (Vec3 t : targets) {
                    double d = pos.distanceToSqr(t);
                    if (d < best) { best = d; nearest = t; }
                }
                ctx.line(pos, nearest, 0x60AAAAFF, false);
            }
        }
    }
}
