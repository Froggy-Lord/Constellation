package com.froggylord.constellation.constellation;

import com.froggylord.constellation.ConstellationClient;
import com.froggylord.constellation.config.AurigaConfig;
import com.froggylord.constellation.core.LocationManager.SkyblockArea;
import com.froggylord.constellation.render.WorldRenderer;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;

// ported from SkyHanni (LGPL-3.0-or-later): data/IslandGraphs.kt, utils/GraphUtils.kt, data/model/graph/Graph.kt, data/model/graph/DijkstraTree.kt
// data ported from SkyHanni Repo (MIT): constants/island_graphs/*.json
public final class AurigaHoppityNavigation {
    private record Edge(Node to,double weight){}
    private static final class Node{final int id;final Vec3 pos;final List<Edge> edges=new ArrayList<>();Node(int id,Vec3 pos){this.id=id;this.pos=pos;}}
    private record QueueNode(Node node,double distance){}
    private record Route(List<Node> nodes,double distance){}
    private static final Map<SkyblockArea,String> FILES=new EnumMap<>(SkyblockArea.class);
    private static final Map<SkyblockArea,Map<Integer,Node>> CACHE=new EnumMap<>(SkyblockArea.class);
    private static AurigaConfig cfg;
    private static SkyblockArea area=SkyblockArea.UNKNOWN;
    private static Vec3 target,lastPlayer,lastTarget;
    private static Node nearest,targetNode;
    private static Route route;
    private static int ticks;

    static{
        FILES.put(SkyblockArea.BACKWATER_BAYOU,"BACKWATER_BAYOU");
        FILES.put(SkyblockArea.CRIMSON_ISLE,"CRIMSON_ISLE");
        FILES.put(SkyblockArea.CRYSTAL_HOLLOWS,"CRYSTAL_HOLLOWS");
        FILES.put(SkyblockArea.DEEP_CAVERNS,"DEEP_CAVERNS");
        FILES.put(SkyblockArea.DUNGEON_HUB,"DUNGEON_HUB");
        FILES.put(SkyblockArea.DWARVEN_MINES,"DWARVEN_MINES");
        FILES.put(SkyblockArea.GALATEA,"GALATEA");
        FILES.put(SkyblockArea.GOLD_MINE,"GOLD_MINES");
        FILES.put(SkyblockArea.HUB,"HUB");
        FILES.put(SkyblockArea.LOTUS_ATOLL,"LOTUS_ATOLL");
        FILES.put(SkyblockArea.SPIDER_DEN,"SPIDER_DEN");
        FILES.put(SkyblockArea.THE_END,"THE_END");
        FILES.put(SkyblockArea.FARMING_ISLANDS,"THE_FARMING_ISLANDS");
        FILES.put(SkyblockArea.PARK,"THE_PARK");
    }

    private AurigaHoppityNavigation(){}

    public static void init(AurigaConfig config){
        cfg=config;
        ConstellationClient.tick().every(2,"auriga-hoppity-navigation",AurigaHoppityNavigation::tick);
    }

    public static void target(Vec3 value){
        SkyblockArea now=ConstellationClient.loc().area();
        if(now!=area||!same(value,target)){area=now;target=value;resetRoute();}
    }

    private static void tick(){
        if(!active()){resetRoute();return;}
        Minecraft mc=Minecraft.getInstance();if(mc.player==null)return;
        Map<Integer,Node> graph=graph(area);if(graph.isEmpty())return;
        Vec3 player=mc.player.position();
        Node now=nearest(graph,player);
        boolean moved=lastPlayer==null||lastPlayer.distanceToSqr(player)>Math.pow(Math.clamp(cfg.hoppityEggPathRecalculateBlocks,1,16),2);
        boolean goalMoved=lastTarget==null||lastTarget.distanceToSqr(target)>.01;
        if(route==null||now!=nearest||moved||goalMoved||++ticks>=Math.clamp(cfg.hoppityEggPathRecalculateTicks,2,100)){
            ticks=0;nearest=now;lastPlayer=player;lastTarget=target;rebuild(graph);
        }
    }

    private static void rebuild(Map<Integer,Node> graph){
        if(nearest==null||target==null){route=null;return;}
        targetNode=nearest(graph,target);Route found=shortest(nearest,targetNode,Math.clamp(cfg.hoppityEggPathMaxVisitedNodes,100,20000));if(found==null){route=null;return;}
        List<Node> nodes=new ArrayList<>(found.nodes);double distance=found.distance+lastPlayer.distanceTo(nearest.pos)+targetNode.pos.distanceTo(target);
        if(nodes.size()>1){Node first=nodes.get(0),second=nodes.get(1);double edge=edgeWeight(first,second),direct=lastPlayer.distanceTo(second.pos),via=lastPlayer.distanceTo(first.pos)+edge;if(direct<via){nodes.remove(0);distance=distance-lastPlayer.distanceTo(first.pos)-edge+direct;}}
        route=new Route(List.copyOf(nodes),Math.max(0,distance));
    }

