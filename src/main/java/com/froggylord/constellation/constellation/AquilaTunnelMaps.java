package com.froggylord.constellation.constellation;

import com.froggylord.constellation.ConstellationClient;
import com.froggylord.constellation.config.AquilaConfig;
import com.froggylord.constellation.render.WorldRenderer;
import com.froggylord.constellation.ui.TunnelMapScreen;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// ported from SkyHanni (LGPL-3.0-or-later): features/mining/TunnelsMaps.kt
// ported from SkyHanni (LGPL-3.0-or-later): utils/GraphUtils.kt
// data ported from SkyHanni Repo (MIT): constants/island_graphs/GLACITE_TUNNELS.json
public final class AquilaTunnelMaps {
    private record Edge(Node to,double weight) {}
    private static final class Node {
        final int id;final Vec3 pos;final String styledName;final String name;final List<Edge> edges=new ArrayList<>();
        Node(int id,Vec3 pos,String styledName){this.id=id;this.pos=pos;this.styledName=styledName;this.name=clean(styledName);}
    }
    private record Route(List<Node> nodes,double distance) {}
    private record QueueNode(Node node,double distance) {}
    private static final Pattern COLLECTOR=Pattern.compile("^(\\w+(?: \\w+)?) Collector$");
    private static final Pattern GLACITE_REWARD=Pattern.compile("^- [\\d,]+ Glacite Powder$");
    private static final Map<Integer,Node> NODES=new LinkedHashMap<>();
    private static final Map<String,List<Node>> DESTINATIONS=new LinkedHashMap<>();
    private static final Map<Node,Long> COOLDOWNS=new HashMap<>();
    private static AquilaConfig cfg;
    private static KeyMapping menuKey,campfireKey,nextKey;
    private static boolean initialized,loaded,commissionTarget;
    private static String active="";
    private static Node goal,previousGoal,closest,campfire;
    private static Route route;
    private static Vec3 lastPathPlayer;
    private static int ticks;
    private static long lastNextAt,lastWarpAt;

    private AquilaTunnelMaps() {}

    public static void init(AquilaConfig config){
        cfg=config;load();if(initialized)return;initialized=true;
        menuKey=ConstellationClient.instance().keys().register("tunnel_map_menu",com.mojang.blaze3d.platform.InputConstants.UNKNOWN.getValue());
        campfireKey=ConstellationClient.instance().keys().register("tunnel_map_campfire",com.mojang.blaze3d.platform.InputConstants.UNKNOWN.getValue());
        nextKey=ConstellationClient.instance().keys().register("tunnel_map_next",com.mojang.blaze3d.platform.InputConstants.UNKNOWN.getValue());
        ClientTickEvents.END_CLIENT_TICK.register(AquilaTunnelMaps::tick);
    }

    private static void load(){
        if(loaded)return;loaded=true;
        try(var stream=AquilaTunnelMaps.class.getResourceAsStream("/assets/constellation/mining/glaciteTunnelsGraph.json")){
            if(stream==null)throw new IllegalStateException("missing Glacite Tunnels graph");
            JsonObject root=JsonParser.parseReader(new InputStreamReader(stream,StandardCharsets.UTF_8)).getAsJsonObject();
            for(var entry:root.entrySet()){
                int id=Integer.parseInt(entry.getKey());JsonObject value=entry.getValue().getAsJsonObject();
                String[] xyz=value.get("Position").getAsString().split(":");
                String name=value.has("Name")?value.get("Name").getAsString():"";
                NODES.put(id,new Node(id,new Vec3(Double.parseDouble(xyz[0]),Double.parseDouble(xyz[1]),Double.parseDouble(xyz[2])),name));
            }
            for(var entry:root.entrySet()){
                Node node=NODES.get(Integer.parseInt(entry.getKey()));JsonObject value=entry.getValue().getAsJsonObject();
                if(value.has("Neighbours"))for(var edge:value.getAsJsonObject("Neighbours").entrySet()){
                    Node target=NODES.get(Integer.parseInt(edge.getKey()));if(target!=null)node.edges.add(new Edge(target,edge.getValue().getAsDouble()));
                }
                if(node.styledName.startsWith("§"))DESTINATIONS.computeIfAbsent(node.name,key->new ArrayList<>()).add(node);
            }
            campfire=DESTINATIONS.getOrDefault("Campfire",List.of()).stream().findFirst().orElse(null);
            for(var entry:new ArrayList<>(DESTINATIONS.entrySet()))DESTINATIONS.put(entry.getKey(),List.copyOf(entry.getValue()));
            ConstellationClient.LOGGER.info("Loaded Glacite Tunnels graph: {} nodes, {} destinations",NODES.size(),DESTINATIONS.size());
        }catch(Exception e){NODES.clear();DESTINATIONS.clear();ConstellationClient.LOGGER.error("Could not load Glacite Tunnels graph",e);}
    }

