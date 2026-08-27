package com.froggylord.constellation.constellation;

import com.froggylord.constellation.ConstellationClient;
import com.froggylord.constellation.config.DracoConfig;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

public final class KuudraBreakdown {
    private static final String END = "[NPC] Elle: Good job everyone. A hard fought battle come to an end. Let's get out of here before we run into any more trouble!";
    private static final String OWN_FRESH = "Your Fresh Tools Perk bonus doubles your building speed for the next 10 seconds!";
    private static final Pattern SUPPLY = Pattern.compile("(?:\\[[^]]*] )?(?<user>\\w{1,16}) recovered one of Elle's supplies! \\(\\d+/\\d+\\)");
    private static final Pattern FUEL = Pattern.compile("(?:\\[[^]]*] )?(?<user>\\w{1,16}) recovered a Fuel Cell and charged the Ballista! \\(\\d+%\\)");
    private static final Pattern STUN = Pattern.compile("(?<user>\\w{1,16}) destroyed one of Kuudra's pods!");
    private static final Pattern PARTY = Pattern.compile("^Party > (?:\\[[^]]*?] )?(?<user>\\w{1,16})(?: [^: ]+)?: ?(?<message>.+)$");
    private static final Pattern DEATH = Pattern.compile("^ \\u2620 (?:(?<user>\\w{1,16})|You were) .+(?: and became a ghost)?\\.$");
    private static final Map<String, Stats> PLAYERS = new LinkedHashMap<>();
    private static boolean active;
    private static boolean printed;
    private static long startedNanos;

    private KuudraBreakdown() {}

    // ported from Athen (BSD-3-Clause): modules/impl/kuudra/KuudraBreakdown.kt
    public static void start() {
        reset();
        DracoConfig cfg = config();
        if (cfg == null || !cfg.enabled || !cfg.kuudraBreakdown) return;
        active = true;
        startedNanos = System.nanoTime();
        refreshPlayers();
    }

    public static void tick() {
        if (!active) return;
        DracoConfig cfg = config();
        if (cfg == null || !cfg.enabled || !cfg.kuudraBreakdown) {
            reset();
            return;
        }
        if (cfg.kuudraBreakdownIncludeLatePlayers && System.nanoTime() - startedNanos <= 10_000_000_000L)
            refreshPlayers();
    }

    public static void onMessage(String text) {
        if (!active || text == null || text.isEmpty()) return;
        DracoConfig cfg = config();
        if (cfg == null || !cfg.enabled || !cfg.kuudraBreakdown) return;

        if (text.matches("^\\s*DEFEAT.*")) {
            reset();
            return;
        }
        if (text.equals(END)) {
            if (cfg.kuudraBreakdownTrigger != 1) print(true);
            return;
        }
        if (text.matches("^\\s*KUUDRA DOWN!.*")) {
            if (cfg.kuudraBreakdownTrigger == 1) print(true);
            return;
        }

        Matcher matcher = SUPPLY.matcher(text);
        if (matcher.matches()) {
            player(matcher.group("user"), cfg).supplies++;
            return;
        }
        matcher = FUEL.matcher(text);
        if (matcher.matches()) {
            player(matcher.group("user"), cfg).fuels++;
            return;
        }
        matcher = STUN.matcher(text);
        if (matcher.matches() && KuudraState.tier() >= 3) {
            player(matcher.group("user"), cfg).stuns++;
            return;
        }
        matcher = DEATH.matcher(text);
        if (matcher.matches()) {
            String name = matcher.group("user");
            if (name == null) name = localName();
            if (name != null) player(name, cfg).deaths++;
            return;
        }
        if (text.equals(OWN_FRESH)) {
            String name = localName();
            if (name != null) player(name, cfg).fresh++;
            return;
        }
        matcher = PARTY.matcher(text);
        if (!matcher.matches() || matcher.group("user").equalsIgnoreCase(localName())) return;
        try {
            if (Pattern.compile(cfg.kuudraBreakdownFreshRegex).matcher(matcher.group("message")).matches())
                player(matcher.group("user"), cfg).fresh++;
        } catch (PatternSyntaxException ignored) {
        }
    }

    public static void reset() {
        PLAYERS.clear();
        active = false;
        printed = false;
        startedNanos = 0;
    }

