package com.froggylord.constellation.constellation;

import com.froggylord.constellation.ConstellationClient;
import com.froggylord.constellation.config.OrionConfig;
import com.froggylord.constellation.render.WorldRenderer;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.phys.AABB;

import java.util.Locale;

// ported from Devonian (GPL-3.0): features/dungeons/f7/WitherHighlight.kt
// entity signatures and phase exclusion cross-checked with NoammAddons (CC0-1.0):
// features/impl/floor7/WitherESP.kt
public final class WitherHighlight {
    private WitherHighlight() {}

    public static void draw(WorldRenderer.Ctx ctx) {
        OrionConfig cfg = ConstellationClient.cfg().orion;
        if (cfg == null || !cfg.enabled || !cfg.witherHighlight || !allowedStage(cfg)) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;
        double range = Math.clamp(cfg.witherHighlightRange, 16, 512);
        double rangeSquared = range * range;

        for (var entity : mc.level.entitiesForRendering()) {
            if (!(entity instanceof WitherBoss wither)) continue;
            if (!wither.isAlive() || wither.distanceToSqr(mc.player) > rangeSquared) continue;
            if (cfg.witherHighlightExcludeArmorSummon
                && (wither.getMaxHealth() == 300.0f || wither.getInvulnerableTicks() == 800)) continue;
            if (cfg.witherHighlightHideInvisible && wither.isInvisible()) continue;

            AABB box = wither.getBoundingBox().inflate(0.03);
            if (cfg.witherHighlightFill) ctx.box(box, cfg.witherHighlightFillColour,
                cfg.witherHighlightThroughWalls);
            if (cfg.witherHighlightOutline) ctx.outline(box, cfg.witherHighlightWireColour,
                cfg.witherHighlightThroughWalls, (float) Math.clamp(cfg.witherHighlightLineWidth, 1, 10));
            if (cfg.witherHighlightBeam) ctx.beam(box.getCenter().x, box.maxY, box.getCenter().z,
                cfg.witherHighlightWireColour, 16, cfg.witherHighlightThroughWalls);
            if (cfg.witherHighlightLabel) {
                String name = phaseName();
                int health = wither.getMaxHealth() <= 0 ? 0
                    : Math.clamp(Math.round(wither.getHealth() * 100.0f / wither.getMaxHealth()), 0, 100);
                ctx.label(wither.position().add(0, wither.getBbHeight() + .55, 0), name + " " + health + "%",
                    cfg.witherHighlightWireColour, cfg.witherHighlightThroughWalls);
            }
        }
    }

    private static boolean allowedStage(OrionConfig cfg) {
        var dungeon = ConstellationClient.dungeon();
        String floor = dungeon.floor();
        if (!ConstellationClient.loc().inDungeons() || !dungeon.inBoss()
            || floor == null || !floor.endsWith("7")) return false;
        return switch (dungeon.bossPhase()) {
            case "Maxor" -> cfg.witherHighlightMaxor;
            case "Storm" -> cfg.witherHighlightStorm;
            case "Goldor" -> cfg.witherHighlightGoldor;
            case "Necron" -> cfg.witherHighlightNecron;
            case "Wither King" -> cfg.witherHighlightWitherKing;
            default -> false;
        };
    }

    private static String phaseName() {
        String phase = ConstellationClient.dungeon().bossPhase();
        return phase == null || phase.isBlank() ? "Wither" : phase;
    }

    public static void registerCommands(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        dispatcher.register(LiteralArgumentBuilder.<FabricClientCommandSource>literal("witherhighlight")
            .executes(ctx -> status())
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("status").executes(ctx -> status()))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("width")
                .then(RequiredArgumentBuilder.<FabricClientCommandSource, Integer>argument("pixels", IntegerArgumentType.integer(1, 10))
                    .executes(ctx -> width(IntegerArgumentType.getInteger(ctx, "pixels")))))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("range")
                .then(RequiredArgumentBuilder.<FabricClientCommandSource, Integer>argument("blocks", IntegerArgumentType.integer(16, 512))
                    .executes(ctx -> range(IntegerArgumentType.getInteger(ctx, "blocks")))))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("color")
                .then(RequiredArgumentBuilder.<FabricClientCommandSource, String>argument("part", StringArgumentType.word())
                    .then(RequiredArgumentBuilder.<FabricClientCommandSource, String>argument("argb", StringArgumentType.word())
                        .executes(ctx -> colour(StringArgumentType.getString(ctx, "part"),
                            StringArgumentType.getString(ctx, "argb")))))));
    }

    private static int status() {
        OrionConfig cfg = ConstellationClient.cfg().orion;
        message("Highlight " + (cfg.witherHighlight ? "on" : "off") + ", outline "
            + (cfg.witherHighlightOutline ? "on" : "off") + ", fill "
            + (cfg.witherHighlightFill ? "on" : "off") + ", width "
            + cfg.witherHighlightLineWidth + ", range " + cfg.witherHighlightRange + ".");
        return 1;
    }

    private static int width(int value) {
        ConstellationClient.cfg().orion.witherHighlightLineWidth = value;
        ConstellationClient.saveConfig();
        message("Outline width set to " + value + ".");
        return 1;
    }

    private static int range(int value) {
        ConstellationClient.cfg().orion.witherHighlightRange = value;
        ConstellationClient.saveConfig();
        message("Render range set to " + value + " blocks.");
        return 1;
    }

    private static int colour(String part, String value) {
        Integer parsed = parseColour(value);
        if (parsed == null) { message("Color must be RRGGBB or AARRGGBB."); return 0; }
        OrionConfig cfg = ConstellationClient.cfg().orion;
        if (part.equalsIgnoreCase("outline") || part.equalsIgnoreCase("wire")) {
            cfg.witherHighlightWireColour = parsed;
        } else if (part.equalsIgnoreCase("fill")) {
            cfg.witherHighlightFillColour = parsed;
        } else {
            message("Part must be outline or fill.");
            return 0;
        }
        ConstellationClient.saveConfig();
        message(title(part) + " color set to " + String.format("%08X", parsed) + ".");
        return 1;
    }

    private static Integer parseColour(String value) {
        try {
            String clean = value.startsWith("#") ? value.substring(1) : value;
            if (clean.length() == 6) clean = "FF" + clean;
            return clean.length() == 8 ? (int) Long.parseLong(clean, 16) : null;
        } catch (NumberFormatException ignored) { return null; }
    }

    private static String title(String value) {
        return value.substring(0, 1).toUpperCase(Locale.ROOT) + value.substring(1).toLowerCase(Locale.ROOT);
    }

    private static void message(String text) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) mc.player.sendSystemMessage(Component.literal("§aWither Highlight §8> §f" + text));
    }
}
