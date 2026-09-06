package com.froggylord.constellation.constellation;

import com.froggylord.constellation.ConstellationClient;
import com.froggylord.constellation.config.OrionConfig;
import com.froggylord.constellation.data.MapSegments;
import com.froggylord.constellation.data.RoomType;
import com.froggylord.constellation.mixin.LerpingBossEventAccessor;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.LerpingBossEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;

// ported from Devonian (GPL-3.0): features/dungeons/clear/WatcherBossBar.kt
public final class WatcherBossBar {
    private WatcherBossBar() {}

    public static Collection<LerpingBossEvent> modify(Collection<LerpingBossEvent> source) {
        OrionConfig cfg = ConstellationClient.cfg().orion;
        if (cfg == null || !cfg.enabled || !cfg.watcherBossBar || !inDungeonClear()) return source;
        List<LerpingBossEvent> result = new ArrayList<>(source.size());
        for (LerpingBossEvent event : source) {
            if (!isWatcher(event.getName())) {
                result.add(event);
                continue;
            }
            if (cfg.watcherBossBarHideNotBlood && MapSegments.currentType() != RoomType.BLOOD) continue;
            if (!cfg.watcherBossBarShowProgress) {
                result.add(event);
                continue;
            }
            result.add(copyWithName(event, progressName(event, cfg)));
        }
        return result;
    }

    private static LerpingBossEvent copyWithName(LerpingBossEvent event, Component name) {
        return new LerpingBossEvent(event.getId(), name, event.getProgress(), event.getColor(), event.getOverlay(),
            event.shouldDarkenScreen(), event.shouldPlayBossMusic(), event.shouldCreateWorldFog());
    }

    private static Component progressName(LerpingBossEvent event, OrionConfig cfg) {
        float target = Math.clamp(((LerpingBossEventAccessor) event).constellation$targetPercent(), 0.0f, 1.0f);
        int total = bloodMobTotal();
        int completed = Math.clamp(Math.round(target * total), 0, total);
        boolean remainingMode = cfg.watcherBossBarShowRemaining && !cfg.watcherBossBarShowPercent;
        int shown = remainingMode ? total - completed : completed;
        String value;
        if (cfg.watcherBossBarShowPercent) value = Math.round(target * 100.0f) + "%";
        else value = shown + "/" + total;
        if (remainingMode) value += " remaining";

        MutableComponent copy = event.getName().copy();
        copy.append(Component.literal(" - ").withStyle(Style.EMPTY.withColor(TextColor.fromRgb(
            cfg.watcherBossBarSeparatorColour & 0xFFFFFF))));
        copy.append(Component.literal(value).withStyle(Style.EMPTY.withColor(TextColor.fromRgb(
            cfg.watcherBossBarProgressColour & 0xFFFFFF))));
        return copy;
    }

    private static boolean isWatcher(Component name) {
        String clean = ChatFormatting.stripFormatting(name.getString()).trim();
        return clean.equals("The Watcher");
    }

    private static boolean inDungeonClear() {
        return ConstellationClient.loc().inDungeons() && !ConstellationClient.dungeon().inBoss();
    }

    // ported from Devonian (GPL-3.0): api/dungeon/FloorType.kt (bloodMobs = floorNum + 12)
    private static int bloodMobTotal() {
        String floor = ConstellationClient.dungeon().floor();
        if (floor == null) return 12;
        String normalized = floor.trim().toUpperCase(Locale.ROOT);
        if (normalized.equals("E") || normalized.equals("ENTRANCE")) return 9;
        for (int i = normalized.length() - 1; i >= 0; i--) {
            if (Character.isDigit(normalized.charAt(i))) return Character.digit(normalized.charAt(i), 10) + 12;
        }
        return 12;
    }

    public static void registerCommands(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        dispatcher.register(LiteralArgumentBuilder.<FabricClientCommandSource>literal("watcherbar")
            .executes(ctx -> status())
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("status").executes(ctx -> status()))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("color")
                .then(RequiredArgumentBuilder.<FabricClientCommandSource, String>argument("part", StringArgumentType.word())
                    .then(RequiredArgumentBuilder.<FabricClientCommandSource, String>argument("rgb", StringArgumentType.word())
                        .executes(ctx -> colour(StringArgumentType.getString(ctx, "part"),
                            StringArgumentType.getString(ctx, "rgb")))))));
    }

    private static int status() {
        OrionConfig cfg = ConstellationClient.cfg().orion;
        String mode = cfg.watcherBossBarShowPercent ? "percent"
            : cfg.watcherBossBarShowRemaining ? "remaining" : "completed";
        message("Bar " + (cfg.watcherBossBar ? "on" : "off") + ", progress "
            + (cfg.watcherBossBarShowProgress ? "on" : "off") + ", mode " + mode + ".");
        return 1;
    }

    private static int colour(String part, String value) {
        Integer parsed = parseColour(value);
        if (parsed == null) { message("Color must be RRGGBB."); return 0; }
        OrionConfig cfg = ConstellationClient.cfg().orion;
        if (part.equalsIgnoreCase("progress")) cfg.watcherBossBarProgressColour = parsed;
        else if (part.equalsIgnoreCase("separator")) cfg.watcherBossBarSeparatorColour = parsed;
        else { message("Part must be progress or separator."); return 0; }
        ConstellationClient.saveConfig();
        message(title(part) + " color set to " + String.format("%06X", parsed & 0xFFFFFF) + ".");
        return 1;
    }

    private static Integer parseColour(String value) {
        try {
            String clean = value.startsWith("#") ? value.substring(1) : value;
            return clean.length() == 6 ? (int) Long.parseLong(clean, 16) : null;
        } catch (NumberFormatException ignored) { return null; }
    }

    private static String title(String value) {
        return value.substring(0, 1).toUpperCase(Locale.ROOT) + value.substring(1).toLowerCase(Locale.ROOT);
    }

    private static void message(String text) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) mc.player.sendSystemMessage(Component.literal("§dWatcher Bar §8> §f" + text));
    }
}
