package com.froggylord.constellation.constellation;

import com.froggylord.constellation.core.BaseConstellation;
import com.froggylord.constellation.core.InitContext;
import com.froggylord.constellation.hud.HudManager;
import com.froggylord.constellation.hud.HudPosition;
import com.mojang.brigadier.CommandDispatcher;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;

public class ApolloHud extends BaseConstellation {

    @Override public String id() { return "apollo"; }
    @Override public String displayName() { return "Apollo"; }
    @Override public String description() { return "hud overlay stuff"; }

    @Override
    public void init(InitContext ctx) {
        ApolloTelemetry.init((com.froggylord.constellation.config.ApolloConfig) config);
    }

    @Override
    public void registerHud(HudManager hud) {
        var cfg = (com.froggylord.constellation.config.ApolloConfig) config;
        hud.register(new com.froggylord.constellation.hud.PerformanceHudWidget(
            HudPosition.of(2, 2), () -> cfg.enabled && cfg.performanceHud));
        hud.register(new com.froggylord.constellation.hud.LocationHudWidget(
            HudPosition.of(2, 14), () -> cfg.enabled && cfg.locationHud));
        hud.register(new com.froggylord.constellation.hud.MovementHudWidget(
            HudPosition.of(2, 38), () -> cfg.enabled && cfg.movementHud));
        hud.register(new com.froggylord.constellation.hud.VitalsHudWidget(
            HudPosition.of(76, 2), () -> cfg.enabled && cfg.vitalsHud));
        hud.register(new com.froggylord.constellation.hud.EffectsHudWidget(
            HudPosition.of(76, 14), () -> cfg.enabled && cfg.effectsHud));
    }

    @Override
    public void registerCommands(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        ApolloTelemetry.registerCommands(dispatcher);
    }
}
