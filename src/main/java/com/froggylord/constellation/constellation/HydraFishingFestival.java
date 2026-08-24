package com.froggylord.constellation.constellation;

import com.froggylord.constellation.ConstellationClient;
import com.froggylord.constellation.config.HydraConfig;
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
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;

import java.util.List;
import java.util.Locale;

// ported from Feesh (Apache-2.0): features/overlays/FishingFestivalTracker.kt
public final class HydraFishingFestival {
    public record State(int total,int greatWhite,int tiger,int blue,int nurse,int bestTotal,int bestGreatWhite,long startedAt,boolean alpha) {}
    private static final String ENDED="FISHING FESTIVAL The festival has concluded! Time to dry off and repair your rods!";
    private static HydraConfig cfg;
    private static boolean initialized,wasEnabled,sessionAlpha;
    private static int greatWhite,tiger,blue,nurse;
    private static long startedAt,lastSubmerged;
    private static String profileKey="";
    private static Object level;
    private static State pendingPb;
    private HydraFishingFestival() {}

    public static void init(HydraConfig config){cfg=config;if(initialized)return;initialized=true;HydraSeaCreatureTracker.onCatch(HydraFishingFestival::onCatch);ConstellationClient.tick().every(1,"hydra-fishing-festival",HydraFishingFestival::tick);ClientReceiveMessageEvents.ALLOW_GAME.register((message,overlay)->{if(!overlay)onChat(clean(message.getString()));return true;});ClientPlayConnectionEvents.JOIN.register((a,b,c)->resetConnection());ClientPlayConnectionEvents.DISCONNECT.register((a,b)->resetConnection());}
    private static void tick(){Minecraft mc=Minecraft.getInstance();boolean selected=configured();if(!selected){if(wasEnabled){lastSubmerged=0;level=null;pendingPb=null;resetSession();}wasEnabled=false;return;}wasEnabled=true;if(mc.player==null||mc.level==null||!ConstellationClient.loc().onHypixel())return;String profile=profile();if(!profile.isEmpty()&&!profile.equals(profileKey)){if(!profileKey.isEmpty()&&cfg.fishingFestivalResetOnProfileChange)resetSession();profileKey=profile;}if(hasData()&&onAlpha())sessionAlpha=true;if(pendingPb!=null&&!profileKey.isEmpty()){State saved=pendingPb;pendingPb=null;personalBests(saved);}if(level!=mc.level){level=mc.level;if(inPast())resetSession();}if(mc.player.fishing!=null&&(mc.player.fishing.isInWater()||mc.player.fishing.isInLava()))lastSubmerged=System.currentTimeMillis();}

