package com.froggylord.constellation.constellation;

import com.froggylord.constellation.ConstellationClient;
import com.froggylord.constellation.config.OrionConfig;
import com.froggylord.constellation.data.DungeonData;
import com.froggylord.constellation.data.RoomMatch;
import com.froggylord.constellation.data.RoomTransform;
import com.froggylord.constellation.render.WorldRenderer;
import com.froggylord.constellation.render.ConstellationTheme;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ambient.Bat;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.*;

public final class SecretWaypoints {

    private static final double SIGNAL_RANGE = 6.0;

    private static String room = "";
    private static int anchorX, anchorZ;
    private static RoomTransform.Direction dir = RoomTransform.Direction.NW;
    private static final List<Wp> wps = new ArrayList<>();
    private static final Set<Integer> collected = new HashSet<>();
    private static final Set<Integer> observedEntities = new HashSet<>();
    private static boolean announcedDone;
    private static Wp lastCollectedInteraction;
    private static long lastCollectedInteractionAt;
    private static boolean initialized;

    private record Wp(int idx, int x, int y, int z, String category, String name) {}

    private SecretWaypoints() {}

    public static int collectedCount() { return collected.size(); }
    public static int totalCount() { return wps.size(); }

    public static void init() {
        if (initialized) return;
        initialized = true;
        CustomDungeonWaypoints.init();
        UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
            Routes.onRecordingBlockInteraction(hit.getBlockPos(), level.getBlockState(hit.getBlockPos()).getBlock());
            if (isActive()) markInteraction(hit.getBlockPos());
            return InteractionResult.PASS;
        });
        ConstellationClient.tick().every(2, "orion-secret-signals", SecretWaypoints::tickSignals);
    }

    public static void onChat(String message) {
        if (!isActive()) return;
        String text = message.toLowerCase(Locale.ROOT);
        if (text.contains("that chest is locked")) {
            rollbackLockedInteraction();
            return;
        }
        if ((text.contains("found") && text.contains("secret"))
            || text.contains("picked up") && text.contains("essence")) markNearest(SIGNAL_RANGE);
    }

    public static void draw(WorldRenderer.Ctx ctx) {
        OrionConfig cfg = ConstellationClient.cfg().orion;
        if (cfg == null || !cfg.secretWaypoints) return;
        if (!ConstellationClient.loc().inDungeons() || !RoomMatch.isMatched()) { room = ""; wps.clear(); return; }

        sync();
        if (cfg.customWaypoints) CustomDungeonWaypoints.draw(ctx);
        if (cfg.routes && Routes.hasUsableRoute(ConstellationClient.dungeon().currentRoom())) return;
        if (wps.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        Vec3 pp = mc.player.position();

        int revealThrough = Integer.MAX_VALUE;
        if (cfg.oneSecretAtATime) {
            revealThrough = wps.stream().filter(w -> !collected.contains(w.idx()))
                .mapToInt(Wp::idx).min().orElse(-1);
        } else if (cfg.progressiveReveal) {
            revealThrough = collected.stream().mapToInt(Integer::intValue).max().orElse(-1) + 1;
        }

        for (Wp w : wps) {
            if (collected.contains(w.idx())) continue;
            if (w.idx() > revealThrough) continue;
            int colour = colourFor(w.category());
            AABB box = new AABB(w.x(), w.y(), w.z(), w.x() + 1, w.y() + 1, w.z() + 1);
            ctx.highlight(box, colour, true);
            if (cfg.secretBeams) ctx.beam(w.x() + .5, w.y() + 1, w.z() + .5, 0xB0000000 | (colour & 0xFFFFFF), 8, true);
            double metres = Math.sqrt(dist2(pp, w));
            ctx.label(new Vec3(w.x() + .5, w.y() + 1.45, w.z() + .5),
                labelFor(w) + "  " + Math.round(metres) + "m", colour, true);
        }
    }

    private static void sync() {
        String cur = ConstellationClient.dungeon().currentRoom();
        int ax = ConstellationClient.dungeon().roomCornerX(), az = ConstellationClient.dungeon().roomCornerZ();
        RoomTransform.Direction d = ConstellationClient.dungeon().roomDirection();
        if (cur.equals(room) && ax == anchorX && az == anchorZ && d == dir) return;

        room = cur; anchorX = ax; anchorZ = az; dir = d;
        wps.clear(); collected.clear(); observedEntities.clear(); announcedDone = false;
        lastCollectedInteraction = null; lastCollectedInteractionAt = 0;
        List<DungeonData.SecretWaypoint> secrets = DungeonData.secretsFor(
            cur, dir, anchorX, anchorZ, RoomMatch.floorY());
        int i = 0;
        for (DungeonData.SecretWaypoint s : secrets)
            wps.add(new Wp(i++, s.x(), s.y(), s.z(), s.category(), s.name()));
    }

    private static boolean isActive() {
        OrionConfig cfg = ConstellationClient.cfg().orion;
        return cfg != null && cfg.enabled && (cfg.secretWaypoints || cfg.routes)
            && ConstellationClient.loc().inDungeons() && RoomMatch.isMatched();
    }

    private static void markInteraction(BlockPos pos) {
        sync();
        Wp best = null;
        double distance = 9.0;
        for (Wp w : wps) {
            if (collected.contains(w.idx()) || !isInteraction(w.category())) continue;
            double d = pos.distToCenterSqr(w.x() + .5, w.y() + .5, w.z() + .5);
            if (d < distance) { distance = d; best = w; }
        }
        if (best != null) collect(best);
    }

    private static void tickSignals() {
        if (!isActive()) return;
        sync();
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;
        Set<Integer> visible = new HashSet<>();
        for (Wp w : wps) {
            if (collected.contains(w.idx()) || !isEntitySecret(w.category())) continue;
            AABB area = new AABB(w.x() - 2, w.y() - 2, w.z() - 2, w.x() + 3, w.y() + 3, w.z() + 3);
            for (Entity entity : mc.level.getEntities((Entity) null, area, e -> matchesEntity(w, e))) {
                visible.add(w.idx());
                break;
            }
            if (observedEntities.contains(w.idx()) && !visible.contains(w.idx())
                && dist2(mc.player.position(), w) <= SIGNAL_RANGE * SIGNAL_RANGE) {
                Routes.onRecordingSecretSignal(w.x(), w.y(), w.z(), w.category());
                collect(w);
            }
        }
        observedEntities.clear();
        observedEntities.addAll(visible);
    }

    private static boolean matchesEntity(Wp w, Entity entity) {
        return w.category().equalsIgnoreCase("bat") ? entity instanceof Bat : entity instanceof ItemEntity;
    }

    private static void markNearest(double range) {
        sync();
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        Wp best = null;
        double distance = range * range;
        for (Wp w : wps) {
            double d = dist2(mc.player.position(), w);
            if (!collected.contains(w.idx()) && d < distance) { distance = d; best = w; }
        }
        if (best != null) collect(best);
    }

    private static void collect(Wp waypoint) {
        if (!collected.add(waypoint.idx())) return;
        Routes.onSecretCollected(waypoint.x(), waypoint.y(), waypoint.z(), waypoint.category());
        if (isInteraction(waypoint.category())) {
            lastCollectedInteraction = waypoint;
            lastCollectedInteractionAt = System.currentTimeMillis();
        }
        Minecraft mc = Minecraft.getInstance();
        if (ConstellationClient.cfg().orion.echoOnCollect && mc.player != null)
            mc.player.playSound(SoundEvents.NOTE_BLOCK_PLING.value(), 0.6f, 1.6f);
        secretsDoneAlert(mc);
    }

    // ported from SecretRoutes (GPL-3.0): events/OnChatReceive.java (locked chest rollback)
    private static void rollbackLockedInteraction() {
        Wp waypoint = lastCollectedInteraction;
        if (waypoint == null || System.currentTimeMillis() - lastCollectedInteractionAt > 3000) return;
        if (collected.remove(waypoint.idx()))
            Routes.onSecretFailed(waypoint.x(), waypoint.y(), waypoint.z(), waypoint.category());
        Routes.onRecordingSecretFailed(waypoint.x(), waypoint.y(), waypoint.z());
        lastCollectedInteraction = null;
        lastCollectedInteractionAt = 0;
        announcedDone = false;
    }

    // ported from devonian (GPL-3.0): features/dungeons/clear/CurrentRoomCleared.kt (secrets-done transition alert)
    private static void secretsDoneAlert(Minecraft mc) {
        if (announcedDone || wps.isEmpty() || collected.size() < wps.size()) return;
        if (!ConstellationClient.cfg().orion.secretsDoneAlert || mc.player == null) return;
        announcedDone = true;
        mc.gui.hud.resetTitleTimes();
        mc.gui.hud.setTitle(Component.literal("§bSecrets Done"));
    }

    private static boolean isInteraction(String category) {
        String value = category.toLowerCase(Locale.ROOT);
        return value.equals("chest") || value.equals("lever") || value.equals("wither")
            || value.equals("wither_essence") || value.equals("superboom");
    }

    private static boolean isEntitySecret(String category) {
        return category.equalsIgnoreCase("item") || category.equalsIgnoreCase("bat");
    }

    private static String labelFor(Wp w) {
        if (w.name() != null && !w.name().isBlank()) return w.name();
        String category = w.category() == null || w.category().isBlank() ? "Secret" : w.category();
        return category.substring(0, 1).toUpperCase(Locale.ROOT) + category.substring(1).replace('_', ' ');
    }

    private static double dist2(Vec3 p, Wp w) {
        double dx = p.x - (w.x() + 0.5), dy = p.y - (w.y() + 0.5), dz = p.z - (w.z() + 0.5);
        return dx * dx + dy * dy + dz * dz;
    }

    
    private static int colourFor(String cat) {
        if (cat == null) return 0xFFFFFF55;
        return switch (cat.toLowerCase(Locale.ROOT)) {
            case "chest" -> ConstellationTheme.ACCENT;
            case "item" -> ConstellationTheme.AQUA;
            case "bat" -> 0xFF8B7CFF;
            case "wither", "wither_essence", "lever" -> 0xFFB96CFF;
            case "fairysoul", "fairy" -> 0xFFFF79C6;
            case "superboom", "superboom-tnt" -> ConstellationTheme.RED;
            case "entrance" -> ConstellationTheme.TEXT;
            case "stonk", "pearl", "aotv" -> ConstellationTheme.GREEN;
            default -> ConstellationTheme.ACCENT_BRIGHT;
        };
    }
}
