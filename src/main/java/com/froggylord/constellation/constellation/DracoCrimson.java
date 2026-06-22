package com.froggylord.constellation.constellation;

import com.froggylord.constellation.ConstellationClient;
import com.froggylord.constellation.config.DracoConfig;
import com.froggylord.constellation.core.BaseConstellation;
import com.froggylord.constellation.core.InitContext;
import com.froggylord.constellation.core.LocationManager.SkyblockArea;
import com.froggylord.constellation.hud.HudManager;
import com.froggylord.constellation.hud.HudPosition;
import com.froggylord.constellation.hud.HudWidget;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Draco — Crimson Isle. Shows reputation / faction standing off the sidebar (the faction
 * name + rep count are always present on the Isle). Kuudra phases + supplies come later.
 */
public class DracoCrimson extends BaseConstellation {

    @Override public String id() { return "draco"; }
    @Override public String displayName() { return "Draco"; }
    @Override public String description() { return "Crimson Isle — reputation, Kuudra, Dojo"; }

    private static final Pattern REP = Pattern.compile("(Barbarian|Mage) Reputation:?\\s*([\\d,]+)");
    private static final Pattern DOJO = Pattern.compile("Dojo:.*?(\\d+).*");
    private static final Pattern VANQ = Pattern.compile("Vanquisher:?\\s*(\\d+)");

    private DracoConfig cfg;

    private static String kuudraPhase = "";
    private static long kuudraPhaseAt = 0;
    private static long ashfangFrozenUntil = 0;
    private static String abiphoneCaller = "";
    private static long abiphoneCallAt = 0;

    @Override
    public void init(InitContext ctx) {
        cfg = (DracoConfig) getConfig();
        net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents.GAME.register((msg, overlay) -> {
            if (overlay || cfg == null || !ConstellationClient.loc().onHypixel()) return;
            String s = msg.getString();

            // Vanquisher — spawns for everyone nearby, easy to miss in the lava glow
            // Verified pattern from SkyHanni: "A Vanquisher is spawning nearby!"
            if (cfg.vanquisherAlert && s.contains("Vanquisher is spawning")) {
                var mc = net.minecraft.client.Minecraft.getInstance();
                if (mc.player != null) {
                    mc.gui.hud.resetTitleTimes();
                    mc.gui.hud.setTitle(net.minecraft.network.chat.Component.literal("§c☠ VANQUISHER!"));
                    mc.player.playSound(net.minecraft.sounds.SoundEvents.WITHER_SPAWN, 0.7f, 1.3f);
                    if (cfg.vanquisherShare) {
                        var p = mc.player.blockPosition();
                        mc.player.connection.sendCommand("pc Vanquisher @ " + p.getX() + " " + p.getY() + " " + p.getZ());
                    }
                }
            }
            // Kuudra phase cues from the fight chatter
            if (cfg.kuudraPhaseHud) {
                String ph = kuudraPhaseOf(s);
                if (ph != null) { kuudraPhase = ph; kuudraPhaseAt = System.currentTimeMillis(); }
            }
            // Abiphone call — track who's calling
            if (cfg.abiphoneHud && s.contains("Abiphone") && (s.contains("calling") || s.contains("ringing"))) {
                String name = s.replaceAll(".*?:\\s*", "").replace("is calling", "").replace("is ringing", "").trim();
                if (!name.isEmpty() && name.length() < 30) { abiphoneCaller = name; abiphoneCallAt = System.currentTimeMillis(); }
            }
            // Ashfang freeze — you're locked for a few seconds, show the countdown
            if (cfg.ashfangFreezeTimer && s.contains("Ashfang") && s.contains("freezes you")) {
                ashfangFrozenUntil = System.currentTimeMillis() + 5000;
                var mc = net.minecraft.client.Minecraft.getInstance();
                if (mc.player != null) mc.player.playSound(net.minecraft.sounds.SoundEvents.GLASS_BREAK, 0.8f, 0.6f);
            }
        });
    }

    private static String kuudraPhaseOf(String s) {
        if (s.contains("Bring the Fuel Cell")) return "Supplies";
        if (s.contains("Build the Ballista")) return "Build Ballista";
        if (s.contains("Kuudra has surfaced") || s.contains("STUN")) return "Stun";
        if (s.contains("Kuudra is breaking through")) return "Defend";
        if (s.contains("DPS") || s.contains("Burn the Supply")) return "DPS";
        if (s.contains("KUUDRA DOWN") || s.contains("defeated Kuudra")) return "Done";
        return null;
    }

