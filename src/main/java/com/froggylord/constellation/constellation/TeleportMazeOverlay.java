package com.froggylord.constellation.constellation;

import com.froggylord.constellation.ConstellationClient;
import com.froggylord.constellation.data.RoomMatch;
import com.froggylord.constellation.data.RoomTransform;
import com.froggylord.constellation.network.PlayerPositionUpdate;
import com.froggylord.constellation.render.WorldRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

// ported from Odin (BSD-3-Clause): features/impl/dungeon/puzzlesolvers/TPMazeSolver.kt
// cross-checked with devonian (GPL-3.0): features/dungeons/solvers/TeleportMazeSolver.kt
public final class TeleportMazeOverlay {
    private static final int[][] PAD_COORDS = {
        {4,69,12},{4,69,6},{10,69,12},{10,69,6},
        {4,69,20},{4,69,14},{10,69,20},{10,69,14},
        {4,69,28},{4,69,22},{10,69,28},{10,69,22},
        {12,69,28},{12,69,22},{18,69,28},{18,69,22},
        {20,69,28},{20,69,22},{26,69,28},{26,69,22},
        {26,69,20},{26,69,14},{20,69,20},{20,69,14},
        {26,69,12},{26,69,6},{20,69,12},{20,69,6},
        {15,69,14},{15,69,12}
    };

    private static final List<BlockPos> pads = new ArrayList<>();
    private static final Set<BlockPos> correct = new HashSet<>();
    private static final Set<BlockPos> visited = new HashSet<>();
    private static BlockPos best;
    private static String roomKey = "";
    private static boolean initialized;

    private TeleportMazeOverlay() {}

    public static void init() {
        if (initialized) return;
        initialized = true;
        ConstellationClient.instance().packets().register(packet -> {
            if (packet instanceof PlayerPositionUpdate update) onPosition(update);
        });
    }

    public static void draw(WorldRenderer.Ctx ctx) {
        if (!RoomMatch.isMatched() || !RoomMatch.currentRoom().contains("teleport-pad-room")) {
            reset();
            return;
        }
        var cfg = ConstellationClient.cfg().orion;
        if (cfg == null || !cfg.teleportMazeSolver || !ConstellationClient.loc().inDungeons()) return;
        ensurePads();

        for (BlockPos pad : pads) {
            int colour;
            boolean throughWalls;
            if (correct.contains(pad)) {
                colour = correct.size() == 1 ? 0xFF55FF55 : 0xFFFFAA00;
                throughWalls = false;
            } else if (visited.contains(pad)) {
                colour = 0xFFFF5555;
                throughWalls = true;
            } else {
                colour = 0x80FFFFFF;
                throughWalls = true;
            }
            ctx.highlight(box(pad), colour, throughWalls);
        }

        Minecraft mc = Minecraft.getInstance();
        if (best != null && mc.player != null)
            ctx.line(mc.player.getEyePosition(), Vec3.atCenterOf(best).add(0, .3, 0), 0xFF55FF55, false);
    }

    private static void onPosition(PlayerPositionUpdate update) {
        if (!RoomMatch.isMatched() || !RoomMatch.currentRoom().contains("teleport-pad-room")) return;
        var cfg = ConstellationClient.cfg().orion;
        if (cfg == null || !cfg.teleportMazeSolver || !ConstellationClient.loc().inDungeons()) return;
        ClientboundPlayerPositionPacket packet = update.packet();
        if (!packet.relatives().isEmpty()) return;
        Vec3 pos = packet.change().position();
        if (pos.x % .5 != 0 || pos.y != 69.5 || pos.z % .5 != 0) return;
        ensurePads();
        if (pads.isEmpty()) return;

        AABB landing = AABB.unitCubeFromLowerCorner(pos).inflate(1, 0, 1);
        AABB oldPlayer = AABB.ofSize(update.before(), .6, 1.8, .6).inflate(1, 0, 1);
        for (BlockPos pad : pads) {
            if (landing.intersects(box(pad)) || oldPlayer.intersects(box(pad))) visited.add(pad);
        }

        filterCorrect(pos, packet.change().yRot(), packet.change().xRot());
        BlockPos current = pads.stream().filter(pad -> landing.intersects(box(pad))).findFirst().orElse(null);
        if (current == null) return;
        int index = pads.indexOf(current);
        if (index >= 28) {
            best = null;
            return;
        }
        int groupStart = index / 4 * 4;
        if (groupStart + 4 > pads.size()) return;

        List<BlockPos> candidates = pads.subList(groupStart, groupStart + 4).stream()
            .filter(pad -> !pad.equals(current) && !visited.contains(pad)).toList();
        best = candidates.stream().filter(correct::contains).findFirst().orElseGet(() ->
            candidates.stream().min((a, b) -> Double.compare(yawDistance(pos, packet.change().yRot(), a),
                yawDistance(pos, packet.change().yRot(), b))).orElse(null));
    }

