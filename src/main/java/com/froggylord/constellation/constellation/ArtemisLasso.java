package com.froggylord.constellation.constellation;

import com.froggylord.constellation.ConstellationClient;
import com.froggylord.constellation.config.ArtemisConfig;
import com.froggylord.constellation.core.LocationManager.SkyblockArea;
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
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Leashable;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

import java.util.*;

public final class ArtemisLasso {
    public record State(boolean active,boolean reel,int percent,String target,String tool,double distance,long startedAt){}
    private static ArtemisConfig cfg;
    private static boolean initialized,readyAlerted;
    private static State state;
    private static Object levelIdentity;
    private static long lastReadyAlert;

    private ArtemisLasso(){}

    public static void init(ArtemisConfig config){
        cfg=config;if(initialized)return;initialized=true;
        ClientPlayConnectionEvents.JOIN.register((a,b,c)->reset());
        ClientPlayConnectionEvents.DISCONNECT.register((a,b)->reset());
        ConstellationClient.tick().every(1,"artemis-lasso",ArtemisLasso::tick);
    }

    // ported from SkyHanni (LGPL-3.0-or-later): features/hunting/LassoDisplay.kt
    // progress calculation ported from Skyblocker (LGPL-3.0-only): skyblock/hunting/LassoHud.java
    private static void tick(){
        Minecraft mc=Minecraft.getInstance();
        if(mc.level!=levelIdentity){levelIdentity=mc.level;clear();}
        if(!active()||mc.player==null||mc.level==null){clear();return;}
        ItemStack held=mc.player.getMainHandItem();String id=itemId(held);
        if(!isLasso(id,held)){clear();return;}
        Entity end=null;
        for(Entity entity:mc.level.entitiesForRendering()){
            if(!(entity instanceof Leashable leashable)||!entity.isAlive())continue;
            if(leashable.getLeashHolder()==mc.player){end=entity;break;}
        }
        if(end==null){clear();return;}
        int reelValue=switch(id){case"ABYSMAL_LASSO"->2;case"VINERIP_LASSO","ENTANGLER_LASSO"->3;case"EVERSTRETCH_LASSO"->4;default->0;};
        boolean reel=false;int percent=-1;String target="";
        double search=Math.clamp(cfg.lassoTargetSearchRange,2,12);
        for(Entity entity:mc.level.entitiesForRendering()){
            if(!(entity instanceof ArmorStand stand)||stand.distanceToSqr(end)>search*search||!stand.hasCustomName())continue;
            Component name=stand.getCustomName();if(name==null)continue;
            String plain=name.getString();
            if(plain.equals("REEL")){reel=true;percent=0;continue;}
            if(plain.equals("                    ")&&name.getSiblings().size()==2){
                int filled=name.getSiblings().getFirst().getString().length();
                percent=Math.clamp((int)(((filled-reelValue)/(20f-reelValue))*100),0,100);
                continue;
            }
            String cleaned=clean(plain);
            if(target.isBlank()&&!cleaned.isBlank()&&!cleaned.equals("Armor Stand")&&!cleaned.contains("REEL"))target=targetName(cleaned);
        }
        if(percent<0&&!reel){clear();return;}
        long started=state==null?System.currentTimeMillis():state.startedAt;
        state=new State(true,reel,Math.max(0,percent),target,toolName(held,id),mc.player.distanceTo(end),started);
        if(reel||percent<=Math.clamp(cfg.lassoReadyPercent,0,100))ready();
        else readyAlerted=false;
    }

    private static void ready(){
        long now=System.currentTimeMillis();
        if(!cfg.lassoReadyAlert||readyAlerted&&!cfg.lassoReadyRepeat
            ||cfg.lassoReadyRepeat&&now-lastReadyAlert<Math.clamp(cfg.lassoReadyRepeatSeconds,1,30)*1000L)return;
        readyAlerted=true;Minecraft mc=Minecraft.getInstance();if(mc.player==null)return;
        if(cfg.lassoReadyChat)local("Reel now.");
        if(cfg.lassoReadyTitle){mc.gui.hud.resetTitleTimes();mc.gui.hud.setTitle(Component.literal("REEL").withColor(cfg.lassoReadyColor&0xFFFFFF));}
        if(cfg.lassoReadySound)mc.player.playSound(SoundEvents.EXPERIENCE_ORB_PICKUP,.8f,1.5f);
        lastReadyAlert=now;
    }

