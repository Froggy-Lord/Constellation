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

        // ---- ALLOW_GAME: action bar cleaner (SkyblockTweaks-style) ----
        if (cfg.actionBarCleaner) {
            net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents.ALLOW_GAME.register((msg, overlay) -> {
                if (!overlay || !cfg.actionBarCleaner) return true;
                String s = msg.getString();
                // strip the verbose action bar spam
                if (s.contains("❤") && s.length() > 30) return false; // strip health/defense display
                return true;
            });
        }

        // ---- ALLOW_GAME: cleaner (runs EARLIEST so it sees messages before anything else) ----
        pipeline.allow(msg -> {
            if (!cfg.cleanBlocksInWay && !cfg.cleanNotEnoughMana && !cfg.cleanCantTeleport) return true;
            String s = msg.getString();
            if (cfg.cleanBlocksInWay && s.contains("blocks in the way")) return false;
            if (cfg.cleanNotEnoughMana && s.contains("don't have enough mana")) return false;
            if (cfg.cleanCantTeleport && s.contains("Can't teleport")) return false;
            return true;
        }, ChatPipeline.Priority.EARLIEST);

        // ---- ALLOW_GAME: kill the useless "Warps" message ----
        pipeline.allow(msg -> {
            String s = msg.getString();
            if (s.contains("●") && (s.contains("/warp") || s.contains("/hub"))) return false;
            return true;
        }, ChatPipeline.Priority.LATEST);

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
            if (cfg.cleanEmpty && s.trim().isEmpty()) return false;
            if (cfg.cleanWarping && (s.contains("warping") || s.contains("sending to"))) return false;
            if (cfg.cleanWelcome && s.contains("welcome to hypixel")) return false;
            if (cfg.cleanGuildExp && s.contains("guild") && s.contains("exp")) return false;
            if (cfg.cleanFriendJoin && (s.contains("joined") || s.contains("left")) && !s.contains("party")) return false;
            if (cfg.cleanWinterGift && s.contains("gift") && s.contains("ice")) return false;
            if (cfg.cleanWatchdog && s.contains("watchdog")) return false;
            if (cfg.cleanProfileJoin && s.contains("profile") && s.contains("loading")) return false;
            if (cfg.cleanFireSale && s.contains("fire sale")) return false;
            if (cfg.cleanDiana && s.contains("diana") && s.contains("burrow")) return false;
            if (cfg.cleanHoppity && (s.contains("hoppity") || s.contains("chocolate egg"))) return false;
            if (cfg.cleanSacrifice && s.contains("sacrifice")) return false;
            if (cfg.cleanParkour && (s.contains("parkour") || s.contains("checkpoint"))) return false;
            if (cfg.cleanTeleportPads && s.contains("teleport pad")) return false;
            if (cfg.cleanAds && (s.contains("buy") || s.contains("sell") || s.contains("check my ah"))) return false;
            if (cfg.cleanShowOff && (s.contains("holding") || s.contains("wearing"))) return false;
            if (cfg.cleanAutopet && s.contains("autopet")) return false;
            if (cfg.cleanCombo && s.contains("kill combo")) return false;
            if (cfg.cleanMimic && s.contains("mimic")) return false;
            if (cfg.cleanDeath && s.contains("☠")) return false;
            if (cfg.cleanHeal && (s.contains("healed") || s.contains("healed you"))) return false;
            if (cfg.cleanAOTE && s.contains("blocks in the way")) return false;
            if (cfg.cleanImplosion && s.contains("implosion")) return false;
            if (cfg.cleanAbilityCooldown && (s.contains("cooldown") || s.contains("no more charges"))) return false;

            // custom spam filter list
            for (String filter : cfg.spamFilters) {
                if (s.contains(filter.toLowerCase(Locale.ROOT))) return false;
            }
            return true;
        });

        // ---- MODIFY_GAME: compact potion messages ----
        if (cfg.compactPotionMessages) {
            pipeline.modify(msg -> {
                String s = msg.getString();
                if (s.contains("Potion effects") || s.contains("Your active")) return null; // null = hide
                return msg;
            });
        }

        // ---- MODIFY_GAME: shorten coin amounts in chat ----
        if (cfg.shortenCoins) {
            // compact 1,234,567 → 1.2M in chat using Matcher.replaceAll
            var COIN = java.util.regex.Pattern.compile("[\\d,]{4,}");
            pipeline.modify(msg -> {
                String s = msg.getString();
                if (s.length() < 10) return msg;
                return net.minecraft.network.chat.Component.literal(
                    COIN.matcher(s).replaceAll(mr -> {
                        try {
                            long n = Long.parseLong(mr.group().replace(",", ""));
                            if (n < 10000) return mr.group();
                            if (n < 1_000_000) return String.format("%.1fk", n / 1000.0);
                            return String.format("%.2fM", n / 1_000_000.0);
                        } catch (Exception e) { return mr.group(); }
                    })
                );
            });
        }

        // ---- MODIFY_GAME: compact bestiary messages ----
        if (cfg.compactBestiary) {
            pipeline.modify(msg -> {
                String s = msg.getString();
                // just hide the verbose bestiary/magic-find lines entirely
                if (s.contains("Bestiary") && (s.contains("+") || s.contains("%"))) return null;
                return msg;
            });
        }

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

        // ---- GAME: AutoGG (send "gg" on dungeon/kuudra end) ----
        if (cfg.autoGG) {
            pipeline.onGame(msg -> {
                String s = msg.getString();
                if (s.contains("Dungeon") && s.contains("complete") || s.contains("Kuudra") && s.contains("defeated")) {
                    var mc = Minecraft.getInstance();
                    if (mc.player != null) mc.player.connection.sendCommand("pc gg");
                }
            });
        }

        // ---- GAME: full inventory warning (SBA feature) ----
        pipeline.onGame(msg -> {
            String s = msg.getString();
            if (s.contains("Your inventory is full") || s.contains("cannot fit") || s.contains("inventory full")) {
                var mc = Minecraft.getInstance();
                if (mc.player != null) {
                    mc.player.sendSystemMessage(Component.literal("§c⚠ INVENTORY FULL!"));
                    mc.player.playSound(net.minecraft.sounds.SoundEvents.NOTE_BLOCK_BASS.value(), 1f, 0.5f);
                }
            }
        });

        // ---- GAME: legendary sea creature alert (SBA feature) ----
        pipeline.onGame(msg -> {
            String s = msg.getString();
            if (s.contains("A legendary Sea Creature has spawned")) {
                var mc = Minecraft.getInstance();
                if (mc.player != null) {
                    mc.gui.hud.resetTitleTimes();
                    mc.gui.hud.setTitle(Component.literal("§b🐟 LEGENDARY SEA CREATURE!"));
                    mc.player.playSound(net.minecraft.sounds.SoundEvents.WITHER_SPAWN, 0.5f, 0.5f);
                }
            }
        });

        // ---- GAME: party triggers ----
        if (cfg.partyTriggers) {
            pipeline.onGame(msg -> handlePartyTrigger(msg));
        }

        // AutoTip — send /tip all every 30 minutes on Hypixel
        ConstellationClient.tick().every(20 * 60 * 30, "cassiopeia-autotip", () -> {
            var mc = Minecraft.getInstance();
            if (mc.player != null && ConstellationClient.loc().onHypixel())
                mc.player.connection.sendCommand("tip all");
        });

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

        // /sbmenu — SkyBlock menu
        dispatcher.register(LiteralArgumentBuilder.<FabricClientCommandSource>literal("sbmenu")
            .executes(ctx -> { sendCmd("sbmenu"); return 1; }));
        dispatcher.register(LiteralArgumentBuilder.<FabricClientCommandSource>literal("craft")
            .executes(ctx -> { sendCmd("craft"); return 1; }));
        dispatcher.register(LiteralArgumentBuilder.<FabricClientCommandSource>literal("skills")
            .executes(ctx -> { sendCmd("skills"); return 1; }));
        dispatcher.register(LiteralArgumentBuilder.<FabricClientCommandSource>literal("roll")
            .executes(ctx -> {
                int r = new java.util.Random().nextInt(6) + 1;
                sendCmd("pc rolled a " + r);
                return 1;
            }));
        dispatcher.register(LiteralArgumentBuilder.<FabricClientCommandSource>literal("ping")
            .executes(ctx -> {
                var mc = Minecraft.getInstance();
                if (mc.player != null) {
                    var server = mc.getCurrentServer();
                    mc.player.sendSystemMessage(Component.literal("§a⏱ " + (server != null ? server.ping + "ms" : "N/A")));
                }
                return 1;
            }));
        dispatcher.register(LiteralArgumentBuilder.<FabricClientCommandSource>literal("calc")
            .then(com.mojang.brigadier.builder.RequiredArgumentBuilder.<FabricClientCommandSource, String>argument(
                "expr", com.mojang.brigadier.arguments.StringArgumentType.greedyString())
                .executes(ctx -> {
                    String expr = com.mojang.brigadier.arguments.StringArgumentType.getString(ctx, "expr");
                    try {
                        double result = eval(expr);
                        var mc = Minecraft.getInstance();
                        if (mc.player != null) mc.player.sendSystemMessage(Component.literal("§a" + expr + " = §f" + result));
                    } catch (Exception e) {
                        var mc = Minecraft.getInstance();
                        if (mc.player != null) mc.player.sendSystemMessage(Component.literal("§cCould not evaluate: " + expr));
                    }
                    return 1;
                })));
        dispatcher.register(LiteralArgumentBuilder.<FabricClientCommandSource>literal("ec")
            .executes(ctx -> { sendCmd("enderchest"); return 1; }));
        dispatcher.register(LiteralArgumentBuilder.<FabricClientCommandSource>literal("mouselock")
            .executes(ctx -> {
                var mc = Minecraft.getInstance();
                mc.mouseHandler.grabMouse();
                if (mc.player != null) mc.player.sendSystemMessage(Component.literal("§eMouse locked to window"));
                return 1;
            }));
        dispatcher.register(LiteralArgumentBuilder.<FabricClientCommandSource>literal("gfs")
            .executes(ctx -> { sendCmd("gfs"); return 1; }));
        dispatcher.register(LiteralArgumentBuilder.<FabricClientCommandSource>literal("sacks")
            .executes(ctx -> { sendCmd("sacks"); return 1; }));
        dispatcher.register(LiteralArgumentBuilder.<FabricClientCommandSource>literal("potionbag")
            .executes(ctx -> { sendCmd("potionbag"); return 1; }));
        // NoFrills-style sack filler commands
        dispatcher.register(LiteralArgumentBuilder.<FabricClientCommandSource>literal("getpearls")
            .executes(ctx -> { sendCmd("gfs ender_pearl 16"); return 1; }));
        dispatcher.register(LiteralArgumentBuilder.<FabricClientCommandSource>literal("getleaps")
            .executes(ctx -> { sendCmd("gfs spirit_leap 16"); return 1; }));
        dispatcher.register(LiteralArgumentBuilder.<FabricClientCommandSource>literal("getboom")
            .executes(ctx -> { sendCmd("gfs superboom_tnt 16"); return 1; }));
        dispatcher.register(LiteralArgumentBuilder.<FabricClientCommandSource>literal("getdraft")
            .executes(ctx -> { sendCmd("gfs architects_draft 1"); return 1; }));
        dispatcher.register(LiteralArgumentBuilder.<FabricClientCommandSource>literal("sendcoords")
            .executes(ctx -> {
                var mc = Minecraft.getInstance();
                if (mc.player != null) {
                    int x = (int)mc.player.getX(), y = (int)mc.player.getY(), z = (int)mc.player.getZ();
                    mc.player.connection.sendCommand("pc " + x + " " + y + " " + z);
                }
                return 1;
            }));
        dispatcher.register(LiteralArgumentBuilder.<FabricClientCommandSource>literal("copycoords")
            .executes(ctx -> {
                var mc = Minecraft.getInstance();
                if (mc.player != null) {
                    int x = (int)mc.player.getX(), y = (int)mc.player.getY(), z = (int)mc.player.getZ();
                    mc.keyboardHandler.setClipboard(x + " " + y + " " + z);
                    mc.player.sendSystemMessage(Component.literal("§eCoords copied: §f" + x + " " + y + " " + z));
                }
                return 1;
            }));
        dispatcher.register(LiteralArgumentBuilder.<FabricClientCommandSource>literal("storage")
            .executes(ctx -> { sendCmd("storage"); return 1; }));
        dispatcher.register(LiteralArgumentBuilder.<FabricClientCommandSource>literal("bz")
            .executes(ctx -> { sendCmd("bz"); return 1; }));
        dispatcher.register(LiteralArgumentBuilder.<FabricClientCommandSource>literal("ah")
            .executes(ctx -> { sendCmd("ah"); return 1; }));
        dispatcher.register(LiteralArgumentBuilder.<FabricClientCommandSource>literal("pets")
            .executes(ctx -> { sendCmd("pets"); return 1; }));
        dispatcher.register(LiteralArgumentBuilder.<FabricClientCommandSource>literal("wardrobe")
            .executes(ctx -> { sendCmd("wardrobe"); return 1; }));
        dispatcher.register(LiteralArgumentBuilder.<FabricClientCommandSource>literal("talisman")
            .executes(ctx -> { sendCmd("accessorybag"); return 1; }));
        dispatcher.register(LiteralArgumentBuilder.<FabricClientCommandSource>literal("sack")
            .executes(ctx -> { sendCmd("sacks"); return 1; }));
        dispatcher.register(LiteralArgumentBuilder.<FabricClientCommandSource>literal("viewstash")
            .executes(ctx -> { sendCmd("viewstash"); return 1; }));
        dispatcher.register(LiteralArgumentBuilder.<FabricClientCommandSource>literal("quiver")
            .executes(ctx -> { sendCmd("quiver"); return 1; }));
        dispatcher.register(LiteralArgumentBuilder.<FabricClientCommandSource>literal("equip")
            .executes(ctx -> { sendCmd("equipment"); return 1; }));

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
        if (mc.player == null) return;
        // expand CommandKeys-style placeholders
        String expanded = cmd
            .replace("%myname%", mc.player.getName().getString())
            .replace("%x%", String.valueOf((int)mc.player.getX()))
            .replace("%y%", String.valueOf((int)mc.player.getY()))
            .replace("%z%", String.valueOf((int)mc.player.getZ()))
            .replace("%pos%", (int)mc.player.getX() + " " + (int)mc.player.getY() + " " + (int)mc.player.getZ());
        mc.player.connection.sendCommand(expanded);
    }

    /** Crude expression evaluator for basic arithmetic. */
    private static double eval(String expr) {
        expr = expr.replaceAll("\\s+", "");
        // find the rightmost + or - (lowest precedence)
        int parens = 0;
        for (int i = expr.length() - 1; i >= 1; i--) {
            char c = expr.charAt(i);
            if (c == ')') parens++;
            else if (c == '(') parens--;
            else if (parens == 0) {
                if (c == '+') return eval(expr.substring(0, i)) + eval(expr.substring(i + 1));
                if (c == '-') return eval(expr.substring(0, i)) - eval(expr.substring(i + 1));
            }
        }
        // find rightmost * or /
        parens = 0;
        for (int i = expr.length() - 1; i >= 1; i--) {
            char c = expr.charAt(i);
            if (c == ')') parens++;
            else if (c == '(') parens--;
            else if (parens == 0) {
                if (c == '*') return eval(expr.substring(0, i)) * eval(expr.substring(i + 1));
                if (c == '/') return eval(expr.substring(0, i)) / eval(expr.substring(i + 1));
            }
        }
        // strip parens
        if (expr.startsWith("(") && expr.endsWith(")")) return eval(expr.substring(1, expr.length() - 1));
        // number
        if (expr.endsWith("k")) return Double.parseDouble(expr.substring(0, expr.length() - 1)) * 1000;
        if (expr.endsWith("m")) return Double.parseDouble(expr.substring(0, expr.length() - 1)) * 1_000_000;
        if (expr.endsWith("b")) return Double.parseDouble(expr.substring(0, expr.length() - 1)) * 1_000_000_000;
        return Double.parseDouble(expr);
    }
}
