package com.froggylord.constellation.constellation;

import com.froggylord.constellation.ConstellationClient;
import com.froggylord.constellation.config.AurigaConfig;
import com.froggylord.constellation.core.BaseConstellation;
import com.froggylord.constellation.core.InitContext;
import com.froggylord.constellation.hud.HudManager;
import com.froggylord.constellation.hud.HudPosition;
import com.froggylord.constellation.hud.HudWidget;

/**
 * Auriga — experiments + enchanting + misc. Starter: experiments table helper indicator. Full
 * experiment solvers (Chronomatron / Ultrasequencer / Superpairs) come later as GUI-slot
 * overlays since the 26.2 screen API moved to an extract-render-state model.
 */
public class AurigaMisc extends BaseConstellation {

    @Override public String id() { return "auriga"; }
    @Override public String displayName() { return "Auriga"; }
    @Override public String description() { return "Experiments — helpers, enchanting, minions"; }

    @Override
    public void registerCommands(com.mojang.brigadier.CommandDispatcher<net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource> dispatcher) {
        cfg = (AurigaConfig) getConfig();
        if (cfg == null || !cfg.shCalcCommand) return;
        dispatcher.register(com.mojang.brigadier.builder.LiteralArgumentBuilder.<net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource>literal("shcalc")
            .executes(ctx -> {
                var mc = net.minecraft.client.Minecraft.getInstance();
                if (mc.player == null || !ConstellationClient.loc().onHypixel()) return 0;
                // read strength + crit damage + weapon damage from sidebar
                int str = 0, cd = 0, wd = 100;
                for (String line : ConstellationClient.loc().getSidebarLines()) {
                    var sm = java.util.regex.Pattern.compile("❁ Strength:?\\s*([\\d,]+)").matcher(line);
                    if (sm.find()) str = Integer.parseInt(sm.group(1).replace(",", ""));
                    var cm = java.util.regex.Pattern.compile("☠ Crit Damage:?\\s*([\\d,]+)%").matcher(line);
                    if (cm.find()) cd = Integer.parseInt(cm.group(1).replace(",", ""));
                    var wm = java.util.regex.Pattern.compile("❁ Damage:?\\s*([\\d,]+)").matcher(line);
                    if (wm.find()) wd = Integer.parseInt(wm.group(1).replace(",", ""));
                }
                double base = (5 + wd) * (1 + str / 100.0) * (1 + cd / 100.0);
                int dmg = (int) Math.round(base);
                mc.player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                    "§6⚔ Damage §f" + compact(dmg) + " §7(weapon §f" + wd + " §7str §f" + str + " §7cd §f" + cd + "%)"));
                return 1;
            }));
    }

    private static String compact(int n) {
        if (n < 1000) return Integer.toString(n);
        if (n < 1_000_000) return String.format("%.1fk", n / 1000.0);
        return String.format("%.1fM", n / 1_000_000.0);
    }

    private AurigaConfig cfg;

    @Override
    public void init(InitContext ctx) {
        cfg = (AurigaConfig) getConfig();
        // experiment-table slot solvers (Ultrasequencer / Superpairs)
        AurigaExperiments.init(cfg);
    }

    @Override
    public void registerHud(HudManager hud) {
        cfg = (AurigaConfig) getConfig();
        if (cfg == null) return;

        if (cfg.experimentHelper) {
            hud.register(new HudWidget("auriga-exp", "Experiments",
                () -> ConstellationClient.loc().onHypixel() ? "§e🧪" : null,
                HudPosition.of(50, 94), cfg.experimentHelper));
        }
        if (cfg.bingoHelper) {
            hud.register(new HudWidget("auriga-bingo", "Bingo",
                () -> {
                    if (!ConstellationClient.loc().onHypixel()) return null;
                    for (String line : ConstellationClient.loc().getSidebarLines()) {
                        if (line.contains("Bingo") || line.contains("Card")) return "§a🎯 " + line.trim();
                    }
                    return null;
                },
                HudPosition.of(50, 102), cfg.bingoHelper));
        }
    }
}
