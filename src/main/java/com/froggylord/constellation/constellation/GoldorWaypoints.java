package com.froggylord.constellation.constellation;

import com.froggylord.constellation.ConstellationClient;
import com.froggylord.constellation.config.OrionConfig;
import com.froggylord.constellation.render.WorldRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * F7/M7 Goldor phase terminal waypoints — verified positions from Skyblocker's
 * goldorwaypoints.json data. Renders beam + highlight through walls so you know
 * exactly where the next terminal is during the Goldor chase.
 */
public final class GoldorWaypoints {

    private GoldorWaypoints() {}

    // Phase 0 positions (first Goldor section)
    private static final int[][] PHASE0 = {
        {110,121,91},  // Device
        {111,113,73},  // Terminal #1
        {111,119,79},  // Terminal #2
        {89,112,92},   // Terminal #3
        {89,122,101},  // Terminal #4
        {106,124,113}, // Lever
        {94,124,113},  // Lever
    };
    // Phase 1
    private static final int[][] PHASE1 = {
        {60,132,143},  // Device
        {68,109,121},  // Terminal #1
        {59,120,122},  // Terminal #2
        {47,109,121},  // Terminal #3
        {40,124,122},  // Terminal #4
        {39,108,143},  // Terminal #5
        {27,124,127},  // Lever
        {23,132,138},  // Lever
    };
    // Phase 2
    private static final int[][] PHASE2 = {
        {0,120,77},    // Device
        {-3,109,112},  // Terminal #1
        {-3,119,93},   // Terminal #2
        {19,123,93},   // Terminal #3
        {-3,109,77},   // Terminal #4
        {14,122,55},   // Lever
        {2,122,55},    // Lever
    };
    // Phase 3
    private static final int[][] PHASE3 = {
        {63,127,35},   // Device
        {41,109,29},   // Terminal #1
        {59,121,29},   // Terminal #2
        {77,109,29},   // Terminal #3
        {95,121,29},   // Terminal #4
        {69,126,58},   // Lever
        {57,126,58},   // Lever
    };

    public static void draw(WorldRenderer.Ctx ctx) {
        OrionConfig cfg = ConstellationClient.cfg().orion;
        if (cfg == null || !cfg.terminalSolvers) return;
        if (!ConstellationClient.loc().inDungeons()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;

        // pick the closest phase to the player — each phase has a distinct X/Z range
        var pp = mc.player.position();
        int[][] positions = null;
        if (pp.x > 50) positions = PHASE0;
        else if (pp.x > 20) positions = PHASE1;
        else if (pp.x > -10) positions = PHASE2;
        else positions = PHASE3;
        if (positions == null) return;

        for (int[] p : positions) {
            double x = p[0], y = p[1], z = p[2];
            // offset: the waypoints are relative to the boss room, but the player position
            // gives us the anchor. render relative to player for through-walls visibility.
            ctx.highlight(new AABB(x - 0.5, y - 0.5, z - 0.5, x + 0.5, y + 0.5, z + 0.5),
                0x40FFFF00, true);
            ctx.beam(x, y + 2, z, 0xFFFFFF00, 8, true);
            ctx.label(new Vec3(x, y + 1.5, z), "Term", 0xFFFFFF00, true);
        }
    }
}
