package com.froggylord.constellation.constellation;

import com.froggylord.constellation.ConstellationClient;
import com.froggylord.constellation.config.CygnusConfig;
import com.froggylord.constellation.core.BaseConstellation;
import com.froggylord.constellation.core.InitContext;
import com.froggylord.constellation.hud.HudManager;
import com.froggylord.constellation.hud.HudPosition;
import com.froggylord.constellation.hud.HudWidget;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Cygnus — events + calendar. For now a SkyBlock date/time readout pulled off the sidebar (the
 * season/day and the in-game clock are both shown there on most islands). Diana burrows, mayor
 * info and event countdowns come later.
 */
public class CygnusEvents extends BaseConstellation {

    @Override public String id() { return "cygnus"; }
    @Override public String displayName() { return "Cygnus"; }
    @Override public String description() { return "Events — calendar, Diana, mayor, seasonal"; }

    private static final Pattern DATE = Pattern.compile("((?:Early|Late) )?(Spring|Summer|Autumn|Winter) (\\d+)(?:st|nd|rd|th)");
    private static final Pattern TIME = Pattern.compile("(\\d{1,2}:\\d{2})(am|pm)");
    private static final Pattern MAYOR = Pattern.compile("Mayor:?\\s*(\\w+)");
    private static final Pattern HOPPITY = Pattern.compile("Hoppity.*?(\\d+)");
    private static final Pattern CHOCOLATE = Pattern.compile("Chocolate:?\\s*([\\d,]+)");

    private CygnusConfig cfg;

    @Override
    public void init(InitContext ctx) {
        cfg = (CygnusConfig) getConfig();
    }

    @Override
    public void registerHud(HudManager hud) {
        cfg = (CygnusConfig) getConfig();
        if (cfg == null) return;

        if (cfg.calendarHud) {
            hud.register(new HudWidget("cygnus-calendar", "Date",
                () -> ConstellationClient.loc().onHypixel() ? calendarLine() : null,
                HudPosition.of(2, 100), cfg.calendarHud));
            hud.register(new HudWidget("cygnus-mayor", "Mayor",
                () -> ConstellationClient.loc().onHypixel() ? mayorLine() : null,
                HudPosition.of(2, 110), cfg.calendarHud));
            hud.register(new HudWidget("cygnus-hoppity", "Hoppity",
                () -> ConstellationClient.loc().onHypixel() ? hoppityLine() : null,
                HudPosition.of(2, 120), cfg.calendarHud));
            hud.register(new HudWidget("cygnus-choc", "Choc",
                () -> ConstellationClient.loc().onHypixel() ? chocLine() : null,
                HudPosition.of(2, 128), cfg.calendarHud));
        }
    }

    private static String calendarLine() {
        String date = null, time = null;
        for (String line : ConstellationClient.loc().getSidebarLines()) {
            if (date == null) {
                Matcher d = DATE.matcher(line);
                if (d.find()) date = (d.group(1) == null ? "" : d.group(1)) + d.group(2) + " " + d.group(3);
            }
            if (time == null) {
                Matcher t = TIME.matcher(line);
                if (t.find()) time = t.group(1) + t.group(2);
            }
        }
        if (date == null && time == null) return null;
        StringBuilder sb = new StringBuilder();
        if (date != null) sb.append("§f").append(date);
        if (time != null) sb.append(sb.length() > 0 ? " §7" : "§7").append(time);
        return sb.toString();
    }

    private static String mayorLine() {
        for (String line : ConstellationClient.loc().getSidebarLines()) {
            Matcher m = MAYOR.matcher(line);
            if (m.find()) return "§6Mayor §f" + m.group(1);
        }
        return null;
    }

    private static String hoppityLine() {
        for (String line : ConstellationClient.loc().getSidebarLines()) {
            Matcher m = HOPPITY.matcher(line);
            if (m.find()) return "§d🐰 " + m.group(1);
        }
        return null;
    }

    private static String chocLine() {
        for (String line : ConstellationClient.loc().getSidebarLines()) {
            Matcher m = CHOCOLATE.matcher(line);
            if (m.find()) return "§6🍫 " + compact(m.group(1));
        }
        return null;
    }

    private static String compact(String raw) {
        try {
            long n = Long.parseLong(raw.replace(",", ""));
            if (n < 1000) return Long.toString(n);
            if (n < 1_000_000) return String.format("%.1fk", n / 1000.0);
            return String.format("%.2fM", n / 1_000_000.0);
        } catch (NumberFormatException e) { return raw; }
    }
}
