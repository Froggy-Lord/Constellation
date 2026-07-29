package com.froggylord.constellation.constellation;

import com.froggylord.constellation.ConstellationClient;
import com.froggylord.constellation.config.AquilaConfig;
import com.froggylord.constellation.core.LocationManager.SkyblockArea;
import com.froggylord.constellation.render.WorldRenderer;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

// ported from SkyHanni (LGPL-3.0-or-later): features/mining/OrderedWaypoints.kt
// ported from SkyHanni (LGPL-3.0-or-later): config/features/mining/orderedwaypoints/OrderedWaypointsConfig.kt
// ported from SkyHanni (LGPL-3.0-or-later): data/model/waypoints/ColeweightWaypointFormat.kt
// ported from SkyHanni (LGPL-3.0-or-later): config/storage/OrderedWaypointsRoutes.kt
public final class AquilaOrderedWaypoints {
    public record Waypoint(int x,int y,int z,String label){BlockPos pos(){return new BlockPos(x,y,z);}Waypoint withLabel(String value){return new Waypoint(x,y,z,value);}}
    public record State(String route,int current,int total,Waypoint waypoint,Waypoint next,double distance,boolean dirty,boolean setup){}
    private static final class ProfileRoutes {Map<String,List<Waypoint>> routes=new LinkedHashMap<>();String activeRoute="";}
    private static final class Store {Map<String,ProfileRoutes> profiles=new HashMap<>();}
    private static final Gson GSON=new GsonBuilder().setPrettyPrinting().create();
    private static final Path FILE=FabricLoader.getInstance().getConfigDir().resolve("constellation-ordered-routes.json");
    private static final List<Waypoint> LOADED=new ArrayList<>();
    private static final List<Integer> RENDER=new ArrayList<>();
    private static final Map<String,ProfileRoutes> PROFILES=new HashMap<>();
    private static AquilaConfig cfg;
    private static String profileKey="",routeName="";
    private static int current,lastCloser;
    private static boolean dirty,initialized;
    private static Object levelIdentity;
    private static SkyblockArea lastArea=SkyblockArea.UNKNOWN;
    private static String detectedShaft="";

    private AquilaOrderedWaypoints(){}
    public static void init(AquilaConfig config){cfg=config;if(!initialized){initialized=true;loadStore();ConstellationClient.tick().every(1,"aquila-ordered-waypoints",AquilaOrderedWaypoints::tick);ClientPlayConnectionEvents.JOIN.register((a,b,c)->connectionReset());ClientPlayConnectionEvents.DISCONNECT.register((a,b)->connectionReset());}}

    private static void tick(){
        Minecraft mc=Minecraft.getInstance();String profile=profile();if(!profile.equals(profileKey))switchProfile(profile);
        SkyblockArea area=ConstellationClient.loc().area();if(mc.level!=levelIdentity){levelIdentity=mc.level;current=0;lastCloser=0;RENDER.clear();if(cfg.orderedWaypointsAutoUnload&&!LOADED.isEmpty())autoUnload("world changed");}
        if(lastArea!=area){if(lastArea==SkyblockArea.GLACITE_MINESHAFT&&cfg.orderedWaypointsAutoUnloadMineshaft)autoUnload("left the Mineshaft");else if(cfg.orderedWaypointsAutoUnload&&!LOADED.isEmpty())autoUnload("area changed");lastArea=area;if(area!=SkyblockArea.GLACITE_MINESHAFT)detectedShaft="";}
        if(area==SkyblockArea.GLACITE_MINESHAFT&&cfg.orderedWaypointsAutoLoadMineshaft)detectShaft();
        if(!active()||mc.player==null||LOADED.isEmpty()){RENDER.clear();return;}decide(mc.player.position());
    }

    private static void detectShaft(){
        if(!detectedShaft.isEmpty())return;ProfileRoutes profile=routes();if(profile==null)return;
        for(String line:ConstellationClient.loc().getSidebarLines())for(String name:profile.routes.keySet())if(line.toUpperCase(Locale.ROOT).matches(".*(?:^|\\s)"+java.util.regex.Pattern.quote(name.toUpperCase(Locale.ROOT))+"(?:\\s|$).*")){detectedShaft=name;if(!name.equals(routeName))loadNamed(name,false);return;}
    }

