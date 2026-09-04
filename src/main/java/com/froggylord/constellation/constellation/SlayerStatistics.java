package com.froggylord.constellation.constellation;

import com.froggylord.constellation.ConstellationClient;
import com.froggylord.constellation.config.PerseusConfig;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// ported from Athen (BSD-3-Clause): modules/impl/slayer/SlayerStats.kt
// drop persistence and chance display ported from Athen (BSD-3-Clause): modules/impl/slayer/SlayerDropsData.kt
// full canonical tables ported from Athen (BSD-3-Clause): api/slayers/enums/drop/impl/*Drops.kt
// vampire XP values cross-checked with Skyblocker (LGPL-3.0): skyblock/slayers/SlayerType.java
public final class SlayerStatistics {
    public enum Grade { GUARANTEED, OCCASIONAL, RARE, EXTRAORDINARY, PRAY_RNGESUS, RNGESUS_INCARNATE }
    public record Drop(SlayerState.Type type, String name, Grade grade, long targetXp, double baseChance) {
        String key() { return type.name().toLowerCase(Locale.ROOT) + ":" + slug(name); }
    }

    private static final Pattern DROP_LINE = Pattern.compile("^(?:RARE DROP!|VERY RARE DROP!|CRAZY RARE DROP!|INSANE DROP!) \\((.*?)\\) \\(\\+(\\d+)% .? Magic Find\\)$", Pattern.CASE_INSENSITIVE);
    private static final Pattern RNG_XP = Pattern.compile("^\\s*RNG Meter - ([\\d,]+) Stored XP$");
    private static final Pattern RNG_SELECTED = Pattern.compile("^You set your (.*?) RNG Meter to drop (.*?)!$", Pattern.CASE_INSENSITIVE);
    private static final Map<String, Drop> LOOKUP = new HashMap<>();
    private static final Map<SlayerState.Type, List<Drop>> BY_TYPE = new java.util.EnumMap<>(SlayerState.Type.class);
    private static final List<Drop> DROPS = List.of(
        d(SlayerState.Type.REV,"Revenant flesh",Grade.GUARANTEED,0,100), d(SlayerState.Type.REV,"Foul flesh",Grade.OCCASIONAL,3093,16.1616), d(SlayerState.Type.REV,"Pestilence I",Grade.RARE,7977,6.2679), d(SlayerState.Type.REV,"Undead catalyst",Grade.EXTRAORDINARY,24750,2.0202), d(SlayerState.Type.REV,"Enchanted Book (Smite VI)",Grade.EXTRAORDINARY,124370,.402), d(SlayerState.Type.REV,"Beheaded horror",Grade.PRAY_RNGESUS,310925,.1608), d(SlayerState.Type.REV,"Revenant catalyst",Grade.EXTRAORDINARY,49500,1.0101), d(SlayerState.Type.REV,"Snake rune I",Grade.PRAY_RNGESUS,332250,.1505), d(SlayerState.Type.REV,"Festering maggot",Grade.PRAY_RNGESUS,367424,.1361), d(SlayerState.Type.REV,"Revenant viscera",Grade.OCCASIONAL,3674,13.6082), d(SlayerState.Type.REV,"Scythe blade",Grade.PRAY_RNGESUS,489900,.1021), d(SlayerState.Type.REV,"Severed hand",Grade.PRAY_RNGESUS,1049785,.0476), d(SlayerState.Type.REV,"Shredded sinew",Grade.PRAY_RNGESUS,918562,.1021), d(SlayerState.Type.REV,"Warden heart",Grade.RNGESUS_INCARNATE,3674250,.0186), d(SlayerState.Type.REV,"Matcha dye",Grade.RNGESUS_INCARNATE,75000000,.0008),
        d(SlayerState.Type.TARA,"Tarantula web",Grade.GUARANTEED,0,100), d(SlayerState.Type.TARA,"Toxic arrow poison",Grade.OCCASIONAL,3277,15.2542), d(SlayerState.Type.TARA,"Bite rune I",Grade.RARE,7657,6.5292), d(SlayerState.Type.TARA,"Darkness within rune I",Grade.PRAY_RNGESUS,74930,.6673), d(SlayerState.Type.TARA,"Spider catalyst",Grade.EXTRAORDINARY,24250,2.0619), d(SlayerState.Type.TARA,"Tarantula silk",Grade.OCCASIONAL,3513,14.2318), d(SlayerState.Type.TARA,"Enchanted Book (Bane of Arthropods VI)",Grade.PRAY_RNGESUS,120269,.4157), d(SlayerState.Type.TARA,"Tarantula catalyst",Grade.PRAY_RNGESUS,117108,.427), d(SlayerState.Type.TARA,"Fly swatter",Grade.PRAY_RNGESUS,234216,.2135), d(SlayerState.Type.TARA,"Vial of venom",Grade.PRAY_RNGESUS,351325,.1423), d(SlayerState.Type.TARA,"Tarantula talisman",Grade.PRAY_RNGESUS,234216,.2135), d(SlayerState.Type.TARA,"Digested mosquito",Grade.PRAY_RNGESUS,702650,.0712), d(SlayerState.Type.TARA,"Shriveled wasp",Grade.PRAY_RNGESUS,351325,.1423), d(SlayerState.Type.TARA,"Ensnared snail",Grade.PRAY_RNGESUS,1171083,.0427), d(SlayerState.Type.TARA,"Primordial eye",Grade.RNGESUS_INCARNATE,3513250,.0142), d(SlayerState.Type.TARA,"Brick red dye",Grade.RNGESUS_INCARNATE,75000000,.0004),
        d(SlayerState.Type.SVEN,"Wolf tooth",Grade.GUARANTEED,0,100), d(SlayerState.Type.SVEN,"Hamster wheel",Grade.OCCASIONAL,3000,16.6667), d(SlayerState.Type.SVEN,"Spirit rune I",Grade.RARE,7917,6.3154), d(SlayerState.Type.SVEN,"Enchanted Book (Critical VI)",Grade.EXTRAORDINARY,61634,.8112), d(SlayerState.Type.SVEN,"Furball",Grade.EXTRAORDINARY,30637,1.632), d(SlayerState.Type.SVEN,"Red claw egg",Grade.PRAY_RNGESUS,410900,.1217), d(SlayerState.Type.SVEN,"Couture rune I",Grade.PRAY_RNGESUS,219833,.2274), d(SlayerState.Type.SVEN,"Grizzly salmon",Grade.PRAY_RNGESUS,880500,.0568), d(SlayerState.Type.SVEN,"Overflux capacitor",Grade.PRAY_RNGESUS,1232700,.0406), d(SlayerState.Type.SVEN,"Celeste dye",Grade.PRAY_RNGESUS,75000000,.0002),
        d(SlayerState.Type.VOID,"Null sphere",Grade.GUARANTEED,0,100), d(SlayerState.Type.VOID,"Twilight arrow poison",Grade.OCCASIONAL,3300,15.1515), d(SlayerState.Type.VOID,"Endersnake rune I",Grade.RARE,9438,5.2977), d(SlayerState.Type.VOID,"Summoning eye",Grade.EXTRAORDINARY,74250,.6734), d(SlayerState.Type.VOID,"Enchanted Book (Mana Steal I)",Grade.RARE,11183,4.4709), d(SlayerState.Type.VOID,"Transmission tuner",Grade.EXTRAORDINARY,22366,2.2355), d(SlayerState.Type.VOID,"Null atom",Grade.RARE,10120,4.9404), d(SlayerState.Type.VOID,"Hazmat enderman",Grade.EXTRAORDINARY,32202,1.5527), d(SlayerState.Type.VOID,"Pocket espresso machine",Grade.PRAY_RNGESUS,128809,.3882), d(SlayerState.Type.VOID,"Enchanted Book (Smarty Pants I)",Grade.EXTRAORDINARY,28338,1.7644), d(SlayerState.Type.VOID,"End rune I",Grade.EXTRAORDINARY,75505,.6622), d(SlayerState.Type.VOID,"Handy blood chalice",Grade.PRAY_RNGESUS,283380,.1764), d(SlayerState.Type.VOID,"Sinful dice",Grade.PRAY_RNGESUS,108992,.4587), d(SlayerState.Type.VOID,"Exceedingly rare ender artifact upgrade",Grade.RNGESUS_INCARNATE,1771125,.0282), d(SlayerState.Type.VOID,"Void conqueror enderman skin",Grade.PRAY_RNGESUS,302020,.1656), d(SlayerState.Type.VOID,"Etherwarp merger",Grade.PRAY_RNGESUS,118075,.4235), d(SlayerState.Type.VOID,"Judgement core",Grade.PRAY_RNGESUS,885562,.0565), d(SlayerState.Type.VOID,"Enchant rune I",Grade.PRAY_RNGESUS,1078642,.0464), d(SlayerState.Type.VOID,"Endstone idol",Grade.RNGESUS_INCARNATE,3542250,.0141), d(SlayerState.Type.VOID,"Byzantium dye",Grade.RNGESUS_INCARNATE,75000000,.0002),
        d(SlayerState.Type.BLAZE,"Derelict ashe",Grade.GUARANTEED,0,100), d(SlayerState.Type.BLAZE,"Enchanted blaze powder",Grade.OCCASIONAL,2351,21.2598), d(SlayerState.Type.BLAZE,"Lavatears rune I",Grade.RARE,21660,2.3084), d(SlayerState.Type.BLAZE,"Wisp's Ice-Flavored Water I Splash Potion",Grade.RARE,14270,3.5039), d(SlayerState.Type.BLAZE,"Bundle of magma arrows",Grade.OCCASIONAL,4756,10.5116), d(SlayerState.Type.BLAZE,"Mana disintegrator",Grade.RARE,10192,4.9054), d(SlayerState.Type.BLAZE,"Scorched books",Grade.RARE,17837,2.8031), d(SlayerState.Type.BLAZE,"Kelvin inverter",Grade.RARE,14270,3.5039), d(SlayerState.Type.BLAZE,"Blaze rod distillate",Grade.RARE,0,4.6952), d(SlayerState.Type.BLAZE,"Glowstone distillate",Grade.RARE,0,4.6952), d(SlayerState.Type.BLAZE,"Magma cream distillate",Grade.RARE,0,4.6952), d(SlayerState.Type.BLAZE,"Nether wart distillate",Grade.RARE,0,4.6952), d(SlayerState.Type.BLAZE,"Gabagool distillate",Grade.RARE,10649,4.6952), d(SlayerState.Type.BLAZE,"Scorched power crystal",Grade.RARE,12558,3.9814), d(SlayerState.Type.BLAZE,"Archfiend dice",Grade.EXTRAORDINARY,37675,1.3271), d(SlayerState.Type.BLAZE,"Enchanted Book (Fire Aspect VI)",Grade.EXTRAORDINARY,32508,1.5381), d(SlayerState.Type.BLAZE,"Fiery burst rune I",Grade.PRAY_RNGESUS,243675,.2052), d(SlayerState.Type.BLAZE,"Flawed opal gemstone",Grade.RARE,14776,3.3838), d(SlayerState.Type.BLAZE,"Enchanted Book (Duplex I)",Grade.RARE,23220,2.1533), d(SlayerState.Type.BLAZE,"High class archfiend dice",Grade.PRAY_RNGESUS,194939,.2565), d(SlayerState.Type.BLAZE,"Wilson's engineering plans",Grade.PRAY_RNGESUS,478058,.1046), d(SlayerState.Type.BLAZE,"Subzero inverter",Grade.PRAY_RNGESUS,478058,.1046), d(SlayerState.Type.BLAZE,"Flame dye",Grade.RNGESUS_INCARNATE,75000000,.0002),
        d(SlayerState.Type.VAMP,"Coven seal",Grade.GUARANTEED,0,100), d(SlayerState.Type.VAMP,"Bundle of quantum book",Grade.OCCASIONAL,1687,13.3333), d(SlayerState.Type.VAMP,"Soultwist rune",Grade.OCCASIONAL,1912,11.7647), d(SlayerState.Type.VAMP,"Bubba blister",Grade.OCCASIONAL,2250,10), d(SlayerState.Type.VAMP,"Chocolate chip",Grade.OCCASIONAL,2250,10), d(SlayerState.Type.VAMP,"Guardian lucky block",Grade.RARE,3600,6.25), d(SlayerState.Type.VAMP,"McGrubber burger",Grade.EXTRAORDINARY,18450,1.2195), d(SlayerState.Type.VAMP,"Unfanged vampire part",Grade.EXTRAORDINARY,18450,1.2195), d(SlayerState.Type.VAMP,"Bundle of The One book",Grade.RARE,12525,1.7964), d(SlayerState.Type.VAMP,"Sangria dye",Grade.PRAY_RNGESUS,1687,.01)
    );

