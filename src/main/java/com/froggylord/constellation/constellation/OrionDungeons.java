package com.froggylord.constellation.constellation;

import com.froggylord.constellation.ConstellationClient;
import com.froggylord.constellation.config.OrionConfig;
import com.froggylord.constellation.core.BaseConstellation;
import com.froggylord.constellation.core.InitContext;
import com.froggylord.constellation.data.DungeonData;
import com.froggylord.constellation.data.RoomMatch;
import com.froggylord.constellation.hud.HudManager;
import com.froggylord.constellation.hud.HudPosition;
import com.froggylord.constellation.hud.HudWidget;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

public class OrionDungeons extends BaseConstellation {

    @Override public String id() { return "orion"; }
    @Override public String displayName() { return "Orion"; }
    @Override public String description() { return "dungeon stuff"; }

    private OrionConfig cfg;
    private boolean wasInDungeon = false;
    private int doorsOpened = 0;
    private static long fireFreezeMs = 0;
    private static long spiritBowUntil = 0;
    private static long saVanishUntil = 0;

    private static void rareRoomAlert(String room, String colour) {
        var mc = Minecraft.getInstance();
        if (mc.player != null) {
            mc.player.sendSystemMessage(Component.literal(colour + "✦ Rare room: " + room));
            mc.player.playSound(net.minecraft.sounds.SoundEvents.AMETHYST_BLOCK_CHIME, 0.7f, 1.3f);
        }
    }

