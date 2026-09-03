package com.froggylord.constellation.constellation;

import com.froggylord.constellation.ConstellationClient;
import com.froggylord.constellation.config.PerseusConfig;
import com.froggylord.constellation.core.LocationManager;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;

import java.util.Locale;
import java.util.Set;

// ported from Athen (BSD-3-Clause): modules/impl/slayer/SlayerSounds.kt
// Vampire filter ported from NoFrills (GPL-3.0): features/slayer/MuteVampire.java
// expanded type sets ported from Skyblocker (LGPL-3.0-or-later): skyblock/slayers/boss/*/Mute*Sounds.java
public final class SlayerSounds {
    private static final Set<Identifier> VOID = Set.of(
        SoundEvents.ENDERMAN_STARE.location(), SoundEvents.ENDERMAN_SCREAM.location());
    private static final Set<Identifier> VAMP = Set.of(
        SoundEvents.ELDER_GUARDIAN_CURSE.location(), SoundEvents.WITHER_SPAWN.location());
    private static final Set<Identifier> BLAZE = Set.of(
        SoundEvents.BLAZE_BURN.location(), SoundEvents.BLAZE_AMBIENT.location(), SoundEvents.BLAZE_HURT.location(),
        SoundEvents.GHAST_SHOOT.location(), SoundEvents.FIRE_AMBIENT.location(), SoundEvents.LAVA_POP.location(),
        SoundEvents.LIGHTNING_BOLT_THUNDER.location(), SoundEvents.LIGHTNING_BOLT_IMPACT.location());
    private static final Set<Identifier> TARA = Set.of(
        SoundEvents.SPIDER_AMBIENT.location(), SoundEvents.SPIDER_DEATH.location(), SoundEvents.SPIDER_HURT.location(),
        SoundEvents.SPIDER_STEP.location(), SoundEvents.BAT_HURT.location(), SoundEvents.SILVERFISH_HURT.location(),
        SoundEvents.SILVERFISH_DEATH.location(), SoundEvents.SILVERFISH_AMBIENT.location(), SoundEvents.SKELETON_AMBIENT.location(),
        SoundEvents.SKELETON_DEATH.location(), SoundEvents.SKELETON_HURT.location(), SoundEvents.SKELETON_STEP.location());
    private static PerseusConfig cfg;

    private SlayerSounds() {}

    public static void init(PerseusConfig config) { cfg = config; }

    public static boolean shouldCancel(ClientboundSoundPacket packet) {
        if (cfg == null || !cfg.enabled || !cfg.slayerSoundFilter || !ConstellationClient.loc().onHypixel()) return false;
        Identifier sound = packet.getSound().value().location();
        if (cfg.muteVoidgloomSounds && VOID.contains(sound)
            && (!cfg.voidgloomSoundsOnlyDuringQuest || fighting(SlayerState.Type.VOID))) return true;
        if (cfg.muteVampireSounds && VAMP.contains(sound) && vampireContext()
            && (!cfg.vampireSoundsOnlyDuringQuest || fighting(SlayerState.Type.VAMP))) return true;
        if (cfg.muteInfernoSounds && BLAZE.contains(sound) && crimsonContext()
            && (!cfg.otherSlayerSoundsOnlyDuringQuest || fighting(SlayerState.Type.BLAZE))) return true;
        if (cfg.muteTarantulaSounds && TARA.contains(sound) && spiderContext() && !fighting(SlayerState.Type.BLAZE)
            && (!cfg.otherSlayerSoundsOnlyDuringQuest || fighting(SlayerState.Type.TARA))) return true;
        return cfg.muteSvenSounds && sound.toString().contains("minecraft:entity.wolf.") && svenContext()
            && (!cfg.otherSlayerSoundsOnlyDuringQuest || fighting(SlayerState.Type.SVEN));
    }

