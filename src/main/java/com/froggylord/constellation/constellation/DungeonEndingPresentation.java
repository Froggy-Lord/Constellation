package com.froggylord.constellation.constellation;

import com.froggylord.constellation.ConstellationClient;
import com.froggylord.constellation.config.OrionConfig;
import com.froggylord.constellation.data.DungeonScore;
import com.froggylord.constellation.data.DungeonState;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;

import java.util.Locale;

public final class DungeonEndingPresentation {
    private static OrionConfig cfg;
    private static long activeUntil;

    private DungeonEndingPresentation() {}

    // ported from Skyblocker (LGPL-3.0-or-later): skyblock/dungeon/secrets/DungeonManager.java dungeon-ended event
    public static void init(OrionConfig config) {
        cfg = config;
        ConstellationClient.bus().subscribe(DungeonState.DungeonComplete.class,
            event -> start(event.elapsedMs(), false));
        ConstellationClient.bus().subscribe(DungeonState.DungeonEnter.class, ignored -> activeUntil = 0);
    }

    private static void start(long elapsedMs, boolean test) {
        if (!enabled()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        long duration = Math.clamp(cfg.dungeonEndingDurationMillis, 1_000, 15_000);
        activeUntil = System.currentTimeMillis() + duration;
        int score = test ? 300 : DungeonScore.score();
        String grade = test ? "S+" : DungeonScore.grade();
        String floor = test ? "M7" : ConstellationClient.dungeon().floor();
        String time = formatTime(test ? 384_250 : elapsedMs);
        if (cfg.dungeonEndingTitle) {
            mc.gui.hud.resetTitleTimes();
            mc.gui.hud.setTimes(Math.clamp(cfg.dungeonEndingFadeInTicks, 0, 100),
                Math.clamp((int) (duration / 50), 20, 300), Math.clamp(cfg.dungeonEndingFadeOutTicks, 0, 100));
            String title = fit(format(cfg.dungeonEndingTitleTemplate, score, grade, floor, time),
                Math.max(24, mc.getWindow().getGuiScaledWidth() / 4 - 8));
            mc.gui.hud.setTitle(Component.literal(title)
                .withColor(cfg.dungeonEndingTitleColour & 0xFFFFFF));
            if (cfg.dungeonEndingSubtitle && !cfg.dungeonEndingSubtitleTemplate.isBlank()) {
                String subtitle = fit(format(cfg.dungeonEndingSubtitleTemplate, score, grade, floor, time),
                    Math.max(48, mc.getWindow().getGuiScaledWidth() / 2 - 8));
                mc.gui.hud.setSubtitle(Component.literal(subtitle)
                    .withColor(cfg.dungeonEndingSubtitleColour & 0xFFFFFF));
            }
        }
        if (cfg.dungeonEndingSound)
            mc.player.playSound(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, cfg.dungeonEndingSoundVolume,
                cfg.dungeonEndingSoundPitch);
    }

    public static boolean shouldHideParticles() {
        return active() && cfg.dungeonEndingHideParticles;
    }

    public static boolean shouldMuteSound() {
        return active() && cfg.dungeonEndingMuteSounds;
    }

    public static boolean shouldHideServerTitle() {
        return active() && cfg.dungeonEndingHideServerTitles;
    }

    private static boolean active() {
        if (!enabled() || activeUntil <= System.currentTimeMillis()) {
            activeUntil = 0;
            return false;
        }
        return true;
    }

    private static boolean enabled() {
        return cfg != null && cfg.enabled && cfg.dungeonEndingPresentation;
    }

    private static String format(String template, int score, String grade, String floor, String time) {
        String value = template == null ? "" : template;
        return value.replace("{score}", Integer.toString(score))
            .replace("{grade}", grade == null || grade.isBlank() ? "?" : grade)
            .replace("{floor}", floor == null || floor.isBlank() ? "Dungeon" : floor)
            .replace("{time}", time);
    }

    private static String formatTime(long elapsedMs) {
        long total = Math.max(0, elapsedMs) / 1_000;
        return String.format(Locale.ROOT, "%d:%02d.%03d", total / 60, total % 60, Math.max(0, elapsedMs) % 1_000);
    }

    private static String fit(String value, int width) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.font.width(value) <= width) return value;
        String suffix = "...";
        int allowed = Math.max(0, width - mc.font.width(suffix));
        String out = value;
        while (!out.isEmpty() && mc.font.width(out) > allowed) out = out.substring(0, out.length() - 1);
        return out.stripTrailing() + suffix;
    }

    public static void registerCommands(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        dispatcher.register(LiteralArgumentBuilder.<FabricClientCommandSource>literal("dungeonending")
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("test").executes(ctx -> {
                start(384_250, true);
                return 1;
            }))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("stop").executes(ctx -> {
                activeUntil = 0;
                Minecraft mc = Minecraft.getInstance();
                if (mc.player != null) mc.gui.hud.clearTitles();
                return 1;
            })));
    }
}
