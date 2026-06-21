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
    @Override public String description() { return "Dungeons — room detection, HUD, secrets, map, routes"; }

    private OrionConfig cfg;
    private boolean wasInDungeon = false;

    @Override
    public void init(InitContext ctx) {
        cfg = (OrionConfig) getConfig();
        if (cfg == null) return;

        // load dungeon data (room skeletons, secrets, routes)
        DungeonData.load();

        // secret waypoints — colour-coded boxes for the current room's secrets
        ConstellationClient.world().register(SecretWaypoints::draw);
        // secret routes — walk path + typed action markers (takes over waypoints in routed rooms)
        ConstellationClient.world().register(Routes::draw);
        // combat esp — starred mobs + secret bats (depth-tested)
        ConstellationClient.world().register(CombatEsp::draw);

        // read death/mimic/prince/watcher lines for the score. read-only (always allow) so it
        // sees boss dialogue even if the chat cleaner would later hide it.
        net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents.ALLOW_GAME.register((message, overlay) -> {
            if (!overlay && ConstellationClient.loc().inDungeons()) {
                String s = message.getString();
                com.froggylord.constellation.data.DungeonScore.onChat(s);
                com.froggylord.constellation.data.DefensiveTracker.onChat(s);
            }
            return true;
        });

        // room detection every 4 ticks — inside it self-throttles to ~4x/sec and caches
        ConstellationClient.tick().every(4, "orion-room-match", () -> {
            if (ConstellationClient.loc().inDungeons()) {
                RoomMatch.update();
                com.froggylord.constellation.data.SkeletonScraper.tick();
                com.froggylord.constellation.data.DefensiveTracker.tick();
                wasInDungeon = true;
            } else if (wasInDungeon) {
                // left the dungeon — print the run summary, then reset detection + score state
                com.froggylord.constellation.data.RunStats.finishRun();
                com.froggylord.constellation.data.MapSegments.reset();
                RoomMatch.resetCache();
                com.froggylord.constellation.data.DungeonScore.reset();
                com.froggylord.constellation.data.DefensiveTracker.reset();
                wasInDungeon = false;
            }
        });

        // recompute score ~1/sec from the sidebar + tab list
        ConstellationClient.tick().every(20, "orion-score", () -> {
            if (ConstellationClient.loc().inDungeons()) com.froggylord.constellation.data.DungeonScore.update();
        });
    }

    @Override
    public void registerHud(HudManager hud) {
        if (cfg == null) return;
        Minecraft mc = Minecraft.getInstance();

        // all dungeon HUD widgets return null (= hidden) unless in an active dungeon run
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
        // dungeon map — lives in the same registry/editor as everything else
        hud.register(new com.froggylord.constellation.hud.MapHudElement());
    }

    @Override
    public void registerCommands(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        // /roomdebug — dump detection state so we can see what's happening live
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
                // also dump sidebar lines
                for (String line : loc.getSidebarLines()) {
                    if (mc.player != null) mc.player.sendSystemMessage(Component.literal("§8| §7" + line));
                }
                return 1;
            }));

        // /dungeonstats — session run history
        dispatcher.register(LiteralArgumentBuilder.<FabricClientCommandSource>literal("dungeonstats")
            .executes(ctx -> { com.froggylord.constellation.data.RunStats.printSession(); return 1; }));

        // force a room scan right now with full diagnostics
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

        // /roomcapture <name> — record the current room's skeleton (for rooms missing from the DB)
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

        // /map scale <1-5> — adjust the dungeon map size
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

    // score values are only meaningful once the run has actually started (elapsed-time line up)
    private static boolean scoreReady() {
        return inDungeon() && com.froggylord.constellation.data.DungeonScore.isActive();
    }

    private static String formatTime(int secs) {
        return secs / 60 + ":" + String.format("%02d", secs % 60);
    }
}
