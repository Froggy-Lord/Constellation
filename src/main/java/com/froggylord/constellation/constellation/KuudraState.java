package com.froggylord.constellation.constellation;

import com.froggylord.constellation.ConstellationClient;
import com.froggylord.constellation.core.LocationManager.SkyblockArea;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Shared Kuudra lifecycle ported from Athen's KuudraAPI. */
public final class KuudraState {
    public enum Phase { NONE, SUPPLY, BUILD, FUEL, STUN, DPS, SKIP, KILL, DONE }

    private static final Pattern TIER = Pattern.compile("Kuudra's Hollow \\(T([1-5])\\)");
    private static final Pattern SUPPLY = Pattern.compile("(?:\\[[^]]*] )?(\\w{1,16}) recovered one of Elle's supplies! \\((\\d+)/(\\d+)\\)");
    private static final Pattern EATEN = Pattern.compile("^(\\w+) has been eaten by Kuudra!$");
    private static boolean initialized;
    private static boolean inRun;
    private static boolean carrying;
    private static int tier;
    private static int recovered;
    private static int total = 6;
    private static Phase phase = Phase.NONE;
    private static long phaseStartNanos;
    private static long runStartNanos;
    private static long locationGraceUntilNanos;

    private KuudraState() {}

    public static void init() {
        if (initialized) return;
        initialized = true;
        // ported from Athen (BSD-3-Clause): api/kuudra/KuudraAPI.kt chat lifecycle
        ClientReceiveMessageEvents.ALLOW_GAME.register((message, overlay) -> {
            if (overlay || !enabled()) return true;
            String text = net.minecraft.ChatFormatting.stripFormatting(message.getString());
            return onMessage(text == null ? message.getString() : text);
        });
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> reset());
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> reset());
    }

    private static boolean onMessage(String text) {
        boolean startMessage = text.equals("[NPC] Elle: Okay adventurers, I will go and fish up Kuudra!");
        // keep contribution parsing before location lag or supply-message replacement can hide the event
        if (!startMessage) KuudraBreakdown.onMessage(text);
        if (startMessage) {
            var area = ConstellationClient.loc().area();
            if (area != SkyblockArea.KUUDRA && area != SkyblockArea.CRIMSON_ISLE && area != SkyblockArea.UNKNOWN)
                return true;
            reset();
            inRun = true;
            runStartNanos = System.nanoTime();
            locationGraceUntilNanos = System.nanoTime() + 5_000_000_000L;
            setPhase(Phase.SUPPLY);
            KuudraBreakdown.start();
        } else if (!inKuudra()) {
            return true;
        } else if (text.equals("[NPC] Elle: OMG! Great work collecting my supplies!")) {
            carrying = false;
            setPhase(Phase.BUILD);
        } else if (text.equals("[NPC] Elle: Phew! The Ballista is finally ready! It should be strong enough to tank Kuudra's blows now!")) {
            setPhase(Phase.FUEL);
        } else if (text.equals("[NPC] Elle: POW! SURELY THAT'S IT! I don't think he has any more in him!")) {
            setPhase(tier == 5 ? Phase.SKIP : Phase.KILL);
        } else if (text.equals("You retrieved some of Elle's supplies from the Lava!")
            || text.equals("You retrieved a Ballista Fuel Cell from the Lava!")) {
            carrying = true;
            // ported from Athen (BSD-3-Clause): modules/impl/kuudra/KuudraTitles.kt pickup event
            KuudraTitles.onPickup();
        } else if (text.equals("You moved and the Chest slipped out of your hands!")) {
            carrying = false;
            // ported from Athen (BSD-3-Clause): modules/impl/kuudra/KuudraTitles.kt drop event
            KuudraTitles.onDrop();
        } else if (text.matches("^\\s*KUUDRA DOWN!.*")) {
            carrying = false;
            inRun = false;
            setPhase(Phase.DONE);
        } else if (text.matches("^\\s*DEFEAT.*")) {
            carrying = false;
            inRun = false;
            setPhase(Phase.DONE);
        } else if (tier >= 3 && phase.ordinal() < Phase.STUN.ordinal()) {
            Matcher eaten = EATEN.matcher(text);
            if (eaten.matches() && !eaten.group(1).equals("Elle")) setPhase(Phase.STUN);
        } else if (tier >= 3 && phase.ordinal() < Phase.DPS.ordinal()
            && text.matches("^\\w+ destroyed one of Kuudra's pods!$")) {
            setPhase(Phase.DPS);
        }

        Matcher supply = SUPPLY.matcher(text);
        if (supply.matches() && phase == Phase.SUPPLY) {
            recovered = parse(supply.group(2), recovered);
            total = parse(supply.group(3), total);
            var cfg = ConstellationClient.cfg().draco;
            if (cfg.kuudraCustomSupplyMessages) {
                String custom = cfg.kuudraSupplyMessage
                    .replace("{player}", supply.group(1))
                    .replace("{time}", formatSeconds(runElapsedMillis()))
                    .replace("{current}", Integer.toString(recovered))
                    .replace("{total}", Integer.toString(total));
                var player = net.minecraft.client.Minecraft.getInstance().player;
                if (player != null) player.sendSystemMessage(net.minecraft.network.chat.Component.literal(custom));
                return !cfg.kuudraHideOriginalSupplyMessages;
            }
        }
        return true;
    }

    // ported from Athen (BSD-3-Clause): api/kuudra/KuudraAPI.kt tierRegex
    public static void tick() {
        if (!enabled()) {
            if (inRun || phase != Phase.NONE) reset();
            return;
        }
        if (!inKuudra()) {
            if (System.nanoTime() >= locationGraceUntilNanos && (inRun || phase != Phase.NONE)) reset();
            return;
        }
        for (String line : ConstellationClient.loc().getSidebarLines()) {
            Matcher matcher = TIER.matcher(line);
            if (matcher.find()) tier = parse(matcher.group(1), tier);
        }
        // ported from Athen (BSD-3-Clause): api/kuudra/KuudraAPI.kt Skip transition
        var player = net.minecraft.client.Minecraft.getInstance().player;
        if (inRun && tier == 5 && phase == Phase.SKIP && player != null && player.getY() < 10) setPhase(Phase.KILL);
    }

    private static void setPhase(Phase next) {
        if (phase == next) return;
        phase = next;
        phaseStartNanos = System.nanoTime();
        KuudraSplits.onPhase(next);
    }

    public static void reset() {
        inRun = false;
        carrying = false;
        tier = 0;
        recovered = 0;
        total = 6;
        phase = Phase.NONE;
        phaseStartNanos = 0;
        runStartNanos = 0;
        locationGraceUntilNanos = 0;
        KuudraSupplyHelper.reset();
        KuudraBuildHelper.reset();
        KuudraStunHelper.reset();
        KuudraTimers.reset();
        KuudraSplits.reset();
        KuudraBreakdown.reset();
        KuudraTitles.reset();
    }

    public static boolean inKuudra() { return ConstellationClient.loc().area() == SkyblockArea.KUUDRA; }
    private static boolean enabled() {
        return ConstellationClient.cfg() != null && ConstellationClient.cfg().draco != null
            && ConstellationClient.cfg().draco.enabled;
    }
    public static boolean inRun() { return inRun; }
    public static boolean carrying() { return carrying; }
    public static int tier() { return tier; }
    public static int recovered() { return recovered; }
    public static int total() { return total; }
    public static Phase phase() { return phase; }
    public static long phaseElapsedMillis() { return elapsed(phaseStartNanos); }
    public static long runElapsedMillis() { return elapsed(runStartNanos); }

    private static long elapsed(long start) {
        return start == 0 ? 0 : Math.max(0, (System.nanoTime() - start) / 1_000_000L);
    }

    private static int parse(String value, int fallback) {
        try { return Integer.parseInt(value); } catch (NumberFormatException ignored) { return fallback; }
    }

    static String formatSeconds(long millis) { return String.format(java.util.Locale.ROOT, "%.1fs", millis / 1000.0); }
}
