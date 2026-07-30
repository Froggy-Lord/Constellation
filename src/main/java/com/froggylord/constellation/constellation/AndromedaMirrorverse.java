package com.froggylord.constellation.constellation;

import com.froggylord.constellation.ConstellationClient;
import com.froggylord.constellation.config.AndromedaConfig;
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
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.cubemob.Slime;
import net.minecraft.world.entity.monster.spider.CaveSpider;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

// ported from SkyHanni (LGPL-3.0-or-later): features/rift/area/mirrorverse/DanceRoomHelper.kt, CraftRoomHolographicMob.kt
public final class AndromedaMirrorverse {
    private static final AABB DANCE_ROOM=new AABB(-267,32,-110,-260,40,-102);
    private static final AABB CRAFT_ROOM=new AABB(-117,51,-128,-108,58,-106);
    private static final double WALL_Z=-116.5;
    private static final List<String> INSTRUCTIONS=new ArrayList<>();
    private static AndromedaConfig cfg;private static boolean initialized,inDance,inCraft;private static int index,countdownTicks;private static Object levelIdentity;
    private AndromedaMirrorverse(){}

    public static void init(AndromedaConfig config){
        cfg=config;load();if(initialized)return;initialized=true;
        ConstellationClient.tick().every(1,"andromeda-mirrorverse",AndromedaMirrorverse::tick);
        ClientPlayConnectionEvents.JOIN.register((a,b,c)->reset());
        ClientPlayConnectionEvents.DISCONNECT.register((a,b)->reset());
    }
    private static void load(){
        if(!INSTRUCTIONS.isEmpty())return;
        // data ported from SkyHanni Repo (MIT): constants/DanceRoomInstructions.json
        try(var stream=AndromedaMirrorverse.class.getResourceAsStream("/assets/constellation/rift/dance_room_instructions.json")){if(stream==null)throw new IllegalStateException("missing dance instructions");JsonObject root=JsonParser.parseReader(new InputStreamReader(stream,StandardCharsets.UTF_8)).getAsJsonObject();root.getAsJsonArray("instructions").forEach(value->INSTRUCTIONS.add(value.getAsString()));ConstellationClient.LOGGER.info("loaded {} Mirrorverse dance instructions",INSTRUCTIONS.size());}catch(Exception e){ConstellationClient.LOGGER.error("failed to load Mirrorverse dance instructions",e);}
    }
    private static void tick(){
        Minecraft mc=Minecraft.getInstance();if(mc.level!=levelIdentity){levelIdentity=mc.level;resetTransient();}
        boolean active=active()&&mc.player!=null;inDance=active&&DANCE_ROOM.contains(mc.player.position());inCraft=active&&CRAFT_ROOM.contains(mc.player.position());
        if(!inDance){index=0;countdownTicks=0;}else if(countdownTicks>0)countdownTicks--;
    }
    public static void onSound(ClientboundSoundPacket packet){
        if(!active()||!inDance||!cfg.mirrorDanceHelper)return;String path=packet.getSound().value().location().getPath();float pitch=packet.getPitch(),volume=packet.getVolume();
        boolean success=path.equals("block.note_block.bass")&&volume==1f&&(near(pitch,.6984127f)||near(pitch,.52380955f));
        boolean failure=path.equals("entity.player.burp")&&near(volume,.8f)||path.equals("entity.player.levelup")&&volume==1f&&near(pitch,1.8412699f);
        if(success){index=Math.min(INSTRUCTIONS.size(),index+1);countdownTicks=20;}else if(failure&&(index>0||countdownTicks>0)){index=0;countdownTicks=0;}
    }
    public static boolean shouldHideTitle(){return active()&&inDance&&cfg.mirrorDanceHelper&&cfg.mirrorDanceHideOriginalTitle;}
    public static boolean shouldHide(Entity entity){if(!(entity instanceof RemotePlayer))return false;return active()&&(inDance&&cfg.mirrorDanceHidePlayers||inCraft&&cfg.mirrorCraftHelper&&cfg.mirrorCraftHidePlayers);}
    public static List<String> danceLines(){
        if(!active()||!inDance||!cfg.mirrorDanceHelper||INSTRUCTIONS.isEmpty())return List.of();List<String> lines=new ArrayList<>();int max=Math.min(INSTRUCTIONS.size()-1,index+Math.clamp(cfg.mirrorDanceLines,1,49));
        for(int i=index;i<=max;i++){String prefix=i==index?cfg.mirrorDanceNow:i==index+1?cfg.mirrorDanceNext:cfg.mirrorDanceLater;String line=formatInstruction(INSTRUCTIONS.get(i));if(i==index&&countdownTicks>0)line+=" "+color(cfg.mirrorDanceCountdown)+String.format(Locale.ROOT,"%d:%03d",countdownTicks*50/1000,countdownTicks*50%1000);lines.add(color(prefix)+" "+line);}return lines;
    }
    private static String formatInstruction(String raw){StringBuilder out=new StringBuilder();for(String word:raw.split(" ")){if(!out.isEmpty())out.append(' ');String first=word.substring(0,1).toUpperCase(Locale.ROOT)+word.substring(1);String setting=switch(word.toLowerCase(Locale.ROOT)){case"move"->cfg.mirrorDanceMove;case"stand"->cfg.mirrorDanceStand;case"sneak"->cfg.mirrorDanceSneak;case"jump"->cfg.mirrorDanceJump;case"punch"->cfg.mirrorDancePunch;default->cfg.mirrorDanceFallback;};out.append(color(setting)).append(first);}return out.toString();}
    private static String color(String value){return value==null?"":value.replace('&','§');}
    public static int danceSpacing(){return cfg==null?0:cfg.mirrorDanceSpacing;}