    private static void filterCorrect(Vec3 pos, float yaw, float pitch) {
        if (correct.isEmpty()) correct.addAll(pads);
        Minecraft mc = Minecraft.getInstance();
        AABB playerBox = mc.player == null ? null : mc.player.getBoundingBox();
        correct.removeIf(pad -> visited.contains(pad)
            || !isXZInterceptable(box(pad).inflate(.75, 0, .75).expandTowards(0, 3, 0), 32, pos, yaw, pitch)
            || playerBox != null && box(pad).inflate(.5, 0, .5).intersects(playerBox));
    }

    // ported from Odin (BSD-3-Clause): utils/VecUtils.kt (isXZInterceptable/getLook)
    private static boolean isXZInterceptable(AABB target, double range, Vec3 pos, float yaw, float pitch) {
        double yawRad = -yaw * 0.017453292F - Math.PI;
        double pitchRad = -pitch * 0.017453292F;
        double factor = -Math.cos(pitchRad);
        Vec3 look = new Vec3(Math.sin(yawRad) * factor, Math.sin(pitchRad), Math.cos(yawRad) * factor);
        Vec3 start = pos.add(0, 1.62, 0);
        Vec3 goal = start.add(look.scale(range));
        return intersectsXZ(start, goal, target.minX, true, target)
            || intersectsXZ(start, goal, target.maxX, true, target)
            || intersectsXZ(start, goal, target.minZ, false, target)
            || intersectsXZ(start, goal, target.maxZ, false, target);
    }

    private static boolean intersectsXZ(Vec3 start, Vec3 goal, double value, boolean xAxis, AABB box) {
        double delta = xAxis ? goal.x - start.x : goal.z - start.z;
        if (delta * delta < 1.0E-8) return false;
        double origin = xAxis ? start.x : start.z;
        double t = (value - origin) / delta;
        if (t < 0 || t > 1) return false;
        double x = start.x + (goal.x - start.x) * t;
        double z = start.z + (goal.z - start.z) * t;
        return xAxis ? z >= box.minZ && z <= box.maxZ : x >= box.minX && x <= box.maxX;
    }

    private static double yawDistance(Vec3 pos, float yaw, BlockPos pad) {
        Vec3 centre = Vec3.atCenterOf(pad);
        double targetYaw = Math.toDegrees(Math.atan2(centre.z - pos.z, centre.x - pos.x)) - 90;
        return Math.abs(Mth.wrapDegrees(targetYaw) - Mth.wrapDegrees(yaw));
    }

    private static void ensurePads() {
        String key = RoomMatch.currentRoom() + ':' + RoomMatch.anchorX() + ':' + RoomMatch.anchorZ() + ':' + RoomMatch.currentDir();
        if (key.equals(roomKey)) return;
        reset();
        roomKey = key;
        for (int[] pad : PAD_COORDS) pads.add(worldPos(pad[0], pad[1], pad[2]));
    }

    private static BlockPos worldPos(int x, int y, int z) {
        long[] world = RoomTransform.relativeToActual(RoomMatch.currentDir(), RoomMatch.anchorX(), RoomMatch.anchorZ(), x, y, z);
        return new BlockPos((int) world[0], (int) world[1], (int) world[2]);
    }

    private static AABB box(BlockPos pos) {
        return new AABB(pos.getX(), pos.getY(), pos.getZ(), pos.getX() + 1, pos.getY() + 1, pos.getZ() + 1);
    }

    private static void reset() {
        pads.clear();
        correct.clear();
        visited.clear();
        best = null;
        roomKey = "";
    }
}