    private static PerseusConfig cfg;
    private static long sessionStartNanos;
    private static long sessionKills;
    private static long sessionXp;
    private static double sessionKillSeconds;
    private static SlayerState.Type sessionType;
    private static int sessionTier;
    private static SlayerState.Type lastDeathType;
    private static int lastDeathTier;
    private static long lastDeathNanos;
    private static boolean initialized;

    static {
        for (SlayerState.Type type : SlayerState.Type.values()) BY_TYPE.put(type, new ArrayList<>());
        for (Drop drop : DROPS) {
            BY_TYPE.get(drop.type()).add(drop);
            LOOKUP.put(normal(drop.name()), drop);
        }
        alias("Smite VI", SlayerState.Type.REV, "Enchanted Book (Smite VI)");
        alias("Bane of Arthropods VI", SlayerState.Type.TARA, "Enchanted Book (Bane of Arthropods VI)");
        alias("Critical VI", SlayerState.Type.SVEN, "Enchanted Book (Critical VI)");
        alias("Mana Steal I", SlayerState.Type.VOID, "Enchanted Book (Mana Steal I)");
        alias("Smarty Pants I", SlayerState.Type.VOID, "Enchanted Book (Smarty Pants I)");
        alias("Ender artifact upgrade", SlayerState.Type.VOID, "Exceedingly rare ender artifact upgrade");
        alias("Wisp's ice water", SlayerState.Type.BLAZE, "Wisp's Ice-Flavored Water I Splash Potion");
        alias("Fire Aspect III", SlayerState.Type.BLAZE, "Enchanted Book (Fire Aspect VI)");
        alias("Duplex I", SlayerState.Type.BLAZE, "Enchanted Book (Duplex I)");
    }

