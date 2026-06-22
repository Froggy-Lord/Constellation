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

    private static int inquisitors = 0;
    private static int mythosDrops = 0;
    // diana burrow triangulation — spade direction samples
    private static final double[][] spadeSamples = new double[4][3]; // {x, z, angle}
    private static int spadeIdx = 0;
    private static double burrowX = Double.NaN, burrowZ = Double.NaN;
    // the mythological-ritual loot lines worth counting
    private static final String[] MYTHOS = {
        "Griffin Feather", "Crown of Greed", "Washed-up Souvenir", "Daedalus Stick",
        "Minos Relic", "Enchanted Egg", "Dwarf Turtle Shelmet", "Antique Remedies",
        "Chimera", "Minos Champion", "Minotaur", "Minos Inquisitor"
    };

    @Override
    public void init(InitContext ctx) {
        cfg = (CygnusConfig) getConfig();
        net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents.GAME.register((msg, overlay) -> {
            if (overlay || cfg == null || !ConstellationClient.loc().onHypixel()) return;
            String s = msg.getString();

            // Minos Inquisitor — the burrow everyone wants. shout it loud.
            if (cfg.dianaInquisitorAlert && s.contains("You dug out a Minos Inquisitor")) {
                inquisitors++;
                com.froggylord.constellation.core.StatStore.add("cygnus.diana.inquisitors", 1);
                var mc = net.minecraft.client.Minecraft.getInstance();
                if (mc.player != null) {
                    mc.gui.hud.resetTitleTimes();
                    mc.gui.hud.setTitle(net.minecraft.network.chat.Component.literal("§5⚔ INQUISITOR!"));
                    mc.player.playSound(net.minecraft.sounds.SoundEvents.WITHER_SPAWN, 0.7f, 1.2f);
                    if (cfg.dianaInquisitorShare) {
                        var p = mc.player.blockPosition();
                        mc.player.connection.sendCommand("pc Inquisitor @ " + p.getX() + " " + p.getY() + " " + p.getZ());
                    }
                }
            }
            // mythos drop tally — the dig loot lines
            if (cfg.dianaDropTracker && (s.contains("You dug out") || s.contains("RARE DROP") || s.contains("PET DROP"))) {
                for (String d : MYTHOS) { if (s.contains(d)) { mythosDrops++; com.froggylord.constellation.core.StatStore.add("cygnus.diana.drops", 1); break; } }
            }
            // spade direction — triangulate burrow position
            if (cfg.dianaBurrowWaypoints && s.contains("The Spade points")) {
                String low = s.toLowerCase(java.util.Locale.ROOT);
                double angle = -1;
                if (low.contains("north") && low.contains("west")) angle = -135;
                else if (low.contains("north") && low.contains("east")) angle = 135;
                else if (low.contains("south") && low.contains("west")) angle = -45;
                else if (low.contains("south") && low.contains("east")) angle = 45;
                else if (low.contains("north")) angle = 180;
                else if (low.contains("south")) angle = 0;
                else if (low.contains("west")) angle = -90;
                else if (low.contains("east")) angle = 90;
                if (angle != -1) {
                    var mc = net.minecraft.client.Minecraft.getInstance();
                    if (mc.player != null) {
                        spadeSamples[spadeIdx % 4][0] = mc.player.getX();
                        spadeSamples[spadeIdx % 4][1] = mc.player.getZ();
                        spadeSamples[spadeIdx % 4][2] = Math.toRadians(angle);
                        spadeIdx++;
                        triangulate();
                    }
                }
            }
        });

        // Carnival helpers — chat-based hints
        if (cfg.carnivalHelper) {
            net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents.GAME.register((msg, overlay) -> {
                if (overlay || !ConstellationClient.loc().onHypixel()) return;
                String s = msg.getString().toLowerCase(java.util.Locale.ROOT);
                var mc = net.minecraft.client.Minecraft.getInstance();
                if (mc.player == null) return;
                if (s.contains("catch a fish") || s.contains("fishing game")) {
                    mc.player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§b🎣 Carnival: Catch a Fish — aim for the bubbles!"));
                } else if (s.contains("zombie shootout") || s.contains("shootout")) {
                    mc.player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§c🎯 Carnival: Zombie Shootout — aim for the heads!"));
                } else if (s.contains("chivalrous") || s.contains("carnival")) {
                    mc.player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§e🎪 Carnival game starting!"));
                }
            });
        }

        // world render — burrow waypoint
        ConstellationClient.world().register(wctx -> {
            if (cfg == null || !cfg.dianaBurrowWaypoints) return;
            if (!ConstellationClient.loc().onHypixel()) return;
            if (Double.isNaN(burrowX)) return;
            var box = new net.minecraft.world.phys.AABB(burrowX - 0.5, 60, burrowZ - 0.5, burrowX + 0.5, 128, burrowZ + 0.5);
            wctx.highlight(box, 0x80FFAA00, true);
            wctx.beam(burrowX, 65, burrowZ, 0xFFFFAA00, 30, true);
        });
    }

    private static void triangulate() {
        if (spadeIdx < 2) return;
        // take the 2 most recent samples and intersect their rays
        double x1 = spadeSamples[(spadeIdx - 2) % 4][0];
        double z1 = spadeSamples[(spadeIdx - 2) % 4][1];
        double a1 = spadeSamples[(spadeIdx - 2) % 4][2];
        double x2 = spadeSamples[(spadeIdx - 1) % 4][0];
        double z2 = spadeSamples[(spadeIdx - 1) % 4][1];
        double a2 = spadeSamples[(spadeIdx - 1) % 4][2];

        double dx1 = Math.sin(a1), dz1 = -Math.cos(a1);
        double dx2 = Math.sin(a2), dz2 = -Math.cos(a2);
        double det = dx1 * dz2 - dz1 * dx2;
        if (Math.abs(det) < 0.001) return; // parallel rays

        double t = ((x2 - x1) * dz2 - (z2 - z1) * dx2) / det;
        burrowX = x1 + t * dx1;
        burrowZ = z1 + t * dz1;
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
        if (cfg.dianaDropTracker) {
            hud.register(new HudWidget("cygnus-diana", "Diana",
                () -> {
                    boolean life = ConstellationClient.cfg() != null && ConstellationClient.cfg().lifetimeStats;
                    long inq = life ? com.froggylord.constellation.core.StatStore.getLong("cygnus.diana.inquisitors", inquisitors) : inquisitors;
                    long drops = life ? com.froggylord.constellation.core.StatStore.getLong("cygnus.diana.drops", mythosDrops) : mythosDrops;
                    if (inq == 0 && drops == 0) return null;
                    return "§5⚔ " + inq + " §7| §6drops §f" + drops + (life ? " §8(all-time)" : "");
                },
                HudPosition.of(2, 138), cfg.dianaDropTracker));
        }
        if (cfg.newYearCakeTracker) {
            hud.register(new HudWidget("cygnus-cake", "NewYear",
                () -> {
                    if (!ConstellationClient.loc().onHypixel()) return null;
                    for (String line : ConstellationClient.loc().getSidebarLines()) {
                        if (line.contains("New Year") || line.contains("Cake")) return "§6🎂 " + line.trim();
                    }
                    return null;
                },
                HudPosition.of(2, 146), cfg.newYearCakeTracker));
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
