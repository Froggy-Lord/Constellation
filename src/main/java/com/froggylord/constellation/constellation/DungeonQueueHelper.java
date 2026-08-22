package com.froggylord.constellation.constellation;

import com.froggylord.constellation.ConstellationClient;
import com.froggylord.constellation.config.OrionConfig;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientSendMessageEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;

import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// timer and floor-entry trigger ported from Devonian (GPL-3.0): features/dungeons/WarpCooldown.kt
// queue trigger ported from NoFrills (GPL-3.0): hud/elements/QueueCooldownTimer.java
// command guard ported from SkyHanni (LGPL-2.1): features/dungeon/DungeonCreationCooldown.kt
public final class DungeonQueueHelper {
    private static final Pattern ENTERED = Pattern.compile(
        "(?s)^-*\\n.*?(?<player>\\w{1,16}) entered (?:MM )?The Catacombs, Floor [IVX]+!\\n-*$");
    private static final Pattern QUEUED = Pattern.compile(
        "(?s)^-*\\n.*?(?<player>\\w{1,16}) queued for .*!\\nThe party is in position #(?<position>\\d+) of the queue!\\n-*$");
    private static final Pattern ROLE_LINE = Pattern.compile("^Party (Leader|Moderators|Members): (.+)$");
    private static final Pattern PLAYER = Pattern.compile("(?:\\[[^]]+] )?(\\w{1,16})(?: [●●])?");
    private static final Set<String> moderators = new LinkedHashSet<>();
    private static OrionConfig cfg;
    private static long cooldownUntil;
    private static int queuePosition;
    private static boolean readingParty;
    private static int delimiters;
    private static boolean recoveryWaiting;
    private static boolean initialized;

    private DungeonQueueHelper() {}

    public static void init(OrionConfig config) {
        cfg = config;
        if (initialized) return;
        initialized = true;
        ClientReceiveMessageEvents.ALLOW_GAME.register((message, overlay) -> {
            if (!overlay) onChat(message.getString());
            return true;
        });
        ClientSendMessageEvents.ALLOW_COMMAND.register(DungeonQueueHelper::allowCommand);
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> reset());
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> reset());
    }

    public static void registerCommands(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        dispatcher.register(LiteralArgumentBuilder.<FabricClientCommandSource>literal("queuecooldown")
            .executes(ctx -> status())
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("status").executes(ctx -> status()))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("clear").executes(ctx -> clear())));
    }

    public static String hudText() {
        if (!active() || cooldownUntil <= System.currentTimeMillis()) return null;
        long left = cooldownUntil - System.currentTimeMillis();
        String colour = colour(left);
        String queue = queuePosition > 0 ? " §7Queue #" + queuePosition : "";
        return colour + String.format(Locale.ROOT, "%.1fs", left / 1000.0) + queue;
    }

    private static void onChat(String raw) {
        if (raw == null || cfg == null) return;
        String message = ChatFormatting.stripFormatting(raw).trim();
        String self = Minecraft.getInstance().getUser().getName();
        Matcher entered = ENTERED.matcher(message);
        Matcher queued = QUEUED.matcher(message);
        if (entered.matches() && self.equalsIgnoreCase(entered.group("player"))) start(0);
        else if (queued.matches() && self.equalsIgnoreCase(queued.group("player")))
            start(Integer.parseInt(queued.group("position")));

        if (message.equals("You were kicked while joining that server!")
            || message.equals("You are no longer allowed to access this instance!")
            || message.toLowerCase(Locale.ROOT).contains("couldn't find a suitable dungeon server")) {
            recoveryWaiting = true;
            showRecovery();
        }
        parseParty(message);
    }

    private static void start(int position) {
        if (!active()) return;
        cooldownUntil = System.currentTimeMillis() + 30_000L;
        queuePosition = position;
    }

    private static boolean allowCommand(String raw) {
        if (cfg == null || !cfg.dungeonQueueBlockCommands || cooldownUntil <= System.currentTimeMillis()) return true;
        String command = raw.toLowerCase(Locale.ROOT).trim();
        if (!command.startsWith("joininstance") && !command.startsWith("joindungeon")) return true;
        local("Blocked /" + raw + " for " + formatRemaining() + ". Disable cooldown command blocking to override.");
        return false;
    }

    // party role parsing ported from NoammAddons (CC0-1.0): features/impl/general/PartyHelper.kt
    private static void parseParty(String message) {
        if (message.matches("^Party Members \\(\\d+\\)$")) {
            readingParty = true;
            delimiters = 0;
            moderators.clear();
            return;
        }
        if (!readingParty) return;
        if (message.startsWith("---")) {
            if (++delimiters >= 1) {
                readingParty = false;
                if (recoveryWaiting) showRecovery();
            }
            return;
        }
        Matcher role = ROLE_LINE.matcher(message);
        if (!role.matches() || !role.group(1).equals("Moderators")) return;
        Matcher names = PLAYER.matcher(role.group(2));
        while (names.find()) moderators.add(names.group(1));
    }

    // clickable manual transfer ported from NoFrills (GPL-3.0): features/general/partycommands/TransferCommand.java
    private static void showRecovery() {
        if (cfg == null || !cfg.dungeonQueueTransferRecovery) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        Component line = Component.literal("§bDungeon Queue §8> §fInstance creation failed. ");
        if (moderators.isEmpty()) {
            line = line.copy().append(Component.literal("§e[Load party roles]")
                .withStyle(s -> s.withClickEvent(new ClickEvent.RunCommand("/party list"))));
        } else {
            line = line.copy().append(Component.literal("§7Transfer to: "));
            for (String moderator : moderators) line = line.copy()
                .append(Component.literal("§a[" + moderator + "] ")
                    .withStyle(s -> s.withClickEvent(new ClickEvent.RunCommand("/party transfer " + moderator))));
        }
        line = line.copy().append(Component.literal("§b[Retry]")
            .withStyle(s -> s.withClickEvent(new ClickEvent.RunCommand("/requeue now"))));
        mc.player.sendSystemMessage(line);
    }

    private static int status() {
        if (cooldownUntil <= System.currentTimeMillis()) local("No dungeon creation cooldown is active.");
        else local("Creation cooldown: " + formatRemaining() + (queuePosition > 0 ? ", queue position #" + queuePosition : "") + '.');
        return 1;
    }

    private static int clear() {
        cooldownUntil = 0;
        queuePosition = 0;
        local("Dungeon creation cooldown display cleared.");
        return 1;
    }

    private static String formatRemaining() {
        return String.format(Locale.ROOT, "%.1fs", Math.max(0, cooldownUntil - System.currentTimeMillis()) / 1000.0);
    }

    private static String colour(long left) {
        if (left >= 22_500) return "§4";
        if (left >= 15_000) return "§c";
        if (left >= 7_500) return "§e";
        return "§a";
    }

    private static boolean active() {
        return cfg != null && cfg.enabled && cfg.dungeonQueueCooldown && ConstellationClient.loc().onHypixel();
    }

    private static void local(String text) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) mc.player.sendSystemMessage(Component.literal("§bDungeon Queue §8> §f" + text));
    }

    private static void reset() {
        cooldownUntil = 0;
        queuePosition = 0;
        readingParty = false;
        recoveryWaiting = false;
        moderators.clear();
    }
}
