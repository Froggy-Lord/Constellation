package com.froggylord.constellation.constellation;

import com.froggylord.constellation.ConstellationClient;
import com.froggylord.constellation.config.OrionConfig;
import com.froggylord.constellation.data.DungeonData;
import com.froggylord.constellation.data.RoomMatch;
import com.froggylord.constellation.data.RoomTransform;
import com.froggylord.constellation.render.WorldRenderer;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

// ported from cryptkit (GPL-3.0): feature/Routes.java, feature/RoomRoutes.java
public final class Routes {

    private static String room = "";
    private static int ax, az;
    private static RoomTransform.Direction dir = RoomTransform.Direction.NW;
    private static boolean pearlMode;
    private static boolean wholeMode;
    private static int currentStep;
    private static List<DungeonData.Route> activeRoute = List.of();
    private static final List<RouteR> routes = new ArrayList<>();

    private enum MarkerType { ETHERWARP, INTERACT, MINE, TNT, PEARL, SECRET }
    private record Marker(double x, double y, double z, int colour, MarkerType type, String label) {}
    private record RouteLine(Vec3 from, Vec3 to, int colour) {}
    private record RouteR(List<Vec3> path, List<Marker> markers, List<RouteLine> extraLines) {}

    // ---- recording state (ported from cryptkit (GPL-3.0): feature/Routes.java) ----

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path USER_FILE = Path.of("config", "constellation", "user-routes.json");
    private static final Path USER_PEARL_FILE = Path.of("config", "constellation", "user-pearl-routes.json");

    private static boolean recording = false;
    private static final List<Vec3> walkPath = new ArrayList<>();
    private static final List<int[]> recEtherwarps = new ArrayList<>();
    private static final List<int[]> recInteracts = new ArrayList<>();
    private static final List<int[]> recMines = new ArrayList<>();
    private static final List<int[]> recTnts = new ArrayList<>();
    private static final List<double[]> recPearls = new ArrayList<>();
    private static final List<double[]> recPearlAngles = new ArrayList<>();
    private static List<DungeonData.Route> pending = List.of();
    private static final List<DungeonData.Route> recordedSteps = new ArrayList<>();
    private static String recordedRoom = "";
    private static boolean merged = false;
    private static String pendingLiveKey = "";
    private static boolean pendingSaved = false;
    private static final Map<String, List<DungeonData.Route>> userRoutes = new HashMap<>();
    private static final Map<String, List<DungeonData.Route>> userPearlRoutes = new HashMap<>();
    private static boolean recordPearlMode;
    private static boolean pendingPearlMode;
    private static int recordAnchorX, recordAnchorZ;
    private static RoomTransform.Direction recordDirection = RoomTransform.Direction.NW;

    private Routes() {}

    // ---- user route persistence / init (ported from cryptkit (GPL-3.0): feature/Routes.java) ----