    private static void decide(Vec3 player){
        RENDER.clear();if(LOADED.isEmpty())return;double range=Math.clamp(cfg.orderedWaypointsRangeTenths,10,100)/10.0;
        if(cfg.orderedWaypointsAutoSkipForward){int best=-1;double distance=Double.MAX_VALUE;for(int i=current+1;i<LOADED.size();i++){double next=distance(LOADED.get(i),player);if(next<range&&next<distance){distance=next;best=i;}}if(best>=0){current=best;lastCloser=current;}}
        current=Math.floorMod(current,LOADED.size());int previous=Math.floorMod(current-1,LOADED.size());addRender(previous);addRender(current);
        for(int i=1;i<=Math.clamp(cfg.orderedWaypointsNextCount,1,5);i++)addRender(Math.floorMod(current+i,LOADED.size()));
        Waypoint currentWp=LOADED.get(current),nextWp=LOADED.get(Math.floorMod(current+1,LOADED.size()));double d1=distance(currentWp,player),d2=distance(nextWp,player);
        if(lastCloser==current&&d1>d2&&d2<range){advance(1);return;}if(d1<range)lastCloser=current;if(d2<range){advance(1);return;}
        if(cfg.orderedWaypointsShowAll)for(int i=0;i<LOADED.size();i++)addRender(i);else if(cfg.orderedWaypointsSetupMode){int setup=Math.clamp(cfg.orderedWaypointsSetupRange,1,100);for(int i=0;i<LOADED.size();i++)if(!RENDER.contains(i)&&distance(LOADED.get(i),player)<setup)addRender(i);}
    }

    private static void addRender(int index){if(!RENDER.contains(index))RENDER.add(index);}
    private static void advance(int amount){if(!LOADED.isEmpty())current=Math.floorMod(current+amount,LOADED.size());RENDER.clear();}
    public static void draw(WorldRenderer.Ctx ctx){
        if(!active()||LOADED.isEmpty())return;Minecraft mc=Minecraft.getInstance();if(mc.player==null)return;for(int index:RENDER){if(index<0||index>=LOADED.size())continue;Waypoint waypoint=LOADED.get(index);int color=color(index);AABB box=new AABB(waypoint.x,waypoint.y,waypoint.z,waypoint.x+1,waypoint.y+1,waypoint.z+1);if(cfg.orderedWaypointsFill)ctx.box(box,color,cfg.orderedWaypointsThroughWalls);else ctx.outline(box,color,cfg.orderedWaypointsThroughWalls,Math.clamp(cfg.orderedWaypointsOutlineThickness,1,10));String name=display(index);if(cfg.orderedWaypointsShowName&&!name.isEmpty())ctx.label(Vec3.atLowerCornerOf(waypoint.pos()).add(.5,2.5,.5),name,color,true);if(cfg.orderedWaypointsShowDistance)ctx.label(Vec3.atLowerCornerOf(waypoint.pos()).add(.5,2,.5),String.format(Locale.ROOT,"%.1fm",distance(waypoint,mc.player.position())),color,true);}
        if(cfg.orderedWaypointsShowAll||RENDER.size()<2)return;int trace=RENDER.size()==2?RENDER.get(0):RENDER.get(2);if(trace<0||trace>=LOADED.size())return;Vec3 target=Vec3.atLowerCornerOf(LOADED.get(trace).pos()).add(.5,.25,.5);
        if(cfg.orderedWaypointsTraceLine&&!cfg.orderedWaypointsSetupMode)ctx.line(mc.player.getEyePosition(),target,cfg.orderedWaypointsTraceColor,cfg.orderedWaypointsThroughWalls);
        if(cfg.orderedWaypointsSetupMode){Waypoint here=LOADED.get(current);double eye=cfg.orderedWaypointsSneakingLine?1.27:1.62;ctx.line(Vec3.atLowerCornerOf(here.pos()).add(.5,1+eye,.5),Vec3.atLowerCornerOf(LOADED.get(trace).pos()).add(.5,.5,.5),cfg.orderedWaypointsSetupColor,cfg.orderedWaypointsThroughWalls);}
    }

