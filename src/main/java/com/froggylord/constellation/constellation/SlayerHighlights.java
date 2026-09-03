package com.froggylord.constellation.constellation;

import com.froggylord.constellation.ConstellationClient;
import com.froggylord.constellation.config.PerseusConfig;
import com.froggylord.constellation.render.WorldRenderer;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.wolf.Wolf;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.monster.Blaze;
import net.minecraft.world.entity.monster.EnderMan;
import net.minecraft.world.entity.monster.skeleton.Skeleton;
import net.minecraft.world.entity.monster.spider.CaveSpider;
import net.minecraft.world.entity.monster.spider.Spider;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// highlight behavior ported from NoFrills (GPL-3.0): features/slayer/BossHighlight.java and SlayerMinibossHighlight.java
// alerts ported from Athen (BSD-3-Clause): modules/impl/slayer/MinibossAlert.kt
// miniboss names and type exceptions ported from Skyblocker (LGPL-3.0-or-later): skyblock/slayers/SlayerType.java
public final class SlayerHighlights {
    private enum Kind { REV, TARA, SVEN, VOID, BLAZE }
    private record MiniDef(String name, Kind kind, int minimumTier, boolean big) {}
    private record Mini(Entity entity, MiniDef def, int standId) {}
    private record Quest(Kind kind, int tier) {}

    private static final List<MiniDef> DEFINITIONS = List.of(
        new MiniDef("Revenant Sycophant", Kind.REV, 3, false), new MiniDef("Revenant Champion", Kind.REV, 4, false),
        new MiniDef("Deformed Revenant", Kind.REV, 4, true), new MiniDef("Atoned Champion", Kind.REV, 5, false),
        new MiniDef("Atoned Revenant", Kind.REV, 5, true), new MiniDef("Tarantula Vermin", Kind.TARA, 3, false),
        new MiniDef("Tarantula Beast", Kind.TARA, 4, false), new MiniDef("Mutant Tarantula", Kind.TARA, 4, true),
        new MiniDef("Primordial Jockey", Kind.TARA, 5, false), new MiniDef("Primordial Viscount", Kind.TARA, 5, true),
        new MiniDef("Pack Enforcer", Kind.SVEN, 3, false), new MiniDef("Sven Follower", Kind.SVEN, 4, false),
        new MiniDef("Sven Alpha", Kind.SVEN, 4, true), new MiniDef("Voidling Devotee", Kind.VOID, 3, false),
        new MiniDef("Voidling Radical", Kind.VOID, 4, false), new MiniDef("Voidcrazed Maniac", Kind.VOID, 4, true),
        new MiniDef("Flare Demon", Kind.BLAZE, 3, false), new MiniDef("Kindleheart Demon", Kind.BLAZE, 4, false),
        new MiniDef("Burningsoul Demon", Kind.BLAZE, 4, true));
    private static final Pattern QUEST = Pattern.compile("(Revenant Horror|Atoned Horror|Tarantula Broodfather|Conjoined Brood|Sven Packmaster|Voidgloom Seraph|Inferno Demonlord)\\s+([IV]{1,5})");
    private static final Pattern CHAT_SPAWN = Pattern.compile("^SLAYER MINI-BOSS (.+?) has spawned!$");
    private static final Map<Integer, Mini> MINIS = new LinkedHashMap<>();
    private static PerseusConfig cfg;
    private static boolean initialized;
    private static long lastScanTick = Long.MIN_VALUE;
    private static long lastAlertNanos;
    private static String lastAlertName = "";
    private static Quest lastQuest;

    private SlayerHighlights() {}

