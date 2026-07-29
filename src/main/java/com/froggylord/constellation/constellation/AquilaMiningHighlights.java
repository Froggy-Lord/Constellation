package com.froggylord.constellation.constellation;

import com.froggylord.constellation.ConstellationClient;
import com.froggylord.constellation.config.AquilaConfig;
import com.froggylord.constellation.core.LocationManager.SkyblockArea;
import com.froggylord.constellation.network.BlockStateUpdate;
import com.froggylord.constellation.render.WorldRenderer;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundLevelParticlesPacket;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

// ported from Skyblocker (LGPL-3.0-or-later): skyblock/dwarven/CarpetHighlighter.java
// ported from Skyblocker (LGPL-3.0-or-later): skyblock/dwarven/CrystalsChestHighlighter.java
public final class AquilaMiningHighlights {
    private static final String CHEST_SPAWN = "You uncovered a treasure chest!";
    private static final Set<BlockPos> CARPETS = new HashSet<>();
    private static final Map<BlockPos, Long> CHESTS = new HashMap<>();
    private static final Map<Vec3, Long> PARTICLES = new HashMap<>();
    private static final Map<BlockPos, Integer> CURRENT_LOCKS = new HashMap<>();
    private static final Map<BlockPos, Integer> NEEDED_LOCKS = new HashMap<>();
    private static AquilaConfig cfg;
    private static boolean initialized;
    private static int waitingForChest;
    private static long chestMessageAt;
    private static Object levelIdentity;
    private static int carpetTick;

    private AquilaMiningHighlights() {}

    public static void init(AquilaConfig config) {
        cfg = config;
        if (initialized) return;
        initialized = true;
        ClientReceiveMessageEvents.ALLOW_GAME.register((message, overlay) -> {
            if (!overlay) onChat(clean(message.getString()));
            return true;
        });
        ConstellationClient.instance().packets().register(packet -> {
            if (packet instanceof BlockStateUpdate update) onBlock(update);
            else if (packet instanceof ClientboundLevelParticlesPacket particles) onParticle(particles);
            else if (packet instanceof ClientboundSoundPacket sound) onSound(sound);
        });
        ConstellationClient.tick().every(1, "aquila-mining-highlights", AquilaMiningHighlights::tick);
        ClientPlayConnectionEvents.JOIN.register((a, b, c) -> reset());
        ClientPlayConnectionEvents.DISCONNECT.register((a, b) -> reset());
    }

    private static boolean active() {
        return cfg != null && cfg.enabled && cfg.miningHighlightsSuite;
    }

    private static boolean dwarven() {
        return active() && ConstellationClient.loc().area() == SkyblockArea.DWARVEN_MINES;
    }

    private static boolean hollows() {
        return active() && ConstellationClient.loc().area() == SkyblockArea.CRYSTAL_HOLLOWS;
    }

