package com.froggylord.constellation.constellation;

import com.froggylord.constellation.ConstellationClient;
import com.froggylord.constellation.config.HydraConfig;
import com.froggylord.constellation.core.LocationManager.SkyblockArea;
import com.froggylord.constellation.render.WorldRenderer;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

// ported from SkyHanni (LGPL-3.0-or-later): features/foraging/WormholeFinder.kt
public final class HydraWormholeFinder {
    private record Wormhole(int id, Vec3 pos) {}
    // ported from SkyHanni (LGPL-3.0-or-later): repo/constants/island_graphs/LOTUS_ATOLL.json
    private static final List<Wormhole> LOTUS = List.of(
        w(490,45,66,-10),w(491,36,66,6),w(492,79,91,2),w(493,68,61,-7),w(494,13,66,-2),
        w(495,42,66,19),w(496,37,70,-34),w(497,92,79,-4),w(498,84,91,-4),w(499,14,74,29),
        w(500,26,66,-10),w(501,25,66,4),w(502,17,66,-13),w(503,73,61,7),w(504,79,61,-19),
        w(505,99,79,-4),w(506,36,66,-20),w(507,63,66,-29),w(508,67,66,20),w(509,54,66,-20),
        w(510,25,66,20),w(511,52,66,5),w(512,18,66,4));
    // ported from SkyHanni (LGPL-3.0-or-later): repo/constants/island_graphs/CRIMSON_ISLE.json
    private static final List<Wormhole> CRIMSON = List.of(
        w(5760,-385,173,-491),w(5761,-453,99,-728),w(5762,-468,92,-825),w(5763,-274,101,-754),
        w(5764,-621,158,-800),w(5765,-423,75,-569),w(5766,-358,75,-565),w(5767,-312,107,-576),
        w(5768,-370,119,-820),w(5769,-245,75,-727),w(5770,-350,140,-807),w(5771,-465,99,-742),
        w(5772,-220,110,-565),w(5773,-345,119,-796),w(5774,-291,96,-855),w(5775,-427,95,-870));
    private static HydraConfig cfg;
    private static List<Wormhole> matches = List.of();
    private static Wormhole target;
    private static Vec3 lastPlayerPos;
    private static Object levelKey;
    private static long lastDepartureAt;
    private static boolean initialized;

    private HydraWormholeFinder() {}

