package com.froggylord.constellation.constellation;

import com.froggylord.constellation.ConstellationClient;
import com.froggylord.constellation.config.OrionConfig;
import com.froggylord.constellation.data.DungeonState;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// ported from devonian (GPL-3.0): features/dungeons/AutoRequeueDungeons.kt
// cross-checked with NoammAddons (CC0-1.0): features/impl/dungeon/AutoRequeue.kt
// cross-checked with Odin (BSD-3-Clause): features/impl/dungeon/DungeonQueue.kt
public final class AutoRequeue {
    private static final Pattern PARTY_CHAT = Pattern.compile("^Party > (?:\\[[^]]+] )?(\\w{1,16}): (.+)$");
    private static final Set<String> downtime = new HashSet<>();
    private static Set<String> runParty = Set.of();
    private static OrionConfig cfg;
    private static int generation;
    private static boolean runFinished;
    private static boolean pending;
    private static boolean initialized;

    private AutoRequeue() {}

    public static void init(OrionConfig config) {
        cfg = config;
        if (initialized) return;
        initialized = true;
        ConstellationClient.bus().subscribe(DungeonState.DungeonStart.class, ignored -> onStart());
        ClientReceiveMessageEvents.ALLOW_GAME.register((message, overlay) -> {
            if (!overlay) onChat(message.getString());
            return true;
        });
    }

    private static void onStart() {
        generation++;
        pending = false;
        runFinished = false;
        downtime.clear();
        runParty = partyNames();
    }

    private static void onComplete() {
        if (runFinished) return;
        runFinished = true;
        if (active()) schedule(false);
    }

    public static int schedule(boolean manual) {
        if (!manual && !active()) return 0;
        if (pending) {
            feedback("A requeue is already pending.");
            return 0;
        }
        if (!manual && cfg.requeueDowntime && !downtime.isEmpty()) {
            feedback("Waiting for downtime: " + String.join(", ", downtime));
            controls(true);
            return 0;
        }
        pending = true;
        int token = ++generation;
        int delay = manual ? 0 : Math.max(0, Math.min(30, cfg.requeueDelaySec));
        if (delay > 0) {
            feedback("Requeueing in " + delay + "s.");
            controls(false);
        }
        ConstellationClient.tick().once(delay * 20, "orion-auto-requeue", () -> execute(token, manual));
        return 1;
    }

    public static int cancel() {
        generation++;
        pending = false;
        runFinished = false;
        ConstellationClient.tick().cancel("orion-auto-requeue");
        feedback("Requeue cancelled.");
        return 1;
    }

    public static int status() {
        feedback(pending ? "A requeue is pending." : runFinished ? "Run complete; no requeue is pending." : "No completed run is pending.");
        if (pending || runFinished) controls(!pending);
        return 1;
    }

    public static int delay(int seconds) {
        cfg.requeueDelaySec = Math.max(0, Math.min(30, seconds));
        ConstellationClient.saveConfig();
        feedback("Requeue delay set to " + cfg.requeueDelaySec + "s.");
        return 1;
    }

    private static void execute(int token, boolean manual) {
        if (token != generation || !pending) return;
        pending = false;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.player.connection == null || !ConstellationClient.loc().onHypixel()) {
            feedback("Requeue stopped: not connected to Hypixel.");
            controls(true);
            return;
        }
        if (!manual && cfg.requeueCancelOnPartyChange && !sameParty()) {
            feedback("Requeue stopped: the party changed after the run.");
            controls(true);
            return;
        }
        if (!manual && cfg.requeueDowntime && !downtime.isEmpty()) {
            feedback("Requeue stopped: " + String.join(", ", downtime) + " still needs downtime.");
            controls(true);
            return;
        }
        mc.player.connection.sendCommand("instancerequeue");
        feedback("Sent /instancerequeue.");
    }

    private static void onChat(String raw) {
        if (cfg == null || raw == null) return;
        String message = net.minecraft.ChatFormatting.stripFormatting(raw).trim();
        if (message.equals("> EXTRA STATS <")) onComplete();
        Matcher party = PARTY_CHAT.matcher(message);
        if (cfg.requeueDowntime && party.matches()) {
            String name = party.group(1);
            String text = party.group(2).trim().toLowerCase(Locale.ROOT);
            if (text.equals("dt") || text.startsWith("!dt")) {
                if (downtime.add(name)) feedback(name + " needs downtime.");
            } else if (text.equals("r") || text.equals("!r")) {
                if (downtime.remove(name)) feedback(name + " is ready.");
                if (runFinished && !pending && downtime.isEmpty() && active()) schedule(false);
            }
        }
        String lower = message.toLowerCase(Locale.ROOT);
        if (pending && cfg.requeueCancelOnPartyChange && (lower.contains("left the party")
            || lower.contains("was removed from the party") || lower.contains("kicked from the party")
            || lower.contains("party was disbanded"))) cancelWithReason("party membership changed");
        if (lower.equals("you were kicked while joining that server!")
            || lower.equals("you are no longer allowed to access this instance!")
            || lower.contains("couldn't find a suitable dungeon server")) {
            pending = false;
            generation++;
            feedback("Dungeon queue failed.");
            controls(true);
        }
    }

    private static void cancelWithReason(String reason) {
        generation++;
        pending = false;
        runFinished = false;
        ConstellationClient.tick().cancel("orion-auto-requeue");
        feedback("Requeue stopped: " + reason + '.');
        controls(true);
    }

    private static boolean sameParty() {
        Set<String> current = partyNames();
        return runParty.isEmpty() || current.isEmpty() || current.equals(runParty);
    }

    private static Set<String> partyNames() {
        Set<String> names = new HashSet<>();
        for (DungeonState.Teammate teammate : ConstellationClient.dungeon().teammates())
            names.add(teammate.name().toLowerCase(Locale.ROOT));
        return Set.copyOf(names);
    }

    private static boolean active() {
        return cfg != null && cfg.enabled && cfg.autoRequeue && ConstellationClient.loc().onHypixel();
    }

    private static void controls(boolean allowNow) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || cfg == null || !cfg.requeueFeedback) return;
        Component line = Component.literal("§8[§bRequeue§8] ");
        if (allowNow) line = line.copy().append(Component.literal("§a[Requeue now]")
            .withStyle(style -> style.withClickEvent(new ClickEvent.RunCommand("/requeue now"))));
        if (pending) line = line.copy().append(Component.literal(" §c[Cancel]")
            .withStyle(style -> style.withClickEvent(new ClickEvent.RunCommand("/requeue cancel"))));
        mc.player.sendSystemMessage(line);
    }

    private static void feedback(String text) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && cfg != null && cfg.requeueFeedback)
            mc.player.sendSystemMessage(Component.literal("§bAutoRequeue §8> §f" + text));
    }
}
