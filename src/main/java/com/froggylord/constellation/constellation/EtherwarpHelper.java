package com.froggylord.constellation.constellation;

import com.froggylord.constellation.ConstellationClient;
import com.froggylord.constellation.config.OrionConfig;
import com.froggylord.constellation.render.WorldRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

// advisory etherwarp destination box. NEVER warps or clicks — render only.
// ported from Odin (BSD-3): src/main/kotlin/com/odtheking/odin/features/impl/render/Etherwarp.kt
// (traverseVoxels DDA + top-adjacent clearance check, @author Bloom)
public final class EtherwarpHelper {

    private EtherwarpHelper() {}

    // base etherwarp range in blocks (before tuned_transmission upgrades)
    private static final double RANGE = 57.0;

    private static final int GREEN = 0x6033FF33; // valid destination
    private static final int RED   = 0x60FF3333; // not enough headroom

    // the resolved target of one raycast
    private record Target(BlockPos pos, VoxelShape shape, boolean valid) {}

    public static void draw(WorldRenderer.Ctx ctx) {
        OrionConfig cfg = ConstellationClient.cfg().orion;
        if (cfg == null || !cfg.etherwarpHelper) return;

        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        Level level = mc.level;
        if (player == null || level == null) return;

        // gate: only while sneaking and holding an etherwarp-capable item, matching the real cast
        if (!player.isShiftKeyDown()) return;
        ItemStack held = player.getMainHandItem();
        if (held.isEmpty()) return;
        String name = held.getHoverName().getString();
        if (!name.contains("Aspect of the Void")
                && !name.contains("Aspect of the End")
                && !name.contains("Etherwarp Conduit")) return;

        Vec3 start = player.getEyePosition();
        Vec3 end = start.add(player.getViewVector(1.0f).scale(RANGE));

        Target target = traverse(start, end, level);
        if (target == null) return; // no solid block within range — nothing to box

        AABB box = target.shape.isEmpty()
                ? new AABB(target.pos)
                : target.shape.bounds().move(target.pos);
        ctx.highlight(box, target.valid ? GREEN : RED, true);
    }

    // DDA voxel walk from start to end; returns the first solid block hit and whether
    // the top-adjacent spot has two blocks of clearance for the player to stand.
    private static Target traverse(Vec3 start, Vec3 end, Level level) {
        double x0 = start.x, y0 = start.y, z0 = start.z;
        double x1 = end.x, y1 = end.y, z1 = end.z;

        int x = Mth.floor(x0), y = Mth.floor(y0), z = Mth.floor(z0);
        int endX = Mth.floor(x1), endY = Mth.floor(y1), endZ = Mth.floor(z1);

        double dirX = x1 - x0, dirY = y1 - y0, dirZ = z1 - z0;
        int stepX = (int) Math.signum(dirX);
        int stepY = (int) Math.signum(dirY);
        int stepZ = (int) Math.signum(dirZ);

        double invDirX = dirX != 0.0 ? 1.0 / dirX : Double.MAX_VALUE;
        double invDirY = dirY != 0.0 ? 1.0 / dirY : Double.MAX_VALUE;
        double invDirZ = dirZ != 0.0 ? 1.0 / dirZ : Double.MAX_VALUE;

        double tDeltaX = Math.abs(invDirX * stepX);
        double tDeltaY = Math.abs(invDirY * stepY);
        double tDeltaZ = Math.abs(invDirZ * stepZ);

        double tMaxX = Math.abs((x + Math.max(stepX, 0) - x0) * invDirX);
        double tMaxY = Math.abs((y + Math.max(stepY, 0) - y0) * invDirY);
        double tMaxZ = Math.abs((z + Math.max(stepZ, 0) - z0) * invDirZ);

        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int i = 0; i < 1000; i++) {
            cursor.set(x, y, z);
            BlockState state = level.getBlockState(cursor);
            if (!state.isAir()) {
                VoxelShape shape = state.getCollisionShape(level, cursor);
                if (!shape.isEmpty()) {
                    // solid hit — this block is the etherwarp landing target
                    BlockPos hit = cursor.immutable();
                    boolean valid = hasClearance(level, hit, shape);
                    return new Target(hit, shape, valid);
                }
                // passable non-air (torch, grass, ...) — the ray keeps going
            }

            if (x == endX && y == endY && z == endZ) return null;

            if (tMaxX <= tMaxY && tMaxX <= tMaxZ) { tMaxX += tDeltaX; x += stepX; }
            else if (tMaxY <= tMaxZ) { tMaxY += tDeltaY; y += stepY; }
            else { tMaxZ += tDeltaZ; z += stepZ; }
        }
        return null;
    }

    // valid destination = feet + head blocks above the standing surface are both non-colliding
    private static boolean hasClearance(Level level, BlockPos hit, VoxelShape shape) {
        double collisionTop = shape.max(Direction.Axis.Y);
        int baseY = hit.getY() + Math.max(1, Mth.ceil(collisionTop));

        BlockPos feet = new BlockPos(hit.getX(), baseY, hit.getZ());
        BlockPos head = feet.above();
        return level.getBlockState(feet).getCollisionShape(level, feet).isEmpty()
                && level.getBlockState(head).getCollisionShape(level, head).isEmpty();
    }
}