    public static void registerCommands(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        dispatcher.register(LiteralArgumentBuilder.<FabricClientCommandSource>literal("kuudrabreakdown")
            .executes(context -> status())
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("status").executes(context -> status()))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("show").executes(context -> show()))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("option")
                .then(RequiredArgumentBuilder.<FabricClientCommandSource, String>argument("name", StringArgumentType.word())
                    .then(RequiredArgumentBuilder.<FabricClientCommandSource, Boolean>argument("enabled", BoolArgumentType.bool())
                        .executes(context -> option(StringArgumentType.getString(context, "name"),
                            BoolArgumentType.getBool(context, "enabled"))))))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("sort")
                .then(RequiredArgumentBuilder.<FabricClientCommandSource, String>argument("mode", StringArgumentType.word())
                    .executes(context -> sort(StringArgumentType.getString(context, "mode")))))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("trigger")
                .then(RequiredArgumentBuilder.<FabricClientCommandSource, String>argument("mode", StringArgumentType.word())
                    .executes(context -> trigger(StringArgumentType.getString(context, "mode")))))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("freshregex")
                .then(RequiredArgumentBuilder.<FabricClientCommandSource, String>argument("regex", StringArgumentType.greedyString())
                    .executes(context -> freshRegex(StringArgumentType.getString(context, "regex")))))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("style")
                .then(RequiredArgumentBuilder.<FabricClientCommandSource, String>argument("target", StringArgumentType.word())
                    .then(RequiredArgumentBuilder.<FabricClientCommandSource, String>argument("template", StringArgumentType.greedyString())
                        .executes(context -> style(StringArgumentType.getString(context, "target"),
                            StringArgumentType.getString(context, "template")))))));
    }

    private static void refreshPlayers() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        mc.level.players().forEach(player -> add(player.getGameProfile().name()));
    }

    private static Stats player(String name, DracoConfig cfg) {
        Stats existing = find(name);
        if (existing != null) return existing;
        if (!cfg.kuudraBreakdownIncludeLatePlayers) return new Stats(name);
        return add(name);
    }

    private static Stats add(String name) {
        if (name == null || !name.matches("\\w{1,16}")) return new Stats("Unknown");
        Stats found = find(name);
        if (found != null) return found;
        Stats created = new Stats(name);
        PLAYERS.put(name.toLowerCase(Locale.ROOT), created);
        return created;
    }

    private static Stats find(String name) {
        return name == null ? null : PLAYERS.get(name.toLowerCase(Locale.ROOT));
    }

    private static void print(boolean finish) {
        if (printed || PLAYERS.isEmpty()) return;
        if (finish) {
            printed = true;
            active = false;
        }
        DracoConfig cfg = config();
        if (cfg == null) return;
        List<Stats> rows = new ArrayList<>(PLAYERS.values());
        Comparator<Stats> comparator = switch (Math.clamp(cfg.kuudraBreakdownSort, 0, 3)) {
            case 1 -> Comparator.comparingInt((Stats value) -> value.supplies).reversed();
            case 2 -> Comparator.comparingInt(Stats::total).reversed();
            case 3 -> Comparator.comparing(value -> value.name.toLowerCase(Locale.ROOT));
            default -> null;
        };
        if (comparator != null) rows.sort(comparator.thenComparing(value -> value.name.toLowerCase(Locale.ROOT)));
        local(clean(cfg.kuudraBreakdownHeader));
        int totalFresh = 0;
        for (Stats stats : rows) {
            totalFresh += stats.fresh;
            if (!cfg.kuudraBreakdownShowZeroPlayers && stats.total() == 0) continue;
            Component line = Component.literal(format(cfg.kuudraBreakdownLine, stats, cfg));
            if (cfg.kuudraBreakdownShowStuns && stats.stuns > 0)
                line = line.copy().withStyle(style -> style.withHoverEvent(new HoverEvent.ShowText(
                    Component.literal("§c" + stats.stuns + " §fStuns"))));
            local(line);
        }
        if (cfg.kuudraBreakdownShowFreshTotal) {
            Component footer = Component.literal(clean(cfg.kuudraBreakdownFooter)
                .replace("{fresh}", Integer.toString(totalFresh)));
            if (cfg.kuudraBreakdownShowFresh && totalFresh > 0) {
                StringBuilder hover = new StringBuilder();
                for (Stats stats : rows) if (stats.fresh > 0) {
                    if (!hover.isEmpty()) hover.append('\n');
                    hover.append("§c").append(stats.name).append("§f: ").append(stats.fresh);
                }
                footer = footer.copy().withStyle(style -> style.withHoverEvent(new HoverEvent.ShowText(
                    Component.literal(hover.toString()))));
            }
            local(footer);
        }
    }

    private static String format(String template, Stats stats, DracoConfig cfg) {
        String result = clean(template)
            .replace("{stun-section}", cfg.kuudraBreakdownShowStuns ? " §7| §c" + stats.stuns + " §fStuns" : "")
            .replace("{fresh-section}", cfg.kuudraBreakdownShowFresh ? " §7| §c" + stats.fresh + " §fFresh" : "")
            .replace("{player}", stats.name)
            .replace("{supplies}", Integer.toString(stats.supplies))
            .replace("{fuels}", Integer.toString(stats.fuels))
            .replace("{deaths}", Integer.toString(stats.deaths))
            .replace("{stuns}", cfg.kuudraBreakdownShowStuns ? Integer.toString(stats.stuns) : "-")
            .replace("{fresh}", cfg.kuudraBreakdownShowFresh ? Integer.toString(stats.fresh) : "-")
            .replace("{total}", Integer.toString(stats.total()));
        return result.replace('\n', ' ').replace('\r', ' ').trim();
    }

    private static int status() {
        DracoConfig cfg = config();
        local("Kuudra breakdown " + (cfg.kuudraBreakdown ? "enabled" : "disabled") + "; tracking "
            + PLAYERS.size() + " players; trigger " + (cfg.kuudraBreakdownTrigger == 1 ? "down" : "Elle")
            + "; sort " + sortName(cfg.kuudraBreakdownSort) + ".");
        return 1;
    }

    private static int show() {
        if (!active || PLAYERS.isEmpty()) {
            local("No active Kuudra breakdown is available.");
            return 0;
        }
        print(false);
        return 1;
    }

    private static int option(String value, boolean enabled) {
        DracoConfig cfg = config();
        switch (value.toLowerCase(Locale.ROOT)) {
            case "enabled", "master" -> cfg.kuudraBreakdown = enabled;
            case "late", "lateplayers" -> cfg.kuudraBreakdownIncludeLatePlayers = enabled;
            case "zeros", "zeroplayers" -> cfg.kuudraBreakdownShowZeroPlayers = enabled;
            case "stuns" -> cfg.kuudraBreakdownShowStuns = enabled;
            case "fresh" -> cfg.kuudraBreakdownShowFresh = enabled;
            case "freshtotal", "footer" -> cfg.kuudraBreakdownShowFreshTotal = enabled;
            default -> {
                local("Option must be enabled, late, zeros, stuns, fresh, or freshtotal.");
                return 0;
            }
        }
        if (!cfg.kuudraBreakdown) reset();
        save();
        local("Kuudra breakdown " + value + " set to " + enabled + ".");
        return 1;
    }

    private static int sort(String value) {
        int mode = switch (value.toLowerCase(Locale.ROOT)) {
            case "team", "order", "0" -> 0;
            case "supply", "supplies", "1" -> 1;
            case "total", "contribution", "2" -> 2;
            case "name", "alphabetical", "3" -> 3;
            default -> -1;
        };
        if (mode < 0) { local("Sort must be team, supplies, total, or name."); return 0; }
        config().kuudraBreakdownSort = mode;
        save();
        local("Kuudra breakdown sort set to " + sortName(mode) + ".");
        return 1;
    }

    private static int trigger(String value) {
        int mode = switch (value.toLowerCase(Locale.ROOT)) {
            case "elle", "dialogue", "0" -> 0;
            case "down", "success", "1" -> 1;
            default -> -1;
        };
        if (mode < 0) { local("Trigger must be Elle or down."); return 0; }
        config().kuudraBreakdownTrigger = mode;
        save();
        local("Kuudra breakdown trigger updated.");
        return 1;
    }

    private static int freshRegex(String value) {
        String clean = value.trim();
        if (clean.isEmpty() || clean.length() > 160) { local("Fresh regex must be 1-160 characters."); return 0; }
        try { Pattern.compile(clean); } catch (PatternSyntaxException ignored) { local("Fresh regex is invalid."); return 0; }
        config().kuudraBreakdownFreshRegex = clean;
        save();
        local("Kuudra breakdown Fresh regex updated.");
        return 1;
    }

    private static int style(String target, String value) {
        String clean = value.trim();
        if (clean.isEmpty() || clean.length() > 240) { local("Template must be 1-240 characters."); return 0; }
        DracoConfig cfg = config();
        switch (target.toLowerCase(Locale.ROOT)) {
            case "header" -> cfg.kuudraBreakdownHeader = clean;
            case "line", "player" -> cfg.kuudraBreakdownLine = clean;
            case "footer", "fresh" -> cfg.kuudraBreakdownFooter = clean;
            default -> { local("Style target must be header, line, or footer."); return 0; }
        }
        save();
        local("Kuudra breakdown " + target + " template updated.");
        return 1;
    }

    private static String sortName(int mode) {
        return switch (Math.clamp(mode, 0, 3)) { case 1 -> "supplies"; case 2 -> "total"; case 3 -> "name"; default -> "team"; };
    }

    private static String localName() {
        Minecraft mc = Minecraft.getInstance();
        return mc.player == null ? null : mc.player.getGameProfile().name();
    }

    private static String clean(String text) {
        if (text == null) return "";
        return text.replace("<dark_red>", "§4").replace("<dark_gray>", "§8")
            .replace("<gray>", "§7").replace("<red>", "§c").replace("<green>", "§a")
            .replace("<yellow>", "§e").replace("<orange>", "§6").replace("<white>", "§f")
            .replace("<reset>", "§r").replace("<r>", "§r");
    }

    private static DracoConfig config() {
        return ConstellationClient.cfg() == null ? null : ConstellationClient.cfg().draco;
    }

    private static void save() { ConstellationClient.saveConfig(); }
    private static void local(String text) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && text != null && !text.isBlank()) mc.player.sendSystemMessage(Component.literal(text));
    }
    private static void local(Component text) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && text != null && !text.getString().isBlank()) mc.player.sendSystemMessage(text);
    }

    private static final class Stats {
        final String name;
        int supplies;
        int fuels;
        int deaths;
        int stuns;
        int fresh;

        Stats(String name) { this.name = name; }
        int total() { return supplies + fuels + stuns + fresh; }
    }
}
