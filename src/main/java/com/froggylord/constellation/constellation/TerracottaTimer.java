package com.froggylord.constellation.constellation;

import com.froggylord.constellation.ConstellationClient;
import com.froggylord.constellation.config.OrionConfig;
import com.froggylord.constellation.data.DungeonState;
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
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;

// phase gate and POTTED_POPPY -> air transition ported from Devonian (GPL-3.0):
// features/dungeons/TerracottaTimer.kt and api/dungeon/Stages.kt
// 15s/12s durations cross-checked with Odin (BSD-3-Clause):
// features/impl/boss/TerracottaTimer.kt
// and NoFrills (GPL-3.0): features/dungeons/TerracottaTimer.java
// phase cleanup cross-checked with NoammAddons (CC0-1.0): features/impl/dungeon/TerracottaTimer.kt
public final class TerracottaTimer {
    private static final String START = "[BOSS] Sadan: So you made it all the way here... Now you wish to defy me? Sadan?!";
    private static final String END = "[BOSS] Sadan: ENOUGH!";
    private static final Map<BlockPos, Long> respawns = new HashMap<>();
    private static boolean initialized;
    private static boolean phaseActive;
    private static long phaseStartTick = -1;
    private static long pendingStartUntil = -1;
    private static long serverTicks;
    private static Object levelIdentity;

    private TerracottaTimer() {}

