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
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundDamageEventPacket;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.monster.cubemob.Slime;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// ported from SkyHanni (LGPL-3.0-or-later): features/rift/area/colosseum/BacteApi.kt, BlobbercystsHighlight.kt
// ported from SkyHanni (LGPL-3.0-or-later): features/rift/area/colosseum/KillZoneWarning.kt, TentacleWaypoint.kt
public final class AndromedaColosseum {
    private enum Phase{NOT_ACTIVE,PHASE_1,PHASE_2,PHASE_3,PHASE_4,PHASE_5}
    private static final class Tentacle{final int id;int hits;long lastSeen;Tentacle(int id,long now){this.id=id;lastSeen=now;}}
    private static final Pattern GROWING=Pattern.compile("([A-Za-z]+) is growing into ([A-Za-z]+)!");
    private static final Pattern KILL_ZONE=Pattern.compile(".*Get back in the arena or you will DIE(!+).*",Pattern.CASE_INSENSITIVE);
    private static final Pattern BACTE_NAME=Pattern.compile(".*\\[Lv\\d+]\\s+(B(?:a(?:c(?:t(?:e)?)?)?)?)\\s+.*",Pattern.CASE_INSENSITIVE);
    private static final Map<Integer,Tentacle> TENTACLES=new HashMap<>();
    private static AndromedaConfig cfg;private static boolean initialized;private static Phase phase=Phase.NOT_ACTIVE;private static String bossName="";private static long lastBacteSeen,killDeadline,lastWarning;private static Object levelIdentity;
    private AndromedaColosseum(){}

    public static void init(AndromedaConfig config){
        cfg=config;if(initialized)return;initialized=true;
        ConstellationClient.tick().every(1,"andromeda-colosseum",AndromedaColosseum::tick);
        ClientReceiveMessageEvents.ALLOW_GAME.register((message,overlay)->{if(!overlay)onChat(clean(message.getString()));return true;});
        ClientPlayConnectionEvents.JOIN.register((a,b,c)->reset());
        ClientPlayConnectionEvents.DISCONNECT.register((a,b)->reset());
    }
    private static void tick(){
        Minecraft mc=Minecraft.getInstance();if(mc.level!=levelIdentity){levelIdentity=mc.level;resetTransient();}
        if(!active()||!inColosseum()){resetTransient();return;}scanBoss(mc);scanTentacles(mc);showKillWarning(mc);
    }
    private static void onChat(String message){
        if(!active()||!inColosseum())return;Matcher growing=GROWING.matcher(message);if(growing.find()){bossName=growing.group(2);phase=fromLength(bossName.length());lastBacteSeen=System.currentTimeMillis();}
        Matcher warning=KILL_ZONE.matcher(message);if(cfg.colosseumKillZoneWarning&&warning.matches()){int level=Math.clamp(warning.group(1).length(),1,12);long now=System.currentTimeMillis();lastWarning=now;killDeadline=now+250L*(12-level);Minecraft mc=Minecraft.getInstance();if(mc.player!=null){if(cfg.colosseumKillZoneSound)mc.player.playSound(SoundEvents.EXPERIENCE_ORB_PICKUP,.9f,0f);if(cfg.colosseumKillZoneChat)local("Get back in the arena. "+formatRemaining());}}
    }
    private static void scanBoss(Minecraft mc){
        long now=System.currentTimeMillis();boolean seen=false;
        for(Entity entity:mc.level.entitiesForRendering())if(entity instanceof ArmorStand stand&&stand.hasCustomName()){String name=clean(stand.getName().getString());Matcher matcher=BACTE_NAME.matcher(name);if(matcher.matches()){String token=matcher.group(1);if(token.isEmpty()||token.length()>5)continue;bossName=token;phase=fromLength(token.length());lastBacteSeen=now;seen=true;}}
        if(!seen&&lastBacteSeen>0&&now-lastBacteSeen>2000){phase=Phase.NOT_ACTIVE;bossName="";lastBacteSeen=0;TENTACLES.clear();}
    }
    private static void scanTentacles(Minecraft mc){
        if(!cfg.colosseumTentacles)return;long now=System.currentTimeMillis();List<ArmorStand> labels=new ArrayList<>();List<Slime> slimes=new ArrayList<>();
        for(Entity entity:mc.level.entitiesForRendering()){if(entity instanceof ArmorStand stand&&stand.hasCustomName()&&clean(stand.getName().getString()).contains("Bacte Tentacle"))labels.add(stand);else if(entity instanceof Slime slime&&slime.getSize()>=4&&slime.getSize()<=8&&Math.ceil(slime.getY())==68)slimes.add(slime);}
        for(ArmorStand label:labels)slimes.stream().filter(slime->slime.distanceToSqr(label)<=25).min(Comparator.comparingDouble(slime->slime.distanceToSqr(label))).ifPresent(slime->{Tentacle value=TENTACLES.computeIfAbsent(slime.getId(),id->new Tentacle(id,now));value.lastSeen=now;});
        TENTACLES.values().removeIf(value->{Entity entity=mc.level.getEntity(value.id);return now-value.lastSeen>2000||!(entity instanceof Slime slime)||!slime.isAlive()||slime.getHealth()<=0;});
    }
    public static void onDamage(ClientboundDamageEventPacket packet){
        if(!active()||!inColosseum()||!cfg.colosseumTentacles)return;Minecraft mc=Minecraft.getInstance();if(mc.level==null)return;Tentacle tentacle=TENTACLES.get(packet.entityId());if(tentacle==null)return;
        String source=packet.getSource(mc.level).getMsgId();if(source.equals("generic"))tentacle.hits++;
    }
    private static void showKillWarning(Minecraft mc){
        if(!cfg.colosseumKillZoneWarning||killDeadline<=System.currentTimeMillis()||System.currentTimeMillis()-lastWarning>250||mc.player==null)return;String remaining=formatRemaining();
        if(cfg.colosseumKillZoneTitle){mc.gui.hud.resetTitleTimes();mc.gui.hud.setTitle(Component.literal("Get back in the arena!").withColor(cfg.colosseumKillZoneColor&0xFFFFFF));if(cfg.colosseumKillZoneSubtitle)mc.gui.hud.setSubtitle(Component.literal(remaining+" left"));mc.gui.hud.setTimes(0,Math.clamp(cfg.colosseumKillZoneTitleTicks,1,10),0);}
    }
    private static String formatRemaining(){return String.format(Locale.ROOT,"%.2fs",Math.max(0,killDeadline-System.currentTimeMillis())/1000.0);}
    private static Phase fromLength(int length){return switch(Math.clamp(length,0,5)){case 1->Phase.PHASE_1;case 2->Phase.PHASE_2;case 3->Phase.PHASE_3;case 4->Phase.PHASE_4;case 5->Phase.PHASE_5;default->Phase.NOT_ACTIVE;};}

