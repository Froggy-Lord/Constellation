package com.froggylord.constellation.constellation;

import com.froggylord.constellation.ConstellationClient;
import com.froggylord.constellation.config.OrionConfig;
import com.froggylord.constellation.render.WorldRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Paints a beam over wither/blood keys on the floor (through walls so you never walk past one)
 * and highlights the actual door once it's detected. Keys are spotted from the chat pickup line;
 * the nearest door block is found by scanning the world around the key pickup point.
 */
public final class DoorHighlighter {

    private DoorHighlighter() {}

    private static long lastKeyAt = 0;
    private static Vec3 keyPos = null;
    private static boolean hasWitherKey = false;
    private static boolean hasBloodKey = false;

    private static BlockPos doorPos = null;
    private static int doorColour = 0xFFFF3333; // red = no key yet

    public static void init() {
        net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents.GAME.register((msg, overlay) -> {
            if (overlay || !ConstellationClient.loc().inDungeons()) return;
            String s = msg.getString();
            if (s.contains("Wither Key") || s.contains("Blood Key")) {
                if (s.contains("picked up")) {
                    var mc = Minecraft.getInstance();
                    if (mc.player != null) {
                        keyPos = mc.player.position();
                        lastKeyAt = System.currentTimeMillis();
                        if (s.contains("Wither")) hasWitherKey = true;
                        else hasBloodKey = true;
                        // scan for the nearest door block
                        doorPos = findDoorNear(mc.player.blockPosition(), s.contains("Wither"));
                    }
                }
            }
        });
    }

    private static BlockPos findDoorNear(BlockPos center, boolean wither) {
        var mc = Minecraft.getInstance();
        if (mc.level == null) return null;
        BlockPos best = null;
        double bestDist = 64; // max scan radius
        for (int dx = -40; dx <= 40; dx += 4) {
            for (int dz = -40; dz <= 40; dz += 4) {
                for (int dy = -5; dy <= 5; dy++) {
                    BlockPos bp = center.offset(dx, dy, dz);
                    var bs = mc.level.getBlockState(bp);
                    String id = bs.getBlock().getDescriptionId();
                    if (wither && id.contains("coal_block")) {
                        // check for wither skull above
                        var above = mc.level.getBlockState(bp.above());
                        if (above.getBlock().getDescriptionId().contains("skull")) {
                            double d = bp.distSqr(center);
                            if (d < bestDist) { bestDist = d; best = bp; }
                        }
                    } else if (!wither && (id.contains("red_glazed") || id.contains("red_concrete"))) {
                        double d = bp.distSqr(center);
                        if (d < bestDist) { bestDist = d; best = bp; }
                    }
                }
            }
        }
        return best;
    }

    public static void draw(WorldRenderer.Ctx ctx) {
        OrionConfig cfg = ConstellationClient.cfg().orion;
        if (cfg == null || !cfg.doorTracker) return;
        if (!ConstellationClient.loc().inDungeons()) return;

        // key beam — glow the key's last known position for ~10s
        if (keyPos != null && System.currentTimeMillis() - lastKeyAt < 12_000) {
            ctx.beam(keyPos.x, keyPos.y, keyPos.z, 0xFFFFFF00, 4, true);
        }

        // door highlight — show the door red (needs key) or green (key held)
        if (doorPos != null) {
            boolean hasKey = hasWitherKey || hasBloodKey;
            int colour = hasKey ? 0xFF33FF33 : 0xFFFF3333;
            AABB box = new AABB(doorPos.getX(), doorPos.getY(), doorPos.getZ(),
                doorPos.getX() + 2, doorPos.getY() + 3, doorPos.getZ() + 2);
            ctx.highlight(box, hasKey ? 0x4033FF33 : 0x80FF3333, true);
            ctx.label(new Vec3(doorPos.getX() + 1, doorPos.getY() + 2.5, doorPos.getZ() + 1),
                hasKey ? "UNLOCK" : "NEED KEY", colour, true);
        }
    }
}
