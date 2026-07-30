package com.froggylord.constellation.constellation;

import com.froggylord.constellation.ConstellationClient;
import com.froggylord.constellation.config.AndromedaConfig;
import com.froggylord.constellation.core.LocationManager.SkyblockArea;
import com.froggylord.constellation.render.WorldRenderer;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundLevelParticlesPacket;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// ported from SkyHanni (LGPL-3.0-or-later): features/rift/area/stillgorechateau/RiftBloodEffigies.kt, SplatterHearts.kt
public final class AndromedaStillgore {
    private enum State{UNKNOWN,NOT_BROKEN,BROKEN}
    private static final class Effigy{State state=State.UNKNOWN;long respawnAt;}
    private record Heart(Vec3 pos,long seen){}
    private static final Pattern TIMER=Pattern.compile("Respawn\\s+((?:(\\d+)m)?\\s*(?:(\\d+)s)?)\\s*\\(or click!\\)",Pattern.CASE_INSENSITIVE);
    private static final Effigy[] EFFIGIES={new Effigy(),new Effigy(),new Effigy(),new Effigy(),new Effigy(),new Effigy()};
    private static final List<Heart> HEARTS=new ArrayList<>();
    private static Set<Integer> previousUnbroken=Set.of();
    private static AndromedaConfig cfg;private static boolean initialized;private static Object levelIdentity;
    private AndromedaStillgore(){}

    public static void init(AndromedaConfig config){
        cfg=config;if(initialized)return;initialized=true;
        ConstellationClient.tick().every(5,"andromeda-stillgore",AndromedaStillgore::tick);
        ClientPlayConnectionEvents.JOIN.register((a,b,c)->reset());
        ClientPlayConnectionEvents.DISCONNECT.register((a,b)->reset());
    }
    private static void tick(){
        Minecraft mc=Minecraft.getInstance();if(mc.level!=levelIdentity){levelIdentity=mc.level;resetTransient();}
        if(!active()||!inStillgore()){HEARTS.clear();return;}
        updateScoreboard();scanTimers(mc);long now=System.currentTimeMillis();HEARTS.removeIf(heart->now-heart.seen>Math.clamp(cfg.splatterHeartLifetimeMillis,100,2000));
    }
    private static void updateScoreboard(){
        Set<Integer> unbroken=AndromedaRiftCore.unbrokenEffigies();long now=System.currentTimeMillis();
        for(int i=0;i<EFFIGIES.length;i++){Effigy effigy=EFFIGIES[i];boolean current=unbroken.contains(i),prior=previousUnbroken.contains(i);if(current){effigy.state=State.NOT_BROKEN;effigy.respawnAt=0;}else if(current!=prior||effigy.state==State.UNKNOWN){effigy.state=State.BROKEN;if(prior)effigy.respawnAt=now+1_200_000L;}}
        previousUnbroken=new LinkedHashSet<>(unbroken);
    }
    private static void scanTimers(Minecraft mc){
        if(mc.level==null||mc.player==null)return;long now=System.currentTimeMillis();
        for(Entity entity:mc.level.entitiesForRendering())if(entity instanceof ArmorStand stand&&stand.hasCustomName()&&stand.distanceToSqr(mc.player)<=225){
            String name=clean(stand.getName().getString());Matcher timer=TIMER.matcher(name);
            if(timer.find()){int index=closest(stand.position());if(index<0)continue;int seconds=number(timer.group(2))*60+number(timer.group(3));Effigy effigy=EFFIGIES[index];effigy.state=State.BROKEN;effigy.respawnAt=now+seconds*1000L;}
            else if(name.equalsIgnoreCase("Break it!")){int index=closest(stand.position());if(index>=0){EFFIGIES[index].state=State.NOT_BROKEN;EFFIGIES[index].respawnAt=0;}}
        }
    }
    private static int closest(Vec3 point){int best=-1;double distance=Double.POSITIVE_INFINITY;for(int i=0;i<EFFIGIES.length;i++){BlockPos pos=AndromedaRiftCore.effigyPosition(i);double current=pos==null?Double.POSITIVE_INFINITY:pos.distToCenterSqr(point);if(current<distance){distance=current;best=i;}}return distance<=400?best:-1;}