    @Override
    public void init(InitContext ctx) {
        cfg = (OrionConfig) getConfig();
        if (cfg == null) return;

        
        DungeonData.load();

        
        OrionTerminals.init(cfg);
        
        OrionSpiritLeap.init(cfg);
        
        DoorHighlighter.init();
        // puzzle solvers — simon says, t...
        OrionPuzzles.init(cfg);
        
        ChestProfitCalc.init(cfg);
        // f5/m5 livid finder — hide wron...
        LividFinder.init();

        
        ConstellationClient.world().register(SecretWaypoints::draw);
        
        ConstellationClient.world().register(Routes::draw);
        
        ConstellationClient.world().register(CombatEsp::draw);
        // f3/m3 blaze puzzle — mark lowe...
        ConstellationClient.world().register(BlazeSolver::draw);
        
        ConstellationClient.world().register(DropEsp::draw);
        
        ConstellationClient.world().register(DoorHighlighter::draw);
        
        ConstellationClient.world().register(OrionPuzzles::drawBeams);
        
        ConstellationClient.world().register(M7Dragons::draw);
        
        ConstellationClient.world().register(GoldorWaypoints::draw);
        // water puzzle gate highlighter
        ConstellationClient.world().register(WaterPuzzleHelper::draw);
        
        ConstellationClient.world().register(IceFillHelper::draw);
        
        ConstellationClient.world().register(BoulderSolver::draw);
        // silverfish solver — highlight ...
        ConstellationClient.world().register(SilverfishSolver::draw);
        ConstellationClient.world().register(LightsOnSolver::draw);
        ConstellationClient.world().register(ArrowAlignSolver::draw);
        ConstellationClient.world().register(TargetPracticeSolver::draw);
        ConstellationClient.world().register(TeleportMazeSolver::draw);

        LividFinder.init();
        ConstellationClient.world().register(LividFinder::draw);

        
        // sees boss dialogue even if the...
        net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents.ALLOW_GAME.register((message, overlay) -> {
            if (!overlay && ConstellationClient.loc().inDungeons()) {
                String s = message.getString();
                com.froggylord.constellation.data.DungeonScore.onChat(s);
                com.froggylord.constellation.data.DefensiveTracker.onChat(s);
                if (cfg.blessingDisplay) OrionBlessings.onChat(s);
                
                if (cfg.rareRoomAlerts) {
                    String low = s.toLowerCase(java.util.Locale.ROOT);
                    if (low.contains("trinity")) rareRoomAlert("Trinity", "§d");
                    else if (low.contains("tomioka")) rareRoomAlert("Tomioka", "§b");
                    else if (low.contains("duncan")) rareRoomAlert("Duncan", "§6");
                    else if (low.contains("this room seems") && low.contains("empty")) rareRoomAlert("Empty Room", "§8");
                }
                
                if (cfg.mimicPartyPing && !cfg.streamerMode && (s.endsWith("Mimic dead!") || s.endsWith("Mimic Killed!"))) {
                    var mc = Minecraft.getInstance();
                    if (mc.player != null) mc.player.connection.sendCommand("pc Mimic dead!");
                }
                // spirit bow — pickup starts a 3...
                if (cfg.spiritBowTimer && s.contains("Spirit Bow") && s.contains("picked up"))
                    spiritBowUntil = System.currentTimeMillis() + 30_000;

                // fire freeze staff cooldown — c...
                if (cfg.fireFreezeTimer && s.contains("Fire Freeze")) {
                    if (s.contains("ready")) fireFreezeMs = 0;
                    else fireFreezeMs = System.currentTimeMillis() + 5700;
                }

                
                if (cfg.shadowAssassinAlert && s.contains("Shadow Assassin") && (s.contains("targeted") || s.contains("targeting"))) {
                    var mc = Minecraft.getInstance();
                    if (mc.player != null) {
                        mc.gui.hud.resetTitleTimes();
                        mc.gui.hud.setTitle(Component.literal("§5🗡 Shadow Assassin!"));
                        mc.player.playSound(net.minecraft.sounds.SoundEvents.WITHER_SPAWN, 0.5f, 0.8f);
                    }
                    
                    if (cfg.saVanishTimer) saVanishUntil = System.currentTimeMillis() + 20_000;
                }

                
                if (s.contains("Wither Key") || s.contains("Blood Key")) {
                    if (s.contains("picked up")) {
                        if (s.contains("Wither")) doorsOpened++;
                        var mc = Minecraft.getInstance();
                        if (mc.player != null) {
                            mc.gui.hud.resetTitleTimes();
                            mc.gui.hud.setTitle(Component.literal("§c🔑 " + s.trim()));
                        }
                    }
                }
                // wither door open — notify party
                if (s.contains("opened a Wither door") || s.contains("opened a Blood door")) {
                    doorsOpened++;
                    var mc = Minecraft.getInstance();
                    if (mc.player != null) {
                        mc.gui.hud.resetTitleTimes();
                        mc.gui.hud.setTitle(Component.literal("§5🚪 " + s.trim()));
                    }
                }
                
                if (s.contains("Recombobulator 3000") || s.contains("Giant's Sword")
                    || s.contains("Necron's Handle") || s.contains("Shadow Fury")
                    || s.contains("Wither Chestplate") || s.contains("Precursor Eye")
                    || s.contains("Dark Claymore") || s.contains("Shadow Assassin Chestplate")
                    || s.contains("Master Star") || s.contains("Diamond Head")) {
                    var mc = Minecraft.getInstance();
                    if (mc.player != null) {
                        mc.gui.hud.resetTitleTimes();
                        mc.gui.hud.setTitle(Component.literal("§6✨ RARE DROP: " + s.trim()));
                        mc.player.playSound(net.minecraft.sounds.SoundEvents.NOTE_BLOCK_PLING.value(), 1f, 0.8f);
                    }
                }
                
                if (s.contains("Trinity") || s.contains("Tomioka") || s.contains("Duncan")) {
                    var mc = Minecraft.getInstance();
                    if (mc.player != null) {
                        mc.gui.hud.resetTitleTimes();
                        mc.gui.hud.setTitle(Component.literal("§d" + s.trim()));
                        mc.player.playSound(net.minecraft.sounds.SoundEvents.NOTE_BLOCK_PLING.value(), 1f, 1.2f);
                    }
                }
            }
            return true;
        });

        // dungeon copilot — occasional c...
        ConstellationClient.tick().every(200, "orion-copilot", () -> {
            if (cfg == null || !cfg.dungeonCopilot || !ConstellationClient.loc().inDungeons()) return;
            var mc2 = Minecraft.getInstance();
            if (mc2.player == null) return;
            int s = com.froggylord.constellation.data.DungeonScore.score();
            String grade = com.froggylord.constellation.data.DungeonScore.grade();
            if (s >= 270) mc2.player.sendSystemMessage(Component.literal("§a✦ Copilot: Score is " + s + " (" + grade + ") — looking good!"));
            else if (s >= 230) mc2.player.sendSystemMessage(Component.literal("§e✦ Copilot: " + s + " — find more secrets for S+"));
            else mc2.player.sendSystemMessage(Component.literal("§c✦ Copilot: " + s + " — need secrets + crypts for higher score"));
        });

        
        ConstellationClient.tick().every(4, "orion-room-match", () -> {
            if (ConstellationClient.loc().inDungeons()) {
                RoomMatch.update();
                com.froggylord.constellation.data.SkeletonScraper.tick();
                com.froggylord.constellation.data.DefensiveTracker.tick();
                wasInDungeon = true;
            } else if (wasInDungeon) {
                
                doorsOpened = 0;
                com.froggylord.constellation.data.RunStats.finishRun();
                com.froggylord.constellation.data.MapSegments.reset();
                RoomMatch.resetCache();
                com.froggylord.constellation.data.DungeonScore.reset();
                com.froggylord.constellation.data.DefensiveTracker.reset();
                OrionBlessings.reset();
                wasInDungeon = false;
                
                if (cfg.autoRequeue && !cfg.requeueSafeMode) {
                    ConstellationClient.tick().once(cfg.requeueDelaySec * 20, "orion-requeue", () -> {
                        var mc = Minecraft.getInstance();
                        if (mc.player != null && !ConstellationClient.loc().inDungeons()) {
                            String floor = com.froggylord.constellation.data.DungeonScore.lastFloor();
                            mc.player.connection.sendCommand("joindungeon catacombs " + (floor != null ? floor : "F7"));
                        }
                    });
                }
            }
        });

        
        ConstellationClient.tick().every(20, "orion-score", () -> {
            if (ConstellationClient.loc().inDungeons()) com.froggylord.constellation.data.DungeonScore.update();
        });
    }

