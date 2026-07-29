package com.froggylord.constellation.constellation;

import com.froggylord.constellation.ConstellationClient;
import com.froggylord.constellation.config.ApolloConfig;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.effect.MobEffectInstance;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.List;
import java.util.Locale;

// ported from Devonian (GPL-3.0-only): features/misc/FPSDisplay.kt, PingDisplay.kt, TpsDisplay.kt, SpeedDisplay.kt
// ported from NoFrills (GPL-3.0-only): hud/elements/FPS.java, hud/elements/Ping.java
public final class ApolloTelemetry {
    public record Metrics(int fps, double fpsAverage, int ping, double pingAverage, double pingMedian,
                          int tpsCurrent, double tpsAverage, int tpsMinimum, int tpsMaximum,
                          double blocksPerSecond, double verticalSpeed, int skyblockSpeed) {}
    public record EffectRow(String name, String duration, int seconds, boolean infinite) {}

    private static ApolloConfig cfg;
    private static final Deque<Sample> fpsSamples = new ArrayDeque<>();
    private static final Deque<Sample> pingSamples = new ArrayDeque<>();
    private static final Deque<Long> serverTicks = new ArrayDeque<>();
    private static final Deque<TpsSample> tpsSamples = new ArrayDeque<>();
    private static final Deque<Double> horizontalSamples = new ArrayDeque<>();
    private static final Deque<Double> verticalSamples = new ArrayDeque<>();
    private static Object level;
    private static double lastX, lastY, lastZ;
    private static boolean positioned;
    private static int tickCounter;
    private static Metrics metrics = new Metrics(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0);

    private record Sample(long time, int value) {}
    private record TpsSample(long time, int value) {}

    private ApolloTelemetry() {}

    public static void init(ApolloConfig config) {
        cfg = config;
        ClientTickEvents.END_CLIENT_TICK.register(client -> tick(client));
    }

