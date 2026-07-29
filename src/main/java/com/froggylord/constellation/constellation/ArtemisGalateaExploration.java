package com.froggylord.constellation.constellation;

import com.froggylord.constellation.ConstellationClient;
import com.froggylord.constellation.config.ArtemisConfig;
import com.froggylord.constellation.core.LocationManager.SkyblockArea;
import com.froggylord.constellation.network.BlockStateUpdate;
import com.froggylord.constellation.render.WorldRenderer;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundLevelParticlesPacket;
import net.minecraft.world.entity.Display;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SeaPickleBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.*;

// ported from Skyblocker (LGPL-3.0-only): skyblock/galatea/ForestNodes.java
// ported from Skyblocker (LGPL-3.0-only): skyblock/galatea/TerracottaPuzzle.java
// ported from Skyblocker (LGPL-3.0-only): skyblock/galatea/{AbstractBlockHighlighter,LushlilacHighlighter,SeaLumiesHighlighter}.java
public final class ArtemisGalateaExploration {
    private enum Resource { LUSHLILAC,SEA_LUMIES }
    private record Node(BlockPos pos,long confirmed){}
    private record Rotation(BlockPos pos,int clicks){}
    private static final BlockPos WALL_TOP_LEFT=new BlockPos(-633,66,85);
    private static final BlockPos WALL_BOTTOM_RIGHT=new BlockPos(-640,65,85);
    private static final BlockPos FLOOR_TOP_LEFT=new BlockPos(-633,59,76);
    private static final BlockPos FLOOR_BOTTOM_LEFT=new BlockPos(-633,59,75);
    private static final Map<Direction,Direction> DIRECTION_MAP=Map.of(
        Direction.NORTH,Direction.WEST,Direction.EAST,Direction.SOUTH,
        Direction.SOUTH,Direction.EAST,Direction.WEST,Direction.NORTH);
    private static final Map<BlockPos,Node> NODES=new LinkedHashMap<>();
    private static final Map<BlockPos,Resource> RESOURCES=new LinkedHashMap<>();
    private static ArtemisConfig cfg;
    private static boolean initialized;
    private static Object levelIdentity;
    private static int chunkCursor;

    private ArtemisGalateaExploration(){}

    public static void init(ArtemisConfig config){
        cfg=config;if(initialized)return;initialized=true;
        ConstellationClient.instance().packets().register(packet->{
            if(packet instanceof ClientboundLevelParticlesPacket particles)onParticle(particles);
            else if(packet instanceof BlockStateUpdate update)onBlock(update);
        });
        ConstellationClient.tick().every(1,"artemis-galatea-exploration",ArtemisGalateaExploration::tick);
        ClientPlayConnectionEvents.JOIN.register((a,b,c)->reset());
        ClientPlayConnectionEvents.DISCONNECT.register((a,b)->reset());
    }

    private static void tick(){
        Minecraft mc=Minecraft.getInstance();
        if(mc.level!=levelIdentity){levelIdentity=mc.level;clear();}
        if(!active()||mc.level==null||mc.player==null){if(!active())clear();return;}
        long now=System.currentTimeMillis();
        NODES.entrySet().removeIf(entry->now-entry.getValue().confirmed>5000||countStrings(mc.level,entry.getKey())!=3&&now-entry.getValue().confirmed>2000);
        scanNextChunk(mc);
        int range=Math.clamp(cfg.galateaExplorationRange,8,192)+32;
        RESOURCES.keySet().removeIf(pos->mc.player.distanceToSqr(Vec3.atCenterOf(pos))>range*range||!matches(mc.level.getBlockState(pos)));
    }

    private static void scanNextChunk(Minecraft mc){
        int radius=Math.clamp(cfg.galateaExplorationChunkRadius,1,8),width=radius*2+1,total=width*width,index=Math.floorMod(chunkCursor++,total);
        int cx=mc.player.getBlockX()>>4,cz=mc.player.getBlockZ()>>4;
        int x=cx+(index%width)-radius,z=cz+(index/width)-radius;
        if(!mc.level.hasChunk(x,z))return;
        LevelChunk chunk=mc.level.getChunk(x,z);
        chunk.findBlocks(ArtemisGalateaExploration::matches,(pos,state)->putResource(pos,state));
    }

