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

public class LyraEconomy extends BaseConstellation {

    @Override public String id() { return "lyra"; }
    @Override public String displayName() { return "Lyra"; }
    @Override public String description() { return "coin/purse stuff"; }

    static final Pattern PURSE = Pattern.compile("(?:Purse|Piggy):\\s*([\\d,]+)");
    private static final Pattern BITS = Pattern.compile("Bits:\\s*([\\d,]+)");

    static long sessionStart = Long.MIN_VALUE;
    static long currentPurse = 0;
    private static long lastPurse = 0;
    static long changeAt = 0;
    static long changeAmount = 0;
    static long currentBits = -1;
    static long sessionStartedAtNanos = 0;

    private LyraConfig cfg;

    @Override
    public void init(InitContext ctx) {
        cfg = (LyraConfig) config;
        LyraTooltips.init(cfg);
        LyraCurrencyTracker.init(cfg, this);
        LyraSlotText.init(cfg);
        LyraInventorySearch.init(cfg);
        LyraInventoryButtons.init(cfg);
        LyraStorageValue.init(cfg);
        LyraBazaarHelper.init(cfg);
        LyraAuctionHelper.init(cfg);
    }

    // accessed by LyraTracker via package-private
    static long bazaarSold = 0, bazaarSpent = 0, bazaarAt = 0;
    static String essenceType = "";
    static int essenceSession = 0;
    static long essenceAt = 0;

    void readPurse() { // package-private for LyraTracker method ref
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
            ConstellationClient.verifyLog("lyra-purse", true, line);
            return;
        }
        ConstellationClient.verifyLog("lyra-purse", false, "no sidebar purse line");
    }

    @Override
    public void registerHud(HudManager hud) {
        cfg = (LyraConfig) config;
        hud.register(new com.froggylord.constellation.hud.LyraCurrencyHudWidget(
            HudPosition.of(2, 80), () -> cfg != null && cfg.enabled
                && (cfg.purseHud || cfg.coinSession || cfg.coinSessionRate || cfg.coinRecentChange || cfg.bitsHud)));
    }

    @Override
    public void registerCommands(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        LyraTooltips.registerCommands(dispatcher);
        LyraCurrencyTracker.registerCommands(dispatcher);
        LyraSlotText.registerCommands(dispatcher);
        LyraInventorySearch.registerCommands(dispatcher);
        LyraInventoryButtons.registerCommands(dispatcher);
        LyraStorageValue.registerCommands(dispatcher);
        LyraBazaarHelper.registerCommands(dispatcher);
        LyraAuctionHelper.registerCommands(dispatcher);
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
