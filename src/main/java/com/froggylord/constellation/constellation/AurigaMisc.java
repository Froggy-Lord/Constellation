package com.froggylord.constellation.constellation;

import com.froggylord.constellation.core.BaseConstellation;
import com.froggylord.constellation.core.InitContext;
import com.froggylord.constellation.hud.HudManager;
import com.mojang.brigadier.CommandDispatcher;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;

public class AurigaMisc extends BaseConstellation {

    @Override public String id() { return "auriga"; }
    @Override public String displayName() { return "Auriga"; }
    @Override public String description() { return "experiments n stuff"; }

    @Override
    public void init(InitContext ctx) {
        AurigaExperiments.init((com.froggylord.constellation.config.AurigaConfig) config);
        AurigaChocolateFactory.init((com.froggylord.constellation.config.AurigaConfig) config);
        AurigaAnvilHelper.init((com.froggylord.constellation.config.AurigaConfig) config);
        AurigaBuffStatus.init((com.froggylord.constellation.config.AurigaConfig) config);
        AurigaReforgeHelper.init((com.froggylord.constellation.config.AurigaConfig) config);
        AurigaSkyblockGuide.init((com.froggylord.constellation.config.AurigaConfig) config);
        AurigaHoppityCollection.init((com.froggylord.constellation.config.AurigaConfig) config);
        AurigaHitmanCosts.init((com.froggylord.constellation.config.AurigaConfig) config);
        AurigaHoppityEventSummary.init((com.froggylord.constellation.config.AurigaConfig) config);
        AurigaUnclaimedEggs.init((com.froggylord.constellation.config.AurigaConfig) config);
        AurigaStrayTimer.init((com.froggylord.constellation.config.AurigaConfig) config);
    }

    @Override
    public void registerHud(HudManager hud) {
        var cfg = (com.froggylord.constellation.config.AurigaConfig) config;
        hud.register(new com.froggylord.constellation.hud.ExperimentHudWidget(
            com.froggylord.constellation.hud.HudPosition.of(50, 20),
            () -> cfg.enabled && cfg.experimentSolver && cfg.experimentHud));
        hud.register(new com.froggylord.constellation.hud.ChocolateFactoryHudWidget(
            com.froggylord.constellation.hud.HudPosition.of(50, 32),
            () -> cfg.enabled && cfg.chocolateFactoryHelper && cfg.chocolateFactoryHud));
        hud.register(new com.froggylord.constellation.hud.HoppityCollectionHudWidget(
            com.froggylord.constellation.hud.HudPosition.of(65, 32),
            () -> cfg.enabled && cfg.hoppityCollectionStats && cfg.hoppityCollectionHud));
        hud.register(new com.froggylord.constellation.hud.HitmanCostsHudWidget(
            com.froggylord.constellation.hud.HudPosition.of(65, 44),
            () -> cfg.enabled && cfg.chocolateFactoryHitmanCosts && cfg.chocolateFactoryHitmanCostsHud));
        hud.register(new com.froggylord.constellation.hud.HoppityEventHudWidget(
            com.froggylord.constellation.hud.HudPosition.of(65, 56),
            () -> cfg.enabled && cfg.hoppityEventSummary && cfg.hoppityEventSummaryHud));
        hud.register(new com.froggylord.constellation.hud.UnclaimedEggsHudWidget(
            com.froggylord.constellation.hud.HudPosition.of(80, 32),
            () -> cfg.enabled && cfg.hoppityUnclaimedEggs && cfg.hoppityUnclaimedEggsHud));
        hud.register(new com.froggylord.constellation.hud.StrayTimerHudWidget(
            com.froggylord.constellation.hud.HudPosition.of(80, 44),
            () -> cfg.enabled && cfg.chocolateFactoryStrayTimer && cfg.chocolateFactoryStrayTimerHud));
        hud.register(new com.froggylord.constellation.hud.AnvilHelperHudWidget(
            com.froggylord.constellation.hud.HudPosition.of(50, 44),
            () -> cfg.enabled && cfg.anvilHelper && cfg.anvilHud));
        hud.register(new com.froggylord.constellation.hud.BuffStatusHudWidget(
            com.froggylord.constellation.hud.HudPosition.of(50, 56),
            () -> cfg.enabled && cfg.buffStatus && cfg.buffStatusHud));
        hud.register(new com.froggylord.constellation.hud.ReforgeHudWidget(
            com.froggylord.constellation.hud.HudPosition.of(50, 68),
            () -> cfg.enabled && cfg.reforgeHelper && cfg.reforgeHud));
    }

    @Override
    public void registerCommands(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        AurigaExperiments.registerCommands(dispatcher);
        AurigaChocolateFactory.registerCommands(dispatcher);
        AurigaAnvilHelper.registerCommands(dispatcher);
        AurigaBuffStatus.registerCommands(dispatcher);
        AurigaReforgeHelper.registerCommands(dispatcher);
        AurigaSkyblockGuide.registerCommands(dispatcher);
        AurigaHoppityCollection.registerCommands(dispatcher);
        AurigaHitmanCosts.registerCommands(dispatcher);
        AurigaHoppityEventSummary.registerCommands(dispatcher);
        AurigaUnclaimedEggs.registerCommands(dispatcher);
        AurigaStrayTimer.registerCommands(dispatcher);
    }
}