    private static void tick(Minecraft mc){
        if(mc.gui.screen()==null){
            while(menuKey.consumeClick())open();
            while(campfireKey.consumeClick())campfire();
            while(nextKey.consumeClick())next();
        }
        if(!active()){if(route!=null||closest!=null)clearTransient();return;}
        if(++ticks%5!=0||mc.player==null)return;
        Node nearest=nearest(mc.player.position());
        boolean moved=lastPathPlayer==null||lastPathPlayer.distanceToSqr(mc.player.position())>9;
        if(nearest!=closest||goal!=previousGoal||moved){closest=nearest;rebuild(mc.player.position());}
        checkArrival(mc.player.position());
        parseCommissionScreen(mc);
    }

    private static void rebuild(Vec3 player){
        previousGoal=goal;lastPathPlayer=player;
        if(closest==null||goal==null){route=null;return;}
        Route graphRoute=shortest(closest,goal);if(graphRoute==null){route=null;return;}
        List<Node> nodes=new ArrayList<>(graphRoute.nodes);double distance=graphRoute.distance+player.distanceTo(closest.pos);
        if(nodes.size()>1){
            Node second=nodes.get(1);double direct=player.distanceTo(second.pos);
            double firstEdge=edgeWeight(nodes.get(0),second);
            if(direct<player.distanceTo(nodes.get(0).pos)+firstEdge){nodes.remove(0);distance=graphRoute.distance-firstEdge+direct;}
        }
        route=new Route(List.copyOf(nodes),distance);
    }

    private static Route shortest(Node start,Node end){
        Map<Node,Double> dist=new HashMap<>();Map<Node,Node> previous=new HashMap<>();
        PriorityQueue<QueueNode> queue=new PriorityQueue<>(Comparator.comparingDouble(QueueNode::distance));
        dist.put(start,0.0);queue.add(new QueueNode(start,0));
        while(!queue.isEmpty()){
            QueueNode current=queue.poll();if(current.distance>dist.getOrDefault(current.node,Double.POSITIVE_INFINITY))continue;
            if(current.node==end)break;
            for(Edge edge:current.node.edges){double next=current.distance+edge.weight;if(next>=dist.getOrDefault(edge.to,Double.POSITIVE_INFINITY))continue;dist.put(edge.to,next);previous.put(edge.to,current.node);queue.add(new QueueNode(edge.to,next));}
        }
        Double total=dist.get(end);if(total==null)return null;
        ArrayList<Node> path=new ArrayList<>();for(Node node=end;node!=null;node=previous.get(node)){path.add(node);if(node==start)break;}
        java.util.Collections.reverse(path);return new Route(List.copyOf(path),total);
    }

