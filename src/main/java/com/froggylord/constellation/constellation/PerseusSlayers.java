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

public class PerseusSlayers extends BaseConstellation {

    @Override public String id() { return "perseus"; }
    @Override public String displayName() { return "Perseus"; }
    @Override public String description() { return "slayer hud and alerts"; }

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

    private static final java.util.regex.Pattern BOSS_NAME =
        java.util.regex.Pattern.compile("(Revenant Horror|Tarantula Broodfather|Sven Packmaster|Voidgloom Seraph|Inferno Demonlord|Riftstalker Bloodfiend)\\b");

    private static long slayerStartAt = 0;
    private static long slayerLastMs = 0;
    private static long slayerBestMs = 0;

    private static String fmt(long ms) {
        long s = ms / 1000;
        return String.format("%d:%02d", s / 60, s % 60);
    }

    private static long pbMs() {
        boolean lifetime = ConstellationClient.cfg() != null && ConstellationClient.cfg().lifetimeStats;
        if (lifetime) return com.froggylord.constellation.core.StatStore.getLong("perseus.slayer.bestMs", slayerBestMs);
        return slayerBestMs;
    }

    private static int bestiaryKills = 0;

    
    private static final int[][] RELICS = {
        {-342,122,-253},{-384,89,-225},{-274,100,-178},{-178,136,-297},{-147,83,-335},
        {-188,80,-346},{-183,68,-283},{-342,89,-221},{-355,86,-213},{-372,89,-242},
        {-354,73,-285},{-317,69,-273},{-296,37,-270},{-275,64,-272},{-303,71,-318},
        {-311,69,-251},{-348,65,-202},{-328,50,-238},{-313,58,-250},{-300,51,-254},
        {-284,49,-234},{-300,50,-218},{-236,51,-239},{-183,51,-252},{-217,58,-304},
        {-272,48,-291},{-225,70,-316},{-254,57,-279},
    };