    private static boolean matches(BlockState state){
        return state.is(Blocks.FLOWERING_AZALEA)||state.is(Blocks.SEA_PICKLE);
    }

    private static void putResource(BlockPos pos,BlockState state){
        if(state.is(Blocks.FLOWERING_AZALEA))RESOURCES.put(pos.immutable(),Resource.LUSHLILAC);
        else if(state.is(Blocks.SEA_PICKLE)&&state.hasProperty(SeaPickleBlock.PICKLES)
            &&state.getValue(SeaPickleBlock.PICKLES)>=Math.clamp(cfg.galateaSeaLumiesMinimum,1,4))RESOURCES.put(pos.immutable(),Resource.SEA_LUMIES);
        else RESOURCES.remove(pos);
    }

    private static void onBlock(BlockStateUpdate update){
        if(!active())return;
        if(matches(update.newState()))putResource(update.pos(),update.newState());
        else RESOURCES.remove(update.pos());
    }

    private static void onParticle(ClientboundLevelParticlesPacket packet){
        if(!active()||!cfg.galateaForestNodes||packet.getParticle().getType()!=ParticleTypes.HAPPY_VILLAGER)return;
        Minecraft mc=Minecraft.getInstance();if(mc.level==null||mc.player==null)return;
        BlockPos pos=BlockPos.containing(packet.getX(),packet.getY()-1,packet.getZ());
        int range=Math.clamp(cfg.galateaExplorationRange,8,192);
        if(mc.player.distanceToSqr(Vec3.atCenterOf(pos))<=range*range&&countStrings(mc.level,pos)==3)
            NODES.put(pos,new Node(pos,System.currentTimeMillis()));
    }

    private static int countStrings(ClientLevel level,BlockPos pos){
        return (int)level.getEntitiesOfClass(Display.ItemDisplay.class,AABB.ofSize(Vec3.atCenterOf(pos),1,1,1),entity->{
            var state=entity.itemRenderState();
            return state!=null&&!state.itemStack().isEmpty()&&state.itemStack().is(Items.STRING);
        }).stream().limit(4).count();
    }

    public static void draw(WorldRenderer.Ctx ctx){
        if(!active())return;Minecraft mc=Minecraft.getInstance();if(mc.player==null||mc.level==null)return;
        int range=Math.clamp(cfg.galateaExplorationRange,8,192);double range2=range*range;boolean walls=cfg.galateaExplorationThroughWalls;
        if(cfg.galateaForestNodes)for(Node node:List.copyOf(NODES.values())){
            Vec3 center=Vec3.atCenterOf(node.pos);double distance=mc.player.position().distanceTo(center);if(distance*distance>range2)continue;
            if(cfg.galateaNodeBox)ctx.highlight(new AABB(node.pos),cfg.galateaNodeColor,walls);
            if(cfg.galateaNodeBeam)ctx.beam(center.x,node.pos.getY(),center.z,cfg.galateaNodeColor,Math.clamp(cfg.galateaNodeBeamHeight,1,64),walls);
            if(cfg.galateaNodeLine)ctx.line(mc.player.getEyePosition(),center,cfg.galateaNodeColor,walls);
            if(cfg.galateaNodeLabel)ctx.label(center.add(0,.8,0),"Forest Node"+(cfg.galateaNodeDistance?" "+Math.round(distance)+"m":""),cfg.galateaNodeColor,walls);
        }
        for(var entry:List.copyOf(RESOURCES.entrySet())){
            Resource type=entry.getValue();if(type==Resource.LUSHLILAC&&!cfg.galateaLushlilac||type==Resource.SEA_LUMIES&&!cfg.galateaSeaLumies)continue;
            BlockPos pos=entry.getKey();Vec3 center=Vec3.atCenterOf(pos);double distance=mc.player.position().distanceTo(center);if(distance*distance>range2)continue;
            int color=type==Resource.LUSHLILAC?cfg.galateaLushlilacColor:cfg.galateaSeaLumiesColor;
            if(cfg.galateaResourceBoxes)ctx.highlight(new AABB(pos),color,walls);
            if(cfg.galateaResourceBeams)ctx.beam(center.x,pos.getY(),center.z,color,Math.clamp(cfg.galateaResourceBeamHeight,1,64),walls);
            if(cfg.galateaResourceLabels)ctx.label(center.add(0,.8,0),(type==Resource.LUSHLILAC?"Lushlilac":"Sea Lumies")+(cfg.galateaResourceDistance?" "+Math.round(distance)+"m":""),color,walls);
        }
        if(cfg.galateaTempleSolver&&mc.player.distanceToSqr(Vec3.atCenterOf(FLOOR_TOP_LEFT))<=range2)
            for(Rotation rotation:solve(mc.level))drawRotation(ctx,rotation,walls);
    }

