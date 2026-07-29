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
    @Override public void init(InitContext ctx){ArtemisHuntingProfit.init((ArtemisConfig)config);}
    @Override public void registerHud(HudManager hud){
        ArtemisConfig cfg=(ArtemisConfig)config;
        hud.register(new com.froggylord.constellation.hud.HuntingProfitHudWidget(
            HudPosition.of(78,10),()->cfg.enabled&&cfg.huntingProfitTracker&&cfg.huntingProfitHud));
    }
    @Override public void registerCommands(CommandDispatcher<FabricClientCommandSource> dispatcher){
        ArtemisHuntingProfit.registerCommands(dispatcher);
    }
}
