package com.froggylord.constellation.constellation;

import com.froggylord.constellation.ConstellationClient;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;

// ported from Odin (BSD-3-Clause): features/impl/dungeon/BloodCamp.kt
// ported from Athen (BSD-3-Clause): modules/impl/dungeon/WatcherHelper.kt
public final class BloodTimer {

    private BloodTimer() {}

    // Watcher's line when it sends the last wave at you — the window to finish blood
    private static final String WATCHER_MOVE = "[BOSS] The Watcher: Let's see how you can handle this.";
    // Watcher's line once every mob is dead and the boss door opens
    private static final String WATCHER_PASS = "[BOSS] The Watcher: You have proven yourself. You may pass.";

    private static boolean active;
    private static boolean finalWave;
    private static boolean killNow;
    private static long startMs;
    private static long speakMs;
    private static int serverTicks;
    private static int startTick;
    private static int killAtTick;

    public static void init() { reset(); }

    public static void reset() {
        active = false;
        finalWave = false;
        killNow = false;
        startMs = 0;
        speakMs = 0;
        startTick = -1;
        killAtTick = -1;
    }

    public static void onChat(String msg) {
        if (msg == null) return;
        if (msg.equals(WATCHER_PASS)) {
            if (active && alertsEnabled()) alert("§aBlood clear", SoundEvents.NOTE_BLOCK_PLING.value(), 1.6f);
            active = false;
            finalWave = false;
            killNow = false;
            return;
        }
        if (msg.equals(WATCHER_MOVE)) {
            begin();
            if (speakMs == 0) speakMs = System.currentTimeMillis() - startMs;
            if (!finalWave) {
                int dialogTicks = Math.max(0, serverTicks - startTick);
                int targetTick = predictedKillTick(dialogTicks);
                killAtTick = serverTicks + Math.max(0, targetTick - dialogTicks);
                if (alertsEnabled()) alert(speakAlert(), SoundEvents.WITHER_SPAWN, 0.8f);
                PartyMessages.send("watcher-move", java.util.Map.of("seconds",
                    String.format("%.1f", Math.max(0, killAtTick - serverTicks) / 20.0)));
            }
            finalWave = true;
            return;
        }
        // any other Watcher line marks the blood room as active (greeting / taunts)
        if (msg.startsWith("[BOSS] The Watcher:")) begin();
    }

    // also called when a Blood door is opened, in case the Watcher greeting was missed
    public static void onBloodDoor() { begin(); }

    public static boolean isActive() { return active; }

    // ported from devonian (GPL-3.0): api/events/EventBus.kt (negative ping packet server clock)
    public static void onServerTick() {
        serverTicks++;
        if (!active || !finalWave || killNow || killAtTick < 0 || serverTicks < killAtTick) return;
        killNow = true;
        if (alertsEnabled()) alert("§cWatcher: Kill Now", SoundEvents.NOTE_BLOCK_PLING.value(), 1.3f);
    }

    private static void begin() {
        if (!active) {
            active = true;
            startMs = System.currentTimeMillis();
            startTick = serverTicks;
        }
    }

    // HUD supplier: null hides the widget
    public static String hudText() {
        if (!ConstellationClient.loc().inDungeons()) { reset(); return null; }
        if (!active) return null;
        long elapsed = System.currentTimeMillis() - startMs;
        long s = elapsed / 1000;
        String time = String.format("%d:%02d", s / 60, s % 60);
        String speak = speakMs > 0 ? " §7| Speak §f" + String.format("%.1fs", speakMs / 1000.0) : "";
        String kill = finalWave
            ? killNow ? " §4| KILL NOW" : " §7| Kill §c" + String.format("%.1fs", Math.max(0, killAtTick - serverTicks) / 20.0)
            : "";
        return "§cBlood " + time + speak + kill;
    }

    // ported from devonian (GPL-3.0): features/dungeons/clear/WatcherKillAlert.kt
    private static int predictedKillTick(int dialogTicks) {
        if (dialogTicks < 390) return 440;
        if (dialogTicks < 441) return 460;
        if (dialogTicks < 460) return 500;
        if (dialogTicks < 490) return 520;
        if (dialogTicks < 510) return 540;
        if (dialogTicks < 550) return 580;
        if (dialogTicks < 570) return 620;
        if (dialogTicks < 610) return 640;
        if (dialogTicks < 630) return 680;
        if (dialogTicks < 670) return 700;
        if (dialogTicks < 690) return 740;
        if (dialogTicks < 730) return 760;
        return dialogTicks + 60;
    }

    private static String speakAlert() {
        String speed = speakMs < 22_000 ? "Fast" : speakMs < 23_000 ? "Normal" : speakMs < 25_000 ? "Slow" : "Very slow";
        return "§c" + speed + " Watcher: " + String.format("%.1fs", speakMs / 1000.0);
    }

    private static boolean alertsEnabled() {
        var cfg = ConstellationClient.cfg().orion;
        return cfg != null && cfg.bloodTimer;
    }

    private static void alert(String title, net.minecraft.sounds.SoundEvent sound, float pitch) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        mc.gui.hud.resetTitleTimes();
        mc.gui.hud.setTitle(Component.literal(title));
        mc.player.playSound(sound, 0.7f, pitch);
    }
}
