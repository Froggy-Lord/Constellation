package com.froggylord.constellation.constellation;

import com.froggylord.constellation.ConstellationClient;
import com.froggylord.constellation.chat.ChatPipeline;
import com.froggylord.constellation.config.CassiopeiaConfig;
import com.froggylord.constellation.core.BaseConstellation;
import com.froggylord.constellation.core.InitContext;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

public class CassiopeiaChat extends BaseConstellation {

    @Override public String id() { return "cassiopeia"; }
    @Override public String displayName() { return "Cassiopeia"; }
    @Override public String description() { return "Chat filters, timestamps, shortcuts, party triggers"; }

    private CassiopeiaConfig cfg;
    private final ChatPipeline pipeline = new ChatPipeline();

    // party trigger state
    private final Map<String, Long> triggerLastUsed = new HashMap<>();
    private static final List<String> KNOWN_PLAYERS = new ArrayList<>();

    @Override
    public void init(InitContext ctx) {
        cfg = (CassiopeiaConfig) getConfig();
        if (cfg == null) return;

        pipeline.init();

        // ---- ALLOW_GAME: cleaner (runs EARLIEST so it sees messages before anything else) ----
        pipeline.allow(msg -> {
            if (!cfg.cleanBlocksInWay && !cfg.cleanNotEnoughMana && !cfg.cleanCantTeleport) return true;
            String s = msg.getString();
            if (cfg.cleanBlocksInWay && s.contains("blocks in the way")) return false;
            if (cfg.cleanNotEnoughMana && s.contains("don't have enough mana")) return false;
            if (cfg.cleanCantTeleport && s.contains("Can't teleport")) return false;
            return true;
        }, ChatPipeline.Priority.EARLIEST);

        // ---- ALLOW_GAME: boss dialogue, blessings, milestones etc ----
        pipeline.allow(msg -> {
            String s = msg.getString().toLowerCase(Locale.ROOT);
            if (cfg.cleanBossDialogue && s.startsWith("[boss]")) return false;
            if (cfg.cleanBlessings && s.contains("blessing of")) return false;
            if (cfg.cleanMilestones && s.contains("milestone")) return false;
            if (cfg.cleanSalvage && s.contains("salvage")) return false;
            if (cfg.cleanKillCombo && s.contains("kill combo")) return false;
            if (cfg.cleanAbilityReady && s.contains("is now ready")) return false;
            if (cfg.cleanIncomingDamage && s.contains("damage!")) return false;
            if (cfg.cleanDividers && s.trim().matches("^-{3,}$")) return false;
            if (cfg.cleanStashNag && s.contains("stash")) return false;
            if (cfg.cleanTrapTrips && s.contains("trap")) return false;
            if (cfg.cleanTeleportFlavour && s.contains("teleport") && s.contains("room")) return false;
            if (cfg.cleanDungeonBuff && s.contains("dungeon buff")) return false;
            if (cfg.cleanWitherDoor && s.contains("wither door")) return false;

            // custom spam filter list
            for (String filter : cfg.spamFilters) {
                if (s.contains(filter.toLowerCase(Locale.ROOT))) return false;
            }
            return true;
        });

        // ---- GAME: mention alerts ----
        if (cfg.mentionAlert) {
            pipeline.onGame(msg -> {
                String s = msg.getString().toLowerCase(Locale.ROOT);
                var mc = Minecraft.getInstance();
                if (mc.player == null) return;
                String name = mc.player.getName().getString().toLowerCase(Locale.ROOT);
                if (s.contains(name)) {
                    mc.player.sendOverlayMessage(Component.literal("§e✦ Mentioned in chat"));
                    mc.player.playSound(net.minecraft.sounds.SoundEvents.BELL_BLOCK, 0.6f, 1.2f);
                }
            });
        }

        // ---- MODIFY_GAME: timestamps ----
        if (cfg.timestamps) {
            var fmt = DateTimeFormatter.ofPattern("HH:mm");
            pipeline.modify(msg -> {
                String time = "§7[" + LocalTime.now().format(fmt) + "]§r ";
                return Component.literal(time).append(msg);
            });
        }

        // ---- MODIFY_GAME: clickable links ----
        if (cfg.clickableLinks) {
            pipeline.modify(msg -> {
                String s = msg.getString();
                // only touch messages that actually have a url — leave the rest alone
                if (!s.contains("http://") && !s.contains("https://")) return msg;
                s = s.replaceAll("(https?://[^\\s]+)", "§9§n$1§r");
                return Component.literal(s);
            });
        }

        // ---- GAME: mention alert ----
        if (cfg.mentionAlert) {
            pipeline.onGame(msg -> {
                var mc = Minecraft.getInstance();
                if (mc.player == null) return;
                String myName = mc.player.getName().getString();
                if (myName.isEmpty()) return;
                if (msg.getString().toLowerCase(Locale.ROOT).contains(myName.toLowerCase(Locale.ROOT)))
                    mc.player.playSound(net.minecraft.sounds.SoundEvents.NOTE_BLOCK_PLING.value(), 1f, 1.8f);
            });
        }

        // ---- GAME: party triggers ----
        if (cfg.partyTriggers) {
            pipeline.onGame(msg -> handlePartyTrigger(msg));
        }

        // short commands handled in registerCommands
    }