    public static void draw(WorldRenderer.Ctx ctx){
        if(!active()||!inColosseum())return;Minecraft mc=Minecraft.getInstance();if(mc.player==null)return;drawBlobbers(ctx,mc);drawTentacles(ctx,mc);
    }
    private static void drawBlobbers(WorldRenderer.Ctx ctx,Minecraft mc){
        if(!cfg.colosseumBlobbercysts)return;double range=Math.clamp(cfg.colosseumBlobberRange,10,150),rangeSq=range*range;
        for(Entity entity:mc.level.entitiesForRendering())if(entity instanceof RemotePlayer player&&clean(player.getName().getString()).equals("Blobbercyst")&&player.distanceToSqr(mc.player)<=rangeSq&&player.isAlive()){if(cfg.colosseumBlobberBox)ctx.highlight(player.getBoundingBox().inflate(.15),cfg.colosseumBlobberColor,cfg.colosseumBlobberThroughWalls);if(cfg.colosseumBlobberLabel){String label="Blobbercyst";if(cfg.colosseumBlobberDistance)label+=" "+Math.round(player.distanceTo(mc.player))+"m";ctx.label(player.position().add(0,player.getBbHeight()+.4,0),label,cfg.colosseumBlobberColor,cfg.colosseumBlobberThroughWalls);}}
    }
    private static void drawTentacles(WorldRenderer.Ctx ctx,Minecraft mc){
        if(!cfg.colosseumTentacles)return;double range=Math.clamp(cfg.colosseumTentacleRange,20,250),rangeSq=range*range;
        for(Tentacle value:TENTACLES.values()){Entity entity=mc.level.getEntity(value.id);if(!(entity instanceof Slime slime)||slime.distanceToSqr(mc.player)>rangeSq)continue;Vec3 pos=slime.position().add(-.5,0,-.5);if(cfg.colosseumTentacleBox)ctx.highlight(new AABB(pos.x,pos.y,pos.z,pos.x+1,pos.y+1,pos.z+1),cfg.colosseumTentacleColor,cfg.colosseumTentacleThroughWalls);if(cfg.colosseumTentacleBeam)ctx.beam(pos.x+.5,pos.y,pos.z+.5,cfg.colosseumTentacleColor,Math.clamp(cfg.colosseumTentacleBeamHeight,2,50),cfg.colosseumTentacleThroughWalls);if(cfg.colosseumTentacleLabel){String label=tentacleText(value.hits);if(cfg.colosseumTentacleDistance)label+=" "+Math.round(slime.distanceTo(mc.player))+"m";ctx.label(pos.add(.5,1.2,.5),label,cfg.colosseumTentacleColor,cfg.colosseumTentacleThroughWalls);}}
    }
    private static String tentacleText(int hits){if(phase==Phase.PHASE_5)return hits+" Hit"+(hits==1?"":"s");int max=phase==Phase.PHASE_4?3:4;return Math.max(0,max-hits)+"/"+max+" HP";}
    public static String phaseHud(){if(!active()||!cfg.colosseumPhaseHud||!inColosseum()||phase==Phase.NOT_ACTIVE&&!cfg.colosseumPhaseShowInactive)return null;String value=switch(phase){case NOT_ACTIVE->"Not Active";case PHASE_1->"Phase 1";case PHASE_2->"Phase 2";case PHASE_3->"Phase 3";case PHASE_4->"Phase 4";case PHASE_5->"Phase 5";};return cfg.colosseumPhaseShowBossName&&!bossName.isBlank()?value+" · "+bossName:value;}
    private static boolean active(){return cfg!=null&&cfg.enabled&&ConstellationClient.loc().area()==SkyblockArea.THE_RIFT;}
    private static boolean inColosseum(){return AndromedaRiftCore.currentArea().equalsIgnoreCase("Colosseum");}
    private static String clean(String value){String clean=ChatFormatting.stripFormatting(value);return clean==null?"":clean.trim();}
    private static void reset(){levelIdentity=null;resetTransient();}
    private static void resetTransient(){phase=Phase.NOT_ACTIVE;bossName="";lastBacteSeen=0;killDeadline=0;lastWarning=0;TENTACLES.clear();}
    private static void save(){ConstellationClient.saveConfig();}
    private static void local(String text){Minecraft mc=Minecraft.getInstance();if(mc.player!=null)mc.player.sendSystemMessage(Component.literal("§5[Colosseum] §f"+text));}

