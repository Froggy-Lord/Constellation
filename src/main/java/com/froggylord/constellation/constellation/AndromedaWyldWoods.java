package com.froggylord.constellation.constellation;

import com.froggylord.constellation.ConstellationClient;
import com.froggylord.constellation.config.AndromedaConfig;
import com.froggylord.constellation.core.LocationManager.SkyblockArea;
import com.froggylord.constellation.render.WorldRenderer;
import com.google.gson.JsonParser;
import com.mojang.authlib.properties.Property;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Set;

// ported from SkyHanni (LGPL-3.0-or-later): features/rift/area/wyldwoods/RiftLarva.kt, RiftOdonata.kt, ShyCruxWarnings.kt
public final class AndromedaWyldWoods {
    private enum Type{LARVA,ODONATA}
    private static final String LARVA_HASH="4ceb0ed8fc2272b3d3d820676d52a38e7b2e8da8c687a233e0dabaa16c0e96df";
    private static final String ODONATA_HASH="9fd806defdfdf59b1f2609c8ee364666de66127a623415b5430c9358c601ef7c";
    private static final Set<String> SHY_NAMES=Set.of("I'm ugly! :(","Eek!","Don't look at me!","Look away!");
    private static AndromedaConfig cfg;private static boolean initialized;private static long lastShyAlert;private static Object levelIdentity;
    private AndromedaWyldWoods(){}

    public static void init(AndromedaConfig config){
        cfg=config;if(initialized)return;initialized=true;
        ConstellationClient.tick().every(1,"andromeda-wyld-woods",AndromedaWyldWoods::tick);
        ClientPlayConnectionEvents.JOIN.register((a,b,c)->reset());
        ClientPlayConnectionEvents.DISCONNECT.register((a,b)->reset());
    }
    private static void tick(){
        Minecraft mc=Minecraft.getInstance();if(mc.level!=levelIdentity){levelIdentity=mc.level;lastShyAlert=0;}
        if(!active()||!cfg.wyldShyWarning||mc.player==null||mc.level==null)return;double range=Math.clamp(cfg.wyldShyRange,3,20),sq=range*range;Entity shy=null;
        for(Entity entity:mc.level.entitiesForRendering())if(SHY_NAMES.contains(clean(entity.getName().getString()))&&entity.distanceToSqr(mc.player)<=sq){shy=entity;break;}
        if(shy==null)return;long now=System.currentTimeMillis();if(now-lastShyAlert<Math.clamp(cfg.wyldShyAlertCooldownMillis,50,2000))return;lastShyAlert=now;
        if(cfg.wyldShyTitle){mc.gui.hud.resetTitleTimes();mc.gui.hud.setTitle(Component.literal("Look away!").withColor(cfg.wyldShyColor&0xFFFFFF));if(cfg.wyldShySubtitle)mc.gui.hud.setSubtitle(Component.literal(clean(shy.getName().getString())));mc.gui.hud.setTimes(0,3,0);}
        if(cfg.wyldShyChat)local("A Shy Crux is nearby. Look away.");if(cfg.wyldShySound)mc.player.playSound(SoundEvents.NOTE_BLOCK_PLING.value(),.8f,.8f);
    }
    public static void draw(WorldRenderer.Ctx ctx){
        if(!active())return;Minecraft mc=Minecraft.getInstance();if(mc.player==null||mc.level==null)return;String held=itemId(mc.player.getMainHandItem());
        for(Entity entity:mc.level.entitiesForRendering())if(entity instanceof ArmorStand stand&&stand.isAlive()){Type type=type(stand);if(type==null)continue;drawTarget(ctx,mc,stand,type,held);}
        if(cfg.wyldShyWarning&&(cfg.wyldShyBox||cfg.wyldShyLabel)){double range=Math.clamp(cfg.wyldShyRange,3,20),sq=range*range;for(Entity entity:mc.level.entitiesForRendering())if(SHY_NAMES.contains(clean(entity.getName().getString()))&&entity.distanceToSqr(mc.player)<=sq){if(cfg.wyldShyBox)ctx.highlight(entity.getBoundingBox().inflate(.1),cfg.wyldShyColor,cfg.wyldShyThroughWalls);if(cfg.wyldShyLabel)ctx.label(entity.position().add(0,entity.getBbHeight()+.4,0),"Look away",cfg.wyldShyColor,cfg.wyldShyThroughWalls);}}
    }
    private static void drawTarget(WorldRenderer.Ctx ctx,Minecraft mc,ArmorStand stand,Type type,String held){
        boolean larva=type==Type.LARVA;if(larva&&!cfg.wyldLarvas||!larva&&!cfg.wyldOdonata)return;if(larva&&cfg.wyldLarvaRequireHook&&!held.equals("LARVA_HOOK")||!larva&&cfg.wyldOdonataRequireBottle&&!held.equals("EMPTY_ODONATA_BOTTLE"))return;
        int range=Math.clamp(larva?cfg.wyldLarvaRange:cfg.wyldOdonataRange,10,150);if(stand.distanceToSqr(mc.player)>range*range)return;int color=larva?cfg.wyldLarvaColor:cfg.wyldOdonataColor;boolean walls=larva?cfg.wyldLarvaThroughWalls:cfg.wyldOdonataThroughWalls;Vec3 center=stand.getBoundingBox().getCenter();
        if(larva?cfg.wyldLarvaBox:cfg.wyldOdonataBox)ctx.highlight(stand.getBoundingBox().inflate(.1),color,walls);if(larva?cfg.wyldLarvaBeam:cfg.wyldOdonataBeam)ctx.beam(center.x,center.y,center.z,color,8,walls);if(larva?cfg.wyldLarvaLabel:cfg.wyldOdonataLabel){String label=larva?"Larva":"Odonata";if(larva?cfg.wyldLarvaDistance:cfg.wyldOdonataDistance)label+=" "+Math.round(stand.distanceTo(mc.player))+"m";ctx.label(center.add(0,.8,0),label,color,walls);}
    }
    private static Type type(ArmorStand stand){
        if(hash(stand.getItemBySlot(EquipmentSlot.HEAD)).equals(LARVA_HASH))return Type.LARVA;
        if(hash(stand.getItemBySlot(EquipmentSlot.MAINHAND)).equals(ODONATA_HASH)||hash(stand.getItemBySlot(EquipmentSlot.OFFHAND)).equals(ODONATA_HASH))return Type.ODONATA;
        return null;
    }
    private static String hash(ItemStack stack){if(stack==null||stack.isEmpty())return"";var profile=stack.get(DataComponents.PROFILE);if(profile==null)return"";for(Property property:profile.partialProfile().properties().get("textures")){String value=textureHash(property.value());if(!value.isEmpty())return value;}return"";}
    private static String textureHash(String encoded){try{String json=new String(Base64.getDecoder().decode(encoded),StandardCharsets.UTF_8);String url=JsonParser.parseString(json).getAsJsonObject().getAsJsonObject("textures").getAsJsonObject("SKIN").get("url").getAsString();int slash=url.lastIndexOf('/');return slash<0?"":url.substring(slash+1);}catch(Exception ignored){return"";}}
    private static String itemId(ItemStack stack){return LyraTooltips.marketId(stack).toUpperCase(Locale.ROOT);}
    private static boolean active(){return cfg!=null&&cfg.enabled&&ConstellationClient.loc().area()==SkyblockArea.THE_RIFT;}
    private static String clean(String value){String clean=ChatFormatting.stripFormatting(value);return clean==null?"":clean.trim();}
    private static void reset(){levelIdentity=null;lastShyAlert=0;}
    private static void save(){ConstellationClient.saveConfig();}
    private static void local(String text){Minecraft mc=Minecraft.getInstance();if(mc.player!=null)mc.player.sendSystemMessage(Component.literal("§5[Wyld Woods] §f"+text));}

