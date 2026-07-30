package com.froggylord.constellation.constellation;

import com.froggylord.constellation.ConstellationClient;
import com.froggylord.constellation.config.AurigaConfig;
import com.froggylord.constellation.core.LocationManager.SkyblockArea;
import com.froggylord.constellation.render.WorldRenderer;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundLevelParticlesPacket;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

// ported from SkyHanni (LGPL-3.0-or-later): features/event/hoppity/HoppityEggLocations.kt
// ported from SkyHanni (LGPL-3.0-or-later): features/event/hoppity/HoppityEggLocator.kt
public final class AurigaHoppityWaypoints {
    private record Location(String id,Vec3 pos){}
    private static final Pattern FOUND=Pattern.compile("^HOPPITY'S HUNT You found a Chocolate [\\p{L}]+ Egg(?: .*)?!$",Pattern.CASE_INSENSITIVE);
    private static final Map<SkyblockArea,List<Location>> LOCATIONS=new EnumMap<>(SkyblockArea.class);
    private static final List<Vec3> PARTICLES=new ArrayList<>();
    private static AurigaConfig cfg;
    private static long locatorUsedAt;
    private static Location guess;
    private static SkyblockArea lastArea=SkyblockArea.UNKNOWN;
    private static boolean initialized;

    private AurigaHoppityWaypoints(){}

    public static void init(AurigaConfig config){
        cfg=config;normalize();load();
        if(initialized)return;initialized=true;
        UseItemCallback.EVENT.register((player,level,hand)->use(player.getItemInHand(hand)));
        UseBlockCallback.EVENT.register((player,level,hand,hit)->use(player.getItemInHand(hand)));
        ConstellationClient.instance().packets().register(packet->{if(packet instanceof ClientboundLevelParticlesPacket p)particle(p);});
        ClientReceiveMessageEvents.ALLOW_GAME.register((message,overlay)->{if(!overlay)chat(message.getString());return true;});
        ConstellationClient.tick().every(20,"auriga-hoppity-waypoint-scope",AurigaHoppityWaypoints::tick);
        ClientPlayConnectionEvents.JOIN.register((a,b,c)->resetTransient());
        ClientPlayConnectionEvents.DISCONNECT.register((a,b)->resetTransient());
    }

    private static void load(){
        LOCATIONS.clear();
        try(var stream=AurigaHoppityWaypoints.class.getResourceAsStream("/assets/constellation/hoppity/egg_locations.json")){
            if(stream==null)throw new IllegalStateException("missing Hoppity egg locations");
            JsonObject root=JsonParser.parseReader(new InputStreamReader(stream,StandardCharsets.UTF_8)).getAsJsonObject().getAsJsonObject("apiEggLocations");
            for(var island:root.entrySet()){
                SkyblockArea area=area(island.getKey());if(area==null)continue;ArrayList<Location> values=new ArrayList<>();
                for(var entry:island.getValue().getAsJsonObject().entrySet()){String[] parts=entry.getValue().getAsString().split(":");if(parts.length!=3)continue;values.add(new Location(entry.getKey(),new Vec3(Double.parseDouble(parts[0]),Double.parseDouble(parts[1]),Double.parseDouble(parts[2]))));}
                LOCATIONS.put(area,List.copyOf(values));
            }
            ConstellationClient.LOGGER.info("loaded {} Hoppity egg locations across {} islands",LOCATIONS.values().stream().mapToInt(List::size).sum(),LOCATIONS.size());
        }catch(Exception e){ConstellationClient.LOGGER.error("failed to load Hoppity egg locations",e);}
    }

    private static InteractionResult use(ItemStack stack){
        if(!active()||!cfg.hoppityEggLocatorSolver||!"EGGLOCATOR".equals(LyraTooltips.marketId(stack)))return InteractionResult.PASS;
        locatorUsedAt=System.currentTimeMillis();PARTICLES.clear();guess=null;return InteractionResult.PASS;
    }

