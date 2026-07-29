package com.froggylord.constellation.constellation;

import com.froggylord.constellation.ConstellationClient;
import com.froggylord.constellation.config.HerculesConfig;
import com.froggylord.constellation.core.LocationManager;
import com.froggylord.constellation.data.TabList;
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
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// ported from SkyHanni (LGPL-3.0-or-later): features/garden/GardenPlotApi.kt
// ported from SkyHanni (LGPL-3.0-or-later): features/garden/pests/SprayDisplay.kt
// ported from SkyHanni (LGPL-3.0-or-later): features/garden/pests/SprayType.kt
public final class HerculesSprays {
    public record SprayRow(String label, String value) {}
    private static final Pattern SPRAYED = Pattern.compile("^SPRAYONATOR! You sprayed Plot - (?<plot>.+) with (?<spray>Compost|Plant Matter|Dung|Honey Jar|Tasty Cheese|Jelly|Moondew)!$", Pattern.CASE_INSENSITIVE);
    private static final Pattern TAB = Pattern.compile("^Spray:\\s*(?<spray>[\\w ]+?)(?:\\s*\\((?<time>[^)]+)\\))?$", Pattern.CASE_INSENSITIVE);
    private static final String WASHER = "SPLASH! Your Garden was cleared of all active Sprayonator effects!";
    private static final Set<String> TYPES = Set.of("Compost","Plant Matter","Dung","Honey Jar","Tasty Cheese","Jelly","Moondew");
    private static HerculesConfig cfg;
    private static boolean wasInGarden;

    private HerculesSprays() {}

    public static void init(HerculesConfig config) {
        cfg = config;
        maps();
        ClientReceiveMessageEvents.GAME.register((message, overlay) -> {
            if (!overlay) onChat(clean(message.getString()));
        });
        ClientPlayConnectionEvents.JOIN.register((a, b, c) -> wasInGarden = false);
        ClientPlayConnectionEvents.DISCONNECT.register((a, b) -> wasInGarden = false);
        ConstellationClient.tick().every(5, "hercules-sprays", HerculesSprays::tick);
    }

    private static void onChat(String line) {
        if (!active() || line.isBlank()) return;
        if (line.equalsIgnoreCase(WASHER)) {
            clearAll(false);
            return;
        }
        Matcher matcher = SPRAYED.matcher(line);
        if (!matcher.matches()) return;
        Integer plot = HerculesPests.plotId(matcher.group("plot"));
        String spray = type(matcher.group("spray"));
        if (plot == null || plot == 0 || spray == null) return;
        set(plot, spray, Math.max(1, cfg.sprayDurationMinutes) * 60_000L, false);
    }

    private static void tick() {
        boolean garden = inGarden();
        if (!garden || cfg == null || !cfg.enabled || !cfg.sprayTracker) {
            wasInGarden = garden;
            return;
        }
        maps();
        readCurrentPlot();
        notifyExpired(!wasInGarden && cfg.sprayNotifyWhileAway);
        wasInGarden = true;
    }

