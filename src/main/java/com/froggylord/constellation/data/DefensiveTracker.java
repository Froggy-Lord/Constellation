package com.froggylord.constellation.data;

import com.froggylord.constellation.ConstellationClient;
import com.froggylord.constellation.core.ActionBar;
import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.component.ItemLore;

import java.util.Locale;
import java.util.regex.Pattern;

// ported from devonian (GPL-3.0): features/dungeons/MaskTimers.kt
public final class DefensiveTracker {

    private DefensiveTracker() {}

    private enum Ability {
        BONZO("Bonzo", 180_000L, 3_000L, Pattern.compile("^Your(?: \\u269A)? Bonzo's Mask saved your life!$")),
        PHOENIX("Phoenix", 60_000L, 4_000L, Pattern.compile("^Your Phoenix Pet saved you from certain death!$"));
        final String label;
        final long defaultCooldown;
        final long immunity;
        final Pattern trigger;
        Ability(String label, long defaultCooldown, long immunity, Pattern trigger) {
            this.label = label;
            this.defaultCooldown = defaultCooldown;
            this.immunity = immunity;
            this.trigger = trigger;
        }
    }

    private static final long[] readyAt = new long[Ability.values().length];
    private static final long[] immuneUntil = new long[Ability.values().length];
    private static final long[] cooldownLength = new long[Ability.values().length];
    private static final boolean[] dinged = new boolean[Ability.values().length];
    private static final Pattern COOLDOWN_LORE = Pattern.compile("^Cooldown: (\\d+)s$");

    public static void onChat(String msg) {
        for (Ability ability : Ability.values()) {
            if (ability.trigger.matcher(msg).matches()) {
                trigger(ability);
                return;
            }
        }
    }

    private static void trigger(Ability a) {
        int index = a.ordinal();
        long now = System.currentTimeMillis();
        long cooldown = a == Ability.BONZO ? bonzoCooldown() : a.defaultCooldown;
        cooldownLength[index] = cooldown;
        readyAt[index] = now + cooldown;
        immuneUntil[index] = now + a.immunity;
        dinged[index] = false;
    }

    private static long bonzoCooldown() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return Ability.BONZO.defaultCooldown;
        ItemLore lore = mc.player.getItemBySlot(EquipmentSlot.HEAD).get(DataComponents.LORE);
        if (lore == null) return Ability.BONZO.defaultCooldown;
        for (int i = lore.lines().size() - 1; i >= 0; i--) {
            var match = COOLDOWN_LORE.matcher(lore.lines().get(i).getString().trim());
            if (!match.matches()) continue;
            try {
                return Long.parseLong(match.group(1)) * 1000L;
            } catch (NumberFormatException ignored) {
                return Ability.BONZO.defaultCooldown;
            }
        }
        return Ability.BONZO.defaultCooldown;
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
        double threshold = Math.clamp(cfg.lowHealthPercent, 1, 99) / 100.0;
        if (cfg.lowHealthAlert && ActionBar.hasData() && ActionBar.healthFraction() < threshold && now - lastLowAlert > 4000) {
            lastLowAlert = now;
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null) {
                // title (not sendSystemMessage — that re-fires the chat receive event and would loop);
                // resetTitleTimes so a re-alert while still low re-shows instead of being swallowed
                mc.gui.hud.resetTitleTimes();
                mc.gui.hud.setTitle(Component.literal("§cLOW HEALTH"));
                mc.gui.hud.setSubtitle(Component.literal("§7" + ActionBar.health() + "§8/§7" + ActionBar.maxHealth() + " HP"));
                mc.player.playSound(SoundEvents.NOTE_BLOCK_PLING.value(), 1f, 0.5f);
            }
        }
    }

    public static String hudLine() {
        long now = System.currentTimeMillis();
        StringBuilder sb = new StringBuilder();
        for (Ability a : Ability.values()) {
            int index = a.ordinal();
            long rem = readyAt[index] - now;
            if (rem <= 0) continue;
            if (sb.length() > 0) sb.append("  ");
            sb.append("§7").append(a.label).append(' ');
            long immunity = immuneUntil[index] - now;
            if (immunity > 0) {
                sb.append("§bIMM ").append(String.format(Locale.ROOT, "%.2fs", immunity / 1000.0));
                continue;
            }
            double ratio = cooldownLength[index] <= 0 ? 0 : (double) rem / cooldownLength[index];
            sb.append(ratio >= .75 ? "§c" : ratio >= .5 ? "§6" : ratio >= .25 ? "§e" : "§a");
            sb.append(String.format(Locale.ROOT, "%.1fs", rem / 1000.0));
        }
        return sb.length() == 0 ? null : sb.toString();
    }

    public static void reset() {
        for (int i = 0; i < readyAt.length; i++) {
            readyAt[i] = 0;
            immuneUntil[i] = 0;
            cooldownLength[i] = 0;
            dinged[i] = false;
        }
    }
}
