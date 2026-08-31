package com.froggylord.constellation.constellation;

import com.froggylord.constellation.ConstellationClient;
import com.froggylord.constellation.config.OrionConfig;
import com.froggylord.constellation.render.WorldRenderer;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundLevelParticlesPacket;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;

// ported from Odin (BSD-3-Clause): features/impl/dungeon/MageBeam.kt
// particle suppression cross-checked with NoFrills (GPL-3.0): features/general/NoRender.java
public final class MageBeamHelper {
    private static final List<Beam> beams = new ArrayList<>();
    private static boolean initialized;
    private static ClientLevel levelIdentity;

    private static final class Beam {
        final List<Vec3> points = new ArrayList<>();
        long lastUpdateTick;
        long expiresTick;

        Beam(Vec3 first, long tick, int duration) {
            points.add(first);
            lastUpdateTick = tick;
            expiresTick = tick + duration;
        }
    }

    private MageBeamHelper() {}

    public static void init() {
        if (initialized) return;
        initialized = true;
        ConstellationClient.tick().every(1, "orion-mage-beam", MageBeamHelper::tick);
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> reset());
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> reset());
    }

    public static boolean onParticle(ClientboundLevelParticlesPacket packet) {
        Minecraft mc = Minecraft.getInstance();
        if (!mc.isSameThread() || mc.level == null) return false;
        ensureLevel(mc.level);
        OrionConfig cfg = config();
        if (cfg == null || !cfg.enabled || !cfg.mageBeamCleaner || !ConstellationClient.loc().inDungeons()
            || packet.getParticle().getType() != ParticleTypes.FIREWORK) return false;
        long tick = mc.level.getGameTime();
        Vec3 point = new Vec3(packet.getX(), packet.getY(), packet.getZ());
        Beam recent = beams.isEmpty() ? null : beams.getLast();
        if (recent != null && tick - recent.lastUpdateTick < 1 && followsDirection(recent.points, point)) {
            recent.points.add(point);
            recent.lastUpdateTick = tick;
            recent.expiresTick = tick + Math.clamp(cfg.mageBeamDurationTicks, 1, 100);
        } else {
            beams.add(new Beam(point, tick, Math.clamp(cfg.mageBeamDurationTicks, 1, 100)));
        }
        return cfg.mageBeamHideParticles;
    }

    public static void draw(WorldRenderer.Ctx ctx) {
        OrionConfig cfg = config();
        if (cfg == null || !cfg.enabled || !cfg.mageBeamCleaner || !ConstellationClient.loc().inDungeons()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        int minimum = Math.clamp(cfg.mageBeamMinPoints, 2, 10);
        Vec3 player = mc.player.position();
        for (Beam beam : beams) {
            if (beam.points.size() < minimum) continue;
            Vec3 closest = beam.points.getFirst();
            Vec3 furthest = closest;
            double minimumDistance = closest.distanceToSqr(player);
            double maximumDistance = minimumDistance;
            for (int i = 1; i < beam.points.size(); i++) {
                Vec3 point = beam.points.get(i);
                double distance = point.distanceToSqr(player);
                if (distance < minimumDistance) { minimumDistance = distance; closest = point; }
                if (distance > maximumDistance) { maximumDistance = distance; furthest = point; }
            }
            if (!closest.equals(furthest))
                ctx.line(closest, furthest, cfg.mageBeamColour, !cfg.mageBeamDepthCheck);
        }
    }

    public static void registerCommands(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        dispatcher.register(LiteralArgumentBuilder.<FabricClientCommandSource>literal("magebeam")
            .executes(ctx -> status())
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("status").executes(ctx -> status()))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("duration")
                .then(RequiredArgumentBuilder.<FabricClientCommandSource, Integer>argument("ticks", IntegerArgumentType.integer(1, 100))
                    .executes(ctx -> duration(IntegerArgumentType.getInteger(ctx, "ticks")))))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("points")
                .then(RequiredArgumentBuilder.<FabricClientCommandSource, Integer>argument("minimum", IntegerArgumentType.integer(2, 10))
                    .executes(ctx -> points(IntegerArgumentType.getInteger(ctx, "minimum")))))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("color")
                .then(RequiredArgumentBuilder.<FabricClientCommandSource, String>argument("argb", StringArgumentType.word())
                    .executes(ctx -> colour(StringArgumentType.getString(ctx, "argb"))))));
    }

    private static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) { reset(); return; }
        ensureLevel(mc.level);
        OrionConfig cfg = config();
        if (cfg == null || !cfg.enabled || !cfg.mageBeamCleaner || !ConstellationClient.loc().inDungeons()) {
            beams.clear();
            return;
        }
        long tick = mc.level.getGameTime();
        for (Iterator<Beam> iterator = beams.iterator(); iterator.hasNext();) {
            if (tick >= iterator.next().expiresTick) iterator.remove();
        }
    }

    private static boolean followsDirection(List<Vec3> points, Vec3 next) {
        if (points.size() <= 1) return true;
        Vec3 last = points.getLast();
        Vec3 existing = last.subtract(points.getFirst());
        Vec3 incoming = next.subtract(last);
        if (existing.lengthSqr() == 0 || incoming.lengthSqr() == 0) return false;
        return existing.normalize().dot(incoming.normalize()) > 0.99;
    }

    private static void ensureLevel(ClientLevel level) {
        if (levelIdentity == level) return;
        beams.clear();
        levelIdentity = level;
    }

    private static void reset() {
        beams.clear();
        levelIdentity = null;
    }

    private static int status() {
        OrionConfig cfg = config();
        if (cfg == null) return 0;
        local((cfg.mageBeamCleaner ? "on" : "off") + ", duration " + cfg.mageBeamDurationTicks
            + " ticks, minimum " + cfg.mageBeamMinPoints + " points, color "
            + String.format(Locale.ROOT, "%08X", cfg.mageBeamColour));
        return 1;
    }

    private static int duration(int value) {
        OrionConfig cfg = config();
        if (cfg == null) return 0;
        cfg.mageBeamDurationTicks = value;
        ConstellationClient.saveConfig();
        local("duration set to " + value + " ticks");
        return 1;
    }

    private static int points(int value) {
        OrionConfig cfg = config();
        if (cfg == null) return 0;
        cfg.mageBeamMinPoints = value;
        ConstellationClient.saveConfig();
        local("minimum set to " + value + " points");
        return 1;
    }

    private static int colour(String raw) {
        OrionConfig cfg = config();
        if (cfg == null) return 0;
        try {
            String hex = raw.startsWith("#") ? raw.substring(1) : raw;
            long parsed = Long.parseUnsignedLong(hex, 16);
            if (hex.length() == 6) parsed |= 0xFF000000L;
            if (hex.length() != 6 && hex.length() != 8) throw new NumberFormatException();
            cfg.mageBeamColour = (int) parsed;
            ConstellationClient.saveConfig();
            local("color set to " + String.format(Locale.ROOT, "%08X", cfg.mageBeamColour));
            return 1;
        } catch (NumberFormatException ignored) {
            local("use RRGGBB or AARRGGBB");
            return 0;
        }
    }

    private static OrionConfig config() {
        return ConstellationClient.cfg() == null ? null : ConstellationClient.cfg().orion;
    }

    private static void local(String text) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) mc.player.sendSystemMessage(Component.literal("§bMage Beam §8> §f" + text));
    }
}
