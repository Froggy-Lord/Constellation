package com.froggylord.constellation.constellation;

import com.froggylord.constellation.ConstellationClient;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;

import java.util.Locale;

// ported from devonian (GPL-3.0): features/dungeons/LividInvulnerable.kt
public final class LividInvulnerableTimer {
    private static final String START = "[BOSS] Livid: Welcome, you've arrived right on time. I am Livid, the Master of Shadows.";
    private static final int INVULNERABLE_TICKS = 390;
    private static int ticks = -1;
    private static boolean initialized;

    private LividInvulnerableTimer() {}

    public static void init() {
        if (initialized) return;
        initialized = true;
        ClientReceiveMessageEvents.ALLOW_GAME.register((message, overlay) -> {
            if (!overlay && message.getString().equals(START) && isFloorFive()) ticks = INVULNERABLE_TICKS;
            return true;
        });
        ConstellationClient.tick().every(1, "orion-livid-invulnerable", LividInvulnerableTimer::tick);
    }

    public static String hudText() {
        var cfg = ConstellationClient.cfg().orion;
        if (cfg == null || !cfg.lividInvulnerableTimer || ticks < 0 || !isF5Boss()) return null;
        return "§eLivid " + String.format(Locale.ROOT, "%.2fs", ticks / 20.0);
    }

    private static void tick() {
        var cfg = ConstellationClient.cfg().orion;
        if (cfg == null || !cfg.lividInvulnerableTimer || !isFloorFive()) {
            ticks = -1;
            return;
        }
        if (ticks >= 0) ticks--;
    }

    private static boolean isFloorFive() {
        return ConstellationClient.loc().inDungeons() && ConstellationClient.dungeon().floor().endsWith("5");
    }

    private static boolean isF5Boss() {
        return isFloorFive() && ConstellationClient.dungeon().inBoss();
    }
}