    private static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != levelIdentity) {
            reset();
            levelIdentity = mc.level;
        }
        if (!dwarven()) {
            CARPETS.clear();
            carpetTick = 0;
        } else if (cfg.dwarvenCarpetHighlighter && mc.level != null && mc.player != null
            && ++carpetTick >= Math.clamp(cfg.dwarvenCarpetScanTicks, 5, 100)) {
            carpetTick = 0;
            scanCarpets(mc);
        }
        if (!hollows()) {
            clearChests();
            return;
        }
        long now = System.currentTimeMillis();
        if (waitingForChest > 0 && now - chestMessageAt > Math.clamp(cfg.treasureChestAssociationSeconds, 1, 15) * 1000L)
            waitingForChest = 0;
        PARTICLES.entrySet().removeIf(entry -> now - entry.getValue() > Math.clamp(cfg.treasureChestParticleMillis, 50, 1000));
        CHESTS.entrySet().removeIf(entry -> {
            boolean gone = now - entry.getValue() > 600_000L || mc.level == null || !mc.level.getBlockState(entry.getKey()).is(Blocks.CHEST);
            if (gone) clearProgress(entry.getKey());
            return gone;
        });
    }

    private static void scanCarpets(Minecraft mc) {
        int radius = Math.clamp(cfg.dwarvenCarpetScanRadius, 4, 24);
        for (BlockPos mutable : BlockPos.withinManhattan(mc.player.blockPosition(), radius, radius, radius))
            if (isOreCarpet(mc, mutable)) CARPETS.add(mutable.immutable());
        CARPETS.removeIf(pos -> !isOreCarpet(mc, pos)
            || pos.distToCenterSqr(mc.player.position()) > Math.pow(radius * 4.0, 2));
    }

    private static boolean isOreCarpet(Minecraft mc, BlockPos pos) {
        var state = mc.level.getBlockState(pos);
        return (state.is(Blocks.CARPET.gray()) || state.is(Blocks.CARPET.lightBlue()) || state.is(Blocks.CARPET.lightGray()))
            && mc.level.getBlockState(pos.below()).is(Blocks.SEA_LANTERN);
    }

    private static void onChat(String message) {
        if (!hollows() || !cfg.treasureChestEsp || !message.equals(CHEST_SPAWN)) return;
        waitingForChest++;
        chestMessageAt = System.currentTimeMillis();
    }

    private static void onBlock(BlockStateUpdate update) {
        Minecraft mc = Minecraft.getInstance();
        if (!hollows() || !cfg.treasureChestEsp || mc.player == null) return;
        BlockPos pos = update.pos().immutable();
        if (waitingForChest > 0 && update.newState().is(Blocks.CHEST)
            && pos.distToCenterSqr(mc.player.position()) <= Math.pow(Math.clamp(cfg.treasureChestAssociationRange, 3, 20), 2)) {
            CHESTS.put(pos, System.currentTimeMillis());
            CURRENT_LOCKS.put(pos, 0);
            waitingForChest--;
        } else if (update.newState().isAir() && CHESTS.remove(pos) != null) clearProgress(pos);
    }

    private static void onParticle(ClientboundLevelParticlesPacket packet) {
        if (hollows() && cfg.treasureChestEsp && cfg.treasureChestLockSpot
            && packet.getParticle().getType() == ParticleTypes.CRIT)
            PARTICLES.put(new Vec3(packet.getX(), packet.getY(), packet.getZ()), System.currentTimeMillis());
    }

    private static void onSound(ClientboundSoundPacket packet) {
        if (!hollows() || !cfg.treasureChestEsp || CHESTS.isEmpty()) return;
        BlockPos target = targetedChest();
        var id = packet.getSound().value().location();
        if (id.equals(SoundEvents.EXPERIENCE_ORB_PICKUP.location()) && packet.getPitch() == 1f && target != null) {
            CURRENT_LOCKS.merge(target, 1, Integer::sum);
            PARTICLES.clear();
        } else if (id.equals(SoundEvents.VILLAGER_NO.location())) {
            if (target != null) CURRENT_LOCKS.put(target, 0);
            PARTICLES.clear();
        } else if (id.equals(SoundEvents.CHEST_OPEN.location()) && target != null) {
            NEEDED_LOCKS.put(target, Math.min(CURRENT_LOCKS.getOrDefault(target, 0), 5));
            CURRENT_LOCKS.put(target, 0);
            PARTICLES.clear();
        }
    }

    private static BlockPos targetedChest() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.hitResult instanceof BlockHitResult hit && CHESTS.containsKey(hit.getBlockPos())) return hit.getBlockPos();
        return null;
    }

    public static void draw(WorldRenderer.Ctx ctx) {
        if (!active()) return;
        if (dwarven() && cfg.dwarvenCarpetHighlighter)
            for (BlockPos pos : CARPETS)
                ctx.box(new AABB(pos.getX(), pos.getY(), pos.getZ(), pos.getX() + 1, pos.getY() + .0625, pos.getZ() + 1),
                    cfg.dwarvenCarpetColor, cfg.dwarvenCarpetThroughWalls);
        if (!hollows() || !cfg.treasureChestEsp) return;
        for (BlockPos pos : CHESTS.keySet()) {
            Vec3 center = Vec3.atCenterOf(pos).subtract(0, .0625, 0);
            if (cfg.treasureChestOutline)
                ctx.outline(AABB.ofSize(center, .885, .885, .885), cfg.treasureChestColor, cfg.treasureChestThroughWalls, 3);
        }
        BlockPos target = targetedChest();
        if (target == null) return;
        Vec3 center = Vec3.atCenterOf(target);
        if (cfg.treasureChestLockSpot) drawLockSpot(ctx, center);
        int needed = NEEDED_LOCKS.getOrDefault(target, 0);
        if (cfg.treasureChestLockProgress && needed > 0) {
            int current = Math.min(CURRENT_LOCKS.getOrDefault(target, 0), needed);
            ctx.label(center.add(0, .75, 0), current + "/" + needed, cfg.treasureChestColor, true);
        }
    }

    private static void drawLockSpot(WorldRenderer.Ctx ctx, Vec3 chest) {
        Vec3 total = Vec3.ZERO;
        int count = 0;
        for (Vec3 particle : PARTICLES.keySet()) {
            if (!particle.closerThan(chest, .8)) continue;
            total = total.add(particle);
            count++;
        }
        if (count == 0) return;
        Vec3 spot = total.scale(1.0 / count);
        ctx.box(AABB.ofSize(spot, .1, .1, .1), cfg.treasureChestColor, true);
    }

    public static void registerCommands(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        dispatcher.register(LiteralArgumentBuilder.<FabricClientCommandSource>literal("mininghighlights")
            .executes(context -> status())
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("clear").executes(context -> {
                CARPETS.clear();
                clearChests();
                return status();
            }))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("carpetradius")
                .then(RequiredArgumentBuilder.<FabricClientCommandSource, Integer>argument("blocks", IntegerArgumentType.integer(4, 24))
                    .executes(context -> {
                        cfg.dwarvenCarpetScanRadius = IntegerArgumentType.getInteger(context, "blocks");
                        save();
                        return status();
                    })))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("carpetticks")
                .then(RequiredArgumentBuilder.<FabricClientCommandSource, Integer>argument("ticks", IntegerArgumentType.integer(5, 100))
                    .executes(context -> {
                        cfg.dwarvenCarpetScanTicks = IntegerArgumentType.getInteger(context, "ticks");
                        save();
                        return status();
                    })))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("chestrange")
                .then(RequiredArgumentBuilder.<FabricClientCommandSource, Integer>argument("blocks", IntegerArgumentType.integer(3, 20))
                    .executes(context -> {
                        cfg.treasureChestAssociationRange = IntegerArgumentType.getInteger(context, "blocks");
                        save();
                        return status();
                    })))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("association")
                .then(RequiredArgumentBuilder.<FabricClientCommandSource, Integer>argument("seconds", IntegerArgumentType.integer(1, 15))
                    .executes(context -> {
                        cfg.treasureChestAssociationSeconds = IntegerArgumentType.getInteger(context, "seconds");
                        save();
                        return status();
                    })))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("particlems")
                .then(RequiredArgumentBuilder.<FabricClientCommandSource, Integer>argument("milliseconds", IntegerArgumentType.integer(50, 1000))
                    .executes(context -> {
                        cfg.treasureChestParticleMillis = IntegerArgumentType.getInteger(context, "milliseconds");
                        save();
                        return status();
                    })))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("color")
                .then(RequiredArgumentBuilder.<FabricClientCommandSource, String>argument("target", StringArgumentType.word())
                    .then(RequiredArgumentBuilder.<FabricClientCommandSource, String>argument("argb", StringArgumentType.word())
                        .executes(context -> color(StringArgumentType.getString(context, "target"), StringArgumentType.getString(context, "argb"))))))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("option")
                .then(RequiredArgumentBuilder.<FabricClientCommandSource, String>argument("name", StringArgumentType.word())
                    .then(RequiredArgumentBuilder.<FabricClientCommandSource, String>argument("state", StringArgumentType.word())
                        .executes(context -> option(StringArgumentType.getString(context, "name"), StringArgumentType.getString(context, "state")))))));
    }

    private static int status() {
        local("Carpets " + on(cfg.dwarvenCarpetHighlighter) + " with " + CARPETS.size() + " cached; chests "
            + on(cfg.treasureChestEsp) + " with " + CHESTS.size() + " active.");
        return 1;
    }

    private static int option(String name, String raw) {
        Boolean value = parse(raw);
        if (value == null) {
            local("State must be on or off.");
            return 0;
        }
        switch (name.toLowerCase(Locale.ROOT)) {
            case "enabled" -> cfg.miningHighlightsSuite = value;
            case "carpets" -> cfg.dwarvenCarpetHighlighter = value;
            case "carpetwalls" -> cfg.dwarvenCarpetThroughWalls = value;
            case "chests" -> cfg.treasureChestEsp = value;
            case "outline" -> cfg.treasureChestOutline = value;
            case "lockspot" -> cfg.treasureChestLockSpot = value;
            case "progress" -> cfg.treasureChestLockProgress = value;
            case "chestwalls" -> cfg.treasureChestThroughWalls = value;
            default -> {
                local("Option must be enabled, carpets, carpetwalls, chests, outline, lockspot, progress, or chestwalls.");
                return 0;
            }
        }
        if (!cfg.dwarvenCarpetHighlighter) CARPETS.clear();
        if (!cfg.treasureChestEsp) clearChests();
        save();
        return status();
    }

    private static int color(String target, String raw) {
        Integer value;
        try {
            String clean = raw.startsWith("#") ? raw.substring(1) : raw;
            if (clean.length() != 8) throw new NumberFormatException();
            value = (int) Long.parseLong(clean, 16);
        } catch (NumberFormatException exception) {
            local("Color must be an eight-digit ARGB hex value.");
            return 0;
        }
        if (target.equalsIgnoreCase("carpet")) cfg.dwarvenCarpetColor = value;
        else if (target.equalsIgnoreCase("chest")) cfg.treasureChestColor = value;
        else {
            local("Color target must be carpet or chest.");
            return 0;
        }
        save();
        return status();
    }

    private static void clearProgress(BlockPos pos) {
        CURRENT_LOCKS.remove(pos);
        NEEDED_LOCKS.remove(pos);
    }

    private static void clearChests() {
        waitingForChest = 0;
        chestMessageAt = 0;
        CHESTS.clear();
        PARTICLES.clear();
        CURRENT_LOCKS.clear();
        NEEDED_LOCKS.clear();
    }

    private static void reset() {
        CARPETS.clear();
        clearChests();
        carpetTick = 0;
        levelIdentity = null;
    }

    private static void save() {
        ConstellationClient.saveConfig();
    }

    private static void local(String text) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) mc.player.sendSystemMessage(Component.literal("§6[Mining Highlights] §f" + text));
    }

    private static String clean(String text) {
        return text.replaceAll("§[0-9A-FK-ORa-fk-or]", "").trim();
    }

    private static Boolean parse(String raw) {
        return switch (raw.toLowerCase(Locale.ROOT)) {
            case "on", "true", "yes", "1" -> true;
            case "off", "false", "no", "0" -> false;
            default -> null;
        };
    }

    private static String on(boolean value) {
        return value ? "on" : "off";
    }
}
