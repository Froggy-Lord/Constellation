package com.froggylord.constellation.constellation;

import com.froggylord.constellation.ConstellationClient;
import com.froggylord.constellation.config.HydraConfig;
import com.froggylord.constellation.core.BaseConstellation;
import com.froggylord.constellation.core.InitContext;
import com.froggylord.constellation.hud.HudManager;
import com.froggylord.constellation.hud.HudPosition;
import com.froggylord.constellation.hud.HudWidget;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

public class HydraFishing extends BaseConstellation {

    @Override public String id() { return "hydra"; }
    @Override public String displayName() { return "Hydra"; }
    @Override public String description() { return "fishing alerts + timers"; }

    private static long castAt = 0;
    private static int seaCreatures = 0;
    private static int seaCreatureCap = 20;
    private static long lastRareAt = 0;
    private static int tBronze = 0, tSilver = 0, tGold = 0, tDiamond = 0;
    private static long goldenFishAt = 0;
    private static long barnOpenAt = 0, barnCloseAt = 0;
    private static int sharkKills = 0;
    private static long totemPlacedAt = 0;

    
    private static final String[] RARE = {
        "Sea Emperor", "Water Hydra", "Lord Jawbus", "Thunder", "Plhlegblast",
        "Phantom Fisher", "Grim Reaper", "Reindrake", "Yeti", "Carrot King",
        "Great White Shark", "Abyssal Miner", "Titanoboa"
    };

    private HydraConfig cfg;