    // ported from Feesh (Apache-2.0): features/overlays/FishingFestivalTracker.kt
    private static void onCatch(HydraSeaCreatureTracker.Catch event){if(!trackingEnabled()||!fishingArea()||!shark(event.name()))return;if(startedAt==0||inPast()){resetSession();startedAt=event.at();}if(onAlpha())sessionAlpha=true;int amount=event.doubleHook()&&cfg.fishingFestivalCountDoubleHooks?2:1;switch(event.name()){case"Great White Shark"->greatWhite+=amount;case"Tiger Shark"->tiger+=amount;case"Blue Shark"->blue+=amount;case"Nurse Shark"->nurse+=amount;default->{return;}}lastSubmerged=event.at();}
    private static void onChat(String message){if(!configured()||!message.equals(ENDED))return;State ended=snapshot();if(ended!=null){endAlert(ended);if(cfg.fishingFestivalPersonalBests){String key=profile().isEmpty()?profileKey:profile();if(key.isEmpty())pendingPb=ended;else personalBests(ended);}}resetSession();}
    private static void endAlert(State state){if(state.total==0||!cfg.fishingFestivalEndAlert)return;Minecraft mc=Minecraft.getInstance();if(mc.player==null)return;String summary=format(cfg.fishingFestivalEndTemplate,state,0,0);if(cfg.fishingFestivalEndTitle){mc.gui.hud.resetTitleTimes();mc.gui.hud.setTitle(Component.literal("Fishing Festival ended").withColor(cfg.fishingFestivalTotalColor&0xFFFFFF));}if(cfg.fishingFestivalEndChat)local(summary);if(cfg.fishingFestivalEndSound)mc.player.playSound(SoundEvents.NOTE_BLOCK_PLING.value(),.9f,1.1f);if(cfg.fishingFestivalEndParty&&mc.player.connection!=null){String party=format(cfg.fishingFestivalPartyTemplate,state,0,0).replace('\n',' ').replace('\r',' ').trim();if(!party.isEmpty())mc.player.connection.sendCommand("pc "+party.substring(0,Math.min(240,party.length())));}}
    private static void personalBests(State state){if(!cfg.fishingFestivalPersonalBests||state.alpha)return;String key=profile().isEmpty()?profileKey:profile();if(state.total==0||key.isEmpty()){pendingPb=state;return;}int oldTotal=cfg.fishingFestivalBestTotals.getOrDefault(key,0),oldWhite=cfg.fishingFestivalBestGreatWhites.getOrDefault(key,0);boolean totalPb=state.total>oldTotal,whitePb=state.greatWhite>oldWhite;if(totalPb){cfg.fishingFestivalBestTotals.put(key,state.total);cfg.fishingFestivalBestTotalTimes.put(key,System.currentTimeMillis());pb(format(cfg.fishingFestivalPbTotalTemplate,state,oldTotal,state.total),"Sharks: "+state.total);}if(whitePb){cfg.fishingFestivalBestGreatWhites.put(key,state.greatWhite);cfg.fishingFestivalBestGreatWhiteTimes.put(key,System.currentTimeMillis());pb(format(cfg.fishingFestivalPbGreatWhiteTemplate,state,oldWhite,state.greatWhite),"Great White Sharks: "+state.greatWhite);}if(totalPb||whitePb){if(cfg.fishingFestivalPbSound){Minecraft mc=Minecraft.getInstance();if(mc.player!=null)mc.player.playSound(SoundEvents.PLAYER_LEVELUP,1f,1f);}ConstellationClient.saveConfig();}}
    private static void pb(String chat,String subtitle){Minecraft mc=Minecraft.getInstance();if(mc.player==null)return;if(cfg.fishingFestivalPbChat)local(chat);if(cfg.fishingFestivalPbTitle){mc.gui.hud.resetTitleTimes();mc.gui.hud.setTitle(Component.literal("PERSONAL BEST!").withColor(cfg.fishingFestivalPbColor&0xFFFFFF));mc.gui.hud.setSubtitle(Component.literal(subtitle));}}

    public static State state(){if(!trackingEnabled()||!hasData())return null;return snapshot();}
    public static boolean visible(){if(!trackingEnabled()||!cfg.fishingFestivalHud||!fishingArea()||!hasData())return false;return !cfg.fishingFestivalHudRequireRecentHook||System.currentTimeMillis()-lastSubmerged<=Math.clamp(cfg.fishingFestivalRecentHookMinutes,1,30)*60_000L;}
    public static HydraConfig config(){return cfg;}
    public static boolean trackingEnabled(){return configured()&&ConstellationClient.loc().onHypixel();}
    private static boolean configured(){return cfg!=null&&cfg.enabled&&cfg.sharkCounter&&cfg.fishingFestivalSuite&&(cfg.fishingFestivalHud||cfg.fishingFestivalEndAlert||cfg.fishingFestivalPersonalBests);}
    private static State snapshot(){if(!hasData())return null;String key=profile().isEmpty()?profileKey:profile();return new State(total(),greatWhite,tiger,blue,nurse,cfg.fishingFestivalBestTotals.getOrDefault(key,0),cfg.fishingFestivalBestGreatWhites.getOrDefault(key,0),startedAt,sessionAlpha);}
    private static boolean shark(String name){return List.of("Great White Shark","Tiger Shark","Blue Shark","Nurse Shark").contains(name);}
    private static boolean hasData(){return total()>0;}
    private static int total(){return greatWhite+tiger+blue+nurse;}
    private static boolean inPast(){return startedAt>0&&System.currentTimeMillis()-startedAt>Math.clamp(cfg.fishingFestivalDurationMinutes,55,90)*60_000L;}
    private static boolean fishingArea(){return switch(ConstellationClient.loc().area()){case UNKNOWN,THE_RIFT,GARDEN,KUUDRA,CATACOMBS,MASTER_MODE,DUNGEON_HUB,THE_END,GLACITE_MINESHAFT->false;default->true;};}
    private static boolean onAlpha(){return ConstellationClient.loc().getSidebarLines().stream().anyMatch(s->s.toLowerCase(Locale.ROOT).contains("alpha.hypixel.net"));}
    private static String profile(){String value=LyraStorageValue.currentProfileKey();return value==null?"":value.trim();}
    private static String format(String template,State state,int oldValue,int newValue){String value=template==null?"":template;return value.replace("{total}",Integer.toString(state.total)).replace("{great-white}",Integer.toString(state.greatWhite)).replace("{tiger}",Integer.toString(state.tiger)).replace("{blue}",Integer.toString(state.blue)).replace("{nurse}",Integer.toString(state.nurse)).replace("{old}",Integer.toString(oldValue)).replace("{new}",Integer.toString(newValue));}
    private static String clean(String raw){String value=ChatFormatting.stripFormatting(raw);return value==null?"":value.trim();}
    private static void resetConnection(){profileKey="";level=null;pendingPb=null;wasEnabled=false;lastSubmerged=0;resetSession();}
    private static void resetSession(){greatWhite=0;tiger=0;blue=0;nurse=0;startedAt=0;lastSubmerged=0;sessionAlpha=false;}

