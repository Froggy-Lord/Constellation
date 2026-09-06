package com.froggylord.constellation.constellation;

import com.froggylord.constellation.core.BaseConstellation;
import com.froggylord.constellation.core.InitContext;
import com.froggylord.constellation.hud.HudManager;
import com.mojang.brigadier.CommandDispatcher;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;

public class ApolloHud extends BaseConstellation {

    @Override public String id() { return "apollo"; }
    @Override public String displayName() { return "Apollo"; }
    @Override public String description() { return "hud overlay stuff"; }

    @Override
    public void init(InitContext ctx) {
    }

    @Override
    public void registerHud(HudManager hud) {
    }

    @Override
    public void registerCommands(CommandDispatcher<FabricClientCommandSource> dispatcher) {
    }
}
