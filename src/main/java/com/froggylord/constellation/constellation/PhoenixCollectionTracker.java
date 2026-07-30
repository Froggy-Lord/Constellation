package com.froggylord.constellation.constellation;

import com.froggylord.constellation.ConstellationClient;
import com.froggylord.constellation.config.PhoenixConfig;
import com.froggylord.constellation.config.PhoenixConfig.CollectionTrackerData;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// ported from SkyHanni (LGPL-2.1): api/CollectionApi.kt
// ported from SkyblockCollectionTracker (LGPL-2.1): tracker/collection/TrackingRates.java, tracker/collection/TrackingHandler.java
public final class PhoenixCollectionTracker {
    private static final Pattern MENU=Pattern.compile("(?i)^(?:.+ )?Collections?$");
    private static final Pattern TOTAL=Pattern.compile("(?i)^(?:Total Collected|Collection):\\s*([\\d,.]+).*$");
    private static final Pattern PROGRESS=Pattern.compile("(?i)^Progress to (.+?):\\s*([\\d,.]+)\\s*/\\s*([\\d,.]+).*$");
    private static PhoenixConfig cfg;
    private static AbstractContainerScreen<?> screen;
    private static String profile="";
    private static String sessionKey="";
    private static long sessionStart=-1,lastAmount=-1,lastGain,sessionStartedAt,activeMillis,activeSince;
    private static boolean paused,initialized;

    private PhoenixCollectionTracker(){}

    public static void init(PhoenixConfig config){
        cfg=config;normalize();
        if(initialized)return;initialized=true;
        ScreenEvents.AFTER_INIT.register((client,current,width,height)->{
            if(!(current instanceof AbstractContainerScreen<?> container)||!MENU.matcher(clean(container.getTitle().getString())).matches())return;
            screen=container;
            ScreenEvents.afterTick(container).register(ignored->scan(container));
            ScreenEvents.remove(container).register(ignored->{if(screen==container)screen=null;});
        });
        ConstellationClient.tick().every(20,"phoenix-collection-tracker",PhoenixCollectionTracker::tick);
    }

    private static void tick(){
        String next=profile();
        if(!next.equals(profile)){profile=next;resetSession();}
        if(active()&&screen!=null)scan(screen);
    }

    private static void scan(AbstractContainerScreen<?> container){
        if(!active()||container!=screen||!MENU.matcher(clean(container.getTitle().getString())).matches())return;
        boolean changed=false;
        for(Slot slot:container.getMenu().slots){
            ItemStack stack=slot.getItem();if(stack.isEmpty()||slot.index>=54)continue;
            String name=clean(stack.getHoverName().getString()).replaceAll("\\s+[IVXLCDM]+$","").trim();
            if(name.isBlank()||name.equalsIgnoreCase("Collection Milestones"))continue;
            long amount=-1,goal=-1;
            for(String raw:lore(stack)){
                String line=clean(raw);Matcher total=TOTAL.matcher(line);if(total.matches())amount=number(total.group(1));
                Matcher progress=PROGRESS.matcher(line);if(progress.matches()){amount=Math.max(amount,number(progress.group(2)));goal=number(progress.group(3));}
            }
            if(amount<0)continue;
            CollectionTrackerData data=data(name);long old=data.amount;
            data.name=name;data.amount=amount;data.updatedAt=System.currentTimeMillis();if(goal>0)data.goal=goal;
            changed|=old!=amount;
            if((cfg.collectionTrackerSelected.isBlank()&&cfg.collectionTrackerAutoSelect)||same(cfg.collectionTrackerSelected,name))select(name,data);
        }
        if(changed&&cfg.collectionTrackerPersistProfiles)ConstellationClient.saveConfig();
    }