    private static void particle(ClientboundLevelParticlesPacket packet){
        if(!active()||!cfg.hoppityEggLocatorSolver||packet.getParticle().getType()!=ParticleTypes.HAPPY_VILLAGER||packet.getCount()!=1||packet.getMaxSpeed()!=0f)return;
        long now=System.currentTimeMillis();if(now-locatorUsedAt>Math.clamp(cfg.hoppityEggLocatorTimeoutSeconds,1,10)*1000L)return;
        Vec3 point=new Vec3(packet.getX(),packet.getY(),packet.getZ());
        if(!PARTICLES.isEmpty()){double distance=point.distanceTo(PARTICLES.getLast());if(distance==0||distance>Math.clamp(cfg.hoppityEggLocatorParticleGapTenths,5,50)/10.0)return;}
        PARTICLES.add(point);if(PARTICLES.size()<4)return;
        Vec3 solved=solve(PARTICLES);if(solved==null)return;
        guess=locations().stream().min(Comparator.comparingDouble(value->value.pos.distanceToSqr(solved))).orElse(null);
    }

    // ported from SkyHanni (LGPL-3.0-or-later): utils/PolynomialFitter.kt ParticlePathBezierFitter.solve
    private static Vec3 solve(List<Vec3> points){
        double[][] coefficient=new double[3][];for(int axis=0;axis<3;axis++){double[] y=new double[points.size()];for(int i=0;i<points.size();i++)y[i]=axis==0?points.get(i).x:axis==1?points.get(i).y:points.get(i).z;coefficient[axis]=fit(y,3);if(coefficient[axis]==null)return null;}
        Vec3 derivative=new Vec3(coefficient[0][1],coefficient[1][1],coefficient[2][1]);double length=derivative.length();if(length<1e-6)return null;
        double pitch=Math.asin(-derivative.y/length);double weight=Math.sqrt(Math.max(0,24*Math.sin(pitch-Math.PI)+25));double t=3*weight/length;
        return new Vec3(at(coefficient[0],t),at(coefficient[1],t),at(coefficient[2],t));
    }
    private static double[] fit(double[] y,int degree){int n=degree+1;double[][] a=new double[n][n+1];for(int r=0;r<n;r++){for(int c=0;c<n;c++){double sum=0;for(int i=0;i<y.length;i++)sum+=Math.pow(i,r+c);a[r][c]=sum;}double sum=0;for(int i=0;i<y.length;i++)sum+=y[i]*Math.pow(i,r);a[r][n]=sum;}for(int p=0;p<n;p++){int best=p;for(int r=p+1;r<n;r++)if(Math.abs(a[r][p])>Math.abs(a[best][p]))best=r;double[] swap=a[p];a[p]=a[best];a[best]=swap;if(Math.abs(a[p][p])<1e-9)return null;double div=a[p][p];for(int c=p;c<=n;c++)a[p][c]/=div;for(int r=0;r<n;r++)if(r!=p){double factor=a[r][p];for(int c=p;c<=n;c++)a[r][c]-=factor*a[p][c];}}double[] out=new double[n];for(int i=0;i<n;i++)out[i]=a[i][n];return out;}
    private static double at(double[] coefficient,double t){double value=0;for(int i=coefficient.length-1;i>=0;i--)value=value*t+coefficient[i];return value;}

    private static void chat(String formatted){
        if(!active()||!FOUND.matcher(clean(formatted)).matches())return;Minecraft mc=Minecraft.getInstance();if(mc.player==null)return;
        Location nearest=locations().stream().min(Comparator.comparingDouble(value->value.pos.distanceToSqr(mc.player.position()))).orElse(null);
        if(nearest==null||nearest.pos.distanceToSqr(mc.player.position())>Math.pow(Math.clamp(cfg.hoppityEggLocationClaimRadius,3,30),2)){local("No known egg location was close enough to mark collected.");return;}
        cfg.hoppityCollectedEggLocations.put(key(nearest),true);if(cfg.hoppityEggLocationsPersistProfiles)ConstellationClient.saveConfig();if(guess==nearest)resetLocator();
    }