    private static int color(int index){if(cfg.orderedWaypointsShowAll)return cfg.orderedWaypointsShowAllColor;if(index==current)return cfg.orderedWaypointsCurrentColor;if(index==Math.floorMod(current-1,LOADED.size()))return cfg.orderedWaypointsPreviousColor;if(RENDER.indexOf(index)>=2&&RENDER.indexOf(index)<2+Math.clamp(cfg.orderedWaypointsNextCount,1,5))return cfg.orderedWaypointsNextColor;return cfg.orderedWaypointsSetupColor;}
    private static String display(int index){Waypoint waypoint=LOADED.get(index);String number=Integer.toString(index+1);return cfg.orderedWaypointsShowCustomLabel&&!waypoint.label.isBlank()?number+" "+waypoint.label:number;}
    private static double distance(Waypoint waypoint,Vec3 player){return Vec3.atCenterOf(waypoint.pos()).distanceTo(player);}
    public static State state(){if(!active()||LOADED.isEmpty())return null;Minecraft mc=Minecraft.getInstance();if(mc.player==null)return null;current=Math.floorMod(current,LOADED.size());Waypoint here=LOADED.get(current),next=LOADED.get(Math.floorMod(current+1,LOADED.size()));return new State(routeName,current+1,LOADED.size(),here,next,distance(here,mc.player.position()),dirty,cfg.orderedWaypointsSetupMode);}
    public static AquilaConfig config(){return cfg;}

    private static boolean active(){if(cfg==null||!cfg.enabled||!cfg.orderedWaypointsSuite||!ConstellationClient.loc().onHypixel())return false;if(!cfg.orderedWaypointsMiningOnly)return true;return switch(ConstellationClient.loc().area()){case DWARVEN_MINES,CRYSTAL_HOLLOWS,GLACITE_TUNNELS,GLACITE_MINESHAFT,GOLD_MINE,DEEP_CAVERNS->true;default->false;};}
    private static String profile(){String value=LyraStorageValue.currentProfileKey();return value==null?"":value;}
    private static String key(){return profileKey.isBlank()?"unknown":profileKey;}
    private static ProfileRoutes routes(){return PROFILES.computeIfAbsent(key(),ignored->new ProfileRoutes());}
    private static void switchProfile(String profile){if(dirty)local("Unsaved ordered-route edits were unloaded after the profile changed.");profileKey=profile;LOADED.clear();RENDER.clear();routeName="";dirty=false;current=0;lastCloser=0;ProfileRoutes saved=routes();if(cfg.orderedWaypointsRememberLoaded&&!saved.activeRoute.isBlank()&&saved.routes.containsKey(saved.activeRoute))loadNamed(saved.activeRoute,false);}
    private static void connectionReset(){if(dirty)local("Unsaved ordered-route edits were unloaded after disconnecting.");profileKey="";LOADED.clear();RENDER.clear();routeName="";dirty=false;current=0;lastCloser=0;levelIdentity=null;lastArea=SkyblockArea.UNKNOWN;detectedShaft="";}

