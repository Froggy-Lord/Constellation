package com.froggylord.constellation.constellation;

import com.froggylord.constellation.ConstellationClient;
import com.froggylord.constellation.config.PhoenixConfig;
import com.froggylord.constellation.core.BaseConstellation;
import com.froggylord.constellation.core.InitContext;
import com.froggylord.constellation.hud.HudManager;
import com.froggylord.constellation.hud.HudPosition;
import com.froggylord.constellation.render.WorldRenderer;
import com.mojang.brigadier.CommandDispatcher;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

public class PhoenixQol extends BaseConstellation {

    @Override public String id() { return "phoenix"; }
    @Override public String displayName() { return "Phoenix"; }
    @Override public String description() { return "qol tweaks"; }

    @Override
    public void init(InitContext ctx) {
        PhoenixWardrobeKeybinds.init((PhoenixConfig) config);
        PhoenixSlotBinding.init((PhoenixConfig) config);
        PhoenixCenturyCake.init((PhoenixConfig) config);
        PhoenixWorldAge.init((PhoenixConfig) config);
        PhoenixScreenshotClipboard.init((PhoenixConfig) config);
        PhoenixSpeedPresets.init((PhoenixConfig) config);
        PhoenixPetDisplay.init((PhoenixConfig) config);
        PhoenixCollectionTracker.init((PhoenixConfig) config);
    }

    @Override
    public void registerCommands(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        PhoenixWardrobeKeybinds.registerCommands(dispatcher);
        PhoenixSlotBinding.registerCommands(dispatcher);
        PhoenixCenturyCake.registerCommands(dispatcher);
        PhoenixWorldAge.registerCommands(dispatcher);
        PhoenixScreenshotClipboard.registerCommands(dispatcher);
        PhoenixSpeedPresets.registerCommands(dispatcher);
        PhoenixPetDisplay.registerCommands(dispatcher);
        PhoenixCollectionTracker.registerCommands(dispatcher);
    }

    @Override
    public void registerHud(HudManager hud) {
        PhoenixConfig cfg = (PhoenixConfig) config;
        hud.register(new com.froggylord.constellation.hud.CenturyCakeHudWidget(
            HudPosition.of(76, 26), () -> cfg.enabled && cfg.centuryCakeTimer && cfg.centuryCakeHud));
        hud.register(new com.froggylord.constellation.hud.WorldAgeHudWidget(
            HudPosition.of(2, 26), () -> cfg.enabled && cfg.worldAge && cfg.worldAgeHud));
        hud.register(new com.froggylord.constellation.hud.SpeedPresetHudWidget(
            HudPosition.of(76, 32), () -> cfg.enabled && cfg.speedPresets && cfg.speedPresetsHud));
        hud.register(new com.froggylord.constellation.hud.PetDisplayHudWidget(
            HudPosition.of(76, 38), () -> cfg.enabled && cfg.petDisplay && cfg.petDisplayHud));
        hud.register(new com.froggylord.constellation.hud.CollectionTrackerHudWidget(
            HudPosition.of(76, 44), () -> cfg.enabled && cfg.collectionTracker && cfg.collectionTrackerHud));
    }

    private static long lastSaveAt = 0;
    private static int hotbarLockSlot = 0;

    private static void evaluateSign(String expr) {
        
        String[] parts = expr.split("\\s+");
        if (parts.length < 3) return;
        double a = Double.parseDouble(parts[0].replace(",", ""));
        double b = Double.parseDouble(parts[2].replace(",", ""));
        double result = switch (parts[1]) {
            case "*", "x", "×" -> a * b;
            case "+" -> a + b;
            case "-" -> a - b;
            case "/" -> b != 0 ? a / b : 0;
            default -> throw new IllegalArgumentException();
        };
        var mc = Minecraft.getInstance();
        if (mc.player != null) {
            String out = "§eSign calc: §f" + a + " " + parts[1] + " " + b + " = §a" + format(result);
            mc.player.sendSystemMessage(net.minecraft.network.chat.Component.literal(out));
        }
    }

    private static String format(double n) {
        if (n == Math.floor(n) && n < Long.MAX_VALUE) return String.format("%,d", (long) n);
        return String.format("%,.1f", n);
    }
}