    private void handlePartyTrigger(Component msg) {
        String s = msg.getString().trim();
        if (!s.startsWith("!")) return;

        var mc = Minecraft.getInstance();
        if (mc.player == null) return;

        // rate limit
        String key = s.split(" ")[0].toLowerCase(Locale.ROOT);
        long now = System.currentTimeMillis();
        if (triggerLastUsed.containsKey(key)) {
            if (now - triggerLastUsed.get(key) < cfg.triggerCooldownSec * 1000L) return;
        }
        triggerLastUsed.put(key, now);

        String cmd = null;
        if (s.equals("!warp")) cmd = "/p warp";
        else if (s.equals("!join")) cmd = "/p accept";
        else if (s.startsWith("!invite ")) cmd = "/p invite " + s.substring(8).trim();
        else if (s.equals("!leave") || s.equals("!dc")) cmd = "/p leave";
        else if (s.startsWith("!ptme ")) cmd = "/p transfer " + s.substring(6).trim();
        else if (s.startsWith("!dt")) cmd = "/pc Downtime — back soon";
        else if (s.equals("!cata") || s.equals("!sb")) cmd = "/pc Cata level?";
        else if (s.equals("!rules")) cmd = "/pc No dupes, no grief, don't be a dick";
        else if (s.equals("!8ball")) {
            String[] answers = {"Yes", "No", "Maybe", "Ask again later", "Definitely", "Doubtful"};
            cmd = "/pc 8ball: " + answers[new Random().nextInt(answers.length)];
        } else if (s.startsWith("!dice ")) {
            try {
                int max = Integer.parseInt(s.substring(6).trim());
                cmd = "/pc rolled " + (new Random().nextInt(max) + 1) + " (1-" + max + ")";
            } catch (Exception e) {}
        } else if (s.equals("!fc")) cmd = "/pc Floor clear — ready?";

        if (cmd != null && !cmd.isEmpty()) {
            final String finalCmd = cmd;
            mc.execute(() -> mc.player.connection.sendCommand(finalCmd));
        }
    }

