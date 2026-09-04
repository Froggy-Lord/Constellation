package com.froggylord.constellation.constellation;

import com.froggylord.constellation.ConstellationClient;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;

import java.util.Locale;

// ported from devonian (GPL-3.0): features/dungeons/SpiritBearTimer.kt
public final class SpiritBearTimer {
    private static final BlockPos SPAWN_SIGNAL = new BlockPos(7, 77, 34);
    private static final int SPAWN_TICKS = 68;
    private static int ticks = -1;
    private static boolean wasLantern;
    private static boolean tracking;
    private static boolean initialized;
    private static Object levelKey;

    private SpiritBearTimer() {}

    public static void init() {
        if (initialized) return;
        initialized = true;
        ConstellationClient.tick().every(1, "orion-spirit-bear-timer", SpiritBearTimer::tick);
    }

    public static String hudText() {
        var cfg = ConstellationClient.cfg().orion;
        if (cfg == null || !cfg.spiritBearTimer || ticks < 0 || !isF4Boss()) return null;
        return "§dBear " + String.format(Locale.ROOT, "%.2fs", ticks / 20.0);
    }

    private static void tick() {
        Minecraft mc = Minecraft.getInstance();
        var cfg = ConstellationClient.cfg().orion;
        if (cfg == null || !cfg.spiritBearTimer || !isF4Boss() || mc.level == null) {
            reset(null);
            return;
        }

        boolean lantern = mc.level.getBlockState(SPAWN_SIGNAL).is(Blocks.SEA_LANTERN);
        if (!tracking || levelKey != mc.level) {
            reset(mc.level);
            tracking = true;
            wasLantern = lantern;
            return;
        }

        if (!wasLantern && lantern) {
            ticks = SPAWN_TICKS;
        } else if (ticks >= 0) {
            ticks--;
        }
        wasLantern = lantern;
    }

    private static boolean isF4Boss() {
        return ConstellationClient.loc().inDungeons()
            && ConstellationClient.dungeon().inBoss()
            && ConstellationClient.dungeon().floor().endsWith("4");
    }

    private static void reset(Object level) {
        levelKey = level;
        ticks = -1;
        wasLantern = false;
        tracking = false;
    }
}
