package com.froggylord.constellation.constellation;

import com.froggylord.constellation.ConstellationClient;
import com.froggylord.constellation.config.AquilaConfig;
import com.froggylord.constellation.core.LocationManager.SkyblockArea;
import com.froggylord.constellation.render.WorldRenderer;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import java.util.function.BooleanSupplier;
import java.util.function.IntSupplier;

// ported from SkyHanni (LGPL-3.0-or-later): features/mining/crystalhollows/NucleusBarriersBox.kt
// ported from SkyHanni (LGPL-3.0-or-later): config/features/mining/nucleus/CrystalHighlighterConfig.kt
// ported from SkyHanni (LGPL-3.0-or-later): config/features/mining/nucleus/CrystalHighlighterColorConfig.kt
// ported from SkyHanni (LGPL-3.0-or-later): features/event/hoppity/HoppityApi.kt
public final class AquilaNucleusBarriers {
    private record Crystal(String name, AABB box, BooleanSupplier enabled, IntSupplier color) {}

    private static final Pattern SPRING = Pattern.compile("^(?:Early |Late )?Spring \\d+(?:st|nd|rd|th)?(?:\\b.*)?$",
        Pattern.CASE_INSENSITIVE);
    private static AquilaConfig cfg;
    private static List<Crystal> crystals = List.of();

    private AquilaNucleusBarriers() {}

    public static void init(AquilaConfig config) {
        cfg = config;
        // SkyHanni expands each endpoint-aligned box by one full block on every face.
        crystals = List.of(
            crystal("Amber", 474, 124, 524, 485, 111, 535, () -> cfg.nucleusBarrierAmber, () -> cfg.nucleusBarrierAmberColor),
            crystal("Amethyst", 474, 124, 492, 485, 111, 503, () -> cfg.nucleusBarrierAmethyst, () -> cfg.nucleusBarrierAmethystColor),
            crystal("Topaz", 508, 124, 473, 519, 111, 484, () -> cfg.nucleusBarrierTopaz, () -> cfg.nucleusBarrierTopazColor),
            crystal("Jade", 542, 124, 492, 553, 111, 503, () -> cfg.nucleusBarrierJade, () -> cfg.nucleusBarrierJadeColor),
            crystal("Sapphire", 542, 124, 524, 553, 111, 535, () -> cfg.nucleusBarrierSapphire, () -> cfg.nucleusBarrierSapphireColor)
        );
    }

    private static Crystal crystal(String name, double x1, double y1, double z1, double x2, double y2, double z2,
                                   BooleanSupplier enabled, IntSupplier color) {
        AABB exact = new AABB(x1, y1, z1, x2, y2, z2).inflate(1);
        return new Crystal(name, exact, enabled, color);
    }

    public static void draw(WorldRenderer.Ctx ctx) {
        if (!active()) return;
        for (Crystal crystal : crystals) {
            if (!crystal.enabled().getAsBoolean()) continue;
            int color = crystal.color().getAsInt();
            if (cfg.nucleusBarrierFilled) {
                ctx.box(crystal.box(), color, cfg.nucleusBarrierThroughWalls);
            } else {
                ctx.outline(crystal.box(), 0xFF000000 | (color & 0xFFFFFF), cfg.nucleusBarrierThroughWalls,
                    Math.clamp(cfg.nucleusBarrierOutlineThickness, 1, 10));
            }
            if (cfg.nucleusBarrierLabels) {
                AABB box = crystal.box();
                ctx.label(new Vec3((box.minX + box.maxX) / 2, box.maxY + .5, (box.minZ + box.maxZ) / 2),
                    crystal.name() + " Barrier", 0xFF000000 | (color & 0xFFFFFF), cfg.nucleusBarrierThroughWalls);
            }
        }
    }

    private static boolean active() {
        Minecraft mc = Minecraft.getInstance();
        if (cfg == null || !cfg.enabled || !cfg.nucleusBarrierHighlighter || mc.player == null
            || ConstellationClient.loc().area() != SkyblockArea.CRYSTAL_HOLLOWS) return false;
        double x = mc.player.getX(), y = mc.player.getY(), z = mc.player.getZ();
        if (x < 450 || x > 575 || y < 60 || y > 160 || z < 450 || z > 575) return false;
        return !cfg.nucleusBarrierOnlyHoppity || ConstellationClient.loc().getSidebarLines().stream()
            .anyMatch(line -> SPRING.matcher(line.trim()).matches());
    }