    @Override
    public void registerHud(HudManager hud) {
        if (cfg == null) return;
        Minecraft mc = Minecraft.getInstance();

        
        if (cfg.saVanishTimer) {
            hud.register(new HudWidget("orion-sa", "SA",
                () -> {
                    long left = saVanishUntil - System.currentTimeMillis();
                    if (left <= 0) return null;
                    return "§5🗡 Vanish " + (left / 1000) + "s";
                },
                HudPosition.of(6, 36), cfg.saVanishTimer));
        }
        if (cfg.spiritBowTimer) {
            hud.register(new HudWidget("orion-spiritbow", "SpiritBow",
                () -> {
                    long left = spiritBowUntil - System.currentTimeMillis();
                    if (left <= 0) return null;
                    return "§b🏹 Bow " + (left / 1000) + "s";
                },
                HudPosition.of(6, 38), cfg.spiritBowTimer));
        }
        if (cfg.fireFreezeTimer) {
            hud.register(new HudWidget("orion-freeze", "Freeze",
                () -> {
                    long left = fireFreezeMs - System.currentTimeMillis();
                    if (left <= 0) return null;
                    return "§b❄ " + String.format("%.1fs", left / 1000.0);
                },
                HudPosition.of(6, 40), cfg.fireFreezeTimer));
        }
        if (cfg.dungeonPotionsHud) {
            hud.register(new HudWidget("orion-dpotions", "DungeonPotions",
                () -> {
                    if (!ConstellationClient.loc().inDungeons()) return null;
                    if (mc.player == null) return null;
                    var effects = mc.player.getActiveEffects();
                    if (effects.isEmpty()) return null;
                    StringBuilder sb = new StringBuilder("§5⚗ ");
                    int shown = 0;
                    for (var e : effects) {
                        if (shown++ > 0) sb.append(" ");
                        int lvl = e.getAmplifier() + 1;
                        int dur = e.getDuration() / 20;
                        sb.append("§d").append(e.getEffect().value().getDisplayName().getString()).append(" §f").append(lvl).append(" §7").append(dur).append("s");
                        if (shown >= 2) break;
                    }
                    return sb.toString();
                },
                HudPosition.of(6, 32), cfg.dungeonPotionsHud));
        }
        if (cfg.blessingDisplay) {
            hud.register(new HudWidget("orion-blessings", "Blessings",
                () -> ConstellationClient.loc().inDungeons() ? OrionBlessings.display() : null,
                HudPosition.of(6, 46), cfg.blessingDisplay));
        }
        if (cfg.scoreHud) {
            hud.register(new HudWidget("orion-score", "Score",
                () -> !scoreReady() ? null
                    : com.froggylord.constellation.data.DungeonScore.score() + " " + com.froggylord.constellation.data.DungeonScore.grade(),
                HudPosition.of(6, 54), cfg.scoreHud));
        }
        if (cfg.secretsHud) {
            hud.register(new HudWidget("orion-secrets", "Secrets",
                () -> !scoreReady() ? null : com.froggylord.constellation.data.DungeonScore.secretPercent() + "%",
                HudPosition.of(6, 66), cfg.secretsHud));
        }
        if (cfg.cryptsHud) {
            hud.register(new HudWidget("orion-crypts", "Crypts",
                () -> !scoreReady() ? null : String.valueOf(com.froggylord.constellation.data.DungeonScore.crypts()),
                HudPosition.of(6, 78), cfg.cryptsHud));
        }
        if (cfg.deathsHud) {
            hud.register(new HudWidget("orion-deaths", "Deaths",
                () -> !scoreReady() ? null : String.valueOf(com.froggylord.constellation.data.DungeonScore.deaths()),
                HudPosition.of(6, 90), cfg.deathsHud));
        }
        if (cfg.timerHud) {
            hud.register(new HudWidget("orion-timer", "Timer",
                () -> !scoreReady() ? null : formatTime(com.froggylord.constellation.data.DungeonScore.timeSeconds()),
                HudPosition.of(6, 102), cfg.timerHud));
        }
        if (cfg.splitsHud) {
            hud.register(new HudWidget("orion-splits", "Splits",
                () -> {
                    if (!scoreReady()) return null;
                    var ds = com.froggylord.constellation.data.DungeonScore.class;
                    long blood = com.froggylord.constellation.data.DungeonScore.bloodSplitMs();
                    long boss = com.froggylord.constellation.data.DungeonScore.bossSplitMs();
                    if (blood == 0 && boss == 0) return null;
                    StringBuilder sb = new StringBuilder("§7Splits");
                    if (blood > 0) sb.append(" §cBlood ").append(formatTimeMs(blood));
                    if (boss > 0) sb.append(sb.length() > 0 ? " §7|" : "").append(" §4Boss ").append(formatTimeMs(boss));
                    return sb.toString();
                },
                HudPosition.of(6, 114), cfg.splitsHud));
        }
        if (cfg.roomNameHud) {
            hud.register(new HudWidget("orion-room", "Room",
                () -> {
                    if (!inDungeon()) return null;
                    return RoomMatch.currentRoom().isEmpty() ? "-" : RoomMatch.currentRoom();
                },
                HudPosition.of(6, 114), cfg.roomNameHud));
        }
        if (cfg.mimicIndicator) {
            hud.register(new HudWidget("orion-mimic", "Mimic",
                () -> {
                    if (!scoreReady() || !com.froggylord.constellation.data.DungeonScore.isMimicFloor()) return null;
                    return com.froggylord.constellation.data.DungeonScore.mimicKilled() ? "§adead" : "§calive";
                },
                HudPosition.of(6, 126), cfg.mimicIndicator));
            hud.register(new HudWidget("orion-doors", "Doors",
                () -> scoreReady() ? "§5Doors " + doorsOpened : null,
                HudPosition.of(6, 138), cfg.mimicIndicator));
        }
        if (cfg.perRoomCount) {
            hud.register(new HudWidget("orion-roomsecrets", "Room",
                () -> {
                    if (!inDungeon() || SecretWaypoints.totalCount() == 0) return null;
                    return SecretWaypoints.collectedCount() + "/" + SecretWaypoints.totalCount();
                },
                HudPosition.of(6, 138), cfg.perRoomCount));
        }
        if (cfg.abilityTracker) {
            hud.register(new HudWidget("orion-defensive", "Defensive",
                () -> inDungeon() ? com.froggylord.constellation.data.DefensiveTracker.hudLine() : null,
                HudPosition.of(6, 150), cfg.abilityTracker));
        }
        if (cfg.dungeonCopilot) {
            hud.register(new HudWidget("orion-copilot", "Copilot",
                () -> {
                    if (!scoreReady()) return null;
                    int c = com.froggylord.constellation.data.DungeonScore.crypts();
                    int pct = com.froggylord.constellation.data.DungeonScore.secretPercent();
                    int s = com.froggylord.constellation.data.DungeonScore.score();
                    int d = com.froggylord.constellation.data.DungeonScore.deaths();
                    if (d > 0) return "§c" + d + " death" + (d > 1 ? "s" : "") + " – careful!";
                    if (c < 5 && pct < 100) return "§eFind " + (5 - c) + " crypts & secrets";
                    if (pct < 70) return "§cNeed secrets: " + pct + "%";
                    if (s >= 300) return "§aS+ secured!";
                    if (s >= 270) return "§6S — " + (300 - s) + " more for S+";
                    if (c < 4) return "§e" + (5 - c) + " crypts missing";
                    return "§aOn track for S+";
                },
                HudPosition.of(6, 162), cfg.dungeonCopilot));
        }
        
        hud.register(new com.froggylord.constellation.hud.MapHudElement());
    }

