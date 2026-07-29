package com.froggylord.constellation.constellation;

import com.froggylord.constellation.core.BaseConstellation;
import com.froggylord.constellation.core.InitContext;
import com.froggylord.constellation.hud.HudManager;
import com.mojang.brigadier.CommandDispatcher;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;

public class HerculesFarming extends BaseConstellation {

    @Override public String id() { return "hercules"; }
    @Override public String displayName() { return "Hercules"; }
    @Override public String description() { return "farming/garden hud"; }

    @Override
    public void init(InitContext ctx) {
        var cfg = (com.froggylord.constellation.config.HerculesConfig) config;
        HerculesVisitorHelper.init(cfg);
        HerculesGardenTracker.init(cfg);
        HerculesPests.init(cfg);
        HerculesPestWaypoint.init(cfg);
        HerculesSprays.init(cfg);
        HerculesStereoHarmony.init(cfg);
        HerculesGreenhouse.init(cfg);
        HerculesFortune.init(cfg);
        HerculesPlotMenu.init(cfg);
        HerculesPlotIcons.init(cfg);
        HerculesCropLocations.init(cfg);
        registerRenderer(HerculesPests::draw);
        registerRenderer(HerculesPestWaypoint::draw);
        registerRenderer(HerculesCropLocations::draw);
    }

    @Override
    public void registerHud(HudManager hud) {
        var cfg = (com.froggylord.constellation.config.HerculesConfig) config;
        hud.register(new com.froggylord.constellation.hud.GardenVisitorHudWidget(
            com.froggylord.constellation.hud.HudPosition.of(76, 38),
            () -> cfg.enabled && cfg.visitorHelper && cfg.visitorShoppingList));
        hud.register(new com.froggylord.constellation.hud.GardenControlHudWidget(
            com.froggylord.constellation.hud.HudPosition.of(2, 42),
            () -> cfg.enabled && cfg.farmingControlHud));
        hud.register(new com.froggylord.constellation.hud.GardenRateHudWidget(
            com.froggylord.constellation.hud.HudPosition.of(2, 58),
            () -> cfg.enabled && cfg.farmingRateHud));
        hud.register(new com.froggylord.constellation.hud.JacobContestHudWidget(
            com.froggylord.constellation.hud.HudPosition.of(78, 58),
            () -> cfg.enabled && cfg.jacobContestHud));
        hud.register(new com.froggylord.constellation.hud.GardenPestHudWidget(
            com.froggylord.constellation.hud.HudPosition.of(2, 72),
            () -> cfg.enabled && cfg.pestCore && cfg.pestFinderHud));
        hud.register(new com.froggylord.constellation.hud.GardenPestTimerHudWidget(
            com.froggylord.constellation.hud.HudPosition.of(2, 84),
            () -> cfg.enabled && cfg.pestCore && cfg.pestTimerHud));
        hud.register(new com.froggylord.constellation.hud.GardenPestStatsHudWidget(
            com.froggylord.constellation.hud.HudPosition.of(76, 72),
            () -> cfg.enabled && cfg.pestCore && cfg.pestStatsHud));
        hud.register(new com.froggylord.constellation.hud.GardenSprayHudWidget(
            com.froggylord.constellation.hud.HudPosition.of(76, 84),
            () -> cfg.enabled && cfg.sprayTracker && cfg.sprayHud));
        hud.register(new com.froggylord.constellation.hud.GardenStereoHudWidget(
            com.froggylord.constellation.hud.HudPosition.of(76, 96),
            () -> cfg.enabled && cfg.stereoHarmony && cfg.stereoDisplay));
        hud.register(new com.froggylord.constellation.hud.GreenhouseGrowthHudWidget(
            com.froggylord.constellation.hud.HudPosition.of(76, 108),
            () -> cfg.enabled && cfg.greenhouseHelper && cfg.greenhouseGrowth));
        hud.register(new com.froggylord.constellation.hud.FarmingFortuneHudWidget(
            com.froggylord.constellation.hud.HudPosition.of(2, 108),
            () -> cfg.enabled && cfg.fortuneHelper && cfg.fortuneDisplay));
    }

    @Override
    public void registerCommands(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        HerculesVisitorHelper.registerCommands(dispatcher);
        HerculesGardenTracker.registerCommands(dispatcher);
        HerculesPests.registerCommands(dispatcher);
        HerculesPestWaypoint.registerCommands(dispatcher);
        HerculesSprays.registerCommands(dispatcher);
        HerculesStereoHarmony.registerCommands(dispatcher);
        HerculesGreenhouse.registerCommands(dispatcher);
        HerculesFortune.registerCommands(dispatcher);
        HerculesPlotMenu.registerCommands(dispatcher);
        HerculesPlotIcons.registerCommands(dispatcher);
        HerculesCropLocations.registerCommands(dispatcher);
    }
}