    private static int loadNamed(String name,boolean announce){ProfileRoutes profile=routes();List<Waypoint> found=profile.routes.get(normalizeName(name));if(found==null||found.isEmpty()){if(announce)local("No saved route named "+normalizeName(name)+".");return 0;}loadRoute(found,normalizeName(name));if(announce)local("Loaded "+LOADED.size()+" waypoints from "+routeName+".");return 1;}
    private static void loadRoute(List<Waypoint> source,String name){LOADED.clear();LOADED.addAll(copy(source));routeName=name;dirty=false;current=nearest();lastCloser=current;RENDER.clear();ProfileRoutes profile=routes();profile.activeRoute=name;saveStore();}
    private static int nearest(){Minecraft mc=Minecraft.getInstance();if(mc.player==null||LOADED.isEmpty())return 0;int best=0;double distance=Double.MAX_VALUE;for(int i=0;i<LOADED.size();i++){double value=distance(LOADED.get(i),mc.player.position());if(value<distance){distance=value;best=i;}}return best;}
    private static int importClipboard(){Minecraft mc=Minecraft.getInstance();String raw=mc.keyboardHandler.getClipboard();if(raw==null||raw.isBlank()||raw.length()>1_500_000){local("Clipboard does not contain a bounded route.");return 0;}List<Waypoint> parsed=parseColeweight(raw);if(parsed==null)parsed=parseLines(raw);if(parsed==null||parsed.isEmpty()){local("Clipboard route is not valid Coleweight JSON or x y z lines.");return 0;}LOADED.clear();LOADED.addAll(parsed);routeName="clipboard";dirty=true;current=nearest();lastCloser=current;RENDER.clear();local("Imported "+LOADED.size()+" waypoints from clipboard. Save them with /ordered save <name>.");return 1;}
    private static List<Waypoint> parseColeweight(String raw){try{JsonElement root=JsonParser.parseString(raw);if(!root.isJsonArray())return null;Map<Integer,Waypoint> numbered=new TreeMap<>();int fallback=1;for(JsonElement element:root.getAsJsonArray()){JsonObject object=element.getAsJsonObject();int x=object.get("x").getAsInt(),y=object.get("y").getAsInt(),z=object.get("z").getAsInt(),number=fallback++;if(!validPos(x,y,z))return null;String label="";if(object.has("options")&&object.get("options").isJsonObject()){JsonObject options=object.getAsJsonObject("options");if(options.has("label"))label=safeLabel(options.get("label").getAsString());if(options.has("name"))number=Integer.parseInt(options.get("name").getAsString());}if(number<1||number>5000||numbered.put(number,new Waypoint(x,y,z,label))!=null)return null;if(numbered.size()>5000)return null;}for(int i=1;i<=numbered.size();i++)if(!numbered.containsKey(i))return null;return normalize(new ArrayList<>(numbered.values()));}catch(Exception ignored){return null;}}
    private static List<Waypoint> parseLines(String raw){try{List<Waypoint> out=new ArrayList<>();for(String line:raw.lines().toList()){String clean=line.trim();if(clean.isEmpty())continue;String[] parts=clean.split("[,\\s]+");if(parts.length<3)return null;int x=Integer.parseInt(parts[0]),y=Integer.parseInt(parts[1]),z=Integer.parseInt(parts[2]);if(!validPos(x,y,z))return null;out.add(new Waypoint(x,y,z,parts.length>3?safeLabel(String.join(" ",java.util.Arrays.copyOfRange(parts,3,parts.length))):""));if(out.size()>5000)return null;}return normalize(out);}catch(Exception ignored){return null;}}
    private static int exportClipboard(){if(LOADED.isEmpty()){local("No route is loaded.");return 0;}JsonArray array=new JsonArray();for(int i=0;i<LOADED.size();i++){Waypoint waypoint=LOADED.get(i);JsonObject object=new JsonObject();object.addProperty("x",waypoint.x);object.addProperty("y",waypoint.y);object.addProperty("z",waypoint.z);object.addProperty("r",0);object.addProperty("g",1);object.addProperty("b",0);JsonObject options=new JsonObject();options.addProperty("name",Integer.toString(i+1));if(!waypoint.label.isBlank())options.addProperty("label",waypoint.label);object.add("options",options);array.add(object);}Minecraft.getInstance().keyboardHandler.setClipboard(GSON.toJson(array));local("Copied "+LOADED.size()+" Coleweight waypoints to clipboard.");return 1;}
    private static int saveRoute(String raw){if(LOADED.isEmpty()){local("No route is loaded.");return 0;}String name=normalizeName(raw);if(name.isBlank()){local("Route name must contain letters, numbers, underscore, dash, or space.");return 0;}routes().routes.put(name,copy(LOADED));routes().activeRoute=name;routeName=name;dirty=false;saveStore();local("Saved "+LOADED.size()+" waypoints as "+name+".");return 1;}
    private static int erase(String raw){String name=normalizeName(raw);if(routes().routes.remove(name)==null){local("No saved route named "+name+".");return 0;}if(routeName.equals(name)){routes().activeRoute="";routeName="clipboard";dirty=true;}saveStore();local("Erased saved route "+name+".");return 1;}
    private static int unload(boolean force){if(dirty&&!force){local("Route has unsaved edits. Use /ordered unload force or save it first.");return 0;}unloadInternal(true);return 1;}
    private static void unloadInternal(boolean announce){LOADED.clear();RENDER.clear();routeName="";dirty=false;current=0;lastCloser=0;ProfileRoutes profile=routes();profile.activeRoute="";saveStore();if(announce)local("Ordered route unloaded.");}
    private static void autoUnload(String reason){if(dirty){local("Auto-unload skipped because the route has unsaved edits ("+reason+").");return;}unloadInternal(false);}
    private static int add(int number,Integer x,Integer y,Integer z){Minecraft mc=Minecraft.getInstance();if(mc.player==null)return 0;if(number<1||number>LOADED.size()+1){local("Waypoint number must be between 1 and "+(LOADED.size()+1)+".");return 0;}BlockPos pos=x==null?mc.player.blockPosition().below():new BlockPos(x,y,z);if(!validPos(pos.getX(),pos.getY(),pos.getZ())){local("Waypoint coordinates are outside supported world bounds.");return 0;}LOADED.add(number-1,new Waypoint(pos.getX(),pos.getY(),pos.getZ(),""));edited();local("Inserted waypoint "+number+" at "+pos.getX()+" "+pos.getY()+" "+pos.getZ()+".");return 1;}
    private static int move(int number){if(!valid(number))return 0;Minecraft mc=Minecraft.getInstance();if(mc.player==null)return 0;BlockPos pos=mc.player.blockPosition().below();Waypoint old=LOADED.get(number-1);LOADED.set(number-1,new Waypoint(pos.getX(),pos.getY(),pos.getZ(),old.label));edited();local("Moved waypoint "+number+".");return 1;}
    private static int remove(int number){if(!valid(number))return 0;LOADED.remove(number-1);if(LOADED.isEmpty()){current=0;lastCloser=0;}else{current=Math.min(current,LOADED.size()-1);lastCloser=current;}edited();local("Removed waypoint "+number+".");return 1;}
    private static int label(int number,String raw){if(!valid(number))return 0;LOADED.set(number-1,LOADED.get(number-1).withLabel(safeLabel(raw)));edited();local("Waypoint "+number+" label updated.");return 1;}
    private static boolean valid(int number){if(number>=1&&number<=LOADED.size())return true;local("Waypoint number must be between 1 and "+LOADED.size()+".");return false;}
    private static void edited(){dirty=true;RENDER.clear();}
    private static int skip(int amount){if(LOADED.isEmpty()){local("No route is loaded.");return 0;}advance(amount);local("Current waypoint is "+(current+1)+".");return 1;}
    private static int skipTo(int number){if(!valid(number))return 0;current=number-1;lastCloser=current;RENDER.clear();return status();}
    private static int list(){ProfileRoutes profile=routes();local(profile.routes.isEmpty()?"No routes saved for this profile.":"Saved routes: "+String.join(", ",profile.routes.keySet())+".");return 1;}
    private static int status(){State state=state();local(state==null?"No ordered route is visible.":state.route+" "+state.current+"/"+state.total+", "+String.format(Locale.ROOT,"%.1fm",state.distance)+(state.dirty?" (unsaved)":"")+".");return 1;}
    private static int option(String raw,String state){Boolean value=parse(state);if(value==null){local("State must be on or off.");return 0;}switch(raw.toLowerCase(Locale.ROOT)){case"enabled"->cfg.orderedWaypointsSuite=value;case"hud"->cfg.orderedWaypointsHud=value;case"fill"->cfg.orderedWaypointsFill=value;case"trace"->cfg.orderedWaypointsTraceLine=value;case"distance"->cfg.orderedWaypointsShowDistance=value;case"name"->cfg.orderedWaypointsShowName=value;case"labels"->cfg.orderedWaypointsShowCustomLabel=value;case"setup"->cfg.orderedWaypointsSetupMode=value;case"sneakline"->cfg.orderedWaypointsSneakingLine=value;case"showall"->cfg.orderedWaypointsShowAll=value;case"walls"->cfg.orderedWaypointsThroughWalls=value;case"autounload"->cfg.orderedWaypointsAutoUnload=value;case"autoloadshaft"->cfg.orderedWaypointsAutoLoadMineshaft=value;case"unloadshaft"->cfg.orderedWaypointsAutoUnloadMineshaft=value;case"skipforward"->cfg.orderedWaypointsAutoSkipForward=value;case"remember"->cfg.orderedWaypointsRememberLoaded=value;case"miningonly"->cfg.orderedWaypointsMiningOnly=value;default->{local("Unknown ordered-route option.");return 0;}}saveConfig();return status();}
    private static int color(String raw,String hex){Integer value=parseColor(hex);if(value==null){local("Color must be eight-digit ARGB hex.");return 0;}switch(raw.toLowerCase(Locale.ROOT)){case"current"->cfg.orderedWaypointsCurrentColor=value;case"previous"->cfg.orderedWaypointsPreviousColor=value;case"next"->cfg.orderedWaypointsNextColor=value;case"trace"->cfg.orderedWaypointsTraceColor=value;case"setup"->cfg.orderedWaypointsSetupColor=value;case"all"->cfg.orderedWaypointsShowAllColor=value;default->{local("Color target must be current, previous, next, trace, setup, or all.");return 0;}}saveConfig();return 1;}

