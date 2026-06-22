package com.froggylord.constellation.core;

import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.minecraft.ChatFormatting;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses the SkyBlock action bar (health / mana / defense). On Hypixel the vanilla health bar
 * is meaningless — the real numbers live in the action bar overlay text — so anything that
 * needs your actual health reads it from here. Shared across constellations (dungeon low-health
 * alerts, the Apollo stat bars, etc).
 */
public final class ActionBar {

    private ActionBar() {}

    private static final Pattern HEALTH = Pattern.compile("(\\d[\\d,]*)/(\\d[\\d,]*)❤");
    private static final Pattern MANA = Pattern.compile("(\\d[\\d,]*)/(\\d[\\d,]*)✎");
    private static final Pattern OVERFLOW = Pattern.compile("(\\d[\\d,]*)ʬ");
    private static final Pattern DEFENSE = Pattern.compile("(\\d[\\d,]*)❈");
    private static final Pattern SKILL = Pattern.compile("\\+([\\d,.]+) (\\w+) \\(([\\d.]+)%\\)");

    private static int health, maxHealth, mana, maxMana, defense, overflowMana;
    private static String skillName = "";
    private static double skillPercent;
    private static long lastUpdate, lastSkillAt;

    public static void init() {
        ClientReceiveMessageEvents.GAME.register((message, overlay) -> {
            if (!overlay) return; // only the action bar
            parse(ChatFormatting.stripFormatting(message.getString()));
        });
    }

    private static void parse(String s) {
        if (s == null) return;
        Matcher h = HEALTH.matcher(s);
        if (h.find()) { health = num(h.group(1)); maxHealth = num(h.group(2)); }
        Matcher m = MANA.matcher(s);
        if (m.find()) { mana = num(m.group(1)); maxMana = num(m.group(2)); }
        Matcher o = OVERFLOW.matcher(s);
        if (o.find()) overflowMana = num(o.group(1));
        Matcher d = DEFENSE.matcher(s);
        if (d.find()) defense = num(d.group(1));
        Matcher sk = SKILL.matcher(s);
        if (sk.find()) {
            skillName = sk.group(2);
            try { skillPercent = Double.parseDouble(sk.group(3)); } catch (NumberFormatException ignored) {}
            lastSkillAt = System.currentTimeMillis();
        }
        lastUpdate = System.currentTimeMillis();
    }

    private static int num(String g) {
        try { return Integer.parseInt(g.replace(",", "")); } catch (NumberFormatException e) { return 0; }
    }

    /** 0..1, or 1 if we haven't seen health yet (don't false-alarm). */
    public static double healthFraction() {
        return maxHealth <= 0 ? 1.0 : (double) health / maxHealth;
    }

    public static boolean hasData() { return maxHealth > 0 && System.currentTimeMillis() - lastUpdate < 5000; }

    public static int health() { return health; }
    public static int maxHealth() { return maxHealth; }
    public static int mana() { return mana; }
    public static int maxMana() { return maxMana; }
    public static int defense() { return defense; }
    public static int overflowMana() { return overflowMana; }

    /** health scaled by defense — the hits you can actually eat. */
    public static int effectiveHealth() { return (int) Math.round(health * (1.0 + defense / 100.0)); }

    public static boolean hasSkill() { return !skillName.isEmpty() && System.currentTimeMillis() - lastSkillAt < 4000; }
    public static String skillName() { return skillName; }
    public static double skillPercent() { return skillPercent; }
}
