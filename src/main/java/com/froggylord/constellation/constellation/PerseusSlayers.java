package com.froggylord.constellation.constellation;

import com.froggylord.constellation.ConstellationClient;
import com.froggylord.constellation.config.PerseusConfig;
import com.froggylord.constellation.core.BaseConstellation;
import com.froggylord.constellation.core.InitContext;
import com.froggylord.constellation.hud.HudManager;
import com.froggylord.constellation.hud.HudPosition;
import com.froggylord.constellation.hud.SlayerHudWidget;
import com.froggylord.constellation.hud.AttunementHudWidget;
import com.froggylord.constellation.hud.SlayerDropsHudWidget;
import com.froggylord.constellation.hud.SlayerStatsHudWidget;
import com.froggylord.constellation.hud.CocoonHudWidget;
import com.froggylord.constellation.render.WorldRenderer;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
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
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// ported from devonian (GPL-3.0): features/slayers/SlayerDisplay.kt
// ported from devonian (GPL-3.0): features/slayers/BossSpawnTime.kt
// ported from devonian (GPL-3.0): features/slayers/BossSlainTime.kt
// cross-checked with Athen (BSD-3-Clause): modules/impl/slayer/SlayerDisplay.kt
// PB and world info ported from Athen (BSD-3-Clause): modules/impl/slayer/SlayerTimers.kt and SlayerInfo.kt
public class PerseusSlayers extends BaseConstellation {
    private static final Pattern TIMER = Pattern.compile("(?:^|\\s)(\\d{1,2}:\\d{2})(?:\\s|$)");
    private static final Pattern HEALTH = Pattern.compile("([0-9][0-9,]*(?:\\.[0-9]+)?[kKmMbBtT]?)\\s*\\u2764");
    private static final Pattern HITS = Pattern.compile("(?:^|\\s)(\\d+) Hits$");
    private static PerseusSlayers INSTANCE;
    private static SlayerState.Boss activeBoss;
    private static long questStartNanos;
    private static long questStartServerTick;
    private static final List<DeathLabel> DEATH_LABELS = new ArrayList<>();
    private static final Map<Integer, CachedAttached> ATTACHED_CACHE = new HashMap<>();
    private static boolean tarantulaFivePhaseOne;
    private PerseusConfig cfg;

    private record Attached(String timer, String health, String hits) {}
    private record CachedAttached(String timer, String hits, long timerSeenTick, long hitsSeenTick) {}
    private record DeathLabel(Vec3 position, String text, long expiresAtTick) {}

    @Override public String id() { return "perseus"; }
    @Override public String displayName() { return "Perseus"; }
    @Override public String description() { return "slayer display, information, and timers"; }