    public static void onParticle(ClientboundLevelParticlesPacket packet){
        if(!active()||!inStillgore()||!cfg.splatterHearts||packet.getParticle().getType()!=ParticleTypes.HEART||packet.getCount()!=3||packet.getMaxSpeed()!=0f)return;
        long now=System.currentTimeMillis();Vec3 pos=new Vec3(packet.getX()-.5,packet.getY()+.3,packet.getZ()-.5);
        HEARTS.removeIf(heart->heart.pos.distanceToSqr(pos)<.04);HEARTS.add(new Heart(pos,now));
    }
    public static void draw(WorldRenderer.Ctx ctx){
        if(!active()||!inStillgore())return;Minecraft mc=Minecraft.getInstance();if(mc.player==null)return;
        if(cfg.effigyWaypoints)drawEffigies(ctx,mc);if(cfg.splatterHearts)drawHearts(ctx);
    }
    private static void drawEffigies(WorldRenderer.Ctx ctx,Minecraft mc){
        long now=System.currentTimeMillis(),soon=now+Math.clamp(cfg.effigyRespawningSoonMinutes,1,15)*60_000L;double range=Math.clamp(cfg.effigyRange,25,500),rangeSq=range*range;
        for(int i=0;i<EFFIGIES.length;i++){BlockPos source=AndromedaRiftCore.effigyPosition(i),pos=cfg.effigyCompact?source.below(6):source;if(pos.distToCenterSqr(mc.player.position())>rangeSq)continue;Effigy effigy=EFFIGIES[i];int color;String label;
            if(effigy.state==State.NOT_BROKEN){color=cfg.effigyColor;label="Break Effigy "+(i+1);}
            else if(effigy.state==State.BROKEN&&effigy.respawnAt>now&&effigy.respawnAt<=soon&&cfg.effigyRespawningSoon){color=cfg.effigyRespawningColor;label="Effigy "+(i+1)+" respawns in "+duration(effigy.respawnAt-now);}
            else if((effigy.state==State.UNKNOWN||effigy.state==State.BROKEN&&effigy.respawnAt==0)&&cfg.effigyUnknownTime){color=cfg.effigyUnknownColor;label="Effigy "+(i+1)+" unknown";}
            else {if(cfg.effigyNearbyBrokenLabels&&effigy.state==State.BROKEN&&pos.distToCenterSqr(mc.player.position())<=225&&cfg.effigyLabel)ctx.label(Vec3.atCenterOf(pos).add(0,1.2,0),"Effigy "+(i+1),cfg.effigyUnknownColor,cfg.effigyThroughWalls);continue;}
            Vec3 center=Vec3.atCenterOf(pos);if(cfg.effigyBox)ctx.highlight(new AABB(pos),color,cfg.effigyThroughWalls);if(cfg.effigyBeam)ctx.beam(center.x,center.y,center.z,color,Math.clamp(cfg.effigyBeamHeight,2,100),cfg.effigyThroughWalls);if(cfg.effigyLabel){if(cfg.effigyDistance)label+=" "+Math.round(Math.sqrt(pos.distToCenterSqr(mc.player.position())))+"m";ctx.label(center.add(0,1.2,0),label,color,cfg.effigyThroughWalls);}
        }
    }
    private static void drawHearts(WorldRenderer.Ctx ctx){
        long now=System.currentTimeMillis();for(Heart heart:List.copyOf(HEARTS)){if(now-heart.seen>Math.clamp(cfg.splatterHeartLifetimeMillis,100,2000))continue;AABB box=new AABB(heart.pos.x,heart.pos.y,heart.pos.z,heart.pos.x+1,heart.pos.y+1,heart.pos.z+1);if(cfg.splatterHeartBox)ctx.highlight(box,cfg.splatterHeartColor,cfg.splatterHeartThroughWalls);if(cfg.splatterHeartBeam)ctx.beam(heart.pos.x+.5,heart.pos.y,heart.pos.z+.5,cfg.splatterHeartColor,4,cfg.splatterHeartThroughWalls);if(cfg.splatterHeartLabel)ctx.label(heart.pos.add(.5,1.2,.5),"Splatter Heart",cfg.splatterHeartColor,cfg.splatterHeartThroughWalls);}
    }
    private static boolean active(){return cfg!=null&&cfg.enabled&&ConstellationClient.loc().area()==SkyblockArea.THE_RIFT;}
    private static boolean inStillgore(){String area=AndromedaRiftCore.currentArea();return area.equalsIgnoreCase("Stillgore Chateau")||area.equalsIgnoreCase("Stillgore Château")||area.equalsIgnoreCase("Oubliette");}
    private static void reset(){levelIdentity=null;resetTransient();}
    private static void resetTransient(){previousUnbroken=Set.of();HEARTS.clear();for(Effigy effigy:EFFIGIES){effigy.state=State.UNKNOWN;effigy.respawnAt=0;}}
    private static int number(String value){try{return value==null?0:Integer.parseInt(value);}catch(Exception ignored){return 0;}}
    private static String duration(long millis){long seconds=Math.max(0,(millis+999)/1000);return seconds>=60?String.format(Locale.ROOT,"%dm%02ds",seconds/60,seconds%60):seconds+"s";}
    private static String clean(String value){String clean=ChatFormatting.stripFormatting(value);return clean==null?"":clean.trim();}
    private static void save(){ConstellationClient.saveConfig();}
    private static void local(String text){Minecraft mc=Minecraft.getInstance();if(mc.player!=null)mc.player.sendSystemMessage(Component.literal("§5[Stillgore] §f"+text));}