    public static void registerCommands(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        dispatcher.register(LiteralArgumentBuilder.<FabricClientCommandSource>literal("apollohud")
            .executes(c -> status())
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("toggle").executes(c -> {
                cfg.enabled = !cfg.enabled; save(); return status();
            }))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("samples")
                .then(RequiredArgumentBuilder.<FabricClientCommandSource, Integer>argument("seconds", IntegerArgumentType.integer(1, 30))
                    .executes(c -> { cfg.performanceSampleSeconds = IntegerArgumentType.getInteger(c, "seconds"); save(); return status(); })))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("effects")
                .then(RequiredArgumentBuilder.<FabricClientCommandSource, Integer>argument("rows", IntegerArgumentType.integer(1, 20))
                    .executes(c -> { cfg.effectsMaxRows = IntegerArgumentType.getInteger(c, "rows"); save(); return status(); })))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("option")
                .then(RequiredArgumentBuilder.<FabricClientCommandSource, String>argument("name", StringArgumentType.word())
                    .then(RequiredArgumentBuilder.<FabricClientCommandSource, String>argument("state", StringArgumentType.word())
                        .executes(c -> option(StringArgumentType.getString(c, "name"),
                            StringArgumentType.getString(c, "state")))))));
    }

    public static void onServerTick() {
        long now = System.currentTimeMillis();
        serverTicks.addLast(now);
        trim(now);
    }

    private static void tick(Minecraft mc) {
        if (mc.level != level) {
            level = mc.level;
            resetTransient();
        }
        if (mc.player == null || mc.level == null) return;
        long now = System.currentTimeMillis();
        int fps = mc.getFps();
        int ping = 0;
        if (mc.getConnection() != null) {
            var info = mc.getConnection().getPlayerInfo(mc.player.getUUID());
            if (info != null) ping = Math.max(0, info.getLatency());
        }
        fpsSamples.addLast(new Sample(now, fps));
        if (++tickCounter >= 20) {
            tickCounter = 0;
            pingSamples.addLast(new Sample(now, ping));
            int current = countAfter(serverTicks, now - 1000);
            tpsSamples.addLast(new TpsSample(now, current));
        }
        movement(mc);
        trim(now);
        metrics = calculate(fps, ping, now, mc);
    }

    private static void movement(Minecraft mc) {
        double x = mc.player.getX(), y = mc.player.getY(), z = mc.player.getZ();
        if (positioned) {
            horizontalSamples.addLast(Math.hypot(x - lastX, z - lastZ) * 20);
            verticalSamples.addLast((y - lastY) * 20);
            int max = Math.clamp(cfg.movementSampleTicks, 1, 100);
            while (horizontalSamples.size() > max) horizontalSamples.removeFirst();
            while (verticalSamples.size() > max) verticalSamples.removeFirst();
        }
        lastX = x; lastY = y; lastZ = z; positioned = true;
    }

    private static Metrics calculate(int fps, int ping, long now, Minecraft mc) {
        int current = countAfter(serverTicks, now - 1000);
        int min = tpsSamples.stream().mapToInt(TpsSample::value).min().orElse(current);
        int max = tpsSamples.stream().mapToInt(TpsSample::value).max().orElse(current);
        double tpsAvg = averageTps(now);
        int statSpeed = (int) Math.round(mc.player.getAttributeBaseValue(Attributes.MOVEMENT_SPEED) * 1000);
        double horizontal = cfg.movementAverage ? averageDouble(horizontalSamples)
            : horizontalSamples.isEmpty() ? 0 : horizontalSamples.peekLast();
        double vertical = cfg.movementAverage ? averageDouble(verticalSamples)
            : verticalSamples.isEmpty() ? 0 : verticalSamples.peekLast();
        return new Metrics(fps, average(fpsSamples), ping, average(pingSamples), median(pingSamples),
            current, tpsAvg, min, max, horizontal, vertical, statSpeed);
    }

    private static void trim(long now) {
        long cutoff = now - Math.clamp(cfg == null ? 5 : cfg.performanceSampleSeconds, 1, 30) * 1000L;
        while (!fpsSamples.isEmpty() && fpsSamples.peekFirst().time() < cutoff) fpsSamples.removeFirst();
        while (!pingSamples.isEmpty() && pingSamples.peekFirst().time() < cutoff) pingSamples.removeFirst();
        while (!serverTicks.isEmpty() && serverTicks.peekFirst() < cutoff) serverTicks.removeFirst();
        while (!tpsSamples.isEmpty() && tpsSamples.peekFirst().time() < cutoff) tpsSamples.removeFirst();
    }

    private static int countAfter(Deque<Long> values, long cutoff) {
        int count = 0;
        for (long value : values) if (value >= cutoff) count++;
        return count;
    }

    private static double averageTps(long now) {
        if (serverTicks.isEmpty()) return 0;
        long oldest = serverTicks.peekFirst();
        double seconds = Math.max(1, Math.min(Math.clamp(cfg.performanceSampleSeconds, 1, 30) * 1000L, now - oldest)) / 1000.0;
        return Math.min(20, serverTicks.size() / seconds);
    }
    private static double average(Deque<Sample> values) {
        return values.stream().mapToInt(Sample::value).average().orElse(0);
    }
    private static double median(Deque<Sample> values) {
        int[] sorted = values.stream().mapToInt(Sample::value).sorted().toArray();
        if (sorted.length == 0) return 0;
        int middle = sorted.length / 2;
        return sorted.length % 2 == 0 ? (sorted[middle - 1] + sorted[middle]) / 2.0 : sorted[middle];
    }
    private static double averageDouble(Deque<Double> values) {
        return values.stream().mapToDouble(Double::doubleValue).average().orElse(0);
    }

    public static ApolloConfig config() { return cfg; }
    public static Metrics metrics() { return metrics; }
    public static boolean scope(boolean hypixelOnly) {
        return cfg != null && cfg.enabled && (!hypixelOnly || ConstellationClient.loc().onHypixel());
    }
    public static String localClock() {
        String pattern = cfg.locationTwelveHourClock
            ? (cfg.locationShowSeconds ? "h:mm:ss a" : "h:mm a")
            : (cfg.locationShowSeconds ? "HH:mm:ss" : "HH:mm");
        return LocalTime.now().format(DateTimeFormatter.ofPattern(pattern, Locale.ROOT));
    }
    public static String facing(float yaw) {
        String[] names = {"South", "West", "North", "East"};
        return names[Math.floorMod(Math.round(yaw / 90f), 4)];
    }
    public static List<EffectRow> effects() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return List.of();
        ArrayList<EffectRow> out = new ArrayList<>();
        for (MobEffectInstance effect : mc.player.getActiveEffects()) {
            boolean infinite = effect.isInfiniteDuration();
            if (infinite && cfg.effectsHideInfinite) continue;
            String name = effect.getEffect().value().getDisplayName().getString();
            if (cfg.effectsShowAmplifier && effect.getAmplifier() > 0) name += " " + roman(effect.getAmplifier() + 1);
            int seconds = infinite ? Integer.MAX_VALUE : Math.max(0, effect.getDuration() / 20);
            String duration = infinite ? "Infinite" : String.format(Locale.ROOT, "%d:%02d", seconds / 60, seconds % 60);
            out.add(new EffectRow(name, duration, seconds, infinite));
        }
        Comparator<EffectRow> order = Comparator.comparingInt(EffectRow::seconds);
        if (!cfg.effectsSortShortest) order = order.reversed();
        out.sort(order.thenComparing(EffectRow::name));
        return out.stream().limit(Math.clamp(cfg.effectsMaxRows, 1, 20)).toList();
    }
    private static String roman(int value) {
        return switch (Math.clamp(value, 1, 10)) {
            case 1 -> "I"; case 2 -> "II"; case 3 -> "III"; case 4 -> "IV"; case 5 -> "V";
            case 6 -> "VI"; case 7 -> "VII"; case 8 -> "VIII"; case 9 -> "IX"; default -> "X";
        };
    }

    private static int status() {
        local("Apollo " + on(cfg.enabled) + ", performance " + on(cfg.performanceHud) + ", location "
            + on(cfg.locationHud) + ", movement " + on(cfg.movementHud) + ", vitals "
            + on(cfg.vitalsHud) + ", effects " + on(cfg.effectsHud) + ".");
        return 1;
    }
    private static int option(String name, String raw) {
        Boolean value = bool(raw);
        if (value == null) { local("State must be on or off."); return 0; }
        switch (name.toLowerCase(Locale.ROOT)) {
            case "performance" -> cfg.performanceHud = value;
            case "fps" -> cfg.performanceShowFps = value;
            case "fpsaverage" -> cfg.performanceShowFpsAverage = value;
            case "ping" -> cfg.performanceShowPing = value;
            case "pingaverage" -> cfg.performanceShowPingAverage = value;
            case "pingmedian" -> cfg.performanceShowPingMedian = value;
            case "tps" -> cfg.performanceShowTpsCurrent = value;
            case "tpsaverage" -> cfg.performanceShowTpsAverage = value;
            case "tpsminimum" -> cfg.performanceShowTpsMinimum = value;
            case "tpsmaximum" -> cfg.performanceShowTpsMaximum = value;
            case "location" -> cfg.locationHud = value;
            case "coordinates" -> cfg.locationShowCoordinates = value;
            case "decimal" -> cfg.locationDecimalCoordinates = value;
            case "facing" -> cfg.locationShowFacing = value;
            case "yaw" -> cfg.locationShowYaw = value;
            case "pitch" -> cfg.locationShowPitch = value;
            case "dimension" -> cfg.locationShowDimension = value;
            case "clock" -> cfg.locationShowLocalClock = value;
            case "twelvehour" -> cfg.locationTwelveHourClock = value;
            case "seconds" -> cfg.locationShowSeconds = value;
            case "movement" -> cfg.movementHud = value;
            case "statspeed" -> cfg.movementShowSkyblockSpeed = value;
            case "bps" -> cfg.movementShowBlocksPerSecond = value;
            case "vertical" -> cfg.movementShowVerticalSpeed = value;
            case "movementaverage" -> cfg.movementAverage = value;
            case "vitals" -> cfg.vitalsHud = value;
            case "health" -> cfg.vitalsShowHealth = value;
            case "healthpercent" -> cfg.vitalsShowHealthPercent = value;
            case "mana" -> cfg.vitalsShowMana = value;
            case "manapercent" -> cfg.vitalsShowManaPercent = value;
            case "overflow" -> cfg.vitalsShowOverflowMana = value;
            case "defense" -> cfg.vitalsShowDefense = value;
            case "effectivehealth" -> cfg.vitalsShowEffectiveHealth = value;
            case "effects" -> cfg.effectsHud = value;
            case "amplifier" -> cfg.effectsShowAmplifier = value;
            case "duration" -> cfg.effectsShowDuration = value;
            case "shortest" -> cfg.effectsSortShortest = value;
            case "infinite" -> cfg.effectsHideInfinite = !value;
            default -> { local("Unknown Apollo HUD option."); return 0; }
        }
        save(); return status();
    }
    private static Boolean bool(String value) {
        return switch (value.toLowerCase(Locale.ROOT)) {
            case "on", "true", "yes", "1" -> true;
            case "off", "false", "no", "0" -> false;
            default -> null;
        };
    }
    private static String on(boolean value) { return value ? "on" : "off"; }
    private static void local(String text) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) mc.player.sendSystemMessage(Component.literal("\u00a7b[Apollo HUD] \u00a7f" + text));
    }
    private static void resetTransient() {
        fpsSamples.clear(); pingSamples.clear(); serverTicks.clear(); tpsSamples.clear();
        horizontalSamples.clear(); verticalSamples.clear(); positioned = false; tickCounter = 0;
    }
    private static void save() { ConstellationClient.saveConfig(); }
}
