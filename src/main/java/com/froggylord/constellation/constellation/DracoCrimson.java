package com.froggylord.constellation.constellation;

import com.froggylord.constellation.ConstellationClient;
import com.froggylord.constellation.config.DracoConfig;
import com.froggylord.constellation.core.BaseConstellation;
import com.froggylord.constellation.core.InitContext;
import com.froggylord.constellation.core.LocationManager.SkyblockArea;
import com.froggylord.constellation.hud.HudManager;
import com.froggylord.constellation.hud.HudPosition;
import com.froggylord.constellation.hud.HudWidget;
import com.froggylord.constellation.hud.KuudraSplitsHudWidget;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.network.chat.Component;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class DracoCrimson extends BaseConstellation {

    @Override public String id() { return "draco"; }
    @Override public String displayName() { return "Draco"; }
    @Override public String description() { return "crimson isle stuff"; }

    private static final Pattern REP = Pattern.compile("(Barbarian|Mage) Reputation:?\\s*([\\d,]+)");
    private static final Pattern DOJO = Pattern.compile("Dojo:.*?(\\d+).*");
    private static final Pattern VANQ = Pattern.compile("Vanquisher:?\\s*(\\d+)");

    private DracoConfig cfg;

    private static String kuudraPhase = "";
    private static long kuudraPhaseAt = 0;
    private static long ashfangFrozenUntil = 0;
    private static String abiphoneCaller = "";
    private static long abiphoneCallAt = 0;

    @Override
    public void init(InitContext ctx) {
        cfg = (DracoConfig) config;
        KuudraState.init();
        KuudraBuildHelper.init();
        KuudraStunHelper.init();
        KuudraSplits.init();
        registerRenderer(KuudraSupplyHelper::draw);
        registerRenderer(KuudraBuildHelper::draw);
        registerRenderer(KuudraStunHelper::draw);
        registerRenderer(KuudraTeammateHighlight::draw);
        every(2, "draco-kuudra-state", KuudraState::tick);
        every(2, "draco-kuudra-supplies", KuudraSupplyHelper::tick);
        every(2, "draco-kuudra-build", KuudraBuildHelper::tick);
        every(2, "draco-kuudra-stun", KuudraStunHelper::tick);
        every(2, "draco-kuudra-timers", KuudraTimers::tick);
        every(2, "draco-kuudra-breakdown", KuudraBreakdown::tick);
        every(2, "draco-kuudra-titles", KuudraTitles::tick);
    }

    private static String kuudraPhaseOf(String s) {
        if (s.contains("Bring the Fuel Cell")) return "Supplies";
        if (s.contains("Build the Ballista")) return "Build Ballista";
        if (s.contains("Kuudra has surfaced") || s.contains("STUN")) return "Stun";
        if (s.contains("Kuudra is breaking through")) return "Defend";
        if (s.contains("DPS") || s.contains("Burn the Supply")) return "DPS";
        if (s.contains("KUUDRA DOWN") || s.contains("defeated Kuudra")) return "Done";
        return null;
    }

    @Override
    public void registerHud(HudManager hud) {
        if (cfg == null) cfg = (DracoConfig) config;
        hud.register(new HudWidget("draco-kuudra-phase", "Kuudra",
            KuudraSupplyHelper::phaseHudText, HudPosition.of(72, 42), () -> cfg.kuudraPhaseHud));
        hud.register(new HudWidget("draco-kuudra-supplies", "Supplies",
            KuudraSupplyHelper::supplyHudText, HudPosition.of(72, 46),
            () -> cfg.kuudraSupplyHelper && cfg.kuudraSupplyCounter));
        hud.register(new HudWidget("draco-kuudra-build", "Build",
            KuudraBuildHelper::buildHudText, HudPosition.of(72, 50),
            () -> cfg.kuudraBuildInfo && cfg.kuudraBuildHud));
        hud.register(new HudWidget("draco-kuudra-fresh", "Fresh",
            KuudraBuildHelper::freshHudText, HudPosition.of(72, 54),
            () -> cfg.kuudraFreshTools && cfg.kuudraFreshHud));
        hud.register(new HudWidget("draco-kuudra-supply-timer", "Supply Spawn",
            KuudraTimers::supplyText, HudPosition.of(72, 58),
            () -> cfg.kuudraTimers && cfg.kuudraSupplySpawnTimer));
        hud.register(new HudWidget("draco-kuudra-build-timer", "Build Start",
            KuudraTimers::buildText, HudPosition.of(72, 62),
            () -> cfg.kuudraTimers && cfg.kuudraBuildStartTimer));
        hud.register(new KuudraSplitsHudWidget(HudPosition.of(72, 66),
            () -> cfg.kuudraSplits && cfg.kuudraSplitsHud));
        hud.register(new HudWidget("draco-kuudra-progress", "Supply Progress",
            KuudraTitles::hudText, HudPosition.of(72, 70),
            () -> cfg.kuudraTitles && cfg.kuudraSupplyProgressHud));
    }

    @Override
    public void registerCommands(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        KuudraSplits.registerCommands(dispatcher);
        KuudraBreakdown.registerCommands(dispatcher);
        KuudraTitles.registerCommands(dispatcher);
        KuudraTeammateHighlight.registerCommands(dispatcher);
        var colorValue = RequiredArgumentBuilder.<FabricClientCommandSource, String>argument("argb", StringArgumentType.word())
            .executes(context -> setSupplyColor(StringArgumentType.getString(context, "target"),
                StringArgumentType.getString(context, "argb")));
        var colorTarget = RequiredArgumentBuilder.<FabricClientCommandSource, String>argument("target", StringArgumentType.word())
            .then(colorValue);
        dispatcher.register(LiteralArgumentBuilder.<FabricClientCommandSource>literal("kuudrahelper")
            .executes(context -> kuudraStatus())
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("status").executes(context -> kuudraStatus()))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("color").then(colorTarget))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("message")
                .then(RequiredArgumentBuilder.<FabricClientCommandSource, String>argument("template", StringArgumentType.greedyString())
                    .executes(context -> setSupplyMessage(StringArgumentType.getString(context, "template")))))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("stun")
                .then(RequiredArgumentBuilder.<FabricClientCommandSource, Integer>argument("percent", IntegerArgumentType.integer(1, 100))
                    .executes(context -> setStunPercent(IntegerArgumentType.getInteger(context, "percent")))))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("freshduration")
                .then(RequiredArgumentBuilder.<FabricClientCommandSource, Integer>argument("seconds", IntegerArgumentType.integer(1, 30))
                    .executes(context -> setFreshDuration(IntegerArgumentType.getInteger(context, "seconds")))))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("freshmessage")
                .then(RequiredArgumentBuilder.<FabricClientCommandSource, String>argument("template", StringArgumentType.greedyString())
                    .executes(context -> setFreshMessage(StringArgumentType.getString(context, "template")))))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("stunmessage")
                .then(RequiredArgumentBuilder.<FabricClientCommandSource, String>argument("message", StringArgumentType.greedyString())
                    .executes(context -> setStunMessage(StringArgumentType.getString(context, "message")))))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("stunpod")
                .then(RequiredArgumentBuilder.<FabricClientCommandSource, String>argument("pod", StringArgumentType.word())
                    .executes(context -> setStunPod(StringArgumentType.getString(context, "pod")))))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("stunmode")
                .then(RequiredArgumentBuilder.<FabricClientCommandSource, String>argument("mode", StringArgumentType.word())
                    .executes(context -> setStunMode(StringArgumentType.getString(context, "mode")))))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("stunrange")
                .then(RequiredArgumentBuilder.<FabricClientCommandSource, Integer>argument("blocks", IntegerArgumentType.integer(8, 64))
                    .executes(context -> setStunRange(IntegerArgumentType.getInteger(context, "blocks")))))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("stunwidth")
                .then(RequiredArgumentBuilder.<FabricClientCommandSource, Float>argument("width", FloatArgumentType.floatArg(0.1f, 10f))
                    .executes(context -> setStunWidth(FloatArgumentType.getFloat(context, "width")))))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("stuncolor")
                .then(RequiredArgumentBuilder.<FabricClientCommandSource, String>argument("target", StringArgumentType.word())
                    .then(RequiredArgumentBuilder.<FabricClientCommandSource, String>argument("argb", StringArgumentType.word())
                        .executes(context -> setStunColor(StringArgumentType.getString(context, "target"),
                            StringArgumentType.getString(context, "argb"))))))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("stunwarning")
                .then(RequiredArgumentBuilder.<FabricClientCommandSource, String>argument("message", StringArgumentType.greedyString())
                    .executes(context -> setStunWarning(StringArgumentType.getString(context, "message")))))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("supplytimer")
                .then(RequiredArgumentBuilder.<FabricClientCommandSource, Integer>argument("milliseconds", IntegerArgumentType.integer(100, 60_000))
                    .executes(context -> setTimerDuration(true, IntegerArgumentType.getInteger(context, "milliseconds")))))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("buildtimer")
                .then(RequiredArgumentBuilder.<FabricClientCommandSource, Integer>argument("milliseconds", IntegerArgumentType.integer(100, 60_000))
                    .executes(context -> setTimerDuration(false, IntegerArgumentType.getInteger(context, "milliseconds")))))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("timerprecision")
                .then(RequiredArgumentBuilder.<FabricClientCommandSource, Integer>argument("decimals", IntegerArgumentType.integer(0, 2))
                    .executes(context -> setTimerPrecision(IntegerArgumentType.getInteger(context, "decimals")))))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("supplytimerstyle")
                .then(RequiredArgumentBuilder.<FabricClientCommandSource, String>argument("template", StringArgumentType.greedyString())
                    .executes(context -> setTimerStyle(true, StringArgumentType.getString(context, "template")))))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("buildtimerstyle")
                .then(RequiredArgumentBuilder.<FabricClientCommandSource, String>argument("template", StringArgumentType.greedyString())
                    .executes(context -> setTimerStyle(false, StringArgumentType.getString(context, "template")))))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("timerreadyhold")
                .then(RequiredArgumentBuilder.<FabricClientCommandSource, Integer>argument("milliseconds", IntegerArgumentType.integer(0, 10_000))
                    .executes(context -> setTimerReadyHold(IntegerArgumentType.getInteger(context, "milliseconds")))))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("supplytimerready")
                .then(RequiredArgumentBuilder.<FabricClientCommandSource, String>argument("text", StringArgumentType.greedyString())
                    .executes(context -> setTimerReadyText(true, StringArgumentType.getString(context, "text")))))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("buildtimerready")
                .then(RequiredArgumentBuilder.<FabricClientCommandSource, String>argument("text", StringArgumentType.greedyString())
                    .executes(context -> setTimerReadyText(false, StringArgumentType.getString(context, "text"))))));
    }

    private int kuudraStatus() {
        local("Kuudra " + (KuudraState.inRun() ? KuudraState.phase().name() : "idle")
            + "; supply helper " + on(cfg.kuudraSupplyHelper)
            + "; pickup/drop/fuel " + on(cfg.kuudraSupplyPickupWaypoints) + "/"
            + on(cfg.kuudraSupplyDropOffWaypoints) + "/" + on(cfg.kuudraFuelWaypoints));
        local("Use /kuudrahelper color <pickup|drop|fuel|nearby|build> <RRGGBB|AARRGGBB>");
        local("Message variables: {player} {time} {current} {total}");
        local("Build " + KuudraBuildHelper.buildProgress() + "% with " + KuudraBuildHelper.builders()
            + " builders; stun alert " + cfg.kuudraBuildStunPercent + "%");
        local("Stun helper " + on(cfg.kuudraStunHelper) + "; role "
            + (KuudraStunHelper.stunning() ? "active" : "idle") + "; belly " + on(KuudraStunHelper.inBelly())
            + "; exact pod " + KuudraStunHelper.selectedPod());
        local("Timers " + on(cfg.kuudraTimers) + "; supply/build "
            + cfg.kuudraSupplySpawnDurationMs + "ms/" + cfg.kuudraBuildStartDurationMs
            + "ms; precision " + cfg.kuudraTimerDecimals);
        return 1;
    }

    private int setSupplyColor(String target, String value) {
        Integer colour = parseColour(value);
        if (colour == null) {
            local("Invalid color. Use RRGGBB or AARRGGBB.");
            return 0;
        }
        switch (target.toLowerCase(java.util.Locale.ROOT)) {
            case "pickup" -> cfg.kuudraSupplyPickupColour = colour;
            case "drop", "dropoff" -> cfg.kuudraSupplyDropOffColour = colour;
            case "fuel" -> cfg.kuudraSupplyFuelColour = colour;
            case "nearby", "player" -> cfg.kuudraSupplyNearbyColour = colour;
            case "build" -> cfg.kuudraBuildColour = colour;
            default -> {
                local("Unknown target. Use pickup, drop, fuel, nearby, or build.");
                return 0;
            }
        }
        ConstellationClient.saveConfig();
        local("Updated Kuudra " + target + " color.");
        return 1;
    }

    private int setSupplyMessage(String template) {
        String clean = template.trim();
        if (clean.isEmpty() || clean.length() > 240) {
            local("Supply message must be 1-240 characters.");
            return 0;
        }
        cfg.kuudraSupplyMessage = clean;
        ConstellationClient.saveConfig();
        local("Updated Kuudra supply message.");
        return 1;
    }

    private int setStunPercent(int percent) {
        cfg.kuudraBuildStunPercent = Math.clamp(percent, 1, 100);
        ConstellationClient.saveConfig();
        local("Kuudra stun alert set to " + cfg.kuudraBuildStunPercent + "%.");
        return 1;
    }

    private int setFreshDuration(int seconds) {
        cfg.kuudraFreshDurationMs = Math.clamp(seconds, 1, 30) * 1_000;
        ConstellationClient.saveConfig();
        local("Fresh Tools duration set to " + seconds + "s.");
        return 1;
    }

    private int setFreshMessage(String template) {
        String clean = template.trim();
        if (clean.isEmpty() || clean.length() > 120) {
            local("Fresh party message must be 1-120 characters.");
            return 0;
        }
        cfg.kuudraFreshPartyMessage = clean;
        ConstellationClient.saveConfig();
        local("Updated Fresh Tools party message. Variable: {build}");
        return 1;
    }

    private int setStunMessage(String message) {
        String clean = message.trim();
        if (clean.isEmpty() || clean.length() > 120) {
            local("Stun message must be 1-120 characters.");
            return 0;
        }
        cfg.kuudraBuildStunMessage = clean;
        ConstellationClient.saveConfig();
        local("Updated Kuudra stun message.");
        return 1;
    }

    private int setStunPod(String value) {
        int pod = switch (value.toLowerCase(java.util.Locale.ROOT)) {
            case "left", "l", "0" -> 0;
            case "middle", "mid", "m", "1" -> 1;
            case "right", "r", "2" -> 2;
            default -> -1;
        };
        if (pod < 0) {
            local("Unknown pod. Use left, middle, or right.");
            return 0;
        }
        cfg.kuudraStunExactPod = pod;
        ConstellationClient.saveConfig();
        local("Exact stun pod set to " + KuudraStunHelper.selectedPod() + ".");
        return 1;
    }

    private int setStunMode(String value) {
        int mode = switch (value.toLowerCase(java.util.Locale.ROOT)) {
            case "outside", "0" -> 0;
            case "aim", "inside", "1" -> 1;
            case "both", "2" -> 2;
            default -> -1;
        };
        if (mode < 0) {
            local("Unknown mode. Use outside, aim, or both.");
            return 0;
        }
        cfg.kuudraStunBlockMode = mode;
        ConstellationClient.saveConfig();
        local("Stun ability block mode updated.");
        return 1;
    }

    private int setStunRange(int blocks) {
        cfg.kuudraStunAimRange = Math.clamp(blocks, 8, 64);
        ConstellationClient.saveConfig();
        local("Stun aim range set to " + cfg.kuudraStunAimRange + " blocks.");
        return 1;
    }

    private int setStunWidth(float width) {
        cfg.kuudraStunLineWidth = Math.clamp(width, 0.1f, 10f);
        ConstellationClient.saveConfig();
        local("Stun outline width updated.");
        return 1;
    }

    private int setStunColor(String target, String value) {
        Integer colour = parseColour(value);
        if (colour == null) {
            local("Invalid color. Use RRGGBB or AARRGGBB.");
            return 0;
        }
        switch (target.toLowerCase(java.util.Locale.ROOT)) {
            case "pod", "pods", "outline" -> cfg.kuudraStunPodColour = colour;
            case "podfill", "fill" -> cfg.kuudraStunPodFillColour = colour;
            case "exact" -> cfg.kuudraStunExactColour = colour;
            case "exactfill" -> cfg.kuudraStunExactFillColour = colour;
            default -> {
                local("Unknown target. Use pod, podfill, exact, or exactfill.");
                return 0;
            }
        }
        ConstellationClient.saveConfig();
        local("Updated stun " + target + " color.");
        return 1;
    }

    private int setStunWarning(String message) {
        String clean = message.trim();
        if (clean.isEmpty() || clean.length() > 120) {
            local("Stun warning must be 1-120 characters.");
            return 0;
        }
        cfg.kuudraStunWarningMessage = clean;
        ConstellationClient.saveConfig();
        local("Updated stun ability warning.");
        return 1;
    }

    private int setTimerDuration(boolean supply, int milliseconds) {
        int value = Math.clamp(milliseconds, 100, 60_000);
        if (supply) cfg.kuudraSupplySpawnDurationMs = value;
        else cfg.kuudraBuildStartDurationMs = value;
        KuudraTimers.reset();
        ConstellationClient.saveConfig();
        local((supply ? "Supply spawn" : "Build start") + " timer set to " + value + "ms.");
        return 1;
    }

    private int setTimerPrecision(int decimals) {
        cfg.kuudraTimerDecimals = Math.clamp(decimals, 0, 2);
        ConstellationClient.saveConfig();
        local("Kuudra timer precision set to " + cfg.kuudraTimerDecimals + ".");
        return 1;
    }

    private int setTimerStyle(boolean supply, String template) {
        String clean = template.trim();
        if (clean.isEmpty() || clean.length() > 160 || !clean.contains("{time}") && !clean.contains("#time")) {
            local("Timer template must be 1-160 characters and contain {time} or #time.");
            return 0;
        }
        if (supply) cfg.kuudraSupplyTimerStyle = clean;
        else cfg.kuudraBuildTimerStyle = clean;
        ConstellationClient.saveConfig();
        local((supply ? "Supply" : "Build") + " timer template updated. Variables: {time} {elapsed}");
        return 1;
    }

    private int setTimerReadyHold(int milliseconds) {
        cfg.kuudraTimerReadyHoldMs = Math.clamp(milliseconds, 0, 10_000);
        ConstellationClient.saveConfig();
        local("Kuudra timer ready hold set to " + cfg.kuudraTimerReadyHoldMs + "ms.");
        return 1;
    }

    private int setTimerReadyText(boolean supply, String text) {
        String clean = text.trim();
        if (clean.isEmpty() || clean.length() > 120) {
            local("Timer ready text must be 1-120 characters.");
            return 0;
        }
        if (supply) cfg.kuudraSupplyTimerReadyText = clean;
        else cfg.kuudraBuildTimerReadyText = clean;
        ConstellationClient.saveConfig();
        local((supply ? "Supply" : "Build") + " timer ready text updated.");
        return 1;
    }

    private static Integer parseColour(String value) {
        String clean = value.startsWith("#") ? value.substring(1) : value;
        if (!clean.matches("(?i)[0-9a-f]{6}|[0-9a-f]{8}")) return null;
        try {
            long parsed = Long.parseUnsignedLong(clean, 16);
            if (clean.length() == 6) parsed |= 0xFF000000L;
            return (int) parsed;
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static String on(boolean value) { return value ? "on" : "off"; }

    private static void local(String text) {
        var player = net.minecraft.client.Minecraft.getInstance().player;
        if (player != null) player.sendSystemMessage(Component.literal(text));
    }

    private static boolean inCrimson() {
        return ConstellationClient.loc().area() == SkyblockArea.CRIMSON_ISLE
            || ConstellationClient.loc().area() == SkyblockArea.KUUDRA;
    }

    @Override
    protected void onDisable() {
        KuudraState.reset();
    }

    // all crimson isle faction/dojo/vanq info is in the tab list, not sidebar
    private static String repLine() {
        for (String line : com.froggylord.constellation.data.TabList.lines()) {
            Matcher m = REP.matcher(line);
            if (m.find()) {
                ConstellationClient.verifyLog("draco-rep", true, line.trim());
                return "§c" + m.group(1) + " §f" + m.group(2)
                    + " §7(" + vanqLine() + ")";
            }
        }
        ConstellationClient.verifyLog("draco-rep", false, "no reputation in tab");
        return null;
    }

    private static String dojoLine() {
        for (String line : com.froggylord.constellation.data.TabList.lines()) {
            Matcher m = DOJO.matcher(line);
            if (m.find()) return "§eDojo §f" + m.group(1);
        }
        return null;
    }

    private static String vanqLine() {
        for (String line : com.froggylord.constellation.data.TabList.lines()) {
            Matcher m = VANQ.matcher(line);
            if (m.find()) return m.group(1) + " kills";
        }
        for (String line : ConstellationClient.loc().getSidebarLines()) {
            Matcher m = VANQ.matcher(line);
            if (m.find()) return m.group(1) + " kills";
        }
        return "? kills";
    }
}
