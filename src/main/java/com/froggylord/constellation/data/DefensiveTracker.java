package com.froggylord.constellation.data;

import com.froggylord.constellation.ConstellationClient;
import net.minecraft.client.Minecraft;
import net.minecraft.sounds.SoundEvents;

/**
 * Tracks the dungeon defensive-ability cooldowns that actually matter — Bonzo's Mask (180s),
 * Spirit Mask (30s) and the Phoenix pet (60s). Hypixel announces each save in chat; we start a
 * countdown from that, surface it on the HUD, and ding when it comes back. Cooldowns are the
 * base values — class CDR will make the real cooldown a touch shorter, so treat it as an upper
 * bound.
 */
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

    /** Called each tick — dings any ability that just came off cooldown. */
    public static void tick() {
        var cfg = ConstellationClient.cfg().orion;
        if (cfg == null || !cfg.abilityReadyDing) return;
        long now = System.currentTimeMillis();
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

    /** HUD line of any active cooldowns, e.g. "Bonzo 2:14  Spirit 0:08", or null if all ready. */
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
