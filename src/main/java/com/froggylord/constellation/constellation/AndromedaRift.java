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

public class AndromedaRift extends BaseConstellation {

    @Override public String id() { return "andromeda"; }
    @Override public String displayName() { return "Andromeda"; }
    @Override public String description() { return "rift hud"; }

    
    private static final Pattern RIFT_TIME = Pattern.compile("(?:Rift|⏣ )Time:?\\s*(\\d+):(\\d+)");
    private static final Pattern MOTES = Pattern.compile("Motes:?\\s*([\\d,]+)");

    private AndromedaConfig cfg;

    private static int enigmaSouls = 0;
    private static int effigies = 0;
    private static long motesSession = 0;
    private static final Pattern MOTES_GAIN = Pattern.compile("([\\d,.]+) Motes");
    private static long lowTimeAt = 0;

    @Override
    public void init(InitContext ctx) {
    }

    @Override
    public void registerHud(HudManager hud) {
    }

    private static boolean inRift() {
        return ConstellationClient.loc().area() == SkyblockArea.THE_RIFT;
    }

    private static int maxRiftSecs = 0;

    private static String timeLine() {
        // primary: sidebar mm:ss (live during play)
        for (String line : ConstellationClient.loc().getSidebarLines()) {
            Matcher m = RIFT_TIME.matcher(line);
            if (m.find()) {
                int mins = Integer.parseInt(m.group(1));
                int secs = Integer.parseInt(m.group(2));
                int total = mins * 60 + secs;
                if (total > maxRiftSecs) maxRiftSecs = total;
                String col = total < 60 ? "§c" : total < 300 ? "§e" : "§b";
                String pct = maxRiftSecs > 0 ? " §7(" + (total * 100 / maxRiftSecs) + "%)" : "";
                return col + String.format("%d:%02d", mins, secs) + pct;
            }
        }
        // fallback: tab shows "Rift Time Left: 43m" (live scrape confirmed)
        for (String line : com.froggylord.constellation.data.TabList.lines()) {
            if (line.contains("Rift Time Left")) {
                String stripped = net.minecraft.ChatFormatting.stripFormatting(line);
                return "§b" + stripped.replace("Rift Time Left:", "").trim();
            }
        }
        return null;
    }

    private static String motesLine() {
        for (String line : ConstellationClient.loc().getSidebarLines()) {
            Matcher m = MOTES.matcher(line);
            if (m.find()) {
                String bal = "§b" + compact(m.group(1));
                return motesSession > 0 ? bal + " §7(+" + compact(Long.toString(motesSession)) + ")" : bal;
            }
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
