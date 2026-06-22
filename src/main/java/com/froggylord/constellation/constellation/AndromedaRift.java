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
    private static final Pattern MOTES = Pattern.compile("Motes:?\\s*([\\d,]+)");

    private AndromedaConfig cfg;

    private static int enigmaSouls = 0;
    private static long lowTimeAt = 0;

    @Override
    public void init(InitContext ctx) {
        cfg = (AndromedaConfig) getConfig();
        net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents.GAME.register((msg, overlay) -> {
            if (overlay || cfg == null || !ConstellationClient.loc().onHypixel()) return;
            String s = msg.getString();
            if (cfg.enigmaSoulTracker && s.contains("SOUL!") && s.contains("Enigma Soul")) {
                enigmaSouls++;
                com.froggylord.constellation.core.StatStore.add("andromeda.enigmaSouls", 1);
                var mc = net.minecraft.client.Minecraft.getInstance();
                if (mc.player != null) {
                    mc.gui.hud.resetTitleTimes();
                    mc.gui.hud.setTitle(net.minecraft.network.chat.Component.literal("§5✦ Enigma Soul!"));
                    mc.player.playSound(net.minecraft.sounds.SoundEvents.AMETHYST_BLOCK_CHIME, 0.8f, 1.0f);
                }
            }
        });
        // warn when the rift clock is about to run out
        ConstellationClient.tick().every(20, "andromeda-lowtime", () -> {
            if (cfg == null || !cfg.riftLowTimeAlert || !inRift()) return;
            for (String line : ConstellationClient.loc().getSidebarLines()) {
                Matcher m = RIFT_TIME.matcher(line);
                if (!m.find()) continue;
                int secs = Integer.parseInt(m.group(1)) * 60 + Integer.parseInt(m.group(2));
                if (secs > 0 && secs <= 60 && System.currentTimeMillis() - lowTimeAt > 30_000) {
                    lowTimeAt = System.currentTimeMillis();
                    var mc = net.minecraft.client.Minecraft.getInstance();
                    if (mc.player != null) {
                        mc.player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§c⏰ Rift time low: " + m.group(1) + ":" + m.group(2)));
                        mc.player.playSound(net.minecraft.sounds.SoundEvents.NOTE_BLOCK_PLING.value(), 1.0f, 0.6f);
                    }
                }
                return;
            }
        });
    }

    @Override
    public void registerHud(HudManager hud) {
        cfg = (AndromedaConfig) getConfig();
        if (cfg == null) return;

        if (cfg.timeHud) {
            hud.register(new HudWidget("andromeda-time", "RiftTime",
                () -> inRift() ? timeLine() : null,
                HudPosition.of(2, 140), cfg.timeHud));
            hud.register(new HudWidget("andromeda-motes", "Motes",
                () -> inRift() ? motesLine() : null,
                HudPosition.of(2, 150), cfg.timeHud));
        }
        if (cfg.enigmaSoulTracker) {
            hud.register(new HudWidget("andromeda-souls", "Souls",
                () -> {
                    if (!inRift()) return null;
                    boolean life = ConstellationClient.cfg() != null && ConstellationClient.cfg().lifetimeStats;
                    long n = life ? com.froggylord.constellation.core.StatStore.getLong("andromeda.enigmaSouls", enigmaSouls) : enigmaSouls;
                    return n > 0 ? "§5✦ " + n + " souls" + (life ? " §8(all-time)" : "") : null;
                },
                HudPosition.of(2, 160), cfg.enigmaSoulTracker));
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

    private static String motesLine() {
        for (String line : ConstellationClient.loc().getSidebarLines()) {
            Matcher m = MOTES.matcher(line);
            if (m.find()) return "§b" + compact(m.group(1));
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