    public static void registerCommands(CommandDispatcher<FabricClientCommandSource> d){d.register(LiteralArgumentBuilder.<FabricClientCommandSource>literal("fishingfestival").executes(c->status()).then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("status").executes(c->status())).then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("reset").executes(c->{resetSession();local("Fishing Festival session reset.");return 1;})).then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("resetpb").then(RequiredArgumentBuilder.<FabricClientCommandSource,String>argument("type",StringArgumentType.word()).executes(c->resetPb(StringArgumentType.getString(c,"type"))))).then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("duration").then(RequiredArgumentBuilder.<FabricClientCommandSource,Integer>argument("minutes",IntegerArgumentType.integer(55,90)).executes(c->{cfg.fishingFestivalDurationMinutes=IntegerArgumentType.getInteger(c,"minutes");save();return status();}))).then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("option").then(RequiredArgumentBuilder.<FabricClientCommandSource,String>argument("name",StringArgumentType.word()).then(RequiredArgumentBuilder.<FabricClientCommandSource,String>argument("state",StringArgumentType.word()).executes(c->option(StringArgumentType.getString(c,"name"),StringArgumentType.getString(c,"state")))))));}
    private static int status(){State state=state();String key=profile();local("Session "+(state==null?0:state.total)+" sharks, PB "+cfg.fishingFestivalBestTotals.getOrDefault(key,0)+" total / "+cfg.fishingFestivalBestGreatWhites.getOrDefault(key,0)+" Great Whites.");return 1;}
    private static int resetPb(String type){String key=profile();if(key.isEmpty()){local("Fishing profile is not available yet.");return 0;}switch(type.toLowerCase(Locale.ROOT)){case"total"->{cfg.fishingFestivalBestTotals.remove(key);cfg.fishingFestivalBestTotalTimes.remove(key);}case"greatwhite","white"->{cfg.fishingFestivalBestGreatWhites.remove(key);cfg.fishingFestivalBestGreatWhiteTimes.remove(key);}case"all"->{cfg.fishingFestivalBestTotals.remove(key);cfg.fishingFestivalBestTotalTimes.remove(key);cfg.fishingFestivalBestGreatWhites.remove(key);cfg.fishingFestivalBestGreatWhiteTimes.remove(key);}default->{local("PB type must be total, greatwhite, or all.");return 0;}}save();return status();}
    private static int option(String name,String raw){Boolean value=switch(raw.toLowerCase(Locale.ROOT)){case"on","true","yes","1"->true;case"off","false","no","0"->false;default->null;};if(value==null){local("State must be on or off.");return 0;}switch(name.toLowerCase(Locale.ROOT)){case"hud"->cfg.fishingFestivalHud=value;case"end"->cfg.fishingFestivalEndAlert=value;case"party"->cfg.fishingFestivalEndParty=value;case"pb"->cfg.fishingFestivalPersonalBests=value;case"double"->cfg.fishingFestivalCountDoubleHooks=value;case"breakdown"->cfg.fishingFestivalHudShowBreakdown=value;case"best"->cfg.fishingFestivalHudShowPersonalBests=value;case"recent"->cfg.fishingFestivalHudRequireRecentHook=value;default->{local("Option must be hud, end, party, pb, double, breakdown, best, or recent.");return 0;}}save();return status();}
    private static void local(String text){Minecraft mc=Minecraft.getInstance();if(mc.player!=null)mc.player.sendSystemMessage(Component.literal("\u00a73[Fishing Festival] \u00a7f"+text));}
    private static void save(){ConstellationClient.saveConfig();}
}
