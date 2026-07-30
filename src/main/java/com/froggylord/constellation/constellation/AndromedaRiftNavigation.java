package com.froggylord.constellation.constellation;

import com.froggylord.constellation.ConstellationClient;
import com.froggylord.constellation.config.AndromedaConfig;
import com.froggylord.constellation.render.WorldRenderer;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.Minecraft;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.monster.EnderMan;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.PriorityQueue;

// ported from SkyHanni (LGPL-3.0-or-later): data/IslandGraphs.kt, utils/GraphUtils.kt, data/model/graph/DijkstraTree.kt, features/rift/RiftApi.kt
// data ported from SkyHanni Repo (MIT): constants/island_graphs/THE_RIFT.json
public final class AndromedaRiftNavigation {
    private record Edge(Node to,double weight){}
    private static final class Node{final int id;final Vec3 pos;final List<Edge> edges=new ArrayList<>();Node(int id,Vec3 pos){this.id=id;this.pos=pos;}}
    private record QueueNode(Node node,double distance){}
    private record Route(List<Node> nodes,double distance){}
    private static final Map<Integer,Node> NODES=new LinkedHashMap<>();
    private static final List<Vec3> PILLARS=new ArrayList<>();
    private static AndromedaConfig cfg;private static boolean loaded;private static int target=-1,ticks,pillarTicks,lastArrivalTarget=-1,blockedNodes;private static long pillarSignature;private static Node nearest,targetNode;private static Route route;private static Vec3 lastPlayer;
    private AndromedaRiftNavigation(){}