    public static void registerCommands(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        dispatcher.register(LiteralArgumentBuilder.<FabricClientCommandSource>literal("nucleusbarriers")
            .executes(context -> status())
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("option")
                .then(RequiredArgumentBuilder.<FabricClientCommandSource, String>argument("name", StringArgumentType.word())
                    .then(RequiredArgumentBuilder.<FabricClientCommandSource, String>argument("state", StringArgumentType.word())
                        .executes(context -> option(StringArgumentType.getString(context, "name"), StringArgumentType.getString(context, "state"))))))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("style")
                .then(RequiredArgumentBuilder.<FabricClientCommandSource, String>argument("style", StringArgumentType.word())
                    .executes(context -> style(StringArgumentType.getString(context, "style")))))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("thickness")
                .then(RequiredArgumentBuilder.<FabricClientCommandSource, Integer>argument("width", IntegerArgumentType.integer(1, 10))
                    .executes(context -> {
                        cfg.nucleusBarrierOutlineThickness = IntegerArgumentType.getInteger(context, "width");
                        save();
                        return status();
                    })))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("color")
                .then(RequiredArgumentBuilder.<FabricClientCommandSource, String>argument("crystal", StringArgumentType.word())
                    .then(RequiredArgumentBuilder.<FabricClientCommandSource, String>argument("argb", StringArgumentType.word())
                        .executes(context -> color(StringArgumentType.getString(context, "crystal"),
                            StringArgumentType.getString(context, "argb")))))));
    }

    private static int status() {
        long shown = crystals.stream().filter(crystal -> crystal.enabled().getAsBoolean()).count();
        local((cfg.nucleusBarrierHighlighter ? "Enabled" : "Disabled") + ", "
            + (cfg.nucleusBarrierFilled ? "filled" : "outlined") + ", " + shown + " crystal barriers, labels "
            + on(cfg.nucleusBarrierLabels) + ", through walls " + on(cfg.nucleusBarrierThroughWalls)
            + ", Hoppity only " + on(cfg.nucleusBarrierOnlyHoppity) + ".");
        return 1;
    }

    private static int style(String raw) {
        if (raw.equalsIgnoreCase("filled")) cfg.nucleusBarrierFilled = true;
        else if (raw.equalsIgnoreCase("outline") || raw.equalsIgnoreCase("outlined")) cfg.nucleusBarrierFilled = false;
        else {
            local("Style must be filled or outline.");
            return 0;
        }
        save();
        return status();
    }

    private static int option(String raw, String state) {
        Boolean value = parse(state);
        if (value == null) {
            local("State must be on or off.");
            return 0;
        }
        switch (raw.toLowerCase(Locale.ROOT)) {
            case "enabled" -> cfg.nucleusBarrierHighlighter = value;
            case "labels" -> cfg.nucleusBarrierLabels = value;
            case "walls" -> cfg.nucleusBarrierThroughWalls = value;
            case "hoppity" -> cfg.nucleusBarrierOnlyHoppity = value;
            case "amber" -> cfg.nucleusBarrierAmber = value;
            case "amethyst" -> cfg.nucleusBarrierAmethyst = value;
            case "topaz" -> cfg.nucleusBarrierTopaz = value;
            case "jade" -> cfg.nucleusBarrierJade = value;
            case "sapphire" -> cfg.nucleusBarrierSapphire = value;
            default -> {
                local("Unknown option. Use enabled, labels, walls, hoppity, amber, amethyst, topaz, jade, or sapphire.");
                return 0;
            }
        }
        save();
        return status();
    }

    private static int color(String raw, String hex) {
        Integer color = parseColor(hex);
        if (color == null) {
            local("Color must be an eight-digit ARGB hex value.");
            return 0;
        }
        switch (raw.toLowerCase(Locale.ROOT)) {
            case "amber" -> cfg.nucleusBarrierAmberColor = color;
            case "amethyst" -> cfg.nucleusBarrierAmethystColor = color;
            case "topaz" -> cfg.nucleusBarrierTopazColor = color;
            case "jade" -> cfg.nucleusBarrierJadeColor = color;
            case "sapphire" -> cfg.nucleusBarrierSapphireColor = color;
            default -> {
                local("Crystal must be amber, amethyst, topaz, jade, or sapphire.");
                return 0;
            }
        }
        save();
        return status();
    }

    private static Boolean parse(String raw) {
        return switch (raw.toLowerCase(Locale.ROOT)) {
            case "on", "true", "yes", "1" -> true;
            case "off", "false", "no", "0" -> false;
            default -> null;
        };
    }

    private static Integer parseColor(String raw) {
        try {
            String clean = raw.startsWith("#") ? raw.substring(1) : raw;
            return clean.length() == 8 ? (int) Long.parseLong(clean, 16) : null;
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private static String on(boolean value) {
        return value ? "on" : "off";
    }

    private static void save() {
        ConstellationClient.saveConfig();
    }

    private static void local(String text) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) mc.player.sendSystemMessage(Component.literal("\u00a76[Nucleus Barriers] \u00a7f" + text));
    }
}
