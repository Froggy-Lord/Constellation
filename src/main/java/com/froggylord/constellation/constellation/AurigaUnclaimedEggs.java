package com.froggylord.constellation.constellation;

import com.froggylord.constellation.ConstellationClient;
import com.froggylord.constellation.config.AurigaConfig;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// ported from SkyHanni (LGPL-3.0-or-later): features/event/hoppity/HoppityEggType.kt
// ported from SkyHanni (LGPL-3.0-or-later): features/event/hoppity/HoppityEggsManager.kt
// ported from SkyHanni (LGPL-3.0-or-later): features/event/hoppity/HoppityEggDisplayManager.kt
public final class AurigaUnclaimedEggs {
    public enum Meal {
        BREAKFAST("Breakfast",7,false,0xFFFFAA00), LUNCH("Lunch",14,false,0xFF5555FF), DINNER("Dinner",21,false,0xFF55FF55),
        BRUNCH("Brunch",7,true,0xFFFFAA00), DEJEUNER("Déjeuner",14,true,0xFF5555FF), SUPPER("Supper",21,true,0xFF55FF55);
        public final String label; final int hour; final boolean alternate; final int color;
        Meal(String label,int hour,boolean alternate,int color){this.label=label;this.hour=hour;this.alternate=alternate;this.color=color;}
    }
    public record Egg(Meal meal,long latest,long next,boolean claimed,boolean ready,boolean remaining) {}
    public record Row(String label,String value,int color) {}

    private static final long EPOCH=1_559_829_300_000L;
    private static final long YEAR=124L*60*60*1000;
    private static final long DAY=YEAR/12/31;
    private static final long HOUR=DAY/24;
    private static final Pattern FOUND=Pattern.compile("^HOPPITY'S HUNT You found a Chocolate ([\\p{L}]+) Egg(?: .*)?!$",Pattern.CASE_INSENSITIVE);
    private static final Pattern SPAWNED=Pattern.compile("^HOPPITY'S HUNT A Chocolate ([\\p{L}]+) Egg has appeared!$",Pattern.CASE_INSENSITIVE);
    private static final Pattern ALREADY=Pattern.compile("^You have already collected this Chocolate ([\\p{L}]+) Egg! Try again when it respawns!$",Pattern.CASE_INSENSITIVE);
    private static final Pattern NONE=Pattern.compile("^There are no hidden Chocolate Rabbit Eggs nearby! Try again later!$",Pattern.CASE_INSENSITIVE);
    private static final Pattern NOT_EVENT=Pattern.compile("^This only works during Hoppity's Hunt!$",Pattern.CASE_INSENSITIVE);

    private static AurigaConfig cfg;
    private static boolean knownState;
    private static long lastWarning;
    private static String lastMessage="";
    private static long lastMessageAt;
    private static String stateProfile="";
    private static int stateYear;

    private AurigaUnclaimedEggs(){}

    public static void init(AurigaConfig config){
        cfg=config;normalize();stateProfile=profile();stateYear=currentYear();
        ClientReceiveMessageEvents.ALLOW_GAME.register((message,overlay)->{if(!overlay)chat(message.getString());return true;});
        ConstellationClient.tick().every(1,"auriga-unclaimed-hoppity-eggs",AurigaUnclaimedEggs::tick);
    }

    private static void chat(String formatted){
        if(!active())return;syncScope();String text=clean(formatted);long now=System.currentTimeMillis();
        if(text.equals(lastMessage)&&now-lastMessageAt<1000)return;lastMessage=text;lastMessageAt=now;
        Matcher matcher=FOUND.matcher(text);
        if(matcher.matches()){Meal meal=meal(matcher.group(1));if(meal!=null){knownState=true;claim(meal);}return;}
        matcher=ALREADY.matcher(text);
        if(matcher.matches()){Meal meal=meal(matcher.group(1));if(meal!=null){knownState=true;claim(meal);if(cfg.hoppityUnclaimedEggsChatTime)nextMessage();}return;}
        if(NONE.matcher(text).matches()){knownState=true;for(Egg egg:eggs())if(egg.latest>0)claim(egg.meal);if(cfg.hoppityUnclaimedEggsChatTime)nextMessage();return;}
        if(SPAWNED.matcher(text).matches()){knownState=true;return;}
        if(NOT_EVENT.matcher(text).matches()&&cfg.hoppityUnclaimedEggsChatTime)local("The next Hoppity's Hunt begins in "+duration(nextEvent()-now)+".");
    }