    public static void registerCommands(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        dispatcher.register(LiteralArgumentBuilder.<FabricClientCommandSource>literal("slayersounds")
            .executes(c -> status())
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("status").executes(c -> status()))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("option")
                .then(RequiredArgumentBuilder.<FabricClientCommandSource, String>argument("name", StringArgumentType.word())
                    .then(RequiredArgumentBuilder.<FabricClientCommandSource, String>argument("state", StringArgumentType.word())
                        .executes(c -> option(StringArgumentType.getString(c, "name"), StringArgumentType.getString(c, "state")))))));
    }

    private static boolean fighting(SlayerState.Type type) {
        for (SlayerState.Boss boss : SlayerState.bosses()) if (boss.type() == type && boss.entity().isAlive()) return true;
        String needle = switch (type) { case REV -> "revenant"; case TARA -> "tarantula"; case SVEN -> "sven"; case VOID -> "voidgloom"; case BLAZE -> "inferno"; case VAMP -> "riftstalker"; };
        return ConstellationClient.loc().getSidebarLines().stream().anyMatch(line -> line.toLowerCase(Locale.ROOT).contains(needle));
    }

    private static boolean vampireContext() { return ConstellationClient.loc().area() == LocationManager.SkyblockArea.THE_RIFT; }
    private static boolean crimsonContext() { LocationManager.SkyblockArea area = ConstellationClient.loc().area(); return area == LocationManager.SkyblockArea.CRIMSON_ISLE || area == LocationManager.SkyblockArea.KUUDRA; }
    private static boolean spiderContext() { LocationManager.SkyblockArea area = ConstellationClient.loc().area(); return area == LocationManager.SkyblockArea.SPIDER_DEN || area == LocationManager.SkyblockArea.CRIMSON_ISLE; }
    private static boolean svenContext() { LocationManager.SkyblockArea area = ConstellationClient.loc().area(); return area == LocationManager.SkyblockArea.PARK || area == LocationManager.SkyblockArea.HUB; }

    private static int status() {
        local("§eSlayer sounds: " + on(cfg.slayerSoundFilter) + "; Voidgloom " + on(cfg.muteVoidgloomSounds)
            + ", Vampire " + on(cfg.muteVampireSounds) + ", Inferno " + on(cfg.muteInfernoSounds)
            + ", Tarantula " + on(cfg.muteTarantulaSounds) + ", Sven " + on(cfg.muteSvenSounds) + ".");
        local("§7Voidgloom quest-only " + on(cfg.voidgloomSoundsOnlyDuringQuest) + "; Vampire quest-only "
            + on(cfg.vampireSoundsOnlyDuringQuest) + "; other quest-only " + on(cfg.otherSlayerSoundsOnlyDuringQuest) + ".");
        return 1;
    }

    private static int option(String name, String state) {
        Boolean value = parseState(state);
        if (value == null) { local("§cState must be on or off."); return 0; }
        switch (name.toLowerCase(Locale.ROOT)) {
            case "enabled", "filter" -> cfg.slayerSoundFilter = value;
            case "void", "voidgloom", "enderman" -> cfg.muteVoidgloomSounds = value;
            case "vamp", "vampire" -> cfg.muteVampireSounds = value;
            case "blaze", "inferno" -> cfg.muteInfernoSounds = value;
            case "tara", "tarantula", "spider" -> cfg.muteTarantulaSounds = value;
            case "sven", "wolf" -> cfg.muteSvenSounds = value;
            case "voidquest" -> cfg.voidgloomSoundsOnlyDuringQuest = value;
            case "vampquest" -> cfg.vampireSoundsOnlyDuringQuest = value;
            case "otherquest", "quest" -> cfg.otherSlayerSoundsOnlyDuringQuest = value;
            default -> { local("§cOption must be filter, void, vamp, blaze, tara, sven, voidquest, vampquest, or otherquest."); return 0; }
        }
        ConstellationClient.saveConfig();
        local("§aSlayer sound option updated.");
        return 1;
    }

    private static Boolean parseState(String state) { return switch (state.toLowerCase(Locale.ROOT)) { case "on", "true", "yes", "1" -> true; case "off", "false", "no", "0" -> false; default -> null; }; }
    private static String on(boolean value) { return value ? "§aon" : "§coff"; }
    private static void local(String text) { Minecraft mc = Minecraft.getInstance(); if (mc.player != null) mc.player.sendSystemMessage(Component.literal("§5Slayer §8> §f" + text)); }
}