    private static void tick(){SkyblockArea area=ConstellationClient.loc().area();if(area!=lastArea){lastArea=area;resetLocator();}if(locatorUsedAt>0&&System.currentTimeMillis()-locatorUsedAt>Math.clamp(cfg.hoppityEggLocatorTimeoutSeconds,1,10)*1000L)PARTICLES.clear();}

    public static void draw(WorldRenderer.Ctx ctx){
        if(!active())return;Minecraft mc=Minecraft.getInstance();if(mc.player==null)return;List<Location> locations=renderLocations(mc);double range=Math.clamp(cfg.hoppityEggWaypointRange,16,5000);for(Location location:locations){double distance=Math.sqrt(location.pos.distanceToSqr(mc.player.position()));if(distance>range)continue;boolean collected=collected(location);int color=collected?cfg.hoppityEggWaypointCollectedColor:cfg.hoppityEggWaypointColor;Vec3 center=location.pos.add(.5,.5,.5);if(cfg.hoppityEggWaypointsBox)ctx.highlight(new AABB(location.pos.x,location.pos.y,location.pos.z,location.pos.x+1,location.pos.y+1,location.pos.z+1),color,cfg.hoppityEggWaypointsThroughWalls);if(cfg.hoppityEggWaypointsBeam)ctx.beam(center.x,location.pos.y+1,center.z,color,Math.clamp(cfg.hoppityEggWaypointBeamHeight,1,64),cfg.hoppityEggWaypointsThroughWalls);if(cfg.hoppityEggWaypointsLabel){String label=guess==location?"Egg Guess":cfg.hoppityEggWaypointsInternalNames?human(location.id):"Egg";if(collected)label+=" (Collected)";if(cfg.hoppityEggWaypointsDistance)label+=" "+Math.round(distance)+"m";ctx.label(center.add(0,1.25,0),label,color,cfg.hoppityEggWaypointsThroughWalls);}if(cfg.hoppityEggWaypointsLine&&(guess==location||cfg.hoppityEggWaypointsOnlyNearest))ctx.line(mc.player.getEyePosition(),center,color,cfg.hoppityEggWaypointsThroughWalls,2);}
    }

    private static List<Location> renderLocations(Minecraft mc){
        List<Location> source=locations();if(source.isEmpty())return List.of();
        if(guess!=null&&cfg.hoppityEggLocatorOnlyGuess)return List.of(guess);
        if(AurigaUnclaimedEggs.eggs().stream().noneMatch(AurigaUnclaimedEggs.Egg::ready))return List.of();
        ArrayList<Location> out=new ArrayList<>();for(Location location:source){if(collected(location)&&cfg.hoppityEggWaypointsHideCollected&&!cfg.hoppityEggWaypointsHighlightCollected)continue;out.add(location);}
        if(cfg.hoppityEggWaypointsOnlyNearest&&!out.isEmpty())return List.of(out.stream().min(Comparator.comparingDouble(value->value.pos.distanceToSqr(mc.player.position()))).orElseThrow());
        return cfg.hoppityEggWaypointsShowAll?List.copyOf(out):List.of();
    }

