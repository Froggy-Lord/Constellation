package com.froggylord.constellation.constellation;

import com.froggylord.constellation.ConstellationClient;
import com.froggylord.constellation.config.PegasusConfig;
import com.froggylord.constellation.core.BaseConstellation;
import com.froggylord.constellation.core.InitContext;
import com.froggylord.constellation.hud.HudManager;
import com.froggylord.constellation.hud.HudPosition;
import com.froggylord.constellation.hud.HudWidget;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.Minecraft;

/**
 * Pegasus — party + social. /rp reparty command, /pl alias, party size HUD widget.
 * Full party triggers + carry mode come later.
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
    public void registerHud(HudManager hud) {
        if (cfg == null) return;
        hud.register(new HudWidget("pegasus-party", "Party",
            () -> {
                if (!ConstellationClient.loc().onHypixel()) return null;
                var mc = Minecraft.getInstance();
                if (mc.getConnection() == null) return null;
                int n = mc.getConnection().getOnlinePlayers().size();
                return "§dParty: " + n;
            },
            HudPosition.of(2, 110), true));
    }

    @Override
    public void registerCommands(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        if (cfg == null || !cfg.autoRejoin) return;
        dispatcher.register(
            LiteralArgumentBuilder.<FabricClientCommandSource>literal("rp")
                .executes(ctx -> {
                    var mc = Minecraft.getInstance();
                    if (mc.player != null) mc.player.connection.sendCommand("p rejoin");
                    return 1;
                }));
        dispatcher.register(
            LiteralArgumentBuilder.<FabricClientCommandSource>literal("pl")
                .executes(ctx -> {
                    var mc = Minecraft.getInstance();
                    if (mc.player != null) mc.player.connection.sendCommand("p list");
                    return 1;
                }));
    }
}