    private SlayerStatistics() {}

    public static List<Drop> drops() { return DROPS; }
    public static Drop findDrop(SlayerState.Type type, String name) { return find(type, name); }

    public static void init(PerseusConfig config) {
        cfg = config;
        normalizeStorage();
        if (initialized) return;
        initialized = true;
        SlayerState.listen(new SlayerState.Listener() {
            @Override public void onSpawn(SlayerState.Boss boss) {}
            @Override public void onDeath(SlayerState.Boss boss, double seconds, int ticks) { death(boss, seconds); }
            @Override public void onReset() { lastDeathType = null; lastDeathNanos = 0; }
        });
        ClientReceiveMessageEvents.GAME.register((message, overlay) -> { if (!overlay) chat(message.getString()); });
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> connectionReset());
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> connectionReset());
    }

    private static void connectionReset() {
        lastDeathType = null;
        lastDeathNanos = 0;
        if (cfg != null && cfg.slayerStatsResetOnConnect) resetSession();
    }

    private static void death(SlayerState.Boss boss, double seconds) {
        if (!active() || !owned(boss)) return;
        if (boss.type() == SlayerState.Type.TARA && boss.tier() == 5 && !boss.variant().equals("Conjoined Brood")) return;
        long now = System.nanoTime();
        if (cfg.slayerStats) {
            boolean changed = sessionType != null && (sessionType != boss.type() || sessionTier != boss.tier());
            if (changed && cfg.slayerStatsSeparateTypeTier) {
                if (cfg.slayerStatsWarnTypeChange) local("§eSlayer type or tier changed; session statistics were reset.");
                resetSession();
            } else if (changed && cfg.slayerStatsWarnTypeChange) {
                local("§eSlayer type changed. Session totals now include multiple types; use /slayerstats reset session to clear them.");
            }
            if (sessionStartNanos == 0) sessionStartNanos = now;
            sessionType = boss.type();
            sessionTier = boss.tier();
            sessionKills++;
            int xp = xp(boss.type(), boss.tier());
            sessionXp += xp;
            sessionKillSeconds += Math.max(0, seconds);
            String statKey = statKey(boss.type(), boss.tier());
            if (cfg.slayerStatsPersistent) {
                cfg.slayerLifetimeKills.merge(statKey, 1L, Long::sum);
                cfg.slayerLifetimeXp.merge(statKey, (long) xp, Long::sum);
                cfg.slayerLifetimeKillSeconds.merge(statKey, Math.max(0, seconds), Double::sum);
            }
        }
        if (cfg.slayerDropsData) {
            lastDeathType = boss.type();
            lastDeathTier = boss.tier();
            lastDeathNanos = now;
            if (cfg.slayerDropsShowSinceLast)
                for (Drop drop : BY_TYPE.get(boss.type())) if (tracked(drop.grade())) cfg.slayerBossesSinceDrop.merge(drop.key(), 1L, Long::sum);
        }
        ConstellationClient.saveConfig();
    }

    private static void chat(String raw) {
        if (!active()) return;
        String stripped = ChatFormatting.stripFormatting(raw);
        String text = stripped == null ? raw.trim() : stripped.trim();
        if (text.equals("SLAYER QUEST STARTED!") && cfg.slayerStats && sessionStartNanos == 0) sessionStartNanos = System.nanoTime();
        Matcher selected = RNG_SELECTED.matcher(text);
        if (selected.matches() && cfg.slayerDropsData && cfg.slayerDropsAutoSelect) {
            SlayerState.Type type = typeFromBoss(selected.group(1));
            Drop drop = find(type, selected.group(2));
            if (drop != null) {
                cfg.slayerSelectedDrops.put(type.name(), drop.name());
                ConstellationClient.saveConfig();
                local("§aSelected " + typeLabel(type) + " RNG drop: " + drop.name());
            }
            return;
        }
        if (!cfg.slayerDropsData || lastDeathType == null || System.nanoTime() - lastDeathNanos > cfg.slayerDropsAttributionSeconds * 1_000_000_000L) return;
        Matcher xp = RNG_XP.matcher(text);
        if (xp.matches()) {
            Long amount = number(xp.group(1));
            if (amount != null) {
                cfg.slayerRngStoredXp.put(lastDeathType.name(), amount);
                ConstellationClient.saveConfig();
                if (cfg.slayerDropsShowChanceAfterKill) showChance(lastDeathType, lastDeathTier);
            }
            return;
        }
        Matcher dropLine = DROP_LINE.matcher(text);
        if (!dropLine.matches()) return;
        Drop drop = find(lastDeathType, dropLine.group(1));
        if (drop == null) return;
        int mf;
        try { mf = Integer.parseInt(dropLine.group(2)); } catch (NumberFormatException ignored) { return; }
        cfg.slayerLastMagicFind.put(lastDeathType.name(), mf);
        cfg.slayerDropCounts.merge(drop.key(), 1L, Long::sum);
        long since = cfg.slayerBossesSinceDrop.getOrDefault(drop.key(), 0L);
        cfg.slayerBossesSinceDrop.put(drop.key(), 0L);
        ConstellationClient.saveConfig();
        if (cfg.slayerDropsShowSinceLast && tracked(drop.grade()) && cfg.slayerDropsChatAnnouncement)
            local("§c" + since + " §fbosses since last §c" + drop.name() + "§f. Total: §c" + cfg.slayerDropCounts.get(drop.key()));
    }

    public static List<String> hudLines() {
        if (!active() || !cfg.slayerStats || !cfg.slayerStatsHud || sessionKills <= 0) return List.of();
        double seconds = Math.max(.001, (System.nanoTime() - sessionStartNanos) / 1_000_000_000.0);
        List<String> lines = new ArrayList<>();
        if (!cfg.slayerStatsTitleStyle.isBlank()) lines.add(clean(cfg.slayerStatsTitleStyle));
        if (cfg.slayerStatsBosses) lines.add(style(cfg.slayerStatsBossesStyle, number(sessionKills)));
        if (cfg.slayerStatsBossesPerHour) lines.add(style(cfg.slayerStatsBossesPerHourStyle, decimal(sessionKills * 3600.0 / seconds)));
        if (cfg.slayerStatsXpPerHour) lines.add(style(cfg.slayerStatsXpPerHourStyle, decimal(sessionXp * 3600.0 / seconds)));
        if (cfg.slayerStatsAverageKill) lines.add(style(cfg.slayerStatsAverageKillStyle, formatSeconds(sessionKillSeconds / sessionKills)));
        if (cfg.slayerStatsSessionTime) lines.add(style(cfg.slayerStatsSessionStyle, duration((long) seconds)));
        return List.copyOf(lines);
    }

    public static List<String> dropHudLines() {
        if (!active() || !cfg.slayerDropsData || !cfg.slayerDropsHud || lastDeathType == null) return List.of();
        Drop selected = selected(lastDeathType);
        if (selected == null) return List.of("No RNG drop selected");
        long stored = cfg.slayerRngStoredXp.getOrDefault(lastDeathType.name(), 0L);
        int mf = cfg.slayerLastMagicFind.getOrDefault(lastDeathType.name(), 0);
        long since = cfg.slayerBossesSinceDrop.getOrDefault(selected.key(), 0L);
        List<String> out = new ArrayList<>();
        out.add(typeLabel(lastDeathType) + " T" + lastDeathTier + ": " + selected.name());
        if (selected.targetXp() > 0) {
            long bossXp = Math.max(1, xp(lastDeathType, lastDeathTier));
            long remaining = Math.max(0, selected.targetXp() - stored);
            out.add(number(stored) + "/" + number(selected.targetXp()) + " XP | " + ((remaining + bossXp - 1) / bossXp) + " bosses");
            out.add(chance(selected, stored, mf) + "% | MF " + mf);
        }
        if (cfg.slayerDropsShowSinceLast) out.add("Since last: " + since);
        return List.copyOf(out);
    }

    public static void registerCommands(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        dispatcher.register(LiteralArgumentBuilder.<FabricClientCommandSource>literal("slayerstats")
            .executes(c -> status())
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("status").executes(c -> status()))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("reset")
                .then(RequiredArgumentBuilder.<FabricClientCommandSource, String>argument("scope", StringArgumentType.word())
                    .executes(c -> reset(StringArgumentType.getString(c, "scope")))))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("decimals")
                .then(RequiredArgumentBuilder.<FabricClientCommandSource, Integer>argument("value", IntegerArgumentType.integer(0, 3))
                    .executes(c -> decimals(IntegerArgumentType.getInteger(c, "value")))))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("style")
                .then(RequiredArgumentBuilder.<FabricClientCommandSource, String>argument("line", StringArgumentType.word())
                    .then(RequiredArgumentBuilder.<FabricClientCommandSource, String>argument("template", StringArgumentType.greedyString())
                        .executes(c -> styleCommand(StringArgumentType.getString(c, "line"), StringArgumentType.getString(c, "template")))))));
        dispatcher.register(LiteralArgumentBuilder.<FabricClientCommandSource>literal("slayerdrops")
            .executes(c -> dropStatus())
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("status").executes(c -> dropStatus()))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("list")
                .then(RequiredArgumentBuilder.<FabricClientCommandSource, String>argument("type", StringArgumentType.word())
                    .executes(c -> listDrops(StringArgumentType.getString(c, "type")))))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("select")
                .then(RequiredArgumentBuilder.<FabricClientCommandSource, String>argument("type", StringArgumentType.word())
                    .then(RequiredArgumentBuilder.<FabricClientCommandSource, String>argument("drop", StringArgumentType.greedyString())
                        .executes(c -> select(StringArgumentType.getString(c, "type"), StringArgumentType.getString(c, "drop"))))))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("precision")
                .then(RequiredArgumentBuilder.<FabricClientCommandSource, Integer>argument("decimals", IntegerArgumentType.integer(0, 8))
                    .executes(c -> dropPrecision(IntegerArgumentType.getInteger(c, "decimals")))))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("window")
                .then(RequiredArgumentBuilder.<FabricClientCommandSource, Integer>argument("seconds", IntegerArgumentType.integer(3, 30))
                    .executes(c -> attributionWindow(IntegerArgumentType.getInteger(c, "seconds")))))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("clear")
                .then(RequiredArgumentBuilder.<FabricClientCommandSource, String>argument("scope", StringArgumentType.word())
                    .executes(c -> clearDrops(StringArgumentType.getString(c, "scope"))))));
    }

    private static int status() {
        local("§eSlayer session: " + sessionKills + " bosses, " + number(sessionXp) + " XP, " + formatSeconds(sessionKillSeconds) + " total kill time.");
        local("§7Persistent records: " + (cfg.slayerStatsPersistent ? "on" : "off") + "; reset on connect: " + (cfg.slayerStatsResetOnConnect ? "on" : "off") + ".");
        for (String key : cfg.slayerLifetimeKills.keySet()) local(" §8- §f" + key + ": §c" + cfg.slayerLifetimeKills.get(key) + " §7bosses, §c" + number(cfg.slayerLifetimeXp.getOrDefault(key, 0L)) + " §7XP");
        return 1;
    }

    private static int reset(String scope) {
        if (scope.equalsIgnoreCase("session")) resetSession();
        else if (scope.equalsIgnoreCase("lifetime") || scope.equalsIgnoreCase("all")) {
            resetSession(); cfg.slayerLifetimeKills.clear(); cfg.slayerLifetimeXp.clear(); cfg.slayerLifetimeKillSeconds.clear(); ConstellationClient.saveConfig();
        } else { local("§cUse session, lifetime, or all."); return 0; }
        local("§aSlayer " + scope.toLowerCase(Locale.ROOT) + " statistics cleared.");
        return 1;
    }

    private static int decimals(int value) { cfg.slayerStatsDecimals = value; ConstellationClient.saveConfig(); local("§aSlayer stats precision set to " + value + "."); return 1; }
    private static int styleCommand(String line, String template) {
        switch (line.toLowerCase(Locale.ROOT)) {
            case "title" -> cfg.slayerStatsTitleStyle = template;
            case "bosses", "kills" -> cfg.slayerStatsBossesStyle = template;
            case "rate", "bossesperhour" -> cfg.slayerStatsBossesPerHourStyle = template;
            case "xp", "xpperhour" -> cfg.slayerStatsXpPerHourStyle = template;
            case "kill", "average" -> cfg.slayerStatsAverageKillStyle = template;
            case "session", "time" -> cfg.slayerStatsSessionStyle = template;
            default -> { local("§cUnknown line. Use title, bosses, rate, xp, kill, or session."); return 0; }
        }
        ConstellationClient.saveConfig(); local("§aSlayer stats style updated."); return 1;
    }

    private static int dropStatus() {
        local("§eSlayer drop data: " + (cfg.slayerDropsData ? "on" : "off") + "; auto-select " + (cfg.slayerDropsAutoSelect ? "on" : "off") + ".");
        for (SlayerState.Type type : SlayerState.Type.values()) {
            Drop drop = selected(type);
            if (drop != null) local(" §8- §f" + typeLabel(type) + ": §c" + drop.name() + " §7since " + cfg.slayerBossesSinceDrop.getOrDefault(drop.key(), 0L));
        }
        return 1;
    }

    private static int listDrops(String input) {
        SlayerState.Type type = SlayerState.parseType(input);
        if (type == null) { local("§cUnknown Slayer type."); return 0; }
        local("§e" + typeLabel(type) + " drops:");
        for (Drop drop : BY_TYPE.get(type)) local(" §8- §f" + drop.name() + " §7[" + drop.grade().name().toLowerCase(Locale.ROOT).replace('_', ' ') + "]");
        return 1;
    }

    private static int select(String input, String name) {
        SlayerState.Type type = SlayerState.parseType(input);
        Drop drop = find(type, name);
        if (drop == null) { local("§cUnknown drop for that Slayer. Use /slayerdrops list <type>."); return 0; }
        cfg.slayerSelectedDrops.put(type.name(), drop.name()); ConstellationClient.saveConfig(); local("§aSelected " + drop.name() + "."); return 1;
    }

    private static int clearDrops(String scope) {
        if (scope.equalsIgnoreCase("counts")) cfg.slayerDropCounts.clear();
        else if (scope.equalsIgnoreCase("since")) cfg.slayerBossesSinceDrop.clear();
        else if (scope.equalsIgnoreCase("meter")) { cfg.slayerRngStoredXp.clear(); cfg.slayerLastMagicFind.clear(); cfg.slayerSelectedDrops.clear(); }
        else if (scope.equalsIgnoreCase("all")) { cfg.slayerDropCounts.clear(); cfg.slayerBossesSinceDrop.clear(); cfg.slayerRngStoredXp.clear(); cfg.slayerLastMagicFind.clear(); cfg.slayerSelectedDrops.clear(); }
        else { local("§cUse counts, since, meter, or all."); return 0; }
        ConstellationClient.saveConfig(); local("§aSlayer drop " + scope + " data cleared."); return 1;
    }

    private static int dropPrecision(int value) { cfg.slayerDropsChanceDecimals = value; ConstellationClient.saveConfig(); local("§aSlayer drop chance precision set to " + value + "."); return 1; }
    private static int attributionWindow(int value) { cfg.slayerDropsAttributionSeconds = value; ConstellationClient.saveConfig(); local("§aSlayer drop attribution window set to " + value + " seconds."); return 1; }

    private static void showChance(SlayerState.Type type, int tier) {
        Drop drop = selected(type);
        if (drop == null || drop.targetXp() <= 0) return;
        long stored = cfg.slayerRngStoredXp.getOrDefault(type.name(), 0L);
        int mf = cfg.slayerLastMagicFind.getOrDefault(type.name(), 0);
        local(" §8- §a" + drop.name() + " §fchance: §b" + chance(drop, stored, mf) + "% §8[MF " + mf + "]");
    }

    private static String chance(Drop drop, long stored, int mf) {
        if (stored >= drop.targetXp()) return "100";
        double multiplier = 1 + Math.min(2.0 * stored / drop.targetXp(), 2.0);
        double value = drop.baseChance() * multiplier;
        if (value < 5) value *= 1 + mf / 100.0;
        return String.format(Locale.ROOT, "%." + Math.clamp(cfg.slayerDropsChanceDecimals, 0, 8) + "f", Math.min(100, value));
    }

    private static void normalizeStorage() {
        if (cfg.slayerLifetimeKills == null) cfg.slayerLifetimeKills = new LinkedHashMap<>(); else cfg.slayerLifetimeKills = positiveLongs(cfg.slayerLifetimeKills);
        if (cfg.slayerLifetimeXp == null) cfg.slayerLifetimeXp = new LinkedHashMap<>(); else cfg.slayerLifetimeXp = positiveLongs(cfg.slayerLifetimeXp);
        if (cfg.slayerLifetimeKillSeconds == null) cfg.slayerLifetimeKillSeconds = new LinkedHashMap<>(); else cfg.slayerLifetimeKillSeconds = positiveDoubles(cfg.slayerLifetimeKillSeconds);
        if (cfg.slayerSelectedDrops == null) cfg.slayerSelectedDrops = new LinkedHashMap<>(); else { Map<String,String> safe = new LinkedHashMap<>(); cfg.slayerSelectedDrops.forEach((k,v) -> { try { SlayerState.Type type = SlayerState.Type.valueOf(k); Drop drop = find(type,v); if (drop != null) safe.put(type.name(),drop.name()); } catch (Exception ignored) {} }); cfg.slayerSelectedDrops = safe; }
        if (cfg.slayerRngStoredXp == null) cfg.slayerRngStoredXp = new LinkedHashMap<>(); else cfg.slayerRngStoredXp = nonnegativeLongs(cfg.slayerRngStoredXp);
        if (cfg.slayerLastMagicFind == null) cfg.slayerLastMagicFind = new LinkedHashMap<>(); else { Map<String,Integer> safe = new LinkedHashMap<>(); cfg.slayerLastMagicFind.forEach((k,v) -> { if (k != null && !k.isBlank() && v != null) safe.put(k, Math.clamp(v, 0, 10000)); }); cfg.slayerLastMagicFind = safe; }
        if (cfg.slayerBossesSinceDrop == null) cfg.slayerBossesSinceDrop = new LinkedHashMap<>(); else cfg.slayerBossesSinceDrop = nonnegativeLongs(cfg.slayerBossesSinceDrop);
        if (cfg.slayerDropCounts == null) cfg.slayerDropCounts = new LinkedHashMap<>(); else cfg.slayerDropCounts = nonnegativeLongs(cfg.slayerDropCounts);
        cfg.slayerStatsDecimals = Math.clamp(cfg.slayerStatsDecimals, 0, 3);
        cfg.slayerDropsChanceDecimals = Math.clamp(cfg.slayerDropsChanceDecimals, 0, 8);
        cfg.slayerDropsAttributionSeconds = Math.clamp(cfg.slayerDropsAttributionSeconds, 3, 30);
    }

    private static Map<String, Long> positiveLongs(Map<String, Long> input) { Map<String, Long> out = new LinkedHashMap<>(); input.forEach((k,v) -> { if (k != null && !k.isBlank() && v != null && v > 0) out.put(k,v); }); return out; }
    private static Map<String, Long> nonnegativeLongs(Map<String, Long> input) { Map<String, Long> out = new LinkedHashMap<>(); input.forEach((k,v) -> { if (k != null && !k.isBlank() && v != null && v >= 0) out.put(k,v); }); return out; }
    private static Map<String, Double> positiveDoubles(Map<String, Double> input) { Map<String, Double> out = new LinkedHashMap<>(); input.forEach((k,v) -> { if (k != null && !k.isBlank() && v != null && Double.isFinite(v) && v > 0) out.put(k,v); }); return out; }
    private static void resetSession() { sessionStartNanos = 0; sessionKills = 0; sessionXp = 0; sessionKillSeconds = 0; sessionType = null; sessionTier = 0; }
    private static boolean active() { return cfg != null && cfg.enabled; }
    private static boolean owned(SlayerState.Boss boss) { Minecraft mc = Minecraft.getInstance(); return mc.player != null && boss.owner().equalsIgnoreCase(mc.player.getName().getString()); }
    private static boolean tracked(Grade grade) { return switch (grade) { case GUARANTEED -> cfg.slayerDropsTrackGuaranteed; case OCCASIONAL -> cfg.slayerDropsTrackOccasional; case RARE -> cfg.slayerDropsTrackRare; case EXTRAORDINARY -> cfg.slayerDropsTrackExtraordinary; case PRAY_RNGESUS -> cfg.slayerDropsTrackPrayRngesus; case RNGESUS_INCARNATE -> cfg.slayerDropsTrackRngesusIncarnate; }; }
    private static int xp(SlayerState.Type type, int tier) { if (type == SlayerState.Type.VAMP) return switch (tier) { case 1 -> 10; case 2 -> 25; case 3 -> 60; case 4 -> 120; case 5 -> 150; default -> 0; }; return switch (tier) { case 1 -> 5; case 2 -> 25; case 3 -> 100; case 4 -> 500; case 5 -> 1500; default -> 0; }; }
    private static String statKey(SlayerState.Type type, int tier) { return type.name().toLowerCase(Locale.ROOT) + ":t" + tier; }
    private static Drop d(SlayerState.Type type, String name, Grade grade, long xp, double chance) { return new Drop(type,name,grade,xp,chance); }
    private static void alias(String alias, SlayerState.Type type, String target) { Drop drop = find(type,target); if (drop != null) LOOKUP.put(normal(alias),drop); }
    private static Drop find(SlayerState.Type type, String name) { if (type == null || name == null) return null; Drop drop = LOOKUP.get(normal(name)); return drop != null && drop.type() == type ? drop : null; }
    private static Drop selected(SlayerState.Type type) { return type == null ? null : find(type, cfg.slayerSelectedDrops.get(type.name())); }
    private static SlayerState.Type typeFromBoss(String text) { String n = normal(text); if (n.contains("revenant")) return SlayerState.Type.REV; if (n.contains("tarantula")) return SlayerState.Type.TARA; if (n.contains("sven")) return SlayerState.Type.SVEN; if (n.contains("voidgloom")) return SlayerState.Type.VOID; if (n.contains("inferno")) return SlayerState.Type.BLAZE; if (n.contains("riftstalker") || n.contains("vampire")) return SlayerState.Type.VAMP; return null; }
    private static String normal(String value) { return value == null ? "" : value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", " ").trim(); }
    private static String slug(String value) { return normal(value).replace(' ', '_'); }
    private static Long number(String value) { try { return Long.parseLong(value.replace(",", "")); } catch (Exception ignored) { return null; } }
    private static String number(long value) { return NumberFormat.getIntegerInstance(Locale.US).format(value); }
    private static String decimal(double value) { return String.format(Locale.ROOT, "%." + Math.clamp(cfg.slayerStatsDecimals,0,3) + "f", Math.max(0,value)); }
    private static String style(String template, Object number) { return clean(template).replace("#number", String.valueOf(number)).replace("{number}", String.valueOf(number)); }
    private static String clean(String value) { return value == null ? "" : value.replace("<red>", "§c").replace("<gray>", "§7").replace("<dark_gray>", "§8").replace("<r>", "§r"); }
    private static String formatSeconds(double value) { return String.format(Locale.ROOT, "%." + Math.clamp(cfg.slayerStatsDecimals,0,3) + "fs", Math.max(0,value)); }
    private static String duration(long seconds) { long h=seconds/3600,m=(seconds%3600)/60,s=seconds%60; return h>0?h+"h "+m+"m "+s+"s":m>0?m+"m "+s+"s":s+"s"; }
    private static String typeLabel(SlayerState.Type type) { return switch (type) { case REV -> "Revenant"; case TARA -> "Tarantula"; case SVEN -> "Sven"; case VOID -> "Voidgloom"; case BLAZE -> "Inferno"; case VAMP -> "Riftstalker"; }; }
    private static void local(String text) { Minecraft mc = Minecraft.getInstance(); if (mc.player != null) mc.player.sendSystemMessage(Component.literal("§5Slayer §8> §f" + text)); }
}
