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

public class CygnusEvents extends BaseConstellation {

    @Override public String id() { return "cygnus"; }
    @Override public String displayName() { return "Cygnus"; }
    @Override public String description() { return "event calendar and diana"; }

    private static final Pattern DATE = Pattern.compile("((?:Early|Late) )?(Spring|Summer|Autumn|Winter) (\\d+)(?:st|nd|rd|th)");
    private static final Pattern TIME = Pattern.compile("(\\d{1,2}:\\d{2})(am|pm)");
    private static final Pattern MAYOR = Pattern.compile("Mayor:?\\s*(\\w+)");
    private static final Pattern HOPPITY = Pattern.compile("Hoppity.*?(\\d+)");
    private static final Pattern CHOCOLATE = Pattern.compile("Chocolate:?\\s*([\\d,]+)");

    private CygnusConfig cfg;

    private static int inquisitors = 0;
    private static int mythosDrops = 0;
    
    private static final double[][] spadeSamples = new double[4][3]; 
    private static int spadeIdx = 0;
    private static double burrowX = Double.NaN, burrowZ = Double.NaN;
    // grabs the exact spawn coords hypixel shouts to everyone nearby
    private static final java.util.regex.Pattern INQUIS_COORDS =
        java.util.regex.Pattern.compile("at Coords (-?\\d+) (-?\\d+) (-?\\d+)");
    private static double inqX = Double.NaN, inqY, inqZ;
    private static long inqSetAt = 0;
    private static final java.util.regex.Pattern BURROW_CHAIN =
        java.util.regex.Pattern.compile("(?:dug out a Griffin Burrow|finished the Griffin burrow chain)!? \\((\\d+)/(\\d+)\\)");
    private static int chainIdx = 0, chainLen = 0;
    private static long chainAt = 0;
    
    private static final String[] MYTHOS = {
        "Griffin Feather", "Crown of Greed", "Washed-up Souvenir", "Daedalus Stick",
        "Minos Relic", "Enchanted Egg", "Dwarf Turtle Shelmet", "Antique Remedies",
        "Chimera", "Minos Champion", "Minotaur", "Minos Inquisitor"
    };

    @Override
    public void init(InitContext ctx) {
    }

    private static void triangulate() {
        if (spadeIdx < 2) return;
        
        double x1 = spadeSamples[(spadeIdx - 2) % 4][0];
        double z1 = spadeSamples[(spadeIdx - 2) % 4][1];
        double a1 = spadeSamples[(spadeIdx - 2) % 4][2];
        double x2 = spadeSamples[(spadeIdx - 1) % 4][0];
        double z2 = spadeSamples[(spadeIdx - 1) % 4][1];
        double a2 = spadeSamples[(spadeIdx - 1) % 4][2];

        double dx1 = Math.sin(a1), dz1 = -Math.cos(a1);
        double dx2 = Math.sin(a2), dz2 = -Math.cos(a2);
        double det = dx1 * dz2 - dz1 * dx2;
        if (Math.abs(det) < 0.001) return; 

        double t = ((x2 - x1) * dz2 - (z2 - z1) * dx2) / det;
        burrowX = x1 + t * dx1;
        burrowZ = z1 + t * dz1;
    }

    @Override
    public void registerHud(HudManager hud) {
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