    @Override
    public void init(InitContext ctx) {
        cfg = (HydraConfig) getConfig();
        
        ConstellationClient.tick().every(4, "hydra-cast", () -> {
            var mc = Minecraft.getInstance();
            if (mc.player == null) return;
            var stack = mc.player.getMainHandItem();
            if (stack.getItem() instanceof net.minecraft.world.item.FishingRodItem
                && mc.options.keyAttack.isDown()) castAt = System.currentTimeMillis();
        });
        
        ConstellationClient.tick().every(2, "hydra-hooks", () -> {
            if (cfg == null || !cfg.hideOtherHooks) return;
            var mc = Minecraft.getInstance();
            if (mc.level == null || mc.player == null) return;
            for (var e : mc.level.entitiesForRendering()) {
                if (e instanceof net.minecraft.world.entity.projectile.FishingHook h
                    && h.getPlayerOwner() != mc.player) h.setInvisible(true);
            }
        });
        // odger waypoint — find the npc ...
        ConstellationClient.world().register(wctx -> {
            if (cfg == null || !cfg.odgerWaypoint || !ConstellationClient.loc().onHypixel()) return;
            var mc3 = Minecraft.getInstance();
            if (mc3.level == null || mc3.player == null) return;
            var pp2 = mc3.player.position();
            for (var e : mc3.level.entitiesForRendering()) {
                if (e.distanceToSqr(pp2) > 2500) continue; // 50-block scan
                var name = e.getCustomName();
                if (name != null && name.getString().contains("Odger")) {
                    wctx.highlight(e.getBoundingBox().inflate(0.5), 0x80FFAA00, true);
                    wctx.label(e.position().add(0, e.getBbHeight() + 0.5, 0), "Odger", 0xFFFFAA00, true);
                }
            }
        });

        
        if (cfg.wormholeLocator) {
            net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents.GAME.register((msg, overlay) -> {
                if (overlay || !ConstellationClient.loc().onHypixel()) return;
                String s = msg.getString();
                if (s.contains("Wormhole") || s.contains("wormhole")) {
                    var mc4 = Minecraft.getInstance();
                    if (mc4.player != null)
                        mc4.player.sendSystemMessage(Component.literal("§5🌀 Wormhole nearby! Check your surroundings."));
                }
            });
        }

        
        ConstellationClient.world().register(wctx -> {
            if (cfg == null || !cfg.lavaFishingHelper || !ConstellationClient.loc().onHypixel()) return;
            var mc4 = Minecraft.getInstance();
            if (mc4.level == null || mc4.player == null) return;
            var pp3 = mc4.player.blockPosition();
            for (int dx = -15; dx <= 15; dx++)
                for (int dz = -15; dz <= 15; dz++)
                    for (int dy = -3; dy <= 3; dy++) {
                        var bp = pp3.offset(dx, dy, dz);
                        var bs = mc4.level.getBlockState(bp);
                        if (bs.getBlock().getDescriptionId().contains("lava")) {
                            wctx.highlight(new net.minecraft.world.phys.AABB(bp), 0x20FF6600, true);
                        }
                    }
        });

        
        ConstellationClient.world().register(wctx -> {
            if (cfg == null || !cfg.chumHider || !ConstellationClient.loc().onHypixel()) return;
            var mc5 = Minecraft.getInstance();
            if (mc5.level == null) return;
            for (var e : mc5.level.entitiesForRendering()) {
                var name = e.getCustomName();
                if (name != null && (name.getString().contains("Chum") || name.getString().contains("chum")))
                    e.setInvisible(true);
            }
        });

        
        ConstellationClient.world().register(wctx -> {
            if (cfg == null || !cfg.thunderHighlight || !ConstellationClient.loc().onHypixel()) return;
            var mc = Minecraft.getInstance();
            if (mc.level == null) return;
            for (var e : mc.level.entitiesForRendering()) {
                var name = e.getCustomName();
                if (name != null && name.getString().contains("Thunder")) {
                    wctx.outline(e.getBoundingBox().inflate(0.3), 0xFFFFFF00, true);
                    wctx.label(e.position().add(0, e.getBbHeight() + 0.5, 0), "⚡ Thunder", 0xFFFFFF00, true);
                }
            }
        });

        net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents.GAME.register((msg, overlay) -> {
            if (overlay || !ConstellationClient.loc().onHypixel()) return;
            String s = msg.getString();
            if (s.contains("Sea Creature") || s.contains("sea creature")) seaCreatures++;
            if (cfg != null && cfg.rareSeaCreatureAlert) checkRare(s);
            
            if (cfg != null && cfg.sharkCounter && (s.contains("Shark") || s.contains("shark")) && (s.contains("killed") || s.contains("slain"))) {
                sharkKills++;
                com.froggylord.constellation.core.StatStore.add("hydra.shark.kills", 1);
            }
            
            if (cfg != null && cfg.totemTimer && s.contains("Totem") && (s.contains("placed") || s.contains("activated")))
                totemPlacedAt = System.currentTimeMillis();
            if (cfg != null && cfg.totemTimer && s.contains("Totem") && (s.contains("expired") || s.contains("wore off")))
                totemPlacedAt = 0;
            
            if (cfg != null && cfg.cocoonAlert && s.contains("Cocoon") && (s.contains("appeared") || s.contains("spawned"))) {
                var mc3 = Minecraft.getInstance();
                if (mc3.player != null) {
                    mc3.player.sendSystemMessage(Component.literal("§d🕸 Cocoon spawned nearby!"));
                    mc3.player.playSound(net.minecraft.sounds.SoundEvents.AMETHYST_BLOCK_CHIME, 0.8f, 1.0f);
                }
            }
            // golden fish spawn — verified p...
            if (cfg != null && cfg.goldenFishTimer && s.contains("Golden Fish") && (s.contains("appeared") || s.contains("spawned"))) {
                if (s.contains("appeared") || s.contains("spawned")) goldenFishAt = System.currentTimeMillis();
                else if (s.contains("caught") || s.contains("despawned")) goldenFishAt = 0;
            }
            
            if (cfg != null && cfg.barnTimer) {
                if (s.contains("barn") && (s.contains("open") || s.contains("doors"))) {
                    barnOpenAt = System.currentTimeMillis();
                    
                    var bm = java.util.regex.Pattern.compile("(\\d+)\\s*(?:minute|min|m)").matcher(s.toLowerCase(java.util.Locale.ROOT));
                    if (bm.find()) barnCloseAt = barnOpenAt + Long.parseLong(bm.group(1)) * 60_000L;
                }
                if (s.contains("barn") && (s.contains("close") || s.contains("shut"))) barnOpenAt = 0;
            }
            
            if (cfg != null && cfg.trophyFishTracker && (s.contains("TROPHY FISH") || s.contains("You caught"))) {
                if (s.contains("Diamond")) { tDiamond++; com.froggylord.constellation.core.StatStore.add("hydra.trophy.diamond", 1); }
                else if (s.contains("Gold")) { tGold++; com.froggylord.constellation.core.StatStore.add("hydra.trophy.gold", 1); }
                else if (s.contains("Silver")) { tSilver++; com.froggylord.constellation.core.StatStore.add("hydra.trophy.silver", 1); }
                else if (s.contains("Bronze")) { tBronze++; com.froggylord.constellation.core.StatStore.add("hydra.trophy.bronze", 1); }
            }
        });
    }

