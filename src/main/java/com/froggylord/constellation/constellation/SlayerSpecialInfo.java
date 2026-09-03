package com.froggylord.constellation.constellation;

import com.froggylord.constellation.ConstellationClient;
import com.froggylord.constellation.config.PerseusConfig;
import com.froggylord.constellation.render.WorldRenderer;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.monster.Blaze;
import net.minecraft.world.entity.monster.Guardian;
import net.minecraft.world.entity.monster.skeleton.WitherSkeleton;
import net.minecraft.world.entity.monster.zombie.ZombifiedPiglin;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// display and laser behavior ported from Athen (BSD-3-Clause): modules/impl/slayer/AttunementDisplay.kt and SlayerInfo.kt
// attunement entity colors ported from NoFrills (GPL-3.0): features/slayer/BossHighlight.java
// IMMUNE color cross-checked with Skyblocker (LGPL-3.0-or-later): skyblock/slayers/boss/demonlord/AttunementColors.java
public final class SlayerSpecialInfo {
    private record Attunement(String name, int count, String time, long seenTick) {}
    private record Demon(Entity entity, int standId) {}

    private static final Pattern ATTUNEMENT = Pattern.compile("^(ASHEN|AURIC|CRYSTAL|SPIRIT|IMMUNE)\\s+\\u2668(\\d+)\\s+(\\d{1,2}:\\d{2})$");
    private static final Map<Integer, Attunement> ATTUNEMENTS = new LinkedHashMap<>();
    private static final Map<Integer, Demon> DEMONS = new LinkedHashMap<>();
    private static PerseusConfig cfg;
    private static boolean initialized;
    private static long lastTick = Long.MIN_VALUE;

    private SlayerSpecialInfo() {}