    @Override
    public void init(InitContext ctx) {
        INSTANCE = this;
        cfg = (PerseusConfig) config;
        normalize();
        SlayerState.init();
        SlayerHighlights.init(cfg);
        SlayerSpecialInfo.init(cfg);
        SlayerStatistics.init(cfg);
        BigSlayerDrops.init(cfg);
        SlayerCocoon.init(cfg);
        SlayerSounds.init(cfg);
        SlayerState.listen(new SlayerState.Listener() {
            @Override public void onSpawn(SlayerState.Boss boss) { spawned(boss); }
            @Override public void onDeath(SlayerState.Boss boss, double seconds, int ticks) { died(boss, seconds); }
            @Override public void onOwnerReset(String owner) { if (ownedBy(owner)) activeBoss = null; }
            @Override public void onReset() { resetTransient(); }
        });
        ClientReceiveMessageEvents.GAME.register((message, overlay) -> { if (!overlay) chat(message.getString()); });
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> resetTransient());
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> resetTransient());
        every(1, "perseus-slayer-state", SlayerState::tick);
        every(20, "perseus-slayer-cleanup", this::cleanup);
        registerRenderer(this::drawInfo);
        registerRenderer(SlayerHighlights::draw);
        registerRenderer(SlayerSpecialInfo::draw);
    }

    @Override
    public void registerHud(HudManager hud) {
        hud.register(new SlayerHudWidget(HudPosition.of(82, 72), () -> cfg != null && cfg.enabled && cfg.slayerDisplay));
        hud.register(new AttunementHudWidget(HudPosition.of(82, 126), () -> cfg != null && cfg.enabled && cfg.infernoAttunementDisplay));
        hud.register(new SlayerStatsHudWidget(HudPosition.of(82, 150), () -> cfg != null && cfg.enabled && cfg.slayerStats && cfg.slayerStatsHud));
        hud.register(new SlayerDropsHudWidget(HudPosition.of(82, 174), () -> cfg != null && cfg.enabled && cfg.slayerDropsData && cfg.slayerDropsHud));
        hud.register(new CocoonHudWidget(HudPosition.of(82, 198), () -> cfg != null && cfg.enabled && cfg.cocoonAlert && cfg.cocoonTimerHud));
    }

    @Override
    public void registerCommands(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        SlayerHighlights.registerCommands(dispatcher);
        SlayerSpecialInfo.registerCommands(dispatcher);
        SlayerStatistics.registerCommands(dispatcher);
        BigSlayerDrops.registerCommands(dispatcher);
        SlayerCocoon.registerCommands(dispatcher);
        SlayerSounds.registerCommands(dispatcher);
        dispatcher.register(LiteralArgumentBuilder.<FabricClientCommandSource>literal("slayertimes")
            .executes(c -> status())
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("status").executes(c -> status()))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("list").executes(c -> listTimes(""))
                .then(RequiredArgumentBuilder.<FabricClientCommandSource, String>argument("filter", StringArgumentType.word())
                    .executes(c -> listTimes(StringArgumentType.getString(c, "filter")))))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("clear")
                .then(RequiredArgumentBuilder.<FabricClientCommandSource, String>argument("filter", StringArgumentType.word())
                    .executes(c -> clearTimes(StringArgumentType.getString(c, "filter")))))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("decimals")
                .then(RequiredArgumentBuilder.<FabricClientCommandSource, Integer>argument("value", IntegerArgumentType.integer(0, 3))
                    .executes(c -> decimals(IntegerArgumentType.getInteger(c, "value")))))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("history")
                .then(RequiredArgumentBuilder.<FabricClientCommandSource, Integer>argument("runs", IntegerArgumentType.integer(1, 100))
                    .executes(c -> historyLimit(IntegerArgumentType.getInteger(c, "runs")))))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("range")
                .then(RequiredArgumentBuilder.<FabricClientCommandSource, Double>argument("blocks", DoubleArgumentType.doubleArg(8, 256))
                    .executes(c -> range(DoubleArgumentType.getDouble(c, "blocks")))))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("style")
                .then(RequiredArgumentBuilder.<FabricClientCommandSource, String>argument("line", StringArgumentType.word())
                    .then(RequiredArgumentBuilder.<FabricClientCommandSource, String>argument("template", StringArgumentType.greedyString())
                        .executes(c -> style(StringArgumentType.getString(c, "line"), StringArgumentType.getString(c, "template")))))));
    }

    private void chat(String raw) {
        if (!enabled()) return;
        String stripped = ChatFormatting.stripFormatting(raw);
        String text = stripped == null ? raw.trim() : stripped.trim();
        if (text.equals("SLAYER QUEST STARTED!")) {
            questStartNanos = System.nanoTime();
            questStartServerTick = SlayerState.serverTicks();
        } else if (text.equals("SLAYER QUEST FAILED!") || text.equals("Your Slayer Quest has been cancelled!")) {
            questStartNanos = 0;
            questStartServerTick = 0;
            activeBoss = null;
        } else if (text.equals("SLAYER QUEST COMPLETE!") || text.equals("NICE! SLAYER BOSS SLAIN!")) {
            questStartNanos = 0;
            questStartServerTick = 0;
        }
    }

    private void spawned(SlayerState.Boss boss) {
        if (!enabled()) return;
        SlayerHighlights.onBossSpawn(boss);
        if (!ownedBy(boss.owner())) return;
        activeBoss = boss;
        boolean tarantulaFive = boss.type() == SlayerState.Type.TARA && boss.tier() == 5;
        if (tarantulaFive && boss.variant().equals("Conjoined Brood")) {
            tarantulaFivePhaseOne = false;
            return;
        }
        if (tarantulaFive) tarantulaFivePhaseOne = true;
        if (!cfg.slayerSpawnTimeChat || questStartNanos <= 0) return;
        double wall = Math.max(0, (System.nanoTime() - questStartNanos) / 1_000_000_000.0);
        double ticks = Math.max(0, SlayerState.serverTicks() - questStartServerTick) / 20.0;
        local(apply(cfg.slayerSpawnTimeMessage, boss, attached(boss), wall, ticks, "", ""));
    }

    private void died(SlayerState.Boss boss, double seconds) {
        if (!enabled() || !ownedBy(boss.owner())) return;
        double wall = Math.max(0, seconds);
        double ticks = Math.max(0, SlayerState.serverTicks() - boss.spawnedAtServerTick()) / 20.0;
        String key = key(boss);
        Double old = cfg.slayerKillPbs.get(key);
        String delta;
        if (old == null) delta = "[New PB]";
        else delta = wall < old ? "[-" + time(old - wall) + "]" : "[+" + time(wall - old) + "]";
        if (cfg.slayerSaveKillPbs && (old == null || wall < old)) cfg.slayerKillPbs.put(key, wall);
        if (cfg.slayerSaveKillHistory) {
            List<Double> history = cfg.slayerKillHistory.computeIfAbsent(key, ignored -> new ArrayList<>());
            history.add(wall);
            while (history.size() > Math.clamp(cfg.slayerKillHistoryLimit, 1, 100)) history.remove(0);
        }
        ConstellationClient.saveConfig();
        String phase = phase(boss);
        if (cfg.slayerKillTimeChat)
            local(apply(cfg.slayerKillTimeMessage, boss, attached(boss), wall, ticks, delta, phase));
        if (cfg.slayerInfoOverlay && cfg.slayerInfoShowKillTime) {
            long now = Minecraft.getInstance().level == null ? 0 : Minecraft.getInstance().level.getGameTime();
            DEATH_LABELS.add(new DeathLabel(boss.entity().position().add(0, boss.entity().getBbHeight() + 0.6, 0),
                style("Slayer" + phase + " " + time(wall)), now + 100));
        }
        if (activeBoss != null && activeBoss.entity().getId() == boss.entity().getId()) activeBoss = null;
        ATTACHED_CACHE.remove(boss.entity().getId());
    }

    public static List<String> hudLines() {
        PerseusSlayers self = INSTANCE;
        if (self == null || !self.enabled() || !self.cfg.slayerDisplay || activeBoss == null || !activeBoss.entity().isAlive()) return List.of();
        Attached data = self.attached(activeBoss);
        List<String> lines = new ArrayList<>();
        if (self.cfg.slayerDisplayTimer) lines.add(self.apply(self.cfg.slayerDisplayTimerStyle, activeBoss, data, 0, 0, "", ""));
        if (self.cfg.slayerDisplayName) lines.add(self.apply(self.cfg.slayerDisplayNameStyle, activeBoss, data, 0, 0, "", ""));
        if (self.cfg.slayerDisplayHealth) lines.add(self.apply(self.cfg.slayerDisplayHealthStyle, activeBoss, data, 0, 0, "", ""));
        if (self.cfg.slayerDisplayHits && !data.hits().isBlank()) lines.add(self.apply(self.cfg.slayerDisplayHitsStyle, activeBoss, data, 0, 0, "", ""));
        if (self.cfg.slayerDisplayOwner) lines.add(activeBoss.owner());
        lines.removeIf(String::isBlank);
        return List.copyOf(lines);
    }

    private void drawInfo(WorldRenderer.Ctx ctx) {
        if (!enabled() || !cfg.slayerInfoOverlay) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;
        double range2 = Math.clamp(cfg.slayerInfoRange, 8, 256); range2 *= range2;
        for (SlayerState.Boss boss : SlayerState.bosses()) {
            if (!boss.entity().isAlive() || boss.entity().distanceToSqr(mc.player) > range2) continue;
            Attached data = attached(boss);
            List<String> lines = new ArrayList<>();
            if (cfg.slayerInfoTimer) lines.add(apply(cfg.slayerDisplayTimerStyle, boss, data, 0, 0, "", ""));
            if (cfg.slayerInfoName) lines.add(apply(cfg.slayerDisplayNameStyle, boss, data, 0, 0, "", ""));
            if (cfg.slayerInfoHealth) lines.add(apply(cfg.slayerDisplayHealthStyle, boss, data, 0, 0, "", ""));
            if (cfg.slayerInfoHits && !data.hits().isBlank()) lines.add(apply(cfg.slayerDisplayHitsStyle, boss, data, 0, 0, "", ""));
            if (cfg.slayerInfoShowOwner) lines.add(boss.owner());
            Vec3 base = boss.entity().position().add(0, boss.entity().getBbHeight() + 0.6 + Math.max(0, lines.size() - 1) * 0.25, 0);
            for (int i = 0; i < lines.size(); i++) if (!lines.get(i).isBlank())
                ctx.label(base.add(0, -i * 0.25, 0), lines.get(i), 0xFFFFFFFF, cfg.slayerInfoThroughWalls);
        }
        long now = mc.level.getGameTime();
        for (DeathLabel label : DEATH_LABELS) if (label.expiresAtTick() >= now)
            ctx.label(label.position(), label.text(), 0xFFFF5555, cfg.slayerInfoThroughWalls);
    }

    public static boolean shouldHideSlayerStand(Entity entity) {
        PerseusSlayers self = INSTANCE;
        if (self == null || !self.enabled() || !self.cfg.slayerInfoOverlay || !self.cfg.slayerInfoHideOriginal
            || !(entity instanceof ArmorStand stand)) return false;
        for (SlayerState.Boss boss : SlayerState.bosses()) {
            if (stand.getId() != boss.nameStandId() && stand.getId() != boss.ownerStandId()
                && stand.getId() != boss.ownerStandId() - 1) continue;
            if (!stand.hasCustomName()) continue;
            String name = plain(stand);
            if (stand.getId() == boss.nameStandId() && HEALTH.matcher(name).find()) return true;
            if (stand.getId() == boss.ownerStandId() && name.contains("Spawned by:")) return true;
            if (stand.getId() == boss.ownerStandId() - 1 && (TIMER.matcher(name).find() || HITS.matcher(name).find())) return true;
        }
        return false;
    }

    private Attached attached(SlayerState.Boss boss) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return new Attached("", "?", "");
        long now = mc.level.getGameTime();
        String timer = "", health = "", hits = "";
        int[] ids = {boss.nameStandId(), boss.ownerStandId() - 1};
        for (int id : ids) {
            Entity entity = mc.level.getEntity(id);
            if (!(entity instanceof ArmorStand stand) || !stand.hasCustomName()) continue;
            String line = plain(stand);
            Matcher tm = TIMER.matcher(line); if (timer.isBlank() && !line.contains("Spawned by:") && tm.find()) timer = tm.group(1);
            Matcher hp = HEALTH.matcher(line); if (health.isBlank() && hp.find()) health = hp.group(1);
            Matcher hit = HITS.matcher(line); if (hits.isBlank() && hit.find()) hits = hit.group(1);
        }
        boolean sawTimer = !timer.isBlank();
        boolean sawHits = !hits.isBlank();
        CachedAttached cached = ATTACHED_CACHE.get(boss.entity().getId());
        if (timer.isBlank() && cached != null && now - cached.timerSeenTick() <= 10) timer = cached.timer();
        if (hits.isBlank() && cached != null && now - cached.hitsSeenTick() <= 10) hits = cached.hits();
        if (health.isBlank() && boss.entity() instanceof net.minecraft.world.entity.LivingEntity living) health = compact(Math.max(0, living.getHealth()));
        String renderedTimer = SlayerSpecialInfo.timerLine(boss, timer);
        Attached result = new Attached(renderedTimer, health.isBlank() ? "?" : health, hits);
        ATTACHED_CACHE.put(boss.entity().getId(), new CachedAttached(timer, hits,
            sawTimer ? now : cached == null ? Long.MIN_VALUE : cached.timerSeenTick(),
            sawHits ? now : cached == null ? Long.MIN_VALUE : cached.hitsSeenTick()));
        return result;
    }

    private String apply(String template, SlayerState.Boss boss, Attached data, double wall, double ticks, String delta, String phase) {
        String value = template == null ? "" : template.replace('\n', ' ').replace('\r', ' ').trim();
        String wallValue = cfg.slayerTimerShowWall ? time(wall) : "{disabled-time}";
        String tickValue = cfg.slayerTimerShowTicks ? time(ticks) : "{disabled-time}";
        String rendered = style(value.replace("#time", data.timer()).replace("#name_short", title(boss.type().shortName()))
            .replace("#name_long", boss.variant()).replace("#tier", roman(boss.tier())).replace("#health", data.health())
            .replace("#hits", data.hits()).replace("{timer}", data.timer()).replace("{type}", title(boss.type().shortName()))
            .replace("{name}", boss.variant()).replace("{tier}", roman(boss.tier())).replace("{health}", data.health())
            .replace("{hits}", data.hits()).replace("{owner}", boss.owner())
            .replace("{wall}", wallValue).replace("{ticks}", tickValue)
            .replace("{delta}", delta).replace("{phase}", phase));
        return rendered.replaceAll("\\{disabled-time}\\s*\\|\\s*", "")
            .replaceAll("\\s*\\|\\s*\\{disabled-time}", "")
            .replace("{disabled-time}", "").replaceAll(" {2,}", " ").trim();
    }

    private void normalize() {
        Map<String, Double> pbs = new LinkedHashMap<>();
        if (cfg.slayerKillPbs != null) for (Map.Entry<String, Double> entry : cfg.slayerKillPbs.entrySet()) {
            if (entry.getKey() != null && !entry.getKey().isBlank() && entry.getValue() != null
                && Double.isFinite(entry.getValue()) && entry.getValue() > 0) pbs.put(entry.getKey(), entry.getValue());
        }
        Map<String, List<Double>> histories = new LinkedHashMap<>();
        if (cfg.slayerKillHistory != null) for (Map.Entry<String, List<Double>> entry : cfg.slayerKillHistory.entrySet()) {
            if (entry.getKey() == null || entry.getKey().isBlank() || entry.getValue() == null) continue;
            List<Double> values = new ArrayList<>();
            for (Double value : entry.getValue()) if (value != null && Double.isFinite(value) && value > 0) values.add(value);
            int limit = Math.clamp(cfg.slayerKillHistoryLimit, 1, 100);
            if (values.size() > limit) values = new ArrayList<>(values.subList(values.size() - limit, values.size()));
            histories.put(entry.getKey(), values);
        }
        cfg.slayerKillPbs = pbs;
        cfg.slayerKillHistory = histories;
    }

    private int status() {
        normalize();
        local("Display " + on(cfg.slayerDisplay) + ", world info " + on(cfg.slayerInfoOverlay) + ", hide originals " + on(cfg.slayerInfoHideOriginal)
            + ", spawn/kill chat " + on(cfg.slayerSpawnTimeChat) + "/" + on(cfg.slayerKillTimeChat)
            + ", PBs " + cfg.slayerKillPbs.size() + ", history keys " + cfg.slayerKillHistory.size() + '.');
        return 1;
    }

    private int listTimes(String filter) {
        normalize();
        String needle = filter.toUpperCase(Locale.ROOT);
        List<String> keys = cfg.slayerKillPbs.keySet().stream().filter(key -> key.toUpperCase(Locale.ROOT).contains(needle)).sorted().toList();
        if (keys.isEmpty()) { local("No matching Slayer PBs."); return 1; }
        for (String key : keys) {
            List<Double> history = cfg.slayerKillHistory.getOrDefault(key, List.of());
            double average = history.stream().mapToDouble(Double::doubleValue).average().orElse(0);
            local(key + ": PB " + time(cfg.slayerKillPbs.get(key)) + (average > 0 ? ", avg " + time(average) + " over " + history.size() : ""));
        }
        return 1;
    }

    private int clearTimes(String filter) {
        String needle = filter.toUpperCase(Locale.ROOT);
        if (needle.equals("ALL")) { cfg.slayerKillPbs.clear(); cfg.slayerKillHistory.clear(); }
        else { cfg.slayerKillPbs.keySet().removeIf(key -> key.toUpperCase(Locale.ROOT).contains(needle)); cfg.slayerKillHistory.keySet().removeIf(key -> key.toUpperCase(Locale.ROOT).contains(needle)); }
        ConstellationClient.saveConfig(); local("Cleared matching Slayer times."); return 1;
    }

    private int decimals(int value) { cfg.slayerTimerDecimals = Math.clamp(value, 0, 3); ConstellationClient.saveConfig(); local("Slayer timer decimals updated."); return 1; }
    private int historyLimit(int value) { cfg.slayerKillHistoryLimit = Math.clamp(value, 1, 100); normalize(); ConstellationClient.saveConfig(); local("Slayer history limit updated."); return 1; }
    private int range(double value) { cfg.slayerInfoRange = Math.clamp(value, 8, 256); ConstellationClient.saveConfig(); local("Slayer info range updated."); return 1; }
    private int style(String line, String template) {
        String clean = template.replace('\n', ' ').replace('\r', ' ').trim();
        if (clean.isEmpty() || clean.length() > 200) { local("Style must be 1-200 characters."); return 0; }
        switch (line.toLowerCase(Locale.ROOT)) {
            case "timer" -> cfg.slayerDisplayTimerStyle = clean;
            case "name" -> cfg.slayerDisplayNameStyle = clean;
            case "health" -> cfg.slayerDisplayHealthStyle = clean;
            case "hits" -> cfg.slayerDisplayHitsStyle = clean;
            case "spawn" -> cfg.slayerSpawnTimeMessage = clean;
            case "kill" -> cfg.slayerKillTimeMessage = clean;
            default -> { local("Style must be timer, name, health, hits, spawn, or kill."); return 0; }
        }
        ConstellationClient.saveConfig(); local("Slayer " + line + " style updated."); return 1;
    }

    private void cleanup() {
        if (Minecraft.getInstance().level == null) { DEATH_LABELS.clear(); return; }
        long now = Minecraft.getInstance().level.getGameTime();
        DEATH_LABELS.removeIf(label -> label.expiresAtTick() < now);
        ATTACHED_CACHE.keySet().removeIf(id -> SlayerState.bosses().stream().noneMatch(boss -> boss.entity().getId() == id));
        if (activeBoss != null && (!activeBoss.entity().isAlive() || activeBoss.entity().isRemoved())) activeBoss = null;
    }

    private static void resetTransient() { activeBoss = null; questStartNanos = 0; questStartServerTick = 0; tarantulaFivePhaseOne = false; ATTACHED_CACHE.clear(); DEATH_LABELS.clear(); }
    private boolean enabled() { return cfg != null && cfg.enabled && ConstellationClient.loc().onHypixel(); }
    private static boolean ownedBy(String owner) { Minecraft mc = Minecraft.getInstance(); return mc.player != null && owner.equalsIgnoreCase(mc.player.getName().getString()); }
    private String key(SlayerState.Boss boss) { return boss.type().shortName() + "_T" + boss.tier() + (boss.variant().equals("Conjoined Brood") ? "_P2" : ""); }
    private String phase(SlayerState.Boss boss) { return boss.type() == SlayerState.Type.TARA && boss.tier() == 5 ? boss.variant().equals("Conjoined Brood") || !tarantulaFivePhaseOne ? " [P2]" : " [P1]" : ""; }
    private String time(double seconds) { return String.format(Locale.ROOT, "%." + Math.clamp(cfg.slayerTimerDecimals, 0, 3) + "fs", Math.max(0, seconds)); }
    private static String compact(double value) { if (value >= 1e9) return String.format(Locale.ROOT, "%.2fb", value / 1e9); if (value >= 1e6) return String.format(Locale.ROOT, "%.2fm", value / 1e6); if (value >= 1e3) return String.format(Locale.ROOT, "%.1fk", value / 1e3); return String.format(Locale.ROOT, "%.0f", value); }
    private static String title(String value) { String lower = value.toLowerCase(Locale.ROOT); return Character.toUpperCase(lower.charAt(0)) + lower.substring(1); }
    private static String roman(int value) { return switch (value) { case 1 -> "I"; case 2 -> "II"; case 3 -> "III"; case 4 -> "IV"; case 5 -> "V"; default -> "?"; }; }
    private static String plain(Entity entity) { String value = ChatFormatting.stripFormatting(entity.getName().getString()); return value == null ? entity.getName().getString() : value; }
    private static String on(boolean value) { return value ? "on" : "off"; }
    private static void local(String text) { Minecraft mc = Minecraft.getInstance(); if (mc.player != null) mc.player.sendSystemMessage(Component.literal("§5Slayer §8> §f" + text)); }
    private static String style(String text) { return text.replace("<dark_red>", "§4").replace("<dark_gray>", "§8").replace("<gray>", "§7").replace("<red>", "§c").replace("<green>", "§a").replace("<yellow>", "§e").replace("<aqua>", "§b").replace("<blue>", "§9").replace("<gold>", "§6").replace("<white>", "§f").replace("<r>", "§r").replace('&', '§'); }
}
