package com.froggylord.constellation.constellation;

import com.froggylord.constellation.ConstellationClient;
import com.froggylord.constellation.config.OrionConfig;
import com.froggylord.constellation.data.DungeonState;
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
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.Arrays;
import java.util.Comparator;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// waypoint data and completion removal ported from Skyblocker (LGPL-3.0-or-later):
// skyblock/dungeon/GoldorWaypointsManager.java and assets/skyblocker/dungeons/goldorwaypoints.json
// class assignments and rendering options ported from Athen (BSD-3-Clause):
// modules/impl/dungeon/terminals/TerminalWaypoints.kt
public final class GoldorWaypoints {
    private static final String STORM_END = "[BOSS] Storm: I should have known that I stood no chance.";
    private static final String GOLDOR_START = "[BOSS] Goldor: Who dares trespass into my domain?";
    private static final String CORE_OPEN = "The Core entrance is opening!";
    private static final Pattern COMPLETION = Pattern.compile(
        "^(\\w{1,16}) (?:activated a (terminal|lever)|completed a (device))! \\((\\d+)/(\\d+)\\)$");

    private enum Kind { TERMINAL, DEVICE, LEVER }

    private static final class Waypoint {
        private final Kind kind;
        private final int phase;
        private final String name;
        private final Vec3 pos;
        private final int assignmentId;
        private final String defaultClass;
        private boolean found;

        private Waypoint(Kind kind, int phase, String name, int x, int y, int z,
                         int assignmentId, String defaultClass) {
            this.kind = kind;
            this.phase = phase;
            this.name = name;
            this.pos = new Vec3(x, y, z);
            this.assignmentId = assignmentId;
            this.defaultClass = defaultClass;
        }
    }

    private static final Waypoint[] WAYPOINTS = {
        device(0, 110, 121, 91),
        terminal(0, 1, 111, 113, 73, "Tank"), terminal(0, 2, 111, 119, 79, "Tank"),
        terminal(0, 3, 89, 112, 92, "Mage"), terminal(0, 4, 89, 122, 101, "Mage"),
        lever(0, 5, 94, 124, 113, "Archer"), lever(0, 6, 106, 124, 113, "Archer"),

        device(1, 60, 132, 143),
        terminal(1, 7, 68, 109, 121, "Tank"), terminal(1, 8, 59, 120, 122, "Mage"),
        terminal(1, 9, 47, 109, 121, "Berserk"), terminal(1, 10, 39, 108, 143, "Archer"),
        terminal(1, 11, 40, 124, 122, "Berserk"), lever(1, 12, 27, 124, 127, "Archer"),
        lever(1, 13, 23, 132, 138, "Healer"),

        device(2, 0, 120, 77),
        terminal(2, 14, -3, 109, 112, "Tank"), terminal(2, 15, -3, 119, 93, "Healer"),
        terminal(2, 16, 19, 123, 93, "Berserk"), terminal(2, 17, -3, 109, 77, "Archer"),
        lever(2, 18, 14, 122, 55, "Archer"), lever(2, 19, 2, 122, 55, "Archer"),

        device(3, 63, 127, 35),
        terminal(3, 20, 41, 109, 29, "Tank"), terminal(3, 21, 44, 121, 29, "Archer"),
        terminal(3, 22, 67, 109, 29, "Berserk"), terminal(3, 23, 72, 115, 48, "Healer"),
        lever(3, 24, 86, 128, 46, "Healer"), lever(3, 25, 84, 121, 34, "Healer")
    };

    private static boolean initialized;
    private static boolean active;
    private static boolean gateDestroyed;
    private static boolean objectivesComplete;
    private static int phase;

    private GoldorWaypoints() {}

    private static Waypoint device(int phase, int x, int y, int z) {
        return new Waypoint(Kind.DEVICE, phase, "Device", x, y, z, 0, "");
    }

    private static Waypoint terminal(int phase, int id, int x, int y, int z, String role) {
        int number = switch (phase) {
            case 0 -> id;
            case 1 -> id - 6;
            case 2 -> id - 13;
            default -> id - 19;
        };
        // Athen's assignment order swaps the two physical S2 labels used by Skyblocker's data.
        if (phase == 1 && id == 10) number = 5;
        else if (phase == 1 && id == 11) number = 4;
        return new Waypoint(Kind.TERMINAL, phase, "Terminal #" + number, x, y, z, id, role);
    }

