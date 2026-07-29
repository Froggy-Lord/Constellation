package com.froggylord.constellation.constellation;

import com.froggylord.constellation.ConstellationClient;
import com.froggylord.constellation.config.HerculesConfig;
import com.froggylord.constellation.core.LocationManager;
import com.froggylord.constellation.render.WorldRenderer;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundLevelParticlesPacket;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

// ported from SkyHanni (LGPL-3.0-or-later): features/garden/pests/PestParticleWaypoint.kt
// polynomial fitter ported from SkyHanni (LGPL-3.0-or-later): utils/PolynomialFitter.kt
// matrix solve ported from SkyHanni (LGPL-3.0-or-later): utils/Matrix.kt
// pitch weight ported from SkyHanni (LGPL-3.0-or-later): utils/LocationUtils.kt
public final class HerculesPestWaypoint {
    private static final List<Vec3> POINTS = new ArrayList<>();
    private static HerculesConfig cfg;
    private static boolean initialized, attackWasDown, wasActive, plotMiddle;
    private static long usedAt;
    private static Vec3 target;
    private static Object levelIdentity;
    private static int lastAlive;

    private HerculesPestWaypoint() {}

    public static void init(HerculesConfig config) {
        cfg = config;
        if (initialized) return;
        initialized = true;
        ConstellationClient.tick().every(1, "hercules-pest-waypoint", HerculesPestWaypoint::tick);
        ClientPlayConnectionEvents.JOIN.register((a,b,c) -> reset());
        ClientPlayConnectionEvents.DISCONNECT.register((a,b) -> reset());
    }