    private static List<Rotation> solve(ClientLevel level){
        List<Direction> targets=new ArrayList<>();
        for(int x=WALL_TOP_LEFT.getX();x>=WALL_BOTTOM_RIGHT.getX();x--)for(int y=WALL_TOP_LEFT.getY();y>=WALL_BOTTOM_RIGHT.getY();y--){
            BlockState state=level.getBlockState(new BlockPos(x,y,WALL_TOP_LEFT.getZ()));
            if(!orange(state))return List.of();
            Direction mapped=DIRECTION_MAP.get(state.getValue(BlockStateProperties.HORIZONTAL_FACING));if(mapped==null)return List.of();targets.add(mapped);
        }
        if(targets.size()!=16)return List.of();List<Rotation> out=new ArrayList<>();
        for(int i=0;i<targets.size();i++){
            BlockPos pos=floor(i);BlockState state=level.getBlockState(pos);if(!orange(state))return List.of();
            int clicks=clicks(state.getValue(BlockStateProperties.HORIZONTAL_FACING),targets.get(i));
            if(clicks!=0||!cfg.galateaTempleHideSolved)out.add(new Rotation(pos,clicks));
        }
        return out;
    }

    private static boolean orange(BlockState state){
        return state.is(Blocks.GLAZED_TERRACOTTA.orange())&&state.hasProperty(BlockStateProperties.HORIZONTAL_FACING);
    }

    private static int clicks(Direction current,Direction target){
        if(current==target)return 0;Direction clockwise=current,counter=current;
        for(int i=1;i<=2;i++){clockwise=clockwise.getClockWise();if(clockwise==target)return i;counter=counter.getCounterClockWise();if(counter==target)return-i;}
        return 0;
    }

    private static BlockPos floor(int index){
        int column=index/2;return(index&1)==0?FLOOR_TOP_LEFT.offset(-column,0,0):FLOOR_BOTTOM_LEFT.offset(-column,0,0);
    }

    private static void drawRotation(WorldRenderer.Ctx ctx,Rotation rotation,boolean walls){
        int color=rotation.clicks>=0?cfg.galateaTempleClockwiseColor:cfg.galateaTempleCounterColor;
        if(cfg.galateaTempleBoxes)ctx.highlight(new AABB(rotation.pos),color,walls);
        if(cfg.galateaTempleLabels){
            String text=rotation.clicks==0?"Solved":Math.abs(rotation.clicks)+(rotation.clicks>0?" right":" left");
            ctx.label(Vec3.atCenterOf(rotation.pos).add(0,.8,0),text,color,walls);
        }
    }