    private static Waypoint lever(int phase, int id, int x, int y, int z, String role) {
        return new Waypoint(Kind.LEVER, phase, "Lever", x, y, z, id, role);
    }

    public static void init() {
        if (initialized) return;
        initialized = true;
        ConstellationClient.bus().subscribe(DungeonState.DungeonEnter.class, ignored -> reset());
        ConstellationClient.bus().subscribe(DungeonState.DungeonStart.class, ignored -> reset());
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> reset());
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> reset());
        ClientReceiveMessageEvents.ALLOW_GAME.register((message, overlay) -> {
            if (!overlay) onChat(message.getString());
            return true;
        });
    }

    public static void draw(WorldRenderer.Ctx ctx) {
        init();
        OrionConfig cfg = ConstellationClient.cfg().orion;
        if (cfg == null || !cfg.enabled || !isFloorSevenBoss()) {
            if (active) reset();
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;

        if (!cfg.goldorWaypoints) return;
        if (!cfg.goldorWaypointFixedPositions && cfg.goldorInactiveTerminals && isGoldorPhase()) {
            drawInactiveObjectives(ctx, mc, cfg);
        }
        if (!cfg.goldorWaypointFixedPositions || !active) return;

        int shownPhase = TerminalBreakdown.isActive() ? TerminalBreakdown.currentSectionIndex() : phase;
        if (shownPhase >= 0 && shownPhase < 4 && shownPhase != phase) {
            phase = shownPhase;
            gateDestroyed = phase == 3;
            objectivesComplete = false;
        }
        String ownClass = ConstellationClient.dungeon().playerClass();
        for (Waypoint waypoint : WAYPOINTS) {
            if (waypoint.phase != shownPhase || (cfg.goldorWaypointHideCompleted && waypoint.found)) continue;
            if (!kindEnabled(cfg, waypoint.kind)) continue;
            String assignment = assignment(cfg, waypoint);
            if (cfg.goldorWaypointClassFilter && !ownClass.isBlank() && !assignment.equals("ALL")
                && !assignment.equalsIgnoreCase(ownClass)) continue;

            int colour = colour(cfg, waypoint.kind);
            boolean throughWalls = cfg.goldorWaypointThroughWalls;
            AABB box = new AABB(waypoint.pos.x, waypoint.pos.y, waypoint.pos.z,
                waypoint.pos.x + 1, waypoint.pos.y + 1, waypoint.pos.z + 1).inflate(0.002);
            if (cfg.goldorWaypointFilled && cfg.goldorWaypointOutline) ctx.highlight(box, colour, throughWalls);
            else if (cfg.goldorWaypointFilled) ctx.box(box, colour, throughWalls);
            else if (cfg.goldorWaypointOutline) ctx.outline(box, colour, throughWalls);
            if (cfg.goldorWaypointBeam) ctx.beam(waypoint.pos.x + .5, waypoint.pos.y + 1,
                waypoint.pos.z + .5, colour, 8, throughWalls);
            if (cfg.goldorWaypointLabels) {
                String label = waypoint.name;
                if (cfg.goldorWaypointShowClass && !assignment.equals("ALL") && !assignment.isBlank()) {
                    label += " [" + assignment + "]";
                }
                ctx.label(waypoint.pos.add(.5, 1.5, .5), label, colour, throughWalls);
            }
        }
    }

    private static void onChat(String message) {
        if (message.equals(STORM_END) || message.equals(GOLDOR_START)) {
            if (!active) start();
            return;
        }
        if (message.equals(CORE_OPEN)) {
            reset();
            return;
        }
        if (!active || !isFloorSevenBoss()) return;
        if (message.equals("The gate has been destroyed!")) {
            gateDestroyed = true;
            advanceIfDone();
            return;
        }
        Matcher matcher = COMPLETION.matcher(message);
        if (!matcher.matches()) return;
        String type = matcher.group(2) == null ? matcher.group(3) : matcher.group(2);
        removeNearest(type, matcher.group(1));
        if (matcher.group(4).equals(matcher.group(5))) {
            objectivesComplete = true;
            advanceIfDone();
        }
    }

    // ported from Skyblocker (LGPL-3.0-or-later):
    // skyblock/dungeon/GoldorWaypointsManager.java (removeNearestWaypoint)
    private static void removeNearest(String type, String playerName) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        Kind kind = switch (type) {
            case "terminal" -> Kind.TERMINAL;
            case "lever" -> Kind.LEVER;
            case "device" -> Kind.DEVICE;
            default -> null;
        };
        if (kind == null) return;
        mc.level.players().stream()
            .filter(player -> player.getGameProfile().name().equalsIgnoreCase(playerName))
            .findFirst()
            .flatMap(player -> Arrays.stream(WAYPOINTS)
                .filter(waypoint -> waypoint.kind == kind && !waypoint.found)
                .filter(waypoint -> waypoint.pos.distanceToSqr(player.position()) <= 256)
                .min(Comparator.comparingDouble(waypoint -> waypoint.pos.distanceToSqr(player.position()))))
            .ifPresent(waypoint -> waypoint.found = true);
    }

    // ported from Odin (BSD-3-Clause): features/impl/boss/InactiveWaypoints.kt
    private static void drawInactiveObjectives(WorldRenderer.Ctx ctx, Minecraft mc, OrionConfig cfg) {
        for (var entity : mc.level.entitiesForRendering()) {
            if (!(entity instanceof ArmorStand)) continue;
            String name = entity.getName().getString();
            Kind kind = switch (name) {
                case "Inactive Terminal" -> Kind.TERMINAL;
                case "Inactive" -> Kind.DEVICE;
                case "Not Activated" -> Kind.LEVER;
                default -> null;
            };
            if (kind == null || !kindEnabled(cfg, kind)) continue;
            int colour = colour(cfg, kind);
            double x = entity.getX(), y = entity.getY(), z = entity.getZ();
            ctx.highlight(new AABB(x - .5, y, z - .5, x + .5, y + 1, z + .5), colour,
                cfg.goldorWaypointThroughWalls);
            if (cfg.goldorWaypointLabels) {
                ctx.label(new Vec3(x, y + 2, z), title(kind), colour, cfg.goldorWaypointThroughWalls);
            }
        }
    }

    public static void registerCommands(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        dispatcher.register(LiteralArgumentBuilder.<FabricClientCommandSource>literal("goldorwaypoints")
            .executes(ctx -> status())
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("status").executes(ctx -> status()))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("list").executes(ctx -> listAssignments()))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("reset").executes(ctx -> manualReset()))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("assign")
                .then(RequiredArgumentBuilder.<FabricClientCommandSource, Integer>argument("id", IntegerArgumentType.integer(1, 25))
                    .then(RequiredArgumentBuilder.<FabricClientCommandSource, String>argument("class", StringArgumentType.word())
                        .executes(ctx -> assign(IntegerArgumentType.getInteger(ctx, "id"),
                            StringArgumentType.getString(ctx, "class"))))))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("color")
                .then(RequiredArgumentBuilder.<FabricClientCommandSource, String>argument("type", StringArgumentType.word())
                    .then(RequiredArgumentBuilder.<FabricClientCommandSource, String>argument("argb", StringArgumentType.word())
                        .executes(ctx -> setColour(StringArgumentType.getString(ctx, "type"),
                            StringArgumentType.getString(ctx, "argb")))))));
    }

    private static int status() {
        OrionConfig cfg = ConstellationClient.cfg().orion;
        message("Waypoints " + (cfg.goldorWaypoints ? "on" : "off") + ", class filter "
            + (cfg.goldorWaypointClassFilter ? "on" : "off") + ", phase " + (phase + 1) + ".");
        message("Assignment IDs 1-25 follow S1-S4 terminals then levers. Use class, all, or default.");
        return 1;
    }

    private static int manualReset() {
        resetFound();
        message("Restored every Goldor waypoint for this run.");
        return 1;
    }

    private static int listAssignments() {
        OrionConfig cfg = ConstellationClient.cfg().orion;
        for (int section = 0; section < 4; section++) {
            StringBuilder line = new StringBuilder("S").append(section + 1).append(':');
            for (Waypoint waypoint : WAYPOINTS) {
                if (waypoint.phase != section || waypoint.assignmentId == 0) continue;
                line.append(' ').append(waypoint.assignmentId).append('=')
                    .append(waypoint.name.replace("Terminal ", "T").replace("Lever", "L"))
                    .append('/').append(assignment(cfg, waypoint));
            }
            message(line.toString());
        }
        return 1;
    }

    private static int assign(int id, String value) {
        String normalized = normalizeClass(value);
        if (normalized == null) {
            message("Class must be healer, mage, berserk, archer, tank, all, or default.");
            return 0;
        }
        OrionConfig cfg = ConstellationClient.cfg().orion;
        if (value.equalsIgnoreCase("default")) cfg.goldorWaypointAssignments.remove(String.valueOf(id));
        else cfg.goldorWaypointAssignments.put(String.valueOf(id), normalized);
        ConstellationClient.saveConfig();
        message("Waypoint " + id + " assignment set to " + (value.equalsIgnoreCase("default") ? "default" : normalized) + ".");
        return 1;
    }

    private static int setColour(String type, String argb) {
        Integer parsed = parseColour(argb);
        if (parsed == null) { message("Color must be RRGGBB or AARRGGBB."); return 0; }
        OrionConfig cfg = ConstellationClient.cfg().orion;
        switch (type.toLowerCase(Locale.ROOT)) {
            case "terminal" -> cfg.goldorTerminalColour = parsed;
            case "device" -> cfg.goldorDeviceColour = parsed;
            case "lever" -> cfg.goldorLeverColour = parsed;
            default -> { message("Type must be terminal, device, or lever."); return 0; }
        }
        ConstellationClient.saveConfig();
        message(title(type) + " color set to " + String.format("%08X", parsed) + ".");
        return 1;
    }

    private static Integer parseColour(String value) {
        try {
            String clean = value.startsWith("#") ? value.substring(1) : value;
            if (clean.length() == 6) clean = "FF" + clean;
            return clean.length() == 8 ? (int) Long.parseLong(clean, 16) : null;
        } catch (NumberFormatException ignored) { return null; }
    }

    private static void start() {
        resetFound();
        active = true;
        phase = 0;
        gateDestroyed = false;
        objectivesComplete = false;
    }

    private static void advanceIfDone() {
        if (!gateDestroyed || !objectivesComplete || phase >= 3) return;
        phase++;
        gateDestroyed = phase == 3;
        objectivesComplete = false;
    }

    private static void reset() {
        active = false;
        phase = 0;
        gateDestroyed = false;
        objectivesComplete = false;
        resetFound();
    }

    private static void resetFound() {
        for (Waypoint waypoint : WAYPOINTS) waypoint.found = false;
    }

    private static boolean kindEnabled(OrionConfig cfg, Kind kind) {
        return switch (kind) {
            case TERMINAL -> cfg.goldorWaypointTerminals;
            case DEVICE -> cfg.goldorWaypointDevices;
            case LEVER -> cfg.goldorWaypointLevers;
        };
    }

    private static int colour(OrionConfig cfg, Kind kind) {
        return switch (kind) {
            case TERMINAL -> cfg.goldorTerminalColour;
            case DEVICE -> cfg.goldorDeviceColour;
            case LEVER -> cfg.goldorLeverColour;
        };
    }

    private static String assignment(OrionConfig cfg, Waypoint waypoint) {
        if (waypoint.assignmentId == 0) return "ALL";
        if (cfg.goldorWaypointAssignments == null) {
            cfg.goldorWaypointAssignments = new java.util.LinkedHashMap<>();
            return waypoint.defaultClass;
        }
        String saved = cfg.goldorWaypointAssignments.get(String.valueOf(waypoint.assignmentId));
        String normalized = saved == null ? null : normalizeClass(saved);
        if (normalized == null || normalized.equals("DEFAULT")) return waypoint.defaultClass;
        return normalized;
    }

    private static String normalizeClass(String value) {
        if (value.equalsIgnoreCase("default")) return "DEFAULT";
        if (value.equalsIgnoreCase("all")) return "ALL";
        for (String role : new String[]{"Healer", "Mage", "Berserk", "Archer", "Tank"}) {
            if (role.equalsIgnoreCase(value)) return role;
        }
        return null;
    }

    private static String title(Kind kind) {
        return kind.name().substring(0, 1) + kind.name().substring(1).toLowerCase(Locale.ROOT);
    }

    private static String title(String value) {
        return value.substring(0, 1).toUpperCase(Locale.ROOT) + value.substring(1).toLowerCase(Locale.ROOT);
    }

    private static boolean isGoldorPhase() {
        return "Goldor".equals(ConstellationClient.dungeon().bossPhase()) || TerminalBreakdown.isActive();
    }

    private static boolean isFloorSevenBoss() {
        String floor = ConstellationClient.dungeon().floor();
        return ConstellationClient.loc().inDungeons() && ConstellationClient.dungeon().inBoss()
            && floor != null && floor.endsWith("7");
    }

    private static void message(String text) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) mc.player.sendSystemMessage(Component.literal("§bGoldor Waypoints §8> §f" + text));
    }
}
