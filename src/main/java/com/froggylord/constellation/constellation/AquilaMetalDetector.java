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
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// ported from Skyblocker (LGPL-3.0-or-later): skyblock/dwarven/MetalDetector.java
// found-time behavior ported from SkyHanni (LGPL-3.0-or-later):
// features/mining/crystalhollows/MetalDetectorSolver.kt
public final class AquilaMetalDetector {
    private static final Pattern TREASURE = Pattern.compile("TREASURE:\\s*(\\d+(?:\\.\\d+)?)m");
    private static final Pattern KEEPER = Pattern.compile("Keeper of (\\w+)");
    private static final Map<String, BlockPos> KEEPER_OFFSETS = Map.of(
        "Diamond", new BlockPos(33, 0, 3), "Lapis", new BlockPos(-33, 0, -3),
        "Emerald", new BlockPos(-3, 0, 33), "Gold", new BlockPos(3, 0, -33)
    );
    private static final Set<BlockPos> CHEST_OFFSETS = Set.of(
        new BlockPos(-38,-22,26),new BlockPos(38,-22,-26),new BlockPos(-40,-22,18),
        new BlockPos(-41,-20,22),new BlockPos(-5,-21,16),new BlockPos(40,-22,-30),
        new BlockPos(-42,-20,-28),new BlockPos(-43,-22,-40),new BlockPos(42,-19,-41),
        new BlockPos(43,-21,-16),new BlockPos(-1,-22,-20),new BlockPos(6,-21,28),
        new BlockPos(7,-21,11),new BlockPos(7,-21,22),new BlockPos(-12,-21,-44),
        new BlockPos(12,-22,31),new BlockPos(12,-22,-22),new BlockPos(12,-21,7),
        new BlockPos(12,-21,-43),new BlockPos(-14,-21,43),new BlockPos(-14,-21,22),
        new BlockPos(-17,-21,20),new BlockPos(-20,-22,0),new BlockPos(1,-21,20),
        new BlockPos(19,-22,29),new BlockPos(20,-22,0),new BlockPos(20,-21,-26),
        new BlockPos(-23,-22,40),new BlockPos(22,-21,-14),new BlockPos(-24,-22,12),
        new BlockPos(23,-22,26),new BlockPos(23,-22,-39),new BlockPos(24,-22,27),
        new BlockPos(25,-22,17),new BlockPos(29,-21,-44),new BlockPos(-31,-21,-12),
        new BlockPos(-31,-21,-40),new BlockPos(30,-21,-25),new BlockPos(-32,-21,-40),
        new BlockPos(-36,-20,42),new BlockPos(-37,-21,-14),new BlockPos(-37,-21,-22)
    );

    private static AquilaConfig cfg;
    private static boolean initialized;
    private static BlockPos center;
    private static List<BlockPos> possible = new ArrayList<>();
    private static boolean newTreasure = true;
    private static boolean started;
    private static double previousDistance = -1;
    private static Vec3 previousPosition;
    private static int stableSamples;
    private static long searchStartedAt;
    private static long lastCenterScan;
    private static boolean wasActive;

    private AquilaMetalDetector() {}

    public static void init(AquilaConfig config) {
        cfg = config;
        if (initialized) return;
        initialized = true;
        ClientReceiveMessageEvents.ALLOW_GAME.register((message, overlay) -> {
            onMessage(message, overlay);
            return true;
        });
        ConstellationClient.tick().every(20, "aquila-metal-detector-scope", AquilaMetalDetector::scope);
        ClientPlayConnectionEvents.JOIN.register((a, b, c) -> reset());
        ClientPlayConnectionEvents.DISCONNECT.register((a, b) -> reset());
    }

    private static boolean active() {
        return cfg != null && cfg.enabled && cfg.metalDetectorHelper && cfg.metalDetectorSuite
            && ConstellationClient.loc().area() == SkyblockArea.CRYSTAL_HOLLOWS;
    }

    private static void scope() {
        boolean now = active();
        if (wasActive && !now) reset();
        wasActive = now;
    }

