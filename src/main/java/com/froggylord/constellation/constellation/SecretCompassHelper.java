package com.froggylord.constellation.constellation;

import com.froggylord.constellation.ConstellationClient;
import com.froggylord.constellation.config.OrionConfig;
import com.froggylord.constellation.render.WorldRenderer;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundLevelParticlesPacket;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// algorithm ported from SkyHanni (LGPL-2.1): features/dungeon/DungeonSecretTrackerLocator.kt
// linear curve fitting ported from SkyHanni (LGPL-2.1): utils/PolynomialFitter.kt
public final class SecretCompassHelper {
    private static final Pattern DISTANCE = Pattern.compile(
        "There's a secret (?<distance>\\d+) blocks(?:.*? and (?<distance2>\\d+) blocks)?[^\\d]*!$");
    private static final String ITEM_ID = "SECRET_TRACKER";
    private static final List<Vec3> points = new ArrayList<>();
    private static OrionConfig cfg;
    private static long lastUse;
    private static long lastParticle;
    private static Integer secretDistance;
    private static Vec3 target;
    private static boolean initialized;

    private SecretCompassHelper() {}

    public static void init(OrionConfig config) {
        cfg = config;
        if (initialized) return;
        initialized = true;
        ConstellationClient.instance().packets().register(packet -> {
            if (packet instanceof ClientboundLevelParticlesPacket particles) onParticle(particles);
        });
        UseItemCallback.EVENT.register((player, level, hand) -> {
            if (!active() || !ITEM_ID.equals(id(player.getItemInHand(hand)))) return InteractionResult.PASS;
            long now = System.currentTimeMillis();
            if (cfg.secretCompassDuplicateGuard && now - lastParticle < 200) return InteractionResult.FAIL;
            resetPrediction();
            lastUse = now;
            return InteractionResult.PASS;
        });
        ClientReceiveMessageEvents.ALLOW_GAME.register((message, overlay) -> {
            if (!overlay) onChat(message.getString());
            return true;
        });
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> resetAll());
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> resetAll());
    }

    public static void registerCommands(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        dispatcher.register(LiteralArgumentBuilder.<FabricClientCommandSource>literal("secretcompass")
            .executes(ctx -> status())
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("status").executes(ctx -> status()))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("reset").executes(ctx -> resetCommand()))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("color")
                .then(RequiredArgumentBuilder.<FabricClientCommandSource, String>argument("argb", StringArgumentType.word())
                    .executes(ctx -> colour(StringArgumentType.getString(ctx, "argb"))))));
    }

    private static void onParticle(ClientboundLevelParticlesPacket packet) {
        if (!active() || packet.getParticle().getType() != ParticleTypes.HAPPY_VILLAGER
            || packet.getCount() != 1 || packet.getMaxSpeed() != 0f) return;
        long now = System.currentTimeMillis();
        if (now - lastUse > 1000) return;
        Vec3 point = new Vec3(packet.getX(), packet.getY(), packet.getZ());
        if (!points.isEmpty()) {
            double distance = point.distanceTo(points.getLast());
            if (distance == 0 || distance > 1.0) return;
        }
        lastParticle = now;
        points.add(point);
        predict();
    }

    private static void onChat(String raw) {
        if (!active() || raw == null) return;
        String message = ChatFormatting.stripFormatting(raw);
        Matcher matcher = DISTANCE.matcher(message);
        if (matcher.find()) {
            int first = Integer.parseInt(matcher.group("distance"));
            int second = matcher.group("distance2") == null ? 0 : Integer.parseInt(matcher.group("distance2"));
            secretDistance = (int) Math.sqrt(first * first + second * second);
            predict();
        } else if (message.equals("There are no missing secrets near you!")) {
            resetPrediction();
        }
    }

    private static void predict() {
        if (secretDistance == null || points.size() < 2) return;
        target = new Vec3(fit(0, secretDistance * 2.0), fit(1, secretDistance * 2.0), fit(2, secretDistance * 2.0));
    }

    private static double fit(int axis, double at) {
        int n = points.size();
        double sx = n * (n - 1) / 2.0;
        double sxx = n * (n - 1) * (2.0 * n - 1) / 6.0;
        double sy = 0, sxy = 0;
        for (int i = 0; i < n; i++) {
            Vec3 point = points.get(i);
            double value = axis == 0 ? point.x : axis == 1 ? point.y : point.z;
            sy += value;
            sxy += i * value;
        }
        double denominator = n * sxx - sx * sx;
        if (denominator == 0) return sy / n;
        double slope = (n * sxy - sx * sy) / denominator;
        double intercept = (sy - slope * sx) / n;
        return intercept + slope * at;
    }

    public static void draw(WorldRenderer.Ctx ctx) {
        if (!active()) { resetPrediction(); return; }
        Minecraft mc = Minecraft.getInstance();
        if (target == null || mc.player == null) return;
        Vec3 eye = mc.player.getEyePosition();
        double distance = eye.distanceTo(target);
        if (distance <= 3) { resetPrediction(); return; }
        int colour = cfg.secretCompassColour;
        boolean walls = cfg.secretCompassThroughWalls;
        if (cfg.secretCompassTracer) ctx.line(eye, target, colour, walls);
        if (cfg.secretCompassBox) ctx.highlight(new AABB(target.x - 0.35, target.y - 0.35, target.z - 0.35,
            target.x + 0.35, target.y + 0.35, target.z + 0.35), colour, walls);
        if (cfg.secretCompassBeam) ctx.beam(target.x, target.y, target.z, colour, 8, walls);
        ctx.label(target.add(0, 0.75, 0), "SECRET " + Math.round(distance) + "m", colour, walls);
    }

    public static String hudText() {
        if (!active()) return null;
        Minecraft mc = Minecraft.getInstance();
        if (target != null && mc.player != null)
            return "§dTarget §f" + Math.round(mc.player.getEyePosition().distanceTo(target)) + "m §7(" + points.size() + " samples)";
        if (System.currentTimeMillis() - lastUse < 1500)
            return secretDistance == null ? "§eReading particles..." : "§eFitting " + secretDistance + "m path...";
        return null;
    }

    private static int status() {
        Minecraft mc = Minecraft.getInstance();
        String state = target != null && mc.player != null
            ? Math.round(mc.player.getEyePosition().distanceTo(target)) + "m target from " + points.size() + " samples"
            : secretDistance != null ? "distance received; waiting for a usable particle line" : "idle";
        local("Secret Tracker locator is " + (cfg != null && cfg.secretCompassHelper ? "enabled" : "disabled") + ": " + state + '.');
        return 1;
    }

    private static int resetCommand() {
        resetPrediction();
        local("Secret Tracker prediction cleared.");
        return 1;
    }

    private static int colour(String raw) {
        if (cfg == null) return 0;
        String value = raw.replace("#", "");
        try {
            long parsed = Long.parseUnsignedLong(value, 16);
            if (value.length() == 6) parsed |= 0xFF000000L;
            if (value.length() != 6 && value.length() != 8) throw new NumberFormatException();
            cfg.secretCompassColour = (int) parsed;
            ConstellationClient.saveConfig();
            local("Secret Tracker color set to #" + String.format(Locale.ROOT, "%08X", cfg.secretCompassColour) + '.');
            return 1;
        } catch (NumberFormatException ignored) {
            local("Use /secretcompass color RRGGBB or AARRGGBB.");
            return 0;
        }
    }

    private static boolean active() {
        return cfg != null && cfg.enabled && cfg.secretCompassHelper && ConstellationClient.loc().inDungeons()
            && !ConstellationClient.dungeon().inBoss();
    }

    private static String id(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        CompoundTag extra = data == null ? new CompoundTag() : data.copyTag().getCompoundOrEmpty("ExtraAttributes");
        return extra.getStringOr("id", "");
    }

    private static void resetPrediction() {
        points.clear();
        secretDistance = null;
        target = null;
    }

    private static void resetAll() {
        resetPrediction();
        lastUse = 0;
        lastParticle = 0;
    }

    private static void local(String text) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) mc.player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§dSecret Compass §8> §f" + text));
    }
}