    public static void registerCommands(CommandDispatcher<FabricClientCommandSource> dispatcher){
        dispatcher.register(LiteralArgumentBuilder.<FabricClientCommandSource>literal("colosseum").executes(c->status())
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("status").executes(c->status()))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("reset").executes(c->{resetTransient();return status();}))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("number").then(RequiredArgumentBuilder.<FabricClientCommandSource,String>argument("name",StringArgumentType.word()).then(RequiredArgumentBuilder.<FabricClientCommandSource,Integer>argument("value",IntegerArgumentType.integer(0)).executes(c->number(StringArgumentType.getString(c,"name"),IntegerArgumentType.getInteger(c,"value"))))))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("option").then(RequiredArgumentBuilder.<FabricClientCommandSource,String>argument("name",StringArgumentType.word()).then(RequiredArgumentBuilder.<FabricClientCommandSource,String>argument("state",StringArgumentType.word()).executes(c->option(StringArgumentType.getString(c,"name"),StringArgumentType.getString(c,"state")))))));
    }
    private static int status(){local("Bacte "+phaseHudValue()+", Blobbercysts "+(cfg.colosseumBlobbercysts?"on":"off")+", kill warning "+(cfg.colosseumKillZoneWarning?"on":"off")+", tentacles "+TENTACLES.size()+".");return 1;}
    private static String phaseHudValue(){return switch(phase){case NOT_ACTIVE->"inactive";case PHASE_1->"phase 1";case PHASE_2->"phase 2";case PHASE_3->"phase 3";case PHASE_4->"phase 4";case PHASE_5->"phase 5";};}
    private static int number(String name,int value){switch(name.toLowerCase(Locale.ROOT)){case"blobberrange"->cfg.colosseumBlobberRange=Math.clamp(value,10,150);case"title"->cfg.colosseumKillZoneTitleTicks=Math.clamp(value,1,10);case"tentaclerange"->cfg.colosseumTentacleRange=Math.clamp(value,20,250);case"beamheight"->cfg.colosseumTentacleBeamHeight=Math.clamp(value,2,50);default->{local("Unknown Colosseum number.");return 0;}}save();return status();}
    private static int option(String name,String state){Boolean value=switch(state.toLowerCase(Locale.ROOT)){case"on","true","yes","1"->true;case"off","false","no","0"->false;default->null;};if(value==null){local("State must be on or off.");return 0;}switch(name.toLowerCase(Locale.ROOT)){case"blobbers"->cfg.colosseumBlobbercysts=value;case"killzone"->cfg.colosseumKillZoneWarning=value;case"title"->cfg.colosseumKillZoneTitle=value;case"subtitle"->cfg.colosseumKillZoneSubtitle=value;case"sound"->cfg.colosseumKillZoneSound=value;case"chat"->cfg.colosseumKillZoneChat=value;case"tentacles"->cfg.colosseumTentacles=value;case"phase"->cfg.colosseumPhaseHud=value;case"inactive"->cfg.colosseumPhaseShowInactive=value;case"bossname"->cfg.colosseumPhaseShowBossName=value;default->{local("Unknown Colosseum option.");return 0;}}save();return status();}
}
