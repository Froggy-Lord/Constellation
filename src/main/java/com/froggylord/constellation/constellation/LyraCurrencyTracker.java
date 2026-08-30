package com.froggylord.constellation.constellation;

import com.froggylord.constellation.ConstellationClient;
import com.froggylord.constellation.config.LyraConfig;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// purse and bits parsing ported from Skyblocker (LGPL-3.0-or-later): utils/Utils.java updatePurse
// cross-checked with SkyBlockAddons (LGPL-3.0-or-later): utils/Utils.java scoreboard purse/bits parsing
// regex cases cross-checked with SkyHanni (LGPL-2.1): data/PurseApi.kt and BitsApi.kt
public final class LyraCurrencyTracker {
    private static final Pattern PURSE = Pattern.compile("(?:Purse|Piggy):\\s*([\\d,.]+)");
    private static final Pattern BITS = Pattern.compile("Bits:\\s*([\\d,.]+)");
    private static LyraConfig cfg;
    private static boolean initialized;

    private LyraCurrencyTracker() {}

    public static void init(LyraConfig config, LyraEconomy host) {
        cfg = config;
        normalize();
        if (initialized) return;
        initialized = true;
        ConstellationClient.tick().every(5, "lyra-currency", LyraCurrencyTracker::tick);
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> { if (cfg.coinSessionResetOnConnect) reset(true); });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> { if (cfg.coinSessionResetOnConnect) reset(true); });
    }

    public static List<String> hudLines() {
        if (!active() || LyraEconomy.sessionStart == Long.MIN_VALUE) return List.of();
        List<String> lines = new ArrayList<>();
        long delta = LyraEconomy.currentPurse - LyraEconomy.sessionStart;
        String color = delta >= 0 ? "§a" : "§c";
        String change = signed(delta);
        if (cfg.purseHud) lines.add(apply(cfg.purseHudStyle, color, change, rate(), recent()));
        if (cfg.coinSession) lines.add(apply(cfg.coinSessionStyle, color, change, rate(), recent()));
        if (cfg.coinSessionRate && LyraEconomy.sessionStartedAtNanos > 0)
            lines.add(apply(cfg.coinRateStyle, color, change, rate(), recent()));
        if (cfg.coinRecentChange && recentVisible()) {
            String recentColor = LyraEconomy.changeAmount >= 0 ? "§a" : "§c";
            lines.add(apply(cfg.coinChangeStyle, recentColor, signed(LyraEconomy.changeAmount), rate(), recent()));
        }
        if (cfg.bitsHud && LyraEconomy.currentBits >= 0)
            lines.add(apply(cfg.bitsHudStyle, "§b", change, rate(), recent()));
        lines.removeIf(String::isBlank);
        return cfg.coinHudCompact ? List.of(String.join(" §8| §r", lines)) : List.copyOf(lines);
    }

    public static void registerCommands(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        dispatcher.register(LiteralArgumentBuilder.<FabricClientCommandSource>literal("currencyhud")
            .executes(c -> status())
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("status").executes(c -> status()))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("reset").executes(c -> { reset(false); local("§aCurrency session reset."); return 1; }))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("decimals")
                .then(RequiredArgumentBuilder.<FabricClientCommandSource, Integer>argument("value", IntegerArgumentType.integer(0, 2))
                    .executes(c -> decimals(IntegerArgumentType.getInteger(c, "value")))))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("changehold")
                .then(RequiredArgumentBuilder.<FabricClientCommandSource, Integer>argument("ticks", IntegerArgumentType.integer(20, 200))
                    .executes(c -> changeHold(IntegerArgumentType.getInteger(c, "ticks")))))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("style")
                .then(RequiredArgumentBuilder.<FabricClientCommandSource, String>argument("line", StringArgumentType.word())
                    .then(RequiredArgumentBuilder.<FabricClientCommandSource, String>argument("text", StringArgumentType.greedyString())
                        .executes(c -> style(StringArgumentType.getString(c, "line"), StringArgumentType.getString(c, "text"))))))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("option")
                .then(RequiredArgumentBuilder.<FabricClientCommandSource, String>argument("name", StringArgumentType.word())
                    .then(RequiredArgumentBuilder.<FabricClientCommandSource, String>argument("state", StringArgumentType.word())
                        .executes(c -> option(StringArgumentType.getString(c, "name"), StringArgumentType.getString(c, "state")))))));
    }

    private static void tick() {
        if (!active()) return;
        long purse = Long.MIN_VALUE;
        long bits = -1;
        for (String line : ConstellationClient.loc().getSidebarLines()) {
            Matcher purseMatcher = PURSE.matcher(line);
            if (purse == Long.MIN_VALUE && purseMatcher.find()) purse = parse(purseMatcher.group(1));
            Matcher bitsMatcher = BITS.matcher(line);
            if (bits < 0 && bitsMatcher.find()) bits = parse(bitsMatcher.group(1));
        }
        if (purse != Long.MIN_VALUE) updatePurse(purse);
        LyraEconomy.currentBits = bits;
    }

    private static void updatePurse(long purse) {
        long previous = LyraEconomy.currentPurse;
        LyraEconomy.currentPurse = purse;
        if (LyraEconomy.sessionStart == Long.MIN_VALUE) {
            LyraEconomy.sessionStart = purse;
            LyraEconomy.sessionStartedAtNanos = System.nanoTime();
            return;
        }
        if (previous != purse) {
            LyraEconomy.changeAmount = purse - previous;
            LyraEconomy.changeAt = System.nanoTime();
        }
    }

    private static void reset(boolean clearBalance) {
        if (clearBalance) { LyraEconomy.currentPurse=0; LyraEconomy.currentBits=-1; }
        LyraEconomy.sessionStart = !clearBalance && LyraEconomy.currentPurse >= 0 ? LyraEconomy.currentPurse : Long.MIN_VALUE;
        LyraEconomy.sessionStartedAtNanos = LyraEconomy.sessionStart == Long.MIN_VALUE ? 0 : System.nanoTime();
        LyraEconomy.changeAmount = 0;
        LyraEconomy.changeAt = 0;
    }

    private static boolean recentVisible() { return LyraEconomy.changeAmount != 0 && System.nanoTime() - LyraEconomy.changeAt <= cfg.coinChangeHoldTicks * 50_000_000L; }
    private static String recent() { return signed(LyraEconomy.changeAmount); }
    private static String rate() {
        if (LyraEconomy.sessionStartedAtNanos <= 0) return compact(0);
        double hours = Math.max(1.0 / 3600.0, (System.nanoTime() - LyraEconomy.sessionStartedAtNanos) / 3_600_000_000_000.0);
        return compact(Math.round((LyraEconomy.currentPurse - LyraEconomy.sessionStart) / hours));
    }

    private static String apply(String style, String color, String change, String rate, String recent) {
        return style.replace("{purse}", compact(LyraEconomy.currentPurse)).replace("{bits}", compact(Math.max(0, LyraEconomy.currentBits)))
            .replace("{change}", change).replace("{rate}", rate).replace("{recent}", recent).replace("{color}", color);
    }

    private static int status() { local("§ePurse HUD " + on(cfg.purseHud) + ", session " + on(cfg.coinSession) + ", rate " + on(cfg.coinSessionRate) + ", recent " + on(cfg.coinRecentChange) + ", Bits " + on(cfg.bitsHud) + "."); return 1; }
    private static int decimals(int value) { cfg.coinHudDecimals=Math.clamp(value,0,2); return save("Currency precision updated."); }
    private static int changeHold(int ticks) { cfg.coinChangeHoldTicks=Math.clamp(ticks,20,200); return save("Recent-change hold updated."); }
    private static int style(String line, String value) {
        String clean=value.trim(); if(clean.isEmpty()||clean.length()>200){local("§cStyle must be 1-200 characters.");return 0;}
        switch(line.toLowerCase(Locale.ROOT)){case"purse"->cfg.purseHudStyle=clean;case"session"->cfg.coinSessionStyle=clean;case"rate"->cfg.coinRateStyle=clean;case"change","recent"->cfg.coinChangeStyle=clean;case"bits"->cfg.bitsHudStyle=clean;default->{local("§cLine must be purse, session, rate, change, or bits.");return 0;}}
        return save("Currency style updated.");
    }
    private static int option(String name,String state){Boolean value=parseState(state);if(value==null){local("§cState must be on or off.");return 0;}switch(name.toLowerCase(Locale.ROOT)){case"purse","enabled"->cfg.purseHud=value;case"session"->cfg.coinSession=value;case"rate"->cfg.coinSessionRate=value;case"change","recent"->cfg.coinRecentChange=value;case"bits"->cfg.bitsHud=value;case"compact"->cfg.coinHudCompact=value;case"reset"->cfg.coinSessionResetOnConnect=value;default->{local("§cOption must be purse, session, rate, change, bits, compact, or reset.");return 0;}}return save("Currency option updated.");}

    private static long parse(String value) { try { return (long) Double.parseDouble(value.replace(",", "")); } catch (NumberFormatException ignored) { return 0; } }
    private static String signed(long value) { return (value >= 0 ? "+" : "-") + compact(Math.abs(value)); }
    private static String compact(long value) { long abs=Math.abs(value);String sign=value<0?"-":"";if(abs<1_000)return sign+abs;if(abs<1_000_000)return sign+format(abs/1_000.0)+"k";if(abs<1_000_000_000)return sign+format(abs/1_000_000.0)+"M";if(abs<1_000_000_000_000L)return sign+format(abs/1_000_000_000.0)+"B";return sign+format(abs/1_000_000_000_000.0)+"T"; }
    private static String format(double value){return String.format(Locale.ROOT,"%."+cfg.coinHudDecimals+"f",value);}
    private static boolean active(){return cfg!=null&&cfg.enabled&&ConstellationClient.loc().onHypixel();}
    private static void normalize(){cfg.coinChangeHoldTicks=Math.clamp(cfg.coinChangeHoldTicks,20,200);cfg.coinHudDecimals=Math.clamp(cfg.coinHudDecimals,0,2);}
    private static Boolean parseState(String state){return switch(state.toLowerCase(Locale.ROOT)){case"on","true","yes","1"->true;case"off","false","no","0"->false;default->null;};}
    private static String on(boolean value){return value?"§aon":"§coff";}
    private static int save(String text){ConstellationClient.saveConfig();local("§a"+text);return 1;}
    private static void local(String text){Minecraft mc=Minecraft.getInstance();if(mc.player!=null)mc.player.sendSystemMessage(Component.literal("§5Lyra §8> §f"+text));}
}
