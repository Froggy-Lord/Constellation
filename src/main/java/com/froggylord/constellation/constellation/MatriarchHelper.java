package com.froggylord.constellation.constellation;

import com.froggylord.constellation.ConstellationClient;
import com.froggylord.constellation.config.DracoConfig;
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
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.monster.cubemob.Slime;
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

// ported from SkyHanni (LGPL-3.0-or-later): features/nether/MatriarchHelper.kt
// graph routing ported from SkyHanni (LGPL-3.0-or-later): data/IslandGraphs.kt, utils/GraphUtils.kt, data/model/graph/DijkstraTree.kt
// graph data ported from SkyHanni Repo (MIT): constants/island_graphs/CRIMSON_ISLE.json
public final class MatriarchHelper {
    private record Pearl(Entity entity,Vec3 pos){}
    private record Edge(Node to,double weight){}
    private static final class Node{final int id;final Vec3 pos;final String name;final List<Edge> edges=new ArrayList<>();Node(int id,Vec3 pos,String name){this.id=id;this.pos=pos;this.name=name;}}
    private record QueueNode(Node node,double distance){}
    private record Route(List<Node> nodes,double distance){}

    private static final Vec3 BELLY=new Vec3(-541,32,-889);
    private static final List<Pearl> PEARLS=new ArrayList<>();
    private static final List<Vec3> ORDERED=new ArrayList<>(),EXIT_PATH=new ArrayList<>();
    private static Map<Integer,Node> graph;
    private static Node exitNode;
    private static DracoConfig cfg;
    private static Object levelIdentity;
    private static double routeDistance;
    private static long lastPearlSeen;
    private static long lastBuild;
    private static String routeSignature="";
    private static Vec3 lastPlayer;

    private MatriarchHelper(){}

    public static void init(DracoConfig config){cfg=config;ConstellationClient.tick().every(2,"draco-matriarch",MatriarchHelper::tick);}

    private static void tick(){
        Minecraft mc=Minecraft.getInstance();if(mc.level!=levelIdentity){levelIdentity=mc.level;clear();}
        if(!active()||mc.player==null||mc.level==null){clear();return;}
        double scan=Math.clamp(cfg.matriarchScanRange,24,128),scanSq=scan*scan;List<ArmorStand> names=new ArrayList<>();List<Slime> slimes=new ArrayList<>();
        for(Entity entity:mc.level.entitiesForRendering()){
            if(!entity.isAlive()||entity.distanceToSqr(mc.player)>scanSq)continue;
            if(entity instanceof ArmorStand stand&&clean(stand.getCustomName()==null?"":stand.getCustomName().getString()).equalsIgnoreCase("COLLECT!"))names.add(stand);
            else if(entity instanceof Slime slime)slimes.add(slime);
        }
        PEARLS.clear();for(ArmorStand stand:names){Slime slime=slimes.stream().filter(s->s.distanceToSqr(stand)<16).min(Comparator.comparingDouble(s->s.distanceToSqr(stand))).orElse(null);Entity base=slime==null?stand:slime;if(PEARLS.stream().noneMatch(p->p.entity().getUUID().equals(base.getUUID())))PEARLS.add(new Pearl(base,base.position().add(0,slime==null?0:1.2,0)));}
        long now=System.currentTimeMillis();if(!PEARLS.isEmpty())lastPearlSeen=now;String signature=PEARLS.stream().map(p->p.entity().getUUID()+":"+Math.round(p.pos().x*10)+":"+Math.round(p.pos().y*10)+":"+Math.round(p.pos().z*10)).sorted().toList().toString();boolean moved=lastPlayer==null||lastPlayer.distanceToSqr(mc.player.position())>4;if(!signature.equals(routeSignature)||moved||now-lastBuild>5000){routeSignature=signature;lastPlayer=mc.player.position();lastBuild=now;rebuild(lastPlayer);}
    }

    private static void rebuild(Vec3 player){
        ORDERED.clear();EXIT_PATH.clear();routeDistance=0;List<Vec3> points=PEARLS.stream().map(Pearl::pos).toList();
        if(cfg.matriarchUseShortestDistance)ORDERED.addAll(shortestOrder(player,points));else ORDERED.addAll(points.stream().sorted(Comparator.comparingDouble(Vec3::y)).toList());
        Vec3 from=player;for(Vec3 point:ORDERED){routeDistance+=from.distanceTo(point);from=point;}
        if(cfg.matriarchSimpleLine||!cfg.matriarchUseGraphExitPath)return;loadGraph();if(exitNode==null)return;Node start=nearest(graph,ORDERED.isEmpty()?player:ORDERED.getLast());Route route=shortest(start,exitNode,20_000);if(route==null)return;for(Node node:route.nodes())EXIT_PATH.add(node.pos);routeDistance+=route.distance();
    }

