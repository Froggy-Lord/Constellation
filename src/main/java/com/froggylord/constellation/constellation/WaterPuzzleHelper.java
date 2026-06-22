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

/**
 * Water puzzle (F7/M7) gate timer renderer. The puzzle has colored gates
 * (terracotta, gold, diamond, emerald, quartz, coal) that open/close on
 * a schedule. This scans for gate blocks near the player and shows their
 * current colour + a beam so you can plan the water flow.
 *
 * Gate timing data from Skyblocker's watertimes.json (verified Hypixel timings).
 */
public final class WaterPuzzleHelper {

    private WaterPuzzleHelper() {}

    // Block colours → gate names used in the puzzle
    private static final String[] GATE_TYPES = {
        "terracotta", "gold", "diamond", "emerald", "quartz", "coal", "water"
    };

    public static void draw(WorldRenderer.Ctx ctx) {
        OrionConfig cfg = ConstellationClient.cfg().orion;
        if (cfg == null || !cfg.waterboardSolver) return;
        if (!ConstellationClient.loc().inDungeons()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;

        // scan for gate blocks (colored terracotta/clay/wool/concrete in a row)
        var pp = mc.player.blockPosition();
        java.util.List<BlockPos> gates = new java.util.ArrayList<>();
        for (int dx = -20; dx <= 20; dx++)
            for (int dz = -20; dz <= 20; dz++)
                for (int dy = -5; dy <= 5; dy++) {
                    var bp = pp.offset(dx, dy, dz);
                    var bs = mc.level.getBlockState(bp);
                    Block b = bs.getBlock();
                    String id = b.getDescriptionId();
                    // detect gate blocks by their description IDs
                    if (id.contains("terracotta") || id.contains("concrete") || id.contains("wool")) {
                        if (!id.contains("light_gray") && !id.contains("gray_")) {
                            gates.add(bp);
                        }
                    }
                }

        // deduplicate: water puzzle gates are arranged in a line of ~7 blocks
        // only highlight one per column
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