    private static Node select(String name){
        List<Node> choices=DESTINATIONS.get(name);if(choices==null||choices.isEmpty()||closest==null)return choices==null||choices.isEmpty()?null:choices.get(0);
        long now=System.currentTimeMillis();List<Node> available=choices.stream().filter(node->COOLDOWNS.getOrDefault(node,0L)<=now).toList();
        List<Node> pool=available.isEmpty()?choices:available;Node best=null;double bestDistance=Double.POSITIVE_INFINITY;
        for(Node node:pool){Route candidate=shortest(closest,node);double distance=candidate==null?Double.POSITIVE_INFINITY:candidate.distance;if(distance<bestDistance){best=node;bestDistance=distance;}}
        if(best!=null&&available.contains(best))COOLDOWNS.put(best,now+Math.clamp(cfg.tunnelMapsSelectionCooldownSeconds,5,300)*1000L);
        return best==null?pool.get(0):best;
    }

    public static void draw(WorldRenderer.Ctx ctx){
        if(!active()||route==null||route.nodes.isEmpty())return;
        int color=pathColor();List<Node> nodes=route.nodes;Minecraft mc=Minecraft.getInstance();
        Vec3 from=mc.player==null?nodes.get(0).pos:mc.player.position().add(0,.2,0);
        int limit=Math.min(nodes.size(),Math.clamp(cfg.tunnelMapsLookAheadNodes,2,825));
        for(int i=0;i<limit;i++){Vec3 to=nodes.get(i).pos;ctx.line(from,to,color,cfg.tunnelMapsThroughWalls,Math.clamp(cfg.tunnelMapsPathWidth,1,15));from=to;}
        Node end=nodes.get(nodes.size()-1);
        if(cfg.tunnelMapsGoalBox)ctx.outline(new AABB(end.pos.x-.5,end.pos.y,end.pos.z-.5,end.pos.x+.5,end.pos.y+1,end.pos.z+.5),color,cfg.tunnelMapsThroughWalls,Math.clamp(cfg.tunnelMapsGoalOutlineWidth,1,10));
        if(cfg.tunnelMapsGoalBeam)ctx.beam(end.pos.x,end.pos.y,end.pos.z,color,Math.clamp(cfg.tunnelMapsBeamHeight,2,100),cfg.tunnelMapsThroughWalls);
        Vec3 labelNode=cfg.tunnelMapsDistanceFirst?nodes.get(0).pos:end.pos;
        if(cfg.tunnelMapsGoalLabel)ctx.label(end.pos.add(0,1.4,0),active,color,cfg.tunnelMapsThroughWalls);
        if(cfg.tunnelMapsDistanceLabel)ctx.label(labelNode.add(0,1.1,0),Math.round(route.distance)+"m",color,cfg.tunnelMapsThroughWalls);
        if(cfg.tunnelMapsNodeLabels)for(int i=0;i<limit;i++){Node node=nodes.get(i);if(!node.name.isBlank())ctx.label(node.pos.add(0,.6,0),node.name,color,cfg.tunnelMapsThroughWalls);}
    }

    private static void checkArrival(Vec3 player){
        if(goal==null)return;double range=goal==campfire?Math.clamp(cfg.tunnelMapsCampfireArrivalRange,6,30):Math.clamp(cfg.tunnelMapsArrivalRange,2,16);
        if(player.distanceToSqr(goal.pos)>=range*range)return;
        if(goal==campfire&&!active.equals("Campfire")){setNextGoal();return;}
        COOLDOWNS.put(goal,System.currentTimeMillis()+Math.clamp(cfg.tunnelMapsArrivalCooldownSeconds,10,300)*1000L);
        if(cfg.tunnelMapsArrivalMessage)local("Reached "+active+".");clearPath(false);
    }

    public static boolean onAttack(){
        if(!active()||!cfg.tunnelMapsLeftClickPigeon)return false;
        Minecraft mc=Minecraft.getInstance();if(mc.player==null||!"ROYAL_PIGEON".equals(LyraTooltips.marketId(mc.player.getMainHandItem())))return false;
        next();return true;
    }

    public static void onSlotClick(AbstractContainerScreen<?> screen,Slot slot,int button,ContainerInput input){
        if(!active()||button!=1||slot==null||!clean(screen.getTitle().getString()).equals("Commissions"))return;
        String target=commissionTarget(slot.getItem());if(target.isBlank())return;commissionTarget=true;setActive(target);
    }

