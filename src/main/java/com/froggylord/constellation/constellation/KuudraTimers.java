package com.froggylord.constellation.constellation;

import com.froggylord.constellation.ConstellationClient;
import com.froggylord.constellation.config.DracoConfig;

import java.util.Locale;

public final class KuudraTimers {
    private static KuudraState.Phase lastPhase = KuudraState.Phase.NONE;
    private static long supplyDeadline;
    private static long buildDeadline;

    private KuudraTimers() {}

    // ported from Athen (BSD-3-Clause): modules/impl/kuudra/KuudraTimers.kt
    public static void tick() {
        DracoConfig cfg = config();
        if (cfg == null || !cfg.enabled || !cfg.kuudraTimers || !KuudraState.inRun()) {
            if (lastPhase != KuudraState.Phase.NONE || supplyDeadline != 0 || buildDeadline != 0) reset();
            return;
        }

        KuudraState.Phase phase = KuudraState.phase();
        if (phase == lastPhase) return;
        lastPhase = phase;
        long now = System.nanoTime();
        long elapsed = KuudraState.phaseElapsedMillis();
        if (phase == KuudraState.Phase.SUPPLY) {
            buildDeadline = 0;
            supplyDeadline = deadline(now, cfg.kuudraSupplySpawnDurationMs, elapsed);
        } else if (phase == KuudraState.Phase.BUILD) {
            supplyDeadline = 0;
            buildDeadline = deadline(now, cfg.kuudraBuildStartDurationMs, elapsed);
        } else {
            supplyDeadline = 0;
            buildDeadline = 0;
        }
    }

    public static String supplyText() {
        DracoConfig cfg = config();
        if (!active(cfg) || !cfg.kuudraSupplySpawnTimer || KuudraState.phase() != KuudraState.Phase.SUPPLY)
            return null;
        return text(supplyDeadline, cfg.kuudraSupplySpawnDurationMs, cfg.kuudraSupplyTimerStyle,
            cfg.kuudraSupplyTimerReadyText, cfg);
    }

    public static String buildText() {
        DracoConfig cfg = config();
        if (!active(cfg) || !cfg.kuudraBuildStartTimer || KuudraState.phase() != KuudraState.Phase.BUILD)
            return null;
        return text(buildDeadline, cfg.kuudraBuildStartDurationMs, cfg.kuudraBuildTimerStyle,
            cfg.kuudraBuildTimerReadyText, cfg);
    }

    public static void reset() {
        lastPhase = KuudraState.Phase.NONE;
        supplyDeadline = 0;
        buildDeadline = 0;
    }

    public static long supplyRemainingMillis() { return remaining(supplyDeadline); }
    public static long buildRemainingMillis() { return remaining(buildDeadline); }

    private static String text(long deadline, int duration, String template, String ready, DracoConfig cfg) {
        if (deadline == 0) return null;
        long left = remaining(deadline);
        if (left <= 0) {
            if (!cfg.kuudraTimerShowReady || System.nanoTime() - deadline
                >= Math.clamp(cfg.kuudraTimerReadyHoldMs, 0, 10_000) * 1_000_000L) return null;
            return clean(ready).replace("{time}", format(0, cfg.kuudraTimerDecimals));
        }
        String result = clean(template)
            .replace("#time", format(left, cfg.kuudraTimerDecimals))
            .replace("{time}", format(left, cfg.kuudraTimerDecimals))
            .replace("{elapsed}", format(Math.max(0, Math.clamp(duration, 100, 60_000) - left),
                cfg.kuudraTimerDecimals));
        return result.isEmpty() ? null : result;
    }

    private static long deadline(long now, int duration, long elapsed) {
        long left = Math.clamp(duration, 100, 60_000) - elapsed;
        return now + left * 1_000_000L;
    }

    private static long remaining(long deadline) {
        return deadline == 0 ? 0 : Math.max(0, (deadline - System.nanoTime()) / 1_000_000L);
    }

    private static String format(long millis, int decimals) {
        int precision = Math.clamp(decimals, 0, 2);
        return String.format(Locale.ROOT, "%1$." + precision + "fs", millis / 1000.0);
    }

    private static String clean(String text) {
        if (text == null) return "";
        return text.replace("<dark_gray>", "§8").replace("<gray>", "§7")
            .replace("<red>", "§c").replace("<green>", "§a").replace("<yellow>", "§e")
            .replace("<aqua>", "§b").replace("<blue>", "§9").replace("<gold>", "§6")
            .replace("<white>", "§f").replace("<reset>", "§r").replace("<r>", "§r")
            .replace('\n', ' ').replace('\r', ' ').trim();
    }

    private static boolean active(DracoConfig cfg) {
        return cfg != null && cfg.enabled && cfg.kuudraTimers && KuudraState.inRun();
    }

    private static DracoConfig config() {
        return ConstellationClient.cfg() == null ? null : ConstellationClient.cfg().draco;
    }
}
