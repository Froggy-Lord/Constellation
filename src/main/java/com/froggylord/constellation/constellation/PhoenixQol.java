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
        PhoenixInputControls.init((PhoenixConfig) config);
        PhoenixSignCalculator.init((PhoenixConfig) config);
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
        PhoenixInputControls.registerCommands(dispatcher);
        PhoenixSignCalculator.registerCommands(dispatcher);
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
            HudPosition.of(2, 59), () -> cfg.enabled && cfg.petDisplay && cfg.petDisplayHud));
        hud.register(new com.froggylord.constellation.hud.CollectionTrackerHudWidget(
            HudPosition.of(76, 44), () -> cfg.enabled && cfg.collectionTracker && cfg.collectionTrackerHud));
    }

}
