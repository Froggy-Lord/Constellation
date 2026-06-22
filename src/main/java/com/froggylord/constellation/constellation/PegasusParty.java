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

    private static final java.util.Map<String, Integer> carryLedger = new java.util.LinkedHashMap<>();

    @Override
    public void init(InitContext ctx) {
        cfg = (PegasusConfig) getConfig();
        if (cfg != null && cfg.trackParty) PartyTracker.init();
        if (cfg != null && cfg.carryMode) {
            net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents.GAME.register((msg, overlay) -> {
                if (overlay || !ConstellationClient.loc().onHypixel()) return;
                String s = msg.getString();
                // "!paid" or "!paid <player>" or "/pc !paid <name>"
                if (s.contains("!paid") || s.contains("paid")) {
                    var m = java.util.regex.Pattern.compile("!?paid\\s+(\\w{2,16})").matcher(s);
                    if (m.find()) carryLedger.merge(m.group(1), 1, Integer::sum);
                }
            });
        }
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
        if (cfg.partyMembersHud) {
            hud.register(new HudWidget("pegasus-members", "Members",
                () -> {
                    var m = PartyTracker.members();
                    if (m.isEmpty()) return null;
                    return "§d⛨ " + String.join(", ", m);
                },
                HudPosition.of(2, 118), cfg.partyMembersHud));
        }
        if (cfg.carryMode) {
            hud.register(new HudWidget("pegasus-carry", "Carry",
                () -> carryLedger.isEmpty() ? null : "§6💰 " + carryLedger.size() + " paid",
                HudPosition.of(2, 126), cfg.carryMode));
        }
    }

    @Override
    public void registerCommands(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        if (cfg == null || !cfg.autoRejoin) return;
        dispatcher.register(
            LiteralArgumentBuilder.<FabricClientCommandSource>literal("rp")
                .executes(ctx -> {
                    // real reparty: rebuild from the tracked member list
                    PartyTracker.reparty();
                    return 1;
                }));
        dispatcher.register(
            LiteralArgumentBuilder.<FabricClientCommandSource>literal("pl")
                .executes(ctx -> {
                    var mc = Minecraft.getInstance();
                    if (mc.player != null) mc.player.connection.sendCommand("p list");
                    return 1;
                }));
        dispatcher.register(
            LiteralArgumentBuilder.<FabricClientCommandSource>literal("carry")
                .executes(ctx -> {
                    var mc = Minecraft.getInstance();
                    if (mc.player == null) return 0;
                    if (carryLedger.isEmpty()) {
                        mc.player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§7No carry payments recorded"));
                    } else {
                        mc.player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§6=== Carry Ledger ==="));
                        for (var e : carryLedger.entrySet())
                            mc.player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§e" + e.getKey() + " §7- §6" + e.getValue() + "m"));
                    }
                    return 1;
                }));
    }
}
