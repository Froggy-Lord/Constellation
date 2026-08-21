package com.froggylord.constellation.constellation;

import com.froggylord.constellation.render.WorldRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

// ported from Skyblocker (LGPL-3): skyblock/dungeon/puzzle/CreeperBeams.java
// the creeper sits on a sea-lantern base; the 5 correct beams are the target
// pairs whose connecting line passes closest to the creeper. greedily pick the
// 5 nearest lines, never reusing a target block.
public final class CreeperBeamsSolver {

    private CreeperBeamsSolver() {}

    // creeper-room puzzle sits on a fixed y band in the catacombs
    private static final int FLOOR_Y = 68;
    private static final int BASE_Y = 74;

    private static final int[] BEAM_COLOURS = {
        0xFF3FC9FF, // light blue
        0xFF55FF55, // lime
        0xFFFFFF55, // yellow
        0xFFFF55FF, // magenta
        0xFFFF9FCF, // pink
    };
    private static final int GREEN_DONE = 0xFF33FF33;

    private static final List<Beam> beams = new ArrayList<>();
    private static BlockPos base = null;

    public static void reset() {
        beams.clear();
        base = null;
    }

    public static void draw(WorldRenderer.Ctx ctx) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            reset();
            return;
        }

        if (base == null) {
            base = findCreeperBase(mc);
            if (base == null) return;
            Vec3 creeperPos = new Vec3(base.getX() + 0.5, BASE_Y + 1.75, base.getZ() + 0.5);
            List<BlockPos> targets = findTargets(mc.level, base);
            beams.clear();
            beams.addAll(findLines(creeperPos, targets));
        }

        // if the base block is gone / room reset, recompute next tick
        if (!isTarget(mc.level, base)) {
            reset();
            return;
        }

        for (int i = 0; i < beams.size(); i++) {
            beams.get(i).render(ctx, mc.level, BEAM_COLOURS[i % BEAM_COLOURS.length]);
        }
    }

    // the sea lantern directly under the creeper
    private static BlockPos findCreeperBase(Minecraft mc) {
        List<Creeper> creepers = mc.level.getEntitiesOfClass(
            Creeper.class,
            mc.player.getBoundingBox().inflate(50d),
            EntitySelector.ENTITY_STILL_ALIVE);
        if (creepers.isEmpty()) return null;
        for (Creeper ce : creepers) {
            Vec3 p = ce.position();
            BlockPos candidate = BlockPos.containing(p.x, BASE_Y, p.z);
            if (isTarget(mc.level, candidate)) return candidate;
        }
        return null;
    }

    // all sea lanterns (and the single prismarine) in the room volume
    private static List<BlockPos> findTargets(ClientLevel level, BlockPos basePos) {
        List<BlockPos> targets = new ArrayList<>();
        BlockPos start = new BlockPos(basePos.getX() - 15, BASE_Y + 12, basePos.getZ() - 15);
        BlockPos end = new BlockPos(basePos.getX() + 16, FLOOR_Y, basePos.getZ() + 16);
        for (BlockPos pos : BlockPos.betweenClosed(start, end)) {
            if (isTarget(level, pos)) targets.add(pos.immutable());
        }
        return targets;
    }

    // greedily pick the 5 lines closest to the creeper, never sharing a target
    private static List<Beam> findLines(Vec3 creeperPos, List<BlockPos> targets) {
        List<double[]> scored = new ArrayList<>(); // {i, j, dist}
        List<Beam> pool = new ArrayList<>();
        for (int i = 0; i < targets.size(); i++) {
            for (int j = i + 1; j < targets.size(); j++) {
                Beam beam = new Beam(targets.get(i), targets.get(j));
                double dist = pointLineDistance(creeperPos, beam.a, beam.b);
                scored.add(new double[]{pool.size(), dist});
                pool.add(beam);
            }
        }
        scored.sort(Comparator.comparingDouble(s -> s[1]));

        List<Beam> result = new ArrayList<>();
        boolean[] used = new boolean[targets.size()];
        for (double[] s : scored) {
            if (result.size() >= 5) break;
            Beam beam = pool.get((int) s[0]);
            if (sharesUsedTarget(beam, targets, used)) continue;
            result.add(beam);
            markUsed(beam, targets, used);
        }
        return result;
    }

    private static boolean sharesUsedTarget(Beam beam, List<BlockPos> targets, boolean[] used) {
        for (int k = 0; k < targets.size(); k++) {
            if (used[k] && (targets.get(k).equals(beam.p1) || targets.get(k).equals(beam.p2))) return true;
        }
        return false;
    }

    private static void markUsed(Beam beam, List<BlockPos> targets, boolean[] used) {
        for (int k = 0; k < targets.size(); k++) {
            if (targets.get(k).equals(beam.p1) || targets.get(k).equals(beam.p2)) used[k] = true;
        }
    }

    // distance from point c to the infinite line through a and b
    private static double pointLineDistance(Vec3 c, Vec3 a, Vec3 b) {
        Vec3 ab = b.subtract(a);
        Vec3 ac = c.subtract(a);
        double abLen = ab.length();
        if (abLen < 1.0e-6) return ac.length();
        return ab.cross(ac).length() / abLen;
    }

    private static boolean isTarget(ClientLevel level, BlockPos pos) {
        Block block = level.getBlockState(pos).getBlock();
        return block == Blocks.SEA_LANTERN || block == Blocks.PRISMARINE;
    }

    private static final class Beam {
        final BlockPos p1;
        final BlockPos p2;
        final Vec3 a;
        final Vec3 b;

        Beam(BlockPos p1, BlockPos p2) {
            this.p1 = p1;
            this.p2 = p2;
            this.a = new Vec3(p1.getX() + 0.5, p1.getY() + 0.5, p1.getZ() + 0.5);
            this.b = new Vec3(p2.getX() + 0.5, p2.getY() + 0.5, p2.getZ() + 0.5);
        }

        // a beam is done once both ends have been converted to prismarine
        boolean done(ClientLevel level) {
            return level.getBlockState(p1).getBlock() == Blocks.PRISMARINE
                && level.getBlockState(p2).getBlock() == Blocks.PRISMARINE;
        }

        void render(WorldRenderer.Ctx ctx, ClientLevel level, int colour) {
            boolean done = done(level);
            int col = done ? GREEN_DONE : colour;
            ctx.line(a, b, col, true);
            ctx.highlight(new AABB(p1), (col & 0x00FFFFFF) | 0x40000000, true);
            ctx.highlight(new AABB(p2), (col & 0x00FFFFFF) | 0x40000000, true);
        }
    }
}
