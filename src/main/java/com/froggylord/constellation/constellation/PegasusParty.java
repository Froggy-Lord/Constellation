package com.froggylord.constellation.constellation;

import com.froggylord.constellation.ConstellationClient;
import com.froggylord.constellation.config.PegasusConfig;
import com.froggylord.constellation.core.BaseConstellation;
import com.froggylord.constellation.core.InitContext;
import com.froggylord.constellation.hud.HudManager;
import com.froggylord.constellation.hud.HudPosition;
import com.froggylord.constellation.hud.CarryHudWidget;
import com.froggylord.constellation.render.WorldRenderer;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import com.froggylord.constellation.ui.CarryTrackerScreen;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// ported from Athen (BSD-3-Clause): modules/common/carry/ICarryStateTracker.kt
// ported from Athen (BSD-3-Clause): modules/impl/dungeon/carry/DungeonCarryTracker.kt
// ported from Athen (BSD-3-Clause): modules/impl/kuudra/carry/KuudraCarryTracker.kt
// payment matching ported from Athen (BSD-3-Clause): modules/impl/slayer/carry/SlayerCarryTracker.kt
public class PegasusParty extends BaseConstellation {
    private static PegasusParty INSTANCE;
    private static final String KUUDRA_START = "[NPC] Elle: Okay adventurers, I will go and fish up Kuudra!";
    private static final Pattern TRADE = Pattern.compile("^Trade completed with (?:\\[.*?] )?(\\w{1,16})!$");
    private static final Pattern COINS = Pattern.compile("^\\s*\\+\\s*([\\d,]+(?:\\.\\d+)?)([kKmMbB]?) coins$");
    private static final Pattern KUUDRA_TIER = Pattern.compile("Kuudra's Hollow \\(T([1-5])\\)", Pattern.CASE_INSENSITIVE);
    private static final HttpClient WEBHOOK_HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(6)).build();
    private static long lastCompletion;
    private static String recentTrader;
    private static long recentTradeAt;
    private static Long recentCoins;
    private static long recentCoinsAt;
    private static boolean kuudraRun;
    private static boolean pendingKuudraStart;
    private static long kuudraStartAt;
    private static final Map<Integer, String> SLAYER_BOSS_CARRIES = new java.util.HashMap<>();
    private PegasusConfig cfg;

    @Override public String id() { return "pegasus"; }
    @Override public String displayName() { return "Pegasus"; }
    @Override public String description() { return "party and carry tools"; }

    @Override
    public void init(InitContext ctx) {
        INSTANCE = this;
        cfg = (PegasusConfig) config;
        ensure();
        ClientReceiveMessageEvents.ALLOW_GAME.register((component, overlay) -> {
            if (!overlay) onChat(component.getString());
            return true;
        });
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> clearTransient());
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> clearTransient());
        SlayerState.init();
        SlayerState.listen(new SlayerState.Listener() {
            @Override public void onSpawn(SlayerState.Boss boss) { slayerSpawn(boss); }
            @Override public void onDeath(SlayerState.Boss boss, double seconds, int clientTicks) { slayerDeath(boss, seconds, clientTicks); }
            @Override public void onOwnerReset(String owner) {
                SLAYER_BOSS_CARRIES.entrySet().removeIf(entry -> entry.getValue().equalsIgnoreCase(owner));
            }
            @Override public void onReset() { SLAYER_BOSS_CARRIES.clear(); }
        });
        registerRenderer(this::drawCarryPlayers);
        every(2, "pegasus-carry-maintenance", this::maintenance);
        every(1, "pegasus-slayer-state", SlayerState::tick);
    }

    @Override
    public void registerHud(HudManager hud) {
        hud.register(new CarryHudWidget(HudPosition.of(82, 38), () -> enabled() && cfg.carryHud));
    }

    @Override protected void onEnable() { for (SlayerState.Boss boss : SlayerState.bosses()) slayerSpawn(boss); }
    @Override protected void onDisable() { SLAYER_BOSS_CARRIES.clear(); }

    private void onChat(String raw) {
        if (!enabled()) return;
        String text = ChatFormatting.stripFormatting(raw).trim();
        expirePayment();
        if (text.equals(KUUDRA_START)) {
            kuudraRun = true;
            pendingKuudraStart = true;
            kuudraStartAt = System.currentTimeMillis();
            announceKuudraStart();
        } else if (text.startsWith("DEFEAT")) {
            kuudraRun = false;
            pendingKuudraStart = false;
            kuudraStartAt = 0;
        }
        Matcher trade = TRADE.matcher(text);
        if (trade.matches()) {
            recentTrader = trade.group(1);
            recentTradeAt = System.currentTimeMillis();
            matchPayment();
        }
        Matcher coins = COINS.matcher(text);
        if (coins.matches()) {
            Long amount = coins(coins.group(1), coins.group(2));
            if (amount != null) {
                recentCoins = amount;
                recentCoinsAt = System.currentTimeMillis();
                matchPayment();
            }
        }
        long now = System.currentTimeMillis();
        if (now - lastCompletion < Math.clamp(cfg.carryPartyProgressCooldownMs, 250, 10_000)) return;
        if (text.equals("> EXTRA STATS <") && cfg.carryAutoDungeon) {
            String floor = ConstellationClient.dungeon().floor();
            if (floor == null || floor.isBlank()) floor = com.froggylord.constellation.data.DungeonScore.lastFloor();
            completePresent("DUNGEON", floor == null ? "" : floor, true);
            lastCompletion = now;
        } else if (text.startsWith("KUUDRA DOWN!") && kuudraRun && inKuudra() && cfg.carryAutoKuudra) {
            String tier = kuudraTier();
            if (!tier.isBlank()) completePresent("KUUDRA", tier, false);
            kuudraRun = false;
            pendingKuudraStart = false;
            kuudraStartAt = 0;
            lastCompletion = now;
        }
    }

    private void completePresent(String type, String target, boolean dungeon) {
        if (target.isBlank()) return;
        List<String> present = dungeon
            ? ConstellationClient.dungeon().teammates().stream().map(t -> t.name().toLowerCase(Locale.ROOT)).toList()
            : worldPlayers();
        for (PegasusConfig.CarryData carry : new ArrayList<>(cfg.carries.values())) {
            if (!carry.type.equals(type) || !carry.target.equalsIgnoreCase(target)) continue;
            if (!present.contains(carry.player.toLowerCase(Locale.ROOT))) continue;
            increment(carry, 1, true);
        }
    }

    // boss ownership and completion ported from Athen (BSD-3-Clause): modules/impl/slayer/carry/SlayerCarryTracker.kt
    private void slayerSpawn(SlayerState.Boss boss) {
        if (!enabled() || !cfg.carryAutoSlayer) return;
        PegasusConfig.CarryData carry = find(boss.owner());
        if (carry == null || !matchesSlayer(carry, boss)) return;
        SLAYER_BOSS_CARRIES.put(boss.entity().getId(), carry.player.toLowerCase(Locale.ROOT));
        if (cfg.carrySlayerSpawnMessage)
            local(template(cfg.carrySlayerSpawnTemplate, carry).replace("{tier}", Integer.toString(boss.tier()))
                .replace("{type}", boss.type().shortName()));
    }

    private void slayerDeath(SlayerState.Boss boss, double seconds, int clientTicks) {
        String player = SLAYER_BOSS_CARRIES.remove(boss.entity().getId());
        if (!enabled() || !cfg.carryAutoSlayer || player == null) return;
        PegasusConfig.CarryData carry = find(player);
        if (carry == null || !matchesSlayer(carry, boss)) return;
        local("Killed boss for " + carry.player + " in " + String.format(Locale.ROOT, "%.1fs", seconds)
            + " | " + String.format(Locale.ROOT, "%.1fs", clientTicks / 20.0) + " (" + clientTicks + " ticks).");
        increment(carry, 1, true);
    }

    private static List<String> worldPlayers() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return List.of();
        return mc.level.players().stream().map(p -> p.getGameProfile().name().toLowerCase(Locale.ROOT)).toList();
    }

    private void matchPayment() {
        if (!cfg.carryPaymentDetection || recentTrader == null || recentCoins == null) return;
        long now = System.currentTimeMillis();
        if (Math.abs(recentTradeAt - recentCoinsAt) > 4000 || now - Math.max(recentTradeAt, recentCoinsAt) > 4000) return;
        PegasusConfig.CarryData carry = find(recentTrader);
        long amount = recentCoins;
        recentTrader = null;
        recentCoins = null;
        if (carry == null) {
            local("Received " + money(amount) + " from an untracked player; no carry changed.");
            return;
        }
        carry.paid = safeAdd(carry.paid, amount);
        long runs = carry.pricePerRun <= 0 ? 0 : amount / carry.pricePerRun;
        boolean exact = carry.pricePerRun > 0 && amount % carry.pricePerRun == 0;
        if (exact) carry.paidRuns = (int) Math.clamp((long) carry.paidRuns + runs, 0, Integer.MAX_VALUE);
        carry.lastPaymentAt = now;
        ConstellationClient.saveConfig();
        if (cfg.carryPaymentChat) local("Payment from " + carry.player + ": " + money(amount) + " (total " + money(carry.paid) + ")"
            + (exact ? ", exactly " + runs + " run" + (runs == 1 ? "" : "s")
                : carry.pricePerRun > 0 ? ", not an exact run-price multiple" : ""));
    }

    private void increment(PegasusConfig.CarryData carry, int amount, boolean automatic) {
        int before = carry.completed;
        carry.completed = (int) Math.clamp((long) carry.completed + amount, 0, carry.total);
        if (carry.completed == before) return;
        long now = System.currentTimeMillis();
        if (carry.firstCompletion == 0) carry.firstCompletion = now;
        carry.lastCompletion = now;
        ConstellationClient.saveConfig();
        String progress = carry.player + " " + carry.target + ": " + carry.completed + "/" + carry.total;
        local((automatic ? "Completed " : "Updated ") + progress);
        if (automatic && cfg.carryPartyProgress)
            PartyMessages.sendAnywhere("carry-progress", variables(carry), carry.player);
        if (automatic && cfg.carryWebhook && cfg.carryWebhookEach)
            webhook("Completed " + carry.completed + "/" + carry.total + " " + carry.target + " carries for " + carry.player + ".");
        if (carry.completed >= carry.total) finish(carry);
    }

    private void finish(PegasusConfig.CarryData carry) {
        PegasusConfig.CarryHistory history = new PegasusConfig.CarryHistory();
        history.player = carry.player;
        history.type = carry.type;
        history.target = carry.target;
        history.completed = carry.completed;
        history.total = carry.total;
        history.pricePerRun = carry.pricePerRun;
        history.paid = carry.paid;
        history.durationMs = carry.firstCompletion == 0 || carry.lastCompletion < carry.firstCompletion
            ? 0 : carry.lastCompletion - carry.firstCompletion;
        history.timestamp = System.currentTimeMillis();
        cfg.carryHistory.add(history);
        while (cfg.carryHistory.size() > Math.clamp(cfg.carryHistoryLimit, 10, 5000)) cfg.carryHistory.remove(0);
        cfg.carries.remove(carry.player.toLowerCase(Locale.ROOT));
        ConstellationClient.saveConfig();
        local("Finished " + carry.completed + "x " + carry.target + " for " + carry.player + ". Paid " + money(carry.paid) + '.');
        if (cfg.carryWebhook && cfg.carryWebhookCompletion)
            webhook("Finished " + carry.completed + "x " + carry.target + " carries for " + carry.player
                + " in " + duration(history.durationMs) + ". Paid " + money(carry.paid) + ".");
    }

    @Override
    public void registerCommands(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        var root = LiteralArgumentBuilder.<FabricClientCommandSource>literal("carry")
            .executes(c -> open())
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("gui").executes(c -> open()))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("list").executes(c -> list()))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("status").executes(c -> status()))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("history").executes(c -> history(1))
                .then(RequiredArgumentBuilder.<FabricClientCommandSource, Integer>argument("page", IntegerArgumentType.integer(1))
                    .executes(c -> history(IntegerArgumentType.getInteger(c, "page")))))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("clear").executes(c -> clear()))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("remove")
                .then(word("player").executes(c -> remove(StringArgumentType.getString(c, "player")))))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("plus")
                .then(word("player").executes(c -> adjust(StringArgumentType.getString(c, "player"), 1))))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("minus")
                .then(word("player").executes(c -> adjust(StringArgumentType.getString(c, "player"), -1))))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("paid")
                .then(word("player").then(RequiredArgumentBuilder.<FabricClientCommandSource, Long>argument("coins", LongArgumentType.longArg(0))
                    .executes(c -> paid(StringArgumentType.getString(c, "player"), LongArgumentType.getLong(c, "coins"))))))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("price")
                .then(word("player").then(RequiredArgumentBuilder.<FabricClientCommandSource, Long>argument("coins", LongArgumentType.longArg(0))
                    .executes(c -> price(StringArgumentType.getString(c, "player"), LongArgumentType.getLong(c, "coins"))))))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("total")
                .then(word("player").then(RequiredArgumentBuilder.<FabricClientCommandSource, Integer>argument("runs", IntegerArgumentType.integer(1, 10000))
                    .executes(c -> total(StringArgumentType.getString(c, "player"), IntegerArgumentType.getInteger(c, "runs"))))))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("option")
                .then(word("name").then(RequiredArgumentBuilder.<FabricClientCommandSource, Boolean>argument("enabled", BoolArgumentType.bool())
                    .executes(c -> option(StringArgumentType.getString(c, "name"), BoolArgumentType.getBool(c, "enabled"))))))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("highlightwidth")
                .then(RequiredArgumentBuilder.<FabricClientCommandSource, Float>argument("width", FloatArgumentType.floatArg(0.1f, 10f))
                    .executes(c -> highlightWidth(FloatArgumentType.getFloat(c, "width")))))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("highlightcolor")
                .then(word("argb").executes(c -> highlightColor(StringArgumentType.getString(c, "argb")))))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("slayerbosswidth")
                .then(RequiredArgumentBuilder.<FabricClientCommandSource, Float>argument("width", FloatArgumentType.floatArg(0.1f, 10f))
                    .executes(c -> slayerBossWidth(FloatArgumentType.getFloat(c, "width")))))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("slayerbosscolor")
                .then(word("argb").executes(c -> slayerBossColor(StringArgumentType.getString(c, "argb")))))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("slayerplayerwidth")
                .then(RequiredArgumentBuilder.<FabricClientCommandSource, Float>argument("width", FloatArgumentType.floatArg(0.1f, 10f))
                    .executes(c -> slayerPlayerWidth(FloatArgumentType.getFloat(c, "width")))))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("slayerplayercolor")
                .then(word("argb").executes(c -> slayerPlayerColor(StringArgumentType.getString(c, "argb")))))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("message")
                .then(word("type").then(RequiredArgumentBuilder.<FabricClientCommandSource, String>argument("template", StringArgumentType.greedyString())
                    .executes(c -> message(StringArgumentType.getString(c, "type"), StringArgumentType.getString(c, "template"))))))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("webhook")
                .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("test").executes(c -> webhookTest()))
                .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("clear").executes(c -> webhookUrl("")))
                .then(RequiredArgumentBuilder.<FabricClientCommandSource, String>argument("url", StringArgumentType.greedyString())
                    .executes(c -> webhookUrl(StringArgumentType.getString(c, "url")))))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("add")
                .then(word("type").then(word("player").then(RequiredArgumentBuilder.<FabricClientCommandSource, Integer>argument("runs", IntegerArgumentType.integer(1, 10000))
                    .then(word("target").then(RequiredArgumentBuilder.<FabricClientCommandSource, Long>argument("price", LongArgumentType.longArg(0))
                        .executes(c -> add(StringArgumentType.getString(c, "type"), StringArgumentType.getString(c, "player"),
                            IntegerArgumentType.getInteger(c, "runs"), StringArgumentType.getString(c, "target"), LongArgumentType.getLong(c, "price")))))))));
        dispatcher.register(root);
    }

    private static RequiredArgumentBuilder<FabricClientCommandSource, String> word(String name) {
        return RequiredArgumentBuilder.argument(name, StringArgumentType.word());
    }

    private int add(String type, String player, int runs, String target, long price) {
        ensure();
        String kind = type.toUpperCase(Locale.ROOT);
        String goal = target.toUpperCase(Locale.ROOT);
        if (!player.matches("\\w{1,16}")) { local("Player must be a valid 1-16 character Minecraft name."); return 0; }
        if (!kind.matches("DUNGEON|KUUDRA|SLAYER")) { local("Type must be dungeon, kuudra, or slayer."); return 0; }
        goal = kind.equals("SLAYER") ? normalizeSlayerTarget(goal) : normalizeTarget(goal);
        if (goal == null) { local("Slayer target must be type_tier, such as void_t4, rev_any, or vamp_t5."); return 0; }
        if (kind.equals("DUNGEON") && !goal.matches("E|[FM][1-7]")) { local("Dungeon target must be E, F1-F7, or M1-M7."); return 0; }
        if (kind.equals("KUUDRA") && !goal.matches("T[1-5]")) { local("Kuudra target must be T1-T5 or a tier name."); return 0; }
        String key = player.toLowerCase(Locale.ROOT);
        PegasusConfig.CarryData old = cfg.carries.get(key);
        if (old != null && (!old.type.equals(kind) || !old.target.equals(goal) || old.pricePerRun != price)) {
            local(player + " already has a different carry. Remove it first."); return 0;
        }
        PegasusConfig.CarryData carry = old == null ? new PegasusConfig.CarryData() : old;
        carry.player = player; carry.type = kind; carry.target = goal;
        carry.total = (int) Math.clamp((long) carry.total + runs, 1, 10_000);
        carry.pricePerRun = price; if (carry.createdAt == 0) carry.createdAt = System.currentTimeMillis();
        cfg.carries.put(key, carry); ConstellationClient.saveConfig();
        local("Tracking " + player + " for " + carry.total + "x " + carry.target + " at " + money(price) + " per run.");
        return 1;
    }

    private int open() { Minecraft.getInstance().execute(() -> Minecraft.getInstance().setScreenAndShow(new CarryTrackerScreen(null, this))); return 1; }

    public List<PegasusConfig.CarryData> activeCarries() { ensure(); return new ArrayList<>(cfg.carries.values()); }
    public void screenAdjust(String player, int amount) { adjust(player, amount); }
    public void screenAdjustTotal(String player, int amount) { adjustTotal(player, amount); }
    public void screenRemove(String player) { remove(player); }

    private int adjust(String player, int amount) { PegasusConfig.CarryData c = find(player); if (c == null) return missing(player); increment(c, amount, false); return 1; }
    private int adjustTotal(String player, int amount) { PegasusConfig.CarryData c = find(player); if (c == null) return missing(player); c.total = (int) Math.clamp(Math.max((long) c.completed + 1, (long) c.total + amount), 1, 10_000); ConstellationClient.saveConfig(); return 1; }
    private int paid(String player, long coins) { PegasusConfig.CarryData c = find(player); if (c == null) return missing(player); c.paid = safeAdd(c.paid, coins); ConstellationClient.saveConfig(); local("Recorded " + money(coins) + " from " + c.player + '.'); return 1; }
    private int price(String player, long coins) { PegasusConfig.CarryData c = find(player); if (c == null) return missing(player); c.pricePerRun = coins; ConstellationClient.saveConfig(); local("Price for " + c.player + " set to " + money(coins) + " per run."); return 1; }
    private int total(String player, int runs) { PegasusConfig.CarryData c = find(player); if (c == null) return missing(player); c.total = Math.max(c.completed + 1, runs); ConstellationClient.saveConfig(); local("Total for " + c.player + " set to " + c.total + "."); return 1; }
    private int remove(String player) { ensure(); PegasusConfig.CarryData c = cfg.carries.remove(player.toLowerCase(Locale.ROOT)); if (c == null) return missing(player); ConstellationClient.saveConfig(); local("Removed " + c.player + " from carry tracking."); return 1; }
    private int clear() { ensure(); int n = cfg.carries.size(); cfg.carries.clear(); ConstellationClient.saveConfig(); local("Cleared " + n + " active carries."); return 1; }
    private int list() { ensure(); if (cfg.carries.isEmpty()) { local("No active carries."); return 1; } cfg.carries.values().forEach(PegasusParty::line); return 1; }
    private int status() {
        ensure();
        local("Tracker " + on(cfg.carryTracker) + ", HUD " + on(cfg.carryHud) + ", party progress " + on(cfg.carryPartyProgress)
            + ", payment detection " + on(cfg.carryPaymentDetection) + ", highlighting " + on(cfg.carryHighlightPlayer)
            + ", auto Dungeon/Kuudra/Slayer " + on(cfg.carryAutoDungeon) + "/" + on(cfg.carryAutoKuudra) + "/" + on(cfg.carryAutoSlayer)
            + ", Slayer boss/player " + on(cfg.carrySlayerHighlightBoss) + "/" + on(cfg.carrySlayerHighlightPlayer)
            + ", webhook " + on(cfg.carryWebhook) + ", active " + cfg.carries.size() + ", history " + cfg.carryHistory.size() + '.');
        return 1;
    }
    private int history(int page) {
        if (cfg.carryHistory.isEmpty()) { local("No carry history."); return 1; }
        List<PegasusConfig.CarryHistory> sorted = cfg.carryHistory.stream()
            .sorted(Comparator.comparingLong((PegasusConfig.CarryHistory h) -> h.timestamp).reversed()).toList();
        int pages = Math.max(1, (sorted.size() + 9) / 10);
        int current = Math.clamp(page, 1, pages);
        local("Carry history page " + current + "/" + pages + ":");
        sorted.subList((current - 1) * 10, Math.min(current * 10, sorted.size())).forEach(h ->
            local(h.player + " " + h.completed + "x " + h.target + " in " + duration(h.durationMs)
                + ", paid " + money(h.paid) + "/" + money(safeMultiply(h.pricePerRun, h.total))));
        return 1;
    }

    private int option(String name, boolean value) {
        switch (name.toLowerCase(Locale.ROOT)) {
            case "tracker", "enabled" -> cfg.carryTracker = value;
            case "hud" -> cfg.carryHud = value;
            case "hudarea", "relevant" -> cfg.carryHudOnlyRelevantArea = value;
            case "hudpayment", "paymenthud" -> cfg.carryHudShowPayment = value;
            case "hudrate", "rate" -> cfg.carryHudShowRate = value;
            case "party", "announce" -> cfg.carryPartyProgress = value;
            case "start", "startmessage" -> cfg.carryShowStartMessage = value;
            case "payment", "paymentdetection" -> cfg.carryPaymentDetection = value;
            case "paymentchat" -> cfg.carryPaymentChat = value;
            case "autodungeon" -> cfg.carryAutoDungeon = value;
            case "autokuudra" -> cfg.carryAutoKuudra = value;
            case "autoslayer" -> cfg.carryAutoSlayer = value;
            case "highlight" -> cfg.carryHighlightPlayer = value;
            case "highlightwalls", "walls" -> cfg.carryHighlightThroughWalls = value;
            case "highlightlabel", "label" -> cfg.carryHighlightLabel = value;
            case "slayerspawn", "slayerstart" -> cfg.carrySlayerSpawnMessage = value;
            case "slayerplayer" -> cfg.carrySlayerHighlightPlayer = value;
            case "slayerplayerwalls" -> cfg.carrySlayerPlayerThroughWalls = value;
            case "slayerplayerlabel" -> cfg.carrySlayerPlayerLabel = value;
            case "slayerboss" -> cfg.carrySlayerHighlightBoss = value;
            case "slayerwalls" -> cfg.carrySlayerHighlightThroughWalls = value;
            case "webhook" -> cfg.carryWebhook = value;
            case "webhookeach" -> cfg.carryWebhookEach = value;
            case "webhookcompletion" -> cfg.carryWebhookCompletion = value;
            case "webhookerrors" -> cfg.carryWebhookErrors = value;
            default -> {
                local("Unknown option. Use tracker, hud, hudarea, hudpayment, hudrate, party, start, payment, paymentchat, autoDungeon, autoKuudra, autoSlayer, highlight, walls, label, slayerSpawn, slayerPlayer, slayerPlayerWalls, slayerPlayerLabel, slayerBoss, slayerWalls, webhook, webhookEach, webhookCompletion, or webhookErrors.");
                return 0;
            }
        }
        ConstellationClient.saveConfig();
        local("Carry " + name + " set to " + value + ".");
        return 1;
    }

    private int highlightWidth(float value) {
        cfg.carryHighlightLineWidth = Math.clamp(value, 0.1f, 10f);
        ConstellationClient.saveConfig();
        local("Carry highlight width updated.");
        return 1;
    }

    private int highlightColor(String value) {
        Integer colour = parseColour(value);
        if (colour == null) { local("Invalid color. Use RRGGBB or AARRGGBB."); return 0; }
        cfg.carryHighlightColour = colour;
        ConstellationClient.saveConfig();
        local("Carry highlight color updated.");
        return 1;
    }

    private int slayerBossWidth(float value) {
        cfg.carrySlayerBossLineWidth = Math.clamp(value, 0.1f, 10f);
        ConstellationClient.saveConfig();
        local("Slayer carry boss width updated.");
        return 1;
    }

    private int slayerBossColor(String value) {
        Integer colour = parseColour(value);
        if (colour == null) { local("Invalid color. Use RRGGBB or AARRGGBB."); return 0; }
        cfg.carrySlayerBossColour = colour;
        ConstellationClient.saveConfig();
        local("Slayer carry boss color updated.");
        return 1;
    }

    private int slayerPlayerWidth(float value) {
        cfg.carrySlayerPlayerLineWidth = Math.clamp(value, 0.1f, 10f);
        ConstellationClient.saveConfig();
        local("Slayer carry player width updated.");
        return 1;
    }

    private int slayerPlayerColor(String value) {
        Integer colour = parseColour(value);
        if (colour == null) { local("Invalid color. Use RRGGBB or AARRGGBB."); return 0; }
        cfg.carrySlayerPlayerColour = colour;
        ConstellationClient.saveConfig();
        local("Slayer carry player color updated.");
        return 1;
    }

    private int message(String type, String value) {
        String clean = value.replace('\n', ' ').replace('\r', ' ').trim();
        if (clean.isEmpty() || clean.length() > 200) { local("Carry message must be 1-200 characters."); return 0; }
        if (type.equalsIgnoreCase("start")) cfg.carryStartMessage = clean;
        else if (type.equalsIgnoreCase("slayerstart") || type.equalsIgnoreCase("slayerspawn")) cfg.carrySlayerSpawnTemplate = clean;
        else if (type.equalsIgnoreCase("party") || type.equalsIgnoreCase("progress")) PartyMessages.setTemplate("carry-progress", clean);
        else { local("Message type must be start, slayerStart, or party."); return 0; }
        ConstellationClient.saveConfig();
        local("Carry " + type + " message updated. Variables: {player} {target} {completed} {total} {paid} {expected} {price} {rate}; Slayer spawn also has {type} and {tier}.");
        return 1;
    }

    private int webhookUrl(String value) {
        String clean = value.trim();
        if (!clean.isEmpty() && !validWebhook(clean)) { local("Webhook URL must be a valid HTTPS URL."); return 0; }
        cfg.carryWebhookUrl = clean;
        ConstellationClient.saveConfig();
        local(clean.isEmpty() ? "Carry webhook URL cleared." : "Carry webhook URL saved.");
        return 1;
    }

    private int webhookTest() {
        if (!validWebhook(cfg.carryWebhookUrl)) { local("Set a valid HTTPS webhook URL first."); return 0; }
        webhook("Constellation carry webhook test.");
        local("Carry webhook test queued.");
        return 1;
    }

    private void maintenance() {
        expirePayment();
        if (kuudraRun && System.currentTimeMillis() - kuudraStartAt > 5_000 && !inKuudra()) {
            kuudraRun = false;
            pendingKuudraStart = false;
            kuudraStartAt = 0;
        }
        if (!SLAYER_BOSS_CARRIES.isEmpty()) {
            java.util.Set<Integer> live = SlayerState.bosses().stream().map(boss -> boss.entity().getId()).collect(java.util.stream.Collectors.toSet());
            SLAYER_BOSS_CARRIES.keySet().removeIf(id -> !live.contains(id));
        }
        if (pendingKuudraStart) announceKuudraStart();
    }

    // start/progress behavior ported from Athen (BSD-3-Clause): modules/impl/kuudra/carry/KuudraCarryTracker.kt
    private void announceKuudraStart() {
        String tier = kuudraTier();
        if (tier.isBlank()) return;
        pendingKuudraStart = false;
        if (!cfg.carryShowStartMessage) return;
        List<String> present = worldPlayers();
        for (PegasusConfig.CarryData carry : cfg.carries.values()) {
            if (!"KUUDRA".equals(carry.type) || !tier.equalsIgnoreCase(carry.target)) continue;
            if (!present.contains(carry.player.toLowerCase(Locale.ROOT))) continue;
            local(template(cfg.carryStartMessage, carry));
        }
    }

    // player highlighting ported from Athen (BSD-3-Clause): modules/impl/kuudra/carry/KuudraCarryTracker.kt
    private void drawCarryPlayers(WorldRenderer.Ctx ctx) {
        if (!enabled()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;
        float partial = mc.getDeltaTracker().getGameTimeDeltaPartialTick(true);
        if (cfg.carrySlayerHighlightBoss) {
            for (SlayerState.Boss boss : SlayerState.bosses()) {
                if (!SLAYER_BOSS_CARRIES.containsKey(boss.entity().getId()) || !boss.entity().isAlive()) continue;
                ctx.outline(interpolatedBox(boss.entity(), partial), cfg.carrySlayerBossColour,
                    cfg.carrySlayerHighlightThroughWalls, cfg.carrySlayerBossLineWidth);
            }
        }
        if (!cfg.carryHighlightPlayer && !cfg.carrySlayerHighlightPlayer) return;
        for (Player player : mc.level.players()) {
            if (player == mc.player || !player.isAlive() || player.isSpectator() || player.getUUID().version() != 4) continue;
            PegasusConfig.CarryData carry = find(player.getGameProfile().name());
            if (carry == null) continue;
            boolean kuudra = "KUUDRA".equals(carry.type) && cfg.carryHighlightPlayer && kuudraRun && inKuudra();
            boolean slayer = "SLAYER".equals(carry.type) && cfg.carrySlayerHighlightPlayer;
            if (!kuudra && !slayer) continue;
            double x = player.xo + (player.getX() - player.xo) * partial;
            double y = player.yo + (player.getY() - player.yo) * partial;
            double z = player.zo + (player.getZ() - player.zo) * partial;
            AABB box = interpolatedBox(player, partial);
            int colour = slayer ? cfg.carrySlayerPlayerColour : cfg.carryHighlightColour;
            boolean walls = slayer ? cfg.carrySlayerPlayerThroughWalls : cfg.carryHighlightThroughWalls;
            float width = slayer ? cfg.carrySlayerPlayerLineWidth : cfg.carryHighlightLineWidth;
            boolean label = slayer ? cfg.carrySlayerPlayerLabel : cfg.carryHighlightLabel;
            ctx.outline(box, colour, walls, width);
            if (label)
                ctx.label(new Vec3(x, box.maxY + 0.35, z), carry.player + " " + carry.completed + "/" + carry.total,
                    colour, walls);
        }
    }

    private static AABB interpolatedBox(net.minecraft.world.entity.Entity entity, float partial) {
        double x = entity.xo + (entity.getX() - entity.xo) * partial;
        double y = entity.yo + (entity.getY() - entity.yo) * partial;
        double z = entity.zo + (entity.getZ() - entity.zo) * partial;
        return entity.getBoundingBox().move(x - entity.getX(), y - entity.getY(), z - entity.getZ());
    }
    private int missing(String p) { local(p + " is not being tracked."); return 0; }

    private static void line(PegasusConfig.CarryData c) {
        Minecraft mc = Minecraft.getInstance(); if (mc.player == null) return;
        Component text = Component.literal("§bCarry §8> §f" + c.player + " §7[" + c.target + "] §b" + c.completed + "/" + c.total
            + " §7paid " + money(c.paid) + "/" + money(expected(c)) + " ")
            .append(Component.literal("§a[+] ").withStyle(s -> s.withClickEvent(new ClickEvent.RunCommand("/carry plus " + c.player))))
            .append(Component.literal("§c[-] ").withStyle(s -> s.withClickEvent(new ClickEvent.RunCommand("/carry minus " + c.player))))
            .append(Component.literal("§8[remove]").withStyle(s -> s.withClickEvent(new ClickEvent.RunCommand("/carry remove " + c.player))));
        mc.player.sendSystemMessage(text);
    }

    public static List<String> hudLines() {
        PegasusParty self = instance();
        if (self == null || !self.enabled() || self.cfg.carries.isEmpty()) return List.of();
        long now = System.currentTimeMillis();
        List<String> result = new ArrayList<>();
        for (PegasusConfig.CarryData carry : self.cfg.carries.values()) {
            if (self.cfg.carryHudOnlyRelevantArea && !self.relevant(carry)) continue;
            String since = carry.lastCompletion == 0 ? "N/A" : duration(now - carry.lastCompletion);
            String rate = rate(carry, now);
            String line = carry.player + " [" + carry.target + "] " + carry.completed + "/" + carry.total;
            if (self.cfg.carryHudShowRate) line += " (" + since + " | " + rate + ")";
            if (self.cfg.carryHudShowPayment) line += " paid " + money(carry.paid) + "/" + money(expected(carry));
            result.add(line);
        }
        return List.copyOf(result);
    }

    private static PegasusParty instance() {
        return INSTANCE;
    }

    private boolean enabled() { return cfg != null && cfg.enabled && cfg.carryMode && cfg.carryTracker && ConstellationClient.loc().onHypixel(); }
    private PegasusConfig.CarryData find(String player) { ensure(); return cfg.carries.get(player.toLowerCase(Locale.ROOT)); }
    private void ensure() {
        if (cfg.carries == null) cfg.carries = new java.util.LinkedHashMap<>();
        if (cfg.carryHistory == null) cfg.carryHistory = new ArrayList<>();
        cfg.carries.entrySet().removeIf(entry -> entry.getKey() == null || entry.getValue() == null);
        for (Map.Entry<String, PegasusConfig.CarryData> entry : cfg.carries.entrySet()) {
            PegasusConfig.CarryData carry = entry.getValue();
            if (carry.player == null || carry.player.isBlank()) carry.player = entry.getKey();
            if (carry.type == null) carry.type = "DUNGEON";
            carry.type = carry.type.toUpperCase(Locale.ROOT);
            if (carry.target == null) carry.target = "";
            String target = carry.target.toUpperCase(Locale.ROOT);
            String normalized = "SLAYER".equals(carry.type) ? normalizeSlayerTarget(target) : normalizeTarget(target);
            carry.target = normalized == null ? target : normalized;
            carry.total = Math.clamp(carry.total, 1, 10_000);
            carry.completed = (int) Math.clamp((long) carry.completed, 0, carry.total);
            carry.pricePerRun = Math.max(0, carry.pricePerRun);
            carry.paid = Math.max(0, carry.paid);
            carry.paidRuns = Math.max(0, carry.paidRuns);
        }
        cfg.carryHistory.removeIf(java.util.Objects::isNull);
    }
    private static void clearTransient() {
        recentTrader = null;
        recentCoins = null;
        recentTradeAt = 0;
        recentCoinsAt = 0;
        lastCompletion = 0;
        kuudraRun = false;
        pendingKuudraStart = false;
        kuudraStartAt = 0;
        SLAYER_BOSS_CARRIES.clear();
    }
    private static void send(String command) { Minecraft mc = Minecraft.getInstance(); if (mc.player != null && mc.player.connection != null) mc.player.connection.sendCommand(command); }
    private static void local(String text) { Minecraft mc = Minecraft.getInstance(); if (mc.player != null) mc.player.sendSystemMessage(Component.literal("§bCarry §8> §f" + text)); }
    private static String normalizeTarget(String target) { return switch (target) { case "BASIC" -> "T1"; case "HOT" -> "T2"; case "BURNING" -> "T3"; case "FIERY" -> "T4"; case "INFERNAL" -> "T5"; default -> target; }; }
    private static String normalizeSlayerTarget(String target) {
        String clean = target.trim().replace('_', ' ').replace(':', ' ').replace('-', ' ').replaceAll("\\s+", " ");
        Matcher compact = Pattern.compile("^([A-Z]+?)(?:T)?([1-5])$").matcher(clean.replace(" ", ""));
        String typeValue;
        String tierValue;
        if (compact.matches()) {
            typeValue = compact.group(1);
            tierValue = compact.group(2);
        } else {
            String[] split = clean.split(" ");
            if (split.length != 2) return null;
            typeValue = split[0];
            tierValue = split[1];
        }
        SlayerState.Type type = SlayerState.parseType(typeValue);
        if (type == null) return null;
        if (tierValue.equalsIgnoreCase("ANY")) return type.shortName() + " ANY";
        String number = tierValue.toUpperCase(Locale.ROOT).startsWith("T") ? tierValue.substring(1) : tierValue;
        try {
            int tier = Integer.parseInt(number);
            return tier >= 1 && tier <= type.maxTier() ? type.shortName() + " T" + tier : null;
        } catch (NumberFormatException ignored) { return null; }
    }

    private static boolean matchesSlayer(PegasusConfig.CarryData carry, SlayerState.Boss boss) {
        if (!"SLAYER".equals(carry.type)) return false;
        String normalized = normalizeSlayerTarget(carry.target);
        if (normalized == null || !normalized.startsWith(boss.type().shortName() + " ")) return false;
        return normalized.endsWith(" ANY") || normalized.endsWith(" T" + boss.tier());
    }
    private static boolean inKuudra() {
        return ConstellationClient.loc().area() == com.froggylord.constellation.core.LocationManager.SkyblockArea.KUUDRA;
    }
    private static String kuudraTier() {
        for (String line : ConstellationClient.loc().getSidebarLines()) {
            Matcher matcher = KUUDRA_TIER.matcher(line);
            if (matcher.find()) return "T" + matcher.group(1);
        }
        int stateTier = KuudraState.tier();
        if (stateTier >= 1 && stateTier <= 5) return "T" + stateTier;
        return "";
    }
    private static Long coins(String number, String suffix) {
        try {
            double n = Double.parseDouble(number.replace(",", ""));
            double m = suffix.equalsIgnoreCase("k") ? 1_000 : suffix.equalsIgnoreCase("m") ? 1_000_000 : suffix.equalsIgnoreCase("b") ? 1_000_000_000 : 1;
            double result = n * m;
            return Double.isFinite(result) && result >= 0 && result <= Long.MAX_VALUE ? Math.round(result) : null;
        } catch (RuntimeException ignored) { return null; }
    }
    private static String money(long coins) { if (coins >= 1_000_000_000) return String.format(Locale.ROOT, "%.2fb", coins / 1e9); if (coins >= 1_000_000) return String.format(Locale.ROOT, "%.2fm", coins / 1e6); if (coins >= 1_000) return String.format(Locale.ROOT, "%.1fk", coins / 1e3); return Long.toString(coins); }

    private boolean relevant(PegasusConfig.CarryData carry) {
        if ("KUUDRA".equals(carry.type)) return ConstellationClient.loc().area() == com.froggylord.constellation.core.LocationManager.SkyblockArea.KUUDRA;
        if ("DUNGEON".equals(carry.type)) return ConstellationClient.loc().inDungeons();
        return !ConstellationClient.loc().inDungeons()
            && ConstellationClient.loc().area() != com.froggylord.constellation.core.LocationManager.SkyblockArea.KUUDRA;
    }

    private static String template(String value, PegasusConfig.CarryData carry) {
        String clean = value == null ? "" : value.replace('\n', ' ').replace('\r', ' ').trim();
        return clean.replace("{player}", carry.player).replace("{target}", carry.target)
            .replace("{completed}", Integer.toString(carry.completed)).replace("{total}", Integer.toString(carry.total))
            .replace("{paid}", money(carry.paid)).replace("{expected}", money(expected(carry)))
            .replace("{price}", money(carry.pricePerRun)).replace("{rate}", rate(carry, System.currentTimeMillis()));
    }

    private static long expected(PegasusConfig.CarryData carry) {
        return safeMultiply(carry.pricePerRun, carry.total);
    }

    private static Map<String, Object> variables(PegasusConfig.CarryData carry) {
        return Map.of("player", carry.player, "target", carry.target, "completed", carry.completed, "total", carry.total,
            "paid", money(carry.paid), "expected", money(expected(carry)), "price", money(carry.pricePerRun),
            "rate", rate(carry, System.currentTimeMillis()));
    }

    private static long safeAdd(long first, long second) {
        try { return Math.addExact(first, second); }
        catch (ArithmeticException ignored) { return Long.MAX_VALUE; }
    }

    private static long safeMultiply(long first, long second) {
        try { return Math.multiplyExact(first, second); }
        catch (ArithmeticException ignored) { return Long.MAX_VALUE; }
    }

    private static String on(boolean value) { return value ? "on" : "off"; }

    private static String rate(PegasusConfig.CarryData carry, long now) {
        if (carry.completed <= 2 || carry.firstCompletion <= 0 || now <= carry.firstCompletion) return "N/A";
        long elapsed = now - carry.firstCompletion;
        long perHour = Math.max(0, Math.round(carry.completed * 3_600_000.0 / elapsed));
        return perHour + "/hr";
    }

    private static String duration(long millis) {
        if (millis <= 0) return "N/A";
        long seconds = millis / 1000;
        long hours = seconds / 3600;
        long minutes = seconds % 3600 / 60;
        long remain = seconds % 60;
        return hours > 0 ? hours + "h " + minutes + "m " + remain + "s"
            : minutes > 0 ? minutes + "m " + remain + "s" : remain + "s";
    }

    private static void expirePayment() {
        long now = System.currentTimeMillis();
        if (recentTrader != null && now - recentTradeAt > 4000) { recentTrader = null; recentTradeAt = 0; }
        if (recentCoins != null && now - recentCoinsAt > 4000) { recentCoins = null; recentCoinsAt = 0; }
    }

    private static boolean validWebhook(String value) {
        try {
            URI uri = URI.create(value == null ? "" : value.trim());
            return "https".equalsIgnoreCase(uri.getScheme()) && uri.getHost() != null && !uri.getHost().isBlank();
        } catch (RuntimeException ignored) { return false; }
    }

    // webhook behavior ported from Athen (BSD-3-Clause): modules/impl/kuudra/carry/KuudraCarryTracker.kt
    private void webhook(String content) {
        if (!validWebhook(cfg.carryWebhookUrl)) {
            if (cfg.carryWebhookErrors) local("Webhook is enabled but its URL is invalid.");
            return;
        }
        String body = "{\"content\":\"" + json(content) + "\"}";
        HttpRequest request;
        try {
            request = HttpRequest.newBuilder(URI.create(cfg.carryWebhookUrl.trim()))
                .timeout(Duration.ofSeconds(10)).header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body)).build();
        } catch (RuntimeException error) {
            if (cfg.carryWebhookErrors) local("Could not create the carry webhook request.");
            return;
        }
        WEBHOOK_HTTP.sendAsync(request, HttpResponse.BodyHandlers.discarding()).whenComplete((response, error) -> {
            if (!cfg.carryWebhookErrors || error == null && response != null && response.statusCode() >= 200 && response.statusCode() < 300) return;
            Minecraft.getInstance().execute(() -> local("Carry webhook failed without exposing its URL."));
        });
    }

    private static String json(String value) {
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"' -> result.append("\\\"");
                case '\\' -> result.append("\\\\");
                case '\n' -> result.append("\\n");
                case '\r' -> result.append("\\r");
                case '\t' -> result.append("\\t");
                default -> { if (c < 0x20) result.append(' '); else result.append(c); }
            }
        }
        return result.toString();
    }

    private static Integer parseColour(String value) {
        try {
            String clean = value.startsWith("#") ? value.substring(1) : value;
            if (!clean.matches("[0-9a-fA-F]{6}|[0-9a-fA-F]{8}")) return null;
            long parsed = Long.parseUnsignedLong(clean, 16);
            return (int) (clean.length() == 6 ? parsed | 0xFF000000L : parsed);
        } catch (RuntimeException ignored) { return null; }
    }
}