    public static void registerCommands(CommandDispatcher<FabricClientCommandSource> dispatcher){
        dispatcher.register(LiteralArgumentBuilder.<FabricClientCommandSource>literal("stillgore").executes(c->status())
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("status").executes(c->status()))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("reset").executes(c->{resetTransient();return status();}))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("number").then(RequiredArgumentBuilder.<FabricClientCommandSource,String>argument("name",StringArgumentType.word()).then(RequiredArgumentBuilder.<FabricClientCommandSource,Integer>argument("value",IntegerArgumentType.integer(0)).executes(c->numberOption(StringArgumentType.getString(c,"name"),IntegerArgumentType.getInteger(c,"value"))))))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("option").then(RequiredArgumentBuilder.<FabricClientCommandSource,String>argument("name",StringArgumentType.word()).then(RequiredArgumentBuilder.<FabricClientCommandSource,String>argument("state",StringArgumentType.word()).executes(c->option(StringArgumentType.getString(c,"name"),StringArgumentType.getString(c,"state")))))));
    }
    private static int status(){local("Effigies "+AndromedaRiftCore.unbrokenEffigies().size()+" unbroken, respawn soon "+(cfg.effigyRespawningSoon?"on":"off")+", unknown "+(cfg.effigyUnknownTime?"on":"off")+", hearts "+(cfg.splatterHearts?"on":"off")+".");return 1;}
    private static int numberOption(String name,int value){switch(name.toLowerCase(Locale.ROOT)){case"soon"->cfg.effigyRespawningSoonMinutes=Math.clamp(value,1,15);case"heartlifetime"->cfg.splatterHeartLifetimeMillis=Math.clamp(value,100,2000);default->{local("Unknown Stillgore number.");return 0;}}save();return status();}
    private static int option(String name,String state){Boolean value=switch(state.toLowerCase(Locale.ROOT)){case"on","true","yes","1"->true;case"off","false","no","0"->false;default->null;};if(value==null){local("State must be on or off.");return 0;}switch(name.toLowerCase(Locale.ROOT)){case"soon"->cfg.effigyRespawningSoon=value;case"unknown"->cfg.effigyUnknownTime=value;case"nearby"->cfg.effigyNearbyBrokenLabels=value;case"hearts"->cfg.splatterHearts=value;case"heartbox"->cfg.splatterHeartBox=value;case"heartbeam"->cfg.splatterHeartBeam=value;case"heartlabel"->cfg.splatterHeartLabel=value;case"heartwalls"->cfg.splatterHeartThroughWalls=value;default->{local("Unknown Stillgore option.");return 0;}}save();return status();}
}
