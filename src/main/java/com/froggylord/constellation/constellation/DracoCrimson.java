package com.froggylord.constellation.constellation;

import com.froggylord.constellation.ConstellationClient;
import com.froggylord.constellation.config.DracoConfig;
import com.froggylord.constellation.core.BaseConstellation;
import com.froggylord.constellation.core.InitContext;
import com.froggylord.constellation.core.LocationManager.SkyblockArea;
import com.froggylord.constellation.hud.HudManager;
import com.froggylord.constellation.hud.HudPosition;
import com.froggylord.constellation.hud.HudWidget;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Draco — Crimson Isle. Shows reputation / faction standing off the sidebar (the faction
 * name + rep count are always present on the Isle). Kuudra phases + supplies come later.
 */
public class DracoCrimson extends BaseConstellation {

    @Override public String id() { return "draco"; }
    @Override public String displayName() { return "Draco"; }
    @Override public String description() { return "Crimson Isle — reputation, Kuudra, Dojo"; }

    private static final Pattern REP = Pattern.compile("(Barbarian|Mage) Reputation:?\\s*([\\d,]+)");
    private static final Pattern DOJO = Pattern.compile("Dojo:.*?(\\d+).*");
    private static final Pattern VANQ = Pattern.compile("Vanquisher:?\\s*(\\d+)");

    private DracoConfig cfg;

    @Override
    public void init(InitContext ctx) { cfg = (DracoConfig) getConfig(); }

    @Override
    public void registerHud(HudManager hud) {
        cfg = (DracoConfig) getConfig();
        if (cfg == null) return;

        if (cfg.activityHud) {
            hud.register(new HudWidget("draco-rep", "Rep",
                () -> inCrimson() ? repLine() : null,
                HudPosition.of(2, 130), cfg.activityHud));
            hud.register(new HudWidget("draco-dojo", "Dojo",
                () -> inCrimson() ? dojoLine() : null,
                HudPosition.of(2, 140), cfg.activityHud));
        }
    }

    private static boolean inCrimson() {
        return ConstellationClient.loc().area() == SkyblockArea.CRIMSON_ISLE;
    }

    private static String repLine() {
        for (String line : ConstellationClient.loc().getSidebarLines()) {
            Matcher m = REP.matcher(line);
            if (m.find()) return "§c" + m.group(1) + " §f" + m.group(2)
                + " §7(" + vanqLine() + ")";
        }
        return null;
    }

    private static String dojoLine() {
        for (String line : ConstellationClient.loc().getSidebarLines()) {
            Matcher m = DOJO.matcher(line);
            if (m.find()) return "§eDojo §f" + m.group(1);
        }
        return null;
    }

    private static String vanqLine() {
        for (String line : ConstellationClient.loc().getSidebarLines()) {
            Matcher m = VANQ.matcher(line);
            if (m.find()) return m.group(1) + " kills";
        }
        return "? kills";
    }
}
