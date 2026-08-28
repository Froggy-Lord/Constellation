package com.froggylord.constellation.constellation;

import com.froggylord.constellation.ConstellationClient;
import com.froggylord.constellation.config.DracoConfig;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class KuudraSplits {
    public enum Split { SUPPLY, BUILD, FUEL, STUN, DPS, SKIP, KILL }

    private static final class Timing {
        long startNanos;
        long endNanos;
        long startTick = -1;
        long endTick = -1;

        boolean started() { return startNanos != 0; }
        boolean ended() { return endNanos != 0; }
        long wall(long now) {
            return !started() ? 0 : Math.max(0, ((ended() ? endNanos : now) - startNanos) / 1_000_000L);
        }
        long ticks(long now) {
            return startTick < 0 ? 0 : Math.max(0, (endTick >= 0 ? endTick : now) - startTick);
        }
        void start(long now, long tick) {
            if (started()) return;
            startNanos = now;
            startTick = tick;
        }
        void end(long now, long tick) {
            if (!started() || ended()) return;
            endNanos = now;
            endTick = tick;
        }
    }

    private static final EnumMap<Split, Timing> TIMINGS = new EnumMap<>(Split.class);
    private static boolean initialized;
    private static boolean savedRun;
    private static long serverTicks;
    private static int runTier;

    static { for (Split split : Split.values()) TIMINGS.put(split, new Timing()); }

    private KuudraSplits() {}

    public static void init() {
        ensureStorage();
        if (initialized) return;
        initialized = true;
        ClientReceiveMessageEvents.ALLOW_GAME.register((message, overlay) -> {
            if (overlay) return true;
            String text = ChatFormatting.stripFormatting(message.getString());
            if (text == null) text = message.getString();
            if (text.matches("^\\s*KUUDRA DOWN!.*")) finishSuccess();
            else if (text.matches("^\\s*DEFEAT.*")) reset();
            return true;
        });
    }

    // ported from Athen (BSD-3-Clause): api/kuudra/enums/KuudraPhase.kt
    public static void onPhase(KuudraState.Phase phase) {
        if (phase == KuudraState.Phase.SUPPLY) {
            reset();
            runTier = KuudraState.tier();
        }
        Split next = split(phase);
        long now = System.nanoTime();
        if (next == null) {
            if (phase == KuudraState.Phase.DONE) endAll(now, serverTicks);
            return;
        }
        if (KuudraState.tier() > 0) runTier = KuudraState.tier();
        for (Split split : Split.values()) if (split.ordinal() < next.ordinal()) TIMINGS.get(split).end(now, serverTicks);
        TIMINGS.get(next).start(now, serverTicks);
    }

    // negative ping server clock ported from Devonian (GPL-3.0): api/events/EventBus.kt
    public static void onServerTick() { serverTicks++; }

    public static List<String> hudLines() {
        DracoConfig cfg = config();
        if (cfg == null || !cfg.enabled || !cfg.kuudraSplits || !cfg.kuudraSplitsHud
            || !KuudraState.inRun()) return List.of();
        int tier = tier();
        if (tier < 1) return List.of();
        long now = System.nanoTime();
        List<String> lines = new ArrayList<>();
        for (Split split : phaseSet(tier)) {
            Timing timing = TIMINGS.get(split);
            if (!cfg.kuudraSplitsShowUnstarted && !timing.started()) continue;
            lines.add(render(style(split, cfg), splitName(split, tier), timing.wall(now), timing.ticks(serverTicks),
                pb(tier, split), cfg));
        }
        long wall = totalWall(tier, now);
        long ticks = totalTicks(tier, serverTicks);
        lines.add(render(cfg.kuudraSplitsAdvanced ? cfg.kuudraSplitOverallStyle : cfg.kuudraSplitGeneralStyle,
            "Overall", wall, ticks, pb(tier, null), cfg));
        if (cfg.kuudraSplitsEstimatePace && phaseSet(tier).stream().anyMatch(s -> TIMINGS.get(s).started())) {
            long estimate = estimate(tier, now, cfg);
            String line = clean(cfg.kuudraSplitEstimateStyle)
                .replace("#time", format(estimate, cfg.kuudraSplitDecimals))
                .replace("{time}", format(estimate, cfg.kuudraSplitDecimals));
            if (!line.isBlank()) lines.add(line);
        }
        return lines;
    }

    public static void reset() {
        for (Split split : Split.values()) TIMINGS.put(split, new Timing());
        savedRun = false;
        runTier = 0;
    }

    public static long pb(int tier, Split split) {
        ensureStorage();
        DracoConfig cfg = config();
        if (cfg == null) return 0;
        Long value = cfg.kuudraSplitPbs.get(key(tier, split));
        return value == null || value <= 0 ? 0 : value;
    }

    public static long average(int tier, Split split) {
        ensureStorage();
        List<Long> values = config().kuudraSplitHistory.get(key(tier, split));
        if (values == null || values.isEmpty()) return hardcoded(tier, split);
        long total = 0;
        int count = 0;
        for (Long value : values) if (value != null && value > 0) { total += value; count++; }
        return count == 0 ? hardcoded(tier, split) : total / count;
    }

    // ported from Athen (BSD-3-Clause): modules/impl/kuudra/KuudraSplits.kt success persistence
    private static void finishSuccess() {
        DracoConfig cfg = config();
        if (cfg == null || !cfg.enabled || !cfg.kuudraSplits || savedRun) return;
        int tier = tier();
        if (tier < 1) return;
        savedRun = true;
        long now = System.nanoTime();
        endAll(now, serverTicks);
        List<Split> phases = phaseSet(tier);
        boolean complete = phases.stream().allMatch(s -> TIMINGS.get(s).started() && TIMINGS.get(s).wall(now) > 0);
        if (!complete) {
            local("§cKuudra split run was incomplete and was not saved.");
            return;
        }

        ensureStorage();
        Map<Split, Long> oldPbs = new EnumMap<>(Split.class);
        long overall = 0;
        for (Split split : phases) {
            long duration = TIMINGS.get(split).wall(now);
            overall += duration;
            oldPbs.put(split, pb(tier, split));
            if (cfg.kuudraSplitsSavePbs) savePb(tier, split, duration);
            if (cfg.kuudraSplitsSaveHistory) addHistory(tier, split, duration, cfg.kuudraSplitHistoryLimit);
        }
        long oldOverall = pb(tier, null);
        if (cfg.kuudraSplitsSavePbs) savePb(tier, null, overall);
        if (cfg.kuudraSplitsSaveHistory) addHistory(tier, null, overall, cfg.kuudraSplitHistoryLimit);
        ConstellationClient.saveConfig();

        if (!cfg.kuudraSplitsChat) return;
        local("§cKuudra T" + tier + " split breakdown:");
        for (Split split : phases) {
            Timing timing = TIMINGS.get(split);
            local(" §8- §c" + splitName(split, tier) + "§f: " + format(timing.wall(now), cfg.kuudraSplitDecimals)
                + " §7[" + formatTicks(timing.ticks(serverTicks), cfg.kuudraSplitDecimals) + "]"
                + delta(timing.wall(now), oldPbs.get(split), cfg.kuudraSplitDecimals));
        }
        local(" §8- §4Overall§f: " + format(overall, cfg.kuudraSplitDecimals)
            + " §7[" + formatTicks(totalTicks(tier, serverTicks), cfg.kuudraSplitDecimals) + "]"
            + delta(overall, oldOverall, cfg.kuudraSplitDecimals));
    }

    public static void registerCommands(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        dispatcher.register(LiteralArgumentBuilder.<FabricClientCommandSource>literal("kuudrasplits")
            .executes(c -> status(tier()))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("status")
                .executes(c -> status(tier()))
                .then(RequiredArgumentBuilder.<FabricClientCommandSource, Integer>argument("tier", IntegerArgumentType.integer(1, 5))
                    .executes(c -> status(IntegerArgumentType.getInteger(c, "tier")))))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("estimate")
                .then(RequiredArgumentBuilder.<FabricClientCommandSource, String>argument("mode", StringArgumentType.word())
                    .executes(c -> estimateMode(StringArgumentType.getString(c, "mode")))))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("history")
                .then(RequiredArgumentBuilder.<FabricClientCommandSource, Integer>argument("runs", IntegerArgumentType.integer(1, 100))
                    .executes(c -> historyLimit(IntegerArgumentType.getInteger(c, "runs")))))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("precision")
                .then(RequiredArgumentBuilder.<FabricClientCommandSource, Integer>argument("decimals", IntegerArgumentType.integer(0, 2))
                    .executes(c -> precision(IntegerArgumentType.getInteger(c, "decimals")))))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("style")
                .then(RequiredArgumentBuilder.<FabricClientCommandSource, String>argument("split", StringArgumentType.word())
                    .then(RequiredArgumentBuilder.<FabricClientCommandSource, String>argument("template", StringArgumentType.greedyString())
                        .executes(c -> style(StringArgumentType.getString(c, "split"),
                            StringArgumentType.getString(c, "template"))))))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("clear")
                .then(RequiredArgumentBuilder.<FabricClientCommandSource, String>argument("tier", StringArgumentType.word())
                    .executes(c -> clear(StringArgumentType.getString(c, "tier"))))));
    }

    private static int status(int tier) {
        if (tier < 1 || tier > 5) {
            local("No Kuudra tier is currently known. Use /kuudrasplits status <1-5>.");
            return 0;
        }
        DracoConfig cfg = config();
        local("§eKuudra T" + tier + " records; estimate " + estimateModeName(cfg.kuudraSplitEstimateType)
            + "; history " + cfg.kuudraSplitHistoryLimit + " runs");
        for (Split split : phaseSet(tier))
            local(" §8- §c" + splitName(split, tier) + "§f: PB " + optional(pb(tier, split), cfg)
                + " §7| Avg " + optional(average(tier, split), cfg));
        local(" §8- §4Overall§f: PB " + optional(pb(tier, null), cfg)
            + " §7| Avg " + optional(historyAverage(tier, null), cfg));
        return 1;
    }

    private static int estimateMode(String value) {
        int mode = switch (value.toLowerCase(Locale.ROOT)) {
            case "pb", "personal", "0" -> 0;
            case "average", "avg", "1" -> 1;
            case "hardcoded", "default", "2" -> 2;
            default -> -1;
        };
        if (mode < 0) { local("Estimate mode must be pb, average, or hardcoded."); return 0; }
        config().kuudraSplitEstimateType = mode;
        ConstellationClient.saveConfig();
        local("Kuudra split estimate mode set to " + estimateModeName(mode) + ".");
        return 1;
    }

    private static int historyLimit(int runs) {
        ensureStorage();
        DracoConfig cfg = config();
        if (cfg == null) return 0;
        cfg.kuudraSplitHistoryLimit = Math.clamp(runs, 1, 100);
        trimHistory(cfg);
        ConstellationClient.saveConfig();
        local("Kuudra split history limit set to " + cfg.kuudraSplitHistoryLimit + ".");
        return 1;
    }

    private static int precision(int decimals) {
        config().kuudraSplitDecimals = Math.clamp(decimals, 0, 2);
        ConstellationClient.saveConfig();
        local("Kuudra split precision updated.");
        return 1;
    }

    private static int style(String target, String template) {
        String clean = template.trim();
        if (clean.isEmpty() || clean.length() > 200) {
            local("Split template must be 1-200 characters.");
            return 0;
        }
        DracoConfig cfg = config();
        switch (target.toLowerCase(Locale.ROOT)) {
            case "general" -> cfg.kuudraSplitGeneralStyle = clean;
            case "supply", "supplies" -> cfg.kuudraSplitSupplyStyle = clean;
            case "build" -> cfg.kuudraSplitBuildStyle = clean;
            case "fuel" -> cfg.kuudraSplitFuelStyle = clean;
            case "eaten" -> cfg.kuudraSplitEatenStyle = clean;
            case "stun" -> cfg.kuudraSplitStunStyle = clean;
            case "dps" -> cfg.kuudraSplitDpsStyle = clean;
            case "skip" -> cfg.kuudraSplitSkipStyle = clean;
            case "kill" -> cfg.kuudraSplitKillStyle = clean;
            case "overall" -> cfg.kuudraSplitOverallStyle = clean;
            case "estimate" -> cfg.kuudraSplitEstimateStyle = clean;
            default -> {
                local("Unknown style. Use general, supply, build, fuel, eaten, stun, dps, skip, kill, overall, or estimate.");
                return 0;
            }
        }
        ConstellationClient.saveConfig();
        local("Updated Kuudra " + target + " split style. Variables: {name} {time} {tick} {pb}");
        return 1;
    }

    private static int clear(String value) {
        DracoConfig cfg = config();
        ensureStorage();
        if (value.equalsIgnoreCase("all")) {
            cfg.kuudraSplitPbs.clear();
            cfg.kuudraSplitHistory.clear();
        } else {
            int tier;
            try { tier = Integer.parseInt(value); } catch (NumberFormatException ignored) { tier = -1; }
            if (tier < 1 || tier > 5) { local("Use a tier from 1-5 or all."); return 0; }
            String prefix = tier + ".";
            cfg.kuudraSplitPbs.keySet().removeIf(key -> key.startsWith(prefix));
            cfg.kuudraSplitHistory.keySet().removeIf(key -> key.startsWith(prefix));
        }
        ConstellationClient.saveConfig();
        local("Cleared Kuudra split records for " + value + ".");
        return 1;
    }

    private static long estimate(int tier, long now, DracoConfig cfg) {
        long result = 0;
        for (Split split : phaseSet(tier)) {
            Timing timing = TIMINGS.get(split);
            long reference = switch (Math.clamp(cfg.kuudraSplitEstimateType, 0, 2)) {
                case 0 -> pb(tier, split);
                case 1 -> average(tier, split);
                default -> hardcoded(tier, split);
            };
            if (reference <= 0) reference = hardcoded(tier, split);
            long actual = timing.wall(now);
            result += timing.ended() ? actual : timing.started() ? Math.max(actual, reference) : reference;
        }
        return result;
    }

    private static String render(String style, String name, long wall, long ticks, long pb, DracoConfig cfg) {
        String result = clean(style).replace("#name", name).replace("{name}", name)
            .replace("#time", format(wall, cfg.kuudraSplitDecimals)).replace("{time}", format(wall, cfg.kuudraSplitDecimals))
            .replace("#tick", formatTicks(ticks, cfg.kuudraSplitDecimals)).replace("{tick}", formatTicks(ticks, cfg.kuudraSplitDecimals))
            .replace("#pb", optional(pb, cfg)).replace("{pb}", optional(pb, cfg));
        return result.replace('\n', ' ').replace('\r', ' ').trim();
    }

    private static String style(Split split, DracoConfig cfg) {
        if (!cfg.kuudraSplitsAdvanced) return cfg.kuudraSplitGeneralStyle;
        return switch (split) {
            case SUPPLY -> cfg.kuudraSplitSupplyStyle;
            case BUILD -> cfg.kuudraSplitBuildStyle;
            case FUEL -> tier() >= 3 ? cfg.kuudraSplitEatenStyle : cfg.kuudraSplitFuelStyle;
            case STUN -> cfg.kuudraSplitStunStyle;
            case DPS -> cfg.kuudraSplitDpsStyle;
            case SKIP -> cfg.kuudraSplitSkipStyle;
            case KILL -> cfg.kuudraSplitKillStyle;
        };
    }

    private static List<Split> phaseSet(int tier) {
        List<Split> result = new ArrayList<>(List.of(Split.SUPPLY, Split.BUILD, Split.FUEL));
        if (tier >= 3) result.addAll(List.of(Split.STUN, Split.DPS));
        if (tier == 5) result.add(Split.SKIP);
        result.add(Split.KILL);
        return result;
    }

    private static Split split(KuudraState.Phase phase) {
        return switch (phase) {
            case SUPPLY -> Split.SUPPLY;
            case BUILD -> Split.BUILD;
            case FUEL -> Split.FUEL;
            case STUN -> Split.STUN;
            case DPS -> Split.DPS;
            case SKIP -> Split.SKIP;
            case KILL -> Split.KILL;
            default -> null;
        };
    }

    private static void endAll(long now, long tick) {
        for (Timing timing : TIMINGS.values()) timing.end(now, tick);
    }

    private static long totalWall(int tier, long now) {
        long total = 0;
        for (Split split : phaseSet(tier)) total += TIMINGS.get(split).wall(now);
        return total;
    }

    private static long totalTicks(int tier, long tick) {
        long total = 0;
        for (Split split : phaseSet(tier)) total += TIMINGS.get(split).ticks(tick);
        return total;
    }

    private static long hardcoded(int tier, Split split) {
        return switch (split) {
            case SUPPLY -> 34_000;
            case BUILD -> 20_000;
            case FUEL -> tier >= 3 ? 5_000 : 15_000;
            case STUN -> 1_000;
            case DPS, SKIP -> 5_000;
            case KILL -> 4_000;
        };
    }

    private static void savePb(int tier, Split split, long value) {
        if (value <= 0) return;
        String key = key(tier, split);
        Long old = config().kuudraSplitPbs.get(key);
        if (old == null || old <= 0 || value < old) config().kuudraSplitPbs.put(key, value);
    }

    private static void addHistory(int tier, Split split, long value, int limit) {
        if (value <= 0) return;
        List<Long> list = config().kuudraSplitHistory.computeIfAbsent(key(tier, split), ignored -> new ArrayList<>());
        list.add(value);
        int keep = Math.clamp(limit, 1, 100);
        while (list.size() > keep) list.removeFirst();
    }

    private static void trimHistory(DracoConfig cfg) {
        int keep = Math.clamp(cfg.kuudraSplitHistoryLimit, 1, 100);
        for (List<Long> list : cfg.kuudraSplitHistory.values())
            while (list != null && list.size() > keep) list.removeFirst();
    }

    private static long historyAverage(int tier, Split split) {
        List<Long> values = config().kuudraSplitHistory.get(key(tier, split));
        if (values == null || values.isEmpty()) return 0;
        long total = 0;
        int count = 0;
        for (Long value : values) if (value != null && value > 0) { total += value; count++; }
        return count == 0 ? 0 : total / count;
    }

    private static String key(int tier, Split split) { return tier + "." + (split == null ? "OVERALL" : split.name()); }
    private static int tier() { return KuudraState.tier() > 0 ? KuudraState.tier() : runTier; }
    private static String splitName(Split split, int tier) { return split == Split.FUEL && tier >= 3 ? "Eaten" : title(split.name()); }
    private static String title(String value) { return value.substring(0, 1) + value.substring(1).toLowerCase(Locale.ROOT); }

    private static String format(long millis, int decimals) {
        int precision = Math.clamp(decimals, 0, 2);
        double seconds = Math.max(0, millis) / 1000.0;
        if (seconds < 60) return String.format(Locale.ROOT, "%1$." + precision + "fs", seconds);
        long minutes = (long) (seconds / 60);
        return minutes + "m " + String.format(Locale.ROOT, "%1$." + precision + "fs", seconds - minutes * 60);
    }

    private static String formatTicks(long ticks, int decimals) { return format(Math.max(0, ticks) * 50L, decimals); }
    private static String optional(long value, DracoConfig cfg) { return value <= 0 ? "--" : format(value, cfg.kuudraSplitDecimals); }
    private static String delta(long value, Long old, int decimals) {
        if (old == null || old <= 0) return "";
        long difference = value - old;
        return (difference <= 0 ? " §a[-" : " §6[+") + format(Math.abs(difference), decimals) + "]";
    }
    private static String estimateModeName(int mode) { return switch (Math.clamp(mode, 0, 2)) { case 0 -> "PB"; case 1 -> "average"; default -> "hardcoded"; }; }

    private static String clean(String text) {
        if (text == null) return "";
        return text.replace("<dark_red>", "§4").replace("<dark_gray>", "§8")
            .replace("<gray>", "§7").replace("<red>", "§c").replace("<green>", "§a")
            .replace("<yellow>", "§e").replace("<orange>", "§6").replace("<white>", "§f")
            .replace("<reset>", "§r").replace("<r>", "§r");
    }

    private static void ensureStorage() {
        DracoConfig cfg = config();
        if (cfg == null) return;
        if (cfg.kuudraSplitPbs == null) cfg.kuudraSplitPbs = new LinkedHashMap<>();
        if (cfg.kuudraSplitHistory == null) cfg.kuudraSplitHistory = new LinkedHashMap<>();
        cfg.kuudraSplitPbs.entrySet().removeIf(entry ->
            entry.getKey() == null || entry.getValue() == null || entry.getValue() <= 0);
        cfg.kuudraSplitHistory.entrySet().removeIf(entry ->
            entry.getKey() == null || entry.getValue() == null);
        for (List<Long> values : cfg.kuudraSplitHistory.values())
            values.removeIf(value -> value == null || value <= 0);
    }

    private static DracoConfig config() { return ConstellationClient.cfg() == null ? null : ConstellationClient.cfg().draco; }
    private static void local(String text) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) mc.player.sendSystemMessage(Component.literal(text));
    }
}