    public static State state(){return state;}
    public static ArtemisConfig config(){return cfg;}
    public static boolean visible(){return active()&&state!=null&&state.active;}
    private static boolean active(){return cfg!=null&&cfg.enabled&&cfg.lassoDisplay&&ConstellationClient.loc().onHypixel()&&(!cfg.lassoGalateaOnly||ConstellationClient.loc().area()==SkyblockArea.GALATEA);}
    private static void reset(){levelIdentity=null;clear();}
    private static void clear(){state=null;readyAlerted=false;lastReadyAlert=0;}
    private static boolean isLasso(String id,ItemStack stack){return id.contains("LASSO")||clean(stack.getHoverName().getString()).toUpperCase(Locale.ROOT).contains("LASSO");}
    private static String itemId(ItemStack stack){if(stack==null||stack.isEmpty())return"";CustomData data=stack.get(DataComponents.CUSTOM_DATA);if(data==null)return"";CompoundTag root=data.copyTag(),extra=root.getCompoundOrEmpty("ExtraAttributes");if(extra.isEmpty())extra=root;return extra.getStringOr("id","").toUpperCase(Locale.ROOT);}
    private static String toolName(ItemStack stack,String id){String name=clean(stack.getHoverName().getString());return name.isBlank()?id.replace('_',' '):name;}
    private static String targetName(String raw){String value=raw.replaceAll("\\d[\\d,.]*[kKmMbB]?/?\\d*[kKmMbB]?\\s*\\p{So}?","").replaceAll("\\[Lv\\d+\\]","").trim();return value.length()>40?value.substring(0,40):value;}
    private static String clean(String raw){String value=ChatFormatting.stripFormatting(raw);return value==null?"":value.trim();}
    private static void local(String text){Minecraft mc=Minecraft.getInstance();if(mc.player!=null)mc.player.sendSystemMessage(Component.literal("§6[Lasso] §f"+text));}
    private static void save(){ConstellationClient.saveConfig();}

    public static void registerCommands(CommandDispatcher<FabricClientCommandSource>d){
        d.register(LiteralArgumentBuilder.<FabricClientCommandSource>literal("lassohud").executes(c->status())
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("status").executes(c->status()))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("reset").executes(c->{clear();local("Lasso display reset.");return 1;}))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("ready").then(RequiredArgumentBuilder.<FabricClientCommandSource,Integer>argument("percent",IntegerArgumentType.integer(0,100)).executes(c->{cfg.lassoReadyPercent=IntegerArgumentType.getInteger(c,"percent");save();return status();})))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("repeatseconds").then(RequiredArgumentBuilder.<FabricClientCommandSource,Integer>argument("seconds",IntegerArgumentType.integer(1,30)).executes(c->{cfg.lassoReadyRepeatSeconds=IntegerArgumentType.getInteger(c,"seconds");save();return status();})))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("range").then(RequiredArgumentBuilder.<FabricClientCommandSource,Integer>argument("blocks",IntegerArgumentType.integer(2,12)).executes(c->{cfg.lassoTargetSearchRange=IntegerArgumentType.getInteger(c,"blocks");save();return status();})))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("option").then(RequiredArgumentBuilder.<FabricClientCommandSource,String>argument("name",StringArgumentType.word()).then(RequiredArgumentBuilder.<FabricClientCommandSource,String>argument("state",StringArgumentType.word()).executes(c->option(StringArgumentType.getString(c,"name"),StringArgumentType.getString(c,"state")))))));
    }
    private static int status(){local("Lasso HUD "+(cfg.lassoDisplay?"on":"off")+(state==null?".":"; "+state.percent+"%"+(state.reel?", reel now":" remaining")+"."));return 1;}
    private static int option(String name,String raw){Boolean value=switch(raw.toLowerCase(Locale.ROOT)){case"on","true","yes","1"->true;case"off","false","no","0"->false;default->null;};if(value==null){local("State must be on or off.");return 0;}switch(name.toLowerCase(Locale.ROOT)){case"enabled"->cfg.lassoDisplay=value;case"galatea"->cfg.lassoGalateaOnly=value;case"progress"->cfg.lassoShowProgress=value;case"percent"->cfg.lassoShowPercent=value;case"target"->cfg.lassoShowTarget=value;case"tool"->cfg.lassoShowTool=value;case"distance"->cfg.lassoShowDistance=value;case"compact"->cfg.lassoCompact=value;case"alert"->cfg.lassoReadyAlert=value;case"chat"->cfg.lassoReadyChat=value;case"title"->cfg.lassoReadyTitle=value;case"sound"->cfg.lassoReadySound=value;case"repeat"->cfg.lassoReadyRepeat=value;default->{local("Unknown Lasso HUD option.");return 0;}}save();return status();}
}