    private static void onMessage(Component component, boolean overlay) {
        if (!active()) return;
        String text = clean(component.getString());
        if (!overlay) {
            if (text.startsWith("You found ") && text.contains("with your Metal Detector")) found();
            return;
        }
        Matcher matcher = TREASURE.matcher(text);
        if (!matcher.find()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;
        double distance;
        try { distance = Double.parseDouble(matcher.group(1)); }
        catch (NumberFormatException ignored) { return; }
        if (!started) {
            started = true;
            searchStartedAt = System.currentTimeMillis();
            if (cfg.metalDetectorStartTip) local("Stand still briefly while the treasure distance repeats.");
        }
        if (center == null && System.currentTimeMillis() - lastCenterScan >= 1000) findCenter(mc);
        Vec3 player = mc.player.position();
        if (Math.abs(distance - previousDistance) < .0001 && player.equals(previousPosition)) stableSamples++;
        else stableSamples = 1;
        previousDistance = distance;
        previousPosition = player;
        if (stableSamples < Math.clamp(cfg.metalDetectorStableSamples, 1, 5)) return;
        int before = possible.size();
        update(distance, player);
        if (possible.size() != before) changed(before);
    }

    private static void update(double distance, Vec3 player) {
        double tolerance = Math.clamp(cfg.metalDetectorToleranceHundredths, 5, 100) / 100.0;
        if (newTreasure) {
            possible = new ArrayList<>();
            newTreasure = false;
            if (center != null) {
                for (BlockPos offset : CHEST_OFFSETS) {
                    BlockPos target = center.offset(offset).above();
                    if (Math.abs(player.distanceTo(Vec3.atLowerCornerOf(target)) - distance) < tolerance) possible.add(target);
                }
            } else {
                int radius = Math.clamp((int) Math.ceil(distance), 1, 128);
                for (int x = -radius; x <= radius; x++) for (int z = -radius; z <= radius; z++) {
                    BlockPos target = BlockPos.containing(player.x + x, player.y, player.z + z);
                    if (Math.abs(player.distanceTo(Vec3.atLowerCornerOf(target)) - distance) < tolerance) possible.add(target);
                }
            }
        } else {
            possible.removeIf(target -> Math.abs(player.distanceTo(Vec3.atLowerCornerOf(target)) - distance) >= tolerance);
        }
        if (possible.isEmpty()) {
            newTreasure = true;
            if (cfg.metalDetectorCountChat) local("No matching locations. Move, then stand still for another reading.");
        }
    }

    private static void findCenter(Minecraft mc) {
        lastCenterScan = System.currentTimeMillis();
        AABB box = mc.player.getBoundingBox().inflate(500);
        for (ArmorStand stand : mc.level.getEntitiesOfClass(ArmorStand.class, box, ArmorStand::hasCustomName)) {
            Matcher matcher = KEEPER.matcher(clean(stand.getName().getString()));
            if (!matcher.matches()) continue;
            BlockPos offset = KEEPER_OFFSETS.get(matcher.group(1));
            if (offset == null) continue;
            center = stand.blockPosition().offset(offset);
            if (cfg.metalDetectorCountChat) local("Mines of Divan center found.");
            return;
        }
    }

    private static void changed(int before) {
        if (possible.size() == 1 && before != 1) {
            if (cfg.metalDetectorCountChat) local("Treasure location found.");
            alert();
        } else if (cfg.metalDetectorCountChat) local(possible.size() + " possible treasure locations.");
    }

    private static void alert() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        if (cfg.metalDetectorFoundSound) mc.player.playSound(SoundEvents.NOTE_BLOCK_PLING.value(), .8f, 1.4f);
        if (cfg.metalDetectorFoundTitle) {
            mc.gui.hud.resetTitleTimes();
            mc.gui.hud.setTitle(Component.literal("Treasure found").withColor(cfg.metalDetectorFoundColor & 0xFFFFFF));
        }
    }

    private static void found() {
        long elapsed = searchStartedAt == 0 ? 0 : System.currentTimeMillis() - searchStartedAt;
        if (cfg.metalDetectorFoundChat) {
            String message = "Metal Detector treasure collected.";
            if (cfg.metalDetectorShowTime && elapsed > 0) message += " Search time: " + elapsed / 1000 + "s.";
            local(message);
        }
        possible.clear();
        newTreasure = true;
        started = false;
        stableSamples = 0;
        previousDistance = -1;
        previousPosition = null;
        searchStartedAt = 0;
    }

    public static void draw(WorldRenderer.Ctx ctx) {
        if (!active() || possible.isEmpty() || possible.size() > Math.clamp(cfg.metalDetectorMaximumWaypoints, 1, 32)) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        boolean exact = possible.size() == 1;
        for (BlockPos reading : possible) {
            BlockPos target = exact ? reading.below() : reading;
            Vec3 center = Vec3.atCenterOf(target);
            int color = exact ? cfg.metalDetectorFoundColor : cfg.metalDetectorPossibleColor;
            if (cfg.metalDetectorBox) ctx.highlight(new AABB(target), color, cfg.metalDetectorThroughWalls);
            if (cfg.metalDetectorBeam) ctx.beam(center.x, target.getY(), center.z, color,
                Math.clamp(cfg.metalDetectorBeamHeight, 2, 100), cfg.metalDetectorThroughWalls);
            if (cfg.metalDetectorLine && exact) ctx.line(mc.player.position().add(0, 1, 0), center, color, false);
            if (cfg.metalDetectorLabel) {
                String label = exact ? "Treasure" : "Possible";
                if (cfg.metalDetectorDistance) label += " " + String.format(Locale.ROOT, "%.1fm", mc.player.position().distanceTo(center));
                ctx.label(center.add(0, 1.25, 0), label, color, cfg.metalDetectorThroughWalls);
            }
        }
    }

