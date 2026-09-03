package com.froggylord.constellation.constellation;

import com.froggylord.constellation.ConstellationClient;
import com.froggylord.constellation.config.PerseusConfig;
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
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;

import java.util.Locale;

// ported from Athen (BSD-3-Clause): modules/impl/slayer/CocoonAlert.kt
// title and sound defaults ported from NoFrills (GPL-3.0): features/slayer/CocoonAlert.java
public final class SlayerCocoon {
    private static final String TRIGGER = "YOU COCOONED YOUR SLAYER BOSS";
    private static PerseusConfig cfg;
    private static long expiresAtNanos;
    private static boolean initialized;

    private SlayerCocoon() {}

    public static void init(PerseusConfig config) {
        cfg = config;
        normalize();
        if (initialized) return;
        initialized = true;
        ClientReceiveMessageEvents.GAME.register((message, overlay) -> {
            if (overlay || !active()) return;
            String stripped = ChatFormatting.stripFormatting(message.getString());
            String text = stripped == null ? message.getString().trim() : stripped.trim();
            if (TRIGGER.equals(text)) activate();
        });
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> reset());
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> reset());
    }

    public static String hudLine() {
        if (!active() || !cfg.cocoonTimerHud) return "";
        long left = expiresAtNanos - System.nanoTime();
        if (left <= 0) { reset(); return ""; }
        String time = format(left / 1_000_000_000.0);
        return cfg.cocoonTimerStyle.replace("{time}", time).replace("{seconds}", time);
    }

    public static void registerCommands(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        dispatcher.register(LiteralArgumentBuilder.<FabricClientCommandSource>literal("cocoonalert")
            .executes(c -> status())
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("status").executes(c -> status()))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("test").executes(c -> test()))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("duration")
                .then(RequiredArgumentBuilder.<FabricClientCommandSource, Integer>argument("ticks", IntegerArgumentType.integer(20, 300))
                    .executes(c -> duration(IntegerArgumentType.getInteger(c, "ticks")))))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("titleduration")
                .then(RequiredArgumentBuilder.<FabricClientCommandSource, Integer>argument("ticks", IntegerArgumentType.integer(10, 100))
                    .executes(c -> titleDuration(IntegerArgumentType.getInteger(c, "ticks")))))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("decimals")
                .then(RequiredArgumentBuilder.<FabricClientCommandSource, Integer>argument("value", IntegerArgumentType.integer(0, 2))
                    .executes(c -> decimals(IntegerArgumentType.getInteger(c, "value")))))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("message")
                .then(RequiredArgumentBuilder.<FabricClientCommandSource, String>argument("text", StringArgumentType.greedyString())
                    .executes(c -> style(false, StringArgumentType.getString(c, "text")))))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("timer")
                .then(RequiredArgumentBuilder.<FabricClientCommandSource, String>argument("text", StringArgumentType.greedyString())
                    .executes(c -> style(true, StringArgumentType.getString(c, "text")))))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("option")
                .then(RequiredArgumentBuilder.<FabricClientCommandSource, String>argument("name", StringArgumentType.word())
                    .then(RequiredArgumentBuilder.<FabricClientCommandSource, String>argument("state", StringArgumentType.word())
                        .executes(c -> option(StringArgumentType.getString(c, "name"), StringArgumentType.getString(c, "state")))))));
    }

    private static void activate() {
        expiresAtNanos = System.nanoTime() + cfg.cocoonDurationTicks * 50_000_000L;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        String time = format(cfg.cocoonDurationTicks / 20.0);
        String alert = cfg.cocoonAlertMessage.replace("{time}", time).replace("{seconds}", time);
        if (cfg.cocoonAlertTitle) {
            mc.gui.hud.setTimes(0, cfg.cocoonTitleDurationTicks, 10);
            mc.gui.hud.setTitle(Component.literal(alert));
        }
        if (cfg.cocoonAlertChat) local(alert);
        if (cfg.cocoonAlertSound) mc.player.playSound(SoundEvents.NOTE_BLOCK_PLING.value(), 1.0f, 0.0f);
    }

    private static int status() {
        local("§eCocoon alert: " + on(cfg.cocoonAlert) + ", HUD " + on(cfg.cocoonTimerHud) + ", title "
            + on(cfg.cocoonAlertTitle) + ", chat " + on(cfg.cocoonAlertChat) + ", sound " + on(cfg.cocoonAlertSound)
            + ", timer " + cfg.cocoonDurationTicks + " ticks, title " + cfg.cocoonTitleDurationTicks + " ticks.");
        local("§7Variables for message and timer: {time}, {seconds}.");
        return 1;
    }

    private static int duration(int ticks) { cfg.cocoonDurationTicks = Math.clamp(ticks, 20, 300); return save("Cocoon duration updated."); }
    private static int titleDuration(int ticks) { cfg.cocoonTitleDurationTicks = Math.clamp(ticks, 10, 100); return save("Cocoon title duration updated."); }
    private static int decimals(int value) { cfg.cocoonTimerDecimals = Math.clamp(value, 0, 2); return save("Cocoon precision updated."); }
    private static int test() { if (!active()) { local("§cEnable Perseus and the Cocoon alert first."); return 0; } activate(); return 1; }
    private static int style(boolean timer, String value) {
        String clean = value.trim();
        if (clean.isEmpty() || clean.length() > 200) { local("§cText must be 1-200 characters."); return 0; }
        if (timer && !clean.contains("{time}") && !clean.contains("{seconds}")) {
            local("§cTimer text must contain {time} or {seconds}."); return 0;
        }
        if (timer) cfg.cocoonTimerStyle = clean; else cfg.cocoonAlertMessage = clean;
        return save("Cocoon " + (timer ? "timer" : "alert") + " text updated.");
    }

    private static int option(String name, String state) {
        Boolean value = parseState(state);
        if (value == null) { local("§cState must be on or off."); return 0; }
        switch (name.toLowerCase(Locale.ROOT)) {
            case "enabled", "alert" -> cfg.cocoonAlert = value;
            case "hud", "timer" -> cfg.cocoonTimerHud = value;
            case "title" -> cfg.cocoonAlertTitle = value;
            case "chat" -> cfg.cocoonAlertChat = value;
            case "sound" -> cfg.cocoonAlertSound = value;
            default -> { local("§cOption must be enabled, hud, title, chat, or sound."); return 0; }
        }
        if (!cfg.cocoonAlert || !cfg.cocoonTimerHud) reset();
        return save("Cocoon " + name + " " + (value ? "enabled." : "disabled."));
    }

    private static void normalize() {
        if (cfg == null) return;
        cfg.cocoonDurationTicks = Math.clamp(cfg.cocoonDurationTicks, 20, 300);
        cfg.cocoonTitleDurationTicks = Math.clamp(cfg.cocoonTitleDurationTicks, 10, 100);
        cfg.cocoonTimerDecimals = Math.clamp(cfg.cocoonTimerDecimals, 0, 2);
        if (cfg.cocoonAlertMessage == null || cfg.cocoonAlertMessage.isBlank()) cfg.cocoonAlertMessage = "§c§lBOSS COCOONED!";
        if (cfg.cocoonTimerStyle == null || cfg.cocoonTimerStyle.isBlank()) cfg.cocoonTimerStyle = "Cocoon: §c{time}";
    }

    private static boolean active() { return cfg != null && cfg.enabled && cfg.cocoonAlert && ConstellationClient.loc().onHypixel(); }
    private static void reset() { expiresAtNanos = 0; }
    private static String format(double seconds) { return String.format(Locale.ROOT, "%." + cfg.cocoonTimerDecimals + "fs", seconds); }
    private static Boolean parseState(String state) { return switch (state.toLowerCase(Locale.ROOT)) { case "on", "true", "yes", "1" -> true; case "off", "false", "no", "0" -> false; default -> null; }; }
    private static String on(boolean value) { return value ? "§aon" : "§coff"; }
    private static int save(String text) { ConstellationClient.saveConfig(); local("§a" + text); return 1; }
    private static void local(String text) { Minecraft mc = Minecraft.getInstance(); if (mc.player != null) mc.player.sendSystemMessage(Component.literal("§5Slayer §8> §f" + text)); }
}
