package com.froggylord.constellation.constellation;

import com.froggylord.constellation.ConstellationClient;
import com.froggylord.constellation.config.PegasusConfig;
import com.froggylord.constellation.core.BaseConstellation;
import com.froggylord.constellation.core.InitContext;

/**
 * Pegasus — party + social. Binds /rp as a client-side shortcut to Hypixel's reparty command
 * so re-grouping after a dungeon is instant. Full party triggers + carry mode come later.
 */
public class PegasusParty extends BaseConstellation {

    @Override public String id() { return "pegasus"; }
    @Override public String displayName() { return "Pegasus"; }
    @Override public String description() { return "Party — /rp reparty, triggers, carry mode"; }

    private PegasusConfig cfg;

    @Override
    public void init(InitContext ctx) {
        cfg = (PegasusConfig) getConfig();
    }

    @Override
    public void registerCommands(com.mojang.brigadier.CommandDispatcher<net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource> dispatcher) {
        if (cfg == null || !cfg.autoRejoin) return;
        dispatcher.register(
            com.mojang.brigadier.builder.LiteralArgumentBuilder
                .<net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource>literal("rp")
                .executes(ctx -> {
                    var mc = net.minecraft.client.Minecraft.getInstance();
                    if (mc.player != null) mc.player.connection.sendCommand("p rejoin");
                    return 1;
                }));
    }
}