    public static void init(HydraConfig config) {
        cfg = config;
        if (initialized) return;
        initialized = true;
        ConstellationClient.tick().every(10, "hydra-wormhole-finder", HydraWormholeFinder::tick);
        ConstellationClient.instance().packets().register(packet -> {
            if (packet instanceof ClientboundSoundPacket sound) onSound(sound);
        });
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> reset());
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> reset());
    }

    private static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (!active() || mc.player == null || mc.level == null || !wearingFroggles()) { resetState(); return; }
        if (levelKey != mc.level) { reset(); levelKey = mc.level; }
        Vec3 playerPos = mc.player.position();
        if (target != null && target.pos.distanceToSqr(playerPos) <= 9) target = null;
        double radius = Math.clamp(cfg.wormholeScanRadius, 1, 12);
        List<Display.TextDisplay> arrows = new ArrayList<>();
        for (var entity : mc.level.entitiesForRendering()) {
            if (entity instanceof Display.TextDisplay display && display.distanceToSqr(mc.player) <= radius * radius)
                arrows.add(display);
        }
        Set<Wormhole> found = new LinkedHashSet<>();
        for (Display.TextDisplay arrow : arrows) {
            Wormhole match = matchArrow(arrow);
            if (match != null) found.add(match);
        }
        matches = List.copyOf(found);
        if (!matches.isEmpty()) target = matches.stream().min(Comparator.comparingDouble(w -> w.pos.distanceToSqr(playerPos))).orElse(null);
        else if (!arrows.isEmpty() && lastPlayerPos != null && horizontalDistanceSqr(playerPos, lastPlayerPos) <= .25) target = null;
        lastPlayerPos = playerPos;
    }

    private static Wormhole matchArrow(Display.TextDisplay arrow) {
        var state = arrow.renderState();
        if (state == null || state.transformation() == null) return null;
        Vector3f direction = new Vector3f(0, 1, 0);
        state.transformation().get(0f).leftRotation().transform(direction);
        direction.y = 0;
        if (direction.lengthSquared() < 1.0E-6f) return null;
        direction.normalize();
        Wormhole best = null;
        double bestScore = Math.clamp(cfg.wormholeDirectionPercent, 80, 100) / 100.0;
        for (Wormhole candidate : nodes()) {
            double dx = candidate.pos.x - arrow.getX(), dz = candidate.pos.z - arrow.getZ();
            double length = Math.sqrt(dx * dx + dz * dz);
            if (length < 1.0E-6) continue;
            double score = direction.x * dx / length + direction.z * dz / length;
            if (score >= bestScore) { bestScore = score; best = candidate; }
        }
        return best;
    }

    public static void draw(WorldRenderer.Ctx ctx) {
        Minecraft mc = Minecraft.getInstance();
        if (!active() || mc.player == null || !wearingFroggles() || (matches.isEmpty() && target == null)) return;
        double max = Math.clamp(cfg.wormholeWaypointRange, 16, 2000);
        Set<Wormhole> visible = new LinkedHashSet<>(matches);
        if (target != null) visible.add(target);
        for (Wormhole wormhole : visible) {
            boolean nearest = wormhole.equals(target);
            if ((!nearest && !cfg.wormholeShowAllMatches) || (nearest && !cfg.wormholeShowNearest)) continue;
            double distance = Math.sqrt(wormhole.pos.distanceToSqr(mc.player.position()));
            if (distance > max) continue;
            int color = nearest ? cfg.wormholeNearestColor : cfg.wormholeColor;
            Vec3 pos = wormhole.pos;
            if (cfg.wormholeShowBox) ctx.highlight(new AABB(pos.x-.5,pos.y,pos.z-.5,pos.x+.5,pos.y+1,pos.z+.5), color, cfg.wormholeThroughWalls);
            if (cfg.wormholeShowBeam) ctx.beam(pos.x,pos.y,pos.z,color,Math.clamp(cfg.wormholeBeamHeight,2,100),cfg.wormholeThroughWalls);
            if (cfg.wormholeShowLabel) ctx.label(pos.add(0,1.5,0),nearest?"Nearest Wormhole":"Wormhole",color,cfg.wormholeThroughWalls);
            if (cfg.wormholeShowDistance) ctx.label(pos.add(0,1.2,0),String.format(Locale.ROOT,"%.0fm",distance),color,cfg.wormholeThroughWalls);
        }
        if (target != null && cfg.wormholeShowNearest && cfg.wormholeShowGuidanceLine
            && target.pos.distanceToSqr(mc.player.position()) <= max * max)
            ctx.line(mc.player.position().add(0,.15,0),target.pos.add(0,.5,0),cfg.wormholeNearestColor,cfg.wormholeThroughWalls);
    }

    private static void onSound(ClientboundSoundPacket packet) {
        if (!active() || !cfg.wormholeDepartureAlert || !wearingFroggles() || (target == null && matches.isEmpty())) return;
        if (!packet.getSound().value().location().equals(SoundEvents.ENDERMAN_TELEPORT.location())
            || Math.abs(packet.getPitch() - .6984127f) > 1.0E-6f) return;
        long now = System.currentTimeMillis();
        if (now - lastDepartureAt < 3000) return;
        lastDepartureAt = now;
        matches = List.of(); target = null;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        if (cfg.wormholeDepartureTitle) { mc.gui.hud.resetTitleTimes(); mc.gui.hud.setTitle(Component.literal("Wormhole closed!").withColor(cfg.wormholeDepartureColor & 0xFFFFFF)); }
        if (cfg.wormholeDepartureChat) local("Wormhole closed!");
        if (cfg.wormholeDepartureSound) mc.player.playSound(SoundEvents.NOTE_BLOCK_PLING.value(), .8f, .6f);
    }

    private static boolean active() {
        if (cfg == null || !cfg.enabled || !cfg.wormholeLocator || !cfg.wormholeSuite || !ConstellationClient.loc().onHypixel()) return false;
        SkyblockArea area = ConstellationClient.loc().area();
        return area == SkyblockArea.LOTUS_ATOLL || area == SkyblockArea.CRIMSON_ISLE;
    }
    private static boolean wearingFroggles() {
        if (!cfg.wormholeRequireFroggles) return true;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return false;
        String id = LyraTooltips.marketId(mc.player.getItemBySlot(EquipmentSlot.HEAD));
        return (cfg.wormholeAllowGoldFroggles && id.equals("FROGGLES_GOLD"))
            || (cfg.wormholeAllowDiamondFroggles && id.equals("FROGGLES_DIAMOND"));
    }
    private static List<Wormhole> nodes() { return ConstellationClient.loc().area() == SkyblockArea.LOTUS_ATOLL ? LOTUS : CRIMSON; }
    private static Wormhole w(int id,double x,double y,double z) { return new Wormhole(id,new Vec3(x,y,z)); }
    private static double horizontalDistanceSqr(Vec3 a,Vec3 b) { double x=a.x-b.x,z=a.z-b.z;return x*x+z*z; }
    private static void reset() { levelKey=null;lastDepartureAt=0;resetState(); }
    private static void resetState() { matches=List.of();target=null;lastPlayerPos=null; }

    public static void registerCommands(CommandDispatcher<FabricClientCommandSource> d) {
        d.register(LiteralArgumentBuilder.<FabricClientCommandSource>literal("wormhole")
            .executes(c -> status()).then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("status").executes(c -> status()))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("clear").executes(c -> { resetState(); local("Finder state cleared."); return 1; }))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("range").then(RequiredArgumentBuilder.<FabricClientCommandSource,Integer>argument("blocks",IntegerArgumentType.integer(16,2000)).executes(c -> { cfg.wormholeWaypointRange=IntegerArgumentType.getInteger(c,"blocks");save();return status(); })))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("radius").then(RequiredArgumentBuilder.<FabricClientCommandSource,Integer>argument("blocks",IntegerArgumentType.integer(1,12)).executes(c -> { cfg.wormholeScanRadius=IntegerArgumentType.getInteger(c,"blocks");save();return status(); })))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("tolerance").then(RequiredArgumentBuilder.<FabricClientCommandSource,Integer>argument("percent",IntegerArgumentType.integer(80,100)).executes(c -> { cfg.wormholeDirectionPercent=IntegerArgumentType.getInteger(c,"percent");save();return status(); })))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("option").then(RequiredArgumentBuilder.<FabricClientCommandSource,String>argument("name",StringArgumentType.word()).then(RequiredArgumentBuilder.<FabricClientCommandSource,String>argument("state",StringArgumentType.word()).executes(c -> option(StringArgumentType.getString(c,"name"),StringArgumentType.getString(c,"state")))))));
    }
    private static int status() { local("Finder "+on(active())+", "+matches.size()+" matched, target "+(target==null?"none":"#"+target.id)+", range "+cfg.wormholeWaypointRange+", tolerance "+cfg.wormholeDirectionPercent+"%.");return 1; }
    private static int option(String name,String state) {
        Boolean value=switch(state.toLowerCase(Locale.ROOT)){case"on","true","yes","1"->true;case"off","false","no","0"->false;default->null;};
        if(value==null){local("State must be on or off.");return 0;}
        switch(name.toLowerCase(Locale.ROOT)){case"enabled"->cfg.wormholeSuite=value;case"froggles"->cfg.wormholeRequireFroggles=value;case"all"->cfg.wormholeShowAllMatches=value;case"nearest"->cfg.wormholeShowNearest=value;case"box"->cfg.wormholeShowBox=value;case"beam"->cfg.wormholeShowBeam=value;case"label"->cfg.wormholeShowLabel=value;case"distance"->cfg.wormholeShowDistance=value;case"line"->cfg.wormholeShowGuidanceLine=value;case"departure"->cfg.wormholeDepartureAlert=value;case"title"->cfg.wormholeDepartureTitle=value;case"chat"->cfg.wormholeDepartureChat=value;case"sound"->cfg.wormholeDepartureSound=value;default->{local("Unknown option. Use enabled, froggles, all, nearest, box, beam, label, distance, line, departure, title, chat, or sound.");return 0;}}
        save();return status();
    }
    private static String on(boolean value){return value?"enabled":"disabled";}
    private static void local(String text){Minecraft mc=Minecraft.getInstance();if(mc.player!=null)mc.player.sendSystemMessage(Component.literal("§5[Wormhole] §f"+text));}
    private static void save(){ConstellationClient.saveConfig();}
}