    public static void init() {
        if (initialized) return;
        initialized = true;
        ConstellationClient.instance().packets().register(packet -> {
            if (packet instanceof BlockStateUpdate update) onBlockUpdate(update);
        });
        ClientReceiveMessageEvents.ALLOW_GAME.register((message, overlay) -> {
            if (!overlay) onChat(ChatFormatting.stripFormatting(message.getString()));
            return true;
        });
        ConstellationClient.bus().subscribe(DungeonState.DungeonEnter.class, ignored -> reset(null));
        ConstellationClient.bus().subscribe(DungeonState.FloorChange.class, ignored -> {
            if (!floorSix()) reset(Minecraft.getInstance().level);
        });
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> reset(null));
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> reset(null));
    }

    public static void registerCommands(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        dispatcher.register(LiteralArgumentBuilder.<FabricClientCommandSource>literal("terracotta")
            .executes(context -> status())
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("status").executes(context -> status()))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("precision")
                .then(RequiredArgumentBuilder.<FabricClientCommandSource, Integer>argument("decimals", IntegerArgumentType.integer(0, 2))
                    .executes(context -> precision(IntegerArgumentType.getInteger(context, "decimals")))))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("duration")
                .then(RequiredArgumentBuilder.<FabricClientCommandSource, String>argument("floor", StringArgumentType.word())
                    .then(RequiredArgumentBuilder.<FabricClientCommandSource, Integer>argument("ticks", IntegerArgumentType.integer(1, 1200))
                        .executes(context -> duration(StringArgumentType.getString(context, "floor"),
                            IntegerArgumentType.getInteger(context, "ticks"))))))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("color")
                .then(RequiredArgumentBuilder.<FabricClientCommandSource, String>argument("stage", StringArgumentType.word())
                    .then(RequiredArgumentBuilder.<FabricClientCommandSource, String>argument("argb", StringArgumentType.word())
                        .executes(context -> colourCommand(StringArgumentType.getString(context, "stage"),
                            StringArgumentType.getString(context, "argb")))))));
    }

    private static void onChat(String message) {
        if (message == null) return;
        if (message.equals(START)) {
            if (floorSix() && ConstellationClient.loc().inDungeons()) startPhase();
            else pendingStartUntil = serverTicks + 100;
        } else if (message.equals(END)) {
            phaseActive = false;
            phaseStartTick = -1;
            pendingStartUntil = -1;
            respawns.clear();
        }
    }

    private static void onBlockUpdate(BlockStateUpdate update) {
        OrionConfig cfg = config();
        if (cfg == null || !cfg.enabled || !cfg.terracottaTimer || !activeGate()) return;
        ensureLevel(Minecraft.getInstance().level);
        if (!update.oldState().is(Blocks.POTTED_POPPY) || !update.newState().isAir()) return;
        long current = now();
        long existing = respawns.getOrDefault(update.pos(), Long.MIN_VALUE);
        if (existing > current) return;
        respawns.put(update.pos().immutable(), current + durationTicks());
    }

    public static void draw(WorldRenderer.Ctx context) {
        OrionConfig cfg = config();
        if (cfg == null || !cfg.enabled || !cfg.terracottaTimer || !cfg.terracottaRespawnLabels || !activeGate()) {
            if (!activeGate()) respawns.clear();
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;
        ensureLevel(mc.level);
        long current = now();
        int decimals = Math.clamp(cfg.terracottaTimerDecimals, 0, 2);
        for (var entry : respawns.entrySet()) {
            double seconds = Math.max(0, entry.getValue() - current) / 20.0;
            context.label(Vec3.atCenterOf(entry.getKey()).add(0, .8, 0),
                String.format(Locale.ROOT, "%1$." + decimals + "fs", seconds), colour(seconds), cfg.terracottaThroughWalls);
        }
    }

    public static String hudText() {
        OrionConfig cfg = config();
        if (cfg == null || !cfg.enabled || !cfg.terracottaTimer || !cfg.terracottaPhaseHud || !activeGate()) return null;
        long current = now();
        double elapsed = phaseStartTick < 0 ? 0 : Math.max(0, current - phaseStartTick) / 20.0;
        String value = String.format(Locale.ROOT, "Terra %.1fs", elapsed);
        if (!respawns.isEmpty()) value += " §7| §f" + respawns.size() + " respawning";
        return value;
    }

    public static void onServerTick() {
        serverTicks++;
        if (!phaseActive && pendingStartUntil >= serverTicks && floorSix()
            && ConstellationClient.loc().inDungeons() && ConstellationClient.dungeon().inBoss()) startPhase();
        if (pendingStartUntil >= 0 && serverTicks > pendingStartUntil) pendingStartUntil = -1;
        OrionConfig cfg = config();
        if (cfg == null || !cfg.enabled || !cfg.terracottaTimer || !activeGate()) {
            if (!activeGate()) respawns.clear();
            return;
        }
        boolean becameReady = removeExpired(now());
        Minecraft mc = Minecraft.getInstance();
        if (becameReady && cfg.terracottaReadySound && mc.player != null)
            mc.player.playSound(SoundEvents.NOTE_BLOCK_PLING.value(), 0.55f, 1.45f);
    }

    private static boolean removeExpired(long current) {
        boolean removed = false;
        Iterator<Map.Entry<BlockPos, Long>> iterator = respawns.entrySet().iterator();
        while (iterator.hasNext()) {
            if (iterator.next().getValue() <= current) {
                iterator.remove();
                removed = true;
            }
        }
        return removed;
    }

    private static int colour(double seconds) {
        OrionConfig cfg = config();
        double maximum = durationTicks() / 20.0;
        if (seconds >= maximum * .75) return cfg.terracottaFarColour;
        if (seconds >= maximum * .50) return cfg.terracottaSoonColour;
        return cfg.terracottaReadyColour;
    }

    private static boolean activeGate() {
        return phaseActive && floorSix() && ConstellationClient.loc().inDungeons()
            && ConstellationClient.dungeon().inBoss();
    }

    private static boolean floorSix() {
        String floor = ConstellationClient.dungeon().floor().toUpperCase(Locale.ROOT).replace(" ", "");
        return floor.equals("F6") || floor.equals("M6") || floor.endsWith("FLOORVI");
    }

    private static int durationTicks() {
        String floor = ConstellationClient.dungeon().floor().toUpperCase(Locale.ROOT).replace(" ", "");
        return floor.equals("M6") || floor.startsWith("MASTER")
            ? Math.clamp(config().terracottaM6RespawnTicks, 1, 1200)
            : Math.clamp(config().terracottaF6RespawnTicks, 1, 1200);
    }

    private static long now() {
        return serverTicks;
    }

    private static void startPhase() {
        ensureLevel(Minecraft.getInstance().level);
        phaseActive = true;
        phaseStartTick = serverTicks;
        pendingStartUntil = -1;
        respawns.clear();
    }

    private static void ensureLevel(Object level) {
        if (levelIdentity != level) reset(level);
    }

    private static void reset(Object level) {
        levelIdentity = level;
        phaseActive = false;
        phaseStartTick = -1;
        pendingStartUntil = -1;
        respawns.clear();
    }

    private static OrionConfig config() {
        return ConstellationClient.cfg() == null ? null : ConstellationClient.cfg().orion;
    }

    private static int status() {
        OrionConfig cfg = config();
        message("Terracotta timers: " + (cfg.terracottaTimer ? "enabled" : "disabled") + ", "
            + cfg.terracottaTimerDecimals + " decimals, F6 " + cfg.terracottaF6RespawnTicks / 20.0
            + "s, M6 " + cfg.terracottaM6RespawnTicks / 20.0 + "s.");
        message("Colors far/middle/ready: " + hex(cfg.terracottaFarColour) + ", "
            + hex(cfg.terracottaSoonColour) + ", " + hex(cfg.terracottaReadyColour) + ".");
        return 1;
    }

    private static int precision(int decimals) {
        config().terracottaTimerDecimals = decimals;
        ConstellationClient.saveConfig();
        return status();
    }

    private static int duration(String floor, int ticks) {
        OrionConfig cfg = config();
        if (floor.equalsIgnoreCase("f6")) cfg.terracottaF6RespawnTicks = ticks;
        else if (floor.equalsIgnoreCase("m6")) cfg.terracottaM6RespawnTicks = ticks;
        else {
            message("Floor must be f6 or m6.");
            return 0;
        }
        ConstellationClient.saveConfig();
        return status();
    }

    private static int colourCommand(String stage, String raw) {
        OrionConfig cfg = config();
        try {
            String value = raw.startsWith("#") ? raw.substring(1) : raw;
            if (value.length() == 6) value = "FF" + value;
            if (value.length() != 8) throw new NumberFormatException();
            int colour = (int) Long.parseLong(value, 16);
            switch (stage.toLowerCase(Locale.ROOT)) {
                case "far" -> cfg.terracottaFarColour = colour;
                case "middle", "mid", "soon" -> cfg.terracottaSoonColour = colour;
                case "ready", "near" -> cfg.terracottaReadyColour = colour;
                default -> {
                    message("Stage must be far, middle, or ready.");
                    return 0;
                }
            }
            ConstellationClient.saveConfig();
            return status();
        } catch (NumberFormatException ignored) {
            message("Use RRGGBB or AARRGGBB.");
            return 0;
        }
    }

    private static String hex(int colour) {
        return String.format(Locale.ROOT, "%08X", colour);
    }

    private static void message(String value) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) mc.player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§b[Orion] §f" + value));
    }
}