    public static void drawSlot(GuiGraphicsExtractor graphics,AbstractContainerScreen<?> screen,Slot slot){
        if(!active()||slot==null||!clean(screen.getTitle().getString()).equals("Commissions")||commissionTarget(slot.getItem()).isBlank())return;
        int c=cfg.tunnelMapsCommissionColor|0xFF000000;graphics.fill(slot.x,slot.y,slot.x+16,slot.y+1,c);graphics.fill(slot.x,slot.y+15,slot.x+16,slot.y+16,c);
        graphics.text(Minecraft.getInstance().font,"R",slot.x+10,slot.y+1,c,true);
    }

    public static List<Component> appendTooltip(AbstractContainerScreen<?> screen,ItemStack stack,List<Component> current){
        if(!active()||!clean(screen.getTitle().getString()).equals("Commissions"))return current;
        String target=commissionTarget(stack);if(target.isBlank())return current;
        ArrayList<Component> out=new ArrayList<>(current);out.add(Component.literal("§eRight-click to route to "+target));return out;
    }

    private static void parseCommissionScreen(Minecraft mc){
        if(!(mc.gui.screen() instanceof AbstractContainerScreen<?> screen)||!clean(screen.getTitle().getString()).equals("Commissions"))return;
        List<String> targets=new ArrayList<>();for(Slot slot:screen.getMenu().slots){String target=commissionTarget(slot.getItem());if(!target.isBlank())targets.add(target);}
        if(cfg.tunnelMapsAutoCommission&&!targets.isEmpty()){String first=targets.get(0);if(!commissionTarget||!active.equals(first)){commissionTarget=true;setActive(first);}}
        else if(cfg.tunnelMapsAutoCommission&&commissionTarget&&targets.isEmpty()){commissionTarget=false;clearPath(true);}
    }

    private static String commissionTarget(ItemStack stack){
        if(stack==null||stack.isEmpty())return"";List<String> lore=lore(stack);
        if(lore.stream().noneMatch(line->GLACITE_REWARD.matcher(line).matches())||lore.stream().anyMatch(line->line.equals("COMPLETED")))return"";
        for(String line:lore){Matcher matcher=COLLECTOR.matcher(line);if(!matcher.matches())continue;String type=matcher.group(1);if(type.equals("Glacite")||type.equals("Scrap"))return"";return generic(type);}
        return"";
    }

    private static String generic(String input){String needle=input.toLowerCase(Locale.ROOT);return DESTINATIONS.keySet().stream().filter(name->name.toLowerCase(Locale.ROOT).contains(needle)).findFirst().orElse("");}
    public static List<String> destinations(){return DESTINATIONS.keySet().stream().filter(name->cfg==null||!cfg.tunnelMapsExcludeFairy||!name.startsWith("Fairy Soul")).sorted(String.CASE_INSENSITIVE_ORDER).toList();}
    public static String activeDestination(){return active;}
    public static String routeStatus(){return route==null?"No route":route.nodes.size()+" nodes, "+Math.round(route.distance)+"m";}
    public static boolean usable(){return active();}
    public static AquilaConfig config(){return cfg;}
    public static void open(){Minecraft mc=Minecraft.getInstance();if(mc.player==null)return;if(!active()){local("Tunnel Maps is only available in the Glacite Tunnels.");return;}mc.setScreenAndShow(new TunnelMapScreen(mc.gui.screen()));}
    public static void choose(String name){commissionTarget=false;setActive(name);}
    public static void clear(){commissionTarget=false;clearPath(true);}
    public static void nextFromUi(){next();}
    public static void campfireFromUi(){campfire();}