    public static void init(AndromedaConfig config){cfg=config;load();ConstellationClient.tick().every(5,"andromeda-rift-navigation",AndromedaRiftNavigation::tick);}
    private static void load(){
        if(loaded)return;loaded=true;
        try(var stream=AndromedaRiftNavigation.class.getResourceAsStream("/assets/constellation/rift/riftGraph.json")){
            if(stream==null)throw new IllegalStateException("missing Rift graph");
            JsonObject root=JsonParser.parseReader(new InputStreamReader(stream,StandardCharsets.UTF_8)).getAsJsonObject();
            for(var entry:root.entrySet()){JsonObject value=entry.getValue().getAsJsonObject();String[] xyz=value.get("Position").getAsString().split(":");NODES.put(Integer.parseInt(entry.getKey()),new Node(Integer.parseInt(entry.getKey()),new Vec3(Double.parseDouble(xyz[0]),Double.parseDouble(xyz[1]),Double.parseDouble(xyz[2]))));}
            int edges=0;for(var entry:root.entrySet()){Node node=NODES.get(Integer.parseInt(entry.getKey()));JsonObject value=entry.getValue().getAsJsonObject();if(!value.has("Neighbours"))continue;for(var edge:value.getAsJsonObject("Neighbours").entrySet()){Node to=NODES.get(Integer.parseInt(edge.getKey()));if(to!=null){node.edges.add(new Edge(to,edge.getValue().getAsDouble()));edges++;}}}
            ConstellationClient.LOGGER.info("loaded Rift navigation graph: {} nodes, {} edges",NODES.size(),edges);
        }catch(Exception e){NODES.clear();ConstellationClient.LOGGER.error("failed to load Rift navigation graph",e);}
    }
    private static boolean active(){return cfg!=null&&cfg.enabled&&cfg.riftPathfinder&&AndromedaRiftCore.isActive()&&!NODES.isEmpty();}
    private static void tick(){
        if(!active()){PILLARS.clear();blockedNodes=0;clearTransient();return;}Minecraft mc=Minecraft.getInstance();if(mc.player==null)return;
        if(++pillarTicks%4==0)scanPillars(mc);
        if(cfg.riftPathfinderAutoNearest&&target<0)target=AndromedaRiftCore.closestMissingIndex(mc.player.position());
        if(target<0)return;
        if(AndromedaRiftCore.soulFound(target)){if(cfg.riftPathfinderAutoNearest)target=AndromedaRiftCore.closestMissingIndex(mc.player.position());else{clear();return;}}
        if(target<0||++ticks%2!=0)return;
        Vec3 player=mc.player.position();Node now=nearest(player,true);boolean moved=lastPlayer==null||lastPlayer.distanceToSqr(player)>9;
        if(now!=nearest||moved||targetNode==null){nearest=now;lastPlayer=player;rebuild();}
        checkArrival(player);
    }
    private static void rebuild(){
        BlockPos goal=AndromedaRiftCore.soulPosition(target);if(nearest==null||goal==null){route=null;return;}
        targetNode=nearest(Vec3.atCenterOf(goal),true);Route found=shortest(nearest,targetNode);if(found==null){route=null;return;}
        List<Node> nodes=new ArrayList<>(found.nodes);double distance=found.distance+lastPlayer.distanceTo(nearest.pos)+targetNode.pos.distanceTo(Vec3.atCenterOf(goal));
        if(nodes.size()>1){Node first=nodes.get(0),second=nodes.get(1);double edge=edgeWeight(first,second),direct=lastPlayer.distanceTo(second.pos),via=lastPlayer.distanceTo(first.pos)+edge;if(direct<via){nodes.remove(0);distance=distance-lastPlayer.distanceTo(first.pos)-edge+direct;}}
        route=new Route(List.copyOf(nodes),Math.max(0,distance));
    }
    private static Node nearest(Vec3 position,boolean avoid){Node best=null;double distance=Double.POSITIVE_INFINITY;for(Node node:NODES.values()){if(avoid&&blocked(node))continue;double current=node.pos.distanceToSqr(position);if(current<distance){distance=current;best=node;}}return best;}
    private static Route shortest(Node start,Node end){
        Map<Node,Double> distance=new HashMap<>();Map<Node,Node> previous=new HashMap<>();PriorityQueue<QueueNode> queue=new PriorityQueue<>(Comparator.comparingDouble(QueueNode::distance));distance.put(start,0.0);queue.add(new QueueNode(start,0));
        while(!queue.isEmpty()){QueueNode current=queue.poll();if(current.distance>distance.getOrDefault(current.node,Double.POSITIVE_INFINITY))continue;if(current.node==end)break;for(Edge edge:current.node.edges){if(blocked(edge.to))continue;double next=current.distance+edge.weight;if(next>=distance.getOrDefault(edge.to,Double.POSITIVE_INFINITY))continue;distance.put(edge.to,next);previous.put(edge.to,current.node);queue.add(new QueueNode(edge.to,next));}}
        Double total=distance.get(end);if(total==null)return null;List<Node> path=new ArrayList<>();for(Node node=end;node!=null;node=previous.get(node)){path.add(node);if(node==start)break;}Collections.reverse(path);return new Route(List.copyOf(path),total);
    }
    private static double edgeWeight(Node from,Node to){for(Edge edge:from.edges)if(edge.to==to)return edge.weight;return from.pos.distanceTo(to.pos);}
    public static void draw(WorldRenderer.Ctx ctx){
        if(!active())return;Minecraft mc=Minecraft.getInstance();if(mc.player==null)return;
        if(cfg.riftTemporalPillarShowDanger)for(Vec3 pillar:PILLARS){double radius=Math.clamp(cfg.riftTemporalPillarRadius,2,20);if(cfg.riftTemporalPillarBox)ctx.highlight(new AABB(pillar.x-radius,pillar.y,pillar.z-radius,pillar.x+radius,pillar.y+3,pillar.z+radius),cfg.riftTemporalPillarColor,cfg.riftTemporalPillarThroughWalls);if(cfg.riftTemporalPillarBeam)ctx.beam(pillar.x,pillar.y,pillar.z,cfg.riftTemporalPillarColor,Math.clamp(cfg.riftTemporalPillarBeamHeight,2,100),cfg.riftTemporalPillarThroughWalls);if(cfg.riftTemporalPillarLabel){String label="Temporal Pillar";if(cfg.riftTemporalPillarDistance)label+=" "+Math.round(pillar.distanceTo(mc.player.position()))+"m";ctx.label(pillar.add(0,3.4,0),label,cfg.riftTemporalPillarColor,cfg.riftTemporalPillarThroughWalls);}}
        if(target<0||route==null||route.nodes.isEmpty())return;
        int color=cfg.riftPathfinderColor,limit=Math.min(route.nodes.size(),Math.clamp(cfg.riftPathfinderLookAhead,2,2201));Vec3 from=mc.player.position().add(0,.2,0);
        for(int i=0;i<limit;i++){Vec3 to=route.nodes.get(i).pos;ctx.line(from,to,color,cfg.riftPathfinderThroughWalls,Math.clamp(cfg.riftPathfinderWidth,1,15));from=to;}
        BlockPos targetPos=AndromedaRiftCore.soulPosition(target);if(targetPos==null)return;Vec3 center=Vec3.atCenterOf(targetPos);
        if(limit==route.nodes.size())ctx.line(from,center,color,cfg.riftPathfinderThroughWalls,Math.clamp(cfg.riftPathfinderWidth,1,15));
        if(cfg.riftPathfinderTargetBox)ctx.highlight(new AABB(targetPos),color,cfg.riftPathfinderThroughWalls);
        if(cfg.riftPathfinderTargetBeam)ctx.beam(center.x,center.y,center.z,color,Math.clamp(cfg.riftPathfinderBeamHeight,2,100),cfg.riftPathfinderThroughWalls);
        if(cfg.riftPathfinderTargetLabel){String label=AndromedaRiftCore.soulName(target);if(cfg.riftPathfinderDistance)label+=" "+Math.round(route.distance)+"m";ctx.label(center.add(0,1.3,0),label,color,cfg.riftPathfinderThroughWalls);}
    }
    private static void checkArrival(Vec3 player){BlockPos pos=AndromedaRiftCore.soulPosition(target);if(pos==null||pos.distToCenterSqr(player)>Math.pow(Math.clamp(cfg.riftPathfinderArrivalRange,2,12),2))return;if(cfg.riftPathfinderArrivalChat&&lastArrivalTarget!=target)local("Reached "+AndromedaRiftCore.soulName(target)+".");lastArrivalTarget=target;if(cfg.riftPathfinderArrivalClear&&!cfg.riftPathfinderAutoNearest)clear();}
    public static void onSoulFound(){if(target<0)return;if(cfg.riftPathfinderAutoNearest){Minecraft mc=Minecraft.getInstance();target=mc.player==null?-1:AndromedaRiftCore.closestMissingIndex(mc.player.position());lastArrivalTarget=-1;resetRoute();}else clear();}
    private static void select(int index){if(index<0||index>=AndromedaRiftCore.soulCount()){local("Soul number must be 1 to "+AndromedaRiftCore.soulCount()+".");return;}target=index;lastArrivalTarget=-1;resetRoute();local("Routing to "+AndromedaRiftCore.soulName(index)+" in "+AndromedaRiftCore.soulArea(index)+".");}
    private static void selectNearest(){Minecraft mc=Minecraft.getInstance();if(mc.player==null)return;int index=AndromedaRiftCore.closestMissingIndex(mc.player.position());if(index<0){local("No missing Enigma Souls remain.");return;}select(index);}
    private static void clear(){target=-1;lastArrivalTarget=-1;clearTransient();}
    private static void resetRoute(){nearest=null;targetNode=null;route=null;lastPlayer=null;}
    private static void clearTransient(){resetRoute();pillarTicks=0;}

