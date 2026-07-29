package com.froggylord.constellation.constellation;

import com.froggylord.constellation.ConstellationClient;
import com.froggylord.constellation.config.HerculesConfig;
import com.froggylord.constellation.core.LocationManager;
import com.froggylord.constellation.render.WorldRenderer;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

// ported from SkyHanni (LGPL-3.0-or-later): features/garden/farming/GardenStartLocation.kt
// ported from SkyHanni (LGPL-3.0-or-later): config/features/garden/CropStartLocationConfig.kt
public final class HerculesCropLocations {
    private enum Mode { START, LAST, BOTH }

    private static HerculesConfig cfg;
    private static final Set<HerculesGardenTracker.Crop> lastArmed = new HashSet<>();
    private static Object levelIdentity;
    private static String profileKey = "";
    private static boolean dirty;
    private static long lastSave;

    private HerculesCropLocations() {}

    public static void init(HerculesConfig config) {
        cfg = config;
        maps();
        normalizeMode();
        HerculesGardenTracker.registerHarvestListener(HerculesCropLocations::onHarvest);
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> resetTransient());
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            if (dirty) save();
            resetTransient();
        });
        ConstellationClient.tick().every(10, "hercules-crop-locations", HerculesCropLocations::tick);
    }

    private static void onHarvest(HerculesGardenTracker.Harvest harvest) {
        if (!active()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        if (cfg.cropLocationPerProfile && !profileAvailable()) return;
        Integer plot = HerculesPests.plotAt(mc.player.getX(), mc.player.getZ());
        if (plot == null || plot == 0) return;
        HerculesGardenTracker.Crop held = HerculesGardenTracker.cropInHand(mc.player.getMainHandItem());
        if (held != harvest.crop()) return;
        String startKey = key(held);
        Vec3 player = blockPosition(mc.player.position());
        if (cfg.cropLocationAutoLearn && !cfg.cropStartLocations.containsKey(startKey)) {
            cfg.cropStartLocations.put(startKey, encode(player));
            if (cfg.cropLocationAutoChat) local("Saved the first " + held.display() + " start location.");
        }
        cfg.cropLastFarmedLocations.put(startKey, encode(player));
        lastArmed.remove(held);
        dirty = true;
    }

    private static void tick() {
        Minecraft mc = Minecraft.getInstance();
        String currentProfile = profile();
        if (mc.level != levelIdentity || !currentProfile.equals(profileKey)) {
            levelIdentity = mc.level;
            profileKey = currentProfile;
            lastArmed.clear();
        }
        if (dirty && System.currentTimeMillis() - lastSave >= 5000) save();
    }

    public static void draw(WorldRenderer.Ctx ctx) {
        if (!active()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        HerculesGardenTracker.Crop crop = HerculesGardenTracker.cropInHand(mc.player.getMainHandItem());
        if (crop == null) return;
        Mode mode = mode();
        if (mode != Mode.LAST) {
            Vec3 start = get(cfg.cropStartLocations, crop);
            if (start != null) render(ctx, start, crop, true, mode == Mode.BOTH);
        }
        if (mode != Mode.START) {
            Vec3 last = get(cfg.cropLastFarmedLocations, crop);
            if (last != null) {
                double minimum = Math.max(0, cfg.cropLocationLastMinDistance);
                if (mc.player.distanceToSqr(last) >= minimum * minimum) lastArmed.add(crop);
                if (lastArmed.contains(crop)) render(ctx, last, crop, false, mode == Mode.BOTH);
            }
        }
    }

    private static void render(WorldRenderer.Ctx ctx, Vec3 pos, HerculesGardenTracker.Crop crop,
                               boolean start, boolean both) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.player.distanceToSqr(pos) > Math.max(16, cfg.cropLocationRenderRange)
            * (double) Math.max(16, cfg.cropLocationRenderRange)) return;
        int color = start ? cfg.cropLocationStartColor : cfg.cropLocationLastColor;
        boolean throughWalls = start ? cfg.cropLocationStartThroughWalls : cfg.cropLocationThroughWalls;
        double size = Math.clamp(cfg.cropLocationBoxSizeTenths, 2, 40) / 10.0;
        AABB box = new AABB(pos.x - size / 2, pos.y, pos.z - size / 2,
            pos.x + size / 2, pos.y + Math.max(0.25, size), pos.z + size / 2);
        if (start ? cfg.cropLocationStartBox : cfg.cropLocationLastBox) ctx.highlight(box, color, throughWalls);
        if (start ? cfg.cropLocationStartBeam : cfg.cropLocationLastBeam) ctx.beam(pos.x, pos.y, pos.z, color,
            Math.clamp(cfg.cropLocationBeamHeight, 1, 64), throughWalls);
        if (cfg.cropLocationLine) ctx.line(mc.player.getEyePosition(), pos.add(0, .5, 0), color, throughWalls);
        if (cfg.cropLocationLabels) {
            String label = crop.display();
            if (both) label += start ? " Start" : " Last Farmed";
            if (cfg.cropLocationDistance) label += " " + Math.round(mc.player.position().distanceTo(pos)) + "m";
            ctx.label(pos.add(0, Math.max(1.2, size + .2), 0), label, color, throughWalls);
        }
    }

    public static void registerCommands(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        dispatcher.register(LiteralArgumentBuilder.<FabricClientCommandSource>literal("cropstart")
            .executes(context -> status())
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("set")
                .executes(context -> set(null))
                .then(RequiredArgumentBuilder.<FabricClientCommandSource, String>argument("crop", StringArgumentType.word())
                    .executes(context -> set(StringArgumentType.getString(context, "crop")))))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("clear")
                .executes(context -> clear(null, false))
                .then(RequiredArgumentBuilder.<FabricClientCommandSource, String>argument("crop", StringArgumentType.word())
                    .executes(context -> clear(StringArgumentType.getString(context, "crop"), false))))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("clearall")
                .executes(context -> clear(null, true)))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("clearstart")
                .executes(context -> clearType(null, true))
                .then(RequiredArgumentBuilder.<FabricClientCommandSource, String>argument("crop", StringArgumentType.word())
                    .executes(context -> clearType(StringArgumentType.getString(context, "crop"), true))))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("clearlast")
                .executes(context -> clearType(null, false))
                .then(RequiredArgumentBuilder.<FabricClientCommandSource, String>argument("crop", StringArgumentType.word())
                    .executes(context -> clearType(StringArgumentType.getString(context, "crop"), false))))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("clearprofiles")
                .executes(context -> clearProfiles()))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("mode")
                .then(RequiredArgumentBuilder.<FabricClientCommandSource, String>argument("mode", StringArgumentType.word())
                    .executes(context -> setMode(StringArgumentType.getString(context, "mode")))))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("option")
                .then(RequiredArgumentBuilder.<FabricClientCommandSource, String>argument("name", StringArgumentType.word())
                    .then(RequiredArgumentBuilder.<FabricClientCommandSource, String>argument("state", StringArgumentType.word())
                        .executes(context -> option(StringArgumentType.getString(context, "name"),
                            StringArgumentType.getString(context, "state"))))))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("color")
                .then(RequiredArgumentBuilder.<FabricClientCommandSource, String>argument("type", StringArgumentType.word())
                    .then(RequiredArgumentBuilder.<FabricClientCommandSource, String>argument("argb", StringArgumentType.word())
                        .executes(context -> color(StringArgumentType.getString(context, "type"),
                            StringArgumentType.getString(context, "argb"))))))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("distance")
                .then(RequiredArgumentBuilder.<FabricClientCommandSource, Integer>argument("blocks", IntegerArgumentType.integer(0, 128))
                    .executes(context -> {
                        cfg.cropLocationLastMinDistance = IntegerArgumentType.getInteger(context, "blocks");
                        save();
                        return status();
                    })))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("range")
                .then(RequiredArgumentBuilder.<FabricClientCommandSource, Integer>argument("blocks", IntegerArgumentType.integer(16, 2048))
                    .executes(context -> {
                        cfg.cropLocationRenderRange = IntegerArgumentType.getInteger(context, "blocks");
                        save();
                        return status();
                    })))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("beamheight")
                .then(RequiredArgumentBuilder.<FabricClientCommandSource, Integer>argument("blocks", IntegerArgumentType.integer(1, 64))
                    .executes(context -> {
                        cfg.cropLocationBeamHeight = IntegerArgumentType.getInteger(context, "blocks");
                        save();
                        return status();
                    })))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("boxsize")
                .then(RequiredArgumentBuilder.<FabricClientCommandSource, String>argument("blocks", StringArgumentType.word())
                    .executes(context -> boxSize(StringArgumentType.getString(context, "blocks"))))));
    }

    private static int set(String cropName) {
        if (!inGarden()) {
            local("This command only works in the Garden.");
            return 0;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return 0;
        if (cfg.cropLocationPerProfile && !profileAvailable()) {
            local("SkyBlock profile data is not available yet.");
            return 0;
        }
        HerculesGardenTracker.Crop crop = cropName == null
            ? HerculesGardenTracker.cropInHand(mc.player.getMainHandItem()) : crop(cropName);
        if (crop == null) {
            local(cropName == null ? "Hold a crop-specific farming tool." : "Unknown crop.");
            return 0;
        }
        maps();
        cfg.cropStartLocations.put(key(crop), encode(blockPosition(mc.player.position())));
        save();
        local("Set the " + crop.display() + " start location.");
        return 1;
    }

    private static int clear(String cropName, boolean all) {
        maps();
        if (all) {
            if (cfg.cropLocationPerProfile) {
                String prefix = profilePrefix();
                cfg.cropStartLocations.keySet().removeIf(key -> key.startsWith(prefix));
                cfg.cropLastFarmedLocations.keySet().removeIf(key -> key.startsWith(prefix));
            } else {
                cfg.cropStartLocations.clear();
                cfg.cropLastFarmedLocations.clear();
            }
            lastArmed.clear();
            save();
            local("Cleared saved crop locations for the active layout.");
            return 1;
        }
        Minecraft mc = Minecraft.getInstance();
        HerculesGardenTracker.Crop crop = cropName == null && mc.player != null
            ? HerculesGardenTracker.cropInHand(mc.player.getMainHandItem()) : crop(cropName);
        if (crop == null) {
            local(cropName == null ? "Hold a crop-specific farming tool." : "Unknown crop.");
            return 0;
        }
        cfg.cropStartLocations.remove(key(crop));
        cfg.cropLastFarmedLocations.remove(key(crop));
        lastArmed.remove(crop);
        save();
        local("Cleared saved " + crop.display() + " locations.");
        return 1;
    }

    private static int clearType(String cropName, boolean start) {
        maps();
        Minecraft mc = Minecraft.getInstance();
        HerculesGardenTracker.Crop crop = cropName == null && mc.player != null
            ? HerculesGardenTracker.cropInHand(mc.player.getMainHandItem()) : crop(cropName);
        if (crop == null) {
            local(cropName == null ? "Hold a crop-specific farming tool." : "Unknown crop.");
            return 0;
        }
        (start ? cfg.cropStartLocations : cfg.cropLastFarmedLocations).remove(key(crop));
        if (!start) lastArmed.remove(crop);
        save();
        local("Cleared the " + crop.display() + (start ? " start location." : " last-farmed location."));
        return 1;
    }

    private static int setMode(String raw) {
        String value = raw.toUpperCase(Locale.ROOT).replace("-", "").replace("_", "");
        String mode = switch (value) {
            case "START", "STARTONLY" -> "START";
            case "LAST", "LASTFARMED", "LASTONLY" -> "LAST";
            case "BOTH" -> "BOTH";
            default -> null;
        };
        if (mode == null) {
            local("Mode must be start, last or both.");
            return 0;
        }
        cfg.cropLocationMode = mode;
        save();
        return status();
    }

    private static int option(String name, String state) {
        Boolean value = parse(state);
        if (value == null) {
            local("State must be on or off.");
            return 0;
        }
        switch (name.toLowerCase(Locale.ROOT)) {
            case "enabled" -> cfg.cropLocationHelper = value;
            case "autolearn" -> cfg.cropLocationAutoLearn = value;
            case "autochat" -> cfg.cropLocationAutoChat = value;
            case "perprofile" -> cfg.cropLocationPerProfile = value;
            case "startbox" -> cfg.cropLocationStartBox = value;
            case "lastbox" -> cfg.cropLocationLastBox = value;
            case "beam" -> {
                cfg.cropLocationStartBeam = value;
                cfg.cropLocationLastBeam = value;
            }
            case "startbeam" -> cfg.cropLocationStartBeam = value;
            case "lastbeam" -> cfg.cropLocationLastBeam = value;
            case "line" -> cfg.cropLocationLine = value;
            case "labels" -> cfg.cropLocationLabels = value;
            case "distance" -> cfg.cropLocationDistance = value;
            case "throughwalls" -> cfg.cropLocationThroughWalls = value;
            case "startthroughwalls" -> cfg.cropLocationStartThroughWalls = value;
            default -> {
                local("Unknown crop-location option.");
                return 0;
            }
        }
        lastArmed.clear();
        save();
        return status();
    }

    private static int color(String type, String raw) {
        int color = parseColor(raw);
        if (color == 0) {
            local("Color must be RRGGBB or AARRGGBB and cannot be fully transparent.");
            return 0;
        }
        if (type.equalsIgnoreCase("start")) cfg.cropLocationStartColor = color;
        else if (type.equalsIgnoreCase("last")) cfg.cropLocationLastColor = color;
        else {
            local("Color type must be start or last.");
            return 0;
        }
        save();
        return status();
    }

    private static int clearProfiles() {
        maps();
        cfg.cropStartLocations.clear();
        cfg.cropLastFarmedLocations.clear();
        lastArmed.clear();
        save();
        local("Cleared crop locations for every saved profile and the global layout.");
        return 1;
    }

    private static int boxSize(String raw) {
        try {
            double value = Double.parseDouble(raw);
            if (!Double.isFinite(value) || value < .2 || value > 4) throw new NumberFormatException();
            cfg.cropLocationBoxSizeTenths = (int) Math.round(value * 10);
            save();
            return status();
        } catch (NumberFormatException ignored) {
            local("Box size must be between 0.2 and 4 blocks.");
            return 0;
        }
    }

    private static int status() {
        maps();
        long starts = cfg.cropStartLocations.keySet().stream().filter(key -> key.startsWith(profilePrefix())).count();
        long lasts = cfg.cropLastFarmedLocations.keySet().stream().filter(key -> key.startsWith(profilePrefix())).count();
        local("Crop locations " + (cfg.cropLocationHelper ? "on" : "off") + ", mode "
            + mode().name().toLowerCase(Locale.ROOT) + ", " + starts + " starts and " + lasts + " last-farmed points.");
        return 1;
    }

    private static Vec3 get(Map<String, String> values, HerculesGardenTracker.Crop crop) {
        String raw = values.get(key(crop));
        if (raw == null) return null;
        Vec3 parsed = decode(raw);
        if (parsed != null) return parsed;
        values.remove(key(crop));
        dirty = true;
        return null;
    }

    private static String key(HerculesGardenTracker.Crop crop) {
        return profilePrefix() + crop.name().toLowerCase(Locale.ROOT);
    }

    private static String profilePrefix() {
        return cfg.cropLocationPerProfile ? profile() + "|" : "global|";
    }

    private static String profile() {
        String profile = LyraStorageValue.currentProfileKey();
        return profile == null || profile.isBlank() ? "unknown" : profile.toLowerCase(Locale.ROOT);
    }

    private static boolean profileAvailable() {
        String profile = LyraStorageValue.currentProfileKey();
        return profile != null && !profile.isBlank();
    }

    private static HerculesGardenTracker.Crop crop(String raw) {
        if (raw == null) return null;
        String value = raw.replace("_", "").replace("-", "").toLowerCase(Locale.ROOT);
        for (HerculesGardenTracker.Crop crop : HerculesGardenTracker.Crop.values()) {
            if (crop.name().replace("_", "").toLowerCase(Locale.ROOT).equals(value)
                || crop.display().replace(" ", "").toLowerCase(Locale.ROOT).equals(value)) return crop;
        }
        return null;
    }

    private static String encode(Vec3 pos) {
        return String.format(Locale.ROOT, "%.1f,%.1f,%.1f", pos.x, pos.y, pos.z);
    }

    private static Vec3 decode(String raw) {
        try {
            String[] parts = raw.split(",");
            if (parts.length != 3) return null;
            double x = Double.parseDouble(parts[0]), y = Double.parseDouble(parts[1]), z = Double.parseDouble(parts[2]);
            if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)
                || Math.abs(x) > 30_000_000 || Math.abs(z) > 30_000_000 || y < -2048 || y > 2048) return null;
            return new Vec3(x, y, z);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static Vec3 blockPosition(Vec3 pos) {
        return new Vec3(Math.floor(pos.x) + .5, Math.floor(pos.y), Math.floor(pos.z) + .5);
    }

    private static Mode mode() {
        normalizeMode();
        return Mode.valueOf(cfg.cropLocationMode);
    }

    private static void normalizeMode() {
        if (cfg == null || cfg.cropLocationMode == null) return;
        String value = cfg.cropLocationMode.toUpperCase(Locale.ROOT);
        cfg.cropLocationMode = value.equals("LAST") || value.equals("BOTH") ? value : "START";
    }

    private static int parseColor(String raw) {
        try {
            String clean = raw.replace("#", "").replace("0x", "");
            long value = Long.parseUnsignedLong(clean, 16);
            if (clean.length() == 6) value |= 0xAA000000L;
            if (clean.length() != 6 && clean.length() != 8) return 0;
            return (int) value;
        } catch (Exception ignored) {
            return 0;
        }
    }

    private static Boolean parse(String value) {
        return switch (value.toLowerCase(Locale.ROOT)) {
            case "on", "true", "yes", "1" -> true;
            case "off", "false", "no", "0" -> false;
            default -> null;
        };
    }

    private static boolean active() {
        return cfg != null && cfg.enabled && cfg.cropLocationHelper && inGarden();
    }

    private static boolean inGarden() {
        return ConstellationClient.loc().area() == LocationManager.SkyblockArea.GARDEN;
    }

    private static void maps() {
        if (cfg.cropStartLocations == null) cfg.cropStartLocations = new HashMap<>();
        if (cfg.cropLastFarmedLocations == null) cfg.cropLastFarmedLocations = new HashMap<>();
    }

    private static void local(String text) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) mc.player.sendSystemMessage(Component.literal("\u00a72[Crop Location] \u00a7f" + text));
    }

    private static void save() {
        dirty = false;
        lastSave = System.currentTimeMillis();
        ConstellationClient.saveConfig();
    }

    private static void resetTransient() {
        levelIdentity = null;
        profileKey = "";
        lastArmed.clear();
    }
}