    // ported from SkyHanni (LGPL-3.0-or-later): utils/GraphUtils.kt findDijkstraDistances
    private static Route shortest(Node start,Node end,int maxVisited){
        Map<Node,Double> distance=new HashMap<>();Map<Node,Node> previous=new HashMap<>();PriorityQueue<QueueNode> queue=new PriorityQueue<>(Comparator.comparingDouble(QueueNode::distance));distance.put(start,0.0);queue.add(new QueueNode(start,0));int visited=0;
        while(!queue.isEmpty()&&visited++<maxVisited){QueueNode current=queue.poll();if(current.distance>distance.getOrDefault(current.node,Double.POSITIVE_INFINITY))continue;if(current.node==end)break;for(Edge edge:current.node.edges){double next=current.distance+edge.weight;if(next>=distance.getOrDefault(edge.to,Double.POSITIVE_INFINITY))continue;distance.put(edge.to,next);previous.put(edge.to,current.node);queue.add(new QueueNode(edge.to,next));}}
        Double total=distance.get(end);if(total==null)return null;List<Node> path=new ArrayList<>();for(Node node=end;node!=null;node=previous.get(node)){path.add(node);if(node==start)break;}Collections.reverse(path);return new Route(List.copyOf(path),total);
    }

    public static void draw(WorldRenderer.Ctx ctx){
        if(!active()||route==null||route.nodes.isEmpty())return;
        Minecraft mc=Minecraft.getInstance();if(mc.player==null)return;
        int limit=Math.min(route.nodes.size(),Math.clamp(cfg.hoppityEggPathLookAheadNodes,2,5000));Vec3 from=mc.player.position().add(0,.2,0);
        for(int i=0;i<limit;i++){Vec3 to=route.nodes.get(i).pos;ctx.line(from,to,cfg.hoppityEggPathColor,cfg.hoppityEggPathThroughWalls,Math.clamp(cfg.hoppityEggPathWidth,1,15));from=to;}
        if(limit==route.nodes.size())ctx.line(from,target.add(.5,.5,.5),cfg.hoppityEggPathColor,cfg.hoppityEggPathThroughWalls,Math.clamp(cfg.hoppityEggPathWidth,1,15));
    }

    public static int nodes(){Map<Integer,Node> graph=CACHE.get(area);return graph==null?0:graph.size();}
    public static int routeNodes(){return route==null?0:route.nodes.size();}
    public static long routeDistance(){return route==null?0:Math.round(route.distance);}
    public static boolean supported(){return FILES.containsKey(ConstellationClient.loc().area());}
    public static void refresh(){resetRoute();}

    private static Map<Integer,Node> graph(SkyblockArea key){
        if(CACHE.containsKey(key))return CACHE.get(key);
        String file=FILES.get(key);if(file==null)return Map.of();Map<Integer,Node> nodes=new LinkedHashMap<>();
        try(var stream=AurigaHoppityNavigation.class.getResourceAsStream("/assets/constellation/hoppity/island_graphs/"+file+".json")){
            if(stream==null)throw new IllegalStateException("missing island graph "+file);
            JsonObject root=JsonParser.parseReader(new InputStreamReader(stream,StandardCharsets.UTF_8)).getAsJsonObject();
            for(var entry:root.entrySet()){JsonObject value=entry.getValue().getAsJsonObject();String[] xyz=value.get("Position").getAsString().split(":");nodes.put(Integer.parseInt(entry.getKey()),new Node(Integer.parseInt(entry.getKey()),new Vec3(Double.parseDouble(xyz[0]),Double.parseDouble(xyz[1]),Double.parseDouble(xyz[2]))));}
            int edges=0;for(var entry:root.entrySet()){Node node=nodes.get(Integer.parseInt(entry.getKey()));JsonObject value=entry.getValue().getAsJsonObject();if(!value.has("Neighbours"))continue;for(var edge:value.getAsJsonObject("Neighbours").entrySet()){Node to=nodes.get(Integer.parseInt(edge.getKey()));if(to!=null){node.edges.add(new Edge(to,edge.getValue().getAsDouble()));edges++;}}}
            Map<Integer,Node> immutable=Map.copyOf(nodes);CACHE.put(key,immutable);ConstellationClient.LOGGER.info("loaded Hoppity navigation graph {}: {} nodes, {} edges",file,nodes.size(),edges);return immutable;
        }catch(Exception e){ConstellationClient.LOGGER.error("failed to load Hoppity navigation graph "+file,e);CACHE.put(key,Map.of());return Map.of();}
    }

    private static Node nearest(Map<Integer,Node> graph,Vec3 position){Node best=null;double distance=Double.POSITIVE_INFINITY;for(Node node:graph.values()){double current=node.pos.distanceToSqr(position);if(current<distance){distance=current;best=node;}}return best;}
    private static double edgeWeight(Node from,Node to){for(Edge edge:from.edges)if(edge.to==to)return edge.weight;return from.pos.distanceTo(to.pos);}
    private static boolean same(Vec3 a,Vec3 b){return a==b||a!=null&&b!=null&&a.distanceToSqr(b)<.01;}
    private static boolean active(){return cfg!=null&&cfg.enabled&&cfg.hoppityEggWaypoints&&cfg.hoppityEggPath&&target!=null&&FILES.containsKey(ConstellationClient.loc().area());}
    private static void resetRoute(){nearest=null;targetNode=null;route=null;lastPlayer=null;lastTarget=null;ticks=0;}
}
