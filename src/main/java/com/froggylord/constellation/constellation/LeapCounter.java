package com.froggylord.constellation.constellation;

import com.froggylord.constellation.ConstellationClient;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;

import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;

// ported from devonian (GPL-3.0): features/dungeons/f7/LeapCounter.kt
public final class LeapCounter {

    private static final Pattern LEAPED_TO = Pattern.compile("^You have teleported to \\w{1,16}!$");
    private static final Spot[] SPOTS = {
        new Spot(58.5, 109.0, 131.5, 1.5, 4),
        new Spot(60.5, 132.0, 139.0, 2.0, 4),
        new Spot(69.5, 109.0, 122.5, 1.0, 4),
        new Spot(48.5, 109.0, 122.5, 1.0, 4),
        new Spot(54.5, 115.0, 50.5, 1.5, 4),
        new Spot(2.5, 109.0, 104.5, 3.0, 3),
        new Spot(18.5, 121.5, 92.0, 2.0, 3),
        new Spot(54.5, 5.0, 76.5, 8.0, 4)
    };

    private static boolean initialized;
    private static boolean activeLastTick;
    private static long lastLeapMs = -1;
    private static String hudText;

    private LeapCounter() {}

    public static void init() {
        if (initialized) return;
        initialized = true;
        ClientReceiveMessageEvents.ALLOW_GAME.register((message, overlay) -> {
            if (!overlay) {
                String text = ChatFormatting.stripFormatting(message.getString());
                if (text != null && LEAPED_TO.matcher(text).matches()) lastLeapMs = System.currentTimeMillis();
            }
            return true;
        });
        ConstellationClient.tick().every(1, "orion-leap-counter", LeapCounter::tick);
    }

    public static String hudText() {
        return hudText;
    }

    private static void tick() {
        var cfg = ConstellationClient.cfg().orion;
        Minecraft mc = Minecraft.getInstance();
        boolean active = cfg != null && cfg.leapCounter && mc.player != null && mc.level != null
            && ConstellationClient.loc().inDungeons()
            && ConstellationClient.dungeon().inBoss()
            && ConstellationClient.dungeon().floor().endsWith("7");
        if (!active) {
            if (activeLastTick) reset();
            activeLastTick = false;
            hudText = null;
            return;
        }
        activeLastTick = true;

        if (lastLeapMs >= 0 && System.currentTimeMillis() - lastLeapMs < 3000) {
            hudText = null;
            return;
        }

        Spot current = null;
        for (Spot spot : SPOTS) {
            if (mc.player.distanceToSqr(spot.x, spot.y, spot.z) <= spot.radius * spot.radius) {
                current = spot;
                break;
            }
        }
        if (current == null || current.triggered) {
            hudText = null;
            return;
        }

        Set<String> teammates = new HashSet<>();
        for (var teammate : ConstellationClient.dungeon().teammates()) {
            teammates.add(teammate.name().toLowerCase(java.util.Locale.ROOT));
        }
        int count = 0;
        for (var player : mc.level.players()) {
            if (player == mc.player) continue;
            String name = player.getGameProfile().name().toLowerCase(java.util.Locale.ROOT);
            if (!teammates.contains(name)) continue;
            if (player.distanceToSqr(current.x, current.y, current.z) <= current.radius * current.radius) count++;
        }

        if (count == 0) {
            hudText = null;
            return;
        }
        String colour = count >= current.expected ? "§a" : count >= Math.max(1, current.expected / 2) ? "§e" : "§c";
        hudText = colour + count + "§f/§9" + current.expected + " Leaped";

        if (count >= current.expected) {
            current.triggered = true;
            if (cfg.leapCounterAlert) {
                mc.gui.hud.resetTitleTimes();
                mc.gui.hud.setTitle(Component.literal("§9" + current.expected + " have leaped"));
                if (cfg.leapCounterSound) {
                    mc.player.playSound(SoundEvents.NOTE_BLOCK_PLING.value(), 1.0f, 1.4f);
                }
            }
        }
    }

    private static void reset() {
        for (Spot spot : SPOTS) spot.triggered = false;
        lastLeapMs = -1;
        hudText = null;
    }

    private static final class Spot {
        private final double x;
        private final double y;
        private final double z;
        private final double radius;
        private final int expected;
        private boolean triggered;

        private Spot(double x, double y, double z, double radius, int expected) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.radius = radius;
            this.expected = expected;
        }
    }
}
