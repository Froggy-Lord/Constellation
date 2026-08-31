package com.froggylord.constellation.constellation;

import com.froggylord.constellation.ConstellationClient;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;

import java.util.Locale;

// ported from devonian (GPL-3.0): features/dungeons/m7/RelicTimer.kt
// cross-checked with Odin (BSD-3-Clause): features/impl/boss/KingRelics.kt
public final class M7RelicTimer {
    private static final int SPAWN_TICKS = 45;
    private static int ticks = -1;
    private static boolean initialized;

    private M7RelicTimer() {}

    public static void init() {
        if (initialized) return;
        initialized = true;
        ClientReceiveMessageEvents.ALLOW_GAME.register((message, overlay) -> {
            if (!overlay && message.getString().equals("[BOSS] Necron: All this, for nothing..."))
                ticks = SPAWN_TICKS;
            return true;
        });
        ConstellationClient.tick().every(1, "orion-m7-relic-timer", M7RelicTimer::tick);
    }

    public static String hudText() {
        var cfg = ConstellationClient.cfg().orion;
        if (cfg == null || !cfg.m7RelicTimer || ticks < 0 || !isM7Boss()) return null;
        return "§aRelics " + String.format(Locale.ROOT, "%.2fs", ticks / 20.0);
    }

    private static void tick() {
        if (!isM7Boss()) {
            ticks = -1;
            return;
        }
        if (ticks >= 0) ticks--;
    }

    private static boolean isM7Boss() {
        return ConstellationClient.loc().inDungeons()
            && ConstellationClient.dungeon().inBoss()
            && ConstellationClient.dungeon().floor().equalsIgnoreCase("M7");
    }
}
