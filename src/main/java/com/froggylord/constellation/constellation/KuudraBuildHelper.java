package com.froggylord.constellation.constellation;

import com.froggylord.constellation.ConstellationClient;
import com.froggylord.constellation.config.DracoConfig;
import com.froggylord.constellation.render.WorldRenderer;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class KuudraBuildHelper {
    private static final Pattern OVERALL = Pattern.compile("Building Progress (\\d+)% \\((\\d+) Players Helping\\)");
    private static final Pattern PILE = Pattern.compile("PROGRESS: (\\d+)%");
    private static final Pattern PARTY_FRESH = Pattern.compile("^Party > (?:\\[[^]]*] )?(\\w{1,16}): FRESH(?: \\[\\d+%])?$");
    private static final String OWN_FRESH = "Your Fresh Tools Perk bonus doubles your building speed for the next 10 seconds!";
    // ported from Athen (BSD-3-Clause): api/kuudra/enums/KuudraSupply.kt
    private static final BuildPile[] PILES = {
        new BuildPile("Shop", new BlockPos(-98, 78, -113)),
        new BuildPile("Equals", new BlockPos(-99, 78, -100)),
        new BuildPile("Cannon", new BlockPos(-110, 78, -107)),
        new BuildPile("X", new BlockPos(-106, 78, -113)),
        new BuildPile("Triangle", new BlockPos(-94, 78, -106)),
        new BuildPile("Slash", new BlockPos(-107, 78, -100))
    };
    private static final Map<String, Long> FRESHERS = new LinkedHashMap<>();
    private static boolean initialized;
    private static boolean stunSent;
    private static PartyState partyState = PartyState.UNKNOWN;
    private static int buildProgress;
    private static int builders;

    private enum PartyState { UNKNOWN, PRESENT, ABSENT }

    private static final class BuildPile {
        final String name;
        final BlockPos pos;
        int progress;
        boolean built;
        BuildPile(String name, BlockPos pos) { this.name = name; this.pos = pos; }
    }

    private KuudraBuildHelper() {}

    public static void init() {
        if (initialized) return;
        initialized = true;
        // ported from Athen (BSD-3-Clause): modules/impl/kuudra/FreshTools.kt
        // and Odin (BSD-3-Clause): features/impl/nether/FreshTools.kt
        ClientReceiveMessageEvents.ALLOW_GAME.register((message, overlay) -> {
            if (overlay || !enabled()) return true;
            String stripped = ChatFormatting.stripFormatting(message.getString());
            if (stripped == null) stripped = message.getString();
            updatePartyState(stripped);
            if (!KuudraState.inRun()) return true;
            if (KuudraState.phase() == KuudraState.Phase.BUILD && stripped.equals(OWN_FRESH)) ownFresh();
            if (ConstellationClient.cfg().draco.kuudraFreshTrackParty) {
                Matcher party = PARTY_FRESH.matcher(stripped);
                if (party.matches()) FRESHERS.put(party.group(1), System.nanoTime());
            }
            return true;
        });
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> resetConnection());
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> resetConnection());
    }

    // ported from Athen (BSD-3-Clause): api/kuudra/KuudraAPI.kt build armor-stand parsing
    public static void tick() {
        DracoConfig cfg = ConstellationClient.cfg().draco;
        if (!enabled() || !KuudraState.inRun()) {
            if (buildProgress != 0 || builders != 0 || !FRESHERS.isEmpty()) reset();
            return;
        }
        long cutoff = System.nanoTime() - Math.clamp(cfg.kuudraFreshDurationMs, 1_000, 30_000) * 1_000_000L;
        FRESHERS.entrySet().removeIf(entry -> entry.getValue() < cutoff);
        if (KuudraState.phase() != KuudraState.Phase.BUILD) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        int newProgress = buildProgress;
        int newBuilders = builders;
        for (var entity : mc.level.entitiesForRendering()) {
            if (!(entity instanceof ArmorStand stand)) continue;
            String name = ChatFormatting.stripFormatting(stand.getName().getString());
            if (name == null) continue;
            Matcher overall = OVERALL.matcher(name);
            if (overall.find()) {
                newProgress = parse(overall.group(1), newProgress);
                newBuilders = parse(overall.group(2), newBuilders);
                continue;
            }
            BuildPile pile = pileAt(stand.blockPosition());
            if (pile == null) continue;
            if (name.equals("PROGRESS: COMPLETE")) {
                pile.progress = 100;
                pile.built = true;
            } else {
                Matcher progress = PILE.matcher(name);
                if (progress.matches()) {
                    pile.progress = parse(progress.group(1), pile.progress);
                    pile.built = false;
                }
            }
        }
        buildProgress = newProgress;
        builders = newBuilders;
        // ported from Athen (BSD-3-Clause): modules/impl/kuudra/BuildInfo.kt buildProgress alert
        if (cfg.kuudraBuildInfo && cfg.kuudraBuildStunAlert && !stunSent
            && buildProgress > Math.clamp(cfg.kuudraBuildStunPercent, 1, 100)) {
            stunSent = true;
            alert(cfg.kuudraBuildStunMessage, cfg.kuudraBuildStunChat, cfg.kuudraBuildStunTitle);
        }
    }

    // ported from Athen (BSD-3-Clause): modules/impl/kuudra/BuildInfo.kt world waypoints
    public static void draw(WorldRenderer.Ctx ctx) {
        DracoConfig cfg = ConstellationClient.cfg().draco;
        if (!cfg.kuudraBuildInfo || !KuudraState.inRun() || KuudraState.phase() != KuudraState.Phase.BUILD) return;
        if (cfg.kuudraBuildWaypoints) {
            for (BuildPile pile : PILES) {
                if (pile.built) continue;
                AABB box = new AABB(pile.pos);
                ctx.outline(box, cfg.kuudraBuildColour, cfg.kuudraBuildThroughWalls);
                if (cfg.kuudraBuildWaypointLabels)
                    ctx.label(Vec3.atCenterOf(pile.pos).add(0, 1.2, 0),
                        pile.name + " Build " + pile.progress + "%", cfg.kuudraBuildColour, cfg.kuudraBuildThroughWalls);
            }
        }
        // ported from Odin (BSD-3-Clause): features/impl/nether/BuildHelper.kt Render on Ballista
        if (cfg.kuudraBuildWorldInfo) {
            ctx.label(new Vec3(-101.5, 82.0, -105.5), "Build " + buildProgress + "%",
                buildColour(buildProgress), cfg.kuudraBuildThroughWalls);
            ctx.label(new Vec3(-101.5, 81.0, -105.5), "Builders " + builders,
                builderColour(builders), cfg.kuudraBuildThroughWalls);
        }
    }

    private static void ownFresh() {
        DracoConfig cfg = ConstellationClient.cfg().draco;
        if (!cfg.kuudraFreshTools) return;
        Minecraft mc = Minecraft.getInstance();
        String name = mc.player == null ? "You" : mc.player.getName().getString();
        FRESHERS.put(name, System.nanoTime());
        if (cfg.kuudraFreshAlert)
            alert(cfg.kuudraFreshAlertMessage, cfg.kuudraFreshAlertChat, cfg.kuudraFreshAlertTitle);
        if (cfg.kuudraFreshNotifyParty && (!cfg.kuudraFreshNotifyOnlyInParty || inPartyInstance())) {
            BuildPile nearest = mc.player == null ? null : closestPile(mc.player.blockPosition(), Double.MAX_VALUE);
            String build = nearest == null ? Integer.toString(buildProgress) : Integer.toString(nearest.progress);
            String text = cfg.kuudraFreshPartyMessage.replace("{build}", build)
                .replace("#buildPerc", build + "%").replace('\n', ' ').replace('\r', ' ').trim();
            if (!text.isEmpty() && mc.player != null && mc.player.connection != null)
                mc.player.connection.sendCommand("pc " + (text.length() > 120 ? text.substring(0, 120) : text));
        }
    }

    public static String buildHudText() {
        DracoConfig cfg = ConstellationClient.cfg().draco;
        if (!cfg.kuudraBuildInfo || !KuudraState.inRun() || KuudraState.phase() != KuudraState.Phase.BUILD) return null;
        return "Builders " + builders + " | Build " + buildProgress + "%";
    }

    public static String freshHudText() {
        DracoConfig cfg = ConstellationClient.cfg().draco;
        if (!cfg.kuudraFreshTools || !KuudraState.inRun() || KuudraState.phase() != KuudraState.Phase.BUILD) return null;
        long now = System.nanoTime();
        int duration = Math.clamp(cfg.kuudraFreshDurationMs, 1_000, 30_000);
        StringBuilder text = new StringBuilder();
        for (var entry : FRESHERS.entrySet()) {
            long left = duration - (now - entry.getValue()) / 1_000_000L;
            if (left <= 0) continue;
            if (!text.isEmpty()) text.append(" | ");
            text.append(entry.getKey()).append(' ').append(String.format(Locale.ROOT, "%.1fs", left / 1000.0));
        }
        return text.isEmpty() ? null : text.toString();
    }

    public static void reset() {
        stunSent = false;
        buildProgress = 0;
        builders = 0;
        FRESHERS.clear();
        for (BuildPile pile : PILES) {
            pile.progress = 0;
            pile.built = false;
        }
    }

    public static int buildProgress() { return buildProgress; }
    public static int builders() { return builders; }

    private static BuildPile closestPile(BlockPos pos, double maxDistanceSquared) {
        BuildPile best = null;
        double distance = maxDistanceSquared;
        for (BuildPile pile : PILES) {
            double current = pile.pos.distSqr(pos);
            if (current < distance) { distance = current; best = pile; }
        }
        return best;
    }

    private static BuildPile pileAt(BlockPos pos) {
        for (BuildPile pile : PILES) if (pile.pos.equals(pos)) return pile;
        return null;
    }

    private static boolean inPartyInstance() {
        return partyState == PartyState.PRESENT;
    }

    private static void updatePartyState(String message) {
        String lower = message.toLowerCase(Locale.ROOT);
        if (message.startsWith("Party > ") || message.startsWith("Party Leader:")
            || message.startsWith("Party Members") || lower.startsWith("you have joined ") && lower.endsWith(" party!")
            || lower.equals("you created a party.") || lower.contains(" joined the party.")) {
            partyState = PartyState.PRESENT;
        } else if (lower.equals("you are not currently in a party.") || lower.equals("you are not in a party.")
            || lower.equals("you left the party.") || lower.startsWith("you have been kicked from the party")
            || lower.startsWith("the party was disbanded")) {
            partyState = PartyState.ABSENT;
        }
    }

    private static void resetConnection() {
        partyState = PartyState.UNKNOWN;
        reset();
    }

    private static int buildColour(int value) {
        return value >= 75 ? 0xFF55FF55 : value >= 50 ? 0xFFFFFF55 : value >= 25 ? 0xFFFFAA00 : 0xFFFF5555;
    }

    private static int builderColour(int value) {
        return value >= 3 ? 0xFF55FF55 : value >= 2 ? 0xFFFFFF55 : 0xFFFF5555;
    }

    private static void alert(String text, boolean chat, boolean title) {
        Minecraft mc = Minecraft.getInstance();
        String clean = text == null ? "" : text.replace('\n', ' ').replace('\r', ' ').trim();
        if (clean.isEmpty() || mc.player == null) return;
        if (chat) mc.player.sendSystemMessage(Component.literal(clean));
        if (title) mc.gui.hud.setTitle(Component.literal(clean));
    }

    private static boolean enabled() {
        return ConstellationClient.cfg() != null && ConstellationClient.cfg().draco != null
            && ConstellationClient.cfg().draco.enabled;
    }

    private static int parse(String value, int fallback) {
        try { return Integer.parseInt(value); } catch (NumberFormatException ignored) { return fallback; }
    }
}
