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

    @Override
    public void init(InitContext ctx) {
        cfg = (OrionConfig) getConfig();
        if (cfg == null) return;

        // load dungeon data (room skeletons, secrets, routes)
        DungeonData.load();

        // room detection every 20 ticks while in dungeons
        ConstellationClient.tick().every(20, "orion-room-match", () -> {
            if (ConstellationClient.loc().inDungeons()) {
                RoomMatch.update();
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

        // force a room scan right now (bypasses the inDungeons gate for testing)
        dispatcher.register(LiteralArgumentBuilder.<FabricClientCommandSource>literal("roomscan")
            .executes(ctx -> {
                RoomMatch.update();
                var mc = Minecraft.getInstance();
                if (mc.player != null) mc.player.sendSystemMessage(
                    Component.literal("§e[Orion]§r forced scan → room: '" + RoomMatch.currentRoom() + "'"));
                return 1;
            }));
    }

    private static boolean inDungeon() {
        return ConstellationClient.loc().inDungeons();
    }
}
