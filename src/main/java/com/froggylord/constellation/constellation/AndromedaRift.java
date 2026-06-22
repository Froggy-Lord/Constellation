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
    private static long motesSession = 0;
    private static final Pattern MOTES_GAIN = Pattern.compile("([\\d,.]+) Motes");
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
            // motes — rift currency, real line is "+<n> Motes" on pickup/sell
            if (cfg.moteProfitTracker) {
                var mm = MOTES_GAIN.matcher(net.minecraft.ChatFormatting.stripFormatting(s));
                if (mm.find() && s.contains("+")) {
                    long got = (long) Double.parseDouble(mm.group(1).replace(",", ""));
                    motesSession += got;
                    com.froggylord.constellation.core.StatStore.add("andromeda.motes", got);
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
                    long total = life ? com.froggylord.constellation.core.StatStore.getLong("andromeda.enigmaSouls", enigmaSouls) : enigmaSouls;
                    long session = enigmaSouls;
                    // skyhanni-style: show session count, total as secondary
                    String line = "§5✦ " + session + " this session";
                    if (life && total > session) line += " §8(" + total + " all-time)";
                    return line;
                },
                HudPosition.of(2, 160), cfg.enigmaSoulTracker));
        }
        if (cfg.effigyTracker) {
            hud.register(new HudWidget("andromeda-effigy", "Effigies",
                () -> {
                    if (!inRift()) return null;
                    boolean life = ConstellationClient.cfg() != null && ConstellationClient.cfg().lifetimeStats;
                    long t = life ? com.froggylord.constellation.core.StatStore.getLong("andromeda.effigies", effigies) : effigies;
                    return t > 0 ? "§4☠ " + effigies + " this session" + (life && t > effigies ? " §8(" + t + " all-time)" : "") : null;
                },
                HudPosition.of(2, 170), cfg.effigyTracker));
        }
        if (cfg.dreadfarmHelper) {
            hud.register(new HudWidget("andromeda-dreadfarm", "Dreadfarm",
                () -> {
                    if (!inRift()) return null;
                    // show blobercyst kills from sidebar if available, otherwise area hint
                    for (String line : ConstellationClient.loc().getSidebarLines()) {
                        if (line.contains("Blobbercyst") || line.contains("Dreadfarm")) return "§4☠ " + line.trim();
                    }
                    return "§4☠ Dreadfarm";
                },
                HudPosition.of(2, 180), cfg.dreadfarmHelper));
        }
        if (cfg.livingCaveHelper) {
            hud.register(new HudWidget("andromeda-cave", "LivingCave",
                () -> {
                    if (!inRift()) return null;
                    var mc2 = net.minecraft.client.Minecraft.getInstance();
                    if (mc2.level == null || mc2.player == null) return null;
                    int spiders = 0, blobs = 0;
                    for (var e : mc2.level.entitiesForRendering()) {
                        if (e.distanceToSqr(mc2.player.position()) > 400) continue;
                        String nm = e.getName().getString().toLowerCase();
                        if (nm.contains("spider") || nm.contains("cave")) spiders++;
                        else if (nm.contains("blobbercyst")) blobs++;
                    }
                    return "§a🕷 " + spiders + " spiders §8| §a🫧 " + blobs + " blobs";
                },
                HudPosition.of(2, 190), cfg.livingCaveHelper));
        }
        if (cfg.mountainTopHelper) {
            hud.register(new HudWidget("andromeda-mountain", "MountainTop",
                () -> {
                    if (!inRift()) return null;
                    for (String line : ConstellationClient.loc().getSidebarLines())
                        if (line.contains("Mountain") || line.contains("Top")) return "§f🏔 " + line.trim();
                    return "§f🏔 Mountain Top";
                },
                HudPosition.of(2, 198), cfg.mountainTopHelper));
        }
        if (cfg.stillgoreHelper) {
            hud.register(new HudWidget("andromeda-stillgore", "Stillgore",
                () -> {
                    if (!inRift()) return null;
                    for (String line : ConstellationClient.loc().getSidebarLines())
                        if (line.contains("Chateau") || line.contains("Stillgore")) return "§8🏰 " + line.trim();
                    return "§8🏰 Stillgore";
                },
                HudPosition.of(2, 206), cfg.stillgoreHelper));
        }
        if (cfg.colosseumHelper) {
            hud.register(new HudWidget("andromeda-colosseum", "Colosseum",
                () -> {
                    if (!inRift()) return null;
                    for (String line : ConstellationClient.loc().getSidebarLines())
                        if (line.contains("Colosseum") || line.contains("Round")) return "§c🏟 " + line.trim();
                    return "§c🏟 Colosseum";
                },
                HudPosition.of(2, 214), cfg.colosseumHelper));
        }
        if (cfg.danceRoomHelper) {
            hud.register(new HudWidget("andromeda-dance", "DanceRoom",
                () -> {
                    if (!inRift()) return null;
                    for (String line : ConstellationClient.loc().getSidebarLines())
                        if (line.contains("Dance") || line.contains("Move")) return "§d💃 " + line.trim();
                    return "§d💃 Dance Room";
                },
                HudPosition.of(2, 222), cfg.danceRoomHelper));
        }
        if (cfg.westVillageHelper) {
            hud.register(new HudWidget("andromeda-westvillage", "WestVillage",
                () -> {
                    if (!inRift()) return null;
                    for (String line : ConstellationClient.loc().getSidebarLines())
                        if (line.contains("Village") || line.contains("West")) return "§e🏘 " + line.trim();
                    return "§e🏘 West Village";
                },
                HudPosition.of(2, 230), cfg.westVillageHelper));
        }
        if (cfg.wyldWoodsHelper) {
            hud.register(new HudWidget("andromeda-wyld", "WyldWoods",
                () -> {
                    if (!inRift()) return null;
                    for (String line : ConstellationClient.loc().getSidebarLines())
                        if (line.contains("Wyld") || line.contains("Woods")) return "§2🌳 " + line.trim();
                    return "§2🌳 Wyld Woods";
                },
                HudPosition.of(2, 238), cfg.wyldWoodsHelper));
        }
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
        if (cfg.bluetoothRingHelper) {
            hud.register(new HudWidget("andromeda-bluetooth", "Bluetooth",
                () -> {
                    if (!inRift()) return null;
                    for (String line : ConstellationClient.loc().getSidebarLines())
                        if (line.contains("Bluetooth") || line.contains("Ring")) return "§9💍 " + line.trim();
                    return "§9💍 Bluetooth Ring";
                },
                HudPosition.of(2, 270), cfg.bluetoothRingHelper));
        }
        if (cfg.vampireSlayerRiftHelper) {
            hud.register(new HudWidget("andromeda-vampslayer", "VampSlayer",
                () -> {
                    if (!inRift()) return null;
                    for (String line : ConstellationClient.loc().getSidebarLines())
                        if (line.contains("Vampire") || line.contains("Slayer")) return "§4🧛 " + line.trim();
                    return "§4🧛 Vampire Slayer";
                },
                HudPosition.of(2, 278), cfg.vampireSlayerRiftHelper));
        if (cfg.wyldWoodsSoulHelper) {
            hud.register(new HudWidget("andromeda-soulswyld", "WyldSouls",
                () -> {
                    if (!inRift()) return null;
                    for (String line : ConstellationClient.loc().getSidebarLines())
                        if (line.contains("Soul") && line.contains("Wyld")) return "§a🌳 " + line.trim();
                    return null;
                },
                HudPosition.of(2, 286), cfg.wyldWoodsSoulHelper));
        }
        if (cfg.dreadfarmEnigmaHelper) {
            hud.register(new HudWidget("andromeda-dreadenigma", "DreadEnigma",
                () -> {
                    if (!inRift()) return null;
                    for (String line : ConstellationClient.loc().getSidebarLines())
                        if (line.contains("Enigma") && line.contains("Dread")) return "§4☠ " + line.trim();
                    return null;
                },
                HudPosition.of(2, 294), cfg.dreadfarmEnigmaHelper));
        }
        }
        }
    }

    private static boolean inRift() {
        return ConstellationClient.loc().area() == SkyblockArea.THE_RIFT;
    }

    private static int maxRiftSecs = 0;

    private static String timeLine() {
        for (String line : ConstellationClient.loc().getSidebarLines()) {
            Matcher m = RIFT_TIME.matcher(line);
            if (m.find()) {
                int mins = Integer.parseInt(m.group(1));
                int secs = Integer.parseInt(m.group(2));
                int total = mins * 60 + secs;
                if (total > maxRiftSecs) maxRiftSecs = total;
                // colour by urgency — red < 60s, yellow < 5min, cyan otherwise (like skyhanni)
                String col = total < 60 ? "§c" : total < 300 ? "§e" : "§b";
                String pct = maxRiftSecs > 0 ? " §7(" + (total * 100 / maxRiftSecs) + "%)" : "";
                return col + String.format("%d:%02d", mins, secs) + pct;
            }
        }
        return null;
    }

    private static String motesLine() {
        for (String line : ConstellationClient.loc().getSidebarLines()) {
            Matcher m = MOTES.matcher(line);
            if (m.find()) {
                String bal = "§b" + compact(m.group(1));
                return motesSession > 0 ? bal + " §7(+" + compact(Long.toString(motesSession)) + ")" : bal;
            }
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
