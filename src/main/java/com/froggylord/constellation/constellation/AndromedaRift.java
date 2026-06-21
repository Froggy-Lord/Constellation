package com.froggylord.constellation.constellation;

import com.froggylord.constellation.ConstellationClient;
import com.froggylord.constellation.config.AndromedaConfig;
import com.froggylord.constellation.core.BaseConstellation;
import com.froggylord.constellation.core.InitContext;
import com.froggylord.constellation.core.LocationManager.SkyblockArea;
import com.froggylord.constellation.hud.HudManager;
import com.froggylord.constellation.hud.HudPosition;
import com.froggylord.constellation.hud.HudWidget;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Andromeda — the Rift. Shows the rift time remaining off the sidebar (a stable Hypixel
 * signal on every rift-side island).
 */
public class AndromedaRift extends BaseConstellation {

    @Override public String id() { return "andromeda"; }
    @Override public String displayName() { return "Andromeda"; }
    @Override public String description() { return "The Rift — time tracker, enigma souls, effigies"; }

    // "Rift Time: 12:34" or just "Time: 12:34" on rift islands
    private static final Pattern RIFT_TIME = Pattern.compile("(?:Rift|⏣ )Time:?\\s*(\\d+):(\\d+)");

    private AndromedaConfig cfg;

    @Override
    public void init(InitContext ctx) { cfg = (AndromedaConfig) getConfig(); }

    @Override
    public void registerHud(HudManager hud) {
        cfg = (AndromedaConfig) getConfig();
        if (cfg == null) return;

        if (cfg.timeHud) {
            hud.register(new HudWidget("andromeda-time", "RiftTime",
                () -> inRift() ? timeLine() : null,
                HudPosition.of(2, 140), cfg.timeHud));
        }
    }

    private static boolean inRift() {
        return ConstellationClient.loc().area() == SkyblockArea.THE_RIFT;
    }

    private static String timeLine() {
        for (String line : ConstellationClient.loc().getSidebarLines()) {
            Matcher m = RIFT_TIME.matcher(line);
            if (m.find()) return "§d" + m.group(1) + ":" + m.group(2);
        }
        return null;
    }
}