    private static void readCurrentPlot() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        Integer plot = HerculesPests.plotAt(mc.player.getX(), mc.player.getZ());
        if (plot == null || plot == 0) return;
        for (String line : TabList.lines()) {
            Matcher matcher = TAB.matcher(clean(line));
            if (!matcher.matches()) continue;
            String spray = type(matcher.group("spray"));
            long duration = parseDuration(matcher.group("time"));
            if (spray == null || duration <= 0) {
                remove(plot, true);
                return;
            }
            long expected = System.currentTimeMillis() + duration;
            long old = cfg.sprayExpiryTimes.getOrDefault(key(plot), 0L);
            String oldType = cfg.sprayTypes.get(key(plot));
            boolean changed = oldType == null || !oldType.equals(spray) || old <= expected - 600_000L;
            set(plot, spray, duration, true);
            if (changed && cfg.sprayNewNotification) announceNew(plot, spray, duration);
            return;
        }
    }

    private static void set(int plot, String spray, long duration, boolean preserveSeconds) {
        long now = System.currentTimeMillis();
        String key = key(plot);
        long expected = now + duration;
        long old = cfg.sprayExpiryTimes.getOrDefault(key, 0L);
        String oldType = cfg.sprayTypes.get(key);
        if (preserveSeconds && Objects.equals(oldType, spray) && old > now && Math.abs(old - expected) < 61_000L) return;
        boolean newApplication = !preserveSeconds || !Objects.equals(oldType, spray) || old <= now || expected - old >= 600_000L;
        cfg.sprayExpiryTimes.put(key, expected);
        cfg.sprayTypes.put(key, spray);
        if (newApplication) cfg.sprayNotified.put(key, false);
        save();
    }

    private static void remove(int plot, boolean notified) {
        String key = key(plot);
        boolean changed = cfg.sprayExpiryTimes.remove(key) != null;
        changed |= cfg.sprayTypes.remove(key) != null;
        cfg.sprayNotified.put(key, notified);
        if (changed) save();
    }

    private static void clearAll(boolean announce) {
        cfg.sprayExpiryTimes.clear();
        cfg.sprayTypes.clear();
        cfg.sprayNotified.clear();
        save();
        if (announce) local("All plot sprays cleared.");
    }

    private static void notifyExpired(boolean away) {
        if (!cfg.sprayExpiryNotification) return;
        long threshold = System.currentTimeMillis() + Math.max(0, cfg.sprayExpiryWarningSeconds) * 1000L;
        List<Integer> expired = new ArrayList<>();
        for (var entry : cfg.sprayExpiryTimes.entrySet()) {
            int plot = number(entry.getKey());
            if (plot <= 0 || entry.getValue() > threshold || cfg.sprayNotified.getOrDefault(entry.getKey(), false)) continue;
            expired.add(plot);
            cfg.sprayNotified.put(entry.getKey(), true);
        }
        if (expired.isEmpty()) return;
        expired.sort(Integer::compareTo);
        String plots = expired.stream().map(HerculesSprays::plotName).reduce((a,b) -> a + ", " + b).orElse("Plot");
        boolean hasAwayVariable = cfg.sprayExpiryTemplate.contains("{away}");
        String text = cfg.sprayExpiryTemplate.replace("{plots}", plots)
            .replace("{count}", Integer.toString(expired.size()))
            .replace("{away}", away ? "While you were away, " : "");
        if (away && !hasAwayVariable) text = "While you were away, " + text;
        Minecraft mc = Minecraft.getInstance();
        if (cfg.sprayExpiryChat) local(text);
        if (mc.player != null && cfg.sprayExpiryTitle) {
            mc.gui.hud.resetTitleTimes();
            mc.gui.hud.setTimes(0, Math.clamp(cfg.sprayTitleTicks, 10, 300), 10);
            mc.gui.hud.setTitle(Component.literal(text).withColor(0xFF5555));
        }
        if (mc.player != null && cfg.sprayExpirySound) mc.player.playSound(SoundEvents.NOTE_BLOCK_PLING.value(), .9f, .75f);
        save();
    }

    private static void announceNew(int plot, String spray, long duration) {
        local(cfg.sprayNewTemplate.replace("{plot}", plotName(plot)).replace("{spray}", spray).replace("{time}", time(duration)));
    }

    public static boolean hudVisible() {
        if (!active()) return false;
        Integer plot = currentPlot();
        if (cfg.sprayOnlyCurrentPlot) return plot != null && plot != 0 && (cfg.sprayShowNotSprayed || activeSpray(plot));
        return cfg.sprayShowNotSprayed || cfg.sprayExpiryTimes.values().stream().anyMatch(v -> v > System.currentTimeMillis());
    }

    public static List<SprayRow> hudRows() {
        if (!active()) return List.of();
        List<SprayRow> rows = new ArrayList<>();
        if (cfg.sprayOnlyCurrentPlot) {
            Integer plot = currentPlot();
            if (plot == null || plot == 0) return rows;
            addRow(rows, plot);
        } else {
            for (int plot = 1; plot <= 24; plot++) if (activeSpray(plot)) addRow(rows, plot);
            if (rows.isEmpty() && cfg.sprayShowNotSprayed) rows.add(new SprayRow("Current plot", "Not sprayed"));
        }
        return rows;
    }

    private static void addRow(List<SprayRow> rows, int plot) {
        long left = cfg.sprayExpiryTimes.getOrDefault(key(plot), 0L) - System.currentTimeMillis();
        String spray = cfg.sprayTypes.get(key(plot));
        if (left <= 0 || spray == null) {
            if (cfg.sprayShowNotSprayed) rows.add(new SprayRow(plotName(plot), "Not sprayed"));
            return;
        }
        String label = cfg.sprayIncludePlotNames ? plotName(plot) : "Spray";
        String value = (cfg.sprayShowType ? spray : "") + (cfg.sprayShowType && cfg.sprayShowTime ? " - " : "") + (cfg.sprayShowTime ? time(left) : "");
        rows.add(new SprayRow(label, value));
    }

    public static void registerCommands(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        dispatcher.register(LiteralArgumentBuilder.<FabricClientCommandSource>literal("sprays")
            .executes(c -> status())
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("clear").executes(c -> { clearAll(true); return 1; }))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("duration").then(RequiredArgumentBuilder.<FabricClientCommandSource,Integer>argument("minutes", IntegerArgumentType.integer(1, 240)).executes(c -> { cfg.sprayDurationMinutes = IntegerArgumentType.getInteger(c, "minutes"); save(); return status(); })))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("warning").then(RequiredArgumentBuilder.<FabricClientCommandSource,Integer>argument("seconds", IntegerArgumentType.integer(0, 600)).executes(c -> { cfg.sprayExpiryWarningSeconds = IntegerArgumentType.getInteger(c, "seconds"); save(); return status(); })))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("expirytemplate").then(RequiredArgumentBuilder.<FabricClientCommandSource,String>argument("text", StringArgumentType.greedyString()).executes(c -> template(true, StringArgumentType.getString(c, "text")))))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("newtemplate").then(RequiredArgumentBuilder.<FabricClientCommandSource,String>argument("text", StringArgumentType.greedyString()).executes(c -> template(false, StringArgumentType.getString(c, "text")))))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("option").then(RequiredArgumentBuilder.<FabricClientCommandSource,String>argument("name", StringArgumentType.word()).then(RequiredArgumentBuilder.<FabricClientCommandSource,String>argument("state", StringArgumentType.word()).executes(c -> option(StringArgumentType.getString(c, "name"), StringArgumentType.getString(c, "state")))))));
    }

    private static int status() {
        long active = cfg.sprayExpiryTimes.values().stream().filter(v -> v > System.currentTimeMillis()).count();
        local("Tracker " + on(cfg.sprayTracker) + ", " + active + " active plot " + (active == 1 ? "spray." : "sprays."));
        return 1;
    }

    private static int option(String name, String state) {
        Boolean value = parse(state);
        if (value == null) { local("State must be on or off."); return 0; }
        switch (name.toLowerCase(Locale.ROOT)) {
            case "enabled" -> cfg.sprayTracker = value;
            case "hud" -> cfg.sprayHud = value;
            case "notsprayed" -> cfg.sprayShowNotSprayed = value;
            case "expiry" -> cfg.sprayExpiryNotification = value;
            case "new" -> cfg.sprayNewNotification = value;
            case "chat" -> cfg.sprayExpiryChat = value;
            case "title" -> cfg.sprayExpiryTitle = value;
            case "sound" -> cfg.sprayExpirySound = value;
            case "away" -> cfg.sprayNotifyWhileAway = value;
            case "plots" -> cfg.sprayIncludePlotNames = value;
            case "type" -> cfg.sprayShowType = value;
            case "time" -> cfg.sprayShowTime = value;
            case "current" -> cfg.sprayOnlyCurrentPlot = value;
            default -> { local("Unknown spray option."); return 0; }
        }
        save();
        return status();
    }

    private static int template(boolean expiry, String raw) {
        String value = raw.replace('\n', ' ').replace('\r', ' ').trim();
        if (value.isEmpty() || value.length() > 160) { local("Template must contain 1-160 characters."); return 0; }
        if (expiry) cfg.sprayExpiryTemplate = value; else cfg.sprayNewTemplate = value;
        save();
        return status();
    }

    private static Integer currentPlot() {
        Minecraft mc = Minecraft.getInstance();
        return mc.player == null ? null : HerculesPests.plotAt(mc.player.getX(), mc.player.getZ());
    }
    static long remainingMillis(int plot) {
        if (cfg == null || cfg.sprayExpiryTimes == null || cfg.sprayTypes == null) return 0;
        if (!cfg.sprayTypes.containsKey(key(plot))) return 0;
        return Math.max(0, cfg.sprayExpiryTimes.getOrDefault(key(plot), 0L) - System.currentTimeMillis());
    }
    static String sprayType(int plot) {
        return remainingMillis(plot) > 0 ? cfg.sprayTypes.get(key(plot)) : null;
    }
    private static boolean activeSpray(int plot) { return cfg.sprayExpiryTimes.getOrDefault(key(plot), 0L) > System.currentTimeMillis() && cfg.sprayTypes.containsKey(key(plot)); }
    private static String plotName(int plot) { for (var entry : cfg.pestPlotNames.entrySet()) if (Objects.equals(entry.getValue(), plot)) return pretty(entry.getKey()); return "Plot " + plot; }
    private static String pretty(String value) { StringBuilder out = new StringBuilder(); for (String word : value.split("\\s+")) { if (!out.isEmpty()) out.append(' '); out.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1)); } return out.toString(); }
    private static String type(String raw) { if (raw == null || raw.equalsIgnoreCase("None")) return null; for (String type : TYPES) if (type.equalsIgnoreCase(raw.trim())) return type; return null; }
    private static long parseDuration(String raw) { if (raw == null) return -1; long seconds = 0; Matcher m = Pattern.compile("(\\d+)m").matcher(raw); if (m.find()) seconds += number(m.group(1)) * 60L; m = Pattern.compile("(\\d+)s").matcher(raw); if (m.find()) seconds += number(m.group(1)); return seconds * 1000L; }
    private static String time(long millis) { long seconds = Math.max(0, millis / 1000); return seconds >= 3600 ? String.format(Locale.ROOT, "%d:%02d:%02d", seconds / 3600, seconds / 60 % 60, seconds % 60) : String.format(Locale.ROOT, "%d:%02d", seconds / 60, seconds % 60); }
    private static int number(String value) { try { return Integer.parseInt(value); } catch (Exception ignored) { return -1; } }
    private static String clean(String value) { String clean = ChatFormatting.stripFormatting(value); return clean == null ? "" : clean.trim(); }
    private static String key(int plot) { return Integer.toString(plot); }
    private static boolean active() { return cfg != null && cfg.enabled && cfg.sprayTracker && inGarden(); }
    private static boolean inGarden() { return ConstellationClient.loc().area() == LocationManager.SkyblockArea.GARDEN; }
    private static Boolean parse(String value) { return switch (value.toLowerCase(Locale.ROOT)) { case "on","true","yes","1" -> true; case "off","false","no","0" -> false; default -> null; }; }
    private static String on(boolean value) { return value ? "on" : "off"; }
    private static void maps() { if (cfg.sprayExpiryTimes == null) cfg.sprayExpiryTimes = new HashMap<>(); if (cfg.sprayTypes == null) cfg.sprayTypes = new HashMap<>(); if (cfg.sprayNotified == null) cfg.sprayNotified = new HashMap<>(); if (cfg.pestPlotNames == null) cfg.pestPlotNames = new HashMap<>(); }
    private static void local(String text) { Minecraft mc = Minecraft.getInstance(); if (mc.player != null) mc.player.sendSystemMessage(Component.literal("\u00a72[Sprays] \u00a7f" + text)); }
    private static void save() { ConstellationClient.saveConfig(); }
}