    public static void init(PerseusConfig config) {
        cfg = config;
        if (initialized) return;
        initialized = true;
        ClientTickEvents.END_CLIENT_TICK.register(client -> tick());
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> reset());
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> reset());
    }

    private static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (!active() || mc.level == null || mc.player == null) {
            reset();
            return;
        }
        long now = mc.level.getGameTime();
        if (now == lastTick) return;
        lastTick = now;
        boolean inferno = cfg.blazeHelper && (infernoQuest()
            || SlayerState.bosses().stream().anyMatch(boss -> boss.type() == SlayerState.Type.BLAZE && boss.entity().isAlive()));
        if (!inferno) {
            ATTUNEMENTS.clear();
            DEMONS.clear();
            return;
        }
        for (SlayerState.Boss boss : SlayerState.bosses()) {
            if (boss.type() != SlayerState.Type.BLAZE || !boss.entity().isAlive()) continue;
            readStand(mc.level.getEntity(boss.ownerStandId() - 1), boss.entity(), now, true);
        }
        for (Entity entity : mc.level.entitiesForRendering()) {
            if (!(entity instanceof ArmorStand stand) || !stand.hasCustomName() || stand.distanceToSqr(mc.player) > 4096) continue;
            Matcher matcher = ATTUNEMENT.matcher(plain(stand));
            if (!matcher.matches()) continue;
            Entity owner = resolveAttunedEntity(stand);
            if (owner != null) read(matcher, owner, stand.getId(), now, false);
        }
        ATTUNEMENTS.entrySet().removeIf(entry -> now - entry.getValue().seenTick() > 10
            || mc.level.getEntity(entry.getKey()) == null || !mc.level.getEntity(entry.getKey()).isAlive());
        DEMONS.entrySet().removeIf(entry -> !ATTUNEMENTS.containsKey(entry.getKey()) || entry.getValue().entity().isRemoved()
            || !entry.getValue().entity().isAlive() || mc.level.getEntity(entry.getKey()) != entry.getValue().entity());
    }

    private static void readStand(Entity stand, Entity owner, long now, boolean boss) {
        if (!(stand instanceof ArmorStand armorStand) || !armorStand.hasCustomName()) return;
        Matcher matcher = ATTUNEMENT.matcher(plain(armorStand));
        if (matcher.matches()) read(matcher, owner, armorStand.getId(), now, boss);
    }

    private static void read(Matcher matcher, Entity owner, int standId, long now, boolean boss) {
        int count;
        try { count = Integer.parseInt(matcher.group(2)); }
        catch (NumberFormatException ignored) { return; }
        ATTUNEMENTS.put(owner.getId(), new Attunement(matcher.group(1), Math.clamp(count, 0, 99), matcher.group(3), now));
        if (!boss && isDemon(owner)) DEMONS.put(owner.getId(), new Demon(owner, standId));
    }

    private static Entity resolveAttunedEntity(ArmorStand stand) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return null;
        Entity exact = mc.level.getEntity(stand.getId() - 1);
        if (isInfernoEntity(exact) && exact.distanceToSqr(stand) <= 9) return exact;
        var candidates = mc.level.getEntitiesOfClass(LivingEntity.class, stand.getBoundingBox().inflate(1, 3, 1), SlayerSpecialInfo::isInfernoEntity);
        return candidates.size() == 1 ? candidates.getFirst() : null;
    }

    private static boolean isInfernoEntity(Entity entity) {
        return entity instanceof Blaze || entity instanceof ZombifiedPiglin || entity instanceof WitherSkeleton;
    }

    private static boolean isDemon(Entity entity) {
        return entity instanceof ZombifiedPiglin || entity instanceof WitherSkeleton;
    }

    public static String hudLine() {
        if (!active() || !cfg.blazeHelper || !cfg.infernoAttunementDisplay) return "";
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return "";
        for (SlayerState.Boss boss : SlayerState.bosses()) {
            if (boss.type() != SlayerState.Type.BLAZE || !boss.owner().equalsIgnoreCase(mc.player.getName().getString())) continue;
            Attunement value = ATTUNEMENTS.get(boss.entity().getId());
            if (value != null) return formatAttunement(cfg.infernoAttunementStyle, value, cfg.infernoAttunementCount);
        }
        return "";
    }

    public static String timerLine(SlayerState.Boss boss, String fallback) {
        if (cfg == null) return fallback;
        if (boss.type() == SlayerState.Type.BLAZE && cfg.blazeHelper) {
            Attunement value = ATTUNEMENTS.get(boss.entity().getId());
            if (value == null) return fallback;
            String template = cfg.infernoSlayerInfoStyle == null ? "" : cfg.infernoSlayerInfoStyle;
            return style(template.replace("{attunement}", colored(value.name())).replace("{count}", Integer.toString(value.count()))
                .replace("{time}", value.time()));
        }
        if (boss.type() == SlayerState.Type.VOID && cfg.endermanHelper && cfg.voidgloomLaserInfo
            && boss.entity().getVehicle() instanceof Guardian guardian) {
            double seconds = Math.max(0, 8.2 - guardian.tickCount * 0.05);
            String time = seconds <= 0 && cfg.voidgloomLaserSoonText ? "Soon" : String.format(Locale.ROOT, "%.1fs", seconds);
            String template = cfg.voidgloomLaserStyle == null ? "" : cfg.voidgloomLaserStyle;
            return style(template.replace("{laser}", fallback).replace("{time}", time));
        }
        return fallback;
    }

    public static int[] colors(int entityId, int fallbackOutline, int fallbackFill) {
        if (cfg == null || !cfg.blazeHelper) return new int[]{fallbackOutline, fallbackFill};
        Attunement value = ATTUNEMENTS.get(entityId);
        if (value == null) return new int[]{fallbackOutline, fallbackFill};
        return switch (value.name()) {
            case "ASHEN" -> new int[]{cfg.infernoAshenOutlineColor, cfg.infernoAshenFillColor};
            case "SPIRIT" -> new int[]{cfg.infernoSpiritOutlineColor, cfg.infernoSpiritFillColor};
            case "AURIC" -> new int[]{cfg.infernoAuricOutlineColor, cfg.infernoAuricFillColor};
            case "CRYSTAL" -> new int[]{cfg.infernoCrystalOutlineColor, cfg.infernoCrystalFillColor};
            case "IMMUNE" -> new int[]{cfg.infernoImmuneOutlineColor, cfg.infernoImmuneFillColor};
            default -> new int[]{fallbackOutline, fallbackFill};
        };
    }

    public static void draw(WorldRenderer.Ctx ctx) {
        if (!active() || !cfg.blazeHelper || !cfg.infernoDemonHighlight) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        double range2 = Math.pow(Math.clamp(cfg.slayerHighlightRange, 8, 256), 2);
        for (Demon demon : new ArrayList<>(DEMONS.values())) {
            Entity entity = demon.entity();
            if (!entity.isAlive() || entity.distanceToSqr(mc.player) > range2) continue;
            int[] colors = colors(entity.getId(), cfg.slayerBossHighlightOutlineColor, cfg.slayerBossHighlightFillColor);
            if (cfg.slayerBossHighlightFill) ctx.box(entity.getBoundingBox().inflate(0.03), colors[1], cfg.slayerBossHighlightThroughWalls);
            if (cfg.slayerBossHighlightOutline) ctx.outline(entity.getBoundingBox().inflate(0.03), colors[0],
                cfg.slayerBossHighlightThroughWalls, Math.clamp(cfg.slayerBossHighlightWidth, 0.1f, 10f));
            if (cfg.infernoDemonLabels) {
                Attunement value = ATTUNEMENTS.get(entity.getId());
                if (value != null) ctx.label(entity.position().add(0, entity.getBbHeight() + 0.25, 0), value.name(),
                    colors[0], cfg.slayerBossHighlightThroughWalls);
            }
        }
    }

    public static void registerCommands(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        dispatcher.register(LiteralArgumentBuilder.<FabricClientCommandSource>literal("slayerspecial")
            .executes(c -> status())
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("status").executes(c -> status()))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("style")
                .then(RequiredArgumentBuilder.<FabricClientCommandSource, String>argument("target", StringArgumentType.word())
                    .then(RequiredArgumentBuilder.<FabricClientCommandSource, String>argument("template", StringArgumentType.greedyString())
                        .executes(c -> setStyle(StringArgumentType.getString(c, "target"), StringArgumentType.getString(c, "template"))))))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("color")
                .then(RequiredArgumentBuilder.<FabricClientCommandSource, String>argument("target", StringArgumentType.word())
                    .then(RequiredArgumentBuilder.<FabricClientCommandSource, String>argument("argb", StringArgumentType.word())
                        .executes(c -> color(StringArgumentType.getString(c, "target"), StringArgumentType.getString(c, "argb"))))))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("option")
                .then(RequiredArgumentBuilder.<FabricClientCommandSource, String>argument("name", StringArgumentType.word())
                    .then(RequiredArgumentBuilder.<FabricClientCommandSource, String>argument("value", StringArgumentType.word())
                        .executes(c -> option(StringArgumentType.getString(c, "name"), StringArgumentType.getString(c, "value")))))));
    }

    private static int status() {
        local("Blaze helper " + on(cfg.blazeHelper) + ", attunement display " + on(cfg.infernoAttunementDisplay)
            + ", boss colors " + on(cfg.infernoAttunementBossColors) + ", demons " + on(cfg.infernoDemonHighlight)
            + ", Enderman helper " + on(cfg.endermanHelper) + ", laser info " + on(cfg.voidgloomLaserInfo) + ".");
        return 1;
    }

    private static int setStyle(String target, String raw) {
        String value = raw.replace('\n', ' ').replace('\r', ' ').trim();
        if (value.isEmpty() || value.length() > 200) { local("Style must be 1-200 characters."); return 0; }
        switch (target.toLowerCase(Locale.ROOT)) {
            case "hud" -> cfg.infernoAttunementStyle = value;
            case "inferno" -> cfg.infernoSlayerInfoStyle = value;
            case "laser" -> cfg.voidgloomLaserStyle = value;
            default -> { local("Target must be hud, inferno, or laser."); return 0; }
        }
        save("Special Slayer style updated."); return 1;
    }

    private static int color(String target, String raw) {
        Integer value = parseColor(raw);
        if (value == null) { local("Color must be RRGGBB or AARRGGBB hex."); return 0; }
        String key = target.toLowerCase(Locale.ROOT);
        boolean fill = key.endsWith("fill");
        String name = fill ? key.substring(0, key.length() - 4) : key.replace("outline", "");
        switch (name) {
            case "ashen" -> { if (fill) cfg.infernoAshenFillColor = value; else cfg.infernoAshenOutlineColor = value; }
            case "spirit" -> { if (fill) cfg.infernoSpiritFillColor = value; else cfg.infernoSpiritOutlineColor = value; }
            case "auric" -> { if (fill) cfg.infernoAuricFillColor = value; else cfg.infernoAuricOutlineColor = value; }
            case "crystal" -> { if (fill) cfg.infernoCrystalFillColor = value; else cfg.infernoCrystalOutlineColor = value; }
            case "immune" -> { if (fill) cfg.infernoImmuneFillColor = value; else cfg.infernoImmuneOutlineColor = value; }
            default -> { local("Use ashen, spirit, auric, crystal, or immune, optionally ending in fill."); return 0; }
        }
        save("Attunement color updated."); return 1;
    }

    private static int option(String name, String raw) {
        Boolean value = bool(raw);
        if (value == null) { local("Value must be on or off."); return 0; }
        switch (name.toLowerCase(Locale.ROOT)) {
            case "hud" -> cfg.infernoAttunementDisplay = value;
            case "blaze" -> cfg.blazeHelper = value;
            case "count" -> cfg.infernoAttunementCount = value;
            case "textcolor" -> cfg.infernoAttunementColorText = value;
            case "bosscolors" -> cfg.infernoAttunementBossColors = value;
            case "demons" -> cfg.infernoDemonHighlight = value;
            case "demonlabels" -> cfg.infernoDemonLabels = value;
            case "laser" -> cfg.voidgloomLaserInfo = value;
            case "enderman" -> cfg.endermanHelper = value;
            case "soon" -> cfg.voidgloomLaserSoonText = value;
            default -> { local("Unknown special Slayer option."); return 0; }
        }
        save("Special Slayer option updated."); return 1;
    }

    private static String formatAttunement(String template, Attunement value, boolean count) {
        String raw = template == null ? "" : template;
        return style(raw.replace("{attunement}", colored(value.name())).replace("{count}", Integer.toString(value.count()))
            .replace("{count-section}", count ? " x" + value.count() : "").replace("{time}", value.time()));
    }

    private static String colored(String name) {
        if (cfg == null || !cfg.infernoAttunementColorText) return name;
        return switch (name) { case "ASHEN" -> "§8" + name; case "AURIC" -> "§e" + name; case "CRYSTAL" -> "§b" + name; case "SPIRIT" -> "§f" + name; case "IMMUNE" -> "§c" + name; default -> name; };
    }

    private static Integer parseColor(String raw) {
        try { String value = raw.replace("#", ""); long parsed = Long.parseUnsignedLong(value, 16); if (value.length() == 6) parsed |= 0xFF000000L; return value.length() == 6 || value.length() == 8 ? (int) parsed : null; }
        catch (NumberFormatException ignored) { return null; }
    }

    private static Boolean bool(String raw) { return switch (raw.toLowerCase(Locale.ROOT)) { case "on", "true", "yes", "1" -> true; case "off", "false", "no", "0" -> false; default -> null; }; }
    private static boolean infernoQuest() {
        for (String line : ConstellationClient.loc().getSidebarLines()) {
            String value = ChatFormatting.stripFormatting(line);
            if ((value == null ? line : value).contains("Inferno Demonlord")) return true;
        }
        return false;
    }
    private static void reset() { ATTUNEMENTS.clear(); DEMONS.clear(); lastTick = Long.MIN_VALUE; }
    private static boolean active() { return cfg != null && cfg.enabled && ConstellationClient.loc().onHypixel(); }
    private static String plain(Entity entity) { String value = ChatFormatting.stripFormatting(entity.getName().getString()); return value == null ? entity.getName().getString() : value; }
    private static String style(String text) { return text.replace("<dark_red>", "§4").replace("<dark_gray>", "§8").replace("<gray>", "§7").replace("<red>", "§c").replace("<green>", "§a").replace("<yellow>", "§e").replace("<aqua>", "§b").replace("<blue>", "§9").replace("<gold>", "§6").replace("<white>", "§f").replace("<bold>", "§l").replace("<r>", "§r").replace('&', '§'); }
    private static String on(boolean value) { return value ? "on" : "off"; }
    private static void save(String text) { ConstellationClient.saveConfig(); local(text); }
    private static void local(String text) { Minecraft mc = Minecraft.getInstance(); if (mc.player != null) mc.player.sendSystemMessage(Component.literal("§5Slayer §8> §f" + text)); }
}
