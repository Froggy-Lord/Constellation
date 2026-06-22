package com.froggylord.constellation.constellation;

import com.froggylord.constellation.ConstellationClient;
import com.froggylord.constellation.config.LyraConfig;
import com.froggylord.constellation.core.BaseConstellation;
import com.froggylord.constellation.core.InitContext;
import com.froggylord.constellation.hud.HudManager;
import com.froggylord.constellation.hud.HudPosition;
import com.froggylord.constellation.hud.HudWidget;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Lyra — economy. Tracks the purse off the sidebar (the most stable signal in the game) and
 * shows the net coins gained/lost this session. The "Purse:" / "Piggy:" line is present on
 * every SkyBlock sidebar, so this needs no per-area special casing.
 */
public class LyraEconomy extends BaseConstellation {

    @Override public String id() { return "lyra"; }
    @Override public String displayName() { return "Lyra"; }
    @Override public String description() { return "Economy — purse, coin tracker, bits"; }

    private static final Pattern PURSE = Pattern.compile("(?:Purse|Piggy):\\s*([\\d,]+)");
    private static final Pattern BITS = Pattern.compile("Bits:\\s*([\\d,]+)");

    private static long sessionStart = Long.MIN_VALUE;
    private static long currentPurse = 0;
    private static long lastPurse = 0;
    private static long changeAt = 0;
    private static long changeAmount = 0;

    private LyraConfig cfg;

    @Override
    public void init(InitContext ctx) {
        cfg = (LyraConfig) getConfig();
        // refresh the parsed purse each second
        ConstellationClient.tick().every(20, "lyra-purse", LyraEconomy::readPurse);
    }

    private static void readPurse() {
        long prev = currentPurse;
        for (String line : ConstellationClient.loc().getSidebarLines()) {
            Matcher m = PURSE.matcher(line);
            if (!m.find()) continue;
            currentPurse = parse(m.group(1));
            if (sessionStart == Long.MIN_VALUE) sessionStart = currentPurse;
            if (prev > 0 && currentPurse != prev) {
                changeAmount = currentPurse - prev;
                changeAt = System.currentTimeMillis();
            }
            lastPurse = prev;
            return;
        }
    }

    @Override
    public void registerHud(HudManager hud) {
        cfg = (LyraConfig) getConfig();
        if (cfg == null) return;

        if (cfg.purseHud) {
            hud.register(new HudWidget("lyra-purse", "Purse",
                () -> {
                    if (!ConstellationClient.loc().onHypixel() || sessionStart == Long.MIN_VALUE) return null;
                    String s = "§6" + compact(currentPurse);
                    if (cfg.coinSession) {
                        long delta = currentPurse - sessionStart;
                        if (delta != 0) s += (delta > 0 ? " §a(+" : " §c(-") + compact(Math.abs(delta)) + ")";
                    }
                    return s;
                },
                HudPosition.of(2, 80), cfg.purseHud));
        }
        // purse change flash — shows briefly when coins change by a meaningful amount
        hud.register(new HudWidget("lyra-change", "ΔCoins",
            () -> {
                long ms = System.currentTimeMillis() - changeAt;
                if (ms > 3000 || changeAmount == 0) return null;
                String s = (changeAmount > 0 ? "§a+" : "§c") + compact(Math.abs(changeAmount));
                return s;
            },
            HudPosition.of(50, 86), true));

        if (cfg.quiverHud) {
            hud.register(new HudWidget("lyra-quiver", "Quiver",
                () -> {
                    var mc = net.minecraft.client.Minecraft.getInstance();
                    if (mc.player == null) return null;
                    // check for arrows in the quiver (offhand or arrow slot)
                    for (var stack : mc.player.getInventory().items) {
                        if (stack.isEmpty()) continue;
                        String id = stack.getItem().getDescriptionId();
                        if (id.contains("arrow") || id.contains("arrow")) {
                            return "§f🏹 " + stack.getCount() + " " + stack.getHoverName().getString();
                        }
                    }
                    return null;
                },
                HudPosition.of(50, 94), cfg.quiverHud));
        }
        if (cfg.bitsHud) {
            hud.register(new HudWidget("lyra-bits", "Bits",
                () -> {
                    for (String line : ConstellationClient.loc().getSidebarLines()) {
                        Matcher m = BITS.matcher(line);
                        if (m.find()) return "§b" + compact(parse(m.group(1)));
                    }
                    return null;
                },
                HudPosition.of(2, 90), cfg.bitsHud));
        }
    }

    @Override
    public void registerCommands(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        // /coinsreset — reset the session baseline to the current purse
        dispatcher.register(LiteralArgumentBuilder.<FabricClientCommandSource>literal("coinsreset")
            .executes(ctx -> {
                sessionStart = currentPurse;
                Minecraft mc = Minecraft.getInstance();
                if (mc.player != null) mc.player.sendSystemMessage(Component.literal("§e[Lyra]§r coin session reset"));
                return 1;
            }));
    }

    private static long parse(String s) {
        try { return Long.parseLong(s.replace(",", "")); } catch (NumberFormatException e) { return 0; }
    }

    private static String compact(long n) {
        if (n < 1000) return Long.toString(n);
        if (n < 1_000_000) return String.format("%.1fk", n / 1000.0);
        if (n < 1_000_000_000) return String.format("%.2fM", n / 1_000_000.0);
        return String.format("%.2fB", n / 1_000_000_000.0);
    }
}
