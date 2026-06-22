package com.froggylord.constellation.constellation;

import com.froggylord.constellation.ConstellationClient;
import net.minecraft.client.Minecraft;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Keeps a running list of who's in your party by reading the party chat. It's the backing for a
 * real /rp — Hypixel has no native reparty, so we disband and re-invite the people we last saw.
 * Membership parsing is the fiddly bit; these patterns cover the common join/leave/list lines.
 */
public final class PartyTracker {

    private PartyTracker() {}

    private static final Set<String> members = new LinkedHashSet<>();

    private static final Pattern JOINED = Pattern.compile("([A-Za-z0-9_]{2,16}) joined the party");
    private static final Pattern LEFT = Pattern.compile("([A-Za-z0-9_]{2,16}) has left the party");
    private static final Pattern REMOVED = Pattern.compile("([A-Za-z0-9_]{2,16}) has been removed from the party");
    private static final Pattern KICKED_OFFLINE = Pattern.compile("([A-Za-z0-9_]{2,16}) was removed from your party because they disconnected");
    private static final Pattern YOU_JOINED = Pattern.compile("You have joined ([A-Za-z0-9_]{2,16})'s? party");
    private static final Pattern INVITED = Pattern.compile("([A-Za-z0-9_]{2,16}) has invited you to join");
    private static final Pattern PARTYING_WITH = Pattern.compile("You'll be partying with: (.+)");
    private static final Pattern MEMBER_LIST = Pattern.compile("Party (?:Members|Moderators|Leader)\\b.*?:\\s*(.+)");

    public static void init() {
        net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents.GAME.register((msg, overlay) -> {
            if (overlay) return;
            String s = msg.getString();
            if (s.contains("The party was disbanded") || s.contains("You left the party")
                || s.contains("You are not in a party") || s.contains("You have been kicked from the party")) {
                members.clear();
                return;
            }
            add(JOINED, s); add(YOU_JOINED, s); add(INVITED, s);
            remove(LEFT, s); remove(REMOVED, s); remove(KICKED_OFFLINE, s);

            Matcher pw = PARTYING_WITH.matcher(s);
            if (pw.find()) addNames(pw.group(1));
            Matcher ml = MEMBER_LIST.matcher(s);
            if (ml.find()) addNames(ml.group(1));
        });
    }

    private static void add(Pattern p, String s) {
        Matcher m = p.matcher(s);
        if (m.find()) members.add(self(m.group(1)));
    }

    private static void remove(Pattern p, String s) {
        Matcher m = p.matcher(s);
        if (m.find()) members.remove(m.group(1));
    }

    private static void addNames(String csv) {
        for (String raw : csv.split("[,●•]")) {
            // each chunk may carry a rank prefix; the name is the last word-token
            Matcher m = Pattern.compile("([A-Za-z0-9_]{2,16})\\s*$").matcher(raw.trim());
            if (m.find()) members.add(self(m.group(1)));
        }
    }

    /** don't try to invite yourself back. */
    private static String self(String name) {
        var mc = Minecraft.getInstance();
        if (mc.player != null && name.equalsIgnoreCase(mc.player.getName().getString())) return "";
        return name;
    }

    public static Set<String> members() { return members; }

    /** disband and re-invite everyone we last had. */
    public static void reparty() {
        var mc = Minecraft.getInstance();
        if (mc.player == null) return;
        Set<String> snapshot = new LinkedHashSet<>(members);
        snapshot.remove("");
        if (snapshot.isEmpty()) {
            mc.player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§d[Pegasus]§r no tracked party to rebuild"));
            return;
        }
        mc.player.connection.sendCommand("p disband");
        int delay = 30;
        for (String name : snapshot) {
            final String n = name;
            ConstellationClient.tick().once(delay, "pegasus-rp-" + n, () -> {
                var m = Minecraft.getInstance();
                if (m.player != null) m.player.connection.sendCommand("p invite " + n);
            });
            delay += 12;
        }
        mc.player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§d[Pegasus]§r repartying " + snapshot.size() + " member(s)"));
    }
}
