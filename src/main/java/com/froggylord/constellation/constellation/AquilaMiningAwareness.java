package com.froggylord.constellation.constellation;

import com.froggylord.constellation.ConstellationClient;
import com.froggylord.constellation.config.AquilaConfig;
import com.froggylord.constellation.core.LocationManager.SkyblockArea;
import com.froggylord.constellation.render.WorldRenderer;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// ownership pairing and render behavior ported from SkyHanni (LGPL-3.0-or-later):
// features/mining/GoldenGoblinHighlight.kt
// high-heat sound filter ported from SkyHanni (LGPL-3.0-or-later):
// features/mining/crystalhollows/HighHeatSound.kt
public final class AquilaMiningAwareness {
    private static final Pattern GOBLIN = Pattern.compile("^(Golden|Diamond) Goblin$");
    private static final Pattern HEAT = Pattern.compile("\\bHeat:?\\s*(\\d+)");
    private static final Set<UUID> SEEN = new HashSet<>();
    private static AquilaConfig cfg;
    private static boolean initialized;
    private static long spawnMessageAt;
    private static long goblinEntityAt;
    private static Entity pendingGoblin;
    private static String pendingName = "";
    private static Entity ownedGoblin;
    private static String ownedName = "";

    private AquilaMiningAwareness() {}

    public static void init(AquilaConfig config) {
        cfg = config;
        if (initialized) return;
        initialized = true;
        ClientReceiveMessageEvents.ALLOW_GAME.register((message, overlay) -> {
            if (!overlay) onChat(clean(message.getString()));
            return true;
        });
        ConstellationClient.tick().every(2, "aquila-mining-awareness", AquilaMiningAwareness::scan);
        ClientPlayConnectionEvents.JOIN.register((a, b, c) -> reset());
        ClientPlayConnectionEvents.DISCONNECT.register((a, b) -> reset());
    }

    private static boolean active() {
        return cfg != null && cfg.enabled && cfg.miningAwarenessSuite;
    }

    private static boolean miningArea() {
        SkyblockArea area = ConstellationClient.loc().area();
        return area == SkyblockArea.DWARVEN_MINES || area == SkyblockArea.CRYSTAL_HOLLOWS
            || area == SkyblockArea.GLACITE_TUNNELS || area == SkyblockArea.GLACITE_MINESHAFT;
    }

    private static void onChat(String message) {
        if (!active() || !cfg.ownGoldenGoblinHighlight || !miningArea()) return;
        if (message.equals("A Golden Goblin has spawned!") || message.equals("A Diamond Goblin has spawned!")) {
            spawnMessageAt = System.currentTimeMillis();
            pair();
        }
    }

    private static void scan() {
        Minecraft mc = Minecraft.getInstance();
        if (!active() || !cfg.ownGoldenGoblinHighlight || !miningArea() || mc.level == null || mc.player == null) {
            ownedGoblin = null;
            return;
        }
        long now = System.currentTimeMillis();
        long window = Math.clamp(cfg.ownGoldenGoblinPairSeconds, 2, 30) * 1000L;
        double range = Math.clamp(cfg.ownGoldenGoblinScanRange, 16, 256);
        double nearest = range * range;
        for (Entity entity : mc.level.entitiesForRendering()) {
            if (!(entity instanceof ArmorStand stand) || !stand.hasCustomName()) continue;
            String name = clean(stand.getCustomName().getString());
            if (!GOBLIN.matcher(name).matches() || !SEEN.add(stand.getUUID()) || stand.distanceToSqr(mc.player) > nearest) continue;
            pendingGoblin = mobFor(mc, stand);
            pendingName = name;
            goblinEntityAt = now;
            nearest = stand.distanceToSqr(mc.player);
        }
        pair();
        if (now - spawnMessageAt > window) spawnMessageAt = 0;
        if (now - goblinEntityAt > window) pendingGoblin = null;
        if (ownedGoblin != null && (ownedGoblin.isRemoved() || ownedGoblin.level() != mc.level))
            ownedGoblin = null;
    }

    private static void pair() {
        if (cfg == null || pendingGoblin == null || spawnMessageAt == 0) return;
        long window = Math.clamp(cfg.ownGoldenGoblinPairSeconds, 2, 30) * 1000L;
        if (Math.abs(spawnMessageAt - goblinEntityAt) > window) return;
        ownedGoblin = pendingGoblin;
        ownedName = pendingName;
        pendingGoblin = null;
        spawnMessageAt = 0;
        goblinEntityAt = 0;
    }

    private static Entity mobFor(Minecraft mc, ArmorStand stand) {
        Entity byId = mc.level.getEntity(stand.getId() - 1);
        if (byId != null && !(byId instanceof ArmorStand) && byId.distanceToSqr(stand) <= 9) return byId;
        Entity closest = stand;
        double distance = 9;
        for (Entity entity : mc.level.getEntities(stand, stand.getBoundingBox().inflate(2.5),
            entity -> !(entity instanceof ArmorStand))) {
            double next = entity.distanceToSqr(stand);
            if (next < distance) {
                closest = entity;
                distance = next;
            }
        }
        return closest;
    }

    public static void draw(WorldRenderer.Ctx ctx) {
        Minecraft mc = Minecraft.getInstance();
        Entity goblin = ownedGoblin;
        if (!active() || !cfg.ownGoldenGoblinHighlight || goblin == null || mc.player == null || goblin.isRemoved()) return;
        int color = cfg.ownGoldenGoblinColor;
        boolean walls = cfg.ownGoldenGoblinThroughWalls;
        AABB box = goblin.getBoundingBox();
        Vec3 center = box.getCenter();
        if (cfg.ownGoldenGoblinBox) ctx.highlight(box.inflate(.08), color, walls);
        if (cfg.ownGoldenGoblinBeam) ctx.beam(center.x, box.minY, center.z, color, Math.clamp(cfg.ownGoldenGoblinBeamHeight, 2, 100), walls);
        if (cfg.ownGoldenGoblinLine) ctx.line(mc.player.position().add(0, 1, 0), center, color, walls);
        if (cfg.ownGoldenGoblinLabel) {
            String label = ownedName;
            if (cfg.ownGoldenGoblinDistance) label += " " + Math.round(mc.player.distanceTo(goblin)) + "m";
            ctx.label(new Vec3(center.x, box.maxY + .5, center.z), label, color, walls);
        }
    }

    public static boolean shouldCancel(ClientboundSoundPacket packet) {
        if (!active() || !cfg.muteCrystalHollowsHighHeat
            || ConstellationClient.loc().area() != SkyblockArea.CRYSTAL_HOLLOWS) return false;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.player.getY() > Math.clamp(cfg.highHeatMagmaFieldsMaximumY, 0, 255)
            || heat() < Math.clamp(cfg.highHeatMuteMinimum, 0, 100)) return false;
        return packet.getSound().value().location().toString().equals("minecraft:entity.wolf.pant")
            && packet.getPitch() == 0f && packet.getVolume() == 1f;
    }

    private static int heat() {
        for (String line : ConstellationClient.loc().getSidebarLines()) {
            Matcher matcher = HEAT.matcher(clean(line));
            if (matcher.find()) return Integer.parseInt(matcher.group(1));
        }
        return 0;
    }

    private static String clean(String text) {
        return text.replaceAll("§[0-9A-FK-ORa-fk-or]", "").trim();
    }

    private static void reset() {
        SEEN.clear();
        ownedGoblin = null;
        pendingGoblin = null;
        ownedName = "";
        pendingName = "";
        spawnMessageAt = 0;
        goblinEntityAt = 0;
    }
}
