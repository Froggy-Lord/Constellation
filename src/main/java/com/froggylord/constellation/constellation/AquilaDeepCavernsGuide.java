package com.froggylord.constellation.constellation;

import com.froggylord.constellation.ConstellationClient;
import com.froggylord.constellation.config.AquilaConfig;
import com.froggylord.constellation.core.LocationManager.SkyblockArea;
import com.froggylord.constellation.render.WorldRenderer;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.awt.Color;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

// ported from SkyHanni (LGPL-3.0-or-later): features/mining/DeepCavernsGuide.kt
// ported from SkyHanni (LGPL-3.0-or-later): utils/ParkourHelper.kt
// ported from SkyHanni (LGPL-3.0-or-later): config/features/mining/caverns/DeepCavernsGuideConfig.kt
// ported from SkyHanni Repo (MIT): constants/DeepCavernsParkour.json
public final class AquilaDeepCavernsGuide {
    public record State(int current, int total, int next, int remaining, double distance) {}

    private static final String LOCKED_LIFT =
        "[NPC] Lift Operator: Venture down into the Lapis Quarry to unlock my Lift Menu!";
    private static final List<Vec3> ROUTE = new ArrayList<>();
    private static AquilaConfig cfg;
    private static boolean initialized;
    private static boolean showing;
    private static int current = -1;
    private static Object levelIdentity;
    private static boolean liftPrompt;
    private static boolean completionSent;

    private AquilaDeepCavernsGuide() {}

    public static void init(AquilaConfig config) {
        cfg = config;
        loadRoute();
        if (initialized) return;
        initialized = true;
        ClientReceiveMessageEvents.ALLOW_GAME.register((message, overlay) -> {
            if (!overlay && active() && cfg.deepCavernsGuideAutoChat && clean(message.getString()).equals(LOCKED_LIFT)) start(true);
            return true;
        });
        ConstellationClient.tick().every(1, "aquila-deep-caverns-guide", AquilaDeepCavernsGuide::tick);
        ClientPlayConnectionEvents.JOIN.register((a, b, c) -> reset());
        ClientPlayConnectionEvents.DISCONNECT.register((a, b) -> reset());
    }

    private static void loadRoute() {
        if (!ROUTE.isEmpty()) return;
        try (var stream = AquilaDeepCavernsGuide.class.getResourceAsStream(
            "/assets/constellation/mining/deepCavernsParkour.json")) {
            if (stream == null) throw new IllegalStateException("missing Deep Caverns route");
            var root = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
            for (JsonElement element : root.getAsJsonArray("locations")) {
                String[] parts = element.getAsString().split(":");
                if (parts.length != 3) throw new IllegalArgumentException("invalid Deep Caverns route point");
                ROUTE.add(new Vec3(Double.parseDouble(parts[0]), Double.parseDouble(parts[1]), Double.parseDouble(parts[2])));
            }
            if (ROUTE.size() != 92) throw new IllegalStateException("expected 92 Deep Caverns route points");
        } catch (Exception exception) {
            ROUTE.clear();
            ConstellationClient.LOGGER.error("Could not load Deep Caverns guide route", exception);
        }
    }

    private static boolean active() {
        return cfg != null && cfg.enabled && cfg.deepCavernsGuide
            && ConstellationClient.loc().area() == SkyblockArea.DEEP_CAVERNS && !ROUTE.isEmpty();
    }