    private static void tick(){
        syncScope();
        if(!active()||!isSpring()||!knownState||!cfg.hoppityUnclaimedEggsWarnings)return;
        List<Egg> eggs=eggs();if(eggs.stream().anyMatch(egg->egg.latest<=0||egg.claimed))return;
        long now=System.currentTimeMillis(),cooldown=Math.clamp(cfg.hoppityUnclaimedEggsWarningMinutes,1,60)*60_000L;
        if(now-lastWarning<cooldown)return;lastWarning=now;Minecraft mc=Minecraft.getInstance();if(mc.player==null)return;
        String text="All 6 Hoppity meal eggs are ready.";
        if(cfg.hoppityUnclaimedEggsWarningChat)local(text);
        if(cfg.hoppityUnclaimedEggsWarningTitle){mc.gui.hud.resetTitleTimes();mc.gui.hud.setTitle(Component.literal("6 Hoppity eggs ready").withColor(cfg.hoppityUnclaimedEggsReadyColor&0xFFFFFF));}
        if(cfg.hoppityUnclaimedEggsWarningSound)mc.player.playSound(SoundEvents.NOTE_BLOCK_PLING.value(),.9f,1.2f);
    }

    public static List<Egg> eggs(){
        long now=System.currentTimeMillis(),start=eventStart(now),end=start+YEAR/4;
        ArrayList<Egg> out=new ArrayList<>();
        for(Meal meal:Meal.values()){
            long latest=0,next=0;
            for(int day=1;day<=93;day++){
                boolean alternate=day%2==0;if(alternate!=meal.alternate)continue;
                long spawn=start+(day-1)*DAY+meal.hour*HOUR;
                if(spawn<=now)latest=spawn;else if(next==0)next=spawn;
            }
            if(next>=end)next=0;
            boolean claimed=latest<=0||cfg.hoppityEggClaimCycles.getOrDefault(claimKey(meal),0L)>=latest;
            out.add(new Egg(meal,latest,next,claimed,latest>0&&!claimed,next>0));
        }
        Comparator<Egg> comparator;
        if(cfg.hoppityUnclaimedEggsReadyFirst)comparator=Comparator.comparing((Egg egg)->!egg.ready);
        else comparator=(a,b)->0;
        if(cfg.hoppityUnclaimedEggsSoonestFirst)comparator=comparator.thenComparingLong(egg->egg.ready?0:egg.next==0?Long.MAX_VALUE:egg.next);
        else comparator=comparator.thenComparing(egg->egg.meal.alternate).thenComparingInt(egg->egg.meal.hour);
        out.sort(comparator);return List.copyOf(out);
    }

    public static List<Row> rows(){
        long now=System.currentTimeMillis();ArrayList<Row> rows=new ArrayList<>();
        for(Egg egg:eggs()){
            if(egg.ready)rows.add(new Row(egg.meal.label,"Ready",cfg.hoppityUnclaimedEggsReadyColor));
            else if(egg.claimed&&egg.remaining&&cfg.hoppityUnclaimedEggsShowClaimed)rows.add(new Row(egg.meal.label,"Claimed · "+duration(egg.next-now),cfg.hoppityUnclaimedEggsClaimedColor));
            else if(egg.remaining&&cfg.hoppityUnclaimedEggsShowFuture)rows.add(new Row(egg.meal.label,duration(egg.next-now),egg.meal.color));
            else if(!egg.claimed)rows.add(new Row(egg.meal.label,"Ready · event ending",cfg.hoppityUnclaimedEggsReadyColor));
        }
        if(cfg.hoppityUnclaimedEggsShowEventTime&&isSpring())rows.add(new Row("Event ends",duration(eventEnd(now)-now),cfg.hoppityUnclaimedEggsFutureColor));
        return List.copyOf(rows);
    }

    public static boolean visible(){return active()&&(!cfg.hoppityUnclaimedEggsEventOnly||isSpring())&&!rows().isEmpty();}
    public static AurigaConfig config(){return cfg;}