    // ported from SkyHanni (LGPL-3.0-or-later): features/rift/RiftApi.kt
    private static void scanPillars(Minecraft mc){
        List<Vec3> found=new ArrayList<>();double range=Math.clamp(cfg.riftTemporalPillarScanRange,20,256),sq=range*range;
        if((cfg.riftTemporalPillarDodge||cfg.riftTemporalPillarShowDanger)&&mc.level!=null)for(Entity entity:mc.level.entitiesForRendering()){if(entity.distanceToSqr(mc.player)>sq||!pillarName(entity))continue;Vec3 position=entity.position();if(entity instanceof ArmorStand){List<EnderMan> bases=mc.level.getEntitiesOfClass(EnderMan.class,entity.getBoundingBox().inflate(4),value->true);if(!bases.isEmpty())position=bases.stream().min(Comparator.comparingDouble(value->value.distanceToSqr(entity))).orElseThrow().position();}found.add(position);}
        found.sort(Comparator.comparingDouble(Vec3::x).thenComparingDouble(Vec3::y).thenComparingDouble(Vec3::z));for(int i=found.size()-1;i>0;i--)if(found.get(i).distanceToSqr(found.get(i-1))<1)found.remove(i);
        long signature=1;for(Vec3 pillar:found)signature=31*signature+BlockPos.containing(pillar).asLong();PILLARS.clear();PILLARS.addAll(found);blockedNodes=cfg.riftTemporalPillarDodge?(int)NODES.values().stream().filter(AndromedaRiftNavigation::blocked).count():0;
        if(signature!=pillarSignature){pillarSignature=signature;resetRoute();}
    }
    private static boolean blocked(Node node){if(!cfg.riftTemporalPillarDodge)return false;double radius=Math.clamp(cfg.riftTemporalPillarRadius,2,20),sq=radius*radius;for(Vec3 pillar:PILLARS)if(node.pos.distanceToSqr(pillar)<sq)return true;return false;}
    private static boolean pillarName(Entity entity){return clean(entity.getName().getString()).equals("Temporal Pillar")||entity.getCustomName()!=null&&clean(entity.getCustomName().getString()).equals("Temporal Pillar");}
    private static String clean(String value){String clean=ChatFormatting.stripFormatting(value);return clean==null?"":clean.trim();}