    public static void registerCommands(CommandDispatcher<FabricClientCommandSource> dispatcher){
        dispatcher.register(LiteralArgumentBuilder.<FabricClientCommandSource>literal("wyldwoods").executes(c->status())
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("status").executes(c->status()))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("number").then(RequiredArgumentBuilder.<FabricClientCommandSource,String>argument("name",StringArgumentType.word()).then(RequiredArgumentBuilder.<FabricClientCommandSource,Integer>argument("value",IntegerArgumentType.integer(0)).executes(c->number(StringArgumentType.getString(c,"name"),IntegerArgumentType.getInteger(c,"value"))))))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("option").then(RequiredArgumentBuilder.<FabricClientCommandSource,String>argument("name",StringArgumentType.word()).then(RequiredArgumentBuilder.<FabricClientCommandSource,String>argument("state",StringArgumentType.word()).executes(c->option(StringArgumentType.getString(c,"name"),StringArgumentType.getString(c,"state")))))));
    }
    private static int status(){local("Larvas "+(cfg.wyldLarvas?"on":"off")+", Odonatas "+(cfg.wyldOdonata?"on":"off")+", Shy warning "+(cfg.wyldShyWarning?"on":"off")+".");return 1;}
    private static int number(String name,int value){switch(name.toLowerCase(Locale.ROOT)){case"larvarange"->cfg.wyldLarvaRange=Math.clamp(value,10,150);case"odonatarange"->cfg.wyldOdonataRange=Math.clamp(value,10,150);case"shyrange"->cfg.wyldShyRange=Math.clamp(value,3,20);case"cooldown"->cfg.wyldShyAlertCooldownMillis=Math.clamp(value,50,2000);default->{local("Unknown Wyld Woods number.");return 0;}}save();return status();}
    private static int option(String name,String state){Boolean value=switch(state.toLowerCase(Locale.ROOT)){case"on","true","yes","1"->true;case"off","false","no","0"->false;default->null;};if(value==null){local("State must be on or off.");return 0;}switch(name.toLowerCase(Locale.ROOT)){case"larvas"->cfg.wyldLarvas=value;case"larvahook"->cfg.wyldLarvaRequireHook=value;case"odonatas"->cfg.wyldOdonata=value;case"odonatabottle"->cfg.wyldOdonataRequireBottle=value;case"shy"->cfg.wyldShyWarning=value;case"title"->cfg.wyldShyTitle=value;case"subtitle"->cfg.wyldShySubtitle=value;case"chat"->cfg.wyldShyChat=value;case"sound"->cfg.wyldShySound=value;case"shybox"->cfg.wyldShyBox=value;case"shylabel"->cfg.wyldShyLabel=value;default->{local("Unknown Wyld Woods option.");return 0;}}save();return status();}
}