    private void checkRare(String s) {
        
        String low = s.toLowerCase(java.util.Locale.ROOT);
        if (low.contains("killed") || low.contains("slain") || low.contains("defeated") || low.contains("died")) return;
        for (String name : RARE) {
            if (!s.contains(name)) continue;
            long now = System.currentTimeMillis();
            if (now - lastRareAt < 4000) return; 
            lastRareAt = now;
            var mc = Minecraft.getInstance();
            if (mc.player != null) {
                mc.gui.hud.resetTitleTimes();
                mc.gui.hud.setTitle(Component.literal("§b🎣 " + name + "!"));
                mc.player.playSound(net.minecraft.sounds.SoundEvents.ENDER_DRAGON_GROWL, 0.6f, 1.4f);
                if (cfg.rareSeaCreaturePartyPing)
                    mc.player.connection.sendCommand("pc " + name + " spawned!");
            }
            return;
        }
    }

    @Override
    public void registerHud(HudManager hud) {
        cfg = (HydraConfig) getConfig();
        if (cfg == null) return;

        if (cfg.seaCreatureAlerts) {
            hud.register(new HudWidget("hydra-timer", "Cast",
                () -> {
                    if (!ConstellationClient.loc().onHypixel() || castAt == 0) return null;
                    long ms = System.currentTimeMillis() - castAt;
                    if (ms > 60_000) return null;
                    String sc = seaCreatures > 0 ? " §7SC: §f" + seaCreatures + "§7/" + seaCreatureCap : "";
                    return "§b🎣 " + (ms / 1000) + "s" + sc;
                },
                HudPosition.of(50, 86), cfg.seaCreatureAlerts));
        }
        if (cfg.trophyFishTracker) {
            hud.register(new HudWidget("hydra-trophy", "Trophy",
                () -> {
                    boolean life = ConstellationClient.cfg() != null && ConstellationClient.cfg().lifetimeStats;
                    long b = life ? com.froggylord.constellation.core.StatStore.getLong("hydra.trophy.bronze", tBronze) : tBronze;
                    long si = life ? com.froggylord.constellation.core.StatStore.getLong("hydra.trophy.silver", tSilver) : tSilver;
                    long g = life ? com.froggylord.constellation.core.StatStore.getLong("hydra.trophy.gold", tGold) : tGold;
                    long di = life ? com.froggylord.constellation.core.StatStore.getLong("hydra.trophy.diamond", tDiamond) : tDiamond;
                    if (b + si + g + di == 0) return null;
                    return "§c⬤" + b + " §7⬤" + si + " §6⬤" + g + " §b⬤" + di;
                },
                HudPosition.of(50, 94), cfg.trophyFishTracker));
        }
        if (cfg.goldenFishTimer) {
            hud.register(new HudWidget("hydra-golden", "Golden",
                () -> {
                    if (goldenFishAt == 0) return null;
                    long elapsed = System.currentTimeMillis() - goldenFishAt;
                    if (elapsed > 90_000) return null;
                    return "§6🐟 Golden " + (elapsed / 1000) + "s ago";
                },
                HudPosition.of(50, 102), cfg.goldenFishTimer));
        }
        if (cfg.barnTimer) {
            hud.register(new HudWidget("hydra-barn", "Barn",
                () -> {
                    if (barnOpenAt == 0) return null;
                    long left = barnCloseAt - System.currentTimeMillis();
                    if (left <= 0) return null;
                    long min = left / 60000, sec = (left % 60000) / 1000;
                    return "§c🏚 Barn " + min + ":" + String.format("%02d", sec);
                },
                HudPosition.of(50, 110), cfg.barnTimer));
        }
        if (cfg.sharkCounter) {
            hud.register(new HudWidget("hydra-shark", "Sharks",
                () -> sharkKills > 0 ? "§b🦈 " + sharkKills : null,
                HudPosition.of(50, 118), cfg.sharkCounter));
        }
        if (cfg.totemTimer) {
            hud.register(new HudWidget("hydra-totem", "Totem",
                () -> {
                    if (totemPlacedAt == 0) return null;
                    long elapsed = System.currentTimeMillis() - totemPlacedAt;
                    return "§5⏱ Totem " + (elapsed / 60000) + "m ago";
                },
                HudPosition.of(50, 126), cfg.totemTimer));
        }
        if (cfg.baitDisplay) {
            hud.register(new HudWidget("hydra-bait", "Bait",
                () -> {
                    if (!ConstellationClient.loc().onHypixel()) return null;
                    for (String line : ConstellationClient.loc().getSidebarLines()) {
                        if (line.contains("Bait") || line.contains("bait")) return "§6🪱 " + line.trim();
                    }
                    return null;
                },
                HudPosition.of(50, 134), cfg.baitDisplay));
        }
        if (cfg.fishingRodTimerHud) {
            hud.register(new HudWidget("hydra-rodtimer", "RodTimer",
                () -> {
                    if (castAt == 0 || !ConstellationClient.loc().onHypixel()) return null;
                    long secs = (System.currentTimeMillis() - castAt) / 1000;
                    if (secs > 60) return null;
                    String col = secs >= 20 ? "§6" : "§7";
                    return col + "🎣 " + secs + "s";
                },
                HudPosition.of(50, 142), cfg.fishingRodTimerHud));
        if (cfg.baitWarningsHud) {
            hud.register(new HudWidget("hydra-baitwarn", "BaitWarn",
                () -> {
                    if (!ConstellationClient.loc().onHypixel()) return null;
                    for (String line : ConstellationClient.loc().getSidebarLines()) {
                        if (line.contains("Bait") && (line.contains("low") || line.contains("0"))) return "§c⚠ " + line.trim();
                    }
                    return null;
                },
                HudPosition.of(50, 150), cfg.baitWarningsHud));
        if (cfg.hotspotRadarGuesser) {
            hud.register(new HudWidget("hydra-hotspot", "Hotspot",
                () -> {
                    if (!ConstellationClient.loc().onHypixel()) return null;
                    for (String line : ConstellationClient.loc().getSidebarLines()) {
                        if (line.contains("Hotspot") || line.contains("Lava Spot")) return "§c🔥 " + line.trim();
                    }
                    return null;
                },
                HudPosition.of(50, 158), cfg.hotspotRadarGuesser));
        if (cfg.chumBucketTimer) {
            hud.register(new HudWidget("hydra-chum", "Chum",
                () -> {
                    if (!ConstellationClient.loc().onHypixel()) return null;
                    for (String line : ConstellationClient.loc().getSidebarLines())
                        if (line.contains("Chum") || line.contains("Bucket")) return "§b🪣 " + line.trim();
                    return null;
                },
                HudPosition.of(50, 166), cfg.chumBucketTimer));
        }
        if (cfg.seaCreatureRarityDisplay) {
            hud.register(new HudWidget("hydra-scrarity", "SCRarity",
                () -> {
                    if (!ConstellationClient.loc().onHypixel()) return null;
                    for (String line : ConstellationClient.loc().getSidebarLines())
                        if (line.contains("Rarity") || line.contains("Tier")) return "§d🏷 " + line.trim();
                    return null;
                },
                HudPosition.of(50, 174), cfg.seaCreatureRarityDisplay));
        }
        }
        }
        }
    }
}
