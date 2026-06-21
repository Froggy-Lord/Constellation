package com.froggylord.constellation.constellation;

import com.froggylord.constellation.ConstellationClient;
import com.froggylord.constellation.config.PerseusConfig;
import com.froggylord.constellation.core.BaseConstellation;
import com.froggylord.constellation.core.InitContext;
import com.froggylord.constellation.hud.HudManager;
import com.froggylord.constellation.hud.HudPosition;
import com.froggylord.constellation.hud.HudWidget;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Perseus — slayers. Reads slayer XP off the sidebar (a stable Hypixel signal) and shows
 * a compact readout. Boss timer deferred — the BossHealthOverlay access path changed in 26.2.
 */
public class PerseusSlayers extends BaseConstellation {

    @Override public String id() { return "perseus"; }
    @Override public String displayName() { return "Perseus"; }
    @Override public String description() { return "Slayers — XP bar, boss timer (TBD)"; }

    private static final Pattern SLAYER_XP = Pattern.compile("Slayer XP:?\\s*([\\d,]+)");

    private PerseusConfig cfg;

    @Override
    public void init(InitContext ctx) { cfg = (PerseusConfig) getConfig(); }

    @Override
    public void registerHud(HudManager hud) {
        cfg = (PerseusConfig) getConfig();
        if (cfg == null) return;

        if (cfg.xpBar) {
            hud.register(new HudWidget("perseus-xp", "SlayerXP",
                () -> ConstellationClient.loc().onHypixel() ? xpLine() : null,
                HudPosition.of(50, 78), cfg.xpBar));
        }
    }

    private static String xpLine() {
        for (String line : ConstellationClient.loc().getSidebarLines()) {
            Matcher m = SLAYER_XP.matcher(line);
            if (m.find()) return "§d" + compact(parse(m.group(1))) + " XP";
        }
        return null;
    }

    private static long parse(String s) { try { return Long.parseLong(s.replace(",", "")); } catch (NumberFormatException e) { return 0; } }
    private static String compact(long n) {
        if (n < 1000) return Long.toString(n);
        if (n < 1_000_000) return String.format("%.1fk", n / 1000.0);
        return String.format("%.2fM", n / 1_000_000.0);
    }
}