    public static void registerCommands(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        dispatcher.register(LiteralArgumentBuilder.<FabricClientCommandSource>literal("metaldetector")
            .executes(context -> status())
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("reset").executes(context -> { resetSearch(); return status(); }))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("samples")
                .then(RequiredArgumentBuilder.<FabricClientCommandSource, Integer>argument("count", IntegerArgumentType.integer(1, 5))
                    .executes(context -> { cfg.metalDetectorStableSamples = IntegerArgumentType.getInteger(context, "count"); save(); return status(); })))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("maximum")
                .then(RequiredArgumentBuilder.<FabricClientCommandSource, Integer>argument("count", IntegerArgumentType.integer(1, 32))
                    .executes(context -> { cfg.metalDetectorMaximumWaypoints = IntegerArgumentType.getInteger(context, "count"); save(); return status(); })))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("tolerance")
                .then(RequiredArgumentBuilder.<FabricClientCommandSource, Integer>argument("hundredths", IntegerArgumentType.integer(5, 100))
                    .executes(context -> { cfg.metalDetectorToleranceHundredths = IntegerArgumentType.getInteger(context, "hundredths"); save(); resetSearch(); return status(); })))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("color")
                .then(RequiredArgumentBuilder.<FabricClientCommandSource, String>argument("target", StringArgumentType.word())
                    .then(RequiredArgumentBuilder.<FabricClientCommandSource, String>argument("argb", StringArgumentType.word())
                        .executes(context -> color(StringArgumentType.getString(context, "target"), StringArgumentType.getString(context, "argb"))))))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("option")
                .then(RequiredArgumentBuilder.<FabricClientCommandSource, String>argument("name", StringArgumentType.word())
                    .then(RequiredArgumentBuilder.<FabricClientCommandSource, String>argument("state", StringArgumentType.word())
                        .executes(context -> option(StringArgumentType.getString(context, "name"), StringArgumentType.getString(context, "state")))))));
    }

    private static int status() {
        local("Solver " + on(cfg.metalDetectorSuite) + ", center " + (center == null ? "unknown" : center.toShortString())
            + ", " + possible.size() + " possible locations.");
        return 1;
    }

    private static int option(String name, String raw) {
        Boolean value = parse(raw);
        if (value == null) { local("State must be on or off."); return 0; }
        switch (name.toLowerCase(Locale.ROOT)) {
            case "enabled" -> cfg.metalDetectorSuite = value;
            case "box" -> cfg.metalDetectorBox = value;
            case "beam" -> cfg.metalDetectorBeam = value;
            case "line" -> cfg.metalDetectorLine = value;
            case "label" -> cfg.metalDetectorLabel = value;
            case "distance" -> cfg.metalDetectorDistance = value;
            case "throughwalls" -> cfg.metalDetectorThroughWalls = value;
            case "tip" -> cfg.metalDetectorStartTip = value;
            case "countchat" -> cfg.metalDetectorCountChat = value;
            case "foundchat" -> cfg.metalDetectorFoundChat = value;
            case "title" -> cfg.metalDetectorFoundTitle = value;
            case "sound" -> cfg.metalDetectorFoundSound = value;
            case "time" -> cfg.metalDetectorShowTime = value;
            default -> { local("Option must be enabled, box, beam, line, label, distance, throughwalls, tip, countchat, foundchat, title, sound, or time."); return 0; }
        }
        save();
        return status();
    }

    private static int color(String target, String raw) {
        Integer color = parseColor(raw);
        if (color == null) { local("Color must be an eight-digit ARGB hex value."); return 0; }
        if (target.equalsIgnoreCase("possible")) cfg.metalDetectorPossibleColor = color;
        else if (target.equalsIgnoreCase("found")) cfg.metalDetectorFoundColor = color;
        else { local("Color target must be possible or found."); return 0; }
        save();
        return status();
    }

    private static void resetSearch() {
        possible.clear();
        newTreasure = true;
        started = false;
        stableSamples = 0;
        previousDistance = -1;
        previousPosition = null;
        searchStartedAt = 0;
    }

    private static void reset() {
        center = null;
        lastCenterScan = 0;
        resetSearch();
    }

    private static Integer parseColor(String raw) {
        try {
            String value = raw.startsWith("#") ? raw.substring(1) : raw;
            if (value.length() != 8) return null;
            return (int) Long.parseLong(value, 16);
        } catch (NumberFormatException ignored) { return null; }
    }
    private static void save() { ConstellationClient.saveConfig(); }
    private static void local(String text) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) mc.player.sendSystemMessage(Component.literal("§3[Metal Detector] §f" + text));
    }
    private static String clean(String text) { return text.replaceAll("§[0-9A-FK-ORa-fk-or]", "").trim(); }
    private static Boolean parse(String raw) {
        return switch (raw.toLowerCase(Locale.ROOT)) {
            case "on", "true", "yes", "1" -> true;
            case "off", "false", "no", "0" -> false;
            default -> null;
        };
    }
    private static String on(boolean value) { return value ? "on" : "off"; }
}