    private static List<Vec3> shortestOrder(Vec3 start,List<Vec3> points){if(points.size()<2)return points;if(points.size()>6)return points.stream().sorted(Comparator.comparingDouble(start::distanceToSqr)).toList();List<List<Vec3>> permutations=new ArrayList<>();permute(new ArrayList<>(points),0,permutations);return permutations.stream().min(Comparator.comparingDouble(list->{double d=0;Vec3 from=start;for(Vec3 p:list){d+=from.distanceTo(p);from=p;}return d;})).orElse(points);}
    private static void permute(List<Vec3> list,int index,List<List<Vec3>> out){if(index>=list.size()){out.add(List.copyOf(list));return;}for(int i=index;i<list.size();i++){Collections.swap(list,index,i);permute(list,index+1,out);Collections.swap(list,index,i);}}

    public static void draw(WorldRenderer.Ctx ctx){
        if(!active())return;Minecraft mc=Minecraft.getInstance();if(mc.player==null)return;double range=Math.clamp(cfg.matriarchRenderRange,16,192),rangeSq=range*range;
        if(cfg.matriarchHighlight)for(int i=0;i<PEARLS.size();i++){Pearl pearl=PEARLS.get(i);if(pearl.pos().distanceToSqr(mc.player.position())>rangeSq)continue;AABB box=pearl.entity().getBoundingBox().inflate(.35);if(cfg.matriarchHighlightFill)ctx.box(box,cfg.matriarchHighlightColor,cfg.matriarchThroughWalls);if(cfg.matriarchHighlightOutline)ctx.outline(box,0xFF000000|(cfg.matriarchHighlightColor&0xFFFFFF),cfg.matriarchThroughWalls,2);if(cfg.matriarchPearlBeams)ctx.beam(pearl.pos().x,pearl.pos().y,pearl.pos().z,cfg.matriarchHighlightColor,Math.clamp(cfg.matriarchBeamHeight,2,32),cfg.matriarchThroughWalls);if(cfg.matriarchPearlLabels)ctx.label(pearl.pos().add(0,1.5,0),"Heavy Pearl "+(i+1),cfg.matriarchHighlightColor,cfg.matriarchThroughWalls);}
        if(cfg.matriarchLine){Vec3 from=mc.player.position().add(0,mc.player.getEyeHeight()*.8,0);int width=Math.clamp(cfg.matriarchLineWidth,1,15);for(Vec3 point:ORDERED){ctx.line(from,point,cfg.matriarchLineColor,cfg.matriarchThroughWalls,width);from=point;}if(!cfg.matriarchSimpleLine){int limit=Math.min(EXIT_PATH.size(),Math.clamp(cfg.matriarchPathLookAheadNodes,2,5000));for(int i=0;i<limit;i++){Vec3 point=EXIT_PATH.get(i);ctx.line(from,point,cfg.matriarchLineColor,cfg.matriarchThroughWalls,width);from=point;}}}
        if(cfg.matriarchShowExitLabel&&exitNode!=null&&!cfg.matriarchSimpleLine){String text="Heavy Pearls exit";if(cfg.matriarchShowDistance)text+=" · "+Math.round(mc.player.position().distanceTo(exitNode.pos))+"m";ctx.label(exitNode.pos.add(0,1.2,0),text,cfg.matriarchExitColor,cfg.matriarchThroughWalls);}
    }

    public static String hudText(){if(!active()||PEARLS.isEmpty()&&System.currentTimeMillis()-lastPearlSeen>2000)return null;String text="Pearls "+PEARLS.size()+"/"+Math.clamp(cfg.matriarchExpectedPearls,1,10);if(cfg.matriarchShowDistance&&routeDistance>0)text+=" | Route "+Math.round(routeDistance)+"m";return text;}