    private static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != levelIdentity) { levelIdentity = mc.level; resetSession(); }
        boolean active = active();
        if (!active) {
            if (wasActive) resetSession();
            wasActive = false;
            attackWasDown = mc.options.keyAttack.isDown();
            return;
        }
        wasActive = true;
        boolean attack = mc.options.keyAttack.isDown();
        if (attack && !attackWasDown && mc.gui.screen() == null && mc.player != null && !mc.player.isShiftKeyDown() && vacuumInHand()) begin();
        attackWasDown = attack;
        long now = System.currentTimeMillis();
        if (usedAt > 0 && now - usedAt > Math.clamp(cfg.pestWaypointShowSeconds, 1, 120) * 1000L) clearPath();
        if (target != null && cfg.pestWaypointClearOnArrival && now - usedAt >= 1000L && horizontalDistance(target) <= Math.clamp(cfg.pestWaypointArrivalRange, 1, 32)) clearPath();
        var state = HerculesPests.state();
        int alive = state == null ? 0 : state.alive();
        if (lastAlive > 0 && alive == 0) clearPath();
        lastAlive = alive;
    }

    private static void begin() {
        clearPath();
        usedAt = System.currentTimeMillis();
    }

    public static boolean onParticle(ClientboundLevelParticlesPacket packet) {
        if (!active()) return false;
        boolean firework = packet.getParticle().getType() == ParticleTypes.FIREWORK;
        if (firework) return cfg.pestWaypointHideParticles && cfg.pestWaypointHideFireworkParticles;
        long now = System.currentTimeMillis();
        if (usedAt == 0 || now - usedAt > Math.clamp(cfg.pestWaypointActivationSeconds, 1, 15) * 1000L) return false;
        boolean enchant = packet.getParticle().getType() == ParticleTypes.ENCHANT
            && packet.getCount() == 10 && packet.getMaxSpeed() == -2f
            && packet.getXDist() == 0f && packet.getYDist() == 0f && packet.getZDist() == 0f;
        if (enchant) return cfg.pestWaypointHideParticles && cfg.pestWaypointHideEnchantParticles;
        boolean angry = packet.getParticle().getType() == ParticleTypes.ANGRY_VILLAGER
            && packet.getCount() == 1 && packet.getMaxSpeed() == 0f
            && packet.getXDist() == 0f && packet.getYDist() == 0f && packet.getZDist() == 0f;
        if (!angry) return false;
        addPoint(new Vec3(packet.getX(), packet.getY(), packet.getZ()), now);
        return cfg.pestWaypointHideParticles && cfg.pestWaypointHidePathParticles;
    }

    private static void addPoint(Vec3 point, long now) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || !finite(point)) return;
        if (POINTS.isEmpty()) {
            if (point.distanceTo(mc.player.position()) > Math.clamp(cfg.pestWaypointStartRange, 1, 16)) return;
            POINTS.add(point);
            return;
        }
        double gap = point.distanceTo(POINTS.getLast());
        if (gap == 0 || gap > Math.clamp(cfg.pestWaypointParticleGapTenths, 5, 100) / 10.0) return;
        POINTS.add(point);
        if (POINTS.size() < 4) return;
        Vec3 solved = solve();
        if (solved == null || !finite(solved) || solved.y < -64 || solved.y > 400) return;
        double range = Math.clamp(cfg.pestWaypointRenderRange, 32, 1024);
        if (solved.distanceTo(mc.player.position()) > range) return;
        target = solved;
        plotMiddle = isPlotMiddle(solved);
    }

    // ported from SkyHanni (LGPL-3.0-or-later): utils/PolynomialFitter.kt
    private static Vec3 solve() {
        double[][] coefficients = new double[3][];
        for (int axis = 0; axis < 3; axis++) {
            coefficients[axis] = fit(axis);
            if (coefficients[axis] == null) return null;
        }
        Vec3 derivative = new Vec3(coefficients[0][1], coefficients[1][1], coefficients[2][1]);
        double length = derivative.length();
        if (!Double.isFinite(length) || length < 1e-6) return null;
        double control = Math.sqrt(24 * Math.sin(pitch(derivative) - Math.PI) + 25);
        double t = 3 * control / length;
        return new Vec3(at(coefficients[0], t), at(coefficients[1], t), at(coefficients[2], t));
    }

    // ported from SkyHanni (LGPL-3.0-or-later): utils/Matrix.kt
    private static double[] fit(int axis) {
        double[][] a = new double[4][5];
        for (int row = 0; row < 4; row++) for (int col = 0; col < 4; col++) {
            double sum = 0;
            for (int i = 0; i < POINTS.size(); i++) sum += Math.pow(i, row + col);
            a[row][col] = sum;
        }
        for (int row = 0; row < 4; row++) {
            double sum = 0;
            for (int i = 0; i < POINTS.size(); i++) {
                Vec3 p = POINTS.get(i);
                double value = axis == 0 ? p.x : axis == 1 ? p.y : p.z;
                sum += Math.pow(i, row) * value;
            }
            a[row][4] = sum;
        }
        for (int col = 0; col < 4; col++) {
            int pivot = col;
            for (int row = col + 1; row < 4; row++) if (Math.abs(a[row][col]) > Math.abs(a[pivot][col])) pivot = row;
            double[] swap = a[col]; a[col] = a[pivot]; a[pivot] = swap;
            if (Math.abs(a[col][col]) < 1e-10) return null;
            double divisor = a[col][col];
            for (int j = col; j < 5; j++) a[col][j] /= divisor;
            for (int row = 0; row < 4; row++) if (row != col) {
                double factor = a[row][col];
                for (int j = col; j < 5; j++) a[row][j] -= factor * a[col][j];
            }
        }
        return new double[]{a[0][4], a[1][4], a[2][4], a[3][4]};
    }

    private static double at(double[] coefficients, double t) {
        return ((coefficients[3] * t + coefficients[2]) * t + coefficients[1]) * t + coefficients[0];
    }

    // ported from SkyHanni (LGPL-3.0-or-later): utils/LocationUtils.kt
    private static double pitch(Vec3 derivative) {
        double expected = -Math.atan2(derivative.y, Math.sqrt(derivative.x * derivative.x + derivative.z * derivative.z));
        double guess = expected, min = -Math.PI / 2, max = Math.PI / 2;
        for (int i = 0; i < 100; i++) {
            double result = Math.atan2(Math.sin(guess) - .75, Math.cos(guess));
            if (result < expected) { min = guess; guess = (min + max) / 2; }
            else { max = guess; guess = (min + max) / 2; }
        }
        return guess;
    }

    public static void draw(WorldRenderer.Ctx ctx) {
        if (!active() || target == null) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || System.currentTimeMillis() - usedAt > Math.clamp(cfg.pestWaypointShowSeconds, 1, 120) * 1000L) return;
        double distance = target.distanceTo(mc.player.getEyePosition());
        if (distance > Math.clamp(cfg.pestWaypointRenderRange, 32, 1024)) return;
        int color = plotMiddle && cfg.pestWaypointDifferentiatePlotMiddle ? cfg.pestWaypointPlotMiddleColor : cfg.pestWaypointColor;
        double half = Math.clamp(cfg.pestWaypointBoxSizeTenths, 2, 30) / 20.0;
        if (cfg.pestWaypointBox) ctx.highlight(new AABB(target.x-half,target.y-half,target.z-half,target.x+half,target.y+half,target.z+half), color, cfg.pestWaypointThroughWalls);
        if (cfg.pestWaypointBeam) ctx.beam(target.x,target.y,target.z,color,Math.clamp(cfg.pestWaypointBeamHeight,1,64),cfg.pestWaypointThroughWalls);
        if (cfg.pestWaypointLine) ctx.line(mc.player.getEyePosition(),target.add(.5,.5,.5),color,cfg.pestWaypointThroughWalls);
        if (cfg.pestWaypointLabel) {
            String suffix = plotMiddle && cfg.pestWaypointDifferentiatePlotMiddle ? " (plot middle)" : "";
            String label = cfg.pestWaypointLabelTemplate.replace("{distance}",cfg.pestWaypointDistance?Math.round(distance)+"m":"").replace("{plot-middle}",suffix).trim();
            if (!label.isEmpty()) ctx.label(target.add(0,.8,0),label+(!cfg.pestWaypointLabelTemplate.contains("{plot-middle}")?suffix:""),color,cfg.pestWaypointThroughWalls);
        }
    }

    public static void registerCommands(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        dispatcher.register(LiteralArgumentBuilder.<FabricClientCommandSource>literal("pestwaypoint")
            .executes(c -> status())
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("status").executes(c -> status()))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("clear").executes(c -> { clearPath(); local("Pest waypoint cleared."); return 1; }))
            .then(number("duration",1,120)).then(number("activation",1,15)).then(number("range",32,1024))
            .then(number("gap",5,100)).then(number("arrival",1,32)).then(number("beamheight",1,64)).then(number("boxsize",2,30))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("label").then(RequiredArgumentBuilder.<FabricClientCommandSource,String>argument("text",StringArgumentType.greedyString()).executes(c -> label(StringArgumentType.getString(c,"text")))))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("color").then(RequiredArgumentBuilder.<FabricClientCommandSource,String>argument("target",StringArgumentType.word()).then(RequiredArgumentBuilder.<FabricClientCommandSource,String>argument("argb",StringArgumentType.word()).executes(c -> color(StringArgumentType.getString(c,"target"),StringArgumentType.getString(c,"argb"))))))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("option").then(RequiredArgumentBuilder.<FabricClientCommandSource,String>argument("name",StringArgumentType.word()).then(RequiredArgumentBuilder.<FabricClientCommandSource,String>argument("state",StringArgumentType.word()).executes(c -> option(StringArgumentType.getString(c,"name"),StringArgumentType.getString(c,"state")))))));
    }

    private static LiteralArgumentBuilder<FabricClientCommandSource> number(String name,int min,int max) {
        return LiteralArgumentBuilder.<FabricClientCommandSource>literal(name).then(RequiredArgumentBuilder.<FabricClientCommandSource,Integer>argument("value",IntegerArgumentType.integer(min,max)).executes(c -> setNumber(name,IntegerArgumentType.getInteger(c,"value"))));
    }
    private static int setNumber(String name,int value) { switch(name){case"duration"->cfg.pestWaypointShowSeconds=value;case"activation"->cfg.pestWaypointActivationSeconds=value;case"range"->cfg.pestWaypointRenderRange=value;case"gap"->cfg.pestWaypointParticleGapTenths=value;case"arrival"->cfg.pestWaypointArrivalRange=value;case"beamheight"->cfg.pestWaypointBeamHeight=value;case"boxsize"->cfg.pestWaypointBoxSizeTenths=value;} save(); return status(); }
    private static int label(String text){String value=text.replace('\n',' ').replace('\r',' ').trim();if(value.isEmpty()||value.length()>120){local("Label must contain 1-120 characters. Variables: {distance}, {plot-middle}.");return 0;}cfg.pestWaypointLabelTemplate=value;save();return status();}
    private static int color(String targetName,String raw){try{String value=raw.startsWith("#")?raw.substring(1):raw.startsWith("0x")?raw.substring(2):raw;long parsed=Long.parseUnsignedLong(value,16);int color=value.length()<=6?(int)(0xFF000000L|parsed):(int)parsed;if(targetName.equalsIgnoreCase("target"))cfg.pestWaypointColor=color;else if(targetName.equalsIgnoreCase("middle"))cfg.pestWaypointPlotMiddleColor=color;else{local("Color target must be target or middle.");return 0;}save();return status();}catch(NumberFormatException ignored){local("Color must be RRGGBB or AARRGGBB.");return 0;}}
    private static int option(String name,String state){Boolean value=parse(state);if(value==null){local("State must be on or off.");return 0;}switch(name.toLowerCase(Locale.ROOT)){case"enabled"->cfg.pestWaypointEnabled=value;case"hideparticles"->cfg.pestWaypointHideParticles=value;case"firework"->cfg.pestWaypointHideFireworkParticles=value;case"enchant"->cfg.pestWaypointHideEnchantParticles=value;case"pathparticles"->cfg.pestWaypointHidePathParticles=value;case"box"->cfg.pestWaypointBox=value;case"beam"->cfg.pestWaypointBeam=value;case"line"->cfg.pestWaypointLine=value;case"label"->cfg.pestWaypointLabel=value;case"distance"->cfg.pestWaypointDistance=value;case"middle"->cfg.pestWaypointDifferentiatePlotMiddle=value;case"throughwalls"->cfg.pestWaypointThroughWalls=value;case"arrival"->cfg.pestWaypointClearOnArrival=value;default->{local("Unknown waypoint option.");return 0;}}save();return status();}
    private static int status(){long age=usedAt==0?0:(System.currentTimeMillis()-usedAt)/1000;local("Waypoint "+on(cfg.pestWaypointEnabled)+", points "+POINTS.size()+", target "+(target==null?"none":Math.round(horizontalDistance(target))+"m")+", use age "+age+"s.");return 1;}
    private static boolean vacuumInHand(){Minecraft mc=Minecraft.getInstance();if(mc.player==null)return false;String id=LyraTooltips.marketId(mc.player.getMainHandItem());return id!=null&&id.toUpperCase(Locale.ROOT).contains("VACUUM");}
    private static boolean isPlotMiddle(Vec3 point){if(point.x < -240||point.x>=240||point.z < -240||point.z>=240)return false;double centerX=Math.rint(point.x/96.0)*96,centerZ=Math.rint(point.z/96.0)*96;return Math.ceil(point.x)==centerX&&Math.ceil(point.z)==centerZ;}
    private static double horizontalDistance(Vec3 point){Minecraft mc=Minecraft.getInstance();if(mc.player==null)return Double.MAX_VALUE;double dx=point.x-mc.player.getX(),dz=point.z-mc.player.getZ();return Math.sqrt(dx*dx+dz*dz);}
    private static boolean finite(Vec3 point){return Double.isFinite(point.x)&&Double.isFinite(point.y)&&Double.isFinite(point.z);}
    private static boolean active(){return cfg!=null&&cfg.enabled&&cfg.pestCore&&cfg.pestWaypointEnabled&&ConstellationClient.loc().area()== LocationManager.SkyblockArea.GARDEN;}
    private static Boolean parse(String value){return switch(value.toLowerCase(Locale.ROOT)){case"on","true","yes","1"->true;case"off","false","no","0"->false;default->null;};}
    private static String on(boolean value){return value?"on":"off";}
    private static void local(String text){Minecraft mc=Minecraft.getInstance();if(mc.player!=null)mc.player.sendSystemMessage(Component.literal("\u00a72[Pest Waypoint] \u00a7f"+text));}
    private static void save(){ConstellationClient.saveConfig();}
    private static void clearPath(){POINTS.clear();target=null;usedAt=0;plotMiddle=false;}
    private static void reset(){levelIdentity=null;wasActive=false;attackWasDown=false;lastAlive=0;resetSession();}
    private static void resetSession(){clearPath();lastAlive=0;}
}
