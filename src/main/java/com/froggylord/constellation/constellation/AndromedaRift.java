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

public class AndromedaRift extends BaseConstellation {

    @Override public String id() { return "andromeda"; }
    @Override public String displayName() { return "Andromeda"; }
    @Override public String description() { return "rift hud"; }

    
    private static final Pattern RIFT_TIME = Pattern.compile("(?:Rift|⏣ )Time:?\\s*(\\d+):(\\d+)");
    private static final Pattern MOTES = Pattern.compile("Motes:?\\s*([\\d,]+)");

    private AndromedaConfig cfg;

    private static int enigmaSouls = 0;
    private static int effigies = 0;
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
            }
            if (cfg.effigyTracker && s.contains("Effigy") && (s.contains("found") || s.contains("collected"))) {
                effigies++;
                com.froggylord.constellation.core.StatStore.add("andromeda.effigies", 1);
                var mc = net.minecraft.client.Minecraft.getInstance();
                if (mc.player != null) {
                    mc.gui.hud.resetTitleTimes();
                    mc.gui.hud.setTitle(net.minecraft.network.chat.Component.literal("§5✦ Enigma Soul!"));
                    mc.player.playSound(net.minecraft.sounds.SoundEvents.AMETHYST_BLOCK_CHIME, 0.8f, 1.0f);
                }
            }
        });
        
        ConstellationClient.world().register(wctx -> {
            if (cfg == null || !cfg.blobbercystGlow || !inRift()) return;
            var mc2 = net.minecraft.client.Minecraft.getInstance();
            if (mc2.level == null || mc2.player == null) return;
            for (var e : mc2.level.entitiesForRendering()) {
                if (e.distanceToSqr(mc2.player.position()) > 400) continue;
                var name = e.getCustomName();
                if (name != null && name.getString().contains("Blobbercyst")) {
                    wctx.highlight(e.getBoundingBox().inflate(0.3), 0x80FF55FF, true);
                    wctx.label(e.position().add(0, e.getBbHeight() + 0.4, 0), "Blobbercyst", 0xFFFF55FF, true);
                }
            }
        });

        
        ConstellationClient.world().register(RiftWaypoints::draw);

        
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
        if (cfg.effigyTracker) {
            hud.register(new HudWidget("andromeda-effigy", "Effigies",
                () -> (inRift() && effigies > 0) ? "§4☠ " + effigies + " effigies" : null,
                HudPosition.of(2, 170), cfg.effigyTracker));
        }
        if (cfg.dreadfarmHelper) {
            hud.register(new HudWidget("andromeda-dreadfarm", "Dreadfarm",
                () -> inRift() ? "§4☠ Dreadfarm — kill blobs" : null,
                HudPosition.of(2, 180), cfg.dreadfarmHelper));
        }
        if (cfg.livingCaveHelper) {
            hud.register(new HudWidget("andromeda-cave", "LivingCave",
                () -> inRift() ? "§a🕷 Living Cave — spooders" : null,
                HudPosition.of(2, 190), cfg.livingCaveHelper));
        }
        if (cfg.mountainTopHelper) {
            hud.register(new HudWidget("andromeda-mountain", "MountainTop",
                () -> inRift() ? "§f🏔 Mountain Top" : null,
                HudPosition.of(2, 198), cfg.mountainTopHelper));
        }
        if (cfg.stillgoreHelper) {
            hud.register(new HudWidget("andromeda-stillgore", "Stillgore",
                () -> inRift() ? "§8🏰 Stillgore Chateau" : null,
                HudPosition.of(2, 206), cfg.stillgoreHelper));
        }
        if (cfg.colosseumHelper) {
            hud.register(new HudWidget("andromeda-colosseum", "Colosseum",
                () -> inRift() ? "§c🏟 Colosseum" : null,
                HudPosition.of(2, 214), cfg.colosseumHelper));
        }
        if (cfg.danceRoomHelper) {
            hud.register(new HudWidget("andromeda-dance", "DanceRoom",
                () -> inRift() ? "§d💃 Dance Room" : null,
                HudPosition.of(2, 222), cfg.danceRoomHelper));
        }
        if (cfg.westVillageHelper) {
            hud.register(new HudWidget("andromeda-westvillage", "WestVillage",
                () -> inRift() ? "§e🏘 West Village" : null,
                HudPosition.of(2, 230), cfg.westVillageHelper));
        }
        if (cfg.wyldWoodsHelper) {
            hud.register(new HudWidget("andromeda-wyld", "WyldWoods",
                () -> inRift() ? "§2🌳 Wyld Woods" : null,
                HudPosition.of(2, 238), cfg.wyldWoodsHelper));
        if (cfg.deadgehogCounter) {
            hud.register(new HudWidget("andromeda-deadgehog", "Deadgehog",
                () -> inRift() ? "§7🦔 Deadgehogs" : null,
                HudPosition.of(2, 246), cfg.deadgehogCounter));
        }
        if (cfg.shyFarmHelper) {
            hud.register(new HudWidget("andromeda-shyfarm", "ShyFarm",
                () -> inRift() ? "§a🌾 Shy Farm" : null,
                HudPosition.of(2, 254), cfg.shyFarmHelper));
        }
        if (cfg.cruxCounter) {
            hud.register(new HudWidget("andromeda-crux", "Crux",
                () -> inRift() ? "§d✦ Crux tracker" : null,
                HudPosition.of(2, 262), cfg.cruxCounter));
        }
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