    @Override
    public void init(InitContext ctx) {
        cfg = (PerseusConfig) getConfig();

        // scan sidebar for boss name so the title shows "⚔ Revenant Horror" not "⚔ "
        ConstellationClient.tick().every(20, "perseus-bossname", () -> {
            if (!ConstellationClient.loc().onHypixel()) return;
            for (String line : ConstellationClient.loc().getSidebarLines()) {
                var m = BOSS_NAME.matcher(line);
                if (m.find()) lastBoss = m.group(1);
            }
        });

        ConstellationClient.world().register(wctx -> {
            if (cfg == null || !cfg.xpBar) return;
            if (ConstellationClient.loc().area() != com.froggylord.constellation.core.LocationManager.SkyblockArea.SPIDER_DEN) return;
            var mc = net.minecraft.client.Minecraft.getInstance();
            if (mc.level == null || mc.player == null) return;
            for (int[] p : RELICS) {
                double x = p[0], y = p[1], z = p[2];
                double dist = mc.player.position().distanceToSqr(x, y, z);
                if (dist > 3600) continue;
                wctx.beam(x, y+1, z, 0xFFFF6600, 5, true);
                wctx.label(new net.minecraft.world.phys.Vec3(x, y+2, z), "Relic", 0xFFFF6600, true);
            }
        });

        net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents.GAME.register((msg, overlay) -> {
            if (!ConstellationClient.loc().onHypixel()) return;
            String s = msg.getString();
            // endstone protector spawn
            if (s.contains("The Endstone Protector") || s.contains("Protector") && s.contains("spawn"))
                zealotKills = 0;
            // broodmother in spider's den — real lines: "Broodmother spawned!" / "Broodmother§7: §eAlive!"
            if (s.contains("Broodmother") && (s.contains("spawned") || s.contains("Alive"))) {
                var mc2 = net.minecraft.client.Minecraft.getInstance();
                if (mc2.player != null) {
                    mc2.gui.hud.resetTitleTimes();
                    mc2.gui.hud.setTitle(net.minecraft.network.chat.Component.literal("§4🕷 Broodmother!"));
                    mc2.player.playSound(net.minecraft.sounds.SoundEvents.WITHER_SPAWN, 0.7f, 0.8f);
                }
            }
            
            if (s.contains("SUMMONING EYE") || s.contains("Summoning Eye")) {
                summoningEyes++;
                zealotSinceEye = 0;
            }
            
            if (s.contains("Zealot") && !s.contains("Bruiser")) { zealotKills++; zealotSinceEye++; }
            
            // real literal: "SLAYER QUEST STARTED!" — lastBoss set from previous sidebar parse
            if (s.contains("SLAYER") && s.contains("SPAWN")) {
                lastBossAt = System.currentTimeMillis();
                slayerStartAt = System.currentTimeMillis();
                var mc = net.minecraft.client.Minecraft.getInstance();
                if (mc.player != null) {
                    mc.gui.hud.resetTitleTimes();
                    mc.gui.hud.setTitle(net.minecraft.network.chat.Component.literal("§c⚔ " + lastBoss));
                    mc.player.playSound(net.minecraft.sounds.SoundEvents.WITHER_SPAWN, 0.7f, 1.0f);
                }
            }
            
            if (slayerStartAt > 0 && (s.contains("SLAYER QUEST COMPLETE") || s.contains("NICE! SLAYER BOSS SLAIN"))) {
                slayerLastMs = System.currentTimeMillis() - slayerStartAt;
                if (slayerBestMs == 0 || slayerLastMs < slayerBestMs) slayerBestMs = slayerLastMs;
                slayerStartAt = 0;
                long lifeBest = com.froggylord.constellation.core.StatStore.recordBest("perseus.slayer.bestMs", slayerLastMs);
                long lifeKills = com.froggylord.constellation.core.StatStore.add("perseus.slayer.kills", 1);
                var mc = net.minecraft.client.Minecraft.getInstance();
                if (mc.player != null)
                    mc.player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                        "§a✔ Slayer §f" + fmt(slayerLastMs) + " §7(pb " + fmt(lifeBest) + ", #" + lifeKills + " all-time)"));
            }
            // mini-boss spawn
            if (s.contains(" spawned") && (s.contains("Revenant") || s.contains("Tarantula") || s.contains("Sven") || s.contains("Voidgloom") || s.contains("Inferno"))) {
                var mc = net.minecraft.client.Minecraft.getInstance();
                if (mc.player != null) {
                    mc.player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§c⚡ Miniboss! " + s.trim()));
                    if (cfg.minibossFlash) {
                        mc.gui.hud.resetTitleTimes();
                        mc.gui.hud.setTitle(net.minecraft.network.chat.Component.literal("§c⚡ Miniboss!"));
                    }
                }
            }
            
            if (cfg.skillLevelUpAlert) {
                String stripped2 = net.minecraft.ChatFormatting.stripFormatting(s);
                if (stripped2.contains("LEVEL UP") || stripped2.contains("SKYBLOCK LEVEL UP") || stripped2.contains("SKILL LEVEL UP")) {
                    var mc = net.minecraft.client.Minecraft.getInstance();
                    if (mc.player != null) {
                        mc.gui.hud.resetTitleTimes();
                        mc.gui.hud.setTitle(net.minecraft.network.chat.Component.literal("§e⬆ LEVEL UP!"));
                        mc.player.playSound(net.minecraft.sounds.SoundEvents.PLAYER_LEVELUP, 1f, 1.0f);
                    }
                }
                // real: "BESTIARY MILESTONE 25"
                var bm = java.util.regex.Pattern.compile("BESTIARY MILESTONE (\\d+)").matcher(stripped2);
                if (bm.find()) {
                    int tier = Integer.parseInt(bm.group(1));
                    com.froggylord.constellation.core.StatStore.add("perseus.bestiary.tier", tier);
                    var mc = net.minecraft.client.Minecraft.getInstance();
                    if (mc.player != null) {
                        mc.gui.hud.resetTitleTimes();
                        mc.gui.hud.setTitle(net.minecraft.network.chat.Component.literal("§d📖 Bestiary §f" + tier + "§d!"));
                        mc.player.playSound(net.minecraft.sounds.SoundEvents.PLAYER_LEVELUP, 1f, 1.2f);
                    }
                }
            }

            if (cfg.brokenHyperionAlert && (s.contains("out of charges") || s.contains("no more charges") || s.contains("ran out of"))) {
                var mc = net.minecraft.client.Minecraft.getInstance();
                if (mc.player != null) {
                    mc.gui.hud.resetTitleTimes();
                    mc.gui.hud.setTitle(net.minecraft.network.chat.Component.literal("§c⚡ OUT OF CHARGES!"));
                    mc.player.playSound(net.minecraft.sounds.SoundEvents.NOTE_BLOCK_PLING.value(), 1f, 0.4f);
                }
            }
            
            if (cfg.rareDropEffect) {
                String stripped = net.minecraft.ChatFormatting.stripFormatting(s);
                if (stripped.contains("RARE DROP") || stripped.contains("CRAZY RARE DROP") || stripped.contains("PRAY RNGESUS")) {
                var mc = net.minecraft.client.Minecraft.getInstance();
                if (mc.player != null) {
                    mc.gui.hud.resetTitleTimes();
                    mc.gui.hud.setTitle(net.minecraft.network.chat.Component.literal("§d§k!!!§r §6" + stripped + " §d§k!!!"));
                    mc.player.playSound(net.minecraft.sounds.SoundEvents.PLAYER_LEVELUP, 1f, 1.0f);
                }
                }
            }
            
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

        if (cfg.slayerTimer) {
            hud.register(new HudWidget("perseus-timer", "Slayer",
                () -> {
                    long pb = pbMs();
                    if (slayerStartAt > 0)
                        return "§c⏱ " + fmt(System.currentTimeMillis() - slayerStartAt)
                            + (pb > 0 ? " §7(pb " + fmt(pb) + ")" : "");
                    if (slayerLastMs > 0)
                        return "§7last " + fmt(slayerLastMs) + " §8| pb " + fmt(pb);
                    return null;
                },
                HudPosition.of(50, 70), cfg.slayerTimer));
        }
        if (cfg.bestiaryTracker) {
            hud.register(new HudWidget("perseus-bestiary", "Bestiary",
                () -> bestiaryKills > 0 ? "§a📖 Bestiary: " + bestiaryKills + " kills" : null,
                HudPosition.of(50, 62), cfg.bestiaryTracker));
        }
        if (cfg.bestiaryTracker) {
            hud.register(new HudWidget("perseus-bestiary-milestone", "BestiaryMS",
                () -> {
                    if (!ConstellationClient.loc().onHypixel()) return null;
                    for (String line : ConstellationClient.loc().getSidebarLines()) {
                        if (line.contains("Bestiary") && line.contains("Milestone")) return "§a📖 " + line.trim();
                    }
                    return null;
                },
                HudPosition.of(50, 46), cfg.bestiaryTracker));
        }
        if (cfg.rngMeterDetail) {
            hud.register(new HudWidget("perseus-rng-detail", "RNGDetail",
                () -> ConstellationClient.loc().onHypixel() ? rngLine() : null,
                HudPosition.of(50, 54), cfg.rngMeterDetail));
        }
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
        if (cfg.damageIndicatorHud) {
            hud.register(new HudWidget("perseus-damage", "DMG",
                () -> ConstellationClient.loc().onHypixel() ? "§c⚔ Damage HUD" : null,
                HudPosition.of(50, 38), cfg.damageIndicatorHud));
        }
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
