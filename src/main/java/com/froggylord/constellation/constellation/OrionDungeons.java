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

        // room detection every 4 ticks — inside it self-throttles to ~4x/sec and caches
        ConstellationClient.tick().every(4, "orion-room-match", () -> {
            if (ConstellationClient.loc().inDungeons()) {
                RoomMatch.update();
                com.froggylord.constellation.data.SkeletonScraper.tick();
                wasInDungeon = true;
            } else if (wasInDungeon) {
                // left the dungeon — reset map calibration so the next run re-anchors
                com.froggylord.constellation.data.MapSegments.reset();
                wasInDungeon = false;
            }
        });
    }

    @Override
    public void registerHud(HudManager hud) {
        if (cfg == null) return;
        Minecraft mc = Minecraft.getInstance();

        // all dungeon HUD widgets return null (= hidden) unless in an active dungeon run
        if (cfg.scoreHud) {
            hud.register(new HudWidget("orion-score", "Score",
                () -> inDungeon() ? "300 S+" : null,
                HudPosition.of(6, 54), cfg.scoreHud));
        }
        if (cfg.secretsHud) {
            hud.register(new HudWidget("orion-secrets", "Secrets",
                () -> inDungeon() ? "0/0" : null,
                HudPosition.of(6, 66), cfg.secretsHud));
        }
        if (cfg.cryptsHud) {
            hud.register(new HudWidget("orion-crypts", "Crypts",
                () -> inDungeon() ? "0" : null,
                HudPosition.of(6, 78), cfg.cryptsHud));
        }
        if (cfg.deathsHud) {
            hud.register(new HudWidget("orion-deaths", "Deaths",
                () -> inDungeon() ? "0" : null,
                HudPosition.of(6, 90), cfg.deathsHud));
        }
        if (cfg.timerHud) {
            hud.register(new HudWidget("orion-timer", "Timer",
                () -> inDungeon() ? "0:00" : null,
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
}
