package com.froggylord.constellation.constellation;

import com.froggylord.constellation.config.ArtemisConfig;
import com.froggylord.constellation.core.BaseConstellation;
import com.froggylord.constellation.core.InitContext;
import com.froggylord.constellation.hud.HudManager;
import com.froggylord.constellation.hud.HudPosition;
import com.mojang.brigadier.CommandDispatcher;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;

public final class ArtemisHunting extends BaseConstellation {
    @Override public String id(){return"artemis";}
    @Override public String displayName(){return"Artemis";}
    @Override public String description(){return"hunting and foraging";}
    @Override public void init(InitContext ctx){
        ArtemisConfig cfg=(ArtemisConfig)config;
        ArtemisHuntingProfit.init(cfg);
        ArtemisHuntingTargets.init(cfg);
        ArtemisLasso.init(cfg);
        ArtemisFusion.init(cfg);
        ArtemisShardTracker.init(cfg);
        ArtemisFusionKeybinds.init(cfg);
        ArtemisHuntaxeLock.init(cfg);
        registerRenderer(ArtemisHuntingTargets::draw);
    }
    @Override public void registerHud(HudManager hud){
        ArtemisConfig cfg=(ArtemisConfig)config;
        hud.register(new com.froggylord.constellation.hud.HuntingProfitHudWidget(
            HudPosition.of(78,10),()->cfg.enabled&&cfg.huntingProfitTracker&&cfg.huntingProfitHud));
        hud.register(new com.froggylord.constellation.hud.LassoHudWidget(
            HudPosition.of(78,22),()->cfg.enabled&&cfg.lassoDisplay));
        hud.register(new com.froggylord.constellation.hud.FusionHudWidget(
            HudPosition.of(78,34),()->cfg.enabled&&cfg.fusionDisplay));
        hud.register(new com.froggylord.constellation.hud.ShardTrackerHudWidget(
            HudPosition.of(78,46),()->cfg.enabled&&cfg.shardTracker&&cfg.shardTrackerHud));
    }
    @Override public void registerCommands(CommandDispatcher<FabricClientCommandSource> dispatcher){
        ArtemisHuntingProfit.registerCommands(dispatcher);
        ArtemisHuntingTargets.registerCommands(dispatcher);
        ArtemisLasso.registerCommands(dispatcher);
        ArtemisFusion.registerCommands(dispatcher);
        ArtemisShardTracker.registerCommands(dispatcher);
        ArtemisFusionKeybinds.registerCommands(dispatcher);
        ArtemisHuntaxeLock.registerCommands(dispatcher);
    }
}
