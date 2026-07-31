package com.froggylord.constellation.constellation;

import com.froggylord.constellation.ConstellationClient;
import com.froggylord.constellation.config.PhoenixConfig;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

// ported from NoFrills (GPL-3.0-only): features/general/AutoSprint.java
public final class PhoenixInputControls {
    private static PhoenixConfig cfg;
    private static boolean forcedSprint;

    private PhoenixInputControls() {}

    public static void init(PhoenixConfig config) {
        cfg = config;
        ConstellationClient.tick().every(1, "phoenix-input-controls", PhoenixInputControls::tick);
    }

    private static void tick() {
        Minecraft minecraft = Minecraft.getInstance();
        boolean active = cfg != null && cfg.enabled && cfg.autoSprint && minecraft.player != null
            && (cfg.autoSprintInWater || !minecraft.player.isInWater());
        if (active) {
            setSprintKey(minecraft, true);
            forcedSprint = true;
        } else if (forcedSprint) {
            minecraft.options.keySprint.setDown(false);
            forcedSprint = false;
        }
    }

    private static void setSprintKey(Minecraft minecraft, boolean sprinting) {
        if (minecraft.options.toggleSprint().get()) {
            if (minecraft.options.keySprint.isDown() == !sprinting) minecraft.options.keySprint.setDown(true);
        } else {
            minecraft.options.keySprint.setDown(sprinting);
        }
    }

    public static void registerCommands(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        dispatcher.register(LiteralArgumentBuilder.<FabricClientCommandSource>literal("phoenixinput")
            .executes(context -> status())
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("option")
                .then(RequiredArgumentBuilder.<FabricClientCommandSource, String>argument("name", StringArgumentType.word())
                    .then(RequiredArgumentBuilder.<FabricClientCommandSource, String>argument("state", StringArgumentType.word())
                        .executes(context -> option(StringArgumentType.getString(context, "name"),
                            StringArgumentType.getString(context, "state")))))));
    }

    private static int option(String name, String raw) {
        Boolean value = state(raw);
        if (value == null) {
            local("State must be on or off.");
            return 0;
        }
        switch (name.toLowerCase(java.util.Locale.ROOT)) {
            case "autosprint" -> cfg.autoSprint = value;
            case "water" -> cfg.autoSprintInWater = value;
            case "instantsneak" -> cfg.instantSneak = value;
            case "scrolllock" -> cfg.hotbarScrollLock = value;
            case "arrows" -> cfg.hideAttachedArrows = value;
            case "effects" -> cfg.hideStatusEffects = value;
            case "signenter" -> cfg.signEnterToDone = value;
            default -> {
                local("Option must be autosprint, water, instantsneak, scrolllock, arrows, effects, or signenter.");
                return 0;
            }
        }
        ConstellationClient.saveConfig();
        return status();
    }

    private static int status() {
        local("Phoenix input: auto sprint " + on(cfg.autoSprint) + ", water " + on(cfg.autoSprintInWater)
            + ", instant sneak " + on(cfg.instantSneak) + ", scroll lock " + on(cfg.hotbarScrollLock)
            + ", arrows " + on(cfg.hideAttachedArrows) + ", effects " + on(cfg.hideStatusEffects)
            + ", sign Enter " + on(cfg.signEnterToDone) + ".");
        return 1;
    }

    private static Boolean state(String raw) {
        return switch (raw.toLowerCase(java.util.Locale.ROOT)) {
            case "on", "true", "yes", "1" -> true;
            case "off", "false", "no", "0" -> false;
            default -> null;
        };
    }

    private static String on(boolean value) { return value ? "on" : "off"; }

    private static void local(String text) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player != null) minecraft.player.sendSystemMessage(Component.literal("§6[Phoenix] §f" + text));
    }
}