    private static void setActive(String name){
        if(!DESTINATIONS.containsKey(name)){local("Unknown tunnel destination.");return;}
        active=name;goal=select(name);previousGoal=null;route=null;
    }
    private static void setNextGoal(){long now=System.currentTimeMillis();if(now-lastNextAt<500)return;lastNextAt=now;goal=select(active);previousGoal=null;route=null;}
    private static void next(){if(active.isBlank()){local("Select a tunnel destination first.");return;}setNextGoal();}
    private static void campfire(){if(!active()){local("Tunnel Maps is only available in the Glacite Tunnels.");return;}long now=System.currentTimeMillis();if(now-lastWarpAt<2000)return;lastWarpAt=now;if(cfg.tunnelMapsTravelScroll){Minecraft mc=Minecraft.getInstance();if(mc.player!=null)mc.player.connection.sendCommand("warp basecamp");}else{goal=campfire;previousGoal=null;route=null;}}
    private static void clearPath(boolean clearActive){route=null;goal=null;previousGoal=null;lastPathPlayer=null;if(clearActive)active="";}
    private static void clearTransient(){clearPath(false);closest=null;COOLDOWNS.clear();}
    private static Node nearest(Vec3 pos){Node best=null;double distance=Double.POSITIVE_INFINITY;for(Node node:NODES.values()){double next=node.pos.distanceToSqr(pos);if(next<distance){distance=next;best=node;}}return best;}
    private static double edgeWeight(Node from,Node to){for(Edge edge:from.edges)if(edge.to==to)return edge.weight;return from.pos.distanceTo(to.pos);}
    private static int pathColor(){if(cfg.tunnelMapsDynamicPathColor&&goal!=null&&!goal.styledName.isBlank()){int color=colorCode(goal.styledName);if(color!=0xFFFFFFFF)return color;}return cfg.tunnelMapsPathColor;}
    private static int colorCode(String text){int index=text.indexOf('§');if(index<0||index+1>=text.length())return 0xFFFFFFFF;return switch(Character.toLowerCase(text.charAt(index+1))){case'0'->0xFF000000;case'1'->0xFF0000AA;case'2'->0xFF00AA00;case'3'->0xFF00AAAA;case'4'->0xFFAA0000;case'5'->0xFFAA00AA;case'6'->0xFFFFAA00;case'7'->0xFFAAAAAA;case'8'->0xFF555555;case'9'->0xFF5555FF;case'a'->0xFF55FF55;case'b'->0xFF55FFFF;case'c'->0xFFFF5555;case'd'->0xFFFF55FF;case'e'->0xFFFFFF55;default->0xFFFFFFFF;};}
    private static boolean active(){return cfg!=null&&cfg.enabled&&cfg.tunnelMaps&&loaded&&!NODES.isEmpty()&&ConstellationClient.loc().onHypixel()&&ConstellationClient.loc().area()==com.froggylord.constellation.core.LocationManager.SkyblockArea.GLACITE_TUNNELS;}
    private static List<String> lore(ItemStack stack){ItemLore lore=stack.get(DataComponents.LORE);return lore==null?List.of():lore.lines().stream().map(line->clean(line.getString())).toList();}
    private static String clean(String raw){String value=ChatFormatting.stripFormatting(raw);return value==null?"":value.trim().replaceAll("\\s+"," ");}
    private static void save(){ConstellationClient.saveConfig();}
    private static void local(String text){Minecraft mc=Minecraft.getInstance();if(mc.player!=null)mc.player.sendSystemMessage(Component.literal("§b[Tunnel Maps] §f"+text));}