    @Override
    public void registerCommands(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        if (!cfg.floorShortcuts && !cfg.warpShortcuts && !cfg.partyShortcuts) return;

        // floor shortcuts: /f1-/f7, /m1-/m7, /e, /r
        if (cfg.floorShortcuts) {
            // hypixel's dungeon-join instance ids use word floor numbers
            for (int i = 0; i < 7; i++) {
                final int idx = i;
                dispatcher.register(LiteralArgumentBuilder.<FabricClientCommandSource>literal("f" + (i + 1))
                    .executes(ctx -> { joinFloor(NORMAL_FLOORS[idx]); return 1; }));
                dispatcher.register(LiteralArgumentBuilder.<FabricClientCommandSource>literal("m" + (i + 1))
                    .executes(ctx -> { joinFloor(MASTER_FLOORS[idx]); return 1; }));
            }
            dispatcher.register(LiteralArgumentBuilder.<FabricClientCommandSource>literal("e")
                .executes(ctx -> { joinFloor("CATACOMBS_ENTRANCE"); return 1; }));
            dispatcher.register(LiteralArgumentBuilder.<FabricClientCommandSource>literal("r")
                .executes(ctx -> {
                    if (lastFloorInstance != null) sendCmd("joininstance " + lastFloorInstance);
                    return 1;
                }));
        }

        // warp shortcuts
        if (cfg.warpShortcuts) {
            dispatcher.register(LiteralArgumentBuilder.<FabricClientCommandSource>literal("h")
                .executes(ctx -> { sendCmd("warp hub"); return 1; }));
            dispatcher.register(LiteralArgumentBuilder.<FabricClientCommandSource>literal("i")
                .executes(ctx -> { sendCmd("warp island"); return 1; }));
            dispatcher.register(LiteralArgumentBuilder.<FabricClientCommandSource>literal("dh")
                .executes(ctx -> { sendCmd("warp dungeon_hub"); return 1; }));
            dispatcher.register(LiteralArgumentBuilder.<FabricClientCommandSource>literal("l")
                .executes(ctx -> { sendCmd("lobby"); return 1; }));
            dispatcher.register(LiteralArgumentBuilder.<FabricClientCommandSource>literal("w")
                .executes(ctx -> { sendCmd("warp"); return 1; }));
        }

        // party shortcuts
        if (cfg.partyShortcuts) {
            dispatcher.register(LiteralArgumentBuilder.<FabricClientCommandSource>literal("pi")
                .executes(ctx -> { sendCmd("party"); return 1; }));
            dispatcher.register(LiteralArgumentBuilder.<FabricClientCommandSource>literal("pw")
                .executes(ctx -> { sendCmd("p warp"); return 1; }));
            dispatcher.register(LiteralArgumentBuilder.<FabricClientCommandSource>literal("pl")
                .executes(ctx -> { sendCmd("p list"); return 1; }));
            dispatcher.register(LiteralArgumentBuilder.<FabricClientCommandSource>literal("pk")
                .executes(ctx -> { sendCmd("p kick"); return 1; }));
            dispatcher.register(LiteralArgumentBuilder.<FabricClientCommandSource>literal("pt")
                .executes(ctx -> { sendCmd("p transfer"); return 1; }));
            dispatcher.register(LiteralArgumentBuilder.<FabricClientCommandSource>literal("pd")
                .executes(ctx -> { sendCmd("p disband"); return 1; }));
            dispatcher.register(LiteralArgumentBuilder.<FabricClientCommandSource>literal("pe")
                .executes(ctx -> { sendCmd("p promote"); return 1; }));
            dispatcher.register(LiteralArgumentBuilder.<FabricClientCommandSource>literal("pko")
                .executes(ctx -> { sendCmd("p kickoffline"); return 1; }));
            dispatcher.register(LiteralArgumentBuilder.<FabricClientCommandSource>literal("pa")
                .executes(ctx -> { sendCmd("p accept"); return 1; }));
            dispatcher.register(LiteralArgumentBuilder.<FabricClientCommandSource>literal("pm")
                .executes(ctx -> { sendCmd("p mute"); return 1; }));
            dispatcher.register(LiteralArgumentBuilder.<FabricClientCommandSource>literal("ps")
                .executes(ctx -> { sendCmd("p settings"); return 1; }));
        }
    }

    // hypixel dungeon-join instance ids (word floor numbers)
    private static final String[] NORMAL_FLOORS = {
        "CATACOMBS_FLOOR_ONE","CATACOMBS_FLOOR_TWO","CATACOMBS_FLOOR_THREE","CATACOMBS_FLOOR_FOUR",
        "CATACOMBS_FLOOR_FIVE","CATACOMBS_FLOOR_SIX","CATACOMBS_FLOOR_SEVEN"
    };
    private static final String[] MASTER_FLOORS = {
        "MASTER_CATACOMBS_FLOOR_ONE","MASTER_CATACOMBS_FLOOR_TWO","MASTER_CATACOMBS_FLOOR_THREE",
        "MASTER_CATACOMBS_FLOOR_FOUR","MASTER_CATACOMBS_FLOOR_FIVE","MASTER_CATACOMBS_FLOOR_SIX",
        "MASTER_CATACOMBS_FLOOR_SEVEN"
    };
    private static String lastFloorInstance = null;

    private static void joinFloor(String instance) {
        lastFloorInstance = instance;
        sendCmd("joininstance " + instance);
    }

    private static void sendCmd(String cmd) {
        var mc = Minecraft.getInstance();
        if (mc.player != null) {
            mc.player.connection.sendCommand(cmd);
        }
    }
}
