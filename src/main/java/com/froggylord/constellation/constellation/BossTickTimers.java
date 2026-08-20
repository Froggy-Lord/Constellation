package com.froggylord.constellation.constellation;

import com.froggylord.constellation.ConstellationClient;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

// ported from Odin (BSD-3-Clause):
// src/main/kotlin/com/odtheking/odin/features/impl/boss/TickTimers.kt
public final class BossTickTimers {
    private static final Pattern STORM_PY = Pattern.compile("^\\[BOSS] Storm: (ENERGY HEED MY CALL|THUNDER LET ME BE YOUR CATALYST)!$");
    private static int necron = -1;
    private static int goldorTick = -1;
    private static int goldorStart = -1;
    private static int stormPad = -1;
    private static int stormLightning = -1;
    private static int stormPy = -1;
    private static boolean pyTriggered;
    private static boolean initialized;

    private BossTickTimers() {}

    public static void init() {
        if (initialized) return;
        initialized = true;
        ClientReceiveMessageEvents.ALLOW_GAME.register((message, overlay) -> {
            if (overlay) return true;
            onChat(message.getString());
            return true;
        });
        ConstellationClient.tick().every(1, "orion-boss-tick-timers", BossTickTimers::tick);
    }

    public static String hudText() {
        if (!ConstellationClient.loc().inDungeons()) return null;
        List<String> timers = new ArrayList<>();
        add(timers, "Necron", necron);
        if (goldorStart >= 0) add(timers, "Start", goldorStart);
        else add(timers, "Goldor", goldorTick);
        add(timers, "Pad", stormPad);
        add(timers, "Lightning", stormLightning);
        add(timers, "PY", stormPy);
        return timers.isEmpty() ? null : String.join(" | ", timers);
    }

    private static void onChat(String value) {
        if (value.equals("[BOSS] Necron: I'm afraid, your journey ends now.")) necron = 60;
        else if (value.equals("[BOSS] Goldor: Who dares trespass into my domain?")) goldorTick = 60;
        else if (value.equals("The Core entrance is opening!")) {
            goldorStart = -1;
            goldorTick = -1;
        } else if (value.equals("[BOSS] Storm: I should have known that I stood no chance.")) {
            goldorStart = 104;
            stormPad = -1;
        } else if (value.equals("[BOSS] Storm: Pathetic Maxor, just like expected.")) {
            stormPad = 20;
            stormLightning = 560;
        } else if (!pyTriggered && STORM_PY.matcher(value).matches()) {
            pyTriggered = true;
            stormPy = 95;
        }
    }

    private static void tick() {
        if (!ConstellationClient.loc().inDungeons()) {
            reset();
            return;
        }
        if (goldorTick == 0 && goldorStart <= 0) goldorTick = 60;
        if (stormPad == 0) stormPad = 20;
        necron = down(necron);
        goldorTick = down(goldorTick);
        goldorStart = down(goldorStart);
        stormPad = down(stormPad);
        stormLightning = down(stormLightning);
        stormPy = down(stormPy);
    }

    private static int down(int timer) {
        return timer >= 0 ? timer - 1 : timer;
    }

    private static void add(List<String> timers, String name, int ticks) {
        if (ticks < 0) return;
        timers.add(name + " " + String.format(Locale.ROOT, "%.1fs", ticks / 20.0));
    }

    private static void reset() {
        necron = -1;
        goldorTick = -1;
        goldorStart = -1;
        stormPad = -1;
        stormLightning = -1;
        stormPy = -1;
        pyTriggered = false;
    }
}