    private static void select(String name,CollectionTrackerData data){
        String key=key(name);cfg.collectionTrackerSelected=name;
        if(!sessionKey.equals(key)){sessionKey=key;sessionStart=data.amount;lastAmount=data.amount;lastGain=0;sessionStartedAt=System.currentTimeMillis();activeMillis=0;activeSince=sessionStartedAt;paused=false;return;}
        if(data.amount>=lastAmount){lastGain=data.amount-lastAmount;lastAmount=data.amount;}
    }

    public static void registerCommands(CommandDispatcher<FabricClientCommandSource> dispatcher){
        var root=LiteralArgumentBuilder.<FabricClientCommandSource>literal("collectiontracker").executes(c->status())
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("status").executes(c->status()))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("pause").executes(c->pause()))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("resume").executes(c->resume()))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("reset").executes(c->{resetSession();return status();}))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("clear").executes(c->clear()))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("track")
                .then(com.mojang.brigadier.builder.RequiredArgumentBuilder.<FabricClientCommandSource,String>argument("collection",StringArgumentType.greedyString()).executes(c->track(StringArgumentType.getString(c,"collection")))))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("goal")
                .then(com.mojang.brigadier.builder.RequiredArgumentBuilder.<FabricClientCommandSource,Long>argument("amount",LongArgumentType.longArg(0)).executes(c->goal(LongArgumentType.getLong(c,"amount")))))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("option")
                .then(com.mojang.brigadier.builder.RequiredArgumentBuilder.<FabricClientCommandSource,String>argument("name",StringArgumentType.word())
                    .then(com.mojang.brigadier.builder.RequiredArgumentBuilder.<FabricClientCommandSource,String>argument("state",StringArgumentType.word()).executes(c->option(StringArgumentType.getString(c,"name"),StringArgumentType.getString(c,"state"))))));
        dispatcher.register(root);
    }

    private static int track(String name){CollectionTrackerData found=find(name);if(found==null){local("Open the Collections menu once, then choose a synced collection.");return 0;}select(found.name,found);ConstellationClient.saveConfig();return status();}
    private static int goal(long amount){cfg.collectionTrackerGoal=amount;CollectionTrackerData data=state();if(data!=null)data.goal=amount;ConstellationClient.saveConfig();return status();}
    private static int pause(){if(!paused){activeMillis+=System.currentTimeMillis()-activeSince;paused=true;}return status();}
    private static int resume(){if(paused){activeSince=System.currentTimeMillis();paused=false;}return status();}
    private static int clear(){cfg.collectionTrackerSelected="";resetSession();ConstellationClient.saveConfig();local("Collection tracker selection cleared.");return 1;}
    private static int option(String name,String raw){Boolean value=bool(raw);if(value==null){local("State must be on or off.");return 0;}switch(name.toLowerCase(Locale.ROOT)){case"enabled"->cfg.collectionTracker=value;case"hud"->cfg.collectionTrackerHud=value;case"autoselect"->cfg.collectionTrackerAutoSelect=value;case"persist"->cfg.collectionTrackerPersistProfiles=value;case"total"->cfg.collectionTrackerShowTotal=value;case"session"->cfg.collectionTrackerShowSession=value;case"rate"->cfg.collectionTrackerShowRate=value;case"goal"->cfg.collectionTrackerShowGoal=value;case"eta"->cfg.collectionTrackerShowEta=value;case"lastgain"->cfg.collectionTrackerShowLastGain=value;case"freshness"->cfg.collectionTrackerShowFreshness=value;default->{local("Unknown Collection Tracker option.");return 0;}}ConstellationClient.saveConfig();return status();}
    private static int status(){CollectionTrackerData data=state();local(data==null?"Collection tracker: no synced collection. Open /collections.":"Collection tracker: "+data.name+" "+format(data.amount)+" | session "+format(gained())+(paused?" | paused":""));return 1;}

    public static CollectionTrackerData state(){return cfg==null||profile.isBlank()||cfg.collectionTrackerSelected.isBlank()?null:data(cfg.collectionTrackerSelected);}
    public static long gained(){return sessionStart<0||lastAmount<sessionStart?0:lastAmount-sessionStart;}
    public static long lastGain(){return lastGain;}
    public static long elapsed(){return Math.max(0,(activeMillis+(paused?0:System.currentTimeMillis()-activeSince))/1000);}
    public static long perHour(){long seconds=elapsed();return seconds<2?0:Math.round(gained()*3600d/seconds);}
    public static long goal(){CollectionTrackerData data=state();return cfg.collectionTrackerGoal>0?cfg.collectionTrackerGoal:data==null?0:data.goal;}
    public static String eta(){long rate=perHour(),remaining=Math.max(0,goal()-(state()==null?0:state().amount));return rate<=0||remaining<=0?"":duration(Math.round(remaining*3600d/rate));}
    public static long age(){CollectionTrackerData data=state();return data==null?Long.MAX_VALUE:Math.max(0,(System.currentTimeMillis()-data.updatedAt)/1000);}
    public static boolean visible(){return active()&&cfg.collectionTrackerHud&&state()!=null;}
    public static boolean paused(){return paused;}
    public static PhoenixConfig config(){return cfg;}

    private static void resetSession(){sessionKey="";sessionStart=-1;lastAmount=-1;lastGain=0;sessionStartedAt=0;activeMillis=0;activeSince=System.currentTimeMillis();paused=false;CollectionTrackerData data=state();if(data!=null)select(data.name,data);}
    private static CollectionTrackerData data(String name){normalize();return cfg.collectionsByProfile.computeIfAbsent(profile(),ignored->new LinkedHashMap<>()).computeIfAbsent(key(name),ignored->new CollectionTrackerData());}
    private static CollectionTrackerData find(String name){Map<String,CollectionTrackerData> map=cfg.collectionsByProfile.get(profile());if(map==null)return null;String wanted=key(name);CollectionTrackerData exact=map.get(wanted);if(exact!=null)return exact;for(CollectionTrackerData data:map.values())if(key(data.name).contains(wanted))return data;return null;}
    private static boolean active(){return cfg!=null&&cfg.enabled&&cfg.collectionTracker&&ConstellationClient.loc().onHypixel();}
    private static String profile(){String value=LyraStorageValue.currentProfileKey();return value==null||value.isBlank()?"unknown":value.toLowerCase(Locale.ROOT);}
    private static void normalize(){if(cfg.collectionsByProfile==null)cfg.collectionsByProfile=new LinkedHashMap<>();}
    private static List<String> lore(ItemStack stack){ItemLore lore=stack.get(DataComponents.LORE);return lore==null?List.of():lore.lines().stream().map(Component::getString).toList();}
    private static String clean(String value){return ChatFormatting.stripFormatting(value)==null?"":ChatFormatting.stripFormatting(value).trim();}
    private static String key(String value){return clean(value).toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+","");}
    private static boolean same(String a,String b){return key(a).equals(key(b));}
    private static long number(String value){try{return Math.round(Double.parseDouble(value.replace(",","")));}catch(Exception ignored){return-1;}}
    public static String format(long value){if(value>=1_000_000_000)return String.format(Locale.ROOT,"%.2fB",value/1e9);if(value>=1_000_000)return String.format(Locale.ROOT,"%.2fM",value/1e6);if(value>=1_000)return String.format(Locale.ROOT,"%.1fk",value/1e3);return Long.toString(value);}
    public static String duration(long seconds){long h=seconds/3600,m=seconds%3600/60,s=seconds%60;return h>0?h+"h "+m+"m":m>0?m+"m "+s+"s":s+"s";}
    private static Boolean bool(String raw){if(raw.equalsIgnoreCase("on")||raw.equalsIgnoreCase("true"))return true;if(raw.equalsIgnoreCase("off")||raw.equalsIgnoreCase("false"))return false;return null;}
    private static void local(String text){Minecraft mc=Minecraft.getInstance();if(mc.player!=null)mc.player.sendSystemMessage(Component.literal(text));}
}
