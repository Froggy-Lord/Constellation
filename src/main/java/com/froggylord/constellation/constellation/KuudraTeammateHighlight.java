package com.froggylord.constellation.constellation;

import com.froggylord.constellation.ConstellationClient;
import com.froggylord.constellation.config.DracoConfig;
import com.froggylord.constellation.render.WorldRenderer;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.Locale;

public final class KuudraTeammateHighlight {
    private KuudraTeammateHighlight() {}

    // ported from Athen (BSD-3-Clause): modules/impl/kuudra/TeammateHighlight.kt
    // interpolated box ported from Athen (BSD-3-Clause): utils/render/RenderUtils.kt
    public static void draw(WorldRenderer.Ctx ctx) {
        DracoConfig cfg = config();
        if (cfg == null || !cfg.enabled || !cfg.kuudraTeammateHighlight || !KuudraState.inRun()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;
        Vec3 camera = mc.player.position();
        double maxDistanceSq = Math.clamp(cfg.kuudraTeammateRange, 16, 512);
        maxDistanceSq *= maxDistanceSq;
        float partial = mc.getDeltaTracker().getGameTimeDeltaPartialTick(true);

        for (Player player : mc.level.players()) {
            if (player == mc.player || !player.isAlive() || player.isSpectator()) continue;
            if (player.getUUID().version() != 4 || player.distanceToSqr(camera) > maxDistanceSq) continue;
            double x = player.xo + (player.getX() - player.xo) * partial;
            double y = player.yo + (player.getY() - player.yo) * partial;
            double z = player.zo + (player.getZ() - player.zo) * partial;
            AABB box = player.getBoundingBox().move(x - player.getX(), y - player.getY(), z - player.getZ());
            boolean throughWalls = cfg.kuudraTeammateThroughWalls;
            if (cfg.kuudraTeammateFill) ctx.box(box, cfg.kuudraTeammateFillColour, throughWalls);
            if (cfg.kuudraTeammateOutline)
                ctx.outline(box, cfg.kuudraTeammateColour, throughWalls, cfg.kuudraTeammateLineWidth);
            if (cfg.kuudraTeammateLabel)
                ctx.label(new Vec3(x, box.maxY + 0.35, z), player.getGameProfile().name(),
                    cfg.kuudraTeammateColour, throughWalls);
            if (cfg.kuudraTeammateBeam)
                ctx.beam(x, box.maxY, z, cfg.kuudraTeammateColour, 10, throughWalls);
        }
    }

    public static void registerCommands(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        dispatcher.register(LiteralArgumentBuilder.<FabricClientCommandSource>literal("kuudrateammates")
            .executes(context -> status())
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("status").executes(context -> status()))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("width")
                .then(RequiredArgumentBuilder.<FabricClientCommandSource, Float>argument("width", FloatArgumentType.floatArg(0.1f, 10f))
                    .executes(context -> width(FloatArgumentType.getFloat(context, "width")))))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("range")
                .then(RequiredArgumentBuilder.<FabricClientCommandSource, Integer>argument("blocks", IntegerArgumentType.integer(16, 512))
                    .executes(context -> range(IntegerArgumentType.getInteger(context, "blocks")))))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("color")
                .then(RequiredArgumentBuilder.<FabricClientCommandSource, String>argument("type", StringArgumentType.word())
                    .then(RequiredArgumentBuilder.<FabricClientCommandSource, String>argument("argb", StringArgumentType.word())
                        .executes(context -> color(StringArgumentType.getString(context, "type"),
                            StringArgumentType.getString(context, "argb"))))))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("option")
                .then(RequiredArgumentBuilder.<FabricClientCommandSource, String>argument("name", StringArgumentType.word())
                    .then(RequiredArgumentBuilder.<FabricClientCommandSource, Boolean>argument("enabled", BoolArgumentType.bool())
                        .executes(context -> option(StringArgumentType.getString(context, "name"),
                            BoolArgumentType.getBool(context, "enabled")))))));
    }

    private static int status() {
        DracoConfig cfg = config();
        local("Kuudra teammate highlight " + on(cfg.kuudraTeammateHighlight) + "; outline/fill "
            + on(cfg.kuudraTeammateOutline) + "/" + on(cfg.kuudraTeammateFill)
            + "; width " + cfg.kuudraTeammateLineWidth + "; range " + cfg.kuudraTeammateRange + ".");
        local("Labels/beams/through walls " + on(cfg.kuudraTeammateLabel) + "/"
            + on(cfg.kuudraTeammateBeam) + "/" + on(cfg.kuudraTeammateThroughWalls) + ".");
        return 1;
    }

    private static int width(float value) {
        config().kuudraTeammateLineWidth = Math.clamp(value, 0.1f, 10f);
        save();
        local("Kuudra teammate line width updated.");
        return 1;
    }

    private static int range(int value) {
        config().kuudraTeammateRange = Math.clamp(value, 16, 512);
        save();
        local("Kuudra teammate range set to " + value + " blocks.");
        return 1;
    }

    private static int color(String type, String value) {
        Integer colour = parseColour(value);
        if (colour == null) { local("Invalid color. Use RRGGBB or AARRGGBB."); return 0; }
        if (type.equalsIgnoreCase("outline") || type.equalsIgnoreCase("line")) config().kuudraTeammateColour = colour;
        else if (type.equalsIgnoreCase("fill")) config().kuudraTeammateFillColour = colour;
        else { local("Color type must be outline or fill."); return 0; }
        save();
        local("Kuudra teammate " + type + " color updated.");
        return 1;
    }

    private static int option(String name, boolean enabled) {
        DracoConfig cfg = config();
        switch (name.toLowerCase(Locale.ROOT)) {
            case "enabled", "master" -> cfg.kuudraTeammateHighlight = enabled;
            case "outline", "box" -> cfg.kuudraTeammateOutline = enabled;
            case "fill" -> cfg.kuudraTeammateFill = enabled;
            case "label", "name" -> cfg.kuudraTeammateLabel = enabled;
            case "beam" -> cfg.kuudraTeammateBeam = enabled;
            case "walls", "throughwalls" -> cfg.kuudraTeammateThroughWalls = enabled;
            default -> { local("Option must be enabled, outline, fill, label, beam, or walls."); return 0; }
        }
        save();
        local("Kuudra teammate " + name + " set to " + enabled + ".");
        return 1;
    }

    private static Integer parseColour(String value) {
        try {
            String clean = value.startsWith("#") ? value.substring(1) : value;
            if (!clean.matches("[0-9a-fA-F]{6}|[0-9a-fA-F]{8}")) return null;
            long parsed = Long.parseUnsignedLong(clean, 16);
            return (int) (clean.length() == 6 ? parsed | 0xFF000000L : parsed);
        } catch (RuntimeException ignored) { return null; }
    }

    private static DracoConfig config() {
        return ConstellationClient.cfg() == null ? null : ConstellationClient.cfg().draco;
    }
    private static String on(boolean value) { return value ? "on" : "off"; }
    private static void save() { ConstellationClient.saveConfig(); }
    private static void local(String text) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) mc.player.sendSystemMessage(Component.literal(text));
    }
}
