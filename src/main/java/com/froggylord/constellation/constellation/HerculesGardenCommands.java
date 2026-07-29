package com.froggylord.constellation.constellation;

import com.froggylord.constellation.ConstellationClient;
import com.froggylord.constellation.config.HerculesConfig;
import com.froggylord.constellation.core.LocationManager;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.Locale;

// ported from SkyHanni (LGPL-3.0-or-later): features/garden/GardenWarpCommands.kt
// ported from SkyHanni (LGPL-3.0-or-later): config/features/garden/GardenCommandsConfig.kt
public final class HerculesGardenCommands {
    private static HerculesConfig cfg;
    private static KeyMapping homeKey;
    private static KeyMapping setHomeKey;
    private static KeyMapping barnKey;
    private static long lastHotkey;

    private HerculesGardenCommands() {}

    public static void init(HerculesConfig config) {
        cfg = config;
        homeKey = ConstellationClient.instance().keys().register("garden_home", GLFW.GLFW_KEY_CAPS_LOCK);
        setHomeKey = ConstellationClient.instance().keys().register("garden_set_home", GLFW.GLFW_KEY_LEFT_ALT);
        barnKey = ConstellationClient.instance().keys().register("garden_barn", GLFW.GLFW_KEY_UNKNOWN);
        ClientTickEvents.END_CLIENT_TICK.register(client -> tick(client));
    }

    public static String rewrite(String command) {
        if (!active() || command == null) return null;
        String clean = command.trim();
        if (cfg.gardenCommandHome && clean.equalsIgnoreCase("home")) return "warp garden";
        if (cfg.gardenCommandBarn && clean.equalsIgnoreCase("barn")) return "tptoplot barn";
        if (cfg.gardenCommandPlot && clean.regionMatches(true, 0, "tp ", 0, 3)) {
            String plot = clean.substring(3).trim();
            if (!plot.isEmpty()) return "tptoplot " + plot;
        }
        return null;
    }

    public static void registerCommands(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        dispatcher.register(LiteralArgumentBuilder.<FabricClientCommandSource>literal("gardencommands")
            .executes(ctx -> status())
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("status").executes(ctx -> status()))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("option")
                .then(RequiredArgumentBuilder.<FabricClientCommandSource, String>argument("name", StringArgumentType.word())
                    .then(RequiredArgumentBuilder.<FabricClientCommandSource, String>argument("state", StringArgumentType.word())
                        .executes(ctx -> option(StringArgumentType.getString(ctx, "name"),
                            StringArgumentType.getString(ctx, "state")))))));
    }

    private static void tick(Minecraft client) {
        boolean home = homeKey.consumeClick();
        boolean setHome = setHomeKey.consumeClick();
        boolean barn = barnKey.consumeClick();
        if (!active() || client.gui.screen() != null || client.getConnection() == null) return;
        if (cfg.gardenHomeHotkey && home) send("warp garden");
        else if (cfg.gardenSetHomeHotkey && setHome) send("setspawn");
        else if (cfg.gardenBarnHotkey && barn) send("tptoplot barn");
    }

    private static void send(String command) {
        long now = System.currentTimeMillis();
        if (now - lastHotkey < Math.max(0, cfg.gardenCommandHotkeyCooldownMillis)) return;
        lastHotkey = now;
        Minecraft mc = Minecraft.getInstance();
        if (mc.getConnection() != null) mc.getConnection().sendCommand(command);
        if (cfg.gardenCommandHotkeyFeedback) local("Sent /" + command);
    }

    private static boolean active() {
        return cfg != null && cfg.enabled && cfg.gardenCommands
            && ConstellationClient.loc().area() == LocationManager.SkyblockArea.GARDEN;
    }

    private static int status() {
        local("Commands " + on(cfg.gardenCommands) + ": /home " + on(cfg.gardenCommandHome)
            + ", /barn " + on(cfg.gardenCommandBarn) + ", /tp <plot> " + on(cfg.gardenCommandPlot)
            + ". Hotkeys: home " + on(cfg.gardenHomeHotkey) + ", set home " + on(cfg.gardenSetHomeHotkey)
            + ", barn " + on(cfg.gardenBarnHotkey) + ".");
        return 1;
    }

    private static int option(String name, String state) {
        Boolean value = parseState(state);
        if (value == null) {
            local("Use on or off.");
            return 0;
        }
        switch (name.toLowerCase(Locale.ROOT)) {
            case "commands", "enabled" -> cfg.gardenCommands = value;
            case "home" -> cfg.gardenCommandHome = value;
            case "barn" -> cfg.gardenCommandBarn = value;
            case "plot", "tp" -> cfg.gardenCommandPlot = value;
            case "homekey" -> cfg.gardenHomeHotkey = value;
            case "sethomekey" -> cfg.gardenSetHomeHotkey = value;
            case "barnkey" -> cfg.gardenBarnHotkey = value;
            case "feedback" -> cfg.gardenCommandHotkeyFeedback = value;
            default -> {
                local("Options: commands, home, barn, plot, homekey, sethomekey, barnkey, feedback.");
                return 0;
            }
        }
        ConstellationClient.saveConfig();
        return status();
    }

    private static Boolean parseState(String state) {
        return switch (state.toLowerCase(Locale.ROOT)) {
            case "on", "true", "yes", "1" -> true;
            case "off", "false", "no", "0" -> false;
            default -> null;
        };
    }

    private static String on(boolean value) {
        return value ? "on" : "off";
    }

    private static void local(String message) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) mc.player.sendSystemMessage(Component.literal("§2[Garden] §f" + message));
    }
}