    public static void registerCommands(CommandDispatcher<FabricClientCommandSource> d){
        var root=LiteralArgumentBuilder.<FabricClientCommandSource>literal("ordered").executes(c->status())
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("status").executes(c->status()))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("list").executes(c->list()))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("import").executes(c->importClipboard()))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("export").executes(c->exportClipboard()))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("load").then(RequiredArgumentBuilder.<FabricClientCommandSource,String>argument("name",StringArgumentType.greedyString()).executes(c->loadNamed(StringArgumentType.getString(c,"name"),true))))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("save").then(RequiredArgumentBuilder.<FabricClientCommandSource,String>argument("name",StringArgumentType.greedyString()).executes(c->saveRoute(StringArgumentType.getString(c,"name")))))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("erase").then(RequiredArgumentBuilder.<FabricClientCommandSource,String>argument("name",StringArgumentType.greedyString()).executes(c->erase(StringArgumentType.getString(c,"name")))))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("unload").executes(c->unload(false)).then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("force").executes(c->unload(true))))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("skip").executes(c->skip(1)).then(RequiredArgumentBuilder.<FabricClientCommandSource,Integer>argument("amount",IntegerArgumentType.integer(-5000,5000)).executes(c->skip(IntegerArgumentType.getInteger(c,"amount")))))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("back").executes(c->skip(-1)).then(RequiredArgumentBuilder.<FabricClientCommandSource,Integer>argument("amount",IntegerArgumentType.integer(1,5000)).executes(c->skip(-IntegerArgumentType.getInteger(c,"amount")))))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("skipto").then(RequiredArgumentBuilder.<FabricClientCommandSource,Integer>argument("number",IntegerArgumentType.integer(1,5000)).executes(c->skipTo(IntegerArgumentType.getInteger(c,"number")))))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("add").executes(c->add(LOADED.size()+1,null,null,null)).then(RequiredArgumentBuilder.<FabricClientCommandSource,Integer>argument("number",IntegerArgumentType.integer(1,5001)).executes(c->add(IntegerArgumentType.getInteger(c,"number"),null,null,null))))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("addat").then(RequiredArgumentBuilder.<FabricClientCommandSource,Integer>argument("number",IntegerArgumentType.integer(1,5001)).then(RequiredArgumentBuilder.<FabricClientCommandSource,Integer>argument("x",IntegerArgumentType.integer()).then(RequiredArgumentBuilder.<FabricClientCommandSource,Integer>argument("y",IntegerArgumentType.integer()).then(RequiredArgumentBuilder.<FabricClientCommandSource,Integer>argument("z",IntegerArgumentType.integer()).executes(c->add(IntegerArgumentType.getInteger(c,"number"),IntegerArgumentType.getInteger(c,"x"),IntegerArgumentType.getInteger(c,"y"),IntegerArgumentType.getInteger(c,"z"))))))))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("move").then(RequiredArgumentBuilder.<FabricClientCommandSource,Integer>argument("number",IntegerArgumentType.integer(1,5000)).executes(c->move(IntegerArgumentType.getInteger(c,"number")))))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("delete").then(RequiredArgumentBuilder.<FabricClientCommandSource,Integer>argument("number",IntegerArgumentType.integer(1,5000)).executes(c->remove(IntegerArgumentType.getInteger(c,"number")))))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("label").then(RequiredArgumentBuilder.<FabricClientCommandSource,Integer>argument("number",IntegerArgumentType.integer(1,5000)).then(RequiredArgumentBuilder.<FabricClientCommandSource,String>argument("text",StringArgumentType.greedyString()).executes(c->label(IntegerArgumentType.getInteger(c,"number"),StringArgumentType.getString(c,"text"))))))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("setup").executes(c->{cfg.orderedWaypointsSetupMode=!cfg.orderedWaypointsSetupMode;saveConfig();return status();}))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("range").then(RequiredArgumentBuilder.<FabricClientCommandSource,Integer>argument("tenths",IntegerArgumentType.integer(10,100)).executes(c->{cfg.orderedWaypointsRangeTenths=IntegerArgumentType.getInteger(c,"tenths");saveConfig();return status();})))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("next").then(RequiredArgumentBuilder.<FabricClientCommandSource,Integer>argument("count",IntegerArgumentType.integer(1,5)).executes(c->{cfg.orderedWaypointsNextCount=IntegerArgumentType.getInteger(c,"count");saveConfig();return status();})))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("option").then(RequiredArgumentBuilder.<FabricClientCommandSource,String>argument("name",StringArgumentType.word()).then(RequiredArgumentBuilder.<FabricClientCommandSource,String>argument("state",StringArgumentType.word()).executes(c->option(StringArgumentType.getString(c,"name"),StringArgumentType.getString(c,"state"))))))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("color").then(RequiredArgumentBuilder.<FabricClientCommandSource,String>argument("target",StringArgumentType.word()).then(RequiredArgumentBuilder.<FabricClientCommandSource,String>argument("argb",StringArgumentType.word()).executes(c->color(StringArgumentType.getString(c,"target"),StringArgumentType.getString(c,"argb"))))));
        d.register(root);d.register(LiteralArgumentBuilder.<FabricClientCommandSource>literal("ow").redirect(root.build()));
    }

    private static List<Waypoint> normalize(List<Waypoint> raw){List<Waypoint> out=new ArrayList<>();for(Waypoint waypoint:raw){if(waypoint==null||!validPos(waypoint.x,waypoint.y,waypoint.z)||out.size()>=5000)continue;out.add(new Waypoint(waypoint.x,waypoint.y,waypoint.z,safeLabel(waypoint.label)));}return out;}
    private static List<Waypoint> copy(List<Waypoint> source){return new ArrayList<>(normalize(source));}
    private static String safeLabel(String raw){if(raw==null)return"";String value=raw.replaceAll("§[0-9A-FK-ORa-fk-or]","").replaceAll("[\\p{Cntrl}]"," ").trim();return value.length()>64?value.substring(0,64):value;}
    private static String normalizeName(String raw){if(raw==null)return"";String value=raw.replaceAll("[^A-Za-z0-9 _-]","").trim().replaceAll("\\s+"," ");return value.length()>48?value.substring(0,48).trim():value;}
    private static boolean validPos(int x,int y,int z){return Math.abs((long)x)<=30_000_000L&&Math.abs((long)z)<=30_000_000L&&y>=-2048&&y<=2048;}
    private static Boolean parse(String raw){return switch(raw.toLowerCase(Locale.ROOT)){case"on","true","yes","1"->true;case"off","false","no","0"->false;default->null;};}
    private static Integer parseColor(String raw){String value=raw.startsWith("#")?raw.substring(1):raw;if(value.length()!=8)return null;try{return(int)Long.parseLong(value,16);}catch(Exception ignored){return null;}}
    private static void loadStore(){try{if(!Files.exists(FILE))return;Store store=GSON.fromJson(Files.readString(FILE,StandardCharsets.UTF_8),Store.class);if(store==null||store.profiles==null)return;for(var entry:store.profiles.entrySet()){ProfileRoutes value=entry.getValue();if(value==null)continue;if(value.routes==null)value.routes=new LinkedHashMap<>();Map<String,List<Waypoint>> clean=new LinkedHashMap<>();for(var route:value.routes.entrySet()){String name=normalizeName(route.getKey());List<Waypoint> points=normalize(route.getValue()==null?List.of():route.getValue());if(!name.isBlank()&&!points.isEmpty())clean.put(name,points);}value.routes=clean;if(value.activeRoute==null||!clean.containsKey(value.activeRoute))value.activeRoute="";PROFILES.put(entry.getKey(),value);}}catch(Exception e){ConstellationClient.LOGGER.warn("could not load ordered mining routes",e);}}
    private static void saveStore(){try{Files.createDirectories(FILE.getParent());Path temp=FILE.resolveSibling(FILE.getFileName()+".tmp");Store store=new Store();store.profiles.putAll(PROFILES);Files.writeString(temp,GSON.toJson(store),StandardCharsets.UTF_8);try{Files.move(temp,FILE,StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);}catch(Exception ignored){Files.move(temp,FILE,StandardCopyOption.REPLACE_EXISTING);}}catch(Exception e){ConstellationClient.LOGGER.warn("could not save ordered mining routes",e);}}
    private static void saveConfig(){ConstellationClient.saveConfig();}
    private static void local(String text){Minecraft mc=Minecraft.getInstance();if(mc.player!=null)mc.player.sendSystemMessage(Component.literal("§3[Ordered Routes] §f"+text));}
}