    public static void init() {
        userRoutes.clear();
        userPearlRoutes.clear();
        loadUserRoutesFromDisk(USER_FILE, userRoutes);
        loadUserRoutesFromDisk(USER_PEARL_FILE, userPearlRoutes);
        mergeUserRoutesIntoLive();
        OnlineRouteDatabase.init();
        ConstellationClient.tick().every(3, "orion-route-record", () -> {
            if (!recording) return;
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null || mc.level == null || !ConstellationClient.loc().inDungeons()
                || !sameRecordingRoom()) { clearRoute(); return; }
            Vec3 pos = mc.player.position();
            // movement spacing ported from SecretRoutes (GPL-3.0): events/OnPlayerTick.java
            if (walkPath.isEmpty() || walkPath.get(walkPath.size() - 1).distanceToSqr(pos) >= 5.76)
                if (walkPath.size() < 4000) walkPath.add(pos);
        });
    }

    // ---- bundled-route playback (keep) ----

    public static boolean hasRouteFor(String roomName) {
        OrionConfig cfg = ConstellationClient.cfg().orion;
        if (cfg == null || !cfg.routes || roomName == null || roomName.isEmpty()) return false;
        Map<String, List<DungeonData.Route>> src = cfg.pearlRoutes ? DungeonData.PEARL_ROUTES : DungeonData.ROUTES;
        String key = roomName.toLowerCase(Locale.ROOT);
        return src.keySet().stream().anyMatch(candidate -> candidate.equals(key) || candidate.startsWith(key + ":"));
    }

    public static boolean hasUsableRoute(String roomName) {
        OrionConfig cfg = ConstellationClient.cfg().orion;
        if (cfg == null || !cfg.routes || roomName == null || roomName.isEmpty()) return false;
        sync(cfg);
        rebuildVisible(cfg);
        return !routes.isEmpty();
    }

    public static void draw(WorldRenderer.Ctx ctx) {
        OrionConfig cfg = ConstellationClient.cfg().orion;
        if (cfg == null || !cfg.routes) { resetPlayback(); return; }
        if (!ConstellationClient.loc().inDungeons() || !RoomMatch.isMatched()) { resetPlayback(); return; }

        sync(cfg);
        rebuildVisible(cfg);
        if (routes.isEmpty()) return;

        for (RouteR r : routes) {
            if (cfg.routeLines) {
                for (int i = 0; i + 1 < r.path().size(); i++) {
                    ctx.line(r.path().get(i), r.path().get(i + 1), cfg.routeLineColour, cfg.routeThroughWalls);
                }
            }
            for (RouteLine line : r.extraLines())
                ctx.line(line.from(), line.to(), line.colour(), cfg.routeThroughWalls);
            if (!cfg.routeMarkers) continue;
            for (Marker m : r.markers()) {
                AABB box = new AABB(m.x(), m.y(), m.z(), m.x() + 1, m.y() + 1, m.z() + 1);
                if (cfg.routeFilledMarkers) ctx.box(box, m.colour(), cfg.routeThroughWalls);
                else ctx.outline(box, m.colour(), cfg.routeThroughWalls);
                if (cfg.routeLabels && !m.label().isEmpty())
                    ctx.label(new Vec3(m.x() + .5, m.y() + 1.25, m.z() + .5), m.label(), m.colour(), cfg.routeThroughWalls);
            }
        }
    }

    private static void sync(OrionConfig cfg) {
        String cur = ConstellationClient.dungeon().currentRoom();
        int x = ConstellationClient.dungeon().roomCornerX(), z = ConstellationClient.dungeon().roomCornerZ();
        RoomTransform.Direction d = ConstellationClient.dungeon().roomDirection();
        if (cur.equals(room) && x == ax && z == az && d == dir && cfg.pearlRoutes == pearlMode) {
            if (activeRoute.isEmpty()) {
                Map<String, List<DungeonData.Route>> source = cfg.pearlRoutes ? DungeonData.PEARL_ROUTES : DungeonData.ROUTES;
                activeRoute = selectRoute(source, cur.toLowerCase(Locale.ROOT));
            }
            if (cfg.routeWholeRoute != wholeMode) {
                wholeMode = cfg.routeWholeRoute;
                rebuildVisible(cfg);
            }
            return;
        }

        room = cur; ax = x; az = z; dir = d; pearlMode = cfg.pearlRoutes; wholeMode = cfg.routeWholeRoute;
        currentStep = 0;
        Map<String, List<DungeonData.Route>> source = cfg.pearlRoutes ? DungeonData.PEARL_ROUTES : DungeonData.ROUTES;
        activeRoute = selectRoute(source, cur.toLowerCase(Locale.ROOT));
        rebuildVisible(cfg);
    }

    // ported from SecretRoutes (GPL-3.0): utils/Room.java (nearest route start selection)
    private static List<DungeonData.Route> selectRoute(Map<String, List<DungeonData.Route>> source, String key) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return source.getOrDefault(key, List.of());
        List<DungeonData.Route> best = List.of();
        double bestDistance = Double.MAX_VALUE;
        List<String> keys = source.keySet().stream()
            .filter(candidate -> candidate.equals(key) || candidate.startsWith(key + ":"))
            .sorted().toList();
        for (String routeKey : keys) {
            List<DungeonData.Route> candidate = source.get(routeKey);
            if (candidate == null || candidate.isEmpty() || candidate.get(0).locations().isEmpty()) continue;
            int[] start = firstRoutePoint(candidate);
            if (start == null) continue;
            long[] world = tf(start);
            double distance = mc.player.position().distanceToSqr(new Vec3(world[0] + .5, world[1] + .5, world[2] + .5));
            if (distance < bestDistance) {
                bestDistance = distance;
                best = candidate;
            }
        }
        return best;
    }

    private static int[] firstRoutePoint(List<DungeonData.Route> route) {
        for (DungeonData.Route step : route) {
            if (!step.locations().isEmpty()) return step.locations().getFirst();
            if (step.secret() != null) return step.secret();
        }
        return null;
    }

    // ported from SecretRoutes (GPL-3.0): events/OnWorldRender.java
    private static void rebuildVisible(OrionConfig cfg) {
        routes.clear();
        if (activeRoute.isEmpty() || currentStep >= activeRoute.size()) return;
        int visible = Math.clamp(cfg.routeVisibleSteps, 1, 5);
        int last = cfg.routeWholeRoute ? activeRoute.size() : Math.min(activeRoute.size(), currentStep + visible);

        for (int step = currentStep; step < last; step++) {
            DungeonData.Route rt = activeRoute.get(step);
            List<Vec3> path = new ArrayList<>();
            for (int[] p : rt.locations()) {
                long[] w = tf(p);
                path.add(new Vec3(w[0] + 0.5, w[1] + 0.5, w[2] + 0.5));
            }
            List<Marker> markers = new ArrayList<>();
            boolean future = cfg.routeDistinguishFuture && step > currentStep;
            if (cfg.routeRenderEtherwarps) addMarkers(markers, rt.etherwarps(), colour(0xFF800080, future), MarkerType.ETHERWARP, "Etherwarp");
            if (cfg.routeRenderInteracts) addMarkers(markers, rt.interacts(), colour(0xFF0000FF, future), MarkerType.INTERACT, "Interact");
            if (cfg.routeRenderMines) addMarkers(markers, rt.mines(), colour(0xFFFFFF00, future), MarkerType.MINE, "Mine");
            if (cfg.routeRenderSuperboom) addMarkers(markers, rt.tnts(), colour(0xFFFF0000, future), MarkerType.TNT, "Superboom");
            List<RouteLine> extraLines = new ArrayList<>();
            if (cfg.routeMarkers && cfg.routeRenderPearls) addPearls(markers, extraLines, rt, future, cfg);
            if (cfg.routeRenderSecrets && rt.secret() != null) {
                String label = secretLabel(rt.secretType());
                markers.add(marker(rt.secret(), colour(secretColour(rt.secretType()), future), MarkerType.SECRET, label));
            }
            if (step == currentStep && cfg.routePlayerToSecret && rt.secret() != null) {
                Minecraft mc = Minecraft.getInstance();
                if (mc.player != null) {
                    Marker target = marker(rt.secret(), cfg.routeLineColour, MarkerType.SECRET, "");
                    extraLines.add(new RouteLine(mc.player.getEyePosition(), new Vec3(target.x() + .5, target.y() + .5, target.z() + .5), cfg.routeLineColour));
                }
            }
            if (step == currentStep && cfg.routePlayerToEtherwarp && !rt.etherwarps().isEmpty()) {
                Minecraft mc = Minecraft.getInstance();
                if (mc.player != null) {
                    Marker target = marker(rt.etherwarps().getFirst(), 0xFF00FFFF, MarkerType.ETHERWARP, "");
                    extraLines.add(new RouteLine(mc.player.getEyePosition(), new Vec3(target.x() + .5, target.y() + .5, target.z() + .5), 0xFF00FFFF));
                }
            }
            routes.add(new RouteR(path, markers, extraLines));
        }
    }

    // ported from SecretRoutes (GPL-3.0): utils/Room.java (nextSecret)
    public static void onSecretCollected(int worldX, int worldY, int worldZ, String category) {
        OrionConfig cfg = ConstellationClient.cfg().orion;
        if (cfg == null || !cfg.routes || !cfg.routeAutoAdvance) return;
        sync(cfg);
        if (activeRoute.isEmpty() || currentStep >= activeRoute.size()) return;
        DungeonData.Route step = activeRoute.get(currentStep);
        if (step.secret() == null || !secretMatches(step, worldX, worldY, worldZ, category)) return;
        currentStep++;
        rebuildVisible(cfg);
    }

    public static void onSecretFailed(int worldX, int worldY, int worldZ, String category) {
        OrionConfig cfg = ConstellationClient.cfg().orion;
        if (cfg == null || !cfg.routes) return;
        sync(cfg);
        if (currentStep <= 0 || activeRoute.isEmpty()) return;
        DungeonData.Route previous = activeRoute.get(currentStep - 1);
        if (!secretMatches(previous, worldX, worldY, worldZ, category)) return;
        currentStep--;
        rebuildVisible(cfg);
    }

    // manual recovery controls ported from SecretRoutes (GPL-3.0): utils/Room.java
    public static String nextStep() {
        OrionConfig cfg = ConstellationClient.cfg().orion;
        if (cfg == null || activeRoute.isEmpty()) return "§eno active route";
        if (currentStep < activeRoute.size() - 1) currentStep++;
        rebuildVisible(cfg);
        return routeStatus();
    }

    public static String previousStep() {
        OrionConfig cfg = ConstellationClient.cfg().orion;
        if (cfg == null || activeRoute.isEmpty()) return "§eno active route";
        if (currentStep > 0) currentStep--;
        rebuildVisible(cfg);
        return routeStatus();
    }

    public static String restartPlayback() {
        OrionConfig cfg = ConstellationClient.cfg().orion;
        if (cfg == null || activeRoute.isEmpty()) return "§eno active route";
        currentStep = 0;
        rebuildVisible(cfg);
        return routeStatus();
    }

    public static String routeStatus() {
        if (activeRoute.isEmpty()) return "§eno active route";
        return "§aroute " + room + " step " + (currentStep + 1) + "/" + activeRoute.size()
            + (pearlMode ? " (pearls)" : " (normal)");
    }

    public static String visibleSteps(int value) {
        OrionConfig cfg = ConstellationClient.cfg().orion;
        if (cfg == null) return "§cOrion config unavailable";
        cfg.routeVisibleSteps = Math.clamp(value, 1, 5);
        ConstellationClient.saveConfig();
        rebuildVisible(cfg);
        return "§avisible route steps: " + cfg.routeVisibleSteps;
    }

    public static String lineColour(String raw, boolean pearl) {
        OrionConfig cfg = ConstellationClient.cfg().orion;
        if (cfg == null) return "§cOrion config unavailable";
        try {
            String hex = raw.startsWith("#") ? raw.substring(1) : raw;
            long parsed = Long.parseUnsignedLong(hex, 16);
            if (hex.length() == 6) parsed |= 0xFF000000L;
            if (hex.length() != 6 && hex.length() != 8) throw new NumberFormatException();
            if (pearl) cfg.routePearlLineColour = (int) parsed;
            else cfg.routeLineColour = (int) parsed;
            ConstellationClient.saveConfig();
            return "§a" + (pearl ? "pearl" : "route") + " line color: "
                + String.format(Locale.ROOT, "%08X", (int) parsed);
        } catch (NumberFormatException ignored) {
            return "§cuse RRGGBB or AARRGGBB";
        }
    }

    private static boolean secretMatches(DungeonData.Route step, int x, int y, int z, String category) {
        long[] target = tf(step.secret());
        double dx = target[0] - x, dy = target[1] - y, dz = target[2] - z;
        if (dx * dx + dy * dy + dz * dz > 9) return false;
        String expected = step.secretType() == null ? "" : step.secretType().toLowerCase(Locale.ROOT);
        String actual = category == null ? "" : category.toLowerCase(Locale.ROOT);
        if (expected.equals("exit") || expected.equals("exitroute")) return false;
        if (expected.equals("bat")) return actual.equals("bat");
        if (expected.equals("item")) return actual.equals("item");
        return expected.equals("interact") && isInteractionCategory(actual);
    }

    private static boolean isInteractionCategory(String category) {
        return category.equals("chest") || category.equals("lever") || category.equals("wither")
            || category.equals("wither_essence") || category.equals("superboom") || category.equals("interact");
    }

    private static void resetPlayback() {
        room = "";
        activeRoute = List.of();
        currentStep = 0;
        routes.clear();
    }

    public static void remergeUserRoutes() {
        mergeUserRoutesIntoLive();
        room = "";
    }

    public static void routeDatabaseChanged(boolean normalRoutes) {
        if (normalRoutes) {
            mergeRoutes(userRoutes, DungeonData.ROUTES);
        } else {
            mergeRoutes(userPearlRoutes, DungeonData.PEARL_ROUTES);
        }
        if (merged && !pendingSaved && pendingPearlMode != normalRoutes
            && !pendingLiveKey.isEmpty() && !pending.isEmpty())
            routeDestination(pendingPearlMode).put(pendingLiveKey, new ArrayList<>(pending));
        room = "";
    }

    private static Marker marker(int[] p, int colour, MarkerType type, String label) {
        long[] w = tf(p);
        return new Marker(w[0], w[1], w[2], colour, type, label);
    }

    private static Marker pearlMarker(double[] p, int colour, String label) {
        double[] w = RoomTransform.relativeToActual(dir, ax, az, p[0], p[1], p[2]);
        return new Marker(w[0] - .25, w[1], w[2] - .25, colour, MarkerType.PEARL, label);
    }

    private static void addMarkers(List<Marker> out, List<int[]> points, int colour, MarkerType type, String label) {
        for (int i = 0; i < points.size(); i++)
            out.add(marker(points.get(i), colour, type, points.size() > 1 ? label + " " + (i + 1) : label));
    }

    // ported from SecretRoutes (GPL-3.0): utils/SecretUtils.java (ender pearl markers and angle lines)
    private static void addPearls(List<Marker> markers, List<RouteLine> lines, DungeonData.Route route,
                                  boolean future, OrionConfig cfg) {
        int colour = colour(0xFF00FFFF, future);
        for (int i = 0; i < route.pearls().size(); i++) {
            Marker marker = pearlMarker(route.pearls().get(i), colour,
                route.pearls().size() > 1 ? "Pearl " + (i + 1) : "Pearl");
            markers.add(marker);
            if (i >= route.pearlAngles().size()) continue;
            double[] angle = route.pearlAngles().get(i);
            double pitch = angle[0];
            double yaw = angle[1] + yawOffset(dir) + 90.0;
            double yawRadians = Math.toRadians(yaw), pitchRadians = Math.toRadians(pitch);
            Vec3 start = new Vec3(marker.x() + .25, marker.y() + 1.62, marker.z() + .25);
            Vec3 direction = new Vec3(-Math.sin(yawRadians) * Math.cos(pitchRadians),
                -Math.sin(pitchRadians), Math.cos(yawRadians) * Math.cos(pitchRadians)).normalize();
            lines.add(new RouteLine(start, start.add(direction.scale(10)), cfg.routePearlLineColour));
        }
    }

    private static double yawOffset(RoomTransform.Direction direction) {
        return switch (direction) { case NW -> 0; case NE -> 90; case SE -> 180; case SW -> 270; };
    }

    private static int colour(int base, boolean future) {
        if (!future) return base;
        int r = (base >> 16) & 0xFF, g = (base >> 8) & 0xFF, b = base & 0xFF;
        return 0xFF000000 | ((r * 2 / 3) << 16) | ((g * 2 / 3) << 8) | (b * 2 / 3);
    }

    private static String secretLabel(String type) {
        if (type == null || type.isBlank()) return "Secret";
        return switch (type.toLowerCase(Locale.ROOT)) {
            case "interact" -> "Interact";
            case "item" -> "Item";
            case "bat" -> "Bat";
            case "exit", "exitroute" -> "Exit";
            default -> "Secret";
        };
    }

    private static long[] tf(int[] p) {
        return RoomTransform.relativeToActual(dir, ax, az, p[0], p[1], p[2]);
    }

    private static int secretColour(String type) {
        if (type == null) return 0xFFFFFF55;
        return switch (type.toLowerCase(Locale.ROOT)) {
            case "chest" -> 0xFFFFAA00;
            case "item" -> 0xFF55FFFF;
            case "bat" -> 0xFF5555FF;
            case "wither" -> 0xFFAA00FF;
            case "interact", "lever" -> 0xFF55FF55;
            default -> 0xFFFFFF55;
        };
    }

    // ---- recording (ported from cryptkit (GPL-3.0): feature/Routes.java) ----

    public static String record() {
        if (!RoomMatch.isMatched()) return "§cnot in a confirmed room";
        if (recording) return "§calready recording; use /cn route clear to discard it";
        if (!pending.isEmpty() && !pendingSaved)
            return "§cunsaved route in review; save or clear it before starting another";
        discardUnsavedPreview();
        recording = true;
        walkPath.clear();
        recEtherwarps.clear();
        recInteracts.clear();
        recMines.clear();
        recTnts.clear();
        recPearls.clear();
        recPearlAngles.clear();
        recordedSteps.clear();
        pending = List.of();
        merged = false;
        pendingLiveKey = "";
        pendingSaved = false;
        recordedRoom = ConstellationClient.dungeon().currentRoom();
        recordAnchorX = ConstellationClient.dungeon().roomCornerX();
        recordAnchorZ = ConstellationClient.dungeon().roomCornerZ();
        recordDirection = ConstellationClient.dungeon().roomDirection();
        OrionConfig cfg = ConstellationClient.cfg().orion;
        recordPearlMode = cfg != null && cfg.pearlRoutes;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) walkPath.add(mc.player.position());
        return "§arecording route in " + recordedRoom
            + " - tag actions, then /cn route tag <secret|item|bat|exit> to finish each step";
    }

    public static String stop() {
        if (!recording) return "§cnot recording";
        if (!sameRecordingRoom()) return clearCorruptRecording();
        recording = false;
        pending = List.copyOf(recordedSteps);
        pendingPearlMode = recordPearlMode;
        if (pending.isEmpty()) {
            recording = true;
            return "§cno completed secret step yet; recording is still active";
        }
        boolean discardedDraft = !walkPath.isEmpty() || !recEtherwarps.isEmpty() || !recInteracts.isEmpty()
            || !recMines.isEmpty() || !recTnts.isEmpty() || !recPearls.isEmpty();
        clearRecordedStep();
        int nodes = pending.stream().mapToInt(step -> step.locations().size()).sum();
        return "§astopped - " + pending.size() + " steps, " + nodes
            + " nodes. /cn route save to persist, /cn route play to see"
            + (discardedDraft ? " §e(unfinished draft discarded)" : "");
    }

    public static String tag(String type) {
        if (!recording) return "§cnot recording";
        if (!sameRecordingRoom()) return clearCorruptRecording();
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return "§cno player";
        // exact pearl position and relative aim ported from SecretRoutes (GPL-3.0): utils/RouteRecording.java
        if (type.equalsIgnoreCase("pearl")) {
            Vec3 pos = mc.player.position();
            double[] rel = RoomTransform.actualToRelative(recordDirection, recordAnchorX, recordAnchorZ, pos.x, pos.y, pos.z);
            recPearls.add(rel);
            recPearlAngles.add(new double[]{mc.player.getXRot(), mc.player.getYRot() - yawOffset(recordDirection)});
            return "§atagged pearl position and aim";
        }
        if (mc.hitResult == null || mc.hitResult.getType() != HitResult.Type.BLOCK)
            return "§clook at a block";
        BlockPos pos = ((BlockHitResult) mc.hitResult).getBlockPos();
        long[] rel = RoomTransform.actualToRelative(recordDirection, recordAnchorX, recordAnchorZ, pos.getX(), pos.getY(), pos.getZ());
        int[] t = new int[]{(int) rel[0], (int) rel[1], (int) rel[2]};
        switch (type.toLowerCase(Locale.ROOT)) {
            case "etherwarp" -> recEtherwarps.add(t);
            case "interact" -> recInteracts.add(t);
            case "mine" -> recMines.add(t);
            case "tnt" -> recTnts.add(t);
            case "secret", "chest" -> { return finishRecordedStep("interact", t); }
            case "item" -> { return finishRecordedStep("item", t); }
            case "bat" -> { return finishRecordedStep("bat", t); }
            case "exit", "exitroute" -> { return finishExitStep(t); }
            default -> { return "§cunknown type (etherwarp, interact, mine, tnt, pearl, secret, item, bat, exit)"; }
        }
        return "§atagged " + type;
    }

    // automatic interaction capture ported from SecretRoutes (GPL-3.0): events/OnPlayerInteract.java
    public static void onRecordingBlockInteraction(BlockPos position, Block block) {
        if (!recording || !sameRecordingRoom()) return;
        long[] relative = RoomTransform.actualToRelative(recordDirection, recordAnchorX, recordAnchorZ,
            position.getX(), position.getY(), position.getZ());
        int[] point = new int[]{(int) relative[0], (int) relative[1], (int) relative[2]};
        if (block == Blocks.LEVER) {
            if (recInteracts.stream().noneMatch(existing -> Arrays.equals(existing, point))) recInteracts.add(point);
            return;
        }
        if (block == Blocks.CHEST || block == Blocks.TRAPPED_CHEST || block == Blocks.PLAYER_HEAD
            || block == Blocks.PLAYER_WALL_HEAD || block == Blocks.SKELETON_SKULL
            || block == Blocks.SKELETON_WALL_SKULL) finishRecordedStep("interact", point);
    }

    public static boolean isRecording() {
        return recording;
    }

    // item/bat step boundary ported from SecretRoutes (GPL-3.0): events/OnItemPickedUp.java
    public static void onRecordingSecretSignal(int worldX, int worldY, int worldZ, String category) {
        if (!recording || !sameRecordingRoom() || category == null) return;
        String type = category.toLowerCase(Locale.ROOT);
        if (!type.equals("item") && !type.equals("bat")) return;
        long[] relative = RoomTransform.actualToRelative(recordDirection, recordAnchorX, recordAnchorZ,
            worldX, worldY, worldZ);
        finishRecordedStep(type, new int[]{(int) relative[0], (int) relative[1], (int) relative[2]});
    }

    public static void onRecordingSecretFailed(int worldX, int worldY, int worldZ) {
        if (!recording || recordedSteps.isEmpty()) return;
        long[] relative = RoomTransform.actualToRelative(recordDirection, recordAnchorX, recordAnchorZ,
            worldX, worldY, worldZ);
        int[] target = new int[]{(int) relative[0], (int) relative[1], (int) relative[2]};
        DungeonData.Route last = recordedSteps.getLast();
        if (!"interact".equalsIgnoreCase(last.secretType()) || !Arrays.equals(last.secret(), target)) return;
        clearRecordedStep();
        recordedSteps.removeLast();
        for (int[] point : last.locations()) {
            long[] world = RoomTransform.relativeToActual(recordDirection, recordAnchorX, recordAnchorZ,
                point[0], point[1], point[2]);
            walkPath.add(new Vec3(world[0] + .5, world[1], world[2] + .5));
        }
        recEtherwarps.addAll(copyList(last.etherwarps()));
        recInteracts.addAll(copyList(last.interacts()));
        recMines.addAll(copyList(last.mines()));
        recTnts.addAll(copyList(last.tnts()));
        recPearls.addAll(copyDoubleList(last.pearls()));
        recPearlAngles.addAll(copyDoubleList(last.pearlAngles()));
    }

    public static String recordingStatus() {
        if (!recording && pending.isEmpty()) return "§eno route recording";
        int draftNodes = walkPath.size();
        return "§a" + (recording ? "recording" : "review") + " " + recordedRoom + ": "
            + recordedSteps.size() + " completed steps, " + draftNodes + " draft nodes, "
            + (recordPearlMode ? "pearl" : "normal") + " mode";
    }

    public static String recordingHudText() {
        if (!recording) return null;
        return "§cRecording §f" + recordedSteps.size() + " steps §7| §f"
            + (recordPearlMode ? "Pearl" : "Normal");
    }

    // step finalization ported from SecretRoutes (GPL-3.0): utils/RouteRecording.java (newSecret)
    private static String finishRecordedStep(String secretType, int[] target) {
        boolean duplicate = recordedSteps.stream().anyMatch(step -> step.secret() != null && Arrays.equals(step.secret(), target));
        if (duplicate) return "§cthat secret is already in this recording";
        List<int[]> locations = recordedLocations();
        recordedSteps.add(new DungeonData.Route(locations,
            copyList(recEtherwarps), copyList(recInteracts), copyList(recMines), copyList(recTnts),
            copyDoubleList(recPearls), copyDoubleList(recPearlAngles), secretType, target.clone()));
        clearRecordedStep();
        return "§atagged " + secretType + " target as step " + recordedSteps.size();
    }

    private static String finishExitStep(int[] target) {
        String result = finishRecordedStep("exitroute", target);
        if (result.startsWith("§c")) return result;
        return result + "; " + stop().replaceFirst("^§a", "");
    }

    private static List<int[]> recordedLocations() {
        List<int[]> locations = new ArrayList<>();
        for (Vec3 point : walkPath) {
            long[] relative = RoomTransform.actualToRelative(recordDirection, recordAnchorX, recordAnchorZ,
                (int) Math.floor(point.x), (int) Math.floor(point.y), (int) Math.floor(point.z));
            int[] value = new int[]{(int) relative[0], (int) relative[1], (int) relative[2]};
            if (locations.isEmpty() || !Arrays.equals(locations.getLast(), value)) locations.add(value);
        }
        return locations;
    }

    private static void clearRecordedStep() {
        walkPath.clear();
        recEtherwarps.clear();
        recInteracts.clear();
        recMines.clear();
        recTnts.clear();
        recPearls.clear();
        recPearlAngles.clear();
    }

    public static String undoRecordedStep() {
        if (!recording) {
            if (pending.isEmpty() || pendingSaved) return "§cnot recording";
            discardUnsavedPreview();
            recording = true;
            pending = List.of();
            merged = false;
            pendingLiveKey = "";
        }
        if (recordedSteps.isEmpty()) return "§cno completed step to undo";
        clearRecordedStep();
        DungeonData.Route restored = recordedSteps.removeLast();
        for (int[] point : restored.locations()) {
            long[] world = RoomTransform.relativeToActual(recordDirection, recordAnchorX, recordAnchorZ,
                point[0], point[1], point[2]);
            walkPath.add(new Vec3(world[0] + .5, world[1], world[2] + .5));
        }
        recEtherwarps.addAll(copyList(restored.etherwarps()));
        recInteracts.addAll(copyList(restored.interacts()));
        recMines.addAll(copyList(restored.mines()));
        recTnts.addAll(copyList(restored.tnts()));
        recPearls.addAll(copyDoubleList(restored.pearls()));
        recPearlAngles.addAll(copyDoubleList(restored.pearlAngles()));
        return "§areopened the last step for editing; " + recordedSteps.size() + " completed steps remain";
    }

    public static String play() {
        if (pending.isEmpty()) return "§cnothing to play - record a route first";
        if (!merged) {
            pendingLiveKey = nextUserRouteKey(recordedRoom.toLowerCase(Locale.ROOT), pendingPearlMode);
            routeDestination(pendingPearlMode).put(pendingLiveKey, new ArrayList<>(pending));
            merged = true;
            room = ""; // force re-sync on next draw
        }
        OrionConfig cfg = ConstellationClient.cfg().orion;
        if (cfg != null && cfg.pearlRoutes != pendingPearlMode)
            return "§arecorded route preview ready; switch " + (pendingPearlMode ? "on" : "off") + " Prefer pearl-clips to display it";
        return "§ashowing recorded route (" + pending.size() + " steps)";
    }

    public static String save() {
        if (pending.isEmpty()) return "§cnothing to save - record a route first";
        String invalid = validatePendingForSave();
        if (invalid != null) return "§ccant save: " + invalid;
        String roomKey = recordedRoom.toLowerCase(Locale.ROOT);
        String key = pendingLiveKey.isEmpty() ? nextUserRouteKey(roomKey, pendingPearlMode) : pendingLiveKey;
        Map<String, List<DungeonData.Route>> savedRoutes = pendingPearlMode ? userPearlRoutes : userRoutes;
        Path file = pendingPearlMode ? USER_PEARL_FILE : USER_FILE;
        List<DungeonData.Route> previous = savedRoutes.get(key);
        savedRoutes.put(key, new ArrayList<>(pending));
        if (!persistUserRoutes(file, savedRoutes)) {
            if (previous == null) savedRoutes.remove(key);
            else savedRoutes.put(key, previous);
            return "§ccouldnt save route; check the log";
        }
        if (!merged) {
            routeDestination(pendingPearlMode).put(key, new ArrayList<>(pending));
            merged = true;
            room = ""; // force re-sync on next draw
        }
        pendingLiveKey = key;
        pendingSaved = true;
        return "§asaved alternate " + key + " for " + recordedRoom;
    }

    private static String validatePendingForSave() {
        DungeonData.Route last = pending.getLast();
        if (!"exit".equalsIgnoreCase(last.secretType()) && !"exitroute".equalsIgnoreCase(last.secretType()))
            return "finish the route with /cn route tag exit while looking at the exit";
        Set<String> secrets = new HashSet<>();
        for (DungeonData.Route step : pending) {
            if (step.secret() == null || step.secretType() == null || step.secretType().isBlank()) return "a step has no target";
            if (!secrets.add(Arrays.toString(step.secret()))) return "duplicate secret target";
            if (step.pearls().size() != step.pearlAngles().size()) return "pearl positions and angles dont match";
            for (double[] point : step.pearls())
                if (point.length < 3 || Arrays.stream(point).anyMatch(value -> !Double.isFinite(value))) return "invalid pearl position";
            for (double[] angle : step.pearlAngles())
                if (angle.length < 2 || Arrays.stream(angle).anyMatch(value -> !Double.isFinite(value))) return "invalid pearl angle";
        }
        return null;
    }

    public static String load() {
        if (recording || !pending.isEmpty() && !pendingSaved)
            return "§csave or clear the active route recording before reloading";
        DungeonData.ROUTES.keySet().removeIf(key -> key.contains(":user:"));
        DungeonData.PEARL_ROUTES.keySet().removeIf(key -> key.contains(":user:"));
        userRoutes.clear();
        userPearlRoutes.clear();
        loadUserRoutesFromDisk(USER_FILE, userRoutes);
        loadUserRoutesFromDisk(USER_PEARL_FILE, userPearlRoutes);
        mergeUserRoutesIntoLive();
        room = "";
        return "§areloaded user routes (" + userRoutes.size() + " normal, " + userPearlRoutes.size() + " pearl)";
    }

    public static String clearRoute() {
        discardUnsavedPreview();
        recording = false;
        walkPath.clear();
        recEtherwarps.clear();
        recInteracts.clear();
        recMines.clear();
        recTnts.clear();
        recPearls.clear();
        recPearlAngles.clear();
        recordedSteps.clear();
        pending = List.of();
        merged = false;
        pendingLiveKey = "";
        pendingSaved = false;
        return "§acleared";
    }

    private static void discardUnsavedPreview() {
        if (merged && !pendingSaved && !pendingLiveKey.isEmpty()) {
            routeDestination(pendingPearlMode).remove(pendingLiveKey);
            room = "";
        }
    }

    private static boolean sameRecordingRoom() {
        return RoomMatch.isMatched()
            && recordedRoom.equals(ConstellationClient.dungeon().currentRoom())
            && recordAnchorX == ConstellationClient.dungeon().roomCornerX()
            && recordAnchorZ == ConstellationClient.dungeon().roomCornerZ()
            && recordDirection == ConstellationClient.dungeon().roomDirection();
    }

    private static String clearCorruptRecording() {
        clearRoute();
        return "§crecording cancelled because the room changed";
    }

    public static String listRoutes() {
        String cur = ConstellationClient.dungeon().currentRoom().toLowerCase(Locale.ROOT);
        OrionConfig cfg = ConstellationClient.cfg().orion;
        Map<String, List<DungeonData.Route>> source = cfg != null && cfg.pearlRoutes ? userPearlRoutes : userRoutes;
        List<List<DungeonData.Route>> found = source.entrySet().stream()
            .filter(e -> e.getKey().startsWith(cur + ":user:"))
            .map(Map.Entry::getValue).toList();
        if (found.isEmpty()) return "§eno saved routes for current room";
        int total = found.stream().flatMap(Collection::stream).mapToInt(r -> r.locations().size()).sum();
        return "§a" + found.size() + " alternate route(s) for " + ConstellationClient.dungeon().currentRoom()
            + " (" + total + " total nodes) " + (!pending.isEmpty() && !pendingSaved ? "[+1 unsaved]" : "");
    }

    // ---- disk i/o (ported from cryptkit (GPL-3.0): feature/Routes.java) ----

    private static boolean persistUserRoutes(Path file, Map<String, List<DungeonData.Route>> source) {
        try {
            JsonObject root = new JsonObject();
            for (var e : source.entrySet()) {
                JsonArray arr = new JsonArray();
                for (DungeonData.Route r : e.getValue()) arr.add(routeToJson(r));
                root.add(e.getKey(), arr);
            }
            Files.createDirectories(file.getParent());
            Path temporary = file.resolveSibling(file.getFileName() + ".tmp");
            Files.writeString(temporary, GSON.toJson(root));
            try {
                Files.move(temporary, file, java.nio.file.StandardCopyOption.ATOMIC_MOVE,
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            } catch (java.nio.file.AtomicMoveNotSupportedException ignored) {
                Files.move(temporary, file, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
            return true;
        } catch (IOException e) {
            ConstellationClient.LOGGER.error("couldnt save user routes", e);
            return false;
        }
    }

    private static JsonObject routeToJson(DungeonData.Route r) {
        JsonObject o = new JsonObject();
        o.add("locations", triplesJson(r.locations()));
        o.add("etherwarps", triplesJson(r.etherwarps()));
        o.add("interacts", triplesJson(r.interacts()));
        o.add("mines", triplesJson(r.mines()));
        o.add("tnts", triplesJson(r.tnts()));
        o.add("enderpearls", doublesJson(r.pearls()));
        o.add("enderpearlangles", doublesJson(r.pearlAngles()));
        if (r.secret() != null) {
            JsonObject secret = new JsonObject();
            secret.addProperty("type", r.secretType() == null ? "interact" : r.secretType());
            secret.add("location", tripleJson(r.secret()));
            o.add("secret", secret);
        }
        return o;
    }

    private static JsonArray triplesJson(List<int[]> triples) {
        JsonArray arr = new JsonArray();
        for (int[] t : triples) arr.add(tripleJson(t));
        return arr;
    }

    private static JsonArray tripleJson(int[] t) {
        JsonArray triple = new JsonArray();
        triple.add(t[0]); triple.add(t[1]); triple.add(t[2]);
        return triple;
    }

    private static JsonArray doublesJson(List<double[]> values) {
        JsonArray arr = new JsonArray();
        for (double[] value : values) {
            JsonArray point = new JsonArray();
            for (double coordinate : value) point.add(coordinate);
            arr.add(point);
        }
        return arr;
    }

    private static void mergeUserRoutesIntoLive() {
        mergeRoutes(userRoutes, DungeonData.ROUTES);
        mergeRoutes(userPearlRoutes, DungeonData.PEARL_ROUTES);
    }

    private static void mergeRoutes(Map<String, List<DungeonData.Route>> source,
                                    Map<String, List<DungeonData.Route>> destination) {
        for (var entry : source.entrySet()) destination.put(entry.getKey(), new ArrayList<>(entry.getValue()));
    }

    private static void loadUserRoutesFromDisk(Path file, Map<String, List<DungeonData.Route>> destination) {
        try {
            if (!Files.exists(file)) return;
            String content = Files.readString(file);
            if (content.isBlank()) return;
            JsonObject root = GSON.fromJson(content, JsonObject.class);
            if (root == null) return;
            for (var entry : root.entrySet()) {
                if (!entry.getValue().isJsonArray()) continue;
                JsonArray arr = entry.getValue().getAsJsonArray();
                List<DungeonData.Route> routes = new ArrayList<>();
                for (var el : arr) {
                    if (!el.isJsonObject()) continue;
                    routes.add(jsonToRoute(el.getAsJsonObject()));
                }
                if (routes.isEmpty()) continue;
                String key = entry.getKey().toLowerCase(Locale.ROOT);
                if (key.contains(":user:")) {
                    destination.put(key, routes);
                } else {
                    // migration from the old format, where unrelated recordings were appended as steps
                    for (DungeonData.Route route : routes)
                        destination.put(nextUserRouteKey(key, destination == userPearlRoutes), new ArrayList<>(List.of(route)));
                }
            }
        } catch (Exception e) {
            ConstellationClient.LOGGER.error("couldnt read user routes", e);
        }
    }

    private static DungeonData.Route jsonToRoute(JsonObject o) {
        return new DungeonData.Route(
            triplesList(o, "locations"), triplesList(o, "etherwarps"),
            triplesList(o, "interacts"), triplesList(o, "mines"),
            triplesList(o, "tnts"), doublesList(o, "enderpearls", 3), doublesList(o, "enderpearlangles", 2),
            secretType(o), secretLocation(o));
    }

    private static List<int[]> triplesList(JsonObject o, String key) {
        List<int[]> out = new ArrayList<>();
        if (!o.has(key) || !o.get(key).isJsonArray()) return out;
        for (var el : o.getAsJsonArray(key)) {
            if (!el.isJsonArray()) continue;
            JsonArray a = el.getAsJsonArray();
            if (a.size() >= 3) out.add(new int[]{a.get(0).getAsInt(), a.get(1).getAsInt(), a.get(2).getAsInt()});
        }
        return out;
    }

    private static List<int[]> copyList(List<int[]> src) {
        List<int[]> out = new ArrayList<>(src.size());
        for (int[] a : src) out.add(a.clone());
        return out;
    }

    private static List<double[]> doublesList(JsonObject o, String key, int minimumLength) {
        List<double[]> out = new ArrayList<>();
        if (!o.has(key) || !o.get(key).isJsonArray()) return out;
        for (var el : o.getAsJsonArray(key)) {
            if (!el.isJsonArray()) continue;
            JsonArray a = el.getAsJsonArray();
            double[] value = new double[a.size()];
            for (int i = 0; i < a.size(); i++) value[i] = a.get(i).getAsDouble();
            if (value.length >= minimumLength && Arrays.stream(value).allMatch(Double::isFinite)) out.add(value);
        }
        return out;
    }

    private static String secretType(JsonObject o) {
        if (!o.has("secret") || !o.get("secret").isJsonObject()) return null;
        JsonObject secret = o.getAsJsonObject("secret");
        return secret.has("type") ? secret.get("type").getAsString() : "interact";
    }

    private static int[] secretLocation(JsonObject o) {
        if (!o.has("secret") || !o.get("secret").isJsonObject()) return null;
        JsonObject secret = o.getAsJsonObject("secret");
        if (!secret.has("location") || !secret.get("location").isJsonArray()) return null;
        JsonArray a = secret.getAsJsonArray("location");
        return a.size() < 3 ? null : new int[]{a.get(0).getAsInt(), a.get(1).getAsInt(), a.get(2).getAsInt()};
    }

    private static List<double[]> copyDoubleList(List<double[]> src) {
        List<double[]> out = new ArrayList<>(src.size());
        for (double[] a : src) out.add(a.clone());
        return out;
    }

    private static Map<String, List<DungeonData.Route>> routeDestination(boolean pearl) {
        return pearl ? DungeonData.PEARL_ROUTES : DungeonData.ROUTES;
    }

    private static String nextUserRouteKey(String roomKey, boolean pearl) {
        Map<String, List<DungeonData.Route>> saved = pearl ? userPearlRoutes : userRoutes;
        Map<String, List<DungeonData.Route>> live = routeDestination(pearl);
        int index = 1;
        while (saved.containsKey(roomKey + ":user:" + index)
            || live.containsKey(roomKey + ":user:" + index)) index++;
        return roomKey + ":user:" + index;
    }
}
