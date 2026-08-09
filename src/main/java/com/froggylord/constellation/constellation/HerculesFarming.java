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
        HerculesNoRodBreak.init(cfg);
        HerculesCarrolynHelper.init(cfg);
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
        HerculesToolkitCropIcons.init(cfg);
        HerculesMouseSensitivity.init(cfg);
        HerculesGardenCommands.init(cfg);
        HerculesCropMilestones.init(cfg);
        HerculesHoeLevel.init(cfg);
        HerculesDnaAnalyzer.init(cfg);
        HerculesFarmingLanes.init(cfg);
        HerculesComposter.init(cfg);
        HerculesJacobUpcoming.init(cfg);
        HerculesJacobHistory.init(cfg);
        HerculesCropMoney.init(cfg);
        HerculesGardenLevel.init(cfg);
        HerculesRareCropTracker.init(cfg);
        HerculesAnitaShop.init(cfg);
        HerculesVisitorLogbook.init(cfg);
        HerculesPesthunterShop.init(cfg);
        HerculesPlotPrices.init(cfg);
        HerculesEliteLeaderboards.init(cfg);
        registerRenderer(HerculesFarmingLanes::draw);
        registerRenderer(HerculesPests::draw);
        registerRenderer(HerculesPestWaypoint::draw);
        registerRenderer(HerculesCropLocations::draw);
        registerRenderer(HerculesCarrolynHelper::draw);
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
        hud.register(new com.froggylord.constellation.hud.HudWidget(
            "garden-mouse-sensitivity", "Mouse", HerculesMouseSensitivity::hudText,
            com.froggylord.constellation.hud.HudPosition.of(2, 114),
            () -> cfg.enabled && cfg.mouseSensitivityHelper && cfg.mouseSensitivityHud));
        hud.register(new com.froggylord.constellation.hud.CropMilestoneHudWidget(
            com.froggylord.constellation.hud.HudPosition.of(76, 114),
            () -> cfg.enabled && cfg.cropMilestoneProgress));
        hud.register(new com.froggylord.constellation.hud.HoeLevelHudWidget(
            com.froggylord.constellation.hud.HudPosition.of(76, 120),
            () -> cfg.enabled && cfg.hoeLevelDisplay));
        hud.register(new com.froggylord.constellation.hud.HudWidget(
            "garden-dna-analyzer", "DNA", HerculesDnaAnalyzer::hudText,
            com.froggylord.constellation.hud.HudPosition.of(50, 50),
            () -> cfg.enabled && cfg.dnaAnalyzerSolver && cfg.dnaAnalyzerHud));
        hud.register(new com.froggylord.constellation.hud.FarmingLaneHudWidget(
            com.froggylord.constellation.hud.HudPosition.of(50, 56),
            () -> cfg.enabled && cfg.farmingLaneDistanceDisplay));
        hud.register(new com.froggylord.constellation.hud.ComposterHudWidget(
            com.froggylord.constellation.hud.HudPosition.of(50, 62),
            () -> cfg.enabled && cfg.composterHelper && (cfg.composterDisplay || cfg.composterDisplayOutsideGarden)));
        hud.register(new com.froggylord.constellation.hud.UpcomingJacobHudWidget(
            com.froggylord.constellation.hud.HudPosition.of(50, 68),
            () -> cfg.enabled && cfg.jacobUpcomingDisplay));
        hud.register(new com.froggylord.constellation.hud.JacobPlannerHudWidget(
            com.froggylord.constellation.hud.HudPosition.of(50, 74),
            () -> cfg.enabled && cfg.jacobContestHistory && (cfg.jacobContestTimeNeeded || cfg.jacobContestFfNeeded)));
        hud.register(new com.froggylord.constellation.hud.CropMoneyHudWidget(
            com.froggylord.constellation.hud.HudPosition.of(50, 80),
            () -> cfg.enabled && cfg.cropMoneyDisplay));
        hud.register(new com.froggylord.constellation.hud.GardenLevelHudWidget(
            com.froggylord.constellation.hud.HudPosition.of(50, 86),
            () -> cfg.enabled && cfg.gardenLevelDisplay));
        hud.register(new com.froggylord.constellation.hud.RareCropHudWidget(
            com.froggylord.constellation.hud.HudPosition.of(50, 92),
            () -> cfg.enabled && cfg.rareCropTracker));
        hud.register(new com.froggylord.constellation.hud.EliteLeaderboardHudWidget(
            com.froggylord.constellation.hud.HudPosition.of(50, 98), HerculesEliteLeaderboards.Kind.WEIGHT,
            () -> cfg.enabled && cfg.eliteLeaderboards && cfg.eliteWeightLeaderboard));
        hud.register(new com.froggylord.constellation.hud.EliteLeaderboardHudWidget(
            com.froggylord.constellation.hud.HudPosition.of(50, 104), HerculesEliteLeaderboards.Kind.CROP,
            () -> cfg.enabled && cfg.eliteLeaderboards && cfg.eliteCropLeaderboard));
        hud.register(new com.froggylord.constellation.hud.EliteLeaderboardHudWidget(
            com.froggylord.constellation.hud.HudPosition.of(50, 110), HerculesEliteLeaderboards.Kind.PEST,
            () -> cfg.enabled && cfg.eliteLeaderboards && cfg.elitePestLeaderboard));
    }

    @Override
    public void registerCommands(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        HerculesVisitorHelper.registerCommands(dispatcher);
        HerculesNoRodBreak.registerCommands(dispatcher);
        HerculesCarrolynHelper.registerCommands(dispatcher);
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
        HerculesToolkitCropIcons.registerCommands(dispatcher);
        HerculesMouseSensitivity.registerCommands(dispatcher);
        HerculesGardenCommands.registerCommands(dispatcher);
        HerculesCropMilestones.registerCommands(dispatcher);
        HerculesHoeLevel.registerCommands(dispatcher);
        HerculesDnaAnalyzer.registerCommands(dispatcher);
        HerculesFarmingLanes.registerCommands(dispatcher);
        HerculesComposter.registerCommands(dispatcher);
        HerculesJacobUpcoming.registerCommands(dispatcher);
        HerculesJacobHistory.registerCommands(dispatcher);
        HerculesCropMoney.registerCommands(dispatcher);
        HerculesGardenLevel.registerCommands(dispatcher);
        HerculesRareCropTracker.registerCommands(dispatcher);
        HerculesAnitaShop.registerCommands(dispatcher);
        HerculesVisitorLogbook.registerCommands(dispatcher);
        HerculesPesthunterShop.registerCommands(dispatcher);
        HerculesPlotPrices.registerCommands(dispatcher);
        HerculesEliteLeaderboards.registerCommands(dispatcher);
    }
}