    @Override
    public void registerHud(HudManager hud) {
        cfg = (DracoConfig) getConfig();
        if (cfg == null) return;

        if (cfg.activityHud) {
            hud.register(new HudWidget("draco-rep", "Rep",
                () -> inCrimson() ? repLine() : null,
                HudPosition.of(2, 130), cfg.activityHud));
            hud.register(new HudWidget("draco-dojo", "Dojo",
                () -> inCrimson() ? dojoLine() : null,
                HudPosition.of(2, 140), cfg.activityHud));
        }
        if (cfg.kuudraPhaseHud) {
            hud.register(new HudWidget("draco-kuudra", "Kuudra",
                () -> {
                    if (kuudraPhase.isEmpty() || System.currentTimeMillis() - kuudraPhaseAt > 120_000) return null;
                    return "§6Kuudra: §f" + kuudraPhase;
                },
                HudPosition.of(50, 80), cfg.kuudraPhaseHud));
        }
        if (cfg.ashfangFreezeTimer) {
            hud.register(new HudWidget("draco-ashfang", "Frozen",
                () -> {
                    long left = ashfangFrozenUntil - System.currentTimeMillis();
                    if (left <= 0) return null;
                    return "§b❄ Frozen " + String.format("%.1fs", left / 1000.0);
                },
                HudPosition.of(50, 72), cfg.ashfangFreezeTimer));
        }
        if (cfg.dojoScoreHud) {
            hud.register(new HudWidget("draco-dojo-score", "DojoScore",
                () -> {
                    if (!ConstellationClient.loc().onHypixel()) return null;
                    for (String line : ConstellationClient.loc().getSidebarLines()) {
                        var m = java.util.regex.Pattern.compile("Score:?\\s*(\\d+)").matcher(line);
                        if (m.find()) return "§e🥋 " + m.group(1) + " pts";
                    }
                    return null;
                },
                HudPosition.of(50, 64), cfg.dojoScoreHud));
        }
        if (cfg.abiphoneHud) {
            hud.register(new HudWidget("draco-abiphone", "Abiphone",
                () -> {
                    if (abiphoneCaller.isEmpty() || System.currentTimeMillis() - abiphoneCallAt > 15_000) return null;
                    return "§d📞 " + abiphoneCaller;
                },
                HudPosition.of(50, 56), cfg.abiphoneHud));
        }
        if (cfg.factionQuestHud) {
            hud.register(new HudWidget("draco-faction", "Quest",
                () -> {
                    if (!ConstellationClient.loc().onHypixel()) return null;
                    for (String line : ConstellationClient.loc().getSidebarLines()) {
                        if (line.contains("Quest") || line.contains("Barbarian") || line.contains("Mage")) return "§c⚔ " + line.trim();
                    }
                    return null;
                },
                HudPosition.of(50, 50), cfg.factionQuestHud));
        }
        if (cfg.dojoChallengeHelper) {
            hud.register(new HudWidget("draco-dojo-challenge", "DojoChal",
                () -> {
                    if (!ConstellationClient.loc().onHypixel()) return null;
                    for (String line : ConstellationClient.loc().getSidebarLines()) {
                        if (line.contains("Challenge") || line.contains("Discipline")) return "§e🥋 " + line.trim();
                    }
                    return null;
                },
                HudPosition.of(50, 44), cfg.dojoChallengeHelper));
        }
        if (cfg.trophyFishingHud) {
            hud.register(new HudWidget("draco-trophy", "CrimsonTrophy",
                () -> {
                    if (!ConstellationClient.loc().onHypixel()) return null;
                    for (String line : ConstellationClient.loc().getSidebarLines()) {
                        if (line.contains("Trophy") || line.contains("Fishing")) return "§6🏆 " + line.trim();
                    }
                    return null;
                },
                HudPosition.of(50, 38), cfg.trophyFishingHud));
        }
        if (cfg.freshToolsTimer) {
            hud.register(new HudWidget("draco-freshtools", "FreshTools",
                () -> {
                    if (!ConstellationClient.loc().onHypixel()) return null;
                    for (String line : ConstellationClient.loc().getSidebarLines()) {
                        if (line.contains("Fresh") || line.contains("Tools")) return "§a🛠 " + line.trim();
                    }
                    return null;
                },
                HudPosition.of(50, 32), cfg.freshToolsTimer));
        }
        if (cfg.supplyObjectiveHud) {
            hud.register(new HudWidget("draco-supply", "Supplies",
                () -> {
                    if (!ConstellationClient.loc().onHypixel()) return null;
                    for (String line : ConstellationClient.loc().getSidebarLines()) {
                        if (line.contains("Supply") || line.contains("Pile") || line.contains("Fuel Cell")) return "§c📦 " + line.trim();
                    }
                    return null;
                },
                HudPosition.of(50, 26), cfg.supplyObjectiveHud));
        }
    }

    private static boolean inCrimson() {
        return ConstellationClient.loc().area() == SkyblockArea.CRIMSON_ISLE;
    }

    private static String repLine() {
        for (String line : ConstellationClient.loc().getSidebarLines()) {
            Matcher m = REP.matcher(line);
            if (m.find()) return "§c" + m.group(1) + " §f" + m.group(2)
                + " §7(" + vanqLine() + ")";
        }
        return null;
    }

    private static String dojoLine() {
        for (String line : ConstellationClient.loc().getSidebarLines()) {
            Matcher m = DOJO.matcher(line);
            if (m.find()) return "§eDojo §f" + m.group(1);
        }
        return null;
    }

    private static String vanqLine() {
        for (String line : ConstellationClient.loc().getSidebarLines()) {
            Matcher m = VANQ.matcher(line);
            if (m.find()) return m.group(1) + " kills";
        }
        return "? kills";
    }
}