    public static void registerCommands(CommandDispatcher<FabricClientCommandSource> dispatcher){
        dispatcher.register(LiteralArgumentBuilder.<FabricClientCommandSource>literal("tunnelmap").executes(c->{open();return 1;})
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("menu").executes(c->{open();return 1;}))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("status").executes(c->status()))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("clear").executes(c->{clear();return status();}))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("next").executes(c->{next();return status();}))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("campfire").executes(c->{campfire();return status();}))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("select").then(RequiredArgumentBuilder.<FabricClientCommandSource,String>argument("destination",StringArgumentType.greedyString()).executes(c->{choose(StringArgumentType.getString(c,"destination"));return status();})))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("color").then(RequiredArgumentBuilder.<FabricClientCommandSource,String>argument("argb",StringArgumentType.word()).executes(c->color(StringArgumentType.getString(c,"argb")))))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("number").then(RequiredArgumentBuilder.<FabricClientCommandSource,String>argument("name",StringArgumentType.word()).then(RequiredArgumentBuilder.<FabricClientCommandSource,Integer>argument("value",com.mojang.brigadier.arguments.IntegerArgumentType.integer(1,825)).executes(c->number(StringArgumentType.getString(c,"name"),com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(c,"value"))))))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("option").then(RequiredArgumentBuilder.<FabricClientCommandSource,String>argument("name",StringArgumentType.word()).then(RequiredArgumentBuilder.<FabricClientCommandSource,String>argument("state",StringArgumentType.word()).executes(c->option(StringArgumentType.getString(c,"name"),StringArgumentType.getString(c,"state")))))));
    }
    private static int status(){local("helper "+on(cfg.tunnelMaps)+", "+NODES.size()+" graph nodes, active "+(active.isBlank()?"none":active)+", "+routeStatus()+".");return 1;}
    private static int color(String raw){Integer value=parseColor(raw);if(value==null){local("Color must be RRGGBB or AARRGGBB.");return 0;}cfg.tunnelMapsPathColor=value;save();return status();}
    private static int number(String name,int value){switch(name.toLowerCase(Locale.ROOT)){case"lookahead"->cfg.tunnelMapsLookAheadNodes=value;case"pathwidth"->cfg.tunnelMapsPathWidth=Math.clamp(value,1,15);case"arrival"->cfg.tunnelMapsArrivalRange=Math.clamp(value,2,16);case"campfirearrival"->cfg.tunnelMapsCampfireArrivalRange=Math.clamp(value,6,30);case"beamheight"->cfg.tunnelMapsBeamHeight=Math.clamp(value,2,100);case"cooldown"->cfg.tunnelMapsArrivalCooldownSeconds=Math.clamp(value,10,300);default->{local("Number must be lookahead, pathwidth, arrival, campfirearrival, beamheight, or cooldown.");return 0;}}save();return status();}
    private static int option(String name,String raw){Boolean value=parse(raw);if(value==null){local("State must be on or off.");return 0;}switch(name.toLowerCase(Locale.ROOT)){
        case"enabled"->cfg.tunnelMaps=value;case"auto"->cfg.tunnelMapsAutoCommission=value;case"travel"->cfg.tunnelMapsTravelScroll=value;case"pigeon"->cfg.tunnelMapsLeftClickPigeon=value;
        case"dynamic"->cfg.tunnelMapsDynamicPathColor=value;case"walls"->cfg.tunnelMapsThroughWalls=value;case"distancefirst"->cfg.tunnelMapsDistanceFirst=value;
        case"fairy"->cfg.tunnelMapsExcludeFairy=!value;case"box"->cfg.tunnelMapsGoalBox=value;case"beam"->cfg.tunnelMapsGoalBeam=value;
        case"label"->cfg.tunnelMapsGoalLabel=value;case"distance"->cfg.tunnelMapsDistanceLabel=value;case"nodes"->cfg.tunnelMapsNodeLabels=value;
        case"arrivalmessage"->cfg.tunnelMapsArrivalMessage=value;case"hud"->cfg.tunnelMapsHud=value;case"hudactive"->cfg.tunnelMapsHudActive=value;
        case"huddistance"->cfg.tunnelMapsHudDistance=value;case"hudnodes"->cfg.tunnelMapsHudNodes=value;default->{local("Unknown Tunnel Maps option.");return 0;}}
        save();return status();}
    private static Boolean parse(String raw){return switch(raw.toLowerCase(Locale.ROOT)){case"on","true","yes","1"->true;case"off","false","no","0"->false;default->null;};}
    private static Integer parseColor(String raw){try{String value=raw.replace("#","");if(value.length()==6)value="FF"+value;if(value.length()!=8)return null;return(int)Long.parseLong(value,16);}catch(Exception ignored){return null;}}
    private static String on(boolean value){return value?"on":"off";}
}