    public static void registerCommands(CommandDispatcher<FabricClientCommandSource> d){d.register(LiteralArgumentBuilder.<FabricClientCommandSource>literal("matriarch").executes(c->status()).then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("status").executes(c->status())).then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("reset").executes(c->{clear();local("Pearl route reset.");return 1;})).then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("range").then(RequiredArgumentBuilder.<FabricClientCommandSource,Integer>argument("blocks",IntegerArgumentType.integer(24,192)).executes(c->{cfg.matriarchRenderRange=IntegerArgumentType.getInteger(c,"blocks");save();return status();}))).then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("option").then(RequiredArgumentBuilder.<FabricClientCommandSource,String>argument("name",StringArgumentType.word()).then(RequiredArgumentBuilder.<FabricClientCommandSource,String>argument("state",StringArgumentType.word()).executes(c->option(StringArgumentType.getString(c,"name"),StringArgumentType.getString(c,"state")))))));}
    private static int status(){loadGraph();local("Helper "+on(active())+", "+PEARLS.size()+" Heavy Pearls, "+EXIT_PATH.size()+" route nodes, "+Math.round(routeDistance)+"m route, graph "+(graph==null?0:graph.size())+" nodes.");return 1;}
    private static int option(String name,String raw){Boolean v=switch(raw.toLowerCase(Locale.ROOT)){case"on","true","yes","1"->true;case"off","false","no","0"->false;default->null;};if(v==null){local("State must be on or off.");return 0;}switch(name.toLowerCase(Locale.ROOT)){case"enabled"->cfg.matriarchHelper=v;case"highlight"->cfg.matriarchHighlight=v;case"line"->cfg.matriarchLine=v;case"simple"->cfg.matriarchSimpleLine=v;case"shortest"->cfg.matriarchUseShortestDistance=v;case"graph"->cfg.matriarchUseGraphExitPath=v;case"labels"->cfg.matriarchPearlLabels=v;case"beams"->cfg.matriarchPearlBeams=v;case"throughwalls"->cfg.matriarchThroughWalls=v;case"hud"->cfg.matriarchHud=v;default->{local("Unknown Matriarch option.");return 0;}}save();return status();}

    private static void loadGraph(){if(graph!=null)return;Map<Integer,Node> nodes=new LinkedHashMap<>();try(var stream=MatriarchHelper.class.getResourceAsStream("/assets/constellation/hoppity/island_graphs/CRIMSON_ISLE.json")){if(stream==null)throw new IllegalStateException("missing Crimson Isle graph");JsonObject root=JsonParser.parseReader(new InputStreamReader(stream,StandardCharsets.UTF_8)).getAsJsonObject();for(var entry:root.entrySet()){JsonObject value=entry.getValue().getAsJsonObject();String[] xyz=value.get("Position").getAsString().split(":");String name=value.has("Name")?value.get("Name").getAsString():"";Node node=new Node(Integer.parseInt(entry.getKey()),new Vec3(Double.parseDouble(xyz[0]),Double.parseDouble(xyz[1]),Double.parseDouble(xyz[2])),name);nodes.put(node.id,node);if(name.equals("Heavy Pearls"))exitNode=node;}for(var entry:root.entrySet()){Node node=nodes.get(Integer.parseInt(entry.getKey()));JsonObject value=entry.getValue().getAsJsonObject();if(!value.has("Neighbours"))continue;for(var edge:value.getAsJsonObject("Neighbours").entrySet()){Node to=nodes.get(Integer.parseInt(edge.getKey()));if(to!=null)node.edges.add(new Edge(to,edge.getValue().getAsDouble()));}}graph=Map.copyOf(nodes);ConstellationClient.LOGGER.info("loaded Matriarch navigation graph with {} nodes",graph.size());}catch(Exception e){graph=Map.of();exitNode=null;ConstellationClient.LOGGER.error("failed to load Matriarch navigation graph",e);}}
    private static Node nearest(Map<Integer,Node> nodes,Vec3 pos){Node best=null;double distance=Double.POSITIVE_INFINITY;for(Node node:nodes.values()){double d=node.pos.distanceToSqr(pos);if(d<distance){distance=d;best=node;}}return best;}
    private static Route shortest(Node start,Node end,int maxVisited){if(start==null||end==null)return null;Map<Node,Double> distance=new HashMap<>();Map<Node,Node> previous=new HashMap<>();PriorityQueue<QueueNode> queue=new PriorityQueue<>(Comparator.comparingDouble(QueueNode::distance));distance.put(start,0.0);queue.add(new QueueNode(start,0));int visited=0;while(!queue.isEmpty()&&visited++<maxVisited){QueueNode current=queue.poll();if(current.distance()>distance.getOrDefault(current.node(),Double.POSITIVE_INFINITY))continue;if(current.node()==end)break;for(Edge edge:current.node().edges){double next=current.distance()+edge.weight();if(next>=distance.getOrDefault(edge.to(),Double.POSITIVE_INFINITY))continue;distance.put(edge.to(),next);previous.put(edge.to(),current.node());queue.add(new QueueNode(edge.to(),next));}}Double total=distance.get(end);if(total==null)return null;List<Node> path=new ArrayList<>();for(Node node=end;node!=null;node=previous.get(node)){path.add(node);if(node==start)break;}Collections.reverse(path);return new Route(List.copyOf(path),total);}
    private static boolean active(){if(cfg==null||!cfg.enabled||!cfg.matriarchHelper||ConstellationClient.loc().area()!=SkyblockArea.CRIMSON_ISLE)return false;Minecraft mc=Minecraft.getInstance();return mc.player!=null&&mc.player.position().distanceToSqr(BELLY)<=Math.pow(Math.clamp(cfg.matriarchScanRange,24,128),2);}
    private static String clean(String s){String out=ChatFormatting.stripFormatting(s);return out==null?"":out.trim();}
    private static String on(boolean v){return v?"on":"off";}
    private static void save(){ConstellationClient.saveConfig();}
    private static void local(String text){Minecraft mc=Minecraft.getInstance();if(mc.player!=null)mc.player.sendSystemMessage(Component.literal("\u00a75[Matriarch] \u00a7f"+text));}
    private static void clear(){PEARLS.clear();ORDERED.clear();EXIT_PATH.clear();routeDistance=0;lastPearlSeen=0;lastBuild=0;routeSignature="";lastPlayer=null;}
}