    public static void registerCommands(CommandDispatcher<FabricClientCommandSource> dispatcher){
        dispatcher.register(LiteralArgumentBuilder.<FabricClientCommandSource>literal("hoppityeggs")
            .executes(context->status())
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("status").executes(context->status()))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("clear")
                .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("confirm").executes(context->clear())))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("warningminutes")
                .then(RequiredArgumentBuilder.<FabricClientCommandSource,Integer>argument("minutes",IntegerArgumentType.integer(1,60))
                    .executes(context->warning(IntegerArgumentType.getInteger(context,"minutes")))))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("option")
                .then(RequiredArgumentBuilder.<FabricClientCommandSource,String>argument("name",StringArgumentType.word())
                    .then(RequiredArgumentBuilder.<FabricClientCommandSource,String>argument("state",StringArgumentType.word())
                        .executes(context->option(StringArgumentType.getString(context,"name"),StringArgumentType.getString(context,"state")))))));
    }

    private static int status(){long ready=eggs().stream().filter(Egg::ready).count();long claimed=eggs().stream().filter(egg->egg.claimed&&egg.latest>0).count();local(ready+" ready, "+claimed+" claimed this cycle, "+(6-ready-claimed)+" upcoming.");return 1;}
    private static int clear(){String prefix=profile()+"|";cfg.hoppityEggClaimCycles.keySet().removeIf(key->key.startsWith(prefix));knownState=false;save();local("Current profile meal-egg state cleared.");return 1;}
    private static int warning(int value){cfg.hoppityUnclaimedEggsWarningMinutes=value;save();local("Warning repeat set to "+value+" minute"+(value==1?"":"s")+".");return 1;}
    private static int option(String name,String raw){
        Boolean value=bool(raw);if(value==null){local("State must be on or off.");return 0;}
        switch(name.toLowerCase(Locale.ROOT)){
            case"enabled"->cfg.hoppityUnclaimedEggs=value;case"hud"->cfg.hoppityUnclaimedEggsHud=value;
            case"eventonly"->cfg.hoppityUnclaimedEggsEventOnly=value;case"soonest"->cfg.hoppityUnclaimedEggsSoonestFirst=value;
            case"readyfirst"->cfg.hoppityUnclaimedEggsReadyFirst=value;case"claimed"->cfg.hoppityUnclaimedEggsShowClaimed=value;
            case"future"->cfg.hoppityUnclaimedEggsShowFuture=value;case"eventtime"->cfg.hoppityUnclaimedEggsShowEventTime=value;
            case"chattime"->cfg.hoppityUnclaimedEggsChatTime=value;case"warnings"->cfg.hoppityUnclaimedEggsWarnings=value;
            case"warningchat"->cfg.hoppityUnclaimedEggsWarningChat=value;case"warningtitle"->cfg.hoppityUnclaimedEggsWarningTitle=value;
            case"warningsound"->cfg.hoppityUnclaimedEggsWarningSound=value;case"persist"->cfg.hoppityUnclaimedEggsPersistProfiles=value;
            default->{local("Unknown Hoppity egg option.");return 0;}
        }save();return status();
    }

    private static void claim(Meal meal){Egg egg=eggs().stream().filter(value->value.meal==meal).findFirst().orElse(null);if(egg==null||egg.latest<=0)return;cfg.hoppityEggClaimCycles.put(claimKey(meal),egg.latest);save();}
    private static void nextMessage(){long now=System.currentTimeMillis();long next=eggs().stream().mapToLong(Egg::next).filter(value->value>now).min().orElse(0);local(next>0?"Next meal egg spawns in "+duration(next-now)+".":"No more meal eggs will spawn this event.");}
    private static String claimKey(Meal meal){return profile()+"|"+currentYear()+"|"+meal.name();}
    private static void syncScope(){String profile=profile();int year=currentYear();if(!profile.equals(stateProfile)||year!=stateYear){stateProfile=profile;stateYear=year;knownState=false;lastWarning=0;}}
    private static String profile(){String value=LyraStorageValue.currentProfileKey();return value==null||value.isBlank()?"unknown":value.toLowerCase(Locale.ROOT);}
    private static Meal meal(String raw){String value=raw.toUpperCase(Locale.ROOT).replace("É","E");if(value.equals("DEJEUNE")||value.equals("DEJEUNER"))return Meal.DEJEUNER;try{return Meal.valueOf(value);}catch(Exception ignored){return null;}}
    private static long eventStart(long now){return EPOCH+Math.max(0,Math.floorDiv(now-EPOCH,YEAR))*YEAR;}
    private static long eventEnd(long now){return eventStart(now)+YEAR/4;}
    private static long nextEvent(){long now=System.currentTimeMillis(),start=eventStart(now);return now<start?start:start+YEAR;}
    private static int currentYear(){return(int)(Math.max(0,System.currentTimeMillis()-EPOCH)/YEAR)+1;}
    private static boolean isSpring(){long within=Math.floorMod(System.currentTimeMillis()-EPOCH,YEAR);return within<YEAR/4;}
    private static String duration(long millis){if(millis<=0)return"now";long seconds=(millis+999)/1000,hours=seconds/3600,minutes=seconds%3600/60,secs=seconds%60;if(hours>0)return hours+"h "+minutes+"m";if(minutes>0)return minutes+"m "+secs+"s";return secs+"s";}
    private static String clean(String value){String clean=ChatFormatting.stripFormatting(value);return clean==null?"":clean.replaceAll("\\s+"," ").strip();}
    private static Boolean bool(String value){return switch(value.toLowerCase(Locale.ROOT)){case"on","true","yes","1"->true;case"off","false","no","0"->false;default->null;};}
    private static boolean active(){return cfg!=null&&cfg.enabled&&cfg.hoppityUnclaimedEggs&&ConstellationClient.loc().onHypixel();}
    private static void normalize(){if(cfg.hoppityEggClaimCycles==null)cfg.hoppityEggClaimCycles=new LinkedHashMap<>();cfg.hoppityUnclaimedEggsWarningMinutes=Math.clamp(cfg.hoppityUnclaimedEggsWarningMinutes,1,60);}
    private static void save(){if(cfg.hoppityUnclaimedEggsPersistProfiles)ConstellationClient.saveConfig();}
    private static void local(String text){Minecraft mc=Minecraft.getInstance();if(mc.player!=null)mc.player.sendSystemMessage(Component.literal("\u00a7b[Hoppity Eggs] \u00a7f"+text));}
}