    public static void registerCommands(CommandDispatcher<FabricClientCommandSource> dispatcher){
        dispatcher.register(LiteralArgumentBuilder.<FabricClientCommandSource>literal("riftnav").executes(c->status())
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("nearest").executes(c->{selectNearest();return status();}))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("clear").executes(c->{clear();return status();}))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("soul").then(RequiredArgumentBuilder.<FabricClientCommandSource,Integer>argument("number",IntegerArgumentType.integer(1,52)).executes(c->{select(IntegerArgumentType.getInteger(c,"number")-1);return status();})))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("named").then(RequiredArgumentBuilder.<FabricClientCommandSource,String>argument("name",StringArgumentType.greedyString()).executes(c->{int index=AndromedaRiftCore.soulIndex(StringArgumentType.getString(c,"name"));if(index<0){local("No matching Enigma Soul.");return 0;}select(index);return status();})))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("width").then(RequiredArgumentBuilder.<FabricClientCommandSource,Integer>argument("pixels",IntegerArgumentType.integer(1,15)).executes(c->{cfg.riftPathfinderWidth=IntegerArgumentType.getInteger(c,"pixels");save();return status();})))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("lookahead").then(RequiredArgumentBuilder.<FabricClientCommandSource,Integer>argument("nodes",IntegerArgumentType.integer(2,2201)).executes(c->{cfg.riftPathfinderLookAhead=IntegerArgumentType.getInteger(c,"nodes");save();return status();})))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("arrival").then(RequiredArgumentBuilder.<FabricClientCommandSource,Integer>argument("blocks",IntegerArgumentType.integer(2,12)).executes(c->{cfg.riftPathfinderArrivalRange=IntegerArgumentType.getInteger(c,"blocks");save();return status();})))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("beamheight").then(RequiredArgumentBuilder.<FabricClientCommandSource,Integer>argument("blocks",IntegerArgumentType.integer(2,100)).executes(c->{cfg.riftPathfinderBeamHeight=IntegerArgumentType.getInteger(c,"blocks");save();return status();})))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("pillarradius").then(RequiredArgumentBuilder.<FabricClientCommandSource,Integer>argument("blocks",IntegerArgumentType.integer(2,20)).executes(c->{cfg.riftTemporalPillarRadius=IntegerArgumentType.getInteger(c,"blocks");pillarSignature=0;save();return status();})))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("pillarrange").then(RequiredArgumentBuilder.<FabricClientCommandSource,Integer>argument("blocks",IntegerArgumentType.integer(20,256)).executes(c->{cfg.riftTemporalPillarScanRange=IntegerArgumentType.getInteger(c,"blocks");save();return status();})))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("pillarbeamheight").then(RequiredArgumentBuilder.<FabricClientCommandSource,Integer>argument("blocks",IntegerArgumentType.integer(2,100)).executes(c->{cfg.riftTemporalPillarBeamHeight=IntegerArgumentType.getInteger(c,"blocks");save();return status();})))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("color").then(RequiredArgumentBuilder.<FabricClientCommandSource,String>argument("argb",StringArgumentType.word()).executes(c->color(StringArgumentType.getString(c,"argb")))))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("pillarcolor").then(RequiredArgumentBuilder.<FabricClientCommandSource,String>argument("argb",StringArgumentType.word()).executes(c->pillarColor(StringArgumentType.getString(c,"argb")))))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("option").then(RequiredArgumentBuilder.<FabricClientCommandSource,String>argument("name",StringArgumentType.word()).then(RequiredArgumentBuilder.<FabricClientCommandSource,String>argument("state",StringArgumentType.word()).executes(c->option(StringArgumentType.getString(c,"name"),StringArgumentType.getString(c,"state")))))));
    }
    private static int status(){String routeState=target<0?"No active route.":"Soul "+(target+1)+": "+(route==null?"calculating":route.nodes.size()+" nodes, "+Math.round(route.distance)+"m")+". ";local(routeState+"Graph "+NODES.size()+" nodes; "+PILLARS.size()+" Temporal Pillar"+(PILLARS.size()==1?"":"s")+" blocking "+blockedNodes+" nodes.");return 1;}
    private static int option(String name,String raw){Boolean value=switch(raw.toLowerCase(Locale.ROOT)){case"on","true","yes","1"->true;case"off","false","no","0"->false;default->null;};if(value==null){local("State must be on or off.");return 0;}switch(name.toLowerCase(Locale.ROOT)){case"enabled"->cfg.riftPathfinder=value;case"auto"->cfg.riftPathfinderAutoNearest=value;case"walls"->cfg.riftPathfinderThroughWalls=value;case"box"->cfg.riftPathfinderTargetBox=value;case"beam"->cfg.riftPathfinderTargetBeam=value;case"label"->cfg.riftPathfinderTargetLabel=value;case"distance"->cfg.riftPathfinderDistance=value;case"clear"->cfg.riftPathfinderArrivalClear=value;case"chat"->cfg.riftPathfinderArrivalChat=value;case"pillardodge"->{cfg.riftTemporalPillarDodge=value;pillarSignature=0;}case"pillardanger"->cfg.riftTemporalPillarShowDanger=value;case"pillarbox"->cfg.riftTemporalPillarBox=value;case"pillarbeam"->cfg.riftTemporalPillarBeam=value;case"pillarlabel"->cfg.riftTemporalPillarLabel=value;case"pillardistance"->cfg.riftTemporalPillarDistance=value;case"pillarwalls"->cfg.riftTemporalPillarThroughWalls=value;default->{local("Unknown Rift navigation option.");return 0;}}save();return status();}
    private static int color(String raw){try{String value=raw.trim().replaceFirst("^(?:#|0[xX])","");long parsed=Long.parseUnsignedLong(value,16);if(value.length()<=6)parsed|=0xFF000000L;cfg.riftPathfinderColor=(int)parsed;save();return status();}catch(Exception e){local("Color must be ARGB hex, such as FF5599FF.");return 0;}}
    private static int pillarColor(String raw){try{String value=raw.trim().replaceFirst("^(?:#|0[xX])","");long parsed=Long.parseUnsignedLong(value,16);if(value.length()<=6)parsed|=0xFF000000L;cfg.riftTemporalPillarColor=(int)parsed;save();return status();}catch(Exception e){local("Color must be ARGB hex, such as 80FF5555.");return 0;}}
    private static void save(){ConstellationClient.saveConfig();}
    public static void selectFromGuide(int index){select(index);}
    public static int targetIndex(){return target;}
    private static void local(String text){Minecraft mc=Minecraft.getInstance();if(mc.player!=null)mc.player.sendSystemMessage(Component.literal("§5[Rift Nav] §f"+text));}
}