    @Override
    public void registerCommands(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        
        dispatcher.register(LiteralArgumentBuilder.<FabricClientCommandSource>literal("roomdebug")
            .executes(ctx -> {
                var loc = ConstellationClient.loc();
                int roomCount = DungeonData.ROOMS.values().stream().mapToInt(java.util.Map::size).sum();
                String msg = "§e[Orion debug]§r\n"
                    + "§7data loaded:§r " + DungeonData.isLoaded() + " (" + roomCount + " rooms)\n"
                    + "§7onHypixel:§r " + loc.onHypixel() + "\n"
                    + "§7inDungeons:§r " + loc.inDungeons() + "\n"
                    + "§7area:§r " + loc.area() + "\n"
                    + "§7sidebar lines:§r " + loc.getSidebarLines().size() + "\n"
                    + "§7current room:§r '" + RoomMatch.currentRoom() + "'";
                var mc = Minecraft.getInstance();
                if (mc.player != null) mc.player.sendSystemMessage(Component.literal(msg));
                
                for (String line : loc.getSidebarLines()) {
                    if (mc.player != null) mc.player.sendSystemMessage(Component.literal("§8| §7" + line));
                }
                return 1;
            }));

        
        
        dispatcher.register(LiteralArgumentBuilder.<FabricClientCommandSource>literal("dndebug")
            .executes(ctx -> {
                var mc = Minecraft.getInstance();
                if (mc.player == null) return 0;
                mc.player.sendSystemMessage(Component.literal("§e=== Constellation Debug ==="));
                mc.player.sendSystemMessage(Component.literal("§7area: §f" + ConstellationClient.loc().area() + " §7inDungeon: §f" + ConstellationClient.loc().inDungeons()));
                mc.player.sendSystemMessage(Component.literal("§7room: §f" + com.froggylord.constellation.data.RoomMatch.currentRoom() + " §7matched: §f" + com.froggylord.constellation.data.RoomMatch.isMatched()));
                mc.player.sendSystemMessage(Component.literal("§7score: §f" + com.froggylord.constellation.data.DungeonScore.score() + " §7grade: §f" + com.froggylord.constellation.data.DungeonScore.grade()));
                mc.player.sendSystemMessage(Component.literal("§7HP: §f" + com.froggylord.constellation.core.ActionBar.health() + "/" + com.froggylord.constellation.core.ActionBar.maxHealth()));
                mc.player.sendSystemMessage(Component.literal("§7sidebar lines:"));
                for (String l : ConstellationClient.loc().getSidebarLines()) {
                    mc.player.sendSystemMessage(Component.literal("§8  " + l));
                }
                return 1;
            }));

        dispatcher.register(LiteralArgumentBuilder.<FabricClientCommandSource>literal("dungeonstats")
            .executes(ctx -> { com.froggylord.constellation.data.RunStats.printSession(); return 1; }));
        
        dispatcher.register(LiteralArgumentBuilder.<FabricClientCommandSource>literal("key")
            .executes(ctx -> {
                var mc = Minecraft.getInstance();
                if (mc.player != null) {
                    var stack = mc.player.getMainHandItem();
                    String name = stack.getHoverName().getString();
                    mc.player.sendSystemMessage(Component.literal("§eKey: " + name));
                }
                return 1;
            }));

        
        dispatcher.register(LiteralArgumentBuilder.<FabricClientCommandSource>literal("roomscan")
            .executes(ctx -> {
                String dbg = RoomMatch.debugScan();
                var mc = Minecraft.getInstance();
                if (mc.player != null) {
                    mc.player.sendSystemMessage(Component.literal("§e[Orion scan]§r " + dbg));
                    mc.player.sendSystemMessage(Component.literal("§7→ room: '" + RoomMatch.currentRoom() + "'"));
                }
                return 1;
            }));

        
        dispatcher.register(LiteralArgumentBuilder.<FabricClientCommandSource>literal("roomcapture")
            .then(com.mojang.brigadier.builder.RequiredArgumentBuilder.<FabricClientCommandSource, String>argument(
                    "name", com.mojang.brigadier.arguments.StringArgumentType.greedyString())
                .executes(ctx -> {
                    String name = com.mojang.brigadier.arguments.StringArgumentType.getString(ctx, "name");
                    String msg = com.froggylord.constellation.data.SkeletonScraper.capture(name);
                    var mc = Minecraft.getInstance();
                    if (mc.player != null) mc.player.sendSystemMessage(Component.literal("§e[scraper]§r " + msg));
                    return 1;
                })));

        
        dispatcher.register(LiteralArgumentBuilder.<FabricClientCommandSource>literal("map")
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("scale")
                .then(com.mojang.brigadier.builder.RequiredArgumentBuilder.<FabricClientCommandSource, Integer>argument(
                        "size", com.mojang.brigadier.arguments.IntegerArgumentType.integer(1, 5))
                    .executes(ctx -> {
                        int size = com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(ctx, "size");
                        cfg.mapScale = size;
                        ConstellationClient.saveConfig();
                        var mc = Minecraft.getInstance();
                        if (mc.player != null) mc.player.sendSystemMessage(Component.literal("§e[Orion]§r map scale → " + size));
                        return 1;
                    })))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("toggle")
                .executes(ctx -> {
                    cfg.dungeonMap = !cfg.dungeonMap;
                    ConstellationClient.saveConfig();
                    var mc = Minecraft.getInstance();
                    if (mc.player != null) mc.player.sendSystemMessage(Component.literal("§e[Orion]§r map " + (cfg.dungeonMap ? "on" : "off")));
                    return 1;
                })));
    }

    private static boolean inDungeon() {
        return ConstellationClient.loc().inDungeons();
    }

    
    private static boolean scoreReady() {
        return inDungeon() && com.froggylord.constellation.data.DungeonScore.isActive();
    }

    private static String formatTime(int secs) {
        return secs / 60 + ":" + String.format("%02d", secs % 60);
    }

    private static String formatTimeMs(long ms) {
        int s = (int) (ms / 1000);
        return s / 60 + ":" + String.format("%02d", s % 60);
    }
}
