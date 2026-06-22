package com.froggylord.constellation.constellation;

import com.froggylord.constellation.ConstellationClient;
import com.froggylord.constellation.config.PerseusConfig;
import com.froggylord.constellation.core.BaseConstellation;
import com.froggylord.constellation.core.InitContext;
import com.froggylord.constellation.hud.HudManager;
import com.froggylord.constellation.hud.HudPosition;
import com.froggylord.constellation.hud.HudWidget;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Perseus — slayers. Reads slayer XP off the sidebar (a stable Hypixel signal) and shows
 * a compact readout. Boss timer deferred — the BossHealthOverlay access path changed in 26.2.
 */
public class PerseusSlayers extends BaseConstellation {

    @Override public String id() { return "perseus"; }
    @Override public String displayName() { return "Perseus"; }
    @Override public String description() { return "Slayers — XP bar, boss timer (TBD)"; }

    private static final Pattern SLAYER_XP = Pattern.compile("Slayer XP:?\\s*([\\d,]+)");
    private static final Pattern RNG = Pattern.compile("RNG Meter.*?([\\d.]+)%");
    private static final Pattern ZEALOT = Pattern.compile("Zealot.*?(\\d+)");
    private static final Pattern PROTECTOR = Pattern.compile("Protector:?\\s*(\\d+)%");
    private static int zealotKills = 0;
    private static int zealotSinceEye = 0;
    private static int summoningEyes = 0;

    private PerseusConfig cfg;

    private static String lastBoss = "";
    private static long lastBossAt = 0;

    @Override
    public void init(InitContext ctx) {
        cfg = (PerseusConfig) getConfig();
        net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents.GAME.register((msg, overlay) -> {
            if (!ConstellationClient.loc().onHypixel()) return;
            String s = msg.getString();
            // Endstone Protector spawn
            if (s.contains("The Endstone Protector") || s.contains("Protector") && s.contains("spawn"))
                zealotKills = 0; // reset zealot count after protector spawn
            // Summoning Eye drop
            if (s.contains("SUMMONING EYE") || s.contains("Summoning Eye")) {
                summoningEyes++;
                zealotSinceEye = 0;
            }
            // Zealot kill (from bestiary or kill combo — rough tracking)
            if (s.contains("Zealot") && !s.contains("Bruiser")) { zealotKills++; zealotSinceEye++; }
            // Slayer boss spawn
            if (s.contains("SLAYER") && s.contains("SPAWN")) {
                lastBoss = s.trim();
                lastBossAt = System.currentTimeMillis();
                var mc = net.minecraft.client.Minecraft.getInstance();
                if (mc.player != null) {
                    mc.gui.hud.resetTitleTimes();
                    mc.gui.hud.setTitle(net.minecraft.network.chat.Component.literal("§c⚔ " + lastBoss));
                    mc.player.playSound(net.minecraft.sounds.SoundEvents.WITHER_SPAWN, 0.7f, 1.0f);
                }
            }
            // Mini-boss spawn
            if (s.contains(" spawned") && (s.contains("Revenant") || s.contains("Tarantula") || s.contains("Sven") || s.contains("Voidgloom") || s.contains("Inferno"))) {
                var mc = net.minecraft.client.Minecraft.getInstance();
                if (mc.player != null) mc.player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§c⚡ Miniboss! " + s.trim()));
            }
            // Brood Mother spawn alert (SBA feature)
            if (s.contains("Brood Mother") && s.contains("hatched")) {
                var mc = net.minecraft.client.Minecraft.getInstance();
                if (mc.player != null) {
                    mc.gui.hud.resetTitleTimes();
                    mc.gui.hud.setTitle(net.minecraft.network.chat.Component.literal("§4🕷 Brood Mother!"));
                    mc.player.playSound(net.minecraft.sounds.SoundEvents.WITHER_SPAWN, 0.5f, 0.3f);
                }
            }
        });
    }

    @Override
    public void registerHud(HudManager hud) {
        cfg = (PerseusConfig) getConfig();
        if (cfg == null) return;

        if (cfg.xpBar) {
            hud.register(new HudWidget("perseus-xp", "SlayerXP",
                () -> ConstellationClient.loc().onHypixel() ? xpLine() : null,
                HudPosition.of(50, 78), cfg.xpBar));
            hud.register(new HudWidget("perseus-rng", "RNG",
                () -> ConstellationClient.loc().onHypixel() ? rngLine() : null,
                HudPosition.of(50, 86), cfg.xpBar));
            hud.register(new HudWidget("perseus-zealot", "Zealot",
                () -> {
                    if (zealotKills == 0) return null;
                    String s = "§d⏣ " + zealotKills + " kills";
                    if (summoningEyes > 0) s += " §eEyes: " + summoningEyes;
                    if (zealotSinceEye > 0) s += " §7(since: " + zealotSinceEye + ")";
                    return s;
                },
                HudPosition.of(50, 94), cfg.xpBar));
            hud.register(new HudWidget("perseus-protector", "Protector",
                () -> {
                    for (String line : ConstellationClient.loc().getSidebarLines()) {
                        Matcher m = PROTECTOR.matcher(line);
                        if (m.find()) return "§5Protector §f" + m.group(1) + "%";
                    }
                    return null;
                },
                HudPosition.of(50, 102), cfg.xpBar));
        }
    }

    private static String xpLine() {
        for (String line : ConstellationClient.loc().getSidebarLines()) {
            Matcher m = SLAYER_XP.matcher(line);
            if (m.find()) return "§d" + compact(parse(m.group(1))) + " XP";
        }
        return null;
    }

    private static String rngLine() {
        for (String line : ConstellationClient.loc().getSidebarLines()) {
            Matcher m = RNG.matcher(line);
            if (m.find()) return "§eRNG " + m.group(1) + "%";
        }
        return null;
    }

    private static long parse(String s) { try { return Long.parseLong(s.replace(",", "")); } catch (NumberFormatException e) { return 0; } }
    private static String compact(long n) {
        if (n < 1000) return Long.toString(n);
        if (n < 1_000_000) return String.format("%.1fk", n / 1000.0);
        return String.format("%.2fM", n / 1_000_000.0);
    }
}