    public static void draw(WorldRenderer.Ctx ctx){
        if(!active()||!inCraft||!cfg.mirrorCraftHelper)return;Minecraft mc=Minecraft.getInstance();if(mc.player==null||mc.level==null)return;double range=Math.clamp(cfg.mirrorCraftRange,10,50),sq=range*range;
        for(Entity entity:mc.level.entitiesForRendering())if(entity instanceof LivingEntity living&&!(entity instanceof net.minecraft.world.entity.player.Player)&&(entity instanceof Zombie||entity instanceof Slime||entity instanceof CaveSpider)&&CRAFT_ROOM.contains(entity.position())&&entity.distanceToSqr(mc.player)<=sq&&entity.getZ()<=WALL_Z){
            double mirroredZ=WALL_Z+(WALL_Z-entity.getZ()),dz=mirroredZ-entity.getZ();AABB box=entity.getBoundingBox().move(0,0,dz);Vec3 center=box.getCenter();if(cfg.mirrorCraftBox)ctx.highlight(box,cfg.mirrorCraftColor,cfg.mirrorCraftThroughWalls);if(cfg.mirrorCraftBeam)ctx.beam(center.x,box.minY,center.z,cfg.mirrorCraftColor,6,cfg.mirrorCraftThroughWalls);if(cfg.mirrorCraftLabel){StringBuilder label=new StringBuilder();if(cfg.mirrorCraftShowName)label.append(clean(entity.getDisplayName().getString()));if(cfg.mirrorCraftShowHealth){if(!label.isEmpty())label.append(' ');label.append(String.format(Locale.ROOT,"%.1f HP",living.getHealth()));}if(!label.isEmpty())ctx.label(center.add(0,entity.getBbHeight()/2+.5,0),label.toString(),cfg.mirrorCraftColor,cfg.mirrorCraftThroughWalls);}
        }
    }
    private static boolean active(){return cfg!=null&&cfg.enabled&&ConstellationClient.loc().area()==SkyblockArea.THE_RIFT;}
    private static boolean near(float left,float right){return Math.abs(left-right)<.00001f;}
    private static String clean(String value){String clean=ChatFormatting.stripFormatting(value);return clean==null?"":clean.trim();}
    private static void reset(){levelIdentity=null;resetTransient();}
    private static void resetTransient(){inDance=inCraft=false;index=countdownTicks=0;}
    private static void save(){ConstellationClient.saveConfig();}
    private static void local(String text){Minecraft mc=Minecraft.getInstance();if(mc.player!=null)mc.player.sendSystemMessage(Component.literal("§5[Mirrorverse] §f"+text));}

    public static void registerCommands(CommandDispatcher<FabricClientCommandSource> dispatcher){
        dispatcher.register(LiteralArgumentBuilder.<FabricClientCommandSource>literal("mirrorverse").executes(c->status())
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("status").executes(c->status()))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("resetdance").executes(c->{index=countdownTicks=0;return status();}))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("number").then(RequiredArgumentBuilder.<FabricClientCommandSource,String>argument("name",StringArgumentType.word()).then(RequiredArgumentBuilder.<FabricClientCommandSource,Integer>argument("value",IntegerArgumentType.integer(-5)).executes(c->number(StringArgumentType.getString(c,"name"),IntegerArgumentType.getInteger(c,"value"))))))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("option").then(RequiredArgumentBuilder.<FabricClientCommandSource,String>argument("name",StringArgumentType.word()).then(RequiredArgumentBuilder.<FabricClientCommandSource,String>argument("state",StringArgumentType.word()).executes(c->option(StringArgumentType.getString(c,"name"),StringArgumentType.getString(c,"state")))))));
    }
    private static int status(){local("Dance "+(cfg.mirrorDanceHelper?"on":"off")+" step "+Math.min(index+1,INSTRUCTIONS.size())+"/"+INSTRUCTIONS.size()+", Craft "+(cfg.mirrorCraftHelper?"on":"off")+".");return 1;}
    private static int number(String name,int value){switch(name.toLowerCase(Locale.ROOT)){case"lines"->cfg.mirrorDanceLines=Math.clamp(value,1,49);case"spacing"->cfg.mirrorDanceSpacing=Math.clamp(value,-5,10);case"craftrange"->cfg.mirrorCraftRange=Math.clamp(value,10,50);default->{local("Unknown Mirrorverse number.");return 0;}}save();return status();}
    private static int option(String name,String state){Boolean value=switch(state.toLowerCase(Locale.ROOT)){case"on","true","yes","1"->true;case"off","false","no","0"->false;default->null;};if(value==null){local("State must be on or off.");return 0;}switch(name.toLowerCase(Locale.ROOT)){case"dance"->cfg.mirrorDanceHelper=value;case"danceplayers"->cfg.mirrorDanceHidePlayers=value;case"dancetitle"->cfg.mirrorDanceHideOriginalTitle=value;case"craft"->cfg.mirrorCraftHelper=value;case"craftname"->cfg.mirrorCraftShowName=value;case"crafthealth"->cfg.mirrorCraftShowHealth=value;case"craftplayers"->cfg.mirrorCraftHidePlayers=value;case"craftbox"->cfg.mirrorCraftBox=value;case"craftbeam"->cfg.mirrorCraftBeam=value;case"craftlabel"->cfg.mirrorCraftLabel=value;default->{local("Unknown Mirrorverse option.");return 0;}}save();return status();}
}