    private static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != levelIdentity) {
            levelIdentity = mc.level;
            clearState();
        }
        if (!active() || mc.player == null) {
            clearState();
            return;
        }
        detectLift(mc);
        if (!showing) return;
        double detection = Math.clamp(cfg.deepCavernsGuideDetectionTenths, 10, 100) / 10.0;
        Vec3 player = mc.player.position();
        if (current < 0) {
            if (mc.player.onGround() && distance(player, ROUTE.getFirst().add(.5, 1, .5)) <= detection) current = 0;
            else if (mc.player.onGround() && cfg.deepCavernsGuideAutoRecover) {
                int recovered = closest(player, Math.clamp(cfg.deepCavernsGuideRecoveryRange, 4, 40));
                if (recovered >= 0) current = recovered;
            }
        } else if (mc.player.onGround()) {
            int reached = current;
            int maximum = Math.min(ROUTE.size() - 1, current + Math.clamp(cfg.deepCavernsGuideLookAhead, 1, 30) + 2);
            for (int index = current; index <= maximum; index++) {
                if (distance(player, ROUTE.get(index).add(.5, 1, .5)) < detection) reached = index;
            }
            current = reached;
        }
        if (current >= ROUTE.size() - 1 && distance(player, ROUTE.getLast().add(.5, 1, .5)) < detection) complete();
    }

    private static void detectLift(Minecraft mc) {
        if (!(mc.gui.screen() instanceof AbstractContainerScreen<?> screen) || !clean(screen.getTitle().getString()).equals("Lift")) {
            liftPrompt = false;
            return;
        }
        liftPrompt = !showing && cfg.deepCavernsGuideLiftButton && liftLocked(screen);
    }

    private static boolean liftLocked(AbstractContainerScreen<?> screen) {
        if (screen.getMenu().slots.size() <= 31) return false;
        String name = clean(screen.getMenu().getSlot(31).getItem().getHoverName().getString());
        return !name.equals("Obsidian Sanctuary");
    }

    public static void drawSlot(GuiGraphicsExtractor graphics, AbstractContainerScreen<?> screen, Slot slot) {
        if (!liftPrompt || screen == null || slot == null || slot.index != 49 || !clean(screen.getTitle().getString()).equals("Lift")) return;
        graphics.fill(slot.x, slot.y, slot.x + 16, slot.y + 16, 0xA00055FF);
        graphics.text(Minecraft.getInstance().font, "GO", slot.x + 2, slot.y + 4, 0xFFFFFFFF, true);
    }

    public static boolean shouldStartClick(AbstractContainerScreen<?> screen, Slot slot, int slotId) {
        if (!liftPrompt || screen == null || slot == null || slotId != 49 || !clean(screen.getTitle().getString()).equals("Lift")) return false;
        start(true);
        return true;
    }

    public static void draw(WorldRenderer.Ctx ctx) {
        if (!active() || !showing) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        if (current < 0) {
            if (cfg.deepCavernsGuideShowStart) drawStart(ctx, mc.player.position());
            return;
        }
        int end = Math.min(ROUTE.size() - 1, current + Math.clamp(cfg.deepCavernsGuideLookAhead, 1, 30));
        for (int index = current; index <= end; index++) {
            Vec3 point = ROUTE.get(index);
            int color = color(index);
            if (cfg.deepCavernsGuideBoxes)
                ctx.box(new AABB(point.x - 1, point.y - 1, point.z - 1, point.x + 2, point.y + 2, point.z + 2),
                    color, cfg.deepCavernsGuideThroughWalls);
            if (cfg.deepCavernsGuideLabels) {
                String label = Integer.toString(index + 1);
                if (cfg.deepCavernsGuideDistance && index == current)
                    label += "  " + String.format(Locale.ROOT, "%.1fm", distance(mc.player.position(), point.add(.5, 1, .5)));
                ctx.label(point.add(.5, 2.4, .5), label, 0xFF000000 | (color & 0xFFFFFF), cfg.deepCavernsGuideThroughWalls);
            }
            if (cfg.deepCavernsGuideLines && index < end)
                ctx.line(point.add(.5, 1, .5), ROUTE.get(index + 1).add(.5, 1, .5), color, cfg.deepCavernsGuideThroughWalls);
        }
    }

    private static void drawStart(WorldRenderer.Ctx ctx, Vec3 player) {
        Vec3 start = ROUTE.getFirst();
        int color = color(0);
        if (cfg.deepCavernsGuideBoxes)
            ctx.highlight(new AABB(start.x - 1, start.y - 1, start.z - 1, start.x + 2, start.y + 2, start.z + 2),
                color, cfg.deepCavernsGuideThroughWalls);
        if (cfg.deepCavernsGuideStartBeam)
            ctx.beam(start.x + .5, start.y, start.z + .5, color, 12, cfg.deepCavernsGuideThroughWalls);
        String label = "Guide Start";
        if (cfg.deepCavernsGuideDistance)
            label += "  " + String.format(Locale.ROOT, "%.1fm", distance(player, start.add(.5, 1, .5)));
        ctx.label(start.add(.5, 2.4, .5), label, 0xFF000000 | (color & 0xFFFFFF), cfg.deepCavernsGuideThroughWalls);
    }

    private static int color(int index) {
        if (!cfg.deepCavernsGuideRainbow) return cfg.deepCavernsGuideLineColor;
        float seconds = Math.clamp(cfg.deepCavernsGuideRainbowSeconds, 1, 20);
        float hue = (float) ((System.currentTimeMillis() % (long) (seconds * 1000)) / (seconds * 1000.0) - index / 12.0);
        hue = hue - (float) Math.floor(hue);
        float saturation = Math.clamp(cfg.deepCavernsGuideRainbowSaturation, 0, 100) / 100f;
        float brightness = Math.clamp(cfg.deepCavernsGuideRainbowBrightness, 1, 100) / 100f;
        return Math.clamp(cfg.deepCavernsGuideRainbowAlpha, 1, 255) << 24
            | Color.HSBtoRGB(hue, saturation, brightness) & 0xFFFFFF;
    }

    private static int closest(Vec3 player, double maximum) {
        int result = -1;
        double best = maximum;
        for (int index = 0; index < ROUTE.size(); index++) {
            double distance = distance(player, ROUTE.get(index).add(.5, 1, .5));
            if (distance < best) {
                best = distance;
                result = index;
            }
        }
        return result;
    }

    private static double distance(Vec3 one, Vec3 two) {
        return one.distanceTo(two);
    }

    private static void start(boolean message) {
        if (!active()) return;
        showing = true;
        current = -1;
        completionSent = false;
        liftPrompt = false;
        if (message) local("Guide started. Follow the route to Rhys.");
    }

    private static void complete() {
        showing = false;
        current = ROUTE.size() - 1;
        if (!completionSent && cfg.deepCavernsGuideCompletionChat) {
            completionSent = true;
            local("Route complete.");
        }
    }

    public static State state() {
        if (!active() || !showing) return null;
        Minecraft mc = Minecraft.getInstance();
        int next = Math.clamp(current + 1, 0, ROUTE.size() - 1);
        double distance = mc.player == null ? 0 : distance(mc.player.position(), ROUTE.get(next).add(.5, 1, .5));
        return new State(Math.max(0, current + 1), ROUTE.size(), next + 1, ROUTE.size() - Math.max(0, current + 1), distance);
    }

    public static AquilaConfig config() {
        return cfg;
    }

    public static void registerCommands(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        dispatcher.register(LiteralArgumentBuilder.<FabricClientCommandSource>literal("deepguide")
            .executes(context -> status())
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("start").executes(context -> { start(true); return 1; }))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("stop").executes(context -> { clearState(); return status(); }))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("restart").executes(context -> { start(true); return 1; }))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("skip")
                .executes(context -> move(1))
                .then(RequiredArgumentBuilder.<FabricClientCommandSource, Integer>argument("amount", IntegerArgumentType.integer(1, 92))
                    .executes(context -> move(IntegerArgumentType.getInteger(context, "amount")))))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("back")
                .executes(context -> move(-1))
                .then(RequiredArgumentBuilder.<FabricClientCommandSource, Integer>argument("amount", IntegerArgumentType.integer(1, 92))
                    .executes(context -> move(-IntegerArgumentType.getInteger(context, "amount")))))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("lookahead")
                .then(RequiredArgumentBuilder.<FabricClientCommandSource, Integer>argument("count", IntegerArgumentType.integer(1, 30))
                    .executes(context -> { cfg.deepCavernsGuideLookAhead = IntegerArgumentType.getInteger(context, "count"); save(); return status(); })))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("detection")
                .then(RequiredArgumentBuilder.<FabricClientCommandSource, Integer>argument("tenths", IntegerArgumentType.integer(10, 100))
                    .executes(context -> { cfg.deepCavernsGuideDetectionTenths = IntegerArgumentType.getInteger(context, "tenths"); save(); return status(); })))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("recovery")
                .then(RequiredArgumentBuilder.<FabricClientCommandSource, Integer>argument("blocks", IntegerArgumentType.integer(4, 40))
                    .executes(context -> { cfg.deepCavernsGuideRecoveryRange = IntegerArgumentType.getInteger(context, "blocks"); save(); return status(); })))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("rainbow")
                .then(RequiredArgumentBuilder.<FabricClientCommandSource, Integer>argument("seconds", IntegerArgumentType.integer(1, 20))
                    .then(RequiredArgumentBuilder.<FabricClientCommandSource, Integer>argument("saturation", IntegerArgumentType.integer(0, 100))
                        .then(RequiredArgumentBuilder.<FabricClientCommandSource, Integer>argument("brightness", IntegerArgumentType.integer(1, 100))
                            .executes(context -> rainbow(context))))))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("alpha")
                .then(RequiredArgumentBuilder.<FabricClientCommandSource, Integer>argument("value", IntegerArgumentType.integer(1, 255))
                    .executes(context -> {
                        cfg.deepCavernsGuideRainbowAlpha = IntegerArgumentType.getInteger(context, "value");
                        save();
                        return status();
                    })))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("color")
                .then(RequiredArgumentBuilder.<FabricClientCommandSource, String>argument("argb", StringArgumentType.word())
                    .executes(context -> color(StringArgumentType.getString(context, "argb")))))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("option")
                .then(RequiredArgumentBuilder.<FabricClientCommandSource, String>argument("name", StringArgumentType.word())
                    .then(RequiredArgumentBuilder.<FabricClientCommandSource, String>argument("state", StringArgumentType.word())
                        .executes(context -> option(StringArgumentType.getString(context, "name"),
                            StringArgumentType.getString(context, "state")))))));
    }

    private static int rainbow(com.mojang.brigadier.context.CommandContext<FabricClientCommandSource> context) {
        cfg.deepCavernsGuideRainbowSeconds = IntegerArgumentType.getInteger(context, "seconds");
        cfg.deepCavernsGuideRainbowSaturation = IntegerArgumentType.getInteger(context, "saturation");
        cfg.deepCavernsGuideRainbowBrightness = IntegerArgumentType.getInteger(context, "brightness");
        save();
        return status();
    }

    private static int move(int amount) {
        if (!active()) {
            local("Enter the Deep Caverns first.");
            return 0;
        }
        if (!showing) start(false);
        current = Math.clamp(Math.max(0, current) + amount, 0, ROUTE.size() - 1);
        return status();
    }

    private static int status() {
        local("Guide " + (showing ? "active" : "inactive") + ", point " + Math.max(0, current + 1) + "/"
            + ROUTE.size() + ", look-ahead " + cfg.deepCavernsGuideLookAhead + ", "
            + (cfg.deepCavernsGuideRainbow ? "rainbow" : "monochrome") + ".");
        return 1;
    }

    private static int option(String raw, String state) {
        Boolean value = parse(state);
        if (value == null) {
            local("State must be on or off.");
            return 0;
        }
        switch (raw.toLowerCase(Locale.ROOT)) {
            case "enabled" -> cfg.deepCavernsGuide = value;
            case "hud" -> cfg.deepCavernsGuideHud = value;
            case "rainbow" -> cfg.deepCavernsGuideRainbow = value;
            case "boxes" -> cfg.deepCavernsGuideBoxes = value;
            case "lines" -> cfg.deepCavernsGuideLines = value;
            case "labels" -> cfg.deepCavernsGuideLabels = value;
            case "distance" -> cfg.deepCavernsGuideDistance = value;
            case "walls" -> cfg.deepCavernsGuideThroughWalls = value;
            case "autochat" -> cfg.deepCavernsGuideAutoChat = value;
            case "liftbutton" -> cfg.deepCavernsGuideLiftButton = value;
            case "recover" -> cfg.deepCavernsGuideAutoRecover = value;
            case "showstart" -> cfg.deepCavernsGuideShowStart = value;
            case "startbeam" -> cfg.deepCavernsGuideStartBeam = value;
            case "completion" -> cfg.deepCavernsGuideCompletionChat = value;
            case "hudprogress" -> cfg.deepCavernsGuideHudShowProgress = value;
            case "hudnext" -> cfg.deepCavernsGuideHudShowNext = value;
            case "hudremaining" -> cfg.deepCavernsGuideHudShowRemaining = value;
            case "huddistance" -> cfg.deepCavernsGuideHudShowDistance = value;
            default -> {
                local("Unknown option. Use enabled, hud, rainbow, boxes, lines, labels, distance, walls, autochat, liftbutton, recover, showstart, startbeam, completion, hudprogress, hudnext, hudremaining, or huddistance.");
                return 0;
            }
        }
        if (!cfg.deepCavernsGuide) clearState();
        save();
        return status();
    }

    private static int color(String raw) {
        try {
            String clean = raw.startsWith("#") ? raw.substring(1) : raw;
            if (clean.length() != 8) throw new NumberFormatException();
            cfg.deepCavernsGuideLineColor = (int) Long.parseLong(clean, 16);
            save();
            return status();
        } catch (NumberFormatException exception) {
            local("Color must be an eight-digit ARGB hex value.");
            return 0;
        }
    }

    private static Boolean parse(String raw) {
        return switch (raw.toLowerCase(Locale.ROOT)) {
            case "on", "true", "yes", "1" -> true;
            case "off", "false", "no", "0" -> false;
            default -> null;
        };
    }

    private static String clean(String raw) {
        String clean = ChatFormatting.stripFormatting(raw);
        return clean == null ? "" : clean.trim();
    }

    private static void reset() {
        levelIdentity = null;
        clearState();
    }

    private static void clearState() {
        showing = false;
        current = -1;
        liftPrompt = false;
        completionSent = false;
    }

    private static void save() {
        ConstellationClient.saveConfig();
    }

    private static void local(String text) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) mc.player.sendSystemMessage(Component.literal("\u00a73[Deep Caverns] \u00a7f" + text));
    }
}