    public static void registerCommands(CommandDispatcher<FabricClientCommandSource> dispatcher){
        dispatcher.register(LiteralArgumentBuilder.<FabricClientCommandSource>literal("hoppitywaypoints")
            .executes(context->status())
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("status").executes(context->status()))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("resetlocator").executes(context->{resetLocator();local("Locator guess cleared.");return 1;}))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("clear")
                .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("confirm").executes(context->clear())))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("range")
                .then(RequiredArgumentBuilder.<FabricClientCommandSource,Integer>argument("blocks",IntegerArgumentType.integer(16,5000)).executes(context->range(IntegerArgumentType.getInteger(context,"blocks")))))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("beamheight")
                .then(RequiredArgumentBuilder.<FabricClientCommandSource,Integer>argument("blocks",IntegerArgumentType.integer(1,64)).executes(context->number("beam",IntegerArgumentType.getInteger(context,"blocks")))))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("particlegap")
                .then(RequiredArgumentBuilder.<FabricClientCommandSource,Integer>argument("tenths",IntegerArgumentType.integer(5,50)).executes(context->number("gap",IntegerArgumentType.getInteger(context,"tenths")))))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("timeout")
                .then(RequiredArgumentBuilder.<FabricClientCommandSource,Integer>argument("seconds",IntegerArgumentType.integer(1,10)).executes(context->number("timeout",IntegerArgumentType.getInteger(context,"seconds")))))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("claimradius")
                .then(RequiredArgumentBuilder.<FabricClientCommandSource,Integer>argument("blocks",IntegerArgumentType.integer(3,30)).executes(context->number("claim",IntegerArgumentType.getInteger(context,"blocks")))))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("color")
                .then(RequiredArgumentBuilder.<FabricClientCommandSource,String>argument("target",StringArgumentType.word())
                    .then(RequiredArgumentBuilder.<FabricClientCommandSource,String>argument("argb",StringArgumentType.word()).executes(context->color(StringArgumentType.getString(context,"target"),StringArgumentType.getString(context,"argb"))))))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("option")
                .then(RequiredArgumentBuilder.<FabricClientCommandSource,String>argument("name",StringArgumentType.word())
                    .then(RequiredArgumentBuilder.<FabricClientCommandSource,String>argument("state",StringArgumentType.word()).executes(context->option(StringArgumentType.getString(context,"name"),StringArgumentType.getString(context,"state")))))));
    }
    private static int status(){long collected=locations().stream().filter(AurigaHoppityWaypoints::collected).count();local(locations().size()+" locations on "+lastArea+", "+collected+" collected, locator "+(guess==null?"idle":"solved")+".");return 1;}
    private static int clear(){String prefix=profile()+"|";cfg.hoppityCollectedEggLocations.keySet().removeIf(value->value.startsWith(prefix));ConstellationClient.saveConfig();local("Current profile collected-location cache cleared.");return 1;}
    private static int range(int value){cfg.hoppityEggWaypointRange=value;ConstellationClient.saveConfig();return status();}
    private static int number(String target,int value){switch(target){case"beam"->cfg.hoppityEggWaypointBeamHeight=value;case"gap"->cfg.hoppityEggLocatorParticleGapTenths=value;case"timeout"->cfg.hoppityEggLocatorTimeoutSeconds=value;case"claim"->cfg.hoppityEggLocationClaimRadius=value;default->{return 0;}}ConstellationClient.saveConfig();return status();}
    private static int color(String target,String raw){try{String value=raw.replaceFirst("^(?:#|0[xX])","");long parsed=Long.parseUnsignedLong(value,16);if(value.length()<=6)parsed|=0xFF000000L;switch(target.toLowerCase(Locale.ROOT)){case"egg","waypoint"->cfg.hoppityEggWaypointColor=(int)parsed;case"collected","duplicate"->cfg.hoppityEggWaypointCollectedColor=(int)parsed;default->{local("Color target must be egg or collected.");return 0;}}ConstellationClient.saveConfig();return status();}catch(Exception ignored){local("Color must be ARGB hex.");return 0;}}
    private static int option(String name,String raw){Boolean value=bool(raw);if(value==null){local("State must be on or off.");return 0;}switch(name.toLowerCase(Locale.ROOT)){case"enabled"->cfg.hoppityEggWaypoints=value;case"all"->cfg.hoppityEggWaypointsShowAll=value;case"hidecollected"->cfg.hoppityEggWaypointsHideCollected=value;case"collected"->cfg.hoppityEggWaypointsHighlightCollected=value;case"nearest"->cfg.hoppityEggWaypointsOnlyNearest=value;case"box"->cfg.hoppityEggWaypointsBox=value;case"beam"->cfg.hoppityEggWaypointsBeam=value;case"label"->cfg.hoppityEggWaypointsLabel=value;case"names"->cfg.hoppityEggWaypointsInternalNames=value;case"distance"->cfg.hoppityEggWaypointsDistance=value;case"line"->cfg.hoppityEggWaypointsLine=value;case"walls"->cfg.hoppityEggWaypointsThroughWalls=value;case"locator"->cfg.hoppityEggLocatorSolver=value;case"guessonly"->cfg.hoppityEggLocatorOnlyGuess=value;case"persist"->cfg.hoppityEggLocationsPersistProfiles=value;default->{local("Unknown Hoppity waypoint option.");return 0;}}ConstellationClient.saveConfig();return status();}

    private static List<Location> locations(){return LOCATIONS.getOrDefault(ConstellationClient.loc().area(),List.of());}
    private static boolean collected(Location location){return cfg.hoppityCollectedEggLocations.getOrDefault(key(location),false);}
    private static String key(Location location){return profile()+"|"+ConstellationClient.loc().area().name()+"|"+location.id;}
    private static String profile(){String value=LyraStorageValue.currentProfileKey();return value==null||value.isBlank()?"unknown":value.toLowerCase(Locale.ROOT);}
    private static void resetTransient(){lastArea=SkyblockArea.UNKNOWN;resetLocator();}
    private static void resetLocator(){locatorUsedAt=0;PARTICLES.clear();guess=null;}
    private static String human(String value){StringBuilder out=new StringBuilder();for(String word:value.split("_")){if(!out.isEmpty())out.append(' ');out.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));}return out.toString();}
    private static String clean(String value){String clean=ChatFormatting.stripFormatting(value);return clean==null?"":clean.replaceAll("\\s+"," ").strip();}
    private static Boolean bool(String value){return switch(value.toLowerCase(Locale.ROOT)){case"on","true","yes","1"->true;case"off","false","no","0"->false;default->null;};}
    private static boolean active(){return cfg!=null&&cfg.enabled&&cfg.hoppityEggWaypoints&&ConstellationClient.loc().onHypixel()&&isSpring()&&!locations().isEmpty();}
    private static boolean isSpring(){final long epoch=1_559_829_300_000L,year=124L*60*60*1000;return Math.floorMod(System.currentTimeMillis()-epoch,year)<year/4;}
    private static SkyblockArea area(String value){return switch(value){case"THE_PARK"->SkyblockArea.PARK;case"GOLD_MINES"->SkyblockArea.GOLD_MINE;case"THE_FARMING_ISLANDS"->SkyblockArea.FARMING_ISLANDS;default->tryArea(value);};}
    private static SkyblockArea tryArea(String value){try{return SkyblockArea.valueOf(value);}catch(Exception ignored){return null;}}
    private static void normalize(){if(cfg.hoppityCollectedEggLocations==null)cfg.hoppityCollectedEggLocations=new LinkedHashMap<>();cfg.hoppityEggWaypointRange=Math.clamp(cfg.hoppityEggWaypointRange,16,5000);cfg.hoppityEggLocatorParticleGapTenths=Math.clamp(cfg.hoppityEggLocatorParticleGapTenths,5,50);cfg.hoppityEggLocatorTimeoutSeconds=Math.clamp(cfg.hoppityEggLocatorTimeoutSeconds,1,10);cfg.hoppityEggLocationClaimRadius=Math.clamp(cfg.hoppityEggLocationClaimRadius,3,30);}
    private static void local(String text){Minecraft mc=Minecraft.getInstance();if(mc.player!=null)mc.player.sendSystemMessage(Component.literal("\u00a7a[Hoppity Waypoints] \u00a7f"+text));}
}