    public static void init(PerseusConfig config) {
        cfg = config;
        if (initialized) return;
        initialized = true;
        ClientTickEvents.END_CLIENT_TICK.register(client -> tick());
        ClientReceiveMessageEvents.GAME.register((message, overlay) -> {
            if (!overlay) onChat(message.getString());
        });
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> reset());
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> reset());
    }

    private static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (!active() || mc.level == null || mc.player == null) {
            MINIS.clear();
            return;
        }
        long tick = mc.level.getGameTime();
        if (tick == lastScanTick) return;
        lastScanTick = tick;
        MINIS.entrySet().removeIf(entry -> entry.getValue().entity().isRemoved() || !entry.getValue().entity().isAlive()
            || mc.level.getEntity(entry.getKey()) != entry.getValue().entity());
        Quest quest = quest();
        if (quest == null) {
            MINIS.clear();
            lastQuest = null;
            return;
        }
        if (!quest.equals(lastQuest)) {
            MINIS.clear();
            lastQuest = quest;
        }
        if (!cfg.slayerMinibossHighlight && !cfg.slayerMinibossAlertEntityDetection) return;
        double range = Math.max(Math.clamp(cfg.slayerHighlightRange, 8, 256), Math.clamp(cfg.slayerMinibossAlertRange, 1, 32));
        double range2 = range * range;
        for (Entity entity : mc.level.entitiesForRendering()) {
            if (!(entity instanceof ArmorStand stand) || !stand.hasCustomName() || stand.distanceToSqr(mc.player) > range2) continue;
            String name = plain(stand);
            MiniDef def = definition(name);
            if (def == null || def.kind() != quest.kind() || def.minimumTier() > quest.tier()) continue;
            Entity mob = resolve(stand, def);
            if (!(mob instanceof LivingEntity) || !mob.isAlive() || MINIS.containsKey(mob.getId())) continue;
            Mini mini = new Mini(mob, def, stand.getId());
            MINIS.put(mob.getId(), mini);
            if (cfg.slayerMinibossAlertEntityDetection && mob.tickCount < 20
                && mob.distanceToSqr(mc.player) <= Math.pow(Math.clamp(cfg.slayerMinibossAlertRange, 1, 32), 2)) alert(def);
        }
    }

    private static void onChat(String raw) {
        if (!active() || !cfg.slayerMinibossAlert || !cfg.slayerMinibossAlertChatDetection
            || cfg.slayerMinibossAlertEntityDetection) return;
        String stripped = ChatFormatting.stripFormatting(raw);
        Matcher matcher = CHAT_SPAWN.matcher(stripped == null ? raw.trim() : stripped.trim());
        if (!matcher.matches()) return;
        MiniDef def = definition(matcher.group(1));
        if (def != null) alert(def);
    }

    public static void draw(WorldRenderer.Ctx ctx) {
        if (!active()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        double range = Math.clamp(cfg.slayerHighlightRange, 8, 256);
        double range2 = range * range;
        if (cfg.slayerBossHighlight) for (SlayerState.Boss boss : SlayerState.bosses()) {
            if (!boss.entity().isAlive() || boss.entity().distanceToSqr(mc.player) > range2
                || cfg.slayerBossHighlightOnlyMine && !boss.owner().equalsIgnoreCase(mc.player.getName().getString())) continue;
            int[] colors = cfg.infernoAttunementBossColors && boss.type() == SlayerState.Type.BLAZE
                ? SlayerSpecialInfo.colors(boss.entity().getId(), cfg.slayerBossHighlightOutlineColor, cfg.slayerBossHighlightFillColor)
                : new int[]{cfg.slayerBossHighlightOutlineColor, cfg.slayerBossHighlightFillColor};
            render(ctx, boss.entity().getBoundingBox(), cfg.slayerBossHighlightOutline, cfg.slayerBossHighlightFill,
                colors[0], colors[1],
                cfg.slayerBossHighlightThroughWalls, cfg.slayerBossHighlightWidth);
            if (cfg.slayerBossHighlightLabel)
                ctx.label(boss.entity().position().add(0, boss.entity().getBbHeight() + 0.25, 0), boss.variant(),
                    cfg.slayerBossHighlightOutlineColor, cfg.slayerBossHighlightThroughWalls);
        }
        Quest quest = quest();
        if (cfg.slayerMinibossHighlight && quest != null) for (Mini mini : new ArrayList<>(MINIS.values())) {
            if (!mini.entity().isAlive() || mini.entity().distanceToSqr(mc.player) > range2
                || mini.def().kind() != quest.kind() || mini.def().minimumTier() > quest.tier()) continue;
            render(ctx, mini.entity().getBoundingBox(), cfg.slayerMinibossHighlightOutline, cfg.slayerMinibossHighlightFill,
                cfg.slayerMinibossHighlightOutlineColor, cfg.slayerMinibossHighlightFillColor,
                cfg.slayerMinibossHighlightThroughWalls, cfg.slayerMinibossHighlightWidth);
            if (cfg.slayerMinibossHighlightLabel)
                ctx.label(mini.entity().position().add(0, mini.entity().getBbHeight() + 0.25, 0), mini.def().name(),
                    cfg.slayerMinibossHighlightOutlineColor, cfg.slayerMinibossHighlightThroughWalls);
        }
    }

    private static void render(WorldRenderer.Ctx ctx, AABB box, boolean outline, boolean fill, int outlineColor,
                               int fillColor, boolean throughWalls, float width) {
        if (fill) ctx.box(box.inflate(0.03), fillColor, throughWalls);
        if (outline) ctx.outline(box.inflate(0.03), outlineColor, throughWalls, Math.clamp(width, 0.1f, 10f));
    }

    private static Entity resolve(ArmorStand stand, MiniDef def) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return null;
        Entity exact = mc.level.getEntity(stand.getId() - 1);
        if (matches(exact, def) && exact.distanceToSqr(stand) <= 9) return mounted(exact);
        List<LivingEntity> candidates = mc.level.getEntitiesOfClass(LivingEntity.class,
            stand.getBoundingBox().inflate(1, 3, 1), entity -> matches(entity, def));
        if (candidates.size() != 1) return null;
        return mounted(candidates.getFirst());
    }

    private static Entity mounted(Entity entity) {
        return entity.isPassenger() && entity.getVehicle() != null ? entity.getVehicle() : entity;
    }

    private static boolean matches(Entity entity, MiniDef def) {
        if (entity == null || entity instanceof ArmorStand) return false;
        if (def.name().equals("Primordial Jockey")) return entity instanceof Skeleton;
        if (def.name().equals("Primordial Viscount")) return entity instanceof CaveSpider;
        return switch (def.kind()) {
            case REV -> entity instanceof Zombie;
            case TARA -> entity instanceof Spider && !(entity instanceof CaveSpider);
            case SVEN -> entity instanceof Wolf;
            case VOID -> entity instanceof EnderMan;
            case BLAZE -> entity instanceof Blaze;
        };
    }

    private static Quest quest() {
        for (String line : ConstellationClient.loc().getSidebarLines()) {
            String value = ChatFormatting.stripFormatting(line);
            String text = value == null ? line : value;
            Matcher matcher = QUEST.matcher(text);
            if (!matcher.find()) continue;
            Kind kind = switch (matcher.group(1)) {
                case "Revenant Horror", "Atoned Horror" -> Kind.REV;
                case "Tarantula Broodfather", "Conjoined Brood" -> Kind.TARA;
                case "Sven Packmaster" -> Kind.SVEN;
                case "Voidgloom Seraph" -> Kind.VOID;
                case "Inferno Demonlord" -> Kind.BLAZE;
                default -> null;
            };
            if (kind != null) return new Quest(kind, roman(matcher.group(2)));
        }
        return null;
    }

    private static MiniDef definition(String text) {
        for (MiniDef def : DEFINITIONS) if (text.contains(def.name())) return def;
        return null;
    }

    private static void alert(MiniDef def) {
        if (!cfg.slayerMinibossAlert) return;
        long now = System.nanoTime();
        if (def.name().equals(lastAlertName) && now - lastAlertNanos < 750_000_000L) return;
        lastAlertName = def.name();
        lastAlertNanos = now;
        String template = def.big() ? cfg.slayerMinibossBigAlertText : cfg.slayerMinibossAlertText;
        String text = style((template == null ? "" : template).replace("{name}", def.name()).replace("{kind}", def.big() ? "big" : "normal"));
        if (cfg.slayerMinibossAlertShowName && !text.contains(def.name())) text = text + " " + def.name();
        if (text.isBlank()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        if (cfg.slayerMinibossAlertChat) mc.player.sendSystemMessage(Component.literal(text));
        if (cfg.slayerMinibossAlertTitle && cfg.minibossFlash) {
            mc.gui.hud.resetTitleTimes();
            mc.gui.hud.setTimes(5, Math.clamp(cfg.slayerMinibossAlertTicks, 5, 200), 5);
            mc.gui.hud.setTitle(Component.literal(text));
        }
        if (cfg.slayerMinibossAlertSound) mc.player.playSound(SoundEvents.NOTE_BLOCK_PLING.value(), 1f, def.big() ? 0.8f : 1.2f);
    }

    public static void onBossSpawn(SlayerState.Boss boss) {
        if (!active() || !cfg.slayerBossSpawnAlert
            || boss.type() == SlayerState.Type.TARA && boss.tier() == 5 && boss.variant().equals("Conjoined Brood")) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || boss.entity().tickCount >= 20 || cfg.slayerBossSpawnAlertOnlyMine
            && !boss.owner().equalsIgnoreCase(mc.player.getName().getString())) return;
        String template = cfg.slayerBossSpawnAlertText == null ? "" : cfg.slayerBossSpawnAlertText;
        String text = style(template.replace("{name}", boss.variant()).replace("{type}", boss.type().shortName())
            .replace("{tier}", Integer.toString(boss.tier())).replace("{owner}", boss.owner()));
        if (cfg.slayerBossSpawnAlertShowName && !text.contains(boss.variant())) text = text + " " + boss.variant();
        if (text.isBlank()) return;
        if (cfg.slayerBossSpawnAlertChat) mc.player.sendSystemMessage(Component.literal(text));
        if (cfg.spawnAlertTitle) {
            mc.gui.hud.resetTitleTimes();
            mc.gui.hud.setTimes(5, Math.clamp(cfg.slayerMinibossAlertTicks, 5, 200), 5);
            mc.gui.hud.setTitle(Component.literal(text));
        }
        if (cfg.slayerBossSpawnCustomSound) mc.player.playSound(SoundEvents.EXPERIENCE_ORB_PICKUP, 1f, 0f);
    }

    public static void registerCommands(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        dispatcher.register(LiteralArgumentBuilder.<FabricClientCommandSource>literal("slayerhighlights")
            .executes(c -> status())
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("status").executes(c -> status()))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("range")
                .then(RequiredArgumentBuilder.<FabricClientCommandSource, Double>argument("blocks", DoubleArgumentType.doubleArg(8, 256))
                    .executes(c -> range(DoubleArgumentType.getDouble(c, "blocks")))))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("alertrange")
                .then(RequiredArgumentBuilder.<FabricClientCommandSource, Double>argument("blocks", DoubleArgumentType.doubleArg(1, 32))
                    .executes(c -> alertRange(DoubleArgumentType.getDouble(c, "blocks")))))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("duration")
                .then(RequiredArgumentBuilder.<FabricClientCommandSource, Double>argument("seconds", DoubleArgumentType.doubleArg(0.25, 10))
                    .executes(c -> duration(DoubleArgumentType.getDouble(c, "seconds")))))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("width")
                .then(RequiredArgumentBuilder.<FabricClientCommandSource, String>argument("target", StringArgumentType.word())
                    .then(RequiredArgumentBuilder.<FabricClientCommandSource, Double>argument("value", DoubleArgumentType.doubleArg(0.1, 10))
                        .executes(c -> width(StringArgumentType.getString(c, "target"), DoubleArgumentType.getDouble(c, "value"))))))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("color")
                .then(RequiredArgumentBuilder.<FabricClientCommandSource, String>argument("target", StringArgumentType.word())
                    .then(RequiredArgumentBuilder.<FabricClientCommandSource, String>argument("argb", StringArgumentType.word())
                        .executes(c -> color(StringArgumentType.getString(c, "target"), StringArgumentType.getString(c, "argb"))))))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("message")
                .then(RequiredArgumentBuilder.<FabricClientCommandSource, String>argument("target", StringArgumentType.word())
                    .then(RequiredArgumentBuilder.<FabricClientCommandSource, String>argument("text", StringArgumentType.greedyString())
                        .executes(c -> message(StringArgumentType.getString(c, "target"), StringArgumentType.getString(c, "text"))))))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("option")
                .then(RequiredArgumentBuilder.<FabricClientCommandSource, String>argument("name", StringArgumentType.word())
                    .then(RequiredArgumentBuilder.<FabricClientCommandSource, String>argument("value", StringArgumentType.word())
                        .executes(c -> option(StringArgumentType.getString(c, "name"), StringArgumentType.getString(c, "value")))))));
    }

    private static int status() {
        local("Boss highlight " + on(cfg.slayerBossHighlight) + ", miniboss highlight " + on(cfg.slayerMinibossHighlight)
            + ", alert " + on(cfg.slayerMinibossAlert) + ", tracked minis " + MINIS.size() + ".");
        return 1;
    }

    private static int range(double value) {
        cfg.slayerHighlightRange = Math.clamp(value, 8, 256); save("Highlight range updated."); return 1;
    }

    private static int alertRange(double value) {
        cfg.slayerMinibossAlertRange = Math.clamp(value, 1, 32); save("Miniboss alert range updated."); return 1;
    }

    private static int duration(double value) {
        cfg.slayerMinibossAlertTicks = Math.clamp((int) Math.round(value * 20), 5, 200); save("Slayer alert duration updated."); return 1;
    }

    private static int width(String target, double value) {
        float width = (float) Math.clamp(value, 0.1, 10);
        if (target.equalsIgnoreCase("boss")) cfg.slayerBossHighlightWidth = width;
        else if (target.equalsIgnoreCase("mini")) cfg.slayerMinibossHighlightWidth = width;
        else { local("Target must be boss or mini."); return 0; }
        save("Highlight width updated."); return 1;
    }

    private static int color(String target, String raw) {
        Integer color = parseColor(raw);
        if (color == null) { local("Color must be RRGGBB or AARRGGBB hex."); return 0; }
        switch (target.toLowerCase(Locale.ROOT)) {
            case "boss", "bossoutline" -> cfg.slayerBossHighlightOutlineColor = color;
            case "bossfill" -> cfg.slayerBossHighlightFillColor = color;
            case "mini", "minioutline" -> cfg.slayerMinibossHighlightOutlineColor = color;
            case "minifill" -> cfg.slayerMinibossHighlightFillColor = color;
            default -> { local("Target must be boss, bossfill, mini, or minifill."); return 0; }
        }
        save("Highlight color updated."); return 1;
    }

    private static int message(String target, String text) {
        String clean = text.replace('\n', ' ').replace('\r', ' ').trim();
        if (clean.isEmpty() || clean.length() > 200) { local("Message must be 1-200 characters."); return 0; }
        if (target.equalsIgnoreCase("normal")) cfg.slayerMinibossAlertText = clean;
        else if (target.equalsIgnoreCase("big")) cfg.slayerMinibossBigAlertText = clean;
        else if (target.equalsIgnoreCase("boss")) cfg.slayerBossSpawnAlertText = clean;
        else { local("Target must be normal, big, or boss."); return 0; }
        save("Miniboss alert message updated."); return 1;
    }

    private static int option(String name, String raw) {
        Boolean value = bool(raw);
        if (value == null) { local("Value must be on or off."); return 0; }
        switch (name.toLowerCase(Locale.ROOT)) {
            case "boss" -> cfg.slayerBossHighlight = value;
            case "onlymine" -> cfg.slayerBossHighlightOnlyMine = value;
            case "bossoutline" -> cfg.slayerBossHighlightOutline = value;
            case "bossfill" -> cfg.slayerBossHighlightFill = value;
            case "bosswalls" -> cfg.slayerBossHighlightThroughWalls = value;
            case "bosslabel" -> cfg.slayerBossHighlightLabel = value;
            case "mini" -> cfg.slayerMinibossHighlight = value;
            case "minioutline" -> cfg.slayerMinibossHighlightOutline = value;
            case "minifill" -> cfg.slayerMinibossHighlightFill = value;
            case "miniwalls" -> cfg.slayerMinibossHighlightThroughWalls = value;
            case "minilabel" -> cfg.slayerMinibossHighlightLabel = value;
            case "alert" -> cfg.slayerMinibossAlert = value;
            case "chatdetect" -> { cfg.slayerMinibossAlertChatDetection = value; if (value) cfg.slayerMinibossAlertEntityDetection = false; }
            case "entitydetect" -> { cfg.slayerMinibossAlertEntityDetection = value; if (value) cfg.slayerMinibossAlertChatDetection = false; }
            case "title" -> cfg.slayerMinibossAlertTitle = value;
            case "chat" -> cfg.slayerMinibossAlertChat = value;
            case "sound" -> cfg.slayerMinibossAlertSound = value;
            case "showname" -> cfg.slayerMinibossAlertShowName = value;
            case "bossspawn" -> cfg.slayerBossSpawnAlert = value;
            case "bossspawnmine" -> cfg.slayerBossSpawnAlertOnlyMine = value;
            case "bosstitle" -> cfg.spawnAlertTitle = value;
            case "bosschat" -> cfg.slayerBossSpawnAlertChat = value;
            case "bosssound" -> cfg.slayerBossSpawnCustomSound = value;
            case "bossshowname" -> cfg.slayerBossSpawnAlertShowName = value;
            default -> { local("Unknown highlight option."); return 0; }
        }
        save("Slayer highlight option updated."); return 1;
    }

    private static Integer parseColor(String raw) {
        try {
            String value = raw.replace("#", "");
            long parsed = Long.parseUnsignedLong(value, 16);
            if (value.length() == 6) parsed |= 0xFF000000L;
            return value.length() == 6 || value.length() == 8 ? (int) parsed : null;
        } catch (NumberFormatException ignored) { return null; }
    }

    private static Boolean bool(String raw) {
        return switch (raw.toLowerCase(Locale.ROOT)) { case "on", "true", "yes", "1" -> true; case "off", "false", "no", "0" -> false; default -> null; };
    }

    private static int roman(String value) { return switch (value) { case "I" -> 1; case "II" -> 2; case "III" -> 3; case "IV" -> 4; case "V" -> 5; default -> 0; }; }
    private static void reset() { MINIS.clear(); lastQuest = null; lastScanTick = Long.MIN_VALUE; lastAlertNanos = 0; lastAlertName = ""; }
    private static boolean active() { return cfg != null && cfg.enabled && ConstellationClient.loc().onHypixel(); }
    private static String plain(Entity entity) { String value = ChatFormatting.stripFormatting(entity.getName().getString()); return value == null ? entity.getName().getString() : value; }
    private static String style(String text) { return text.replace("<dark_red>", "§4").replace("<dark_gray>", "§8").replace("<gray>", "§7").replace("<red>", "§c").replace("<green>", "§a").replace("<yellow>", "§e").replace("<aqua>", "§b").replace("<blue>", "§9").replace("<gold>", "§6").replace("<white>", "§f").replace("<bold>", "§l").replace("<r>", "§r").replace('&', '§'); }
    private static String on(boolean value) { return value ? "on" : "off"; }
    private static void save(String text) { ConstellationClient.saveConfig(); local(text); }
    private static void local(String text) { Minecraft mc = Minecraft.getInstance(); if (mc.player != null) mc.player.sendSystemMessage(Component.literal("§5Slayer §8> §f" + text)); }
}