    private static boolean active(){
        return cfg!=null&&cfg.enabled&&cfg.galateaExploration&&ConstellationClient.loc().area()==SkyblockArea.GALATEA;
    }
    private static void reset(){levelIdentity=null;clear();}
    private static void clear(){NODES.clear();RESOURCES.clear();chunkCursor=0;}
    private static void save(){ConstellationClient.saveConfig();}
    private static void local(String text){Minecraft mc=Minecraft.getInstance();if(mc.player!=null)mc.player.sendSystemMessage(Component.literal("§6[Galatea] §f"+text));}

    public static void registerCommands(CommandDispatcher<FabricClientCommandSource>d){
        d.register(LiteralArgumentBuilder.<FabricClientCommandSource>literal("galateahelper").executes(c->status())
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("status").executes(c->status()))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("clear").executes(c->{clear();local("Exploration cache cleared.");return 1;}))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("range").then(RequiredArgumentBuilder.<FabricClientCommandSource,Integer>argument("blocks",IntegerArgumentType.integer(8,192)).executes(c->{cfg.galateaExplorationRange=IntegerArgumentType.getInteger(c,"blocks");save();return status();})))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("chunks").then(RequiredArgumentBuilder.<FabricClientCommandSource,Integer>argument("radius",IntegerArgumentType.integer(1,8)).executes(c->{cfg.galateaExplorationChunkRadius=IntegerArgumentType.getInteger(c,"radius");save();clear();return status();})))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("lumies").then(RequiredArgumentBuilder.<FabricClientCommandSource,Integer>argument("minimum",IntegerArgumentType.integer(1,4)).executes(c->{cfg.galateaSeaLumiesMinimum=IntegerArgumentType.getInteger(c,"minimum");save();RESOURCES.entrySet().removeIf(e->e.getValue()==Resource.SEA_LUMIES);return status();})))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("option").then(RequiredArgumentBuilder.<FabricClientCommandSource,String>argument("name",StringArgumentType.word()).then(RequiredArgumentBuilder.<FabricClientCommandSource,String>argument("state",StringArgumentType.word()).executes(c->option(StringArgumentType.getString(c,"name"),StringArgumentType.getString(c,"state")))))));
    }
    private static int status(){local("Exploration "+on(cfg.galateaExploration)+", "+NODES.size()+" nodes and "+RESOURCES.size()+" resources cached, range "+cfg.galateaExplorationRange+".");return 1;}
    private static int option(String name,String raw){Boolean value=parse(raw);if(value==null){local("State must be on or off.");return 0;}switch(name.toLowerCase(Locale.ROOT)){
        case"enabled"->cfg.galateaExploration=value;case"nodes"->cfg.galateaForestNodes=value;case"nodebox"->cfg.galateaNodeBox=value;case"nodelabel"->cfg.galateaNodeLabel=value;case"nodebeam"->cfg.galateaNodeBeam=value;case"nodeline"->cfg.galateaNodeLine=value;case"nodedistance"->cfg.galateaNodeDistance=value;
        case"temple"->cfg.galateaTempleSolver=value;case"templelabels"->cfg.galateaTempleLabels=value;case"templeboxes"->cfg.galateaTempleBoxes=value;case"hidesolved"->cfg.galateaTempleHideSolved=value;
        case"lushlilac"->cfg.galateaLushlilac=value;case"sealumies","lumies"->cfg.galateaSeaLumies=value;case"resourceboxes"->cfg.galateaResourceBoxes=value;case"resourcelabels"->cfg.galateaResourceLabels=value;case"resourcebeams"->cfg.galateaResourceBeams=value;case"resourcedistance"->cfg.galateaResourceDistance=value;case"throughwalls"->cfg.galateaExplorationThroughWalls=value;
        default->{local("Unknown Galatea option.");return 0;}
    }save();if(!value&&(name.equalsIgnoreCase("enabled")))clear();return status();}
    private static Boolean parse(String raw){return switch(raw.toLowerCase(Locale.ROOT)){case"on","true","yes","1"->true;case"off","false","no","0"->false;default->null;};}
    private static String on(boolean value){return value?"on":"off";}
}
