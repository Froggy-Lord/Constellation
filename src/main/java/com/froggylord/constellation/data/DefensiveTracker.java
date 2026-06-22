package com.froggylord.constellation.data;

import com.froggylord.constellation.ConstellationClient;
import com.froggylord.constellation.core.ActionBar;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;

public final class DefensiveTracker {

    private DefensiveTracker() {}

    private enum Ability {
        BONZO("Bonzo", 180_000L),
        SPIRIT("Spirit", 30_000L),
        PHOENIX("Phoenix", 60_000L);
        final String label;
        final long cd;
        Ability(String label, long cd) { this.label = label; this.cd = cd; }
    }

    private static final long[] readyAt = new long[Ability.values().length];
    private static final boolean[] dinged = new boolean[Ability.values().length];

    public static void onChat(String msg) {
        if (msg.contains("Bonzo's Mask") && msg.contains("saved your life")) trigger(Ability.BONZO);
        else if (msg.contains("Spirit Mask") && msg.contains("saved your life")) trigger(Ability.SPIRIT);
        else if (msg.contains("Phoenix") && msg.contains("saved you")) trigger(Ability.PHOENIX);
    }

    private static void trigger(Ability a) {
        readyAt[a.ordinal()] = System.currentTimeMillis() + a.cd;
        dinged[a.ordinal()] = false;
    }

    private static long lastLowAlert = 0;

    public static void tick() {
        var cfg = ConstellationClient.cfg().orion;
        if (cfg == null) return;
        long now = System.currentTimeMillis();
        if (cfg.abilityReadyDing) {
            for (Ability a : Ability.values()) {
                int i = a.ordinal();
                if (readyAt[i] == 0 || dinged[i]) continue;
                if (now >= readyAt[i]) {
                    dinged[i] = true;
                    Minecraft mc = Minecraft.getInstance();
                    if (mc.player != null) mc.player.playSound(SoundEvents.NOTE_BLOCK_BELL.value(), 0.7f, 1.4f);
                }
            }
        }
        if (cfg.lowHealthAlert && ActionBar.hasData() && ActionBar.healthFraction() < 0.35 && now - lastLowAlert > 4000) {
            lastLowAlert = now;
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null) {
                mc.gui.hud.resetTitleTimes();
                mc.gui.hud.setTitle(Component.literal("§c❤ LOW HEALTH"));
                mc.player.playSound(SoundEvents.NOTE_BLOCK_PLING.value(), 1f, 0.5f);
            }
        }
    }

    public static String hudLine() {
        long now = System.currentTimeMillis();
        StringBuilder sb = new StringBuilder();
        for (Ability a : Ability.values()) {
            long rem = readyAt[a.ordinal()] - now;
            if (rem <= 0) continue;
            int s = (int) (rem / 1000);
            if (sb.length() > 0) sb.append("  ");
            sb.append("§7").append(a.label).append(" §f").append(s / 60).append(':').append(String.format("%02d", s % 60));
        }
        return sb.length() == 0 ? null : sb.toString();
    }

    public static void reset() {
        for (int i = 0; i < readyAt.length; i++) { readyAt[i] = 0; dinged[i] = false; }
    }
}
